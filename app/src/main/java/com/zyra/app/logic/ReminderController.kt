package com.zyra.app.logic

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.*

/**
 * Reminder Controller - JARVIS-style reminders and alarms
 * "Remind me to call Mom at 5 PM"
 * "Set alarm for 7 AM"
 */
object ReminderController {
    
    private const val PREFS_NAME = "zyra_reminders"
    private var nextReminderId = 1000
    
    /**
     * Set a time-based reminder
     */
    fun setReminder(
        context: Context,
        message: String,
        timeInMillis: Long
    ): Reminder {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reminderId = nextReminderId++
        
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("reminder_id", reminderId)
            putExtra("message", message)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Set exact alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
        }
        
        val reminder = Reminder(
            id = reminderId,
            message = message,
            timeInMillis = timeInMillis,
            isActive = true
        )
        
        saveReminder(context, reminder)
        return reminder
    }
    
    /**
     * Set a reminder after X minutes
     */
    fun setReminderAfter(
        context: Context,
        message: String,
        minutes: Int
    ): Reminder {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, minutes)
        return setReminder(context, message, calendar.timeInMillis)
    }
    
    /**
     * Set an alarm for specific time
     */
    fun setAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        message: String = "Alarm"
    ): Reminder {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            
            // If time has passed today, set for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        return setReminder(context, message, calendar.timeInMillis)
    }
    
    /**
     * Cancel a reminder
     */
    fun cancelReminder(context: Context, reminderId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, ReminderBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        deleteReminder(context, reminderId)
    }
    
    /**
     * Get all active reminders
     */
    fun getActiveReminders(context: Context): List<Reminder> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val reminders = mutableListOf<Reminder>()
        
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("reminder_")) {
                val parts = (value as String).split("|")
                if (parts.size == 4) {
                    reminders.add(
                        Reminder(
                            id = parts[0].toInt(),
                            message = parts[1],
                            timeInMillis = parts[2].toLong(),
                            isActive = parts[3].toBoolean()
                        )
                    )
                }
            }
        }
        
        return reminders.filter { it.isActive }
    }
    
    /**
     * Parse natural language time
     * Examples: "in 30 minutes", "at 5 PM", "tomorrow at 9 AM"
     */
    fun parseTime(timeString: String): Long? {
        val calendar = Calendar.getInstance()
        val lowerTime = timeString.lowercase()
        
        return when {
            // "in X minutes"
            lowerTime.contains("in") && lowerTime.contains("minute") -> {
                val minutes = lowerTime.replace(Regex("[^0-9]"), "").toIntOrNull() ?: return null
                calendar.add(Calendar.MINUTE, minutes)
                calendar.timeInMillis
            }
            
            // "in X hours"
            lowerTime.contains("in") && lowerTime.contains("hour") -> {
                val hours = lowerTime.replace(Regex("[^0-9]"), "").toIntOrNull() ?: return null
                calendar.add(Calendar.HOUR_OF_DAY, hours)
                calendar.timeInMillis
            }
            
            // "at X PM/AM"
            lowerTime.contains("at") -> {
                parseTimeOfDay(lowerTime)
            }
            
            // "tomorrow at X"
            lowerTime.contains("tomorrow") -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                parseTimeOfDay(lowerTime) ?: calendar.timeInMillis
            }
            
            else -> null
        }
    }
    
    private fun parseTimeOfDay(timeString: String): Long? {
        val calendar = Calendar.getInstance()
        val isPM = timeString.contains("pm")
        val isAM = timeString.contains("am")
        
        // Extract hour
        val hourMatch = Regex("(\\d{1,2})").find(timeString) ?: return null
        var hour = hourMatch.value.toInt()
        
        // Convert to 24-hour format
        if (isPM && hour < 12) hour += 12
        if (isAM && hour == 12) hour = 0
        
        // Extract minute if present
        val minuteMatch = Regex(":(\\d{2})").find(timeString)
        val minute = minuteMatch?.groupValues?.get(1)?.toInt() ?: 0
        
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        
        // If time has passed today, set for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        return calendar.timeInMillis
    }
    
    private fun saveReminder(context: Context, reminder: Reminder) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(
            "reminder_${reminder.id}",
            "${reminder.id}|${reminder.message}|${reminder.timeInMillis}|${reminder.isActive}"
        ).apply()
    }
    
    private fun deleteReminder(context: Context, reminderId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("reminder_$reminderId").apply()
    }
    
    data class Reminder(
        val id: Int,
        val message: String,
        val timeInMillis: Long,
        val isActive: Boolean
    )
}

/**
 * Broadcast Receiver for reminders
 */
class ReminderBroadcastReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: "Reminder"
        val reminderId = intent.getIntExtra("reminder_id", 0)
        
        // Show notification
        showReminderNotification(context, message, reminderId)
        
        // Speak the reminder if Zyra is active
        speakReminder(context, message)
    }
    
    private fun showReminderNotification(context: Context, message: String, id: Int) {
        // TODO: Implement notification
    }
    
    private fun speakReminder(context: Context, message: String) {
        // TODO: Integrate with VoiceAssistantService to speak reminder
    }
}
