package com.sayswear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the exit status observed by a shell, outside the JUnit JVM. */
class LauncherProcessTest {
    @TempDir Path temporary;

    @Test void invalidStartupArgumentsReturnFailureToTheShell() throws Exception {
        for (List<String> arguments : List.of(List.of("--unknown-option"),
                List.of("--endpoint"), List.of("--decision-timeout-ms", "invalid"))) {
            Result result = launch(arguments, Map.of(), "");
            assertNotEquals(0, result.exitCode(), () -> "Arguments " + arguments + ": " + result.output());
            assertTrue(result.output().contains("Say Swear:"), result.output());
        }
    }

    @Test void helpAndNormalCliShutdownReturnSuccess() throws Exception {
        Result help = launch(List.of("--help"), Map.of(), "");
        assertEquals(0, help.exitCode(), help.output());
        assertTrue(help.output().contains("--decision-timeout-ms"), help.output());
        Result quit = launch(List.of("--cli", "--demo", "--quiet-cli"), Map.of(), "/quit\n");
        assertEquals(0, quit.exitCode(), quit.output());
        assertTrue(quit.output().contains("DEMO ONLY"), quit.output());
    }

    private Result launch(List<String> arguments, Map<String, String> overrides, String input) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-cp", System.getProperty("sayswear.test.runtimeClasspath"),
                Main.class.getName()));
        command.addAll(arguments);
        Path output = Files.createTempFile(temporary, "launcher-", ".txt");
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        for (String name : List.of("SEMIF_ENDPOINT", "DECISION_TIMEOUT_MS", "SHERPA_MODEL_DIR",
                "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) builder.environment().remove(name);
        builder.environment().putAll(overrides);
        Process process = builder.start();
        try {
            try (var stdin = process.getOutputStream()) { stdin.write(input.getBytes(StandardCharsets.UTF_8)); }
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Launcher did not terminate");
            return new Result(process.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }

    private record Result(int exitCode, String output) { }
}
