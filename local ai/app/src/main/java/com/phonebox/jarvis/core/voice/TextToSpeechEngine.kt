package com.phonebox.jarvis.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native Android Text-to-Speech wrapper configured for local offline synthesis.
 */
class TextToSpeechEngine(
    private val context: Context,
    private val onSpeakingStarted: () -> Unit,
    private val onSpeakingFinished: () -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val isReady = AtomicBoolean(false)

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "US English offline language data missing.")
            } else {
                tts?.setPitch(0.95f)
                tts?.setSpeechRate(1.05f)
                setupListener()
                isReady.set(true)
                Log.i(TAG, "Local Text-to-Speech engine initialized.")
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with code $status")
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeakingStarted()
            }

            override fun onDone(utteranceId: String?) {
                onSpeakingFinished()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onSpeakingFinished()
            }
        })
    }

    fun speak(text: String, utteranceId: String = "jarvis_response") {
        if (isReady.get()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            Log.w(TAG, "TTS not ready yet; skipping vocalization.")
            onSpeakingFinished()
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady.set(false)
    }

    companion object {
        private const val TAG = "TextToSpeechEngine"
    }
}

