package net.oxog.phonebox.deskpanel.jarvis.integrations.phonemon

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.oxog.phonebox.deskpanel.AgentLink
import net.oxog.phonebox.deskpanel.PhoneMonApplication
import org.json.JSONObject

class PhoneMonAdapter {
    suspend fun call(tool: String, params: Map<String, Any?> = emptyMap()): String = withContext(Dispatchers.IO) {
        val data = JSONObject(params)
        val activeLink = attachedLink ?: fallbackLink
        val viaPref = data.optString("via", "").lowercase()
        val preferredRoute = when (viaPref) {
            "adb", "usb" -> AgentLink.Route.USB
            "tailscale", "wifi" -> AgentLink.Route.WIFI
            else -> null
        }
        if (tool == "wake_pc") {
            val sent = activeLink.wakeActivePc()
            return@withContext if (sent) "Sent Wake-on-LAN packet to the paired computer." else "Could not wake the computer. Make sure the phone is on the computer's network and paired."
        }
        var verb = "GET"
        var body: ByteArray? = null
        fun post(path: String): String { verb = "POST"; return path }
        fun segment(key: String): String {
            val value = data.getString(key)
            require(value.length in 1..100 && value != "." && value != ".." && '/' !in value)
            return Uri.encode(value)
        }
        val path = when (tool) {
            "session" -> {
                body = JSONObject()
                    .put("state", data.getString("state"))
                    .put("text", data.getString("text").take(2000))
                    .put("user", data.optString("user").take(2000))
                    .toString().toByteArray()
                post("/api/jarvis/session")
            }
            "panel_status" -> when (data.getString("section")) {
                "apps" -> "/api/apps"
                "processes" -> "/api/procs"
                "docker" -> "/api/docker"
                "discord" -> "/api/discord"
                "steam" -> "/api/steam/library"
                "batteries" -> "/api/batteries"
                "home" -> "/api/ha/states?ids=" + Uri.encode(data.optString("ids"))
                else -> error("Unknown panel section")
            }
            "open_app" -> post("/api/apps/open?id=" + segment("id"))
            "toggle_entity" -> post("/api/ha/toggle/" + segment("id"))
            "docker_action" -> {
                val op = data.getString("action")
                require(op in setOf("start", "stop", "restart"))
                post("/api/docker/" + segment("id") + "/" + op)
            }
            "discord_action" -> {
                val op = data.getString("action")
                require(op in setOf("mute", "deafen", "inputmode", "join", "leave"))
                post("/api/discord/$op" + if (op == "join") "?channel=" + segment("id") else "")
            }
            "launch_game" -> post("/api/steam/launch?id=" + segment("id") + "&widget=" + Uri.encode(data.getString("widget")))
            "profile" -> "/api/jarvis/profile"
            "get_pc_metrics" -> "/api/state"
            "list_controls" -> "/api/controls/state"
            "list_shortcuts" -> "/api/jarvis/catalog"
            "network_links" -> "/api/jarvis/links"
            "adb_action" -> {
                val uri = Uri.parse("/api/jarvis/adb").buildUpon()
                    .appendQueryParameter("action", data.getString("action").lowercase())
                val serial = data.optString("serial")
                if (serial.isNotBlank()) uri.appendQueryParameter("serial", serial)
                post(uri.build().toString())
            }
            "obs_status" -> "/api/controls/obs"
            "execute_custom_command" -> post("/api/run/" + segment("command_id"))
            "switch_panel_page" -> post("/api/goto/" + segment("target"))
            "pause_rules" -> post("/api/rules/pause?minutes=" + data.getInt("minutes").also { require(it in 0..1440) })
            "spotify_status" -> "/api/controls/spotify/state"
            "spotify_action", "desk_command", "obs_action", "set_brightness" -> {
                val (prefix, action) = when (tool) {
                    "spotify_action" -> "/api/controls/spotify/action/" to data.getString("action").also {
                        require(it in setOf("favorite", "play-queued", "playlist"))
                    }
                    "obs_action" -> "/api/controls/obs/" to data.getString("action").also {
                        require(it in setOf("scene", "transition", "duration", "volume", "mute", "source", "record", "pause", "stream", "replay", "save-replay", "camera"))
                    }
                    "set_brightness" -> "/api/controls/command/" to "brightness"
                    else -> "/api/controls/command/" to data.getString("kind").also {
                        require(it in setOf("action", "profile", "device", "session", "default", "duck", "sleep", "desktop", "power-plan", "lock", "monitor-off", "sound-stop", "panic"))
                    }
                }
                val uri = Uri.parse(prefix + action).buildUpon()
                for (key in listOf("id", "op", "value", "enabled", "muted")) {
                    if (data.has(key)) uri.appendQueryParameter(key, data.get(key).toString().also { require(it.length <= 256) })
                }
                post(uri.build().toString())
            }
            "send_notification" -> {
                body = JSONObject().put("title", data.getString("title")).put("text", data.getString("message")).toString().toByteArray()
                post("/api/notify")
            }
            "control_media" -> when (val op = data.getString("action").lowercase()) {
                "volume_up", "volume_down", "mute" -> post("/api/volume?op=" + op.removePrefix("volume_"))
                "set_volume" -> post("/api/volume?level=" + data.getInt("level").coerceIn(0, 100))
                "play_pause" -> post("/api/media/playpause")
                "previous" -> post("/api/media/prev")
                "next", "pause", "play" -> post("/api/media/$op")
                else -> error("Unknown media action")
            }
            else -> error("Unknown tool")
        }

        val result = activeLink.request(verb, path, body, if (body != null) "application/json" else null, panelRequest = false, preferredRoute = preferredRoute)
        val rawBody = result.body.bufferedReader().use { reader ->
            val chars = CharArray(16000)
            val count = reader.readBlock(chars)
            String(chars, 0, count)
        }
        check(result.code in 200..299) {
            if (result.code == 503) {
                when (preferredRoute) {
                    AgentLink.Route.USB -> "USB or ADB connection is unavailable."
                    AgentLink.Route.WIFI -> "Tailscale or Wi-Fi connection is unreachable."
                    else -> "Pair the desk panel with the computer."
                }
            } else {
                val err = runCatching { JSONObject(rawBody).optString("message").ifBlank { JSONObject(rawBody).optString("error") } }.getOrDefault("")
                err.ifBlank { "PhoneMon returned HTTP ${result.code}" }
            }
        }
        val routeLabel = describeRoute(activeLink)
        if (tool == "get_pc_metrics") {
            val pc = JSONObject(rawBody).getJSONObject("pc")
            return@withContext buildString {
                append("CPU ").append(pc.optString("cpu", "unavailable")).append("%")
                append(" · RAM ").append(pc.optString("ramUsedGb", "?")).append(" / ").append(pc.optString("ramTotalGb", "?")).append(" GB")
                when (val gpu = pc.opt("gpu")) {
                    is Number -> append(" · GPU ").append(gpu).append("%")
                    is JSONObject -> {
                        val details = listOfNotNull(
                            gpu.optString("name").takeIf { !gpu.isNull("name") && it.isNotBlank() },
                            (gpu.opt("util") as? Number)?.let { "$it% utilization" },
                            (gpu.opt("temp") as? Number)?.let { "$it degrees Celsius" }
                        )
                        if (details.isNotEmpty()) append(" · GPU ").append(details.joinToString(", "))
                    }
                }
                append(" (").append(routeLabel).append(')')
            }
        }
        if (tool == "network_links") {
            return@withContext formatLinksSummary(rawBody, routeLabel)
        }
        if (tool == "adb_action") {
            val msg = runCatching { JSONObject(rawBody).optString("message") }.getOrDefault("")
            return@withContext if (msg.isNotBlank()) "$msg ($routeLabel)" else "Done ($routeLabel)."
        }
        if (tool == "control_media" && viaPref.isNotBlank()) {
            val act = data.getString("action").replace('_', ' ')
            return@withContext if (act == "set volume") "Volume set to ${data.optInt("level")} percent ($routeLabel)."
            else "Executed $act ($routeLabel)."
        }
        rawBody
    }

    private fun describeRoute(link: AgentLink): String = when (link.route) {
        AgentLink.Route.USB -> "via ADB"
        AgentLink.Route.WIFI -> {
            val host = link.lastHost ?: ""
            if (isTailscaleIp(host)) "via Tailscale" else "via Wi-Fi"
        }
        AgentLink.Route.NONE -> "offline"
    }

    private fun isTailscaleIp(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 100 && second in 64..127
    }

    private fun formatLinksSummary(json: String, routeLabel: String): String {
        val obj = JSONObject(json)
        val adbDevs = obj.optJSONArray("adbDevices")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val tsIps = obj.optJSONArray("tailscaleAddresses")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val tsState = obj.optString("tailscaleState", "")
        val tsPeers = obj.optInt("tailscaleOnlinePeers", 0)
        return buildString {
            append("Active link is ").append(routeLabel).append(". ")
            if (adbDevs.isNotEmpty()) append("ADB connected to ").append(adbDevs.joinToString(", ")).append(". ")
            else if (obj.optBoolean("adbAvailable")) append("ADB is ready with no device attached. ")
            else append("ADB is not installed on the computer. ")
            if (tsIps.isNotEmpty()) {
                append("Tailscale is active at ").append(tsIps.first())
                if (tsPeers > 0) append(" with ").append(tsPeers).append(" online peers")
                append(".")
            } else if (tsState.isNotBlank()) {
                append("Tailscale state is ").append(tsState).append(".")
            } else {
                append("Tailscale interface was not detected.")
            }
        }.trim()
    }

    private fun java.io.Reader.readBlock(buffer: CharArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val count = read(buffer, offset, buffer.size - offset)
            if (count <= 0) break
            offset += count
        }
        return offset
    }

    companion object {
        @Volatile
        private var attachedLink: AgentLink? = null

        private val fallbackLink by lazy { AgentLink(PhoneMonApplication.instance) }

        fun attach(link: AgentLink) {
            attachedLink = link
        }

        fun detach(link: AgentLink) {
            if (attachedLink === link) {
                attachedLink = null
            }
        }
    }
}

