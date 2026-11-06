package balbucio.livescreenshotcapture.storage;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class StorageServiceTest {
    @Test
    void savesPngWithExpectedLayout(@TempDir Path tmp) throws Exception {
        StorageService storage = new StorageService(tmp, "streamer-a", "png", 0.92f);
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Path saved = storage.save(img, "camera", System.currentTimeMillis());
        assertThat(Files.exists(saved)).isTrue();
        assertThat(saved.toString()).contains("streamer-a");
        assertThat(saved.getFileName().toString()).endsWith("_camera.png");
    }

    @Test
    void intervalSessionWritesFramesAndMetadata(@TempDir Path tmp) throws Exception {
        StorageService storage = new StorageService(tmp, "streamer-a", "png", 0.92f);
        long startedAt = 1_760_000_000_000L;
        IntervalSession session = new IntervalSession(
                storage.intervalSessionId(startedAt, "camera"), "streamer-a", "Streamer A",
                "default", "Default", "camera", "Camera", 10, 60, "png", startedAt,
                null, null, 0, "test");
        Path dir = storage.startIntervalSession(session);

        assertThat(dir).startsWith(tmp.resolve("streamer-a"));
        assertThat(dir.getParent().getFileName().toString()).isEqualTo("auto");
        assertThat(dir.getFileName().toString()).endsWith("_camera");
        assertThat(Files.exists(dir.resolve("session.json"))).isTrue();
        assertThat(Files.isDirectory(dir.resolve("frames"))).isTrue();

        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Path frame = storage.saveIntervalFrame(img, dir, 1, startedAt + 500);
        assertThat(frame.getParent().getFileName().toString()).isEqualTo("frames");
        assertThat(frame.getFileName().toString()).matches("000001_\\d+\\.png");
        assertThat(Files.exists(frame)).isTrue();

        storage.finishIntervalSession(dir, session, startedAt + 5_000, "manual");
        String json = Files.readString(dir.resolve("session.json"));
        assertThat(json).contains("\"endReason\" : \"manual\"");
        assertThat(json).contains("\"frameCount\" : 1");
        assertThat(json).contains("\"endedAtMillis\" : " + (startedAt + 5_000));
    }

    @Test
    void intervalFrameUsesConfiguredFormat(@TempDir Path tmp) throws Exception {
        StorageService storage = new StorageService(tmp, "streamer-a", "jpg", 0.85f);
        long startedAt = System.currentTimeMillis();
        IntervalSession session = new IntervalSession("id", "streamer-a", "Streamer A",
                "default", "Default", "stream", "Full Stream", 5, 0, "jpg", startedAt,
                null, null, 0, "test");
        Path dir = storage.startIntervalSession(session);
        Path frame = storage.saveIntervalFrame(
                new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), dir, 2, startedAt);
        assertThat(frame.getFileName().toString()).endsWith(".jpg");
    }
}
