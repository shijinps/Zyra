package com.zyra.app

import android.app.Application

class ZyraApp : Application() {
    
    val database by lazy { com.zyra.app.data.brain.BrainDatabase.getDatabase(this) }
    val brainRepository by lazy { com.zyra.app.data.brain.BrainRepository(database.brainDao()) }

    override fun onCreate() {
        super.onCreate()
    }
}
