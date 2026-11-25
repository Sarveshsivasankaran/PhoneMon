package net.oxog.phonebox.deskpanel

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One private, offline recognition stream. Model loading and audio reads share a worker. */
internal class PanelVoice(private val context: Context, private val event: (String, String, Int) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var model: Model? = null
    @Volatile private var active = 0
    @Volatile private var finish = 0

    fun start(id: Int, continuousWake: Boolean = false, wakeName: String = "jarvis") {
        active = id
        finish = 0
        worker.execute {
            if (active != id) return@execute
            try {
                val english = model ?: loadModel().also { model = it }
                if (active == id) recognize(id, english, continuousWake, wakeName)
            } catch (e: Exception) {
                post(id, "error", e.localizedMessage ?: "Offline recognition failed")
            } catch (_: UnsatisfiedLinkError) {
                post(id, "error", "Offline voice is unavailable on this device")
            }
        }
    }

    fun stop(id: Int) { if (active == id) finish = id }
    fun cancel(id: Int) {
        if (active != 0 && (id == 0 || id == active)) {
            val previous = active
            active = 0
            event("error", "cancelled", previous)
        }
    }
    fun close() {
        cancel(0)
        worker.execute { model?.close(); model = null }
        worker.shutdown()
    }

    private fun post(id: Int, kind: String, text: String) {
        main.post {
            if (active == id) {
                if (kind == "final" || kind == "error") active = 0
                event(kind, text, id)
            }
        }
    }

    private fun loadModel(): Model {
        val base = context.noBackupFilesDir
        val dir = File(base, "vosk-en-us-0.15")
        synchronized(modelFiles) {
            if (!dir.isDirectory) {
                val stage = File(base, "vosk-en-us-0.15-installing").apply { mkdirs() }
                ZipInputStream(context.assets.open("voice-en.zip").buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val dest = File(stage, entry.name).canonicalFile
                        require(dest.path.startsWith(stage.canonicalPath + File.separator))
                        if (!entry.isDirectory) {
                            dest.parentFile?.mkdirs()
                            dest.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                check(stage.renameTo(dir)) { "Could not install offline speech files" }
            }
        }
        return Model(dir.path)
    }

    @SuppressLint("MissingPermission") // Dictation owns the permission request before start().
    private fun recognize(id: Int, english: Model, continuousWake: Boolean, wakeName: String) {
        val rate = 16000
        val capacity = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(capacity > 0) { "Microphone does not support speech capture" }
        val bufferBytes = maxOf(capacity * 2, 12800)
        val recorder = createRecorder(rate, bufferBytes)
        var result = ""
        val effects = attachAudioEffects(recorder.audioSessionId)
        val wakeRegex = buildWakeRegex(wakeName)
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
            Recognizer(english, rate.toFloat()).use { recognizer ->
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone is busy" }
                post(id, "ready", "")
                val buffer = ShortArray(1600)
                val deadline = if (continuousWake) Long.MAX_VALUE else SystemClock.elapsedRealtime() + 30000
                var partial = ""
                var wakePartialSent = false
                while (active == id && finish != id && SystemClock.elapsedRealtime() < deadline) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    check(read >= 0) { "Microphone capture interrupted" }
                    if (read == 0) continue
                    boostQuietSpeech(buffer, read)
                    if (recognizer.acceptWaveForm(buffer, read)) {
                        val segment = JSONObject(recognizer.result).optString("text").trim()
                        if (continuousWake) {
                            wakePartialSent = false
                            partial = ""
                            if (segment.isNotEmpty() && wakeRegex.containsMatchIn(segment)) {
                                result = segment
                                break
                            }
                        } else if (segment.isNotEmpty()) {
                            result = segment
                            break
                        }
                    } else {
                        val heard = JSONObject(recognizer.partialResult).optString("partial")
                        if (heard != partial) {
                            partial = heard
                            if (!continuousWake || wakePartialSent || wakeRegex.containsMatchIn(heard)) {
                                if (continuousWake) wakePartialSent = true
                                post(id, "partial", heard)
                            }
                        }
                    }
                }
                if (active == id && result.isEmpty() && !continuousWake) {
                    result = JSONObject(recognizer.finalResult).optString("text").trim()
                } else if (active == id && result.isEmpty() && continuousWake && wakeRegex.containsMatchIn(partial)) {
                    result = partial.trim()
                }
            }
        } finally {
            effects.forEach { runCatching { it() } }
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
        }
        post(id, "final", result)
    }

    @SuppressLint("MissingPermission")
    private fun createRecorder(rate: Int, bufferBytes: Int): AudioRecord {
        val voice = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes)
        if (voice.state == AudioRecord.STATE_INITIALIZED) return voice
        voice.release()
        return AudioRecord(MediaRecorder.AudioSource.MIC, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes)
    }

    private fun attachAudioEffects(sessionId: Int): List<() -> Unit> {
        val releases = mutableListOf<() -> Unit>()
        if (NoiseSuppressor.isAvailable()) {
            runCatching {
                NoiseSuppressor.create(sessionId)?.let { ns ->
                    ns.enabled = true
                    releases += { ns.release() }
                }
            }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            runCatching {
                AcousticEchoCanceler.create(sessionId)?.let { aec ->
                    aec.enabled = true
                    releases += { aec.release() }
                }
            }
        }
        if (AutomaticGainControl.isAvailable()) {
            runCatching {
                AutomaticGainControl.create(sessionId)?.let { agc ->
                    agc.enabled = true
                    releases += { agc.release() }
                }
            }
        }
        return releases
    }

    private fun boostQuietSpeech(samples: ShortArray, count: Int) {
        var sumSq = 0.0
        var peak = 0
        for (i in 0 until count) {
            val s = samples[i].toInt()
            val abs = if (s < 0) -s else s
            if (abs > peak) peak = abs
            sumSq += s.toDouble() * s.toDouble()
        }
        if (peak < 50) return // ignore silence noise floor
        val rms = sqrt(sumSq / count)
        if (rms in 35.0..1800.0) {
            val maxSafeGain = 24000.0 / peak
            val gain = minOf(3.8, maxSafeGain, 2200.0 / rms)
            if (gain > 1.15) {
                for (i in 0 until count) {
                    val scaled = (samples[i] * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    samples[i] = scaled.toShort()
                }
            }
        }
    }

    private fun buildWakeRegex(wakeName: String): Regex {
        val cleanName = Regex.escape(wakeName.trim().lowercase().ifBlank { "jarvis" })
        return Regex("""\b(?:$cleanName|jarvis|jar\s+vis|jervis|jarves|jarvas|jarvice|charvis|garvis)\b""", RegexOption.IGNORE_CASE)
    }

    private companion object { val modelFiles = Any() }
}
