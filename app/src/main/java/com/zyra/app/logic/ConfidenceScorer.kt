package com.zyra.app.logic

import com.zyra.app.logic.IntentType

/**
 * Confidence-Based Clarification System
 * Handles low-confidence predictions with smart clarification
 */
object ConfidenceScorer {
    
    data class IntentPrediction(
        val intent: IntentType,
        val confidence: Float,
        val alternatives: List<Pair<IntentType, Float>>
    )
    
    /**
     * Predict intent with confidence score
     */
    fun predictWithConfidence(query: String): IntentPrediction {
        // This would integrate with IntentClassifier
        // For now, simplified implementation
        
        val primaryIntent = com.zyra.app.logic.ml.IntentClassifier.predict(query)
        
        // Calculate confidence based on various factors
        val confidence = calculateConfidence(query, primaryIntent)
        
        // Get alternative intents (would come from neural network probabilities)
        val alternatives = getAlternativeIntents(query, primaryIntent)
        
        return IntentPrediction(primaryIntent, confidence, alternatives)
    }
    
    /**
     * Calculate confidence score
     */
    private fun calculateConfidence(query: String, intent: IntentType): Float {
        var confidence = 0.5f
        
        // Factor 1: Query length and clarity
        val words = query.split(" ").filter { it.isNotBlank() }
        if (words.size >= 3) confidence += 0.1f
        
        // Factor 2: Keyword match strength
        val hasStrongKeyword = hasStrongKeywordMatch(query, intent)
        if (hasStrongKeyword) confidence += 0.3f
        
        // Factor 3: Context availability
        if (com.zyra.app.data.EnhancedMemoryModule.isInConversation()) {
            confidence += 0.1f
        }
        
        return confidence.coerceIn(0f, 1f)
    }
    
    /**
     * Check for strong keyword match
     */
    private fun hasStrongKeywordMatch(query: String, intent: IntentType): Boolean {
        val lowerQuery = query.lowercase()
        
        val strongKeywords = when (intent) {
            IntentType.CALL -> listOf("call", "dial", "phone")
            IntentType.TIME -> listOf("time", "clock")
            IntentType.DATE -> listOf("date", "day", "today")
            IntentType.BATTERY -> listOf("battery", "charge", "power")
            IntentType.JOKE -> listOf("joke", "funny", "laugh")
            IntentType.ALARM -> listOf("alarm", "wake")
            IntentType.TIMER -> listOf("timer", "countdown")
            else -> listOf()
        }
        
        return strongKeywords.any { lowerQuery.contains(it) }
    }
    
    /**
     * Get alternative intents
     */
    private fun getAlternativeIntents(query: String, primaryIntent: IntentType): List<Pair<IntentType, Float>> {
        // Simplified - would use neural network probabilities in production
        val alternatives = mutableListOf<Pair<IntentType, Float>>()
        
        // Add some plausible alternatives based on keywords
        val lowerQuery = query.lowercase()
        
        if (lowerQuery.contains("call") || lowerQuery.contains("contact")) {
            if (primaryIntent != IntentType.CALL) alternatives.add(IntentType.CALL to 0.4f)
            if (primaryIntent != IntentType.SEND_MESSAGE) alternatives.add(IntentType.SEND_MESSAGE to 0.3f)
        }
        
        if (lowerQuery.contains("time") || lowerQuery.contains("clock")) {
            if (primaryIntent != IntentType.TIME) alternatives.add(IntentType.TIME to 0.4f)
            if (primaryIntent != IntentType.ALARM) alternatives.add(IntentType.ALARM to 0.3f)
        }
        
        return alternatives.sortedByDescending { it.second }.take(2)
    }
    
    /**
     * Generate clarification question
     */
    fun generateClarification(prediction: IntentPrediction, query: String): String? {
        return when {
            prediction.confidence < 0.3f -> {
                "I didn't quite understand that. Could you rephrase?"
            }
            prediction.confidence < 0.6f && prediction.alternatives.isNotEmpty() -> {
                val alt1 = prediction.alternatives[0].first.name.lowercase().replace("_", " ")
                val alt2 = if (prediction.alternatives.size > 1) {
                    prediction.alternatives[1].first.name.lowercase().replace("_", " ")
                } else null
                
                if (alt2 != null) {
                    "Did you want to $alt1 or $alt2?"
                } else {
                    "Did you mean to $alt1?"
                }
            }
            prediction.confidence < 0.7f -> {
                // Partial understanding - ask for missing info
                getMissingInfoQuestion(prediction.intent, query)
            }
            else -> null // High confidence, proceed
        }
    }
    
    /**
     * Get question for missing information
     */
    private fun getMissingInfoQuestion(intent: IntentType, query: String): String? {
        return when (intent) {
            IntentType.CALL -> {
                if (!containsContactName(query)) {
                    "Who would you like me to call?"
                } else null
            }
            IntentType.SEND_MESSAGE -> {
                when {
                    !containsContactName(query) -> "Who should I message?"
                    !containsMessage(query) -> "What should I say?"
                    else -> null
                }
            }
            IntentType.ALARM, IntentType.TIMER -> {
                if (!containsTime(query)) {
                    "For what time?"
                } else null
            }
            else -> null
        }
    }
    
    /**
     * Check if query contains contact name
     */
    private fun containsContactName(query: String): Boolean {
        // Simplified - would check against actual contacts
        val commonNames = listOf("mom", "dad", "boss", "john", "mary")
        return commonNames.any { query.lowercase().contains(it) }
    }
    
    /**
     * Check if query contains message
     */
    private fun containsMessage(query: String): Boolean {
        // Check if query has enough words to be a message
        val words = query.split(" ").filter { it.isNotBlank() }
        return words.size > 3
    }
    
    /**
     * Check if query contains time
     */
    private fun containsTime(query: String): Boolean {
        val timePatterns = listOf(
            Regex("\\d+\\s*(am|pm|hour|minute|second)"),
            Regex("\\d+:\\d+"),
            Regex("(morning|evening|afternoon|night)")
        )
        
        return timePatterns.any { it.containsMatchIn(query.lowercase()) }
    }
    
    /**
     * Should ask for confirmation
     */
    fun shouldConfirm(prediction: IntentPrediction, intent: IntentType): Boolean {
        // Confirm for critical actions with low confidence
        val criticalIntents = listOf(
            IntentType.CALL,
            IntentType.SEND_MESSAGE,
            IntentType.CUT_CALL,
            IntentType.UNLOCK_DEVICE
        )
        
        return intent in criticalIntents && prediction.confidence < 0.8f
    }
}
