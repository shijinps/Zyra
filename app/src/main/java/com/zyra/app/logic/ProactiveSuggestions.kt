package com.zyra.app.logic

import android.content.Context
import java.util.*

/**
 * Proactive Suggestions - JARVIS-style intelligence
 * Suggests things based on context
 */
object ProactiveSuggestions {
    
    /**
     * Get morning suggestions
     */
    fun getMorningSuggestions(context: Context): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        
        // Battery check
        val battery = DeviceDiagnostics.getBatteryInfo(context)
        if (battery.level < 30 && !battery.isCharging) {
            suggestions.add(
                Suggestion(
                    type = SuggestionType.BATTERY,
                    message = "Battery's at ${battery.level}%. Want me to enable power saving mode?",
                    action = "enable_power_saving"
                )
            )
        }
        
        // Weather-based suggestions
        // TODO: Add weather-based suggestions
        
        return suggestions
    }
    
    /**
     * Get traffic suggestions
     */
    fun getTrafficSuggestions(context: Context): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        
        // TODO: Implement traffic checking
        // If heavy traffic, suggest leaving early
        
        return suggestions
    }
    
    /**
     * Get battery suggestions
     */
    fun getBatterySuggestions(context: Context): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        
        val battery = DeviceDiagnostics.getBatteryInfo(context)
        
        when {
            battery.level <= 15 && !battery.isCharging -> {
                suggestions.add(
                    Suggestion(
                        type = SuggestionType.BATTERY,
                        message = "Battery critically low at ${battery.level}%. Should I enable power saving mode?",
                        action = "enable_power_saving"
                    )
                )
            }
            battery.level <= 30 && !battery.isCharging -> {
                suggestions.add(
                    Suggestion(
                        type = SuggestionType.BATTERY,
                        message = "Battery's getting low at ${battery.level}%. Might want to charge soon.",
                        action = "remind_charge"
                    )
                )
            }
        }
        
        return suggestions
    }
    
    /**
     * Get context-aware suggestions
     */
    fun getContextSuggestions(context: Context): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        
        // Morning suggestions (6 AM - 10 AM)
        if (hour in 6..10) {
            suggestions.addAll(getMorningSuggestions(context))
        }
        
        // Always check battery
        suggestions.addAll(getBatterySuggestions(context))
        
        return suggestions
    }
    
    data class Suggestion(
        val type: SuggestionType,
        val message: String,
        val action: String
    )
    
    enum class SuggestionType {
        BATTERY,
        TRAFFIC,
        WEATHER,
        CALENDAR,
        REMINDER
    }
}
