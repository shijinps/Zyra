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
                val checkServerTool = object : io.elevenlabs.ClientTool {
                    override suspend fun execute(parameters: Map<String, Any>): io.elevenlabs.ClientToolResult {
                        Log.d(TAG, "Tool called: check_server with $parameters")
                        val result = handleClientTool("check_server", parameters)
                        return io.elevenlabs.ClientToolResult.success(result)
                    }
                }
                
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
                    clientTools = mapOf("check_server" to checkServerTool)
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
        
        // 2. Try Manual Session End (Best Effort)
        try {
             val endMethod = currentSession?.javaClass?.methods?.find { it.name == "endSession" }
             if (endMethod != null) {
                 // Kotlin suspend functions take a Continuation as the last argument.
                 // Java reflection validation of arg count will fail if we don't account for it.
                 // If it requires args, we can't easily call it via simple reflection.
                 Log.d(TAG, "Found endSession method: ${endMethod.name}, args: ${endMethod.parameterTypes.size}")
                 
                 if (endMethod.parameterTypes.isEmpty()) {
                     endMethod.invoke(currentSession)
                 } else {
                     Log.w(TAG, "endSession requires arguments (likely suspend). Cannot invoke easily via reflection.")
                     // Debug listing to confirm
                     currentSession?.javaClass?.methods?.forEach { method ->
                        if (method.name.contains("Session")) {
                             Log.e(TAG, "Method: ${method.name} -> Args: ${method.parameterTypes.joinToString { it.simpleName }}")
                        }
                     }
                 }
             } else {
                 Log.e(TAG, "No endSession method found!")
                 // Log all methods to see what IS available
                 currentSession?.javaClass?.methods?.forEach { method ->
                     Log.e(TAG, "Method: ${method.name}")
                 }
             }
        } catch (e: Exception) {
             Log.e(TAG, "Could not end session", e)
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
