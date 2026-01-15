package com.zyra.app.service

import android.content.Context
import android.service.voice.VoiceInteractionSession
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.zyra.app.logic.ZyraState
import com.zyra.app.ui.SiriWaveAnimation
import kotlinx.coroutines.delay

class ZyraSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreateContentView(): View {
        return ComposeView(context).apply {
            setContent {
                SessionContent()
            }
        }
    }

    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // This is called when the user triggers the assistant (e.g. Long Press Home)
        // We should start the VoiceAssistantService or trigger listening here
        // For now, let's just show the animation and maybe start the service if not running
    }

    override fun onHide() {
        super.onHide()
        // Assistant dismissed
    }
}

@Composable
fun SessionContent() {
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = 50.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        SiriWaveAnimation(
            state = ZyraState.LISTENING, // Default to listening when triggered via Assist gesture
            modifier = Modifier.size(300.dp)
        )
    }
}
