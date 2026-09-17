package balbucio.livescreenshotcapture.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StorageCleanup {
    private static final Logger log = LoggerFactory.getLogger(StorageCleanup.class);

    public record CleanupResult(long deletedFiles, long freedBytes, long remainingBytes,
            long remainingFiles) {
    }

    public long sizeOf(Path baseDir) {
        return listFiles(baseDir).stream().mapToLong(FileEntry::size).sum();
    }

    public CleanupResult enforceQuota(Path baseDir, long maxBytes) {
        List<FileEntry> files = listFiles(baseDir);
        long total = files.stream().mapToLong(FileEntry::size).sum();
        if (total <= maxBytes) {
            return new CleanupResult(0, 0, total, files.size());
        }
        files.sort(Comparator.comparingLong(FileEntry::modified));
        long deletedFiles = 0;
        long freedBytes = 0;
        for (FileEntry entry : files) {
            if (total <= maxBytes) {
                break;
            }
            try {
                if (Files.deleteIfExists(entry.path())) {
                    total -= entry.size();
                    freedBytes += entry.size();
                    deletedFiles++;
                }
            } catch (IOException e) {
                log.warn("Could not delete old capture {}: {}", entry.path(), e.getMessage());
            }
        }
        log.info("Storage cleanup: deleted {} files, freed {} bytes, {} remains", deletedFiles,
                freedBytes, total);
        return new CleanupResult(deletedFiles, freedBytes, total, files.size() - deletedFiles);
    }

    private List<FileEntry> listFiles(Path baseDir) {
        List<FileEntry> out = new ArrayList<>();
        if (baseDir == null || !Files.isDirectory(baseDir)) {
            return out;
        }
        try (Stream<Path> walk = Files.walk(baseDir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    long size = Files.size(p);
                    FileTime modified = Files.getLastModifiedTime(p);
                    out.add(new FileEntry(p, size, modified.toMillis()));
                } catch (IOException e) {
                    log.debug("Skipping unreadable file {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not scan captures folder: {}", e.getMessage());
        }
        return out;
    }

    private record FileEntry(Path path, long size, long modified) {
    }
}
