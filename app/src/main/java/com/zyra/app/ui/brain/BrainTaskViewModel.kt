package com.zyra.app.ui.brain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zyra.app.ZyraApp
import com.zyra.app.data.brain.BrainActionType
import com.zyra.app.data.brain.BrainRepository
import com.zyra.app.data.brain.BrainTask
import com.zyra.app.data.brain.BrainTaskStep
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrainTaskViewModel(private val repository: BrainRepository) : ViewModel() {

    val allTasks: StateFlow<List<BrainTask>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTask(name: String, trigger: String, steps: List<BrainTaskStep>) = viewModelScope.launch {
        repository.addTask(name, trigger, steps)
    }

    fun removeTask(task: BrainTask) = viewModelScope.launch {
        repository.deleteTask(task)
    }
    
    fun executeTask(context: android.content.Context, task: BrainTask) = viewModelScope.launch {
        val steps = repository.getStepsForTask(task.id)
        val executor = com.zyra.app.logic.brain.BrainExecutor(context)
        executor.executeTask(task, steps)
    }

    suspend fun getSteps(taskId: Int): List<BrainTaskStep> {
        return repository.getStepsForTask(taskId)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ZyraApp)
                BrainTaskViewModel(app.brainRepository)
            }
        }
    }
}
