package com.zyra.app.logic

import android.content.Context
import com.zyra.app.logic.IntentType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * Proactive Intelligence Engine
 * Learns user patterns and makes predictive suggestions
 */
object ProactiveEngine {
    
    private const val PATTERN_FILE = "zyra_patterns.json"
    private const val MIN_FREQUENCY_FOR_SUGGESTION = 3
    
    data class UserPattern(
        val action: IntentType,
        val context: MutableMap<String, String>, // "day" -> "Sunday", "time" -> "18:00", "location" -> "Home"
        var frequency: Int = 1,
        var lastOccurrence: Long = System.currentTimeMillis(),
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun matches(intent: IntentType, ctx: Map<String, String>): Boolean {
            if (intent != action) return false
            
            // Check if key contexts match
            val keyContexts = listOf("day", "time", "location", "contact")
            return keyContexts.any { key ->
                context[key] == ctx[key] && ctx[key] != null
            }
        }
        
        fun shouldSuggest(now: Calendar): Boolean {
            if (frequency < MIN_FREQUENCY_FOR_SUGGESTION) return false
            
            // Check time match (within 30 minutes)
            val patternTime = context["time"]
            if (patternTime != null) {
                val currentTime = String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
                if (!isTimeNear(currentTime, patternTime, 30)) return false
            }
            
            // Check day match
            val patternDay = context["day"]
            if (patternDay != null) {
                val currentDay = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, java.util.Locale.getDefault())
                if (currentDay != patternDay) return false
            }
            
            // Check location match
            val patternLocation = context["location"]
            if (patternLocation != null) {
                // This would need to be checked against current location
                // For now, we'll skip location-based suggestions
            }
            
            return true
        }
        
        fun toSuggestionText(): String {
            return when (action) {
                IntentType.CALL -> "Would you like me to call ${context["contact"]}?"
                IntentType.ALARM -> "Should I set your usual alarm for ${context["time"]}?"
                IntentType.PLAY_MUSIC -> "Time for your usual music session?"
                IntentType.SEND_MESSAGE -> "Would you like to message ${context["contact"]}?"
                else -> "Would you like me to ${action.name.lowercase().replace("_", " ")}?"
            }
        }
        
        private fun isTimeNear(time1: String, time2: String, minutesThreshold: Int): Boolean {
            try {
                val parts1 = time1.split(":")
                val parts2 = time2.split(":")
                
                val minutes1 = parts1[0].toInt() * 60 + parts1[1].toInt()
                val minutes2 = parts2[0].toInt() * 60 + parts2[1].toInt()
                
                return kotlin.math.abs(minutes1 - minutes2) <= minutesThreshold
            } catch (e: Exception) {
                return false
            }
        }
    }
    
    private val patterns = mutableListOf<UserPattern>()
    
    /**
     * Learn a pattern from user action
     */
    fun learnPattern(intent: IntentType, context: Map<String, String>) {
        // Build context with current time/day
        val enrichedContext = context.toMutableMap()
        val now = Calendar.getInstance()
        
        if (!enrichedContext.containsKey("time")) {
            enrichedContext["time"] = String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        }
        
        if (!enrichedContext.containsKey("day")) {
            enrichedContext["day"] = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, java.util.Locale.getDefault()) ?: ""
        }
        
        // Check if pattern exists
        val existing = patterns.find { it.matches(intent, enrichedContext) }
        
        if (existing != null) {
            existing.frequency++
            existing.lastOccurrence = System.currentTimeMillis()
        } else {
            patterns.add(UserPattern(intent, enrichedContext))
        }
        
        // Limit patterns to 100
        if (patterns.size > 100) {
            // Remove least frequent patterns
            patterns.sortBy { it.frequency }
            patterns.removeAt(0)
        }
    }
    
    /**
     * Get proactive suggestions based on current context
     */
    fun getSuggestions(): List<String> {
        val now = Calendar.getInstance()
        
        return patterns
            .filter { it.shouldSuggest(now) }
            .sortedByDescending { it.frequency }
            .take(3)
            .map { it.toSuggestionText() }
    }
    
    /**
     * Get suggestions for specific context
     */
    fun getSuggestionsForContext(location: String? = null, time: String? = null): List<String> {
        val now = Calendar.getInstance()
        
        return patterns
            .filter { pattern ->
                var matches = pattern.frequency >= MIN_FREQUENCY_FOR_SUGGESTION
                
                if (location != null && pattern.context["location"] != null) {
                    matches = matches && pattern.context["location"] == location
                }
                
                if (time != null && pattern.context["time"] != null) {
                    matches = matches && pattern.shouldSuggest(now)
                }
                
                matches
            }
            .sortedByDescending { it.frequency }
            .take(3)
            .map { it.toSuggestionText() }
    }
    
    /**
     * Get pattern for specific action
     */
    fun getPatternForAction(intent: IntentType): UserPattern? {
        return patterns
            .filter { it.action == intent }
            .maxByOrNull { it.frequency }
    }
    
    /**
     * Detect anomalies (user didn't do something they usually do)
     */
    fun detectAnomalies(): List<String> {
        val now = Calendar.getInstance()
        val anomalies = mutableListOf<String>()
        
        patterns.filter { it.frequency >= 5 }.forEach { pattern ->
            if (pattern.shouldSuggest(now)) {
                val timeSinceLastOccurrence = System.currentTimeMillis() - pattern.lastOccurrence
                val expectedInterval = 7 * 24 * 3600000L // 1 week
                
                if (timeSinceLastOccurrence > expectedInterval * 1.5) {
                    anomalies.add("You usually ${pattern.action.name.lowercase()} around this time. Everything okay?")
                }
            }
        }
        
        return anomalies
    }
    
    /**
     * Save patterns
     */
    fun save(context: Context) {
        try {
            val json = JSONObject()
            val patternsArray = JSONArray()
            
            patterns.forEach { pattern ->
                val obj = JSONObject().apply {
                    put("action", pattern.action.name)
                    put("frequency", pattern.frequency)
                    put("lastOccurrence", pattern.lastOccurrence)
                    put("createdAt", pattern.createdAt)
                    
                    val contextObj = JSONObject()
                    pattern.context.forEach { (k, v) -> contextObj.put(k, v) }
                    put("context", contextObj)
                }
                patternsArray.put(obj)
            }
            
            json.put("patterns", patternsArray)
            
            val file = File(context.filesDir, PATTERN_FILE)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Load patterns
     */
    fun load(context: Context) {
        try {
            val file = File(context.filesDir, PATTERN_FILE)
            if (!file.exists()) return
            
            val json = JSONObject(file.readText())
            val patternsArray = json.optJSONArray("patterns") ?: return
            
            patterns.clear()
            
            for (i in 0 until patternsArray.length()) {
                val obj = patternsArray.getJSONObject(i)
                
                val contextMap = mutableMapOf<String, String>()
                val contextObj = obj.optJSONObject("context")
                contextObj?.keys()?.forEach { key ->
                    contextMap[key] = contextObj.getString(key)
                }
                
                patterns.add(
                    UserPattern(
                        action = IntentType.valueOf(obj.getString("action")),
                        context = contextMap,
                        frequency = obj.getInt("frequency"),
                        lastOccurrence = obj.getLong("lastOccurrence"),
                        createdAt = obj.getLong("createdAt")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
