package com.zyra.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigateToHome: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1500),
        label = "alpha"
    )
    val scaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1.2f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(3000) // Show Splash for 3 seconds
        onNavigateToHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF000000), Color(0xFF1A237E), Color(0xFF000000))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glowing Background Orb
        Box(
                modifier = Modifier
                    .size(300.dp)
                    .scale(scaleAnim.value * 1.5f)
                    .alpha(0.3f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF00E5FF), Color.Transparent)
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
        )

        // Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Replaced static logo with Dynamic Neural Core Animation
            SiriWaveAnimation(
                state = com.zyra.app.logic.ZyraState.LISTENING, // Active visual
                modifier = Modifier
                    .size(300.dp)
                    .scale(scaleAnim.value)
                    .alpha(alphaAnim.value)
            )
            
            Spacer(modifier = Modifier.height(30.dp))
            
            // App Name Text
            Text(
                text = "ZYRA",
                style = androidx.compose.material3.MaterialTheme.typography.displayLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color.White,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color(0xFF00FFCC), // Neon Green Glow
                        blurRadius = 30f
                    )
                ),
                modifier = Modifier.alpha(alphaAnim.value)
            )
        }
    }
}
