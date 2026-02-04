package com.zyra.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.zyra.app.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke

import com.zyra.app.data.Contact
import com.zyra.app.data.ContactRepository
import com.zyra.app.logic.CommandManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import com.zyra.app.logic.ZyraState
import com.zyra.app.service.VoiceAssistantService
import com.zyra.app.ui.SiriWaveAnimation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Premium Dark Colors
val Obsidian = Color(0xFF050505)
val MidnightBlue = Color(0xFF0A0F1E)
val NeonGreen = Color(0xFF00E676)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartService: () -> Unit,
    contactRepository: ContactRepository,
    commandManager: CommandManager,
    onManualWake: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    onNavigateToBrain: () -> Unit
) {
    var zyraState by remember { mutableStateOf(ZyraState.IDLE) }
    var showAddDialog by remember { mutableStateOf(false) }
    var commands by remember { mutableStateOf(commandManager.getAllCommands()) }
    val context = LocalContext.current
    
    // Status Checks
    val hasOverlayPermission = remember(context) { android.provider.Settings.canDrawOverlays(context) }
    val isServiceRunning = remember { true } // Ideally check actual service status

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val stateName = intent?.getStringExtra(VoiceAssistantService.EXTRA_STATE)
                if (stateName != null) {
                    try { zyraState = ZyraState.valueOf(stateName) } catch (e: Exception) { }
                }
            }
        }
        val filter = IntentFilter(VoiceAssistantService.BROADCAST_STATE)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
        context.registerReceiver(receiver, filter, flags)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val contacts by produceState<List<Contact>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { contactRepository.getContacts() }
    }

    Scaffold(
        floatingActionButton = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(NeonGreen, Color(0xFF00B0FF), NeonGreen)
                        )
                    )
                    .clickable { showAddDialog = true }
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp) // Inner border effect
                        .clip(CircleShape)
                        .background(Color.Black)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(NeonGreen.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, "Add", tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = Obsidian
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(MidnightBlue, Obsidian)))
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Header & Live Visualizer
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Animated ZYRA Text
                            val shimmerColors = listOf(
                                Color.White,
                                NeonGreen,
                                Color.White,
                                Color(0xFF00B0FF),
                                Color.White
                            )
                            val transition = rememberInfiniteTransition(label = "textShimmer")
                            val translateAnim = transition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1000f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(
                                        durationMillis = 2000,
                                        easing = LinearEasing
                                    ),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "translateAnim"
                            )
                            
                            val brush = Brush.linearGradient(
                                colors = shimmerColors,
                                start = Offset.Zero,
                                end = Offset(x = translateAnim.value, y = translateAnim.value)
                            )
                            
                            Text(
                                text = "ZYRA",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    brush = brush,
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = NeonGreen.copy(alpha = 0.5f),
                                        offset = Offset(0f, 0f),
                                        blurRadius = 20f
                                    )
                                )
                            )
                            IconButton(onClick = onNavigateToFeedback) {
                                Icon(Icons.Default.Feedback, null, tint = Color.White.copy(0.7f))
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Live Orb
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                            SiriWaveAnimation(state = zyraState, modifier = Modifier.fillMaxSize())
                        }
                        
                        Text(
                            text = if (zyraState == ZyraState.IDLE) "Waiting for 'Hey Zyra'" else zyraState.name,
                            color = NeonGreen,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // 2. Status Grid
                item {
                    Text("SYSTEM STATUS", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusCard(
                            label = "Overlay",
                            isActive = hasOverlayPermission,
                            modifier = Modifier.weight(1f),
                            onClick = { 
                                if (!hasOverlayPermission) {
                                     val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                                     context.startActivity(intent)
                                }
                            }
                        )
                        StatusCard(
                            label = "Wake Word",
                            isActive = true, // Always active in service
                            modifier = Modifier.weight(1f)
                        )
                        StatusCard(
                            label = "WAKE ZYRA",
                            isActive = true,
                            modifier = Modifier.weight(1f),
                            onClick = onManualWake, // DIRECT TRIGGER
                            icon = Icons.Default.PowerSettingsNew // Use Power Icon for Wake
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatusCard(
                            label = "Service",
                            isActive = isServiceRunning,
                            modifier = Modifier.weight(1f),
                             onClick = onStartService,
                             icon = Icons.Default.Settings // Use Settings Icon
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        StatusCard(
                            label = "Brain / Tasks",
                            isActive = true,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToBrain,
                            icon = Icons.Default.Psychology // Brain Icon
                        )
                    }
                }

                // 3. Command Manager
                item {
                    Text("POWER COMMANDS", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
                
                items(commands.toList()) { (phrase, contactName) ->
                    GlassCard {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(phrase.replaceFirstChar { it.uppercase() }, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("Call $contactName", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = {
                                commandManager.removeCommand(phrase)
                                commands = commandManager.getAllCommands()
                            }) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.7f)) }
                        }
                    }
                }
                
                item {
                     Spacer(modifier = Modifier.height(80.dp)) // Pad for FAB
                }
            }
        
            if (showAddDialog) {
                AddCommandDialogPremium(
                    contacts = contacts,
                    onDismiss = { showAddDialog = false },
                    onSave = { p, c ->
                        commandManager.addCommand(p, c.phoneNumber, c.name)
                        commands = commandManager.getAllCommands()
                        showAddDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun StatusCard(label: String, isActive: Boolean, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.PowerSettingsNew, onClick: () -> Unit = {}) {
    Card(
        modifier = modifier
            .height(90.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2433).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(0.1f), Color.Transparent)))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) NeonGreen else Color.Red,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun GlassCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2433).copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        content()
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCommandDialogPremium(
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onSave: (String, Contact) -> Unit
) {
    var phrase by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    
    val filteredContacts = remember(searchQuery, contacts) {
        if (searchQuery.isEmpty()) contacts
        else contacts.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Power Command", color = NeonGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                // Command Input
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text("When I say...", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = NeonGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Search Contacts
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search contact...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = NeonGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                
                // Contact List
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(filteredContacts) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedContact = contact }
                                .background(if (selectedContact == contact) NeonGreen.copy(0.2f) else Color.Transparent)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(if (selectedContact == contact) NeonGreen else Color.DarkGray, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(contact.name.take(1), color = if (selectedContact == contact) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(contact.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                        Divider(color = Color.White.copy(0.05f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (phrase.isNotEmpty() && selectedContact != null) {
                        onSave(phrase, selectedContact!!)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                enabled = phrase.isNotEmpty() && selectedContact != null
            ) {
                Text("Activate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}
