package balbucio.livescreenshotcapture;

import balbucio.livescreenshotcapture.buffer.FrameBuffer;
import balbucio.livescreenshotcapture.capture.CaptureService;
import balbucio.livescreenshotcapture.capture.RobotCaptureBackend;
import balbucio.livescreenshotcapture.config.AppConfig;
import balbucio.livescreenshotcapture.config.Settings;
import balbucio.livescreenshotcapture.config.SettingsService;
import balbucio.livescreenshotcapture.hotkey.HotkeyAction;
import balbucio.livescreenshotcapture.hotkey.HotkeyService;
import balbucio.livescreenshotcapture.model.CaptureRegion;
import balbucio.livescreenshotcapture.model.RelativeRectangle;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import balbucio.livescreenshotcapture.notification.NotificationService;
import balbucio.livescreenshotcapture.preset.CapturePreset;
import balbucio.livescreenshotcapture.profile.Layout;
import balbucio.livescreenshotcapture.profile.Profile;
import balbucio.livescreenshotcapture.profile.ProfileService;
import balbucio.livescreenshotcapture.region.RegionService;
import balbucio.livescreenshotcapture.screenshot.BurstService;
import balbucio.livescreenshotcapture.screenshot.ScreenshotService;
import balbucio.livescreenshotcapture.storage.StorageService;
import balbucio.livescreenshotcapture.ui.PreviewWindow;
import balbucio.livescreenshotcapture.ui.RegionSelector;
import balbucio.livescreenshotcapture.ui.SettingsWindow;
import balbucio.livescreenshotcapture.ui.TrayManager;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends Application {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private CaptureService captureService;
    private HotkeyService hotkeyService;
    private ScreenshotService screenshotService;
    private BurstService burstService;
    private ScheduledExecutorService burstScheduler;
    private StorageService storageService;
    private NotificationService notifications;
    private ProfileService profileService;
    private SettingsService settingsService;
    private Settings settings;
    private TrayManager trayManager;
    private Stage primaryStage;
    private Profile activeProfile;
    private ExecutorService ioExecutor;
    private FrameBuffer buffer;
    private RobotCaptureBackend captureBackend;
    private Label statusLabel;
    private Label profileInfoLabel;
    private Label memLabel;
    private TextArea logArea;
    private ComboBox<Profile> profileBox;
    private ComboBox<CapturePreset> presetBox;
    private ComboBox<Long> lookbackBox;
    private ComboBox<Layout> layoutBox;
    private List<Layout> layoutOrder = new ArrayList<>();

    @Override
    public void start(Stage stage) throws Exception {
        AppConfig config = AppConfig.load();
        RegionService regionService = new RegionService();
        profileService = new ProfileService(ProfileService.defaultConfigDir());
        settingsService = new SettingsService(ProfileService.defaultConfigDir());
        settings = settingsService.load();
        activeProfile = profileService.getOrCreateDefault(config.streamRegion());

        buffer = new FrameBuffer(activeProfile.preset().retentionMillis());
        captureBackend = new RobotCaptureBackend();
        captureService = new CaptureService(captureBackend, buffer,
                activeProfile.streamRegion(), activeProfile.preset().bufferFps());
        storageService = new StorageService(
                settingsService.resolveOutputDir(settings), activeProfile.id(),
                activeProfile.preset().normalizedFormat(), activeProfile.preset().jpegQuality());
        ioExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "io-executor");
            t.setDaemon(true);
            return t;
        });
        screenshotService = new ScreenshotService(captureService, regionService, storageService, ioExecutor);
        burstScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "burst-scheduler");
            t.setDaemon(true);
            return t;
        });
        burstService = new BurstService(captureService, regionService, storageService,
                ioExecutor, burstScheduler);
        notifications = new NotificationService();
        hotkeyService = new HotkeyService();
        hotkeyService.setBindings(settingsService.resolveBindings(settings));

        statusLabel = new Label();
        profileInfoLabel = new Label();
        memLabel = new Label();
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(10);

        notifications.onToast(msg -> Platform.runLater(() -> {
            statusLabel.setText(msg);
            logArea.appendText(msg + "\n");
        }));

        hotkeyService.addListener(this::onHotkey);
        hotkeyService.addLayoutListener(i -> Platform.runLater(() -> switchToLayoutIndex(i)));
        try {
            hotkeyService.start();
        } catch (Exception e) {
            log.warn("Global hotkeys unavailable: {}", e.getMessage());
        }
        captureService.start();

        profileBox = new ComboBox<>();
        refreshProfiles();
        profileBox.setValue(activeProfile);
        profileBox.setOnAction(e -> {
            Profile selected = profileBox.getValue();
            if (selected != null && !selected.id().equals(activeProfile.id())) {
                switchProfile(selected);
            }
        });
        Button newProfileBtn = new Button("New Profile (select regions)");
        newProfileBtn.setOnAction(e -> createProfileWizard());

        layoutBox = new ComboBox<>();
        layoutBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Layout l) {
                if (l == null) {
                    return "";
                }
                int idx = layoutOrder.indexOf(l);
                return (idx >= 0 ? (idx + 1) + ". " : "") + l.name();
            }

            @Override
            public Layout fromString(String s) {
                return null;
            }
        });
        layoutBox.setOnAction(e -> {
            Layout selected = layoutBox.getValue();
            if (selected != null && !selected.id().equals(activeProfile.activeLayout().id())) {
                switchLayout(selected.id());
            }
        });
        Button newLayoutBtn = new Button("New Layout");
        newLayoutBtn.setOnAction(e -> createLayoutWizard());
        Button deleteLayoutBtn = new Button("Delete Layout");
        deleteLayoutBtn.setOnAction(e -> deleteActiveLayout());

        presetBox = new ComboBox<>();
        presetBox.getItems().setAll(allPresets());
        presetBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CapturePreset p) {
                return p == null ? "" : p.name() + " (" + p.format() + "/" + p.bufferFps()
                        + "fps/" + p.bufferSeconds() + "s)";
            }

            @Override
            public CapturePreset fromString(String s) {
                return null;
            }
        });
        presetBox.setValue(activeProfile.preset());
        presetBox.setOnAction(e -> {
            CapturePreset selected = presetBox.getValue();
            if (selected != null && !selected.id().equals(activeProfile.preset().id())) {
                applyPreset(selected);
            }
        });

        lookbackBox = new ComboBox<>();
        lookbackBox.getItems().addAll(0L, -500L, -1000L);
        lookbackBox.setValue(0L);
        lookbackBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Long v) {
                return v == null || v == 0 ? "Immediate" : v + "ms";
            }

            @Override
            public Long fromString(String s) {
                return 0L;
            }
        });

        Button cameraBtn = new Button("Capture Camera (Ctrl+Shift+F9)");
        cameraBtn.setOnAction(e -> captureCameraWithLookback());
        Button streamBtn = new Button("Capture Stream (Ctrl+Shift+F10)");
        streamBtn.setOnAction(e -> captureStreamWithLookback());
        Button burstBtn = new Button("Camera Burst (Ctrl+Shift+F11)");
        burstBtn.setOnAction(e -> doBurst());
        Button pauseBtn = new Button("Pause / Resume");
        pauseBtn.setOnAction(e -> togglePause());
        Button capturesBtn = new Button("Open Captures");
        capturesBtn.setOnAction(e -> openCaptures());
        Button settingsBtn = new Button("Settings");
        settingsBtn.setOnAction(e -> openSettings());

        HBox profileRow = new HBox(8, new Label("Profile:"), profileBox, newProfileBtn);
        HBox layoutRow = new HBox(8, new Label("Layout:"), layoutBox, newLayoutBtn,
                deleteLayoutBtn, new Label("(Ctrl+1..9)"));
        HBox presetRow = new HBox(8, new Label("Preset:"), presetBox,
                new Label("Lookback:"), lookbackBox, memLabel);
        HBox buttons = new HBox(8, cameraBtn, streamBtn, burstBtn, pauseBtn);
        Button repositionBtn = new Button("Reposition Stream");
        repositionBtn.setOnAction(e -> repositionStream());
        Button editCameraBtn = new Button("Edit Camera");
        editCameraBtn.setOnAction(e -> editCamera());
        Button previewBtn = new Button("Preview");
        previewBtn.setOnAction(e -> showPreview());
        HBox regionRow = new HBox(8, repositionBtn, editCameraBtn, previewBtn);
        VBox root = new VBox(10,
                new Label("Live Screenshot Capture — Fase 6 (Desktop UX)"),
                profileRow,
                layoutRow,
                presetRow,
                profileInfoLabel,
                statusLabel,
                new Label("Output: " + storageService.getBaseDir().toAbsolutePath()),
                buttons, new HBox(8, capturesBtn, settingsBtn), regionRow, logArea);
        root.setPadding(new Insets(12));
        refreshLayouts();
        applyFeedbackSettings();
        updateStatus("Capturing");

        primaryStage = stage;
        stage.setTitle("Live Screenshot Capture");
        stage.setScene(new Scene(root, 720, 560));
        stage.setOnCloseRequest(e -> {
            if (settings.closeToTray() && trayManager != null && trayManager.isInstalled()) {
                e.consume();
                stage.hide();
                notifications.notify("Minimized to tray — use the tray icon to exit.");
            }
        });

        installTray();
        if (settings.startMinimized() && trayManager != null && trayManager.isInstalled()) {
            log.info("Starting minimized to tray");
        } else {
            stage.show();
        }
    }

    private void refreshProfiles() {
        try {
            List<Profile> profiles = profileService.list();
            Platform.runLater(() -> {
                profileBox.getItems().setAll(profiles);
                profileBox.setValue(activeProfile);
            });
        } catch (Exception e) {
            log.warn("Could not list profiles", e);
        }
    }

    private void refreshLayouts() {
        layoutOrder = new ArrayList<>(activeProfile.layouts().values());
        Layout active = activeProfile.activeLayout();
        Platform.runLater(() -> {
            layoutBox.getItems().setAll(layoutOrder);
            layoutBox.setValue(active);
        });
    }

    private void switchLayout(String layoutId) {
        try {
            activeProfile = profileService.save(activeProfile.withActiveLayout(layoutId));
            refreshLayouts();
            updateStatus("Layout switched");
            notifications.notify("Layout: " + activeProfile.activeLayout().name());
        } catch (Exception e) {
            notifications.notify("Failed to switch layout: " + e.getMessage());
        }
    }

    private void switchToLayoutIndex(int index) {
        List<Layout> order = new ArrayList<>(activeProfile.layouts().values());
        if (index < 1 || index > order.size()) {
            return;
        }
        Layout target = order.get(index - 1);
        if (!target.id().equals(activeProfile.activeLayout().id())) {
            switchLayout(target.id());
        }
    }

    private void createLayoutWizard() {
        TextInputDialog dialog = new TextInputDialog("Just Chatting");
        dialog.setTitle("New Layout");
        dialog.setHeaderText("Create layout for " + activeProfile.name());
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(name -> {
            if (name.isBlank()) {
                return;
            }
            notifications.notify("Select the CAMERA area for layout '" + name.trim() + "'…");
            RegionSelector.selectCamera(window(), activeProfile.streamRegion())
                    .thenAccept(opt -> Platform.runLater(() -> {
                        if (opt.isEmpty()) {
                            notifications.notify("Layout creation cancelled.");
                            return;
                        }
                        try {
                            activeProfile = profileService.addLayout(
                                    activeProfile.id(), name.trim(), opt.get());
                            refreshProfiles();
                            refreshLayouts();
                            updateStatus("Layout created");
                            notifications.notify("Layout created: " + name.trim());
                        } catch (Exception e) {
                            notifications.notify("Failed to create layout: " + e.getMessage());
                        }
                    }));
        });
    }

    private void deleteActiveLayout() {
        Layout active = activeProfile.activeLayout();
        try {
            activeProfile = profileService.deleteLayout(activeProfile.id(), active.id());
            refreshProfiles();
            refreshLayouts();
            updateStatus("Layout deleted");
            notifications.notify("Layout deleted: " + active.name());
        } catch (Exception e) {
            notifications.notify("Cannot delete layout: " + e.getMessage());
        }
    }

    private List<CapturePreset> allPresets() {
        List<CapturePreset> all = new ArrayList<>();
        all.add(CapturePreset.HIGH_QUALITY);
        all.add(CapturePreset.LOW_MEMORY);
        all.add(CapturePreset.REACTION);
        all.addAll(settings.customPresets());
        return all;
    }

    private void togglePause() {
        if (captureService.isRunning()) {
            captureService.stop();
            statusLabel.setText("Paused — buffering OFF");
        } else {
            captureService.start();
            updateStatus("Capturing resumed");
        }
        refreshTray();
    }

    private void openCaptures() {
        try {
            java.nio.file.Files.createDirectories(storageService.getBaseDir());
            Desktop.getDesktop().open(storageService.getBaseDir().toFile());
        } catch (Exception e) {
            notifications.notify("Cannot open captures folder: " + e.getMessage());
        }
    }

    private void openSettings() {
        SettingsWindow.show(window(), settingsService, new SettingsWindow.Listener() {
            @Override
            public void onGeneralSaved(Settings updated) {
                settings = updated;
                storageService.setBaseDir(settingsService.resolveOutputDir(updated));
                applyFeedbackSettings();
            }

            @Override
            public void onHotkeysSaved(Map<HotkeyAction, Set<Integer>> bindings) {
                hotkeyService.setBindings(bindings);
            }

            @Override
            public void onPresetsChanged() {
                settings = settingsService.load();
                CapturePreset keep = activeProfile.preset();
                presetBox.getItems().setAll(allPresets());
                presetBox.getItems().stream()
                        .filter(p -> p.id().equals(keep.id()))
                        .findFirst()
                        .ifPresentOrElse(presetBox::setValue, () -> presetBox.setValue(keep));
            }
        }, notifications::notify);
    }

    private void applyFeedbackSettings() {
        notifications.setBalloonEnabled(settings.trayBalloon());
        notifications.setSoundEnabled(settings.sound());
    }

    private void showMainWindow() {
        if (primaryStage != null) {
            primaryStage.show();
            primaryStage.toFront();
        }
    }

    private void installTray() {
        if (!TrayManager.isSupported()) {
            log.info("System tray not available, running with window only");
            return;
        }
        Platform.setImplicitExit(false);
        trayManager = new TrayManager(new TrayManager.Actions() {
            @Override
            public void showWindow() {
                Platform.runLater(() -> showMainWindow());
            }

            @Override
            public void captureCamera() {
                onHotkey(HotkeyAction.CAPTURE_CAMERA);
            }

            @Override
            public void captureStream() {
                onHotkey(HotkeyAction.CAPTURE_STREAM);
            }

            @Override
            public void captureBurst() {
                onHotkey(HotkeyAction.CAPTURE_BURST);
            }

            @Override
            public void togglePause() {
                Platform.runLater(() -> Main.this.togglePause());
            }

            @Override
            public void openCaptures() {
                Platform.runLater(() -> openCaptures());
            }

            @Override
            public void openSettings() {
                Platform.runLater(() -> openSettings());
            }

            @Override
            public void selectProfile(String profileId) {
                Platform.runLater(() -> {
                    try {
                        profileService.get(profileId).ifPresent(p -> {
                            if (!p.id().equals(activeProfile.id())) {
                                switchProfile(p);
                            }
                        });
                    } catch (Exception e) {
                        notifications.notify("Failed to switch profile: " + e.getMessage());
                    }
                });
            }

            @Override
            public void selectLayout(String layoutId) {
                Platform.runLater(() -> {
                    if (!layoutId.equals(activeProfile.activeLayout().id())) {
                        switchLayout(layoutId);
                    }
                });
            }

            @Override
            public void exit() {
                Platform.runLater(() -> {
                    primaryStage = null;
                    shutdown();
                });
            }
        });
        trayManager.install();
        notifications.onBalloon(trayManager::displayInfo);
        refreshTray();
    }

    private String currentStatusLine() {
        return (captureService != null && captureService.isRunning() ? "Capturing: " : "Paused: ")
                + activeProfile.name() + " / " + activeProfile.activeLayout().name();
    }

    private void refreshTray() {
        if (trayManager == null || !trayManager.isInstalled()) {
            return;
        }
        try {
            trayManager.refresh(currentStatusLine(), profileService.list(),
                    activeProfile.id(), !captureService.isRunning());
        } catch (Exception e) {
            log.warn("Could not refresh tray menu", e);
        }
    }

    private Window window() {
        return profileBox.getScene().getWindow();
    }

    private void createProfileWizard() {
        TextInputDialog dialog = new TextInputDialog("Streamer A");
        dialog.setTitle("New Profile");
        dialog.setHeaderText("Create profile for a streamer");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(name -> {
            if (name.isBlank()) {
                return;
            }
            notifications.notify("Select the STREAM area on screen…");
            RegionSelector.selectStream(window()).thenAccept(optStream -> Platform.runLater(() -> {
                if (optStream.isEmpty()) {
                    notifications.notify("Profile creation cancelled (no stream selected).");
                    return;
                }
                ScreenRegion stream = optStream.get();
                notifications.notify("Now select the CAMERA area inside the stream…");
                RegionSelector.selectCamera(window(), stream).thenAccept(optCam -> Platform.runLater(() -> {
                    if (optCam.isEmpty()) {
                        notifications.notify("Profile creation cancelled (no camera selected).");
                        return;
                    }
                    try {
                        Profile created = profileService.create(name.trim(), stream, optCam.get(),
                                CapturePreset.HIGH_QUALITY);
                        refreshProfiles();
                        switchProfile(created);
                        notifications.notify("Profile created: " + created.name()
                                + " stream=" + stream + " camera=" + optCam.get());
                    } catch (Exception ex) {
                        notifications.notify("Failed to create profile: " + ex.getMessage());
                    }
                }));
            }));
        });
    }

    private void repositionStream() {
        notifications.notify("Select the new STREAM position…");
        RegionSelector.selectStream(window()).thenAccept(opt -> Platform.runLater(() -> {
            if (opt.isEmpty()) {
                notifications.notify("Reposition cancelled.");
                return;
            }
            try {
                activeProfile = profileService.repositionStream(activeProfile.id(), opt.get());
                captureService.setStreamRegion(opt.get());
                refreshProfiles();
                updateStatus("Stream repositioned");
                notifications.notify("Stream repositioned to " + opt.get()
                        + " — camera kept (relative).");
            } catch (Exception e) {
                notifications.notify("Failed to reposition: " + e.getMessage());
            }
        }));
    }

    private void editCamera() {
        notifications.notify("Select the new CAMERA area inside the blue stream bounds…");
        RegionSelector.selectCamera(window(), activeProfile.streamRegion())
                .thenAccept(opt -> Platform.runLater(() -> {
                    if (opt.isEmpty()) {
                        notifications.notify("Camera edit cancelled.");
                        return;
                    }
                    try {
                        RelativeRectangle bounds = opt.get();
                        activeProfile = profileService.updateCameraRegion(
                                activeProfile.id(), activeProfile.activeLayout().id(), bounds);
                        captureService.getBuffer().clear();
                        refreshProfiles();
                        updateStatus("Camera updated");
                        notifications.notify("Camera updated to " + bounds);
                    } catch (Exception e) {
                        notifications.notify("Failed to update camera: " + e.getMessage());
                    }
                }));
    }

    private void showPreview() {
        BufferedImage frame = captureService.latest()
                .map(f -> f.image())
                .orElseGet(() -> {
                    try {
                        return captureBackend.capture(activeProfile.streamRegion().toAwtRectangle());
                    } catch (Exception e) {
                        notifications.notify("Preview failed: " + e.getMessage());
                        return null;
                    }
                });
        if (frame == null) {
            notifications.notify("Preview unavailable — no buffered frame yet.");
            return;
        }
        PreviewWindow.show(window(), frame, activeProfile);
    }

    private void switchProfile(Profile profile) {
        try {
            activeProfile = profile;
            profileService.setActive(profile.id());
            captureService.setStreamRegion(profile.streamRegion());
            captureService.setBufferFps(profile.preset().bufferFps());
            buffer.setRetentionMillis(profile.preset().retentionMillis());
            storageService.setProfileId(profile.id());
            storageService.setFormat(profile.preset().normalizedFormat());
            presetBox.setValue(profile.preset());
            refreshLayouts();
            updateStatus("Switched to " + profile.name());
        } catch (Exception e) {
            notifications.notify("Failed to switch profile: " + e.getMessage());
        }
    }

    private void applyPreset(CapturePreset preset) {
        try {
            activeProfile = profileService.save(activeProfile.withPreset(preset));
            captureService.setBufferFps(preset.bufferFps());
            buffer.setRetentionMillis(preset.retentionMillis());
            storageService.setFormat(preset.normalizedFormat());
            updateStatus("Preset applied");
            notifications.notify("Preset applied: " + preset.name());
        } catch (Exception e) {
            notifications.notify("Failed to apply preset: " + e.getMessage());
        }
    }

    private void updateStatus(String prefix) {
        String info = prefix + ": " + activeProfile.name()
                + " | stream=" + activeProfile.streamRegion()
                + " | layout=" + activeProfile.activeLayout().name()
                + " | preset=" + activeProfile.preset().name()
                + " @" + activeProfile.preset().bufferFps() + "fps";
        statusLabel.setText(info);
        profileInfoLabel.setText("Camera region (relative): " + activeProfile.cameraRegion().bounds());
        updateMemEstimate();
        refreshTray();
    }

    private void updateMemEstimate() {
        ScreenRegion s = activeProfile.streamRegion();
        CapturePreset p = activeProfile.preset();
        long frames = (long) p.bufferFps() * p.bufferSeconds();
        long bytes = (long) s.width() * s.height() * 4 * frames;
        double mb = bytes / (1024.0 * 1024.0);
        memLabel.setText(String.format("Buffer ~%.0f MB (%d frames)", mb, frames));
        if (mb > 300) {
            log.warn("Buffer estimate high: {} MB — consider Low Memory preset", (int) mb);
        }
    }

    private void captureCameraWithLookback() {
        long lookback = lookbackBox.getValue();
        screenshotService.captureDelayed(activeProfile.cameraRegion(), lookback)
                .thenAccept(p -> p.ifPresent(path ->
                        notifications.notifySaved("Camera [" + activeProfile.name() + "]", path.toString())));
    }

    private void captureStreamWithLookback() {
        long lookback = lookbackBox.getValue();
        screenshotService.captureDelayed(CaptureRegion.FULL_STREAM, lookback)
                .thenAccept(p -> p.ifPresent(path ->
                        notifications.notifySaved("Stream [" + activeProfile.name() + "]", path.toString())));
    }

    private void doBurst() {
        burstService.burst(activeProfile.cameraRegion()).thenAccept(paths -> {
            if (paths.isEmpty()) {
                notifications.notify("Burst failed — no buffered frames.");
            } else {
                notifications.notify("\uD83D\uDCF8 " + paths.size() + " burst frames captured");
            }
        });
    }

    private void onHotkey(HotkeyAction action) {
        switch (action) {
            case CAPTURE_CAMERA -> screenshotService.capture(activeProfile.cameraRegion())
                    .thenAccept(p -> p.ifPresent(path ->
                            notifications.notifySaved("Camera [" + activeProfile.name() + "]", path.toString())));
            case CAPTURE_STREAM -> screenshotService.captureStream()
                    .thenAccept(p -> p.ifPresent(path ->
                            notifications.notifySaved("Stream [" + activeProfile.name() + "]", path.toString())));
            case CAPTURE_BURST -> doBurst();
        }
    }

    private void shutdown() {
        try {
            if (captureService != null) {
                captureService.stop();
            }
        } catch (Exception ignored) {
        }
        try {
            if (hotkeyService != null) {
                hotkeyService.stop();
            }
        } catch (Exception ignored) {
        }
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }
        if (burstScheduler != null) {
            burstScheduler.shutdownNow();
        }
        if (trayManager != null) {
            trayManager.remove();
        }
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
