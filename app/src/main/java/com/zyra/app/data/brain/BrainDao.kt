package com.zyra.app.data.brain

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BrainDao {
    @Query("SELECT * FROM brain_tasks")
    fun getAllTasks(): Flow<List<BrainTask>>

    @Query("SELECT * FROM brain_tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): BrainTask?
    
    @Query("SELECT * FROM brain_tasks WHERE triggerPhrase = :phrase LIMIT 1")
    suspend fun getTaskByTrigger(phrase: String): BrainTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: BrainTask): Long

    @Delete
    suspend fun deleteTask(task: BrainTask)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: BrainTaskStep)

    @Query("DELETE FROM brain_task_steps WHERE taskId = :taskId")
    suspend fun deleteStepsForTask(taskId: Int)

    @Query("SELECT * FROM brain_task_steps WHERE taskId = :taskId ORDER BY `order` ASC")
    suspend fun getStepsForTask(taskId: Int): List<BrainTaskStep>
    
    @Transaction
    suspend fun insertTaskWithSteps(task: BrainTask, steps: List<BrainTaskStep>) {
        val taskId = insertTask(task)
        val stepsWithId = steps.map { it.copy(taskId = taskId.toInt()) }
        deleteStepsForTask(taskId.toInt()) // clear old steps if any
        stepsWithId.forEach { insertStep(it) }
    }
}
