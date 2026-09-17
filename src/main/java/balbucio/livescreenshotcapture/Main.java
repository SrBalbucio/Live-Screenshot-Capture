package balbucio.livescreenshotcapture;

import balbucio.livescreenshotcapture.buffer.FrameBuffer;
import balbucio.livescreenshotcapture.capture.CaptureService;
import balbucio.livescreenshotcapture.capture.RobotCaptureBackend;
import balbucio.livescreenshotcapture.config.AppConfig;
import balbucio.livescreenshotcapture.hotkey.HotkeyAction;
import balbucio.livescreenshotcapture.hotkey.HotkeyService;
import balbucio.livescreenshotcapture.model.CaptureRegion;
import balbucio.livescreenshotcapture.model.RelativeRectangle;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import balbucio.livescreenshotcapture.notification.NotificationService;
import balbucio.livescreenshotcapture.preset.CapturePreset;
import balbucio.livescreenshotcapture.profile.Profile;
import balbucio.livescreenshotcapture.profile.ProfileService;
import balbucio.livescreenshotcapture.region.RegionService;
import balbucio.livescreenshotcapture.screenshot.ScreenshotService;
import balbucio.livescreenshotcapture.storage.StorageService;
import balbucio.livescreenshotcapture.ui.PreviewWindow;
import balbucio.livescreenshotcapture.ui.RegionSelector;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends Application {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private CaptureService captureService;
    private HotkeyService hotkeyService;
    private ScreenshotService screenshotService;
    private StorageService storageService;
    private NotificationService notifications;
    private ProfileService profileService;
    private Profile activeProfile;
    private ExecutorService ioExecutor;
    private FrameBuffer buffer;
    private RobotCaptureBackend captureBackend;
    private Label statusLabel;
    private Label profileInfoLabel;
    private TextArea logArea;
    private ComboBox<Profile> profileBox;

    @Override
    public void start(Stage stage) throws Exception {
        AppConfig config = AppConfig.load();
        RegionService regionService = new RegionService();
        profileService = new ProfileService(ProfileService.defaultConfigDir());
        activeProfile = profileService.getOrCreateDefault(config.streamRegion());

        buffer = new FrameBuffer(activeProfile.preset().retentionMillis());
        captureBackend = new RobotCaptureBackend();
        captureService = new CaptureService(captureBackend, buffer,
                activeProfile.streamRegion(), activeProfile.preset().bufferFps());
        storageService = new StorageService(config.baseDir(), activeProfile.id(),
                activeProfile.preset().normalizedFormat(), activeProfile.preset().jpegQuality());
        ioExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "io-executor");
            t.setDaemon(true);
            return t;
        });
        screenshotService = new ScreenshotService(captureService, regionService, storageService, ioExecutor);
        notifications = new NotificationService();
        hotkeyService = new HotkeyService();

        statusLabel = new Label();
        profileInfoLabel = new Label();
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(10);

        notifications.onToast(msg -> Platform.runLater(() -> {
            statusLabel.setText(msg);
            logArea.appendText(msg + "\n");
        }));

        hotkeyService.addListener(this::onHotkey);
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

        Button cameraBtn = new Button("Capture Camera (Ctrl+Shift+F9)");
        cameraBtn.setOnAction(e -> onHotkey(HotkeyAction.CAPTURE_CAMERA));
        Button streamBtn = new Button("Capture Stream (Ctrl+Shift+F10)");
        streamBtn.setOnAction(e -> onHotkey(HotkeyAction.CAPTURE_STREAM));
        Button pauseBtn = new Button("Pause / Resume");
        pauseBtn.setOnAction(e -> {
            if (captureService.isRunning()) {
                captureService.stop();
                statusLabel.setText("Paused — buffering OFF");
            } else {
                captureService.start();
                updateStatus("Capturing resumed");
            }
        });

        HBox profileRow = new HBox(8, new Label("Profile:"), profileBox, newProfileBtn);
        HBox buttons = new HBox(8, cameraBtn, streamBtn, pauseBtn);
        Button repositionBtn = new Button("Reposition Stream");
        repositionBtn.setOnAction(e -> repositionStream());
        Button editCameraBtn = new Button("Edit Camera");
        editCameraBtn.setOnAction(e -> editCamera());
        Button previewBtn = new Button("Preview");
        previewBtn.setOnAction(e -> showPreview());
        HBox regionRow = new HBox(8, repositionBtn, editCameraBtn, previewBtn);
        VBox root = new VBox(10,
                new Label("Live Screenshot Capture — Fase 3 (Region Selection)"),
                profileRow,
                profileInfoLabel,
                statusLabel,
                new Label("Output: " + config.baseDir().toAbsolutePath()),
                buttons, regionRow, logArea);
        root.setPadding(new Insets(12));
        updateStatus("Capturing");

        stage.setTitle("Live Screenshot Capture");
        stage.setScene(new Scene(root, 680, 460));
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();
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
            updateStatus("Switched to " + profile.name());
        } catch (Exception e) {
            notifications.notify("Failed to switch profile: " + e.getMessage());
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
    }

    private void onHotkey(HotkeyAction action) {
        switch (action) {
            case CAPTURE_CAMERA -> screenshotService.capture(activeProfile.cameraRegion())
                    .thenAccept(p -> p.ifPresent(path ->
                            notifications.notifySaved("Camera [" + activeProfile.name() + "]", path.toString())));
            case CAPTURE_STREAM -> screenshotService.captureStream()
                    .thenAccept(p -> p.ifPresent(path ->
                            notifications.notifySaved("Stream [" + activeProfile.name() + "]", path.toString())));
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
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
