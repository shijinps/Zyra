package com.zyra.app.logic

import android.content.Context

/**
 * Memory Controller - Manages conversation history and learning
 * Enables Zyra to remember past interactions and provide context-aware responses
 */
object MemoryController {
    
    private var database: MemoryDatabase? = null
    
    /**
     * Initialize memory system
     */
    fun initialize(context: Context) {
        if (database == null) {
            database = MemoryDatabase(context.applicationContext)
        }
    }
    
    /**
     * Save conversation to memory
     */
    fun saveConversation(
        context: Context,
        userInput: String,
        zyraResponse: String,
        intentType: String,
        success: Boolean = true,
        contextInfo: String? = null
    ) {
        initialize(context)
        database?.saveConversation(userInput, zyraResponse, intentType, success, contextInfo)
    }
    
    /**
     * Save action to memory
     */
    fun saveAction(
        context: Context,
        actionType: String,
        actionData: String?,
        location: String? = null,
        success: Boolean = true
    ) {
        initialize(context)
        database?.saveAction(actionType, actionData, location, success)
    }
    
    /**
     * Save search query
     */
    fun saveSearch(
        context: Context,
        searchType: String,
        query: String,
        result: String?
    ) {
        initialize(context)
        database?.saveSearch(searchType, query, result)
    }
    
    /**
     * Get conversation history
     */
    fun getConversationHistory(
        context: Context,
        limit: Int = 100
    ): List<MemoryDatabase.Conversation> {
        initialize(context)
        return database?.getRecentConversations(limit) ?: emptyList()
    }
    
    /**
     * Search conversation history
     */
    fun searchHistory(
        context: Context,
        query: String
    ): List<MemoryDatabase.Conversation> {
        initialize(context)
        return database?.searchConversations(query) ?: emptyList()
    }
    
    /**
     * Check if user has asked this before
     */
    fun hasAskedBefore(
        context: Context,
        query: String,
        withinDays: Int = 7
    ): Boolean {
        initialize(context)
        val history = searchHistory(context, query)
        val cutoffTime = System.currentTimeMillis() - (withinDays * 24 * 60 * 60 * 1000L)
        return history.any { it.timestamp > cutoffTime }
    }
    
    /**
     * Get previous answer for similar query
     */
    fun getPreviousAnswer(
        context: Context,
        query: String
    ): String? {
        initialize(context)
        val history = searchHistory(context, query)
        return history.firstOrNull()?.zyraResponse
    }
    
    /**
     * Get context from last conversation
     */
    fun getLastContext(context: Context): String? {
        initialize(context)
        val history = getConversationHistory(context, 1)
        return history.firstOrNull()?.userInput
    }
    
    /**
     * Clear all memory (for privacy)
     */
    fun clearAllMemory(context: Context) {
        initialize(context)
        database?.clearAllData()
    }
    
    /**
     * Get smart response based on history
     */
    fun getSmartResponse(
        context: Context,
        query: String
    ): String? {
        // Check if user asked this before
        if (hasAskedBefore(context, query, 7)) {
            val previousAnswer = getPreviousAnswer(context, query)
            if (previousAnswer != null) {
                return "You asked this before. $previousAnswer"
            }
        }
        return null
    }
}
