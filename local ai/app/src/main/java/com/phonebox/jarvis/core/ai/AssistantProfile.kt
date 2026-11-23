package com.phonebox.jarvis.core.ai

import com.phonebox.jarvis.JarvisApplication
import com.phonebox.jarvis.core.agent.ToolRegistry
import com.phonebox.jarvis.integrations.phonemon.PhoneMonAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

internal object AssistantProfile {
    private val prefs get() = JarvisApplication.instance.getSharedPreferences("assistant", 0)
    val name = MutableStateFlow("JARVIS")
    fun current() = JSONObject(prefs.getString("profile", "{}") ?: "{}")
    suspend fun sync() {
        name.value = current().optString("assistantName","JARVIS")
        val profile = JSONObject(PhoneMonAdapter().call("profile"))
        require(profile.getString("assistantName").length in 1..40 && profile.getString("personality").length <= 2000)
        require(profile.getString("model") in setOf("qwen2.5-0.5b", "lfm2.5-vl-450m"))
        prefs.edit().putString("profile",profile.toString()).apply()
        name.value = profile.getString("assistantName")
    }
    fun instructions(profile: JSONObject, tools: Boolean): String = buildString {
        appendLine("You are " + profile.optString("assistantName", "JARVIS") + ". " + profile.optString("personality"))
        if (tools) {
            appendLine("""For a requested panel action, respond only with {"tool":"name","parameters":{...}}. Available tools:""")
            ToolRegistry.ALL_TOOLS.forEach { appendLine(it.name + " " + it.parametersSchemaJson + " " + it.description) }
        }
    }
}
