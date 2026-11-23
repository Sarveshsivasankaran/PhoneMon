package com.phonebox.jarvis.core.ai

import android.content.Context
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * ModelManager oversees model downloads, SHA-256 verification, dynamic sequential paging,
 * memory monitoring, and thermal governor mitigation on the Android phone.
 */
class ModelManager(private val context: Context) {

    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    private val _reasoningState = MutableStateFlow(ModelState("qwen3-1.7b-instruct"))
    val reasoningState: StateFlow<ModelState> = _reasoningState.asStateFlow()

    private val _visionState = MutableStateFlow(ModelState("lfm2.5-vl-450m"))
    val visionState: StateFlow<ModelState> = _visionState.asStateFlow()

    private val _systemMemoryUsageMb = MutableStateFlow(0)
    val systemMemoryUsageMb: StateFlow<Int> = _systemMemoryUsageMb.asStateFlow()

    private val _thermalThrottled = MutableStateFlow(false)
    val thermalThrottled: StateFlow<Boolean> = _thermalThrottled.asStateFlow()

    private val reasoningSpec = ReasoningModelSpec()
    private val visionSpec = VisionModelSpec()

    private val llamaBridge = LlamaBridge.instance
    private val visionEngine = LfmVisionEngine.instance

    init {
        refreshModelStatuses()
        setupThermalGovernor()
    }

    fun getModelsDirectory(): File = modelsDir

    fun refreshModelStatuses() {
        val qwenFile = File(modelsDir, reasoningSpec.fileName)
        if (qwenFile.exists() && qwenFile.length() > 0) {
            _reasoningState.value = _reasoningState.value.copy(
                status = if (llamaBridge.isLoaded()) ModelLoadStatus.LOADED_IN_RAM else ModelLoadStatus.READY_ON_DISK,
                activeMemoryMb = if (llamaBridge.isLoaded()) 1364 else 0
            )
        } else {
            _reasoningState.value = _reasoningState.value.copy(status = ModelLoadStatus.NOT_DOWNLOADED)
        }

        val lfmFile = File(modelsDir, visionSpec.modelFileName)
        val projFile = File(modelsDir, visionSpec.projectorFileName)
        if (lfmFile.exists() && projFile.exists()) {
            _visionState.value = _visionState.value.copy(
                status = if (visionEngine.isLoaded()) ModelLoadStatus.LOADED_IN_RAM else ModelLoadStatus.READY_ON_DISK,
                activeMemoryMb = if (visionEngine.isLoaded()) 460 else 0
            )
        } else {
            _visionState.value = _visionState.value.copy(status = ModelLoadStatus.NOT_DOWNLOADED)
        }
    }

    /**
     * Loads Qwen3 1.7B into memory, ensuring memory safety on Android phone.
     */
    suspend fun loadReasoningModel(): Boolean = withContext(Dispatchers.IO) {
        val qwenFile = File(modelsDir, reasoningSpec.fileName)
        if (!qwenFile.exists()) {
            _reasoningState.value = _reasoningState.value.copy(
                status = ModelLoadStatus.FAILED,
                errorMessage = "Model weights not downloaded."
            )
            return@withContext false
        }

        // If vision model is active and total RAM budget is constrained, page out vision model
        if (visionEngine.isLoaded()) {
            Log.i(TAG, "Arbitrating memory: Unloading LFM2.5-VL to prioritize Qwen3 1.7B.")
            visionEngine.unloadVisionModel()
            _visionState.value = _visionState.value.copy(
                status = ModelLoadStatus.READY_ON_DISK,
                activeMemoryMb = 0
            )
        }

        _reasoningState.value = _reasoningState.value.copy(status = ModelLoadStatus.LOADING)
        val success = llamaBridge.loadModel(qwenFile, reasoningSpec)
        if (success) {
            _reasoningState.value = _reasoningState.value.copy(
                status = ModelLoadStatus.LOADED_IN_RAM,
                activeMemoryMb = 1364
            )
            updateTotalMemoryUsage()
        } else {
            _reasoningState.value = _reasoningState.value.copy(
                status = ModelLoadStatus.FAILED,
                errorMessage = "Failed to map GGUF weights in native llama.cpp"
            )
        }
        success
    }

    /**
     * Sequentially loads LFM2.5-VL for vision tasks, temporarily paging out reasoning model if necessary.
     */
    suspend fun loadVisionModel(requireFullMemorySwap: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val lfmFile = File(modelsDir, visionSpec.modelFileName)
        val projFile = File(modelsDir, visionSpec.projectorFileName)
        if (!lfmFile.exists() || !projFile.exists()) {
            _visionState.value = _visionState.value.copy(
                status = ModelLoadStatus.FAILED,
                errorMessage = "Vision weights not found on disk."
            )
            return@withContext false
        }

        if (requireFullMemorySwap && llamaBridge.isLoaded()) {
            Log.i(TAG, "RAM safety threshold reached: Unloading Qwen3 to load LFM2.5-VL.")
            llamaBridge.unloadModel()
            _reasoningState.value = _reasoningState.value.copy(
                status = ModelLoadStatus.READY_ON_DISK,
                activeMemoryMb = 0
            )
        }

        _visionState.value = _visionState.value.copy(status = ModelLoadStatus.LOADING)
        val success = visionEngine.loadVisionModel(lfmFile, projFile)
        if (success) {
            _visionState.value = _visionState.value.copy(
                status = ModelLoadStatus.LOADED_IN_RAM,
                activeMemoryMb = 460
            )
            updateTotalMemoryUsage()
        } else {
            _visionState.value = _visionState.value.copy(
                status = ModelLoadStatus.FAILED,
                errorMessage = "Failed to load vision projector weights"
            )
        }
        success
    }

    /**
     * Download manager with progress reporting and cancellation.
     */
    suspend fun downloadModel(
        urlStr: String,
        targetFile: File,
        expectedSizeBytes: Long,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode !in 200..299) {
                Log.e(TAG, "Server returned HTTP ${connection.responseCode}")
                return@withContext false
            }

            val totalBytes = if (connection.contentLengthLong > 0) connection.contentLengthLong else expectedSizeBytes
            val input = connection.inputStream
            val output = FileOutputStream(targetFile)

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var totalRead: Long = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                val progress = totalRead.toFloat() / totalBytes.toFloat()
                onProgress(progress)
            }

            output.flush()
            output.close()
            input.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${targetFile.name}", e)
            targetFile.delete()
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * SHA-256 integrity check.
     */
    fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val calculatedHex = digest.digest().joinToString("") { "%02x".format(it) }
        val matches = calculatedHex.equals(expectedSha256, ignoreCase = true)
        Log.i(TAG, "File ${file.name} checksum check: $matches (Calculated: $calculatedHex)")
        return matches
    }

    private fun updateTotalMemoryUsage() {
        val totalMb = _reasoningState.value.activeMemoryMb + _visionState.value.activeMemoryMb + 180 // App UI & Audio overhead
        _systemMemoryUsageMb.value = totalMb
    }

    private fun setupThermalGovernor() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            powerManager.addThermalStatusListener { status ->
                val isThrottled = status >= PowerManager.THERMAL_STATUS_MODERATE
                _thermalThrottled.value = isThrottled
                if (isThrottled) {
                    Log.w(TAG, "Thermal status MODERATE or higher. Governor reducing thread count to 2.")
                }
            }
        }
    }

    companion object {
        private const val TAG = "JarvisModelManager"
    }
}

