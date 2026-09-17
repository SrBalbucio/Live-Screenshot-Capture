package balbucio.livescreenshotcapture.capture;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public interface CaptureBackend {
    BufferedImage capture(Rectangle area) throws CaptureException;
}
