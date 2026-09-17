package balbucio.livescreenshotcapture.region;

import balbucio.livescreenshotcapture.model.RelativeRectangle;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import java.awt.Rectangle;

public final class RegionGeometry {
    private static final int MIN_SIZE = 10;

    private RegionGeometry() {
    }

    public static Rectangle normalize(int x1, int y1, int x2, int y2) {
        return new Rectangle(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
    }

    public static boolean isValid(Rectangle r) {
        return r != null && r.width >= MIN_SIZE && r.height >= MIN_SIZE;
    }

    public static Rectangle clampToStream(Rectangle selection, ScreenRegion stream) {
        Rectangle bounds = stream.toAwtRectangle();
        Rectangle inter = selection.intersection(bounds);
        if (inter.width < MIN_SIZE || inter.height < MIN_SIZE) {
            return new Rectangle(inter.x, inter.y,
                    Math.max(0, inter.width), Math.max(0, inter.height));
        }
        return inter;
    }

    public static RelativeRectangle toRelative(ScreenRegion stream, Rectangle absolute) {
        double x = (absolute.x - stream.x()) / (double) stream.width();
        double y = (absolute.y - stream.y()) / (double) stream.height();
        double w = absolute.width / (double) stream.width();
        double h = absolute.height / (double) stream.height();
        x = Math.min(1.0, Math.max(0.0, x));
        y = Math.min(1.0, Math.max(0.0, y));
        w = Math.min(1.0 - x, Math.max(0.01, w));
        h = Math.min(1.0 - y, Math.max(0.01, h));
        return new RelativeRectangle(x, y, w, h);
    }
}
