package balbucio.livescreenshotcapture.capture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Test fixture: builds BGRA {@link CapturedFrame}s without any native backend. */
public final class TestFrames {
    private TestFrames() {
    }

    /** Solid color frame. */
    public static CapturedFrame solid(long timestampMillis, int w, int h, int argb) {
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, argb);
        return ofArgb(timestampMillis, w, h, px);
    }

    /** Opaque black frame. */
    public static CapturedFrame solid(long timestampMillis, int w, int h) {
        return solid(timestampMillis, w, h, 0xFF000000);
    }

    /** Frame from explicit ARGB ints (row-major). */
    public static CapturedFrame ofArgb(long timestampMillis, int w, int h, int[] argb) {
        ByteBuffer px = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.LITTLE_ENDIAN);
        px.asIntBuffer().put(argb, 0, w * h);
        return new CapturedFrame(timestampMillis, w, h, w * 4, px);
    }
}
