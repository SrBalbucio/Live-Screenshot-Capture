package balbucio.livescreenshotcapture.preset;

public record CapturePreset(String id, String name, int bufferSeconds, int bufferFps,
        String format, float jpegQuality) {
    public static final CapturePreset HIGH_QUALITY =
            new CapturePreset("high-quality", "High Quality", 5, 10, "png", 0.92f);
    public static final CapturePreset LOW_MEMORY =
            new CapturePreset("low-memory", "Low Memory", 3, 5, "jpg", 0.85f);
    public static final CapturePreset REACTION =
            new CapturePreset("reaction", "Reaction Capture", 8, 15, "png", 0.92f);

    public CapturePreset {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("preset id must not be blank");
        }
        if (bufferSeconds <= 0 || bufferFps <= 0) {
            throw new IllegalArgumentException("bufferSeconds/bufferFps must be > 0");
        }
        if (format == null || (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpg")
                && !format.equalsIgnoreCase("jpeg"))) {
            throw new IllegalArgumentException("format must be png or jpg");
        }
    }

    public long retentionMillis() {
        return bufferSeconds * 1000L;
    }

    public String normalizedFormat() {
        return format.equalsIgnoreCase("jpeg") ? "jpg" : format.toLowerCase();
    }
}
