package balbucio.livescreenshotcapture.config;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class SingleInstanceTest {
    @Test
    void secondAcquireFailsWhileFirstHeld(@TempDir Path tmp) throws Exception {
        try (SingleInstance first = SingleInstance.acquire(tmp).orElseThrow()) {
            Optional<SingleInstance> second = SingleInstance.acquire(tmp);
            assertThat(second).isEmpty();
        }
        try (SingleInstance third = SingleInstance.acquire(tmp).orElseThrow()) {
            assertThat(third).isNotNull();
        }
    }
}
