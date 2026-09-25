package balbucio.livescreenshotcapture.buffer;

import balbucio.livescreenshotcapture.capture.CapturedFrame;
import balbucio.livescreenshotcapture.capture.TestFrames;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FrameBufferTest {
    private static CapturedFrame frame(long ts) {
        return TestFrames.solid(ts, 16, 16);
    }

    @Test
    void evictsOldFrames() {
        FrameBuffer buf = new FrameBuffer(1000);
        buf.push(frame(0));
        buf.push(frame(500));
        buf.push(frame(1500));
        assertThat(buf.size()).isEqualTo(2);
        assertThat(buf.latest()).isPresent();
        assertThat(buf.latest().get().timestampMillis()).isEqualTo(1500);
    }

    @Test
    void atOffsetFindsNearest() {
        FrameBuffer buf = new FrameBuffer(5000);
        buf.push(frame(1000));
        buf.push(frame(2000));
        buf.push(frame(3000));
        assertThat(buf.atOffset(2100).get().timestampMillis()).isEqualTo(2000);
        assertThat(buf.atOffset(2900).get().timestampMillis()).isEqualTo(3000);
    }
}
