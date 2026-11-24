package net.oxog.phonebox.deskpanel

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import org.json.JSONObject

/** The paired panel retains its TLS pins and credentials. Only a sibling app signed by
 * the publisher may invoke these named operations. No URL or command comes from the model. */
class JarvisProvider : ContentProvider() {
    private val link by lazy { AgentLink(requireNotNull(context)) }
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val pm = requireNotNull(context).packageManager
        if (pm.checkSignatures(Process.myUid(), Binder.getCallingUid()) != PackageManager.SIGNATURE_MATCH)
            throw SecurityException("PhoneMon signature required")
        val data = JSONObject(arg ?: "{}")
        var verb = "GET"
        var body: ByteArray? = null
        fun post(path: String): String { verb = "POST"; return path }
        fun segment(key: String): String {
            val value = data.getString(key)
            require(value.length in 1..100 && value != "." && value != ".." && '/' !in value)
            return Uri.encode(value)
        }
        val path = when (method) {
            "session" -> {
                body = JSONObject().put("state",data.getString("state")).put("text",data.getString("text").take(2000)).put("user", data.optString("user").take(2000)).toString().toByteArray()
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
                require(op in setOf("start","stop","restart"))
                post("/api/docker/" + segment("id") + "/" + op)
            }
            "discord_action" -> {
                val op = data.getString("action")
                require(op in setOf("mute","deafen","inputmode","join","leave"))
                post("/api/discord/$op" + if (op == "join") "?channel=" + segment("id") else "")
            }
            "launch_game" -> post("/api/steam/launch?id=" + segment("id") + "&widget=" + Uri.encode(data.getString("widget")))
            "profile" -> "/api/jarvis/profile"
            "get_pc_metrics" -> "/api/state"
            "list_controls" -> "/api/controls/state"
            "list_shortcuts" -> "/api/jarvis/catalog"
            "obs_status" -> "/api/controls/obs"
            "execute_custom_command" -> post("/api/run/" + segment("command_id"))
            "switch_panel_page" -> post("/api/goto/" + segment("target"))
            "pause_rules" -> post("/api/rules/pause?minutes=" + data.getInt("minutes").also { require(it in 0..1440) })
            "spotify_status" -> "/api/controls/spotify/state"
            "spotify_action", "desk_command", "obs_action", "set_brightness" -> {
                val (prefix, action) = when (method) {
                    "spotify_action" -> "/api/controls/spotify/action/" to data.getString("action").also {
                        require(it in setOf("favorite","play-queued","playlist"))
                    }
                    "obs_action" -> "/api/controls/obs/" to data.getString("action").also {
                        require(it in setOf("scene","transition","duration","volume","mute","source","record","pause","stream","replay","save-replay","camera"))
                    }
                    "set_brightness" -> "/api/controls/command/" to "brightness"
                    else -> "/api/controls/command/" to data.getString("kind").also {
                        require(it in setOf("action","profile","device","session","default","duck","sleep","desktop","power-plan","lock","monitor-off","sound-stop","panic"))
                    }
                }
                val uri = Uri.parse(prefix + action).buildUpon()
                for (key in listOf("id","op","value","enabled","muted")) {
                    if (data.has(key)) uri.appendQueryParameter(key,data.get(key).toString().also { require(it.length <= 256) })
                }
                post(uri.build().toString())
            }
            "send_notification" -> {
                body = JSONObject().put("title",data.getString("title")).put("text",data.getString("message")).toString().toByteArray()
                post("/api/notify")
            }
            "control_media" -> when (val op = data.getString("action").lowercase()) {
                "volume_up","volume_down","mute" -> post("/api/volume?op=" + op.removePrefix("volume_"))
                "play_pause" -> post("/api/media/playpause")
                "previous" -> post("/api/media/prev")
                "next","pause","play" -> post("/api/media/$op")
                else -> error("Unknown media action")
            }
            else -> error("Unknown tool")
        }
        val result = link.request(verb, path, body, if (body != null) "application/json" else null, panelRequest = false)
        val value = result.body.bufferedReader().use { reader ->
            if (method == "get_pc_metrics" && result.code in 200..299) {
                val pc = JSONObject(reader.readText()).getJSONObject("pc")
                return@use buildString {
                    append("CPU ").append(pc.optString("cpu", "unavailable")).append("%")
                    append(" · RAM ").append(pc.optString("ramUsedGb", "?")).append(" / ").append(pc.optString("ramTotalGb", "?")).append(" GB")
                    if (!pc.isNull("gpu")) append(" · GPU ").append(pc.optString("gpu")).append("%")
                }
            }
            val chars = CharArray(16000)
            val count = reader.readBlock(chars)
            String(chars, 0, count)
        }
        return Bundle().apply { putInt("code",result.code); putString("body",value) }
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

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sortOrder: String?): Cursor? = null
}
