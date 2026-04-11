package com.zyra.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigateToHome: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "quantum_core")
    
    // Rotation Animations
    val rotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "ring_rotate_1"
    )
    val rotation2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "ring_rotate_2"
    )
    val rotation3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "ring_rotate_3"
    )

    // Pulse Animation for the Central Core
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    // Text tracking and Alpha animation
    var textVisible by remember { mutableStateOf(false) }
    val textAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(2000),
        label = "text_alpha"
    )
    val letterSpacing by animateFloatAsState(
        targetValue = if (textVisible) 8f else 0f,
        animationSpec = tween(2500, easing = LinearOutSlowInEasing),
        label = "letter_spacing"
    )

    LaunchedEffect(Unit) {
        delay(500)
        textVisible = true
        delay(3500) // Total splash duration
        onNavigateToHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Deep Radial Background Glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF001A33),
                            Color.Black
                        ),
                        radius = 1500f
                    )
                )
        )

        // Quantum Core Canvas
        Canvas(modifier = Modifier.size(300.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            
            // 1. Outer Ring (Slow, Cyan)
            withTransform({
                rotate(rotation1, center)
            }) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color(0xFF00E5FF), Color.Transparent, Color(0xFF00E5FF)),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 280f,
                    useCenter = false,
                    style = Stroke(width = 4f, cap = StrokeCap.Round),
                    size = Size(size.width * 0.9f, size.height * 0.9f),
                    topLeft = Offset(size.width * 0.05f, size.height * 0.05f)
                )
            }

            // 2. Middle Ring (Reverse, Neon Green)
            withTransform({
                rotate(rotation2, center)
            }) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color(0xFF00FFCC), Color.Transparent, Color(0xFF00FFCC)),
                        center = center
                    ),
                    startAngle = 120f,
                    sweepAngle = 200f,
                    useCenter = false,
                    style = Stroke(width = 6f, cap = StrokeCap.Round),
                    size = Size(size.width * 0.7f, size.height * 0.7f),
                    topLeft = Offset(size.width * 0.15f, size.height * 0.15f)
                )
            }

            // 3. Inner Kinetic Ring (Fast, White/Blue)
            withTransform({
                rotate(rotation3, center)
            }) {
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White, Color(0xFF00E5FF)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    startAngle = 240f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 2f, cap = StrokeCap.Round),
                    size = Size(size.width * 0.5f, size.height * 0.5f),
                    topLeft = Offset(size.width * 0.25f, size.height * 0.25f)
                )
            }

            // 4. Glowing Neural Center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00E5FF), Color.Transparent),
                    center = center,
                    radius = (40f * pulseScale)
                ),
                radius = 60f * pulseScale,
                center = center
            )
        }

        // Animated Brand Typography
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ZYRA",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    letterSpacing = letterSpacing.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color(0xFF00E5FF).copy(alpha = 0.5f),
                        blurRadius = 40f
                    )
                ),
                modifier = Modifier.alpha(textAlpha)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "NEURAL OS INTERFACE",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF00FFCC).copy(alpha = 0.6f),
                    letterSpacing = 4.sp
                ),
                modifier = Modifier.alpha(textAlpha * 0.7f)
            )
        }
    }
}
