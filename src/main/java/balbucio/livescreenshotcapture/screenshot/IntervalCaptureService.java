package balbucio.livescreenshotcapture.screenshot;

import balbucio.livescreenshotcapture.capture.BgraImages;
import balbucio.livescreenshotcapture.capture.CapturedFrame;
import balbucio.livescreenshotcapture.capture.CaptureService;
import balbucio.livescreenshotcapture.model.CaptureRegion;
import balbucio.livescreenshotcapture.region.RegionService;
import balbucio.livescreenshotcapture.storage.IntervalSession;
import balbucio.livescreenshotcapture.storage.StorageService;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically persists the newest buffered frame to a dedicated session folder.
 *
 * <p>It does not start a second capture loop: it reuses {@link CaptureService}, so the cost of
 * enabling auto capture is only the encoding/IO of the frames that are actually kept. Frames are
 * saved at native crop resolution (no upscale) because they are meant to feed a future timelapse.
 */
public class IntervalCaptureService {
    private static final Logger log = LoggerFactory.getLogger(IntervalCaptureService.class);

    public static final int MIN_INTERVAL_SECONDS = 1;
    public static final int MAX_INTERVAL_SECONDS = 3600;
    public static final int UNLIMITED_MINUTES = 0;

    private static final long MINUTE_MILLIS = 60_000L;

    public record Stats(boolean running, String sessionId, String regionId, int intervalSeconds,
            int maxMinutes, long startedAtMillis, int savedFrames, int skippedFrames,
            Path sessionDir) {
    }

    /** All callbacks fire off the JavaFX thread; listeners are responsible for dispatching. */
    public interface Listener {
        default void onFrameSaved(Path path, Stats stats) {
        }

        default void onSkipped(String reason, Stats stats) {
        }

        default void onStopped(String reason, Stats stats) {
        }
    }

    private final CaptureService captureService;
    private final RegionService regionService;
    private final StorageService storageService;
    private final Executor ioExecutor;
    private final Supplier<CaptureRegion> regionSupplier;
    private final LongSupplier clock;
    private final ScheduledExecutorService scheduler;

    private volatile Listener listener = new Listener() {
    };
    private volatile boolean running;
    private volatile int intervalSeconds = 10;
    private volatile int maxMinutes = 60;
    private volatile long startedAtMillis;
    private volatile String sessionId;
    private volatile String regionId;
    private volatile Path sessionDir;
    private volatile IntervalSession session;
    private volatile long lastFrameTimestamp = Long.MIN_VALUE;

    private final AtomicInteger savedFrames = new AtomicInteger();
    private final AtomicInteger skippedFrames = new AtomicInteger();
    private final AtomicInteger nextFrameNumber = new AtomicInteger();
    private final AtomicBoolean saveInFlight = new AtomicBoolean();
    private ScheduledFuture<?> task;

    public IntervalCaptureService(CaptureService captureService, RegionService regionService,
            StorageService storageService, Executor ioExecutor,
            Supplier<CaptureRegion> regionSupplier) {
        this(captureService, regionService, storageService, ioExecutor, regionSupplier,
                System::currentTimeMillis, defaultScheduler());
    }

    IntervalCaptureService(CaptureService captureService, RegionService regionService,
            StorageService storageService, Executor ioExecutor,
            Supplier<CaptureRegion> regionSupplier, LongSupplier clock,
            ScheduledExecutorService scheduler) {
        this.captureService = captureService;
        this.regionService = regionService;
        this.storageService = storageService;
        this.ioExecutor = ioExecutor;
        this.regionSupplier = regionSupplier;
        this.clock = clock;
        this.scheduler = scheduler;
    }

    private static ScheduledExecutorService defaultScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "interval-capture");
            t.setDaemon(true);
            return t;
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? new Listener() {
        } : listener;
    }

    /**
     * Starts a new session. If one is already running it is closed silently and replaced.
     */
    public synchronized void start(String profileId, String profileName, String layoutId,
            String layoutName, int intervalSeconds, int maxMinutes) {
        if (running) {
            closeSession("restarted", false);
        }
        CaptureRegion region = regionSupplier.get();
        if (region == null) {
            throw new IllegalStateException("No capture region available for auto capture");
        }
        this.intervalSeconds = clampInterval(intervalSeconds);
        this.maxMinutes = Math.max(UNLIMITED_MINUTES, maxMinutes);
        this.regionId = region.id();
        this.startedAtMillis = clock.getAsLong();
        this.sessionId = storageService.intervalSessionId(startedAtMillis, regionId);
        this.session = new IntervalSession(sessionId, profileId, profileName, layoutId, layoutName,
                regionId, region.name(), this.intervalSeconds, this.maxMinutes,
                storageService.getFormat(), startedAtMillis, null, null, 0, appVersion());
        try {
            this.sessionDir = storageService.startIntervalSession(session);
        } catch (IOException e) {
            this.session = null;
            this.sessionDir = null;
            throw new IllegalStateException(
                    "Could not create auto capture session: " + e.getMessage(), e);
        }
        this.lastFrameTimestamp = Long.MIN_VALUE;
        this.nextFrameNumber.set(0);
        this.savedFrames.set(0);
        this.skippedFrames.set(0);
        this.saveInFlight.set(false);
        this.running = true;
        scheduleNext(this.intervalSeconds);
        log.info("Auto capture started: {} every {}s (limit: {})", sessionDir, this.intervalSeconds,
                maxMinutes <= 0 ? "unlimited" : maxMinutes + " min");
    }

    public synchronized void stop(String reason) {
        if (!running) {
            return;
        }
        closeSession(reason, true);
    }

    private void closeSession(String reason, boolean notifyListener) {
        running = false;
        ScheduledFuture<?> current = task;
        task = null;
        if (current != null) {
            current.cancel(false);
        }
        IntervalSession finished = session;
        Path dir = sessionDir;
        if (finished != null && dir != null) {
            try {
                storageService.finishIntervalSession(dir, finished, clock.getAsLong(), reason);
            } catch (IOException e) {
                log.warn("Could not finish auto capture session {}: {}", dir, e.getMessage());
            }
        }
        log.info("Auto capture stopped: {} ({})", dir, reason);
        if (notifyListener) {
            fireStopped(reason);
        }
    }

    public synchronized void setIntervalSeconds(int seconds) {
        int clamped = clampInterval(seconds);
        if (clamped == intervalSeconds) {
            return;
        }
        intervalSeconds = clamped;
        if (session != null) {
            session = session.withIntervalSeconds(clamped);
        }
        if (running) {
            if (task != null) {
                task.cancel(false);
            }
            scheduleNext(clamped);
        }
    }

    public synchronized void setMaxMinutes(int minutes) {
        maxMinutes = Math.max(UNLIMITED_MINUTES, minutes);
    }

    public synchronized void shutdown() {
        if (running) {
            closeSession("shutdown", false);
        }
        scheduler.shutdownNow();
    }

    public boolean isRunning() {
        return running;
    }

    public Stats stats() {
        return new Stats(running, sessionId, regionId, intervalSeconds, maxMinutes,
                startedAtMillis, savedFrames.get(), skippedFrames.get(), sessionDir);
    }

    private void scheduleNext(int seconds) {
        task = scheduler.scheduleWithFixedDelay(this::tickSafely, 0, seconds, TimeUnit.SECONDS);
    }

    private void tickSafely() {
        try {
            tick();
        } catch (Exception e) {
            log.warn("Auto capture tick failed: {}", e.getMessage());
        }
    }

    /** Visible for tests: performs exactly one scheduling tick. */
    void tick() {
        if (!running) {
            return;
        }
        long now = clock.getAsLong();
        if (maxMinutes > 0 && now - startedAtMillis >= maxMinutes * MINUTE_MILLIS) {
            stop("duration-limit");
            return;
        }
        if (!captureService.isRunning()) {
            skip("capture-paused");
            return;
        }
        Optional<CapturedFrame> maybeFrame = captureService.latest();
        if (maybeFrame.isEmpty()) {
            skip("no-frame");
            return;
        }
        CapturedFrame frame = maybeFrame.get();
        if (frame.timestampMillis() == lastFrameTimestamp) {
            skip("duplicate-frame");
            return;
        }
        if (!saveInFlight.compareAndSet(false, true)) {
            skip("io-busy");
            return;
        }

        CaptureRegion region = regionSupplier.get();
        Path dir = sessionDir;
        if (region == null || dir == null) {
            saveInFlight.set(false);
            skip("no-region");
            return;
        }
        lastFrameTimestamp = frame.timestampMillis();
        int frameNumber = nextFrameNumber.incrementAndGet();
        try {
            Rectangle cropRect = regionService.toFrameRelative(captureService.getStreamRegion(),
                    region.bounds(), frame.width(), frame.height());
            BufferedImage cropped = BgraImages.cropToImage(frame, cropRect);
            long frameTimestamp = frame.timestampMillis();
            CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return storageService.saveIntervalFrame(
                                    cropped, dir, frameNumber, frameTimestamp);
                        } catch (IOException e) {
                            throw new java.util.concurrent.CompletionException(e);
                        }
                    }, ioExecutor)
                    .whenComplete((path, error) -> {
                        saveInFlight.set(false);
                        if (error != null) {
                            skippedFrames.incrementAndGet();
                            // Let the next tick retry the same buffered frame instead of losing it.
                            lastFrameTimestamp = Long.MIN_VALUE;
                            log.error("Auto capture: failed to save frame {}", frameNumber, error);
                            fireSkipped("save-failed");
                        } else if (path != null) {
                            savedFrames.incrementAndGet();
                            fireFrameSaved(path);
                        }
                    });
        } catch (Exception e) {
            saveInFlight.set(false);
            skippedFrames.incrementAndGet();
            log.warn("Auto capture: frame processing failed: {}", e.getMessage());
            fireSkipped("process-failed");
        }
    }

    private void skip(String reason) {
        skippedFrames.incrementAndGet();
        fireSkipped(reason);
    }

    private void fireFrameSaved(Path path) {
        try {
            listener.onFrameSaved(path, stats());
        } catch (Exception e) {
            log.warn("Auto capture listener failed", e);
        }
    }

    private void fireSkipped(String reason) {
        try {
            listener.onSkipped(reason, stats());
        } catch (Exception e) {
            log.warn("Auto capture listener failed", e);
        }
    }

    private void fireStopped(String reason) {
        try {
            listener.onStopped(reason, stats());
        } catch (Exception e) {
            log.warn("Auto capture listener failed", e);
        }
    }

    private static int clampInterval(int seconds) {
        return Math.max(MIN_INTERVAL_SECONDS, Math.min(MAX_INTERVAL_SECONDS, seconds));
    }

    private static String appVersion() {
        String version = IntervalCaptureService.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }
}
