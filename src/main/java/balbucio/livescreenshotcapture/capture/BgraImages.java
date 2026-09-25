package balbucio.livescreenshotcapture.capture;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * BGRA {@link CapturedFrame} to {@link BufferedImage} conversion, kept off the
 * capture hot path and used only when a frame is saved or previewed.
 *
 * <p>In memory BGRA bytes read as little-endian ints are exactly
 * {@code 0xAARRGGBB} (Java {@code TYPE_INT_ARGB}) values, so conversion is a bulk
 * copy with no per-pixel swizzle. The resulting image wraps the copied int[]
 * through a packed raster — no second copy.
 */
public final class BgraImages {
    private static final int[] ARGB_MASKS = {0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000};

    private BgraImages() {
    }

    /** Crops a region and converts it to a {@code TYPE_INT_ARGB} image in one pass. */
    public static BufferedImage cropToImage(CapturedFrame frame, Rectangle crop) {
        Rectangle safe = clamp(crop, frame.width(), frame.height());
        int w = safe.width;
        int h = safe.height;
        int[] px = new int[w * h];
        IntBuffer ints = frame.pixels().duplicate()
                .order(ByteOrder.LITTLE_ENDIAN)
                .asIntBuffer();
        int strideInts = frame.rowStride() >> 2;
        for (int y = 0; y < h; y++) {
            ints.position((safe.y + y) * strideInts + safe.x);
            ints.get(px, y * w, w);
        }
        return wrapArgb(px, w, h);
    }

    /** Converts the whole frame. */
    public static BufferedImage toImage(CapturedFrame frame) {
        return cropToImage(frame, new Rectangle(0, 0, frame.width(), frame.height()));
    }

    /** Wraps ARGB ints ({@code 0xAARRGGBB}) in an image without copying. */
    public static BufferedImage wrapArgb(int[] argb, int w, int h) {
        DataBufferInt db = new DataBufferInt(argb, w * h);
        WritableRaster raster = Raster.createPackedRaster(db, w, h, w, ARGB_MASKS, null);
        DirectColorModel cm = new DirectColorModel(32,
                0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000);
        return new BufferedImage(cm, raster, false, null);
    }

    /**
     * Copies the requested crop rows out of a frame into a fresh direct BGRA
     * buffer (position 0, limit = crop width * crop height * 4).
     */
    public static ByteBuffer copyCrop(CapturedFrame frame, Rectangle crop) {
        Rectangle safe = clamp(crop, frame.width(), frame.height());
        int cw = safe.width;
        int ch = safe.height;
        ByteBuffer out = ByteBuffer.allocateDirect(cw * ch * 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer src = frame.pixels().duplicate();
        for (int y = 0; y < ch; y++) {
            int rowStart = (safe.y + y) * frame.rowStride() + safe.x * 4;
            src.limit(rowStart + cw * 4).position(rowStart);
            out.put(src);
        }
        return ((ByteBuffer) out.flip()).slice().order(ByteOrder.LITTLE_ENDIAN);
    }

    private static Rectangle clamp(Rectangle r, int maxW, int maxH) {
        int x = Math.max(0, Math.min(r.x, maxW - 1));
        int y = Math.max(0, Math.min(r.y, maxH - 1));
        int w = Math.max(1, Math.min(r.width, maxW - x));
        int h = Math.max(1, Math.min(r.height, maxH - y));
        return new Rectangle(x, y, w, h);
    }
}
