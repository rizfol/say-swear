package com.sayswear.game;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LevelTest {
    @Test
    void collisionUsesWholePlayerFootprintAndRoundSegmentEnds() {
        Level level = new Level(List.of(new Vector2(5, 10), new Vector2(25, 10)),
                3, new Vector2(25, 10), 0.7, 30, 20);

        assertTrue(level.isSafe(new Vector2(15, 12.3)));
        assertFalse(level.isSafe(new Vector2(15, 12.31)));
        assertTrue(level.isSafe(new Vector2(3, 10)));
        assertFalse(level.isSafe(new Vector2(2.6, 10)));
        assertTrue(level.isGoal(new Vector2(24, 10)));
        assertFalse(level.isGoal(new Vector2(20, 10)));
    }

    @Test
    void geometryIsDefensivelyCopiedAndCannotBeMutatedThroughSnapshots() {
        List<Vector2> path = new ArrayList<>(List.of(new Vector2(5, 10), new Vector2(25, 10)));
        Level level = new Level(path, 3, new Vector2(25, 10), 0.7, 30, 20);
        path.clear();

        assertEquals(2, level.safePath().size());
        assertThrows(UnsupportedOperationException.class, () -> level.safePath().clear());
    }

    @Test
    void invalidGeometryFailsBeforeAGameStarts() {
        List<Vector2> path = List.of(new Vector2(5, 10), new Vector2(25, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new Level(path, 0.5, new Vector2(25, 10), 0.7, 30, 20));
        assertThrows(IllegalArgumentException.class,
                () -> new Level(path, 3, new Vector2(25, 18), 0.7, 30, 20));
        assertThrows(IllegalArgumentException.class,
                () -> new Level(List.of(new Vector2(5, 10)), 3, new Vector2(5, 10), 0.7, 30, 20));
        assertThrows(IllegalArgumentException.class, () -> new Vector2(Double.NaN, 0));
    }

    @Test
    void standardLevelHasSafeStartAndGoal() {
        Level level = Level.standard();
        assertTrue(level.isSafe(level.start()));
        assertTrue(level.isSafe(level.goal()));
        assertFalse(level.isSafe(new Vector2(1, 1)));
        assertEquals(110, level.width());
        assertEquals(76, level.height());
    }
}
