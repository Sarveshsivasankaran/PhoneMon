package com.phonebox.jarvis

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import com.phonebox.jarvis.core.agent.AgentOrchestrator
import com.phonebox.jarvis.core.agent.PermissionManager
import com.phonebox.jarvis.core.ai.*
import com.phonebox.jarvis.core.memory.MemoryManager
import com.phonebox.jarvis.core.voice.TextToSpeechEngine
import com.phonebox.jarvis.integrations.phonemon.PhoneMonAdapter
import kotlinx.coroutines.*
import org.json.JSONObject

internal object PanelSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val adapter = PhoneMonAdapter()
    private val agent: AgentOrchestrator by lazy {
        val app = JarvisApplication.instance
        val dao = app.database.jarvisDao()
        val speech = TextToSpeechEngine(app, { setState(JarvisState.SPEAKING) }, { setState(JarvisState.STANDBY) })
        AgentOrchestrator(scope, MemoryManager(dao), PermissionManager(dao), adapter, speech)
    }
    private fun setState(state: JarvisState) { agent.setState(state) }
    private var user = ""
    private var nextSync = 0L
    private var sync: Job? = null
    private var install: Job? = null
    private var lastSent = ""
    private var nextHeartbeat = 0L
    private var heartbeat: Job? = null

    fun capture(frame: Bitmap?, error: String?) {
        if (frame == null) { agent.showVisionResult(error ?: "Front camera unavailable."); return }
        user = "Describe the scene in view."
        setState(JarvisState.VISION)
        scope.launch {
            try { agent.showVisionResult(PhoneInference.answer(user, frame)) }
            finally { frame.recycle() }
        }
    }

    fun request(method: String, value: String): String {
        val now = SystemClock.elapsedRealtime()
        if (now >= nextSync && sync?.isActive != true) {
            nextSync = now + 10000
            sync = scope.launch { runCatching { AssistantProfile.sync() } }
        }
        val busy = agent.currentState.value.isBusy() || agent.pendingApprovalTool.value != null
        when (method) {
            "text", "voice" -> if (!busy && value.isNotBlank() && value.length <= 2000) {
                user = value
                agent.handleUserInput(value, method == "voice")
            }
            "listening" -> if (!busy) setState(JarvisState.LISTENING)
            "idle" -> if (agent.currentState.value == JarvisState.LISTENING) setState(JarvisState.STANDBY)
            "approve", "deny" -> if (value.isNotEmpty() && value == pending()) {
                if (method == "approve") agent.approvePendingTool() else agent.rejectPendingTool()
            }
            "install" -> if (value in setOf("qwen2.5-0.5b","lfm2.5-vl-450m") && install?.isActive != true) {
                install = scope.launch { PhoneRuntime.install(value) }
            }
            "status" -> Unit
        }
        val state = agent.currentState.value.name
        val reply = agent.currentStreamingText.value.take(16000)
        val signature = state + reply.take(2000) + user
        if ((lastSent != signature || now >= nextHeartbeat) && heartbeat?.isActive != true) {
            nextHeartbeat = now + 5000
            lastSent = signature
            heartbeat = scope.launch { runCatching { adapter.call("session", mapOf("state" to state, "text" to reply.take(2000), "user" to user)) } }
        }
        return JSONObject().put("name", AssistantProfile.name.value).put("state",state)
            .put("user",user).put("text",reply).put("pending",pending())
            .put("setup",PhoneRuntime.status.value).put("installing",install?.isActive == true).toString()
    }

    private fun pending() = agent.pendingApprovalTool.value?.let {
        JSONObject().put("tool",it.first).put("parameters",JSONObject(it.second)).toString()
    }.orEmpty()
}

class PanelSessionProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val pm = requireNotNull(context).packageManager
        check(pm.checkSignatures(Process.myUid(), Binder.getCallingUid()) == PackageManager.SIGNATURE_MATCH)
        val result = runBlocking { withContext(Dispatchers.Main.immediate) { PanelSession.request(method, arg.orEmpty()) } }
        return Bundle().apply { putString("session",result) }
    }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sortOrder: String?): Cursor? = null
}
