package com.zyra.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.zyra.app.logic.ZyraState

@Composable
fun SiriWaveAnimation(
    state: ZyraState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "quantum_core_home")
    
    // Core Rotation Animations
    val rotationFast by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rotation_fast"
    )
    val rotationSlow by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "rotation_slow"
    )

    // State-based Pulse
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (state == ZyraState.IDLE) 0.95f else 0.8f,
        targetValue = if (state == ZyraState.IDLE) 1.05f else 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == ZyraState.IDLE) 2000 else 8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Dynamic Colors based on State
    val coreColor = when (state) {
        ZyraState.IDLE -> Color(0xFF00E5FF) // Cyan
        ZyraState.LISTENING -> Color(0xFF00FFCC) // Neon Green
        ZyraState.PROCESSING -> Color(0xFF00B0FF) // Deep Blue
        ZyraState.SPEAKING -> Color(0xFFA200FF) // Purple
        ZyraState.CALLING -> Color(0xFFFF3D00) // Red Alert
        ZyraState.ERROR -> Color(0xFFEF5350) // Crimson Red Error
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            
            // 1. Orbital Ring 1 (Outer)
            withTransform({
                rotate(rotationSlow, center)
            }) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(coreColor.copy(alpha = 0.1f), coreColor.copy(alpha = 0.6f), coreColor.copy(alpha = 0.1f)),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    size = Size(size.width * 0.9f, size.height * 0.9f),
                    topLeft = Offset(size.width * 0.05f, size.height * 0.05f)
                )
            }

            // 2. Orbital Ring 2 (Middle - Counter)
            withTransform({
                rotate(-rotationSlow * 1.5f, center)
            }) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color.Transparent, coreColor.copy(alpha = 0.8f), Color.Transparent),
                        center = center
                    ),
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                    size = Size(size.width * 0.75f, size.height * 0.75f),
                    topLeft = Offset(size.width * 0.125f, size.height * 0.125f)
                )
            }

            // 3. Kinetic Core Ring (Inner)
            withTransform({
                rotate(rotationFast, center)
            }) {
                drawArc(
                    color = coreColor,
                    startAngle = 180f,
                    sweepAngle = 60f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    size = Size(size.width * 0.5f, size.height * 0.5f),
                    topLeft = Offset(size.width * 0.25f, size.height * 0.25f)
                )
            }

            // 4. Glowing Neural Center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor.copy(alpha = 0.9f * pulseScale), Color.Transparent),
                    center = center,
                    radius = (size.minDimension * 0.15f * pulseScale)
                ),
                radius = size.minDimension * 0.15f * pulseScale,
                center = center
            )
        }
    }
}
