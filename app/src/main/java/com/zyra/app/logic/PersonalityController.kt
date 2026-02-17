package com.zyra.app.logic

import kotlin.random.Random

/**
 * Personality Controller - Makes Zyra talk like a human
 * Uses user's names (Zyco/Shijin) randomly and adds conversational flair
 */
object PersonalityController {
    
    // User names with weighted probability - STRICTLY Sir/Boss
    private val names = listOf(
        "Sir" to 60,      // 60% probability
        "Boss" to 40,     // 40% probability
        "Chief" to 0      // 0% probability (Reserved)
    )
    
    /**
     * Get a random name based on weighted probability
     */
    fun getRandomName(): String {
        val totalWeight = names.sumOf { it.second }
        var randomWeight = Random.nextInt(totalWeight)
        
        for ((name, weight) in names) {
            randomWeight -= weight
            if (randomWeight < 0) {
                return name
            }
        }
        return "Sir" // Fallback
    }
    
    /**
     * Personalize a message with user's name
     */
    fun personalize(message: String): String {
        val name = getRandomName()
        return when {
            message.contains("Sir") || message.contains("Boss") -> message // Already personalized
            message.endsWith(".") -> message.dropLast(1) + ", $name."
            message.endsWith("!") -> message.dropLast(1) + ", $name." // Jarvis uses periods mostly
            else -> "$message, $name"
        }
    }
    
    /**
     * Make response conversational (J.A.R.V.I.S. Style)
     */
    fun makeConversational(category: String, data: Map<String, Any>): String {
        return when (category) {
            "weather" -> weatherResponse(data)
            "news" -> newsResponse(data)
            "battery" -> batteryResponse(data)
            "diagnostics" -> diagnosticsResponse(data)
            "music_next" -> musicNextResponse()
            "music_previous" -> musicPreviousResponse()
            "music_pause" -> musicPauseResponse()
            "music_play" -> musicPlayResponse()
            "call_started" -> callStartedResponse(data)
            "navigation" -> navigationResponse(data)
            "reminder_set" -> reminderSetResponse(data)
            "task_added" -> taskAddedResponse(data)
            "greeting_morning" -> morningGreeting()
            "greeting_afternoon" -> afternoonGreeting()
            "greeting_evening" -> eveningGreeting()
            "error" -> errorResponse()
            "acknowledgment" -> acknowledgmentResponse()
            else -> personalizeGeneric(data["message"] as? String ?: "As you wish.")
        }
    }
    
    // Weather responses - Concise & Informative
    private fun weatherResponse(data: Map<String, Any>): String {
        val temp = data["temperature"] as? Int ?: 25
        val description = data["description"] as? String ?: "clear"
        
        val responses = listOf(
            "Current conditions are $description at $temp degrees.",
            "It is currently $temp degrees and $description.",
            "Weather report: $temp degrees, $description.",
            "Outside temperature is $temp degrees with $description skies."
        )
        return personalize(responses.random())
    }
    
    // News responses
    private fun newsResponse(data: Map<String, Any>): String {
        val headline = data["headline"] as? String ?: "updates"
        
        val responses = listOf(
            "Latest headline: $headline.",
            "I found this item of interest: $headline.",
            "Current events update: $headline.",
            "In the news: $headline."
        )
        return personalize(responses.random())
    }
    
    // Battery responses - Technical & Dry
    private fun batteryResponse(data: Map<String, Any>): String {
        val level = data["level"] as? Int ?: 50
        val isCharging = data["isCharging"] as? Boolean ?: false
        
        return when {
            isCharging -> personalize(listOf(
                "Power levels at $level% and charging.",
                "Charging initiated. Current level: $level%.",
                "Power source connected. Level: $level%."
            ).random())
            level >= 80 -> personalize(listOf(
                "Power levels distinctively high at $level%.",
                "Systems functioning at near full capacity. $level%.",
                "Battery is ample at $level%."
            ).random())
            level >= 50 -> personalize(listOf(
                "Power levels nominal at $level%.",
                "Battery at $level%. Systems stable.",
                "Remaining power: $level%."
            ).random())
            level >= 20 -> personalize(listOf(
                "Power levels dropping to $level%.",
                "Battery at $level%. I suggest charging soon.",
                "Energy levels at $level%."
            ).random())
            else -> personalize(listOf(
                "Critical power alert. Level at $level%.",
                "Power reserves critically low. $level%.",
                "I advise connecting to a power source immediately. $level% remaining."
            ).random())
        }
    }
    
    // Diagnostics responses
    private fun diagnosticsResponse(data: Map<String, Any>): String {
        val responses = listOf(
            "All systems operational.",
            "Diagnostics complete. No anomalies detected.",
            "Systems check complete. We are green.",
            "Functionality is at 100%."
        )
        return personalize(responses.random())
    }
    
    // Music control responses
    private fun musicNextResponse(): String {
        val responses = listOf(
            "Skipping track.",
            "Next track queued.",
            "Advancing playlist.",
            "Playing next."
        )
        return personalize(responses.random())
    }
    
    private fun musicPreviousResponse(): String {
        val responses = listOf(
            "Replaying previous track.",
            "Rewinding.",
            "Returning to the last song.",
            "Previous track."
        )
        return personalize(responses.random())
    }
    
    private fun musicPauseResponse(): String {
        val responses = listOf(
            "Pausing playback.",
            "Audio suspended.",
            "Music paused.",
            "Holding track."
        )
        return personalize(responses.random())
    }
    
    private fun musicPlayResponse(): String {
        val responses = listOf(
            "Resuming playback.",
            "Audio restored.",
            "Continuing playlist.",
            "Dropping the needle."
        )
        return personalize(responses.random())
    }
    
    // Call responses
    private fun callStartedResponse(data: Map<String, Any>): String {
        val contact = data["contact"] as? String ?: "contact"
        val responses = listOf(
            "Dialing $contact.",
            "Initiating call to $contact.",
            "Connecting you with $contact.",
            "Calling $contact."
        )
        return personalize(responses.random())
    }
    
    // Navigation responses
    private fun navigationResponse(data: Map<String, Any>): String {
        val destination = data["destination"] as? String ?: "destination"
        val responses = listOf(
            "Setting course for $destination.",
            "Plotting route to $destination.",
            "Navigating to $destination.",
            "Route confirmed to $destination."
        )
        return personalize(responses.random())
    }
    
    // Reminder responses
    private fun reminderSetResponse(data: Map<String, Any>): String {
        val time = data["time"] as? String ?: "requested time"
        val responses = listOf(
            "Reminder set for $time.",
            "I have scheduled that reminder for $time.",
            "It will be done. Reminder at $time.",
            "Logged. Reminder at $time."
        )
        return personalize(responses.random())
    }
    
    // Task responses
    private fun taskAddedResponse(data: Map<String, Any>): String {
        val task = data["task"] as? String ?: "task"
        val responses = listOf(
            "Task '$task' added to the queue.",
            "I have updated your to-do list with '$task'.",
            "Task logged: '$task'.",
            "Added to list."
        )
        return personalize(responses.random())
    }
    
    // Greeting responses
    private fun morningGreeting(): String {
        val responses = listOf(
            "Good morning. Systems online.",
            "A fine morning to you.",
            "Good morning. Ready to begin?",
            "Systems initialized. Good morning."
        )
        return personalize(responses.random())
    }
    
    private fun afternoonGreeting(): String {
        val responses = listOf(
            "Good afternoon.",
            "Good afternoon. Systems functioning normally.",
            "Afternoon.",
            "Awaiting your instructions."
        )
        return personalize(responses.random())
    }
    
    private fun eveningGreeting(): String {
        val responses = listOf(
            "Good evening.",
            "Evening mode active.",
            "Good evening. How may I assist?",
            "A pleasant evening to you."
        )
        return personalize(responses.random())
    }
    
    // Error responses
    private fun errorResponse(): String {
        val responses = listOf(
            "I am unable to comply.",
            "An error occurred in processing.",
            "I encountered a problem.",
            "Systems failing to execute."
        )
        return personalize(responses.random())
    }
    
    // Acknowledgment responses
    private fun acknowledgmentResponse(): String {
        val responses = listOf(
            "Very well.",
            "On it.",
            "Processing.",
            "As you wish.",
            "Confirmed.",
            "Executing."
        )
        return personalize(responses.random())
    }
    
    // Generic personalization
    private fun personalizeGeneric(message: String): String {
        return personalize(message)
    }

    /**
     * Get a random filler phrase to reduce perceived latency
     * "Thinking..." sounds
     */
    fun getFillerPhrase(): String {
        val fillers = listOf(
            "One moment, Sir.",
            "Processing...",
            "Checking on that.",
            "Let me see.",
            "Accessing databanks.",
            "Right away, Sir.",
            "Just a second."
        )
        return fillers.random()
    }

    /**
     * Get a random filler phrase for location requests
     * Masks GPS latency
     */
    fun getLocationFillerPhrase(): String {
        val fillers = listOf(
            "Accessing satellite positioning...",
            "Triangulating your coordinates, Sir...",
            "Connecting to global positioning system...",
            "Pinpointing your location now...",
            "Calibrating location sensors...",
            "Acquiring GPS signal..."
        )
        return fillers.random()
    }
}
