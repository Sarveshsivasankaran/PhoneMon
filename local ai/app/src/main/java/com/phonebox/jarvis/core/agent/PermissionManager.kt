package com.phonebox.jarvis.core.agent

import com.phonebox.jarvis.database.AuditLogEntity
import com.phonebox.jarvis.database.JarvisDao

/**
 * Manages tool execution permissions and security auditing.
 */
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

