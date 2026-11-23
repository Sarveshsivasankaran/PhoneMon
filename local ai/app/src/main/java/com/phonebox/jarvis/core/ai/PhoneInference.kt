package com.phonebox.jarvis.core.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

internal object PhoneInference {
    private val client = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .readTimeout(3, TimeUnit.MINUTES)
        .callTimeout(3, TimeUnit.MINUTES)
        .build()

    suspend fun answer(prompt: String, image: Bitmap? = null): String = withContext(Dispatchers.IO) {
        val profile = AssistantProfile.current()
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
        if (image != null) {
            val scale = minOf(1f, 384f / maxOf(image.width, image.height))
            val frame = Bitmap.createScaledBitmap(image, maxOf(1, (image.width * scale).toInt()),
                maxOf(1, (image.height * scale).toInt()), true)
            val bytes = ByteArrayOutputStream()
            frame.compress(Bitmap.CompressFormat.JPEG, 80, bytes)
            if (frame !== image) frame.recycle()
            val url = "data:image/jpeg;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", url)))
        }
        val message = JSONObject().put("role", "user").put("content", if (image == null) prompt else content)
        val body = JSONObject().put("messages", JSONArray().put(JSONObject().put("role","system").put("content", AssistantProfile.instructions(profile, image == null))).put(message)).put("max_tokens", 256)
        return@withContext try { PhoneRuntime.infer(profile.optString("model"), image != null) { port, key ->
        val request = Request.Builder().url("http://127.0.0.1:$port/v1/chat/completions").header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use "Phone model returned HTTP ${response.code}."
                JSONObject(response.body?.string().orEmpty()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content")
            }
        } catch (error: IOException) {
            "On-device inference failed: ${error.localizedMessage}"
        } catch (error: org.json.JSONException) {
            "Phone model returned an unreadable reply."
        }
        } } catch (e: Exception) { e.message ?: "Model inference failed" }
    }
}
