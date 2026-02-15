package com.zyra.app.logic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Calendar Intelligence
 * Provides smart calendar integration and reminders
 */
class CalendarIntelligence(private val context: Context) {
    
    data class CalendarEvent(
        val id: Long,
        val title: String,
        val startTime: Long,
        val endTime: Long,
        val location: String?,
        val description: String?
    )
    
    /**
     * Get upcoming events
     */
    fun getUpcomingEvents(hours: Int = 24): List<CalendarEvent> {
        if (!hasCalendarPermission()) {
            return emptyList()
        }
        
        val events = mutableListOf<CalendarEvent>()
        val now = System.currentTimeMillis()
        val endTime = now + (hours * 3600000L)
        
        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DESCRIPTION
                ),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(now.toString(), endTime.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    events.add(parseEvent(it))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return events
    }
    
    /**
     * Get events for today
     */
    fun getTodayEvents(): List<CalendarEvent> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis
        
        return getEventsInRange(startOfDay, endOfDay)
    }
    
    /**
     * Get next event
     */
    fun getNextEvent(): CalendarEvent? {
        return getUpcomingEvents(168).firstOrNull() // Next week
    }
    
    /**
     * Get smart reminder for event
     */
    fun getSmartReminder(event: CalendarEvent, currentLocation: LocationIntelligence.LatLng? = null): String? {
        val minutesUntilEvent = (event.startTime - System.currentTimeMillis()) / 60000
        
        // Calculate travel time if location is provided
        var travelTime = 0
        if (event.location != null && currentLocation != null) {
            travelTime = estimateTravelTime(currentLocation, event.location)
        }
        
        return when {
            minutesUntilEvent <= 5 -> "Your event '${event.title}' starts in 5 minutes!"
            minutesUntilEvent <= 15 -> "Your event '${event.title}' starts in 15 minutes."
            minutesUntilEvent <= travelTime + 15 -> {
                "You should leave now for '${event.title}'. Estimated travel time: $travelTime minutes."
            }
            minutesUntilEvent <= 60 -> "Reminder: '${event.title}' in ${minutesUntilEvent.toInt()} minutes."
            else -> null
        }
    }
    
    /**
     * Detect schedule conflicts
     */
    fun detectConflicts(): List<Pair<CalendarEvent, CalendarEvent>> {
        val events = getUpcomingEvents(168) // Next week
        val conflicts = mutableListOf<Pair<CalendarEvent, CalendarEvent>>()
        
        for (i in events.indices) {
            for (j in i + 1 until events.size) {
                val event1 = events[i]
                val event2 = events[j]
                
                if (eventsOverlap(event1, event2)) {
                    conflicts.add(event1 to event2)
                }
            }
        }
        
        return conflicts
    }
    
    /**
     * Get schedule summary
     */
    fun getScheduleSummary(hours: Int = 24): String {
        val events = getUpcomingEvents(hours)
        
        return when {
            events.isEmpty() -> "You have no events scheduled in the next $hours hours."
            events.size == 1 -> "You have 1 event: ${events[0].title} at ${formatTime(events[0].startTime)}."
            else -> {
                val summary = StringBuilder("You have ${events.size} events:\n")
                events.take(5).forEach { event ->
                    summary.append("- ${event.title} at ${formatTime(event.startTime)}\n")
                }
                if (events.size > 5) {
                    summary.append("... and ${events.size - 5} more")
                }
                summary.toString()
            }
        }
    }
    
    /**
     * Check if user is free at a specific time
     */
    fun isFreeAt(timeMillis: Long): Boolean {
        val events = getEventsInRange(timeMillis - 1000, timeMillis + 1000)
        return events.isEmpty()
    }
    
    /**
     * Find next free slot
     */
    fun findNextFreeSlot(durationMinutes: Int = 60): Long? {
        val events = getUpcomingEvents(168)
        var currentTime = System.currentTimeMillis()
        
        for (event in events) {
            val gap = event.startTime - currentTime
            if (gap >= durationMinutes * 60000) {
                return currentTime
            }
            currentTime = event.endTime
        }
        
        return currentTime
    }
    
    /**
     * Parse event from cursor
     */
    private fun parseEvent(cursor: Cursor): CalendarEvent {
        return CalendarEvent(
            id = cursor.getLong(0),
            title = cursor.getString(1) ?: "Untitled Event",
            startTime = cursor.getLong(2),
            endTime = cursor.getLong(3),
            location = cursor.getString(4),
            description = cursor.getString(5)
        )
    }
    
    /**
     * Get events in time range
     */
    private fun getEventsInRange(startTime: Long, endTime: Long): List<CalendarEvent> {
        if (!hasCalendarPermission()) {
            return emptyList()
        }
        
        val events = mutableListOf<CalendarEvent>()
        
        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DESCRIPTION
                ),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(startTime.toString(), endTime.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    events.add(parseEvent(it))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return events
    }
    
    /**
     * Check if events overlap
     */
    private fun eventsOverlap(event1: CalendarEvent, event2: CalendarEvent): Boolean {
        return event1.startTime < event2.endTime && event2.startTime < event1.endTime
    }
    
    /**
     * Estimate travel time (simplified)
     */
    private fun estimateTravelTime(from: LocationIntelligence.LatLng, toAddress: String): Int {
        // This is a simplified version
        // In production, you'd use Google Maps API or similar
        return 15 // Default 15 minutes
    }
    
    /**
     * Format time for display
     */
    private fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }
    
    /**
     * Check calendar permission
     */
    private fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }
}
