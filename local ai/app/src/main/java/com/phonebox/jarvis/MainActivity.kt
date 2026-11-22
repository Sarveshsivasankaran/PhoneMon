package com.phonebox.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import com.phonebox.jarvis.core.ai.LfmVisionEngine
import com.phonebox.jarvis.core.ai.LlamaBridge
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
    private val visionEngine = LfmVisionEngine.instance

    private var micAmplitude by mutableFloatStateOf(0f)
    private var isMicActive by mutableStateOf(false)
    private var latestCameraFrame: Bitmap? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
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
            llamaBridge = LlamaBridge.instance,
            memoryManager = memoryManager,
            permissionManager = permissionManager,
            phoneMonAdapter = phoneMonAdapter,
            ttsEngine = ttsEngine
        )

        cameraManager = CameraManager(this)
        cameraManager.initialize(this) { frame ->
            latestCameraFrame = frame
        }

        wakeWordDetector = WakeWordDetector(
            onWakeWordDetected = {
                orchestrator.setState(JarvisState.LISTENING)
                ttsEngine.speak("Yes? I am listening.")
            },
            onAmplitudeChanged = { amp ->
                micAmplitude = amp
            }
        )

        setContent {
            JarvisTheme {
                JarvisMainScreen(
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
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startListening()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun startListening() {
        wakeWordDetector.startListening(lifecycleScope)
        isMicActive = true
    }

    private fun stopListening() {
        wakeWordDetector.stopListening()
        isMicActive = false
    }

    private fun triggerVisionAnalysis() {
        val frame = latestCameraFrame
        if (frame != null) {
            orchestrator.setState(JarvisState.VISION)
            lifecycleScope.launch {
                val description = visionEngine.analyzeFrame(frame, "Describe the scene in view")
                orchestrator.handleUserInput("I am showing you the camera: $description", isVoice = true)
            }
        } else {
            orchestrator.handleUserInput("Camera sensor is activating, please point toward the subject.", isVoice = true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        ttsEngine.shutdown()
        cameraManager.shutdown()
    }
}

