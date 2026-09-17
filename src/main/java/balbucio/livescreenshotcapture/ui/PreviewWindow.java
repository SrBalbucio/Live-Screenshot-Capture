package balbucio.livescreenshotcapture.ui;

import balbucio.livescreenshotcapture.model.CaptureRegion;
import balbucio.livescreenshotcapture.profile.Profile;
import java.awt.image.BufferedImage;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

public class PreviewWindow {
    private static final double MAX_DISPLAY_WIDTH = 960.0;

    private PreviewWindow() {
    }

    public static void show(Window owner, BufferedImage frame, Profile profile) {
        Image fxImage = SwingFXUtils.toFXImage(frame, null);
        double scale = Math.min(1.0, MAX_DISPLAY_WIDTH / fxImage.getWidth());
        double dispW = fxImage.getWidth() * scale;
        double dispH = fxImage.getHeight() * scale;

        ImageView view = new ImageView(fxImage);
        view.setFitWidth(dispW);
        view.setFitHeight(dispH);
        view.setPreserveRatio(true);

        Canvas overlay = new Canvas(dispW, dispH);
        drawOverlay(overlay, profile, dispW, dispH);

        StackPane stack = new StackPane(view, overlay);

        CaptureRegion cam = profile.cameraRegion();
        Label title = new Label("Preview — " + profile.name() + " / layout " + profile.activeLayout().name());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        Label streamLabel = new Label("Stream: " + profile.streamRegion().width() + "x"
                + profile.streamRegion().height() + " @ (" + profile.streamRegion().x() + ","
                + profile.streamRegion().y() + ") — green = full stream");
        Label camLabel = new Label("Camera (relative): " + cam.bounds() + " — yellow box");
        Label presetLabel = new Label("Preset: " + profile.preset().name() + " | output: " + profile.id());

        VBox root = new VBox(8, title, streamLabel, camLabel, presetLabel, new ScrollPane(stack));
        root.setPadding(new Insets(12));

        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle("Profile Preview — " + profile.name());
        stage.setScene(new Scene(root));
        stage.show();
    }

    private static void drawOverlay(Canvas canvas, Profile profile, double dispW, double dispH) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setStroke(Color.LIME);
        gc.setLineWidth(2);
        gc.strokeRect(1, 1, dispW - 2, dispH - 2);
        CaptureRegion cam = profile.cameraRegion();
        double x = cam.bounds().x() * dispW;
        double y = cam.bounds().y() * dispH;
        double w = cam.bounds().width() * dispW;
        double h = cam.bounds().height() * dispH;
        gc.setStroke(Color.YELLOW);
        gc.setLineWidth(2.5);
        gc.strokeRect(x, y, w, h);
        gc.setFill(Color.YELLOW);
        gc.fillText("CAM", x + 4, y + 14);
    }
}
