package com.sayswear.app;

import com.sayswear.agent.*;
import com.sayswear.control.ActionExecutor;
import com.sayswear.control.ControlManager;
import com.sayswear.game.*;
import com.sayswear.view.GameView;
import com.sayswear.voice.VoiceInputService;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serializes all game/control mutations; AI and microphone work never block the loop. */
public final class ApplicationController implements GameObserver, AutoCloseable {
    private final GameModel game;
    private final GameView view;
    private final VoiceCommandAgent agent;
    private final ControlManager controls;
    private final ScheduledExecutorService loop;
    private final boolean autoTick;
    private final AtomicBoolean closing = new AtomicBoolean();
    private volatile Thread loopThread;
    private volatile GameSnapshot snapshot;
    private VoiceInputService voice;
    private boolean started;
    private boolean voiceRequested;
    private long version;
    private long utterance;
    private long voiceSourceId = -1;
    private long voiceUtterance;
    private String lastVoiceText = "";
    private long lastTickNanos;

    public ApplicationController(GameModel game, GameView view, VoiceCommandAgent agent) {
        this(game, view, agent, true);
    }

    ApplicationController(GameModel game, GameView view, VoiceCommandAgent agent, boolean autoTick) {
        this.game = Objects.requireNonNull(game);
        this.view = Objects.requireNonNull(view);
        this.agent = Objects.requireNonNull(agent);
        this.autoTick = autoTick;
        controls = new ControlManager(game, new ActionExecutor(game));
        snapshot = game.getSnapshot();
        loop = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "say-swear-game-loop");
            thread.setDaemon(true);
            loopThread = thread;
            return thread;
        });
    }

    /** Attach once during composition, before start(). */
    public void attachVoice(VoiceInputService service) {
        if (started || voice != null) throw new IllegalStateException("Attach voice before starting");
        voice = Objects.requireNonNull(service);
    }

    public void start() {
        dispatch(() -> {
            if (started) return;
            started = true;
            game.addObserver(this); // Cleanup must run before presentation observers.
            game.addObserver(view);
            publish();
            lastTickNanos = System.nanoTime();
            if (autoTick) loop.scheduleAtFixedRate(this::tickFromClock, 16, 16, TimeUnit.MILLISECONDS);
        });
    }

    public GameSnapshot getSnapshot() { return snapshot; }

    public void submitTextCommand(String text) {
        dispatch(() -> submit(text, ++utterance));
    }

    /** Microphone callbacks carry the epoch captured when recording began. */
    public void handleTranscript(String text, long sourceUtterance, long epoch) {
        dispatch(() -> {
            if (!voiceRequested || epoch != controls.getContext().epoch()
                    || sourceUtterance < voiceSourceId) return;
            String normalized = normalize(text);
            if (sourceUtterance != voiceSourceId) {
                voiceSourceId = sourceUtterance;
                voiceUtterance = ++utterance;
                lastVoiceText = "";
            }
            // A typed command supersedes the rest of an older spoken utterance.
            if (voiceUtterance != utterance || normalized.equals(lastVoiceText)) return;
            lastVoiceText = normalized;
            view.showVoiceStatus("Listening — interpreting speech");
            submit(normalized, voiceUtterance);
        });
    }

    private void submit(String text, long inputUtterance) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) return;
        if (game.getSnapshot().status() != PlayerStatus.ACTIVE) {
            view.showMessage("Wait for recovery, or restart after reaching the goal.");
            return;
        }
        if (normalized.length() > 1000) {
            failCurrent("Instruction is too long. Use a short movement instruction.");
            return;
        }
        view.showTranscript(normalized);
        controls.beginInput(++version);
        ControlContext context = controls.getContext();
        TranscriptUpdate update = new TranscriptUpdate(normalized, inputUtterance, version, context.epoch());
        try {
            agent.interpret(update, context).whenComplete((decision, failure) -> dispatch(() -> {
                if (failure == null) handleDecision(decision);
                else if (isCurrent(update)) failCurrent("Decision unavailable: " + errorMessage(failure));
            }));
        } catch (RuntimeException exception) {
            if (isCurrent(update)) failCurrent("Decision unavailable: " + errorMessage(exception));
        }
    }

    public void handleDecision(ActionDecision decision) {
        dispatch(() -> {
            try {
                if (controls.accept(decision)) {
                    view.showMessage(acceptanceMessage(decision));
                    publish();
                } else if (decision != null && decision.epoch() == controls.getContext().epoch()
                        && decision.transcriptVersion() == controls.getContext().latestVersion()) {
                    view.showMessage("Controls changed while interpreting. Please repeat your instruction.");
                }
            } catch (IllegalArgumentException exception) {
                failCurrent("Invalid model decision; controls released. Please try again.");
            }
        });
    }

    private String acceptanceMessage(ActionDecision decision) {
        if (decision.type() == ActionType.REPEAT_LAST) {
            MovementIntent movement = controls.getContext().lastMovement();
            return movement == null ? "No previous movement to repeat. Give a direction first."
                    : "Accepted: REPEAT_LAST " + movement.type() + " " + movement.directions()
                    + (movement.durationMs() == 0 ? "" : " for " + movement.durationMs() + " ms");
        }
        if (decision.type() == ActionType.KEEP_CURRENT && game.getSnapshot().heldDirections().isEmpty()) {
            return "Nothing is moving. Give a direction first.";
        }
        if (decision.type() == ActionType.NO_ACTION) {
            return "No movement instruction recognized; controls are unchanged.";
        }
        return "Accepted: " + decision.type() + (decision.directions().isEmpty() ? "" : " " + decision.directions())
                + (decision.durationMs() == 0 ? "" : " for " + decision.durationMs() + " ms");
    }

    public void startVoiceControl() {
        dispatch(() -> {
            if (voice == null) {
                view.showMessage("Speech recognition is not configured. See the setup instructions.");
            } else if (game.getSnapshot().status() != PlayerStatus.ACTIVE) {
                view.showMessage("Start listening after recovery or restart the run.");
            } else if (!voiceRequested) {
                voiceRequested = true;
                voiceSourceId = -1;
                view.showVoiceStatus("Starting microphone and local speech recognition…");
                voice.start(controls.getContext().epoch());
            }
        });
    }

    public void stopVoiceControl() { pauseControls(); }

    public void pauseControls() {
        dispatch(() -> {
            voiceRequested = false;
            invalidateControls();
            view.showMessage("Controls released. Give a new instruction to move.");
            view.showVoiceStatus("Listening is off");
            publish();
        });
    }

    public void handleVoiceFailure(String message, long epoch) {
        dispatch(() -> {
            if (voiceRequested && epoch == controls.getContext().epoch())
                failCurrent("Microphone / speech recognition: " + message);
        });
    }

    public void handleVoiceReady(long epoch) {
        dispatch(() -> {
            if (voiceRequested && epoch == controls.getContext().epoch())
                view.showVoiceStatus("Listening — speak a movement instruction");
        });
    }

    public void restart() {
        dispatch(() -> {
            voiceRequested = false;
            invalidateControls();
            game.restart();
            view.showTranscript("");
            view.showVoiceStatus("Listening is off");
            view.showMessage("New run. Start listening or type a movement instruction.");
            publish();
        });
    }

    @Override public void onGameEvent(GameEvent event) {
        if (Thread.currentThread() != loopThread) {
            throw new IllegalStateException("Game mutations must run on the application loop");
        }
        switch (event.type()) {
            case FALL_DETECTED -> {
                invalidateControls();
                if (voiceRequested) view.showVoiceStatus("Recovering — listening will restart");
            }
            case RESPAWNED -> {
                if (voiceRequested && voice != null) {
                    voiceSourceId = -1;
                    voice.start(controls.getContext().epoch());
                    view.showVoiceStatus("Listening — give a fresh instruction");
                }
            }
            case GOAL_REACHED -> {
                voiceRequested = false;
                invalidateControls();
                view.showVoiceStatus("Goal reached — listening is off");
            }
            default -> { }
        }
        snapshot = game.getSnapshot();
    }

    private void tickFromClock() {
        if (closing.get()) return;
        long now = System.nanoTime();
        double seconds = Math.min((now - lastTickNanos) / 1_000_000_000.0, 0.05);
        lastTickNanos = now;
        try { advance(seconds); }
        catch (RuntimeException exception) { failCurrent("Game update failed: " + errorMessage(exception)); }
    }

    private void advance(double seconds) {
        controls.advance(seconds);
        game.update(seconds);
        snapshot = game.getSnapshot();
    }

    // Package-private deterministic integration-test seam; the real app uses the clock above.
    CompletableFuture<Void> advanceForTest(double seconds) { return enqueue(() -> advance(seconds)); }
    CompletableFuture<Void> barrier() { return enqueue(() -> { }); }

    private boolean isCurrent(TranscriptUpdate update) {
        ControlContext current = controls.getContext();
        return current.epoch() == update.epoch() && current.latestVersion() == update.version();
    }

    private void failCurrent(String message) {
        voiceRequested = false;
        invalidateControls();
        view.showVoiceStatus("Listening is off");
        view.showMessage(message);
        publish();
    }

    private void invalidateControls() {
        controls.reset(); // Invalidate first, before cancelling asynchronous callbacks.
        agent.reset();
        if (voice != null) voice.stop();
        voiceSourceId = -1;
        lastVoiceText = "";
    }

    private void publish() { snapshot = game.getSnapshot(); view.render(snapshot); }

    private void dispatch(Runnable action) {
        if (closing.get()) return;
        if (Thread.currentThread() == loopThread) action.run();
        else {
            try { loop.execute(action); }
            catch (RejectedExecutionException ignored) { /* Closing won the race. */ }
        }
    }

    private CompletableFuture<Void> enqueue(Runnable action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (closing.get()) return CompletableFuture.failedFuture(new IllegalStateException("Closed"));
        loop.execute(() -> {
            try { action.run(); result.complete(null); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    private static String normalize(String text) {
        return Objects.requireNonNullElse(text, "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String errorMessage(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) failure = failure.getCause();
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    @Override public void close() {
        if (!closing.compareAndSet(false, true)) return;
        Runnable cleanup = () -> {
            voiceRequested = false;
            controls.reset();
            agent.close();
            if (voice != null) voice.close();
            game.removeObserver(this);
            game.removeObserver(view);
            snapshot = game.getSnapshot();
        };
        if (Thread.currentThread() == loopThread) cleanup.run();
        else {
            try { loop.submit(cleanup).get(5, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            catch (ExecutionException | TimeoutException exception) {
                System.err.println("Shutdown incomplete: " + errorMessage(exception));
            }
        }
        loop.shutdownNow();
    }
}
