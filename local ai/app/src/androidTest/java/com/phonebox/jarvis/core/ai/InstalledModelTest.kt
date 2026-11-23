package com.phonebox.jarvis.core.ai

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** Run on an ARM64 phone after installing both catalog models. */
class InstalledModelTest {
    @Test fun offlineChat() = runBlocking {
        val reply = PhoneInference.answer("What is two plus two? Answer with one digit.")
        assertTrue(reply, "4" in reply || "four" in reply.lowercase())
    }

    @Test fun offlineVision() = runBlocking {
        val frame = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            frame.eraseColor(Color.RED)
            val reply = PhoneInference.answer("What color is this image? Answer with one word.", frame)
            assertTrue(reply, "red" in reply.lowercase())
        } finally {
            frame.recycle()
        }
    }
}
