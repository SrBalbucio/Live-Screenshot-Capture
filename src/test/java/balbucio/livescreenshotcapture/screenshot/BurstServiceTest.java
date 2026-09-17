package balbucio.livescreenshotcapture.screenshot;

import balbucio.livescreenshotcapture.buffer.FrameBuffer;
import balbucio.livescreenshotcapture.capture.CaptureBackend;
import balbucio.livescreenshotcapture.capture.CaptureService;
import balbucio.livescreenshotcapture.capture.CapturedFrame;
import balbucio.livescreenshotcapture.model.CaptureRegion;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import balbucio.livescreenshotcapture.region.RegionService;
import balbucio.livescreenshotcapture.storage.StorageService;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class BurstServiceTest {
    private static BufferedImage img(int seed) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, seed);
        return img;
    }

    private CaptureService serviceWithFrames(ScreenRegion stream) {
        CaptureBackend stub = new CaptureBackend() {
            @Override
            public BufferedImage capture(Rectangle area) {
                return img(1);
            }
        };
        FrameBuffer buffer = new FrameBuffer(10_000);
        CaptureService service = new CaptureService(stub, buffer, stream, 10);
        long now = System.currentTimeMillis();
        buffer.push(new CapturedFrame(now - 900, img(2)));
        buffer.push(new CapturedFrame(now - 100, img(3)));
        return service;
    }

    @Test
    void burstSavesPastAndFutureFrames(@TempDir Path tmp) throws Exception {
        ScreenRegion stream = new ScreenRegion(0, 0, 64, 64);
        CaptureService captureService = serviceWithFrames(stream);
        StorageService storage = new StorageService(tmp, "tester", "png", 0.92f);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        try {
            BurstService burst = new BurstService(captureService, new RegionService(), storage,
                    Runnable::run, scheduler, List.of(-5000L, 0L), List.of(150L));
            List<Path> saved = burst.burst(CaptureRegion.FULL_STREAM).get(5, TimeUnit.SECONDS);
            assertThat(saved).hasSize(3);
            assertThat(saved).allMatch(p -> p.getFileName().toString().contains("_burst_"));
            assertThat(saved.get(0).getFileName().toString()).contains("burst_-5000");
            assertThat(saved.get(1).getFileName().toString()).contains("burst_000");
            assertThat(saved.get(2).getFileName().toString()).contains("burst_+150");
            assertThat(saved.get(2).getParent().getFileName().toString()).isEqualTo("burst");
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void delayedCaptureUsesBufferedFrame(@TempDir Path tmp) throws Exception {
        ScreenRegion stream = new ScreenRegion(0, 0, 64, 64);
        CaptureService captureService = serviceWithFrames(stream);
        StorageService storage = new StorageService(tmp, "tester", "png", 0.92f);
        ScreenshotService screenshots = new ScreenshotService(captureService, new RegionService(),
                storage, Runnable::run);
        Path saved = screenshots.captureDelayed(CaptureRegion.FULL_STREAM, -5000)
                .get(5, TimeUnit.SECONDS).orElseThrow();
        assertThat(saved.getFileName().toString()).endsWith("_stream.png");
    }

    @Test
    void burstLabelFormat() {
        assertThat(StorageService.burstLabel(-1000)).isEqualTo("-1000");
        assertThat(StorageService.burstLabel(-500)).isEqualTo("-500");
        assertThat(StorageService.burstLabel(0)).isEqualTo("000");
        assertThat(StorageService.burstLabel(500)).isEqualTo("+500");
        assertThat(StorageService.burstLabel(1000)).isEqualTo("+1000");
    }
}
