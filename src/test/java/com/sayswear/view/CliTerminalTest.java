package com.sayswear.view;

import com.sayswear.game.Direction;
import com.sayswear.game.GameSnapshot;
import com.sayswear.game.Level;
import com.sayswear.game.PlayerStatus;
import com.sayswear.game.Vector2;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ArrayTerminalDataStream;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.TerminalOutputStream;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.model.TerminalTextBuffer;
import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real JLine editing and interprets its ANSI output into screen cells. */
@Timeout(15)
class CliTerminalTest {
    @Test
    void backgroundFramesAndMessagesPreserveTheVisiblePartialCommand() throws Exception {
        try (Harness terminal = new Harness(80, 24)) {
            terminal.view.render(snapshot(10));
            Future<String> command = terminal.read();
            terminal.screen.await(text -> text.contains("say> "));
            terminal.type("a little ri");
            String before = terminal.screen.await(text -> text.contains("say> a little ri"));
            int promptRow = rowContaining(before, "say> a little ri");

            CompletableFuture.runAsync(() -> {
                for (int i = 0; i < 20; i++) {
                    terminal.view.render(snapshot(10 + i / 10.0));
                    terminal.view.showTranscript("background speech " + i);
                    terminal.view.showMessage("background update " + i);
                }
            }).get(5, TimeUnit.SECONDS);

            String after = terminal.screen.await(text -> text.contains("background update 19"));
            assertEquals(1, occurrences(after, "say> "), after);
            assertTrue(after.contains("say> a little ri"), after);
            assertEquals(promptRow, rowContaining(after, "say> a little ri"),
                    "Refreshing the game must not scroll the input line");
            assertFalse(command.isDone(), "Background rendering must not submit the partial line");

            terminal.type("ght\r");
            assertEquals("a little right", command.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void resizingIncludingAShortNarrowTerminalKeepsTheLineEditable() throws Exception {
        try (Harness terminal = new Harness(80, 24)) {
            terminal.view.render(snapshot(10));
            Future<String> command = terminal.read();
            terminal.screen.await(text -> text.contains("say> "));
            terminal.type("right no le");
            terminal.screen.await(text -> text.contains("say> right no le"));

            for (Size size : new Size[] {new Size(60, 18), new Size(24, 5), new Size(100, 30)}) {
                terminal.resize(size);
                terminal.view.render(snapshot(12.25));
                terminal.view.showMessage("resized");
                String screen = terminal.screen.await(text -> text.contains("say> right no le"));
                assertEquals(1, occurrences(screen, "say> "), screen);
                assertEquals(size.getRows(), screen.lines().count(), screen);
                assertTrue(screen.lines().allMatch(line -> line.length() <= size.getColumns()), screen);
                assertFalse(command.isDone());
            }

            // Backspace is interpreted by LineReader, not passed to the command agent.
            terminal.type("x\u007fft\r");
            assertEquals("right no left", command.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void interruptionEofAndCloseRestoreUsableTerminalState() throws Exception {
        try (Harness terminal = new Harness(80, 24)) {
            Attributes initial = terminal.terminal.getAttributes();
            terminal.view.render(snapshot(10));
            Future<String> interrupted = terminal.read();
            terminal.screen.await(text -> text.contains("say> "));
            terminal.type("left");
            terminal.screen.await(text -> text.contains("say> left"));
            terminal.type("\u0003");
            assertEquals("/pause", interrupted.get(5, TimeUnit.SECONDS));
            assertEquals(initial.getLocalFlags(), terminal.terminal.getAttributes().getLocalFlags(),
                    "LineReader must restore canonical/echo flags after interruption");

            long previousFlush = terminal.screen.flushCount();
            Future<String> eof = terminal.read();
            terminal.screen.await(text -> terminal.screen.flushCount() > previousFlush
                    && text.contains("say> ")
                    && !terminal.terminal.getAttributes().getLocalFlag(Attributes.LocalFlag.ICANON));
            terminal.type("\u0004");
            assertNull(eof.get(5, TimeUnit.SECONDS));
            terminal.view.close();

            // A bounded dashboard leaves a restricted scrolling region while active.
            // After close, ordinary output must be able to reach the physical last row.
            terminal.screen.writeDirect("\u001b[H");
            for (int i = 0; i < 24; i++) terminal.screen.writeDirect("restored " + i + "\r\n");
            terminal.screen.writeDirect("shell-ready");
            String restored = terminal.screen.text();
            assertEquals(23, rowContaining(restored, "shell-ready"), restored);
        }
    }

    private static GameSnapshot snapshot(double x) {
        return new GameSnapshot(new Vector2(x, 64), Vector2.ZERO, Set.of(Direction.RIGHT),
                PlayerStatus.ACTIVE, Level.standard());
    }

    private static int occurrences(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }

    private static int rowContaining(String screen, String text) {
        String[] rows = screen.split("\n", -1);
        for (int i = 0; i < rows.length; i++) if (rows[i].contains(text)) return i;
        return -1;
    }

    private static final class Harness implements AutoCloseable {
        final PipedInputStream input = new PipedInputStream(8192);
        final PipedOutputStream keyboard = new PipedOutputStream(input);
        final ScreenOutput screen;
        final Terminal terminal;
        final CliView view;
        final ExecutorService inputWorker = Executors.newSingleThreadExecutor();

        Harness(int columns, int rows) throws IOException {
            screen = new ScreenOutput(columns, rows, keyboard);
            terminal = TerminalBuilder.builder().name("say-swear-test").system(false)
                    .type("xterm-256color").encoding(StandardCharsets.UTF_8)
                    .streams(input, screen).size(new Size(columns, rows)).build();
            view = new CliView(terminal);
        }

        Future<String> read() { return inputWorker.submit(view::readCommand); }

        void type(String text) throws IOException {
            keyboard.write(text.getBytes(StandardCharsets.UTF_8));
            keyboard.flush();
        }

        void resize(Size size) {
            screen.resize(size);
            terminal.setSize(size);
            terminal.raise(Terminal.Signal.WINCH);
        }

        @Override public void close() throws Exception {
            try { view.close(); }
            finally {
                keyboard.close();
                inputWorker.shutdownNow();
                assertTrue(inputWorker.awaitTermination(3, TimeUnit.SECONDS), "Input worker did not terminate");
            }
        }
    }

    /**
     * JediTerm is independent of the JLine code under test. JLine 3.30.16's own
     * ScreenTerminal clamps LF below the scrolling region back into that region,
     * which corrupts a valid Status dashboard. JediTerm implements the physical
     * screen boundary correctly, so application output needs no test-only rewrite.
     */
    private static final class ScreenOutput extends OutputStream {
        private final TerminalTextBuffer buffer;
        private final JediTerminal screen;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        private final StringBuilder trace = new StringBuilder();
        private long flushCount;

        ScreenOutput(int columns, int rows, OutputStream terminalReplies) {
            StyleState style = new StyleState();
            buffer = new TerminalTextBuffer(columns, rows, style, 100);
            screen = new JediTerminal(new HeadlessDisplay(), buffer, style);
            screen.setTerminalOutput(new TerminalOutputStream() {
                @Override public void sendBytes(byte[] bytes, boolean userInput) {
                    try {
                        terminalReplies.write(bytes);
                        terminalReplies.flush();
                    } catch (IOException exception) { throw new UncheckedIOException(exception); }
                }

                @Override public void sendString(String value, boolean userInput) {
                    sendBytes(value.getBytes(StandardCharsets.UTF_8), userInput);
                }
            });
        }

        @Override public synchronized void write(int value) { pending.write(value); }

        @Override public synchronized void write(byte[] bytes, int offset, int length) {
            pending.write(bytes, offset, length);
        }

        @Override public synchronized void flush() throws IOException {
            String emitted = pending.toString(StandardCharsets.UTF_8);
            trace.append(emitted);
            writeDirect(emitted);
            pending.reset();
            flushCount++;
            notifyAll();
        }

        synchronized void resize(Size size) {
            screen.resize(new TermSize(size.getColumns(), size.getRows()), RequestOrigin.User);
            notifyAll();
        }

        synchronized void writeDirect(String value) throws IOException {
            JediEmulator emulator = new JediEmulator(new ArrayTerminalDataStream(value.toCharArray()), screen);
            while (emulator.hasNext()) emulator.next();
        }

        synchronized String text() { return buffer.getScreenLines(); }

        synchronized long flushCount() { return flushCount; }

        synchronized String await(Predicate<String> condition) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!condition.test(text())) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) fail("Terminal condition was not reached. Screen:\n" + text()
                        + "\nRecent ANSI:\n" + trace.substring(Math.max(0, trace.length() - 5000))
                        .replace("\u001b", "<ESC>").replace("\r", "<CR>").replace("\n", "<LF>\n"));
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
            return text();
        }
    }

    /** Screen state lives in TerminalTextBuffer; this fixture creates no native window. */
    private static final class HeadlessDisplay implements TerminalDisplay {
        @Override public void setCursor(int x, int y) { }
        @Override public void setCursorShape(CursorShape shape) { }
        @Override public void beep() { }
        @Override public void scrollArea(int top, int size, int delta) { }
        @Override public void setCursorVisible(boolean visible) { }
        @Override public void useAlternateScreenBuffer(boolean enabled) { }
        @Override public String getWindowTitle() { return ""; }
        @Override public void setWindowTitle(String title) { }
        @Override public TerminalSelection getSelection() { return null; }
        @Override public void terminalMouseModeSet(MouseMode mode) { }
        @Override public void setMouseFormat(MouseFormat format) { }
        @Override public boolean ambiguousCharsAreDoubleWidth() { return false; }
    }
}
