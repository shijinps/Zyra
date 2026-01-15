package com.zyra.app.service

import android.content.Intent
import android.speech.RecognitionService
import android.util.Log

/**
 * A stub RecognitionService compliant with Android's Voice Interaction requirements.
 * This effectively delegates the actual listening logic to our existing [VoiceAssistantService]
 * via the system, or acts as a placeholder so the app appears in "Default Digital Assistant".
 */
class ZyraRecognitionService : RecognitionService() {

    override fun onStartListening(intent: Intent?, listener: Callback?) {
        Log.d("ZyraRecognition", "onStartListening called by system")
        // In a full implementation, we would bridge this to SpeechRecognizer.
        // For now, we rely on VoiceAssistantService doing the heavy lifting 
        // via its own Hotword detection or manual trigger.
        // We notify error to prevent the system from waiting indefinitely if it expects a result.
        // Error 7 is ERROR_NO_MATCH
        // listener?.error(7) 
    }

    override fun onStopListening(listener: Callback?) {
        Log.d("ZyraRecognition", "onStopListening")
    }

    override fun onCancel(listener: Callback?) {
        Log.d("ZyraRecognition", "onCancel")
    }
}
