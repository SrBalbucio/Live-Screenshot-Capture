package balbucio.livescreenshotcapture.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StorageService {
    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter SESSION_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");

    private volatile Path baseDir;
    private volatile String profileId;
    private volatile String format;
    private volatile float jpegQuality;
    private final ObjectMapper sessionMapper;

    public StorageService(Path baseDir, String profileId, String format, float jpegQuality) {
        this.baseDir = baseDir;
        this.profileId = profileId;
        this.format = format.toLowerCase();
        this.jpegQuality = jpegQuality;
        this.sessionMapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature
                        .FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Path save(BufferedImage image, String regionId, long timestampMillis) throws IOException {
        return save(image, regionId, timestampMillis, 1);
    }

    public Path save(BufferedImage image, String regionId, long timestampMillis, int upscale)
            throws IOException {
        LocalDateTime dt = LocalDateTime.now();
        String day = LocalDate.now().format(DAY_FMT);
        String stamp = dt.format(FILE_FMT);
        String ext = format.equals("jpg") ? "jpg" : "png";
        String fileName = stamp + "_" + regionId + upscaleSuffix(upscale) + "." + ext;
        Path dir = baseDir.resolve(profileId).resolve(day).resolve(regionId);
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        write(image, file);
        log.info("Saved screenshot: {}", file);
        return file;
    }

    public Path saveBurst(BufferedImage image, long baseTimestampMillis, long offsetMs)
            throws IOException {
        return saveBurst(image, baseTimestampMillis, offsetMs, 1);
    }

    public Path saveBurst(BufferedImage image, long baseTimestampMillis, long offsetMs,
            int upscale) throws IOException {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(baseTimestampMillis), ZoneId.systemDefault());
        String day = dt.toLocalDate().format(DAY_FMT);
        String stamp = dt.format(FILE_FMT);
        String ext = format.equals("jpg") ? "jpg" : "png";
        String fileName = stamp + "_burst_" + burstLabel(offsetMs) + upscaleSuffix(upscale)
                + "." + ext;
        Path dir = baseDir.resolve(profileId).resolve(day).resolve("burst");
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        write(image, file);
        log.info("Saved burst frame: {}", file);
        return file;
    }

    public static String burstLabel(long offsetMs) {
        if (offsetMs == 0) {
            return "000";
        }
        return offsetMs > 0 ? "+" + offsetMs : String.valueOf(offsetMs);
    }

    /**
     * Human-readable id for one auto capture run, also used as the session folder name.
     */
    public String intervalSessionId(long startedAtMillis, String regionId) {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(startedAtMillis), ZoneId.systemDefault());
        String safeRegion = regionId == null || regionId.isBlank() ? "camera" : regionId;
        return dt.format(SESSION_FMT) + "_" + safeRegion;
    }

    /**
     * Folder that holds one auto capture run:
     * {@code <base>/<profile>/<day>/auto/<timestamp>_<region>/}.
     */
    public Path intervalSessionDir(long startedAtMillis, String regionId) {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(startedAtMillis), ZoneId.systemDefault());
        String day = dt.toLocalDate().format(DAY_FMT);
        return baseDir.resolve(profileId).resolve(day).resolve("auto")
                .resolve(intervalSessionId(startedAtMillis, regionId));
    }

    /** Creates the session folder and writes the initial {@code session.json}. */
    public Path startIntervalSession(IntervalSession session) throws IOException {
        Path dir = intervalSessionDir(session.startedAtMillis(), session.regionId());
        Files.createDirectories(dir.resolve("frames"));
        writeSession(dir, session);
        log.info("Started interval capture session: {}", dir);
        return dir;
    }

    /**
     * Saves one frame inside {@code <sessionDir>/frames}, named so lexical order matches
     * capture order and the real timestamp stays available for timelapse assembly.
     */
    public Path saveIntervalFrame(BufferedImage image, Path sessionDir, int frameNumber,
            long frameTimestampMillis) throws IOException {
        Path framesDir = sessionDir.resolve("frames");
        Files.createDirectories(framesDir);
        String ext = format.equals("jpg") ? "jpg" : "png";
        String fileName = String.format(Locale.ROOT, "%06d_%d.%s",
                frameNumber, frameTimestampMillis, ext);
        Path file = framesDir.resolve(fileName);
        write(image, file);
        log.info("Saved interval frame: {}", file);
        return file;
    }

    /**
     * Rewrites {@code session.json} with the end reason and the number of frames on disk.
     */
    public void finishIntervalSession(Path sessionDir, IntervalSession session,
            long endedAtMillis, String reason) throws IOException {
        IntervalSession finished = session.ended(endedAtMillis, reason, countFrames(sessionDir));
        writeSession(sessionDir, finished);
        log.info("Finished interval capture session: {} ({})", sessionDir, reason);
    }

    private int countFrames(Path sessionDir) {
        Path framesDir = sessionDir.resolve("frames");
        if (!Files.isDirectory(framesDir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(framesDir)) {
            return (int) files.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            log.warn("Could not count interval frames in {}: {}", framesDir, e.getMessage());
            return 0;
        }
    }

    private void writeSession(Path sessionDir, IntervalSession session) throws IOException {
        Files.createDirectories(sessionDir);
        sessionMapper.writeValue(sessionDir.resolve("session.json").toFile(), session);
    }

    static String upscaleSuffix(int upscale) {
        return upscale > 1 ? "@" + upscale + "x" : "";
    }

    private void write(BufferedImage image, Path file) throws IOException {
        if (format.equals("png")) {
            ImageIO.write(image, "png", file.toFile());
            return;
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "png", file.toFile());
            return;
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(jpegQuality);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    public Path getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(Path baseDir) {
        this.baseDir = baseDir;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format.toLowerCase();
    }
}
