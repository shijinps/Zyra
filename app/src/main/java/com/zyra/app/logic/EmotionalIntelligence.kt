package com.zyra.app.logic

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Emotional Intelligence Module
 * Detects user sentiment and adapts responses accordingly
 */
object EmotionalIntelligence {
    
    private const val EMOTION_FILE = "zyra_emotion_data.json"
    
    enum class UserEmotion {
        HAPPY, SAD, FRUSTRATED, EXCITED, NEUTRAL, STRESSED, ANGRY, GRATEFUL
    }
    
    data class EmotionalContext(
        val emotion: UserEmotion,
        val confidence: Float,
        val timestamp: Long,
        val trigger: String = ""
    )
    
    private val emotionHistory = mutableListOf<EmotionalContext>()
    private var currentEmotion: UserEmotion = UserEmotion.NEUTRAL
    
    // Emotion keyword patterns
    private val emotionPatterns = mapOf(
        UserEmotion.FRUSTRATED to listOf(
            "ugh", "damn", "stupid", "not working", "broken", "useless",
            "annoying", "irritating", "hate", "terrible", "awful", "sucks"
        ),
        UserEmotion.HAPPY to listOf(
            "great", "awesome", "perfect", "thanks", "excellent", "wonderful",
            "amazing", "fantastic", "love", "brilliant", "good", "nice"
        ),
        UserEmotion.STRESSED to listOf(
            "busy", "late", "hurry", "quick", "rush", "urgent", "pressure",
            "overwhelmed", "tired", "exhausted", "stressed"
        ),
        UserEmotion.ANGRY to listOf(
            "angry", "furious", "mad", "pissed", "rage", "livid"
        ),
        UserEmotion.SAD to listOf(
            "sad", "depressed", "down", "unhappy", "miserable", "upset",
            "disappointed", "hurt"
        ),
        UserEmotion.EXCITED to listOf(
            "excited", "pumped", "thrilled", "can't wait", "yay", "woohoo"
        ),
        UserEmotion.GRATEFUL to listOf(
            "thank you", "thanks", "appreciate", "grateful", "thankful"
        )
    )
    
    /**
     * Detect emotion from user text
     */
    fun detectEmotion(text: String, voiceTone: Float? = null): UserEmotion {
        val lowerText = text.lowercase()
        
        // Check for explicit emotion keywords
        val detectedEmotion = emotionPatterns.entries
            .map { (emotion, keywords) ->
                val matchCount = keywords.count { lowerText.contains(it) }
                emotion to matchCount
            }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
        
        // Analyze punctuation for intensity
        val hasExclamation = text.contains("!")
        val hasMultipleExclamation = text.count { it == '!' } > 1
        val hasAllCaps = text.any { it.isUpperCase() } && text.length > 3
        
        // Adjust based on intensity markers
        val finalEmotion = when {
            detectedEmotion == UserEmotion.FRUSTRATED && hasMultipleExclamation -> UserEmotion.ANGRY
            detectedEmotion == UserEmotion.HAPPY && hasExclamation -> UserEmotion.EXCITED
            detectedEmotion != null -> detectedEmotion
            hasAllCaps -> UserEmotion.FRUSTRATED
            else -> UserEmotion.NEUTRAL
        }
        
        // Calculate confidence
        val confidence = when {
            detectedEmotion != null -> 0.8f
            hasAllCaps -> 0.6f
            else -> 0.3f
        }
        
        // Record emotion
        recordEmotion(finalEmotion, confidence, text)
        currentEmotion = finalEmotion
        
        return finalEmotion
    }
    
    /**
     * Adapt response based on detected emotion
     */
    fun adaptResponse(baseResponse: String, emotion: UserEmotion): String {
        return when (emotion) {
            UserEmotion.FRUSTRATED -> {
                "I understand this is frustrating. $baseResponse"
            }
            UserEmotion.ANGRY -> {
                "I apologize for any inconvenience. Let me help. $baseResponse"
            }
            UserEmotion.STRESSED -> {
                "$baseResponse I'll make it quick."
            }
            UserEmotion.SAD -> {
                "I'm here for you. $baseResponse"
            }
            UserEmotion.HAPPY -> {
                "$baseResponse Glad I could help!"
            }
            UserEmotion.EXCITED -> {
                "$baseResponse This is exciting!"
            }
            UserEmotion.GRATEFUL -> {
                "You're welcome! $baseResponse"
            }
            UserEmotion.NEUTRAL -> baseResponse
        }
    }
    
    /**
     * Get empathetic prefix based on emotion
     */
    fun getEmpatheticPrefix(emotion: UserEmotion): String? {
        return when (emotion) {
            UserEmotion.FRUSTRATED -> "I can sense you're frustrated. "
            UserEmotion.ANGRY -> "I understand you're upset. "
            UserEmotion.STRESSED -> "I know you're in a hurry. "
            UserEmotion.SAD -> "I'm sorry you're feeling down. "
            else -> null
        }
    }
    
    /**
     * Suggest tone adjustment for PersonaManager
     */
    fun suggestTone(emotion: UserEmotion): String {
        return when (emotion) {
            UserEmotion.FRUSTRATED, UserEmotion.ANGRY -> "Patient and Apologetic"
            UserEmotion.STRESSED -> "Efficient and Concise"
            UserEmotion.SAD -> "Warm and Empathetic"
            UserEmotion.HAPPY, UserEmotion.EXCITED -> "Enthusiastic and Playful"
            UserEmotion.GRATEFUL -> "Humble and Appreciative"
            UserEmotion.NEUTRAL -> "Professional and Helpful"
        }
    }
    
    /**
     * Get emotional trend over time
     */
    fun getEmotionalTrend(hours: Int = 24): Map<UserEmotion, Int> {
        val cutoffTime = System.currentTimeMillis() - (hours * 3600000)
        
        return emotionHistory
            .filter { it.timestamp > cutoffTime }
            .groupBy { it.emotion }
            .mapValues { it.value.size }
    }
    
    /**
     * Check if user is in a negative emotional state
     */
    fun isNegativeState(): Boolean {
        return currentEmotion in listOf(
            UserEmotion.FRUSTRATED,
            UserEmotion.ANGRY,
            UserEmotion.SAD,
            UserEmotion.STRESSED
        )
    }
    
    /**
     * Get current emotion
     */
    fun getCurrentEmotion(): UserEmotion {
        return currentEmotion
    }
    
    /**
     * Record emotion in history
     */
    private fun recordEmotion(emotion: UserEmotion, confidence: Float, trigger: String) {
        emotionHistory.add(
            EmotionalContext(
                emotion = emotion,
                confidence = confidence,
                timestamp = System.currentTimeMillis(),
                trigger = trigger
            )
        )
        
        // Keep last 50 emotions
        if (emotionHistory.size > 50) {
            emotionHistory.removeAt(0)
        }
    }
    
    /**
     * Save emotion data
     */
    fun save(context: Context) {
        try {
            val json = JSONObject()
            json.put("currentEmotion", currentEmotion.name)
            
            val historyArray = org.json.JSONArray()
            emotionHistory.forEach { ctx ->
                val obj = JSONObject().apply {
                    put("emotion", ctx.emotion.name)
                    put("confidence", ctx.confidence)
                    put("timestamp", ctx.timestamp)
                    put("trigger", ctx.trigger)
                }
                historyArray.put(obj)
            }
            json.put("history", historyArray)
            
            val file = File(context.filesDir, EMOTION_FILE)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Load emotion data
     */
    fun load(context: Context) {
        try {
            val file = File(context.filesDir, EMOTION_FILE)
            if (!file.exists()) return
            
            val json = JSONObject(file.readText())
            currentEmotion = UserEmotion.valueOf(json.getString("currentEmotion"))
            
            val historyArray = json.optJSONArray("history") ?: return
            emotionHistory.clear()
            
            for (i in 0 until historyArray.length()) {
                val obj = historyArray.getJSONObject(i)
                emotionHistory.add(
                    EmotionalContext(
                        emotion = UserEmotion.valueOf(obj.getString("emotion")),
                        confidence = obj.getDouble("confidence").toFloat(),
                        timestamp = obj.getLong("timestamp"),
                        trigger = obj.optString("trigger", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
