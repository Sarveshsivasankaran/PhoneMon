package com.phonebox.jarvis.core.agent

import android.util.Log
import com.phonebox.jarvis.core.ai.JarvisState
import com.phonebox.jarvis.core.ai.PhoneInference
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
        scope.launch { finishTurn(description) }
    }

    fun previewVoice(voice: String) {
        if (voice in setOf("slt", "rms", "awb")) {
            ttsEngine.speak("Hello. This is my offline voice.", voice)
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

            val direct = when (input.trim().lowercase().trimEnd('?', '.', '!')) {
                "pc status", "show pc status", "show pc metrics", "show cpu usage", "what is my cpu usage" -> "get_pc_metrics"
                "list shortcuts", "show my shortcuts" -> "list_shortcuts"
                "obs status" -> "obs_status"
                "spotify status" -> "spotify_status"
                else -> null
            }
            if (direct != null) executeTool(direct, emptyMap())
            else processModelOutput(PhoneInference.answer(input))
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

    private suspend fun executeTool(toolName: String, params: Map<String, Any?>) {
        ttsEngine.stop()
        _currentState.value = JarvisState.EXECUTING
        val resultText = try {
            phoneMonAdapter.call(toolName, params).ifBlank { "Completed $toolName." }
        } catch (e: Exception) {
            "Could not complete $toolName: ${e.message}"
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

