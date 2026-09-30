package com.sayswear.app;

import com.sayswear.agent.*;
import com.sayswear.game.GameModel;
import com.sayswear.view.CliView;
import com.sayswear.view.GameView;
import com.sayswear.voice.SherpaOnnxAdapter;
import com.sayswear.voice.VoiceInputService;
import javafx.application.Application;
import java.io.IOException;

/** Plain Java launcher keeps CLI startup independent of the JavaFX application lifecycle. */
public final class Main {
    private Main() { }

    public static void main(String[] arguments) {
        try {
            AppConfig configuration = AppConfig.parse(arguments);
            if (configuration.help()) { printHelp(); return; }
            if (configuration.cli()) runCli(configuration);
            else Application.launch(SaySwearApplication.class, arguments);
        } catch (IllegalArgumentException | IOException exception) {
            System.err.println("Say Swear: " + exception.getMessage());
        }
    }

    static ApplicationController compose(AppConfig configuration, GameView view, boolean withVoice) {
        DecisionModel model = configuration.demo() ? new DemoDecisionModel()
                : new OpenJevAdapter(configuration.endpoint(), configuration.model(), configuration.decisionTimeout());
        var controller = new ApplicationController(new GameModel(), view,
                new VoiceCommandAgent(model, configuration.decisionTimeout()));
        if (withVoice) {
            controller.attachVoice(new VoiceInputService(new SherpaOnnxAdapter(configuration.asrModelDirectory()),
                    controller::handleTranscript, controller::handleVoiceFailure, controller::handleVoiceReady));
        }
        return controller;
    }

    private static void runCli(AppConfig configuration) throws IOException {
        // Resource order closes the controller before the terminal, so late events cannot repaint it.
        try (CliView view = CliView.open(configuration.quietCli());
             ApplicationController controller = compose(configuration, view, false)) {
            view.setSessionDescription(configuration.decisionDescription());
            view.showHelp();
            controller.start();
            controller.barrier().join();
            String input;
            while ((input = view.readCommand()) != null) {
                String command = input.strip();
                if (command.equalsIgnoreCase("/quit")) break;
                switch (command.toLowerCase(java.util.Locale.ROOT)) {
                    case "/state" -> view.render(controller.getSnapshot());
                    case "/pause" -> controller.pauseControls();
                    case "/restart" -> controller.restart();
                    case "/help" -> view.showHelp();
                    default -> {
                        if (command.startsWith("/")) view.showMessage("Unknown session command. Use /help.");
                        else controller.submitTextCommand(command);
                    }
                }
                controller.barrier().join();
            }
        }
    }

    private static void printHelp() {
        System.out.println("""
                Say Swear — JavaFX by default; add --cli for the terminal frontend.
                  --demo                      Explicit offline parser; not the AI implementation
                  --quiet-cli                 Plain text for scripted input; no live terminal redraw
                  --endpoint URL              Local SemIf decision bridge (SEMIF_ENDPOINT)
                  --decision-timeout-ms N      Interpretation deadline, default 2000
                  --asr-model-dir DIRECTORY   Local streaming ASR files (SHERPA_MODEL_DIR)
                  --help                      Show this help
                Both frontends share the game, agent and control core. No API key is required.
                """);
    }
}
