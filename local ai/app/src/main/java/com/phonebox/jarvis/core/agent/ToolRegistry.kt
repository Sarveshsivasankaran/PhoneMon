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
        description = "Sends a visual notification banner to the desktop screen.",
        requiresApproval = false,
        parametersSchemaJson = "{\"title\": \"string\", \"message\": \"string\"}"
    )

    val EXECUTE_CUSTOM_COMMAND = ToolDefinition(
        name = "execute_custom_command",
        description = "Executes an approved pre-configured desktop workflow. Destructive operations require explicit approval.",
        requiresApproval = true,
        parametersSchemaJson = "{\"command_id\": \"string\"}"
    )

    val ALL_TOOLS = listOf(
        GET_PC_METRICS,
        CONTROL_MEDIA,
        SWITCH_PANEL_PAGE,
        SEND_NOTIFICATION,
        EXECUTE_CUSTOM_COMMAND
    )

    fun findTool(name: String): ToolDefinition? = ALL_TOOLS.find { it.name.equals(name, ignoreCase = true) }
}

