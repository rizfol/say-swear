# Say Swear

**An AI voice-controlled 2D precision game — EECS 3311, Fall 2026**

Guide one character along a winding path over a void. You choose every turn by speaking or typing natural-language instructions. Every fall releases movement and returns you to the level start. Reach the goal to finish the single level.

The application is **Java 21**, with a **JavaFX GUI and a terminal CLI sharing one game, agent, and control core**. The decision backend is **local SemIf (formerly OpenJev), using Qwen3.5 4B**. Streaming speech recognition uses **sherpa-onnx**. No API key is required.

## Project stages and records

| Artifact | Location |
|---|---|
| Revised Stage 1 design report | [docs/stage1-report.md](docs/stage1-report.md) |
| Stage 2 implementation report | [stage2-report.pdf](stage2-report.pdf) ([editable report source](docs/stage2-report.md)) |
| Current class, use-case, and sequence diagrams | [docs/uml/](docs/uml/) |
| Original Stage 1 snapshot | Git history (Git commit `56da5fd95c43dde4760032e59a36787a2c74049c`) |
| Stage 2 feature → implementation mapping | [docs/feature-implementation-mapping.md](docs/feature-implementation-mapping.md) |
| AI tools/models and representative collaboration log | [docs/ai-collaboration-log.md](docs/ai-collaboration-log.md) |
| Original design → implementation change → reason | [docs/design-changes.md](docs/design-changes.md) |
| Executed validation and remaining limitations | [docs/validation.md](docs/validation.md) |
| Implemented pattern explanations | [docs/implemented-design-patterns.md](docs/implemented-design-patterns.md) |
| Feature video links and recording plan | [docs/stage2-demonstration-index.md](docs/stage2-demonstration-index.md) |

The revised Stage 1 report describes the current local SemIf/Qwen design and embeds the UMLet-exported SVGs under `docs/uml/`. The original design remains available in the Git snapshot above. The [design-change record](docs/design-changes.md) explains the move from the earlier hosted OpenJev assumption to **SemIf** and the other implementation changes. Stage 3 KUMA evaluation is a later deliverable.

**Current gameplay:** every fall returns to the level start, with no saved intermediate positions. F10 is **Fall Detection and Start Recovery**. A fresh `again` command repeats the remembered movement, including its duration. [D17–D18](docs/design-changes.md) record these changes; current UC06/SD05 show start recovery. The earlier checkpoint design is preserved in Git history. [Validation](docs/validation.md) records repeat tests and the remaining default-budget GPU check.

**Stage 2 submission status (October 27, 2026):** the user reports having played the game and confirms that it works properly. The instructor replied that the existing early commit history works as evidence for the weekly-progress requirement; the [weekly summary](docs/validation.md) preserves the actual dates and links. Videos/timestamps and focused code review are still pending. The [AI accounting](docs/ai-collaboration-log.md) now records student-estimated tokens and subscription spending, with its basis and unmeasured categories stated. The report remains a review draft until these items are completed. Submission is due November 10.

## 1. Setup

### Prerequisites

- A **Java 21 JDK**. `java -version` must report 21 when using Gradle directly. `run.ps1` can find an installed Adoptium/Oracle JDK 21 or accept `-JavaHome`. On Linux, `run.sh` checks `JAVA_HOME`, `PATH`, and `/usr/lib/jvm`, or accepts `--java-home`.
- Git and internet access for the first dependency/model download. Gradle 8.14.2 is supplied through the wrapper; a separate Gradle installation is unnecessary.
- For local decisions: Python 3.12 and the pinned SemIf/native runtime described in [SemIf setup](docs/semif-setup.md). Python hosts this external inference dependency; the application, both frontends, controls, and game remain Java.
- For GUI voice input: a microphone, OS microphone permission, and the downloaded English ASR model. The CLI needs no microphone or ASR model.

Build and test from the repository root on Windows:

```powershell
.\run.ps1 -Mode test
.\run.ps1 -Mode build
```

If JDK discovery needs help:

```powershell
.\run.ps1 -Mode test -JavaHome 'C:\path\to\jdk-21'
```

The scripts keep Gradle, native caches, temporary files, the inference virtual environment, and model weights under ignored `.tools/` and `models/` directories. The first setup needs several GB of disk space. Do not commit those directories.

### Linux startup

With Bash and a Java 21 JDK installed, use the Linux equivalent of `run.ps1`:

```bash
./run.sh --mode test
./run.sh --mode build
./run.sh --demo                  # GUI demo; no decision service needed
./run.sh --mode cli --demo       # Terminal demo
```

The default mode is `gui`. The GUI needs a graphical desktop and GTK 3 runtime libraries. Once your local SemIf service is running, omit `--demo` to use it:

```bash
./run.sh
./run.sh --mode cli
./run.sh --mode cli --quiet-cli
./run.sh --java-home /path/to/jdk-21 --endpoint http://127.0.0.1:8765/decide
```

Use `--decision-timeout-ms` and `--asr-model-dir` for the other configuration overrides, or set the environment variables listed below. `./run.sh --help` lists all options. The launcher builds the CLI distribution and starts it directly to preserve terminal input. It runs from the repository root, so the default model path works even when invoked from another directory.

This launcher starts the Java application; SemIf runs in a separate terminal. Install Python 3.12 and the build prerequisites in [SemIf setup](docs/semif-setup.md#install-and-launch-on-linux), then use:

```bash
./scripts/setup-semif.sh --cpu-only   # One-time dependencies and model download
./scripts/run-semif.sh --cpu-only     # Keep running; wait for Ready
# In another terminal:
./run.sh --mode cli
```

For NVIDIA CUDA, omit `--cpu-only` in both commands; the CUDA toolkit is required during setup. Demo mode needs no SemIf service, and typed input needs no ASR model. For GUI voice input on Linux, also run `./scripts/setup-asr.sh` as described below.

### Local decision model

Follow [docs/semif-setup.md](docs/semif-setup.md) for the pinned runtime and hardware-specific installation. The model is `Qwen/Qwen3.5-4B`, using the pinned Q4_K_M GGUF and matching reference tokenizer.

```powershell
.\scripts\setup-semif.ps1
.\scripts\run-semif.ps1
```

Keep the model terminal running. Wait for its `Ready` message before starting gameplay. `http://127.0.0.1:8765/health` reports the loaded backend/model and runtime metadata. The project provides the small loopback HTTP bridge because SemIf's scoring library has no application-specific HTTP interface.

After updating to repeat-command support, stop an already-running model service with Ctrl+C and run `scripts/run-semif.ps1` again. The updated Java request includes structured movement memory and 52 actions; the older bridge accepts the previous schema.

### Streaming speech model

On Windows:

```powershell
.\scripts\setup-asr.ps1
```

On Linux, with Bash, `curl`, `tar`, `bzip2`, and GNU coreutils (`sha256sum`/`stat`) available:

```bash
./scripts/setup-asr.sh
```

Both scripts download and verify the fixed English Zipformer model under `models/`, with resumable downloads cached under `.tools/downloads/`. The Linux script resolves the repository from its own location; `./run.sh` uses the default model path without an environment override. For direct launches from another working directory, set `SHERPA_MODEL_DIR` to the absolute model directory in your current shell; the setup script cannot change its parent shell's environment.

See [ASR setup, model-path examples, and the optional native smoke test](docs/asr-setup.md). Native sherpa libraries are resolved with the Java dependencies. Setup opens no microphone and starts no service; model initialization starts only when you choose **Start listening**. The Python SemIf decision service is configured separately. Native Linux recognition and live microphone behavior remain unverified.

## 2. Run and play

With the local decision service ready:

```powershell
.\run.ps1 -Mode gui
.\run.ps1 -Mode cli
```

The GUI displays the actual level, player, start, goal, transcript, held directions, and lifecycle state. Click **Start listening**, then speak. **Stop & release** immediately ends recording and clears controls. A text-instruction field provides the same natural-language route as the CLI; it does not bind movement keys.

A red arrow with white **YOU ARE HERE** text points to the player while active at the start with neutral controls. It disappears when movement begins and returns after a restart or fall recovery.

The CLI shows an ASCII map (`P` player, `.` safe corridor, `S` start, `G` goal), exact position/velocity, held directions, and game status. In a supported terminal, the game panel redraws in place at the bottom while a separate input area preserves the text you are editing. The map adapts to the terminal size. Screen coordinates use **up = decreasing Y**.

`run.ps1 -Mode cli` builds the distribution and launches it directly, so the game can access the terminal. For scripts, use `-QuietCli` with `run.ps1`, or `--quiet-cli` with the packaged launcher. Redirected input/output and unsupported terminals also use plain output without periodic maps; `/state` and lifecycle events still expose game state. JLine **3.30.16** and its JNI terminal provider are included by Gradle for Java 21; no separate terminal library installation is needed.

| Instruction | Intended behavior |
|---|---|
| `right`, `go up` | Set continuous movement in that direction |
| `stop` | Release all movement; retain the remembered movement for a later repeat |
| `other way` | Reverse the directions held at the start of this utterance |
| `keep going` | Preserve movement and any existing timer |
| `right — no, left` | Correct the instruction to left |
| `up and right` | Hold two perpendicular directions; diagonal speed is normalized |
| `a little left` | Hold left for 200 ms, then release |
| `a tiny bit right` | Hold right for 100 ms |
| `again` | Execute the last accepted movement again, with its remembered directions and duration |

For example, after `a little left` finishes, a fresh `again` repeats the same 200 ms left movement. Repeated partial transcripts within that same spoken utterance do not restart its timer. If there is no remembered movement, the game stays unchanged and explains that there is nothing to repeat.

Timer expiry, `keep going`, unclear input, and ordinary stop/release instructions preserve this memory. `/pause`, **Stop & release**, a fall, restart, or current input error clears it. A new directional or timed action replaces the remembered movement.

Interpretation is model output, so these are expected behaviors to review, not a guarantee for every accent or paraphrase. An ambiguous `NO_ACTION` leaves existing movement unchanged. A current model error/timeout stops controls and listening; give a new text instruction or restart listening to retry. Falling invalidates pending interpretations, cancels timers, releases held directions, and returns the player to the level start with zero velocity. Give a fresh instruction after recovery.

CLI session controls:

| Command | Effect |
|---|---|
| `/state` | Refresh the current shared snapshot; print it in plain-output mode |
| `/pause` | Immediately release movement and invalidate pending work |
| `/restart` | Start the same level again, with neutral controls |
| `/help` | Show instructions |
| `/quit` or EOF | Close the session and release resources |

`stop` is interpreted by the AI; `/pause` and **Stop & release** are immediate deterministic session controls.

### Explicit offline demonstration

```powershell
.\run.ps1 -Mode gui -Demo
.\run.ps1 -Mode cli -Demo
```

Demo mode uses a small deterministic parser and is visibly labelled. It is useful for inspecting the game and controls without SemIf. It is **not** an AI model or a replacement for testing real inference, and the application never switches to it automatically after a model failure.

### Configuration

| Setting | Default | Override |
|---|---|---|
| SemIf endpoint | `http://127.0.0.1:8765/decide` | `SEMIF_ENDPOINT`, `--endpoint`, or `run.ps1 -Endpoint` |
| Decision deadline, including agent queue time | 2000 ms | `DECISION_TIMEOUT_MS`, `--decision-timeout-ms`, or `run.ps1 -DecisionTimeoutMs` |
| ASR model directory | `models/sherpa-onnx-streaming-zipformer-en-2023-06-26` | `SHERPA_MODEL_DIR`, `--asr-model-dir`, or `run.ps1 -AsrModelDirectory` |

`.env.example` documents the names; the application does not automatically load `.env` files. Rapid partials exceeded the original 1000 ms deadline during testing; 2000 ms allowed the latest correction to complete in the tested burst. A longer deadline allows slower inference but does not make navigation more responsive. Review the measured local latency in the validation record before changing it.

Direct Gradle usage with Java 21 is also available. On Linux/macOS, launch the installed CLI after the build so it inherits the terminal directly:

```text
./gradlew test
./gradlew run --args="--gui"
./gradlew installDist
./build/install/say-swear/bin/say-swear --cli
./build/install/say-swear/bin/say-swear --cli --demo
./build/install/say-swear/bin/say-swear --cli --quiet-cli
```

On Windows use `gradlew.bat` and `build\install\say-swear\bin\say-swear.bat`, or use `run.ps1` as above. Run launchers from the repository root or provide an absolute ASR model path. Avoid Gradle `run`/`runCli` for the interactive CLI because their forwarded streams do not give the application a direct terminal. Desktop native dependencies are platform-specific. The packaged CLI passed a native Windows ConPTY check; Linux/macOS interaction and human visual review remain unverified.

## 3. Architecture and implementation boundary

```text
GUI microphone → VoiceInputService → StreamingSpeechRecognizer / SherpaOnnxAdapter
                                            ↓ partial transcripts
JavaFxGameView / CliView → ApplicationController → VoiceCommandAgent
                                                       ↓ DecisionModel
                                                  OpenJevAdapter
                                                       ↓ local HTTP
                                            SemIf → Qwen3.5 4B logits
                                                       ↓ bounded ActionDecision
ApplicationController → ControlManager → ActionExecutor → GameCommand → GameModel
                                                                          ↓
                                                       GameEvent / immutable GameSnapshot
                                                                          ↓
                                                               selected GUI or CLI
```

`ControlContext` includes held directions, previous accepted command text, structured `lastMovement`, and session metadata. `MovementIntent` stores the type, directions, and duration of an accepted `PRESS`, `SWITCH`, or `TIMED_PRESS`; it remains available after a timer releases its inputs. The AI receives **no map, player position, start, or goal coordinates**. It does not navigate or solve the level. Without new human input it produces no new actions.

SemIf scores finite alternatives directly from model logits. Its native 16-option limit requires separate questions for eight action types, vertical direction, horizontal direction, and optional duration. A decision takes one to four readouts; each direction question includes the starting held direction on that axis. The bridge combines the answers into one of the Java adapter's 52 legal semantic labels. `REPEAT_LAST` has no directions and zero duration: the AI selects repetition, then Java validates freshness and creates a fresh command from remembered `MovementIntent`. It does not rerun the previous transcript through the model. Model probabilities are conditional option scores, not calibrated confidence.

For model-selected corrections/reversals, the direction questions use a comma-separated word view because testing exposed sensitivity to unpunctuated speech. The original transcript remains intact in Java and the action-type question. This formatting selects no direction; its rationale and limits are recorded in the design changes.

The shared application loop serializes state mutations. HTTP and microphone work run on workers; JavaFX receives coalesced snapshots on its UI thread. New input versions supersede older requests. Session epochs prevent pre-fall/pre-restart responses from resuming movement, and context revisions reject results based on expired timers. The agent freezes the starting context of each spoken utterance so repeated partials do not repeatedly reverse a direction.

| Pattern | Concrete implementation |
|---|---|
| MVC | `GameModel`, `ApplicationController`, shared `GameView`, JavaFX/CLI views |
| Command | `GameCommand`, key-down/up, timed-press, release-all, `ActionExecutor` |
| Adapter | `DecisionModel` / `OpenJevAdapter`; `StreamingSpeechRecognizer` / `SherpaOnnxAdapter` |
| Observer | `GameModel` events to controller and selected view through `GameObserver` |
| State | `ActiveState`, `FallingState`, `RespawningState`, `FinishedState` |

The [feature mapping](docs/feature-implementation-mapping.md) connects F01–F10 to original design identifiers, implementation files, methods, patterns, verification evidence, and pending demonstration segments. [Implemented design patterns](docs/implemented-design-patterns.md) explains the actual roles and collaboration in the source.

### Current UML diagrams

| Diagram | UMLet-exported SVG |
|---|---|
| Class diagram | [View](docs/uml/class-diagram.svg) |
| Use-case diagram | [View](docs/uml/use-case-diagram.svg) |
| SD01 — GUI voice command | [View](docs/uml/sequence-diagrams/sd01-gui-voice-command.svg) |
| SD02 — CLI text command | [View](docs/uml/sequence-diagrams/sd02-cli-text-command.svg) |
| SD03 — Contextual command and repeat | [View](docs/uml/sequence-diagrams/sd03-contextual-command.svg) |
| SD04 — Correction, combined and timed commands | [View](docs/uml/sequence-diagrams/sd04-live-correction.svg) |
| SD05 — Fall and start recovery | [View](docs/uml/sequence-diagrams/sd05-fall-recovery.svg) |

These seven SVGs were exported from UMLet. Earlier diagram versions remain in Git history (Git commit `56da5fd95c43dde4760032e59a36787a2c74049c`, `docs/uml/stage1`).

## 4. Verification and decisions to review

Ordinary JUnit tests run without model downloads, network calls to a live model, or microphone recording. They cover simulation, control contracts, stale results, cancellation, failure/recovery, agent scheduling, mocked HTTP, capture lifecycle, and CLI output. Opt-in desktop/native checks are documented in [validation.md](docs/validation.md).

The repeat update passed **73 Java tests**, **nine bridge tests**, packaged demo/live CLI checks, and **28 real-model CPU cases**. The CPU checks used longer diagnostic deadlines; the updated **2000 ms GPU burst gate remains unverified**. Restart the model service after updating, then review responsiveness with the intended hardware and microphone.

The subsequent CLI display update passed the full build with **76 Java tests**, including **seven CLI tests**, plus packaged demo, automatic piped-fallback, native Windows ConPTY, and actual PowerShell quiet-launch checks. An unfinished instruction survived periodic redraw and reached the actual game intact. [D19](docs/design-changes.md) and [validation](docs/validation.md) record the scope and remaining platform review.

The user has played the game and reports that it works properly. This records general gameplay acceptance; the interface, microphone setup, individual commands, and measured latency were not specified. The [validation record](docs/validation.md) identifies the remaining review and evidence without treating that gameplay check as missing.

Important focused review items:

1. **Real interaction:** play with your microphone and voice. Check short words, pause boundaries, corrections, and recovery. Recorded-sample recognition does not establish microphone/accent accuracy.
2. **Latency and feel:** judge the path width, movement speed, 100/200/400 ms timed actions, and interpretation delay together. The human still controls timing.
3. **Ambiguity:** `NO_ACTION` preserves movement; a current failure stops it. Confirm that distinction and the immediate pause control.
4. **Freshness:** inspect the controller, agent, and control-manager tests explaining why old decisions and expired timers cannot override newer controls.
5. **Mixed GUI input:** a typed instruction supersedes remaining partials of the current spoken utterance; speech becomes eligible again at the next endpoint.
6. **Course ownership:** read the implementation and record the actual scope and outcome of your focused code review in the collaboration log. General gameplay acceptance has already been recorded; AI-generated tests and source do not establish your understanding of each implementation path.
7. **Repeat memory:** review `again` after a timed action expires, after ordinary `stop`, and after a pause/fall clears memory. A fresh utterance repeats; duplicate partials should not restart the same action.

The game intentionally has one player, one fixed level, a start, falling, and a goal. There are no checkpoints, enemies, jumping, inventory, combat, procedural generation, or autonomous navigation.

### Rebuild the Stage 2 report

The report source includes the maintained mapping, pattern, change, collaboration, verification, and video-index documents. After filling pending evidence, regenerate the PDF from the repository root:

```text
python -m pip install reportlab svglib
python scripts/build-stage2-report.py
```

These packages are only needed to rebuild the documentation PDF. They are not Java application dependencies. The builder embeds the existing UMLet SVGs and does not regenerate diagrams. The large class/sequence diagrams use larger appendix pages so their text remains readable when zoomed or printed at the indicated size. Review the generated PDF after changing report content.
