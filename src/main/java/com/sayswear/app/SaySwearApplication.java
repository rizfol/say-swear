package com.sayswear.app;

import com.sayswear.view.JavaFxGameView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** JavaFX lifecycle only; all gameplay and input coordination lives in the shared controller. */
public final class SaySwearApplication extends Application {
    private ApplicationController controller;

    @Override public void start(Stage stage) {
        AppConfig configuration = AppConfig.parse(getParameters().getRaw().toArray(String[]::new));
        JavaFxGameView view = new JavaFxGameView();
        controller = Main.compose(configuration, view, true);
        view.bindController(controller);
        stage.setTitle("Say Swear" + (configuration.demo() ? " — Offline demo" : " — SemIf / Qwen3.5 4B"));
        stage.setScene(new Scene(view.root()));
        stage.setMinWidth(850);
        stage.setMinHeight(620);
        stage.show();
        controller.start();
        view.showMessage(configuration.decisionDescription());
        stage.setOnCloseRequest(event -> controller.close());
    }

    @Override public void stop() { if (controller != null) controller.close(); }
}
