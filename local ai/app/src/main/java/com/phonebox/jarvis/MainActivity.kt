package com.phonebox.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.phonebox.jarvis.core.agent.AgentOrchestrator
import com.phonebox.jarvis.core.agent.PermissionManager
import com.phonebox.jarvis.core.ai.JarvisState
import com.phonebox.jarvis.core.memory.MemoryManager
import com.phonebox.jarvis.core.vision.CameraManager
import com.phonebox.jarvis.core.voice.TextToSpeechEngine
import com.phonebox.jarvis.core.voice.WakeWordDetector
import com.phonebox.jarvis.integrations.phonemon.PhoneMonAdapter
import com.phonebox.jarvis.ui.screens.JarvisMainScreen
import com.phonebox.jarvis.ui.theme.JarvisTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var orchestrator: AgentOrchestrator
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var ttsEngine: TextToSpeechEngine
    private lateinit var cameraManager: CameraManager

    private var micAmplitude by mutableFloatStateOf(0f)
    private var wakeCount by mutableIntStateOf(0)
    private var resumeListening = false
    private var capturing = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (permissions[Manifest.permission.CAMERA] == true) triggerVisionAnalysis()
        if (recordAudioGranted) {
            startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = JarvisApplication.instance
        val dao = app.database.jarvisDao()
        val memoryManager = MemoryManager(dao)
        val permissionManager = PermissionManager(dao)
        val phoneMonAdapter = PhoneMonAdapter()

        ttsEngine = TextToSpeechEngine(
            context = this,
            onSpeakingStarted = { orchestrator.setState(JarvisState.SPEAKING) },
            onSpeakingFinished = { orchestrator.setState(JarvisState.STANDBY) }
        )

        orchestrator = AgentOrchestrator(
            scope = lifecycleScope,
            memoryManager = memoryManager,
            permissionManager = permissionManager,
            phoneMonAdapter = phoneMonAdapter,
            ttsEngine = ttsEngine
        )

        cameraManager = CameraManager(this)
        wakeWordDetector = WakeWordDetector(
            context = this,
            onCommand = { orchestrator.handleUserInput(it, isVoice = true) },
            onUnavailable = { android.widget.Toast.makeText(this, "Offline English voice recognition unavailable. Use text input.", android.widget.Toast.LENGTH_LONG).show() },
            onWakeWordDetected = {
                wakeCount++
            },
            onAmplitudeChanged = { amp ->
                micAmplitude = amp
            }
        )

        setContent {
            JarvisTheme {
                val isMicActive by wakeWordDetector.isMicActive.collectAsState()
                JarvisMainScreen(
                    wakeCount = wakeCount,
                    orchestrator = orchestrator,
                    modelManager = app.modelManager,
                    micAmplitude = micAmplitude,
                    isMicActive = isMicActive,
                    onToggleMic = {
                        if (isMicActive) stopListening() else startListening()
                    },
                    onTriggerVision = {
                        triggerVisionAnalysis()
                    }
                )
            }
        }

        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        startListening()
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            wakeWordDetector.startListening()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun stopListening() {
        wakeWordDetector.stopListening()
    }

    private fun triggerVisionAnalysis() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            return
        }
        if (capturing) return
        capturing = true
        cameraManager.capture(this) { frame ->
            lifecycleScope.launch {
                try {
                    val description = if (frame == null) "Camera frame unavailable." else
                        com.phonebox.jarvis.core.ai.PhoneInference.answer("Describe the scene in view", frame)
                    orchestrator.showVisionResult(description)
                } finally {
                    frame?.recycle()
                    capturing = false
                }
            }
        }
    }

    override fun onStop() {
        resumeListening = wakeWordDetector.isMicActive.value
        stopListening()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (resumeListening) {
            resumeListening = false
            startListening()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        ttsEngine.shutdown()
        cameraManager.shutdown()
    }
}

