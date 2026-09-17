package balbucio.livescreenshotcapture.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class StorageCleanupTest {
    private Path file(Path dir, String name, int bytes, long ageSeconds) throws Exception {
        Path p = dir.resolve(name);
        Files.write(p, new byte[bytes]);
        Files.setLastModifiedTime(p, FileTime.from(Instant.now().minusSeconds(ageSeconds)));
        return p;
    }

    @Test
    void deletesOldestFirstUntilUnderQuota(@TempDir Path tmp) throws Exception {
        Path oldest = file(tmp, "old.png", 100, 300);
        Path middle = file(tmp, "mid.png", 200, 200);
        Path newest = file(tmp, "new.png", 300, 100);

        StorageCleanup.CleanupResult result =
                new StorageCleanup().enforceQuota(tmp, 350);

        assertThat(result.deletedFiles()).isEqualTo(2);
        assertThat(result.freedBytes()).isEqualTo(300);
        assertThat(Files.exists(oldest)).isFalse();
        assertThat(Files.exists(middle)).isFalse();
        assertThat(Files.exists(newest)).isTrue();
        assertThat(result.remainingBytes()).isEqualTo(300);
    }

    @Test
    void keepsEverythingUnderQuota(@TempDir Path tmp) throws Exception {
        file(tmp, "a.png", 100, 100);
        StorageCleanup.CleanupResult result =
                new StorageCleanup().enforceQuota(tmp, 1024);
        assertThat(result.deletedFiles()).isZero();
        assertThat(result.remainingFiles()).isEqualTo(1);
    }

    @Test
    void missingDirIsNoop(@TempDir Path tmp) {
        StorageCleanup.CleanupResult result =
                new StorageCleanup().enforceQuota(tmp.resolve("nope"), 10);
        assertThat(result.deletedFiles()).isZero();
        assertThat(result.remainingBytes()).isZero();
    }
}
