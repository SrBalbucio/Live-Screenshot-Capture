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
    private final ImageUpscaler upscaler;
    private volatile int cameraUpscale;

    public ScreenshotService(CaptureService captureService, RegionService regionService,
            StorageService storageService, Executor ioExecutor) {
        this(captureService, regionService, storageService, ioExecutor, null, 1);
    }

    public ScreenshotService(CaptureService captureService, RegionService regionService,
            StorageService storageService, Executor ioExecutor, ImageUpscaler upscaler,
            int cameraUpscale) {
        this.captureService = captureService;
        this.regionService = regionService;
        this.storageService = storageService;
        this.ioExecutor = ioExecutor;
        this.upscaler = upscaler;
        this.cameraUpscale = cameraUpscale;
    }

    public void setCameraUpscale(int cameraUpscale) {
        this.cameraUpscale = cameraUpscale;
    }

    public CompletableFuture<Optional<Path>> capture(CaptureRegion region) {
        return captureDelayed(region, 0);
    }

    public CompletableFuture<Optional<Path>> captureDelayed(CaptureRegion region, long offsetFromNowMillis) {
        Optional<CapturedFrame> frame = offsetFromNowMillis == 0
                ? captureService.latest()
                : captureService.atOffsetMillis(offsetFromNowMillis);
        if (frame.isEmpty()) {
            log.warn("No buffered frame available for offset {}", offsetFromNowMillis);
            return CompletableFuture.completedFuture(Optional.empty());
        }
        BufferedImage source = frame.get().image();
        Rectangle cropRect = regionService.toFrameRelative(
                captureService.getStreamRegion(), region.bounds(), source.getWidth(), source.getHeight());
        BufferedImage cropped = regionService.crop(source, cropRect);
        int upscale = upscaleFor(region);
        BufferedImage out = upscale > 1 && upscaler != null
                ? upscaler.upscale(cropped, upscale)
                : cropped;
        long ts = frame.get().timestampMillis();
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path p = storageService.save(out, region.id(), ts, upscale);
                return Optional.of(p);
            } catch (Exception e) {
                log.error("Failed to save screenshot", e);
                return Optional.empty();
            }
        }, ioExecutor);
    }

    private int upscaleFor(CaptureRegion region) {
        if (cameraUpscale <= 1 || "stream".equals(region.id())) {
            return 1;
        }
        return cameraUpscale;
    }

    public CompletableFuture<Optional<Path>> captureStream() {
        return capture(CaptureRegion.FULL_STREAM);
    }

    public CompletableFuture<Optional<Path>> captureCamera() {
        return capture(CaptureRegion.CAMERA);
    }
}
