package com.zyra.app.logic

import com.zyra.app.logic.IntentType

/**
 * Explainability & Transparency Engine
 * Provides reasoning traces and explanations for decisions
 */
object ExplainabilityEngine {
    
    data class ReasoningTrace(
        val decision: String,
        val confidence: Float,
        val reasoning: List<String>,
        val alternatives: List<String>,
        val contextUsed: Map<String, String> = emptyMap()
    )
    
    private val decisionHistory = mutableListOf<ReasoningTrace>()
    
    /**
     * Explain a decision
     */
    fun explainDecision(
        intent: IntentType,
        query: String,
        confidence: Float,
        contextUsed: Boolean = false,
        entities: Map<String, String> = emptyMap()
    ): String {
        val reasoning = mutableListOf<String>()
        
        reasoning.add("You said: '$query'")
        reasoning.add("I detected intent: ${intent.name.lowercase().replace("_", " ")}")
        reasoning.add("Confidence: ${(confidence * 100).toInt()}%")
        
        if (contextUsed) {
            reasoning.add("I used context from our previous conversation")
        }
        
        if (entities.isNotEmpty()) {
            val entityStr = entities.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            reasoning.add("Extracted information: $entityStr")
        }
        
        return reasoning.joinToString(". ")
    }
    
    /**
     * Explain why an action was taken
     */
    fun explainAction(action: String, trigger: String, conditions: List<String> = emptyList()): String {
        val parts = mutableListOf<String>()
        
        parts.add("I $action because $trigger")
        
        if (conditions.isNotEmpty()) {
            parts.add("Conditions met: ${conditions.joinToString(", ")}")
        }
        
        return parts.joinToString(". ")
    }
    
    /**
     * Explain a learning event
     */
    fun explainLearning(what: String, from: String): String {
        return "I learned that $what from $from"
    }
    
    /**
     * Get alternative suggestions
     */
    fun getAlternatives(intent: IntentType, query: String): List<String> {
        val alternatives = mutableListOf<String>()
        
        when (intent) {
            IntentType.CALL -> {
                alternatives.add("Send a message instead")
                alternatives.add("Check contact details")
            }
            IntentType.SEND_MESSAGE -> {
                alternatives.add("Call instead")
                alternatives.add("Schedule message for later")
            }
            IntentType.ALARM -> {
                alternatives.add("Set a timer instead")
                alternatives.add("Add calendar event")
            }
            else -> {}
        }
        
        return alternatives
    }
    
    /**
     * Record decision for transparency
     */
    fun recordDecision(trace: ReasoningTrace) {
        decisionHistory.add(trace)
        
        // Keep last 50 decisions
        if (decisionHistory.size > 50) {
            decisionHistory.removeAt(0)
        }
    }
    
    /**
     * Get recent decisions
     */
    fun getRecentDecisions(count: Int = 10): List<ReasoningTrace> {
        return decisionHistory.takeLast(count)
    }
    
    /**
     * Explain why something failed
     */
    fun explainFailure(action: String, reason: String, suggestion: String? = null): String {
        var explanation = "I couldn't $action because $reason"
        
        if (suggestion != null) {
            explanation += ". $suggestion"
        }
        
        return explanation
    }
}
