package com.phonebox.jarvis.core.voice

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class BundledSpeechTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun previewCompletesThroughAndroidMediaPlayer() {
        val completed = CountDownLatch(1)
        val engine = TextToSpeechEngine(context, {}, { completed.countDown() }, {})
        try {
            instrumentation.runOnMainSync { engine.speak("Phone Box voice test.", "slt") }
            assertTrue("Offline preview did not finish playback", completed.await(20, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { engine.shutdown() }
        }
    }

    @Test fun allThreeVoicesProduceDistinctNonSilentWaves() {
        val digests = mutableSetOf<List<Byte>>()
        for (voice in listOf("slt", "rms", "awb")) {
            val wave = File.createTempFile("voice-test", ".wav", context.cacheDir)
            try {
                assertTrue(voice, Flite.synthesize(voice, "Welcome to Phone Box.", wave.absolutePath))
                val bytes = wave.readBytes()
                assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
                assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
                assertTrue(voice, bytes.size > 8044)
                assertTrue(voice, bytes.drop(44).any { it.toInt() != 0 })
                digests.add(MessageDigest.getInstance("SHA-256").digest(bytes).toList())
            } finally { wave.delete() }
        }
        assertEquals(3, digests.size)
    }
}
