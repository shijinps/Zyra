package com.zyra.app.logic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Information Controller - JARVIS-level intelligence
 * Provides weather, news, and general information
 */
object InformationController {
    
    // API keys from BuildConfig
    private val OPENWEATHER_API_KEY = com.zyra.app.BuildConfig.OPENWEATHER_API_KEY
    private val NEWS_API_KEY = com.zyra.app.BuildConfig.NEWS_API_KEY
    
    /**
     * Get current weather for location
     */
    suspend fun getWeather(city: String = "Bangalore"): WeatherInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.openweathermap.org/data/2.5/weather?" +
                    "q=$city&" +
                    "appid=$OPENWEATHER_API_KEY&" +
                    "units=metric"
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    
                    val main = json.getJSONObject("main")
                    val weather = json.getJSONArray("weather").getJSONObject(0)
                    
                    WeatherInfo(
                        temperature = main.getDouble("temp").toInt(),
                        feelsLike = main.getDouble("feels_like").toInt(),
                        description = weather.getString("description"),
                        humidity = main.getInt("humidity"),
                        city = json.getString("name")
                    )
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Get weather forecast for next 5 days
     */
    suspend fun getWeatherForecast(city: String = "Bangalore"): List<ForecastInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.openweathermap.org/data/2.5/forecast?" +
                    "q=$city&" +
                    "appid=$OPENWEATHER_API_KEY&" +
                    "units=metric&" +
                    "cnt=5"
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val list = json.getJSONArray("list")
                    
                    (0 until list.length()).map { i ->
                        val item = list.getJSONObject(i)
                        val main = item.getJSONObject("main")
                        val weather = item.getJSONArray("weather").getJSONObject(0)
                        
                        ForecastInfo(
                            temperature = main.getDouble("temp").toInt(),
                            description = weather.getString("description"),
                            time = item.getString("dt_txt")
                        )
                    }
                } else emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Get top news headlines
     */
    suspend fun getNews(country: String = "in", category: String = "general"): List<NewsItem> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://newsapi.org/v2/top-headlines?" +
                    "country=$country&" +
                    "category=$category&" +
                    "pageSize=5&" +
                    "apiKey=$NEWS_API_KEY"
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val articles = json.getJSONArray("articles")
                    
                    (0 until articles.length()).map { i ->
                        val article = articles.getJSONObject(i)
                        NewsItem(
                            title = article.getString("title"),
                            description = article.optString("description", ""),
                            source = article.getJSONObject("source").getString("name"),
                            publishedAt = article.getString("publishedAt")
                        )
                    }
                } else emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Get morning briefing (JARVIS-style)
     */
    suspend fun getMorningBriefing(context: Context): MorningBriefing {
        val weather = getWeather()
        val news = getNews()
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
        
        return MorningBriefing(
            greeting = getGreeting(),
            time = time,
            date = date,
            weather = weather,
            topNews = news.take(3)
        )
    }
    
    /**
     * Get time-appropriate greeting
     */
    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..11 -> "Good morning, sir"
            in 12..16 -> "Good afternoon, sir"
            in 17..20 -> "Good evening, sir"
            else -> "Good night, sir"
        }
    }
    
    /**
     * Format weather info for speech
     */
    fun formatWeatherForSpeech(weather: WeatherInfo?): String {
        return if (weather != null) {
            "It's currently ${weather.temperature}°C in ${weather.city}, " +
            "with ${weather.description}. " +
            "Humidity is at ${weather.humidity}%."
        } else {
            "I'm unable to fetch weather information at the moment."
        }
    }
    
    /**
     * Format news for speech
     */
    fun formatNewsForSpeech(news: List<NewsItem>): String {
        return if (news.isNotEmpty()) {
            val headlines = news.take(3).mapIndexed { index, item ->
                "${index + 1}. ${item.title}"
            }.joinToString(". ")
            "Here are the top headlines: $headlines"
        } else {
            "I'm unable to fetch news at the moment."
        }
    }
    
    /**
     * Format morning briefing for speech
     */
    fun formatMorningBriefingForSpeech(briefing: MorningBriefing): String {
        val weatherText = if (briefing.weather != null) {
            "The weather is ${briefing.weather.temperature}°C with ${briefing.weather.description}."
        } else {
            ""
        }
        
        val newsText = if (briefing.topNews.isNotEmpty()) {
            "Top news: ${briefing.topNews[0].title}"
        } else {
            ""
        }
        
        return "${briefing.greeting}. It's ${briefing.time} on ${briefing.date}. $weatherText $newsText"
    }
    
    data class WeatherInfo(
        val temperature: Int,
        val feelsLike: Int,
        val description: String,
        val humidity: Int,
        val city: String
    )
    
    data class ForecastInfo(
        val temperature: Int,
        val description: String,
        val time: String
    )
    
    data class NewsItem(
        val title: String,
        val description: String,
        val source: String,
        val publishedAt: String
    )
    
    data class MorningBriefing(
        val greeting: String,
        val time: String,
        val date: String,
        val weather: WeatherInfo?,
        val topNews: List<NewsItem>
    )
}
