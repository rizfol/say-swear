package com.sayswear.view;

import com.sayswear.game.Direction;
import com.sayswear.game.GameEvent;
import com.sayswear.game.GameEventType;
import com.sayswear.game.GameSnapshot;
import com.sayswear.game.Level;
import com.sayswear.game.PlayerStatus;
import com.sayswear.game.Vector2;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CliViewTest {
    @Test
    void mapExposesPlayerStartGoalAndExactSharedState() {
        Level level = Level.standard();
        GameSnapshot snapshot = new GameSnapshot(new Vector2(21.25, 64), new Vector2(7, 0),
                Set.of(Direction.RIGHT), PlayerStatus.ACTIVE, level);

        String output = CliView.formatSnapshot(snapshot);

        assertTrue(output.contains("STATE ACTIVE | held: RIGHT"));
        assertTrue(output.contains("position: (21.25, 64.00) | velocity: (7.00, 0.00)"));
        assertTrue(output.contains("start: (10.00, 64.00) | goal: (100.00, 12.00)"));
        List<String> rows = output.lines().filter(line -> line.startsWith("|")).toList();
        assertEquals(CliView.MAP_ROWS, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.length() == CliView.MAP_COLUMNS + 2));
        assertEquals(1, rows.stream().flatMapToInt(String::chars).filter(c -> c == 'P').count());
        assertEquals(1, rows.stream().flatMapToInt(String::chars).filter(c -> c == 'S').count());
        assertEquals(0, rows.stream().flatMapToInt(String::chars).filter(c -> c == 'C').count());
        assertEquals(1, rows.stream().flatMapToInt(String::chars).filter(c -> c == 'G').count());
        assertTrue(rows.stream().anyMatch(row -> row.contains(".")));
    }

    @Test
    void scriptedModeSuppressesOrdinaryTicksButStillReportsRecovery() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CliView view = view(bytes, new AtomicLong());
        view.setInteractive(false);
        GameSnapshot snapshot = snapshot(PlayerStatus.ACTIVE);
        for (int i = 0; i < 1000; i++) {
            view.onGameEvent(new GameEvent(GameEventType.STATE_CHANGED, snapshot));
        }
        assertEquals("", bytes.toString(StandardCharsets.UTF_8));

        view.onGameEvent(new GameEvent(GameEventType.RESPAWNED, snapshot));
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("STATE ACTIVE"));
        assertTrue(output.contains("Back at the start"));
        assertFalse(output.contains("say>"));
    }

    @Test
    void interactiveTicksAreThrottledButExplicitStateRequestsAlwaysRender() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AtomicLong clock = new AtomicLong();
        CliView view = view(bytes, clock);
        view.setInteractive(true);
        GameEvent event = new GameEvent(GameEventType.STATE_CHANGED, snapshot(PlayerStatus.ACTIVE));

        view.onGameEvent(event);
        int firstLength = bytes.size();
        clock.set(999_999_999L);
        view.onGameEvent(event);
        assertEquals(firstLength, bytes.size());
        clock.set(1_000_000_000L);
        view.onGameEvent(event);
        assertEquals(firstLength * 2, bytes.size());
        view.render(event.snapshot());
        assertEquals(firstLength * 3, bytes.size());
    }

    @Test
    void inputIsReturnedWithoutInterpretationAndEofIsPreserved() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CliView view = new CliView(new PrintStream(bytes, true, StandardCharsets.UTF_8),
                new BufferedReader(new StringReader("right - no, left\n/state\n")));
        view.setInteractive(false);
        assertEquals("right - no, left", view.readCommand());
        assertEquals("/state", view.readCommand());
        assertNull(view.readCommand());
        assertEquals("", bytes.toString(StandardCharsets.UTF_8));
    }

    private static CliView view(ByteArrayOutputStream bytes, AtomicLong clock) {
        return new CliView(new PrintStream(bytes, true, StandardCharsets.UTF_8),
                new BufferedReader(new StringReader("")), clock::get);
    }

    private static GameSnapshot snapshot(PlayerStatus status) {
        Level level = Level.standard();
        return new GameSnapshot(level.start(), Vector2.ZERO, Set.of(), status, level);
    }
}
