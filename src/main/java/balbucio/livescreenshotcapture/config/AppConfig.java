package balbucio.livescreenshotcapture.config;

import balbucio.livescreenshotcapture.model.ScreenRegion;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private final int bufferSeconds;
    private final int bufferFps;
    private final String format;
    private final float jpegQuality;
    private final ScreenRegion streamRegion;
    private final Path baseDir;

    public AppConfig(int bufferSeconds, int bufferFps, String format, float jpegQuality,
            ScreenRegion streamRegion, Path baseDir) {
        this.bufferSeconds = bufferSeconds;
        this.bufferFps = bufferFps;
        this.format = format;
        this.jpegQuality = jpegQuality;
        this.streamRegion = streamRegion;
        this.baseDir = baseDir;
    }

    public static AppConfig load() {
        Properties p = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/app.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            log.warn("Could not load app.properties, using defaults");
        }
        int bufferSeconds = Integer.parseInt(p.getProperty("buffer.seconds", "5"));
        int bufferFps = Integer.parseInt(p.getProperty("buffer.fps", "10"));
        String format = p.getProperty("capture.format", "png");
        float jpegQuality = Float.parseFloat(p.getProperty("capture.jpegQuality", "0.92"));
        ScreenRegion stream = parseStreamRegion(p.getProperty("stream.region", ""));
        Path baseDir = Paths.get(p.getProperty("storage.dir", "captures"));
        return new AppConfig(bufferSeconds, bufferFps, format, jpegQuality, stream, baseDir);
    }

    private static ScreenRegion parseStreamRegion(String value) {
        if (value != null && !value.isBlank()) {
            try {
                String[] parts = value.split(",");
                return new ScreenRegion(
                        Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
            } catch (Exception e) {
                log.warn("Invalid stream.region '{}', using default", value);
            }
        }
        try {
            Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
            int w = Math.min(1280, bounds.width);
            int h = Math.min(720, bounds.height);
            int x = bounds.x + (bounds.width - w) / 2;
            int y = bounds.y + (bounds.height - h) / 2;
            return new ScreenRegion(x, y, w, h);
        } catch (Exception e) {
            return new ScreenRegion(100, 100, 1280, 720);
        }
    }

    public int bufferSeconds() {
        return bufferSeconds;
    }

    public int bufferFps() {
        return bufferFps;
    }

    public String format() {
        return format;
    }

    public float jpegQuality() {
        return jpegQuality;
    }

    public ScreenRegion streamRegion() {
        return streamRegion;
    }

    public Path baseDir() {
        return baseDir;
    }

    public long retentionMillis() {
        return bufferSeconds * 1000L;
    }
}
