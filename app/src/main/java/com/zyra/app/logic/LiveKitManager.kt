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

object LiveKitManager {
    private const val TAG = "LiveKitManager"
    private var room: Room? = null
    
    // TODO: Replace with your actual LiveKit Cloud URL
    private const val LIVEKIT_URL = "wss://zyra-sbn0wnp2.livekit.cloud" 
    
    // In production, Token should be fetched from your FastAPI server, NOT hardcoded.
    // However, for testing, you can generate a token in the dashboard and paste it here.
    private const val TEST_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJuYW1lIjoiQW5kcm9pZCBVc2VyIiwidmlkZW8iOnsicm9vbUpvaW4iOnRydWUsInJvb20iOiJ6eXJhLXJvb20iLCJjYW5QdWJsaXNoIjp0cnVlLCJjYW5TdWJzY3JpYmUiOnRydWUsImNhblB1Ymxpc2hEYXRhIjp0cnVlfSwic3ViIjoienlyYS1hbmRyb2lkLXVzZXIiLCJpc3MiOiJBUElaUE5NdGhhSGpUY24iLCJuYmYiOjE3NzA2Mjk2NzcsImV4cCI6MTc3MDY1MTI3N30.CV5KzsY3gKhtBUcJfBGyjttW3bDLfRnCbYOvUkixWM8" 

    interface LiveKitListener {
        fun onConnected()
        fun onDisconnected()
        fun onError(error: String)
        fun onCallCommandReceived(phone: String, name: String)
    }
    
    private var listener: LiveKitListener? = null
    
    fun setListener(newListener: LiveKitListener?) {
        listener = newListener
    }

    fun init() {
        // Initialize if needed, though usually just creating room is enough
    }

    fun connect(context: Context, token: String = TEST_TOKEN) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Use applicationContext to prevent memory leaks since LiveKitManager is a singleton
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
                // Publish local microphone with Noise Cancellation
                val audioOptions = io.livekit.android.room.track.LocalAudioTrackOptions(
                    noiseSuppression = true,
                    echoCancellation = true,
                    autoGainControl = true,
                    highPassFilter = true
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
