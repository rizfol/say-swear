"""Opt-in packaged CLI check. Run gradlew installDist first; live mode needs SemIf ready."""
from pathlib import Path
import argparse
import os
import queue
import re
import subprocess
import threading
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--demo", action="store_true", help="Use the explicit non-AI parser.")
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"), help="Java 21 JDK directory.")
    arguments = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    java = str(Path(arguments.java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if arguments.java_home else "java"
    command = [java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp",
               str(root / "build/install/say-swear/lib/*"), "com.sayswear.app.Main", "--cli", "--quiet-cli"]
    if arguments.demo:
        command.append("--demo")
    process = subprocess.Popen(command, cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, encoding="utf-8")
    lines, transcript = queue.Queue(), []

    def read_output():
        for line in process.stdout:
            transcript.append(line)
            lines.put(line)

    reader = threading.Thread(target=read_output, daemon=True)
    reader.start()

    def until(fragment):
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=max(0.01, deadline - time.monotonic()))
            except queue.Empty:
                break
            if fragment in line:
                return line
        raise AssertionError("Missing output: " + fragment + "\n" + "".join(transcript[-40:]))

    def send(value):
        process.stdin.write(value + "\n")
        process.stdin.flush()

    try:
        until("Exact coordinates are shown above")
        send("a little right")
        until("Accepted: TIMED_PRESS")
        time.sleep(0.35)
        send("/state")
        until("STATE ACTIVE | held: NONE")
        moved = until("position:")
        x = float(re.search(r"position: \(([0-9.]+)", moved).group(1))
        assert 10 < x < 11.5, moved
        send("again")
        until("Accepted: REPEAT_LAST TIMED_PRESS [RIGHT] for 200 ms")
        time.sleep(0.35)
        send("/state")
        until("STATE ACTIVE | held: NONE")
        repeated = until("position:")
        repeated_x = float(re.search(r"position: \(([0-9.]+)", repeated).group(1))
        assert x < repeated_x < x + 1.5, repeated
        send("/restart")
        until("New run.")
        until("STATE ACTIVE | held: NONE")
        restarted = until("position:")
        assert "position: (10.00, 64.00)" in restarted, restarted
        send("again")
        until("No previous movement to repeat. Give a direction first.")
        until("STATE ACTIVE | held: NONE")
        send("/quit")
        assert process.wait(timeout=5) == 0
        print("PASS CLI", "explicit demo" if arguments.demo else "real SemIf",
              "timed movement, expiry, repeat, /state, /restart, cleared repeat history, /quit; moved x=",
              x, "then", repeated_x)
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()
        reader.join(timeout=1)
        evidence = root / ".tools"
        evidence.mkdir(exist_ok=True)
        suffix = "demo" if arguments.demo else "live"
        (evidence / f"cli-smoke-{suffix}.log").write_text("".join(transcript), encoding="utf-8")


if __name__ == "__main__":
    main()
