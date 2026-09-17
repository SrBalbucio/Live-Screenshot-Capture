package balbucio.livescreenshotcapture.config;

import balbucio.livescreenshotcapture.hotkey.HotkeyAction;
import balbucio.livescreenshotcapture.hotkey.HotkeyCombo;
import balbucio.livescreenshotcapture.hotkey.HotkeyService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SettingsService {
    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
    private static final String RUN_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String RUN_VALUE = "LiveScreenshotCapture";

    private final Path file;
    private final ObjectMapper mapper;

    public SettingsService(Path configDir) {
        this.file = configDir.resolve("settings.json");
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Settings load() {
        if (!Files.exists(file)) {
            return Settings.defaults();
        }
        try {
            Settings loaded = mapper.readValue(file.toFile(), Settings.class);
            return loaded != null ? withDefaults(loaded) : Settings.defaults();
        } catch (Exception e) {
            log.warn("Could not read settings, using defaults: {}", e.getMessage());
            return Settings.defaults();
        }
    }

    public void save(Settings settings) throws IOException {
        Files.createDirectories(file.getParent());
        mapper.writeValue(file.toFile(), settings);
    }

    public Path resolveOutputDir(Settings settings) {
        Path dir = Paths.get(settings.effectiveOutputDir());
        return dir.isAbsolute() ? dir : Paths.get("").toAbsolutePath().resolve(dir);
    }

    public Map<HotkeyAction, Set<Integer>> resolveBindings(Settings settings) {
        Map<HotkeyAction, Set<Integer>> result = new EnumMap<>(HotkeyAction.class);
        Map<HotkeyAction, Set<Integer>> defaults = HotkeyService.defaultBindings();
        for (HotkeyAction action : HotkeyAction.values()) {
            String combo = settings.hotkeys().get(action.name());
            if (combo == null || combo.isBlank()) {
                result.put(action, defaults.get(action));
                continue;
            }
            try {
                result.put(action, HotkeyCombo.parse(combo));
            } catch (Exception e) {
                log.warn("Invalid hotkey for {}, using default: {}", action, e.getMessage());
                result.put(action, defaults.get(action));
            }
        }
        return result;
    }

    public void applyLaunchOnStartup(boolean enable, String command) throws IOException {
        if (!isWindows()) {
            throw new IOException("Launch on startup is only supported on Windows");
        }
        if (enable && (command == null || command.isBlank())) {
            throw new IOException("Launch on startup needs the packaged app"
                    + " (unavailable when running from the IDE)");
        }
        try {
            if (enable) {
                exec("reg", "add", RUN_KEY, "/v", RUN_VALUE, "/t", "REG_SZ",
                        "/d", command, "/f");
            } else {
                exec("reg", "delete", RUN_KEY, "/v", RUN_VALUE, "/f");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while updating startup entry", e);
        }
    }

    public String currentLaunchCommand() {
        String configured = System.getProperty("app.launch.command", "").trim();
        if (!configured.isEmpty()) {
            return configured;
        }
        String cmd = ProcessHandle.current().info().command().orElse("");
        if (cmd.toLowerCase(Locale.ROOT).endsWith(".exe")
                && cmd.toLowerCase(Locale.ROOT).contains("screenshot")) {
            return "\"" + cmd + "\"";
        }
        return "";
    }

    private Settings withDefaults(Settings loaded) {
        Settings defaults = Settings.defaults();
        Map<String, String> hotkeys = new java.util.LinkedHashMap<>(defaults.hotkeys());
        hotkeys.putAll(loaded.hotkeys());
        String outputDir = loaded.outputDir() == null ? defaults.outputDir() : loaded.outputDir();
        return new Settings(outputDir, hotkeys, loaded.trayBalloon(), loaded.sound(),
                loaded.startMinimized(), loaded.closeToTray(), loaded.launchOnStartup(),
                loaded.customPresets());
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private void exec(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IOException("Command failed: " + String.join(" ", cmd) + " -> " + out.trim());
        }
    }
}
