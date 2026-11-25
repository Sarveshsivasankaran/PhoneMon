package net.oxog.phonebox.deskpanel

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speech to text for the Dictation widget, with the phone's own speech recognizer (on most phones Google's, which may
 * use its servers). "On the phone only" uses Android 12's on-device recognizer, which never leaves the phone, and is
 * refused on older Android, where a recognizer may ignore a request to stay offline.
 *
 * One session at a time, at most [MAX_MS] long, cancelled when the panel pauses (screen off, another app in front).
 * Every session has an id: events go to the panel's main frame as window.onAndroidSpeech(kind, text, id), and stop or
 * cancel act only on the session they name, so one widget can never end another's. A sandboxed widget frame never
 * sees the results. Nothing is stored. SpeechRecognizer must be used on the main thread, so every entry point posts there.
 */
class Dictation(private val activity: Activity, private val js: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val panelVoice = PanelVoice(activity.applicationContext, ::send)
    private val ids = AtomicInteger()
    private var recognizer: SpeechRecognizer? = null
    private var session = 0
    private var waitingFor: Request? = null
    private val timeLimit = Runnable { recognizer?.stopListening() }

    private data class Request(
        val id: Int,
        val lang: String,
        val offline: Boolean,
        val jarvisWake: Boolean = false,
        val jarvisCommand: Boolean = false,
        val wakeName: String = "jarvis"
    )

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(activity)
    fun hasMicPermission(): Boolean = granted()

    /** Starts a session and returns its id; asks for the microphone first if needed. [lang] is BCP 47 or "". */
    fun start(
        lang: String,
        offline: Boolean,
        jarvisWake: Boolean = false,
        jarvisCommand: Boolean = false,
        wakeName: String = "jarvis"
    ): Int {
        val request = Request(ids.incrementAndGet(), lang, offline, jarvisWake, jarvisCommand, wakeName)
        main.post {
            if (!granted()) {
                // a newer request replaces one still waiting for the permission prompt
                waitingFor?.let { send("error", "cancelled", it.id) }
                val asking = waitingFor != null
                waitingFor = request
                if (!asking) activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST)
            } else begin(request)
        }
        return request.id
    }

    // below Android 6 the microphone is granted when the app is installed
    private fun granted() = Build.VERSION.SDK_INT < 23 ||
        activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** From MainActivity.onRequestPermissionsResult. */
    fun onPermission(granted: Boolean) {
        val request = waitingFor ?: return
        waitingFor = null
        when {
            granted -> begin(request)
            // "don't ask again": Android no longer shows the prompt, only its settings can turn the microphone on
            Build.VERSION.SDK_INT >= 23 && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ->
                send("error", "permission-blocked", request.id)
            else -> send("error", "permission", request.id)
        }
    }

    /** Stops listening and delivers what was heard so far, if [id] is the session running. */
    fun stop(id: Int) = main.post { panelVoice.stop(id); if (id == session) recognizer?.stopListening() }

    /**
     * Drops the session without a result, if [id] is the one running or waiting. 0 = whatever runs (the panel paused):
     * a request waiting for the permission prompt stays, as that prompt itself pauses the panel.
     */
    fun cancel(id: Int) = main.post {
        panelVoice.cancel(id)
        waitingFor?.takeIf { id != 0 && it.id == id }?.let { waitingFor = null; send("error", "cancelled", it.id) }
        if (recognizer != null && (id == 0 || id == session)) { val was = session; end(); send("error", "cancelled", was) }
    }

    fun close() { cancel(0); main.post { panelVoice.close() } }

    private fun begin(request: Request) {
        panelVoice.cancel(0)
        if (recognizer != null) { val was = session; end(); send("error", "cancelled", was) }
        if (request.jarvisWake) { panelVoice.start(request.id, continuousWake = true, wakeName = request.wakeName); return }
        if (request.jarvisCommand) {
            // Try system SpeechRecognizer (neural ASR) for accurate command dictation, falling back to local Vosk offline.
            val tryOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)
            if (available() || tryOnDevice) {
                startJarvisSystemRecognizer(request, preferOnDevice = tryOnDevice)
            } else {
                panelVoice.start(request.id, continuousWake = false, wakeName = request.wakeName)
            }
            return
        }
        if (!available()) { send("error", "unavailable", request.id); return }
        // "on the phone only" is a promise: only Android 12's on-device recognizer keeps it
        if (request.offline && Build.VERSION.SDK_INT < 31) { send("error", "offline-unsupported", request.id); return }
        val r = try {
            if (request.offline) SpeechRecognizer.createOnDeviceSpeechRecognizer(activity) else SpeechRecognizer.createSpeechRecognizer(activity)
        } catch (_: UnsupportedOperationException) {
            // Android 12+ without an on-device recognizer (isOnDeviceRecognitionAvailable is Android 13)
            send("error", "offline-unavailable", request.id); return
        }
        attachSystemRecognizer(r, request, allowSystemRetry = false, allowVoskFallback = false)
    }

    private fun startJarvisSystemRecognizer(request: Request, preferOnDevice: Boolean) {
        val r = try {
            if (preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
            } else if (available()) {
                SpeechRecognizer.createSpeechRecognizer(activity)
            } else null
        } catch (_: Exception) {
            null
        }
        if (r == null) {
            if (preferOnDevice && available()) startJarvisSystemRecognizer(request, preferOnDevice = false)
            else panelVoice.start(request.id, continuousWake = false, wakeName = request.wakeName)
            return
        }
        attachSystemRecognizer(r, request, allowSystemRetry = preferOnDevice && available(), allowVoskFallback = true)
    }

    private fun attachSystemRecognizer(r: SpeechRecognizer, request: Request, allowSystemRetry: Boolean, allowVoskFallback: Boolean) {
        recognizer = r
        session = request.id
        val id = request.id
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = send("ready", "", id)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = send("end", "", id)
            override fun onError(error: Int) {
                if (session != id) return
                end()
                if (request.jarvisCommand && error in setOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    send("final", "", id)
                    return
                }
                if (allowSystemRetry && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    startJarvisSystemRecognizer(request, preferOnDevice = false)
                    return
                }
                if (allowVoskFallback && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    panelVoice.start(id, continuousWake = false, wakeName = request.wakeName)
                    return
                }
                send("error", error.toString(), id)
            }
            override fun onResults(results: Bundle?) {
                if (session != id) return
                end()
                send("final", bestCandidate(results, request.jarvisCommand), id)
            }
            override fun onPartialResults(partialResults: Bundle?) = send("partial", normalizeSpoken(first(partialResults), request.jarvisCommand), id)
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
        if (request.jarvisCommand) {
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }
        if (request.offline && !request.jarvisCommand) intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        if (request.lang.isNotBlank()) intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, request.lang)
        r.startListening(intent)
        main.postDelayed(timeLimit, MAX_MS)
    }

    private fun end() {
        main.removeCallbacks(timeLimit)
        recognizer?.let { it.cancel(); it.destroy() }
        recognizer = null
        session = 0
    }

    private fun first(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""

    private fun bestCandidate(b: Bundle?, normalize: Boolean): String {
        val list = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return ""
        if (list.isEmpty()) return ""
        if (!normalize) return list.first()
        val domainHit = list.firstOrNull { cand ->
            DOMAIN_HINT_REGEX.containsMatchIn(cand)
        }
        return normalizeSpoken(domainHit ?: list.first(), true)
    }

    private fun normalizeSpoken(text: String, normalize: Boolean): String {
        if (!normalize || text.isBlank()) return text
        return text
            .replace(Regex("""\btail\s+scale\b""", RegexOption.IGNORE_CASE), "Tailscale")
            .replace(Regex("""\ba\.?\s*d\.?\s*b\.?\b""", RegexOption.IGNORE_CASE), "ADB")
            .replace(Regex("""\bphone\s+(?:mon|man|box)\b""", RegexOption.IGNORE_CASE), "PhoneMon")
            .replace(Regex("""\b(?:jar\s+vis|jervis|jarves|jarvas|jarvice)\b""", RegexOption.IGNORE_CASE), "Jarvis")
    }

    private fun send(kind: String, text: String, id: Int) =
        js("window.onAndroidSpeech && window.onAndroidSpeech(${JSONObject.quote(kind)}, ${JSONObject.quote(text.take(MAX_CHARS))}, $id)")

    companion object {
        const val PERMISSION_REQUEST = 41
        private const val MAX_MS = 60_000L
        private const val MAX_CHARS = 2000
        private val DOMAIN_HINT_REGEX = Regex("""\b(jarvis|adb|tailscale|phonemon|spotify|docker|discord|brightness|volume|monitor|lock|obs)\b""", RegexOption.IGNORE_CASE)
    }
}
