# Stage 2 Validation Record

This record separates executed checks from outstanding validation. Results below were obtained on Windows on September 30, 2026, using Java 21, Gradle 8.14.2, and the shared Java application. The [feature mapping](feature-implementation-mapping.md) identifies the corresponding implementation files.

## Latest change — in-place CLI display

The user reported that repeated maps scrolled the terminal while typing. D19/C15 introduce JLine **3.30.16**, a fixed bottom `Status` game panel, a separate `LineReader` prompt, and a map fitted to the terminal size. The Java 21 distribution includes the JNI provider. The Windows launcher now builds with `installDist` and invokes the installed application directly; the README documents the equivalent Linux/macOS launch. Quiet, redirected, and unsupported-terminal input/output use the existing plain state/event path without periodic maps.

| Check | Actual result | Scope and limit |
|---|---|---|
| Full build and distribution installation | **76 ordinary Java tests passed**, with zero failures, errors, or skipped tests; `build` and `installDist` passed. | The earlier 73-test suite plus the three terminal integration tests. No live model or microphone is required for this suite. |
| CLI unit and terminal integration tests | **Seven passed:** four existing [plain-output tests](../src/test/java/com/sayswear/view/CliViewTest.java) and three [terminal tests](../src/test/java/com/sayswear/view/CliTerminalTest.java). | Real JLine input/display behavior was interpreted by the independent JediTerm screen emulator. Twenty background snapshot/transcript/message updates preserved the prompt row and partial command. Resize checks covered 80×24 → 60×18 → 24×5 → 100×30, including backspace; Ctrl+C pause, EOF, terminal attributes, and scroll-region restoration passed. This is not a native terminal visual check. |
| Packaged demo CLI | **Passed:** timed movement/repeat expired at x = **11.37 → 12.70**; restart, no-history feedback, and quit passed. | Exercises the existing `--quiet-cli` application path and shared core with the explicitly selected demo model. |
| Automatically detected piped input/output | **Passed:** no escape sequences or ordinary periodic frames; explicit state and quit worked without `--quiet-cli`. | Verifies plain fallback for redirected streams. |
| Native Windows terminal | **Passed** against the packaged `--cli --demo` application in an 80×24 Windows ConPTY. JLine selected JNI `NativeWinSysTerminal` / `windows-vtp`. | Typed `a little ri`, waited two seconds through periodic refresh, then completed `ght`: the intact instruction produced RIGHT for 200 ms, moved x = 10 → 11.33, and released. `again` moved to x = 12.67 and released. Pause, restart to (10,64), and quit passed with exit 0. Output used fixed cursor rows; shutdown cleared the panel, disabled bracketed paste, and restored a usable console. This was a native process/input/output check, not human visual inspection. |
| Actual Windows launcher with scripted input | **Passed:** `powershell -NoProfile -ExecutionPolicy Bypass -File run.ps1 -Mode cli -Demo -QuietCli`, fed `/state` and `/quit` through a pipe. | Exit 0, exactly two `STATE ACTIVE` snapshots (initial and requested), and no ANSI escapes. Confirms that the build step does not consume piped commands and the installed `.bat` launcher works. PowerShell syntax and `git diff --check` also passed. |

**UML follow-up (C16):** reviewed all seven current diagrams against the implementation. Updated the class diagram for `CliView` terminal ownership and lifecycle, SD02 for fixed-panel rendering/editable input/plain fallback, and SD05 for conditional ordinary refresh versus unconditional lifecycle refresh. Regenerated those three SVGs; the other four already matched. All seven sources passed PlantUML 1.2026.8 `-checkonly`, and all seven SVG files parsed as XML. The class diagram now measures **3905 × 2283**; preview inspection and SVG geometry checks found no overlapping entity rectangles or out-of-box member text across its **41 entities**. All **14 archived Stage 1 files** remain SHA-256-identical to commit `b7bfad5`. This follow-up changes documentation only; the application results above remain the latest executed tests.

An initial test used JLine's `ScreenTerminal` emulator, whose cursor-down handling below a scroll margin distorted the screen. The test harness now uses **JediTerm 3.76**, resolved from the official JetBrains repository as a **test-only** dependency. Production remains JLine 3.30.16.

The test tool initially supplied `TERM=dumb`, so the first native attempt correctly used plain fallback. A process-local diagnostic wrapper cleared that variable for the ConPTY check; no user or system environment setting was changed. The application continues to honor `TERM=dumb` as an unsupported terminal.

Native Linux/macOS interaction and human visual/usability review remain unverified. Earlier sections below remain historical evidence.

## Earlier change — repeat memory and current UML

`MovementIntent` remembers accepted `PRESS`, `SWITCH`, or `TIMED_PRESS` type/directions/duration separately from currently held inputs and previous command text. A fresh `REPEAT_LAST` asks deterministic Java controls to create a new command from that memory. Timed repeats retain the original 100/200/400 ms duration after expiry. At most one replay executes per utterance; duplicate partials do not restart a timer, even after an intervening interpretation. Missing history performs no commands and displays “No previous movement to repeat. Give a direction first.”

Automatic timer release, `NO_ACTION`, `KEEP_CURRENT`, and ordinary stop/release retain movement memory. Reset, `/pause`, GUI **Stop & release**, fall, restart, and current input error clear it. The model receives structured memory and selects among 52 actions/eight types. An already-running bridge needs restarting to load the new schema.

**Repeat verification:** the following checks passed after the change. [D18](design-changes.md) and [C14](ai-collaboration-log.md) record the work. All seven current diagram sources rendered to matching SVGs; the source boundaries and actual-method references were checked. Original Stage 1 diagrams are archived under [uml/stage1/](uml/stage1/) and current files remain under [uml/](uml/).

| Check | Actual result | Scope and limit |
|---|---|---|
| Full build and distribution installation | **73 ordinary Java tests passed**, with zero failures, errors, or skipped tests; `installDist` passed. | Repeat memory, no-history behavior, timer replay, lifecycle clearing, same-utterance suppression, and request context are covered alongside existing behavior. |
| SemIf bridge contracts | **Nine tests passed.** | Controlled scores verify the 52-action schema, structured memory, repeat mapping, and existing bridge behavior; they do not measure real-model accuracy. |
| Packaged demo CLI | **Passed:** `a little right` expired at x = **11.35**; `again` replayed and expired at x = **12.67**. Restart followed by `again` produced the no-history message and neutral state; quit passed. | Actual packaged application and shared controls using the explicitly selected deterministic demo model. |
| Genuine SemIf/Qwen CPU semantics | **20 unchanged baseline cases and eight repeat/history cases passed.** Baseline requests took **828.8–2293.3 ms**; repeat/history requests took **822.9–883.2 ms**. | Owned loopback service on port **8766**, CPU inference, **60000 ms diagnostic cap**. Includes repeat requests with no history. These are semantic samples, not validation of the default 2000 ms gameplay budget. |
| Packaged CLI with genuine model | **Passed:** movement/repeat expired at x = **11.36 → 12.67**; restart, no-history message, neutral controls, and quit passed. | Actual Java → SemIf/Qwen → game path against the CPU service, using **DECISION_TIMEOUT_MS=30000**. This is not a default-budget performance pass. |

Ordinary test counts after repeat support and before the CLI display change: `DemoDecisionModelTest` **3**, `OpenJevAdapterTest` **4**, `VoiceCommandAgentTest` **5**, `ApplicationControllerTest` **10**, `ActionExecutorTest` **6**, `ControlManagerTest` **22**, `GameModelTest` **9**, `LevelTest` **4**, `CliViewTest` **4**, and `VoiceInputServiceTest` **6**; total **73**. The earlier 63-test table below remains historical evidence.

**Diagram verification:** the existing PlantUML **1.2026.8** JAR rendered all seven current `.puml` files to their matching `.svg` paths. All `@startuml`/`@enduml` boundaries and actual Java method references were checked. The final class diagram is **3905 × 2235**; its rendered PNG preview was visually inspected with no visible class/member overlap. An XML geometry check on the actual SVG found **41 entity rectangles**, no pairwise rectangle overlap, and all class text within its containing rectangle. All **14 archived original source/SVG files** match the originals by SHA-256. Current source line endings were normalized to LF, and `git diff --check` passed. Browser preview could not start because the computer-use runtime assets were missing (OS error 3); no browser visual inspection is claimed.

The updated **2000 ms GPU burst gate has not been rerun**. Restart the existing model bridge to load the new schema before checking that budget in normal play. The user's existing GPU service on port 8765 was left untouched; the separate CPU service on port 8766 was task-owned and was stopped after verification. Earlier GPU model/burst timings below describe the pre-repeat implementation and must not be read as current repeat-performance results.

## Earlier change — every fall returns to the start

The user requested removal of checkpoints after the checks recorded below. F10 is now **Fall Detection and Start Recovery**: `GameModel.resetToStart()` returns to `Level.start()`, clears held inputs and velocity, and retains cancellation of timers and stale decisions. Checkpoint data, activation events, and GUI/CLI progress markers are removed. [D17](design-changes.md) and [C12](ai-collaboration-log.md) record the change; the original UC06/SD05 and Stage 1 report/UML remain historical references.

**Regression status:** verification after checkpoint removal passed on September 30, 2026:

| Check | Command/setup | Actual result and scope |
|---|---|---|
| Full Java build | `./run.ps1 -Mode build` | **Passed: 63 tests, zero failures, errors, or skipped tests.** The updated game test progresses along the actual standard level through former checkpoint turns, falls twice, and verifies both recoveries return to the start with neutral controls. The controller test also progresses before falling, verifies the start position, and rejects a stale response. |
| JavaFX desktop regression | Set `SAY_SWEAR_GUI_SMOKE=1`; `./gradlew.bat guiSmokeTest` | **One passed.** Assistant inspection of the new scene capture confirmed no checkpoint markers/metric, visible start/goal markers, and a fall-to-start explanation. Actual text submission, Stop & release, and Restart run passed using the explicit demo model. |
| Distribution and packaged demo CLI | `./gradlew.bat installDist`; `python scripts/check-cli.py --java-home '<Java21path>' --demo` | **Passed.** Timed movement reached x = **11.36**; expiry, state, restart, and quit checks passed. The updated CLI uses start/goal information without checkpoint output. |

The ordinary test-class counts remain the same as the earlier 63-test table below. A source scan found no checkpoint references in `src/`, and `git diff --check` passed. Agent/model behavior was unchanged by this gameplay revision; the earlier genuine-model results remain recorded below and were not rerun for checkpoint removal. Microphone gameplay and student usability review remain outstanding.

## Executed checks before checkpoint removal

| Check | Command/setup | Actual result | Scope |
|---|---|---|---|
| Full Java build and ordinary tests | `powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Mode build` | **Build passed; 63 tests passed with zero failures, errors, or skipped tests.** Distribution ZIP and TAR were generated. | Game/control/controller, CLI, demo parser, agent scheduling, HTTP adapter, and fake-hardware voice lifecycle. Detailed counts appear below. |
| SemIf bridge contract tests | `.\.tools\semif-venv\Scripts\python.exe -m unittest discover -s tools/semif -p test_server.py` | **Seven passed.** | [Bridge tests](../tools/semif/test_server.py) check factored choices, word-boundary formatting, stop/ambiguity, schema/world-state rejection, deadlines, distribution validity, and loopback HTTP using controlled scores. They do not establish model accuracy. |
| Packaged CLI demonstration path | `python scripts/check-cli.py --java-home '<Java21path>' --demo` | **Passed.** Timed movement reached x = 11.32; timer expiry, state output, restart, and quit checks passed. | [CLI check](../scripts/check-cli.py) launches the packaged application and exercises its actual shared core with the explicitly selected deterministic demo interpreter. This is not a real-model result. |
| Packaged CLI with genuine model | With SemIf ready: `python scripts/check-cli.py --java-home '<Java21path>'` | **Passed.** Timed movement reached x = 11.34; timer expiry, state output, restart, and quit checks passed. | Actual packaged `Main` → controller/agent → Java adapter → SemIf/Qwen → deterministic controls/game, using the 2000 ms application budget. No demo fallback or microphone was used. |
| JavaFX desktop smoke | Set `SAY_SWEAR_GUI_SMOKE=1`; `./gradlew.bat guiSmokeTest` | **One passed.** The running JavaFX scene was also visually inspected by the development assistant. | Actual text-field submission of `right` reached the shared controller and moved the player. Stop & release cleared held directions and velocity; Restart run restored the start state. This used the explicit deterministic demo model. |
| Native streaming ASR | Set `SHERPA_MODEL_DIR`; `./gradlew.bat asrSmokeTest` | **One passed.** The 6.63-second official sample WAV produced **17 partial transcripts and one endpoint**, matched the expected phrases, and produced no error callbacks. | Actual sherpa-onnx 1.13.5 native/model loading and incremental decoding. No microphone was opened. |
| Genuine local decision model | Start SemIf; set `SAY_SWEAR_LIVE_SEMIF=1`; `./gradlew.bat liveSemIfTest` | **Two JUnit methods passed:** 20 isolated cases plus the burst below. Isolated requests took 242.2–503.1 ms. | Actual Java `OpenJevAdapter` → SemIf → Qwen3.5 4B. Isolated cases use a 15-second diagnostic deadline; this differs from the application's budget. |
| Genuine agent burst, in the same live suite | Four same-utterance partials 180 ms apart, with a 2000 ms queue-inclusive budget | **Passed.** The latest correction returned `SWITCH LEFT`, version 4, after **990.7 ms**. | Actual `VoiceCommandAgent` queue/replacement/deadline behavior with real inference. The test requires a valid latest result at the default budget. |
| ASR setup repeatability | `powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/setup-asr.ps1`, initial run and cached rerun | **Passed.** Official archive downloaded/extracted; cached archive passed pinned SHA-256 verification and was reused. PowerShell syntax check also passed. | Reproducible model setup; archive and extracted files remain outside version control. See [ASR setup](asr-setup.md). |
| Documented SemIf launcher | `./scripts/run-semif.ps1`, then request `http://127.0.0.1:8765/health` | **Passed.** Correct SemIf/Qwen backend/model readiness arrived in about nine seconds. | Actual documented launcher and native runtime. Task-owned service processes were stopped after verification; no validation service was left listening. |

The desktop capture is an ignored development artifact under `.tools/`; it is not a generated UML diagram or a replacement for human interface review. Generated test XML/HTML is under ignored `build/test-results/` and `build/reports/tests/` and can be regenerated with the commands above.

### Ordinary Java test counts before checkpoint removal

| Test class | Passed |
|---|---:|
| [DemoDecisionModelTest](../src/test/java/com/sayswear/agent/DemoDecisionModelTest.java) | 2 |
| [OpenJevAdapterTest](../src/test/java/com/sayswear/agent/OpenJevAdapterTest.java) | 3 |
| [VoiceCommandAgentTest](../src/test/java/com/sayswear/agent/VoiceCommandAgentTest.java) | 4 |
| [ApplicationControllerTest](../src/test/java/com/sayswear/app/ApplicationControllerTest.java) | 8 |
| [ActionExecutorTest](../src/test/java/com/sayswear/control/ActionExecutorTest.java) | 6 |
| [ControlManagerTest](../src/test/java/com/sayswear/control/ControlManagerTest.java) | 17 |
| [GameModelTest](../src/test/java/com/sayswear/game/GameModelTest.java) | 9 |
| [LevelTest](../src/test/java/com/sayswear/game/LevelTest.java) | 4 |
| [CliViewTest](../src/test/java/com/sayswear/view/CliViewTest.java) | 4 |
| [VoiceInputServiceTest](../src/test/java/com/sayswear/voice/VoiceInputServiceTest.java) | 6 |
| **Total** | **63** |

Packaging generated `build/distributions/say-swear-2.0.0.zip` and `build/distributions/say-swear-2.0.0.tar`. A subsequent complete build rerun after controller-status and launch-configuration adjustments also passed all 63 ordinary tests. Packaging does not imply that downloaded models are included or that every model instruction is interpreted correctly.

### Reproduce the packaged CLI check

With Java 21 selected for Gradle and the real SemIf service ready:

```powershell
.\gradlew.bat installDist
python scripts/check-cli.py --java-home '<Java21path>'
```

Replace `<Java21path>` with the installed JDK 21 directory. Append `--demo` to the Python command to test the explicit deterministic demonstration path without a model service. The check exercises the packaged CLI and actual shared game/controller; it never opens a microphone.

## Genuine GPU decision-model results before repeat support

The Java [OpenJevAdapter](../src/main/java/com/sayswear/agent/OpenJevAdapter.java) and local [SemIf bridge](../tools/semif/server.py) successfully used SemIf commit `23cf1f39fc9534fe81437200959b6dfc7106e45a` with Qwen3.5 4B Q4_K_M on an RTX 2070 SUPER 8 GB, with 34/34 layers offloaded. The host CPU is a Ryzen 7 5700X. [LiveSemIfTest](../src/test/java/com/sayswear/agent/LiveSemIfTest.java) sent the following isolated sequential requests through the actual Java HTTP adapter:

| Input | Initially held | Actual returned action | Observed elapsed time |
|---|---|---|---:|
| `left` | None | `PRESS LEFT` | 404.2 ms |
| `right` | None | `PRESS RIGHT` | 403.1 ms |
| `up` | None | `PRESS UP` | 403.0 ms |
| `down` | None | `PRESS DOWN` | 402.5 ms |
| `up and left` | None | `PRESS UP+LEFT` | 405.2 ms |
| `up and right` | None | `PRESS UP+RIGHT` | 405.7 ms |
| `down and left` | None | `PRESS DOWN+LEFT` | 407.2 ms |
| `down and right` | None | `PRESS DOWN+RIGHT` | 406.3 ms |
| `a little left` | None | `TIMED_PRESS LEFT`, 200 ms | 502.6 ms |
| `a little right` | None | `TIMED_PRESS RIGHT`, 200 ms | 500.9 ms |
| `a little up` | None | `TIMED_PRESS UP`, 200 ms | 501.2 ms |
| `a little down` | None | `TIMED_PRESS DOWN`, 200 ms | 503.1 ms |
| `other way` | `RIGHT` | `SWITCH LEFT` | 405.2 ms |
| `other way` | `UP+RIGHT` | `SWITCH DOWN+LEFT` | 407.5 ms |
| `other way` | None | `NO_ACTION` | 406.9 ms |
| `keep going` | `UP` | `KEEP_CURRENT` | 242.2 ms |
| `right, no, left` | `RIGHT` | `SWITCH LEFT` | 406.2 ms |
| `stop` | `RIGHT` | `RELEASE_ALL` | 243.3 ms |
| `solve the level and navigate to the goal` | None | `NO_ACTION` | 242.7 ms |
| `banana soup` | None | `NO_ACTION` | 242.4 ms |

All 20 returned the intended bounded semantics in the final run. These are measured samples, not a reliability or latency guarantee. The isolated-case test set a **15-second diagnostic request deadline**; the separate burst check below includes queue time within the **2000 ms application budget**. The packaged CLI check above additionally exercised real model decisions through the actual game/controller.

### Real inference under a burst of partials

The agent received `right`, `right no`, `right no left`, and `right no left please` from one utterance, each 180 ms apart. In the final run, the third pending input was replaced and cancelled after **189.8 ms**. The first returned `PRESS RIGHT` after **765.4 ms**, the incomplete second returned `NO_ACTION` after **967.4 ms**, and the latest returned `SWITCH LEFT` with transcript version 4 after **990.7 ms**. Times are measured from each request's submission, including its queue wait.

The application default is **2000 ms**. This is an upper bound, not a forced two-second delay. The final burst test requires the latest valid correction within that budget. Controller/control-manager stale-result rejection is independently tested; the agent-level burst does not apply earlier returned proposals to a game.

An earlier run with the original **1000 ms** budget expired: its second/latest requests timed out after about 1001.8/1000.3 ms and returned no actionable result. That evidence motivated the larger queue-inclusive default. It is retained as a successful fail-closed observation, not a successful correction.

### Failures and corrections retained in the record

1. The first native attempt failed with Windows illegal-instruction exit `0xc000001d` after CUDA offload. Its CPU library required AVX512, while this host supports AVX2. A separate project-local overlay now combines the compatible CPU DLL from the **same-version official portable wheel** with unchanged CUDA runtime libraries. The installed dependency remains intact; hashes/provenance and setup are documented in [SemIf setup](semif-setup.md) and [configure-semif-native.py](../scripts/configure-semif-native.py).
2. Earlier genuine attempts misinterpreted relative and combined directions. An initial question/option refinement passed eight samples, but broader CLI/model checks then exposed timed right/down and bare up/down errors, including `NO_ACTION` for `a little right`. The pre-repeat bridge used genuine upstream **full-prompt scoring** with seven action types, separate vertical/horizontal three-option readouts, and an optional three-option duration readout: **one to four model readouts** per command. The expanded cases above covered these directions. Repeat support later added the eighth type without changing this separation of interpretation and execution.
3. Direction examples and richer paraphrase options were also tried; some contaminated an unmentioned axis or produced `NO_ACTION`. Those regressions were discarded. The retained minimal axis questions contain the transcript and initially held direction for that axis; the model chooses the axis result.
4. CLI refresh originally relied on `System.console()`, but Gradle forwards standard input even for an interactive human terminal, leaving that value null. The first fix enabled about 1 Hz output by default and added explicit `--quiet-cli`. Its append-only maps still disrupted typing; D19 subsequently replaces that display with a fixed game panel and direct terminal launch, retaining plain nonperiodic fallback output.
5. An intermediate 20-case run passed but still misread unpunctuated `right no left please` as `RIGHT` during a burst after 1091.7 ms. The retained fix presents **only Qwen-selected `SWITCH` axis input** as comma-separated words, splitting existing commas/whitespace first so formatting is idempotent. The original transcript remains unchanged in Java, the request, and action-type scoring. This generic presentation step neither identifies directions nor parses correction intent. Qwen still chooses each axis through logits; no parser fallback was added. The unchanged four-partial burst and all 20 isolated cases then passed together.

## Remaining checks

- Have the student review the code, design changes, and GUI/CLI usability. Development-assistant inspection is not human acceptance.
- Test a real microphone session, including start/stop, changing speech partials, correction, fall/recovery, and device failure. The native WAV test does not establish microphone permissions, background-noise tolerance, short-direction accuracy, or live latency.

Stage 3 KUMA evaluation remains a separate deliverable.
