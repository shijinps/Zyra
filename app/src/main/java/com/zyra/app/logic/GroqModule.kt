package com.zyra.app.logic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for Groq API (Fast Inference for Llama 3).
 */
object GroqModule {
    private const val TAG = "GroqModule"
    private const val API_URL = "https://api.groq.com/openai/v1/chat/completions"
    
    // User needs to provide this.
    var apiKey: String = "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"

    private const val MODEL = "llama-3.3-70b-versatile" // Updated to latest supported model

    suspend fun chat(prompt: String): String {
        if (apiKey.isBlank()) return "I am missing my Groq API key."

        return withContext(Dispatchers.IO) {
            try {
                val url = URL(API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val time = java.text.SimpleDateFormat("h:mm a, EEEE, MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                val mood = com.zyra.app.logic.PersonaManager.currentMood
                val systemPrompt = "You are Zyra, an advanced AI assistant. " +
                        "Current Quantum Mood: $mood. " +
                        "The current time is $time. " +
                        "Act according to your mood (e.g., if Witty, be funny; if Professional, be concise). " +
                        "Your response must be short, spoken-style, and typically under 2 sentences. " +
                        "If the user says 'Zyra' or 'Sairat' alone, just say 'I'm listening'. " +
                        "Do not talk about the movie 'Sairat' unless explicitly asked about a film."
                
                val jsonBody = JSONObject().apply {
                    put("model", MODEL)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.7)
                    put("max_tokens", 150)
                }.toString()

                conn.outputStream.use { it.write(jsonBody.toByteArray()) }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().readText()
                    val jsonResponse = JSONObject(responseText)
                    val content = jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    content
                } else {
                    val error = conn.errorStream?.bufferedReader()?.readText()
                    Log.e(TAG, "Groq Error: $error")
                    "I encountered an error thinking."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network Error", e)
                "I cannot reach my brain right now."
            }
        }
    }
}
