package com.phonebox.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.phonebox.jarvis.core.vision.CameraManager

class CaptureActivity : ComponentActivity() {
    private lateinit var camera: CameraManager
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capture() else { PanelSession.capture(null, "Camera access was denied."); finish() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        camera = CameraManager(this)
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) capture()
        else if (savedInstanceState == null) permission.launch(Manifest.permission.CAMERA)
    }
    private fun capture() = camera.capture(this) { frame ->
        PanelSession.capture(frame, "Could not capture a front-camera frame.")
        finish()
    }
    override fun onDestroy() { camera.shutdown(); super.onDestroy() }
}
