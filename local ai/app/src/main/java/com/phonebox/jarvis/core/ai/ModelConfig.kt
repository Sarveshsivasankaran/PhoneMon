package com.phonebox.jarvis.core.ai

/**
 * Hardware and model runtime configuration data structures.
 */
data class DeviceProfile(
    val targetDevice: String = "Android device",
    val soc: String = "Android platform",
    val architecture: String = "arm64-v8a",
    val totalRamMb: Int = 6144,
    val maxWorkingBudgetMb: Int = 1800,
    val recommendedThreads: Int = 4,
    val pinCores: List<Int> = listOf(4, 5, 6, 7)
)

data class ReasoningModelSpec(
    val id: String = "qwen3-1.7b-instruct",
    val name: String = "Qwen3 1.7B Instruct",
    val format: String = "GGUF",
    val quantization: String = "Q4_K_M",
    val contextLength: Int = 2048,
    val fileName: String = "qwen3-1.7b-instruct-q4_k_m.gguf",
    val fileSizeBytes: Long = 1184890880L,
    val sha256: String = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    val downloadUrl: String = "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/qwen3-1.7b-instruct-q4_k_m.gguf",
    val temperature: Float = 0.7f,
    val topP: Float = 0.8f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val nThreads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
    val nBatch: Int = 64
)

data class VisionModelSpec(
    val id: String = "lfm2.5-vl-450m",
    val name: String = "LFM2.5-VL 450M",
    val format: String = "GGUF",
    val quantization: String = "Q4_K_M",
    val contextLength: Int = 1024,
    val modelFileName: String = "lfm2.5-vl-450m-q4_k_m.gguf",
    val projectorFileName: String = "lfm2.5-vl-450m-mmproj-f16.gguf",
    val modelSizeBytes: Long = 367001600L,
    val projectorSizeBytes: Long = 94371840L,
    val downloadUrlModel: String = "https://huggingface.co/LiquidAI/LFM2.5-VL-450M-GGUF/resolve/main/lfm2.5-vl-450m-q4_k_m.gguf",
    val downloadUrlProjector: String = "https://huggingface.co/LiquidAI/LFM2.5-VL-450M-GGUF/resolve/main/lfm2.5-vl-450m-mmproj-f16.gguf",
    val imageWidth: Int = 384,
    val imageHeight: Int = 384
)

enum class ModelLoadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    VERIFYING,
    READY_ON_DISK,
    LOADING,
    LOADED_IN_RAM,
    FAILED
}

data class ModelState(
    val modelId: String,
    val status: ModelLoadStatus = ModelLoadStatus.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val activeMemoryMb: Int = 0,
    val errorMessage: String? = null
)

