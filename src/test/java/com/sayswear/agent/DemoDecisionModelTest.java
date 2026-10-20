package com.sayswear.agent;

import com.sayswear.game.Direction;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DemoDecisionModelTest {
    @Test void explicitDemoSupportsCorrectionContextAndCombinedTimedInput() {
        assertEquals(ActionType.RELEASE_ALL, decide("stop").type());
        assertEquals(Set.of(Direction.LEFT), decide("other way").directions());
        assertEquals(ActionType.KEEP_CURRENT, decide("keep going").type());
        assertEquals(Set.of(Direction.LEFT), decide("right — no, left").directions());
        ActionDecision timed = decide("a little up and right");
        assertEquals(ActionType.TIMED_PRESS, timed.type());
        assertEquals(Set.of(Direction.UP, Direction.RIGHT), timed.directions());
        assertEquals(200, timed.durationMs());
    }

    @Test void demoDeclinesUnclearOpposedAndAutonomousInstructions() {
        for (String text : new String[]{"up and down", "go to the goal", "solve the path", "left eventually", ""}) {
            assertEquals(ActionType.NO_ACTION, decide(text).type(), text);
        }
    }

    @Test void repeatUsesStructuredHistoryEvenAfterStopAndDoesNotBecomeKeepCurrent() {
        var history = new MovementIntent(ActionType.TIMED_PRESS, Set.of(Direction.RIGHT), 200);
        var context = new ControlContext(Set.of(), "stop", 1, 3, 4, true, history);
        var model = new DemoDecisionModel();
        for (String text : new String[]{"again", "do that again", "repeat that please"}) {
            var result = model.decide(new TranscriptUpdate(text, 2, 4, 1), context).toCompletableFuture().join();
            assertEquals(ActionType.REPEAT_LAST, result.type(), text);
            assertTrue(result.directions().isEmpty());
            assertEquals(0, result.durationMs());
            assertEquals(ActionType.REPEAT_LAST, decide(text).type(), "Java handles missing movement history.");
        }
        assertEquals(ActionType.KEEP_CURRENT,
                model.decide(new TranscriptUpdate("keep going", 2, 4, 1), context).toCompletableFuture().join().type());
    }

    private ActionDecision decide(String text) {
        return new DemoDecisionModel().decide(new TranscriptUpdate(text, 1, 1, 1),
                new ControlContext(Set.of(Direction.RIGHT), "right", 1, 1, 1, true)).toCompletableFuture().join();
    }
}
