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
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.converter.gson.GsonConverterFactory

object LiveKitManager {
    private const val TAG = "LiveKitManager"
    private var room: Room? = null
    private var eventJob: Job? = null
    private var healthMonitorJob: Job? = null
    private var activeAudioTrackSid: String? = null
    
    // URL to your LiveKit Cloud or Self-hosted Instance
    private const val LIVEKIT_URL = "wss://zyra-sbn0wnp2.livekit.cloud" 
    
    // BASE URL for your FastAPI Brain (where /livekit/token endpoint lives)
    private const val BRAIN_BASE_URL = "https://apizyra.shijin.in" 
    private const val BRAIN_SECRET = "zyra-secret-key-123"

    // High-performance pooled HTTP client for ultra-low latency token exchange
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Zyra-Android/1.0")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl(BRAIN_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.zyra.app.network.ZyraApiService::class.java)
    }

    private var cachedToken: String? = null
    private var cachedTokenTime: Long = 0L
    private const val TOKEN_MAX_AGE_MS = 20 * 60 * 1000L // 20 minutes

    fun prefetchToken() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Pre-warming token ONLY (dispatch = false) to prevent spawning premature agent workers
                val response = apiService.getLiveKitToken(BRAIN_SECRET, dispatch = false).execute()
                if (response.isSuccessful && response.body() != null) {
                    cachedToken = response.body()!!.token
                    cachedTokenTime = System.currentTimeMillis()
                    Log.d(TAG, "LiveKit token pre-warmed and ready.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Prefetch token non-fatal error: ${e.message}")
            }
        }
    }

    interface LiveKitListener {
        fun onConnected()
        fun onDisconnected()
        fun onError(error: String)
        fun onAgentUnavailable()
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
        healthMonitorJob?.cancel()
        healthMonitorJob = null
        eventJob?.cancel()
        eventJob = null
        activeAudioTrackSid = null
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
            // Asynchronously disconnect old room without blocking
            val oldRoom = room
            room = null
            healthMonitorJob?.cancel()
            healthMonitorJob = null
            eventJob?.cancel()
            eventJob = null
            activeAudioTrackSid = null
            if (oldRoom != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try { oldRoom.disconnect() } catch (_: Exception) {}
                }
            }

            try {
                var token: String? = null
                val now = System.currentTimeMillis()
                if (cachedToken != null && (now - cachedTokenTime) < TOKEN_MAX_AGE_MS) {
                    token = cachedToken
                    Log.d(TAG, "Using cached pre-warmed LiveKit token (instant connect)")
                    prefetchToken() // refresh for next turn
                } else {
                    Log.d(TAG, "Requesting fresh LiveKit token from Brain...")
                    var lastErr = ""
                    for (attempt in 1..2) {
                        try {
                            val response = withContext(Dispatchers.IO) {
                                apiService.getLiveKitToken(BRAIN_SECRET, dispatch = true).execute()
                            }
                            if (response.isSuccessful && response.body() != null) {
                                token = response.body()!!.token
                                cachedToken = token
                                cachedTokenTime = System.currentTimeMillis()
                                break
                            } else {
                                lastErr = "HTTP ${response.code()}"
                            }
                        } catch (e: Exception) {
                            lastErr = e.message ?: "Network error"
                        }
                        if (attempt < 2) kotlinx.coroutines.delay(300L)
                    }

                    if (token == null) {
                        val errorMsg = "Server failed to grant token: $lastErr"
                        Log.e(TAG, errorMsg)
                        listener?.onError(errorMsg)
                        return@launch
                    }
                }
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
                                Log.d(TAG, "Connected to LiveKit Room: ${currentRoom.name}, SID: ${currentRoom.sid}. remoteParticipants at connect=${currentRoom.remoteParticipants.size}")
                                listener?.onConnected()
                                publishAudio()
                                startAgentHealthMonitor(currentRoom)
                            }
                            is RoomEvent.Disconnected -> {
                                Log.d(TAG, "Disconnected from LiveKit. Reason: ${event.reason}, Error: ${event.error?.message}")
                                activeAudioTrackSid = null
                                listener?.onDisconnected()
                            }
                            is RoomEvent.FailedToConnect -> {
                                Log.e(TAG, "Failed to connect to LiveKit: ${event.error.message}")
                                activeAudioTrackSid = null
                                listener?.onError(event.error.message ?: "Unknown Connection Error")
                            }
                            is RoomEvent.ParticipantConnected -> {
                                Log.d(TAG, "Participant joined: identity=${event.participant.identity?.value}, name=${event.participant.name}")
                            }
                            is RoomEvent.ParticipantDisconnected -> {
                                Log.d(TAG, "Participant left: identity=${event.participant.identity?.value}")
                            }
                            is RoomEvent.TrackSubscribed -> {
                                Log.d(TAG, "Subscribed to track: kind=${event.track.kind}, name=${event.track.name}, sid=${event.track.sid}")
                                if (event.track is io.livekit.android.room.track.RemoteAudioTrack || event.track.kind == io.livekit.android.room.track.Track.Kind.AUDIO) {
                                    val remoteAudio = event.track as? io.livekit.android.room.track.RemoteAudioTrack
                                    if (activeAudioTrackSid == null) {
                                        activeAudioTrackSid = event.track.sid
                                        Log.i(TAG, "Primary Voice Agent audio track assigned: ${event.track.sid}. Cancelling health monitor timeout.")
                                        healthMonitorJob?.cancel()
                                    } else if (activeAudioTrackSid != event.track.sid) {
                                        // Extra agent track detected: mute it to prevent phase echo
                                        Log.w(TAG, "Duplicate remote audio track detected (${event.track.sid}), disabling to prevent echo!")
                                        try {
                                            remoteAudio?.enabled = false
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Could not disable duplicate audio track: ${e.message}")
                                        }
                                    }
                                }
                            }
                            is RoomEvent.TrackUnsubscribed -> {
                                Log.d(TAG, "Unsubscribed from track: kind=${event.track.kind}, name=${event.track.name}")
                                if (event.track.sid == activeAudioTrackSid) {
                                    activeAudioTrackSid = null
                                }
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

                currentRoom.connect(LIVEKIT_URL, token!!)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to LiveKit", e)
                listener?.onError(e.message ?: "Exception during connect")
            }
        }
    }

    private fun startAgentHealthMonitor(monitoredRoom: Room) {
        healthMonitorJob?.cancel()
        healthMonitorJob = CoroutineScope(Dispatchers.Main).launch {
            // 5s check
            kotlinx.coroutines.delay(5000L)
            if (room !== monitoredRoom) return@launch
            val count5s = monitoredRoom.remoteParticipants.size
            Log.d(TAG, "Room health check [5s]: remoteParticipants=$count5s")

            // 10s check
            kotlinx.coroutines.delay(5000L)
            if (room !== monitoredRoom) return@launch
            val count10s = monitoredRoom.remoteParticipants.size
            Log.d(TAG, "Room health check [10s]: remoteParticipants=$count10s")

            // 15s check - give worker sufficient grace to dispatch and establish audio
            kotlinx.coroutines.delay(5000L)
            if (room !== monitoredRoom) return@launch
            val count15s = monitoredRoom.remoteParticipants.size
            val hasRemoteAudio = monitoredRoom.remoteParticipants.values.any { p ->
                p.audioTrackPublications.isNotEmpty()
            }
            Log.d(TAG, "Room health check [15s]: remoteParticipants=$count15s, hasRemoteAudioTrack=$hasRemoteAudio")

            if (count15s == 0 || !hasRemoteAudio) {
                Log.w(TAG, "Voice agent not available after 15s (count=$count15s, hasRemoteAudio=$hasRemoteAudio)")
                listener?.onAgentUnavailable()
            }
        }
    }

    private fun publishAudio() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Enable hardware Acoustic Echo Cancellation (AEC) and Noise Suppression (NS)
                val audioOptions = io.livekit.android.room.track.LocalAudioTrackOptions(
                    noiseSuppression = true, 
                    echoCancellation = true,
                    autoGainControl = true,
                    highPassFilter = true
                )
                val localTrack = room?.localParticipant?.createAudioTrack("microphone", audioOptions)
                localTrack?.start()
                room?.localParticipant?.publishAudioTrack(localTrack!!)
                Log.d(TAG, "Microphone published with Echo Cancellation and Noise Suppression enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish audio", e)
            }
        }
    }
}
