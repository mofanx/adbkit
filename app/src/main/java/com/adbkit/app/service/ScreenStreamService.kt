package com.adbkit.app.service

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.adbkit.app.AdbKitApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Screen streaming service using a scrcpy-like approach:
 *
 * 1. Push a ScreenServer DEX to the target device
 * 2. Run it via `app_process` — it uses SurfaceControl + MediaCodec encoder
 *    to capture the screen and output low-latency H.264 to stdout
 * 3. Decode the H.264 stream locally with MediaCodec and render to Surface
 *
 * Falls back to screencap JPEG loop if the server fails to start.
 */
class ScreenStreamService {

    data class StreamConfig(
        val maxSize: Int = 720,
        val bitrate: Int = 8_000_000,
        val maxFps: Int = 30
    )

    data class StreamState(
        val isStreaming: Boolean = false,
        val fps: Int = 0,
        val streamMode: String = "none", // "h264", "mjpeg", "none"
        val error: String = "",
        val videoWidth: Int = 0,
        val videoHeight: Int = 0
    )

    companion object {
        private const val SERVER_DEX = "screen-server.dex"
        private const val DEVICE_SERVER_PATH = "/data/local/tmp/adbkit-server.dex"
    }

    private val _state = MutableStateFlow(StreamState())
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private var serverProcess: Process? = null
    private var decoderJob: Job? = null
    private var codec: MediaCodec? = null
    private val isRunning = AtomicBoolean(false)
    private var frameCount = 0
    private var lastFpsTime = 0L
    private var serverPushed = false

    // For screencap fallback
    private val _screenshotBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val screenshotBitmap: StateFlow<android.graphics.Bitmap?> = _screenshotBitmap.asStateFlow()

    fun startStream(surface: Surface, config: StreamConfig, scope: CoroutineScope) {
        stopStream()

        isRunning.set(true)
        frameCount = 0
        lastFpsTime = System.currentTimeMillis()

        decoderJob = scope.launch(Dispatchers.IO) {
            try {
                if (tryStartServerStream(surface, config)) {
                    return@launch
                }
                // Fallback to screencap
                startScreencapFallback(config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Stream error")
            } finally {
                cleanupAll()
                isRunning.set(false)
                _state.value = _state.value.copy(isStreaming = false, streamMode = "none")
            }
        }
    }

    /**
     * Push server DEX to device if not already done.
     */
    private suspend fun ensureServerPushed() {
        if (serverPushed) return
        val context = AdbKitApplication.instance
        val device = AdbService.getCurrentDevice()
        val adbPath = AdbService.getAdbPath()

        // Extract DEX from assets to local temp file
        val tempFile = java.io.File(context.cacheDir, SERVER_DEX)
        try {
            context.assets.open(SERVER_DEX).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to extract server DEX from assets: ${e.message}")
        }

        // Push to device
        val cmd = buildString {
            append(adbPath)
            if (device != null) append(" -s $device")
            append(" push ${tempFile.absolutePath} $DEVICE_SERVER_PATH")
        }
        val result = AdbService.executeCommand(cmd)
        if (!result.success) {
            throw RuntimeException("Failed to push server: ${result.error}")
        }
        serverPushed = true
    }

    /**
     * Start the ScreenServer on the device and decode its H.264 output.
     */
    private suspend fun tryStartServerStream(surface: Surface, config: StreamConfig): Boolean {
        try {
            ensureServerPushed()
        } catch (e: Exception) {
            // Server DEX not available, fall back
            return false
        }

        val device = AdbService.getCurrentDevice()
        val adbPath = AdbService.getAdbPath()

        // Run server via app_process
        val cmd = buildString {
            append(adbPath)
            if (device != null) append(" -s $device")
            append(" exec-out CLASSPATH=$DEVICE_SERVER_PATH")
            append(" app_process / ScreenServer")
            append(" ${config.maxSize}")
            append(" ${config.bitrate}")
            append(" ${config.maxFps}")
        }

        val pb = ProcessBuilder("sh", "-c", cmd)
        pb.environment()["HOME"] = AdbKitApplication.instance.filesDir.absolutePath
        pb.environment()["TMPDIR"] = AdbKitApplication.instance.cacheDir.absolutePath

        try {
            serverProcess = pb.start()
        } catch (e: Exception) {
            return false
        }

        val inputStream = serverProcess!!.inputStream

        // Read 8-byte header: [width(4 bytes BE)][height(4 bytes BE)]
        val header = ByteArray(8)
        var headerRead = 0
        try {
            withTimeout(8000) {
                while (headerRead < 8) {
                    val n = withContext(Dispatchers.IO) { inputStream.read(header, headerRead, 8 - headerRead) }
                    if (n <= 0) break
                    headerRead += n
                }
            }
        } catch (e: Exception) {
            serverProcess?.destroyForcibly()
            serverProcess = null
            return false
        }

        if (headerRead < 8) {
            serverProcess?.destroyForcibly()
            serverProcess = null
            return false
        }

        val videoWidth = ((header[0].toInt() and 0xFF) shl 24) or
                ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
        val videoHeight = ((header[4].toInt() and 0xFF) shl 24) or
                ((header[5].toInt() and 0xFF) shl 16) or
                ((header[6].toInt() and 0xFF) shl 8) or
                (header[7].toInt() and 0xFF)

        if (videoWidth <= 0 || videoHeight <= 0 || videoWidth > 4096 || videoHeight > 4096) {
            serverProcess?.destroyForcibly()
            serverProcess = null
            return false
        }

        // Configure MediaCodec decoder
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, videoWidth * videoHeight)
        try {
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        } catch (_: Exception) {}

        try {
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec!!.configure(format, surface, null, 0)
            codec!!.start()
        } catch (e: Exception) {
            serverProcess?.destroyForcibly()
            serverProcess = null
            return false
        }

        _state.value = StreamState(isStreaming = true, streamMode = "h264", videoWidth = videoWidth, videoHeight = videoHeight)

        // Main decode loop
        decodeH264Loop(inputStream)
        return true
    }

    private suspend fun decodeH264Loop(inputStream: InputStream) {
        val buffer = ByteArray(65536)
        while (isRunning.get() && currentCoroutineContext().isActive) {
            val bytesRead = withContext(Dispatchers.IO) {
                try {
                    inputStream.read(buffer)
                } catch (e: Exception) {
                    -1
                }
            }
            if (bytesRead <= 0) break
            feedRawBytes(buffer, bytesRead)
        }
    }

    private fun feedRawBytes(data: ByteArray, length: Int) {
        val codec = this.codec ?: return
        var pos = 0
        while (pos < length && isRunning.get()) {
            val inputIndex = codec.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex) ?: break
                inputBuffer.clear()
                val chunk = minOf(length - pos, inputBuffer.remaining())
                inputBuffer.put(data, pos, chunk)
                codec.queueInputBuffer(inputIndex, 0, chunk, System.nanoTime() / 1000, 0)
                pos += chunk
            }
            drainCodecOutput()
        }
    }

    private fun drainCodecOutput() {
        val codec = this.codec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = try {
                codec.dequeueOutputBuffer(bufferInfo, 1_000)
            } catch (e: Exception) {
                break
            }
            if (outputIndex >= 0) {
                codec.releaseOutputBuffer(outputIndex, true)
                updateFps()
            } else {
                break
            }
        }
    }

    /**
     * Fallback: screencap PNG loop.
     */
    private suspend fun startScreencapFallback(config: StreamConfig) {
        _state.value = StreamState(isStreaming = true, streamMode = "mjpeg")

        val device = AdbService.getCurrentDevice()
        val adbPath = AdbService.getAdbPath()
        val interval = 1000L / config.maxFps.coerceIn(1, 10)

        while (isRunning.get() && currentCoroutineContext().isActive) {
            try {
                val cmd = buildString {
                    append(adbPath)
                    if (device != null) append(" -s $device")
                    append(" exec-out screencap -p")
                }
                val pb = ProcessBuilder("sh", "-c", cmd)
                pb.environment()["HOME"] = AdbKitApplication.instance.filesDir.absolutePath
                pb.environment()["TMPDIR"] = AdbKitApplication.instance.cacheDir.absolutePath
                val process = pb.start()
                val bytes = process.inputStream.readBytes()
                process.waitFor()
                if (bytes.size > 100) {
                    val opts = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = if (config.maxSize < 720) 2 else 1
                    }
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    if (bitmap != null) {
                        _screenshotBitmap.value = bitmap
                        updateFps()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            delay(interval)
        }
    }

    private fun updateFps() {
        frameCount++
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsTime
        if (elapsed >= 1000) {
            val fps = (frameCount * 1000L / elapsed).toInt()
            _state.value = _state.value.copy(fps = fps)
            frameCount = 0
            lastFpsTime = now
        }
    }

    fun stopStream() {
        isRunning.set(false)
        decoderJob?.cancel()
        decoderJob = null
        cleanupAll()
    }

    private fun cleanupAll() {
        try { serverProcess?.destroyForcibly() } catch (_: Exception) {}
        serverProcess = null
        try {
            codec?.stop()
            codec?.release()
        } catch (_: Exception) {}
        codec = null
    }

    fun isStreaming(): Boolean = isRunning.get()
}
