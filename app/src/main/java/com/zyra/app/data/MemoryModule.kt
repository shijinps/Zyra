package com.zyra.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File

object MemoryModule {
    
    private const val FILE_NAME = "zyra_brain_data.json"
    
    // Maps "alias" -> "real command"
    // e.g. "selfie mode" -> "open camera and click picture"
    val aliases = mutableMapOf<String, String>()
    
    // Maps "key" -> "value"
    // e.g. "wifi password" -> "1234"
    val facts = mutableMapOf<String, String>()
    
    // Simple history list
    val conversationHistory = mutableListOf<String>()

    fun init(context: Context) {
        load(context)
        // Default facts if empty
        if (getFact("user_name") == null) {
            learnFact("user_name", "Boss", context)
        }
        // Pre-populate password from user request
        if (getFact("password") == null) {
            learnFact("password", "02552", context)
        }
    }

    fun learnAlias(alias: String, command: String, context: Context) {
        aliases[alias.lowercase()] = command.lowercase()
        save(context)
    }

    fun learnFact(key: String, value: String, context: Context) {
        facts[key.lowercase()] = value
        save(context)
    }
    
    fun addToHistory(text: String, context: Context) {
        conversationHistory.add(text)
        if (conversationHistory.size > 5) { // Keep last 5 for context
            conversationHistory.removeAt(0)
        }
        save(context) // Save history for persistence across reboots
    }

    fun getFact(key: String): String? {
        return facts[key.lowercase()]
    }

    fun getAllFacts(): Map<String, String> {
        return facts
    }
    
    fun getLastTopic(): String? {
        return if (conversationHistory.isNotEmpty()) conversationHistory.last() else null
    }

    fun checkForAlias(text: String): String {
        return aliases.getOrDefault(text.lowercase(), text)
    }

    private fun save(context: Context) {
        try {
            val json = JSONObject()
            
            val aliasObj = JSONObject()
            aliases.forEach { (k, v) -> aliasObj.put(k, v) }
            json.put("aliases", aliasObj)
            
            val factObj = JSONObject()
            facts.forEach { (k, v) -> factObj.put(k, v) }
            json.put("facts", factObj)
            
            // Save History
            val historyArray = org.json.JSONArray()
            conversationHistory.forEach { historyArray.put(it) }
            json.put("history", historyArray)
            
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun load(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return
            
            val json = JSONObject(file.readText())
            
            val aliasObj = json.optJSONObject("aliases")
            aliasObj?.keys()?.forEach { key ->
                aliases[key] = aliasObj.getString(key)
            }
            
            val factObj = json.optJSONObject("facts")
            factObj?.keys()?.forEach { key ->
                facts[key] = factObj.getString(key)
            }
            
            val historyArray = json.optJSONArray("history")
            if (historyArray != null) {
                conversationHistory.clear()
                for (i in 0 until historyArray.length()) {
                    conversationHistory.add(historyArray.getString(i))
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
