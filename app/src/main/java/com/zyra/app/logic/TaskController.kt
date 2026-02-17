package com.zyra.app.logic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Task Controller - JARVIS-style task management
 * "Add buy milk to my to-do list"
 * "What's on my to-do list?"
 */
object TaskController {
    
    private const val TASKS_FILE = "zyra_tasks.json"
    
    /**
     * Add a new task
     */
    fun addTask(context: Context, taskName: String, category: String = "general"): Task {
        val tasks = getAllTasks(context).toMutableList()
        val newTask = Task(
            id = System.currentTimeMillis().toInt(),
            name = taskName,
            category = category,
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )
        
        tasks.add(newTask)
        saveTasks(context, tasks)
        return newTask
    }
    
    /**
     * Get all tasks
     */
    fun getAllTasks(context: Context): List<Task> {
        val file = File(context.filesDir, TASKS_FILE)
        if (!file.exists()) return emptyList()
        
        val json = file.readText()
        val jsonArray = JSONArray(json)
        val tasks = mutableListOf<Task>()
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            tasks.add(
                Task(
                    id = obj.getInt("id"),
                    name = obj.getString("name"),
                    category = obj.optString("category", "general"),
                    isCompleted = obj.getBoolean("isCompleted"),
                    createdAt = obj.getLong("createdAt")
                )
            )
        }
        
        return tasks
    }
    
    /**
     * Get pending (incomplete) tasks
     */
    fun getPendingTasks(context: Context): List<Task> {
        return getAllTasks(context).filter { !it.isCompleted }
    }
    
    /**
     * Get tasks by category
     */
    fun getTasksByCategory(context: Context, category: String): List<Task> {
        return getAllTasks(context).filter { it.category == category }
    }
    
    /**
     * Mark task as completed
     */
    fun completeTask(context: Context, taskId: Int): Boolean {
        val tasks = getAllTasks(context).toMutableList()
        val task = tasks.find { it.id == taskId } ?: return false
        
        val index = tasks.indexOf(task)
        tasks[index] = task.copy(isCompleted = true)
        saveTasks(context, tasks)
        return true
    }
    
    /**
     * Mark task as incomplete
     */
    fun uncompleteTask(context: Context, taskId: Int): Boolean {
        val tasks = getAllTasks(context).toMutableList()
        val task = tasks.find { it.id == taskId } ?: return false
        
        val index = tasks.indexOf(task)
        tasks[index] = task.copy(isCompleted = false)
        saveTasks(context, tasks)
        return true
    }
    
    /**
     * Delete a task
     */
    fun deleteTask(context: Context, taskId: Int): Boolean {
        val tasks = getAllTasks(context).toMutableList()
        val removed = tasks.removeIf { it.id == taskId }
        if (removed) {
            saveTasks(context, tasks)
        }
        return removed
    }
    
    /**
     * Clear all completed tasks
     */
    fun clearCompletedTasks(context: Context): Int {
        val tasks = getAllTasks(context)
        val pendingTasks = tasks.filter { !it.isCompleted }
        val clearedCount = tasks.size - pendingTasks.size
        saveTasks(context, pendingTasks)
        return clearedCount
    }
    
    /**
     * Clear all tasks
     */
    fun clearAllTasks(context: Context): Int {
        val count = getAllTasks(context).size
        saveTasks(context, emptyList())
        return count
    }
    
    /**
     * Format tasks for speech
     */
    fun formatTasksForSpeech(tasks: List<Task>): String {
        return when {
            tasks.isEmpty() -> "Your to-do list is empty"
            tasks.size == 1 -> "You have 1 task: ${tasks[0].name}"
            else -> {
                val taskList = tasks.take(5).mapIndexed { index, task ->
                    "${index + 1}. ${task.name}"
                }.joinToString(". ")
                
                if (tasks.size > 5) {
                    "You have ${tasks.size} tasks. Here are the first 5: $taskList. And ${tasks.size - 5} more"
                } else {
                    "You have ${tasks.size} tasks: $taskList"
                }
            }
        }
    }
    
    /**
     * Parse task command
     * Examples: "add buy milk", "add call mom to personal"
     */
    fun parseTaskCommand(command: String): Pair<String, String>? {
        val lowerCommand = command.lowercase()
        
        // Extract task name
        val taskName = when {
            lowerCommand.startsWith("add ") -> {
                lowerCommand.removePrefix("add ").trim()
            }
            lowerCommand.contains("to my") -> {
                lowerCommand.substringBefore("to my").trim()
            }
            else -> command.trim()
        }
        
        // Extract category if specified
        val category = when {
            lowerCommand.contains("to shopping") -> "shopping"
            lowerCommand.contains("to work") -> "work"
            lowerCommand.contains("to personal") -> "personal"
            else -> "general"
        }
        
        return if (taskName.isNotBlank()) {
            Pair(taskName, category)
        } else null
    }
    
    private fun saveTasks(context: Context, tasks: List<Task>) {
        val jsonArray = JSONArray()
        tasks.forEach { task ->
            val obj = JSONObject().apply {
                put("id", task.id)
                put("name", task.name)
                put("category", task.category)
                put("isCompleted", task.isCompleted)
                put("createdAt", task.createdAt)
            }
            jsonArray.put(obj)
        }
        
        val file = File(context.filesDir, TASKS_FILE)
        file.writeText(jsonArray.toString())
    }
    
    data class Task(
        val id: Int,
        val name: String,
        val category: String,
        val isCompleted: Boolean,
        val createdAt: Long
    )
}
