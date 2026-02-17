package com.zyra.app.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Stock Controller - Stock market data
 * "Tesla stock price"
 * "How's the market doing?"
 */
object StockController {
    
    // Alpha Vantage API key (FREE - 500 calls/day)
    private val API_KEY = com.zyra.app.BuildConfig.ALPHA_VANTAGE_API_KEY
    
    /**
     * Get stock price
     */
    suspend fun getStockPrice(symbol: String): StockInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://www.alphavantage.co/query?" +
                    "function=GLOBAL_QUOTE&" +
                    "symbol=$symbol&" +
                    "apikey=$API_KEY"
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val quote = json.getJSONObject("Global Quote")
                    
                    StockInfo(
                        symbol = symbol,
                        price = quote.getString("05. price").toDouble(),
                        change = quote.getString("09. change").toDouble(),
                        changePercent = quote.getString("10. change percent")
                    )
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Format for speech
     */
    fun formatForSpeech(stock: StockInfo): String {
        val direction = if (stock.change >= 0) "up" else "down"
        return "${stock.symbol} is trading at $${stock.price}, $direction ${stock.changePercent}"
    }
    
    data class StockInfo(
        val symbol: String,
        val price: Double,
        val change: Double,
        val changePercent: String
    )
}
