package com.phonebox.jarvis.integrations.phonemon

import android.content.pm.PackageManager
import android.net.Uri
import com.phonebox.jarvis.JarvisApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PhoneMonAdapter {
    suspend fun call(tool: String, params: Map<String, Any?> = emptyMap()): String = withContext(Dispatchers.IO) {
        val app = JarvisApplication.instance
        val authority = "net.oxog.phonebox.deskpanel.jarvis"
        val provider = app.packageManager.resolveContentProvider(authority, 0) ?: error("Install the updated PhoneMon panel")
        check(app.packageManager.checkSignatures(app.packageName, provider.packageName) == PackageManager.SIGNATURE_MATCH) {
            "Install both apps from the same PhoneMon release"
        }
        val reply = app.contentResolver.call(Uri.parse("content://$authority"), tool, JSONObject(params).toString(), null)
            ?: error("Pair the desk panel with the computer")
        val code = reply.getInt("code")
        check(code in 200..299) { "PhoneMon returned HTTP $code" }
        reply.getString("body").orEmpty()
    }
}
