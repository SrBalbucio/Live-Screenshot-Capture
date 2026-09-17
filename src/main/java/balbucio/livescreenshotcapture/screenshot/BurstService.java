package balbucio.livescreenshotcapture.screenshot;

import balbucio.livescreenshotcapture.capture.CapturedFrame;
import balbucio.livescreenshotcapture.capture.CaptureService;
import balbucio.livescreenshotcapture.model.CaptureRegion;
import balbucio.livescreenshotcapture.region.RegionService;
import balbucio.livescreenshotcapture.storage.StorageService;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BurstService {
    private static final Logger log = LoggerFactory.getLogger(BurstService.class);

    public static final List<Long> DEFAULT_PAST_OFFSETS = List.of(-1000L, -500L, 0L);
    public static final List<Long> DEFAULT_FUTURE_OFFSETS = List.of(500L, 1000L);

    private final CaptureService captureService;
    private final RegionService regionService;
    private final StorageService storageService;
    private final Executor ioExecutor;
    private final ScheduledExecutorService scheduler;
    private final List<Long> pastOffsets;
    private final List<Long> futureOffsets;
    private final ImageUpscaler upscaler;
    private volatile int cameraUpscale;
    private volatile boolean keepOriginal;

    public BurstService(CaptureService captureService, RegionService regionService,
            StorageService storageService, Executor ioExecutor, ScheduledExecutorService scheduler) {
        this(captureService, regionService, storageService, ioExecutor, scheduler,
                DEFAULT_PAST_OFFSETS, DEFAULT_FUTURE_OFFSETS, null, 1, false);
    }

    public BurstService(CaptureService captureService, RegionService regionService,
            StorageService storageService, Executor ioExecutor, ScheduledExecutorService scheduler,
            List<Long> pastOffsets, List<Long> futureOffsets) {
        this(captureService, regionService, storageService, ioExecutor, scheduler,
                pastOffsets, futureOffsets, null, 1, false);
    }

    public BurstService(CaptureService captureService, RegionService regionService,
            StorageService storageService, Executor ioExecutor, ScheduledExecutorService scheduler,
            List<Long> pastOffsets, List<Long> futureOffsets, ImageUpscaler upscaler,
            int cameraUpscale) {
        this(captureService, regionService, storageService, ioExecutor, scheduler,
                pastOffsets, futureOffsets, upscaler, cameraUpscale, false);
    }

    public BurstService(CaptureService captureService, RegionService regionService,
            StorageService storageService, Executor ioExecutor, ScheduledExecutorService scheduler,
            List<Long> pastOffsets, List<Long> futureOffsets, ImageUpscaler upscaler,
            int cameraUpscale, boolean keepOriginal) {
        this.captureService = captureService;
        this.regionService = regionService;
        this.storageService = storageService;
        this.ioExecutor = ioExecutor;
        this.scheduler = scheduler;
        this.pastOffsets = List.copyOf(pastOffsets);
        this.futureOffsets = List.copyOf(futureOffsets);
        this.upscaler = upscaler;
        this.cameraUpscale = cameraUpscale;
        this.keepOriginal = keepOriginal;
    }

    public void setCameraUpscale(int cameraUpscale) {
        this.cameraUpscale = cameraUpscale;
    }

    public void setKeepOriginal(boolean keepOriginal) {
        this.keepOriginal = keepOriginal;
    }

    public CompletableFuture<List<Path>> burst(CaptureRegion region) {
        return burstDetailed(region).thenApply(
                frames -> frames.stream().map(BurstFrame::path).toList());
    }

    public CompletableFuture<List<BurstFrame>> burstDetailed(CaptureRegion region) {
        long base = System.currentTimeMillis();
        List<CompletableFuture<Optional<BurstFrame>>> all = new ArrayList<>();
        for (long offset : pastOffsets) {
            all.add(saveFrame(region, captureService.getBuffer().atOffset(base + offset), base, offset));
        }
        for (long offset : futureOffsets) {
            CompletableFuture<Optional<BurstFrame>> slot = new CompletableFuture<>();
            scheduler.schedule(() -> {
                try {
                    saveFrame(region, captureService.latest(), base, offset)
                            .whenComplete((path, err) -> {
                                if (err != null) {
                                    slot.completeExceptionally(err);
                                } else {
                                    slot.complete(path);
                                }
                            });
                } catch (Exception e) {
                    slot.completeExceptionally(e);
                }
            }, offset, TimeUnit.MILLISECONDS);
            all.add(slot);
        }
        return CompletableFuture.allOf(all.toArray(new CompletableFuture[0]))
                .thenApply(v -> all.stream()
                        .map(f -> f.getNow(Optional.empty()))
                        .flatMap(Optional::stream)
                        .toList());
    }

    private CompletableFuture<Optional<BurstFrame>> saveFrame(CaptureRegion region,
            Optional<CapturedFrame> frame, long base, long offset) {
        if (frame.isEmpty()) {
            log.warn("Burst: no buffered frame for offset {}", offset);
            return CompletableFuture.completedFuture(Optional.empty());
        }
        BufferedImage source = frame.get().image();
        Rectangle cropRect = regionService.toFrameRelative(
                captureService.getStreamRegion(), region.bounds(), source.getWidth(), source.getHeight());
        BufferedImage cropped = regionService.crop(source, cropRect);
        int upscale = "stream".equals(region.id()) ? 1 : Math.max(1, cameraUpscale);
        BufferedImage out = upscale > 1 && upscaler != null
                ? upscaler.upscale(cropped, upscale)
                : cropped;
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path saved = storageService.saveBurst(out, base, offset, upscale);
                Path original = null;
                if (keepOriginal && upscale > 1) {
                    original = storageService.saveBurst(cropped, base, offset, 1);
                }
                return Optional.of(new BurstFrame(saved, offset, original));
            } catch (Exception e) {
                log.error("Burst: failed to save frame {}", offset, e);
                return Optional.empty();
            }
        }, ioExecutor);
    }
}
