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

        val id = params["id"] as? String
        val validId = id != null && id.length in 1..100 && id !in setOf(".", "..") && '/' !in id
        when (toolName) {
            "panel_status" -> return params["section"] in setOf("apps","processes","docker","discord","steam","batteries","home")
            "open_app", "toggle_entity" -> return validId
            "launch_game" -> return validId && (params["widget"] as? String)?.matches(Regex("[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+")) == true
            "discord_action" -> return params["action"] in setOf("mute","deafen","inputmode","leave") || (params["action"] == "join" && validId)
            "docker_action" -> return validId && params["action"] in setOf("start","stop","restart")
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
            "list_controls", "list_shortcuts", "obs_status", "get_pc_metrics" -> return params.isEmpty()
            "pause_rules" -> return params["minutes"]?.toString()?.toIntOrNull() in 0..1440
            "set_brightness" -> return params["id"] is String && params["value"]?.toString()?.toIntOrNull() in 0..100
            "spotify_status" -> return params.isEmpty()
            "spotify_action" -> return params["action"] == "favorite" || (params["action"] in setOf("play-queued","playlist") && params["id"] is String)
            "desk_command" -> return params["kind"] in setOf("action","profile","device","session","default","duck","sleep","desktop","power-plan","lock","monitor-off","sound-stop","panic")
                && (params["value"] == null || params["value"]?.toString()?.toIntOrNull() in -1..360)
                && (params["muted"] == null || params["muted"] is Boolean)
            "obs_action" -> return when (params["action"]) {
                "scene","transition" -> params["id"] is String
                "volume" -> params["id"] is String && params["value"]?.toString()?.toIntOrNull() in 0..100
                "mute","source" -> params["id"] is String && params["enabled"] is Boolean
                "duration" -> params["value"]?.toString()?.toIntOrNull() in 50..10000
                else -> params["action"] in setOf("record","pause","stream","replay","save-replay","camera")
            }
            "execute_custom_command" -> {
                val commandId = params["command_id"]?.toString() ?: ""
                // Allow only alphanumeric identifiers
                return commandId.matches(Regex("^[a-zA-Z0-9_-]{1,64}$"))
            }
        }
        return false
    }
}

