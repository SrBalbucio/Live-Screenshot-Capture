package balbucio.livescreenshotcapture.profile;

import balbucio.livescreenshotcapture.model.CaptureRegion;
import balbucio.livescreenshotcapture.model.RelativeRectangle;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import balbucio.livescreenshotcapture.preset.CapturePreset;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileLayoutsTest {
    private Profile createBase(ProfileService service) throws Exception {
        return service.create("Streamer A", new ScreenRegion(0, 0, 1600, 900),
                CaptureRegion.CAMERA.bounds(), CapturePreset.HIGH_QUALITY);
    }

    @Test
    void addLayoutActivatesIt(@TempDir Path tmp) throws Exception {
        ProfileService service = new ProfileService(tmp);
        Profile base = createBase(service);
        RelativeRectangle chattingCam = new RelativeRectangle(0.05, 0.2, 0.3, 0.5);
        Profile updated = service.addLayout(base.id(), "Just Chatting", chattingCam);

        assertThat(updated.layouts()).containsKeys("default", "just-chatting");
        assertThat(updated.activeLayoutId()).isEqualTo("just-chatting");
        assertThat(updated.cameraRegion().bounds()).isEqualTo(chattingCam);

        Profile reloaded = service.get(base.id()).orElseThrow();
        assertThat(reloaded.activeLayout().name()).isEqualTo("Just Chatting");
    }

    @Test
    void slugCollisionGetsSuffix(@TempDir Path tmp) throws Exception {
        ProfileService service = new ProfileService(tmp);
        Profile base = createBase(service);
        service.addLayout(base.id(), "Gaming", CaptureRegion.CAMERA.bounds());
        Profile twice = service.addLayout(base.id(), "Gaming", CaptureRegion.CAMERA.bounds());
        assertThat(twice.layouts()).containsKeys("gaming", "gaming-2");
    }

    @Test
    void switchActiveLayout(@TempDir Path tmp) throws Exception {
        ProfileService service = new ProfileService(tmp);
        Profile base = createBase(service);
        Profile withTwo = service.addLayout(base.id(), "Gaming", CaptureRegion.CAMERA.bounds());
        Profile switched = service.save(withTwo.withActiveLayout("default"));
        assertThat(switched.activeLayoutId()).isEqualTo("default");
        assertThatThrownBy(() -> withTwo.withActiveLayout("nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteActiveFallsBackToFirst(@TempDir Path tmp) throws Exception {
        ProfileService service = new ProfileService(tmp);
        Profile base = createBase(service);
        Profile withTwo = service.addLayout(base.id(), "Gaming", CaptureRegion.CAMERA.bounds());
        assertThat(withTwo.activeLayoutId()).isEqualTo("gaming");
        Profile after = service.deleteLayout(base.id(), "gaming");
        assertThat(after.layouts()).containsOnlyKeys("default");
        assertThat(after.activeLayoutId()).isEqualTo("default");
    }

    @Test
    void cannotDeleteLastLayout(@TempDir Path tmp) throws Exception {
        ProfileService service = new ProfileService(tmp);
        Profile base = createBase(service);
        assertThatThrownBy(() -> service.deleteLayout(base.id(), "default"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void activeLayoutSurvivesJsonRoundTrip(@TempDir Path tmp) throws Exception {
        ProfileService service = new ProfileService(tmp);
        Profile base = createBase(service);
        Profile withTwo = service.addLayout(base.id(), "Gaming", CaptureRegion.CAMERA.bounds());
        service.save(withTwo.withActiveLayout("gaming"));

        Profile reloaded = service.get(base.id()).orElseThrow();
        assertThat(reloaded.activeLayoutId()).isEqualTo("gaming");
        assertThat(reloaded.activeLayout().name()).isEqualTo("Gaming");

        Profile listed = service.list().stream()
                .filter(p -> p.id().equals(base.id()))
                .findFirst()
                .orElseThrow();
        assertThat(listed.activeLayoutId()).isEqualTo("gaming");
    }
}
