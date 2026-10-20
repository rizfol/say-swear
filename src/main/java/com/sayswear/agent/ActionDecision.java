package com.sayswear.agent;

import com.sayswear.game.Direction;
import java.util.Objects;
import java.util.Set;

/** Immutable semantic proposal. ControlManager still validates its schema and freshness. */
public record ActionDecision(ActionType type, Set<Direction> directions, int durationMs,
                             long transcriptVersion, long utteranceId, long epoch,
                             long contextRevision, String sourceText) {
    public ActionDecision {
        Objects.requireNonNull(type, "type");
        directions = Set.copyOf(directions);
        Objects.requireNonNull(sourceText, "sourceText");
    }

    public static ActionDecision semantic(ActionType type, Set<Direction> directions, int durationMs) {
        return new ActionDecision(type, directions, durationMs, -1, -1, -1, -1, "");
    }

    public ActionDecision withRequest(TranscriptUpdate update, ControlContext context) {
        return new ActionDecision(type, directions, durationMs, update.version(), update.utteranceId(),
                update.epoch(), context.revision(), update.text());
    }
}
