package balbucio.livescreenshotcapture.capture;

import balbucio.capturegraphics.api.DisplayId;
import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DxgiCaptureBackendTest {

    private static DisplayId display(String id, int x, int y, int w, int h) {
        return new DisplayId(id, id, x, y, w, h, false);
    }

    @Test
    void picksDisplayContainingAreaCenter() {
        List<DisplayId> displays = List.of(
                display("dxgi-output-0", 0, 0, 1920, 1080),
                display("dxgi-output-1", 1920, 0, 2560, 1440));
        assertThat(DxgiCaptureBackend.resolveDisplay(displays, new Rectangle(2000, 100, 100, 100)))
                .extracting(DisplayId::id).isEqualTo("dxgi-output-1");
        assertThat(DxgiCaptureBackend.resolveDisplay(displays, new Rectangle(10, 10, 100, 100)))
                .extracting(DisplayId::id).isEqualTo("dxgi-output-0");
    }

    @Test
    void negativeDesktopOriginsAreSupported() {
        List<DisplayId> displays = List.of(
                display("dxgi-output-0", -1920, 0, 1920, 1080),
                display("dxgi-output-1", 0, 0, 3840, 2160));
        assertThat(DxgiCaptureBackend.resolveDisplay(displays, new Rectangle(-1800, 500, 100, 100)))
                .extracting(DisplayId::id).isEqualTo("dxgi-output-0");
    }

    @Test
    void fallsBackToFirstDisplayWhenOutside() {
        List<DisplayId> displays = List.of(
                display("dxgi-output-0", 0, 0, 1920, 1080),
                display("dxgi-output-1", 1920, 0, 2560, 1440));
        assertThat(DxgiCaptureBackend.resolveDisplay(displays, new Rectangle(9000, 9000, 100, 100)))
                .extracting(DisplayId::id).isEqualTo("dxgi-output-0");
    }

    @Test
    void emptyDisplayListYieldsNull() {
        assertThat(DxgiCaptureBackend.resolveDisplay(List.of(), new Rectangle(0, 0, 10, 10)))
                .isNull();
    }
}
