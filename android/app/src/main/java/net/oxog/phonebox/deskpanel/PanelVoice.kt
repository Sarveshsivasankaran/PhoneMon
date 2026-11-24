package net.oxog.phonebox.deskpanel

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/** One private, offline recognition stream. Model loading and audio reads share a worker. */
internal class PanelVoice(private val context: Context, private val event: (String, String, Int) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var model: Model? = null
    @Volatile private var active = 0
    @Volatile private var finish = 0

    fun start(id: Int) {
        active = id
        finish = 0
        worker.execute {
            if (active != id) return@execute
            try {
                val english = model ?: loadModel().also { model = it }
                if (active == id) recognize(id, english)
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
    private fun recognize(id: Int, english: Model) {
        val rate = 16000
        val capacity = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(capacity > 0) { "Microphone does not support speech capture" }
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(capacity, 6400))
        var result = ""
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
            Recognizer(english, rate.toFloat()).use { recognizer ->
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone is busy" }
                post(id, "ready", "")
                val buffer = ShortArray(1600)
                val deadline = SystemClock.elapsedRealtime() + 30000
                var partial = ""
                while (active == id && finish != id && SystemClock.elapsedRealtime() < deadline) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    check(read >= 0) { "Microphone capture interrupted" }
                    if (recognizer.acceptWaveForm(buffer, read)) {
                        result = JSONObject(recognizer.result).optString("text").trim()
                        if (result.isNotEmpty()) break
                    } else {
                        val heard = JSONObject(recognizer.partialResult).optString("partial")
                        if (heard != partial) { partial = heard; post(id, "partial", heard) }
                    }
                }
                if (active == id && result.isEmpty()) result = JSONObject(recognizer.finalResult).optString("text").trim()
            }
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
        }
        post(id, "final", result)
    }

    private companion object { val modelFiles = Any() }
}
