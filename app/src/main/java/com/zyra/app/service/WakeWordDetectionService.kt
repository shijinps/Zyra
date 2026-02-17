package com.zyra.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import kotlinx.coroutines.*
import android.util.Log

/**
 * Wake Word Detection Service
 * Always listens for "Hey Zyra" in the background
 * Similar to "Hey Siri" or "OK Google"
 */
class WakeWordDetectionService : Service() {
    
    private var porcupine: Porcupine? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    companion object {
        private const val TAG = "WakeWordService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "wake_word_channel"
        
        fun start(context: Context) {
            val intent = Intent(context, WakeWordDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordDetectionService::class.java))
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        initializePorcupine()
        startListening()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        porcupine?.delete()
        serviceScope.cancel()
    }
    
    private fun initializePorcupine() {
        try {
            // Initialize Porcupine with built-in "Hey Siri" keyword
            // We'll use the closest available keyword for now
            porcupine = Porcupine.Builder()
                .setAccessKey(com.zyra.app.BuildConfig.PICOVOICE_ACCESS_KEY)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS) // Closest to "Hey Zyra"
                .build(applicationContext)
                
            Log.d(TAG, "Porcupine initialized successfully")
        } catch (e: PorcupineException) {
            Log.e(TAG, "Failed to initialize Porcupine", e)
        }
    }
    
    private fun startListening() {
        if (isListening || porcupine == null) return
        
        serviceScope.launch {
            try {
                val frameLength = porcupine!!.frameLength
                val sampleRate = porcupine!!.sampleRate
                
                val minBufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize
                )
                
                audioRecord?.startRecording()
                isListening = true
                
                val buffer = ShortArray(frameLength)
                
                while (isListening && isActive) {
                    val numRead = audioRecord?.read(buffer, 0, frameLength) ?: 0
                    
                    if (numRead == frameLength) {
                        val keywordIndex = porcupine?.process(buffer)
                        
                        if (keywordIndex != null && keywordIndex >= 0) {
                            // Wake word detected!
                            onWakeWordDetected()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in wake word detection", e)
            }
        }
    }
    
    private fun stopListening() {
        isListening = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
    
    private fun onWakeWordDetected() {
        Log.d(TAG, "Wake word detected!")
        
        // Show visual feedback (animation)
        showWakeWordAnimation()
        
        // Start voice assistant service
        val intent = Intent(this, VoiceAssistantService::class.java).apply {
            action = "WAKE_WORD_DETECTED"
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        // Optionally: play a sound or haptic feedback
        playWakeWordSound()
    }
    
    private fun showWakeWordAnimation() {
        // TODO: Show Siri-like animation overlay
        // This will be implemented in WakeWordOverlay.kt
        val intent = Intent("com.zyra.app.WAKE_WORD_DETECTED")
        sendBroadcast(intent)
    }
    
    private fun playWakeWordSound() {
        // TODO: Play a subtle sound to acknowledge wake word detection
        // Similar to Siri's "ding" sound
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Listening for 'Hey Zyra'"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.zyra.app.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zyra is listening")
            .setContentText("Say 'Hey Zyra' to activate")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
