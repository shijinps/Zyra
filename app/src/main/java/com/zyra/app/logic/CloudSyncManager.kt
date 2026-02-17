package com.zyra.app.logic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud Sync Manager - Syncs local memory to MongoDB in Coolify
 * Enables cloud backup and cross-device sync
 */
object CloudSyncManager {
    
    // MongoDB connection via n8n webhook
    private const val SYNC_URL = "https://n8n.shijin.in/webhook/zyra/memory"
    private val DEVICE_ID = android.os.Build.SERIAL
    
    private var isSyncing = false
    private var lastSyncTime: Long = 0
    
    /**
     * Sync local database to cloud
     */
    suspend fun syncToCloud(context: Context): SyncResult {
        if (isSyncing) {
            return SyncResult(false, "Sync already in progress")
        }
        
        isSyncing = true
        
        return withContext(Dispatchers.IO) {
            try {
                // Check internet connectivity
                if (!isOnline(context)) {
                    isSyncing = false
                    return@withContext SyncResult(false, "No internet connection")
                }
                
                // Get unsynced conversations
                val conversations = MemoryController.getConversationHistory(context, 1000)
                val unsyncedConversations = conversations.filter { 
                    it.timestamp > lastSyncTime 
                }
                
                if (unsyncedConversations.isEmpty()) {
                    isSyncing = false
                    return@withContext SyncResult(true, "Already up to date")
                }
                
                // Prepare data for MongoDB
                val jsonData = JSONObject().apply {
                    put("device_id", DEVICE_ID)
                    val conversationsArray = JSONArray()
                    unsyncedConversations.forEach { conv ->
                        val obj = JSONObject().apply {
                            put("timestamp", conv.timestamp)
                            put("user_input", conv.userInput)
                            put("zyra_response", conv.zyraResponse)
                            put("intent_type", conv.intentType)
                            put("success", conv.success)
                        }
                        conversationsArray.put(obj)
                    }
                    put("conversations", conversationsArray)
                }
                
                // Send to cloud
                val result = sendToMongoDB(jsonData)
                
                if (result) {
                    lastSyncTime = System.currentTimeMillis()
                    isSyncing = false
                    SyncResult(true, "Synced ${unsyncedConversations.size} conversations")
                } else {
                    isSyncing = false
                    SyncResult(false, "Failed to sync to cloud")
                }
                
            } catch (e: Exception) {
                Log.e("CloudSync", "Sync error: ${e.message}")
                isSyncing = false
                SyncResult(false, "Error: ${e.message}")
            }
        }
    }
    
    /**
     * Send data to MongoDB via n8n
     */
    private fun sendToMongoDB(data: JSONObject): Boolean {
        return try {
            val url = URL("$SYNC_URL/sync")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            // Send data
            connection.outputStream.use { os ->
                os.write(data.toString().toByteArray())
            }
            
            val responseCode = connection.responseCode
            Log.d("CloudSync", "Sync response: $responseCode")
            
            responseCode == 200
            
        } catch (e: Exception) {
            Log.e("CloudSync", "MongoDB sync error: ${e.message}")
            false
        }
    }
    
    /**
     * Pull data from cloud
     */
    suspend fun pullFromCloud(context: Context): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                if (!isOnline(context)) {
                    return@withContext SyncResult(false, "No internet connection")
                }
                
                val url = URL("$SYNC_URL/pull?device_id=$DEVICE_ID&since=$lastSyncTime")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val jsonObject = JSONObject(response)
                    val jsonArray = jsonObject.getJSONArray("conversations")
                    
                    // Save to local database
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        MemoryController.saveConversation(
                            context = context,
                            userInput = obj.getString("user_input"),
                            zyraResponse = obj.getString("zyra_response"),
                            intentType = obj.getString("intent_type"),
                            success = obj.getBoolean("success")
                        )
                    }
                    
                    SyncResult(true, "Pulled ${jsonArray.length()} conversations")
                } else {
                    SyncResult(false, "Failed to pull from cloud")
                }
                
            } catch (e: Exception) {
                Log.e("CloudSync", "Pull error: ${e.message}")
                SyncResult(false, "Error: ${e.message}")
            }
        }
    }
    
    /**
     * Auto-sync in background
     */
    fun startAutoSync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                // Try to sync every 30 minutes
                delay(30 * 60 * 1000)
                
                if (isOnline(context)) {
                    syncToCloud(context)
                }
            }
        }
    }
    
    /**
     * Check internet connectivity
     */
    private fun isOnline(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            false
        }
    }
    
    data class SyncResult(
        val success: Boolean,
        val message: String
    )
}
