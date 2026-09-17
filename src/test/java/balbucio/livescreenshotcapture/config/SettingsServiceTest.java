package balbucio.livescreenshotcapture.config;

import balbucio.livescreenshotcapture.hotkey.HotkeyAction;
import balbucio.livescreenshotcapture.preset.CapturePreset;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsServiceTest {
    @Test
    void defaultsThenRoundTrip(@TempDir Path tmp) throws Exception {
        SettingsService service = new SettingsService(tmp);
        Settings defaults = service.load();
        assertThat(defaults.hotkeys()).containsKeys("CAPTURE_CAMERA", "CAPTURE_STREAM",
                "CAPTURE_BURST");
        assertThat(defaults.closeToTray()).isTrue();

        CapturePreset custom = new CapturePreset("custom-1", "Custom", 4, 8, "png", 0.92f);
        Settings updated = new Settings("D:/caps",
                Map.of("CAPTURE_CAMERA", "Ctrl+Alt+C",
                        "CAPTURE_STREAM", "Ctrl+Alt+S",
                        "CAPTURE_BURST", "Ctrl+Alt+B"),
                false, true, true, false, false, List.of(custom), 3);
        service.save(updated);

        Settings reloaded = service.load();
        assertThat(reloaded.outputDir()).isEqualTo("D:/caps");
        assertThat(reloaded.customPresets()).hasSize(1);
        assertThat(reloaded.effectiveCameraUpscale()).isEqualTo(3);
        assertThat(service.resolveOutputDir(reloaded)).isEqualTo(Path.of("D:/caps"));

        Map<HotkeyAction, Set<Integer>> bindings = service.resolveBindings(reloaded);
        assertThat(bindings.get(HotkeyAction.CAPTURE_CAMERA))
                .isEqualTo(balbucio.livescreenshotcapture.hotkey.HotkeyCombo
                        .parse("CTRL+ALT+C"));
    }

    @Test
    void invalidComboFallsBackToDefault(@TempDir Path tmp) throws Exception {
        SettingsService service = new SettingsService(tmp);
        Settings bad = new Settings("captures", Map.of("CAPTURE_CAMERA", "CTRL+NOPE"),
                true, false, false, true, false, List.of(), 0);
        service.save(bad);
        Map<HotkeyAction, Set<Integer>> bindings =
                service.resolveBindings(service.load());
        assertThat(bindings.get(HotkeyAction.CAPTURE_CAMERA)).isEqualTo(
                balbucio.livescreenshotcapture.hotkey.HotkeyService.defaultBindings()
                        .get(HotkeyAction.CAPTURE_CAMERA));
    }

    @Test
    void upscaleFallsBackToDefault(@TempDir Path tmp) {
        SettingsService service = new SettingsService(tmp);
        assertThat(service.load().effectiveCameraUpscale()).isEqualTo(2);
        Settings legacy = new Settings("captures", Map.of(), true, false, false, true, false,
                List.of(), 0);
        assertThat(legacy.effectiveCameraUpscale()).isEqualTo(2);
    }

    @Test
    void startupGuardRejectsBlankCommand(@TempDir Path tmp) {
        SettingsService service = new SettingsService(tmp);
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            assertThatThrownBy(() -> service.applyLaunchOnStartup(true, "whatever"))
                    .isInstanceOf(java.io.IOException.class);
            return;
        }
        assertThatThrownBy(() -> service.applyLaunchOnStartup(true, "  "))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("packaged");
    }
}
