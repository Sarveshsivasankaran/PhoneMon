package com.phonebox.jarvis.core.ai

import com.phonebox.jarvis.JarvisApplication
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal object PhoneRuntime {
    private data class Weight(val repository: String, val filename: String, val bytes: Long, val hash: String)
    private val text = Weight("Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67", "qwen2.5-0.5b-instruct-q4_k_m.gguf", 491400032, "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db")
    private const val visionRepo = "LiquidAI/LFM2.5-VL-450M-GGUF/resolve/1abed04b6fe71314d8c446a1371c03d7c332266d"
    private val vision = Weight(visionRepo, "LFM2.5-VL-450M-Q4_K_M.gguf", 229313568, "1093f1331319199bbcacdbd7ecc9aa6e5678db6b55073948d0f508be90c8ab68")
    private val projector = Weight(visionRepo, "mmproj-LFM2.5-VL-450m-Q8_0.gguf", 102815168, "ebfc428baa37efad8bae93864f914b2634a09009f91ad59f974fe1a1565d8561")
    val status = MutableStateFlow("Select Set up model to install weights")
    private val lock = Mutex()
    private val directory get() = File(JarvisApplication.instance.noBackupFilesDir, "weights").apply { mkdirs() }

    suspend fun install(model: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                for (weight in if (model == "lfm2.5-vl-450m") listOf(vision, projector) else listOf(text)) {
                    val file = File(directory, weight.filename)
                    if (file.length() == weight.bytes) {
                        status.value = "Checking ${weight.filename}"
                        if (JarvisApplication.instance.modelManager.verifyChecksum(file, weight.hash)) continue
                    }
                    require(directory.usableSpace > weight.bytes + 16777216) { "Insufficient phone storage" }
                    val temp = File(directory, weight.filename + ".part")
                    val digest = MessageDigest.getInstance("SHA-256")
                    val connection = URL("https://huggingface.co/${weight.repository}/${weight.filename}").openConnection()
                    connection.connectTimeout = 15000
                    connection.readTimeout = 45000
                    try {
                        connection.getInputStream().use { input -> temp.outputStream().use { output ->
                            val buffer = ByteArray(131072)
                            var count = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val size = input.read(buffer)
                                if (size < 0) break
                                count += size
                                require(count <= weight.bytes)
                                output.write(buffer, 0, size)
                                digest.update(buffer, 0, size)
                                status.value = "Downloading ${weight.filename}: ${100 * count / weight.bytes}%"
                            }
                            require(count == weight.bytes && digest.digest().joinToString("") { "%02x".format(it) } == weight.hash) { "Model verification failed" }
                        } }
                        check(temp.renameTo(file)) { "Could not save model" }
                    } finally {
                        temp.delete()
                        (connection as? HttpURLConnection)?.disconnect()
                    }
                }
                status.value = "Model installed"
            } catch (e: Exception) { status.value = e.message ?: "Download interrupted" }
        }
    }

    suspend fun infer(model: String, image: Boolean, block: (Int, String) -> String): String = withContext(Dispatchers.IO) {
        lock.withLock {
            val weight = if (image || model == "lfm2.5-vl-450m") vision else text
            val file = File(directory, weight.filename)
            if (file.length() != weight.bytes) return@withLock "Install ${weight.filename} with Set up model."
            val libs = File(JarvisApplication.instance.applicationInfo.nativeLibraryDir)
            val binary = File(libs, "libjarvis-server.so")
            if (!binary.canExecute()) return@withLock "The phone needs a compatible ARM64 runtime."
            val port = ServerSocket(0).use { it.localPort }
            val key = UUID.randomUUID().toString()
            val args = mutableListOf(binary.path, "-m", file.path, "--host", "127.0.0.1", "--port", port.toString(), "--api-key", key, "-c", "2048", "-t", Runtime.getRuntime().availableProcessors().coerceIn(1,4).toString(), "-b", "64", "-ngl", "0")
            if (image) {
                val projection = File(directory, projector.filename)
                if (projection.length() != projector.bytes) return@withLock "Set up the vision model first."
                args.addAll(listOf("--mmproj", projection.path))
            }
            val builder = ProcessBuilder(args).directory(libs).redirectErrorStream(true)
            builder.environment()["LD_LIBRARY_PATH"] = libs.path
            val process = builder.redirectOutput(File("/dev/null")).start()
            try {
                repeat(240) {
                    check(process.isAlive) { "Phone runtime exited while loading the model" }
                    val ready = runCatching {
                        val conn = URL("http://127.0.0.1:$port/health").openConnection() as HttpURLConnection
                        try { conn.connectTimeout = 300; conn.readTimeout = 500; conn.responseCode == 200 } finally { conn.disconnect() }
                    }.getOrDefault(false)
                    if (ready && process.isAlive) return@withLock block(port, key)
                    delay(250)
                }
                "Model load timed out."
            } finally { process.destroyForcibly() }
        }
    }
}
