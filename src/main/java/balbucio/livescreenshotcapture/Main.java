package balbucio.livescreenshotcapture;

import balbucio.livescreenshotcapture.buffer.FrameBuffer;
import balbucio.livescreenshotcapture.capture.CaptureService;
import balbucio.livescreenshotcapture.capture.RobotCaptureBackend;
import balbucio.livescreenshotcapture.config.AppConfig;
import balbucio.livescreenshotcapture.hotkey.HotkeyAction;
import balbucio.livescreenshotcapture.hotkey.HotkeyService;
import balbucio.livescreenshotcapture.notification.NotificationService;
import balbucio.livescreenshotcapture.region.RegionService;
import balbucio.livescreenshotcapture.screenshot.ScreenshotService;
import balbucio.livescreenshotcapture.storage.StorageService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends Application {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private CaptureService captureService;
    private HotkeyService hotkeyService;
    private ScreenshotService screenshotService;
    private NotificationService notifications;
    private ExecutorService ioExecutor;
    private Label statusLabel;
    private TextArea logArea;

    @Override
    public void start(Stage stage) throws Exception {
        AppConfig config = AppConfig.load();
        RegionService regionService = new RegionService();
        FrameBuffer buffer = new FrameBuffer(config.retentionMillis());
        RobotCaptureBackend backend = new RobotCaptureBackend();
        captureService = new CaptureService(backend, buffer, config.streamRegion(), config.bufferFps());
        StorageService storage = new StorageService(config.baseDir(), "default", config.format(), config.jpegQuality());
        ioExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "io-executor");
            t.setDaemon(true);
            return t;
        });
        screenshotService = new ScreenshotService(captureService, regionService, storage, ioExecutor);
        notifications = new NotificationService();
        hotkeyService = new HotkeyService();

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

        statusLabel = new Label("Capturing: " + config.streamRegion() + " @ " + config.bufferFps() + "fps");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(10);

        Button cameraBtn = new Button("Capture Camera (Ctrl+Shift+F9)");
        cameraBtn.setOnAction(e -> onHotkey(HotkeyAction.CAPTURE_CAMERA));
        Button streamBtn = new Button("Capture Stream (Ctrl+Shift+F10)");
        streamBtn.setOnAction(e -> onHotkey(HotkeyAction.CAPTURE_STREAM));
        Button pauseBtn = new Button("Pause / Resume");
        pauseBtn.setOnAction(e -> {
            if (captureService.isRunning()) {
                captureService.stop();
                statusLabel.setText("Paused");
            } else {
                captureService.start();
                statusLabel.setText("Capturing resumed");
            }
        });

        HBox buttons = new HBox(8, cameraBtn, streamBtn, pauseBtn);
        VBox root = new VBox(10,
                new Label("Live Screenshot Capture — Fase 1 (Capture Core)"),
                statusLabel,
                new Label("Buffer: " + config.bufferSeconds() + "s | Format: " + config.format()
                        + " | Output: " + config.baseDir().toAbsolutePath()),
                buttons, logArea);
        root.setPadding(new Insets(12));

        stage.setTitle("Live Screenshot Capture");
        stage.setScene(new Scene(root, 640, 420));
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();
    }

    private void onHotkey(HotkeyAction action) {
        switch (action) {
            case CAPTURE_CAMERA -> screenshotService.captureCamera()
                    .thenAccept(p -> p.ifPresent(path ->
                            notifications.notifySaved("Camera", path.toString())));
            case CAPTURE_STREAM -> screenshotService.captureStream()
                    .thenAccept(p -> p.ifPresent(path ->
                            notifications.notifySaved("Stream", path.toString())));
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
