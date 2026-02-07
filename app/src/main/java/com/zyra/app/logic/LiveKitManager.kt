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

object LiveKitManager {
    private const val TAG = "LiveKitManager"
    private var room: Room? = null
    
    // TODO: Replace with your actual LiveKit Cloud URL
    private const val LIVEKIT_URL = "wss://zyra-sbn0wnp2.livekit.cloud" 
    
    // In production, Token should be fetched from your FastAPI server, NOT hardcoded.
    // However, for testing, you can generate a token in the dashboard and paste it here.
    private const val TEST_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NzA0NjAwODgsImlkZW50aXR5IjoienlyYS10ZXN0LXVzZXIiLCJpc3MiOiJBUElaUE5NdGhhSGpUY24iLCJuYmYiOjE3NzA0NTkxODgsInN1YiI6Inp5cmEtdGVzdC11c2VyIiwidmlkZW8iOnsiY2FuUHVibGlzaCI6dHJ1ZSwicm9vbSI6Inp5cmEtcm9vbSIsInJvb21Kb2luIjp0cnVlfX0.CXI1b9sU5vxd2EqNJ1DCq4dSxjZDGBORZdyNnLvq7dE" 
    // ^ USER NEEDS TO GENERATE A CLIENT TOKEN FROM DASHBOARD OR SERVER, 
    // THE API KEY/SECRET CANNOT BE USED DIRECTLY ON CLIENT SIDE. 

    fun init(context: Context) {
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
                            publishAudio()
                        }
                        is RoomEvent.Disconnected -> {
                            Log.d(TAG, "Disconnected from LiveKit")
                        }
                        is RoomEvent.TrackSubscribed -> {
                            Log.d(TAG, "Subscribed to track: ${event.track.name}")
                        }
                        else -> {}
                    }
                }

                room?.connect(LIVEKIT_URL, token)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to LiveKit", e)
            }
        }
    }

    private fun publishAudio() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Publish local microphone
                val localTrack = room?.localParticipant?.createAudioTrack("microphone")
                localTrack?.start()
                room?.localParticipant?.publishAudioTrack(localTrack!!)
                Log.d(TAG, "Microphone published")
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
