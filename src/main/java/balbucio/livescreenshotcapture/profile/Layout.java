package balbucio.livescreenshotcapture.profile;

import balbucio.livescreenshotcapture.model.CaptureRegion;
import balbucio.livescreenshotcapture.model.RelativeRectangle;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Layout(String id, String name, Map<String, CaptureRegion> regions) {
    public Layout {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("layout id must not be blank");
        }
        regions = regions == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(regions));
    }

    public static Layout withCamera(String id, String name, RelativeRectangle cameraBounds) {
        return new Layout(id, name, Map.of("camera", new CaptureRegion("camera", "Camera", cameraBounds)));
    }

    public CaptureRegion cameraRegion() {
        CaptureRegion cam = regions.get("camera");
        return cam != null ? cam : CaptureRegion.CAMERA;
    }
}
