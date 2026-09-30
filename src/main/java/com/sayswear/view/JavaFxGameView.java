package com.sayswear.view;

import com.sayswear.app.ApplicationController;
import com.sayswear.game.Direction;
import com.sayswear.game.GameSnapshot;
import com.sayswear.game.Level;
import com.sayswear.game.PlayerStatus;
import com.sayswear.game.Vector2;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/** Canvas-based presentation of the shared game; it never simulates movement. */
public final class JavaFxGameView implements GameView {
    private final BorderPane root = new BorderPane();
    private final GameCanvas viewport = new GameCanvas();
    private final Label lifecycle = label("READY", "state-pill");
    private final Label voiceStatus = label("Listening is off", "voice-status");
    private final Label transcript = label("Your words will appear here.", "transcript");
    private final Label message = label("Start listening, then tell your player where to go.", "message");
    private final Label held = label("NONE", "metric-value");
    private final Label position = label("—", "metric-value");
    private final Label velocity = label("—", "metric-value");
    private final TextField textCommand = new TextField();

    private final AtomicReference<GameSnapshot> pendingSnapshot = new AtomicReference<>();
    private final AtomicReference<String> pendingTranscript = new AtomicReference<>();
    private final AtomicReference<String> pendingMessage = new AtomicReference<>();
    private final AtomicReference<String> pendingVoiceStatus = new AtomicReference<>();
    private final AtomicBoolean updateScheduled = new AtomicBoolean();
    private ApplicationController controller;

    /** Construct on the JavaFX application thread; callbacks may arrive on any thread. */
    public JavaFxGameView() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Create JavaFxGameView on the JavaFX application thread");
        }
        root.getStyleClass().add("app-root");
        root.setPrefSize(1180, 800);
        root.setPadding(new Insets(26));
        URL stylesheet = JavaFxGameView.class.getResource("/com/sayswear/say-swear.css");
        if (stylesheet != null) {
            root.getStylesheets().add(stylesheet.toExternalForm());
        }
        root.setTop(header());
        VBox playArea = new VBox(12, gameHeader(), viewport, legend());
        playArea.setMinWidth(340);
        VBox.setVgrow(viewport, Priority.ALWAYS);
        BorderPane.setMargin(playArea, new Insets(22, 22, 0, 0));
        root.setCenter(playArea);
        ScrollPane sidebar = new ScrollPane(sidebar());
        sidebar.setFitToWidth(true);
        sidebar.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebar.setPrefWidth(300);
        sidebar.setMinWidth(275);
        sidebar.getStyleClass().add("sidebar-scroll");
        BorderPane.setMargin(sidebar, new Insets(22, 0, 0, 0));
        root.setRight(sidebar);
    }

    public Parent root() {
        return root;
    }

    public void bindController(ApplicationController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public void requestStartVoice() {
        if (controller != null) {
            controller.startVoiceControl();
        }
    }

    public void requestStopVoice() {
        if (controller != null) {
            controller.stopVoiceControl();
        }
    }

    @Override
    public void render(GameSnapshot snapshot) {
        pendingSnapshot.set(Objects.requireNonNull(snapshot, "snapshot"));
        scheduleUpdate();
    }

    @Override
    public void showMessage(String value) {
        pendingMessage.set(Objects.requireNonNullElse(value, ""));
        scheduleUpdate();
    }

    @Override
    public void showTranscript(String value) {
        pendingTranscript.set(Objects.requireNonNullElse(value, ""));
        scheduleUpdate();
    }

    @Override
    public void showVoiceStatus(String value) {
        pendingVoiceStatus.set(Objects.requireNonNullElse(value, ""));
        scheduleUpdate();
    }

    private void scheduleUpdate() {
        if (updateScheduled.compareAndSet(false, true)) {
            // Always queue, even from the FX thread, to combine bursts of callbacks.
            Platform.runLater(this::drainUpdates);
        }
    }

    private void drainUpdates() {
        try {
            GameSnapshot snapshot = pendingSnapshot.getAndSet(null);
            if (snapshot != null) {
                applySnapshot(snapshot);
            }
            String value = pendingTranscript.getAndSet(null);
            if (value != null) {
                transcript.setText(value.isBlank() ? "Your words will appear here." : value);
            }
            value = pendingMessage.getAndSet(null);
            if (value != null) {
                message.setText(value);
            }
            value = pendingVoiceStatus.getAndSet(null);
            if (value != null) {
                voiceStatus.setText(value);
            }
        } finally {
            updateScheduled.set(false);
            if (pendingSnapshot.get() != null || pendingTranscript.get() != null
                    || pendingMessage.get() != null || pendingVoiceStatus.get() != null) {
                scheduleUpdate();
            }
        }
    }

    private void applySnapshot(GameSnapshot snapshot) {
        lifecycle.setText(snapshot.status().name());
        String active = Arrays.stream(Direction.values()).filter(snapshot.heldDirections()::contains)
                .map(Enum::name).collect(Collectors.joining(" + "));
        held.setText(active.isEmpty() ? "NONE" : active);
        position.setText(coordinates(snapshot.position()));
        velocity.setText(coordinates(snapshot.velocity()));
        viewport.setSnapshot(snapshot);
    }

    private HBox header() {
        VBox titles = new VBox(4,
                label("A VOICE PRECISION GAME", "eyebrow"),
                label("Say Swear", "app-title"),
                label("Your voice. One narrow path.", "subtitle"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(20, titles, spacer, lifecycle);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private HBox gameHeader() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(label("THE CROSSING", "section-title"), spacer,
                label("LEVEL 01", "small-muted"));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox sidebar() {
        Button start = new Button("Start listening");
        start.getStyleClass().add("primary-button");
        start.setMaxWidth(Double.MAX_VALUE);
        start.setOnAction(event -> requestStartVoice());
        Button stop = new Button("Stop & release");
        stop.getStyleClass().add("stop-button");
        stop.setMaxWidth(Double.MAX_VALUE);
        stop.setOnAction(event -> requestStopVoice());
        VBox listening = card("VOICE CONTROL", voiceStatus, start, stop,
                label("Speak a direction. Keep talking to correct it. Stop & release clears all movement immediately.", "helper"));

        VBox heard = card("WHAT YOU SAID", transcript);
        VBox feedback = card("LATEST UPDATE", message);
        VBox metrics = card("PLAYER", metric("HELD DIRECTIONS", held), metric("POSITION · X, Y", position),
                metric("VELOCITY · X, Y", velocity));

        textCommand.setPromptText("e.g. a little right");
        textCommand.setAccessibleText("Type a natural-language movement instruction");
        textCommand.setOnAction(event -> submitTypedCommand());
        Button send = new Button("Send instruction");
        send.setMaxWidth(Double.MAX_VALUE);
        send.setOnAction(event -> submitTypedCommand());
        VBox typed = card("TEXT INSTRUCTIONS", label("You can also type a command and press Enter.", "helper"), textCommand, send);

        Label examples = label("“up”  ·  “stop”\n“other way”  ·  “keep going”\n“a little left”  ·  “again”", "examples");
        Button restart = new Button("Restart run");
        restart.getStyleClass().add("quiet-button");
        restart.setMaxWidth(Double.MAX_VALUE);
        restart.setOnAction(event -> {
            if (controller != null) {
                controller.restart();
            }
        });
        VBox sidebar = new VBox(12, listening, heard, feedback, metrics, typed,
                card("TRY SAYING", examples), restart);
        sidebar.setPadding(new Insets(0, 8, 0, 0));
        return sidebar;
    }

    private void submitTypedCommand() {
        String text = textCommand.getText().strip();
        if (!text.isEmpty() && controller != null) {
            controller.submitTextCommand(text);
            textCommand.clear();
        }
    }

    private static VBox metric(String title, Label value) {
        return new VBox(3, label(title, "metric-title"), value);
    }

    private static VBox card(String title, javafx.scene.Node... children) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.getChildren().add(label(title, "section-title"));
        card.getChildren().addAll(children);
        return card;
    }

    private static HBox legend() {
        HBox row = new HBox(18,
                label("●  YOU", "legend-player"),
                label("S  START", "legend-start"),
                label("◇  GOAL", "legend-goal"));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 0, 0, 0));
        return row;
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static String coordinates(Vector2 vector) {
        return String.format(Locale.ROOT, "%.2f, %.2f", vector.x(), vector.y());
    }

    /** Resizing changes the projection only, never the level or player coordinates. */
    private static final class GameCanvas extends Region {
        private final Canvas canvas = new Canvas();
        private GameSnapshot snapshot;

        GameCanvas() {
            getChildren().add(canvas);
            setMinSize(300, 320);
            setPrefSize(820, 640);
            setAccessibleText("Game map: cyan player, warm path, start marked S, gold goal");
        }

        void setSnapshot(GameSnapshot snapshot) {
            this.snapshot = snapshot;
            draw();
        }

        @Override
        protected void layoutChildren() {
            canvas.setWidth(getWidth());
            canvas.setHeight(getHeight());
            draw();
        }

        private void draw() {
            GraphicsContext g = canvas.getGraphicsContext2D();
            double width = canvas.getWidth();
            double height = canvas.getHeight();
            g.clearRect(0, 0, width, height);
            g.setFill(Color.web("#0a111d"));
            g.fillRoundRect(0, 0, width, height, 20, 20);
            g.setStroke(Color.web("#273344"));
            g.setLineWidth(1);
            g.strokeRoundRect(0.5, 0.5, width - 1, height - 1, 20, 20);
            if (snapshot == null || width < 80 || height < 80) {
                return;
            }
            Level level = snapshot.level();
            double scale = Math.min((width - 48) / level.width(), (height - 58) / level.height());
            double offsetX = (width - level.width() * scale) / 2;
            double offsetY = (height - level.height() * scale) / 2;
            g.save();
            g.translate(offsetX, offsetY);
            g.scale(scale, scale);
            g.setStroke(Color.web("#14202f"));
            g.setLineWidth(0.5 / scale);
            for (double x = 0; x <= level.width(); x += 10) {
                g.strokeLine(x, 0, x, level.height());
            }
            for (double y = 0; y <= level.height(); y += 10) {
                g.strokeLine(0, y, level.width(), y);
            }
            g.setLineCap(StrokeLineCap.ROUND);
            g.setLineJoin(StrokeLineJoin.ROUND);
            strokePath(g, level.safePath(), level.pathHalfWidth() * 2 + 1.1, Color.web("#514b45"));
            strokePath(g, level.safePath(), level.pathHalfWidth() * 2, Color.web("#c4b49e"));
            strokePath(g, level.safePath(), 0.18, Color.web("#dfd0b9"));

            Vector2 start = level.start();
            g.setFill(Color.web("#6f6251"));
            g.setFont(Font.font("System", FontWeight.BOLD, 2.2));
            g.fillText("S", start.x() - 0.7, start.y() - 1.1);
            Vector2 goal = level.goal();
            g.setFill(Color.web("#f4c267"));
            g.fillPolygon(new double[]{goal.x(), goal.x() + 1.7, goal.x(), goal.x() - 1.7},
                    new double[]{goal.y() - 1.7, goal.y(), goal.y() + 1.7, goal.y()}, 4);
            g.setStroke(Color.web("#694820"));
            g.setLineWidth(0.25);
            g.strokePolygon(new double[]{goal.x(), goal.x() + 1.7, goal.x(), goal.x() - 1.7},
                    new double[]{goal.y() - 1.7, goal.y(), goal.y() + 1.7, goal.y()}, 4);

            Vector2 player = snapshot.position();
            double radius = level.playerRadius();
            Color playerColor = snapshot.status() == PlayerStatus.FALLING
                    ? Color.web("#ff8790") : Color.web("#6be8e2");
            g.setFill(new Color(playerColor.getRed(), playerColor.getGreen(), playerColor.getBlue(), 0.16));
            g.fillOval(player.x() - radius - 1, player.y() - radius - 1, 2 * (radius + 1), 2 * (radius + 1));
            g.setFill(playerColor);
            g.fillOval(player.x() - radius, player.y() - radius, radius * 2, radius * 2);
            g.setStroke(Color.web("#103f47"));
            g.setLineWidth(0.2);
            g.strokeOval(player.x() - radius, player.y() - radius, radius * 2, radius * 2);
            g.restore();

            if (snapshot.status() == PlayerStatus.ACTIVE && snapshot.heldDirections().isEmpty()
                    && player.distance(level.start()) < 1e-6) {
                drawStartArrow(g, offsetX + player.x() * scale,
                        offsetY + player.y() * scale - radius * scale - 8, width);
            }

            g.setFill(Color.web("#8799ad"));
            g.setFont(Font.font("System", 10));
            g.fillText("Every fall sends you back to the start.", 18, height - 16);
            if (snapshot.status() == PlayerStatus.FINISHED) {
                g.setFill(Color.web("#f4c267"));
                g.setFont(Font.font("System", FontWeight.BOLD, 22));
                g.fillText("YOU MADE IT.", 24, 34);
            }
        }

        /** Screen-space dimensions keep the welcome cue readable as the map resizes. */
        private static void drawStartArrow(GraphicsContext g, double playerX, double tipY, double width) {
            double arrowWidth = 156;
            double left = Math.max(12, Math.min(playerX - arrowWidth / 2, width - arrowWidth - 12));
            double right = left + arrowWidth;
            double top = tipY - 78;
            double shoulder = top + 38;
            double headTop = tipY - 24;
            double[] x = {left, right, right, playerX + 13, playerX + 13,
                    playerX + 27, playerX, playerX - 27, playerX - 13, playerX - 13, left};
            double[] y = {top, top, shoulder, shoulder, headTop,
                    headTop, tipY, headTop, headTop, shoulder, shoulder};

            g.save();
            g.setFill(Color.web("#dc2626"));
            g.fillPolygon(x, y, x.length);
            g.setStroke(Color.web("#ff7777"));
            g.setLineWidth(1.5);
            g.strokePolygon(x, y, x.length);
            g.setFill(Color.WHITE);
            g.setFont(Font.font("System", FontWeight.BOLD, 14));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText("YOU ARE HERE", left + arrowWidth / 2, top + 19);
            g.restore();
        }

        private static void strokePath(GraphicsContext g, List<Vector2> path, double width, Color color) {
            g.setStroke(color);
            g.setLineWidth(width);
            g.beginPath();
            g.moveTo(path.getFirst().x(), path.getFirst().y());
            for (int i = 1; i < path.size(); i++) {
                g.lineTo(path.get(i).x(), path.get(i).y());
            }
            g.stroke();
        }
    }
}
