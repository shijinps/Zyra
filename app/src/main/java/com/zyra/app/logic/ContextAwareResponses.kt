package com.zyra.app.logic

import android.content.Context

/**
 * Context-Aware Responses - Provides intelligent responses based on history
 * Makes Z.A.Y. remember context and provide smarter answers
 */
object ContextAwareResponses {
    
    /**
     * Get smart response based on history and context
     */
    fun getSmartResponse(
        context: Context,
        query: String,
        currentContext: Map<String, Any> = emptyMap()
    ): String? {
        // Check if user asked this before
        if (MemoryController.hasAskedBefore(context, query, 7)) {
            val previousAnswer = MemoryController.getPreviousAnswer(context, query)
            if (previousAnswer != null) {
                return PersonalityController.personalize(
                    "You asked this last week. $previousAnswer"
                )
            }
        }
        
        // Check for context references ("her", "there", "that", etc.)
        val contextResponse = resolveContextReferences(context, query)
        if (contextResponse != null) {
            return contextResponse
        }
        
        return null
    }
    
    /**
     * Resolve context references like "her", "there", "that"
     */
    private fun resolveContextReferences(context: Context, query: String): String? {
        val lowerQuery = query.lowercase()
        
        // "Call her" - resolve "her" from last conversation
        if (lowerQuery.contains("call her") || lowerQuery.contains("call him")) {
            val lastCall = getLastAction(context, "CALL")
            if (lastCall != null) {
                return "Calling $lastCall"
            }
        }
        
        // "Navigate there" - resolve "there" from last navigation
        if (lowerQuery.contains("navigate there") || lowerQuery.contains("take me there")) {
            val lastNavigation = getLastAction(context, "NAVIGATE")
            if (lastNavigation != null) {
                return "Navigating to $lastNavigation"
            }
        }
        
        // "Play that again" - resolve "that" from last music
        if (lowerQuery.contains("play that") || lowerQuery.contains("that song")) {
            val lastSong = getLastAction(context, "MUSIC")
            if (lastSong != null) {
                return "Playing $lastSong"
            }
        }
        
        return null
    }
    
    /**
     * Get last action of specific type
     */
    private fun getLastAction(context: Context, actionType: String): String? {
        val history = MemoryController.getConversationHistory(context, 50)
        return history.firstOrNull { it.intentType == actionType }?.userInput
    }
    
    /**
     * Get suggestions based on current context
     */
    fun getSuggestions(
        context: Context,
        currentTime: Long,
        currentLocation: String? = null
    ): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        
        // Get pattern-based suggestions
        val patternSuggestions = PatternLearner.getProactiveSuggestions(context)
        patternSuggestions.forEach { suggestion ->
            suggestions.add(
                Suggestion(
                    type = SuggestionType.PATTERN,
                    message = suggestion,
                    confidence = 0.8
                )
            )
        }
        
        // Get time-based suggestions
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = currentTime
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        
        when (hour) {
            in 6..9 -> {
                // Morning suggestions
                suggestions.add(
                    Suggestion(
                        type = SuggestionType.TIME_BASED,
                        message = "Good morning! Want the weather and news?",
                        confidence = 0.7
                    )
                )
            }
            in 17..19 -> {
                // Evening suggestions
                suggestions.add(
                    Suggestion(
                        type = SuggestionType.TIME_BASED,
                        message = "Traffic to home is usually heavy now. Want me to check?",
                        confidence = 0.6
                    )
                )
            }
        }
        
        return suggestions.sortedByDescending { it.confidence }
    }
    
    /**
     * Format conversation history for speech
     */
    fun formatHistoryForSpeech(
        context: Context,
        query: String
    ): String {
        val history = MemoryController.searchHistory(context, query)
        
        return when {
            history.isEmpty() -> "I don't have any history for that"
            history.size == 1 -> {
                val conv = history[0]
                "You asked: ${conv.userInput}. I said: ${conv.zyraResponse}"
            }
            else -> {
                val recent = history.take(3)
                val formatted = recent.mapIndexed { index, conv ->
                    "${index + 1}. ${conv.userInput}"
                }.joinToString(". ")
                "Here are your recent queries: $formatted"
            }
        }
    }
    
    data class Suggestion(
        val type: SuggestionType,
        val message: String,
        val confidence: Double
    )
    
    enum class SuggestionType {
        PATTERN,
        TIME_BASED,
        LOCATION_BASED,
        CONTEXT_BASED
    }
}
