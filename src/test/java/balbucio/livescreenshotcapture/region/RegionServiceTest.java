package balbucio.livescreenshotcapture.region;

import balbucio.livescreenshotcapture.model.RelativeRectangle;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegionServiceTest {
    private final RegionService service = new RegionService();

    @Test
    void fullStreamMapsToWholeArea() {
        ScreenRegion stream = new ScreenRegion(120, 80, 1600, 900);
        Rectangle abs = service.toAbsolute(stream, RelativeRectangle.FULL);
        assertThat(abs).isEqualTo(new Rectangle(120, 80, 1600, 900));
    }

    @Test
    void cameraExampleFromSpec() {
        ScreenRegion stream = new ScreenRegion(120, 80, 1600, 900);
        RelativeRectangle cam = new RelativeRectangle(0.81, 0.03, 0.17, 0.28);
        Rectangle abs = service.toAbsolute(stream, cam);
        assertThat(abs.x).isEqualTo(120 + (int) (1600 * 0.81));
        assertThat(abs.y).isEqualTo(80 + (int) (900 * 0.03));
        assertThat(abs.width).isEqualTo((int) (1600 * 0.17));
        assertThat(abs.height).isEqualTo((int) (900 * 0.28));
    }

    @Test
    void invalidRelativeRejected() {
        assertThatThrownBy(() -> new RelativeRectangle(0.9, 0.9, 0.2, 0.2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cropClampsToFrame() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        BufferedImage cropped = service.crop(img, new Rectangle(90, 90, 50, 50));
        assertThat(cropped.getWidth()).isEqualTo(10);
        assertThat(cropped.getHeight()).isEqualTo(10);
    }
}
