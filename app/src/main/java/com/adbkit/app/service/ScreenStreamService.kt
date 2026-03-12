package com.adbkit.app.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import com.adbkit.app.AdbKitApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scrcpy-compatible screen streaming service.
 *
 * Protocol:
 *   1. 8-byte video header: [width(4B BE)][height(4B BE)]
 *   2. Per packet: 12-byte meta [pts_flags(8B BE)][size(4B BE)] + [data]
 *      - bit 63 = config (SPS/PPS), bit 62 = keyframe
 *
 * No screenshot fallback — H.264 only.
 */
class ScreenStreamService {

    data class StreamConfig(
        val maxSize: Int = 720,
        val bitrate: Int = 8_000_000,
        val audioEnabled: Boolean = false
    )

    data class StreamState(
        val isStreaming: Boolean = false,
        val fps: Int = 0,
        val streamMode: String = "none", // "h264", "none"
        val error: String = "",
        val videoWidth: Int = 0,
        val videoHeight: Int = 0
    )

    companion object {
        private const val TAG = "ScreenStream"
        private const val SERVER_DEX = "screen-server.dex"
        private const val DEVICE_SERVER_PATH = "/data/local/tmp/adbkit-server.dex"
        private const val DEVICE_SERVER_LOG = "/data/local/tmp/adbkit-server.log"
        private const val PACKET_FLAG_CONFIG = 1L shl 63
        private const val PACKET_FLAG_KEY_FRAME = 1L shl 62
        private const val PACKET_FLAG_AUDIO = 1L shl 61
        private const val AUDIO_SAMPLE_RATE = 48000
    }

    private val _state = MutableStateFlow(StreamState())
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private var serverProcess: Process? = null
    private var stderrJob: Job? = null
    private var decoderJob: Job? = null
    private var codec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private val isRunning = AtomicBoolean(false)
    private var frameCount = 0
    private var lastFpsTime = 0L
    private var serverPushed = false

    fun startStream(surface: Surface, config: StreamConfig, scope: CoroutineScope) {
        stopStream()

        isRunning.set(true)
        frameCount = 0
        lastFpsTime = System.currentTimeMillis()

        decoderJob = scope.launch(Dispatchers.IO) {
            try {
                startServerStream(surface, config, scope)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Stream error", e)
                _state.value = _state.value.copy(error = e.message ?: "Stream error")
            } finally {
                cleanupAll()
                isRunning.set(false)
                _state.value = _state.value.copy(isStreaming = false, streamMode = "none")
            }
        }
    }

    private suspend fun ensureServerPushed() {
        if (serverPushed) return
        val context = AdbKitApplication.instance
        val device = AdbService.getCurrentDevice()
        val adbPath = AdbService.getAdbPath()

        val tempFile = java.io.File(context.cacheDir, SERVER_DEX)
        context.assets.open(SERVER_DEX).use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }

        val cmd = buildString {
            append(adbPath)
            if (device != null) append(" -s $device")
            append(" push '${tempFile.absolutePath}' '$DEVICE_SERVER_PATH'")
        }
        Log.d(TAG, "Push DEX: $cmd")
        val result = AdbService.executeCommand(cmd)
        if (!result.success) throw RuntimeException("Push failed: ${result.error}")
        Log.d(TAG, "DEX pushed")
        serverPushed = true
    }

    private suspend fun startServerStream(surface: Surface, config: StreamConfig, scope: CoroutineScope) {
        ensureServerPushed()

        val device = AdbService.getCurrentDevice()
        val adbPath = AdbService.getAdbPath()

        // Kill any existing server
        try { AdbService.shell("pkill -f 'app_process.*ScreenServer'") } catch (_: Exception) {}

        // CRITICAL: redirect device stderr to log file, otherwise exec-out merges
        // stderr into stdout, corrupting the binary H.264 stream
        val audioFlag = if (config.audioEnabled) "1" else "0"
        val serverCmd = "CLASSPATH=$DEVICE_SERVER_PATH app_process / ScreenServer" +
                " ${config.maxSize} ${config.bitrate} $audioFlag" +
                " 2>$DEVICE_SERVER_LOG"
        val cmd = buildString {
            append(adbPath)
            if (device != null) append(" -s $device")
            append(" exec-out sh -c '$serverCmd'")
        }

        Log.d(TAG, "Start server: $cmd")

        val pb = ProcessBuilder("sh", "-c", cmd)
        pb.environment()["HOME"] = AdbKitApplication.instance.filesDir.absolutePath
        pb.environment()["TMPDIR"] = AdbKitApplication.instance.cacheDir.absolutePath
        serverProcess = pb.start()

        // Consume local adb stderr
        stderrJob = scope.launch(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(serverProcess!!.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d(TAG, "adb: $line")
                    }
                }
            } catch (_: Exception) {}
        }

        val dis = DataInputStream(BufferedInputStream(serverProcess!!.inputStream, 65536))

        // Read 8-byte video header
        val header = ByteArray(8)
        var headerRead = 0
        try {
            withTimeout(15000) {
                while (headerRead < 8) {
                    val n = withContext(Dispatchers.IO) { dis.read(header, headerRead, 8 - headerRead) }
                    if (n <= 0) break
                    headerRead += n
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Header timeout ($headerRead bytes read)", e)
            readServerLog()
            throw RuntimeException("Server header timeout")
        }

        if (headerRead < 8) {
            readServerLog()
            throw RuntimeException("Server header incomplete: $headerRead bytes")
        }

        val videoWidth = readBE32(header, 0)
        val videoHeight = readBE32(header, 4)

        if (videoWidth <= 0 || videoHeight <= 0 || videoWidth > 4096 || videoHeight > 4096) {
            readServerLog()
            throw RuntimeException("Invalid dimensions: ${videoWidth}x${videoHeight}")
        }

        Log.d(TAG, "Video: ${videoWidth}x${videoHeight}")

        // Configure decoder
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, videoWidth * videoHeight)
        try { format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1) } catch (_: Exception) {}

        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec!!.configure(format, surface, null, 0)
        codec!!.start()

        _state.value = StreamState(
            isStreaming = true, streamMode = "h264",
            videoWidth = videoWidth, videoHeight = videoHeight
        )

        // Packet-based decode loop (scrcpy protocol)
        decodePacketLoop(dis)
    }

    /**
     * Read framed packets from the server stream.
     * Each packet: 12-byte meta [pts_flags(8B)][size(4B)] + [data]
     */
    private suspend fun decodePacketLoop(dis: DataInputStream) {
        val metaBuf = ByteArray(12)
        val ptsMask = PACKET_FLAG_CONFIG - 1 and (PACKET_FLAG_KEY_FRAME - 1) and (PACKET_FLAG_AUDIO - 1)

        while (isRunning.get() && currentCoroutineContext().isActive) {
            val metaRead = readFully(dis, metaBuf, 0, 12)
            if (metaRead < 12) break

            val ptsAndFlags = readBE64(metaBuf, 0)
            val packetSize = readBE32(metaBuf, 8)

            if (packetSize <= 0 || packetSize > 4 * 1024 * 1024) {
                Log.w(TAG, "Invalid packet size: $packetSize")
                break
            }

            val packetData = ByteArray(packetSize)
            val dataRead = readFully(dis, packetData, 0, packetSize)
            if (dataRead < packetSize) break

            val isAudio = (ptsAndFlags and PACKET_FLAG_AUDIO) != 0L
            val isConfig = (ptsAndFlags and PACKET_FLAG_CONFIG) != 0L
            val isKeyFrame = (ptsAndFlags and PACKET_FLAG_KEY_FRAME) != 0L
            val pts = if (isConfig) 0L else (ptsAndFlags and ptsMask)

            if (isAudio) {
                feedAudioPacket(packetData, pts, isConfig)
            } else {
                feedPacket(packetData, pts, isConfig, isKeyFrame)
            }
        }
    }

    private suspend fun readFully(dis: DataInputStream, buf: ByteArray, off: Int, len: Int): Int {
        var total = 0
        while (total < len && isRunning.get()) {
            val n = withContext(Dispatchers.IO) {
                try { dis.read(buf, off + total, len - total) } catch (_: Exception) { -1 }
            }
            if (n <= 0) return total
            total += n
        }
        return total
    }

    private fun feedPacket(data: ByteArray, pts: Long, isConfig: Boolean, isKeyFrame: Boolean) {
        val codec = this.codec ?: return

        val inputIndex = codec.dequeueInputBuffer(10_000)
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            inputBuffer.put(data, 0, data.size)

            val flags = when {
                isConfig -> MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                isKeyFrame -> MediaCodec.BUFFER_FLAG_KEY_FRAME
                else -> 0
            }
            codec.queueInputBuffer(inputIndex, 0, data.size, pts, flags)
        }

        drainDecoderOutput()
    }

    private fun feedAudioPacket(data: ByteArray, pts: Long, isConfig: Boolean) {
        if (isConfig) {
            // Initialize audio decoder and AudioTrack from config data
            try {
                if (audioCodec == null) {
                    val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_SAMPLE_RATE, 2)
                    format.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(data))
                    audioCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                    audioCodec!!.configure(format, null, null, 0)
                    audioCodec!!.start()

                    val bufSize = AudioTrack.getMinBufferSize(AUDIO_SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                    audioTrack = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                        .setAudioFormat(AudioFormat.Builder()
                            .setSampleRate(AUDIO_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build())
                        .setBufferSizeInBytes(bufSize.coerceAtLeast(4096) * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                    audioTrack!!.play()
                    Log.d(TAG, "Audio decoder+track initialized")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio init failed", e)
            }
            return
        }

        val ac = audioCodec ?: return
        val at = audioTrack ?: return

        // Feed AAC to decoder
        val inputIndex = ac.dequeueInputBuffer(5_000)
        if (inputIndex >= 0) {
            val inputBuffer = ac.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            inputBuffer.put(data, 0, data.size)
            ac.queueInputBuffer(inputIndex, 0, data.size, pts, 0)
        }

        // Drain decoded PCM to AudioTrack
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = try { ac.dequeueOutputBuffer(info, 1_000) } catch (_: Exception) { break }
            if (idx >= 0) {
                val outBuf = ac.getOutputBuffer(idx)
                if (outBuf != null && info.size > 0) {
                    val pcm = ByteArray(info.size)
                    outBuf.position(info.offset)
                    outBuf.get(pcm)
                    at.write(pcm, 0, pcm.size)
                }
                ac.releaseOutputBuffer(idx, false)
            } else {
                break
            }
        }
    }

    private fun drainDecoderOutput() {
        val codec = this.codec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val idx = try {
                codec.dequeueOutputBuffer(bufferInfo, 1_000)
            } catch (_: Exception) { break }
            if (idx >= 0) {
                codec.releaseOutputBuffer(idx, true)
                updateFps()
            } else {
                break
            }
        }
    }

    private fun updateFps() {
        frameCount++
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsTime
        if (elapsed >= 1000) {
            _state.value = _state.value.copy(fps = (frameCount * 1000L / elapsed).toInt())
            frameCount = 0
            lastFpsTime = now
        }
    }

    fun stopStream() {
        isRunning.set(false)
        decoderJob?.cancel()
        decoderJob = null
        stderrJob?.cancel()
        stderrJob = null
        cleanupAll()
    }

    private suspend fun readServerLog() {
        try {
            val result = AdbService.shell("cat $DEVICE_SERVER_LOG 2>/dev/null")
            if (result.success && result.output.isNotBlank()) {
                result.output.lines().forEach { line ->
                    if (line.isNotBlank()) Log.d(TAG, "ServerLog: $line")
                }
            }
        } catch (_: Exception) {}
    }

    private fun cleanupAll() {
        try { serverProcess?.destroyForcibly() } catch (_: Exception) {}
        serverProcess = null
        try { codec?.stop(); codec?.release() } catch (_: Exception) {}
        codec = null
        try { audioCodec?.stop(); audioCodec?.release() } catch (_: Exception) {}
        audioCodec = null
        try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
    }

    fun isStreaming(): Boolean = isRunning.get()

    private fun readBE32(buf: ByteArray, off: Int): Int {
        return ((buf[off].toInt() and 0xFF) shl 24) or
                ((buf[off + 1].toInt() and 0xFF) shl 16) or
                ((buf[off + 2].toInt() and 0xFF) shl 8) or
                (buf[off + 3].toInt() and 0xFF)
    }

    private fun readBE64(buf: ByteArray, off: Int): Long {
        return ((buf[off].toLong() and 0xFF) shl 56) or
                ((buf[off + 1].toLong() and 0xFF) shl 48) or
                ((buf[off + 2].toLong() and 0xFF) shl 40) or
                ((buf[off + 3].toLong() and 0xFF) shl 32) or
                ((buf[off + 4].toLong() and 0xFF) shl 24) or
                ((buf[off + 5].toLong() and 0xFF) shl 16) or
                ((buf[off + 6].toLong() and 0xFF) shl 8) or
                (buf[off + 7].toLong() and 0xFF)
    }
}
