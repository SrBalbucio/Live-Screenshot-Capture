package balbucio.livescreenshotcapture.capture;

import java.awt.image.BufferedImage;

public record CapturedFrame(long timestampMillis, BufferedImage image) {
    public CapturedFrame {
        if (image == null) {
            throw new IllegalArgumentException("image must not be null");
        }
    }
}
