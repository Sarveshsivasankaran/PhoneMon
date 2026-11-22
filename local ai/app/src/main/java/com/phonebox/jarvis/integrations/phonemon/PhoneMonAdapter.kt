package com.phonebox.jarvis.integrations.phonemon

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Standalone PhoneMon adapter communicating over documented local HTTP/WebSocket endpoints.
 * Operates strictly over authenticated external APIs without modifying any host PhoneMon code.
 */
class PhoneMonAdapter(
    private var host: String = "127.0.0.1",
    private var port: Int = 8765
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun updateConnection(host: String, port: Int) {
        this.host = host
        this.port = port
    }

    suspend fun getSystemMetrics(): String = withContext(Dispatchers.IO) {
        try {
            val url = "http://$host:$port/api/status"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string() ?: "{}"
                } else {
                    "{\"error\": \"Desktop agent responded with HTTP ${response.code}\"}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying PhoneMon agent status", e)
            "{\"error\": \"Could not reach desktop agent at $host:$port\"}"
        }
    }

    suspend fun sendNotification(title: String, message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "http://$host:$port/api/push"
            val json = "{\"type\": \"notification\", \"title\": \"$title\", \"message\": \"$message\"}"
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending push notification", e)
            false
        }
    }

    suspend fun triggerMediaAction(action: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "http://$host:$port/api/action"
            val json = "{\"action\": \"$action\"}"
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed triggering media action", e)
            false
        }
    }

    companion object {
        private const val TAG = "PhoneMonAdapter"
    }
}

