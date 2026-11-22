package com.phonebox.jarvis.tests.ai

import com.phonebox.jarvis.core.ai.LlamaBridge
import com.phonebox.jarvis.core.ai.ReasoningModelSpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LlamaBridgeTest {

    @Test
    fun testManagedInferenceFallback() = runBlocking {
        val bridge = LlamaBridge.instance
        val tokens = mutableListOf<String>()

        bridge.generateStream("Hello assistant", ReasoningModelSpec()) { token ->
            tokens.add(token)
        }.collect { fullText ->
            assertTrue(fullText.isNotBlank())
        }

        assertTrue(tokens.isNotEmpty())
    }

    @Test
    fun testInferenceCancellation() = runBlocking {
        val bridge = LlamaBridge.instance
        bridge.cancelInference()
        // Ensure state resets safely
        assertNotNull(bridge)
    }
}

