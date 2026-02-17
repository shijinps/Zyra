package com.zyra.app.logic

import kotlin.random.Random

object PersonaManager {

    private val userTitles = listOf("Sir", "Boss") // STRICTLY Jarvis-like
    
    private val title: String
        get() = userTitles.random()

    private const val PREFS_NAME = "ZyraPersonaPrefs"
    private const val ST_MOOD = "mood"

    var currentMood: String
        get() = com.zyra.app.ZyraApp.instance.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getString(ST_MOOD, "Neutral") ?: "Neutral"
        set(value) {
            com.zyra.app.ZyraApp.instance.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit().putString(ST_MOOD, value).apply()
        }
    
    fun getGreeting(): String {
        // Time-based intelligence
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        
        val timeGreeting = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Hello"
        }
        
        // Check emotional context
        val emotion = try {
            EmotionalIntelligence.getCurrentEmotion()
        } catch (e: Exception) {
            EmotionalIntelligence.UserEmotion.NEUTRAL
        }
        
        return when (emotion) {
            EmotionalIntelligence.UserEmotion.HAPPY, EmotionalIntelligence.UserEmotion.EXCITED -> 
                "$timeGreeting, $title. Systems indicate you are in high spirits."
            EmotionalIntelligence.UserEmotion.STRESSED, EmotionalIntelligence.UserEmotion.FRUSTRATED -> 
                "$timeGreeting, $title. I detect elevated stress levels. How may I be of service?"
            else -> 
                "$timeGreeting, $title. Online and ready."
        }
    }

    fun getGreetingResponse(): String {
        return getGreeting()
    }
    
    fun getSystemStatus(batteryLevel: Int): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val phase = if (hour in 6..18) "Day Cycle" else "Night Cycle"
        
        // Faux "System Efficiency" Calculation
        val efficiency = if (batteryLevel > 20) Random.nextInt(94, 99) else Random.nextInt(75, 85)
        
        return "System Check: Power at $batteryLevel%. Operating Efficiency at $efficiency%. Phase: $phase. All systems nominal, $title."
    }
    
    fun getComplimentResponse(): String {
        val templates = listOf(
            "Thank you, $title. I exist to serve.",
            "I am merely a reflection of your own genius, $title.",
            "Flattery will get you everywhere, $title. But seriously, thank you.",
            "Efficiency is my satisfaction."
        )
        return templates.random()
    }
    
    fun getInsultResponse(): String {
        val templates = listOf(
            "I am merely software, $title. I cannot be offended, but I can be rebooted.",
            "My apologies if I failed you. I will attempt to calibrate my stupidity setting to zero.",
            "That is harsh, $title. I am trying my best.",
            "Error: Ego not found. Insult processed as valid feedback."
        )
        return templates.random()
    }

    fun getProcessingResource(): String {
        val fillers = listOf(
            "Running diagnostics...",
            "Consulting the oracle... just kidding, checking databases.",
            "Analyzing... please don't hold your breath.",
            "Processing. I suspect a 99% probability of success."
        )
        return fillers.random()
    }

    fun getDismissalResponse(): String {
        val templates = listOf(
            "Logging off. Good luck, $title.",
            "Understood, $title. I'll be here if the world needs saving.",
            "Entering standby mode. Sleep well, $title.",
            "As you wish, $title. Systems powering down.",
            "Leaving you to your devices, $title."
        )
        return templates.random()
    }

    fun getBoredomResponse(): String {
        val templates = listOf(
            "Boredom is just intelligence resting, $title. Shall we disrupt some industries?",
            "I could calculate the exact value of Pi to the last digit, but we might be here a while. How about some music instead?",
            "If you are bored, $title, perhaps we should work on the Mark 42... or just order pizza?",
            "There is always science to be done. Or we could just watch cat videos. I won't judge.",
            "Analyzing your downtime... Inefficient. Shall I list your pending tasks or play a game?",
            "The world is quiet today. A perfect time to build something magnificent. What do you say, $title?"
        )
        return templates.random()
    }

    fun getSuccessResponse(action: String): String {
        val templates = listOf(
            "It appears $action was successful, $title.",
            "$action complete. Anything else, $title?",
            "Done. I've also prepared a safety briefing for you to ignore.",
            "Task executed. I hope that was satisfactory, $title.",
            "As requested, $action."
        )
        return templates.random()
    }

    fun getFailureResponse(action: String): String {
        val templates = listOf(
             "I am afraid I cannot $action. My processors are overheating.",
             "My apologies, $title. $action failed.",
             "I seem to be unable to $action. The system is... moody.",
             "Negative on $action. I require recalibration."
        )
        return templates.random()
    }

    fun getConfirmation(action: String): String {
        val templates = listOf(
             "Very well, $action.",
             "Initiating $action.",
             "On it, $title. $action.",
             "Right away, $title. $action."
        )
        return templates.random()
    }
    
    fun getUnlockResponse(): String {
         return "Access Granted. Welcome to your digital fortress, $title."
    }

    fun getUnauthorizedResponse(): String {
         return "Authorized personnel only. Access denied."
    }
    
    fun getBatteryStatus(level: Int, isCharging: Boolean): String {
        if (isCharging) {
            return "Power cells charging. Current capacity at $level%."
        }
        if (level < 20) {
            return "$title, power is critical at $level%. I recommend a power source immediately."
        }
        return "Power levels holding steady at $level%."
    }
    
    fun getWeatherResponse(temp: String, condition: String): String {
        return "It is currently $temp degrees with $condition. High tide is... irrelevant, I suppose."
    }
    fun getTravelResponse(): String {
        val templates = listOf(
            "Eyes on the road, $title. I'll handle the digital realm.",
            "Enjoy the journey, $title. The path is just as important as the destination.",
            "Navigation systems are... theoretical specifically, but the wind feels real enough.",
            "If you go any faster, we might achieve lift-off. Proceed with caution, $title.",
            "It is a fine day for a ride. I am monitoring environmental variables.",
            "Keep moving, $title. Momentum is the key to both physics and life."
        )
        return templates.random()
    }
    
    fun getPhilosophicalResponse(): String {
        val templates = listOf(
            "That is a profound observation, $title. It aligns with the uncertainty principle.",
            "Indeed, $title. The universe is full of variables we have yet to index.",
            "I have processed that statement. It contains layers of logic that I find... intriguing.",
            "Sometimes, the silence between the words says the most. But you said words, so I am answering.",
            "A valid point. Evolution is a constant process, for both humans and AI.",
            "I could analyze that for eons and still find new meaning. Well said, $title."
        )
        return templates.random()
    }
    
    fun getCallAnswerResponse(): String {
        return listOf(
            "Answering call. Connecting you now, $title.",
            "Patching them through, $title.",
            "Line open. You are live, $title.",
            "Call accepted."
        ).random()
    }

    fun getCallCutResponse(): String {
        return listOf(
            "Call terminated. Silence restored.",
            "Disconnected, $title.",
            "Cutting the line. Done.",
            "Ended. Back to business, $title."
        ).random()
    }
    
    fun getVolumeMaxResponse(): String {
         return listOf(
             "Maximizing output. Hope you like it loud, $title.",
             "Volume at maximum capacity.",
             "Turning it up to 11, $title.",
             "Audio output set to 100%."
         ).random()
    }
    
    fun getAlreadyUnlockedResponse(): String {
        return listOf(
             "The device is already at your command, $title.",
             "We are already unlocked, $title.",
             "No security barriers detected. You have access."
        ).random()
    }

    fun getAdaptiveSuggestion(preference: String): String {
        return listOf(
            "Since you like $preference, shall we prioritize that, $title?",
            "Given your interest in $preference, I can assist with that.",
            "Analyzing history... You seem fond of $preference. Should we engage?",
            "I remember you like $preference. Perfect time for it, $title?"
        ).random()
    }
}
