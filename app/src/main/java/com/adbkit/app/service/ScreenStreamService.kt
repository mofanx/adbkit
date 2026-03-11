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
 * Screen streaming service using screenrecord H.264 + MediaCodec,
 * similar to scrcpy's approach for low-latency screen mirroring.
 *
 * Flow: adb exec-out screenrecord --output-format=h264 -> InputStream -> MediaCodec -> Surface
 *
 * Falls back to optimized screencap (JPEG) if H.264 streaming is not available.
 */
class ScreenStreamService {

    data class StreamConfig(
        val width: Int = 720,
        val height: Int = 1280,
        val bitrate: Int = 8_000_000,
        val maxFps: Int = 30,
        val useH264: Boolean = true
    )

    data class StreamState(
        val isStreaming: Boolean = false,
        val fps: Int = 0,
        val streamMode: String = "none", // "h264", "mjpeg", "none"
        val error: String = ""
    )

    private val _state = MutableStateFlow(StreamState())
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private var streamProcess: Process? = null
    private var decoderJob: Job? = null
    private var codec: MediaCodec? = null
    private val isRunning = AtomicBoolean(false)
    private var frameCount = 0
    private var lastFpsTime = 0L

    // For screencap fallback
    private val _screenshotBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val screenshotBitmap: StateFlow<android.graphics.Bitmap?> = _screenshotBitmap.asStateFlow()

    /**
     * Start H.264 video stream, decoded by MediaCodec and rendered to the given Surface.
     */
    fun startStream(surface: Surface, config: StreamConfig, scope: CoroutineScope) {
        if (isRunning.getAndSet(true)) return

        frameCount = 0
        lastFpsTime = System.currentTimeMillis()

        decoderJob = scope.launch(Dispatchers.IO) {
            try {
                // Try H.264 streaming first
                if (config.useH264 && startH264Stream(surface, config)) {
                    return@launch
                }
                // Fallback to screencap JPEG loop
                startScreencapFallback(config)
            } catch (e: CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Stream error")
            } finally {
                cleanup()
                isRunning.set(false)
                _state.value = _state.value.copy(isStreaming = false, streamMode = "none")
            }
        }
    }

    private suspend fun startH264Stream(surface: Surface, config: StreamConfig): Boolean {
        val device = AdbService.getCurrentDevice()
        val adbPath = AdbService.getAdbPath()
        val sizeArg = "${config.width}x${config.height}"

        val cmd = buildString {
            append(adbPath)
            if (device != null) append(" -s $device")
            append(" exec-out screenrecord")
            append(" --output-format=h264")
            append(" --size $sizeArg")
            append(" --bit-rate ${config.bitrate}")
            append(" -")
        }

        val pb = ProcessBuilder("sh", "-c", cmd)
        pb.environment()["HOME"] = AdbKitApplication.instance.filesDir.absolutePath
        pb.environment()["TMPDIR"] = AdbKitApplication.instance.cacheDir.absolutePath

        try {
            streamProcess = pb.start()
        } catch (e: Exception) {
            return false
        }

        val inputStream = streamProcess!!.inputStream

        // Check if we get data within 2 seconds (screenrecord may fail on some devices)
        val testBuffer = ByteArray(64)
        var bytesRead: Int
        try {
            withTimeout(2000) {
                bytesRead = withContext(Dispatchers.IO) { inputStream.read(testBuffer) }
            }
        } catch (e: Exception) {
            streamProcess?.destroyForcibly()
            streamProcess = null
            return false
        }

        if (bytesRead <= 0) {
            streamProcess?.destroyForcibly()
            streamProcess = null
            return false
        }

        // Initialize MediaCodec H.264 decoder
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, config.width, config.height)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, config.width * config.height)

        try {
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec!!.configure(format, surface, null, 0)
            codec!!.start()
        } catch (e: Exception) {
            streamProcess?.destroyForcibly()
            streamProcess = null
            return false
        }

        _state.value = StreamState(isStreaming = true, streamMode = "h264")

        // Feed H.264 NAL units to MediaCodec
        try {
            // First, feed the initial bytes we already read
            feedToCodec(testBuffer, 0, bytesRead)
            // Then continuously read and feed
            decodeH264Loop(inputStream)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Stream ended or error - try restart
            if (isRunning.get()) {
                // screenrecord has a 3-minute limit, restart
                cleanup()
                delay(200)
                if (isRunning.get()) {
                    return startH264Stream(surface, config)
                }
            }
        }
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
            feedToCodec(buffer, 0, bytesRead)
            drainCodecOutput()
        }
    }

    private fun feedToCodec(data: ByteArray, offset: Int, length: Int) {
        val codec = this.codec ?: return
        var pos = offset
        while (pos < offset + length) {
            val inputIndex = codec.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                val chunk = minOf(length - (pos - offset), inputBuffer.remaining())
                inputBuffer.put(data, pos, chunk)
                codec.queueInputBuffer(inputIndex, 0, chunk, System.nanoTime() / 1000, 0)
                pos += chunk
            } else {
                // Input buffer not available, drain output first
                drainCodecOutput()
            }
        }
    }

    private fun drainCodecOutput() {
        val codec = this.codec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            if (outputIndex >= 0) {
                // Render to surface (true = render)
                codec.releaseOutputBuffer(outputIndex, true)
                updateFps()
            } else {
                break
            }
        }
    }

    /**
     * Fallback: screencap PNG loop with bitmap subsampling.
     */
    private suspend fun startScreencapFallback(config: StreamConfig) {
        _state.value = StreamState(isStreaming = true, streamMode = "mjpeg")

        val device = AdbService.getCurrentDevice()
        val adbPath = AdbService.getAdbPath()
        val interval = 1000L / config.maxFps.coerceIn(1, 30)

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
                        // Subsample if image is much larger than target
                        inSampleSize = if (config.width < 720) 2 else 1
                    }
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    if (bitmap != null) {
                        _screenshotBitmap.value = bitmap
                        updateFps()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ignore individual capture errors
            }
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
        cleanup()
    }

    private fun cleanup() {
        try { streamProcess?.destroyForcibly() } catch (_: Exception) {}
        streamProcess = null
        try {
            codec?.stop()
            codec?.release()
        } catch (_: Exception) {}
        codec = null
    }

    fun isStreaming(): Boolean = isRunning.get()
}
