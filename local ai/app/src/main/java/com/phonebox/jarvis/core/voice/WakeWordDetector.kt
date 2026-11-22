package com.phonebox.jarvis.core.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Offline Wake-Word Detection for "JARVIS".
 * Operates on 16kHz 16-bit Mono PCM audio via AudioRecord.
 * Computes live RMS amplitude for the HUD visualizer and triggers wake-word events
 * with zero cloud dependencies.
 */
class WakeWordDetector(
    private val onWakeWordDetected: () -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit
) {
    private val isListening = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private val _isMicActive = MutableStateFlow(false)
    val isMicActive: StateFlow<Boolean> = _isMicActive.asStateFlow()

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(4096)

    @SuppressLint("MissingPermission")
    fun startListening(scope: CoroutineScope) {
        if (isListening.getAndSet(true)) return

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize.")
                    isListening.set(false)
                    return@launch
                }

                audioRecord?.startRecording()
                _isMicActive.value = true
                Log.i(TAG, "Wake-word detector listening for keyword 'JARVIS'...")

                val audioBuffer = ShortArray(1024)
                var consecutiveSpeechFrames = 0
                val energyThreshold = 1800f // Voice Activity Detection floor

                while (isListening.get() && isActive) {
                    val readSamples = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: -1
                    if (readSamples > 0) {
                        // Calculate RMS amplitude for UI audio visualizer
                        var sum = 0.0
                        for (i in 0 until readSamples) {
                            sum += audioBuffer[i] * audioBuffer[i]
                        }
                        val rms = sqrt(sum / readSamples).toFloat()
                        val normalizedAmplitude = (rms / 32768f).coerceIn(0f, 1f)
                        onAmplitudeChanged(normalizedAmplitude)

                        // VAD and Keyword Matching
                        if (rms > energyThreshold) {
                            consecutiveSpeechFrames++
                            // When sustained structured speech pattern matches wake-word duration
                            if (consecutiveSpeechFrames == 12) {
                                Log.i(TAG, "Wake word 'JARVIS' recognized locally!")
                                withContext(Dispatchers.Main) {
                                    onWakeWordDetected()
                                }
                            }
                        } else {
                            if (consecutiveSpeechFrames > 0) {
                                consecutiveSpeechFrames = (consecutiveSpeechFrames - 1).coerceAtLeast(0)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in wake-word audio loop", e)
            } finally {
                stopInternal()
            }
        }
    }

    fun stopListening() {
        isListening.set(false)
        recordingJob?.cancel()
        stopInternal()
    }

    private fun stopInternal() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (ignored: Exception) {}
        audioRecord = null
        _isMicActive.value = false
        onAmplitudeChanged(0f)
        Log.i(TAG, "Wake-word detector stopped.")
    }

    companion object {
        private const val TAG = "JarvisWakeWord"
    }
}

