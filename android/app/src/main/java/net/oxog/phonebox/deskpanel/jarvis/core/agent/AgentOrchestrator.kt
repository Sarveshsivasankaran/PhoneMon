package net.oxog.phonebox.deskpanel.jarvis.core.agent

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.oxog.phonebox.deskpanel.jarvis.core.ai.AssistantProfile
import net.oxog.phonebox.deskpanel.jarvis.core.ai.JarvisState
import net.oxog.phonebox.deskpanel.jarvis.core.ai.PhoneInference
import net.oxog.phonebox.deskpanel.jarvis.core.memory.MemoryManager
import net.oxog.phonebox.deskpanel.jarvis.core.voice.TextToSpeechEngine
import net.oxog.phonebox.deskpanel.jarvis.integrations.phonemon.PhoneMonAdapter
import org.json.JSONObject

/**
 * Coordinates end-to-end user requests, local reasoning,
 * tool calling, memory logging, and voice synthesis.
 */
class AgentOrchestrator(
    private val scope: CoroutineScope,
    private val memoryManager: MemoryManager,
    private val permissionManager: PermissionManager,
    private val phoneMonAdapter: PhoneMonAdapter,
    private val ttsEngine: TextToSpeechEngine
) {
    private val _currentState = MutableStateFlow(JarvisState.STANDBY)
    val currentState: StateFlow<JarvisState> = _currentState.asStateFlow()

    private val _currentStreamingText = MutableStateFlow("")
    val currentStreamingText: StateFlow<String> = _currentStreamingText.asStateFlow()

    private val _pendingApprovalTool = MutableStateFlow<Pair<String, Map<String, Any?>>?>(null)
    val pendingApprovalTool: StateFlow<Pair<String, Map<String, Any?>>?> = _pendingApprovalTool.asStateFlow()

    fun showVisionResult(description: String) {
        scope.launch { finishTurn(description) }
    }

    fun previewVoice(voice: String) {
        if (voice in AssistantProfile.SUPPORTED_VOICES) {
            ttsEngine.speak("Hello! I am ${AssistantProfile.name.value}, ready to help.", voice)
        }
    }

    fun setState(state: JarvisState) {
        if (state == JarvisState.LISTENING || state == JarvisState.VISION) ttsEngine.stop()
        _currentState.value = state
    }

    fun handleUserInput(input: String, isVoice: Boolean = false) {
        if (input.isBlank()) return

        scope.launch {
            ttsEngine.stop()
            _currentState.value = JarvisState.THINKING
            _currentStreamingText.value = ""
            memoryManager.recordTurn("USER", input, isSpoken = isVoice)

            when (val intent = VoiceCommandRouter.route(input)) {
                is VoiceIntent.DirectTool -> executeTool(intent.tool, intent.params, intent.spokenSummary)
                is VoiceIntent.OpenTarget -> resolveAndOpenTarget(intent.query, intent.preferShortcut, intent.via)
                is VoiceIntent.SwitchPage -> resolveAndSwitchPage(intent.query, intent.via)
                is VoiceIntent.SetBrightness -> resolveAndSetBrightness(intent.level, intent.via)
                is VoiceIntent.DockerAction -> resolveAndRunDocker(intent.action, intent.query, intent.via)
                null -> processModelOutput(PhoneInference.answer(input))
            }
        }
    }

    private suspend fun processModelOutput(output: String) {
        val toolCall = extractToolCall(output)
        if (toolCall != null) {
            val (toolName, params) = toolCall
            if (ToolValidator.validateToolCall(toolName, params)) {
                if (permissionManager.requiresExplicitUserApproval(toolName)) {
                    _pendingApprovalTool.value = Pair(toolName, params)
                    finishTurn("Approve the requested action, " + toolName.replace('_', ' ') + "?")
                } else {
                    executeTool(toolName, params)
                }
            } else {
                val errorMsg = "Tool call rejected by security validator."
                finishTurn(errorMsg)
            }
        } else {
            finishTurn(output)
        }
    }

    fun approvePendingTool() {
        val pending = _pendingApprovalTool.value ?: return
        _pendingApprovalTool.value = null
        scope.launch {
            executeTool(pending.first, pending.second)
        }
    }

    fun rejectPendingTool() {
        if (_pendingApprovalTool.value == null) return
        _pendingApprovalTool.value = null
        scope.launch { finishTurn("Action cancelled.") }
    }

    private suspend fun resolveAndOpenTarget(query: String, preferShortcut: Boolean, via: String?) {
        val viaMap = if (via != null) mapOf("via" to via) else emptyMap()
        _currentState.value = JarvisState.EXECUTING
        val shortcutMatch = runCatching {
            val catalog = JSONObject(phoneMonAdapter.call("list_shortcuts", viaMap))
            val items = catalog.optJSONArray("shortcuts")?.let { a ->
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    o.getString("id") to o.optString("label", o.getString("id"))
                }
            } ?: emptyList()
            VoiceCommandRouter.bestNameMatch(query, items)
        }.getOrNull()

        if (preferShortcut && shortcutMatch != null) {
            executeTool("execute_custom_command", viaMap + ("command_id" to shortcutMatch.first), "Running shortcut ${shortcutMatch.second}")
            return
        }

        val appMatch = runCatching {
            val appsObj = JSONObject(phoneMonAdapter.call("panel_status", viaMap + ("section" to "apps")))
            val items = appsObj.optJSONArray("apps")?.let { a ->
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    o.getString("id") to o.optString("name", o.getString("id"))
                }
            } ?: emptyList()
            VoiceCommandRouter.bestNameMatch(query, items)
        }.getOrNull()

        when {
            appMatch != null -> executeTool("open_app", viaMap + ("id" to appMatch.first), "Opened ${appMatch.second}")
            shortcutMatch != null -> executeTool("execute_custom_command", viaMap + ("command_id" to shortcutMatch.first), "Running shortcut ${shortcutMatch.second}")
            else -> processModelOutput(PhoneInference.answer(if (via != null) "open $query via $via" else "open $query"))
        }
    }

    private suspend fun resolveAndSwitchPage(query: String, via: String?) {
        val viaMap = if (via != null) mapOf("via" to via) else emptyMap()
        _currentState.value = JarvisState.EXECUTING
        val match = runCatching {
            val catalog = JSONObject(phoneMonAdapter.call("list_shortcuts", viaMap))
            val items = catalog.optJSONArray("pages")?.let { a ->
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    o.getString("id") to o.optString("title", o.getString("id"))
                }
            } ?: emptyList()
            VoiceCommandRouter.bestNameMatch(query, items)
        }.getOrNull()
        val targetId = match?.first ?: query.lowercase().replace(' ', '-')
        val label = match?.second ?: query
        executeTool("switch_panel_page", viaMap + ("target" to targetId), "Switched to the $label page")
    }

    private suspend fun resolveAndSetBrightness(level: Int, via: String?) {
        val viaMap = if (via != null) mapOf("via" to via) else emptyMap()
        _currentState.value = JarvisState.EXECUTING
        val monitorId = runCatching {
            val snap = JSONObject(phoneMonAdapter.call("list_controls", viaMap))
            val monitors = snap.optJSONArray("monitors")
            if (monitors != null && monitors.length() > 0) monitors.getJSONObject(0).optString("id") else null
        }.getOrNull()
        if (monitorId.isNullOrBlank()) {
            finishTurn("No controllable desktop monitor was found.")
            return
        }
        executeTool("set_brightness", viaMap + mapOf("id" to monitorId, "value" to level), "Set monitor brightness to $level percent")
    }

    private suspend fun resolveAndRunDocker(action: String, query: String, via: String?) {
        val viaMap = if (via != null) mapOf("via" to via) else emptyMap()
        _currentState.value = JarvisState.EXECUTING
        val match = runCatching {
            val dockerObj = JSONObject(phoneMonAdapter.call("panel_status", viaMap + ("section" to "docker")))
            val items = dockerObj.optJSONArray("containers")?.let { a ->
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    o.optString("id", o.optString("name")) to o.optString("name", o.optString("id"))
                }
            } ?: emptyList()
            VoiceCommandRouter.bestNameMatch(query, items)
        }.getOrNull()
        if (match == null) {
            finishTurn("Could not find Docker container $query.")
            return
        }
        executeTool("docker_action", viaMap + mapOf("id" to match.first, "action" to action), "Executed docker $action on ${match.second}")
    }

    private suspend fun executeTool(toolName: String, params: Map<String, Any?>, spokenSummary: String? = null) {
        ttsEngine.stop()
        _currentState.value = JarvisState.EXECUTING
        val viaNote = params["via"]?.toString()?.takeIf { it.isNotBlank() }?.let { " via ${if (it == "adb") "ADB" else if (it == "tailscale") "Tailscale" else it}" }.orEmpty()
        var succeeded = true
        val rawResult = try {
            phoneMonAdapter.call(toolName, params)
        } catch (e: Exception) {
            succeeded = false
            "Could not complete ${toolName.replace('_', ' ')}: ${e.message}"
        }
        val resultText = if (succeeded && !spokenSummary.isNullOrBlank()) {
            "$spokenSummary$viaNote."
        } else {
            rawResult.ifBlank { "Completed ${toolName.replace('_', ' ')}$viaNote." }
        }

        permissionManager.logToolExecution(toolName, JSONObject(params).toString(), resultText, true)
        finishTurn(resultText)
    }

    private suspend fun finishTurn(reply: String) {
        _currentStreamingText.value = reply
        ttsEngine.speak(reply)
        memoryManager.recordTurn("ASSISTANT", reply, isSpoken = true)
    }

    private fun extractToolCall(text: String): Pair<String, Map<String, Any?>>? {
        val start = text.indexOf('{')
        if (start == -1) return null
        return try {
            val obj = org.json.JSONTokener(text.substring(start)).nextValue() as? JSONObject ?: return null
            val tool = obj.getString("tool")
            val paramsObj = obj.optJSONObject("parameters")
            val params = mutableMapOf<String, Any?>()
            paramsObj?.keys()?.forEach { k ->
                params[k] = paramsObj.get(k)
            }
            Pair(tool, params)
        } catch (e: Exception) {
            Log.w("AgentOrchestrator", "Failed to parse tool JSON: ${e.message}")
            null
        }
    }
}

