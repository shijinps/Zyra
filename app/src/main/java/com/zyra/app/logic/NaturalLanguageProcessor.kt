package com.zyra.app.logic


import com.zyra.app.logic.ml.IntentClassifier

enum class IntentType {
    CALL,
    CUT_CALL,
    TIME,
    DATE,
    BATTERY,
    GREETING,
    JOKE,
    MEDIA_CONTROL,
    KNOWLEDGE_QUERY,
    VEHICLE_INFO,
    OPEN_APP,
    DEVICE_CONTROL, // Flashlight, Wifi, BT

    VOLUME_CONTROL, // New: Volume Up/Down
    IN_APP_ACTION, // New: Click, Scroll, Type, Global
    CAPTURE_PHOTO, // New: Open camera and click
    PLAY_MUSIC, // New: Play specific app music
    LEARN_COMMAND, // New: Custom aliases
    CLOSE_APP, // New: Go Home / Back
    END_SESSION, // New: "Thank you", "Close Zyra"
    SEND_MESSAGE, // New: Send WhatsApp/SMS
    UNLOCK_DEVICE, // New: Unlock phone
    LOCK_DEVICE, // New: Lock phone
    SET_PIN, // New: User providing lock screen PIN
    REPEAT, // New: "Do it again"
    ANSWER_CALL, // New: Answer incoming call
    ALARM, // New: Set alarm
    TIMER, // New: Set timer
    UNIT_CONVERSION, // New: "10 km to miles"
    LAW_QUERY, // New: Indian Laws
    GK_QUERY, // New: General Knowledge
    EDUCATION_QUERY, // New: Math/Science Curriculum
    ADVANCED_MATH_QUERY, // New: B.Tech/PhD Math
    DECISION_QUERY, // New: Logic/Game Theory
    UNKNOWN
}

data class ParsedIntent(
    val type: IntentType,
    val extra: String? = null
)


class NaturalLanguageProcessor {

    private val classifier = IntentClassifier()
    fun train(text: String, type: IntentType) {
        classifier.trainDynamic(text, type)
    }
    companion object {
        var isOnlineMode = false
    }

    fun process(text: String): ParsedIntent {
        // 1. MEMORY OVERRIDE (The Nervous System)
        // Check if this command is an alias for something else
        // e.g. "Selfie Mode" -> "Open Camera and click picture"
        val resolvedText = com.zyra.app.data.MemoryModule.checkForAlias(text)
        val lower = resolvedText.lowercase()

        // --- ONLINE MODE TOGGLE ---
        if (lower == "go online" || lower == "switch to online" || lower == "enable online mode") {
            isOnlineMode = true
            com.zyra.app.logic.GeminiModule.initialize() // Ensure it's ready
            return ParsedIntent(IntentType.KNOWLEDGE_QUERY, "I am now online. What can I do for you?")
        }
        if (lower == "go offline" || lower == "switch to offline" || lower == "disable online mode") {
            isOnlineMode = false
            return ParsedIntent(IntentType.KNOWLEDGE_QUERY, "I am now offline. Returning to local processing.")
        }
        
        // --- ONLINE MODE ROUTING ---
        if (isOnlineMode) {
            // Check for critical local commands first (like Stop, Close, Device Control overrides)
            if (lower == "stop" || lower == "stop listening" || lower == "shut up" || lower == "close zyra") {
                return ParsedIntent(IntentType.END_SESSION)
            }
            if (lower.contains("flashlight")) {
                 // Allow local flashlight control even in online mode
                 if (lower.contains("on")) return ParsedIntent(IntentType.DEVICE_CONTROL, "flashlight:on")
                 if (lower.contains("off")) return ParsedIntent(IntentType.DEVICE_CONTROL, "flashlight:off")
            }

            // Allow Unlock Phone in Online Mode
            if (lower.contains("unlock phone") || lower.contains("unlock screen") || lower.contains("unlock device") || lower.contains("open phone")) {
                return ParsedIntent(IntentType.UNLOCK_DEVICE)
            }
            
            // Otherwise, route EVERYTHING to Gemini (labeled as GK_QUERY for now, likely handled by Service)
            // We return a special 'GEMINI' type or overload KNOWLEDGE_QUERY
            return ParsedIntent(IntentType.KNOWLEDGE_QUERY, "GEMINI:$resolvedText")
        }

        // --- OFFLINE/LOCAL MODE (Existing Logic) ---
        // 0. Global Navigation (Close App / Home)
        if (lower == "go home" || lower == "home" || lower.startsWith("close") || lower.startsWith("exit")) {
             return ParsedIntent(IntentType.CLOSE_APP)
        }
        if (lower == "you may leave" || lower == "you may go" || lower == "leave" || lower == "dismissed") {
             return ParsedIntent(IntentType.END_SESSION)
        }
        if (lower == "do it again" || lower == "repeat" || lower == "once more" || lower == "again") {
             return ParsedIntent(IntentType.REPEAT)
        }
        if (lower == "go back" || lower == "back") return ParsedIntent(IntentType.IN_APP_ACTION, "global:back")
        if (lower == "recent apps" || lower == "recents") return ParsedIntent(IntentType.IN_APP_ACTION, "global:recents")

        // 0.5 Volume Control
        if (lower.contains("volume") || lower.contains("sound") || lower.contains("voice")) {
            if (lower.contains("up") || lower.contains("increase") || lower.contains("raise") || lower.contains("louder")) {
                return ParsedIntent(IntentType.VOLUME_CONTROL, "up")
            }
            if (lower.contains("down") || lower.contains("decrease") || lower.contains("lower") || lower.contains("reduce") || lower.contains("quieter")) {
                return ParsedIntent(IntentType.VOLUME_CONTROL, "down")
            }
            if (lower.contains("mute") || lower.contains("silent")) {
                return ParsedIntent(IntentType.VOLUME_CONTROL, "mute")
            }
            if (lower.contains("max") || lower.contains("full")) {
                return ParsedIntent(IntentType.VOLUME_CONTROL, "max")
            }
        }
        
        // 0.6 Session Management (Thank You / Close Zyra)
        if (lower == "thank you" || lower == "thanks" || lower.startsWith("thank you") ||
            lower == "bye" || lower == "goodbye" || 
            lower == "may i leave" || lower == "close zyra" || lower == "close assistant" ||
            lower == "stop listening" || lower == "dismiss") {
            return ParsedIntent(IntentType.END_SESSION)
        }
        
        // 0.7 Explicit Close App (by name)
        // "Close WhatsApp", "Close Camera" -> handled in IntentType.CLOSE_APP with extra if needed
        // For now, "Close App" in simple form maps to Global Home.
        // If user says "Close [AppName]", we might want to kill it, but Android restricts that.
        // We will stick to Global Home for "Close *" commands unless it's "Close Zyra".

        // 0.8 Messaging (WhatsApp)
        // "Send message to [Name] saying [Message]"
        // "WhatsApp [Name] saying [Message]"
        // "Tell [Name] that [Message]"
        if (lower.startsWith("send message to ") || lower.startsWith("message ") || lower.startsWith("whatsapp ") || lower.startsWith("tell ")) {
             var temp = lower.replace("send message to ", "")
                             .replace("send a message to ", "")
             
             // Check for "whatsapp" prefix
             if (temp.startsWith("whatsapp ")) {
                 temp = temp.replace("whatsapp ", "")
             } else if (temp.startsWith("message ")) {
                 temp = temp.replace("message ", "")
             } else if (temp.startsWith("tell ")) {
                 temp = temp.replace("tell ", "")
             }
             
             // Separators: "saying", "that", "body"
             val separators = listOf(" saying ", " that ", " body ", ":")
             for (sep in separators) {
                 if (temp.contains(sep)) {
                     val parts = temp.split(sep, limit = 2)
                     val name = parts[0].trim()
                     val message = parts[1].trim()
                     if (name.isNotEmpty() && message.isNotEmpty()) {
                         return ParsedIntent(IntentType.SEND_MESSAGE, "$name|$message")
                     }
                 }
             }
             return ParsedIntent(IntentType.SEND_MESSAGE, lower)
        }
        
        // 0.9 Unlock Phone
        if (lower.contains("unlock phone") || lower.contains("unlock screen") || lower.contains("unlock device") || lower.contains("open phone")) {
             return ParsedIntent(IntentType.UNLOCK_DEVICE)
        }

        // 0.9 Lock Phone
        if (lower.contains("lock phone") || lower.contains("lock screen") || lower.contains("screen off") || lower.contains("turn off screen") || lower.contains("go to sleep")) {
             return ParsedIntent(IntentType.LOCK_DEVICE)
        }
        
        // 0.95 Set Password/PIN
        if (lower.contains("password is") || lower.contains("pin is")) {
            val pin = lower.replace("my ", "")
                           .replace("phones ", "")
                           .replace("phone ", "")
                           .replace("password is ", "")
                           .replace("pin is ", "")
                           .trim()
                           .replace(" ", "") // Remove spaces from "0 2 5 5 2"
            
            // Basic validation: just digits
            if (pin.all { it.isDigit() }) {
                return ParsedIntent(IntentType.SET_PIN, pin)
            }
        }

        // OFFLINE FEATURES (GitHub Import)
        
        // Flashlight
        if (lower.contains("flashlight") || lower.contains("torch")) {
            if (lower.contains("on") || lower.contains("enable")) {
                return ParsedIntent(IntentType.DEVICE_CONTROL, "flashlight:on")
            }
            if (lower.contains("off") || lower.contains("disable")) {
                return ParsedIntent(IntentType.DEVICE_CONTROL, "flashlight:off")
            }
        }
        
        // Alarm: "Set alarm for 6:30 am"
        if (lower.startsWith("set alarm") || lower.startsWith("wake me up")) {
             return ParsedIntent(IntentType.ALARM, lower.replace("set alarm", "").replace("wake me up", "").trim())
        }
        
        // Timer: "Set timer for 5 minutes"
        if (lower.startsWith("set timer") || lower.startsWith("timer")) {
             return ParsedIntent(IntentType.TIMER, lower.replace("set timer", "").replace("timer", "").trim())
        }
        
        // Unit Conversion: "Convert 10 km to miles"
        if (lower.startsWith("convert")) {
             return ParsedIntent(IntentType.UNIT_CONVERSION, lower.replace("convert", "").trim())
        }

        // KNOWLEDGE EXPANSION (Law & GK)
        
        // Law: "What is Section 66", "Punishment for hacking"
        if (lower.contains("section") || lower.contains("act") || lower.contains("punishment for") || lower.contains("crime") || lower.contains("law")) {
            // Filter out generic phrases if needed, but for now catch broad law terms
            if (lower.contains("section") || lower.contains("ipc") || lower.contains("it act") || lower.contains("hacking")) {
               return ParsedIntent(IntentType.LAW_QUERY, lower)
            }
        }
        
        // GK: "Capital of", "Who is", "Tell me about"
        if (lower.contains("capital of") || lower.contains("atomic number") || lower.contains("newton") || lower.contains("law of motion")) {
            return ParsedIntent(IntentType.GK_QUERY, lower)
        }
        if (lower.startsWith("who is ") || lower.startsWith("what is ")) {
             // Catch-all KNOWLEDGE_QUERY or specialized GK
             // We'll let GK Base try first, if fail, fallback to KnowledgeBase
             return ParsedIntent(IntentType.GK_QUERY, lower)
        }
        if (lower.contains("districts in") || lower.contains("prime minister")) {
             return ParsedIntent(IntentType.GK_QUERY, lower)
        }

        // Education: "Formula for...", "Theorem", "Chemical name"
        if (lower.contains("formula") || lower.contains("theorem") || lower.contains("scientific name") || lower.contains("chemical name") || lower.contains("symbol for")) {
             return ParsedIntent(IntentType.EDUCATION_QUERY, lower)
        }
        if (lower.contains("geometry") || lower.contains("algebra") || lower.contains("physics") || lower.contains("chemistry") || lower.contains("biology")) {
             return ParsedIntent(IntentType.EDUCATION_QUERY, lower)
        }

        // Advanced Math: "Integral", "Derivative", "Eigen", "Laplace", "Fourier", "Riemann"
        if (lower.contains("integral") || lower.contains("derivative") || lower.contains("eigen") || lower.contains("laplace") || lower.contains("fourier") || lower.contains("riemann") || lower.contains("topology") || lower.contains("manifold")) {
             return ParsedIntent(IntentType.ADVANCED_MATH_QUERY, lower)
        }
        if (lower.contains("group theory") || lower.contains("ring theory") || lower.contains("fermat")) {
             return ParsedIntent(IntentType.ADVANCED_MATH_QUERY, lower)
        }
        
        // Decision/Logic: "Should I", "Analyze", "Probability", "Dilemma"
        if (lower.startsWith("should i") || lower.contains("decide for me") || lower.contains("analyze this") || lower.contains("risk") || lower.contains("game theory") || lower.contains("dilemma")) {
             return ParsedIntent(IntentType.DECISION_QUERY, lower)
        }

        // 1. In-App Scrolling
        if (lower.contains("scroll down")) return ParsedIntent(IntentType.IN_APP_ACTION, "scroll:down")
        if (lower.contains("scroll up")) return ParsedIntent(IntentType.IN_APP_ACTION, "scroll:up")
        
        // ... (Existing Checks) ...
        
        // Check Memory for personalized facts
        // "What is my wifi password", "Tell me my name"
        if (lower.contains("my ") && (lower.contains("what is") || lower.contains("tell me") || lower.contains("remember"))) {
             val keyCandidates = lower.replace("what is", "")
                                      .replace("tell me", "")
                                      .replace("remember", "")
                                      .replace("my ", "")
                                      .trim()
             
             // Try exact key match first
             var fact = com.zyra.app.data.MemoryModule.getFact(keyCandidates)
             
             // Try partial match if no exact key
             if (fact == null) {
                 val allKeys = com.zyra.app.data.MemoryModule.facts.keys
                 val bestKey = allKeys.find { keyCandidates.contains(it) || it.contains(keyCandidates) }
                 if (bestKey != null) {
                     fact = com.zyra.app.data.MemoryModule.getFact(bestKey)
                 }
             }
             
             if (fact != null) {
                 return ParsedIntent(IntentType.KNOWLEDGE_QUERY, "Your $keyCandidates is $fact.")
             }
        }

        // Fact Learning: "Remember that wifi is 1234"
        if (lower.startsWith("remember that ") || lower.startsWith("save that ")) {
            return ParsedIntent(IntentType.LEARN_COMMAND, "fact:$lower")
        }
        
        // Command Learning: "Learn command selfie mode as open camera"
        if (lower.startsWith("learn command") || lower.startsWith("learn that")) {
             return ParsedIntent(IntentType.LEARN_COMMAND, "alias:$lower")
        }
        
        // 2. In-App Clicking
        if (lower.startsWith("click ") || lower.startsWith("tap ") || lower.startsWith("press ")) {
            val target = lower.replace("click ", "")
                              .replace("tap ", "")
                              .replace("press ", "").trim()
            if (target.isNotEmpty()) {
                return ParsedIntent(IntentType.IN_APP_ACTION, "click:$target")
            }
        }
        
        // 3. In-App Typing
        if (lower.startsWith("type ") || lower.startsWith("enter ") || lower.startsWith("write ")) {
             val content = lower.replace("type ", "")
                                .replace("enter ", "")
                                .replace("write ", "").trim()
             if (content.isNotEmpty()) {
                 return ParsedIntent(IntentType.IN_APP_ACTION, "type:$content")
             }
        }
        
        // 4. Vehicle Info
        if (lower.contains("vehicle") || (lower.contains("check") && lower.contains("number"))) {
             return ParsedIntent(IntentType.VEHICLE_INFO)
        }
        
        // 5. Media Controls
        // CRITICAL FIX: Check for "play next" / "play previous" BEFORE "play" to avoid swallowing the command
        if (lower.contains("play next") || lower.contains("play skip")) return ParsedIntent(IntentType.MEDIA_CONTROL, "next")
        if (lower.contains("play previous") || lower.contains("play last")) return ParsedIntent(IntentType.MEDIA_CONTROL, "previous")
        if (lower.contains("resume music") || lower.contains("continue music")) return ParsedIntent(IntentType.MEDIA_CONTROL, "play")

        // 7.6. Specific Music Apps (Spotify) - MOVED UP PRIORITY
        if (lower.contains("spotify") && (lower.contains("play") || lower.contains("song"))) {
            // Extract Query: "Play [Believer] on Spotify" or "Play song [Believer] on Spotify"
            val query = lower.replace("play", "")
                             .replace("song", "")
                             .replace("on spotify", "")
                             .replace("from spotify", "")
                             .replace("spotify", "") // cleanup
                             .replace("music", "")   // cleanup "play music believer"
                             .trim()
            
            // If query is empty, just open spotify (which handled by "spotify" extra)
            // If query is present, pass "spotify|$query" so Service knows both
            return if (query.isNotEmpty()) {
                ParsedIntent(IntentType.PLAY_MUSIC, "spotify|$query")
            } else {
                ParsedIntent(IntentType.PLAY_MUSIC, "spotify")
            }
        }

        // Be careful with "start" here, check for "start music" specifically first
        if (lower.contains("play") && !lower.contains("music") && !lower.contains("song") && !lower.contains("spotify")) {
             // "Play" on its own usually means resume (handled in Service fallback if we pass null anyway?)
             // But existing logic says MEDIA_CONTROL which simply sends KEYCODE_MEDIA_PLAY.
             // If user says "Play Believer", this block might catch it inappropriately if we are strict.
             // BUT "Believer" doesn't contain "music" or "song".
             // We should check if it looks like a song request: "Play [Something]"
             
             val potentialSong = lower.replace("play", "").trim()
             if (potentialSong.isNotEmpty() && !potentialSong.contains("next") && !potentialSong.contains("previous")) {
                 // Treat as generic music play request: "Play Believer"
                 return ParsedIntent(IntentType.PLAY_MUSIC, potentialSong)
             }
             return ParsedIntent(IntentType.MEDIA_CONTROL, "play")
        }
        
        if (lower.contains("start music") || (lower.contains("play") && (lower.contains("music") || lower.contains("song")))) {
             // "Play music" (generic) or "Play song [X]"
             val query = lower.replace("start music", "")
                              .replace("play music", "")
                              .replace("play song", "")
                              .replace("play", "") // fallback
                              .trim()
             
             return if (query.isNotEmpty()) {
                 ParsedIntent(IntentType.PLAY_MUSIC, query)
             } else {
                 ParsedIntent(IntentType.PLAY_MUSIC)
             }
        }
        
        if (lower.contains("pause") || lower.contains("stop music")) return ParsedIntent(IntentType.MEDIA_CONTROL, "pause")
        if (lower.contains("next") || lower.contains("skip")) return ParsedIntent(IntentType.MEDIA_CONTROL, "next")
        if (lower.contains("previous song") || lower.contains("last song")) return ParsedIntent(IntentType.MEDIA_CONTROL, "previous")
        if (lower.contains("previous") && lower.contains("seconds")) return ParsedIntent(IntentType.MEDIA_CONTROL, "rewind") // "Previous 10 seconds"
        if (lower.contains("rewind") || lower.contains("backward") || lower.contains("back")) return ParsedIntent(IntentType.MEDIA_CONTROL, "rewind")
        if (lower.contains("forward") || lower.contains("fast forward") || lower.contains("seconds")) return ParsedIntent(IntentType.MEDIA_CONTROL, "fast_forward")
        
        // 6. Call Controls
        if (lower.contains("cut call") || lower.contains("hang up") || lower.contains("end call")) {
            return ParsedIntent(IntentType.CUT_CALL)
        }
        
        // 7. Device Controls
        if (lower.contains("flashlight") || lower.contains("torch")) {
            if (lower.contains("on") || lower.contains("enable") || lower.contains("start")) return ParsedIntent(IntentType.DEVICE_CONTROL, "flashlight:on")
            if (lower.contains("off") || lower.contains("disable") || lower.contains("stop")) return ParsedIntent(IntentType.DEVICE_CONTROL, "flashlight:off")
        }
        if (lower.contains("wifi") || lower.contains("internet")) {
             if (lower.contains("on") || lower.contains("enable")) return ParsedIntent(IntentType.DEVICE_CONTROL, "wifi:on")
             if (lower.contains("off") || lower.contains("disable")) return ParsedIntent(IntentType.DEVICE_CONTROL, "wifi:off")
             return ParsedIntent(IntentType.DEVICE_CONTROL, "wifi:settings")
        }
        if (lower.contains("bluetooth")) {
             if (lower.contains("on") || lower.contains("enable")) return ParsedIntent(IntentType.DEVICE_CONTROL, "bluetooth:on")
             if (lower.contains("off") || lower.contains("disable")) return ParsedIntent(IntentType.DEVICE_CONTROL, "bluetooth:off")
             return ParsedIntent(IntentType.DEVICE_CONTROL, "bluetooth:settings")
        }
        
        // 7.5. Camera & Photos
        if (lower.contains("click picture") || lower.contains("take photo") || 
            (lower.contains("open camera") && lower.contains("click"))) {
            return ParsedIntent(IntentType.CAPTURE_PHOTO)
        }
        
        // 7.7. Learning (Handled at start, but backup here)
        // ...

        // 5. Calls & Contacts
        if (lower.contains("call") || lower.contains("dial") || lower.contains("phone")) {
             // Check for CUT call
             if (lower.contains("cut") || lower.contains("end") || lower.contains("hang up") || lower.contains("disconnect") || lower.contains("decline") || lower.contains("dont answer")) {
                 return ParsedIntent(IntentType.CUT_CALL)
             }
             
             // Check for ANSWER call
             if (lower.contains("answer") || lower.contains("pick up") || lower.contains("attend") || lower.contains("accept")) {
                 val mode = if (lower.contains("speaker") || lower.contains("loud")) "speaker" else null
                 return ParsedIntent(IntentType.ANSWER_CALL, mode)
             }
        
             val name = text.replace("call ", "", true)
                            .replace("dial ", "", true)
                            .replace("phone ", "", true)
                            .trim()
             return ParsedIntent(IntentType.CALL, name)
        }
        
        // Standalone Answer/Decline commands
        if (lower.contains("answer") || lower.contains("attend") || lower.contains("accept") || lower.contains("pick up")) {
            val mode = if (lower.contains("speaker") || lower.contains("loud")) "speaker" else null
            return ParsedIntent(IntentType.ANSWER_CALL, mode)
        }
        if (lower.contains("cut") || lower.contains("decline") || lower.contains("hang up") || lower.contains("disconnect") || lower.contains("dont answer")) {
            return ParsedIntent(IntentType.CUT_CALL)
        }

        // 8. App Launch
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ")) {
            var appName = lower.replace("open ", "")
                               .replace("launch ", "")
                               .replace("start ", "").trim()
            
            var taskAction = ""

            // Handle compound strings: "Open WhatsApp and send message" -> App: "WhatsApp"
            val connectors = listOf(" and ", " then ", " to ", " for ")
            for (connector in connectors) {
                if (appName.contains(connector)) {
                    val split = appName.split(connector, limit = 2)
                    appName = split[0].trim()
                    taskAction = split[1].trim()
                    break 
                }
            }

            if (appName.isNotEmpty() && appName != "music" && appName != "camera") { // Avoid conflict
                // Return format: "AppName|TaskAction"
                // Check for dual/cloned keywords in the AppName itself to pass to openApp
                // The openApp method will handle the filtering.
                val extra = if (taskAction.isNotEmpty()) "$appName|$taskAction" else appName
                return ParsedIntent(IntentType.OPEN_APP, extra)
            }
        }

        // 8. Knowledge/Explain
        if (lower.startsWith("explain") || lower.contains("what is")) {
            return ParsedIntent(IntentType.KNOWLEDGE_QUERY, lower)
        }

        // 9. Use Neural Network
        return when (val predictedType = classifier.predict(text)) {
            IntentType.CALL -> {
                val name = lower.replace("call", "")
                    .replace("dial", "")
                    .replace("phone", "")
                    .replace("please", "")
                    .trim()
                ParsedIntent(predictedType, name)
            }
            IntentType.UNKNOWN -> ParsedIntent(IntentType.UNKNOWN, text)
            else -> ParsedIntent(predictedType)
        }
        return ParsedIntent(IntentType.UNKNOWN, null)
    }
}




