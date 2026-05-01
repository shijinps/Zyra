import re

file_path = "app/src/main/java/com/zyra/app/service/VoiceAssistantService.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Imports
content = content.replace(
    "import ai.picovoice.porcupine.PorcupineManager\nimport ai.picovoice.porcupine.PorcupineException\nimport ai.picovoice.porcupine.Porcupine",
    "import org.vosk.Model\nimport org.vosk.Recognizer\nimport org.vosk.android.SpeechService\nimport org.vosk.android.StorageService\nimport org.json.JSONObject\nimport org.vosk.android.RecognitionListener as VoskRecognitionListener"
)

# 2. Variables
content = content.replace(
    "private var porcupineManager: PorcupineManager? = null // Wake Word Engine",
    "private var voskModel: Model? = null\n    private var voskSpeechService: SpeechService? = null // Wake Word Engine"
)
content = content.replace("isPorcupineActive", "isVoskActive")
content = content.replace("isPorcupine", "isVoskModelReady")

# 3. initPorcupine -> initVosk
init_vosk_code = """
    private var isVoskModelReady = false

    private fun initVosk() {
        StorageService.unpack(this, "model", "model",
            { model ->
                voskModel = model
                isVoskModelReady = true
                Log.d("ZyraService", "Vosk model loaded successfully.")
                // Start passive listening if not already listening
                if (!isListening && !isLiveKitSessionActive && !isActiveSession) {
                    startPassiveListening()
                }
            },
            { exception ->
                Log.e("ZyraService", "Failed to unpack Vosk model", exception)
            }
        )
    }

    private val voskListener = object : VoskRecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            hypothesis?.let {
                try {
                    val json = JSONObject(it)
                    val text = json.optString("partial", "").lowercase()
                    if (text.contains("zyra") || text.contains("zara") || text.contains("terminator")) {
                        onWakeWordDetected()
                    }
                } catch (e: Exception) {}
            }
        }

        override fun onResult(hypothesis: String?) {
            hypothesis?.let {
                try {
                    val json = JSONObject(it)
                    val text = json.optString("text", "").lowercase()
                    if (text.contains("zyra") || text.contains("zara") || text.contains("terminator")) {
                        onWakeWordDetected()
                    }
                } catch (e: Exception) {}
            }
        }

        override fun onFinalResult(hypothesis: String?) {}
        override fun onError(exception: Exception?) {
            Log.e("ZyraService", "Vosk Error", exception)
            isVoskActive = false
        }
        override fun onTimeout() {}
    }

    private fun onWakeWordDetected() {
        Log.d("ZyraService", "Wake Word Detected via Vosk!")
        // Stop passive listening
        try { 
            voskSpeechService?.stop() 
            voskSpeechService = null
        } catch (e: Exception) {}
        isVoskActive = false
        isListening = false
        
        // Activate Active Session
        isActiveSession = true
        
        silenceBeep()
        try {
            val toneGen = android.media.ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP)
            android.os.Handler(mainLooper).postDelayed({ toneGen.release() }, 150)
        } catch (e: Exception) {}
        
        broadcastState(ZyraState.LISTENING)
        com.zyra.app.logic.LiveKitManager.connect(this@VoiceAssistantService)
    }
"""

import re
start_idx = content.find("private fun initPorcupine()")
if start_idx != -1:
    brace_count = 0
    in_function = False
    end_idx = -1
    for i in range(start_idx, len(content)):
        if content[i] == '{':
            brace_count += 1
            in_function = True
        elif content[i] == '}':
            brace_count -= 1
            if in_function and brace_count == 0:
                end_idx = i
                break
    
    if end_idx != -1:
        content = content[:start_idx] + init_vosk_code.strip() + content[end_idx+1:]

# 4. Replace initPorcupine() call with initVosk()
content = content.replace("initPorcupine()", "initVosk()")

# 5. startPassiveListening replacements
start_passive_idx = content.find("private fun startPassiveListening()")
if start_passive_idx != -1:
    brace_count = 0
    in_function = False
    end_idx = -1
    for i in range(start_passive_idx, len(content)):
        if content[i] == '{':
            if brace_count == 0:
                body_start = i + 1
            brace_count += 1
            in_function = True
        elif content[i] == '}':
            brace_count -= 1
            if in_function and brace_count == 0:
                end_idx = i
                break
    
    new_body = """
        if (isListening) return // SpeechRecognizer is active

        Log.d("ZyraService", "Starting Passive Listening (Vosk)...")
        if (isVoskModelReady && voskModel != null) {
            try {
                if (!isVoskActive) {
                    voskSpeechService?.stop()
                    voskSpeechService = SpeechService(Recognizer(voskModel, 16000.0f), 16000.0f)
                    voskSpeechService?.startListening(voskListener)
                    isVoskActive = true
                }
            } catch (e: Exception) {
                Log.e("ZyraService", "Vosk Start Failed", e)
                startActiveListening()
            }
        } else {
            startActiveListening()
        }
    """
    content = content[:body_start] + new_body + content[end_idx:]

# 6. Stop Porcupine replacements
content = content.replace("try { porcupineManager?.stop() } catch(_: Exception) {}", "try { voskSpeechService?.stop(); voskSpeechService = null } catch(_: Exception) {}")
content = content.replace("try { porcupineManager?.stop() } catch (e: Exception) {}", "try { voskSpeechService?.stop(); voskSpeechService = null } catch (e: Exception) {}")
content = content.replace("porcupineManager?.delete()", "voskSpeechService?.stop(); voskSpeechService?.shutdown(); voskModel?.close()")

# 7. Watchdog log changes
content = content.replace("Watchdog: Porcupine is active and healthy.", "Watchdog: Vosk is active and healthy.")
content = content.replace("Porcupine is Healthy. Do nothing.", "Vosk is Healthy. Do nothing.")
content = content.replace("AND NOT Porcupine AND", "AND NOT Vosk AND")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Patching complete.")
