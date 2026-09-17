package balbucio.livescreenshotcapture.ui;

import balbucio.livescreenshotcapture.screenshot.BurstFrame;
import balbucio.livescreenshotcapture.storage.StorageService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public class BurstGalleryWindow {
    private static final double THUMB_WIDTH = 220.0;

    private BurstGalleryWindow() {
    }

    public static void show(Window owner, List<BurstFrame> frames, Consumer<Set<Path>> onKeep) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle("Burst — pick the best frames (" + frames.size() + ")");

        HBox cards = new HBox(10);
        cards.setPadding(new Insets(10));
        cards.setAlignment(Pos.CENTER);
        List<CheckBox> boxes = new ArrayList<>();
        for (BurstFrame frame : frames) {
            CheckBox keep = new CheckBox(StorageService.burstLabel(frame.offsetMs()) + " ms");
            keep.setSelected(true);
            boxes.add(keep);
            Image image = new Image(frame.path().toUri().toString(), THUMB_WIDTH, 0, true, true);
            ImageView thumb = new ImageView(image);
            thumb.setFitWidth(THUMB_WIDTH);
            thumb.setPreserveRatio(true);
            thumb.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    showFull(stage, frame);
                }
            });
            VBox card = new VBox(6, thumb, keep);
            card.setAlignment(Pos.CENTER);
            cards.getChildren().add(card);
        }

        Button keepSelected = new Button("Keep selected");
        keepSelected.setOnAction(e -> {
            Set<Path> kept = new HashSet<>();
            for (int i = 0; i < frames.size(); i++) {
                if (boxes.get(i).isSelected()) {
                    kept.add(frames.get(i).path());
                }
            }
            stage.close();
            onKeep.accept(kept);
        });
        Button keepAll = new Button("Keep all");
        keepAll.setOnAction(e -> {
            Set<Path> kept = new HashSet<>();
            frames.forEach(f -> kept.add(f.path()));
            stage.close();
            onKeep.accept(kept);
        });
        Button discardAll = new Button("Discard all");
        discardAll.setOnAction(e -> {
            stage.close();
            onKeep.accept(Set.of());
        });

        Label hint = new Label("Uncheck what you don't want. Double-click a thumbnail for full size. "
                + "Saved originals follow the same choice. Closing this window keeps everything.");
        hint.setWrapText(true);
        VBox root = new VBox(10, hint, new ScrollPane(cards),
                new HBox(8, keepSelected, keepAll, discardAll));
        root.setPadding(new Insets(12));

        stage.setScene(new Scene(root));
        stage.show();
    }

    private static void showFull(Window owner, BurstFrame frame) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle("Burst " + StorageService.burstLabel(frame.offsetMs()) + " ms — "
                + frame.path().getFileName());
        ImageView view = new ImageView(new Image(frame.path().toUri().toString()));
        view.setPreserveRatio(true);
        view.setFitWidth(Math.min(1280, view.getImage().getWidth()));
        stage.setScene(new Scene(new ScrollPane(view)));
        stage.show();
    }
}
