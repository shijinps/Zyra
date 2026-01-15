package com.zyra.app.logic

import com.zyra.app.data.MemoryModule
import java.util.Calendar

/**
 * The "Reasoner" for Zyra's Proactive / Autonomous capabilities.
 * Checks system state and decides if Zyra should speak without being spoken to.
 */
object AutonomyEngine {

    // Thresholds
    private const val SILENCE_THRESHOLD_MS = 10 * 60 * 1000L // 10 Minutes
    
    fun evaluateState(lastInteractionTime: Long): ProactiveDecision? {
        val now = System.currentTimeMillis()
        val timeSinceInteraction = now - lastInteractionTime
        
        // 1. SILENCE TRIGGER (Boredom Loop)
        // If it's been quiet for > 10 mins, maybe suggest something based on context
        if (timeSinceInteraction > SILENCE_THRESHOLD_MS) {
            // Only trigger if we haven't triggered recently (caller should handle "last triggered" logic)
            // Decisions:
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            
            // Morning Check (8 AM - 9 AM)
            if (hour == 8) {
                return ProactiveDecision("Sir, it is 8 AM. Systems are optimal. Have you reviewed your agenda?")
            }
            
            // Late Night Check (1 AM - 4 AM)
            if (hour in 1..4) {
                return ProactiveDecision("You are burning the midnight oil, Sir. Perhaps some focus music?")
            }
            
            // Random "Thought" (Simulated Consciousness)
            val pref = MemoryModule.getFact("user_preference")
            if (pref != null) {
                 return ProactiveDecision("It has been quiet. I was analyzing your preference for ${pref}. Shall we explore that?")
            } else {
                 return ProactiveDecision(PersonaManager.getPhilosophicalResponse())
            }
        }
        
        return null
    }
}

data class ProactiveDecision(
    val speech: String,
    val action: String? = null // Future: link to OpenApp etc.
)
