import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Scrcpy-inspired screen capture server for ADBKit.
 * Runs on the target device via: app_process / ScreenServer [maxSize] [bitrate] [maxFps]
 *
 * Architecture (mirrors scrcpy):
 *   1. Get display info via DisplayManagerGlobal (hidden API)
 *   2. Create virtual display (DisplayManager API, fallback to SurfaceControl)
 *   3. Encode screen via MediaCodec H.264 encoder with Surface input
 *   4. Write raw Annex B H.264 stream to stdout
 *
 * Key differences from naive implementations:
 *   - REPEAT_PREVIOUS_FRAME_AFTER prevents black screen on static content
 *   - Looper.prepare() prevents deadlock on some devices (e.g. Meizu)
 *   - Legacy SurfaceControl API used for maximum compatibility
 *   - Automatic resolution downscaling on encoder failure
 *   - Proper secure flag handling per Android version
 */
public final class ScreenServer {

    private static final int REPEAT_FRAME_DELAY_US = 100_000; // 100ms - repeat frame if screen static
    private static final int DEFAULT_I_FRAME_INTERVAL = 10; // seconds
    private static final int[] MAX_SIZE_FALLBACK = {2560, 1920, 1600, 1280, 1024, 800, 640, 480};

    private static int displayWidth;
    private static int displayHeight;

    public static void main(String... args) {
        // Some devices deadlock if the encoding thread has no Looper (e.g. Meizu)
        // Reference: https://github.com/Genymobile/scrcpy/issues/4143
        Looper.prepareMainLooper();

        try {
            int maxSize = 720;
            int bitRate = 8_000_000;
            int maxFps = 30;

            if (args.length >= 1) maxSize = Integer.parseInt(args[0]);
            if (args.length >= 2) bitRate = Integer.parseInt(args[1]);
            if (args.length >= 3) maxFps = Integer.parseInt(args[2]);

            log("Starting ScreenServer: maxSize=" + maxSize + " bitrate=" + bitRate + " fps=" + maxFps);

            getDisplaySize();
            log("Display size: " + displayWidth + "x" + displayHeight);

            streamWithFallback(maxSize, bitRate, maxFps);

        } catch (Exception e) {
            log("Fatal error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * Try streaming at the requested size, then automatically downsize on failure.
     */
    private static void streamWithFallback(int maxSize, int bitRate, int maxFps) throws Exception {
        // Try requested size first
        try {
            doStream(maxSize, bitRate, maxFps);
            return;
        } catch (Exception e) {
            log("Failed at maxSize=" + maxSize + ": " + e.getMessage());
        }

        // Try fallback sizes
        for (int fallback : MAX_SIZE_FALLBACK) {
            if (fallback >= maxSize) continue;
            try {
                log("Retrying with maxSize=" + fallback);
                doStream(fallback, bitRate, maxFps);
                return;
            } catch (Exception e) {
                log("Failed at maxSize=" + fallback + ": " + e.getMessage());
            }
        }

        throw new RuntimeException("All resolutions failed");
    }

    private static void doStream(int maxSize, int bitRate, int maxFps) throws Exception {
        int outWidth, outHeight;
        if (displayWidth > displayHeight) {
            outWidth = Math.min(maxSize, displayWidth);
            outHeight = outWidth * displayHeight / displayWidth;
        } else {
            outHeight = Math.min(maxSize, displayHeight);
            outWidth = outHeight * displayWidth / displayHeight;
        }
        // Encoder requires dimensions divisible by 8 (some devices by 2, 8 is safest)
        outWidth = outWidth & ~7;
        outHeight = outHeight & ~7;
        if (outWidth <= 0) outWidth = 8;
        if (outHeight <= 0) outHeight = 8;

        log("Output size: " + outWidth + "x" + outHeight);

        // Write 8-byte header: [width(4B BE)][height(4B BE)]
        FileOutputStream out = new FileOutputStream(FileDescriptor.out);
        writeInt(out, outWidth);
        writeInt(out, outHeight);
        out.flush();

        // Create encoder
        MediaFormat format = createFormat(outWidth, outHeight, bitRate, maxFps);
        MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        log("Using encoder: " + codec.getName());

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface inputSurface = codec.createInputSurface();

        // Create virtual display - try DisplayManager first, then SurfaceControl
        IBinder scDisplay = null;
        VirtualDisplay virtualDisplay = null;

        try {
            virtualDisplay = createVirtualDisplay(inputSurface, outWidth, outHeight);
            log("Display created via DisplayManager API");
        } catch (Exception e) {
            log("DisplayManager failed: " + e.getMessage() + ", trying SurfaceControl");
            try {
                scDisplay = createSurfaceControlDisplay();
                setSurfaceControlDisplay(scDisplay, inputSurface, outWidth, outHeight);
                log("Display created via SurfaceControl API");
            } catch (Exception e2) {
                log("SurfaceControl also failed: " + e2.getMessage());
                inputSurface.release();
                codec.release();
                throw new RuntimeException("Could not create display: " + e2.getMessage());
            }
        }

        codec.start();
        log("Encoder started, streaming...");

        // Main encode loop
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        boolean alive = true;
        boolean firstFrame = false;

        try {
            while (alive) {
                int outputIndex = codec.dequeueOutputBuffer(bufferInfo, -1); // blocking wait
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        boolean isConfig = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                        if (!isConfig && !firstFrame) {
                            firstFrame = true;
                            log("First frame sent");
                        }
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        byte[] data = new byte[bufferInfo.size];
                        outputBuffer.get(data);
                        try {
                            out.write(data);
                            out.flush();
                        } catch (IOException e) {
                            alive = false;
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false);
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        alive = false;
                    }
                }
            }
        } finally {
            log("Stopping encoder");
            try { codec.stop(); } catch (Exception ignored) {}
            codec.release();
            inputSurface.release();
            if (scDisplay != null) destroySurfaceControlDisplay(scDisplay);
            if (virtualDisplay != null) virtualDisplay.release();
        }
    }

    private static MediaFormat createFormat(int width, int height, int bitRate, int maxFps) {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        // KEY_FRAME_RATE must be present but does not impact actual variable frame rate
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, DEFAULT_I_FRAME_INTERVAL);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);

        // CRITICAL: Repeat the previous frame if no new content (prevents black screen on static screen)
        // This is the key setting that scrcpy uses - without it, encoder produces nothing when screen is idle
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_FRAME_DELAY_US);

        // Limit max fps to encoder
        if (maxFps > 0) {
            format.setFloat("max-fps-to-encoder", maxFps);
        }

        // Low latency hints (best effort, ignored if not supported)
        try { format.setInteger(MediaFormat.KEY_LATENCY, 0); } catch (Exception ignored) {}
        try { format.setInteger(MediaFormat.KEY_PRIORITY, 1); } catch (Exception ignored) {}

        // Color range
        try {
            format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED);
        } catch (Exception ignored) {}

        return format;
    }

    // ─── Display creation ───────────────────────────────────────────────────────

    /**
     * Try creating a virtual display via DisplayManager (more compatible on newer Android).
     */
    private static VirtualDisplay createVirtualDisplay(Surface surface, int width, int height) throws Exception {
        Class<?> dmgClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
        Method getInstance = dmgClass.getMethod("getInstance");
        Object dmg = getInstance.invoke(null);

        // int VIRTUAL_DISPLAY_FLAG_PUBLIC = 1 << 0
        // int VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR = 1 << 4
        // int VIRTUAL_DISPLAY_FLAG_PRESENTATION = 1 << 1 (in older scrcpy)
        int flags = (1 << 0) | (1 << 4); // PUBLIC | AUTO_MIRROR

        // createVirtualDisplay(String name, boolean secure, int width, int height, int densityDpi,
        //                      Surface surface, int flags, VirtualDisplay.Callback callback, Handler handler,
        //                      String uniqueId)
        // Try different method signatures as they vary across Android versions
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
            // Older API without uniqueId
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

    /**
     * Create display via SurfaceControl (legacy fallback, used by scrcpy on older devices).
     * Uses openTransaction/closeTransaction for maximum compatibility.
     */
    private static IBinder createSurfaceControlDisplay() throws Exception {
        Class<?> cls = Class.forName("android.view.SurfaceControl");
        // Android 12+: secure displays can't be created with shell permissions
        boolean secure = Build.VERSION.SDK_INT < 30;
        Method method = cls.getMethod("createDisplay", String.class, boolean.class);
        return (IBinder) method.invoke(null, "adbkit", secure);
    }

    private static void setSurfaceControlDisplay(IBinder display, Surface surface, int width, int height) throws Exception {
        Rect deviceRect = new Rect(0, 0, displayWidth, displayHeight);
        Rect outputRect = new Rect(0, 0, width, height);

        Class<?> cls = Class.forName("android.view.SurfaceControl");

        // Use legacy openTransaction/closeTransaction API (most compatible, scrcpy uses this)
        Method openTransaction = cls.getMethod("openTransaction");
        openTransaction.invoke(null);
        try {
            cls.getMethod("setDisplaySurface", IBinder.class, Surface.class)
                    .invoke(null, display, surface);
            cls.getMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class)
                    .invoke(null, display, 0, deviceRect, outputRect);
            cls.getMethod("setDisplayLayerStack", IBinder.class, int.class)
                    .invoke(null, display, 0);
        } finally {
            Method closeTransaction = cls.getMethod("closeTransaction");
            closeTransaction.invoke(null);
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
        // Try DisplayManagerGlobal first (most reliable)
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

        // Fallback: wm size
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
        log("Using fallback display size: " + displayWidth + "x" + displayHeight);
    }

    // ─── Utilities ──────────────────────────────────────────────────────────────

    private static void writeInt(FileOutputStream out, int value) throws IOException {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void log(String msg) {
        System.err.println("[ScreenServer] " + msg);
        System.err.flush();
    }
}
