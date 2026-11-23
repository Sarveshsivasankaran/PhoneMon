package com.phonebox.jarvis.core.agent

/**
 * Metadata and specifications for approved tools.
 */
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
        GET_PC_METRICS,
        CONTROL_MEDIA,
        SWITCH_PANEL_PAGE,
        SEND_NOTIFICATION,
        EXECUTE_CUSTOM_COMMAND
    )

    fun findTool(name: String): ToolDefinition? = ALL_TOOLS.find { it.name.equals(name, ignoreCase = true) }
}

