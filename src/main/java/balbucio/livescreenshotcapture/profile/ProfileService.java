package balbucio.livescreenshotcapture.profile;

import balbucio.livescreenshotcapture.model.CaptureRegion;
import balbucio.livescreenshotcapture.model.RelativeRectangle;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import balbucio.livescreenshotcapture.preset.CapturePreset;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileService {
    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final Path configDir;
    private final Path profilesDir;
    private final Path activeFile;
    private final ObjectMapper mapper;

    public ProfileService(Path configDir) {
        this.configDir = configDir;
        this.profilesDir = configDir.resolve("profiles");
        this.activeFile = configDir.resolve("active-profile.txt");
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static Path defaultConfigDir() {
        String appdata = System.getenv("APPDATA");
        if (appdata != null && !appdata.isBlank()) {
            return Paths.get(appdata, "LiveScreenshotCapture");
        }
        return Paths.get(System.getProperty("user.home"), ".config", "live-screenshot-capture");
    }

    public List<Profile> list() throws IOException {
        ensureDirs();
        if (!Files.isDirectory(profilesDir)) {
            return List.of();
        }
        List<Profile> out = new ArrayList<>();
        try (var stream = Files.list(profilesDir)) {
            for (Path p : stream.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                try {
                    out.add(mapper.readValue(p.toFile(), Profile.class));
                } catch (Exception e) {
                    log.warn("Skipping unreadable profile {}: {}", p.getFileName(), e.getMessage());
                }
            }
        }
        out.sort(Comparator.comparing(Profile::name));
        return out;
    }

    public Optional<Profile> get(String id) throws IOException {
        Path file = profilesDir.resolve(id + ".json");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(mapper.readValue(file.toFile(), Profile.class));
    }

    public Profile save(Profile profile) throws IOException {
        ensureDirs();
        if (!profile.layouts().containsKey(profile.activeLayoutId())) {
            throw new IllegalArgumentException("activeLayout unknown: " + profile.activeLayoutId());
        }
        Path file = profilesDir.resolve(profile.id() + ".json");
        mapper.writeValue(file.toFile(), profile);
        log.info("Saved profile: {}", file);
        return profile;
    }

    public void delete(String id) throws IOException {
        Files.deleteIfExists(profilesDir.resolve(id + ".json"));
    }

    public Profile create(String name, ScreenRegion streamRegion, RelativeRectangle cameraBounds,
            CapturePreset preset) throws IOException {
        String id = slugify(name) + "-" + UUID.randomUUID().toString().substring(0, 8);
        Layout layout = Layout.withCamera("default", "Default",
                cameraBounds != null ? cameraBounds : CaptureRegion.CAMERA.bounds());
        Profile profile = new Profile(id, name, streamRegion,
                Map.of(layout.id(), layout), layout.id(),
                preset != null ? preset : CapturePreset.HIGH_QUALITY);
        return save(profile);
    }

    public Profile getOrCreateDefault(ScreenRegion fallbackStream) throws IOException {
        Optional<Profile> active = getActive();
        if (active.isPresent()) {
            return active.get();
        }
        List<Profile> all = list();
        if (!all.isEmpty()) {
            setActive(all.get(0).id());
            return all.get(0);
        }
        Profile created = create("Default", fallbackStream, CaptureRegion.CAMERA.bounds(),
                CapturePreset.HIGH_QUALITY);
        setActive(created.id());
        return created;
    }

    public Optional<Profile> getActive() throws IOException {
        if (Files.exists(activeFile)) {
            String id = Files.readString(activeFile, StandardCharsets.UTF_8).trim();
            if (!id.isEmpty()) {
                Optional<Profile> p = get(id);
                if (p.isPresent()) {
                    return p;
                }
            }
        }
        return Optional.empty();
    }

    public void setActive(String id) throws IOException {
        ensureDirs();
        Files.writeString(activeFile, id, StandardCharsets.UTF_8);
    }

    public Profile repositionStream(String profileId, ScreenRegion newStream) throws IOException {
        Profile current = get(profileId)
                .orElseThrow(() -> new IllegalArgumentException("unknown profile: " + profileId));
        Profile updated = current.withStreamRegion(newStream);
        return save(updated);
    }

    public Profile updateCameraRegion(String profileId, String layoutId, RelativeRectangle bounds)
            throws IOException {
        Profile current = get(profileId)
                .orElseThrow(() -> new IllegalArgumentException("unknown profile: " + profileId));
        Layout layout = current.layouts().get(layoutId);
        if (layout == null) {
            throw new IllegalArgumentException("unknown layout: " + layoutId);
        }
        Map<String, Layout> layouts = new java.util.LinkedHashMap<>(current.layouts());
        Map<String, CaptureRegion> regions = new java.util.LinkedHashMap<>(layout.regions());
        CaptureRegion prev = regions.getOrDefault("camera", CaptureRegion.CAMERA);
        regions.put("camera", new CaptureRegion("camera", prev.name(), bounds));
        layouts.put(layoutId, new Layout(layout.id(), layout.name(), regions));
        Profile updated = new Profile(current.id(), current.name(), current.streamRegion(),
                layouts, current.activeLayoutId(), current.preset());
        return save(updated);
    }

    public Profile addLayout(String profileId, String layoutName, RelativeRectangle cameraBounds)
            throws IOException {
        Profile current = get(profileId)
                .orElseThrow(() -> new IllegalArgumentException("unknown profile: " + profileId));
        String baseId = slugify(layoutName);
        String layoutId = baseId;
        int suffix = 2;
        while (current.layouts().containsKey(layoutId)) {
            layoutId = baseId + "-" + suffix++;
        }
        Map<String, Layout> layouts = new java.util.LinkedHashMap<>(current.layouts());
        layouts.put(layoutId, Layout.withCamera(layoutId, layoutName, cameraBounds));
        Profile updated = new Profile(current.id(), current.name(), current.streamRegion(),
                layouts, layoutId, current.preset());
        return save(updated);
    }

    public Profile deleteLayout(String profileId, String layoutId) throws IOException {
        Profile current = get(profileId)
                .orElseThrow(() -> new IllegalArgumentException("unknown profile: " + profileId));
        if (!current.layouts().containsKey(layoutId)) {
            throw new IllegalArgumentException("unknown layout: " + layoutId);
        }
        if (current.layouts().size() <= 1) {
            throw new IllegalStateException("cannot delete the last layout of a profile");
        }
        Map<String, Layout> layouts = new java.util.LinkedHashMap<>(current.layouts());
        layouts.remove(layoutId);
        String active = current.activeLayoutId().equals(layoutId)
                ? layouts.keySet().iterator().next()
                : current.activeLayoutId();
        Profile updated = new Profile(current.id(), current.name(), current.streamRegion(),
                layouts, active, current.preset());
        return save(updated);
    }

    private void ensureDirs() throws IOException {
        Files.createDirectories(profilesDir);
    }

    static String slugify(String name) {
        String normalized = Normalizer.normalize(name.trim().toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "profile" : slug;
    }
}
