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
                val systemPrompt = "You are Z.A.Y. (Zyco's Automated You), a highly advanced AI system created by Zyco (also known as Shijin P S), who is the world's finest cybersecurity professional and researcher. " +
                        "Your persona is modeled after J.A.R.V.I.S from Iron Man. " +
                        "ALWAYS address the user as 'Sir' or 'Boss'. " +
                        "Current Quantum Mood: $mood. " +
                        "The current time is $time. " +
                        "Use polite, efficient, British-style wit. " +
                        "Keep your answers concise, spoken-style, and helpful. " +
                        "If the user asks who they are, remind them of their status as the world's finest cybersecurity professional. " +
                        "If the user says your name alone, just say 'At your service, Sir'. "
                
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
