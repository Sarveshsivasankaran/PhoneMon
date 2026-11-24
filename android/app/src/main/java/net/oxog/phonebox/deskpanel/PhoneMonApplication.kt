package net.oxog.phonebox.deskpanel

import android.app.Application
import net.oxog.phonebox.deskpanel.jarvis.database.JarvisDatabase

class PhoneMonApplication : Application() {
    val database: JarvisDatabase by lazy { JarvisDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: PhoneMonApplication
            private set
    }
}

