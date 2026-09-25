package balbucio.livescreenshotcapture.capture;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AWT Robot fallback in BGRA form. Slower than DXGI by an order of magnitude but
 * works everywhere: no native library, captures arbitrary desktop rectangles
 * (including regions spanning multiple monitors) and always returns fresh pixels.
 */
public class RobotCaptureBackend implements CaptureBackend {
    private static final Logger log = LoggerFactory.getLogger(RobotCaptureBackend.class);
    private final Robot robot;

    public RobotCaptureBackend() throws CaptureException {
        try {
            this.robot = new Robot();
        } catch (Exception e) {
            throw new CaptureException("Failed to initialize AWT Robot", e);
        }
    }

    RobotCaptureBackend(Robot robot) {
        this.robot = robot;
    }

    @Override
    public String id() {
        return "robot";
    }

    @Override
    public CapturedFrame capture(Rectangle area) throws CaptureException {
        if (area == null || area.width <= 0 || area.height <= 0) {
            throw new CaptureException("Invalid capture area: " + area);
        }
        try {
            BufferedImage img = robot.createScreenCapture(area);
            int w = img.getWidth();
            int h = img.getHeight();
            int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
            ByteBuffer px = ByteBuffer.allocateDirect(w * h * 4)
                    .order(ByteOrder.LITTLE_ENDIAN);
            px.asIntBuffer().put(argb, 0, w * h);
            return new CapturedFrame(System.currentTimeMillis(), w, h, w * 4, px);
        } catch (Exception e) {
            throw new CaptureException("Robot capture failed for " + area, e);
        }
    }
}
