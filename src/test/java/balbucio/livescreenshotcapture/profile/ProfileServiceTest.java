package balbucio.livescreenshotcapture.profile;

import balbucio.livescreenshotcapture.model.CaptureRegion;
import balbucio.livescreenshotcapture.model.RelativeRectangle;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import balbucio.livescreenshotcapture.preset.CapturePreset;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ProfileServiceTest {
    @Test
    void createListAndActivate(@TempDir Path tmp) throws Exception {
        ProfileService service = new ProfileService(tmp);
        ScreenRegion stream = new ScreenRegion(120, 80, 1600, 900);
        Profile a = service.create("Streamer A", stream, CaptureRegion.CAMERA.bounds(),
                CapturePreset.HIGH_QUALITY);
        Profile b = service.create("Streamer B", stream, CaptureRegion.CAMERA.bounds(),
                CapturePreset.LOW_MEMORY);

        List<Profile> all = service.list();
        assertThat(all).hasSize(2);

        service.setActive(a.id());
        assertThat(service.getActive()).isPresent();
        assertThat(service.getActive().get().id()).isEqualTo(a.id());

        Profile loaded = service.get(b.id()).orElseThrow();
        assertThat(loaded.preset().id()).isEqualTo("low-memory");
        assertThat(loaded.cameraRegion().bounds().x()).isEqualTo(0.81);
    }

    @Test
    void repositionStreamKeepsRelativeCamera(@TempDir Path tmp) throws Exception {
        ProfileService service = new ProfileService(tmp);
        RelativeRectangle cam = new RelativeRectangle(0.81, 0.03, 0.17, 0.28);
        Profile p = service.create("Streamer A", new ScreenRegion(0, 0, 1600, 900), cam,
                CapturePreset.HIGH_QUALITY);

        Profile moved = service.repositionStream(p.id(), new ScreenRegion(200, 100, 1280, 720));

        assertThat(moved.streamRegion()).isEqualTo(new ScreenRegion(200, 100, 1280, 720));
        assertThat(moved.cameraRegion().bounds()).isEqualTo(cam);
    }

    @Test
    void updateCameraRegion(@TempDir Path tmp) throws Exception {
        ProfileService service = new ProfileService(tmp);
        Profile p = service.create("Streamer A", new ScreenRegion(0, 0, 1600, 900),
                CaptureRegion.CAMERA.bounds(), CapturePreset.HIGH_QUALITY);
        RelativeRectangle next = new RelativeRectangle(0.05, 0.5, 0.3, 0.4);
        Profile updated = service.updateCameraRegion(p.id(), "default", next);
        assertThat(updated.cameraRegion().bounds()).isEqualTo(next);
    }

    @Test
    void getOrCreateDefault(@TempDir Path tmp) throws Exception {
        ProfileService service = new ProfileService(tmp);
        Profile first = service.getOrCreateDefault(new ScreenRegion(10, 20, 640, 480));
        Profile second = service.getOrCreateDefault(new ScreenRegion(10, 20, 640, 480));
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(service.list()).hasSize(1);
    }
}
