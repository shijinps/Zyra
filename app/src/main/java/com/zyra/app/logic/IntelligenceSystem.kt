package com.zyra.app.logic

import android.content.Context
import com.zyra.app.data.ContactRepository
import com.zyra.app.data.EnhancedMemoryModule
import com.zyra.app.data.SmartContactManager

/**
 * Intelligence System Initializer
 * Initializes all intelligence modules in the correct order
 */
object IntelligenceSystem {
    
    private var isInitialized = false
    
    /**
     * Initialize all intelligence modules
     */
    fun initialize(context: Context, contactRepository: ContactRepository) {
        if (isInitialized) return
        
        try {
            // Phase 1: Core Memory & Context
            EnhancedMemoryModule.init(context)
            
            // Phase 2: Emotional & Learning
            EmotionalIntelligence.load(context)
            ReinforcementLearner.load(context)
            
            // Phase 3: Proactive & Patterns
            ProactiveEngine.load(context)
            
            // Phase 4: Contact & Location
            SmartContactManager.init(context, contactRepository)
            LocationIntelligence.init(context)
            
            // Phase 5: Workflows & Automation
            WorkflowEngine.load(context)
            
            // Phase 6: Initialize Gemini with API key
            GeminiModule.initialize()
            
            isInitialized = true
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Save all module states
     */
    fun saveAll(context: Context) {
        try {
            EnhancedMemoryModule.save(context)
            EmotionalIntelligence.save(context)
            ReinforcementLearner.save(context)
            ProactiveEngine.save(context)
            SmartContactManager.save(context)
            LocationIntelligence.save(context)
            WorkflowEngine.save(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Process query with full intelligence stack
     */
    suspend fun processIntelligentQuery(
        context: Context,
        query: String,
        nlpResult: ParsedIntent
    ): String {
        // 1. Detect emotion
        val emotion = EmotionalIntelligence.detectEmotion(query)
        
        // 2. Get confidence prediction
        val prediction = ConfidenceScorer.predictWithConfidence(query)
        
        // 3. Check if clarification needed
        val clarification = ConfidenceScorer.generateClarification(prediction, query)
        if (clarification != null) {
            return clarification
        }
        
        // 4. Resolve entities from context
        val entities = mutableMapOf<String, String>()
        
        // Check for pronouns and resolve
        val words = query.split(" ")
        words.forEach { word ->
            val resolved = EnhancedMemoryModule.resolvePronoun(word)
            if (resolved != null) {
                entities["resolved_$word"] = resolved
            }
        }
        
        // 5. Add context to memory
        EnhancedMemoryModule.addContext(
            intent = nlpResult.type,
            entities = entities,
            userQuery = query
        )
        
        // 6. Learn pattern for proactive suggestions
        ProactiveEngine.learnPattern(nlpResult.type, entities)
        
        // 7. Use hybrid intelligence for response
        val hybridIntelligence = HybridIntelligence(context)
        val response = hybridIntelligence.answer(query, nlpResult.toString())
        
        // 8. Adapt response based on emotion
        val adaptedResponse = EmotionalIntelligence.adaptResponse(response, emotion)
        
        // 9. Record decision for explainability
        ExplainabilityEngine.recordDecision(
            ExplainabilityEngine.ReasoningTrace(
                decision = nlpResult.type.name,
                confidence = prediction.confidence,
                reasoning = listOf("Processed query: $query"),
                alternatives = prediction.alternatives.map { it.first.name },
                contextUsed = entities
            )
        )
        
        return adaptedResponse
    }
    
    /**
     * Get proactive suggestions
     */
    fun getProactiveSuggestions(): List<String> {
        return ProactiveEngine.getSuggestions()
    }
    
    /**
     * Check and execute workflows
     */
    fun checkWorkflows(context: Context) {
        WorkflowEngine.checkTimeTriggers(context)
    }
    
    /**
     * Handle location change
     */
    fun handleLocationChange(context: Context, lat: Double, lng: Double) {
        val previousPlace = LocationIntelligence.getCurrentPlace()
        LocationIntelligence.updateLocation(lat, lng)
        val currentPlace = LocationIntelligence.getCurrentPlace()
        
        // Check for geofencing triggers
        if (currentPlace != null && currentPlace != previousPlace) {
            WorkflowEngine.checkLocationTriggers(context, currentPlace, entering = true)
        }
        
        if (previousPlace != null && currentPlace != previousPlace) {
            WorkflowEngine.checkLocationTriggers(context, previousPlace, entering = false)
        }
    }
    
    /**
     * Record contact interaction
     */
    fun recordContactInteraction(contactName: String, type: String) {
        when (type) {
            "call" -> SmartContactManager.recordCall(contactName)
            "message" -> SmartContactManager.recordMessage(contactName)
        }
    }
    
    /**
     * Get calendar reminders
     */
    fun getCalendarReminders(context: Context): List<String> {
        val calendarIntelligence = CalendarIntelligence(context)
        val upcomingEvents = calendarIntelligence.getUpcomingEvents(2)
        
        return upcomingEvents.mapNotNull { event ->
            calendarIntelligence.getSmartReminder(event)
        }
    }
}
