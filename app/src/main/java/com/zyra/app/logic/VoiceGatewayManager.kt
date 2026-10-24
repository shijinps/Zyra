package com.zyra.app.logic

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import android.speech.tts.TextToSpeech
import java.util.Locale

object VoiceGatewayManager : TextToSpeech.OnInitListener {
    private const val TAG = "VoiceGateway"
    private val GATEWAY_URL get() = ZyraConfig.getVoiceGatewayUrl()
    
    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.ENGLISH
            isTtsReady = true
            Log.d(TAG, "TTS Initialized Successfully")
        }
    }

    fun connect(onReady: () -> Unit) {
        if (isConnected) return

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder().url(GATEWAY_URL).build()
        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d(TAG, "Connected to Zyra Voice Gateway")
                onReady()
                startStreaming()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val reply = json.optString("text")
                    if (reply.isNotEmpty()) {
                        Log.d(TAG, "Zyra says: $reply")
                        speak(reply)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.d(TAG, "Closing: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e(TAG, "WebSocket Failure: ${t.message}")
            }
        })
    }

    private fun startStreaming() {
        // High-speed audio streaming loop
        Thread {
            val bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            
            recorder.startRecording()
            val buffer = ByteArray(bufferSize)
            
            while (isConnected) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val base64Audio = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                    val json = JSONObject().apply {
                        put("audio", base64Audio)
                    }
                    webSocket?.send(json.toString())
                }
            }
            recorder.stop()
            recorder.release()
        }.start()
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zyra_reply")
        }
    }

    fun disconnect() {
        isConnected = false
        webSocket?.close(1000, "User disconnect")
        webSocket = null
    }
}
