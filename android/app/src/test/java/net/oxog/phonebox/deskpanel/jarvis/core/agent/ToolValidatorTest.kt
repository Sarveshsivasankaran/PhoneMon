package net.oxog.phonebox.deskpanel.jarvis.core.agent

import org.junit.Assert.*
import org.junit.Test

class ToolValidatorTest {

    @Test
    fun testValidMediaActions() {
        assertTrue(ToolValidator.validateToolCall("control_media", mapOf("action" to "play")))
        assertTrue(ToolValidator.validateToolCall("control_media", mapOf("action" to "PAUSE")))
        assertTrue(ToolValidator.validateToolCall("control_media", mapOf("action" to "next")))
        assertFalse(ToolValidator.validateToolCall("control_media", mapOf("action" to "unsupported_action")))
    }

    @Test
    fun testNotificationValidation() {
        assertTrue(ToolValidator.validateToolCall("send_notification", mapOf("title" to "Alert", "message" to "Build complete")))
        assertFalse(ToolValidator.validateToolCall("send_notification", mapOf("title" to "", "message" to "Build complete")))
        assertFalse(ToolValidator.validateToolCall("send_notification", mapOf("title" to "Alert", "message" to "")))
    }

    @Test
    fun testCustomCommandSanitization() {
        assertTrue(ToolValidator.validateToolCall("execute_custom_command", mapOf("command_id" to "clean_build_01")))
        // Injections or special shell characters must be rejected
        assertFalse(ToolValidator.validateToolCall("execute_custom_command", mapOf("command_id" to "rm -rf /; echo")))
        assertFalse(ToolValidator.validateToolCall("execute_custom_command", mapOf("command_id" to "cat /etc/passwd")))
    }

    @Test
    fun testUnknownToolRejection() {
        assertFalse(ToolValidator.validateToolCall("arbitrary_bash_exec", mapOf("cmd" to "ls")))
    }
}

