package net.oxog.phonebox.deskpanel.jarvis.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import net.oxog.phonebox.deskpanel.jarvis.core.ai.AssistantProfile

internal object Flite {
    init { System.loadLibrary("phonebox_speech") }
    external fun synthesize(voice: String, text: String, wavePath: String): Boolean
}

class TextToSpeechEngine(
    private val context: Context,
    private val onSpeakingStarted: () -> Unit,
    private val onSpeakingFinished: () -> Unit,
    private val onError: () -> Unit
) {
    private data class Preset(val locale: Locale, val female: Boolean?, val pitch: Float, val rate: Float, val fliteFallback: String, val slot: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var utterance: Job? = null
    private var systemTts: TextToSpeech? = null
    private var ttsReady: CompletableDeferred<Boolean>? = null
    private val pendingWaiters = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    fun speak(text: String, voice: String = AssistantProfile.activeVoice()) {
        stop()
        val cleanText = prepareForSpeech(text)
        if (cleanText.isBlank()) {
            onSpeakingFinished()
            return
        }
        utterance = scope.launch {
            try {
                onSpeakingStarted()
                val preset = PRESETS[voice]
                val spokenBySystem = if (preset != null) speakWithSystemTts(cleanText, preset) else false
                if (!spokenBySystem) {
                    val fliteVoice = preset?.fliteFallback ?: voice.takeIf { it in FLITE_VOICES } ?: "slt"
                    speakWithFlite(cleanText, fliteVoice)
                }
                onSpeakingFinished()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e("TextToSpeechEngine", "Speech playback failed", failure)
                onError()
            }
        }
    }

    private suspend fun ensureSystemTts(): TextToSpeech? {
        ttsReady?.let { deferred ->
            val ok = withTimeoutOrNull(1500) { deferred.await() } ?: false
            return if (ok) systemTts else null
        }
        val deferred = CompletableDeferred<Boolean>()
        ttsReady = deferred
        val created = try {
            TextToSpeech(context.applicationContext) { status ->
                deferred.complete(status == TextToSpeech.SUCCESS)
            }
        } catch (e: Exception) {
            Log.w("TextToSpeechEngine", "Android system TTS unavailable, falling back to Flite", e)
            deferred.complete(false)
            return null
        }
        created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                utteranceId?.let { pendingWaiters.remove(it)?.complete(true) }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { pendingWaiters.remove(it)?.complete(false) }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { pendingWaiters.remove(it)?.complete(false) }
            }
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                utteranceId?.let { pendingWaiters.remove(it)?.complete(true) }
            }
        })
        created.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        systemTts = created
        val ok = withTimeoutOrNull(2500) { deferred.await() } ?: false
        return if (ok) created else null
    }

    private suspend fun speakWithSystemTts(text: String, preset: Preset): Boolean {
        val tts = ensureSystemTts() ?: return false
        val pickedVoice = runCatching { selectVoice(tts, preset) }.getOrNull()
        val langResult = tts.setLanguage(pickedVoice?.locale ?: preset.locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            val enResult = tts.setLanguage(Locale.US)
            if (enResult == TextToSpeech.LANG_MISSING_DATA || enResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                return false
            }
        }
        if (pickedVoice != null) runCatching { tts.voice = pickedVoice }
        tts.setPitch(preset.pitch)
        tts.setSpeechRate(preset.rate)

        for (segment in splitIntoSentences(text, 550)) {
            coroutineContext.ensureActive()
            val id = "jarvis-${UUID.randomUUID()}"
            val done = CompletableDeferred<Boolean>()
            pendingWaiters[id] = done
            try {
                val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id) }
                val queued = tts.speak(segment, TextToSpeech.QUEUE_FLUSH, params, id)
                if (queued != TextToSpeech.SUCCESS) {
                    pendingWaiters.remove(id)
                    return false
                }
                val ok = withTimeoutOrNull(45_000) { done.await() } ?: false
                if (!ok) return false
            } catch (c: CancellationException) {
                pendingWaiters.remove(id)
                tts.stop()
                throw c
            }
        }
        return true
    }

    private fun selectVoice(tts: TextToSpeech, preset: Preset): Voice? {
        val all = tts.voices ?: return null
        val english = all.filter { it.locale.language.equals("en", ignoreCase = true) }
        if (english.isEmpty()) return null
        val wantCountry = preset.locale.country
        val wantMale = preset.female == false
        val wantFemale = preset.female == true
        val scored = english.sortedWith(compareByDescending<Voice> { v ->
            var score = 0
            if (v.locale.country.equals(wantCountry, ignoreCase = true)) score += 100
            else if (v.locale.country.equals("US", ignoreCase = true)) score += 20
            if (!v.isNetworkConnectionRequired) score += 40
            if (v.quality >= Voice.QUALITY_VERY_HIGH) score += 30
            else if (v.quality >= Voice.QUALITY_HIGH) score += 20
            val tag = (v.name + " " + v.features.joinToString(" ")).lowercase(Locale.ROOT)
            val looksMale = Regex("""\b(male|man|boy|rms|awb|gba|rjs|tpd|iom|en-.*-m|guy|daniel|oliver|arthur|matthew)\b|[-_](rms|iom|gbd|gbb|rjs|tpd|x-iom|x-gbd|x-ene|x-esd|x-aud|x-sfg)\b""")
                .containsMatchIn(tag) && !tag.contains("female")
            val looksFemale = Regex("""\b(female|woman|girl|slt|aria|emma|samantha|karen|sfg|tpf|iob|gba)\b|[-_](slt|iob|tpf|sfg|x-iob|x-tpf|x-gba|x-enc|x-aub|x-sfg)\b""")
                .containsMatchIn(tag)
            if (wantMale && looksMale) score += 45
            if (wantFemale && looksFemale) score += 45
            if (wantMale && looksFemale) score -= 25
            if (wantFemale && looksMale) score -= 25
            score
        }.thenBy { it.name })
        val exact = scored.filter { it.locale.country.equals(wantCountry, ignoreCase = true) }
        val pool = if (exact.size > 1) exact else scored
        return pool[preset.slot % pool.size]
    }

    private suspend fun speakWithFlite(text: String, voice: String) {
        for (chunk in splitIntoSentences(text, 300)) {
            sayChunk(voice, chunk)
        }
    }

    private suspend fun sayChunk(voice: String, text: String) {
        val file = File.createTempFile("speech-", ".wav", context.cacheDir)
        try {
            withContext(Dispatchers.Default) {
                check(Flite.synthesize(voice, text, file.absolutePath)) { "Flite could not synthesize speech" }
            }
            val player = MediaPlayer()
            try {
                suspendCancellableCoroutine<Unit> { done ->
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    player.setOnCompletionListener { if (done.isActive) done.resume(Unit) }
                    player.setOnErrorListener { _, what, extra ->
                        if (done.isActive) done.resumeWithException(IOException("Playback failed ($what/$extra)"))
                        true
                    }
                    player.setOnPreparedListener { if (done.isActive) it.start() }
                    player.setDataSource(file.absolutePath)
                    player.prepareAsync()
                }
            } finally {
                player.release()
            }
        } finally {
            file.delete()
        }
    }

    fun stop() {
        utterance?.cancel()
        pendingWaiters.values.forEach { it.complete(true) }
        pendingWaiters.clear()
        runCatching { systemTts?.stop() }
    }

    fun shutdown() {
        stop()
        scope.cancel()
        runCatching { systemTts?.shutdown() }
        systemTts = null
        ttsReady = null
    }

    internal companion object {
        private val FLITE_VOICES = setOf("slt", "rms", "awb")
        private val PRESETS = mapOf(
            "natural" to Preset(Locale.US, null, 1.0f, 1.02f, "slt", 0),
            "jarvis" to Preset(Locale.UK, false, 0.90f, 1.00f, "awb", 0),
            "emma" to Preset(Locale.UK, true, 1.03f, 1.02f, "slt", 1),
            "aria" to Preset(Locale.US, true, 1.04f, 1.03f, "slt", 0),
            "ryan" to Preset(Locale.US, false, 0.91f, 1.00f, "rms", 1),
            "aura" to Preset(Locale("en", "AU"), true, 1.02f, 1.02f, "slt", 0),
            "arjun" to Preset(Locale("en", "IN"), false, 0.94f, 1.00f, "rms", 0),
            "priya" to Preset(Locale("en", "IN"), true, 1.03f, 1.02f, "slt", 1)
        )

        fun prepareForSpeech(raw: String): String = raw
            .replace(Regex("```[\\s\\S]*?```"), " code block omitted ")
            .replace(Regex("[*_`#]+"), "")
            .replace(" · ", ", ")
            .replace("·", ", ")
            .replace(Regex("(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)\\s*GB"), "$1 of $2 gigabytes")
            .replace(Regex("\\s+"), " ")
            .trim()

        fun splitIntoSentences(text: String, maxChars: Int): List<String> {
            val parts = text.split(Regex("(?<=[.!?])\\s+|\\n+")).filter { it.isNotBlank() }
            if (parts.isEmpty()) return listOf(text)
            val result = mutableListOf<String>()
            val current = StringBuilder()
            for (part in parts) {
                if (part.length > maxChars) {
                    if (current.isNotEmpty()) { result += current.toString(); current.clear() }
                    var chunk = StringBuilder()
                    for (word in part.split(Regex("\\s+"))) {
                        if (chunk.length + word.length + 1 > maxChars && chunk.isNotEmpty()) {
                            result += chunk.toString(); chunk = StringBuilder()
                        }
                        if (chunk.isNotEmpty()) chunk.append(' ')
                        chunk.append(word)
                    }
                    if (chunk.isNotEmpty()) result += chunk.toString()
                    continue
                }
                if (current.length + part.length + 1 > maxChars && current.isNotEmpty()) {
                    result += current.toString(); current.clear()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(part)
            }
            if (current.isNotEmpty()) result += current.toString()
            return result
        }
    }
}

