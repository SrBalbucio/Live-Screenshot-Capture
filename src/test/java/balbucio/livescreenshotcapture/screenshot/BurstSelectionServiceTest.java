package balbucio.livescreenshotcapture.screenshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class BurstSelectionServiceTest {
    @Test
    void keepOnlyDeletesTheRest(@TempDir Path tmp) throws Exception {
        Path a = Files.createFile(tmp.resolve("a.png"));
        Path b = Files.createFile(tmp.resolve("b.png"));
        Path c = Files.createFile(tmp.resolve("c.png"));

        List<Path> kept = new BurstSelectionService()
                .keepOnly(List.of(a, b, c), Set.of(a, c));

        assertThat(kept).containsExactlyInAnyOrder(a, c);
        assertThat(Files.exists(a)).isTrue();
        assertThat(Files.exists(b)).isFalse();
        assertThat(Files.exists(c)).isTrue();
    }

    @Test
    void keepAllDeletesNothing(@TempDir Path tmp) throws Exception {
        Path a = Files.createFile(tmp.resolve("a.png"));
        List<Path> kept = new BurstSelectionService().keepOnly(List.of(a), Set.of(a));
        assertThat(kept).containsExactly(a);
        assertThat(Files.exists(a)).isTrue();
    }
}
