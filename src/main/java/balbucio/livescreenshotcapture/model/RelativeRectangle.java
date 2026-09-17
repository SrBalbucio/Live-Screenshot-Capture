package balbucio.livescreenshotcapture.model;

public record RelativeRectangle(double x, double y, double width, double height) {
    public static final RelativeRectangle FULL = new RelativeRectangle(0, 0, 1, 1);

    public RelativeRectangle {
        if (x < 0 || x > 1 || y < 0 || y > 1 || width <= 0 || height <= 0
                || x + width > 1.000001 || y + height > 1.000001) {
            throw new IllegalArgumentException(
                    "Relative bounds must be within 0..1: " + x + "," + y + "," + width + "," + height);
        }
    }
}
