package com.zyra.app.ui

import android.content.Context
import android.util.Log
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.zyra.app.logic.ZyraState
import com.zyra.app.service.VoiceAssistantService

class OverlayManager(private val context: Context) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var currentZyraState by mutableStateOf(ZyraState.IDLE)
    
    // Lifecycle components required for Compose in a Service/Window
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun show() {
        if (composeView != null) {
            Log.d("OverlayManager", "show() called but already showing. Update state only.")
            return
        }
        
        Log.d("OverlayManager", "show() called. Creating view.")

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            500, // Height in pixels, adjust as needed or use dp conversion
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@OverlayManager)
            setViewTreeViewModelStoreOwner(this@OverlayManager)
            setViewTreeSavedStateRegistryOwner(this@OverlayManager)
            
            setContent {
                OverlayContent(currentZyraState)
            }
        }

        try {
            windowManager.addView(composeView, params)
            Log.d("OverlayManager", "View added to WindowManager.")
        } catch (e: Exception) {
            Log.e("OverlayManager", "Failed to add view: ${e.message}", e)
            e.printStackTrace()
        }
    }

    fun hide() {
        if (composeView == null) return

        try {
            windowManager.removeView(composeView)
            composeView = null
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateState(state: ZyraState) {
        currentZyraState = state
        // If state is IDLE, we might want to hide automatically after a delay, 
        // but the Service controls the logic usually.
    }
}

@Composable
fun OverlayContent(state: ZyraState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 20.dp), // Lift up slightly
        contentAlignment = Alignment.BottomCenter
    ) {
        // Re-use the existing SiriWaveAnimation
        
        // VISUAL FEEDBACK FOR CALLING
        if (state == ZyraState.CALLING) {
             androidx.compose.material3.Button(
                 onClick = { 
                     // Broadcast Intent to Service to CUT CALL
                     val intent = android.content.Intent(context, VoiceAssistantService::class.java).apply {
                         action = "com.zyra.app.ACTION_CUT_CALL"
                     }
                     context.startService(intent)
                 },
                 colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Red),
                 modifier = Modifier.padding(bottom = 100.dp)
             ) {
                 Text("End Call", color = Color.White)
             }
        }

        SiriWaveAnimation(
            state = state,
            modifier = Modifier.size(300.dp) // Large orb
        )
    }
}
