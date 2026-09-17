package balbucio.livescreenshotcapture.profile;

import balbucio.livescreenshotcapture.model.CaptureRegion;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import balbucio.livescreenshotcapture.preset.CapturePreset;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Profile(String id, String name, ScreenRegion streamRegion,
        Map<String, Layout> layouts, String activeLayoutId, CapturePreset preset) {
    public Profile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("profile id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("profile name must not be blank");
        }
        if (streamRegion == null) {
            throw new IllegalArgumentException("streamRegion must not be null");
        }
        layouts = layouts == null || layouts.isEmpty()
                ? Map.of("default", Layout.withCamera("default", "Default",
                        CaptureRegion.CAMERA.bounds()))
                : Collections.unmodifiableMap(new LinkedHashMap<>(layouts));
        if (activeLayoutId == null || !layouts.containsKey(activeLayoutId)) {
            activeLayoutId = layouts.keySet().iterator().next();
        }
        if (preset == null) {
            preset = CapturePreset.HIGH_QUALITY;
        }
    }

    public Layout activeLayout() {
        Layout l = layouts.get(activeLayoutId);
        return l != null ? l : layouts.values().iterator().next();
    }

    public CaptureRegion cameraRegion() {
        return activeLayout().cameraRegion();
    }

    public Profile withStreamRegion(ScreenRegion region) {
        return new Profile(id, name, region, layouts, activeLayoutId, preset);
    }

    public Profile withActiveLayout(String layoutId) {
        if (!layouts.containsKey(layoutId)) {
            throw new IllegalArgumentException("unknown layout: " + layoutId);
        }
        return new Profile(id, name, streamRegion, layouts, layoutId, preset);
    }

    public Profile withPreset(CapturePreset next) {
        return new Profile(id, name, streamRegion, layouts, activeLayoutId, next);
    }

    @Override
    public String toString() {
        return name;
    }
}
