package com.zyra.app

import android.app.Application

class ZyraApp : Application() {
    
    val database by lazy { com.zyra.app.data.brain.BrainDatabase.getDatabase(this) }
    val brainRepository by lazy { com.zyra.app.data.brain.BrainRepository(database.brainDao()) }

    companion object {
        lateinit var instance: ZyraApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
