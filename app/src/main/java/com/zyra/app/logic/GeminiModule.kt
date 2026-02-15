package com.zyra.app.logic

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiModule {

    // Ideally, this should be in BuildConfig.API_KEY
    // For now, we will try to read it from a simpler source or assume it's injected.
    // The user will need to replace this if we don't find it.
    private const val API_KEY = "AIzaSyAoEtzfq9yA8hY9hvKGaRB-BSHmUYFWdyU" 

    private var generativeModel: GenerativeModel? = null
    private var visionModel: GenerativeModel? = null

    fun initialize(apiKey: String = API_KEY) {
        if (apiKey == "YOUR_API_KEY_HERE" || apiKey.isEmpty()) {
            return
        }

        // Text-only model (faster, cheaper)
        generativeModel = GenerativeModel(
            modelName = "gemini-pro",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 1024
            }
        )

        // Vision model
        visionModel = GenerativeModel(
            modelName = "gemini-pro-vision",
            apiKey = apiKey
        )
    }

    suspend fun generateResponse(prompt: String, includeContext: Boolean = true): String {
        return withContext(Dispatchers.IO) {
            val model = generativeModel ?: return@withContext "Error: Online mode is not configured. Please set the API Key."
            
            try {
                // Get conversation context
                val conversationContext = if (includeContext) {
                    try {
                        com.zyra.app.data.EnhancedMemoryModule.getConversationSummary(3)
                    } catch (e: Exception) {
                        ""
                    }
                } else ""
                
                // Build context-aware prompt
                val contextPrefix = if (conversationContext.isNotEmpty()) {
                    "Previous conversation:\n$conversationContext\n\n"
                } else ""
                
                // We wrap the user prompt with some system context
                val systemContext = "You are Zyra, an advanced AI assistant. Keep your answers concise, helpful, and friendly." +
                        " IMPORTANT: If the user provides a personal fact, append ||MEMORY:key|value||." +
                        " ALSO: If the user's command falls into one of these strict categories: [CALL, TIME, DATE, JOKE, BATTERY, GREETING, DEVICE_CONTROL, UNLOCK_DEVICE], " +
                        "append ||TRAIN:CATEGORY||. Example: 'Turn on the light' -> ||TRAIN:DEVICE_CONTROL||. " +
                        " Answer specifically: "
                
                val fullPrompt = "$systemContext\n\n$contextPrefix$prompt"
                
                val response = model.generateContent(fullPrompt)
                response.text ?: "I am online, but I couldn't think of a response."
            } catch (e: Exception) {
                "Error connecting to the online brain: ${e.localizedMessage}"
            }
        }
    }

    suspend fun generateResponseWithImage(prompt: String, image: Bitmap): String {
        return withContext(Dispatchers.IO) {
            val model = visionModel ?: return@withContext "Error: Vision model not configured."
            
            try {
                val inputContent = content {
                    image(image)
                    text(prompt)
                }
                val response = model.generateContent(inputContent)
                response.text ?: "I see the image, but I have no words."
            } catch (e: Exception) {
                "Error processing vision: ${e.localizedMessage}"
            }
        }
    }
    
    fun isConfigured(): Boolean {
        return generativeModel != null
    }
}
