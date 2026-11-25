package net.oxog.phonebox.deskpanel.jarvis.core.agent

internal sealed class VoiceIntent {
    data class DirectTool(val tool: String, val params: Map<String, Any?>, val spokenSummary: String? = null) : VoiceIntent()
    data class OpenTarget(val query: String, val preferShortcut: Boolean, val via: String?) : VoiceIntent()
    data class SwitchPage(val query: String, val via: String?) : VoiceIntent()
    data class SetBrightness(val level: Int, val via: String?) : VoiceIntent()
    data class DockerAction(val action: String, val query: String, val via: String?) : VoiceIntent()
}

/**
 * Deterministic voice/text router for system, ADB, and Tailscale controls so clear
 * commands execute reliably over USB/ADB or Tailscale/Wi-Fi without depending on
 * fragile 0.5B JSON formatting.
 */
internal object VoiceCommandRouter {
    private val VIA_REGEX = Regex("""\b(?:using|via|over|through|on)\s+(adb|usb|tailscale|tail\s+scale|wi-?fi)\b""", RegexOption.IGNORE_CASE)
    private val FILLER_PREFIX = Regex("""^(?:please\s+|can\s+you\s+|could\s+you\s+|would\s+you\s+|jarvis[,\s]*)+""", RegexOption.IGNORE_CASE)
    private val LINKS_STATUS_REGEX = Regex(
        """^(?:(?:check|show|get|test|what(?:'s|\s+is))\s+(?:the\s+|my\s+)?(?:adb|tailscale|tail\s+scale)(?:\s+(?:(?:and|or|&)\s+)?(?:adb|tailscale|tail\s+scale))?(?:\s+(?:status|links?|connections?))?|(?:the\s+|my\s+)?(?:adb|tailscale|tail\s+scale)(?:\s+(?:(?:and|or|&)\s+)?(?:adb|tailscale|tail\s+scale))?\s+(?:status|links?|connections?)|(?:check|show|get)\s+(?:the\s+)?(?:connection|network|links?)|(?:network|connection|link)\s+status)$"""
    )
    private val VOLUME_LEVEL_REGEX = Regex("""^(?:set|change|make|put)?\s*(?:the\s+)?(?:pc\s+|computer\s+|system\s+)?volume(?:\s+level)?(?:\s+to|\s+at)?\s+(\d{1,3})\s*(?:percent|%)?$""")
    private val BRIGHTNESS_REGEX = Regex("""^(?:set|change|dim|adjust)?\s*(?:the\s+)?(?:monitor\s+|screen\s+)?brightness(?:\s+to|\s+at)?\s+(\d{1,3})\s*(?:percent|%)?$""")
    private val PAUSE_RULES_REGEX = Regex("""^pause\s+(?:the\s+)?rules(?:\s+for\s+(\d{1,4})\s*(?:min|mins|minutes?)?)?$""")
    private val DOCKER_REGEX = Regex("""^(start|stop|restart)\s+(?:the\s+)?(?:docker\s+)?container\s+(.+)$""")
    private val PAGE_REGEX = Regex("""^(?:go\s+to|switch\s+to|show|open)\s+(?:the\s+)?(.+?)\s+page$""")
    private val SWITCH_PAGE_ALT_REGEX = Regex("""^(?:go\s+to|switch\s+to)\s+page\s+(.+)$""")
    private val SHORTCUT_REGEX = Regex("""^(?:run|execute|trigger)\s+(?:the\s+)?(?:shortcut|macro|workflow)\s+(.+)$""")
    private val OPEN_REGEX = Regex("""^(?:open|launch|start|run)\s+(?:the\s+)?(?:app\s+|application\s+|program\s+)?(.+)$""")

    fun route(rawInput: String): VoiceIntent? {
        val (cleaned, via) = stripRouteAndFillers(rawInput)
        if (cleaned.isEmpty()) return null
        val paramsBase = if (via != null) mapOf<String, Any?>("via" to via) else emptyMap()

        // ADB & Tailscale inspection and control
        if (LINKS_STATUS_REGEX.matches(cleaned)) {
            return VoiceIntent.DirectTool("network_links", paramsBase)
        }
        if (cleaned in setOf("adb devices", "list adb devices", "show adb devices", "connected phones", "list devices")) {
            return VoiceIntent.DirectTool("adb_action", paramsBase + ("action" to "devices"))
        }
        if (cleaned in setOf("reconnect adb", "fix adb", "refresh adb", "connect adb", "start adb reverse")) {
            return VoiceIntent.DirectTool("adb_action", paramsBase + ("action" to "reconnect"))
        }
        if (cleaned in setOf("adb open panel", "open panel via adb", "open panel on phone", "launch panel")) {
            return VoiceIntent.DirectTool("adb_action", paramsBase + ("action" to "open_panel"))
        }
        if (cleaned in setOf("adb wake phone", "wake phone screen", "wake phone")) {
            return VoiceIntent.DirectTool("adb_action", paramsBase + ("action" to "wake_screen"))
        }
        if (cleaned in setOf("adb sleep phone", "sleep phone screen", "turn off phone screen")) {
            return VoiceIntent.DirectTool("adb_action", paramsBase + ("action" to "sleep_screen"))
        }

        // Wake-on-LAN
        if (cleaned in setOf("wake pc", "wake computer", "wake my pc", "wake my computer", "turn on pc", "turn on computer")) {
            return VoiceIntent.DirectTool("wake_pc", emptyMap())
        }

        // PC telemetry & status
        if (cleaned in setOf(
                "pc status", "show pc status", "show pc metrics", "system status",
                "cpu usage", "show cpu usage", "what is my cpu usage", "how is my pc",
                "ram usage", "memory usage", "gpu temperature", "gpu usage"
            )
        ) {
            return VoiceIntent.DirectTool("get_pc_metrics", paramsBase)
        }
        if (cleaned in setOf("list shortcuts", "show shortcuts", "show my shortcuts")) {
            return VoiceIntent.DirectTool("list_shortcuts", paramsBase)
        }
        if (cleaned in setOf("obs status", "show obs status")) {
            return VoiceIntent.DirectTool("obs_status", paramsBase)
        }
        if (cleaned in setOf("spotify status", "what is playing", "whats playing", "current song", "current track")) {
            return VoiceIntent.DirectTool("spotify_status", paramsBase)
        }

        // Exact volume percentage
        VOLUME_LEVEL_REGEX.matchEntire(cleaned)?.let { match ->
            val level = match.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: return null
            return VoiceIntent.DirectTool("control_media", paramsBase + mapOf("action" to "set_volume", "level" to level))
        }

        // Volume & audio controls
        val mediaAction = when (cleaned) {
            "volume up", "turn up volume", "turn up the volume", "increase volume", "louder", "raise volume" -> "volume_up"
            "volume down", "turn down volume", "turn down the volume", "decrease volume", "quieter", "lower volume" -> "volume_down"
            "mute", "unmute", "mute audio", "mute pc", "mute computer", "toggle mute" -> "mute"
            "play music", "play media", "resume music", "resume playback" -> "play"
            "pause music", "pause media", "pause playback", "stop music" -> "pause"
            "play pause", "play or pause", "toggle playback" -> "play_pause"
            "next song", "next track", "skip track", "skip song", "next" -> "next"
            "previous song", "previous track", "last song", "last track", "previous" -> "previous"
            else -> null
        }
        if (mediaAction != null) {
            return VoiceIntent.DirectTool("control_media", paramsBase + ("action" to mediaAction))
        }

        // Screen lock, monitor, virtual desktop, panic mute, sound stop
        val deskKind: Pair<String, Map<String, Any?>>? = when (cleaned) {
            "lock pc", "lock computer", "lock screen", "lock my pc", "lock my computer", "lock the screen" ->
                "lock" to emptyMap()
            "monitor off", "turn off monitor", "turn off monitors", "turn off screen", "turn off the monitor", "screen off" ->
                "monitor-off" to emptyMap()
            "stop sound", "stop radio", "stop audio", "stop playing sound" ->
                "sound-stop" to emptyMap()
            "panic mute", "mute microphone", "mute mic", "mute all" ->
                "panic" to emptyMap()
            "next desktop", "switch desktop right", "desktop right" ->
                "desktop" to mapOf("value" to 1)
            "previous desktop", "switch desktop left", "desktop left" ->
                "desktop" to mapOf("value" to -1)
            else -> null
        }
        if (deskKind != null) {
            val nice = when (deskKind.first) {
                "lock" -> "Locked the computer"
                "monitor-off" -> "Turned off the monitors"
                "sound-stop" -> "Stopped sound playback"
                "panic" -> "Muted microphones and audio devices"
                "desktop" -> "Switched virtual desktop"
                else -> "Completed ${deskKind.first}"
            }
            return VoiceIntent.DirectTool("desk_command", paramsBase + mapOf("kind" to deskKind.first) + deskKind.second, nice)
        }

        // Spotify & OBS & Discord shortcuts
        when (cleaned) {
            "favorite song", "like song", "like this song", "favorite track" ->
                return VoiceIntent.DirectTool("spotify_action", paramsBase + ("action" to "favorite"), "Favorited current Spotify track")
            "start recording", "record obs" ->
                return VoiceIntent.DirectTool("obs_action", paramsBase + mapOf("action" to "record", "enabled" to true), "Toggled OBS recording")
            "stop recording" ->
                return VoiceIntent.DirectTool("obs_action", paramsBase + mapOf("action" to "record", "enabled" to false), "Stopped OBS recording")
            "start streaming", "start stream" ->
                return VoiceIntent.DirectTool("obs_action", paramsBase + mapOf("action" to "stream", "enabled" to true), "Started OBS stream")
            "stop streaming", "stop stream" ->
                return VoiceIntent.DirectTool("obs_action", paramsBase + mapOf("action" to "stream", "enabled" to false), "Stopped OBS stream")
            "save replay", "save clip" ->
                return VoiceIntent.DirectTool("obs_action", paramsBase + ("action" to "save-replay"), "Saved OBS replay")
            "mute discord" ->
                return VoiceIntent.DirectTool("discord_action", paramsBase + ("action" to "mute"), "Toggled Discord mute")
            "deafen discord" ->
                return VoiceIntent.DirectTool("discord_action", paramsBase + ("action" to "deafen"), "Toggled Discord deafen")
            "leave discord", "leave voice", "leave voice channel" ->
                return VoiceIntent.DirectTool("discord_action", paramsBase + ("action" to "leave"), "Left Discord voice channel")
            "resume rules", "unpause rules" ->
                return VoiceIntent.DirectTool("pause_rules", paramsBase + ("minutes" to 0), "Resumed panel rules")
        }

        PAUSE_RULES_REGEX.matchEntire(cleaned)?.let { match ->
            val mins = match.groupValues[1].toIntOrNull()?.coerceIn(0, 1440) ?: 15
            return VoiceIntent.DirectTool("pause_rules", paramsBase + ("minutes" to mins), "Paused rules for $mins minutes")
        }

        BRIGHTNESS_REGEX.matchEntire(cleaned)?.let { match ->
            val level = match.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: return null
            return VoiceIntent.SetBrightness(level, via)
        }

        DOCKER_REGEX.matchEntire(cleaned)?.let { match ->
            return VoiceIntent.DockerAction(match.groupValues[1], match.groupValues[2].trim(), via)
        }

        PAGE_REGEX.matchEntire(cleaned)?.let { match ->
            return VoiceIntent.SwitchPage(match.groupValues[1].trim(), via)
        }
        SWITCH_PAGE_ALT_REGEX.matchEntire(cleaned)?.let { match ->
            return VoiceIntent.SwitchPage(match.groupValues[1].trim(), via)
        }

        SHORTCUT_REGEX.matchEntire(cleaned)?.let { match ->
            return VoiceIntent.OpenTarget(match.groupValues[1].trim(), preferShortcut = true, via = via)
        }

        OPEN_REGEX.matchEntire(cleaned)?.let { match ->
            val target = match.groupValues[1].trim()
            if (target.isNotEmpty() && target !in setOf("settings", "camera")) {
                return VoiceIntent.OpenTarget(target, preferShortcut = false, via = via)
            }
        }

        return null
    }

    internal fun stripRouteAndFillers(raw: String): Pair<String, String?> {
        var via: String? = null
        val match = VIA_REGEX.find(raw)
        if (match != null) {
            val token = match.groupValues[1].lowercase().replace(" ", "").replace("-", "")
            via = when (token) {
                "adb", "usb" -> "adb"
                "tailscale", "wifi" -> "tailscale"
                else -> null
            }
        }
        var text = VIA_REGEX.replace(raw, " ")
            .trim()
            .trimEnd('.', '!', '?', ',')
        text = FILLER_PREFIX.replace(text, "").trim()
        text = text.replace(Regex("""\s+(?:please|thanks|thank\s+you)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .lowercase()
            .trim()
        return text to via
    }

    internal fun bestNameMatch(query: String, candidates: List<Pair<String, String>>): Pair<String, String>? {
        val q = query.trim().lowercase().replace(Regex("""[^a-z0-9\s]"""), "")
        if (q.isEmpty()) return null
        val qTokens = q.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        return candidates
            .mapNotNull { (id, label) ->
                val cleanLabel = label.lowercase().replace(Regex("""[^a-z0-9\s]"""), "")
                val cleanId = id.lowercase().replace(Regex("""[^a-z0-9\s]"""), "")
                val score = when {
                    cleanId == q || cleanLabel == q -> 100
                    cleanLabel.startsWith(q) || cleanId.startsWith(q) -> 85
                    cleanLabel.contains(q) || cleanId.contains(q) -> 70
                    qTokens.isNotEmpty() && qTokens.all { tok -> cleanLabel.contains(tok) || cleanId.contains(tok) } -> 60
                    else -> 0
                }
                if (score > 0) Triple(score, id, label) else null
            }
            .maxByOrNull { it.first }
            ?.let { it.second to it.third }
    }
}

