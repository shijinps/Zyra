package com.zyra.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.zyra.app.logic.ZyraState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

@Composable
fun SiriWaveAnimation(
    state: ZyraState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            ZyraState.IDLE -> IdleOrb()
            ZyraState.LISTENING -> ListeningOrb()
            ZyraState.PROCESSING -> ProcessingOrb()
            ZyraState.SPEAKING -> SpeakingOrb()
            ZyraState.CALLING -> CallingOrb()
        }
    }
}

@Composable
fun IdleOrb() {
    val infiniteTransition = rememberInfiniteTransition(label = "idle_orb")
    
    // Pulse Animation
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_scale"
    )
    
    // Rotation Animation for Rings
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val baseRadius = (size.minDimension / 2) * 0.4f
        
        // 1. Outer Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.3f),
                    Color.Transparent
                )
            ),
            radius = baseRadius * 2.5f,
            center = center
        )

        // 2. Rotating Outer Ring (Deep Blue)
        drawArc(
            color = Color(0xFF00B0FF).copy(alpha = 0.6f),
            startAngle = rotation,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(center.x - baseRadius * 1.4f, center.y - baseRadius * 1.4f),
            size = androidx.compose.ui.geometry.Size(baseRadius * 2.8f, baseRadius * 2.8f),
            style = Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )

        // 3. Rotating Inner Ring (Cyan, Counter-Clockwise)
        drawArc(
            color = Color(0xFF00E5FF).copy(alpha = 0.8f),
            startAngle = -rotation * 1.5f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(center.x - baseRadius * 1.1f, center.y - baseRadius * 1.1f),
            size = androidx.compose.ui.geometry.Size(baseRadius * 2.2f, baseRadius * 2.2f),
            style = Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        
        // 4. Central Breathing Core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White,
                    Color(0xFF00E5FF).copy(alpha = 0.5f),
                    Color.Transparent
                )
            ),
            radius = baseRadius * scale,
            center = center
        )
    }
}

@Composable
fun ListeningOrb() {
    val infiniteTransition = rememberInfiniteTransition(label = "listening_orb")
    
    // Main phase for movement
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "phase"
    )
    
    // Breathing amplitude (Expansion/Contraction)
    val amplitude by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amp"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        // JARVIS / Arc Reactor Colors:
        // Cyan, Deep Blue, Light Blue, White
        val waves = listOf(
            Triple(Color(0xFF00B0FF), 1.0f, 0.0f),    // Deep Blue (Base)
            Triple(Color(0xFF00E5FF), 1.2f, 1.5f),    // Cyan (Main)
            Triple(Color(0xFF80D8FF), 0.8f, 3.0f),    // Light Blue (Slow)
            Triple(Color(0xFF0091EA), 1.1f, 4.5f)     // Azure (Offset)
        )
        
        waves.forEachIndexed { index, (color, freqMult, phaseOffset) ->
            val path = Path()
            path.moveTo(0f, centerY)
            
            val stepPx = 4f
            var x = 0f
            while (x <= width) {
                val normalizedX = x / width
                val envelope = sin(normalizedX * Math.PI).toFloat() // Taper at ends
                
                // Complex Interference Pattern:
                // y = sin(kx - wt) + sin(kx * 1.5 - wt * 0.5) 
                // This creates "knots" and "loops" in the wave visual
                val wave1 = sin(normalizedX * 4 * Math.PI + phase * freqMult + phaseOffset)
                
                val y = centerY + (wave1 * height * 0.35f * amplitude * envelope).toFloat()
                
                if (x == 0f) path.moveTo(x, y)
                else path.lineTo(x, y)
                
                x += stepPx
            }
            path.lineTo(width, centerY)

            // Draw Glow (Wide, Low Alpha)
            drawPath(
                path = path,
                color = color.copy(alpha = 0.3f),
                style = Stroke(width = 12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            
            // Draw Core (Thin, High Alpha)
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

@Composable
fun ProcessingOrb() {
    val infiniteTransition = rememberInfiniteTransition(label = "processing_orb")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "rot"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 3f
        
        with(drawContext.canvas.nativeCanvas) {
            save()
            rotate(rotation, center.x, center.y)
            
            // Draw a spinning galaxy-like structure
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF00E5FF),
                        Color.Transparent,
                        Color(0xFFD500F9),
                        Color.Transparent,
                        Color(0xFF00E5FF)
                    )
                ),
                radius = radius,
                center = center
            )
            
            restore()
        }
        
        // Center core
        drawCircle(
             color = Color.White.copy(alpha = 0.9f),
             radius = radius * 0.2f,
             center = center
        )
    }
}

@Composable
fun SpeakingOrb() {
    // Simulate voice amplitude
    val infiniteTransition = rememberInfiniteTransition(label = "speaking_orb")
    val amplitude by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = FastOutSlowInEasing), // Fast pulse
            repeatMode = RepeatMode.Reverse
        ),
        label = "amp"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val baseRadius = size.minDimension / 3f
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD600).copy(alpha = 0.6f), // Gold for Speaking
                    Color(0xFFFF6D00).copy(alpha = 0.3f), // Orange
                    Color.Transparent
                )
            ),
            radius = baseRadius * amplitude,
            center = center
        )
        
        // Inner intense core
        drawCircle(
            color = Color.White,
            radius = baseRadius * 0.4f,
            center = center
        )
    }
}

@Composable
fun CallingOrb() {
    val infiniteTransition = rememberInfiniteTransition(label = "calling_orb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "call_pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val baseRadius = size.minDimension / 3f
        
        // Green Glow for Call
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00C853).copy(alpha = 0.6f), // Green
                    Color(0xFF64DD17).copy(alpha = 0.3f), // Lime
                    Color.Transparent
                )
            ),
            radius = baseRadius * pulse,
            center = center
        )
        
        // Phone Icon Abstraction (Two simple circles or just green core)
        drawCircle(
            color = Color.White,
            radius = baseRadius * 0.4f,
            center = center
        )
    }
}
