package com.phonebox.jarvis.core.agent


/**
 * Validates tool invocations, ensuring parameters conform to strict constraints
 * and disallowing arbitrary or unauthenticated system commands.
 */
object ToolValidator {

    private val ALLOWED_MEDIA_ACTIONS = setOf(
        "play", "pause", "play_pause", "next", "previous", "volume_up", "volume_down", "mute"
    )

    fun validateToolCall(toolName: String, params: Map<String, Any?>): Boolean {
        val tool = ToolRegistry.findTool(toolName)
        if (tool == null) {
            return false
        }

        when (toolName) {
            "control_media" -> {
                val action = params["action"]?.toString()?.lowercase() ?: return false
                return action in ALLOWED_MEDIA_ACTIONS
            }
            "send_notification" -> {
                val title = params["title"]?.toString() ?: ""
                val msg = params["message"]?.toString() ?: ""
                return title.isNotBlank() && msg.isNotBlank() && title.length <= 100 && msg.length <= 500
            }
            "switch_panel_page" -> {
                val target = params["target"]?.toString() ?: ""
                return target.isNotBlank() && target.length <= 50
            }
            "get_pc_metrics" -> return true
            "execute_custom_command" -> {
                val commandId = params["command_id"]?.toString() ?: ""
                // Allow only alphanumeric identifiers
                return commandId.matches(Regex("^[a-zA-Z0-9_-]{1,64}$"))
            }
        }
        return false
    }
}

