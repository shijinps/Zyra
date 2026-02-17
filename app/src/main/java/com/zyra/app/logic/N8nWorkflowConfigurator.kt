package com.zyra.app.logic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * n8n Workflow Configurator - Automatically sets up MongoDB sync workflow
 * Creates and configures n8n workflow programmatically via API
 */
object N8nWorkflowConfigurator {
    
    private const val N8N_URL = "https://n8n.shijin.in"
    private const val N8N_API_KEY = "" // Add your n8n API key if needed
    
    // MongoDB connection details
    private const val MONGODB_USER = "root"
    private const val MONGODB_PASSWORD = "QjKUFbQWCl1aiuNySmQWQGuY3yS7pMulyPsanFuOHoIYCS8HLMp6NkbybXEZoycF"
    private const val MONGODB_HOST = "ockookc04kwwo4gksskkck88"
    private const val MONGODB_PORT = "27017"
    private const val MONGODB_DATABASE = "zyra_memory"
    
    /**
     * Setup complete n8n workflow for MongoDB sync
     */
    suspend fun setupWorkflow(context: Context): SetupResult {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Create MongoDB credential
                val credentialId = createMongoDBCredential()
                if (credentialId == null) {
                    return@withContext SetupResult(false, "Failed to create MongoDB credential")
                }
                
                // Step 2: Create workflow
                val workflowId = createWorkflow(credentialId)
                if (workflowId == null) {
                    return@withContext SetupResult(false, "Failed to create workflow")
                }
                
                // Step 3: Activate workflow
                val activated = activateWorkflow(workflowId)
                if (!activated) {
                    return@withContext SetupResult(false, "Failed to activate workflow")
                }
                
                // Save configuration
                val prefs = context.getSharedPreferences("zyra_memory", Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("n8n_configured", true)
                    .putString("workflow_id", workflowId)
                    .apply()
                
                SetupResult(true, "n8n workflow configured successfully")
                
            } catch (e: Exception) {
                Log.e("N8nConfig", "Setup error: ${e.message}")
                SetupResult(false, "Error: ${e.message}")
            }
        }
    }
    
    /**
     * Create MongoDB credential in n8n
     */
    private fun createMongoDBCredential(): String? {
        return try {
            val url = URL("$N8N_URL/api/v1/credentials")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                if (N8N_API_KEY.isNotEmpty()) {
                    setRequestProperty("X-N8N-API-KEY", N8N_API_KEY)
                }
                doOutput = true
            }
            
            val credentialData = JSONObject().apply {
                put("name", "ZYRA MongoDB")
                put("type", "mongoDb")
                put("data", JSONObject().apply {
                    put("user", MONGODB_USER)
                    put("password", MONGODB_PASSWORD)
                    put("host", MONGODB_HOST)
                    put("port", MONGODB_PORT)
                    put("database", MONGODB_DATABASE)
                })
            }
            
            connection.outputStream.use { os ->
                os.write(credentialData.toString().toByteArray())
            }
            
            if (connection.responseCode == 200 || connection.responseCode == 201) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonResponse = JSONObject(response)
                jsonResponse.getString("id")
            } else {
                Log.e("N8nConfig", "Failed to create credential: ${connection.responseCode}")
                null
            }
            
        } catch (e: Exception) {
            Log.e("N8nConfig", "Credential creation error: ${e.message}")
            null
        }
    }
    
    /**
     * Create MongoDB sync workflow
     */
    private fun createWorkflow(credentialId: String): String? {
        return try {
            val url = URL("$N8N_URL/api/v1/workflows")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                if (N8N_API_KEY.isNotEmpty()) {
                    setRequestProperty("X-N8N-API-KEY", N8N_API_KEY)
                }
                doOutput = true
            }
            
            val workflowData = createWorkflowJSON(credentialId)
            
            connection.outputStream.use { os ->
                os.write(workflowData.toString().toByteArray())
            }
            
            if (connection.responseCode == 200 || connection.responseCode == 201) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonResponse = JSONObject(response)
                jsonResponse.getString("id")
            } else {
                Log.e("N8nConfig", "Failed to create workflow: ${connection.responseCode}")
                null
            }
            
        } catch (e: Exception) {
            Log.e("N8nConfig", "Workflow creation error: ${e.message}")
            null
        }
    }
    
    /**
     * Create workflow JSON structure
     */
    private fun createWorkflowJSON(credentialId: String): JSONObject {
        return JSONObject().apply {
            put("name", "Zyra Memory Sync - Auto Configured")
            put("active", false)
            
            // Nodes
            val nodes = JSONArray()
            
            // Webhook - Sync
            nodes.put(JSONObject().apply {
                put("parameters", JSONObject().apply {
                    put("httpMethod", "POST")
                    put("path", "zyra/memory/sync")
                    put("responseMode", "responseNode")
                })
                put("name", "Webhook - Sync")
                put("type", "n8n-nodes-base.webhook")
                put("typeVersion", 1)
                put("position", JSONArray().apply {
                    put(250)
                    put(300)
                })
            })
            
            // MongoDB Insert
            nodes.put(JSONObject().apply {
                put("parameters", JSONObject().apply {
                    put("operation", "insertMany")
                    put("collection", "zyra_conversations")
                    put("fields", "={{ \$json.conversations }}")
                })
                put("name", "Insert to MongoDB")
                put("type", "n8n-nodes-base.mongoDb")
                put("typeVersion", 1)
                put("position", JSONArray().apply {
                    put(450)
                    put(300)
                })
                put("credentials", JSONObject().apply {
                    put("mongoDb", JSONObject().apply {
                        put("id", credentialId)
                    })
                })
            })
            
            // Respond
            nodes.put(JSONObject().apply {
                put("parameters", JSONObject().apply {
                    put("respondWith", "json")
                    put("responseBody", "={{ { \"success\": true, \"synced\": \$json.conversations.length } }}")
                })
                put("name", "Respond")
                put("type", "n8n-nodes-base.respondToWebhook")
                put("typeVersion", 1)
                put("position", JSONArray().apply {
                    put(650)
                    put(300)
                })
            })
            
            // Webhook - Pull
            nodes.put(JSONObject().apply {
                put("parameters", JSONObject().apply {
                    put("httpMethod", "GET")
                    put("path", "zyra/memory/pull")
                    put("responseMode", "responseNode")
                })
                put("name", "Webhook - Pull")
                put("type", "n8n-nodes-base.webhook")
                put("typeVersion", 1)
                put("position", JSONArray().apply {
                    put(250)
                    put(500)
                })
            })
            
            // MongoDB Find
            nodes.put(JSONObject().apply {
                put("parameters", JSONObject().apply {
                    put("operation", "find")
                    put("collection", "zyra_conversations")
                    put("query", "={{ { \"device_id\": \$json.query.device_id, \"timestamp\": { \"\$gt\": parseInt(\$json.query.since) } } }}")
                    put("options", JSONObject().apply {
                        put("limit", 1000)
                        put("sort", JSONObject().apply {
                            put("timestamp", -1)
                        })
                    })
                })
                put("name", "Query MongoDB")
                put("type", "n8n-nodes-base.mongoDb")
                put("typeVersion", 1)
                put("position", JSONArray().apply {
                    put(450)
                    put(500)
                })
                put("credentials", JSONObject().apply {
                    put("mongoDb", JSONObject().apply {
                        put("id", credentialId)
                    })
                })
            })
            
            // Respond Pull
            nodes.put(JSONObject().apply {
                put("parameters", JSONObject().apply {
                    put("respondWith", "json")
                    put("responseBody", "={{ { \"success\": true, \"conversations\": \$json } }}")
                })
                put("name", "Respond Pull")
                put("type", "n8n-nodes-base.respondToWebhook")
                put("typeVersion", 1)
                put("position", JSONArray().apply {
                    put(650)
                    put(500)
                })
            })
            
            put("nodes", nodes)
            
            // Connections
            put("connections", JSONObject().apply {
                put("Webhook - Sync", JSONObject().apply {
                    put("main", JSONArray().apply {
                        put(JSONArray().apply {
                            put(JSONObject().apply {
                                put("node", "Insert to MongoDB")
                                put("type", "main")
                                put("index", 0)
                            })
                        })
                    })
                })
                put("Insert to MongoDB", JSONObject().apply {
                    put("main", JSONArray().apply {
                        put(JSONArray().apply {
                            put(JSONObject().apply {
                                put("node", "Respond")
                                put("type", "main")
                                put("index", 0)
                            })
                        })
                    })
                })
                put("Webhook - Pull", JSONObject().apply {
                    put("main", JSONArray().apply {
                        put(JSONArray().apply {
                            put(JSONObject().apply {
                                put("node", "Query MongoDB")
                                put("type", "main")
                                put("index", 0)
                            })
                        })
                    })
                })
                put("Query MongoDB", JSONObject().apply {
                    put("main", JSONArray().apply {
                        put(JSONArray().apply {
                            put(JSONObject().apply {
                                put("node", "Respond Pull")
                                put("type", "main")
                                put("index", 0)
                            })
                        })
                    })
                })
            })
        }
    }
    
    /**
     * Activate workflow
     */
    private fun activateWorkflow(workflowId: String): Boolean {
        return try {
            val url = URL("$N8N_URL/api/v1/workflows/$workflowId/activate")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                if (N8N_API_KEY.isNotEmpty()) {
                    setRequestProperty("X-N8N-API-KEY", N8N_API_KEY)
                }
            }
            
            connection.responseCode == 200
            
        } catch (e: Exception) {
            Log.e("N8nConfig", "Activation error: ${e.message}")
            false
        }
    }
    
    /**
     * Check if workflow is already configured
     */
    fun isConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences("zyra_memory", Context.MODE_PRIVATE)
        return prefs.getBoolean("n8n_configured", false)
    }
    
    data class SetupResult(
        val success: Boolean,
        val message: String
    )
}
