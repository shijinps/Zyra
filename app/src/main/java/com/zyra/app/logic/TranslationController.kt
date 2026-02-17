package com.zyra.app.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Translation Controller - Multi-language translation
 * "Translate hello to French"
 * "How do you say thank you in Spanish?"
 */
object TranslationController {
    
    // Google Translate API key (add to BuildConfig)
    private val API_KEY = com.zyra.app.BuildConfig.GOOGLE_TRANSLATE_API_KEY
    
    /**
     * Translate text to target language
     */
    suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String = "auto"
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val url = "https://translation.googleapis.com/language/translate/v2?" +
                    "q=$encodedText&" +
                    "target=${getLanguageCode(targetLanguage)}&" +
                    "key=$API_KEY"
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    json.getJSONObject("data")
                        .getJSONArray("translations")
                        .getJSONObject(0)
                        .getString("translatedText")
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Get language code from language name
     */
    private fun getLanguageCode(language: String): String {
        return when (language.lowercase()) {
            "french", "france" -> "fr"
            "spanish", "spain" -> "es"
            "german", "germany" -> "de"
            "italian", "italy" -> "it"
            "japanese", "japan" -> "ja"
            "chinese", "china" -> "zh"
            "korean", "korea" -> "ko"
            "hindi", "india" -> "hi"
            "arabic" -> "ar"
            "russian", "russia" -> "ru"
            "portuguese", "portugal" -> "pt"
            else -> language.take(2)
        }
    }
}
