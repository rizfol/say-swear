package com.sayswear.agent;

import com.sayswear.game.Direction;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Explicit offline demonstration only. This small deterministic parser is NOT an AI model. */
public final class DemoDecisionModel implements DecisionModel {
    private static final Pattern CORRECTION = Pattern.compile("\\b(?:no|actually|instead)\\b");

    @Override
    public CompletionStage<ActionDecision> decide(TranscriptUpdate update, ControlContext context) {
        return CompletableFuture.completedFuture(parse(update.text(), context));
    }

    private ActionDecision parse(String text, ControlContext context) {
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ").strip();
        if (!context.movementAllowed() || normalized.matches(".*\\b(goal|solve|navigate|path|route|destination)\\b.*")) {
            return action(ActionType.NO_ACTION, Set.of(), 0);
        }
        var correction = CORRECTION.matcher(normalized);
        boolean corrected = false;
        int correctionEnd = 0;
        while (correction.find()) { corrected = true; correctionEnd = correction.end(); }
        if (corrected) normalized = normalized.substring(correctionEnd).strip();
        normalized = normalized.replaceAll("\\bplease\\b", "").replaceAll("\\s+", " ").strip();
        if (Set.of("stop", "stop moving", "release all", "halt").contains(normalized)) {
            return action(ActionType.RELEASE_ALL, Set.of(), 0);
        }
        if (Set.of("keep going", "continue", "keep moving").contains(normalized)) {
            return action(ActionType.KEEP_CURRENT, Set.of(), 0);
        }
        if (Set.of("again", "do that again", "repeat", "repeat that", "do it again").contains(normalized)) {
            return action(ActionType.REPEAT_LAST, Set.of(), 0);
        }
        if (Set.of("other way", "the other way", "reverse").contains(normalized)) {
            var reversed = EnumSet.noneOf(Direction.class);
            context.heldDirections().forEach(d -> reversed.add(d.opposite()));
            return action(reversed.isEmpty() ? ActionType.NO_ACTION : ActionType.SWITCH, reversed, 0);
        }
        boolean release = normalized.startsWith("release ") || normalized.startsWith("let go of ");
        int duration = normalized.contains("tiny") ? 100
                : normalized.contains("longer") ? 400
                : normalized.matches(".*\\b(little|briefly|short)\\b.*") ? 200 : 0;
        String directionsOnly = normalized.replaceAll(
                "\\b(?:let go of|a little bit|a tiny bit|a little|a bit|for a short time|a longer moment|"
                        + "go|move|press|turn|release|briefly|tiny|longer|short|and)\\b", " ")
                .replaceAll("\\s+", " ").strip();
        var directions = EnumSet.noneOf(Direction.class);
        if (directionsOnly.isEmpty()) return action(ActionType.NO_ACTION, Set.of(), 0);
        for (String token : directionsOnly.split(" ")) {
            try { directions.add(Direction.valueOf(token.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ex) { return action(ActionType.NO_ACTION, Set.of(), 0); }
        }
        if (directions.size() > 2 || directions.stream().anyMatch(d -> directions.contains(d.opposite()))) {
            return action(ActionType.NO_ACTION, Set.of(), 0);
        }
        return action(release ? ActionType.RELEASE : duration > 0 ? ActionType.TIMED_PRESS
                : corrected ? ActionType.SWITCH : ActionType.PRESS, directions, release ? 0 : duration);
    }

    private static ActionDecision action(ActionType type, Set<Direction> directions, int duration) {
        return ActionDecision.semantic(type, directions, duration);
    }
}
