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
}
