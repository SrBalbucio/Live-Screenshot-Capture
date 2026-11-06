package balbucio.livescreenshotcapture.config;

import balbucio.livescreenshotcapture.hotkey.HotkeyAction;
import balbucio.livescreenshotcapture.hotkey.HotkeyCombo;
import balbucio.livescreenshotcapture.hotkey.HotkeyService;
import balbucio.livescreenshotcapture.preset.CapturePreset;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Settings(String outputDir, Map<String, String> hotkeys, boolean trayBalloon,
        boolean sound, boolean startMinimized, boolean closeToTray, boolean launchOnStartup,
        List<CapturePreset> customPresets, int cameraUpscale, boolean keepOriginal,
        long storageQuotaMb, int autoCaptureIntervalSeconds, String autoCaptureRegion,
        Integer autoCaptureMaxMinutes) {

    public static final int DEFAULT_AUTO_CAPTURE_INTERVAL_SECONDS = 10;
    public static final String DEFAULT_AUTO_CAPTURE_REGION = "camera";
    public static final int DEFAULT_AUTO_CAPTURE_MAX_MINUTES = 60;
    public static final int AUTO_CAPTURE_UNLIMITED_MINUTES = 0;

    public Settings {
        hotkeys = hotkeys == null ? Map.of() : Map.copyOf(hotkeys);
        customPresets = customPresets == null ? List.of() : List.copyOf(customPresets);
        if (autoCaptureIntervalSeconds <= 0) {
            autoCaptureIntervalSeconds = DEFAULT_AUTO_CAPTURE_INTERVAL_SECONDS;
        }
        autoCaptureRegion = normalizeAutoCaptureRegion(autoCaptureRegion);
        // null means the setting was absent (legacy settings.json): keep the safe default.
        autoCaptureMaxMinutes = autoCaptureMaxMinutes == null
                ? DEFAULT_AUTO_CAPTURE_MAX_MINUTES
                : Math.max(AUTO_CAPTURE_UNLIMITED_MINUTES, autoCaptureMaxMinutes);
    }

    public static Settings defaults() {
        Map<String, String> defaults = new java.util.LinkedHashMap<>();
        for (Map.Entry<HotkeyAction, java.util.Set<Integer>> e
                : HotkeyService.defaultBindings().entrySet()) {
            defaults.put(e.getKey().name(), HotkeyCombo.format(e.getValue()));
        }
        return new Settings("captures", defaults, true, false, false, true, false, List.of(),
                2, false, 2048, DEFAULT_AUTO_CAPTURE_INTERVAL_SECONDS,
                DEFAULT_AUTO_CAPTURE_REGION, DEFAULT_AUTO_CAPTURE_MAX_MINUTES);
    }

    private static String normalizeAutoCaptureRegion(String region) {
        if (region == null) {
            return DEFAULT_AUTO_CAPTURE_REGION;
        }
        String normalized = region.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("camera") || normalized.equals("stream")
                ? normalized
                : DEFAULT_AUTO_CAPTURE_REGION;
    }

    public String effectiveOutputDir() {
        return outputDir == null || outputDir.isBlank() ? "captures" : outputDir;
    }

    public int effectiveCameraUpscale() {
        return cameraUpscale <= 0 ? 2 : cameraUpscale;
    }

    public long effectiveQuotaBytes() {
        return storageQuotaMb <= 0 ? Long.MAX_VALUE : storageQuotaMb * 1024 * 1024;
    }

    public boolean autoCaptureUnlimited() {
        return autoCaptureMaxMinutes <= AUTO_CAPTURE_UNLIMITED_MINUTES;
    }
}
