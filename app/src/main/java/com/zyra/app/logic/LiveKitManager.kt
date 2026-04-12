package com.zyra.app.logic

import android.content.Context
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object LiveKitManager {
    private const val TAG = "LiveKitManager"
    private var room: Room? = null
    
    // URL to your LiveKit Cloud or Self-hosted Instance
    private const val LIVEKIT_URL = "wss://zyra-sbn0wnp2.livekit.cloud" 
    
    // BASE URL for your FastAPI Brain (where /livekit/token endpoint lives)
    private const val BRAIN_BASE_URL = "https://apizyra.shijin.in" 
    private const val BRAIN_SECRET = "zyra-secret-key-123"

    interface LiveKitListener {
        fun onConnected()
        fun onDisconnected()
        fun onError(error: String)
        fun onCallCommandReceived(phone: String, name: String)
        fun onEndCallCommandReceived()
    }
    
    private var listener: LiveKitListener? = null
    
    fun setListener(newListener: LiveKitListener?) {
        listener = newListener
    }

    fun init() {
        // Initialize if needed
    }

    fun connect(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d(TAG, "Requesting fresh LiveKit token from Brain...")
                
                // 1. Fetch fresh token from FastAPI server
                val retrofit = retrofit2.Retrofit.Builder()
                    .baseUrl(BRAIN_BASE_URL)
                    .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                    .build()
                
                val apiService = retrofit.create(com.zyra.app.network.ZyraApiService::class.java)
                val response = withContext(Dispatchers.IO) {
                    apiService.getLiveKitToken(BRAIN_SECRET).execute()
                }

                if (!response.isSuccessful || response.body() == null) {
                    val errorMsg = "Server failed to grant token: \${response.code()}"
                    Log.e(TAG, errorMsg)
                    listener?.onError(errorMsg)
                    return@launch
                }

                val token = response.body()!!.token
                Log.d(TAG, "Token acquired. Connecting to LiveKit...")

                // 2. Connect to Room with the dynamic token
                room = LiveKit.create(context.applicationContext)
                
                room?.events?.collect { event ->
                    when (event) {
                        is RoomEvent.Connected -> {
                            Log.d(TAG, "Connected to LiveKit Room!")
                            listener?.onConnected()
                            publishAudio()
                        }
                        is RoomEvent.Disconnected -> {
                            Log.d(TAG, "Disconnected from LiveKit")
                            listener?.onDisconnected()
                        }
                        is RoomEvent.FailedToConnect -> {
                            Log.e(TAG, "Failed to connect to LiveKit: \${event.error?.message}")
                            listener?.onError(event.error?.message ?: "Unknown Connection Error")
                        }
                        is RoomEvent.TrackSubscribed -> {
                            Log.d(TAG, "Subscribed to track: \${event.track.name}")
                        }
                        is RoomEvent.DataReceived -> {
                            try {
                                val jsonStr = String(event.data)
                                val json = JSONObject(jsonStr)
                                if (json.optString("action") == "ACTION_CALL") {
                                    val phone = json.optString("phone")
                                    val name = json.optString("name")
                                    listener?.onCallCommandReceived(phone, name)
                                } else if (json.optString("action") == "ACTION_HANGUP") {
                                    listener?.onEndCallCommandReceived()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing data channel message", e)
                            }
                        }
                        else -> {}
                    }
                }

                room?.connect(LIVEKIT_URL, token)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to LiveKit", e)
                listener?.onError(e.message ?: "Exception during connect")
            }
        }
    }

    private fun publishAudio() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // SENSITIVITY TUNE: Lowering noise suppression so ZAY hears the raw voice better
                val audioOptions = io.livekit.android.room.track.LocalAudioTrackOptions(
                    noiseSuppression = false, 
                    echoCancellation = true,
                    autoGainControl = true,
                    highPassFilter = false
                )
                val localTrack = room?.localParticipant?.createAudioTrack("microphone", audioOptions)
                localTrack?.start()
                room?.localParticipant?.publishAudioTrack(localTrack!!)
                Log.d(TAG, "Microphone published with Noise Cancellation")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish audio", e)
            }
        }
    }

    fun disconnect() {
        room?.disconnect()
        room = null
    }
}
