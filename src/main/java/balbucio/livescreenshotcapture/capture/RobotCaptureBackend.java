package balbucio.livescreenshotcapture.capture;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public BufferedImage capture(Rectangle area) throws CaptureException {
        if (area == null || area.width <= 0 || area.height <= 0) {
            throw new CaptureException("Invalid capture area: " + area);
        }
        try {
            return robot.createScreenCapture(area);
        } catch (Exception e) {
            throw new CaptureException("Robot capture failed for " + area, e);
        }
    }
}
