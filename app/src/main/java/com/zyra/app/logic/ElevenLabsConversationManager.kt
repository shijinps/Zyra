package com.zyra.app.logic

import android.content.Context
import android.util.Log
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
// import io.elevenlabs.Conversation
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

    private var currentSession: Any? = null
    private var conversationJob: kotlinx.coroutines.Job? = null

    fun startConversation(context: Context, agentId: String) {
        if (!isInitialized) init(context)
        

        
        conversationJob = CoroutineScope(Dispatchers.IO).launch {
            _connectionState.value = ConnectionState.CONNECTING
            try {
                Log.d(TAG, "Starting conversation with Agent: $agentId")
                
                // Client Tool Implementation
// Client Tool removed: Configuring as Webhook directly in ElevenLabs Dashboard
                
                val config = ConversationConfig(
                    agentId = agentId,
                    onConnect = { conversationId ->
                        Log.d(TAG, "Session Started: $conversationId")
                        _connectionState.value = ConnectionState.CONNECTED
                        eventListener?.onConversationStarted()
                    },
                    onDisconnect = { reason ->
                        Log.d(TAG, "Session Ended: $reason")
                        _connectionState.value = ConnectionState.DISCONNECTED
                        eventListener?.onConversationStopped()
                        currentSession = null
                    },
                    clientTools = emptyMap()
                )
                
                // Correct Signature: Config, then Context
                currentSession = ConversationClient.startSession(config, context)
                
                Log.d(TAG, "Conversation Session Created")


            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: Failed to start conversation", e)
                _connectionState.value = ConnectionState.ERROR
                eventListener?.onError(e.message ?: "Unknown start error")
            }
        }
    }

    fun stopConversation() {
        Log.d(TAG, "Stopping conversation")
        
        // 1. Cancel the Job (Interrupts startSession if suspended)
        conversationJob?.cancel()
        conversationJob = null
        
        // 2. Reflection Stop (Handling Suspend Function)
        // Since we cannot import 'Conversation' directly (Unresolved Reference),
        // we use reflection to call 'endSession(Continuation)'.
        try {
            val session = currentSession ?: return
            val endMethod = session.javaClass.methods.find { it.name == "endSession" }
            
            if (endMethod != null) {
                Log.d(TAG, "Invoking endSession via reflection")
                
                // Create a dummy Continuation
                val continuation = object : kotlin.coroutines.Continuation<Unit> {
                    override val context = kotlin.coroutines.EmptyCoroutineContext
                    override fun resumeWith(result: Result<Unit>) {
                        if (result.isFailure) {
                            Log.e(TAG, "endSession failed", result.exceptionOrNull())
                        } else {
                            Log.d(TAG, "endSession completed successfully")
                        }
                    }
                }
                
                // Invoke: endSession(Continuation)
                // Note: Suspend functions compile to methods taking Continuation as last param.
                if (endMethod.parameterTypes.isNotEmpty()) {
                    endMethod.invoke(session, continuation)
                } else {
                    // Fallback if not suspend (unlikely given logs)
                    endMethod.invoke(session)
                }
            } else {
                Log.e(TAG, "No endSession method found during cleanup")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end session via reflection", e)
        }
        
        currentSession = null
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

    fun getStatusReport(): String {
        return """
            ElevenLabs Link Status:
            -----------------------
            SDK Initialized: $isInitialized
            Connection State: ${_connectionState.value}
            Session Object: ${if (currentSession != null) "Active" else "Null"}
            Last Event: ${eventListener?.let { "Listener Attached" } ?: "No Listener"}
        """.trimIndent()
    }
}
