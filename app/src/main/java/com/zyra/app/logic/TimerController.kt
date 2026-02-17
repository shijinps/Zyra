package com.zyra.app.logic

import android.content.Context
import android.os.CountDownTimer
import android.os.SystemClock
import kotlin.math.roundToLong

/**
 * Timer Controller - Timers and stopwatch
 * "Set timer for 10 minutes"
 * "Start stopwatch"
 */
object TimerController {
    
    private var currentTimer: CountDownTimer? = null
    private var stopwatchStartTime: Long = 0
    private var stopwatchElapsedTime: Long = 0
    private var isStopwatchRunning = false
    
    /**
     * Set a countdown timer
     */
    fun setTimer(durationMinutes: Int, onTick: (Long) -> Unit, onFinish: () -> Unit): Boolean {
        cancelTimer()
        
        val durationMillis = durationMinutes * 60 * 1000L
        
        currentTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                onTick(millisUntilFinished)
            }
            
            override fun onFinish() {
                onFinish()
            }
        }.start()
        
        return true
    }
    
    /**
     * Cancel current timer
     */
    fun cancelTimer() {
        currentTimer?.cancel()
        currentTimer = null
    }
    
    /**
     * Start stopwatch
     */
    fun startStopwatch() {
        if (!isStopwatchRunning) {
            stopwatchStartTime = SystemClock.elapsedRealtime() - stopwatchElapsedTime
            isStopwatchRunning = true
        }
    }
    
    /**
     * Pause stopwatch
     */
    fun pauseStopwatch() {
        if (isStopwatchRunning) {
            stopwatchElapsedTime = SystemClock.elapsedRealtime() - stopwatchStartTime
            isStopwatchRunning = false
        }
    }
    
    /**
     * Reset stopwatch
     */
    fun resetStopwatch() {
        stopwatchStartTime = 0
        stopwatchElapsedTime = 0
        isStopwatchRunning = false
    }
    
    /**
     * Get stopwatch elapsed time
     */
    fun getStopwatchTime(): Long {
        return if (isStopwatchRunning) {
            SystemClock.elapsedRealtime() - stopwatchStartTime
        } else {
            stopwatchElapsedTime
        }
    }
    
    /**
     * Format time for speech
     */
    fun formatTimeForSpeech(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        
        return when {
            hours > 0 -> "$hours hours, $minutes minutes, and $seconds seconds"
            minutes > 0 -> "$minutes minutes and $seconds seconds"
            else -> "$seconds seconds"
        }
    }
}
