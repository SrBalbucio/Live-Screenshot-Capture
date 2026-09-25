package balbucio.livescreenshotcapture.capture;

import java.nio.ByteBuffer;

/**
 * One buffered frame in native BGRA layout (4 bytes per pixel, blue first),
 * exactly what DXGI Desktop Duplication produces and what BGRA-ordered ints
 * ({@code 0xAARRGGBB} little-endian) map onto without any swizzle.
 *
 * @param timestampMillis wall-clock time of the last real screen update
 * @param width           frame width in pixels
 * @param height          frame height in pixels
 * @param rowStride       bytes per row (>= width * 4)
 * @param pixels          pixel buffer, position 0, limit = rowStride * height
 */
public record CapturedFrame(long timestampMillis, int width, int height, int rowStride,
        ByteBuffer pixels) {
    public CapturedFrame {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be > 0: " + width + "x" + height);
        }
        if (rowStride < width * 4) {
            throw new IllegalArgumentException("rowStride must fit width*4: " + rowStride);
        }
        if (pixels == null) {
            throw new IllegalArgumentException("pixels must not be null");
        }
    }

    public static CapturedFrame ofArgb(long timestampMillis, int width, int height, int[] argb) {
        if (argb.length < width * height) {
            throw new IllegalArgumentException("argb array too small");
        }
        ByteBuffer px = ByteBuffer.allocateDirect(width * height * 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        px.asIntBuffer().put(argb, 0, width * height);
        return new CapturedFrame(timestampMillis, width, height, width * 4, px);
    }
}
