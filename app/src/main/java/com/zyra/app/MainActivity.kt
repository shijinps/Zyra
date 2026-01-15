package com.zyra.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zyra.app.ui.HomeScreen
import com.zyra.app.ui.SplashScreen
import com.zyra.app.service.VoiceAssistantService
import com.zyra.app.data.ContactRepository
import com.zyra.app.logic.CommandManager
import com.zyra.app.ui.FeedbackScreen
import androidx.compose.runtime.remember
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import com.zyra.app.worker.WatchdogWorker
import com.zyra.app.ui.theme.ZyraTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Handle logic when permissions are granted/denied
        checkOverlayPermission()
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            // User denied permission or went back
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request "Extreme" Battery Whitelist
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        // Enable Lock Screen Access (Show over Keyguard)
        setupLockScreen()

        // Request Default Digital Assistant Role (Crucial for Background Mic)
        requestAssistantRole()
        
        // Schedule Background Watchdog (Every 15 minutes)
        val watchdogRequest = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ZyraWatchdog",
            ExistingPeriodicWorkPolicy.KEEP,
            watchdogRequest
        )

        requestPermissions()

        val contactRepository = ContactRepository(this)
        val commandManager = CommandManager(this)

        setContent {
            ZyraTheme {
                Surface {
                    ZyraAppNavHost(
                        onStartService = { startService(Intent(this, VoiceAssistantService::class.java)) },
                        onStopService = { stopService(Intent(this, VoiceAssistantService::class.java)) },
                        contactRepository = contactRepository,
                        commandManager = commandManager
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.ANSWER_PHONE_CALLS
            )
        )
    }

    private fun requestAssistantRole() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_ASSISTANT)) {
                if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT)) {
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_ASSISTANT)
                    startActivityForResult(intent, 202)
                }
            }
        } else {
             // For older devices, guide to settings
             // startActivity(Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS))
        }
    }

    private fun checkOverlayPermission() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun setupLockScreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }
}

@Composable
fun ZyraAppNavHost(
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    contactRepository: ContactRepository,
    commandManager: CommandManager
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen {
                navController.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
        composable("home") {
            HomeScreen(
                onStartService = onStartService,
                onStopService = onStopService,
                contactRepository = contactRepository,
                commandManager = commandManager,

                onNavigateToFeedback = { navController.navigate("feedback") },
                onNavigateToBrain = { navController.navigate("brain_list") }
            )
        }
        composable("feedback") {
            FeedbackScreen()
        }
        composable("brain_list") {
            com.zyra.app.ui.brain.BrainScreen(
                onAddTask = { navController.navigate("brain_editor") }
            )
        }
        composable("brain_editor") {
            com.zyra.app.ui.brain.CoreBrainTaskEditor(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
