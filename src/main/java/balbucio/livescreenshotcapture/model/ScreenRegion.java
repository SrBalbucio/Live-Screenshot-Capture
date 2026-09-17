package balbucio.livescreenshotcapture.model;

import java.awt.Rectangle;

public record ScreenRegion(int x, int y, int width, int height) {
    public ScreenRegion {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be > 0: " + width + "x" + height);
        }
    }

    public Rectangle toAwtRectangle() {
        return new Rectangle(x, y, width, height);
    }
}
