package com.phonebox.jarvis.core.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onCommand: (String) -> Unit = {},
    private val onUnavailable: () -> Unit = {}
) {
    private val active = MutableStateFlow(false)
    val isMicActive = active.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var commandDeadline = 0L
    private val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
    }

    fun startListening() {
        if (active.value) return
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onUnavailable()
            return
        }
        val engine = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = engine
        active.value = true
        engine.setRecognitionListener(object : RecognitionListener {
            private fun restart(delay: Long) {
                handler.postDelayed({ if (recognizer === engine) engine.startListening(request) }, delay)
            }
            override fun onResults(results: Bundle?) {
                if (recognizer !== engine) return
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                val match = words?.let { KEYWORD.find(it) }
                val now = android.os.SystemClock.elapsedRealtime()
                if (words != null && match != null) {
                    onWakeWordDetected()
                    val command = words.substring(match.range.last + 1).trim(' ', ',', '.', '?', '!')
                    commandDeadline = if (command.isEmpty()) now + 8000 else 0
                    if (command.isNotEmpty()) onCommand(command)
                } else if (!words.isNullOrBlank() && now < commandDeadline) {
                    commandDeadline = 0
                    onCommand(words)
                }
                restart(350)
            }
            override fun onError(error: Int) {
                if (recognizer !== engine) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(350)
                    else -> { stopListening(); onUnavailable() }
                }
            }
            override fun onRmsChanged(rmsdB: Float) = onAmplitudeChanged((rmsdB / 12f).coerceIn(0f, 1f))
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        engine.startListening(request)
    }

    fun stopListening() {
        handler.removeCallbacksAndMessages(null)
        val engine = recognizer
        commandDeadline = 0
        recognizer = null
        engine?.destroy()
        active.value = false
        onAmplitudeChanged(0f)
    }

    private companion object {
        val KEYWORD = Regex("""\bjarvis\b""", RegexOption.IGNORE_CASE)
    }
}
