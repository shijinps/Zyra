package com.zyra.app.logic.brain

import android.content.Context
import android.util.Log
import com.zyra.app.data.brain.BrainActionType
import com.zyra.app.data.brain.BrainTask
import com.zyra.app.data.brain.BrainTaskStep
import com.zyra.app.service.ZyraAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class BrainExecutor(private val context: Context) {

    suspend fun executeTask(task: BrainTask, steps: List<BrainTaskStep>) = withContext(Dispatchers.Default) {
        Log.d("BrainExecutor", "Starting Task: ${task.name}")
        
        steps.sortedBy { it.order }.forEach { step ->
            Log.d("BrainExecutor", "Executing Step ${step.order}: ${step.actionType} - ${step.actionData}")
            executeStep(step)
            // Default delay between steps to allow UI to settle
            delay(2000) 
        }
        
        Log.d("BrainExecutor", "Task Completed: ${task.name}")
    }

    private suspend fun executeStep(step: BrainTaskStep) {
        val service = ZyraAccessibilityService.instance
        if (service == null) {
            Log.e("BrainExecutor", "Accessibility Service NOT connected.")
            return
        }

        when (step.actionType) {
            BrainActionType.OPEN_APP -> {
                val intent = context.packageManager.getLaunchIntentForPackage(step.actionData)
                if (intent != null) {
                    context.startActivity(intent)
                } else {
                    // Try to find by name if package name fails (simplified)
                    // For now, assume package name or simple name handling needed later
                    Log.w("BrainExecutor", "Could not find app to open: ${step.actionData}")
                }
            }
            BrainActionType.CLICK_TEXT -> {
                service.handleAction("click:${step.actionData}")
            }
            BrainActionType.SCROLL -> {
                service.handleAction("scroll:${step.actionData}") // data = "down" or "up"
            }
            BrainActionType.INPUT_TEXT -> {
                service.handleAction("type:${step.actionData}")
            }
            BrainActionType.WAIT -> {
                 try {
                     val time = step.actionData.toLongOrNull() ?: 1000
                     delay(time)
                 } catch (e: Exception) { }
            }
            BrainActionType.SAY -> {
                // We don't have direct access to TTS here easily unless we go through service
                // Assuming service has a speak method accessible or via broadcast
            }
        }
    }
}
