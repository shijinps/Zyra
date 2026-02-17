package com.zyra.app.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Wikipedia Controller - Information lookup
 * "Who is Elon Musk?"
 * "What is quantum computing?"
 */
object WikipediaController {
    
    /**
     * Search Wikipedia and get summary
     */
    suspend fun search(query: String): WikiResult? {
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedQuery"
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    
                    WikiResult(
                        title = json.getString("title"),
                        extract = json.getString("extract"),
                        url = json.optString("content_urls")
                    )
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Format result for speech
     */
    fun formatForSpeech(result: WikiResult): String {
        // Limit to first 2-3 sentences for speech
        val sentences = result.extract.split(". ")
        val summary = sentences.take(3).joinToString(". ")
        return "$summary."
    }
    
    data class WikiResult(
        val title: String,
        val extract: String,
        val url: String
    )
}
