package com.sayswear.view;

import com.sayswear.game.Direction;
import com.sayswear.game.GameEvent;
import com.sayswear.game.GameEventType;
import com.sayswear.game.GameSnapshot;
import com.sayswear.game.Level;
import com.sayswear.game.Vector2;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.InfoCmp.Capability;
import org.jline.utils.Status;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Terminal presentation only. Reading a line does not advance the simulation;
 * the launcher's input worker submits completed lines to the shared controller.
 */
public final class CliView implements GameView, AutoCloseable {
    public static final int MAP_COLUMNS = 61;
    public static final int MAP_ROWS = 27;
    private static final long FRAME_INTERVAL_NANOS = 1_000_000_000L;
    private static final String PROMPT = "say> ";
    private static final String REFRESH_WIDGET = "say-swear-refresh";
    private static final String SESSION_HELP = "/state /pause /restart /help /quit";
    private static final String MOVEMENT_HELP = "Say: left, stop, other way, up and right, a little left, again.";

    private final PrintStream output;
    private final BufferedReader input;
    private final LongSupplier nanoTime;
    private final Terminal terminal;
    private final Status status;
    private final DashboardReader lineReader;
    private volatile boolean interactive = System.console() != null;
    private volatile boolean closed;
    private volatile GameSnapshot latestSnapshot;
    private volatile String description = "";
    private volatile String latestTranscript = "";
    private volatile String latestMessage = "Type a movement instruction, then press Enter.";
    private boolean reading;
    private long lastRenderNanos = Long.MIN_VALUE;

    public CliView() {
        this(System.out);
    }

    public CliView(PrintStream output) {
        this(output, new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
    }

    public CliView(PrintStream output, BufferedReader input) {
        this(output, input, System::nanoTime);
    }

    CliView(PrintStream output, BufferedReader input, LongSupplier nanoTime) {
        this.output = Objects.requireNonNull(output, "output");
        this.input = Objects.requireNonNull(input, "input");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        terminal = null;
        status = null;
        lineReader = null;
    }

    /** Use native terminal editing only when both standard input and output are real TTYs. */
    public static CliView open(boolean quietCli) throws IOException {
        if (!quietCli) {
            Terminal candidate = null;
            try {
                candidate = TerminalBuilder.builder().name("Say Swear").system(true)
                        .systemOutput(TerminalBuilder.SystemOutput.SysOut).dumb(false).build();
                if (supportsDashboard(candidate)) {
                    CliView live = new CliView(candidate);
                    candidate = null; // The returned view now owns this terminal.
                    return live;
                }
            } catch (IOException | IllegalStateException unavailable) {
                // Redirected input/output and unavailable native terminals use ordinary text.
            } finally {
                if (candidate != null) candidate.close();
            }
        }
        CliView plain = new CliView();
        plain.setInteractive(false);
        return plain;
    }

    /** Stream-backed terminals exercise the same editor/dashboard in headless tests. */
    CliView(Terminal terminal) throws IOException {
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        if (!supportsDashboard(terminal)) throw new IllegalArgumentException("Terminal cannot display a dashboard");
        output = System.out;
        input = null;
        nanoTime = System::nanoTime;
        status = Objects.requireNonNull(Status.getStatus(terminal));
        status.setBorder(false);
        lineReader = new DashboardReader(terminal);
        lineReader.option(LineReader.Option.DISABLE_EVENT_EXPANSION, true);
        lineReader.getWidgets().put(REFRESH_WIDGET, () -> {
            updateDashboard();
            lineReader.callWidget(LineReader.REDISPLAY);
            return true;
        });
        lineReader.getWidgets().put(LineReader.CALLBACK_INIT, () -> {
            updateDashboard();
            return true;
        });
        interactive = true;
    }

    private static boolean supportsDashboard(Terminal terminal) {
        int width = terminal.getWidth(), height = terminal.getHeight();
        return width > 0 && width < 1000 && height > 0 && height < 1000
                && terminal.getStringCapability(Capability.change_scroll_region) != null
                && terminal.getStringCapability(Capability.save_cursor) != null
                && terminal.getStringCapability(Capability.restore_cursor) != null
                && terminal.getStringCapability(Capability.cursor_address) != null;
    }

    public synchronized void setSessionDescription(String value) {
        description = oneLine(value);
        if (lineReader != null) refreshDashboard();
        else {
            output.println("SAY SWEAR — " + description);
            output.flush();
        }
    }

    public synchronized void showHelp() {
        if (lineReader != null) {
            latestMessage = MOVEMENT_HELP;
            refreshDashboard();
        } else {
            output.println(MOVEMENT_HELP);
            output.println("Movement continues while you type. " + SESSION_HELP);
            output.println("/pause is an immediate session stop; ordinary 'stop' goes through the decision model.");
            output.flush();
        }
    }

    /** Scripted input gets explicit states and important events, never periodic maps. */
    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    /** Returns null on EOF. The caller owns the input stream and the input loop. */
    public String readCommand() throws IOException {
        if (closed) return null;
        if (lineReader != null) {
            try { return lineReader.readLine(PROMPT); }
            catch (UserInterruptException interrupted) { return "/pause"; }
            catch (EndOfFileException ended) { return null; }
        }
        synchronized (this) {
            reading = true;
            printPrompt();
        }
        try {
            return input.readLine();
        } finally {
            synchronized (this) {
                reading = false;
            }
        }
    }

    @Override
    public synchronized void render(GameSnapshot snapshot) {
        if (closed) return;
        latestSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        lastRenderNanos = nanoTime.getAsLong();
        if (lineReader != null) {
            refreshDashboard();
            return;
        }
        output.println();
        output.print(formatSnapshot(snapshot));
        if (reading) {
            printPrompt();
        }
        output.flush();
    }

    @Override
    public synchronized void onGameEvent(GameEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.type() == GameEventType.STATE_CHANGED) {
            long now = nanoTime.getAsLong();
            if (interactive && (lastRenderNanos == Long.MIN_VALUE
                    || now - lastRenderNanos >= FRAME_INTERVAL_NANOS)) {
                render(event.snapshot());
            }
            return;
        }
        // Lifecycle events remain visible even when ordinary frames are suppressed.
        GameView.super.onGameEvent(event);
    }

    @Override
    public synchronized void showMessage(String message) {
        if (closed) return;
        latestMessage = oneLine(message);
        if (lineReader != null) { refreshDashboard(); return; }
        output.println("[say] " + latestMessage);
        if (reading) {
            printPrompt();
        }
        output.flush();
    }

    @Override
    public synchronized void showTranscript(String transcript) {
        if (closed) return;
        latestTranscript = oneLine(transcript);
        if (lineReader != null) { refreshDashboard(); return; }
        output.println("[heard] " + latestTranscript);
        if (reading) {
            printPrompt();
        }
        output.flush();
    }

    private void printPrompt() {
        if (interactive) {
            output.print(PROMPT);
            output.flush();
        }
    }

    private void refreshDashboard() {
        if (closed) return;
        try { lineReader.callWidget(REFRESH_WIDGET); }
        catch (IllegalStateException betweenReads) {
            // Latest immutable state is retained; CALLBACK_INIT paints it at the next read.
        }
    }

    /** Called only while DashboardReader holds its editor lock. */
    private void updateDashboard() {
        if (closed) return;
        int width = terminal.getWidth(), height = terminal.getHeight();
        if (width < 1 || width >= 1000 || height < 2 || height >= 1000) {
            status.hide();
            return;
        }
        status.resize();
        int available = Math.max(1, height - 3);
        List<String> lines = new ArrayList<>();
        lines.add("SAY SWEAR" + (description.isEmpty() ? "" : " — " + description));
        GameSnapshot snapshot = latestSnapshot;
        if (snapshot != null) {
            String held = Arrays.stream(Direction.values()).filter(snapshot.heldDirections()::contains)
                    .map(Enum::name).collect(Collectors.joining(" + "));
            lines.add("STATE " + snapshot.status() + " | held: " + (held.isEmpty() ? "NONE" : held));
            lines.add(String.format(Locale.ROOT, "position: (%.2f, %.2f) | velocity: (%.2f, %.2f)",
                    snapshot.position().x(), snapshot.position().y(), snapshot.velocity().x(), snapshot.velocity().y()));
        }
        lines.add("[heard] " + latestTranscript);
        lines.add("[say] " + latestMessage);
        lines.add(SESSION_HELP);
        int mapRows = Math.min(MAP_ROWS, available - lines.size() - 3);
        int mapColumns = Math.min(MAP_COLUMNS, width - 2);
        if (snapshot != null && mapRows >= 3 && mapColumns >= 9) {
            String border = "+" + "-".repeat(mapColumns) + "+";
            lines.add(border);
            for (char[] row : buildMap(snapshot, mapColumns, mapRows)) lines.add("|" + new String(row) + "|");
            lines.add(border);
            lines.add("P player | . safe path | S start | G goal");
        }
        List<AttributedString> clipped = lines.stream().limit(available)
                .map(AttributedString::new).map(line -> line.columnSubSequence(0, width)).toList();
        status.update(clipped);
    }

    /** Fit the status before JLine redraws after a resize, retaining its editable buffer. */
    private final class DashboardReader extends LineReaderImpl {
        DashboardReader(Terminal terminal) throws IOException { super(terminal); }

        @Override protected synchronized void handleSignal(Terminal.Signal signal) {
            lock.lock();
            try {
                if (signal == Terminal.Signal.WINCH) updateDashboard();
                super.handleSignal(signal);
            } finally { lock.unlock(); }
        }

        void closeDashboard() throws IOException {
            lock.lock();
            try {
                status.hide();
                status.close();
                terminal.close();
            } finally { lock.unlock(); }
        }
    }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        if (lineReader != null) lineReader.closeDashboard();
        // Plain streams belong to the caller (usually System.in/System.out).
    }

    /** A deterministic textual projection of the same immutable snapshot as the GUI. */
    public static String formatSnapshot(GameSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Level level = snapshot.level();
        char[][] map = buildMap(snapshot, MAP_COLUMNS, MAP_ROWS);

        String held = Arrays.stream(Direction.values()).filter(snapshot.heldDirections()::contains)
                .map(Enum::name).collect(Collectors.joining(" + "));
        if (held.isEmpty()) {
            held = "NONE";
        }
        StringBuilder result = new StringBuilder(2300);
        result.append("STATE ").append(snapshot.status()).append(" | held: ").append(held).append('\n');
        result.append(String.format(Locale.ROOT,
                "position: (%.2f, %.2f) | velocity: (%.2f, %.2f)%n",
                snapshot.position().x(), snapshot.position().y(),
                snapshot.velocity().x(), snapshot.velocity().y()));
        result.append(String.format(Locale.ROOT,
                "start: (%.2f, %.2f) | goal: (%.2f, %.2f)%n",
                level.start().x(), level.start().y(),
                level.goal().x(), level.goal().y()));
        String border = "+" + "-".repeat(MAP_COLUMNS) + "+\n";
        result.append(border);
        for (char[] row : map) {
            result.append('|').append(row).append("|\n");
        }
        result.append(border);
        result.append("P player | . safe center positions | G goal | S start\n");
        result.append("Up decreases y; right increases x. Exact coordinates are shown above.\n");
        return result.toString();
    }

    private static char[][] buildMap(GameSnapshot snapshot, int columns, int rows) {
        Level level = snapshot.level();
        char[][] map = new char[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                Vector2 sample = new Vector2(column * level.width() / Math.max(1, columns - 1),
                        row * level.height() / Math.max(1, rows - 1));
                map[row][column] = level.isSafe(sample) ? '.' : ' ';
            }
        }
        mark(map, level.start(), level, 'S');
        mark(map, level.goal(), level, 'G');
        mark(map, snapshot.position(), level, 'P');
        return map;
    }

    private static void mark(char[][] map, Vector2 position, Level level, char symbol) {
        int column = (int) Math.round(position.x() / level.width() * (map[0].length - 1));
        int row = (int) Math.round(position.y() / level.height() * (map.length - 1));
        if (row >= 0 && row < map.length && column >= 0 && column < map[0].length) {
            map[row][column] = symbol;
        }
    }

    private static String oneLine(String value) {
        return Objects.requireNonNullElse(value, "").replaceAll("[\\p{Cc}\\p{Cf}]", " ");
    }
}
