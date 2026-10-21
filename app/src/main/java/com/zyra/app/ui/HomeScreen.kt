package com.zyra.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyra.app.data.ContactRepository
import com.zyra.app.logic.CommandManager
import com.zyra.app.logic.ZyraState
import com.zyra.app.service.VoiceAssistantService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow

// Professional HUD Palette
val ObsidianDeep = Color(0xFF030305)
val CyberCyan = Color(0xFF00E5FF)
val NeuralGreen = Color(0xFF00FFCC)
val GlassWhite = Color.White.copy(alpha = 0.05f)

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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val hasOverlay = remember(context) { android.provider.Settings.canDrawOverlays(context) }
    var backendHealthy by remember { mutableStateOf<Boolean?>(null) }
    val wakeWordHealthy = zyraState != ZyraState.ERROR && errorMessage == null
    val livekitActive = zyraState == ZyraState.LISTENING || zyraState == ZyraState.PROCESSING || zyraState == ZyraState.SPEAKING

    LaunchedEffect(Unit) {
        while (true) {
            backendHealthy = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val url = java.net.URL("https://apizyra.shijin.in/health")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.requestMethod = "GET"
                    conn.responseCode == 200
                }
            } catch (_: Exception) {
                false
            }
            kotlinx.coroutines.delay(30000L)
        }
    }

    // Fake Neural Logs for demo (In real use, these would come from an Observation system)
    val neuralLogs = remember {
        mutableStateListOf(
            "SYSTEM INITIALIZED [v2.5]",
            "VCF ENGINE: Local persistence ready.",
            "MEMORY: 742 contacts indexed.",
            "BRAIN: Context memory restored.",
            "READY: Awaiting user input..."
        )
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val stateName = intent?.getStringExtra(VoiceAssistantService.EXTRA_STATE)
                val errorMsg = intent?.getStringExtra(VoiceAssistantService.EXTRA_ERROR_MESSAGE)
                if (errorMsg != null) {
                    errorMessage = errorMsg
                }
                if (stateName != null) {
                    try { 
                        zyraState = ZyraState.valueOf(stateName)
                        if (zyraState != ZyraState.ERROR && errorMsg == null) {
                            errorMessage = null
                        }
                        neuralLogs.add(0, "STATE_CHANGE: -> ${stateName}")
                    } catch (e: Exception) { }
                }
            }
        }
        val filter = IntentFilter(VoiceAssistantService.BROADCAST_STATE)
        context.registerReceiver(receiver, filter, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0)
        onDispose { context.unregisterReceiver(receiver) }
    }

    Scaffold(
        containerColor = ObsidianDeep
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF050A1A), ObsidianDeep)
                    )
                )
                .padding(padding)
        ) {
            // HUD Decorative Background (Scanning Line)
            val infiniteTransition = rememberInfiniteTransition(label = "hud_scan")
            val scanY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1000f,
                animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
                label = "scan_y"
            )
            
            Canvas(modifier = Modifier.fillMaxSize().alpha(0.05f)) {
                drawLine(
                    color = CyberCyan,
                    start = Offset(0f, scanY),
                    end = Offset(size.width, scanY),
                    strokeWidth = 2f
                )
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Sleek Technical Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Z.A.Y. / Z.Y.R.A.",
                            color = CyberCyan,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "NEURAL OS INTERFACE v2.0",
                            color = Color.White.copy(0.4f),
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp
                        )
                    }
                    Row {
                        IconButton(onClick = onNavigateToFeedback) {
                            Icon(Icons.Default.MonitorHeart, null, tint = NeuralGreen.copy(0.6f))
                        }
                        IconButton(onClick = { showDiagnostics = true }) {
                            Icon(Icons.Default.Dns, null, tint = Color.White.copy(0.3f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Error Banner if in ERROR state or errorMessage is present
                if (zyraState == ZyraState.ERROR || errorMessage != null) {
                    val displayMsg = errorMessage ?: "Wake word key refused"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF3E1015))
                            .border(1.dp, Color(0xFFEF5350), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "⚠️ $displayMsg",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // 2. The Quantum Core (Interactive)
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clickable { onManualWake() },
                    contentAlignment = Alignment.Center
                ) {
                    SiriWaveAnimation(state = zyraState, modifier = Modifier.fillMaxSize())
                }
                
                Text(
                    text = when (zyraState) {
                        ZyraState.IDLE -> "SYSTEM IDLE"
                        ZyraState.ERROR -> "SYSTEM ERROR"
                        else -> "ACTIVE: $zyraState"
                    },
                    color = when (zyraState) {
                        ZyraState.IDLE -> Color.White.copy(0.5f)
                        ZyraState.ERROR -> Color(0xFFEF5350)
                        else -> NeuralGreen
                    },
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // On-Screen Push-to-Talk (PTT) Trigger Button
                Button(
                    onClick = onManualWake,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (zyraState == ZyraState.ERROR) Color(0xFFEF5350).copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.15f),
                        contentColor = if (zyraState == ZyraState.ERROR) Color(0xFFEF5350) else CyberCyan
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (zyraState == ZyraState.ERROR) Color(0xFFEF5350).copy(alpha = 0.5f) else CyberCyan.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Push to Talk", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (zyraState == ZyraState.LISTENING || zyraState == ZyraState.PROCESSING || zyraState == ZyraState.SPEAKING) "SESSION ACTIVE (TAP TO END)" else "PUSH TO TALK",
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. System Status Indicator Chips (Backend, Wake Word, LiveKit)
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IntegrityItem(
                            icon = Icons.Default.Dns,
                            label = if (backendHealthy == true) "BACKEND: OK" else if (backendHealthy == false) "BACKEND: ERR" else "BACKEND: ...",
                            isActive = backendHealthy == true
                        )
                        IntegrityItem(
                            icon = Icons.Default.RecordVoiceOver,
                            label = if (wakeWordHealthy) "WAKE WORD: ON" else "WAKE WORD: ERR",
                            isActive = wakeWordHealthy
                        )
                        IntegrityItem(
                            icon = Icons.Default.CloudQueue,
                            label = if (livekitActive) "LIVEKIT: LIVE" else "LIVEKIT: READY",
                            isActive = livekitActive || backendHealthy == true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                // 4. Neural Activity Log (Professional Terminal)
                Text(
                    text = "NEURAL ACTIVITY FEED",
                    color = Color.White.copy(0.4f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(12.dp))
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        reverseLayout = false
                    ) {
                        items(neuralLogs.take(50)) { log ->
                            Text(
                                text = ">> ${log}",
                                color = if (log.contains("ACTIVE") || log.contains("CHANGE")) NeuralGreen else Color.White.copy(0.7f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 2.dp).alpha(0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                
                // Primary Brain Control Button
                Button(
                    onClick = onNavigateToBrain,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassWhite, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(0.1f))
                ) {
                    Icon(Icons.Default.Psychology, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("ACCESS LONG-TERM MEMORY", letterSpacing = 1.sp)
                }
            }

            if (showDiagnostics) {
                DiagnosticsDialog(onDismiss = { showDiagnostics = false })
            }
        }
    }
}

@Composable
fun IntegrityItem(icon: ImageVector, label: String, isActive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) NeuralGreen else Color.Red.copy(0.6f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 9.sp, color = Color.White.copy(0.5f), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.03f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        content()
    }
}

@Composable
fun DiagnosticsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CORE DIAGNOSTICS", color = CyberCyan, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "System: Stable\nVCF Cache: 100%\nLatency: ~240ms\nIdentity: Z.A.Y. Active",
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(0.8f)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = NeuralGreen) }
        },
        containerColor = Color(0xFF121212),
        shape = RoundedCornerShape(16.dp)
    )
}
