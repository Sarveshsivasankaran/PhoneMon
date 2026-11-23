package com.phonebox.jarvis.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonebox.jarvis.core.agent.AgentOrchestrator
import com.phonebox.jarvis.core.ai.JarvisState
import com.phonebox.jarvis.core.ai.ModelManager
import com.phonebox.jarvis.ui.components.AudioWaveVisualizer
import com.phonebox.jarvis.ui.components.GlowingBorder
import com.phonebox.jarvis.ui.components.OrbVisualizer
import com.phonebox.jarvis.ui.theme.*

@Composable
fun JarvisMainScreen(
    wakeCount: Int,
    orchestrator: AgentOrchestrator,
    modelManager: ModelManager,
    micAmplitude: Float,
    isMicActive: Boolean,
    onToggleMic: () -> Unit,
    onTriggerVision: () -> Unit
) {
    val state by orchestrator.currentState.collectAsState()
    val streamingText by orchestrator.currentStreamingText.collectAsState()
    val pendingTool by orchestrator.pendingApprovalTool.collectAsState()
    val isThrottled by modelManager.thermalThrottled.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val assistantName by com.phonebox.jarvis.core.ai.AssistantProfile.name.collectAsState()
    val setupStatus by com.phonebox.jarvis.core.ai.PhoneRuntime.status.collectAsState()
    val scope = rememberCoroutineScope()
    var setup by remember { mutableStateOf(false) }
    if (setup) AlertDialog(onDismissRequest = { setup = false }, title = { Text("On-device models") },
        text = { Text(setupStatus) }, confirmButton = {
            TextButton(onClick = { scope.launch { com.phonebox.jarvis.core.ai.PhoneRuntime.install("lfm2.5-vl-450m") } }) { Text("Install vision") }
        }, dismissButton = {
            TextButton(onClick = { scope.launch { com.phonebox.jarvis.core.ai.PhoneRuntime.install("qwen2.5-0.5b") } }) { Text("Install chat") }
        })
    LaunchedEffect(state, streamingText) {
        val panel = com.phonebox.jarvis.integrations.phonemon.PhoneMonAdapter()
        while (true) {
            runCatching { panel.call("session", mapOf("state" to state.name, "text" to streamingText.take(2000))) }
            kotlinx.coroutines.delay(5000)
        }
    }
    val glowColor = Color(state.glowColorHex)

    var showFace by remember { mutableStateOf(false) }
    LaunchedEffect(wakeCount) {
        if (wakeCount > 0) {
            showFace = true
            kotlinx.coroutines.delay(2400)
            showFace = false
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // Glowing Screen Borders on Active Voice/Listening
        GlowingBorder(
            isActive = showFace,
            glowColor = glowColor
        )

        val compact = maxHeight < 520.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top HUD Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = assistantName,
                    color = CyanGlow,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { setup = true }) { Text("Set up model") }
                Text(
                    text = if (isThrottled) "THROTTLED" else state.displayName,
                    color = if (isThrottled) Color.Yellow else TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Central Animated Circular Visualizer
            if (!compact) OrbVisualizer(
                state = state,
                amplitude = micAmplitude
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Audio Waveform Visualizer
            if (!compact && isMicActive) AudioWaveVisualizer(
                amplitude = micAmplitude,
                barColor = glowColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Live Response Terminal / Observation Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(BgCard, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderGlow, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "TERMINAL FEED",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (streamingText.isNotBlank()) streamingText else "Standing by for user voice prompt or text command...",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                }
            }

            // Explicit Confirmation Gate for Pending Destructive Tool Calls
            if (pendingTool != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = BgSurface),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFF9100), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "SECURITY AUTHORIZATION REQUIRED",
                            color = Color(0xFFFF9100),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Execute action: ${pendingTool?.first} with parameters: ${pendingTool?.second}",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { orchestrator.rejectPendingTool() }) {
                                Text("DENY", color = Color.Red)
                            }
                            Button(
                                onClick = { orchestrator.approvePendingTool() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanGlow)
                            ) {
                                Text("APPROVE", color = Color.Black)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Input Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onTriggerVision,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = CyanGlow)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = "Camera Vision")
                }

                IconButton(
                    onClick = onToggleMic,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (isMicActive) Color.Green else TextMuted)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice Input")
                }

                TextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Ask JARVIS...", color = TextMuted) },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = BgSurface,
                        unfocusedContainerColor = BgSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedIndicatorColor = CyanGlow
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            orchestrator.handleUserInput(textInput, isVoice = false)
                            textInput = ""
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = CyanGlow)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = showFace,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.phonebox.jarvis.R.drawable.jarvis_face),
                contentDescription = "Jarvis heard you",
                modifier = Modifier.size(if (compact) 64.dp else 96.dp)
            )
        }
    }
}
