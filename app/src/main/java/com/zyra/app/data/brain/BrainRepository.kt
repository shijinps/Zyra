package com.zyra.app.data.brain

import kotlinx.coroutines.flow.Flow

class BrainRepository(private val brainDao: BrainDao) {

    val allTasks: Flow<List<BrainTask>> = brainDao.getAllTasks()

    suspend fun getTaskById(id: Int): BrainTask? {
        return brainDao.getTaskById(id)
    }

    suspend fun addTask(name: String, triggerPhrase: String, steps: List<BrainTaskStep>) {
        val task = BrainTask(name = name, triggerPhrase = triggerPhrase)
        brainDao.insertTaskWithSteps(task, steps)
    }

    suspend fun updateTask(task: BrainTask, steps: List<BrainTaskStep>) {
        brainDao.insertTaskWithSteps(task, steps)
    }

    suspend fun deleteTask(task: BrainTask) {
        brainDao.deleteTask(task)
    }

    suspend fun getStepsForTask(taskId: Int): List<BrainTaskStep> {
        return brainDao.getStepsForTask(taskId)
    }
    
    suspend fun getTaskByTrigger(phrase: String): BrainTask? {
        return brainDao.getTaskByTrigger(phrase)
    }
}
