package net.oxog.phonebox.deskpanel.jarvis.core.agent

import net.oxog.phonebox.deskpanel.jarvis.database.AuditLogEntity
import net.oxog.phonebox.deskpanel.jarvis.database.JarvisDao

class PermissionManager(private val dao: JarvisDao) {
    fun requiresExplicitUserApproval(toolName: String): Boolean {
        val tool = ToolRegistry.findTool(toolName)
        return tool?.requiresApproval ?: true
    }

    suspend fun logToolExecution(
        toolName: String,
        argumentsJson: String,
        resultJson: String,
        isApproved: Boolean
    ) {
        dao.insertAuditLog(
            AuditLogEntity(
                toolName = toolName,
                argumentsJson = argumentsJson,
                resultJson = resultJson,
                isApproved = isApproved
            )
        )
    }
}

