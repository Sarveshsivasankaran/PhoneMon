package net.oxog.phonebox.deskpanel.jarvis.core.agent

data class ToolDefinition(
    val name: String,
    val description: String,
    val requiresApproval: Boolean = false,
    val parametersSchemaJson: String
)

object ToolRegistry {
    val GET_PC_METRICS = ToolDefinition(
        name = "get_pc_metrics",
        description = "Retrieves CPU, RAM, GPU, storage and network telemetry from the desktop agent.",
        requiresApproval = false,
        parametersSchemaJson = "{}"
    )

    val CONTROL_MEDIA = ToolDefinition(
        name = "control_media",
        description = "Controls media playback on the computer (play, pause, next, prev, volume_up, volume_down).",
        requiresApproval = false,
        parametersSchemaJson = "{\"action\": \"string\"}"
    )

    val SWITCH_PANEL_PAGE = ToolDefinition(
        name = "switch_panel_page",
        description = "Switches the active desk panel page on the phone (page_index or template_name).",
        requiresApproval = false,
        parametersSchemaJson = "{\"target\": \"string\"}"
    )

    val SEND_NOTIFICATION = ToolDefinition(
        name = "send_notification",
        description = "Sends a notification to the panel.",
        requiresApproval = true,
        parametersSchemaJson = "{\"title\": \"string\", \"message\": \"string\"}"
    )

    val EXECUTE_CUSTOM_COMMAND = ToolDefinition(
        name = "execute_custom_command",
        description = "Executes an approved pre-configured desktop workflow. Destructive operations require explicit approval.",
        requiresApproval = true,
        parametersSchemaJson = "{\"command_id\": \"string\"}"
    )

    val ALL_TOOLS = listOf(
        ToolDefinition("list_controls", "List desk controls and their state", false, "{}"),
        ToolDefinition("list_shortcuts", "Read the public panel configuration and shortcut IDs", false, "{}"),
        ToolDefinition("obs_status", "Read OBS state", false, "{}"),
        ToolDefinition("obs_action", "OBS scene, transition, duration, volume, mute, source, record, pause, stream, replay, save-replay or camera", true, """{"action":"string","id":"optional OBS ID","value":"optional integer","enabled":"optional boolean"}"""),
        ToolDefinition("pause_rules", "Pause rules for minutes, zero resumes", true, """{"minutes":"integer"}"""),
        ToolDefinition("set_brightness", "Adjust a listed monitor brightness from 0 to 100", false, """{"id":"monitor ID","value":"integer"}"""),
        ToolDefinition("desk_command", "Operate a configured action or profile, device/session volume, audio duck, sleep timer, desktop switch, power plan, screen lock, monitor off, or stop sound", true, """{"kind":"action|profile|device|session|default|duck|sleep|desktop|power-plan|lock|monitor-off|sound-stop|panic","id":"optional control ID","op":"optional operation","value":"optional integer","muted":"optional boolean"}"""),
        ToolDefinition("spotify_status", "Read the Spotify queue and active track", false, "{}"),
        ToolDefinition("spotify_action", "Favorite the current track, play a queued track or configured playlist", true, """{"action":"favorite|play-queued|playlist","id":"optional ID"}"""),
        ToolDefinition("panel_status", "List items from a panel section", false, """{"section":"apps|processes|docker|discord|steam|batteries|home","ids":"optional comma-separated home entity IDs"}"""),
        ToolDefinition("open_app", "Open a listed desktop application", true, """{"id":"app ID"}"""),
        ToolDefinition("toggle_entity", "Toggle a Home Assistant entity enabled for the panel", true, """{"id":"entity ID"}"""),
        ToolDefinition("docker_action", "Control a listed container", true, """{"id":"container ID","action":"start|stop|restart"}"""),
        ToolDefinition("discord_action", "Change Discord voice settings or channel", true, """{"action":"mute|deafen|inputmode|join|leave","id":"channel ID for join"}"""),
        ToolDefinition("launch_game", "Launch a listed Steam game through its enabled widget", true, """{"id":"game ID","widget":"pageId/widgetId"}"""),
        GET_PC_METRICS,
        CONTROL_MEDIA,
        SWITCH_PANEL_PAGE,
        SEND_NOTIFICATION,
        EXECUTE_CUSTOM_COMMAND
    )

    // Small local models do better when only relevant tools appear in the prompt.
    private val domains = mapOf(
        "panel_status" to "apps processes docker containers discord steam games batteries battery home entities",
        "open_app" to "app application open launch",
        "toggle_entity" to "home entity light switch",
        "docker_action" to "docker container",
        "discord_action" to "discord",
        "launch_game" to "steam game",
        "get_pc_metrics" to "cpu gpu memory ram disk network metrics telemetry temperature",
        "control_media" to "music song track playback player media mute volume pause resume next previous",
        "switch_panel_page" to "page panel dashboard",
        "send_notification" to "notify notification",
        "execute_custom_command" to "shortcut workflow macro command",
        "list_shortcuts" to "shortcut shortcuts workflow workflows macro macros",
        "list_controls" to "controls devices mixer profile monitor",
        "set_brightness" to "brightness dim brighten",
        "desk_command" to "brightness sound audio profile lock monitor desktop power duck timer configured control",
        "obs_action" to "obs stream record scene replay transition recording",
        "obs_status" to "obs stream recording",
        "pause_rules" to "rule rules automation automations",
        "spotify_action" to "spotify playlist queue",
        "spotify_status" to "spotify playlist queue"
    ).mapValues { it.value.split(' ').toSet() }

    fun forRequest(prompt: String): List<ToolDefinition> {
        val words = Regex("[a-z0-9]+").findAll(prompt.lowercase()).map { it.value }.toSet()
        return ALL_TOOLS.filter { tool -> domains[tool.name]?.any { it in words } == true }
    }

    fun findTool(name: String): ToolDefinition? = ALL_TOOLS.find { it.name.equals(name, ignoreCase = true) }
}

