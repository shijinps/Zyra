package com.zyra.app.logic.ml

import android.content.Context
import com.zyra.app.logic.IntentType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object IntentPersistence {
    private const val FILE_NAME = "intent_training_data.json"

    fun save(context: Context, text: String, type: IntentType) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val jsonArray = if (file.exists()) {
                JSONArray(file.readText())
            } else {
                JSONArray()
            }

            val entry = JSONObject()
            entry.put("text", text)
            entry.put("type", type.name)
            jsonArray.put(entry)

            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun load(context: Context): List<Pair<String, IntentType>> {
        val list = mutableListOf<Pair<String, IntentType>>()
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return list

            val jsonArray = JSONArray(file.readText())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val text = obj.getString("text")
                val typeName = obj.getString("type")
                try {
                    list.add(text to IntentType.valueOf(typeName))
                } catch (e: Exception) {
                    // Ignore invalid types
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
    
    fun getFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }
}
