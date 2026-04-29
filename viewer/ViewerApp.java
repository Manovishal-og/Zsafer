package com.zsafer.viewer;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ViewerApp extends Application {

    @Override
    public void start(Stage stage) {

        var params = getParameters().getNamed();

        String url = params.get("url");
        String type = params.get("type");

        StreamView view = new StreamView(url, type);

        stage.setScene(new Scene(view.getRoot(), 900, 600));
        stage.setTitle("Zsafer Viewer");
        stage.setAlwaysOnTop(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
