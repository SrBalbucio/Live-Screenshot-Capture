package balbucio.livescreenshotcapture.screenshot;

import java.nio.file.Path;

public record BurstFrame(Path path, long offsetMs, Path originalPath) {
    public BurstFrame(Path path, long offsetMs) {
        this(path, offsetMs, null);
    }
}
