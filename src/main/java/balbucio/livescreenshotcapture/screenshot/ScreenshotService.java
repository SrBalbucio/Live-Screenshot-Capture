package balbucio.livescreenshotcapture.screenshot;

import balbucio.livescreenshotcapture.capture.CapturedFrame;
import balbucio.livescreenshotcapture.capture.CaptureService;
import balbucio.livescreenshotcapture.model.CaptureRegion;
import balbucio.livescreenshotcapture.region.RegionService;
import balbucio.livescreenshotcapture.storage.StorageService;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScreenshotService {
    private static final Logger log = LoggerFactory.getLogger(ScreenshotService.class);

    private final CaptureService captureService;
    private final RegionService regionService;
    private final StorageService storageService;
    private final Executor ioExecutor;

    public ScreenshotService(CaptureService captureService, RegionService regionService,
            StorageService storageService, Executor ioExecutor) {
        this.captureService = captureService;
        this.regionService = regionService;
        this.storageService = storageService;
        this.ioExecutor = ioExecutor;
    }

    public CompletableFuture<Optional<Path>> capture(CaptureRegion region) {
        Optional<CapturedFrame> frame = captureService.latest();
        if (frame.isEmpty()) {
            log.warn("No buffered frame available");
            return CompletableFuture.completedFuture(Optional.empty());
        }
        BufferedImage source = frame.get().image();
        Rectangle cropRect = regionService.toFrameRelative(
                captureService.getStreamRegion(), region.bounds(), source.getWidth(), source.getHeight());
        BufferedImage cropped = regionService.crop(source, cropRect);
        long ts = frame.get().timestampMillis();
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path p = storageService.save(cropped, region.id(), ts);
                return Optional.of(p);
            } catch (Exception e) {
                log.error("Failed to save screenshot", e);
                return Optional.empty();
            }
        }, ioExecutor);
    }

    public CompletableFuture<Optional<Path>> captureStream() {
        return capture(CaptureRegion.FULL_STREAM);
    }

    public CompletableFuture<Optional<Path>> captureCamera() {
        return capture(CaptureRegion.CAMERA);
    }
}
