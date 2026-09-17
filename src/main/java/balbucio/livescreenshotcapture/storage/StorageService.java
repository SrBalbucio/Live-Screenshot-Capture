package balbucio.livescreenshotcapture.storage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    private final Path baseDir;
    private volatile String profileId;
    private volatile String format;
    private volatile float jpegQuality;

    public StorageService(Path baseDir, String profileId, String format, float jpegQuality) {
        this.baseDir = baseDir;
        this.profileId = profileId;
        this.format = format.toLowerCase();
        this.jpegQuality = jpegQuality;
    }

    public Path save(BufferedImage image, String regionId, long timestampMillis) throws IOException {
        LocalDateTime dt = LocalDateTime.now();
        String day = LocalDate.now().format(DAY_FMT);
        String stamp = dt.format(FILE_FMT);
        String ext = format.equals("jpg") ? "jpg" : "png";
        String fileName = stamp + "_" + regionId + "." + ext;
        Path dir = baseDir.resolve(profileId).resolve(day).resolve(regionId);
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        write(image, file);
        log.info("Saved screenshot: {}", file);
        return file;
    }

    public Path saveBurst(BufferedImage image, long baseTimestampMillis, long offsetMs)
            throws IOException {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(baseTimestampMillis), ZoneId.systemDefault());
        String day = dt.toLocalDate().format(DAY_FMT);
        String stamp = dt.format(FILE_FMT);
        String ext = format.equals("jpg") ? "jpg" : "png";
        String fileName = stamp + "_burst_" + burstLabel(offsetMs) + "." + ext;
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
