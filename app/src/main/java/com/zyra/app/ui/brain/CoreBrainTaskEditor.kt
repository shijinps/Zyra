package com.zyra.app.ui.brain

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyra.app.data.brain.BrainActionType
import com.zyra.app.data.brain.BrainTaskStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreBrainTaskEditor(
    viewModel: BrainTaskViewModel = viewModel(factory = BrainTaskViewModel.Factory),
    onBack: () -> Unit
) {
    var taskName by remember { mutableStateOf("") }
    var triggerPhrase by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf(listOf<BrainTaskStep>()) }
    
    // Dialog State
    var showStepDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Brain Task") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (taskName.isNotEmpty()) {
                            viewModel.addTask(taskName, triggerPhrase, steps)
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.Save, "Save")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showStepDialog = true }) {
                Icon(Icons.Default.Add, "Add Step")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = taskName,
                onValueChange = { taskName = it },
                label = { Text("Task Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = triggerPhrase,
                onValueChange = { triggerPhrase = it },
                label = { Text("Voice Trigger (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Automation Steps", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(steps) { index, step ->
                    StepItem(index + 1, step, onDelete = {
                        steps = steps.toMutableList().apply { removeAt(index) }
                    })
                }
            }
        }
    }

    if (showStepDialog) {
        AddStepDialog(
            onDismiss = { showStepDialog = false },
            onAdd = { type, data ->
                val newStep = BrainTaskStep(
                    taskId = 0, // temp
                    order = steps.size,
                    actionType = type,
                    actionData = data
                )
                steps = steps + newStep
                showStepDialog = false
            }
        )
    }
}

@Composable
fun StepItem(order: Int, step: BrainTaskStep, onDelete: () -> Unit) {
    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$order.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = step.actionType.name, style = MaterialTheme.typography.labelMedium)
                Text(text = step.actionData, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Remove")
            }
        }
    }
}

@Composable
fun AddStepDialog(onDismiss: () -> Unit, onAdd: (BrainActionType, String) -> Unit) {
    var selectedType by remember { mutableStateOf(BrainActionType.OPEN_APP) }
    var dataInput by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Action Step") },
        text = {
            Column {
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(text = selectedType.name)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        BrainActionType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dataInput,
                    onValueChange = { dataInput = it },
                    label = { Text("Data (e.g., App Name or Text)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedType == BrainActionType.CLICK_TEXT) {
                     Text("Enter the text to find and click.", style = MaterialTheme.typography.bodySmall)
                } else if (selectedType == BrainActionType.OPEN_APP) {
                     Text("Enter the exact app name.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(selectedType, dataInput) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
