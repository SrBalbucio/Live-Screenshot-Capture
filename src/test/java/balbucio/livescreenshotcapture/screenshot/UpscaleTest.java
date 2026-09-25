package balbucio.livescreenshotcapture.screenshot;

import balbucio.livescreenshotcapture.buffer.FrameBuffer;
import balbucio.livescreenshotcapture.capture.CaptureBackend;
import balbucio.livescreenshotcapture.capture.CaptureService;
import balbucio.livescreenshotcapture.capture.CapturedFrame;
import balbucio.livescreenshotcapture.capture.TestFrames;
import balbucio.livescreenshotcapture.model.CaptureRegion;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import balbucio.livescreenshotcapture.region.RegionService;
import balbucio.livescreenshotcapture.storage.StorageService;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class UpscaleTest {
    private static BufferedImage img(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    void bicubicScalesProgressively() {
        BicubicUpscaler upscaler = new BicubicUpscaler();
        assertThat(upscaler.upscale(img(100, 60), 2).getWidth()).isEqualTo(200);
        assertThat(upscaler.upscale(img(100, 60), 2).getHeight()).isEqualTo(120);
        assertThat(upscaler.upscale(img(100, 60), 3).getWidth()).isEqualTo(300);
        BufferedImage same = img(40, 40);
        assertThat(upscaler.upscale(same, 1)).isSameAs(same);
    }

    private CaptureService serviceWithFrame(ScreenRegion stream, int w, int h) {
        CaptureBackend stub = new CaptureBackend() {
            @Override
            public String id() {
                return "stub";
            }

            @Override
            public CapturedFrame capture(Rectangle area) {
                return TestFrames.solid(System.currentTimeMillis(), w, h);
            }
        };
        FrameBuffer buffer = new FrameBuffer(10_000);
        CaptureService service = new CaptureService(stub, buffer, stream, 10);
        buffer.push(TestFrames.solid(System.currentTimeMillis(), w, h));
        return service;
    }

    @Test
    void cameraCaptureIsUpscaledWithSuffix(@TempDir Path tmp) throws Exception {
        ScreenRegion stream = new ScreenRegion(0, 0, 200, 100);
        CaptureService captureService = serviceWithFrame(stream, 200, 100);
        StorageService storage = new StorageService(tmp, "tester", "png", 0.92f);
        ScreenshotService screenshots = new ScreenshotService(captureService, new RegionService(),
                storage, Runnable::run, new BicubicUpscaler(), 2);

        Path saved = screenshots.capture(CaptureRegion.CAMERA)
                .get(5, TimeUnit.SECONDS).orElseThrow();
        assertThat(saved.getFileName().toString()).contains("camera@2x.png");
        BufferedImage onDisk = ImageIO.read(saved.toFile());
        assertThat(onDisk.getWidth()).isEqualTo((int) (200 * 0.17) * 2);
        assertThat(onDisk.getHeight()).isEqualTo((int) (100 * 0.28) * 2);
    }

    @Test
    void streamCaptureNeverUpscaled(@TempDir Path tmp) throws Exception {
        ScreenRegion stream = new ScreenRegion(0, 0, 200, 100);
        CaptureService captureService = serviceWithFrame(stream, 200, 100);
        StorageService storage = new StorageService(tmp, "tester", "png", 0.92f);
        ScreenshotService screenshots = new ScreenshotService(captureService, new RegionService(),
                storage, Runnable::run, new BicubicUpscaler(), 2);

        Path saved = screenshots.captureStream().get(5, TimeUnit.SECONDS).orElseThrow();
        assertThat(saved.getFileName().toString()).doesNotContain("@");
        assertThat(ImageIO.read(saved.toFile()).getWidth()).isEqualTo(200);
    }

    @Test
    void keepOriginalSavesBothVersions(@TempDir Path tmp) throws Exception {
        ScreenRegion stream = new ScreenRegion(0, 0, 200, 100);
        CaptureService captureService = serviceWithFrame(stream, 200, 100);
        StorageService storage = new StorageService(tmp, "tester", "png", 0.92f);
        ScreenshotService screenshots = new ScreenshotService(captureService, new RegionService(),
                storage, Runnable::run, new BicubicUpscaler(), 2, true);

        Path upscaled = screenshots.capture(CaptureRegion.CAMERA)
                .get(5, TimeUnit.SECONDS).orElseThrow();
        assertThat(upscaled.getFileName().toString()).contains("camera@2x.png");
        Path original;
        try (var files = java.nio.file.Files.list(upscaled.getParent())) {
            List<Path> originals = files
                    .filter(p -> p.getFileName().toString().endsWith("_camera.png"))
                    .toList();
            assertThat(originals).hasSize(1);
            original = originals.get(0);
        }
        BufferedImage originalImg = ImageIO.read(original.toFile());
        assertThat(originalImg.getWidth()).isEqualTo((int) (200 * 0.17));
    }

    @Test
    void keepOriginalOffSavesSingleFile(@TempDir Path tmp) throws Exception {
        ScreenRegion stream = new ScreenRegion(0, 0, 200, 100);
        CaptureService captureService = serviceWithFrame(stream, 200, 100);
        StorageService storage = new StorageService(tmp, "tester", "png", 0.92f);
        ScreenshotService screenshots = new ScreenshotService(captureService, new RegionService(),
                storage, Runnable::run, new BicubicUpscaler(), 2, false);

        Path upscaled = screenshots.capture(CaptureRegion.CAMERA)
                .get(5, TimeUnit.SECONDS).orElseThrow();
        long count;
        try (var files = java.nio.file.Files.list(upscaled.getParent())) {
            count = files.count();
        }
        assertThat(count).isEqualTo(1);
    }
}
