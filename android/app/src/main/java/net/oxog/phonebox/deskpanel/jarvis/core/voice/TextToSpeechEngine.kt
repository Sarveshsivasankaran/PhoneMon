package net.oxog.phonebox.deskpanel.jarvis.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.io.IOException
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var utterance: Job? = null

    fun speak(text: String, voice: String = AssistantProfile.current().optString("voice", "slt")) {
        stop()
        utterance = scope.launch {
            try {
                onSpeakingStarted()
                // Keep synthesis latency and native memory bounded for longer replies.
                val words = text.trim().split(Regex("\\s+"))
                val chunk = StringBuilder()
                for (word in words) {
                    if (chunk.length + word.length > 300 && chunk.isNotEmpty()) {
                        sayChunk(voice, chunk.toString())
                        chunk.clear()
                    }
                    if (chunk.isNotEmpty()) chunk.append(' ')
                    chunk.append(word)
                }
                if (chunk.isNotEmpty()) sayChunk(voice, chunk.toString())
                onSpeakingFinished()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e("TextToSpeechEngine", "Offline speech failed", failure)
                onError()
            }
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

    fun stop() { utterance?.cancel() }
    fun shutdown() { scope.cancel() }
}

