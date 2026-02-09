@file:Suppress("SpellCheckingInspection")
package com.zyra.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.app.PendingIntent

import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.zyra.app.R
import com.zyra.app.logic.CommandManager
import com.zyra.app.logic.NaturalLanguageProcessor
import com.zyra.app.logic.OfflineBrain
// import com.zyra.app.logic.ParsedIntent 
import com.zyra.app.logic.IntentType
import android.speech.tts.TextToSpeech
import java.util.Locale
import com.zyra.app.logic.algorithms.Levenshtein
import com.zyra.app.data.ContactRepository
import android.media.AudioManager
import android.view.KeyEvent
import android.Manifest
import android.hardware.camera2.CameraManager
import android.provider.Settings
import android.os.PowerManager
import android.content.pm.PackageManager
import com.zyra.app.logic.ZyraState

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceAssistantService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var commandManager: CommandManager
    private lateinit var nlp: NaturalLanguageProcessor
    private lateinit var brain: OfflineBrain
    private lateinit var contactRepository: ContactRepository
    private var tts: TextToSpeech? = null
    private var isListening = false
    private var isTtsReady = false
    private lateinit var audioManager: AudioManager
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var overlayManager: com.zyra.app.ui.OverlayManager

    // Connection Watchdog
    private val connectionHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val connectionTimeoutRunnable = Runnable {
        Log.e("ZyraService", "ElevenLabs Connection Timed Out")
        elevenLabsManager.stopConversation()
        speak("I am unable to connect to the cloud. Switching to local mode.")
        isActiveSession = false
        broadcastState(ZyraState.IDLE)
        startListening()
    }

    // ElevenLabs Integration
    private val elevenLabsManager = com.zyra.app.logic.ElevenLabsConversationManager
    private val ELEVENLABS_AGENT_ID = "agent_5401kg271w7hemrvsb9dkje7rkwv" // Correct Agent ID provided by user


    companion object {
        const val BROADCAST_STATE = "com.zyra.app.STATE_UPDATE"
        const val EXTRA_STATE = "state"
    }

    private lateinit var brainRepository: com.zyra.app.data.brain.BrainRepository
    private lateinit var brainExecutor: com.zyra.app.logic.brain.BrainExecutor
    private var cachedBrainTasks: List<com.zyra.app.data.brain.BrainTask> = emptyList()
    
    // APP CONTEXT AWARENESS
    private var currentOpenPackage: String? = null
    private var lastMusicAppPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        commandManager = CommandManager(this)
        nlp = NaturalLanguageProcessor(this)
        brain = OfflineBrain()
        contactRepository = ContactRepository(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this, this)
        overlayManager = com.zyra.app.ui.OverlayManager(this)

        // Initialize Brain
        val app = applicationContext as com.zyra.app.ZyraApp
        brainRepository = app.brainRepository
        brainExecutor = com.zyra.app.logic.brain.BrainExecutor(this)
        
        // Listen to Brain Tasks
        CoroutineScope(Dispatchers.IO).launch {
            brainRepository.allTasks.collect { tasks ->
                cachedBrainTasks = tasks
                Log.d("ZyraService", "Brain Tasks Updated: ${tasks.size}")
            }
        }

        startForegroundService()
        initSpeechRecognizer()
        startListening() // Explicit start on launch

        // Initialize Memory
        com.zyra.app.data.MemoryModule.init(this)

        // Acquire WakeLock for Extreme Persistence
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Zyra::ExtremeCpuLock")
        wakeLock?.acquire() // Indefinite acquisition, released in onDestroy
        
        // Register PhoneStateListener to auto-detect call end
        // Register PhoneStateListener to auto-detect call end
        try {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Modern API (Android 12+)
                    telephonyManager.registerTelephonyCallback(
                        mainExecutor,
                        object : android.telephony.TelephonyCallback(), android.telephony.TelephonyCallback.CallStateListener {
                            override fun onCallStateChanged(state: Int) {
                                handleCallStateChange(state, null)
                            }
                        }
                    )
                } else {
                    // Legacy API
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(object : android.telephony.PhoneStateListener() {
                        @Suppress("DEPRECATION") // Deprecated in Java
                        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                            handleCallStateChange(state, phoneNumber)
                        }
                    }, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
                }
            } else {
                 Log.w("ZyraService", "READ_PHONE_STATE permission missing. Auto-cleanup of call state disabled.")
            }
        } catch (e: Exception) {
            Log.e("ZyraService", "Failed to register PhoneStateListener", e)
        }

        // Initialize ElevenLabs Manager
        elevenLabsManager.init(this)
        elevenLabsManager.setListener(object : com.zyra.app.logic.ElevenLabsConversationManager.ConversationEventListener {
            override fun onConversationStarted() {
                connectionHandler.removeCallbacks(connectionTimeoutRunnable) // Cancel watchdog
                Log.d("ZyraService", "ElevenLabs Conversation Started. Pausing local listener.")
                isListening = false
                speechRecognizer.stopListening()
                broadcastState(ZyraState.LISTENING) // Update UI to show active listening
            }

            override fun onConversationStopped() {
                android.os.Handler(mainLooper).post {
                    Log.d("ZyraService", "ElevenLabs Conversation Stopped. Resuming local listener.")
                    isActiveSession = false
                    startListening() // Resume local wake word listening
                    broadcastState(ZyraState.IDLE)
                }
            }

            override fun onError(message: String) {
                android.os.Handler(mainLooper).post {
                    connectionHandler.removeCallbacks(connectionTimeoutRunnable) // Cancel watchdog
                    Log.e("ZyraService", "ElevenLabs Error: $message")
                    if (message.contains("unauthorized") || message.contains("401")) {
                        speak("My authentication failed. Please check my settings.")
                    } else {
                        speak("I lost the connection. $message")
                    }
                    // Call onConversationStopped explicitly on main thread logic if needed, 
                    // or just copy the logic to avoid double-posting
                    Log.d("ZyraService", "Resume local listener after error.")
                    isActiveSession = false
                    startListening()
                    broadcastState(ZyraState.IDLE)
                }
            }
        })
    }

    private var isExplicitlyStopped = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.zyra.app.ACTION_STOP_SERVICE") {
            Log.d("ZyraService", "Service explicitly stopped by user. Shutting down.")
            isExplicitlyStopped = true
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Ensure service stays alive - Refresh Foreground Notification
        startForegroundService()
        
        if (intent?.action == "com.zyra.app.ACTION_CUT_CALL") {
            cutCall()
            return START_STICKY
        }
        
        if (intent?.action == "com.zyra.app.ACTION_WAKE") {
            Log.d("ZyraService", "Manual Wake Triggered")
            isActiveSession = true
            resetSessionTimeout()
            
            // Show Overlay
             if (Settings.canDrawOverlays(this)) {
                 overlayManager.show()
                 overlayManager.updateState(ZyraState.LISTENING)
             }
             
             // Start ElevenLabs

             startElevenLabsSession()
             return START_STICKY
        }

        if (!isListening) {
             startListening()
        }
        return START_STICKY
    }
    private fun broadcastState(state: ZyraState) {
        // 1. Update Overlay
        if (state == ZyraState.IDLE) {
             // Keep overlay hidden to prevent blocking touch
             overlayManager.hide()
        } else if (state == ZyraState.LISTENING && !isActiveSession) {
             // Passive listening (Waiting for Wake Word) -> Keep Hidden to prevent blocking touch
             overlayManager.hide()
        } else if (state == ZyraState.PROCESSING && !isActiveSession) {
             // Processing... Keep visible if desired, or hide.
             // User requested "remove animation model". Let's hide it until active.
             overlayManager.hide()
        } else {
             // Active Interaction (Speaking, Processing (Active), or Active Listening)
             if (Settings.canDrawOverlays(this)) {
                 overlayManager.show()
                 overlayManager.updateState(state)
             }
        }
        
        // Special Case: If CALLING, ensure overlay is shown even if it was hidden
        if (state == ZyraState.CALLING && Settings.canDrawOverlays(this)) {
            overlayManager.show()
            overlayManager.updateState(state)
        }
        
        // 2. Broadcast to App
        val intent = Intent(BROADCAST_STATE).apply {
            putExtra(EXTRA_STATE, state.name)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
    private fun startForegroundService() {
        val channelId = "VoiceAssistantChannel"
        // Min SDK is 26, so this check is redundant but harmless. Keeping for clarity or removing if requested.
        val channel = NotificationChannel(
            channelId, "Zyra Voice Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

        // Stop Intent
        val stopIntent = Intent(this, VoiceAssistantService::class.java).apply {
            action = "com.zyra.app.ACTION_STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Zyra")
            .setContentText("Listening for commands...")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_launcher, "Stop", stopPendingIntent) // TODO: Add a proper 'stop' icon if available
            .build()

        // Android 14 requirements: "microphone" type requires RECORD_AUDIO permission at runtime.
        // It also requires start from "while-in-use" state unless exempt.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // PRIMARY: Try to start with MICROPHONE capability (best for Assistant)
                val hasMicPermission = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (hasMicPermission) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    } else {
                        startForeground(1, notification) 
                    }
                } else {
                    throw SecurityException("Mic permission missing")
                }
            } catch (e: Exception) {
                Log.w("ZyraService", "Failed to start as Microphone FGS: ${e.message}. Attempting fallback.")
                try {
                     // FALLBACK: Use Location + Media Playback (TTS) to keep service alive.
                     // This prevents "ForegroundServiceDidNotStartInTimeException" crash.
                     var fallbackType = 0
                     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                         fallbackType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or 
                                      android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                         startForeground(1, notification, fallbackType)
                     } else {
                         startForeground(1, notification)
                     }
                } catch (e2: Exception) {
                    Log.e("ZyraService", "Critical: Failed to start FGS fallback.", e2)
                    stopSelf()
                }
            }
        } else {
            startForeground(1, notification)
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer.setRecognitionListener(this)
            // Do NOT start listening here automatically. Caller must invoke startListening().
        } else {
            Log.e("ZyraService", "Speech Recognition NOT Available!")
            speak("I cannot hear you. Speech recognition is missing.")
        }
    }

    private fun startListening() {
        if (!isListening) {
             // Noise Cancellation / Environment Setup
            try {
                 // AUDIO FOCUS MANAGEMENT
                 // Only request exclusive focus if we are in an ACTIVE session (User is talking to us).
                 // If we are just passively listening for "Zyra", DO NOT steal focus from music.
                 val shouldRequestFocus = isActiveSession
                 
                 // If music is playing, we NEVER want to steal focus for passive listening.
                 // We rely on the microphone mixing.
                 
                 if (shouldRequestFocus) {
                     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                         val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                             .setAudioAttributes(android.media.AudioAttributes.Builder()
                                 .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                 .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                 .build())
                             .setAcceptsDelayedFocusGain(false)
                             .build()
                         audioManager.requestAudioFocus(focusRequest)
                     } else {
                         @Suppress("DEPRECATION")
                         audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                     }
                 } else {
                     // Ensure we have abandoned focus if we are entering passive mode
                     try { audioManager.abandonAudioFocus(null) } catch (_: Exception) {}
                 }
            } catch (_: Exception) { Log.e("ZyraService", "Focus error") }
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                // Use FREE_FORM for better noise tolerance and natural speech in traffic
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // Switched to US English per user request/issue
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) 
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                
                // Noise / Detection enhancements
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            }
            try {
                Log.d("ZyraService", "Starting SpeechRecognizer...")
                
                if (!audioManager.isMusicActive) {
                    silenceBeep() 
                }

                speechRecognizer.startListening(intent)
                
                if (!audioManager.isMusicActive) {
                    restoreSound()
                }
                
                isListening = true
                broadcastState(ZyraState.LISTENING)
                
                Log.d("ZyraService", "Listening started")
            } catch (e: Exception) {
                Log.e("ZyraService", "Error starting listener: ${e.message}")
                isListening = false
                // Do NOT go idle here if active
                if (!isActiveSession) broadcastState(ZyraState.IDLE)
                
                // Retry after a delay to avoid crash loops
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!isListening) startListening()
                }, 1000)
            }
        }
    }
    
    // ...



    // Duplicate onError removed. Keeping onResults below.


    override fun onError(error: Int) {
        isListening = false
        // Release focus immediately on error
        try { audioManager.abandonAudioFocus(null) } catch (_: Exception) {}
        
        Log.e("ZyraService", "Speech Error: $error")
        
        // Handle specific errors
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            consecutiveErrorCount++
            
            // EXPONENTIAL BACKOFF
            // 1st error: 500ms
            // 2nd error: 2000ms
            // 3rd error: 5000ms
            // 4th error: 10000ms
            // Max: 30000ms
            val backoff = when (consecutiveErrorCount) {
                1 -> 500L
                2 -> 2000L
                3 -> 5000L
                4 -> 10000L
                else -> 30000L
            }
            
            Log.d("ZyraService", "Speech Error: $error. Count: $consecutiveErrorCount. Delay: $backoff")
            
            // Only restart if we are in "Active Session" or if we want continuous listening.
            // But for "Passive listening" (Hotword), we should be gentle.
            // If the user isn't talking to us (No Match), and we are just waiting for "Zyra", 
            // the backoff helps battery and audio focus.
            
            restartListening(backoff)
            
            // If not in active session, ensure we broadcast IDLE so UI doesn't stick
            if (!isActiveSession) broadcastState(ZyraState.IDLE)
            
        } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            // Wait a bit longer if busy
            restartListening(2000)
        } else if (error == 11 || error == SpeechRecognizer.ERROR_SERVER) { 
             // Error 11 = ERROR_SERVER_DISCONNECTED (Android 12+)
             // This often happens in a loop if Google App is unstable or network drops.
             // We MUST backoff to prevent drain.
             consecutiveErrorCount++
             val backoff = when (consecutiveErrorCount) {
                1 -> 1000L
                2 -> 3000L
                else -> 10000L // Cool down significantly
             }
             Log.w("ZyraService", "Server Disconnected (Error $error). Backing off for ${backoff}ms")
             restartListening(backoff)
             
             if (!isActiveSession) broadcastState(ZyraState.IDLE)
        } else {
            // Other errors (Network, Audio, Client)
            // Be careful not to loop on fatal errors
            if (error == SpeechRecognizer.ERROR_CLIENT) {
                // Often happens if we start too fast.
                restartListening(2000)
            } else {
                 restartListening(1000)
            }
            if (!isActiveSession) broadcastState(ZyraState.IDLE)
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        // Abandon focus immediately on results to allow media to resume full volume if ducked
        try { audioManager.abandonAudioFocus(null) } catch (_: Exception) {}
        
        broadcastState(ZyraState.PROCESSING)
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        
        if (!matches.isNullOrEmpty()) {
            consecutiveErrorCount = 0 // Success! Reset error count.
            processCommands(matches)
        } else {
             restartListening() 
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        broadcastState(ZyraState.LISTENING)
    }
    override fun onRmsChanged(rmsdB: Float) {
        // Optional visualization hook
    }
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches.isNullOrEmpty()) return

        val text = matches[0].lowercase()
        // Quick check for wake word - STRICT LIST
         val wakeWordVariations = listOf(
            "zyra", "zara", "zira", "cyra", "xyla", "viera"
            // REMOVED: "sara", "sarah", "saira", "sherra", etc.
        )
        
        val isWakeWord = wakeWordVariations.any { text.contains(it) }
        // STRICTER: Only activate if we are somewhat confident it's not a lyrics fragment
        // If music is playing, we might want to wait for FINAL results, but partial is faster.
        // Let's rely on the strict list.
        if (isWakeWord && !isActiveSession) {
             Log.d("ZyraService", "Wake Word detected via Partial Results: $text")
             isActiveSession = true // Activate session!
             broadcastState(ZyraState.LISTENING) // Show overlay
             
             // Request Focus only NOW that we caught the wake word
             try {
                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                     val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                         .setAudioAttributes(android.media.AudioAttributes.Builder()
                             .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                             .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                             .build())
                         .setAcceptsDelayedFocusGain(false)
                         .build()
                     audioManager.requestAudioFocus(focusRequest)
                 }
             } catch (_: Exception) {}
        }
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}

    
    private fun restartListening(delayMs: Long = 500) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isListening) {
                // Ensure we don't start if service is killed
                try {
                     startListening()
                } catch (e: Exception) {
                     Log.e("ZyraService", "Failed to restart listening", e)
                }
            }
        }, delayMs)
    }

    // Cleaned up legacy debris


    // Duplicates removed

    override fun onBeginningOfSpeech() {
        Log.d("ZyraService", "Speech started")
        // INTELLIGENT DUCKING:
        // When speech ACTUALLY starts (not just listening silence), we request "Transient May Duck" focus.
        // This lowers the music volume so the recognizer can hear the rest of the sentence clearly.
        // It's much less intrusive than stopping music entirely.
        try {
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                 val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                     .setAudioAttributes(android.media.AudioAttributes.Builder()
                         .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                         .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                         .build())
                     .setAcceptsDelayedFocusGain(false)
                     .build()
                 audioManager.requestAudioFocus(focusRequest)
             } else {
                 @Suppress("DEPRECATION")
                 audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
             }
        } catch (_: Exception) { }
    }
    // Duplicates removed

    override fun onEndOfSpeech() {
        Log.d("ZyraService", "Speech ended")
    }

    private var activeSessionExpiryHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var consecutiveErrorCount = 0 // Track loops for backoff
    private var isActiveSession = false
    private var lastInteractionTime = 0L
    private val sessionTimeoutMs = 30000L // Increased to 30 seconds for better flow

    // BEEP KILLER
    private var initialStreamVolume = 0
    
    private fun silenceBeep() {
        try {
            // Mute Notification/System/Music streams briefly to kill the "Bing" sound
            // We save the volume to restore it later
            // Note: Modifications to STREAM_MUSIC might affect music playback, so act carefully.
            // Usually the beep is on NOTIFICATION or SYSTEM.
            if (!isListening) { // Only do this if we are starting a NEW listen
                 initialStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                 audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                 audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0)
                 // audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0) // Risky if playing music
            }
        } catch (_: Exception) {}
    }

    private fun restoreSound() {
         // Restore after ~500ms (enough time for the beep to pass)
         android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
             try {
                 audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                 audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_UNMUTE, 0)
             } catch (_: Exception) {}
         }, 500)
    }

    private fun processCommands(matches: List<String>) {
        consecutiveErrorCount = 0 // Reset on success
        Log.d("ZyraService", "Heard: $matches")
        
        val bestMatch = matches.firstOrNull()?.lowercase() ?: run { startListening(); return }
        
        // --- Wake Word Logic ---
        // --- Wake Word Logic ---
        @Suppress("SpellCheckingInspection")
        val wakeWordVariations = listOf(
            "zyra", "zara", "zira", "cyra", "xyla", "viera" 
            // REMOVED: "sara", "sarah" (too close to 'saira'), "sierra", "chehra", "ishara"
            // We want STRICTER waking to avoid music triggers.
        )
        // Strict match: must contain one of the above explicitly
        val hasWakeWord = wakeWordVariations.any { bestMatch.contains(it) }
        
        if (!isActiveSession && !hasWakeWord) {
            // IGNORE input if not active and didn't say name
            startListening()
            return
        }
        
        if (hasWakeWord) {
            isActiveSession = true
            resetSessionTimeout()
            
            // IMMEDIATE VISUAL FEEDBACK: Force overlay to show
            if (Settings.canDrawOverlays(this)) {
                overlayManager.show()
                overlayManager.updateState(ZyraState.LISTENING) // Show listening state immediately
            }

            // TRIGGER ELEVENLABS SESSION
            // Instead of local processing, we start the full-duplex session
            Log.d("ZyraService", "Wake Word Detected -> Starting ElevenLabs Session")
            startElevenLabsSession()
            
            return // Stop local processing, let ElevenLabs handle the rest
        } else {
             // In Active Session, refresh timeout
             resetSessionTimeout()
         }

        // 0. GLOBAL DISMISSAL (Top Priority - Bypass everything else)
        // Handles "Zyra leave", "Zyra dismiss", "Zyra goodbye", etc.
        val lowerText = bestMatch.lowercase()
        // Check exact matches or strong keywords for dismissal
        if (lowerText.contains("goodbye") || lowerText.contains("go to sleep") || 
            lowerText == "leave" || lowerText == "dismiss" || lowerText == "rest" ||
            lowerText == "stop listening" || lowerText == "shut down") {
             
             speak(com.zyra.app.logic.PersonaManager.getDismissalResponse())
             isActiveSession = false
             broadcastState(ZyraState.IDLE)
             return
        }

        // --- Intent Processing ---
        
        // -1. CHECK CONVERSATION STATE
        if (conversationManager.isBusy()) {
            handleConversationState(bestMatch)
            return
        }

        // 1. Check Custom Commands (Learning Memory)
        val resolvedText = checkCustomCommands(bestMatch)
        if (resolvedText == "brain_executed") return // Task triggered, stop.
        
        val intent = nlp.process(resolvedText) // Use full match to preserve context
        
        // Execute Actions
        when (intent.type) {
            IntentType.CAPTURE_PHOTO -> handleCapturePhoto()
            IntentType.PLAY_MUSIC -> handlePlayMusic(intent.extra)
            IntentType.LEARN_COMMAND -> handleLearnCommand(intent.extra)
            IntentType.END_SESSION -> handleEndSession()
            IntentType.CLOSE_APP -> handleCloseApp()
            IntentType.MEDIA_CONTROL -> {
                speak(brain.getResponse(intent, this))
                handleMediaCommand(intent.extra)
            }
            IntentType.CUT_CALL -> {
                // speak(brain.getResponse(intent, this)) // Handled in cutCall
                cutCall()
            }
            IntentType.ANSWER_CALL -> {
                // speak("Answering call.") // Handled in handleAnswerCall
                handleAnswerCall(intent.extra)
            }
            IntentType.VEHICLE_INFO -> {
                speak("Scanning screen for vehicle details...")
                // Trigger Accessibility Service Scan
                // ZyraAccessibilityService.requestScreenScan(this) // This line is commented out in the original, keeping it that way.
            }
            IntentType.CALL -> {
                 if (intent.extra != null) {
                     speak(brain.getResponse(intent, this))
                     resolveAndCall(intent.extra)
                 } else {
                     speak("Who do you want to call?")
                 }
            }
            IntentType.OPEN_APP -> {
                 if (intent.extra != null) {
                    openApp(intent.extra)
                 } else {
                    speak("Which app should I open?")
                 }
            }
            IntentType.IN_APP_ACTION -> {

            }
            IntentType.DEVICE_CONTROL -> {
                speak(brain.getResponse(intent, this))
                handleDeviceControl(intent.extra)
            }
            IntentType.SEND_MESSAGE -> {
                if (intent.extra != null) {
                    val parts = intent.extra.split("|")
                    if (parts.size >= 2) {
                        handleSendMessage(parts[0], parts[1])
                    } else {
                        speak("Who should I message and what should I say?")
                    }
                }
            }
            IntentType.UNLOCK_DEVICE -> {
                handleUnlockDevice()
            }
            IntentType.LOCK_DEVICE -> {
                handleLockDevice()
            }
            IntentType.SET_PIN -> {
                val pin = intent.extra
                if (pin != null) {
                    val prefs = getSharedPreferences("com.zyra.app.prefs", MODE_PRIVATE)
                    prefs.edit().putString("device_pin", pin).apply()
                    speak("I've remembered your PIN. I can now unlock your phone for you.")
                }
            }
            IntentType.ALARM -> {
                handleAlarm(intent.extra)
            }
            IntentType.TIMER -> {
                handleTimer(intent.extra)
            }
            IntentType.UNIT_CONVERSION -> {
                 speak(brain.getResponse(intent, this))
            }
            IntentType.VOLUME_CONTROL -> {
                if (intent.extra == "max") {
                    speak(com.zyra.app.logic.PersonaManager.getVolumeMaxResponse())
                } else {
                    speak(com.zyra.app.logic.PersonaManager.getConfirmation("adjusting volume"))
                }
                handleVolumeControl(intent.extra)
            }
            IntentType.SCREEN_CONTEXT -> {
                 if (com.zyra.app.service.ZyraAccessibilityService.instance != null) {
                     val prompt = intent.extra ?: "Describe this screen"
                     speak("One moment, looking at your screen...")
                     com.zyra.app.service.ZyraAccessibilityService.requestScreenAnalysis(prompt)
                 } else {
                     speak("I need Accessibility permissions to see your screen. Please enable me in settings.")
                     // Optional: Open settings intent
                 }
            }
            else -> {
                // --- GEMINI ONLINE INTERCEPT ---
                if (intent.extra?.startsWith("GEMINI:") == true) {
                    val prompt = intent.extra.removePrefix("GEMINI:")
                    
                    speak("Thinking...") 
                    
                    CoroutineScope(Dispatchers.IO).launch {
                        var response = com.zyra.app.logic.GeminiModule.generateResponse(prompt)
                        
                        // Check for MEMORY tag
                        if (response.contains("||MEMORY:")) {
                            try {
                                val tagStart = response.indexOf("||MEMORY:")
                                val tagEnd = response.indexOf("||", tagStart + 2)
                                if (tagEnd != -1) {
                                    val tagContent = response.substring(tagStart + 9, tagEnd) // "key|value"
                                    val parts = tagContent.split("|")
                                    if (parts.size >= 2) {
                                        val key = parts[0].trim()
                                        val value = parts[1].trim()
                                        com.zyra.app.data.MemoryModule.learnFact(key, value, this@VoiceAssistantService)
                                        Log.d("ZyraService", "Learned fact via Gemini: $key = $value")
                                    }
                                    // Remove tag from response
                                    val firstPart = response.substring(0, tagStart)
                                    val secondPart = response.substring(tagEnd + 2)
                                    response = (firstPart + secondPart).trim()
                                }
                            } catch (e: Exception) { Log.e("ZyraService", "Error parsing memory", e) }
                        }
                        
                        // Check for TRAIN tag
                        if (response.contains("||TRAIN:")) {
                            try {
                                val tagStart = response.indexOf("||TRAIN:")
                                val tagEnd = response.indexOf("||", tagStart + 2)
                                if (tagEnd != -1) {
                                    val tagContent = response.substring(tagStart + 8, tagEnd) // "CATEGORY"
                                    val type = IntentType.valueOf(tagContent)
                                    nlp.train(prompt, type)
                                    Log.d("ZyraService", "Auto-Trained Offline Brain: '$prompt' -> $type")
                                    
                                    // Remove tag
                                    val firstPart = response.substring(0, tagStart)
                                    val secondPart = response.substring(tagEnd + 2)
                                    response = (firstPart + secondPart).trim()
                                    
                                    // EXECUTE IMMEDIATELY
                                    withContext(Dispatchers.Main) {
                                        speak(response) // Speak first
                                        when (type) {
                                            IntentType.UNLOCK_DEVICE -> handleUnlockDevice()
                                            IntentType.DEVICE_CONTROL -> handleDeviceControl(prompt) // Infer command
                                            IntentType.CALL -> speak("I can't make calls from online mode yet.")
                                            else -> {}
                                        }
                                    }
                                    return@launch // Stop further processing since we handled it
                                }
                            } catch (e: Exception) { Log.e("ZyraService", "Error parsing training tag", e) }
                        }
                        
                        withContext(Dispatchers.Main) {
                            speak(response)
                        }
                    }
                    return
                }

                val response = brain.getResponse(intent, this)
                
                // INTELLIGENT FALLBACK: Check if it's a question before prompting search
                val text = intent.extra?.lowercase() ?: ""
                
                // CONTEXTUAL SEARCH INTERCEPT
                // If user says "Search for X" and an app is open, try searching inside that app first
                if (currentOpenPackage != null && (intent.type == IntentType.UNKNOWN || intent.type == IntentType.KNOWLEDGE_QUERY)) {
                     if (text.startsWith("search") || text.startsWith("find") || text.startsWith("look for")) {
                          speak("Searching in app context.")
                          val query = text.replace("search", "")
                                          .replace("find", "")
                                          .replace("look for", "")
                                          .replace("for", "").trim()
                          performInAppTask("search $query")
                          return
                     }
                }

                val isQuestion = text.startsWith("who") || text.startsWith("what") || 
                                 text.startsWith("where") || text.startsWith("when") || 
                                 text.startsWith("why") || text.startsWith("how") || 
                                 text.startsWith("define") || text.contains(" meaning")

                if ((intent.type == IntentType.UNKNOWN || response.lowercase().contains("i'm not sure")) && isQuestion) {
                    speak("I'm not sure about that. Should I search Google for '${intent.extra ?: "it"}'?")
                    conversationManager.startWebSearchConfirmation(intent.extra ?: "")
                    resetSessionTimeout()
                } else {
                    speak(response)
                    
                    // Trigger Sync on "Go Online"
                    if (response.contains("I am now online")) {
                         CoroutineScope(Dispatchers.IO).launch {
                             com.zyra.app.logic.BrainSyncManager.sync(this@VoiceAssistantService)
                         }
                    }
                }
            }
        }
    }

    private fun checkCustomCommands(text: String): String {
        // 0. Check BRAIN TASKS (Priority)
        // Fuzzy Match + Synonym Normalization
        val normalizedInput = com.zyra.app.logic.brain.SynonymNormalizer.normalize(text)
        
        // Find best match among Brain Tasks
        val bestTask = cachedBrainTasks.firstOrNull { task ->
            if (task.triggerPhrase.isEmpty()) return@firstOrNull false
            val normalizedTrigger = com.zyra.app.logic.brain.SynonymNormalizer.normalize(task.triggerPhrase)
            
            // 1. Exact Match (Normalized)
            if (normalizedInput == normalizedTrigger) return@firstOrNull true
            
            // 2. Fuzzy Match
            val dist = Levenshtein.calculate(normalizedInput, normalizedTrigger)
            val threshold = (normalizedTrigger.length * 0.4).toInt().coerceAtLeast(1) // 40% error tolerance
            dist <= threshold
        }

        if (bestTask != null) {
            Log.d("ZyraService", "Matched Brain Task: ${bestTask.name}")
            speak("Executing ${bestTask.name}")
            // Execute in background
            CoroutineScope(Dispatchers.IO).launch {
                val steps = brainRepository.getStepsForTask(bestTask.id)
                brainExecutor.executeTask(bestTask, steps)
            }
            return "brain_executed" // Special flag
        }

        // 1. Check MemoryModule for text aliases
        val memoryAlias = com.zyra.app.data.MemoryModule.checkForAlias(text)
        if (memoryAlias != text) return memoryAlias

        // 2. Check CommandManager for call shortcuts
        val commandTarget = commandManager.getCommandTarget(text)
        if (commandTarget != null) {
            return "call ${commandTarget.second}"
        }

        return text
    }

    private fun openApp(spokenPayload: String) {
        val parts = spokenPayload.split("|")
        val spokenAppName = parts[0].trim()
        val taskAction = if (parts.size > 1) parts[1].trim() else null

        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolvedInfos = pm.queryIntentActivities(mainIntent, 0)
            
            val cleanQuery = spokenAppName.lowercase().trim().replace(" app", "")
            Log.d("ZyraService", "Smart Launch: Query='$cleanQuery', Scanned=${resolvedInfos.size} apps")

            val candidates = resolvedInfos.map { info ->
                val label = info.loadLabel(pm).toString()
                val packageName = info.activityInfo.packageName
                val cleanLabel = label.lowercase()
                
                // Calculate Similarity Score (Reverse of Distance)
                val dist = com.zyra.app.logic.algorithms.Levenshtein.calculate(cleanQuery, cleanLabel)
                val maxLength = maxOf(cleanQuery.length, cleanLabel.length)
                val score = if (maxLength == 0) 0 else ((1.0 - dist.toDouble() / maxLength) * 100).toInt()
                
                Candidate(label, packageName, score)
            }.filter { it.score > 40 }

            val sortedCandidates = candidates.sortedByDescending { it.score }
            
            withContext(Dispatchers.Main) {
                if (sortedCandidates.isEmpty()) {
                    speak("I couldn't find $spokenAppName on your phone. Should I search for it on the Play Store?")
                    conversationManager.startPlayStoreSearchConfirmation(spokenAppName)
                    resetSessionTimeout()
                } else {
                    val bestCandidate = sortedCandidates[0]
                    
                    // Ambiguity Check
                    val ambiguousCandidates = sortedCandidates.take(2)
                    if (ambiguousCandidates.size == 2) {
                         val first = ambiguousCandidates[0]
                         val second = ambiguousCandidates[1]
                         
                         if (first.label == second.label || (first.score - second.score < 10 && first.score > 50)) {
                              conversationManager.startAppClarification(
                                  ambiguousCandidates.map { com.zyra.app.logic.AppCandidate(it.label, it.packageName, it.score) },
                                  taskAction
                              )
                              
                               var prompt = "I found multiple versions of ${first.label}. "
                               val isFirstClone = first.packageName.contains("dual") || first.packageName.contains("clone") || first.packageName.contains("work")
                               val isSecondClone = second.packageName.contains("dual") || second.packageName.contains("clone") || second.packageName.contains("work")
                               
                               if (isFirstClone || isSecondClone) {
                                   prompt += "Do you want the Main one or the Clone?"
                               } else {
                                   prompt += "Do you want the first one or the second one?"
                               }
                               speak(prompt)
                               resetSessionTimeout()
                               return@withContext
                         }
                    }

                    launchAppPackage(bestCandidate.packageName, bestCandidate.label, taskAction)
                    
                    // CONTEXT AWARENESS
                    currentOpenPackage = bestCandidate.packageName
                }
            }
        }
    }

    private val priorityPackages = setOf(
        "com.whatsapp",
        "com.instagram.android",
        "com.facebook.katana",
        "com.google.android.youtube",
        "com.snapchat.android",
        "com.twitter.android",
        "com.linkedin.android",
        "com.zhiliaoapp.musically"
    )


    private var isTrustedSession = false

    private fun handleUnlockDevice() {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        
        if (!keyguardManager.isKeyguardLocked) {
             speak(com.zyra.app.logic.PersonaManager.getAlreadyUnlockedResponse())
             return
        }
        
        // Fast Track if already trusted recently
        if (isTrustedSession) {
            speak(com.zyra.app.logic.PersonaManager.getConfirmation("accessing secure protocols"))
            performPhysicalUnlock()
            return
        }
        
        // Start Security Flow
        speak("Identify yourself.")
        conversationManager.startUserIdentification()
        resetSessionTimeout()
    }

    private fun handleLockDevice() {
        val service = com.zyra.app.service.ZyraAccessibilityService.instance
        if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
             speak("Locking phone.")
             com.zyra.app.service.ZyraAccessibilityService.performAction("global:lock")
             isActiveSession = false // End session as phone is locking
             broadcastState(ZyraState.IDLE)
        } else {
             speak("I need Accessibility Service enabled to lock the phone.")
             val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
             startActivity(intent)
        }
    }

    private fun performPhysicalUnlock() {
        // 1. Swipe Up (Always needed to show PIN pad)
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val x = width / 2f
        val yStart = height * 0.85f
        val yEnd = height * 0.4f
        
        if (ZyraAccessibilityService.instance == null) {
            speak("I need Accessibility Service enabled to unlock the phone.")
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            startActivity(intent)
            return
        }

        // 0. WAKE UP SCREEN
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "Zyra:UnlockWakeLock"
            )
            wakeLock.acquire(3000) // Hold for 3s to ensure swipe checks register
        }

        ZyraAccessibilityService.performAction("gesture:$x,$yStart,$x,$yEnd,300")
        
        // 2. Enter PIN if available
        val prefs = getSharedPreferences("com.zyra.app.prefs", MODE_PRIVATE)
        var pin = prefs.getString("device_pin", null)
        
        // Fallback to Memory Module
        if (pin == null) {
            val memory = com.zyra.app.data.MemoryModule.getAllFacts()
            pin = memory["password"] ?: memory["phone password"] ?: memory["pin"] ?: memory["code"] ?: memory["passcode"]
        }
        
        if (pin != null) {
            // Wait for swipe animation to finish (e.g. 500ms)
            android.os.Handler(mainLooper).postDelayed({
                enterPin(pin!!)
            }, 600)
        } else {
            speak("I don't know your PIN. Please tell me 'My password is...' so I can learn it.")
        }
    }
    
    private fun enterPin(pin: String) {
        CoroutineScope(Dispatchers.Main).launch {
            pin.forEachIndexed { index, char ->
                // Delay between clicks to prevent missing inputs
                // 300ms per digit is safe
                android.os.Handler(mainLooper).postDelayed({
                    ZyraAccessibilityService.performAction("click:$char")
                }, index * 350L)
            }
            
            // Click Enter/OK after done
            // Some phones auto-unlock, some need Enter.
            // Try clicking "Enter", "OK", "Done", "Go" just in case after the last digit
            android.os.Handler(mainLooper).postDelayed({
                ZyraAccessibilityService.performAction("click:Enter")
                ZyraAccessibilityService.performAction("click:OK")
            }, pin.length * 350L + 200)
        }
    }

    private fun handleSendMessage(name: String, message: String) {
        try {
            // Sanitize number (WhatsApp needs country code usually, but let's try raw or basic sanitization)
            // Ideally, the user's contacts should have +91 etc.
            // If contact.phoneNumber doesn't have country code, this might fail or open a "Invite" page.
            // We'll assume typical contact format.
            // TODO: Resolve contact number from name
            var phoneNumber = name 
            var cleanNumber = phoneNumber.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
            if (!cleanNumber.startsWith("+")) {
                // Heuristic: If 10 digits, assume local (e.g. India +91) or just send as is (WhatsApp sometimes resolves)
                // Better approach: Use the Intent.ACTION_SEND for generic reliability if number format is unknown,
                // BUT ACTION_SEND requires user to pick contact.
                // ACTION_VIEW with "wa.me" requires valid number.
                
                // Let's assume user likely has 10 digit numbers stored, maybe prefix default code if desired?
                // For now, send as is.
                Log.w("ZyraService", "Number might need country code: $cleanNumber")
            }
            
            // Use generic ACTION_VIEW with API
            val url = "https://api.whatsapp.com/send?phone=$cleanNumber&text=${java.net.URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = url.toUri()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setPackage("com.whatsapp") // Target WhatsApp directly
            
            startActivity(intent)
            speak(com.zyra.app.logic.PersonaManager.getConfirmation("sending packet"))
            
            // 3. Automate "Send" Click
            android.os.Handler(mainLooper).postDelayed({
                // Try multiple times in case of load lag
                ZyraAccessibilityService.performAction("click:send")
            }, 3000)
            
            android.os.Handler(mainLooper).postDelayed({
                // Retry
                ZyraAccessibilityService.performAction("click:send")
            }, 5000)

        } catch (e: Exception) {
            Log.e("ZyraService", "WhatsApp error", e)
            speak("I couldn't open WhatsApp.")
        }
    }
    // --- Conversation Management ---
    private val conversationManager = com.zyra.app.logic.ConversationManager()
    
    // --- Handlers ---
    private fun handleConversationState(text: String) {
        val lowerText = text.lowercase()
        
        // GLOBAL CANCEL / EXIT STRATEGY
        // Handling diverse user expressions for "Stop"
        if (lowerText.contains("cancel") || lowerText.contains("stop") || 
            lowerText.contains("never mind") || lowerText.contains("forget it") ||
            lowerText.contains("leave it") || lowerText.contains("shut up") ||
            lowerText.contains("exit") || lowerText.contains("quit") ||
            lowerText.contains("don't") || lowerText == "no" || lowerText == "leave") {
            
            conversationManager.clear()
            speak("Cancelled.")
            return
        }
        
        when (val state = conversationManager.currentState) {
            is com.zyra.app.logic.InteractionState.ClarifyContact -> {
                 // Check for "None" specifically here before generic handling
                 if (lowerText.contains("none") || lowerText.contains("neither") || lowerText.contains("no one")) {
                     conversationManager.clear()
                     speak("Okay, cancelling call.")
                     return
                 }
                 handleContactClarification(state.candidates, text)
            }
            is com.zyra.app.logic.InteractionState.ClarifyApp -> {
                 handleAppClarification(state.candidates, state.action, text)
            }
            is com.zyra.app.logic.InteractionState.ConfirmWebSearch -> {
                 if (lowerText.contains("yes") || lowerText.contains("sure") || lowerText.contains("okay") || lowerText.contains("search")) {
                     val intent = Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=${state.query}".toUri()).apply {
                         flags = Intent.FLAG_ACTIVITY_NEW_TASK
                     }
                     startActivity(intent)
                     conversationManager.clear()
                 } else {
                     speak("Okay, I won't search.")
                     conversationManager.clear()
                 }
            }
            is com.zyra.app.logic.InteractionState.ConfirmPlayStoreSearch -> {
                 if (lowerText.contains("yes") || lowerText.contains("sure") || lowerText.contains("okay")) {
                     val intent = Intent(Intent.ACTION_VIEW, "market://search?q=${state.appName}".toUri()).apply {
                         flags = Intent.FLAG_ACTIVITY_NEW_TASK
                     }
                     try {
                         startActivity(intent)
                     } catch (e: Exception) {
                         speak("I can't open the Play Store.")
                     }
                     conversationManager.clear()
                 } else {
                     speak("Okay.")
                     conversationManager.clear()
                 }
            }

            is com.zyra.app.logic.InteractionState.ConfirmCall -> {
                 // "Did you mean Kiran?" -> "Yes"
                 if (lowerText.contains("yes") || lowerText.contains("correct") || lowerText.contains("sure") || lowerText.contains("yap") || lowerText.contains("right") || lowerText.contains("call") || lowerText.contains("connect")) {
                     speak("Calling ${state.name}.")
                     makeCall(state.number)
                     conversationManager.clear()
                 } else if (lowerText.contains("no") || lowerText.contains("cancel") || lowerText.contains("wrong") || lowerText.contains("don't")) {
                     speak("Okay, cancelled.")
                     conversationManager.clear()
                 } else {
                     // Unclear input - Do not cancel, just re-ask
                     speak("I didn't understand. Please say Yes to call ${state.name}, or No to cancel.")
                     resetSessionTimeout() // Keep listening
                 }
            }
            is com.zyra.app.logic.InteractionState.IdentifyUser -> {
                // "Who is this?" -> Expect "Shijin" or "Zyco"
                if (lowerText.contains("shijin") || lowerText.contains("zyco") || lowerText.contains("psycho") || lowerText.contains("saiko")) {
                    speak("Security voice activation required.")
                    conversationManager.startAuthCodeVerification()
                    resetSessionTimeout()
                } else {
                    speak("Not authorized.")
                    conversationManager.clear()
                }
            }
            is com.zyra.app.logic.InteractionState.VerifyAuthCode -> {
                // "Security voice activation required." -> Expect "Z Series"
                if (lowerText.contains("z series") || lowerText.contains("zseries") || lowerText.contains("zed series") || lowerText.contains("zee series")) {
                    speak("Access Granted. Welcome to Z's World.")
                    conversationManager.clear()
                    
                    isTrustedSession = true // Enable fast-track for future unlocks
                    
                    // Proceed to Unlock
                    performPhysicalUnlock()
                } else {
                    speak("Access Declined.")
                    conversationManager.clear()
                }
            }
            is com.zyra.app.logic.InteractionState.IncomingCall -> {
                 if (lowerText.contains("answer") || lowerText.contains("yes") || lowerText.contains("pick")) {
                     speak("Answering.")
                     handleAnswerCall(null)
                     conversationManager.clear()
                 } else if (lowerText.contains("decline") || lowerText.contains("no") || lowerText.contains("cut")) {
                     speak("Declining.")
                     // End call (Telecom)
                     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                         if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                             val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                             try { telecomManager.endCall() } catch(_: Exception) {}
                         }
                     }
                     conversationManager.clear()
                 } else {
                     speak("Say Answer or Decline.")
                 }
            }
            else -> { conversationManager.clear() }


        }
    }

    private fun handleContactClarification(candidates: List<com.zyra.app.data.Contact>, text: String) {
         // (Existing logic adapted to parameters)
        val lowerText = text.lowercase()
        
        if (lowerText.contains("first") || lowerText.contains("1st") || lowerText.contains("one")) {
            makeCall(candidates[0].phoneNumber)
            conversationManager.clear()
            return
        }
        if (candidates.size >= 2 && (lowerText.contains("second") || lowerText.contains("2nd") || lowerText.contains("two"))) {
            makeCall(candidates[1].phoneNumber)
            conversationManager.clear()
            return
        }
        if (candidates.size >= 3 && (lowerText.contains("third") || lowerText.contains("3rd") || lowerText.contains("three"))) {
             makeCall(candidates[2].phoneNumber)
             conversationManager.clear()
             return
        }
        
        val candidateNames = candidates.map { it.name }
        val bestRefinedMatch = Levenshtein.findBestMatch(text, candidateNames)
        
        if (bestRefinedMatch != null) {
            val contact = candidates.find { it.name == bestRefinedMatch }
            if (contact != null) {
                 makeCall(contact.phoneNumber)
                 conversationManager.clear()
                 return
            }
        }
        
        speak("I didn't catch that. Please say the name again or say 'cancel'.")
        resetSessionTimeout()
    }
    
    private fun handleAppClarification(candidates: List<com.zyra.app.logic.AppCandidate>, action: String?, text: String) {
        val lowerText = text.lowercase()
        
        val candidate1 = candidates[0]
        val candidate2 = candidates[1]
        
        val isC1Clone = candidate1.packageName.contains("dual") || candidate1.packageName.contains("clone") || candidate1.packageName.contains("parallel")
        val isC2Clone = candidate2.packageName.contains("dual") || candidate2.packageName.contains("clone") || candidate2.packageName.contains("parallel")
        val priorityPackages = setOf(
            "com.whatsapp", "com.instagram.android", "com.facebook.katana", "com.google.android.youtube",
            "com.snapchat.android", "com.twitter.android", "com.zhiliaoapp.musically"
        )
        val isC1Main = priorityPackages.contains(candidate1.packageName)
        val isC2Main = priorityPackages.contains(candidate2.packageName)
        
        if (lowerText.contains("main") || lowerText.contains("original") || lowerText.contains("normal") || lowerText.contains("regular")) {
             if (isC1Main || !isC1Clone) { 
                 launchAppPackage(candidate1.packageName, candidate1.label, action); conversationManager.clear(); return 
             }
             if (isC2Main || !isC2Clone) { 
                 launchAppPackage(candidate2.packageName, candidate2.label, action); conversationManager.clear(); return 
             }
        }
        
        if (lowerText.contains("clone") || lowerText.contains("dual") || lowerText.contains("second") || lowerText.contains("work")) {
             if (isC1Clone) { 
                 launchAppPackage(candidate1.packageName, candidate1.label, action); conversationManager.clear(); return 
             }
             if (isC2Clone) { 
                 launchAppPackage(candidate2.packageName, candidate2.label, action); conversationManager.clear(); return 
             }
             launchAppPackage(candidate2.packageName, candidate2.label, action); conversationManager.clear(); return 
        }
        
        if (lowerText.contains("first") || lowerText.contains("one") || lowerText.contains("1st")) {
            launchAppPackage(candidate1.packageName, candidate1.label, action)
            conversationManager.clear()
            return
        }
        
        speak("Please say 'Main' or 'Clone'.")
        resetSessionTimeout()
    }
    
    private fun launchAppPackage(packageName: String, label: String, taskAction: String?) {
        val pm = packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            speak(com.zyra.app.logic.PersonaManager.getConfirmation("Launch sequence for $label initiated"))
            try {
                startActivity(launchIntent)
                if (taskAction != null) {
                     android.os.Handler(mainLooper).postDelayed({
                         performInAppTask(taskAction)
                     }, 4500)
                }
            } catch (_: Exception) {
                speak(com.zyra.app.logic.PersonaManager.getFailureResponse("launch $label"))
            }
            resetSessionTimeout()
        } else {
            speak(com.zyra.app.logic.PersonaManager.getFailureResponse("find $label"))
        }
    }

    private fun performInAppTask(task: String) {
        // "search for songs", "type hello", "click settings"
        val lowerTask = task.lowercase()
        speak("Executing: $task")
        
        if (lowerTask.startsWith("search")) {
             // "search for xyz"
             val query = lowerTask.replace("search", "").replace("for", "").trim()
             // 1. Click Search Icon
             ZyraAccessibilityService.performAction("click:search")
             // 2. Type Query (Delayed)
             android.os.Handler(mainLooper).postDelayed({
                  ZyraAccessibilityService.performAction("type:$query")
             }, 1500)
        } else if (lowerTask.startsWith("type") || lowerTask.startsWith("write") || lowerTask.startsWith("send")) {
             // "type hello world"
             val text = lowerTask.replace("type", "")
                                 .replace("write", "")
                                 .replace("send", "")
                                 .replace("message", "").trim()
             ZyraAccessibilityService.performAction("type:$text")
        } else {
             // "click profile", "tap next"
             val target = lowerTask.replace("click", "")
                                   .replace("tap", "")
                                   .replace("press", "").trim()
             ZyraAccessibilityService.performAction("click:$target")
        }
    }

    
    private var isCallInProgress = false

    private fun resetSessionTimeout() {
        activeSessionExpiryHandler.removeCallbacksAndMessages(null)
        activeSessionExpiryHandler.postDelayed({
            isActiveSession = false
            if (isCallInProgress) {
                // If call is active, DO NOT go IDLE. Keep overlay.
                broadcastState(ZyraState.CALLING)
            } else {
                broadcastState(ZyraState.IDLE)
            }
        }, sessionTimeoutMs)
    }
    
    private fun handleMediaCommand(command: String?) {
        val keyEvent = when (command) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "fast_forward" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            "rewind" -> KeyEvent.KEYCODE_MEDIA_REWIND
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyEvent))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyEvent))
        
        // Restart listening after a short delay so we don't pick up the song immediately
        android.os.Handler(mainLooper).postDelayed({ startListening() }, 2000)
    }
    
    private fun handleVolumeControl(action: String?) {
        val stream = AudioManager.STREAM_MUSIC
        val flags = AudioManager.FLAG_SHOW_UI
        
        when (action) {
            "up" -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, flags)
            }
            "down" -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, flags)
            }
            "mute" -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, flags)
            }
            "max" -> {
                val max = audioManager.getStreamMaxVolume(stream)
                audioManager.setStreamVolume(stream, max, flags)
            }
        }
        
        // Resume listening
        android.os.Handler(mainLooper).postDelayed({ startListening() }, 1000)
    }

    private fun handleDeviceControl(command: String?) {
        if (command == null) return
        
        when {
            command.startsWith("flashlight") -> {
                val turnOn = command.contains("on")
                val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
                try {
                    val cameraId = cameraManager.cameraIdList[0] // Usually back camera
                    cameraManager.setTorchMode(cameraId, turnOn)
                } catch (e: Exception) {
                    Log.e("ZyraService", "Flashlight error", e)
                    speak("I cannot access the flashlight.")
                }
            }
            command.startsWith("wifi") -> {
                 // Direct toggle is restricted, accept opening settings
                 val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                     flags = Intent.FLAG_ACTIVITY_NEW_TASK
                 }
                 startActivity(intent)
            }
            command.startsWith("bluetooth") -> {
                 val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                     flags = Intent.FLAG_ACTIVITY_NEW_TASK
                 }
                 startActivity(intent)
            }
        }
        
        // Resume listening after a short delay
        android.os.Handler(mainLooper).postDelayed({ startListening() }, 1500)
    }
    
    // Duplicate cutCall removed

    
    // --- Clarification Context ---
    private var pendingContactCandidates: List<com.zyra.app.data.Contact>? = null

    private fun resolveAndCall(spokenName: String) {
          if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
              speak("I need access to your contacts to make a call. Please allow it in settings.")
              return
          }

          CoroutineScope(Dispatchers.IO).launch {
             try {
                 val contacts = contactRepository.getContacts()
                 if (contacts.isEmpty()) {
                     withContext(Dispatchers.Main) {
                         speak("I found no contacts on your phone.")
                     }
                     return@launch
                 }

                 val names = contacts.map { it.name }
                 // Use findAllMatches to get ALL potential candidates
                 var matchedNames = Levenshtein.findAllMatches(spokenName, names)
                 
                 // --- PHONETIC FALLBACK (Indian English / Kerala Accent) ---
                 if (matchedNames.isEmpty()) {
                     Log.d("ZyraService", "No direct matches for '$spokenName'. Trying phonetic fallback...")
                     val normalizedSpoken = normalizePhonetics(spokenName)
                     
                     // Filter contacts where normalized version is similar
                     val phoneticMatches = contacts.filter { contact ->
                         val normalizedContact = normalizePhonetics(contact.name)
                         // Check for substring or high similarity on normalized strings
                         normalizedContact.contains(normalizedSpoken) || 
                         com.zyra.app.logic.algorithms.Levenshtein.calculateSimilarity(normalizedSpoken, normalizedContact) > 85
                     }.map { it.name }
                     
                     if (phoneticMatches.isNotEmpty()) {
                         Log.d("ZyraService", "Phonetic matches found: $phoneticMatches")
                         matchedNames = phoneticMatches
                     }
                 }
                 
                 withContext(Dispatchers.Main) {
                     if (matchedNames.isEmpty()) {
                         // INTELLIGENT FALLBACK
                         speak("I couldn't find $spokenName in your contacts. Should I search Google for them?")
                         conversationManager.startWebSearchConfirmation(spokenName)
                         resetSessionTimeout()
                     } else if (matchedNames.size == 1) {
                         // SINGLE MATCH
                         val matchName = matchedNames[0]
                         val contact = contacts.find { it.name == matchName }
                         
                         if (contact != null) {
                             // SAFETY CHECK: Calculate Similarity
                             // If user said "Kiran" and match is "Kiran", score is 100 -> Auto Call
                             // If user said "Kiran" and match is "Kiran Kumar", score is < 100 -> Ask Confirmation
                             
                             val score = com.zyra.app.logic.algorithms.Levenshtein.calculateSimilarity(spokenName.lowercase(), matchName.lowercase())
                             
                             if (score > 90 || matchName.lowercase() == spokenName.lowercase()) {
                                 // HIGH CONFIDENCE
                                 makeCall(contact.phoneNumber)
                             } else {
                                 // LOW CONFIDENCE (Safety Latch)
                                 speak("Did you mean $matchName?")
                                 conversationManager.startCallConfirmation(matchName, contact.phoneNumber)
                                 resetSessionTimeout()
                             }
                         }
                     } else {
                         // MULTIPLE MATCHES - CLARIFY
                         // Filter contacts to only those in matchedNames



                         val candidates = contacts.filter { it.name in matchedNames }
                             .distinctBy { it.name } // Avoid duplicate names if multiple numbers (handled later?)
                             .take(3) // Limit to top 3 to avoid long reading

                         conversationManager.startContactClarification(candidates)
                         
                         val namesString = candidates.joinToString(" or ") { it.name }
                         speak("I found multiple contacts: $namesString. Which one?")
                         resetSessionTimeout() // Wait for user reply

                     }
                 }
             } catch (e: Exception) {
                 Log.e("ZyraService", "Error resolving contact", e)
                 withContext(Dispatchers.Main) {
                     speak("I encountered an error trying to access your contacts.")
                 }
             }
         }
    }
    
    // --- Removed old inline clarification methods (now in handleConversationState) ---

    private fun handleCapturePhoto() {
        speak(com.zyra.app.logic.PersonaManager.getConfirmation("Opening camera optics"))
        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
            resetSessionTimeout() // Keep listening
            
            // Attempt auto-click after 3.5s
            android.os.Handler(mainLooper).postDelayed({
                speak("Capturing image.")
                
                // METHOD 1: VOLUME BUTTON HACK
                // Triggers shutter on almost all Android phones
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                
                // METHOD 2: Gesture Backup (Center Bottom)
                ZyraAccessibilityService.performAction("gesture:540,2050")
                
            }, 3500)
        } catch (_: Exception) {
            speak(com.zyra.app.logic.PersonaManager.getFailureResponse("open the camera"))
        }
    }

    private fun handleAnswerCall(mode: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                try {
                    telecomManager.acceptRingingCall()
                    speak(com.zyra.app.logic.PersonaManager.getCallAnswerResponse())
                    
                    if (mode == "speaker") {
                         // Wait for call to connect
                         android.os.Handler(mainLooper).postDelayed({
                             val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                             audioManager.mode = AudioManager.MODE_IN_CALL
                             audioManager.isSpeakerphoneOn = true
                             speak(com.zyra.app.logic.PersonaManager.getConfirmation("activating loud speaker"))
                         }, 1000)
                    }
                    
                } catch (e: Exception) {
                    Log.e("ZyraService", "Error answering call", e)
                    speak("I tried to answer, but the system blocked it.")
                }
            } else {
                speak("I need permission to answer calls, Sir.")
            }
        } else {
             // Fallback
             val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
             val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
             audioManager.dispatchMediaKeyEvent(event)
             
             android.os.Handler(mainLooper).postDelayed({
                 audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK))
             }, 100)
             speak("Attempting to pick up.")
        }
    }

    private fun cutCall() {
        android.os.Handler(mainLooper).post {
             android.widget.Toast.makeText(this, "Ending Call...", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // Reset state tracker
        isCallInProgress = false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
             if (checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                 val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                 try {
                     telecomManager.endCall()
                     speak(com.zyra.app.logic.PersonaManager.getCallCutResponse())
                 } catch (e: Exception) {
                     Log.e("ZyraService", "Error ending call via TelecomManager", e)
                     fallbackCutCall()
                 }
             } else {
                  speak("I need the 'Answer Phone Calls' permission to do that. Please check my settings.")
                  fallbackCutCall()
             }
        } else {
             fallbackCutCall()
        }
        // Force update UI
        broadcastState(ZyraState.IDLE)
    }
    


    private fun handleCloseApp() {
        speak("Minimizing application.")
        ZyraAccessibilityService.performAction("global:home")
        currentOpenPackage = null
    }

    private fun handleEndSession() {
        speak(com.zyra.app.logic.PersonaManager.getDismissalResponse())
        isActiveSession = false
        broadcastState(ZyraState.IDLE) // Hide overlay
        startListening() // Return to passive wake-word mode
    }

    private fun handlePlayMusic(payload: String?) {
        // Payload formats: "spotify|believer", "believer", "spotify", null
        var targetApp = "spotify"
        var query = ""
        
        if (payload != null) {
            val parts = payload.split("|")
            if (payload.contains("spotify")) {
                targetApp = "spotify"
                if (parts.size > 1) {
                     query = parts[1]
                }
            } else {
                targetApp = "generic"
                query = payload
            }
        }
        
        // Refined Logic
        if (payload != null && !payload.contains("spotify")) {
             query = payload
             targetApp = "generic"
        } else if (payload != null && payload.startsWith("spotify|")) {
             query = payload.substring(8)
        }

        // --- TRACKING ---
        if (targetApp.contains("spotify")) {
            lastMusicAppPackage = "com.spotify.music"
        } else if (targetApp != "generic") {
            // If user said "Play music on YouTube Music", we might have extraction logic elsewhere, 
            // but for now we only support spotify explicitly in NLP. 
            // If we add support for others, update this.
        }

        // Logic check: "Resume" vs "Search"
        val isGenericResume = query.isEmpty() || query == "music" || query == "resume"
        
        if (isGenericResume) {
             speak("Resuming music.")
             
             // INTELLIGENT RESUME:
             // 1. If we know the last app, launch it first to ensure media buttons go to it
             if (lastMusicAppPackage != null) {
                 val launch = packageManager.getLaunchIntentForPackage(lastMusicAppPackage!!)
                 if (launch != null) {
                     try {
                         startActivity(launch)
                         // Wait for app to be ready before clicking Play
                         android.os.Handler(mainLooper).postDelayed({
                             val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
                             val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
                             audioManager.dispatchMediaKeyEvent(eventDown)
                             audioManager.dispatchMediaKeyEvent(eventUp)
                         }, 2500) // 2.5s wait for app launch
                     } catch (_: Exception) { /* ignore */ }
                 }
             } else {
                 // Classic Global Resume
                 val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
                 val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
                 audioManager.dispatchMediaKeyEvent(eventDown)
                 audioManager.dispatchMediaKeyEvent(eventUp)
             }

             // End session
             isActiveSession = false
             broadcastState(ZyraState.IDLE)
             android.os.Handler(mainLooper).postDelayed({ startListening() }, 3000)
             return
        }

        speak("Playing $query")
        
        // ... (Search Logic) ...
        if (targetApp.contains("spotify") || (targetApp == "context_in_app" && currentOpenPackage?.contains("spotify") == true)) {
             // Use SEARCH INTENT with package set to Spotify (Best for auto-play)
             val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                 // Open Spotify
                 setPackage("com.spotify.music")
                 putExtra(android.app.SearchManager.QUERY, query) // Use specific song
                 putExtra(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
                 addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
             }
             try {
                 startActivity(intent)
             } catch (_: Exception) {
                 // Fallback to Deep Link
                 val deepLinkUri = "spotify:search:$query"
                 val deepLink = Intent(Intent.ACTION_VIEW, deepLinkUri.toUri()).apply {
                      flags = Intent.FLAG_ACTIVITY_NEW_TASK
                 }
                 try {
                     startActivity(deepLink)
                 } catch (_: Exception) {
                     speak("Spotify is not installed.")
                 }
             }
        } else {
            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(android.app.SearchManager.QUERY, query)
                putExtra(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                speak("I couldn't find a music app to play.")
            }
        }
        
        // AUTOMATION: Click Play if needed
        isActiveSession = false
        broadcastState(ZyraState.IDLE)
        
        android.os.Handler(mainLooper).postDelayed({ 
             val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
             val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
             audioManager.dispatchMediaKeyEvent(eventDown)
             audioManager.dispatchMediaKeyEvent(eventUp)
             
             if (targetApp.contains("spotify")) {
                  com.zyra.app.service.ZyraAccessibilityService.performAction("click:Shuffle Play")
                  android.os.Handler(mainLooper).postDelayed({
                      com.zyra.app.service.ZyraAccessibilityService.performAction("click:Play")
                  }, 500)
             }
             
             startListening() 
        }, 4000)
    }
    
    private fun handleLearnCommand(text: String?) {
        if (text == null) return
        
        // Format 1: "alias:learn command [ALIAS] as [COMMAND]" (from NLP)
        // Format 2: "fact:remember that [KEY] is [VALUE]"
        
        if (text.startsWith("alias:")) {
             val instruction = text.substring(6) // remove "alias:"
             // Regex to find "learn command (.+) as (.+)"
             val pattern = java.util.regex.Pattern.compile("learn command (.+) as (.+)")
             val matcher = pattern.matcher(instruction)
             if (matcher.find()) {
                  val alias = matcher.group(1)?.trim() ?: ""
                  val command = matcher.group(2)?.trim() ?: ""
                 com.zyra.app.data.MemoryModule.learnAlias(alias, command, this)
                 speak("Understood. I have learned that '$alias' means '$command'.")
             } else {
                 speak("Please say 'Learn command [Example] as [Action]'.")
             }
        } else if (text.startsWith("fact:")) {
             val instruction = text.substring(5) // remove "fact:"
             // Regex to find "remember that (.+) is (.+)"
             // Also "save that..."
             val pattern = java.util.regex.Pattern.compile("(remember|save) that (.+) is (.+)")
             val matcher = pattern.matcher(instruction)
             if (matcher.find()) {
                 val key = matcher.group(2)?.trim() ?: ""
                 val value = matcher.group(3)?.trim() ?: ""
                 com.zyra.app.data.MemoryModule.learnFact(key, value, this)
                 speak("Saved to memory. $key is $value.")
             } else {
                 speak("Please say 'Remember that [Thing] is [Value]'.")
             }
        } else {
            speak("I am ready to learn. Tell me what to remember.")
        }
    }



    private fun speak(text: String) {
        broadcastState(ZyraState.SPEAKING)
        if (isTtsReady && tts != null) {
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    // Back to listening
                    android.os.Handler(mainLooper).post { 
                        // CRITICAL FIX: After speaking, if we are in an active session,
                        // do NOT go IDLE (which hides overlay). Go to LISTENING.
                        if (isActiveSession) {
                            // Reset timeout so user has time to reply AFTER she finishes speaking
                            resetSessionTimeout()
                            broadcastState(ZyraState.LISTENING)
                        } else {
                            broadcastState(ZyraState.IDLE)
                        }
                        startListening() 
                    }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                     android.os.Handler(mainLooper).post { startListening() }
                }

                @Deprecated("Deprecated in Java")
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) {
                     // For legacy support if needed, but API 21+ prefers the Int version
                     // We just delegate or do same logic
                     onError(utteranceId, -1)
                }
            })
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ZyraUtterance")
        } else {
            // If TTS fails, go back to listening
            startListening()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // JARVIS CONFIGURATION
            // 1. Accent: British English (Paul Bettany style)
            val result = tts?.setLanguage(Locale.UK)
            
            // 2. Pitch: Lower/Deeper (Calm, robotic but warm)
            // 1.0 is normal. 0.7-0.8 is deeper.
            tts?.setPitch(0.75f)
            
            // 3. Rate: Slightly slower, deliberate, authoritative
            tts?.setSpeechRate(0.9f)
            
            // 4. Voice Selection (Try to find a Male voice if possible)
            // This is heuristic as engines vary (Google TTS vs Samsung TTS)
            try {
                val voices = tts?.voices
                if (voices != null) {
                    val targetVoice = voices.find { 
                        it.locale == Locale.UK && 
                        (it.name.contains("male", ignoreCase = true) || it.name.contains("en-gb-x-rjs-network")) 
                    }
                    if (targetVoice != null) {
                        tts?.voice = targetVoice
                    }
                }
            } catch (e: Exception) {
                Log.e("ZyraService", "Could not set specific voice", e)
            }

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("ZyraService", "TTS Language not supported")
                // Fallback to US if UK is missing
                tts?.setLanguage(Locale.US)
            } else {
                isTtsReady = true
            }
        } else {
            Log.e("ZyraService", "TTS Initialization failed")
        }
    }

    private fun makeCall(phoneNumber: String) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            speak("I do not have permission to make calls. Please allow it in the app settings.")
            return
        }

        // Show CALLING state immediately and track it
        isCallInProgress = true
        broadcastState(ZyraState.CALLING)

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = "tel:$phoneNumber".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
            
            // Keep the overlay visible during the call (Infinite Persistence)
            // DO NOT HIDE. The "End Call" button needs to be available.
            // We rely on "cutCall()" or manual end to clear the state.
            broadcastState(ZyraState.CALLING) 
            
            // Optionally: Auto-hide after a VERY long time (e.g. 1 hour) just in case
            android.os.Handler(mainLooper).postDelayed({
                 // Only hide if still calling (safety check)
                 if (isActiveSession) { // Logic check: isActiveSession might be false, but state is CALLING
                     // No-op: Let the user end it.
                     Log.d("ZyraService", "Call timeout check: Session still active, not hiding overlay.")
                 }
            }, 3600000) 
            
        } catch (e: SecurityException) {
            Log.e("ZyraService", "Permission denied for calling", e)
            speak("I cannot make the call due to permission restrictions.")
        }
        // Don't restart listening immediately if we are leaving the app to call
        // BUT keep session active so if they return quickly or it fails, we are ready
        resetSessionTimeout()
    }
    

    private fun handleCallStateChange(state: Int, phoneNumber: String?) {
        when (state) {
            android.telephony.TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call Answered or Outgoing Call Started
                Log.d("ZyraService", "Call Active (OFFHOOK). Zyra attempting to stay alive...")
                isCallInProgress = true
                
                // EXPERIMENTAL: DO NOT STOP LISTENING
                // We want Zyra to be able to "hear" even during a call.
                // Note: Android Hardware might block the mic, causing ERROR_BUSY or ERROR_AUDIO.
                // We rely on onError() to retry.
                
                // Ensure we are listening if not already
                if (!isListening) {
                     startListening()
                }
                
                broadcastState(ZyraState.CALLING)
            }
            
            android.telephony.TelephonyManager.CALL_STATE_RINGING -> {
                // SIRI MODEL: Announce Caller and Listen for "Answer" or "Decline"
                val caller = if (!phoneNumber.isNullOrEmpty()) {
                    // TODO: Resolve contact name
                    "Incoming call" 
                } else "Incoming call"
                
                // Speak over ringtone
                conversationManager.startIncomingCall() // Sets volume
                speak("$caller. Say Answer or Decline.")
            }
            
            android.telephony.TelephonyManager.CALL_STATE_IDLE -> {
                Log.d("ZyraService", "Call Ended (IDLE). Resuming Assistant...")
                isCallInProgress = false
                broadcastState(ZyraState.IDLE)
                
                // Resume listening if we were "Always On"
                // We use a small delay to let the audio system settle after the call
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!isListening) startListening()
                }, 1000)
            }
        }
    }
    
    // Recognition Listener Methods
    // Duplicates removed

    


    private fun fallbackCutCall() {
         Log.d("ZyraService", "Attempting Fallback Cut Call...")
         
         // 1. Try Accessibility Service (Global Action Back can sometimes end calls or minimize them)
         // Not a guaranteed 'End Call', but better than nothing if Telecom fails.
         if (com.zyra.app.service.ZyraAccessibilityService.instance != null) {
             com.zyra.app.service.ZyraAccessibilityService.performAction("global:back")
         }

         // 2. Headset Hook (Legacy Method - flaky on newer Androids)
         val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
         val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
         audioManager.dispatchMediaKeyEvent(event)
         
         android.os.Handler(mainLooper).postDelayed({
             val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
             audioManager.dispatchMediaKeyEvent(eventUp)
         }, 100)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    // CRITICAL: This is called when user "Swipes" the app away.
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("ZyraService", "App Swiped Away (Task Removed) - Restarting Listener...")
        
        // 1. FORCE Restart Listener
        // The OS might seek to kill us, so we must be aggressive.
        // If we were "listening", that binder is likely dead or about to die.
        try {
            // Force reset state
            isListening = false 
            
            // Kill existing recognizer to be safe
            try { speechRecognizer.destroy() } catch (_: Exception) {}
            
            // Re-init completely
            initSpeechRecognizer() 
            
            // DELAYED START: Give the OS 1s to settle the 'swipe' animation/kill
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                 startListening()
            }, 1000)
            
        } catch (_: Exception) {
            // If destroy fails, just try starting manually
            try { startListening() } catch(_: Exception) {}
        }

        // 2. Trigger Resurrection (Just in case OS kills the process soon)
        val restartIntent = Intent(this, BootReceiver::class.java)
        restartIntent.action = "com.zyra.app.RESTART"
        sendBroadcast(restartIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            speechRecognizer.destroy()
        } catch (_: Exception) { }
        
        tts?.stop()
        tts?.shutdown()
        wakeLock?.release()
        
        // "Immortal" Logic: If destroyed (e.g. by system swipe), immediately ask BootReceiver to restart us.
        Log.e("ZyraService", "Service Destroyed! Triggering Resurrection...")
        val restartIntent = Intent(this, BootReceiver::class.java)
        restartIntent.action = "com.zyra.app.RESTART"
        sendBroadcast(restartIntent)
    }

    // --- HELPER: Phonetic Normalization for Indian/Kerala Accents ---
    private fun normalizePhonetics(input: String): String {
        var text = input.lowercase().trim()
        
        // 1. Consonant Swaps (Common in Indian accents)
        text = text.replace("p", "b")  // Pro -> Bro
        text = text.replace("f", "ph") // Fan -> Phan
        text = text.replace("v", "w")  // Vine -> Wine
        text = text.replace("z", "s")  // Zyra -> Syra
        text = text.replace("sh", "s") // Shee -> See
        text = text.replace("th", "t") // They -> Tey
        
        // 2. Vowel Reduction (Simplify to skeleton)
        // Keep it simple for now, maybe map all vowels to 'a' or remove them? 
        // Removing vowels is too aggressive ("Sai" -> "S", "See" -> "S").
        // Let's just standardise double vowels.
        text = text.replace("ee", "i")
        text = text.replace("oo", "u")
        
        return text
    }





    // --- OFFLINE FEATURE HANDLERS ---
    


    private fun handleAlarm(timeText: String?) {
        val time = timeText ?: return
        // Ideally we parse "6:30 am", but for now pass standard Intent
        // Native Alarm Intent behaves weirdly with raw text, usually needs Hour/Minute
        // However, `ACTION_SET_ALARM` doesn't take raw text easily.
        // We will try a smart parsing or just launch the clock app.
        
        // Simple Parse: look for digits
        try {
            val parts = time.replace(":", " ").replace("am", "").replace("pm", "").trim().split(" ")
            var hour = parts.firstOrNull { it.toIntOrNull() != null }?.toInt() ?: 7
            val minute = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
            
            if (time.lowercase().contains("pm") && hour < 12) hour += 12
            if (time.lowercase().contains("am") && hour == 12) hour = 0

            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Zyra Alarm")
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            speak("Setting alarm for $hour:${if(minute<10) "0$minute" else minute}")
        } catch (e: Exception) {
             speak("Opening clock.")
             val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                 addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)   
             }
             startActivity(intent)
        }
    }

    private fun handleTimer(durationText: String?) {
        // "5 minutes" -> Parse 5
        val text = durationText ?: return
        val numberRegex = Regex("\\d+")
        val match = numberRegex.find(text)
        val value = match?.value?.toIntOrNull() ?: 5 // Default 5
        
        // Assume minutes if not specified, unless contains "second"
        val lengthSeconds = if (text.contains("second")) value else value * 60
        
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(android.provider.AlarmClock.EXTRA_LENGTH, lengthSeconds)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Zyra Timer")
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            speak("Timer set for $value ${if(text.contains("second")) "seconds" else "minutes"}.")
        } catch (e: Exception) {
            speak("I cannot set a timer.")
        }
    }

    // End of Handlers

    private fun startElevenLabsSession() {
        // Start Watchdog (12 seconds timeout)
        connectionHandler.removeCallbacks(connectionTimeoutRunnable)
        connectionHandler.postDelayed(connectionTimeoutRunnable, 12000) // 12s to allow for cold start / slow network
        
        elevenLabsManager.startConversation(this, ELEVENLABS_AGENT_ID)
    }
}

data class Candidate(val label: String, val packageName: String, val score: Int)
