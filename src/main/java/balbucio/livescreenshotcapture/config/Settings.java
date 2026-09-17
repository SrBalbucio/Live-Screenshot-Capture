package balbucio.livescreenshotcapture.config;

import balbucio.livescreenshotcapture.hotkey.HotkeyAction;
import balbucio.livescreenshotcapture.hotkey.HotkeyCombo;
import balbucio.livescreenshotcapture.hotkey.HotkeyService;
import balbucio.livescreenshotcapture.preset.CapturePreset;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Settings(String outputDir, Map<String, String> hotkeys, boolean trayBalloon,
        boolean sound, boolean startMinimized, boolean closeToTray, boolean launchOnStartup,
        List<CapturePreset> customPresets) {
    public Settings {
        hotkeys = hotkeys == null ? Map.of() : Map.copyOf(hotkeys);
        customPresets = customPresets == null ? List.of() : List.copyOf(customPresets);
    }

    public static Settings defaults() {
        Map<String, String> defaults = new java.util.LinkedHashMap<>();
        for (Map.Entry<HotkeyAction, java.util.Set<Integer>> e
                : HotkeyService.defaultBindings().entrySet()) {
            defaults.put(e.getKey().name(), HotkeyCombo.format(e.getValue()));
        }
        return new Settings("captures", defaults, true, false, false, true, false, List.of());
    }

    public String effectiveOutputDir() {
        return outputDir == null || outputDir.isBlank() ? "captures" : outputDir;
    }
}
