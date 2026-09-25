package balbucio.livescreenshotcapture.capture;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BgraImagesTest {

    /** Builds a frame with non-trivial pixels and padded row stride. */
    private static CapturedFrame paddedFrame(int w, int h, int padInts, boolean opaque) {
        int stride = (w + padInts) * 4;
        ByteBuffer px = ByteBuffer.allocateDirect(stride * h).order(ByteOrder.LITTLE_ENDIAN);
        int[] row = new int[w + padInts];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = (x * 7) & 0xFF;
                int g = (y * 11) & 0xFF;
                int b = ((x + y) * 3) & 0xFF;
                int a = opaque ? 0xFF : ((x + y) & 0xFF);
                row[x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            px.asIntBuffer().position(y * (w + padInts)).put(row, 0, w + padInts);
        }
        return new CapturedFrame(1234L, w, h, stride, px);
    }

    @Test
    void toImagePreservesPixels() {
        CapturedFrame frame = paddedFrame(16, 8, 0, true);
        BufferedImage img = BgraImages.toImage(frame);
        assertThat(img.getWidth()).isEqualTo(16);
        assertThat(img.getHeight()).isEqualTo(8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                int r = (x * 7) & 0xFF;
                int g = (y * 11) & 0xFF;
                int b = ((x + y) * 3) & 0xFF;
                assertThat(img.getRGB(x, y)).as("pixel %d,%d", x, y)
                        .isEqualTo((0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    @Test
    void paddedStrideIsHonored() {
        CapturedFrame frame = paddedFrame(16, 8, 5, true);
        BufferedImage padded = BgraImages.toImage(frame);
        BufferedImage tight = BgraImages.toImage(paddedFrame(16, 8, 0, true));
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                assertThat(padded.getRGB(x, y)).isEqualTo(tight.getRGB(x, y));
            }
        }
    }

    @Test
    void cropExtractsSubRegion() {
        CapturedFrame frame = paddedFrame(32, 32, 0, true);
        BufferedImage crop = BgraImages.cropToImage(frame, new Rectangle(10, 12, 8, 6));
        assertThat(crop.getWidth()).isEqualTo(8);
        assertThat(crop.getHeight()).isEqualTo(6);
        BufferedImage full = BgraImages.toImage(frame);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 8; x++) {
                assertThat(crop.getRGB(x, y)).isEqualTo(full.getRGB(10 + x, 12 + y));
            }
        }
    }

    @Test
    void cropIsClampedToFrame() {
        CapturedFrame frame = paddedFrame(16, 16, 0, true);
        BufferedImage crop = BgraImages.cropToImage(frame, new Rectangle(12, 12, 50, 50));
        assertThat(crop.getWidth()).isEqualTo(4);
        assertThat(crop.getHeight()).isEqualTo(4);
    }

    @Test
    void alphaSurvivesConversion() {
        CapturedFrame frame = paddedFrame(8, 8, 0, false);
        BufferedImage img = BgraImages.toImage(frame);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int expectedAlpha = ((x + y) & 0xFF) << 24;
                assertThat(img.getRGB(x, y) & 0xFF000000).isEqualTo(expectedAlpha);
            }
        }
    }

    @Test
    void copyCropProducesRegionSizedBuffer() {
        CapturedFrame frame = paddedFrame(32, 32, 3, true);
        ByteBuffer out = BgraImages.copyCrop(frame, new Rectangle(4, 6, 10, 8));
        assertThat(out.remaining()).isEqualTo(10 * 8 * 4);
        CapturedFrame crop = new CapturedFrame(0L, 10, 8, 10 * 4, out);
        BufferedImage fromCopy = BgraImages.toImage(crop);
        BufferedImage fromDirect = BgraImages.cropToImage(frame, new Rectangle(4, 6, 10, 8));
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 10; x++) {
                assertThat(fromCopy.getRGB(x, y)).isEqualTo(fromDirect.getRGB(x, y));
            }
        }
    }

    @Test
    void heapBuffersWorkToo() {
        int[] argb = {0xFF112233, 0xFF445566, 0xFF778899, 0xFFAABBCC};
        CapturedFrame frame = TestFrames.ofArgb(0L, 2, 2, argb);
        BufferedImage img = BgraImages.toImage(frame);
        assertThat(img.getRGB(0, 0)).isEqualTo(0xFF112233);
        assertThat(img.getRGB(1, 0)).isEqualTo(0xFF445566);
        assertThat(img.getRGB(0, 1)).isEqualTo(0xFF778899);
        assertThat(img.getRGB(1, 1)).isEqualTo(0xFFAABBCC);
    }
}
