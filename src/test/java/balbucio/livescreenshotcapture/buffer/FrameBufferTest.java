package balbucio.livescreenshotcapture.buffer;

import balbucio.livescreenshotcapture.capture.CapturedFrame;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FrameBufferTest {
    private static BufferedImage img() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    void evictsOldFrames() {
        FrameBuffer buf = new FrameBuffer(1000);
        buf.push(new CapturedFrame(0, img()));
        buf.push(new CapturedFrame(500, img()));
        buf.push(new CapturedFrame(1500, img()));
        assertThat(buf.size()).isEqualTo(2);
        assertThat(buf.latest()).isPresent();
        assertThat(buf.latest().get().timestampMillis()).isEqualTo(1500);
    }

    @Test
    void atOffsetFindsNearest() {
        FrameBuffer buf = new FrameBuffer(5000);
        buf.push(new CapturedFrame(1000, img()));
        buf.push(new CapturedFrame(2000, img()));
        buf.push(new CapturedFrame(3000, img()));
        assertThat(buf.atOffset(2100).get().timestampMillis()).isEqualTo(2000);
        assertThat(buf.atOffset(2900).get().timestampMillis()).isEqualTo(3000);
    }
}
