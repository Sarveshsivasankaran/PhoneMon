package com.phonebox.jarvis.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonebox.jarvis.core.ai.JarvisState

/**
 * Animated circular HUD visualizer responding directly to real backend states.
 */
@Composable
fun OrbVisualizer(
    state: JarvisState,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val pulseScale = if (state == JarvisState.STANDBY || state == JarvisState.OFFLINE) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "orb")
        val pulse by transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "pulse"
        )
        pulse
    }

    val stateColor = Color(state.glowColorHex)

    Box(modifier = modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2.5f) * pulseScale * (1f + amplitude * 0.4f)

            // Outer Glow Ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(stateColor.copy(alpha = 0.35f), Color.Transparent),
                    center = center,
                    radius = baseRadius * 1.5f
                ),
                radius = baseRadius * 1.5f,
                center = center
            )

            // Dynamic Segmented Reticle Ring
            drawCircle(
                color = stateColor.copy(alpha = 0.7f),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // Inner Pulsing Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.9f), stateColor.copy(alpha = 0.6f), Color.Transparent),
                    center = center,
                    radius = baseRadius * 0.65f
                ),
                radius = baseRadius * 0.65f,
                center = center
            )
        }

        // Center HUD State Label
        Text(
            text = state.displayName,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
    }
}

