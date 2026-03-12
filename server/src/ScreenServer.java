import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Scrcpy-compatible screen capture server for ADBKit.
 * Runs on the target device via: app_process / ScreenServer [maxSize] [bitrate] [audio]
 *
 * Protocol (matching scrcpy):
 *   1. Video header:  8 bytes [width(4B BE)][height(4B BE)]
 *   2. Each packet:  12 bytes meta [pts_flags(8B BE)][packet_size(4B BE)] + [packet_data]
 *      - pts_flags bit 63: config packet (SPS/PPS)
 *      - pts_flags bit 62: key frame
 *      - pts_flags bit 61: audio packet
 *      - remaining bits: presentation timestamp in microseconds
 *
 * Architecture (mirrors scrcpy SurfaceEncoder):
 *   1. Get display info via DisplayManagerGlobal (hidden API)
 *   2. Create virtual display (DisplayManager API, fallback to SurfaceControl)
 *   3. Encode screen via MediaCodec H.264 encoder with Surface input
 *   4. Optionally capture audio via AudioRecord + AAC encoder
 *   5. Write framed packets to stdout
 */
public final class ScreenServer {

    private static final long PACKET_FLAG_CONFIG = 1L << 63;
    private static final long PACKET_FLAG_KEY_FRAME = 1L << 62;
    private static final long PACKET_FLAG_AUDIO = 1L << 61;

    private static final int REPEAT_FRAME_DELAY_US = 100_000; // 100ms
    private static final int DEFAULT_I_FRAME_INTERVAL = 10; // seconds
    private static final int[] MAX_SIZE_FALLBACK = {2560, 1920, 1600, 1280, 1024, 800};
    private static final int MAX_CONSECUTIVE_ERRORS = 3;

    private static final int AUDIO_SAMPLE_RATE = 48000;
    private static final int AUDIO_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_BIT_RATE = 128_000;

    private static int displayWidth;
    private static int displayHeight;
    private static volatile boolean running = true;
    private static final Object outputLock = new Object();

    public static void main(String... args) {
        // Some devices deadlock if the encoding thread has no Looper (e.g. Meizu)
        // Reference: https://github.com/Genymobile/scrcpy/issues/4143
        Looper.prepareMainLooper();

        try {
            int maxSize = 720;
            int bitRate = 8_000_000;
            boolean audioEnabled = false;

            if (args.length >= 1) maxSize = Integer.parseInt(args[0]);
            if (args.length >= 2) bitRate = Integer.parseInt(args[1]);
            if (args.length >= 3) audioEnabled = "1".equals(args[2]);

            log("Starting: maxSize=" + maxSize + " bitrate=" + bitRate + " audio=" + audioEnabled);

            getDisplaySize();
            log("Display: " + displayWidth + "x" + displayHeight);

            streamWithFallback(maxSize, bitRate, audioEnabled);

        } catch (Exception e) {
            log("Fatal: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void streamWithFallback(int maxSize, int bitRate, boolean audioEnabled) throws Exception {
        // Try requested size first
        try {
            doStream(maxSize, bitRate, audioEnabled);
            return;
        } catch (Exception e) {
            log("Failed at maxSize=" + maxSize + ": " + e.getMessage());
        }

        // Try fallback sizes (scrcpy downsizeOnError)
        for (int fallback : MAX_SIZE_FALLBACK) {
            if (fallback >= maxSize) continue;
            try {
                log("Retrying with -m" + fallback + "...");
                doStream(fallback, bitRate, audioEnabled);
                return;
            } catch (Exception e) {
                log("Failed at maxSize=" + fallback + ": " + e.getMessage());
            }
        }

        throw new RuntimeException("All resolutions failed");
    }

    private static void doStream(int maxSize, int bitRate, boolean audioEnabled) throws Exception {
        int outWidth, outHeight;
        if (displayWidth > displayHeight) {
            outWidth = Math.min(maxSize, displayWidth);
            outHeight = outWidth * displayHeight / displayWidth;
        } else {
            outHeight = Math.min(maxSize, displayHeight);
            outWidth = outHeight * displayWidth / displayHeight;
        }
        outWidth = outWidth & ~7;
        outHeight = outHeight & ~7;
        if (outWidth <= 0) outWidth = 8;
        if (outHeight <= 0) outHeight = 8;

        log("Output: " + outWidth + "x" + outHeight);

        FileOutputStream rawOut = new FileOutputStream(FileDescriptor.out);

        // Write 8-byte video header
        writeBE32(rawOut, outWidth);
        writeBE32(rawOut, outHeight);
        rawOut.flush();

        // Create encoder
        MediaFormat format = createFormat(outWidth, outHeight, bitRate);
        MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        log("Encoder: " + codec.getName());

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface inputSurface = codec.createInputSurface();

        // Create virtual display
        IBinder scDisplay = null;
        VirtualDisplay virtualDisplay = null;

        try {
            virtualDisplay = createVirtualDisplay(inputSurface, outWidth, outHeight);
            log("Display: DisplayManager API");
        } catch (Exception e) {
            log("DisplayManager failed: " + e.getMessage());
            try {
                scDisplay = createSurfaceControlDisplay();
                setSurfaceControlDisplay(scDisplay, inputSurface, outWidth, outHeight);
                log("Display: SurfaceControl API");
            } catch (Exception e2) {
                log("SurfaceControl failed: " + e2.getMessage());
                inputSurface.release();
                codec.release();
                throw new RuntimeException("Could not create display");
            }
        }

        codec.start();
        log("Streaming started");

        // Start audio thread if enabled
        Thread audioThread = null;
        if (audioEnabled && Build.VERSION.SDK_INT >= 29) {
            audioThread = new Thread(() -> {
                try {
                    captureAudio(rawOut);
                } catch (Exception e) {
                    log("Audio error: " + e.getMessage());
                }
            }, "audio-encoder");
            audioThread.setDaemon(true);
            audioThread.start();
            log("Audio capture started");
        } else if (audioEnabled) {
            log("Audio capture requires Android 10+, skipping");
        }

        // Main encode loop - mirrors scrcpy SurfaceEncoder.encode()
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        boolean firstFrameSent = false;
        int consecutiveErrors = 0;
        byte[] metaBuf = new byte[12];

        try {
            boolean eos = false;
            while (!eos && running) {
                int outputIndex;
                try {
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, -1);
                } catch (IllegalStateException e) {
                    if (firstFrameSent) {
                        consecutiveErrors++;
                        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) throw e;
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                        continue;
                    }
                    throw e;
                }

                try {
                    eos = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (outputIndex >= 0 && bufferInfo.size > 0) {
                        ByteBuffer codecBuffer = codec.getOutputBuffer(outputIndex);
                        if (codecBuffer != null) {
                            boolean isConfig = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                            boolean isKeyFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;

                            if (!isConfig) {
                                if (!firstFrameSent) {
                                    firstFrameSent = true;
                                    consecutiveErrors = 0;
                                    log("First frame");
                                }
                            }

                            long ptsAndFlags;
                            if (isConfig) {
                                ptsAndFlags = PACKET_FLAG_CONFIG;
                            } else {
                                ptsAndFlags = bufferInfo.presentationTimeUs;
                                if (isKeyFrame) {
                                    ptsAndFlags |= PACKET_FLAG_KEY_FRAME;
                                }
                            }

                            codecBuffer.position(bufferInfo.offset);
                            codecBuffer.limit(bufferInfo.offset + bufferInfo.size);

                            putBE64(metaBuf, 0, ptsAndFlags);
                            putBE32(metaBuf, 8, bufferInfo.size);
                            byte[] data = new byte[bufferInfo.size];
                            codecBuffer.get(data);
                            try {
                                synchronized (outputLock) {
                                    rawOut.write(metaBuf);
                                    rawOut.write(data);
                                    rawOut.flush();
                                }
                            } catch (IOException e) {
                                running = false;
                                break;
                            }
                        }
                    }
                } finally {
                    if (outputIndex >= 0) {
                        codec.releaseOutputBuffer(outputIndex, false);
                    }
                }
            }
        } finally {
            running = false;
            log("Stopping");
            try { codec.stop(); } catch (Exception ignored) {}
            codec.release();
            inputSurface.release();
            if (scDisplay != null) destroySurfaceControlDisplay(scDisplay);
            if (virtualDisplay != null) virtualDisplay.release();
            if (audioThread != null) {
                try { audioThread.join(2000); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Capture internal audio using AudioRecord (Android 10+) and encode to AAC.
     * Audio packets are written with PACKET_FLAG_AUDIO set in the flags field.
     */
    private static void captureAudio(FileOutputStream rawOut) throws Exception {
        int bufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE,
                AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize <= 0) bufferSize = 4096;
        bufferSize = Math.max(bufferSize, 4096);

        AudioRecord recorder = null;
        try {
            // Try REMOTE_SUBMIX source (internal audio capture, requires shell UID)
            recorder = new AudioRecord(MediaRecorder.AudioSource.REMOTE_SUBMIX,
                    AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize * 2);
        } catch (Exception e) {
            log("REMOTE_SUBMIX failed: " + e.getMessage());
        }

        if (recorder == null || recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            if (recorder != null) { try { recorder.release(); } catch (Exception ignored) {} }
            try {
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize * 2);
            } catch (Exception e) {
                log("MIC fallback failed: " + e.getMessage());
                return;
            }
        }

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            log("AudioRecord init failed");
            recorder.release();
            return;
        }

        // Create AAC encoder
        MediaFormat audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_SAMPLE_RATE, 2);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);

        MediaCodec audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioCodec.start();
        recorder.startRecording();

        log("Audio encoder: " + audioCodec.getName());

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        byte[] readBuf = new byte[bufferSize];
        byte[] metaBuf = new byte[12];

        try {
            while (running) {
                // Feed PCM to encoder
                int inputIndex = audioCodec.dequeueInputBuffer(5000);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = audioCodec.getInputBuffer(inputIndex);
                    if (inputBuffer != null) {
                        int read = recorder.read(readBuf, 0, Math.min(readBuf.length, inputBuffer.remaining()));
                        if (read > 0) {
                            inputBuffer.clear();
                            inputBuffer.put(readBuf, 0, read);
                            audioCodec.queueInputBuffer(inputIndex, 0, read,
                                    System.nanoTime() / 1000, 0);
                        } else {
                            audioCodec.queueInputBuffer(inputIndex, 0, 0,
                                    System.nanoTime() / 1000, 0);
                        }
                    }
                }

                // Drain encoded output
                int outputIndex;
                while ((outputIndex = audioCodec.dequeueOutputBuffer(info, 1000)) >= 0) {
                    if (info.size > 0) {
                        ByteBuffer outBuf = audioCodec.getOutputBuffer(outputIndex);
                        if (outBuf != null) {
                            boolean isConfig = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;

                            long ptsAndFlags = PACKET_FLAG_AUDIO;
                            if (isConfig) {
                                ptsAndFlags |= PACKET_FLAG_CONFIG;
                            } else {
                                ptsAndFlags |= info.presentationTimeUs;
                            }

                            outBuf.position(info.offset);
                            outBuf.limit(info.offset + info.size);
                            byte[] data = new byte[info.size];
                            outBuf.get(data);

                            putBE64(metaBuf, 0, ptsAndFlags);
                            putBE32(metaBuf, 8, info.size);

                            try {
                                synchronized (outputLock) {
                                    rawOut.write(metaBuf);
                                    rawOut.write(data);
                                    rawOut.flush();
                                }
                            } catch (IOException e) {
                                running = false;
                                return;
                            }
                        }
                    }
                    audioCodec.releaseOutputBuffer(outputIndex, false);
                }
            }
        } finally {
            try { recorder.stop(); } catch (Exception ignored) {}
            recorder.release();
            try { audioCodec.stop(); } catch (Exception ignored) {}
            audioCodec.release();
            log("Audio stopped");
        }
    }

    private static MediaFormat createFormat(int width, int height, int bitRate) {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        // Must be present to configure the encoder, but does not impact the actual variable frame rate
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, DEFAULT_I_FRAME_INTERVAL);

        // Display the very first frame, and recover from bad quality when no new frames
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_FRAME_DELAY_US);

        // Color range (API 24+)
        if (Build.VERSION.SDK_INT >= 24) {
            format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED);
        }

        return format;
    }

    // ─── Display creation ───────────────────────────────────────────────────────

    private static VirtualDisplay createVirtualDisplay(Surface surface, int width, int height) throws Exception {
        Class<?> dmgClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
        Object dmg = dmgClass.getMethod("getInstance").invoke(null);

        // PUBLIC | AUTO_MIRROR
        int flags = (1 << 0) | (1 << 4);
        VirtualDisplay vd = null;

        // Android 10+: with uniqueId parameter
        try {
            Method m = dmgClass.getMethod("createVirtualDisplay", String.class, boolean.class,
                    int.class, int.class, int.class, Surface.class, int.class,
                    android.hardware.display.VirtualDisplay.Callback.class,
                    android.os.Handler.class, String.class);
            vd = (VirtualDisplay) m.invoke(dmg, "adbkit", false, width, height, 1, surface, flags, null, null, null);
        } catch (NoSuchMethodException ignored) {}

        if (vd == null) {
            try {
                Method m = dmgClass.getMethod("createVirtualDisplay", String.class, boolean.class,
                        int.class, int.class, int.class, Surface.class, int.class,
                        android.hardware.display.VirtualDisplay.Callback.class,
                        android.os.Handler.class);
                vd = (VirtualDisplay) m.invoke(dmg, "adbkit", false, width, height, 1, surface, flags, null, null);
            } catch (NoSuchMethodException ignored) {}
        }

        if (vd == null) {
            throw new RuntimeException("No compatible createVirtualDisplay method found");
        }
        return vd;
    }

    private static IBinder createSurfaceControlDisplay() throws Exception {
        Class<?> cls = Class.forName("android.view.SurfaceControl");
        boolean secure = Build.VERSION.SDK_INT < 30 ||
                (Build.VERSION.SDK_INT == 30 && !"S".equals(Build.VERSION.CODENAME));
        Method method = cls.getMethod("createDisplay", String.class, boolean.class);
        return (IBinder) method.invoke(null, "adbkit", secure);
    }

    private static void setSurfaceControlDisplay(IBinder display, Surface surface, int width, int height) throws Exception {
        Rect deviceRect = new Rect(0, 0, displayWidth, displayHeight);
        Rect outputRect = new Rect(0, 0, width, height);

        Class<?> cls = Class.forName("android.view.SurfaceControl");
        cls.getMethod("openTransaction").invoke(null);
        try {
            cls.getMethod("setDisplaySurface", IBinder.class, Surface.class)
                    .invoke(null, display, surface);
            cls.getMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class)
                    .invoke(null, display, 0, deviceRect, outputRect);
            cls.getMethod("setDisplayLayerStack", IBinder.class, int.class)
                    .invoke(null, display, 0);
        } finally {
            cls.getMethod("closeTransaction").invoke(null);
        }
    }

    private static void destroySurfaceControlDisplay(IBinder display) {
        try {
            Class<?> cls = Class.forName("android.view.SurfaceControl");
            cls.getMethod("destroyDisplay", IBinder.class).invoke(null, display);
        } catch (Exception ignored) {}
    }

    // ─── Display info ───────────────────────────────────────────────────────────

    private static void getDisplaySize() throws Exception {
        try {
            Class<?> dmgClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
            Object dmg = dmgClass.getMethod("getInstance").invoke(null);
            Object info = dmgClass.getMethod("getDisplayInfo", int.class).invoke(dmg, 0);
            if (info != null) {
                Class<?> diClass = info.getClass();
                try {
                    displayWidth = diClass.getField("logicalWidth").getInt(info);
                    displayHeight = diClass.getField("logicalHeight").getInt(info);
                    if (displayWidth > 0 && displayHeight > 0) return;
                } catch (NoSuchFieldException ignored) {}
                try {
                    displayWidth = diClass.getField("appWidth").getInt(info);
                    displayHeight = diClass.getField("appHeight").getInt(info);
                    if (displayWidth > 0 && displayHeight > 0) return;
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception e) {
            log("DisplayManagerGlobal failed: " + e.getMessage());
        }

        try {
            Process p = Runtime.getRuntime().exec(new String[]{"wm", "size"});
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Physical size:")) {
                    String[] parts = line.replace("Physical size:", "").trim().split("x");
                    if (parts.length == 2) {
                        displayWidth = Integer.parseInt(parts[0].trim());
                        displayHeight = Integer.parseInt(parts[1].trim());
                        if (displayWidth > 0 && displayHeight > 0) return;
                    }
                }
            }
        } catch (Exception e) {
            log("wm size failed: " + e.getMessage());
        }

        displayWidth = 1080;
        displayHeight = 1920;
        log("Fallback display: " + displayWidth + "x" + displayHeight);
    }

    // ─── Utilities ──────────────────────────────────────────────────────────────

    private static void writeBE32(OutputStream out, int value) throws IOException {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void putBE64(byte[] buf, int offset, long value) {
        buf[offset]     = (byte) (value >> 56);
        buf[offset + 1] = (byte) (value >> 48);
        buf[offset + 2] = (byte) (value >> 40);
        buf[offset + 3] = (byte) (value >> 32);
        buf[offset + 4] = (byte) (value >> 24);
        buf[offset + 5] = (byte) (value >> 16);
        buf[offset + 6] = (byte) (value >> 8);
        buf[offset + 7] = (byte) value;
    }

    private static void putBE32(byte[] buf, int offset, int value) {
        buf[offset]     = (byte) (value >> 24);
        buf[offset + 1] = (byte) (value >> 16);
        buf[offset + 2] = (byte) (value >> 8);
        buf[offset + 3] = (byte) value;
    }

    private static void log(String msg) {
        System.err.println("[ScreenServer] " + msg);
        System.err.flush();
    }
}
