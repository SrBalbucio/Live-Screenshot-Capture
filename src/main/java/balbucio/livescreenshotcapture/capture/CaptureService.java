package balbucio.livescreenshotcapture.capture;

import balbucio.livescreenshotcapture.buffer.FrameBuffer;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaptureService {
    private static final Logger log = LoggerFactory.getLogger(CaptureService.class);

    private final CaptureBackend backend;
    private final FrameBuffer buffer;
    private volatile ScreenRegion streamRegion;
    private volatile int bufferFps;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private final AtomicLong captureCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();

    public CaptureService(CaptureBackend backend, FrameBuffer buffer, ScreenRegion streamRegion, int bufferFps) {
        this.backend = backend;
        this.buffer = buffer;
        this.streamRegion = streamRegion;
        this.bufferFps = bufferFps;
    }

    public synchronized void start() {
        if (task != null && !task.isDone()) {
            return;
        }
        long periodMs = Math.max(16, 1000L / Math.max(1, bufferFps));
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "capture-thread");
            t.setDaemon(true);
            return t;
        });
        log.info("Starting capture: region={} fps={}", streamRegion, bufferFps);
        task = scheduler.scheduleAtFixedRate(this::captureOnce, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(true);
            task = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        log.info("Capture stopped");
    }

    public boolean isRunning() {
        return task != null && !task.isDone();
    }

    void captureOnce() {
        try {
            BufferedImage img = backend.capture(streamRegion.toAwtRectangle());
            buffer.push(new CapturedFrame(System.currentTimeMillis(), img));
            captureCount.incrementAndGet();
        } catch (Exception e) {
            errorCount.incrementAndGet();
            log.warn("Capture failed: {}", e.getMessage());
        }
    }

    public Optional<CapturedFrame> latest() {
        return buffer.latest();
    }

    public Optional<CapturedFrame> atOffsetMillis(long offsetFromNow) {
        return buffer.atOffset(System.currentTimeMillis() + offsetFromNow);
    }

    public void setStreamRegion(ScreenRegion region) {
        this.streamRegion = region;
        buffer.clear();
    }

    public void setBufferFps(int fps) {
        this.bufferFps = fps;
        if (isRunning()) {
            stop();
            start();
        }
    }

    public ScreenRegion getStreamRegion() {
        return streamRegion;
    }

    public FrameBuffer getBuffer() {
        return buffer;
    }

    public long getCaptureCount() {
        return captureCount.get();
    }

    public long getErrorCount() {
        return errorCount.get();
    }
}
