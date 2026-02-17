package com.zyra.app.logic

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages Text-to-Speech using the ElevenLabs REST API.
 * This bypasses the SDK's audio routing issues by fetching an MP3 and playing it via standard MediaPlayer.
 */
object ElevenLabsTTSManager {
    private const val TAG = "ElevenLabsTTS"
    private const val API_URL = "https://api.elevenlabs.io/v1/text-to-speech"
    
    // Voice ID: "CwhRBWXzGAHq8TQ4Fs17" (Roger - British Male)
    private const val VOICE_ID = "CwhRBWXzGAHq8TQ4Fs17" 
    
    // We need the API Key. 
    // User Provided: sk_836b76037c9c1699dba27e70dfb33eb8d63014ec7c99b478
    // Standard API Key required for TTS.
    var apiKey: String = "sk_836b76037c9c1699dba27e70dfb33eb8d63014ec7c99b478"

    private var mediaPlayer: MediaPlayer? = null
    private var isSpeaking = false

    suspend fun speak(context: Context, text: String, onComplete: (() -> Unit)? = null) {
        if (text.isBlank()) return
        if (apiKey.isBlank()) {
            Log.e(TAG, "API Key missing")
            return
        }

        stop() // Stop previous speech

        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching audio for: $text")
                val mp3File = fetchAudio(context, text)
                if (mp3File != null) {
                    playAudio(context, mp3File, onComplete)
                } else {
                    onComplete?.invoke() // Fallback if fetch fails
                }
                Unit 
            } catch (e: Exception) {
                Log.e(TAG, "TTS Failed", e)
                onComplete?.invoke()
            }
        }
    }



    private fun fetchAudio(context: Context, text: String): File? {
        // Include VOICE_ID in filename to avoid playing cached files from previous voices (e.g. female voice)
        val fileName = "tts_${text.hashCode()}_$VOICE_ID.mp3"
        val file = File(context.cacheDir, fileName)
        
        // 1. Check Cache
        if (file.exists()) {
            Log.d(TAG, "Cache hit for: $text")
            return file
        }

        // 2. Network Request
        val url = URL("$API_URL/$VOICE_ID")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("xi-api-key", apiKey)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val jsonBody = org.json.JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_turbo_v2")
            put("voice_settings", org.json.JSONObject().apply {
                put("stability", 0.5)
                put("similarity_boost", 0.75)
            })
        }.toString()

        try {
            conn.outputStream.use { it.write(jsonBody.toByteArray()) }
            
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                // Save to cache dir
                conn.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                return file
            } else {
                Log.e(TAG, "Server Error: $responseCode - ${conn.errorStream?.bufferedReader()?.readText()}")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network Error", e)
            return null
        }
    }

    private suspend fun playAudio(context: Context, file: File, onComplete: (() -> Unit)? = null) {
        withContext(Dispatchers.Main) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, Uri.fromFile(file))
                    
                    // CRITICAL: Use Music Stream to avoid Earpiece issues
                    // setAudioStreamType(android.media.AudioManager.STREAM_MUSIC) // Deprecated
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    
                    prepare()
                    start()
                    isSpeaking = true
                    Log.d(TAG, "Playing audio...")
                    
                    setOnCompletionListener {
                        Log.d(TAG, "Playback finished")
                        isSpeaking = false
                        it.release()
                        mediaPlayer = null
                        onComplete?.invoke()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Playback Error", e)
                onComplete?.invoke()
            }
        }
    }

    fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
            isSpeaking = false
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking to stop", e)
        }
    }
}
