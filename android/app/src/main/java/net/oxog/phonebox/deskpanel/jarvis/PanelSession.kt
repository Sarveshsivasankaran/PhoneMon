package net.oxog.phonebox.deskpanel.jarvis

import android.graphics.Bitmap
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.*
import net.oxog.phonebox.deskpanel.PhoneMonApplication
import net.oxog.phonebox.deskpanel.jarvis.core.agent.AgentOrchestrator
import net.oxog.phonebox.deskpanel.jarvis.core.agent.PermissionManager
import net.oxog.phonebox.deskpanel.jarvis.core.ai.*
import net.oxog.phonebox.deskpanel.jarvis.core.memory.MemoryManager
import net.oxog.phonebox.deskpanel.jarvis.core.voice.TextToSpeechEngine
import net.oxog.phonebox.deskpanel.jarvis.integrations.phonemon.PhoneMonAdapter
import org.json.JSONObject

internal object PanelSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val adapter = PhoneMonAdapter()
    private val speech: TextToSpeechEngine by lazy {
        TextToSpeechEngine(
            PhoneMonApplication.instance,
            { setState(JarvisState.SPEAKING) },
            { setState(JarvisState.STANDBY) },
            { setState(JarvisState.ERROR) }
        )
    }
    private val agent: AgentOrchestrator by lazy {
        val dao = PhoneMonApplication.instance.database.jarvisDao()
        AgentOrchestrator(scope, MemoryManager(dao), PermissionManager(dao), adapter, speech)
    }
    private fun setState(state: JarvisState) { agent.setState(state) }
    private var user = ""
    private var thumbnail = ""
    private var nextSync = 0L
    private var sync: Job? = null
    private var install: Job? = null
    private var lastSent = ""
    private var nextHeartbeat = 0L
    private var heartbeat: Job? = null

    fun capture(frame: Bitmap?, error: String?) {
        if (frame == null) { agent.showVisionResult(error ?: "Front camera unavailable."); return }
        val preview = Bitmap.createScaledBitmap(frame, 160, maxOf(1, frame.height * 160 / frame.width), true)
        val bytes = java.io.ByteArrayOutputStream()
        preview.compress(Bitmap.CompressFormat.JPEG, 65, bytes)
        thumbnail = "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP)
        if (preview !== frame) preview.recycle()
        user = "Describe the scene in view."
        setState(JarvisState.VISION)
        scope.launch {
            try { agent.showVisionResult(PhoneInference.answer(user, frame)) }
            finally { frame.recycle() }
        }
    }

    fun request(method: String, value: String): String =
        if (Looper.myLooper() == Looper.getMainLooper()) {
            requestOnMain(method, value)
        } else {
            runBlocking { withContext(Dispatchers.Main.immediate) { requestOnMain(method, value) } }
        }

    private fun requestOnMain(method: String, value: String): String {
        val now = SystemClock.elapsedRealtime()
        if (now >= nextSync && sync?.isActive != true) {
            nextSync = now + 10000
            sync = scope.launch { runCatching { AssistantProfile.sync() } }
        }
        val busy = agent.currentState.value.isBusy() || agent.pendingApprovalTool.value != null
        when (method) {
            "previewVoice" -> if (!busy) agent.previewVoice(value.ifBlank { AssistantProfile.current().optString("voice", "slt") })
            "text", "voice" -> if (!busy && value.isNotBlank() && value.length <= 2000) {
                user = value
                agent.handleUserInput(value, method == "voice")
            }
            "listening" -> if (!busy) setState(JarvisState.LISTENING)
            "idle" -> if (agent.currentState.value == JarvisState.LISTENING) setState(JarvisState.STANDBY)
            "approve", "deny" -> if (value.isNotEmpty() && value == pending()) {
                if (method == "approve") agent.approvePendingTool() else agent.rejectPendingTool()
            }
            "install" -> if (value in setOf("qwen2.5-0.5b", "lfm2.5-vl-450m") && install?.isActive != true) {
                install = scope.launch { PhoneRuntime.install(value) }
            }
            "clearThumbnail" -> thumbnail = ""
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
        return JSONObject().put("name", AssistantProfile.name.value).put("state", state)
            .put("thumbnail", thumbnail).put("user", user).put("text", reply).put("pending", pending())
            .put("setup", PhoneRuntime.status.value).put("installing", install?.isActive == true).toString()
    }

    fun stopSpeech() {
        scope.launch {
            speech.stop()
            if (agent.currentState.value == JarvisState.SPEAKING || agent.currentState.value == JarvisState.LISTENING) {
                setState(JarvisState.STANDBY)
            }
        }
    }

    private fun pending() = agent.pendingApprovalTool.value?.let {
        JSONObject().put("tool", it.first).put("parameters", JSONObject(it.second)).toString()
    }.orEmpty()
}
