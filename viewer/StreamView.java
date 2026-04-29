package com.zsafer.viewer;

import javafx.scene.control.Label;
import javafx.scene.image.*;
import javafx.scene.layout.StackPane;
import javafx.scene.media.*;
import javafx.scene.text.Text;

public class StreamView {

    private StackPane root = new StackPane();

    public StreamView(String url, String type) {

        try {

            switch (type) {

                case "image":
                    ImageView iv = new ImageView(new Image(url));
                    iv.setPreserveRatio(true);
                    iv.setFitWidth(800);
                    root.getChildren().add(iv);
                    break;

                case "video":
                    Media media = new Media(url);
                    MediaPlayer player = new MediaPlayer(media);
                    MediaView mv = new MediaView(player);
                    root.getChildren().add(mv);
                    player.play();
                    break;

                case "text":
                    root.getChildren().add(new Label("Text preview coming"));
                    break;

                default:
                    root.getChildren().add(new Label("Unsupported type"));
            }

        } catch (Exception e) {
            root.getChildren().add(new Label("Error loading file"));
        }

        // WATERMARK
        Text wm = new Text("ZSAFER | ONE-TIME VIEW");
        wm.setOpacity(0.25);
        wm.setRotate(-30);
        wm.setStyle("-fx-font-size: 24px;");

        root.getChildren().add(wm);
    }

    public StackPane getRoot() {
        return root;
    }
}
