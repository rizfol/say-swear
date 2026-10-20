package com.sayswear.view;

import com.sayswear.agent.DemoDecisionModel;
import com.sayswear.agent.VoiceCommandAgent;
import com.sayswear.app.ApplicationController;
import com.sayswear.game.Direction;
import com.sayswear.game.GameModel;
import com.sayswear.game.PlayerStatus;
import com.sayswear.game.Vector2;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Optional desktop verification; ordinary automated tests do not initialize a GUI toolkit. */
@Tag("gui-smoke")
@EnabledIfEnvironmentVariable(named = "SAY_SWEAR_GUI_SMOKE", matches = "1")
class JavaFxSmokeTest {
    @Test
    @Timeout(30)
    void typedNaturalLanguageAndSessionButtonsControlTheActualSharedGame() throws Exception {
        // Keep native extraction inside the project, including under a restricted test account.
        Path nativeCache = Path.of(".tools", "javafx-cache").toAbsolutePath();
        Files.createDirectories(nativeCache);
        System.setProperty("javafx.cachedir", nativeCache.toString());
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.countDown();
        });
        assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX should start on this desktop");

        Stage stage = null;
        ApplicationController controller = null;
        try {
            JavaFxGameView view = onFx(JavaFxGameView::new);
            controller = new ApplicationController(new GameModel(), view,
                    new VoiceCommandAgent(new DemoDecisionModel(), Duration.ofSeconds(3)));
            ApplicationController actualController = controller;
            stage = onFx(() -> {
                view.bindController(actualController);
                Stage window = new Stage();
                window.setTitle("Say Swear - GUI smoke verification");
                window.setScene(new Scene(view.root(), 1180, 800));
                window.show();
                view.root().applyCss();
                view.root().layout();
                return window;
            });
            controller.start();
            onFx(() -> {
                TextField instruction = (TextField) view.root().lookup(".text-field");
                assertNotNull(instruction, "The GUI should expose a natural-language text field");
                instruction.setText("right");
                instruction.fireEvent(new ActionEvent());
                assertEquals("", instruction.getText(), "Submitting should clear the input field");
                return null;
            });
            await(() -> actualController.getSnapshot().heldDirections().equals(Set.of(Direction.RIGHT)),
                    "The actual GUI instruction should reach the shared control manager");
            await(() -> actualController.getSnapshot().position().distance(
                            actualController.getSnapshot().level().start()) > 0.1,
                    "The independent application loop should move the real player");

            Stage actualStage = stage;
            BufferedImage capture = onFx(() -> {
                view.root().applyCss();
                view.root().layout();
                WritableImage image = actualStage.getScene().snapshot(null);
                int width = (int) image.getWidth();
                int height = (int) image.getHeight();
                assertTrue(width >= 1000 && height >= 600, "Capture should contain the complete application scene");
                int[] pixels = new int[width * height];
                image.getPixelReader().getPixels(0, 0, width, height,
                        PixelFormat.getIntArgbInstance(), pixels, 0, width);
                BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                result.setRGB(0, 0, width, height, pixels, 0, width);
                return result;
            });
            Path capturePath = Path.of(".tools", "gui-smoke.png");
            Files.createDirectories(capturePath.getParent());
            assertTrue(ImageIO.write(capture, "png", capturePath.toFile()));
            System.out.println("JavaFX scene capture: " + capturePath.toAbsolutePath());

            onFx(() -> {
                button(view, "Stop & release").fire();
                return null;
            });
            await(() -> actualController.getSnapshot().heldDirections().isEmpty(),
                    "Stop & release must clear actual game inputs immediately");
            assertEquals(Vector2.ZERO, controller.getSnapshot().velocity());

            onFx(() -> {
                button(view, "Restart run").fire();
                return null;
            });
            await(() -> actualController.getSnapshot().position().equals(
                            actualController.getSnapshot().level().start()),
                    "Restart run must restore the same game's start position");
            assertEquals(PlayerStatus.ACTIVE, controller.getSnapshot().status());
            assertTrue(controller.getSnapshot().heldDirections().isEmpty());
        } finally {
            if (controller != null) controller.close();
            if (stage != null) {
                Stage openStage = stage;
                onFx(() -> { openStage.close(); return null; });
            }
            Platform.exit();
        }
    }

    private static Button button(JavaFxGameView view, String text) {
        return view.root().lookupAll(".button").stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> text.equals(button.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing GUI button: " + text));
    }

    private static <T> T onFx(Callable<T> operation) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(operation.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    private static void await(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), message);
    }
}
