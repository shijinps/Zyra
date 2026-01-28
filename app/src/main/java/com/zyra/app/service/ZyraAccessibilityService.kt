package com.zyra.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZyraAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isScanning = false

    companion object {
        var instance: ZyraAccessibilityService? = null
        
        fun requestScreenScan(callbackService: VoiceAssistantService) {
            instance?.scanAndRead(callbackService) ?: run {
                Log.e("ZyraAccess", "Service not running or disabled")
            }
        }
        
        fun requestScreenAnalysis(prompt: String) {
            instance?.analyzeScreen(prompt)
        }
        
        fun performAction(actionPayload: String) {
            instance?.handleAction(actionPayload)
        }
    }
    
    fun handleAction(payload: String) {
        // payload examples: "click:search", "scroll:down", "global:home", "gesture:540,1800"
        val parts = payload.split(":", limit = 2)
        if (parts.size < 2) return
        
        val command = parts[0]
        val target = parts[1]
        
        when (command) {
            "click" -> clickElement(target)
            "scroll" -> scrollWindow(target)
            "global" -> performGlobal(target)
            "type" -> typeText(target)
            "desc" -> clickElementByDescription(target)
            "gesture" -> {
                 // Format: gesture:x1,y1,x2,y2,duration
                 val parts = target.split(",")
                 if (parts.size == 2) {
                     // Click
                     clickAtLocation(parts[0].toFloat(), parts[1].toFloat())
                 } else if (parts.size >= 4) {
                     // Swipe
                     val x1 = parts[0].toFloat()
                     val y1 = parts[1].toFloat()
                     val x2 = parts[2].toFloat()
                     val y2 = parts[3].toFloat()
                     val duration = if (parts.size > 4) parts[4].toLong() else 300L
                     performSwipe(x1, y1, x2, y2, duration)
                 }
            }
        }
    }
    
    // Simulates a tap
    private fun clickAtLocation(x: Float, y: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
             val path = android.graphics.Path()
             path.moveTo(x, y)
             val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100)
             val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
             dispatchGesture(gesture, null, null)
             Log.d("ZyraAccess", "Dispatched click at $x, $y")
        }
    }

    // Simulates a swipe
    private fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
             val path = android.graphics.Path()
             path.moveTo(x1, y1)
             path.lineTo(x2, y2)
             val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, duration)
             val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
             dispatchGesture(gesture, null, null)
             Log.d("ZyraAccess", "Dispatched swipe from $x1,$y1 to $x2,$y2")
        }
    }
    
    public fun performGlobal(action: String) {
        when (action) {
            "home" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "back" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "recents" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            "lock" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            }
        }
    }
    

    
    private fun clickElementByDescription(desc: String) {
        val root = rootInActiveWindow ?: return
        
        // No direct API for "find by description" exists like "find by text" in older APIs?
        // Actually, we have to traverse or use search.
        // But for efficiency, let's do a recursive search or use findAccessibilityNodeInfosByText if desc matches text? 
        // No, desc is different.
        
        // BFS Search for Content Description
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        var attempts = 0
        while (!queue.isEmpty() && attempts < 500) { // Safety limit
            val node = queue.poll() ?: continue
            attempts++
            
            if (node.contentDescription != null && node.contentDescription.toString().contains(desc, ignoreCase = true)) {
                // Found it
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d("ZyraAccess", "Clicked node by desc: $desc")
                    return
                } else {
                    val parent = node.parent
                    if (parent != null && parent.isClickable) {
                         parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                         Log.d("ZyraAccess", "Clicked parent by desc: $desc")
                         return
                    }
                }
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) queue.add(child)
            }
        }
    }

    private fun clickElement(text: String) {
        val root = rootInActiveWindow ?: return
        // Partial match, case insensitive
        val nodes = root.findAccessibilityNodeInfosByText(text)
        
        // 1. Try exact/direct list from API
        if (nodes.isNotEmpty()) {
            for (node in nodes) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d("ZyraAccess", "Clicked node with text: $text")
                    return
                } else {
                     // Try parent (often textview needs parent click)
                     val parent = node.parent
                     if (parent != null && parent.isClickable) {
                         parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                         Log.d("ZyraAccess", "Clicked parent of node with text: $text")
                         return
                     }
                }
            }
        }
    }

    private fun scrollWindow(direction: String) {
        val root = rootInActiveWindow ?: return
        val scrollable = findScrollableNode(root)
        if (scrollable != null) {
            if (direction == "down") {
                scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            } else {
                scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }
        }
    }
    
    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findScrollableNode(child)
                if (found != null) return found
            }
        }
        return null
    }
    
    private fun typeText(text: String) {
        val root = rootInActiveWindow ?: return
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        
        if (focus != null && focus.isEditable) {
            val arguments = android.os.Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        tts = TextToSpeech(this, this)
        Log.d("ZyraAccess", "Accessibility Service Connected")
        
        // "Extreme" Persistence: Force start VoiceAssistantService if it died
        ensureVoiceServiceRunning()
        
        // Start Heartbeat Watchdog
        // Checks every 5 seconds if the main service is alive.
        android.os.Handler(android.os.Looper.getMainLooper()).post(object : Runnable {
            override fun run() {
                ensureVoiceServiceRunning()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 5000)
            }
        })
    }
    
    private fun ensureVoiceServiceRunning() {
        val serviceIntent = Intent(this, VoiceAssistantService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            // Already running or some other minor issue
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We generally don't want to react to every event, only when requested.
    }

    override fun onInterrupt() {
        Log.d("ZyraAccess", "Accessibility Service Interrupted")
        instance = null
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        tts?.shutdown()
        return super.onUnbind(intent)
    }

    private fun scanAndRead(callbackService: VoiceAssistantService) {
        val rootNode = rootInActiveWindow ?: return
        val vehicleNumber = findVehicleNumber(rootNode)
        
        if (vehicleNumber != null) {
            Log.d("ZyraAccess", "Found Vehicle: $vehicleNumber")
            speak("I found vehicle number $vehicleNumber on your screen. Checking details...")
            // Here we would trigger the "Check Database" logic
            // For now, simulate result
            speak("Vehicle $vehicleNumber is a 2024 Model, Registered to Zyra User.")
        } else {
            speak("I could not find any vehicle number on the screen.")
        }
    }

    private fun findVehicleNumber(node: AccessibilityNodeInfo): String? {
        if (node.text != null) {
            val text = node.text.toString()
            // Regex for generic Indian format: KL 07 BT 1234 or KL07BT1234
            val pattern = Pattern.compile("[A-Z]{2}[ ]?[0-9]{1,2}[ ]?[A-Z]{0,3}[ ]?[0-9]{4}", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group()
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findVehicleNumber(child)
                child.recycle()
                if (result != null) return result
            }
        }
        return null
    }

    private fun analyzeScreen(prompt: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY,
                this.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try {
                            val hardwareBuffer = result.hardwareBuffer
                            val colorSpace = result.colorSpace
                            val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            
                            if (bitmap != null) {
                                // Copy to software bitmap if needed, but wrap might be enough for Gemini
                                // Actually, wrapHardwareBuffer returns an immutable bitmap.
                                // We need to ensure we close the hardware buffer? 
                                // The documentation says "The caller is responsible for closing the hardware buffer".
                                // But Bitmap.wrapHardwareBuffer takes ownership? No.
                                // Let's copy it to be safe and close the buffer.
                                val softwareBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                hardwareBuffer.close()
                                
                                speak("I see it. Analyzing...")
                                
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    val response = com.zyra.app.logic.GeminiModule.generateResponseWithImage(prompt, softwareBitmap)
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        speak(response)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ZyraAccess", "Screenshot failed", e)
                            speak("I tried to look at the screen, but something went wrong.")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e("ZyraAccess", "Screenshot failed with error $errorCode")
                        speak("I cannot see the screen right now.")
                    }
                })
        } else {
            speak("Screen analysis is only available on Android 11 and above.")
        }
    }

    private fun speak(text: String) {
         if (tts != null) {
             tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ZyraAccessUtterance")
         }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.getDefault())
        }
    }
}
