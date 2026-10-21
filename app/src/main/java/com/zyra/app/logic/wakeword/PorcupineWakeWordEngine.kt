package com.zyra.app.logic.wakeword

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import com.zyra.app.BuildConfig
import com.zyra.app.logic.AssetModelManager

class PorcupineWakeWordEngine(private val context: Context) : WakeWordEngine {
    companion object {
        private const val TAG = "PorcupineEngine"
    }

    override val name: String = "Picovoice Porcupine"

    private var porcupineManager: PorcupineManager? = null
    private var _isRunning: Boolean = false
    override val isRunning: Boolean
        get() = _isRunning && porcupineManager != null

    override fun initialize(listener: WakeWordListener): Result<Unit> {
        val accessKey = BuildConfig.PICOVOICE_ACCESS_KEY
        val keyInfo = if (accessKey.isNotEmpty()) {
            "non-empty, length=${accessKey.length}, suffix=...${accessKey.takeLast(4)}"
        } else {
            "empty"
        }

        return try {
            val ppnFile = AssetModelManager.syncWakeWordModels(context)
            if (ppnFile == null || !ppnFile.exists()) {
                throw java.io.FileNotFoundException("zyra.ppn model file could not be extracted to filesDir")
            }

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(ppnFile.absolutePath)
                .setSensitivity(0.7f)
                .build(context, object : PorcupineManagerCallback {
                    override fun invoke(keywordIndex: Int) {
                        Log.d(TAG, "Wake word detected via Porcupine index: $keywordIndex")
                        listener.onWakeWordDetected(keywordIndex)
                    }
                })
            Log.d(TAG, "Porcupine initialized successfully with model: ${ppnFile.absolutePath}")
            Result.success(Unit)
        } catch (e: Exception) {
            porcupineManager = null
            _isRunning = false
            val isFatal = e.javaClass.simpleName.contains("ActivationRefused") ||
                    e.javaClass.simpleName.contains("ActivationKeyException") ||
                    e.javaClass.simpleName.contains("InvalidArgumentException")
            val readableMsg = if (e.javaClass.simpleName.contains("ActivationRefused")) {
                "Wake word key refused"
            } else {
                e.message ?: "Porcupine initialization failed"
            }
            Log.e(TAG, "Porcupine init exception: class=${e.javaClass.name}, message=${e.message}, AccessKey=[$keyInfo]")
            listener.onError(e, isFatal, readableMsg)
            Result.failure(e)
        }
    }

    override fun start() {
        val manager = porcupineManager ?: run {
            Log.w(TAG, "start: manager is null, cannot start")
            return
        }
        if (_isRunning) {
            Log.v(TAG, "start: already running, skipping")
            return
        }
        try {
            manager.start()
            _isRunning = true
            Log.d(TAG, "Porcupine started successfully")
        } catch (e: Exception) {
            _isRunning = false
            Log.e(TAG, "Porcupine start exception: class=${e.javaClass.name}, message=${e.message}", e)
        }
    }

    override fun stop() {
        try {
            porcupineManager?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Porcupine stop exception", e)
        } finally {
            _isRunning = false
        }
    }

    override fun destroy() {
        stop()
        try {
            porcupineManager?.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Porcupine delete exception", e)
        } finally {
            porcupineManager = null
        }
    }
}
