package balbucio.livescreenshotcapture.region;

import balbucio.livescreenshotcapture.model.RelativeRectangle;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RegionGeometryTest {
    @Test
    void normalizeHandlesUpsideDownDrag() {
        Rectangle r = RegionGeometry.normalize(300, 200, 100, 50);
        assertThat(r).isEqualTo(new Rectangle(100, 50, 200, 150));
    }

    @Test
    void rejectsTinySelection() {
        assertThat(RegionGeometry.isValid(new Rectangle(0, 0, 5, 5))).isFalse();
        assertThat(RegionGeometry.isValid(new Rectangle(0, 0, 100, 100))).isTrue();
    }

    @Test
    void absoluteToRelativeMatchesSpecExample() {
        ScreenRegion stream = new ScreenRegion(120, 80, 1600, 900);
        Rectangle abs = new Rectangle(120 + (int) (1600 * 0.81), 80 + (int) (900 * 0.03),
                (int) (1600 * 0.17), (int) (900 * 0.28));
        RelativeRectangle rel = RegionGeometry.toRelative(stream, abs);
        assertThat(rel.x()).isCloseTo(0.81, within(0.01));
        assertThat(rel.y()).isCloseTo(0.03, within(0.01));
        assertThat(rel.width()).isCloseTo(0.17, within(0.01));
        assertThat(rel.height()).isCloseTo(0.28, within(0.01));
    }

    @Test
    void clampKeepsSelectionInsideStream() {
        ScreenRegion stream = new ScreenRegion(100, 100, 800, 600);
        Rectangle outside = new Rectangle(0, 0, 500, 500);
        Rectangle clamped = RegionGeometry.clampToStream(outside, stream);
        assertThat(clamped.x).isGreaterThanOrEqualTo(100);
        assertThat(clamped.y).isGreaterThanOrEqualTo(100);
        assertThat(clamped.x + clamped.width).isLessThanOrEqualTo(900);
        assertThat(clamped.y + clamped.height).isLessThanOrEqualTo(700);
    }

    @Test
    void repositionKeepsRelativeStable() {
        ScreenRegion oldStream = new ScreenRegion(0, 0, 1600, 900);
        ScreenRegion newStream = new ScreenRegion(200, 100, 1280, 720);
        Rectangle absCamera = new Rectangle(1400, 120, 200, 252);
        RelativeRectangle rel = RegionGeometry.toRelative(oldStream, absCamera);
        Rectangle reapplied = new RegionService().toAbsolute(newStream, rel);
        assertThat(reapplied.x).isEqualTo(200 + (int) (1280 * rel.x()));
        assertThat(reapplied.width).isEqualTo((int) (1280 * rel.width()));
    }
}
