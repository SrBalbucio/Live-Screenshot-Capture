package balbucio.livescreenshotcapture.ui;

import balbucio.livescreenshotcapture.model.RelativeRectangle;
import balbucio.livescreenshotcapture.model.ScreenRegion;
import balbucio.livescreenshotcapture.region.RegionGeometry;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegionSelector {
    private static final Logger log = LoggerFactory.getLogger(RegionSelector.class);

    private RegionSelector() {
    }

    public static CompletableFuture<Optional<ScreenRegion>> selectStream(Window owner) {
        ensureFxThread();
        CompletableFuture<Optional<ScreenRegion>> future = new CompletableFuture<>();
        open(owner, "Select STREAM area: drag a rectangle, Enter/double-click confirms, Esc cancels",
                null, sel -> future.complete(Optional.of(new ScreenRegion(sel.x, sel.y, sel.width, sel.height))),
                () -> future.complete(Optional.empty()));
        return future;
    }

    public static CompletableFuture<Optional<RelativeRectangle>> selectCamera(Window owner, ScreenRegion stream) {
        ensureFxThread();
        CompletableFuture<Optional<RelativeRectangle>> future = new CompletableFuture<>();
        open(owner, "Select CAMERA area inside the blue stream bounds, Enter/double-click confirms, Esc cancels",
                stream,
                sel -> {
                    Rectangle clamped = RegionGeometry.clampToStream(sel, stream);
                    if (!RegionGeometry.isValid(clamped)) {
                        return;
                    }
                    future.complete(Optional.of(RegionGeometry.toRelative(stream, clamped)));
                },
                () -> future.complete(Optional.empty()));
        return future;
    }

    private interface OnSelect {
        void accept(Rectangle absoluteSelection);
    }

    private static void open(Window owner, String hint, ScreenRegion streamBounds,
            OnSelect onSelect, Runnable onCancel) {
        Rectangle2D virtual = virtualBounds();
        double vx = virtual.getMinX();
        double vy = virtual.getMinY();
        double vw = virtual.getWidth();
        double vh = virtual.getHeight();

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setAlwaysOnTop(true);
        stage.setX(vx);
        stage.setY(vy);
        stage.setWidth(vw);
        stage.setHeight(vh);
        stage.setTitle("Select region");

        Pane root = new Pane();
        root.setPrefSize(vw, vh);

        Image background = captureDesktop(virtual);
        if (background != null) {
            ImageView bgView = new ImageView(background);
            bgView.setFitWidth(vw);
            bgView.setFitHeight(vh);
            bgView.setPreserveRatio(false);
            bgView.setSmooth(true);
            root.getChildren().add(bgView);
        }

        Canvas canvas = new Canvas(vw, vh);

        Pane glass = new Pane();
        glass.setPrefSize(vw, vh);

        Label hintLabel = new Label(hint);
        hintLabel.setTextFill(Color.WHITE);
        hintLabel.setStyle("-fx-background-color: rgba(0,0,0,0.75); -fx-padding: 8 12; -fx-background-radius: 6;");
        Button confirmBtn = new Button("Confirm (Enter)");
        Button cancelBtn = new Button("Cancel (Esc)");
        HBox hintBar = new HBox(10, hintLabel, confirmBtn, cancelBtn);
        hintBar.setLayoutX(16);
        hintBar.setLayoutY(16);

        root.getChildren().addAll(canvas, glass, hintBar);
        Scene scene = new Scene(root, vw, vh);
        scene.setFill(Color.TRANSPARENT);

        double[] anchor = new double[2];
        Rectangle[] current = new Rectangle[1];
        AtomicBoolean done = new AtomicBoolean(false);

        Runnable redraw = () -> draw(canvas, current[0], streamBounds, vx, vy, vw, vh);

        glass.setOnMousePressed(e -> {
            anchor[0] = e.getScreenX();
            anchor[1] = e.getScreenY();
            current[0] = new Rectangle((int) e.getScreenX(), (int) e.getScreenY(), 0, 0);
            redraw.run();
        });
        glass.setOnMouseDragged(e -> {
            current[0] = RegionGeometry.normalize((int) anchor[0], (int) anchor[1],
                    (int) e.getScreenX(), (int) e.getScreenY());
            redraw.run();
        });
        glass.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                confirmBtn.fire();
            }
        });

        Runnable close = () -> {
            stage.hide();
            stage.close();
        };
        confirmBtn.setOnAction(e -> {
            if (done.get()) {
                return;
            }
            Rectangle sel = current[0];
            if (!RegionGeometry.isValid(sel)) {
                hintLabel.setText("Drag a larger rectangle first — then confirm.");
                return;
            }
            if (done.compareAndSet(false, true)) {
                close.run();
                onSelect.accept(sel);
            }
        });
        cancelBtn.setOnAction(e -> {
            if (done.compareAndSet(false, true)) {
                close.run();
                onCancel.run();
            }
        });
        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER -> confirmBtn.fire();
                case ESCAPE -> cancelBtn.fire();
                default -> {
                }
            }
        });
        stage.setOnHidden(e -> {
            if (done.compareAndSet(false, true)) {
                onCancel.run();
            }
        });

        redraw.run();
        stage.setScene(scene);
        stage.show();
        stage.toFront();
        glass.requestFocus();
    }

    private static void draw(Canvas canvas, Rectangle selection, ScreenRegion streamBounds,
            double vx, double vy, double vw, double vh) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, vw, vh);
        gc.setFill(Color.rgb(0, 0, 0, 0.55));
        gc.fillRect(0, 0, vw, vh);
        if (streamBounds != null) {
            double sx = streamBounds.x() - vx;
            double sy = streamBounds.y() - vy;
            gc.setStroke(Color.DEEPSKYBLUE);
            gc.setLineWidth(2.5);
            gc.strokeRect(sx, sy, streamBounds.width(), streamBounds.height());
            gc.setFill(Color.DEEPSKYBLUE);
            gc.fillText("STREAM", sx + 6, sy + 16);
        }
        if (selection != null && selection.width > 0 && selection.height > 0) {
            double lx = selection.x - vx;
            double ly = selection.y - vy;
            gc.clearRect(lx, ly, selection.width, selection.height);
            gc.setStroke(Color.LIME);
            gc.setLineWidth(2);
            gc.strokeRect(lx, ly, selection.width, selection.height);
            gc.setFill(Color.LIME);
            gc.fillText(selection.width + " x " + selection.height, lx + 6, ly + 16);
        }
    }

    private static Image captureDesktop(Rectangle2D virtual) {
        try {
            Robot robot = new Robot();
            BufferedImage shot = robot.createScreenCapture(new Rectangle(
                    (int) virtual.getMinX(), (int) virtual.getMinY(),
                    (int) virtual.getWidth(), (int) virtual.getHeight()));
            return SwingFXUtils.toFXImage(shot, null);
        } catch (Exception e) {
            log.warn("Desktop background capture failed, overlay will use live transparency: {}",
                    e.getMessage());
            return null;
        }
    }

    private static Rectangle2D virtualBounds() {
        List<Screen> screens = Screen.getScreens();
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        for (Screen s : screens) {
            Rectangle2D b = s.getBounds();
            minX = Math.min(minX, b.getMinX());
            minY = Math.min(minY, b.getMinY());
            maxX = Math.max(maxX, b.getMaxX());
            maxY = Math.max(maxY, b.getMaxY());
        }
        if (screens.isEmpty()) {
            return new Rectangle2D(0, 0, 1280, 720);
        }
        return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }

    private static void ensureFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("RegionSelector must be called on the JavaFX Application Thread");
        }
    }
}
