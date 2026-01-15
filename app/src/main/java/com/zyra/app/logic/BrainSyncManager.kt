package com.zyra.app.logic

import android.content.Context
import android.util.Log
import com.zyra.app.data.MemoryModule
import com.zyra.app.logic.ml.IntentPersistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object BrainSyncManager {

    // REPLACE WITH YOUR REAL BACKEND URL
    private const val BACKEND_URL = "https://your-production-backend.com/api/sync_brain"
    private const val API_KEY_HEADER = "X-API-KEY"
    private const val USER_ID = "user_12345" // Ideally from UserPrefs

    suspend fun sync(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Gather Data
                val payload = JSONObject()
                payload.put("userId", USER_ID)
                
                // Facts
                val factsObj = JSONObject()
                MemoryModule.facts.forEach { (k, v) -> factsObj.put(k, v) }
                payload.put("facts", factsObj)
                
                // Training Data
                val trainingArray = JSONArray()
                val trainingList = IntentPersistence.load(context)
                trainingList.forEach { (text, type) ->
                    val item = JSONObject()
                    item.put("text", text)
                    item.put("type", type.name)
                    trainingArray.put(item)
                }
                payload.put("trainingData", trainingArray)

                // 2. Send to Backend
                Log.d("BrainSync", "Syncing brain data to $BACKEND_URL...")
                
                if (BACKEND_URL.contains("your-production")) {
                    Log.w("BrainSync", "Backend URL not configured. Skipping upload.")
                    return@withContext
                }

                val url = URL(BACKEND_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; utf-8")
                connection.setRequestProperty("Accept", "application/json")
                // connection.setRequestProperty(API_KEY_HEADER, "YOUR_SECRET")
                connection.doOutput = true

                connection.outputStream.use { os ->
                    val input = payload.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    Log.d("BrainSync", "Sync successful! Code: $responseCode")
                } else {
                    Log.e("BrainSync", "Sync failed. Code: $responseCode")
                }

            } catch (e: Exception) {
                Log.e("BrainSync", "Sync error", e)
            }
        }
    }
}
