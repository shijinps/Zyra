package com.zyra.app.logic

import android.content.Context
import com.zyra.app.logic.IntentType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * Workflow Engine for Task Automation
 * Enables IFTTT-style automation and multi-step task execution
 */
object WorkflowEngine {
    
    private const val WORKFLOW_FILE = "zyra_workflows.json"
    
    data class Workflow(
        val id: String,
        val name: String,
        val trigger: Trigger,
        val conditions: List<Condition>,
        val actions: List<Action>,
        var enabled: Boolean = true,
        var lastExecuted: Long = 0,
        var executionCount: Int = 0
    )
    
    sealed class Trigger {
        data class TimeBasedTrigger(
            val hour: Int,
            val minute: Int,
            val daysOfWeek: List<Int> = listOf() // Empty = every day
        ) : Trigger()
        
        data class LocationTrigger(
            val placeName: String,
            val entering: Boolean = true // true = entering, false = leaving
        ) : Trigger()
        
        data class EventTrigger(
            val eventType: String, // "call_received", "battery_low", "wifi_connected"
            val value: String? = null
        ) : Trigger()
        
        data class ManualTrigger(val keyword: String) : Trigger()
    }
    
    sealed class Condition {
        data class TimeCondition(val afterHour: Int, val beforeHour: Int) : Condition()
        data class LocationCondition(val placeName: String) : Condition()
        data class BatteryCondition(val minLevel: Int, val maxLevel: Int = 100) : Condition()
        data class DayCondition(val daysOfWeek: List<Int>) : Condition()
        
        fun evaluate(context: Context): Boolean {
            return when (this) {
                is TimeCondition -> {
                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    hour in afterHour..beforeHour
                }
                is BatteryCondition -> {
                    // Would check actual battery level
                    true
                }
                is DayCondition -> {
                    val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                    daysOfWeek.contains(day)
                }
                else -> true
            }
        }
    }
    
    sealed class Action {
        data class CallAction(val contactName: String) : Action()
        data class MessageAction(val contactName: String, val message: String) : Action()
        data class DeviceAction(val device: String, val state: Boolean) : Action() // "wifi", "bluetooth", "flashlight"
        data class VolumeAction(val level: Int) : Action()
        data class SpeakAction(val text: String) : Action()
        data class PlayMusicAction(val playlist: String? = null) : Action()
        data class AlarmAction(val hour: Int, val minute: Int) : Action()
        data class NotificationAction(val title: String, val message: String) : Action()
        
        suspend fun execute(context: Context) {
            // Execution would be handled by VoiceAssistantService
            // For now, this is a placeholder
        }
    }
    
    private val workflows = mutableListOf<Workflow>()
    
    /**
     * Add a new workflow
     */
    fun addWorkflow(workflow: Workflow) {
        workflows.add(workflow)
    }
    
    /**
     * Remove workflow
     */
    fun removeWorkflow(id: String) {
        workflows.removeIf { it.id == id }
    }
    
    /**
     * Enable/disable workflow
     */
    fun setWorkflowEnabled(id: String, enabled: Boolean) {
        workflows.find { it.id == id }?.enabled = enabled
    }
    
    /**
     * Execute workflow if conditions are met
     */
    suspend fun executeWorkflow(context: Context, workflow: Workflow): Boolean {
        if (!workflow.enabled) return false
        
        // Check all conditions
        val allConditionsMet = workflow.conditions.all { it.evaluate(context) }
        if (!allConditionsMet) return false
        
        // Execute all actions
        workflow.actions.forEach { action ->
            action.execute(context)
        }
        
        // Update workflow stats
        workflow.lastExecuted = System.currentTimeMillis()
        workflow.executionCount++
        
        return true
    }
    
    /**
     * Check time-based triggers
     */
    fun checkTimeTriggers(context: Context) {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        
        workflows.filter { it.enabled && it.trigger is Trigger.TimeBasedTrigger }.forEach { workflow ->
            val trigger = workflow.trigger as Trigger.TimeBasedTrigger
            
            val timeMatches = trigger.hour == currentHour && trigger.minute == currentMinute
            val dayMatches = trigger.daysOfWeek.isEmpty() || trigger.daysOfWeek.contains(currentDay)
            
            if (timeMatches && dayMatches) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    executeWorkflow(context, workflow)
                }
            }
        }
    }
    
    /**
     * Check location triggers
     */
    fun checkLocationTriggers(context: Context, placeName: String, entering: Boolean) {
        workflows.filter { it.enabled && it.trigger is Trigger.LocationTrigger }.forEach { workflow ->
            val trigger = workflow.trigger as Trigger.LocationTrigger
            
            if (trigger.placeName.equals(placeName, ignoreCase = true) && trigger.entering == entering) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    executeWorkflow(context, workflow)
                }
            }
        }
    }
    
    /**
     * Check event triggers
     */
    fun checkEventTriggers(context: Context, eventType: String, value: String? = null) {
        workflows.filter { it.enabled && it.trigger is Trigger.EventTrigger }.forEach { workflow ->
            val trigger = workflow.trigger as Trigger.EventTrigger
            
            if (trigger.eventType == eventType && (trigger.value == null || trigger.value == value)) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    executeWorkflow(context, workflow)
                }
            }
        }
    }
    
    /**
     * Check manual triggers
     */
    fun checkManualTrigger(context: Context, keyword: String): Workflow? {
        return workflows.find { 
            it.enabled && 
            it.trigger is Trigger.ManualTrigger && 
            (it.trigger as Trigger.ManualTrigger).keyword.equals(keyword, ignoreCase = true)
        }
    }
    
    /**
     * Get all workflows
     */
    fun getAllWorkflows(): List<Workflow> {
        return workflows.toList()
    }
    
    /**
     * Get workflow by ID
     */
    fun getWorkflow(id: String): Workflow? {
        return workflows.find { it.id == id }
    }
    
    /**
     * Create example workflows
     */
    fun createExampleWorkflows() {
        // Example 1: Morning routine
        addWorkflow(
            Workflow(
                id = "morning_routine",
                name = "Morning Routine",
                trigger = Trigger.TimeBasedTrigger(7, 0, listOf(2, 3, 4, 5, 6)), // Mon-Fri
                conditions = listOf(),
                actions = listOf(
                    Action.SpeakAction("Good morning! Time to start your day."),
                    Action.DeviceAction("wifi", true)
                )
            )
        )
        
        // Example 2: Arriving home
        addWorkflow(
            Workflow(
                id = "arriving_home",
                name = "Arriving Home",
                trigger = Trigger.LocationTrigger("Home", entering = true),
                conditions = listOf(),
                actions = listOf(
                    Action.DeviceAction("wifi", true),
                    Action.SpeakAction("Welcome home!")
                )
            )
        )
        
        // Example 3: Low battery alert
        addWorkflow(
            Workflow(
                id = "low_battery",
                name = "Low Battery Alert",
                trigger = Trigger.EventTrigger("battery_low"),
                conditions = listOf(Condition.BatteryCondition(0, 20)),
                actions = listOf(
                    Action.SpeakAction("Your battery is low. Please charge your device.")
                )
            )
        )
    }
    
    /**
     * Save workflows
     */
    fun save(context: Context) {
        try {
            val json = JSONObject()
            val workflowsArray = JSONArray()
            
            workflows.forEach { workflow ->
                val obj = JSONObject().apply {
                    put("id", workflow.id)
                    put("name", workflow.name)
                    put("enabled", workflow.enabled)
                    put("lastExecuted", workflow.lastExecuted)
                    put("executionCount", workflow.executionCount)
                    // Trigger, conditions, and actions would be serialized here
                    // Simplified for now
                }
                workflowsArray.put(obj)
            }
            
            json.put("workflows", workflowsArray)
            
            val file = File(context.filesDir, WORKFLOW_FILE)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Load workflows
     */
    fun load(context: Context) {
        try {
            val file = File(context.filesDir, WORKFLOW_FILE)
            if (!file.exists()) {
                createExampleWorkflows()
                return
            }
            
            // Load implementation would go here
            // For now, create example workflows
            createExampleWorkflows()
            
        } catch (e: Exception) {
            e.printStackTrace()
            createExampleWorkflows()
        }
    }
}
