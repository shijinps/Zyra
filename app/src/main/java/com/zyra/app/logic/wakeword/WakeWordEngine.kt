package com.zyra.app.logic.wakeword

interface WakeWordListener {
    fun onWakeWordDetected(keywordIndex: Int = 0)
    fun onError(error: Throwable, isFatal: Boolean, readableMessage: String)
}

interface WakeWordEngine {
    val name: String
    val isRunning: Boolean
    fun initialize(listener: WakeWordListener): Result<Unit>
    fun start()
    fun stop()
    fun destroy()
}
