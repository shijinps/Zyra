package com.zyra.app.logic

import android.content.Context
import com.zyra.app.logic.ml.IntentClassifier
import com.zyra.app.logic.ml.IntentPersistence
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Reinforcement Learning Module
 * Learns from user feedback and corrections to improve over time
 */
object ReinforcementLearner {
    
    private const val FEEDBACK_FILE = "zyra_feedback_data.json"
    
    data class FeedbackEntry(
        val query: String,
        val predictedIntent: IntentType,
        val correctIntent: IntentType?,
        val wasCorrect: Boolean,
        val timestamp: Long
    )
    
    private val feedbackHistory = mutableListOf<FeedbackEntry>()
    
    /**
     * Record implicit feedback (user repeated command = previous was wrong)
     */
    fun recordImplicitFeedback(
        originalQuery: String,
        predictedIntent: IntentType,
        repeatedQuery: String
    ) {
        // If user repeats similar command, previous interpretation was likely wrong
        if (isSimilarQuery(originalQuery, repeatedQuery)) {
            recordFeedback(originalQuery, predictedIntent, false)
        }
    }
    
    /**
     * Record explicit feedback
     */
    fun recordFeedback(
        query: String,
        predictedIntent: IntentType,
        wasCorrect: Boolean,
        correctIntent: IntentType? = null
    ) {
        feedbackHistory.add(
            FeedbackEntry(
                query = query,
                predictedIntent = predictedIntent,
                correctIntent = correctIntent,
                wasCorrect = wasCorrect,
                timestamp = System.currentTimeMillis()
            )
        )
        
        // Adjust weights based on feedback
        if (!wasCorrect && correctIntent != null) {
            // Penalize wrong prediction
            adjustWeights(query, predictedIntent, reward = -0.5)
            
            // Reinforce correct intent
            adjustWeights(query, correctIntent, reward = 1.0)
            
            // Retrain on correct mapping
            IntentClassifier.trainDynamic(query, correctIntent)
        } else if (wasCorrect) {
            // Reinforce correct prediction
            adjustWeights(query, predictedIntent, reward = 0.5)
        }
        
        // Keep last 200 feedback entries
        if (feedbackHistory.size > 200) {
            feedbackHistory.removeAt(0)
        }
    }
    
    /**
     * Handle user correction
     */
    fun handleCorrection(
        context: Context,
        originalQuery: String,
        correctIntent: IntentType
    ) {
        // Save to persistent training data
        IntentPersistence.save(context, originalQuery, correctIntent)
        
        // Retrain immediately
        IntentClassifier.trainDynamic(originalQuery, correctIntent)
        
        // Record feedback
        val predictedIntent = IntentClassifier.predict(originalQuery)
        recordFeedback(originalQuery, predictedIntent, false, correctIntent)
    }
    
    /**
     * Adjust neural network weights (simplified reinforcement)
     */
    private fun adjustWeights(query: String, intent: IntentType, reward: Double) {
        // This is a simplified version
        // In a full implementation, this would adjust the neural network weights
        // For now, we rely on the dynamic training in IntentClassifier
        
        if (reward > 0) {
            // Positive reinforcement - train multiple times
            repeat(10) {
                IntentClassifier.trainDynamic(query, intent)
            }
        }
        // Negative reinforcement is handled by training the correct intent
    }
    
    /**
     * Get accuracy statistics
     */
    fun getAccuracyStats(): Map<String, Any> {
        if (feedbackHistory.isEmpty()) {
            return mapOf("accuracy" to 0.0, "totalFeedback" to 0)
        }
        
        val correct = feedbackHistory.count { it.wasCorrect }
        val total = feedbackHistory.size
        val accuracy = (correct.toDouble() / total.toDouble()) * 100
        
        return mapOf(
            "accuracy" to accuracy,
            "totalFeedback" to total,
            "correct" to correct,
            "incorrect" to (total - correct)
        )
    }
    
    /**
     * Get intent-specific accuracy
     */
    fun getIntentAccuracy(intent: IntentType): Double {
        val intentFeedback = feedbackHistory.filter { it.predictedIntent == intent }
        if (intentFeedback.isEmpty()) return 0.0
        
        val correct = intentFeedback.count { it.wasCorrect }
        return (correct.toDouble() / intentFeedback.size.toDouble()) * 100
    }
    
    /**
     * Get problematic queries (frequently misclassified)
     */
    fun getProblematicQueries(): List<Pair<String, Int>> {
        return feedbackHistory
            .filter { !it.wasCorrect }
            .groupBy { it.query }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(10)
    }
    
    /**
     * Check if two queries are similar
     */
    private fun isSimilarQuery(query1: String, query2: String): Boolean {
        val normalized1 = query1.lowercase().trim()
        val normalized2 = query2.lowercase().trim()
        
        // Check if one contains the other
        if (normalized1.contains(normalized2) || normalized2.contains(normalized1)) {
            return true
        }
        
        // Check Levenshtein distance
        val distance = com.zyra.app.logic.algorithms.Levenshtein.distance(normalized1, normalized2)
        return distance <= 5
    }
    
    /**
     * Save feedback data
     */
    fun save(context: Context) {
        try {
            val json = JSONObject()
            val feedbackArray = JSONArray()
            
            feedbackHistory.forEach { entry ->
                val obj = JSONObject().apply {
                    put("query", entry.query)
                    put("predictedIntent", entry.predictedIntent.name)
                    put("correctIntent", entry.correctIntent?.name)
                    put("wasCorrect", entry.wasCorrect)
                    put("timestamp", entry.timestamp)
                }
                feedbackArray.put(obj)
            }
            
            json.put("feedback", feedbackArray)
            
            val file = File(context.filesDir, FEEDBACK_FILE)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Load feedback data
     */
    fun load(context: Context) {
        try {
            val file = File(context.filesDir, FEEDBACK_FILE)
            if (!file.exists()) return
            
            val json = JSONObject(file.readText())
            val feedbackArray = json.optJSONArray("feedback") ?: return
            
            feedbackHistory.clear()
            
            for (i in 0 until feedbackArray.length()) {
                val obj = feedbackArray.getJSONObject(i)
                
                feedbackHistory.add(
                    FeedbackEntry(
                        query = obj.getString("query"),
                        predictedIntent = IntentType.valueOf(obj.getString("predictedIntent")),
                        correctIntent = obj.optString("correctIntent", null)?.let { IntentType.valueOf(it) },
                        wasCorrect = obj.getBoolean("wasCorrect"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
