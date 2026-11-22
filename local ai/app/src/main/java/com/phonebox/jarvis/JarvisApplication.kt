package com.phonebox.jarvis

import android.app.Application
import android.util.Log
import com.phonebox.jarvis.core.ai.ModelManager
import com.phonebox.jarvis.database.JarvisDatabase

class JarvisApplication : Application() {

    lateinit var database: JarvisDatabase
        private set

    lateinit var modelManager: ModelManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = JarvisDatabase.getInstance(this)
        modelManager = ModelManager(this)
        Log.i("JarvisApplication", "JARVIS Local AI Application initialized on-device.")
    }

    companion object {
        lateinit var instance: JarvisApplication
            private set
    }
}

