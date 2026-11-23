package com.phonebox.jarvis.core.agent

import android.util.Log
import com.phonebox.jarvis.core.ai.JarvisState
import com.phonebox.jarvis.core.ai.PhoneInference
import com.phonebox.jarvis.core.ai.ReasoningModelSpec
import com.phonebox.jarvis.core.memory.MemoryManager
import com.phonebox.jarvis.core.voice.TextToSpeechEngine
import com.phonebox.jarvis.integrations.phonemon.PhoneMonAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Coordinates end-to-end user requests, local Qwen3 reasoning,
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
        _currentStreamingText.value = description
        _currentState.value = JarvisState.STANDBY
    }

    fun setState(state: JarvisState) {
        _currentState.value = state
    }

    fun handleUserInput(input: String, isVoice: Boolean = false) {
        if (input.isBlank()) return

        scope.launch {
            _currentState.value = JarvisState.THINKING
            _currentStreamingText.value = ""
            memoryManager.recordTurn("USER", input, isSpoken = isVoice)

            processModelOutput(PhoneInference.answer(input), isVoice)
        }
    }

    private suspend fun processModelOutput(output: String, isVoice: Boolean) {
        val toolCall = extractToolCall(output)
        if (toolCall != null) {
            val (toolName, params) = toolCall
            if (ToolValidator.validateToolCall(toolName, params)) {
                if (permissionManager.requiresExplicitUserApproval(toolName)) {
                    _currentState.value = JarvisState.STANDBY
                    _pendingApprovalTool.value = Pair(toolName, params)
                } else {
                    executeTool(toolName, params, isVoice)
                }
            } else {
                val errorMsg = "Tool call rejected by security validator."
                finishTurn(errorMsg, isVoice)
            }
        } else {
            finishTurn(output, isVoice)
        }
    }

    fun approvePendingTool() {
        val pending = _pendingApprovalTool.value ?: return
        _pendingApprovalTool.value = null
        scope.launch {
            executeTool(pending.first, pending.second, isVoice = true)
        }
    }

    fun rejectPendingTool() {
        _pendingApprovalTool.value = null
        _currentState.value = JarvisState.STANDBY
        _currentStreamingText.value = "Action cancelled by user."
    }

    private suspend fun executeTool(toolName: String, params: Map<String, Any?>, isVoice: Boolean) {
        _currentState.value = JarvisState.EXECUTING
        var resultText = ""

        when (toolName) {
            "get_pc_metrics" -> {
                val metrics = phoneMonAdapter.getSystemMetrics()
                resultText = "Computer metrics retrieved: $metrics"
            }
            "control_media" -> {
                val action = params["action"]?.toString() ?: "play_pause"
                val ok = phoneMonAdapter.triggerMediaAction(action)
                resultText = if (ok) "Media command '$action' executed." else "Failed sending media action."
            }
            "send_notification" -> {
                val title = params["title"]?.toString() ?: "JARVIS"
                val msg = params["message"]?.toString() ?: ""
                val ok = phoneMonAdapter.sendNotification(title, msg)
                resultText = if (ok) "Notification sent to PC." else "Could not dispatch notification."
            }
            else -> {
                resultText = "Tool $toolName is unavailable."
            }
        }

        permissionManager.logToolExecution(toolName, JSONObject(params).toString(), resultText, true)
        finishTurn(resultText, isVoice)
    }

    private suspend fun finishTurn(reply: String, isVoice: Boolean) {
        _currentStreamingText.value = reply
        memoryManager.recordTurn("ASSISTANT", reply, isSpoken = isVoice)

        if (isVoice) {
            _currentState.value = JarvisState.SPEAKING
            ttsEngine.speak(reply)
        } else {
            _currentState.value = JarvisState.STANDBY
        }
    }

    private fun extractToolCall(text: String): Pair<String, Map<String, Any?>>? {
        val start = text.indexOf("{\"tool\":")
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

