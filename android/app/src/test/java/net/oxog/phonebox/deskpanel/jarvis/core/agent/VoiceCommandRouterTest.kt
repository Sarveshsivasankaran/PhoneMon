package net.oxog.phonebox.deskpanel.jarvis.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandRouterTest {
    @Test
    fun `routes adb and tailscale system commands with transport override`() {
        val links = VoiceCommandRouter.route("check adb and tailscale status via tailscale") as VoiceIntent.DirectTool
        assertEquals("network_links", links.tool)
        assertEquals("tailscale", links.params["via"])

        val reconnect = VoiceCommandRouter.route("Jarvis, please reconnect adb") as VoiceIntent.DirectTool
        assertEquals("adb_action", reconnect.tool)
        assertEquals("reconnect", reconnect.params["action"])
    }

    @Test
    fun `routes volume level, wake, lock, and monitor commands`() {
        val vol = VoiceCommandRouter.route("set volume to 65 percent using adb") as VoiceIntent.DirectTool
        assertEquals("control_media", vol.tool)
        assertEquals("set_volume", vol.params["action"])
        assertEquals(65, vol.params["level"])
        assertEquals("adb", vol.params["via"])

        val lock = VoiceCommandRouter.route("lock my pc over tailscale") as VoiceIntent.DirectTool
        assertEquals("desk_command", lock.tool)
        assertEquals("lock", lock.params["kind"])
        assertEquals("tailscale", lock.params["via"])

        val monitor = VoiceCommandRouter.route("turn off monitor") as VoiceIntent.DirectTool
        assertEquals("desk_command", monitor.tool)
        assertEquals("monitor-off", monitor.params["kind"])

        val wake = VoiceCommandRouter.route("wake computer") as VoiceIntent.DirectTool
        assertEquals("wake_pc", wake.tool)
        assertTrue(wake.params.isEmpty())
    }

    @Test
    fun `routes apps, shortcuts, pages, brightness, docker, and ignores open questions`() {
        val app = VoiceCommandRouter.route("open Spotify") as VoiceIntent.OpenTarget
        assertEquals("spotify", app.query)
        assertEquals(false, app.preferShortcut)

        val shortcut = VoiceCommandRouter.route("run shortcut Morning Routine over wifi") as VoiceIntent.OpenTarget
        assertEquals("morning routine", shortcut.query)
        assertEquals(true, shortcut.preferShortcut)
        assertEquals("tailscale", shortcut.via)

        val page = VoiceCommandRouter.route("go to monitoring page") as VoiceIntent.SwitchPage
        assertEquals("monitoring", page.query)

        val bright = VoiceCommandRouter.route("set brightness to 40 percent") as VoiceIntent.SetBrightness
        assertEquals(40, bright.level)

        val docker = VoiceCommandRouter.route("restart container postgres over adb") as VoiceIntent.DockerAction
        assertEquals("restart", docker.action)
        assertEquals("postgres", docker.query)
        assertEquals("adb", docker.via)

        assertNull(VoiceCommandRouter.route("what is the weather today"))
        assertEquals(
            "spotify_app" to "Spotify",
            VoiceCommandRouter.bestNameMatch("spotify", listOf("chrome" to "Google Chrome", "spotify_app" to "Spotify"))
        )
    }
}

