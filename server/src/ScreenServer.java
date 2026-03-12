import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.IBinder;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Minimal scrcpy-like screen capture server.
 * Runs on the target device via: app_process / ScreenServer [width] [height] [bitrate] [maxFps]
 *
 * Uses SurfaceControl (hidden API) to create a virtual display,
 * MediaCodec encoder to produce H.264, and writes raw Annex B stream to stdout.
 */
public final class ScreenServer {

    private static int displayWidth;
    private static int displayHeight;

    public static void main(String... args) {
        try {
            int maxSize = 720;
            int bitRate = 8_000_000;
            int maxFps = 30;

            if (args.length >= 1) maxSize = Integer.parseInt(args[0]);
            if (args.length >= 2) bitRate = Integer.parseInt(args[1]);
            if (args.length >= 3) maxFps = Integer.parseInt(args[2]);

            // Get actual display size
            getDisplaySize();

            // Compute output size maintaining aspect ratio
            int outWidth, outHeight;
            if (displayWidth > displayHeight) {
                // Landscape
                outWidth = maxSize;
                outHeight = maxSize * displayHeight / displayWidth;
            } else {
                // Portrait
                outWidth = maxSize * displayWidth / displayHeight;
                outHeight = maxSize;
            }
            // Ensure even dimensions (required by encoder)
            outWidth = outWidth & ~1;
            outHeight = outHeight & ~1;

            // Write header: width and height as 4 bytes each (big-endian)
            FileOutputStream out = new FileOutputStream(FileDescriptor.out);
            out.write((outWidth >> 24) & 0xFF);
            out.write((outWidth >> 16) & 0xFF);
            out.write((outWidth >> 8) & 0xFF);
            out.write(outWidth & 0xFF);
            out.write((outHeight >> 24) & 0xFF);
            out.write((outHeight >> 16) & 0xFF);
            out.write((outHeight >> 8) & 0xFF);
            out.write(outHeight & 0xFF);
            out.flush();

            // Configure encoder
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outWidth, outHeight);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, maxFps);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            // Low latency hints
            try {
                format.setInteger(MediaFormat.KEY_LATENCY, 0);
            } catch (Exception ignored) {}
            try {
                format.setInteger("priority", 1); // real-time priority
            } catch (Exception ignored) {}
            format.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, maxFps);

            MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            Surface inputSurface = codec.createInputSurface();

            // Create virtual display bound to encoder's input surface
            IBinder display = createDisplay();
            setDisplaySurface(display, inputSurface, outWidth, outHeight);

            codec.start();

            // Main encode loop: read frames and write to stdout
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean alive = true;

            while (alive) {
                int outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000); // 10ms
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        byte[] data = new byte[bufferInfo.size];
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        outputBuffer.get(data);
                        try {
                            out.write(data);
                            out.flush();
                        } catch (IOException e) {
                            // stdout closed (client disconnected)
                            alive = false;
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false);
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        alive = false;
                    }
                }
            }

            codec.stop();
            codec.release();
            destroyDisplay(display);

        } catch (Exception e) {
            System.err.println("ScreenServer error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void getDisplaySize() throws Exception {
        // Use DisplayManagerGlobal to get display info
        try {
            Class<?> dmgClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
            Method getInstance = dmgClass.getMethod("getInstance");
            Object dmg = getInstance.invoke(null);

            // getDisplayInfo(int displayId) returns DisplayInfo
            Method getDisplayInfo = dmgClass.getMethod("getDisplayInfo", int.class);
            Object displayInfo = getDisplayInfo.invoke(dmg, 0);

            if (displayInfo != null) {
                Class<?> diClass = displayInfo.getClass();
                // Try logicalWidth/logicalHeight first (newer APIs)
                try {
                    displayWidth = diClass.getField("logicalWidth").getInt(displayInfo);
                    displayHeight = diClass.getField("logicalHeight").getInt(displayInfo);
                    return;
                } catch (NoSuchFieldException ignored) {}
                // Fallback to appWidth/appHeight
                try {
                    displayWidth = diClass.getField("appWidth").getInt(displayInfo);
                    displayHeight = diClass.getField("appHeight").getInt(displayInfo);
                    return;
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception ignored) {}

        // Fallback: use wm size
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"wm", "size"});
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Physical size:")) {
                    String size = line.replace("Physical size:", "").trim();
                    String[] parts = size.split("x");
                    if (parts.length == 2) {
                        displayWidth = Integer.parseInt(parts[0].trim());
                        displayHeight = Integer.parseInt(parts[1].trim());
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}

        // Ultimate fallback
        displayWidth = 1080;
        displayHeight = 1920;
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private static IBinder createDisplay() throws Exception {
        Class<?> cls = Class.forName("android.view.SurfaceControl");
        Method method = cls.getMethod("createDisplay", String.class, boolean.class);
        return (IBinder) method.invoke(null, "adbkit", false);
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private static void destroyDisplay(IBinder display) {
        try {
            Class<?> cls = Class.forName("android.view.SurfaceControl");
            Method method = cls.getMethod("destroyDisplay", IBinder.class);
            method.invoke(null, display);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private static void setDisplaySurface(IBinder display, Surface surface, int width, int height) throws Exception {
        Rect deviceRect = new Rect(0, 0, displayWidth, displayHeight);
        Rect outputRect = new Rect(0, 0, width, height);

        Class<?> cls = Class.forName("android.view.SurfaceControl");

        // Try SurfaceControl.Transaction API (Android 9+)
        try {
            Class<?> txClass = Class.forName("android.view.SurfaceControl$Transaction");
            Object tx = txClass.getConstructor().newInstance();

            Method setDisplaySurface = txClass.getMethod("setDisplaySurface", IBinder.class, Surface.class);
            setDisplaySurface.invoke(tx, display, surface);

            Method setDisplayProjection = txClass.getMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class);
            setDisplayProjection.invoke(tx, display, 0, deviceRect, outputRect);

            Method setDisplayLayerStack = txClass.getMethod("setDisplayLayerStack", IBinder.class, int.class);
            setDisplayLayerStack.invoke(tx, display, 0);

            Method apply = txClass.getMethod("apply");
            apply.invoke(tx);
            return;
        } catch (Exception ignored) {}

        // Fallback to legacy API (Android 8 and below)
        Method openTransaction = cls.getMethod("openTransaction");
        openTransaction.invoke(null);
        try {
            Method setDS = cls.getMethod("setDisplaySurface", IBinder.class, Surface.class);
            setDS.invoke(null, display, surface);

            Method setDP = cls.getMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class);
            setDP.invoke(null, display, 0, deviceRect, outputRect);

            Method setDLS = cls.getMethod("setDisplayLayerStack", IBinder.class, int.class);
            setDLS.invoke(null, display, 0);
        } finally {
            Method closeTransaction = cls.getMethod("closeTransaction");
            closeTransaction.invoke(null);
        }
    }
}
