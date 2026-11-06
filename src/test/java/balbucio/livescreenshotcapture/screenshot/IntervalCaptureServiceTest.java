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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class IntervalCaptureServiceTest {

    private static final ScreenRegion STREAM = new ScreenRegion(0, 0, 64, 64);

    private static final class StubCaptureService extends CaptureService {
        private volatile boolean running = true;
        private volatile CapturedFrame frame;

        StubCaptureService() {
            super(new CaptureBackend() {
                @Override
                public String id() {
                    return "stub";
                }

                @Override
                public CapturedFrame capture(Rectangle area) {
                    return null;
                }
            }, new FrameBuffer(10_000), STREAM, 10);
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public Optional<CapturedFrame> latest() {
            return Optional.ofNullable(frame);
        }

        @Override
        public ScreenRegion getStreamRegion() {
            return STREAM;
        }

        void setFrame(CapturedFrame next) {
            this.frame = next;
        }

        void setRunning(boolean next) {
            this.running = next;
        }
    }

    private record Fixture(StubCaptureService capture, IntervalCaptureService service,
            StorageService storage, AtomicLong clock) {
    }

    private Fixture fixture(Path tmp, long now) {
        StubCaptureService capture = new StubCaptureService();
        StorageService storage = new StorageService(tmp, "streamer-a", "png", 0.92f);
        AtomicLong clock = new AtomicLong(now);
        IntervalCaptureService service = new IntervalCaptureService(capture, new RegionService(),
                storage, Runnable::run, () -> CaptureRegion.CAMERA, clock::get,
                new ManualScheduler());
        return new Fixture(capture, service, storage, clock);
    }

    /** Scheduler that never runs tasks so tests can call {@link IntervalCaptureService#tick()}. */
    private static final class ManualScheduler implements ScheduledExecutorService {
        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
                long delay, TimeUnit unit) {
            return new FakeFuture();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout,
                TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,
                long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeFuture implements ScheduledFuture<Object> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }

    private List<Path> frames(Path sessionDir) throws Exception {
        try (var stream = Files.list(sessionDir.resolve("frames"))) {
            return stream.sorted().toList();
        }
    }

    @Test
    void tickSavesLatestFrameAndSkipsDuplicates(@TempDir Path tmp) throws Exception {
        Fixture f = fixture(tmp, 1_760_000_000_000L);
        f.service().start("streamer-a", "Streamer A", "default", "Default", 5, 60);

        f.capture().setFrame(TestFrames.solid(f.clock().get(), 64, 64, 0xFF112233));
        f.service().tick();
        assertThat(f.service().stats().savedFrames()).isEqualTo(1);
        List<Path> first = frames(f.service().stats().sessionDir());
        assertThat(first).hasSize(1);
        assertThat(first.get(0).getFileName().toString()).matches("000001_\\d+\\.png");

        // Same buffered frame: the interval fired faster than the capture FPS.
        f.service().tick();
        assertThat(f.service().stats().savedFrames()).isEqualTo(1);
        assertThat(f.service().stats().skippedFrames()).isEqualTo(1);
        assertThat(frames(f.service().stats().sessionDir())).hasSize(1);

        f.clock().addAndGet(5_000);
        f.capture().setFrame(TestFrames.solid(f.clock().get(), 64, 64, 0xFF445566));
        f.service().tick();
        assertThat(f.service().stats().savedFrames()).isEqualTo(2);
        assertThat(frames(f.service().stats().sessionDir())).hasSize(2);

        f.service().stop("manual");
        assertThat(f.service().isRunning()).isFalse();
        String json = Files.readString(
                f.service().stats().sessionDir().resolve("session.json"));
        assertThat(json).contains("\"endReason\" : \"manual\"");
        assertThat(json).contains("\"frameCount\" : 2");
    }

    @Test
    void pausedCaptureSkipsWithoutSaving(@TempDir Path tmp) throws Exception {
        Fixture f = fixture(tmp, 1_760_000_000_000L);
        f.service().start("streamer-a", "Streamer A", "default", "Default", 5, 60);
        f.capture().setFrame(TestFrames.solid(f.clock().get(), 64, 64));
        f.capture().setRunning(false);

        f.service().tick();
        assertThat(f.service().stats().savedFrames()).isZero();
        assertThat(f.service().stats().skippedFrames()).isEqualTo(1);
        assertThat(frames(f.service().stats().sessionDir())).isEmpty();
    }

    @Test
    void missingFrameSkips(@TempDir Path tmp) {
        Fixture f = fixture(tmp, 1_760_000_000_000L);
        f.service().start("streamer-a", "Streamer A", "default", "Default", 5, 60);
        f.service().tick();
        assertThat(f.service().stats().savedFrames()).isZero();
        assertThat(f.service().stats().skippedFrames()).isEqualTo(1);
    }

    @Test
    void durationLimitStopsSession(@TempDir Path tmp) throws Exception {
        Fixture f = fixture(tmp, 1_760_000_000_000L);
        f.service().start("streamer-a", "Streamer A", "default", "Default", 5, 1);
        f.capture().setFrame(TestFrames.solid(f.clock().get(), 64, 64));
        f.service().tick();

        f.clock().addAndGet(60_001);
        f.capture().setFrame(TestFrames.solid(f.clock().get(), 64, 64, 0xFF00FF00));
        f.service().tick();

        assertThat(f.service().isRunning()).isFalse();
        String json = Files.readString(
                f.service().stats().sessionDir().resolve("session.json"));
        assertThat(json).contains("\"endReason\" : \"duration-limit\"");
        assertThat(json).contains("\"frameCount\" : 1");
    }

    @Test
    void intervalIsClampedToMinimum(@TempDir Path tmp) {
        Fixture f = fixture(tmp, 1_760_000_000_000L);
        f.service().start("streamer-a", "Streamer A", "default", "Default", 0, 60);
        assertThat(f.service().stats().intervalSeconds())
                .isEqualTo(IntervalCaptureService.MIN_INTERVAL_SECONDS);
    }

    @Test
    void sessionFolderCarriesProfileAndRegion(@TempDir Path tmp) {
        Fixture f = fixture(tmp, 1_760_000_000_000L);
        f.service().start("streamer-a", "Streamer A", "default", "Default", 10, 0);
        Path dir = f.service().stats().sessionDir();
        assertThat(dir.toString()).contains("streamer-a");
        assertThat(dir.getParent().getFileName().toString()).isEqualTo("auto");
        assertThat(dir.getFileName().toString()).endsWith("_camera");
        assertThat(Files.exists(dir.resolve("session.json"))).isTrue();
    }
}
