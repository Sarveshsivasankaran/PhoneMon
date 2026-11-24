package net.oxog.phonebox.deskpanel.jarvis.core.ai

import kotlinx.coroutines.flow.MutableStateFlow
import net.oxog.phonebox.deskpanel.PhoneMonApplication
import net.oxog.phonebox.deskpanel.jarvis.core.agent.ToolRegistry
import net.oxog.phonebox.deskpanel.jarvis.integrations.phonemon.PhoneMonAdapter
import org.json.JSONObject

internal object AssistantProfile {
    private val prefs get() = PhoneMonApplication.instance.getSharedPreferences("assistant", 0)
    val name = MutableStateFlow("JARVIS")
    fun current() = JSONObject(prefs.getString("profile", "{}") ?: "{}")
    suspend fun sync() {
        name.value = current().optString("assistantName", "JARVIS")
        val profile = JSONObject(PhoneMonAdapter().call("profile"))
        require(profile.getString("assistantName").length in 1..40 && profile.getString("personality").length <= 2000)
        require(profile.getString("model") in setOf("qwen2.5-0.5b", "lfm2.5-vl-450m"))
        require(profile.optString("voice", "slt") in setOf("slt", "rms", "awb"))
        prefs.edit().putString("profile", profile.toString()).apply()
        name.value = profile.getString("assistantName")
    }
    fun instructions(profile: JSONObject, prompt: String, image: Boolean): String = buildString {
        appendLine("You are " + profile.optString("assistantName", "JARVIS") + ". " + profile.optString("personality"))
        val tools = if (image) emptyList() else ToolRegistry.forRequest(prompt)
        if (tools.isEmpty()) {
            appendLine("Answer the user's question directly in plain language.")
        } else {
            appendLine("Answer questions normally. Only when the user requests one of these desk actions, output JSON with a tool name and parameters.")
            tools.forEach { appendLine(it.name + " " + it.parametersSchemaJson + " " + it.description) }
        }
    }
}

