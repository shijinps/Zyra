package com.zyra.app.data.brain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "brain_tasks")
data class BrainTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val triggerPhrase: String = "" // Optional voice trigger
)

@Entity(
    tableName = "brain_task_steps",
    foreignKeys = [
        ForeignKey(
            entity = BrainTask::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BrainTaskStep(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,
    val order: Int,
    val actionType: BrainActionType,
    val actionData: String // JSON or delimited string for params
)

enum class BrainActionType {
    OPEN_APP,
    CLICK_TEXT,
    SCROLL,
    INPUT_TEXT,
    WAIT,
    SAY
}
