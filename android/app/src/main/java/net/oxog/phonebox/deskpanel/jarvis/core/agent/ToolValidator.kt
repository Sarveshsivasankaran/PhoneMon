package net.oxog.phonebox.deskpanel.jarvis.core.agent

object ToolValidator {
    private val ALLOWED_MEDIA_ACTIONS = setOf(
        "play", "pause", "play_pause", "next", "previous", "volume_up", "volume_down", "mute", "set_volume"
    )
    private val ALLOWED_ADB_ACTIONS = setOf(
        "reconnect", "devices", "open_panel", "wake_screen", "sleep_screen", "volume_up", "volume_down", "mute", "play_pause", "home", "back"
    )
    private val ALLOWED_VIAS = setOf("auto", "adb", "usb", "tailscale", "wifi")

    fun validateToolCall(toolName: String, params: Map<String, Any?>): Boolean {
        ToolRegistry.findTool(toolName) ?: return false

        val via = params["via"]?.toString()?.lowercase()
        if (via != null && via !in ALLOWED_VIAS) return false
        val coreParams = params.filterKeys { it != "via" }

        val id = coreParams["id"] as? String
        val validId = id != null && id.length in 1..100 && id !in setOf(".", "..") && '/' !in id
        when (toolName) {
            "panel_status" -> return coreParams["section"] in setOf("apps","processes","docker","discord","steam","batteries","home")
            "open_app", "toggle_entity" -> return validId
            "launch_game" -> return validId && (coreParams["widget"] as? String)?.matches(Regex("[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+")) == true
            "discord_action" -> return coreParams["action"] in setOf("mute","deafen","inputmode","leave") || (coreParams["action"] == "join" && validId)
            "docker_action" -> return validId && coreParams["action"] in setOf("start","stop","restart")
            "control_media" -> {
                val action = coreParams["action"]?.toString()?.lowercase() ?: return false
                if (action == "set_volume") return coreParams["level"]?.toString()?.toIntOrNull() in 0..100
                return action in ALLOWED_MEDIA_ACTIONS
            }
            "adb_action" -> {
                val action = coreParams["action"]?.toString()?.lowercase() ?: return false
                val serial = coreParams["serial"] as? String
                val serialOk = serial == null || serial.matches(Regex("^[A-Za-z0-9.:_-]{1,64}$"))
                return action in ALLOWED_ADB_ACTIONS && serialOk
            }
            "send_notification" -> {
                val title = coreParams["title"]?.toString() ?: ""
                val msg = coreParams["message"]?.toString() ?: ""
                return title.isNotBlank() && msg.isNotBlank() && title.length <= 100 && msg.length <= 500
            }
            "switch_panel_page" -> {
                val target = coreParams["target"]?.toString() ?: ""
                return target.isNotBlank() && target.length <= 50
            }
            "list_controls", "list_shortcuts", "obs_status", "get_pc_metrics", "network_links", "wake_pc" -> return coreParams.isEmpty()
            "pause_rules" -> return coreParams["minutes"]?.toString()?.toIntOrNull() in 0..1440
            "set_brightness" -> return coreParams["id"] is String && coreParams["value"]?.toString()?.toIntOrNull() in 0..100
            "spotify_status" -> return coreParams.isEmpty()
            "spotify_action" -> return coreParams["action"] == "favorite" || (coreParams["action"] in setOf("play-queued","playlist") && coreParams["id"] is String)
            "desk_command" -> return coreParams["kind"] in setOf("action","profile","device","session","default","duck","sleep","desktop","power-plan","lock","monitor-off","sound-stop","panic")
                && (coreParams["value"] == null || coreParams["value"]?.toString()?.toIntOrNull() in -1..360)
                && (coreParams["muted"] == null || coreParams["muted"] is Boolean)
            "obs_action" -> return when (coreParams["action"]) {
                "scene","transition" -> coreParams["id"] is String
                "volume" -> coreParams["id"] is String && coreParams["value"]?.toString()?.toIntOrNull() in 0..100
                "mute","source" -> coreParams["id"] is String && coreParams["enabled"] is Boolean
                "duration" -> coreParams["value"]?.toString()?.toIntOrNull() in 50..10000
                else -> coreParams["action"] in setOf("record","pause","stream","replay","save-replay","camera")
            }
            "execute_custom_command" -> {
                val commandId = coreParams["command_id"]?.toString() ?: ""
                return commandId.matches(Regex("^[a-zA-Z0-9_-]{1,64}$"))
            }
        }
        return false
    }
}

