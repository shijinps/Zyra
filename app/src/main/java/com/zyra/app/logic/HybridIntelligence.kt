package com.zyra.app.logic

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

/**
 * Hybrid Online/Offline Intelligence
 * Automatically routes queries to online or offline systems based on complexity and availability
 */
class HybridIntelligence(private val context: Context) {
    
    private val responseCache = mutableMapOf<String, CachedResponse>()
    private val offlineBrain = OfflineBrain()
    
    data class CachedResponse(
        val query: String,
        val response: String,
        val timestamp: Long,
        val source: String // "online" or "offline"
    )
    
    companion object {
        private const val CACHE_FILE = "zyra_response_cache.json"
        private const val CACHE_EXPIRY_MS = 24 * 3600000L // 24 hours
        private const val MAX_CACHE_SIZE = 100
    }
    
    /**
     * Answer query using hybrid approach
     */
    suspend fun answer(query: String, nlpResult: String): String {
        // 1. Check cache first
        val cached = getCachedResponse(query)
        if (cached != null) {
            return cached
        }
        
        // 2. Determine if query is simple or complex
        val isSimpleQuery = isSimpleQuery(query)
        
        // 3. Try offline first for simple queries (faster)
        if (isSimpleQuery) {
            val offlineAnswer = offlineBrain.getResponse(nlpResult, context)
            
            if (!isGenericResponse(offlineAnswer)) {
                cacheResponse(query, offlineAnswer, "offline")
                return offlineAnswer
            }
        }
        
        // 4. Try online for complex queries or if offline gave generic response
        if (isNetworkAvailable()) {
            try {
                val onlineAnswer = withTimeout(5000) {
                    GeminiModule.generateResponse(query)
                }
                
                // Cache successful online response
                if (!isGenericResponse(onlineAnswer)) {
                    cacheResponse(query, onlineAnswer, "online")
                    return onlineAnswer
                }
            } catch (e: Exception) {
                // Network timeout or error, fall back to offline
            }
        }
        
        // 5. Final fallback to offline
        val offlineAnswer = offlineBrain.getResponse(nlpResult, context)
        cacheResponse(query, offlineAnswer, "offline")
        return offlineAnswer
    }
    
    /**
     * Check if query is simple enough for offline processing
     */
    private fun isSimpleQuery(query: String): Boolean {
        val lowerQuery = query.lowercase()
        
        // Simple queries that offline can handle well
        val simplePatterns = listOf(
            "time", "date", "battery", "joke", "hello", "hi",
            "call", "message", "alarm", "timer", "flashlight",
            "wifi", "bluetooth", "volume", "weather"
        )
        
        return simplePatterns.any { lowerQuery.contains(it) }
    }
    
    /**
     * Check if response is generic/unhelpful
     */
    private fun isGenericResponse(response: String): Boolean {
        val genericPhrases = listOf(
            "I don't know",
            "I'm not sure",
            "I couldn't find",
            "I don't understand",
            "Can you rephrase",
            "Sorry, I can't"
        )
        
        return genericPhrases.any { response.contains(it, ignoreCase = true) }
    }
    
    /**
     * Check network availability
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    /**
     * Get cached response if available and not expired
     */
    private fun getCachedResponse(query: String): String? {
        val normalized = normalizeQuery(query)
        val cached = responseCache[normalized] ?: return null
        
        // Check if cache is expired
        if (System.currentTimeMillis() - cached.timestamp > CACHE_EXPIRY_MS) {
            responseCache.remove(normalized)
            return null
        }
        
        return cached.response
    }
    
    /**
     * Cache response
     */
    private fun cacheResponse(query: String, response: String, source: String) {
        val normalized = normalizeQuery(query)
        
        responseCache[normalized] = CachedResponse(
            query = query,
            response = response,
            timestamp = System.currentTimeMillis(),
            source = source
        )
        
        // Maintain cache size
        if (responseCache.size > MAX_CACHE_SIZE) {
            // Remove oldest entry
            val oldest = responseCache.values.minByOrNull { it.timestamp }
            oldest?.let { responseCache.remove(normalizeQuery(it.query)) }
        }
        
        saveCache()
    }
    
    /**
     * Normalize query for caching
     */
    private fun normalizeQuery(query: String): String {
        return query.lowercase().trim()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
    }
    
    /**
     * Save cache to disk
     */
    private fun saveCache() {
        try {
            val json = JSONObject()
            val cacheArray = org.json.JSONArray()
            
            responseCache.values.forEach { cached ->
                val obj = JSONObject().apply {
                    put("query", cached.query)
                    put("response", cached.response)
                    put("timestamp", cached.timestamp)
                    put("source", cached.source)
                }
                cacheArray.put(obj)
            }
            
            json.put("cache", cacheArray)
            
            val file = File(context.filesDir, CACHE_FILE)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Load cache from disk
     */
    fun loadCache() {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return
            
            val json = JSONObject(file.readText())
            val cacheArray = json.optJSONArray("cache") ?: return
            
            responseCache.clear()
            
            for (i in 0 until cacheArray.length()) {
                val obj = cacheArray.getJSONObject(i)
                
                val cached = CachedResponse(
                    query = obj.getString("query"),
                    response = obj.getString("response"),
                    timestamp = obj.getLong("timestamp"),
                    source = obj.getString("source")
                )
                
                // Only load if not expired
                if (System.currentTimeMillis() - cached.timestamp <= CACHE_EXPIRY_MS) {
                    responseCache[normalizeQuery(cached.query)] = cached
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Clear cache
     */
    fun clearCache() {
        responseCache.clear()
        saveCache()
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "size" to responseCache.size,
            "onlineCount" to responseCache.values.count { it.source == "online" },
            "offlineCount" to responseCache.values.count { it.source == "offline" }
        )
    }
}
