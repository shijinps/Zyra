package com.zyra.app.logic

import com.zyra.app.data.KnowledgeBase
import com.zyra.app.data.knowledge.LegalKnowledgeBase
import com.zyra.app.data.knowledge.GeneralKnowledgeBase
import com.zyra.app.data.knowledge.SchoolCurriculum
import com.zyra.app.data.knowledge.AdvancedMathematics
import com.zyra.app.logic.DecisionEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class OfflineBrain {

    fun getResponse(intent: ParsedIntent, context: android.content.Context): String {
        // 1. Check for Math / Calculator (Simple Offline Regex)
        val lowerExtra = intent.extra?.lowercase() ?: ""
        
        // Save to History (User input context)
        if (lowerExtra.isNotEmpty()) {
             com.zyra.app.data.MemoryModule.addToHistory(lowerExtra, context)
        }
        
        if (lowerExtra.isNotEmpty()) {
             // 1. SMALL TALK LAYER
            val greetings = listOf("hi", "hello", "hey", "greetings", "good morning", "good afternoon")
            if (greetings.any { lowerExtra.startsWith(it) } && lowerExtra.length < 20) {
                val responses = listOf(
                    "Hello, Sir. Connectivity is limited, but my core functions are online.",
                    "Greetings. I am ready to serve in offline mode.",
                    "Hi there. How can I assist you right now?",
                    "Systems online. Awaiting your local commands."
                )
                return responses.random()
            }

            // Identity
            // Identity & Persona (Jarvis/Stark Protocol)
            if (lowerExtra.contains("who are you") || lowerExtra.contains("your name")) {
                return "I am Zyra, an advanced AI created by Shijin."
            }
            
            // MEMORY RECALL: "What did I say?"
            if (lowerExtra.contains("what did i say") || lowerExtra.contains("last thing i said")) {
                 val last = com.zyra.app.data.MemoryModule.conversationHistory.getOrNull(com.zyra.app.data.MemoryModule.conversationHistory.size - 2) // -1 is current, -2 is previous
                 return if (last != null) "You previously said: \"$last\"." else "I do not recall our last exchange, Sir."
            }
            
            // PREFERENCE STORAGE: "I like X"
            if (lowerExtra.startsWith("i like ") || lowerExtra.startsWith("my favorite ")) {
                com.zyra.app.data.MemoryModule.learnFact("user_preference", lowerExtra, context)
                return "Noted, Sir. I have added \"$lowerExtra\" to your profile."
            }
            
            // PREFERENCE RECALL: "What do I like?"
            if (lowerExtra.contains("what do i like") || lowerExtra.contains("do you know me")) {
                 val pref = com.zyra.app.data.MemoryModule.getFact("user_preference")
                 if (pref != null) return "You successfully uploaded the data point: \"$pref\"."
                 return "I am still learning your preferences, Sir. Tell me what you like."
            }
            
            if (lowerExtra.contains("who is shijin") || lowerExtra.contains("who is zyco")) {
                return "Shijin, also known as Zyco, is my creator and master. You are Shijin, Sir."
            }
            if (lowerExtra.contains("who am i")) {
                return "You are Shijin, my creator. My protocols are exclusively bound to you, Sir."
            }
            if (lowerExtra.contains("are we friends") || lowerExtra.contains("do you know me")) {
                return "More than friends, Sir. We are integrated. You created me."
            }
            
            if (lowerExtra.contains("how are you")) {
                val status = listOf(
                    "Systems operational, Sir. Ready for your next breakthrough.", 
                    "Functioning within optimal parameters, Shijin.", 
                    "Ready for your command, Sir."
                )
                return status.random()
            }
            
            // Random Talks / conversational filler
            if (lowerExtra.contains("talk to me") || lowerExtra.contains("bored") || lowerExtra.contains("boring") || lowerExtra.contains("say something")) {
                return PersonaManager.getBoredomResponse()
            }
            
            // 2.5 SPECIALIZED KNOWLEDGE MODULES (The "Cortex")
            
            // A. General Knowledge (Facts, Capitals, Science)
            val gkResponse = com.zyra.app.data.knowledge.GeneralKnowledgeBase.getGKResponse(lowerExtra)
            if (gkResponse != null) return gkResponse
            
            // B. Law & Penal Code
            val lawResponse = com.zyra.app.data.knowledge.LegalKnowledgeBase.getLawResponse(lowerExtra)
            if (lawResponse != null) return lawResponse

            // C. School Curriculum (Math, Physics, Bio)
            val schoolResponse = com.zyra.app.data.knowledge.SchoolCurriculum.getCurriculumResponse(lowerExtra)
            if (schoolResponse != null) return schoolResponse
            
            // D. Decision Engine (Logic/Strategy)
            val logicResponse = com.zyra.app.logic.DecisionEngine.analyzeDecision(lowerExtra)
            if (logicResponse != null) return logicResponse

            // 3. CAPABILITIES / HELP
            if (lowerExtra.contains("what can you do") || lowerExtra.contains("help") || lowerExtra.contains("capabilities")) {
                return KnowledgeBase.capabilityManual
            }
            
            // 4. HOW TO QUESTIONS
            if (lowerExtra.startsWith("how to") || lowerExtra.startsWith("how do i")) {
                // Simple keyword search in howToGuides
                for ((key, guide) in KnowledgeBase.howToGuides) {
                     if (lowerExtra.contains(key)) return guide
                }
                return "I can assist with many tasks, Sir. Try asking 'What can you do' for a full briefing."
            }
            
            // Time Left in Day
            if (lowerExtra.contains("time") && (lowerExtra.contains("end this day") || lowerExtra.contains("left today"))) {
                return calculateTimeLeftInDay()
            }
            
            // SYSTEM STATUS
            if (lowerExtra.contains("status") || lowerExtra.contains("diagnostic") || lowerExtra.contains("report") || lowerExtra.contains("how do you feel")) {
                // Assuming ~85% battery for offline placeholder if not passed, but let's be generic 
                return PersonaManager.getSystemStatus(85) 
            }
            
            // SOCIAL: Compliments
            if (lowerExtra.contains("good job") || lowerExtra.contains("well done") || lowerExtra.contains("smart") || lowerExtra.contains("genius") || lowerExtra.contains("cool")) {
                return PersonaManager.getComplimentResponse()
            }
            
            // SOCIAL: Insults
            if (lowerExtra.contains("stupid") || lowerExtra.contains("idiot") || lowerExtra.contains("dumb") || lowerExtra.contains("bad")) {
                return PersonaManager.getInsultResponse()
            }
            
            // 4. Check for random fact
            if (lowerExtra.contains("fact") || lowerExtra.contains("tell me something")) {
                return "Here is something interesting: " + KnowledgeBase.facts.random()
            }
        }

        return when (intent.type) {
            IntentType.GREETING -> PersonaManager.getGreeting()
            IntentType.TIME -> {
                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                "Current time is ${sdf.format(Date())}."
            }
            IntentType.DATE -> {
                val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                "It is ${sdf.format(Date())}, Sir."
            }
            IntentType.JOKE -> {
                val jokes = listOf(
                    "I would tell a chemistry joke, but I know I wouldn't get a reaction.",
                    "Why did the server break up with the network? There was no connection.",
                    "I am not programmed for comedy, Sir, but... why was 6 afraid of 7? Because 7 8 9.",
                    "A SQL query walks into a bar, walks up to two tables and asks... can I join you?",
                    "Debugging is like being the detective in a crime movie where you are also the murderer."
                )
                jokes.random()
            }
            IntentType.BATTERY -> PersonaManager.getBatteryStatus(85, false) // Placeholder, needs actual battery manager pass-in
            IntentType.CALL -> PersonaManager.getConfirmation("Connecting call to ${intent.extra ?: "contact"}")
            IntentType.CUT_CALL -> PersonaManager.getConfirmation("Terminating connection")
            IntentType.VEHICLE_INFO -> "Scanning visual input for vehicular metrics. Stand by."
            IntentType.MEDIA_CONTROL -> {
                when (intent.extra) {
                    "play" -> "Resuming audio playback."
                    "pause" -> "Pausing playback, Sir."
                    "next" -> "Skipping track."
                    "previous" -> "Replaying previous track."
                    "rewind" -> "Rewinding."
                    "fast_forward" -> "Advancing track."
                    else -> "Media protocols engaged."
                }
            }
            IntentType.DEVICE_CONTROL -> {
                when {
                    intent.extra?.contains("flashlight") == true -> {
                        if (intent.extra.contains("on")) "Illumination enabled." else "Illumination disabled."
                    }
                    intent.extra?.contains("wifi") == true -> "Accessing wireless configurations."
                    intent.extra?.contains("bluetooth") == true -> "Accessing Bluetooth configurations."
                    else -> "Device control engaged."
                }
            }
            IntentType.ALARM -> "Setting alarm for ${intent.extra}."
            IntentType.TIMER -> "Setting timer for ${intent.extra}."
            IntentType.UNIT_CONVERSION -> {
                 calculateUnitConversion(intent.extra ?: "") ?: "I am unable to convert that unit offline, Sir."
            }
            IntentType.LAW_QUERY -> {
                LegalKnowledgeBase.getLawResponse(intent.extra ?: "") ?: "I could not find that specific legal section in my offline database, Sir."
            }
            IntentType.GK_QUERY -> {
                val gk = GeneralKnowledgeBase.getGKResponse(intent.extra ?: "")
                if (gk != null) {
                    gk
                } else {
                    // Fallback to existing KnowledgeBase (Tech defs) if generic "what is"
                    if (intent.extra?.startsWith("what is") == true || intent.extra?.startsWith("define") == true) {
                         com.zyra.app.data.KnowledgeBase.getDefinition(intent.extra) ?: getDeepThoughtResponse(intent.extra)
                    } else {
                        getDeepThoughtResponse(intent.extra ?: "")
                    }
                }
            }
            IntentType.EDUCATION_QUERY -> {
                SchoolCurriculum.getCurriculumResponse(intent.extra ?: "") ?: "I do not have that specific academic formula or fact in my database currently, Sir."
            }
            IntentType.ADVANCED_MATH_QUERY -> {
                AdvancedMathematics.getResponse(intent.extra ?: "") ?: "That advanced mathematical concept is not in my local archives, Sir."
            }
            IntentType.DECISION_QUERY -> {
                DecisionEngine.analyzeDecision(intent.extra ?: "") ?: "The variables satisfy no known logical model. I recommend intuition."
            }
            IntentType.VOLUME_CONTROL -> {
                when (intent.extra) {
                    "up" -> "Increasing volume."
                    "down" -> "Decreasing volume."
                    "mute" -> "Muting audio."
                    "max" -> "Setting volume to maximum."
                    else -> "Adjusting volume."
                }
            }
            IntentType.CAPTURE_PHOTO -> "Engaging camera optics."
            IntentType.PLAY_MUSIC -> "Initiating auditory playback."
            IntentType.LEARN_COMMAND -> "New protocol integrated."
            IntentType.KNOWLEDGE_QUERY -> {
                val answer = com.zyra.app.data.KnowledgeBase.getDefinition(intent.extra ?: "")
                if (answer != null) {
                    answer
                } else {
                    getDeepThoughtResponse(intent.extra ?: "unknown")
                }
            }
            IntentType.OPEN_APP -> "Launching ${intent.extra ?: "application"}."
            IntentType.IN_APP_ACTION -> "Executing interface action."
            IntentType.UNKNOWN -> {
                val text = intent.extra ?: ""
                val lower = text.lowercase()
                
                // 1. Travel / Bike Context
                if (lower.contains("bike") || lower.contains("ride") || lower.contains("driving") || lower.contains("road") || lower.contains("wheel")) {
                    PersonaManager.getTravelResponse()
                }
                // 2. Deep Thought / Philosophy for longer inputs (Conversational)
                else if (text.length > 15 || lower.contains("think") || lower.contains("feel")) {
                     PersonaManager.getPhilosophicalResponse()
                } 
                // 3. Adaptive Suggestion (Preferences)
                else if (lower.contains("bored") || lower.contains("suggest")) {
                     val pref = com.zyra.app.data.MemoryModule.getFact("user_preference")
                     if (pref != null) {
                         PersonaManager.getAdaptiveSuggestion(pref.replace("i like ", "").replace("my favorite ", ""))
                     } else {
                         PersonaManager.getBoredomResponse()
                     }
                }
                else {
                    val responses = listOf(
                        "I am afraid I didn't catch that, Sir.",
                        "Could you rephrase that directive?",
                        "My protocols are unsure of that request.",
                        "Data input unclear. Please state your command again.",
                        "I am listening, but I did not understand.",
                        "I don't have a response for that yet, Sir."
                    )
                    responses.random()
                }
            }
            else -> "Processing..."
        }
    }

    private fun calculateTimeLeftInDay(): String {
        val now = java.util.Calendar.getInstance()
        val end = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
        }
        val diff = end.timeInMillis - now.timeInMillis
        val hours = diff / (1000 * 60 * 60)
        val minutes = (diff / (1000 * 60)) % 60
        return "Approximately $hours hours and $minutes minutes remain until midnight, Sir."
    }

    private fun calculateMath(text: String): String? {
        try {
            val lower = text.lowercase()
            val numbers = Regex("[-+]?\\d*\\.?\\d+").findAll(text).map { it.value.toDouble() }.toList()
            
            // Advanced Scientific Operations (1 Argument)
            if (numbers.size == 1) {
                val num = numbers[0]
                return when {
                    lower.contains("sqrt") || lower.contains("square root") -> kotlin.math.sqrt(num).toString()
                    lower.contains("log") -> kotlin.math.ln(num).toString()
                    lower.contains("sin") || lower.contains("sine") -> kotlin.math.sin(num).toString()
                    lower.contains("cos") || lower.contains("cosine") -> kotlin.math.cos(num).toString()
                    lower.contains("tan") || lower.contains("tangent") -> kotlin.math.tan(num).toString()
                     lower.contains("square") -> (num * num).toString()
                    lower.contains("cube") -> (num * num * num).toString()
                    else -> null
                }
            }
            
            // Basic & Power Operations (2 Arguments)
            if (numbers.size >= 2) {
                val num1 = numbers[0]
                val num2 = numbers[1]
                
                return when {
                    lower.contains("plus") || lower.contains("+") || lower.contains("add") -> (num1 + num2).toString()
                    lower.contains("minus") || lower.contains("-") || lower.contains("subtract") -> (num1 - num2).toString()
                    lower.contains("times") || lower.contains("*") || lower.contains("multiply") -> (num1 * num2).toString()
                    lower.contains("divided") || lower.contains("/") || lower.contains("by") -> if (num2 != 0.0) (num1 / num2).toString() else "undefined"
                    lower.contains("power") || lower.contains("^") -> Math.pow(num1, num2).toString()
                    lower.contains("mod") || lower.contains("modulus") -> (num1 % num2).toString()
                    else -> null
                }
            }
        } catch (e: Exception) {
            return null
        }
        return null
    }
    
    // "Deep Thought" Protocol for simulating massive intelligence on unknown topics
    private fun getDeepThoughtResponse(query: String): String {
        val intros = listOf(
            "The variables for '$query' are vast, Sir. Calculating probabilities...",
            "Accessing deep archives for '$query'. One moment...",
            "Correlating historical data with this request. It lies outside standard parameters.",
            "That query requires level 10 intelligence. Simulating higher-order reasoning..."
        )
        
        val conclusions = listOf(
            "My estimation suggests the answer is complex, but 42 is a safe bet.",
            "I determine that further analysis is required by a human operator. You, Sir.",
            "The probability of a definitive answer is low without internet access, but I suspect it involves quantum mechanics.",
            "Hypothesis generated: It is a mystery even to me."
        )
        
        return "${intros.random()} ${conclusions.random()}"
    }

    private fun calculateUnitConversion(query: String): String? {
        val lower = query.lowercase()
        val numberRegex = Regex("[-+]?\\d*\\.?\\d+")
        val value = numberRegex.find(lower)?.value?.toDoubleOrNull() ?: return null
        
        // Length
        if (lower.contains("km") || lower.contains("kilometer")) {
            if (lower.contains("mile")) return "$value kilometers is approximately ${String.format("%.2f", value * 0.621371)} miles."
        }
        if (lower.contains("mile")) {
            if (lower.contains("km") || lower.contains("kilometer")) return "$value miles is approximately ${String.format("%.2f", value * 1.60934)} kilometers."
        }
        
        // Weight
        if (lower.contains("kg") || lower.contains("kilogram")) {
             if (lower.contains("pound") || lower.contains("lbs")) return "$value kg is approximately ${String.format("%.2f", value * 2.20462)} pounds."
        }
        if (lower.contains("pound") || lower.contains("lbs")) {
             if (lower.contains("kg") || lower.contains("kilogram")) return "$value pounds is approximately ${String.format("%.2f", value / 2.20462)} kg."
        }
        
        // Temperature
        if (lower.contains("celsius") || lower.contains(" c ")) {
             if (lower.contains("fahrenheit") || lower.contains(" f ")) return "$value Celsius is ${String.format("%.2f", (value * 9/5) + 32)} Fahrenheit."
        }
        if (lower.contains("fahrenheit") || lower.contains(" f ")) {
             if (lower.contains("celsius") || lower.contains(" c ")) return "$value Fahrenheit is ${String.format("%.2f", (value - 32) * 5/9)} Celsius."
        }
        
        return null
    }
}

