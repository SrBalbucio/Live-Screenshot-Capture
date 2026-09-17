package balbucio.livescreenshotcapture.ui;

import balbucio.livescreenshotcapture.config.Settings;
import balbucio.livescreenshotcapture.config.SettingsService;
import balbucio.livescreenshotcapture.hotkey.HotkeyAction;
import balbucio.livescreenshotcapture.hotkey.HotkeyCombo;
import balbucio.livescreenshotcapture.preset.CapturePreset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class SettingsWindow {
    public interface Listener {
        void onGeneralSaved(Settings settings);

        void onHotkeysSaved(Map<HotkeyAction, Set<Integer>> bindings);

        void onPresetsChanged();
    }

    private SettingsWindow() {
    }

    public static void show(Window owner, SettingsService service, Listener listener,
            Consumer<String> notifier) {
        Settings current = service.load();
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Settings");

        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
                generalTab(service, current, listener, notifier, stage),
                hotkeysTab(service, current, listener, notifier),
                presetsTab(service, listener, notifier));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Scene scene = new Scene(tabs, 560, 420);
        stage.setScene(scene);
        stage.show();
    }

    private static Tab generalTab(SettingsService service, Settings current, Listener listener,
            Consumer<String> notifier, Stage stage) {
        TextField outputField = new TextField(current.effectiveOutputDir());
        outputField.setPrefWidth(320);
        Button browse = new Button("Browse…");
        browse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Captures folder");
            try {
                Path initial = Paths.get(outputField.getText());
                if (java.nio.file.Files.isDirectory(initial)) {
                    chooser.setInitialDirectory(initial.toFile());
                }
            } catch (Exception ignored) {
            }
            java.io.File chosen = chooser.showDialog(stage);
            if (chosen != null) {
                outputField.setText(chosen.getAbsolutePath());
            }
        });

        CheckBox startMinimized = new CheckBox("Start minimized to tray");
        startMinimized.setSelected(current.startMinimized());
        CheckBox closeToTray = new CheckBox("Close button minimizes to tray (instead of exit)");
        closeToTray.setSelected(current.closeToTray());
        CheckBox balloon = new CheckBox("System tray balloon on capture");
        balloon.setSelected(current.trayBalloon());
        CheckBox sound = new CheckBox("Beep on capture");
        sound.setSelected(current.sound());
        CheckBox keepOriginal = new CheckBox("Also save original (non-upscaled) camera image");
        keepOriginal.setSelected(current.keepOriginal());
        CheckBox launchOnStartup = new CheckBox("Launch on Windows startup");
        launchOnStartup.setSelected(current.launchOnStartup());
        ComboBox<Integer> upscaleBox = new ComboBox<>();
        upscaleBox.getItems().addAll(1, 2, 3);
        upscaleBox.setValue(current.effectiveCameraUpscale());
        upscaleBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer v) {
                return v == null ? "" : v + "x";
            }

            @Override
            public Integer fromString(String s) {
                return 2;
            }
        });
        Label startupInfo = new Label();
        String cmd = service.currentLaunchCommand();
        startupInfo.setText(cmd.isEmpty()
                ? "Startup registration needs the packaged app (running from IDE)."
                : "Registers: " + cmd);
        startupInfo.setWrapText(true);

        Button save = new Button("Save");
        save.setOnAction(e -> {
            Settings updated = new Settings(outputField.getText().trim(), current.hotkeys(),
                    balloon.isSelected(), sound.isSelected(), startMinimized.isSelected(),
                    closeToTray.isSelected(), launchOnStartup.isSelected(),
                    current.customPresets(), upscaleBox.getValue(),
                    keepOriginal.isSelected());
            try {
                service.save(updated);
                try {
                    service.applyLaunchOnStartup(updated.launchOnStartup(),
                            service.currentLaunchCommand());
                } catch (Exception ex) {
                    notifier.accept("Startup setting failed: " + ex.getMessage());
                    return;
                }
                listener.onGeneralSaved(updated);
                notifier.accept("Settings saved.");
            } catch (Exception ex) {
                notifier.accept("Failed to save settings: " + ex.getMessage());
            }
        });

        VBox root = new VBox(10,
                new Label("Captures folder:"),
                new HBox(8, outputField, browse),
                new HBox(8, new Label("Camera upscale:"), upscaleBox,
                        new Label("(bicubic + sharpen, files get @Nx suffix)")),
                startMinimized, closeToTray, balloon, sound, keepOriginal, launchOnStartup,
                startupInfo, save);
        root.setPadding(new Insets(14));
        Tab tab = new Tab("General");
        tab.setContent(root);
        return tab;
    }

    private static Tab hotkeysTab(SettingsService service, Settings current, Listener listener,
            Consumer<String> notifier) {
        Map<HotkeyAction, Set<Integer>> pending =
                new EnumMap<>(service.resolveBindings(current));
        Map<HotkeyAction, Label> labels = new EnumMap<>(HotkeyAction.class);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(14));
        int row = 0;
        for (HotkeyAction action : HotkeyAction.values()) {
            Label name = new Label(actionName(action));
            Label combo = new Label(HotkeyCombo.format(pending.get(action)));
            combo.setMinWidth(180);
            labels.put(action, combo);
            Button record = new Button("Record…");
            record.setOnAction(e -> recordCombo(grid.getScene().getWindow(), action)
                    .ifPresent(codes -> {
                        pending.put(action, codes);
                        combo.setText(HotkeyCombo.format(codes));
                    }));
            grid.add(name, 0, row);
            grid.add(combo, 1, row);
            grid.add(record, 2, row);
            row++;
        }
        Label note = new Label("Ctrl+1..9 always switch layouts and cannot be changed here.");
        note.setWrapText(true);
        Button reset = new Button("Reset defaults");
        reset.setOnAction(e -> {
            Map<HotkeyAction, Set<Integer>> defaults =
                    balbucio.livescreenshotcapture.hotkey.HotkeyService.defaultBindings();
            pending.clear();
            pending.putAll(defaults);
            labels.forEach((a, l) -> l.setText(HotkeyCombo.format(pending.get(a))));
        });
        Button save = new Button("Save hotkeys");
        save.setOnAction(e -> {
            Map<String, String> stored = new LinkedHashMap<>();
            pending.forEach((a, codes) -> stored.put(a.name(), HotkeyCombo.format(codes)));
            Settings updated = new Settings(current.outputDir(), stored, current.trayBalloon(),
                    current.sound(), current.startMinimized(), current.closeToTray(),
                    current.launchOnStartup(), current.customPresets(),
                    current.cameraUpscale(), current.keepOriginal());
            try {
                service.save(updated);
                listener.onHotkeysSaved(new EnumMap<>(pending));
                notifier.accept("Hotkeys saved.");
            } catch (Exception ex) {
                notifier.accept("Failed to save hotkeys: " + ex.getMessage());
            }
        });

        VBox root = new VBox(10, grid, note, new HBox(8, reset, save));
        Tab tab = new Tab("Hotkeys");
        tab.setContent(root);
        return tab;
    }

    private static String actionName(HotkeyAction action) {
        return switch (action) {
            case CAPTURE_CAMERA -> "Capture camera";
            case CAPTURE_STREAM -> "Capture full stream";
            case CAPTURE_BURST -> "Camera burst";
        };
    }

    private static Optional<Set<Integer>> recordCombo(Window owner, HotkeyAction action) {
        Stage dialog = new Stage();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Record hotkey — " + actionName(action));
        Label prompt = new Label("Press the desired keys… (Esc cancels)");
        prompt.setPadding(new Insets(20));
        Scene scene = new Scene(new VBox(prompt), 320, 120);
        Set<Integer>[] result = new Set[1];
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                dialog.close();
                return;
            }
            int code = FxKeyMapper.toNative(e.getCode());
            if (code < 0 || HotkeyCombo.isModifier(code)) {
                return;
            }
            Set<Integer> codes = new java.util.LinkedHashSet<>();
            if (e.isControlDown()) {
                codes.add(FxKeyMapper.toNative(KeyCode.CONTROL));
            }
            if (e.isShiftDown()) {
                codes.add(FxKeyMapper.toNative(KeyCode.SHIFT));
            }
            if (e.isAltDown()) {
                codes.add(FxKeyMapper.toNative(KeyCode.ALT));
            }
            if (e.isMetaDown()) {
                codes.add(FxKeyMapper.toNative(KeyCode.META));
            }
            codes.add(code);
            result[0] = codes;
            dialog.close();
            e.consume();
        });
        dialog.setScene(scene);
        dialog.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    private static Tab presetsTab(SettingsService service, Listener listener,
            Consumer<String> notifier) {
        ListView<CapturePreset> list = new ListView<>();
        Runnable reload = () -> list.getItems()
                .setAll(new ArrayList<>(service.load().customPresets()));
        reload.run();
        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(CapturePreset p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? "" : p.name() + " — " + p.format() + "/"
                        + p.bufferFps() + "fps/" + p.bufferSeconds() + "s");
            }
        });

        Button add = new Button("New preset…");
        add.setOnAction(e -> newPresetDialog(list.getScene().getWindow()).ifPresent(preset -> {
            try {
                Settings current = service.load();
                List<CapturePreset> customs = new ArrayList<>(current.customPresets());
                customs.add(preset);
                service.save(new Settings(current.outputDir(), current.hotkeys(),
                        current.trayBalloon(), current.sound(), current.startMinimized(),
                        current.closeToTray(), current.launchOnStartup(), customs,
                        current.cameraUpscale(), current.keepOriginal()));
                reload.run();
                listener.onPresetsChanged();
                notifier.accept("Preset created: " + preset.name());
            } catch (Exception ex) {
                notifier.accept("Failed to create preset: " + ex.getMessage());
            }
        }));
        Button delete = new Button("Delete");
        delete.setOnAction(e -> {
            CapturePreset selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            try {
                Settings current = service.load();
                List<CapturePreset> customs = new ArrayList<>(current.customPresets());
                customs.removeIf(p -> p.id().equals(selected.id()));
                service.save(new Settings(current.outputDir(), current.hotkeys(),
                        current.trayBalloon(), current.sound(), current.startMinimized(),
                        current.closeToTray(), current.launchOnStartup(), customs,
                        current.cameraUpscale(), current.keepOriginal()));
                reload.run();
                listener.onPresetsChanged();
            } catch (Exception ex) {
                notifier.accept("Failed to delete preset: " + ex.getMessage());
            }
        });

        Label builtins = new Label("Built-in: High Quality, Low Memory, Reaction Capture.");
        builtins.setWrapText(true);
        VBox root = new VBox(10, builtins, list, new HBox(8, add, delete));
        root.setPadding(new Insets(14));
        Tab tab = new Tab("Presets");
        tab.setContent(root);
        return tab;
    }

    private static Optional<CapturePreset> newPresetDialog(Window owner) {
        TextInputDialog nameDialog = new TextInputDialog("My Preset");
        nameDialog.setTitle("New preset");
        nameDialog.setHeaderText("Preset name");
        Optional<String> name = nameDialog.showAndWait().filter(s -> !s.isBlank());
        if (name.isEmpty()) {
            return Optional.empty();
        }
        Stage dialog = new Stage();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Preset: " + name.get());
        Spinner<Integer> fps = new Spinner<>(1, 30, 10);
        fps.setEditable(true);
        Spinner<Integer> seconds = new Spinner<>(1, 30, 5);
        seconds.setEditable(true);
        ComboBox<String> format = new ComboBox<>();
        format.getItems().addAll("png", "jpg");
        format.setValue("png");
        Button ok = new Button("Create");
        CapturePreset[] result = new CapturePreset[1];
        ok.setOnAction(e -> {
            float quality = format.getValue().equals("jpg") ? 0.85f : 0.92f;
            String id = name.get().trim().toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", "-") + "-" + System.currentTimeMillis() % 10000;
            result[0] = new CapturePreset(id, name.get().trim(), seconds.getValue(),
                    fps.getValue(), format.getValue(), quality);
            dialog.close();
        });
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(14));
        grid.add(new Label("Buffer FPS:"), 0, 0);
        grid.add(fps, 1, 0);
        grid.add(new Label("Buffer seconds:"), 0, 1);
        grid.add(seconds, 1, 1);
        grid.add(new Label("Format:"), 0, 2);
        grid.add(format, 1, 2);
        grid.add(ok, 1, 3);
        dialog.setScene(new Scene(grid));
        dialog.showAndWait();
        return Optional.ofNullable(result[0]);
    }
}
