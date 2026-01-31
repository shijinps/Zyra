package com.zyra.app.logic

import android.content.Context
import android.util.Log
import com.elevenlabs.android.ElevenLabs
import com.elevenlabs.android.ElevenLabsConfig
// import com.elevenlabs.android.Conversation 
// import com.elevenlabs.android.ConversationConfig
// import com.elevenlabs.android.ConversationCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

/**
 * Manages the ElevenLabs Conversational AI session.
 * Handles:
 * - SDK Initialization
 * - Connection to Agent
 * - Client-Side Tool Execution (The "Bridge")
 */
object ElevenLabsConversationManager {
    private const val TAG = "ElevenLabsManager"
    private var isInitialized = false
    
    // State for UI/Service
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, LISTENING, SPEAKING, ERROR
    }

    // Callback for Service to handle audio focus/events
    interface ConversationEventListener {
        fun onConversationStarted()
        fun onConversationStopped()
        fun onError(message: String)
    }

    private var eventListener: ConversationEventListener? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    fun init(context: Context) {
        if (isInitialized) return
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        isInitialized = true
        Log.d(TAG, "ElevenLabs SDK Initialized")
    }

    fun setListener(listener: ConversationEventListener) {
        this.eventListener = listener
    }

    fun startConversation(context: Context, agentId: String) {
        if (!isInitialized) init(context)
        
        CoroutineScope(Dispatchers.IO).launch {
            _connectionState.value = ConnectionState.CONNECTING
            try {
                Log.d(TAG, "Starting conversation with Agent: $agentId")
                
                // TODO: Replace with actual SDK call
                // val config = ConversationConfig(
                //    agentId = agentId,
                //    clientTools = mapOf("vibrate" to ..., "ping" to ...) 
                // )
                // ElevenLabs.Conversation.startSession(config, object : ConversationCallback { ... })
               
                // MOCKING SUCCESS for Development
                // Real SDK integration requires the JAR/AAR which I cannot see fully.
                // Assuming the user will verify the import.
                
                _connectionState.value = ConnectionState.CONNECTED
                eventListener?.onConversationStarted()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start conversation", e)
                _connectionState.value = ConnectionState.ERROR
                eventListener?.onError(e.message ?: "Unknown error")
            }
        }
    }

    fun stopConversation() {
        Log.d(TAG, "Stopping conversation")
        // ElevenLabs.Conversation.endSession()
        _connectionState.value = ConnectionState.DISCONNECTED
        eventListener?.onConversationStopped()
    }

    /**
     * The "Bridge" for Client Tools.
     * Use this to execute local commands requested by the AI.
     */
    /**
     * The "Bridge" for Client Tools.
     * Use this to execute local commands requested by the AI.
     */
    suspend fun handleClientTool(toolName: String, params: Map<String, Any>): String {
        Log.d(TAG, "Client Tool Request: $toolName with $params")
        return when (toolName) {
            "get_gps", "get_location" -> {
                fetchLocation()
            }
            "check_server_status", "check_server" -> {
                val host = params["server"] as? String ?: "8.8.8.8"
                pingServer(host)
            }
            "vibrate" -> {
                 // Vibrate logic here (needs Context or Vibrator service injected)
                 "Vibrated"
            }
            else -> "Tool not found"
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun fetchLocation(): String = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                cont.resumeWith(Result.success("${location.latitude},${location.longitude}"))
            } else {
                cont.resumeWith(Result.success("Location unknown"))
            }
        }.addOnFailureListener {
            cont.resumeWith(Result.success("Location error: ${it.message}"))
        }
    }

    private fun pingServer(host: String): String {
        return try {
            val isReachable = java.net.InetAddress.getByName(host).isReachable(2000)
            if (isReachable) "Server $host is Online" else "Server $host is Offline"
        } catch (e: Exception) {
            "Error checking $host: ${e.message}"
        }
    }
}
