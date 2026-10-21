package com.zyra.app.logic

import android.content.Context
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object LiveKitManager {
    private const val TAG = "LiveKitManager"
    private var room: Room? = null
    private var eventJob: Job? = null
    
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

    suspend fun disconnectRoom(timeoutMs: Long = 1000L) {
        eventJob?.cancel()
        eventJob = null
        val oldRoom = room ?: return
        room = null
        withContext(Dispatchers.IO) {
            try {
                withTimeoutOrNull(timeoutMs) {
                    oldRoom.disconnect()
                } ?: Log.w(TAG, "Timed out waiting for old room to disconnect after ${timeoutMs}ms")
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting old room", e)
            }
        }
    }

    fun disconnect() {
        CoroutineScope(Dispatchers.Main).launch {
            disconnectRoom()
            // Reset service state on manual disconnect since event collection job was cancelled
            listener?.onDisconnected()
        }
    }

    fun connect(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            // Await old room disconnect with timeout before connecting
            disconnectRoom(timeoutMs = 1200L)

            try {
                Log.d(TAG, "Requesting fresh LiveKit token from Brain...")
                
                // 1. Fetch fresh token from FastAPI server (identity is generated server-side)
                val retrofit = Retrofit.Builder()
                    .baseUrl(BRAIN_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                
                val apiService = retrofit.create(com.zyra.app.network.ZyraApiService::class.java)
                val response = withContext(Dispatchers.IO) {
                    apiService.getLiveKitToken(BRAIN_SECRET).execute()
                }

                if (!response.isSuccessful || response.body() == null) {
                    val errorMsg = "Server failed to grant token: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    listener?.onError(errorMsg)
                    return@launch
                }

                val token = response.body()!!.token
                Log.d(TAG, "Token acquired. Connecting to LiveKit...")

                // 2. Connect to Room with the dynamic token
                val currentRoom = LiveKit.create(context.applicationContext)
                room = currentRoom
                
                eventJob = launch {
                    currentRoom.events.collect { event ->
                        // Event isolation: ignore stale events if this is no longer the active room
                        if (room !== currentRoom) {
                            Log.d(TAG, "Discarding event from stale room instance: $event")
                            return@collect
                        }
                        when (event) {
                            is RoomEvent.Connected -> {
                                Log.d(TAG, "Connected to LiveKit Room: ${currentRoom.name}, SID: ${currentRoom.sid}")
                                listener?.onConnected()
                                publishAudio()
                            }
                            is RoomEvent.Disconnected -> {
                                Log.d(TAG, "Disconnected from LiveKit. Reason: ${event.reason}, Error: ${event.error?.message}")
                                listener?.onDisconnected()
                            }
                            is RoomEvent.FailedToConnect -> {
                                Log.e(TAG, "Failed to connect to LiveKit: ${event.error.message}")
                                listener?.onError(event.error.message ?: "Unknown Connection Error")
                            }
                            is RoomEvent.ParticipantConnected -> {
                                Log.d(TAG, "Participant joined: identity=${event.participant.identity?.value}, name=${event.participant.name}")
                            }
                            is RoomEvent.ParticipantDisconnected -> {
                                Log.d(TAG, "Participant left: identity=${event.participant.identity?.value}")
                            }
                            is RoomEvent.TrackSubscribed -> {
                                Log.d(TAG, "Subscribed to track: kind=${event.track.kind}, name=${event.track.name}")
                            }
                            is RoomEvent.TrackUnsubscribed -> {
                                Log.d(TAG, "Unsubscribed from track: kind=${event.track.kind}, name=${event.track.name}")
                            }
                            is RoomEvent.Reconnecting -> {
                                Log.w(TAG, "LiveKit room reconnecting...")
                            }
                            is RoomEvent.Reconnected -> {
                                Log.d(TAG, "LiveKit room reconnected successfully")
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
                }

                currentRoom.connect(LIVEKIT_URL, token)
                
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
}
