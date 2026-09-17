package balbucio.livescreenshotcapture.screenshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BurstSelectionService {
    private static final Logger log = LoggerFactory.getLogger(BurstSelectionService.class);

    public List<Path> keepOnly(List<Path> all, Set<Path> keep) throws IOException {
        List<Path> kept = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Path p : all) {
            if (keep.contains(p)) {
                kept.add(p);
                continue;
            }
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                failed.add(p.getFileName().toString());
                log.warn("Could not delete burst frame {}: {}", p, e.getMessage());
            }
        }
        if (!failed.isEmpty()) {
            throw new IOException("Could not delete: " + String.join(", ", failed));
        }
        return kept;
    }
}
