package com.zyra.app.data

import android.content.Context
import com.zyra.app.logic.IntentType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Enhanced Memory Module with Contextual Conversation Threading
 * Tracks conversation context, entities, and enables pronoun resolution
 */
object EnhancedMemoryModule {
    
    private const val CONTEXT_FILE = "zyra_context_data.json"
    private const val MAX_CONTEXT_STACK = 10
    
    data class ConversationContext(
        val topic: String,
        val entities: MutableMap<String, String>, // "contact" -> "Mom", "time" -> "6 AM"
        val lastIntent: IntentType,
        val timestamp: Long,
        var resolved: Boolean = false,
        val userQuery: String = ""
    )
    
    private val contextStack = mutableListOf<ConversationContext>()
    private var currentContext: ConversationContext? = null
    
    /**
     * Initialize the enhanced memory system
     */
    fun init(context: Context) {
        load(context)
    }
    
    /**
     * Add a new conversation context
     */
    fun addContext(
        intent: IntentType, 
        entities: Map<String, String>,
        userQuery: String = ""
    ) {
        val context = ConversationContext(
            topic = intent.name,
            entities = entities.toMutableMap(),
            lastIntent = intent,
            timestamp = System.currentTimeMillis(),
            userQuery = userQuery
        )
        
        contextStack.add(context)
        currentContext = context
        
        // Maintain stack size
        if (contextStack.size > MAX_CONTEXT_STACK) {
            contextStack.removeAt(0)
        }
    }
    
    /**
     * Resolve entity from conversation history
     * Example: "Call him" -> resolves "him" from previous "contact" entity
     */
    fun resolveEntity(type: String): String? {
        // Search backwards through context for entity
        return contextStack.reversed()
            .firstOrNull { it.entities.containsKey(type) }
            ?.entities?.get(type)
    }
    
    /**
     * Resolve pronouns to actual entities
     * "him", "her", "it", "them" -> actual contact/object name
     */
    fun resolvePronoun(pronoun: String): String? {
        val lowerPronoun = pronoun.lowercase()
        
        return when (lowerPronoun) {
            "him", "her", "them" -> resolveEntity("contact")
            "it", "that", "this" -> resolveEntity("object") ?: resolveEntity("app")
            "there" -> resolveEntity("location")
            else -> null
        }
    }
    
    /**
     * Get the last intent for context chaining
     * Example: "Set alarm" -> "For what time?" -> "6 AM" (chains to alarm intent)
     */
    fun getLastIntent(): IntentType? {
        return currentContext?.lastIntent
    }
    
    /**
     * Check if we're in the middle of a multi-turn conversation
     */
    fun isInConversation(): Boolean {
        val lastContext = contextStack.lastOrNull() ?: return false
        val timeSinceLastInteraction = System.currentTimeMillis() - lastContext.timestamp
        return timeSinceLastInteraction < 60000 // 60 seconds timeout
    }
    
    /**
     * Get conversation topic for context
     */
    fun getCurrentTopic(): String? {
        return currentContext?.topic
    }
    
    /**
     * Mark current context as resolved
     */
    fun markResolved() {
        currentContext?.resolved = true
    }
    
    /**
     * Get all entities from current context
     */
    fun getCurrentEntities(): Map<String, String> {
        return currentContext?.entities?.toMap() ?: emptyMap()
    }
    
    /**
     * Update entity in current context
     */
    fun updateEntity(key: String, value: String) {
        currentContext?.entities?.put(key, value)
    }
    
    /**
     * Get conversation summary for LLM context
     */
    fun getConversationSummary(lastN: Int = 3): String {
        return contextStack.takeLast(lastN)
            .joinToString("\n") { ctx ->
                "User: ${ctx.userQuery} | Intent: ${ctx.lastIntent.name} | Entities: ${ctx.entities}"
            }
    }
    
    /**
     * Clear conversation context (session end)
     */
    fun clearContext() {
        currentContext = null
        // Keep history but mark as old
    }
    
    /**
     * Get related contexts by intent type
     */
    fun getRelatedContexts(intent: IntentType, limit: Int = 3): List<ConversationContext> {
        return contextStack.reversed()
            .filter { it.lastIntent == intent }
            .take(limit)
    }
    
    /**
     * Infer missing information from context
     * Example: "Call" without name -> check if previous context had a contact
     */
    fun inferMissingInfo(intent: IntentType, currentEntities: Map<String, String>): Map<String, String> {
        val inferred = currentEntities.toMutableMap()
        
        when (intent) {
            IntentType.CALL -> {
                if (!inferred.containsKey("contact")) {
                    resolveEntity("contact")?.let { inferred["contact"] = it }
                }
            }
            IntentType.SEND_MESSAGE -> {
                if (!inferred.containsKey("contact")) {
                    resolveEntity("contact")?.let { inferred["contact"] = it }
                }
                if (!inferred.containsKey("message")) {
                    resolveEntity("message")?.let { inferred["message"] = it }
                }
            }
            IntentType.ALARM, IntentType.TIMER -> {
                if (!inferred.containsKey("time")) {
                    resolveEntity("time")?.let { inferred["time"] = it }
                }
            }
            else -> {}
        }
        
        return inferred
    }
    
    /**
     * Save context to persistent storage
     */
    fun save(context: Context) {
        try {
            val json = JSONObject()
            val contextsArray = JSONArray()
            
            contextStack.forEach { ctx ->
                val ctxObj = JSONObject().apply {
                    put("topic", ctx.topic)
                    put("lastIntent", ctx.lastIntent.name)
                    put("timestamp", ctx.timestamp)
                    put("resolved", ctx.resolved)
                    put("userQuery", ctx.userQuery)
                    
                    val entitiesObj = JSONObject()
                    ctx.entities.forEach { (k, v) -> entitiesObj.put(k, v) }
                    put("entities", entitiesObj)
                }
                contextsArray.put(ctxObj)
            }
            
            json.put("contexts", contextsArray)
            
            val file = File(context.filesDir, CONTEXT_FILE)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Load context from persistent storage
     */
    private fun load(context: Context) {
        try {
            val file = File(context.filesDir, CONTEXT_FILE)
            if (!file.exists()) return
            
            val json = JSONObject(file.readText())
            val contextsArray = json.optJSONArray("contexts") ?: return
            
            contextStack.clear()
            
            for (i in 0 until contextsArray.length()) {
                val ctxObj = contextsArray.getJSONObject(i)
                
                val entities = mutableMapOf<String, String>()
                val entitiesObj = ctxObj.optJSONObject("entities")
                entitiesObj?.keys()?.forEach { key ->
                    entities[key] = entitiesObj.getString(key)
                }
                
                val ctx = ConversationContext(
                    topic = ctxObj.getString("topic"),
                    entities = entities,
                    lastIntent = IntentType.valueOf(ctxObj.getString("lastIntent")),
                    timestamp = ctxObj.getLong("timestamp"),
                    resolved = ctxObj.getBoolean("resolved"),
                    userQuery = ctxObj.optString("userQuery", "")
                )
                
                contextStack.add(ctx)
            }
            
            // Set current context to most recent
            currentContext = contextStack.lastOrNull()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
