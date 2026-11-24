package com.phonebox.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val panel = "net.oxog.phonebox.deskpanel"
        if (packageManager.checkSignatures(packageName, panel) == PackageManager.SIGNATURE_MATCH) {
            startActivity(Intent().setClassName(panel, "$panel.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("showAssistant", true))
            finish()
        } else {
            AlertDialog.Builder(this).setTitle("PhoneMon desk panel")
                .setMessage("Install the PhoneMon panel APK from the same release to open your assistant.")
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .setOnCancelListener { finish() }.show()
        }
    }
}
