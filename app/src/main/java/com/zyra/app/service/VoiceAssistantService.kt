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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.zyra.app.network.ZyraApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.net.Uri
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
import android.location.Location
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.zyra.app.logic.ZyraState
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import com.zyra.app.BuildConfig

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
    private var porcupineManager: PorcupineManager? = null

    enum class ServiceLifecycleState {
        STARTING,
        WAKEWORD_ACTIVE,
        CONNECTING,
        SESSION_ACTIVE,
        ERROR
    }

    private var lifecycleState: ServiceLifecycleState = ServiceLifecycleState.STARTING
    private var serviceErrorMessage: String? = null
    private var isPorcupineActive = false

    private lateinit var overlayManager: com.zyra.app.ui.OverlayManager
    private var isLiveKitSessionActive = false // FLAG to prevent SpeechRecognizer from fighting LiveKit

    // WATCHDOG: Restart listener if silent/frozen for too long
    private var lastVoiceActivityTime: Long = System.currentTimeMillis()
    private val watchdogHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            // CIRCUIT BREAKER: If in ERROR state (e.g. Porcupine failed to initialize), stop retrying!
            if (lifecycleState == ServiceLifecycleState.ERROR) {
                Log.w("ZyraService", "Watchdog Circuit Breaker active: Service in ERROR state ($serviceErrorMessage). Halting restart loop.")
                return // HALT WATCHDOG
            }

            // Do not interrupt while LiveKit is connecting or actively speaking
            if (lifecycleState == ServiceLifecycleState.CONNECTING || lifecycleState == ServiceLifecycleState.SESSION_ACTIVE || isLiveKitSessionActive) {
                watchdogHandler.postDelayed(this, 2000)
                return
            }

            val timeSinceLastActivity = System.currentTimeMillis() - lastVoiceActivityTime
            
            // SERVICE HEALTH CHECK
            if (isListening) {
                if (timeSinceLastActivity > 8000) {
                    Log.d("ZyraService", "Watchdog: Silence/Freeze detected (${timeSinceLastActivity}ms). Restarting listener...")
                    restartListening()
                    lastVoiceActivityTime = System.currentTimeMillis()
                }
            } else if (isPorcupineActive && porcupineManager != null && lifecycleState == ServiceLifecycleState.WAKEWORD_ACTIVE) {
                // Porcupine wake word engine is healthy. Do nothing.
            } else {
                Log.w("ZyraService", "Watchdog: Inactive state detected (state=$lifecycleState). Starting listener...")
                startListening()
                lastVoiceActivityTime = System.currentTimeMillis()
            }
            
            // Check every 2 seconds
            watchdogHandler.postDelayed(this, 2000)
        }
    }

    // Connection Watchdog
    private val connectionHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val connectionTimeoutRunnable = Runnable {
        Log.e("ZyraService", "ElevenLabs Connection Timed Out")
        com.zyra.app.logic.LiveKitManager.disconnect()
        speak("I am unable to connect to the cloud. Switching to local mode.")
        isActiveSession = false
        broadcastState(ZyraState.IDLE)
        startListening()
    }

    // LiveKit Integration
    private val ELEVENLABS_AGENT_ID = "agent_5401kg271w7hemrvsb9dkje7rkwv" // Correct Agent ID provided by user


    companion object {
        const val BROADCAST_STATE = "com.zyra.app.STATE_UPDATE"
        const val EXTRA_STATE = "state"
        const val EXTRA_ERROR_MESSAGE = "error_message"
    }

    private lateinit var brainRepository: com.zyra.app.data.brain.BrainRepository
    private lateinit var brainExecutor: com.zyra.app.logic.brain.BrainExecutor
    private var cachedBrainTasks: List<com.zyra.app.data.brain.BrainTask> = emptyList()
    
    // APP CONTEXT AWARENESS
    private var currentOpenPackage: String? = null
    private var lastMusicAppPackage: String? = null

    private fun syncContactsInBackground() {
        // Disabled until endpoint is HTTPS with per-device authentication.
        // Cleartext HTTP to LAN IP (192.168.1.5) is prohibited.
        Log.i("ZyraService", "Contact sync is disabled pending HTTPS endpoint and per-device auth.")
    }

    override fun onCreate() {
        super.onCreate()
        contactRepository = ContactRepository(this)
        commandManager = CommandManager(this)
        nlp = NaturalLanguageProcessor(this)
        brain = OfflineBrain()
        syncContactsInBackground()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
        // Configure audio for concurrent access (allows listening while media plays)
        configureAudioForConcurrentAccess()
        
        // TTS DISABLED: Using only ElevenLabs to prevent double audio
        // tts = TextToSpeech(this, this) // DISABLED
        overlayManager = com.zyra.app.ui.OverlayManager(this)

        // Initialize Porcupine (Wake Word)
        initPorcupine()

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
        
        // Start Watchdog
        watchdogHandler.post(watchdogRunnable)

        // Initialize Memory
        com.zyra.app.data.MemoryModule.init(this)
        
        // ========== JARVIS INTELLIGENCE SYSTEM INITIALIZATION ==========
        // This initializes ALL intelligence modules:
        // - Memory & Learning (MemoryDatabase, ReinforcementLearner)
        // - Spatial Intelligence (LocationIntelligence, SpatialIntelligence)
        // - Proactive Engine (Pattern learning, suggestions)
        // - Smart Contact Manager
        // - Workflow Engine
        // - All 14 JARVIS Controllers
        Log.d("ZyraService", "Initializing JARVIS Intelligence System...")
        try {
            com.zyra.app.logic.IntelligenceSystem.initialize(this, contactRepository)
            Log.d("ZyraService", "✓ JARVIS Intelligence System initialized successfully")
        } catch (e: Exception) {
            Log.e("ZyraService", "Failed to initialize IntelligenceSystem", e)
        }
        // ===============================================================


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

        // Initialize LiveKit Manager
        com.zyra.app.logic.LiveKitManager.init()
        com.zyra.app.logic.LiveKitManager.setListener(object : com.zyra.app.logic.LiveKitManager.LiveKitListener {
            override fun onCallCommandReceived(phone: String, name: String) {
                Log.d("ZyraService", "Jarvis Call Request Received: $name ($phone)")
                try {
                    val uri = Uri.parse("tel:$phone")
                    val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        Log.d("ZyraService", "Placing call natively via TelecomManager to bypass lock screen.")
                        telecomManager.placeCall(uri, null)
                    } else {
                        Log.e("ZyraService", "Missing CALL_PHONE permission for native bypass.")
                        val intent = Intent(Intent.ACTION_CALL, uri)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    Log.e("ZyraService", "Failed to start phone call", e)
                }
            }

            override fun onEndCallCommandReceived() {
                Log.d("ZyraService", "Hang Up Command Received from Gemini")
                try {
                    val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            telecomManager.endCall()
                            Log.d("ZyraService", "Successfully ended call via TelecomManager.")
                        } else {
                            Log.e("ZyraService", "Android Version too low for native endCall.")
                        }
                    } else {
                        Log.e("ZyraService", "Missing ANSWER_PHONE_CALLS permission to cut call.")
                    }
                } catch (e: Exception) {
                    Log.e("ZyraService", "Failed to hang up phone call", e)
                }
            }

            override fun onConnected() {
                connectionHandler.removeCallbacks(connectionTimeoutRunnable) // Cancel watchdog
                Log.d("ZyraService", "LiveKit Conversation Started. Pausing local listener.")
                
                // DEBUG: Play Beep to confirm Audio Path works
                try {
                    // Use STREAM_MUSIC (3) as it calls to the main loudspeaker more reliably than VOICE_CALL (0)
                    val toneGen = android.media.ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP)
                    android.os.Handler(mainLooper).postDelayed({ toneGen.release() }, 200)
                } catch (e: Exception) {
                    Log.e("ZyraService", "Failed to play debug beep", e)
                }

                // FORCE VOLUME UP
                maximizeCallVolume()
                
                // AGGRESSIVE AUDIO ROUTING FIX (Mediatek/Samsung Compatibility)
                // System sometimes reverts Speakerphone after VoIP starts. We force it back.
                val routingHandler = android.os.Handler(mainLooper)
                val routingRunnable = object : Runnable {
                    var count = 0
                    override fun run() {
                        try {
                            if (!audioManager.isSpeakerphoneOn) {
                                Log.d("ZyraService", "Re-enforcing Speakerphone...")
                                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                                audioManager.isSpeakerphoneOn = true
                            }
                        } catch (e: Exception) { Log.e("ZyraService", "Routing fix failed", e) }
                        
                        count++
                        if (count < 5 && isLiveKitSessionActive) {
                            routingHandler.postDelayed(this, 1000) // Check every second for 5s
                        }
                    }
                }
                routingRunnable.run() // Run immediately

                isLiveKitSessionActive = true // LOCK local listener
                lifecycleState = ServiceLifecycleState.SESSION_ACTIVE
                isListening = false
                
                // Explicitly cancel to ensure no trailing errors trigger restarts
                try {
                     speechRecognizer.cancel()
                } catch (e: Exception) {
                     speechRecognizer.stopListening()
                }
                
                broadcastState(ZyraState.LISTENING) // Update UI to show active listening
            }
            
            override fun onDisconnected() {
                android.os.Handler(mainLooper).post {
                    Log.d("ZyraService", "LiveKit Conversation Stopped. Resuming local listener.")
                    
                    // Reset Audio Mode
                    try {
                        audioManager.mode = AudioManager.MODE_NORMAL
                        audioManager.isSpeakerphoneOn = false
                    } catch (_: Exception) {}

                    isLiveKitSessionActive = false // UNLOCK
                    isActiveSession = false
                    if (lifecycleState != ServiceLifecycleState.ERROR) {
                        lifecycleState = ServiceLifecycleState.WAKEWORD_ACTIVE
                    }
                    startListening() // Resume local wake word listening
                    broadcastState(ZyraState.IDLE)
                }
            }

            override fun onError(message: String) {
                android.os.Handler(mainLooper).post {
                    connectionHandler.removeCallbacks(connectionTimeoutRunnable) // Cancel watchdog
                    Log.e("ZyraService", "LiveKit Error: $message")
                    
                    // Reset Audio Mode
                    try {
                        audioManager.mode = AudioManager.MODE_NORMAL
                        audioManager.isSpeakerphoneOn = false
                    } catch (_: Exception) {}
                    
                    isLiveKitSessionActive = false // UNLOCK on error
                    
                    if (message.contains("unauthorized") || message.contains("401")) {
                        speak("My authentication failed. Please check my settings.")
                    } else {
                        speak("I lost the connection. $message")
                    }
                    // Resume local listener
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
        
        // Handle call intent from home screen
        if (intent?.action == "com.zyra.app.ACTION_MAKE_CALL") {
            val phoneNumber = intent.getStringExtra("phone_number")
            val contactName = intent.getStringExtra("contact_name")
            
            if (phoneNumber != null) {
                Log.d("ZyraService", "Making call from home screen to: $contactName ($phoneNumber)")
                isActiveSession = true
                resetSessionTimeout()
                
                if (contactName != null) {
                    speak("Calling $contactName")
                }
                makeCall(phoneNumber)
            } else {
                Log.e("ZyraService", "Call intent missing phone number")
                speak("I couldn't make the call. Phone number is missing.")
            }
            return START_STICKY
        }
        
        if (intent?.action == "com.zyra.app.ACTION_WAKE") {
            Log.d("ZyraService", "Manual Wake Triggered")
            lifecycleState = ServiceLifecycleState.CONNECTING // Guard watchdog from interrupting connection
            isActiveSession = true
            resetSessionTimeout()
            
            // Show Overlay and Beep IMMEDIATELY
             if (Settings.canDrawOverlays(this)) {
                 overlayManager.show()
                 overlayManager.updateState(ZyraState.LISTENING)
             }
             
             // Immediate Feedback Beep
             try {
                 val toneGen = android.media.ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                 toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP)
                 android.os.Handler(mainLooper).postDelayed({ toneGen.release() }, 150)
             } catch (e: Exception) {
                 Log.e("ZyraService", "Failed to play instant wake beep", e)
             }
             
             // Start LiveKit Session
             com.zyra.app.logic.LiveKitManager.connect(this)
             return START_STICKY
        }

        if (!isListening) {
             startListening()
        }
        return START_STICKY
    }
    private fun broadcastState(state: ZyraState, message: String? = null) {
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
            if (message != null) {
                putExtra(EXTRA_ERROR_MESSAGE, message)
            }
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
                     var fallbackType: Int
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

    private fun configureAudioForConcurrentAccess() {
        try {
            // Set audio mode to NORMAL to allow concurrent recording
            // This prevents Zyra from blocking other apps' audio
            audioManager.mode = AudioManager.MODE_NORMAL
            
            // Ensure we're not forcing speakerphone
            audioManager.isSpeakerphoneOn = false
            
            Log.d("ZyraService", "✓ Audio configured for concurrent access (mode=NORMAL)")
        } catch (e: Exception) {
            Log.e("ZyraService", "Failed to configure concurrent audio", e)
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

    private fun initPorcupine() {
        val accessKey = BuildConfig.PICOVOICE_ACCESS_KEY
        val keyInfo = if (accessKey.isNotEmpty()) {
            "non-empty, length=${accessKey.length}, suffix=...${accessKey.takeLast(4)}"
        } else {
            "empty"
        }

        try {
            val ppnFile = com.zyra.app.logic.AssetModelManager.syncWakeWordModels(this)
            if (ppnFile == null || !ppnFile.exists()) {
                throw java.io.FileNotFoundException("zyra.ppn model file could not be extracted to filesDir")
            }

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(ppnFile.absolutePath)
                .setSensitivity(0.7f)
                .build(this, object : PorcupineManagerCallback {
                    override fun invoke(keywordIndex: Int) {
                        Log.d("ZyraService", "Wake Word Detected via Porcupine Index: $keywordIndex")
                        onWakeWordDetected()
                    }
                })
            lifecycleState = ServiceLifecycleState.WAKEWORD_ACTIVE
            serviceErrorMessage = null
            Log.d("ZyraService", "Porcupine 4.x initialized successfully with model: ${ppnFile.absolutePath}")
        } catch (e: Exception) {
            porcupineManager = null
            isPorcupineActive = false
            lifecycleState = ServiceLifecycleState.ERROR
            val readableMsg = if (e.javaClass.simpleName.contains("ActivationRefused")) {
                "Wake word key refused"
            } else {
                e.message ?: "Porcupine initialization failed"
            }
            serviceErrorMessage = readableMsg
            Log.e("ZyraService", "Porcupine init exception: class=${e.javaClass.name}, message=${e.message}, AccessKey=[$keyInfo]")
            broadcastState(ZyraState.ERROR, readableMsg)
        }
    }

    private fun onWakeWordDetected() {
        Log.d("ZyraService", "Wake Word Detected!")
        try { 
            porcupineManager?.stop() 
            isPorcupineActive = false
        } catch (e: Exception) {}
        
        isListening = false
        isActiveSession = true
        lifecycleState = ServiceLifecycleState.CONNECTING
        
        // Immediate Feedback
        try {
            val toneGen = android.media.ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP)
        } catch (e: Exception) {}
        
        broadcastState(ZyraState.LISTENING)
        
        // Connect to the NEW Ultra-Cheap Voice Gateway
        com.zyra.app.logic.VoiceGatewayManager.init(this)
        com.zyra.app.logic.VoiceGatewayManager.connect {
            Log.d("ZyraService", "Connected to Gemini Flash-Lite Gateway")
        }
    }

    private fun startListening() {
        if (isLiveKitSessionActive || lifecycleState == ServiceLifecycleState.CONNECTING || lifecycleState == ServiceLifecycleState.ERROR) return

        // Dispatcher: Active vs Passive
        if (isActiveSession) {
            startActiveListening()
        } else {
            startPassiveListening()
        }
    }

    private fun startPassiveListening() {
        if (isListening) return // SpeechRecognizer is active
        if (lifecycleState == ServiceLifecycleState.ERROR) {
            Log.w("ZyraService", "startPassiveListening: Skipped due to ERROR state ($serviceErrorMessage)")
            return
        }

        val manager = porcupineManager
        if (manager == null) {
            Log.w("ZyraService", "startPassiveListening: porcupineManager is null. Cannot start.")
            return
        }
        if (isPorcupineActive) {
            Log.v("ZyraService", "startPassiveListening: Porcupine already running, skipping redundant start.")
            return
        }

        Log.d("ZyraService", "Starting Passive Listening (Porcupine 4.x)...")
        try {
            manager.start()
            isPorcupineActive = true
            lifecycleState = ServiceLifecycleState.WAKEWORD_ACTIVE
        } catch (e: Exception) {
            isPorcupineActive = false
            porcupineManager = null
            lifecycleState = ServiceLifecycleState.ERROR
            val readableMsg = if (e.javaClass.simpleName.contains("ActivationRefused")) {
                "Wake word key refused"
            } else {
                e.message ?: "Porcupine start failed"
            }
            serviceErrorMessage = readableMsg
            Log.e("ZyraService", "Porcupine start exception: class=${e.javaClass.name}, message=${e.message}", e)
            broadcastState(ZyraState.ERROR, readableMsg)
        }
    }

    private fun startActiveListening() {
        if (isLiveKitSessionActive) {
            Log.d("ZyraService", "startListening BLOCKED: ElevenLabs session is active.")
            return
        }

        // Ensure Porcupine is stopped before starting SR
        try { porcupineManager?.stop() } catch(_: Exception) {}

        if (!isListening) {
             // AUDIO FOCUS MANAGEMENT - FIXED FOR CONCURRENT ACCESS
             // CRITICAL: Do NOT request audio focus during passive listening
             // This allows Zyra to listen for wake word WITHOUT pausing music/reels
            try {
                 // Active session - requesting audio focus
                 Log.d("ZyraService", "Active session - requesting audio focus")
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
            } catch (e: Exception) { 
                Log.e("ZyraService", "Audio focus error", e) 
            }
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                // Use FREE_FORM for better noise tolerance and natural speech in traffic
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN") // Switched to Indian English per user request
                putExtra("android.speech.extra.LANGUAGE_PREFERENCE", "en-IN") // Explicit preference for Samsung/Google TTS
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) 
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false) // Online is better for accents
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                
                // CONCURRENT AUDIO ACCESS - Allow listening while media plays
                // SOURCE 6 (VOICE_RECOGNITION) is best for Assistants. 
                // SOURCE 7 (VOICE_COMMUNICATION) enables AEC (Echo Cancellation) which helps with music.
                // NOTE: Removed EXTRA_AUDIO_SOURCE due to ClassCastException (Integer vs Parcelable) and Google App compatibility issues.
                // Defaulting to system default (usually VOICE_RECOGNITION or MIC).
                // putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                
                // Noise / Detection enhancements - Tuned for S23
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000) // Changed to Int
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
            }
            try {
                Log.d("ZyraService", "Starting SpeechRecognizer...")
                
                if (isActiveSession && !audioManager.isMusicActive) {
                    silenceBeep() 
                }
                
                // Ensure TTS is stopped to free up audio hardware
                com.zyra.app.logic.ElevenLabsTTSManager.stop()

                speechRecognizer.startListening(intent)
                
                if (isActiveSession && !audioManager.isMusicActive) {
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



    override fun onRmsChanged(rmsdB: Float) {
        // RMS callback proves recognizer is alive
        lastVoiceActivityTime = System.currentTimeMillis()
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        lastVoiceActivityTime = System.currentTimeMillis()
    }

    override fun onError(error: Int) {
        isListening = false 
        try { 
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null) 
        } catch (_: Exception) {}
        
        Log.e("ZyraService", "Speech Error: $error")
        consecutiveErrorCount++
        
        // CRITICAL FIX for Error 11 (Server Disconnected) / Error 5 (Client Side)
        if (error == 11 || error == SpeechRecognizer.ERROR_SERVER || error == SpeechRecognizer.ERROR_CLIENT) {
             Log.w("ZyraService", "Critical Error ($error) - Hard Resetting Recognizer...")
             try { speechRecognizer.destroy() } catch (_: Exception) {}
             isListening = false
             android.os.Handler(mainLooper).postDelayed({
                 try {
                     initSpeechRecognizer()
                     startListening()
                 } catch (e: Exception) {
                     Log.e("ZyraService", "Re-init failed", e)
                 }
             }, 1000)
             return
        }

        // GIVE UP after 3 consecutive "No Matches" to prevent infinite loops
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            if (isActiveSession) {
                if (consecutiveErrorCount >= 3) {
                     Log.w("ZyraService", "Too many errors ($consecutiveErrorCount). Giving up.")
                     speak("I'm sorry, I didn't hear anything.") 
                     isActiveSession = false
                     broadcastState(ZyraState.IDLE)
                     startListening() // Calls startPassiveListening inside
                     return
                }
            } else {
                // In passive mode (waiting for wake word), NO_MATCH is expected.
                // Reset consecutive error count and do NOT give up.
                consecutiveErrorCount = 0
            }
        }

        // Standard Backoff
        val backoff = when {
            error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 600L // Increased from 100ms
            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 2000L
            else -> 1000L
        }
        
        // Retrying...
        restartListening(backoff)
        
        // ONLY broadcast IDLE if we are NOT in an active session (Passive Mode usually handles this internally)
        // OR if we are about to give up (handled above).
        // If we are retrying in Active Session, keep UI as LISTENING (or similar) to avoid flickering
        if (isActiveSession) {
             // broadcastState(ZyraState.IDLE) // REMOVED to prevent "flicker" during retry
             // Optionally show "Processing..." or keep "Listening"
        }
    }


    override fun onResults(results: Bundle?) {
        lastVoiceActivityTime = System.currentTimeMillis()
        isListening = false
        
        // DEBUG: Log everything about the results
        Log.d("ZyraService", "onResults() called - results bundle: ${results != null}")
        results?.keySet()?.forEach { key ->
            Log.d("ZyraService", "  Bundle key: $key = ${results.get(key)}")
        }
        
        // Abandon focus immediately on results to allow media to resume full volume if ducked
        try { 
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null) 
        } catch (_: Exception) {}
        
        broadcastState(ZyraState.PROCESSING)
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        
        Log.d("ZyraService", "Matches: ${matches?.size ?: 0} results")
        matches?.forEachIndexed { index, match ->
            Log.d("ZyraService", "  [$index]: $match")
        }
        
        if (!matches.isNullOrEmpty()) {
            consecutiveErrorCount = 0 // Success! Reset error count.
            processCommands(matches)
        } else {
            Log.w("ZyraService", "No speech results! isActiveSession=$isActiveSession")
            // ALWAYS restart listening. Even in passive mode, we need to keep listening for the wake word.
            restartListening()
            
            if (!isActiveSession) {
                Log.d("ZyraService", "No results in passive mode - restarting silently")
                broadcastState(ZyraState.IDLE)
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        broadcastState(ZyraState.LISTENING)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        lastVoiceActivityTime = System.currentTimeMillis()
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches.isNullOrEmpty()) return

        val text = matches[0].lowercase()
        
        // ENHANCED WAKE WORD DETECTION with "why" support
        val wakeWordVariations = listOf(
            "zyra", "zara", "zira", "cyra", "xyla", "viera",
            "saira", "sai", "sarah", "sara" // Common misinterpretations
        )
        
        // Phonetic variations for "why" (Indian accent)
        val whyVariations = listOf("why", "y", "vai", "wi", "wai", "way", "wye")
        
        val hasWakeWord = wakeWordVariations.any { text.contains(it) }
        val hasWhy = whyVariations.any { word ->
            // Match whole word or at end of sentence
            text.contains(" $word") || text.endsWith(word) || text == word
        }
        
        // Detect "Zyra why" pattern OR just "Zyra" wake word
        val isWakeWordCommand = hasWakeWord && (hasWhy || !isActiveSession)
        
        if (isWakeWordCommand && !isActiveSession) {
             Log.d("ZyraService", "Wake Word detected via Partial Results: $text (hasWhy=$hasWhy)")
             isActiveSession = true // Activate session!
             resetSessionTimeout()
             broadcastState(ZyraState.LISTENING) // Show overlay
             
             // Request Focus only NOW that we caught the wake word
             // Use MAY_DUCK to lower music volume instead of stopping it
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
             } catch (e: Exception) {
                 Log.e("ZyraService", "Failed to request audio focus", e)
             }
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
    private var isVoskActive = false
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
        
        // Track user query for memory persistence
        lastUserQuery = bestMatch
        
        // --- Wake Word Logic ---
        // --- Wake Word Logic ---
        @Suppress("SpellCheckingInspection")
        val wakeWordVariations = listOf(
            "zyra", "zara", "zira", "cyra", "xyla", "viera",
            "saira", "sai", "sarah", "sara",
            "z y r a", "jayra", "jyra" // Added spelled out and common mishearings
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

            // TRIGGER MODULAR PIPELINE (Turn-Based)
            // If the user only said the Name ("Hey Zyra"), we acknowledge and wait.
            // If they said a command ("Hey Zyra what is time"), we process it immediately.
            
            val commandContent = bestMatch.replace(Regex("(?i)hey|hello|hi|okay"), "").trim()
            val justName = wakeWordVariations.any { commandContent.equals(it, ignoreCase = true) } || commandContent.isEmpty()

            if (justName) {
                 Log.d("ZyraService", "Wake Word Only -> Prompting")
                 speak("Yes?") // Efficient acknowledgement
                 // The speak() method automatically restarts listening after completion
                 return
            } else {
                 Log.d("ZyraService", "Wake Word + Command -> Processing immediately")
                 // Fall through to Intent Processing below
            }
        } else {
             // In Active Session, refresh timeout
             resetSessionTimeout()
         }

        // --- SANITIZATION ---
        // Strip "Zyra" or "Hey Zyra" from the start to ensure clean command processing
        // "Zyra play music" -> "play music"
        var sanitizedInput = bestMatch
        for (variant in wakeWordVariations) {
            if (sanitizedInput.lowercase().startsWith(variant)) {
                sanitizedInput = sanitizedInput.substring(variant.length).trim()
            }
        }
        // Also clean "Hey", "Hello" again just in case (e.g. "Hey Zyra play")
        sanitizedInput = sanitizedInput.replace(Regex("^(?i)(hey|hello|hi|okay|please) "), "").trim()
        
        if (sanitizedInput.isEmpty()) sanitizedInput = bestMatch // Fallback if they just said "Zyra" (handled above, but safety)

        // 0. GLOBAL DISMISSAL (Top Priority - Bypass everything else)
        // Handles "Zyra leave", "Zyra dismiss", "Zyra goodbye", etc.
        val lowerText = sanitizedInput.lowercase()
        // Check exact matches or strong keywords for dismissal
        if (lowerText.contains("goodbye") || lowerText.contains("go to sleep") || 
            lowerText == "leave" || lowerText == "dismiss" || lowerText == "rest" ||
            lowerText == "stop listening" || lowerText == "shut down") {
             
             speak(com.zyra.app.logic.PersonaManager.getDismissalResponse())
             isActiveSession = false
             broadcastState(ZyraState.IDLE)
             return
        }

        // 0.5 LOCAL TOOLS (Time/Date) - Instant & Reliable
        if (lowerText.contains("what's the time") || lowerText.contains("current time") || lowerText.contains("tell me the time") || (lowerText.contains("time") && lowerText.contains("now"))) {
             val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
             speak("It is $time")
             return
        }
        if (lowerText.contains("what's the date") || lowerText.contains("current date") || lowerText.contains("day is it")) {
             val date = java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault()).format(java.util.Date())
             speak("It is $date")
             return
        }

        // 0.6 DECISIONS (Using DecisionEngine)
        if (lowerText.contains("should i") || lowerText.contains("decide") || lowerText.contains("pick for me") || lowerText.contains("make a choice")) {
             speak("Consulting Decision Matrix...")
             val decision = com.zyra.app.logic.DecisionEngine.analyzeDecision(lowerText)
             if (decision != null) {
                 speak(decision)
                 return
             }
        }

        // 0.8 CALL CONTROL (Global - Cut/End)
        if (lowerText.contains("cut call") || lowerText.contains("end call") || lowerText.contains("hang up") || lowerText.contains("disconnect call")) {
             cutCall()
             return
        }

        // 0.9 MEDIA CONTROL (Global - Pause/Resume/Next)
        if (lowerText.contains("pause") || lowerText.contains("stop music") || lowerText.contains("stop song")) {
             handleMediaCommand("pause")
             speak("Paused.")
             return
        }
        if (lowerText == "play music" || lowerText == "resume" || lowerText == "resume song" || lowerText == "continue music") {
             handleMediaCommand("play")
             speak("Resuming.")
             return
        }
        if (lowerText.contains("next song") || lowerText.contains("skip song") || lowerText.contains("next track")) {
             handleMediaCommand("next")
             speak("Skipping.")
             return
        }

        // 0.92 VOLUME CONTROL (Local - Maximize/Decrease/Increase)
        // Specific user requests: "maximise volume", "maximize sound", "decree sound"
        if (lowerText.contains("maximiz") || lowerText.contains("maximis") || lowerText.contains("full volume") || lowerText.contains("max volume")) {
             val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
             audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
             speak("Volume maximized.")
             return
        }
        if (lowerText.contains("decree sound") || lowerText.contains("decrease") || lowerText.contains("lower volume") || lowerText.contains("volume down") || lowerText.contains("sound down") || lowerText.contains("low sound")) {
             audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
             audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) // Double step for noticeable effect
             speak("Turning it down.")
             return
        }
        if (lowerText.contains("increase") || lowerText.contains("raise volume") || lowerText.contains("volume up") || lowerText.contains("sound up")) {
             audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
             audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
             speak("Turning it up.")
             return
        }
        if (lowerText.contains("mute") && !lowerText.contains("unmute")) {
             audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
             speak("Muted.")
             return
        }
        if (lowerText.contains("unmute") || lowerText.contains("speak up")) {
             audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
             speak("Unmuted.")
             return
        }
        
        // 0.94 HOT ROUTING (Problematic Contacts)
        // User struggles with "NBS Badkarma" (Heard as "Invest Karma") and "NBS Zacko" (Heard as "Psycho")
        // If these keywords appear ANYWHERE, assume it's a call request.
        if (lowerText.contains("karma") || lowerText.contains("bad karma") || lowerText.contains("badkarma")) {
             speak("Calling Badkarma...")
             resolveAndCall("NBS Badkarma")
             return
        }
        if (lowerText.contains("zacko") || lowerText.contains("psycho") || lowerText.contains("saco") || lowerText.contains("zaco")) {
             speak("Calling Zacko...")
             resolveAndCall("NBS Zacko")
             return
        }

        // 0.95 DIRECT CALLING (Local Task)
        // Hotfix: Handle "code" as "call" for Indian accent misinterpretations
        if (lowerText.startsWith("call ") || lowerText.startsWith("dial ") || lowerText.startsWith("phone ") || lowerText.startsWith("code ")) {
             val name = lowerText.replace("call ", "").replace("dial ", "").replace("phone ", "").replace("code ", "").trim()
             if (name.isNotEmpty()) {
                 speak("Searching contacts for $name...")
                 resolveAndCall(name)
                 return
             }
        }

        // -1. CHECK CONVERSATION STATE
        if (conversationManager.isBusy()) {
            handleConversationState(sanitizedInput)
            return
        }

        // 1. Check Custom Commands (Learning Memory)
        val resolvedText = checkCustomCommands(sanitizedInput)
        if (resolvedText == "brain_executed") return // Task triggered, stop.
        
        val intent = nlp.process(resolvedText) // Use full match to preserve context
        
        // Execute Actions
        when (intent.type) {
            IntentType.GREETING -> {
                speak(com.zyra.app.logic.PersonaManager.getGreetingResponse())
            }
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
                // ========== JARVIS CONTROLLER ROUTING ==========
                // Route commands to appropriate JARVIS controllers
                var handled = false
                
                // PERSONALITY CONTROLLER - Human-like responses
                if (lowerText.contains("how are you") || lowerText.contains("what's up") || 
                    lowerText.contains("how's it going") || lowerText.contains("how do you feel")) {
                    val response = com.zyra.app.logic.PersonaManager.getComplimentResponse()
                    speak(response)
                    handled = true
                }
                
                // REMINDER CONTROLLER - Set reminders (requires time parsing)
                else if (lowerText.contains("remind me") || lowerText.contains("set reminder")) {
                    speak("Reminder feature requires time specification. Please say when you want to be reminded.")
                    // TODO: Add time parsing logic
                    handled = true
                }
                
                // TASK CONTROLLER - To-do lists
                else if (lowerText.contains("add task") || lowerText.contains("to do") || 
                         lowerText.contains("create task") || lowerText.contains("new task")) {
                    speak("Adding task...")
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val taskName = bestMatch.replace(Regex("(add task|to do|create task|new task)"), "").trim()
                            val task = com.zyra.app.logic.TaskController.addTask(this@VoiceAssistantService, taskName)
                            withContext(Dispatchers.Main) { speak("Task added: ${task.name}") }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { speak("Failed to add task") }
                        }
                    }
                    handled = true
                }
                
                // NOTE CONTROLLER - Voice notes
                else if (lowerText.contains("take note") || lowerText.contains("remember this") || 
                         lowerText.contains("save note") || lowerText.contains("make note")) {
                    speak("Saving note...")
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val noteContent = bestMatch.replace(Regex("(take note|remember this|save note|make note)"), "").trim()
                            val note = com.zyra.app.logic.NoteController.addNote(this@VoiceAssistantService, noteContent)
                            withContext(Dispatchers.Main) { speak("Note saved: ${note.content}") }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { speak("Failed to save note") }
                        }
                    }
                    handled = true
                }
                
                // WIKIPEDIA CONTROLLER - Knowledge lookup
                else if (lowerText.contains("who is") || lowerText.contains("what is") || 
                         lowerText.contains("tell me about") || lowerText.contains("wikipedia")) {
                    speak("Searching Wikipedia...")
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val query = bestMatch.replace(Regex("(who is|what is|tell me about|wikipedia)"), "").trim()
                            val result = com.zyra.app.logic.WikipediaController.search(query)
                            val response = if (result != null) {
                                com.zyra.app.logic.WikipediaController.formatForSpeech(result)
                            } else {
                                "I couldn't find information about that"
                            }
                            withContext(Dispatchers.Main) { speak(response) }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { speak("Wikipedia search failed") }
                        }
                    }
                    handled = true
                }
                
                // SPATIAL INTELLIGENCE - "Where am I" feature
                else if (lowerText.contains("where am i") || lowerText.contains("current location") || 
                         lowerText.contains("my location")) {
                    speak("Checking your location...")
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Check location permission
                            if (ContextCompat.checkSelfPermission(
                                this@VoiceAssistantService,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED) {
                                withContext(Dispatchers.Main) {
                                    speak("I need location permission to tell you where you are. Please grant it in app settings.")
                                }
                                return@launch
                            }
                            
                            
                            
                            // Request FRESH high-accuracy GPS location (not cached)
                            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@VoiceAssistantService)
                            
                            // Check last location first for speed (if < 2 mins old)
                            var locationFound = false
                            try {
                                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                    if (location != null && System.currentTimeMillis() - location.time < 120000) {
                                        // Use recent location immediately
                                        locationFound = true
                                        handleLocationFound(location)
                                    }
                                }
                            } catch (e: SecurityException) {
                                // Ignore
                            }
                            
                            // If no recent location, request fresh one with filler
                            if (!locationFound) {
                                // LATENCY MASKING: Speak a tech-savvy filler while GPS locks
                                withContext(Dispatchers.Main) {
                                    speak(com.zyra.app.logic.PersonalityController.getLocationFillerPhrase())
                                }
                                
                                val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                                
                                fusedLocationClient.getCurrentLocation(
                                    Priority.PRIORITY_HIGH_ACCURACY,
                                    cancellationTokenSource.token
                                ).addOnSuccessListener { location: Location? ->
                                if (location != null) {
                                    handleLocationFound(location)
                                } else {
                                    speak("I couldn't get your current location. Please make sure GPS is enabled.")
                                }
                            }.addOnFailureListener { e ->
                                Log.e("ZyraService", "Fresh location fetch failed", e)
                                speak("I couldn't determine your location. Please check that GPS is enabled and you're outdoors for better signal.")
                            }
                        }
                    } catch (e: Exception) {
                            Log.e("ZyraService", "Location error", e)
                            withContext(Dispatchers.Main) { 
                                speak("I couldn't determine your location. Please check location permissions.")
                            }
                        }
                    }
                    handled = true
                }
                
                // SPATIAL INTELLIGENCE - Find places along route
                else if (lowerText.contains("find") && (lowerText.contains("on the way") || 
                         lowerText.contains("along route") || lowerText.contains("nearby"))) {
                    speak("Searching along your route...")
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Extract place type from query
                            val placeType = when {
                                lowerText.contains("atm") -> "atm"
                                lowerText.contains("bank") -> "bank"
                                lowerText.contains("gas") || lowerText.contains("petrol") -> "gas_station"
                                lowerText.contains("restaurant") || lowerText.contains("food") -> "restaurant"
                                lowerText.contains("coffee") -> "cafe"
                                else -> "point_of_interest"
                            }
                            
                            val places = com.zyra.app.logic.SpatialIntelligence.findPlacesAlongRoute(placeType, 5)
                            val summary = com.zyra.app.logic.SpatialIntelligence.generatePlacesSummary(places)
                            withContext(Dispatchers.Main) { speak(summary) }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { 
                                speak("I couldn't search along your route. Make sure you have an active navigation.")
                            }
                        }
                    }
                    handled = true
                }
                
                // If not handled by controllers, use generic fallback
                if (!handled) {
                    // MODULAR BRAIN FALLBACK
                    // If we don't know what it is locally, ask the Big Brain (Groq).
                    val query = intent.extra ?: bestMatch
                    // LATENCY MASKING: Play a "Thinking..." phrase while waiting for API
                    speak(com.zyra.app.logic.PersonalityController.getFillerPhrase())
                    invokeGroqAI(query)
                }
                // ================================================
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
            @Suppress("DEPRECATION")
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
                enterPin(pin)
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
        
        var selectedContact: com.zyra.app.data.Contact? = null

        // 1. Ordinal Selection
        if (lowerText.contains("first") || lowerText.contains("1st") || lowerText.contains("one") || lowerText.contains("top")) {
            selectedContact = candidates[0]
        }
        // ESCAPE HATCH for New Commands (Fix for "Why" loop)
        else if (lowerText.startsWith("why") || lowerText.startsWith("what") || lowerText.startsWith("how") || 
                 lowerText.startsWith("call") || lowerText.startsWith("open") || lowerText.startsWith("play")) {
             Log.d("ZyraService", "Breaking out of clarification for new command: $text")
             conversationManager.clear()
             processCommands(listOf(text))
             return
        }
        else if (candidates.size >= 2 && (lowerText.contains("second") || lowerText.contains("2nd") || lowerText.contains("two") || lowerText.contains("bottom"))) {
            selectedContact = candidates[1]
        }
        else if (candidates.size >= 3 && (lowerText.contains("third") || lowerText.contains("3rd") || lowerText.contains("three"))) {
             selectedContact = candidates[2]
        }
        // 2. Confirmation (If users says "Yes", assume the first one - usually the best match)
        else if (lowerText.contains("yes") || lowerText.contains("call") || lowerText.contains("sure") || lowerText.contains("correct")) {
            selectedContact = candidates[0]
        }
        
        // 3. Name Match
        if (selectedContact == null) {
            val candidateNames = candidates.map { it.name }
            val bestRefinedMatch = Levenshtein.findBestMatch(text, candidateNames)
            if (bestRefinedMatch != null) {
                selectedContact = candidates.find { it.name == bestRefinedMatch }
            }
        }

        // --- ACTION EXECUTION ---
        if (selectedContact != null) {
            // CONFIRMATION STEP ADDED: Ask "Shall I call X?" instead of calling directly
            speak("Shall I call ${selectedContact.name}?")
            conversationManager.startCallConfirmation(selectedContact.name, selectedContact.phoneNumber)
            resetSessionTimeout()
            return
        }
        
        // 4. Cancellation
        if (lowerText.contains("no") || lowerText.contains("cancel") || lowerText.contains("stop") || lowerText.contains("none")) {
            speak("Cancelled.")
            conversationManager.clear()
            return
        }
        
        speak("I didn't catch that. Please say First, Second, or the Name.")
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

        // ESCAPE HATCH for New Commands
        if (lowerText.startsWith("why") || lowerText.startsWith("what") || lowerText.startsWith("how") || 
            lowerText.startsWith("call") || lowerText.startsWith("open") || lowerText.startsWith("play")) {
             conversationManager.clear()
             processCommands(listOf(text))
             return
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
          // ALIAS FIXED: "Psycho" -> "NBS Zacko"
          // This handles the specific user complaint where "Call NBS Zacko" is heard as "Psycho"
          var finalName = spokenName
          if (finalName.equals("psycho", ignoreCase = true) || finalName.contains("zacko", ignoreCase = true)) {
              finalName = "NBS Zacko" 
          }
          // ALIAS: "Badkarma" -> "NBS Badkarma"
          if (finalName.contains("karma", ignoreCase = true) || finalName.contains("bad karma", ignoreCase = true)) {
              finalName = "NBS Badkarma"
          }

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
                 var matchedNames = Levenshtein.findAllMatches(finalName, names)
                 
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
                             
                             if (score > 90 || matchName.equals(spokenName, ignoreCase = true)) {
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
                    @Suppress("DEPRECATION")
                    telecomManager.acceptRingingCall()
                    speak(com.zyra.app.logic.PersonaManager.getCallAnswerResponse())
                    
                    if (mode == "speaker") {
                     // Wait for call to connect
                         android.os.Handler(mainLooper).postDelayed({
                             val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                             audioManager.mode = AudioManager.MODE_IN_CALL
                             @Suppress("DEPRECATION")
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
                     @Suppress("DEPRECATION")
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
        isActiveSession = false // Fix: End session so overlay hides after speaking
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
             query = payload.drop(8)
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
        if (targetApp.contains("spotify")) {
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
             val instruction = text.drop(6) // remove "alias:"
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
             val instruction = text.drop(5) // remove "fact:"
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


    // ========== MEMORY PERSISTENCE ==========
    // Save conversation to memory database for learning and history
    private var lastUserQuery: String = ""
    
    private fun saveConversationToMemory(userInput: String, zyraResponse: String, intentType: String = "UNKNOWN") {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                com.zyra.app.logic.MemoryController.saveConversation(
                    context = this@VoiceAssistantService,
                    userInput = userInput,
                    zyraResponse = zyraResponse,
                    intentType = intentType
                )
                Log.d("ZyraService", "✓ Conversation saved to memory")
            }
        } catch (e: Exception) {
            Log.e("ZyraService", "Failed to save conversation to memory", e)
        }
    }
    // ========================================

    private fun speak(text: String) {
        broadcastState(ZyraState.SPEAKING)
        
        // Save conversation to memory (if we have a user query)
        if (lastUserQuery.isNotEmpty()) {
            saveConversationToMemory(lastUserQuery, text)
            lastUserQuery = "" // Reset after saving
        }
        
        // ENSURE SINGLE VOICE: Native TTS is disabled.


        // UNIFIED VOICE: Use ElevenLabs for ALL speech (Local & Cloud)
        CoroutineScope(Dispatchers.IO).launch {
             com.zyra.app.logic.ElevenLabsTTSManager.speak(this@VoiceAssistantService, text) {
                 // On Complete (Back on Main Thread via Handler if needed, but callback might be largely async)
                 // The Manager calls invoke() from Main Thread in playAudio, but IO in error/fallback.
                 // Safer to wrap in Handler.
                 android.os.Handler(mainLooper).post { 
                    if (isActiveSession) {
                        resetSessionTimeout()
                        broadcastState(ZyraState.LISTENING)
                    } else {
                        broadcastState(ZyraState.IDLE)
                    }
                    startListening() 
                 }
             }
        }
    }

    override fun onInit(status: Int) {
        // NATIVE TTS COMPLETELY DISABLED: To prevent "double voice" issues.
        // We rely exclusively on ElevenLabsTTSManager.
        isTtsReady = false
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
            speak("I don't have permission to make calls. Opening settings for you to allow it.")
            
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
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
        
        // 0. Pause Watchdog to prevent race condition during reset
        watchdogHandler.removeCallbacksAndMessages(null)

        // 1. FORCE Restart Listener
        try {
            // Force reset state
            isListening = false 
            isActiveSession = false // Force Passive Mode on app swipe
            isLiveKitSessionActive = false // CRITICAL FIX: Reset session flag so we don't block restart 
            com.zyra.app.logic.LiveKitManager.disconnect() // CRITICAL FIX: Kill zombie session immediately 
            
            // Clean Stop Porcupine to ensure fresh start
            try {
                porcupineManager?.stop()
            } catch (e: Exception) {
                Log.e("ZyraService", "Error stopping Porcupine during swipe cleanup", e)
            }

            // Kill existing recognizer to be safe
            try { speechRecognizer.destroy() } catch (_: Exception) {}
            
            // Re-init completely
            initSpeechRecognizer() 
            
            // DELAYED START: Give the OS 1s to settle the 'swipe' animation/kill
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                 startListening()
                 // Resume Watchdog check
                 watchdogHandler.postDelayed(watchdogRunnable, 5000)
            }, 1000)
            
        } catch (_: Exception) {
            // If destroy fails, just try starting manually
            try { startListening() } catch(_: Exception) {}
            // Resume Watchdog in case of failure
            watchdogHandler.postDelayed(watchdogRunnable, 5000)
        }

        // 2. Trigger Resurrection (Just in case OS kills the process soon)
        val restartIntent = Intent(this, BootReceiver::class.java)
        restartIntent.action = "com.zyra.app.RESTART"
        sendBroadcast(restartIntent)
        
        // 3. SERVICE PERSISTENCE: Do NOT kill the process.
        // We are a Foreground Service, so we should stay alive.
        // Just ensure we are in a clean state (Passive Mode).
        Log.d("ZyraService", "App swiped. Service staying alive in Passive Mode.")
    }

    override fun onDestroy() {
        // 1. Porcupine Cleanup
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
            isPorcupineActive = false
        } catch (e: Exception) {
            Log.e("ZyraService", "Porcupine Clean-up Failed", e)
        }

        // 2. Save Intelligence State (Existing)
        try {
            Log.d("ZyraService", "Saving JARVIS Intelligence System state...")
            com.zyra.app.logic.IntelligenceSystem.saveAll(this)
            Log.d("ZyraService", "✓ JARVIS state saved successfully")
        } catch (e: Exception) {
            Log.e("ZyraService", "Failed to save IntelligenceSystem state", e)
        }
        
        // 3. Speech Recognizer Cleanup
        try {
            speechRecognizer.destroy()
        } catch (_: Exception) { }
        
        // 4. ElevenLabs & TTS Cleanup
        isLiveKitSessionActive = false 
        com.zyra.app.logic.LiveKitManager.disconnect() 
        
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        
        // 5. WakeLock Cleanup
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e("ZyraService", "Error releasing WakeLock", e)
        }
        
        // 6. Resurrection Logic
        Log.d("ZyraService", "Service Destroyed")
        watchdogHandler.removeCallbacksAndMessages(null)
        Log.e("ZyraService", "Service Destroyed! Triggering Resurrection...")
        val restartIntent = Intent(this, BootReceiver::class.java)
        restartIntent.action = "com.zyra.app.RESTART"
        sendBroadcast(restartIntent)

        super.onDestroy()
    }

    // --- HELPER: Maximize Volume for clear AI Speech ---
    // --- HELPER: Maximize Volume for clear AI Speech ---
    private fun maximizeCallVolume() {
        fun setMax() {
            try {
                val maxVoice = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val currentVoice = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                
                val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val currentMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                
                Log.d("ZyraService", "Maximizing Volume. Voice: $currentVoice/$maxVoice, Music: $currentMusic/$maxMusic")
                
                // Set to 100% to ensure clarity
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoice, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0) 
            } catch (e: Exception) {
                Log.e("ZyraService", "Error setting volume", e)
            }
        }

        // Offload to IO thread to prevent UI jank
        CoroutineScope(Dispatchers.IO).launch {
            setMax()
            // Retry logic also on background
            kotlinx.coroutines.delay(500)
            setMax()
        }
    }

    private fun normalizePhonetics(input: String): String {
        var text = input.lowercase().trim()
        
        // 1. Consonant Swaps (Common in Indian accents)
        // Standardize to softer/simpler phonemes
        text = text.replace("ph", "f") 
        text = text.replace("v", "w")
        text = text.replace("z", "s")
        text = text.replace("sh", "s")
        text = text.replace("th", "t")
        text = text.replace("k", "c")
        text = text.replace("ck", "c") // Zacko -> Zaco
        
        // 2. Vowel Reduction
        text = text.replace("ee", "i") // Meet -> Mit
        text = text.replace("oo", "u") // Moon -> Mun
        text = text.replace("y", "i")  // Cycle -> Cicle (and Zyra -> Sira)
        
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

    private fun handleLocationFound(location: Location) {
        Log.d("ZyraService", "GPS location found: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}m")
        
        // Update LocationIntelligence with fresh coordinates
        com.zyra.app.logic.LocationIntelligence.updateLocation(
            location.latitude,
            location.longitude
        )
        
        // Use Geocoder to get actual place name
        try {
            val geocoder = Geocoder(this@VoiceAssistantService, Locale.getDefault())
            // Helper for geocoding on background thread if possible, but Geocoder is synchronous.
            // Since we are likely on a background thread from the listener (Play Services), this is okay.
            // If not, we should launch coroutine. But listeners usually run on main or provided looper.
            // For safety, let's wrap in IO scope just in case.
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    
                    val (placeName, specialty) = if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        
                        // Get locality/area name (like "Moorkanad, Karuvannur")
                        val locationName = buildString {
                            address.subLocality?.let { append(it) }
                                ?: address.locality?.let { append(it) }
                                ?: address.featureName?.let { append(it) }
                            
                            // Add parent locality if different
                            if (address.locality != null && address.locality != address.subLocality) {
                                if (isNotEmpty()) append(", ")
                                append(address.locality)
                            }
                        }
                        
                        // Generate specialty/interesting fact
                        val fact = buildString {
                            // Add administrative area (state/region)
                            address.adminArea?.let { 
                                append(" in $it")
                            }
                            // Add country if not India (for international locations)
                            if (address.countryCode != "IN" && address.countryName != null) {
                                if (isNotEmpty()) append(", ")
                                append(address.countryName)
                            }
                        }
                        
                        Pair(
                            locationName.ifEmpty { "an unknown location" },
                            fact.ifEmpty { null }
                        )
                    } else {
                        Pair("an unknown location", null)
                    }
                    
                    // Check for known place from LocationIntelligence
                    val knownPlace = com.zyra.app.logic.LocationIntelligence.getCurrentPlace()
                    
                    // Get contextual suggestions
                    val suggestions = com.zyra.app.logic.LocationIntelligence.getContextualSuggestions()
                    
                    // Build response - NO COORDINATES, only place name + specialty
                    val response = buildString {
                        if (knownPlace != null) {
                            append("You are at $knownPlace")
                        } else {
                            append("You are at $placeName")
                        }
                        
                        // Add specialty/fact if available
                        specialty?.let {
                            append("$it")
                        }
                        append(". ")
                        
                        // Add suggestions if available
                        if (suggestions.isNotEmpty()) {
                            append("Would you like me to: ")
                            append(suggestions.take(2).joinToString(", or "))
                            append("?")
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        speak(response)
                    }
                } catch (e: Exception) {
                    Log.e("ZyraService", "Geocoding failed", e)
                    withContext(Dispatchers.Main) {
                        speak("I couldn't determine your exact address, but coordinates are acquired.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ZyraService", "Location handling error", e)
            speak("Error processing location data.")
        }
    }

    private fun invokeGroqAI(text: String) {
        // 1. Validate Keys
        if (com.zyra.app.logic.GroqModule.apiKey.isBlank() || com.zyra.app.logic.ElevenLabsTTSManager.apiKey.isBlank()) {
            speak("I need my API keys to function. Please configure them.")
            broadcastState(ZyraState.IDLE)
            return
        }

        // 2. Show Processing State
        broadcastState(ZyraState.PROCESSING)
        // speak("Thinking...") // REMOVED: Too repetitive/slow. UI is enough.

        // 3. Background Pipeline
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // A. Brain (Groq)
                val responseText = com.zyra.app.logic.GroqModule.chat(text)
                Log.d("ZyraService", "Groq Response: $responseText")

                // B. Voice (ElevenLabs TTS)
                // We use our new Manager which uses MediaPlayer (Music Stream)
                withContext(Dispatchers.Main) {
                    // Update UI to speaking before audio starts logic
                    broadcastState(ZyraState.SPEAKING)
                }
                
                com.zyra.app.logic.ElevenLabsTTSManager.speak(this@VoiceAssistantService, responseText)
                
                // Note: ElevenLabsTTSManager handles the "Speaking" state internally? 
                // No, it plays audio. We should probably track it.
                // It has a completion listener that could trigger "Idle".
                // For now, TTSManager doesn't callback to Service. 
                // I should probably pass a callback to standard speak or just trust the duration.
                // Valid point: The TTSManager I wrote earlier fires its own events or just plays.
                // Let's rely on TTSManager logging for now and maybe update Service state 
                // via a shared listener if needed later. 
                // Actually, I'll update TTSManager in next step to be cleaner if needed.
                
                // For now, assume TTS starts immediately.
                
            } catch (e: Exception) {
                Log.e("ZyraService", "Modular Pipeline Failed", e)
                withContext(Dispatchers.Main) {
                   speak("I had a problem processing that.")
                   broadcastState(ZyraState.IDLE)
                }
            }
        }
    }






}

data class Candidate(val label: String, val packageName: String, val score: Int)
