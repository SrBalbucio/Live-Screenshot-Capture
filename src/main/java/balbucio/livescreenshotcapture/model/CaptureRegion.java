package balbucio.livescreenshotcapture.model;

public record CaptureRegion(String id, String name, RelativeRectangle bounds) {
    public static final CaptureRegion FULL_STREAM =
            new CaptureRegion("stream", "Full Stream", RelativeRectangle.FULL);
    public static final CaptureRegion CAMERA =
            new CaptureRegion("camera", "Camera", new RelativeRectangle(0.81, 0.03, 0.17, 0.28));

    public CaptureRegion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
    }
}
