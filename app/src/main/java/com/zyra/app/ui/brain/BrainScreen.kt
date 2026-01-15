package com.zyra.app.ui.brain

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyra.app.data.brain.BrainTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainScreen(
    viewModel: BrainTaskViewModel = viewModel(factory = BrainTaskViewModel.Factory),
    onCheckTask: (BrainTask) -> Unit = {}, // For debugging/editing
    onAddTask: () -> Unit // Navigate to editor
) {
    val tasks by viewModel.allTasks.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Brain Tasks",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            items(tasks) { task ->
                val context = androidx.compose.ui.platform.LocalContext.current
                BrainTaskItem(
                    task = task,
                    onDelete = { viewModel.removeTask(task) },
                    onClick = { onCheckTask(task) },
                    onExecute = { viewModel.executeTask(context, task) }
                )
            }
        }
    }
}

@Composable
fun BrainTaskItem(
    task: BrainTask,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onExecute: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = task.name, style = MaterialTheme.typography.titleMedium)
                if (task.triggerPhrase.isNotEmpty()) {
                    Text(text = "Trigger: \"${task.triggerPhrase}\"", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row {
                 IconButton(onClick = onExecute) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}
