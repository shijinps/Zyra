package com.zyra.app.logic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud Database Initializer - Creates PostgreSQL tables via n8n
 * Automatically sets up cloud database schema on first run
 */
object CloudDatabaseInitializer {
    
    private const val INIT_URL = "https://n8n.shijin.in/webhook/zyra/memory/init"
    private var isInitialized = false
    
    /**
     * Initialize cloud database tables
     * Creates all necessary tables in PostgreSQL via n8n webhook
     */
    suspend fun initializeCloudDatabase(context: Context): InitResult {
        if (isInitialized) {
            return InitResult(true, "Already initialized")
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // Check if online
                if (!isOnline(context)) {
                    return@withContext InitResult(false, "No internet connection")
                }
                
                // Send initialization request
                val result = sendInitRequest()
                
                if (result) {
                    isInitialized = true
                    // Save initialization flag
                    val prefs = context.getSharedPreferences("zyra_memory", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("cloud_db_initialized", true).apply()
                    
                    InitResult(true, "Cloud database initialized successfully")
                } else {
                    InitResult(false, "Failed to initialize cloud database")
                }
                
            } catch (e: Exception) {
                Log.e("CloudInit", "Initialization error: ${e.message}")
                InitResult(false, "Error: ${e.message}")
            }
        }
    }
    
    /**
     * Send initialization request to n8n
     */
    private fun sendInitRequest(): Boolean {
        return try {
            val url = URL(INIT_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30000 // 30 seconds for table creation
                readTimeout = 30000
            }
            
            // Send empty body to trigger initialization
            val jsonData = JSONObject().apply {
                put("action", "initialize_tables")
            }
            
            connection.outputStream.use { os ->
                os.write(jsonData.toString().toByteArray())
            }
            
            val responseCode = connection.responseCode
            Log.d("CloudInit", "Response code: $responseCode")
            
            responseCode == 200
            
        } catch (e: Exception) {
            Log.e("CloudInit", "Init request error: ${e.message}")
            false
        }
    }
    
    /**
     * Check if cloud database is already initialized
     */
    fun isCloudDatabaseInitialized(context: Context): Boolean {
        val prefs = context.getSharedPreferences("zyra_memory", Context.MODE_PRIVATE)
        return prefs.getBoolean("cloud_db_initialized", false)
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
    
    data class InitResult(
        val success: Boolean,
        val message: String
    )
}
