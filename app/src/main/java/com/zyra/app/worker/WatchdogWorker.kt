package com.zyra.app.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.zyra.app.service.VoiceAssistantService

class WatchdogWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        Log.d("ZyraWatchdog", "Checking Service status...")
        
        // Strategy: We can't easily check if specific service is running without deprecated APIs or ActivityManager (expensive).
        // Since VoiceAssistantService checks !isListening in onStartCommand and handles idempotency,
        // we can just send an Intent to Start it. If it's running, onStartCommand fires and does nothing/refreshes.
        // If it was dead, it restarts.
        
        val intent = Intent(applicationContext, VoiceAssistantService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
        
        return Result.success()
    }
}
