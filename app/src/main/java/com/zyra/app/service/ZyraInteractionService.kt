package com.zyra.app.service

import android.service.voice.VoiceInteractionService

class ZyraInteractionService : VoiceInteractionService() {
    // This service is mainly an entry point for the system to bind to.
    
    override fun onReady() {
        super.onReady()
        android.util.Log.d("ZyraInteraction", "System bound to Zyra Assistant. Starting persistent voice service...")
        
        // CRITICAL: Since we are now the "Default Assistant", the system keeps US alive.
        // We use this privilege to keep our specialized VoiceAssistantService alive too.
        val serviceIntent = android.content.Intent(this, VoiceAssistantService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
