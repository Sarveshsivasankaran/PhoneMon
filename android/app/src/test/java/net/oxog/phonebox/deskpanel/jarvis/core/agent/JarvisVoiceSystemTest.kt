package net.oxog.phonebox.deskpanel.jarvis.core.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisVoiceSystemTest {
    @Test
    fun `validates set_volume, network_links, wake_pc, adb_action, and via routes`() {
        assertTrue(ToolValidator.validateToolCall("control_media", mapOf("action" to "set_volume", "level" to 50)))
        assertTrue(ToolValidator.validateToolCall("control_media", mapOf("action" to "set_volume", "level" to 0, "via" to "adb")))
        assertFalse(ToolValidator.validateToolCall("control_media", mapOf("action" to "set_volume", "level" to 120)))

        assertTrue(ToolValidator.validateToolCall("network_links", mapOf("via" to "tailscale")))
        assertFalse(ToolValidator.validateToolCall("network_links", mapOf("via" to "bluetooth")))
        assertFalse(ToolValidator.validateToolCall("network_links", mapOf("extra" to "bad")))

        assertTrue(ToolValidator.validateToolCall("wake_pc", emptyMap()))
        assertTrue(ToolValidator.validateToolCall("wake_pc", mapOf("via" to "wifi")))

        assertTrue(ToolValidator.validateToolCall("adb_action", mapOf("action" to "reconnect")))
        assertTrue(ToolValidator.validateToolCall("adb_action", mapOf("action" to "open_panel", "serial" to "192.168.1.50:5555", "via" to "adb")))
        assertFalse(ToolValidator.validateToolCall("adb_action", mapOf("action" to "rm_rf")))
        assertFalse(ToolValidator.validateToolCall("adb_action", mapOf("action" to "devices", "serial" to "bad serial/with space")))
    }
}

