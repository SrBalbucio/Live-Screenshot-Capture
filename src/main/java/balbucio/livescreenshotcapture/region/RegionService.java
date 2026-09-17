package balbucio.livescreenshotcapture.region;

import balbucio.livescreenshotcapture.model.RelativeRectangle;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public class RegionService {

    public Rectangle toAbsolute(ScreenRegion stream, RelativeRectangle relative) {
        int x = stream.x() + (int) (stream.width() * relative.x());
        int y = stream.y() + (int) (stream.height() * relative.y());
        int w = (int) (stream.width() * relative.width());
        int h = (int) (stream.height() * relative.height());
        return new Rectangle(x, y, w, h);
    }

    public Rectangle toFrameRelative(ScreenRegion stream, RelativeRectangle relative, int frameWidth, int frameHeight) {
        int x = (int) (frameWidth * relative.x());
        int y = (int) (frameHeight * relative.y());
        int w = (int) (frameWidth * relative.width());
        int h = (int) (frameHeight * relative.height());
        return clampTo(new Rectangle(x, y, w, h), frameWidth, frameHeight);
    }

    public BufferedImage crop(BufferedImage frame, Rectangle frameRelative) {
        Rectangle safe = clampTo(frameRelative, frame.getWidth(), frame.getHeight());
        return frame.getSubimage(safe.x, safe.y, safe.width, safe.height);
    }

    private Rectangle clampTo(Rectangle r, int maxW, int maxH) {
        int x = Math.max(0, Math.min(r.x, maxW - 1));
        int y = Math.max(0, Math.min(r.y, maxH - 1));
        int w = Math.max(1, Math.min(r.width, maxW - x));
        int h = Math.max(1, Math.min(r.height, maxH - y));
        return new Rectangle(x, y, w, h);
    }
}
