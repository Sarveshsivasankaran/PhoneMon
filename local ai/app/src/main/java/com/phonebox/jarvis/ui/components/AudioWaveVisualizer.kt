package com.phonebox.jarvis.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Animated audio waveform bar visualizer responding to mic amplitude.
 */
@Composable
fun AudioWaveVisualizer(
    amplitude: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        val barCount = 28
        val spacing = size.width / barCount
        val barWidth = spacing * 0.55f

        for (i in 0 until barCount) {
            val randomJitter = if (amplitude > 0.05f) Random.nextFloat() * 0.4f else 0.05f
            val barHeight = (size.height * (amplitude * 0.8f + randomJitter)).coerceIn(4f, size.height)
            val x = i * spacing + spacing / 2f
            val startY = (size.height - barHeight) / 2f
            val endY = startY + barHeight

            drawLine(
                color = barColor.copy(alpha = 0.8f),
                start = Offset(x, startY),
                end = Offset(x, endY),
                strokeWidth = barWidth
            )
        }
    }
}

