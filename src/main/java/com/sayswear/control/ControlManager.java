package com.sayswear.control;

import com.sayswear.agent.ActionDecision;
import com.sayswear.agent.ActionType;
import com.sayswear.agent.ControlContext;
import com.sayswear.agent.MovementIntent;
import com.sayswear.game.Direction;
import com.sayswear.game.GameModel;
import com.sayswear.game.PlayerStatus;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Validates model proposals and constructs deterministic plans; the game owns held inputs. */
public final class ControlManager {
    private final GameModel game;
    private final ActionExecutor executor;
    private long latestVersion = -1;
    private long epoch;
    private long revision;
    private String previousCommand = "";
    private ActionDecision lastAccepted;
    private MovementIntent lastMovement;
    private long lastRepeatedUtterance = -1;

    public ControlManager(GameModel game, ActionExecutor executor) {
        this.game = Objects.requireNonNull(game, "game");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void beginInput(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("Transcript versions must be nonnegative");
        }
        latestVersion = Math.max(latestVersion, version);
    }

    public ControlContext getContext() {
        var snapshot = game.getSnapshot();
        return new ControlContext(snapshot.heldDirections(), previousCommand, epoch, revision,
                latestVersion, snapshot.status() == PlayerStatus.ACTIVE, lastMovement);
    }

    /**
     * Stale work has no effect, even if its schema is invalid. A malformed current proposal
     * throws so the controller can invalidate the whole input session and release controls.
     */
    public boolean accept(ActionDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("A decision is required");
        }
        if (decision.epoch() != epoch || decision.transcriptVersion() != latestVersion
                || decision.contextRevision() != revision
                || game.getSnapshot().status() != PlayerStatus.ACTIVE) {
            return false;
        }
        if (!validate(decision)) {
            throw new IllegalArgumentException("Invalid action type, direction set, duration, or request metadata");
        }
        if (isSemanticDuplicate(decision)
                || decision.type() == ActionType.REPEAT_LAST && decision.utteranceId() == lastRepeatedUtterance) {
            return true;
        }
        if (decision.type() != ActionType.KEEP_CURRENT && decision.type() != ActionType.NO_ACTION
                && !(decision.type() == ActionType.REPEAT_LAST && lastMovement == null)) {
            List<GameCommand> commands = plan(decision);
            executor.cancelAll();
            executor.execute(commands);
        }
        if (decision.type() == ActionType.PRESS || decision.type() == ActionType.SWITCH
                || decision.type() == ActionType.TIMED_PRESS) {
            lastMovement = new MovementIntent(decision.type(), decision.directions(), decision.durationMs());
        } else if (decision.type() == ActionType.REPEAT_LAST) {
            lastRepeatedUtterance = decision.utteranceId();
        }
        previousCommand = decision.sourceText();
        lastAccepted = decision;
        revision++;
        return true;
    }

    /** Pure defensive schema validation; freshness is checked separately by accept(). */
    public boolean validate(ActionDecision decision) {
        if (decision == null || decision.type() == null || decision.directions() == null
                || decision.sourceText() == null || decision.transcriptVersion() < 0
                || decision.utteranceId() < 0 || decision.epoch() < 0 || decision.contextRevision() < 0) {
            return false;
        }
        Set<Direction> directions = decision.directions();
        boolean directed = switch (decision.type()) {
            case PRESS, RELEASE, SWITCH, TIMED_PRESS -> true;
            case KEEP_CURRENT, RELEASE_ALL, NO_ACTION, REPEAT_LAST -> false;
        };
        if (directed) {
            if (directions.isEmpty() || directions.size() > 2
                    || directions.stream().anyMatch(direction -> direction == null
                    || directions.contains(direction.opposite()))) {
                return false;
            }
        } else if (!directions.isEmpty()) {
            return false;
        }
        if (decision.type() == ActionType.TIMED_PRESS) {
            return decision.durationMs() == 100 || decision.durationMs() == 200 || decision.durationMs() == 400;
        }
        return decision.durationMs() == 0;
    }

    /** Compute from the held set before cancellation, then reassert every desired direction. */
    public List<GameCommand> plan(ActionDecision decision) {
        if (!validate(decision)) {
            throw new IllegalArgumentException("Cannot plan an invalid action");
        }
        if (decision.type() == ActionType.REPEAT_LAST) {
            if (lastMovement == null) return List.of();
            // Copy semantics into the CURRENT request. Never reuse old identities or command objects.
            decision = new ActionDecision(lastMovement.type(), lastMovement.directions(), lastMovement.durationMs(),
                    decision.transcriptVersion(), decision.utteranceId(), decision.epoch(),
                    decision.contextRevision(), decision.sourceText());
        }
        if (decision.type() == ActionType.KEEP_CURRENT || decision.type() == ActionType.NO_ACTION) {
            return List.of();
        }
        if (decision.type() == ActionType.RELEASE_ALL) {
            return List.of(new ReleaseAllCommand());
        }
        Set<Direction> held = game.getSnapshot().heldDirections();
        EnumSet<Direction> desired = EnumSet.noneOf(Direction.class);
        if (decision.type() == ActionType.RELEASE) {
            desired.addAll(held);
            desired.removeAll(decision.directions());
        } else {
            desired.addAll(decision.directions());
        }
        List<GameCommand> commands = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (held.contains(direction) && !desired.contains(direction)) {
                commands.add(new KeyUpCommand(direction));
            }
        }
        if (decision.type() == ActionType.TIMED_PRESS) {
            commands.add(new TimedPressCommand(desired, decision.durationMs()));
        } else {
            for (Direction direction : Direction.values()) {
                if (desired.contains(direction)) {
                    commands.add(new KeyDownCommand(direction));
                }
            }
        }
        return List.copyOf(commands);
    }

    public void advance(double deltaTime) {
        if (executor.advance(deltaTime)) {
            revision++;
        }
    }

    /** Invalidate old asynchronous results before clearing commands and movement. */
    public void reset() {
        epoch++;
        revision++;
        latestVersion = -1;
        previousCommand = "";
        lastAccepted = null;
        lastMovement = null;
        lastRepeatedUtterance = -1;
        executor.cancelAll();
        executor.execute(List.of(new ReleaseAllCommand()));
    }

    private boolean isSemanticDuplicate(ActionDecision decision) {
        return lastAccepted != null
                && lastAccepted.epoch() == decision.epoch()
                && lastAccepted.utteranceId() == decision.utteranceId()
                && lastAccepted.type() == decision.type()
                && lastAccepted.directions().equals(decision.directions())
                && lastAccepted.durationMs() == decision.durationMs();
    }
}
