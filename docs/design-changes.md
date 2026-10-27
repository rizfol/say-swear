# Stage 2 Design Changes

This is the consolidated **Original design → Implemented change → Reason → Impact** record required by the Course Project Stage 2 Instructions. F01–F10 remain traceable through the [feature mapping](feature-implementation-mapping.md). The same five patterns remain implemented; [pattern roles and methods](implemented-design-patterns.md) explain their structural realization.

## Baseline, current artifacts and evidence

The original Stage 1 design is preserved in Git history at commit `56da5fd95c43dde4760032e59a36787a2c74049c`. The [revised Stage 1 report](stage1-report.md) and current [UMLet-exported SVGs](uml/) describe the selected implementation. Earlier PlantUML sources, rendered previews and `docs/uml/stage1/` archives mentioned in development records are historical artifacts in Git history, not current-file promises. UMLet replaced the diagram authoring/export format; this did not change application behavior or add a design pattern.

Recorded verification progressed from 63 ordinary Java tests, to 73 after repeat support, to 76 after terminal redraw. Nine controlled bridge tests and 28 CPU semantic samples were recorded for repeat support. Earlier 20-case GPU and four-partial burst checks predate repeat. Historical diagram rendering/geometry/hash checks apply to the versions named in [validation](validation.md), not automatically to replacement SVGs.

The user reports having played the game and confirms that it works. This is general gameplay acceptance; no interface, microphone, command coverage or timing details were supplied. Specific microphone coverage and the updated 2000 ms GPU burst check remain unverified. Videos remain pending. These limits do not imply that the corresponding code is missing.

## D01 — Correct the decision provider identity

**Original:** the initial report interpreted OpenJev as the unrelated hosted `openjev.sh` service with an `openjev` model alias.

**Implemented change:** retain Java `DecisionModel`/`OpenJevAdapter`, but connect to a project loopback HTTP bridge for genuine **SemIf (formerly OpenJev), using Qwen3.5 4B**. The upstream SemIf commit is pinned to `23cf1f39fc9534fe81437200959b6dfc7106e45a`. Python hosts the external inference dependency; the game, frontends and controls remain Java.

**Reason / original need:** correct the provider research error after the user identified the intended project and model. Natural-language movement interpretation remains the original user need; it now runs locally without an API key.

**Impact:** F04–F08; UC01–UC05; Adapter preserved. [OpenJevAdapter.decide()/parseResponse()](../src/main/java/com/sayswear/agent/OpenJevAdapter.java), [DecisionModel.decide()](../src/main/java/com/sayswear/agent/DecisionModel.java), and [DecisionEngine.decide()/load_scorer()](../tools/semif/server.py) implement the boundary. Genuine-model and packaged CLI checks are recorded separately from demo tests.

## D02 — Concrete immutable values and typed status

**Original:** value objects were conceptual and snapshot lifecycle status was string-valued.

**Implemented change:** use records for `Vector2`, `GameSnapshot`, `GameEvent` and agent/control values, plus `PlayerStatus`. Copy direction sets and level geometry into immutable collections.

**Reason / original need:** safely publish one model to both views and avoid spelling-dependent lifecycle handling. Movement and recovery behavior remain unchanged.

**Impact:** F01/F05/F09/F10; UC01–UC06; MVC, Observer and State preserved. [GameSnapshot constructor](../src/main/java/com/sayswear/game/GameSnapshot.java), [ControlContext constructor](../src/main/java/com/sayswear/agent/ControlContext.java), [Level constructor](../src/main/java/com/sayswear/game/Level.java), [Player.status()](../src/main/java/com/sayswear/game/Player.java), [PlayerStatus](../src/main/java/com/sayswear/game/PlayerStatus.java) define the values. The enum is for presentation; `PlayerState` objects still execute lifecycle behavior.

## D03 — One presentation/observer contract

**Original:** both concrete views separately implemented `GameView` and `GameObserver`; transcript/status methods were frontend-specific.

**Implemented change:** `GameView` extends `GameObserver`, with default transcript/status hooks and lifecycle presentation. Concrete views provide rendering and messages; CLI overrides event handling for its output policy.

**Reason / original need:** give the controller one presentation contract and centralize common fall/respawn/goal messages without duplicating the simulation.

**Impact:** F01–F04/F10; UC01/UC02/UC06; MVC and Observer refined, not replaced. [GameView.onGameEvent()/render()/showMessage()](../src/main/java/com/sayswear/view/GameView.java), [CliView.onGameEvent()](../src/main/java/com/sayswear/view/CliView.java), [ApplicationController.start()](../src/main/java/com/sayswear/app/ApplicationController.java) implement the change.

## D04 — Add GUI natural-language text input

**Original:** GUI instructions entered through speech; CLI instructions were typed.

**Implemented change:** add the GUI Text Instructions field and Send instruction action, routed to the same text-command controller method used by CLI.

**Reason / original need:** allow users to exercise/correct movement when audio setup is unavailable. This is an added entry route for F04, not direct keyboard movement or a substitute for F02/F03.

**Impact:** F04–F09; UC01/UC02/UC04; MVC preserved. [JavaFxGameView.submitTypedCommand()](../src/main/java/com/sayswear/view/JavaFxGameView.java), [ApplicationController.submitTextCommand()](../src/main/java/com/sayswear/app/ApplicationController.java), [Main.runCli()](../src/main/java/com/sayswear/app/Main.java) share the pipeline. Historical GUI smoke testing exercised actual text-field movement with explicit demo mode.

## D05 — Add fresh-run and inspection controls

**Original:** session start/stop and checkpoint recovery were planned; a manual fresh-run control was not specified.

**Implemented change:** GUI Restart run and CLI `/restart`, `/state`, `/pause`, `/help`, `/quit` provide deterministic session operations. Restart invalidates pending work before resetting the game.

**Reason / original need:** let users restart the single level, inspect exact state and stop a session. These supporting operations are not counted toward the ten meaningful features.

**Impact:** F01/F02/F09/F10; UC01/UC02/UC06; MVC, Command, Observer and State preserved. [ApplicationController.restart()/pauseControls()](../src/main/java/com/sayswear/app/ApplicationController.java), [GameModel.restart()](../src/main/java/com/sayswear/game/GameModel.java), [Main.runCli()](../src/main/java/com/sayswear/app/Main.java) and the GUI button handlers implement them. GUI and packaged CLI checks covered reset/release behavior.

## D06 — Implement responsive presentation scheduling

**Original:** snapshots and throttled CLI display were described without concrete scheduling utilities.

**Implemented change:** JavaFX coalesces snapshots/transcript/messages on its application thread. The initial CLI used a 61×27 ASCII overview and approximately one-second refresh; explicit quiet mode suppressed periodic scripted output. D19 later replaces the scrolling interactive presentation. The current GUI also highlights the neutral player at the start; this is presentation of existing state.

**Reason / original need:** prevent repaint accumulation and make the player/state readable without adding game mechanics. At the initial stage, Gradle-forwarded streams made `System.console()` alone insufficient to identify a usable terminal.

**Impact:** F01/F03/F04/F10; UC01/UC02/UC06; MVC and Observer preserved. [JavaFxGameView.render()/scheduleUpdate()/drainUpdates()](../src/main/java/com/sayswear/view/JavaFxGameView.java), [CliView.formatSnapshot()/onGameEvent()](../src/main/java/com/sayswear/view/CliView.java) are relevant. D19 supersedes the original append-only CLI refresh policy, not the shared model.

## D07 — Add an explicitly selected offline demo model

**Original:** semantic interpretation used the configured real `DecisionModel`.

**Implemented change:** add `DemoDecisionModel`, a limited deterministic parser selected only by explicit `--demo`. Real SemIf remains the default; there is no automatic fallback to demo when inference fails.

**Reason / original need:** permit application development/demonstration without loading an AI service. This does not replace the required LLM integration or establish AI accuracy.

**Impact:** F04–F09 development/demo path; UC01–UC05; Adapter target contract preserved. [DemoDecisionModel.decide()/parse()](../src/main/java/com/sayswear/agent/DemoDecisionModel.java), [Main.compose()](../src/main/java/com/sayswear/app/Main.java), [SaySwearApplication.start()](../src/main/java/com/sayswear/app/SaySwearApplication.java) select/label the mode. Demo results are identified separately in validation.

## D08 — Define observer removal and failure isolation

**Original:** subject notification was planned without a subscriber-removal or exception policy.

**Implemented change:** notify a copied subscriber list, allow removal, and catch/log individual subscriber failures while continuing notification. Register controller cleanup before the presentation observer.

**Reason / original need:** a presentation failure must not prevent lifecycle cleanup; close/unsubscribe must be safe during notification.

**Impact:** F01/F09/F10; UC01/UC02/UC06; Observer refined. [GameModel.removeObserver()/notifyObservers()](../src/main/java/com/sayswear/game/GameModel.java), [ApplicationController.start()/close()](../src/main/java/com/sayswear/app/ApplicationController.java) implement the policy. Observer removal and fall cleanup have deterministic tests.

## D09 — Choose a reproducible Java build

**Original:** Java/JavaFX were required without concrete build and dependency versions.

**Implemented change:** use Java 21, JavaFX 21, Gradle wrapper 8.14.2, pinned sherpa Java/native dependencies and JUnit. Platform classifiers select native dependencies for the host.

**Reason / original need:** provide reproducible compilation, ordinary tests and packaged GUI/CLI startup rather than requiring manually assembled classpaths.

**Impact:** all features and UC01–UC06; all five patterns retained, no added pattern. [build.gradle](../build.gradle) defines `test`, `installDist`, `guiSmokeTest`, `asrSmokeTest` and `liveSemIfTest`; [wrapper configuration](../gradle/wrapper/gradle-wrapper.properties) pins Gradle. Historical suites increased 63 → 73 → 76 ordinary tests as functionality evolved; special tasks remain opt-in. D20 records Linux setup entry points.

## D10 — Specify elapsed-time simulation and timer ownership

**Original:** a fixed 60 Hz timestep and roughly one-tick timer quantization were proposed.

**Implemented change:** schedule updates every 16 ms, measure elapsed time and cap one update at 50 ms. Advance timers before game movement; subdivide movement for corridor collision checks. `ActionExecutor` owns active timed commands and `TimedPressCommand` owns remaining duration.

**Reason / original need:** bound motion after a delayed loop turn and prevent a large update from skipping across void. Timer tolerance follows the actual capped update; prolonged stalls can make simulation lag wall time.

**Impact:** F01/F08/F09/F10; UC01/UC02/UC05/UC06; Command and State preserved. [ApplicationController.tickFromClock()/advance()](../src/main/java/com/sayswear/app/ApplicationController.java), [ActionExecutor.advance()](../src/main/java/com/sayswear/control/ActionExecutor.java), [TimedPressCommand.advance()](../src/main/java/com/sayswear/control/TimedPressCommand.java), [ActiveState.update()](../src/main/java/com/sayswear/game/ActiveState.java) implement this. Movement, quantization and collision tests cover deterministic behavior.

## D11 — Centralize request invalidation and cleanup

**Original:** sequences used explanatory operations `handleInputFailure(message, epoch, version)` and `invalidateControls(reason)`.

**Implemented change:** completion callbacks check captured identity and use private `failCurrent(message)`/`invalidateControls()` helpers. Explicit `close()` lifecycles release resources. Control reset advances the epoch before cancellation callbacks run; decisions carry local version/epoch/revision metadata.

**Reason / original need:** use one neutral cleanup path and prevent old successes/failures from changing a newer input session. Public implementation operations differ from the conceptual signatures without changing their responsibility.

**Impact:** F02/F04/F06/F09/F10; UC01/UC02/UC04/UC06; MVC, Command, Adapter and Observer preserved. [ApplicationController.isCurrent()/failCurrent()/invalidateControls()/close()](../src/main/java/com/sayswear/app/ApplicationController.java), [ControlManager.reset()/accept()](../src/main/java/com/sayswear/control/ControlManager.java), [VoiceCommandAgent.reset()/close()](../src/main/java/com/sayswear/agent/VoiceCommandAgent.java), [VoiceInputService.stop()/close()](../src/main/java/com/sayswear/voice/VoiceInputService.java) implement cleanup. Tests cover stale callbacks and bounded/reset behavior.

## D12 — Define precedence between voice and typed input

**Original:** the selected frontend supplied voice or typed text, so simultaneous input-source precedence was unspecified.

**Implemented change:** a typed command supersedes remaining partials of the current spoken utterance. Fresh voice becomes eligible after a new endpoint. Both sources share one version counter and control pipeline.

**Reason / original need:** prevent an older evolving transcript from undoing a newer typed correction after D04 added GUI text entry.

**Impact:** F02/F03/F06/F09; UC01/UC02/UC04; MVC and Adapter preserved. [ApplicationController.submitTextCommand()/handleTranscript()](../src/main/java/com/sayswear/app/ApplicationController.java) and [VoiceInputService.endpoint()](../src/main/java/com/sayswear/voice/VoiceInputService.java) implement source/utterance identity. Specific mixed microphone/text coverage remains unverified.

## D13 — Factor bounded semantic selection into model readouts

**Original:** the adapter conceptually obtained one bounded action from the provider.

**Implemented change:** genuine upstream full-prompt SemIf scoring selects action type, vertical `UP/DOWN/NONE`, horizontal `LEFT/RIGHT/NONE`, and an optional duration. One to four readouts compose one allowed action. The original seven-type/51-label version became eight types/52 labels with D18.

**Reason / original need:** respect the provider's per-readout option limit and resolve observed direction-set errors while supporting every diagonal. Each axis receives the transcript and its initial held direction; no map, position or destination is included.

**Impact:** F04–F08; UC01–UC05; Adapter preserved, deterministic Command boundary unchanged. [DecisionEngine.decide()/validate()](../tools/semif/server.py), [OpenJevAdapter.createChoices()/parseResponse()](../src/main/java/com/sayswear/agent/OpenJevAdapter.java) enforce the vocabulary. Historical model failures, eventual sampled successes and controlled bridge tests remain recorded in validation; this is not a universal accuracy claim.

## D14 — Specify compatible native model deployment

**Original:** native model deployment and quantization were unspecified.

**Implemented change:** use pinned Q4_K_M GGUF weights/reference tokenizer. The Windows deployment uses a project-local overlay pairing the same-version official portable AVX2 CPU DLL with unchanged CUDA libraries; the installed dependency is preserved. Explicit GPU allocation extends the upstream CPU allocation setting while retaining its scoring.

**Reason / original need:** the initial CUDA wheel's CPU library required AVX512 unavailable on the verification CPU. GPU offload still needs compatible CPU instructions. Quantization makes local inference practical within the available memory.

**Impact:** F04–F08 runtime; UC01–UC05; Adapter boundary preserved. [configure-semif-native.py `configure()`](../scripts/configure-semif-native.py), [server `load_scorer()`](../tools/semif/server.py), [SemIf setup](semif-setup.md) document provenance and startup. Historical readiness/model checks passed with 34/34 layers offloaded; this is platform-specific evidence.

## D15 — Adjust the decision deadline

**Original:** the initial implementation used a 1000 ms deadline including queue time.

**Implemented change:** default to 2000 ms while retaining finite provider timeouts, two active calls, one replaceable latest pending input and fail-closed current failures. Explicit launch options override environment settings.

**Reason / original need:** the initial four-partial burst expired at 1000 ms. A later pre-repeat burst returned the latest correction after 990.7 ms with the 2000 ms budget; the larger allowance accommodates queue/runtime variation without adding artificial delay.

**Impact:** F04/F05/F06/F08/F09; UC01–UC05; Adapter/Command unchanged. [VoiceCommandAgent.interpret()/expire()](../src/main/java/com/sayswear/agent/VoiceCommandAgent.java), [OpenJevAdapter.decide()](../src/main/java/com/sayswear/agent/OpenJevAdapter.java), [AppConfig.parse()](../src/main/java/com/sayswear/app/AppConfig.java), [run.ps1](../run.ps1) implement deadlines/configuration. Pre-repeat real CLI/burst checks passed; current repeat-schema GPU budget verification remains outstanding.

## D16 — Normalize correction word boundaries for axis readouts

**Original:** the same raw transcript representation reached every model readout.

**Implemented change:** preserve raw text in Java, the HTTP request and action-type scoring. Only after Qwen selects `SWITCH`, split commas/whitespace and join words with `, ` for axis scoring.

**Reason / original need:** the unchanged ASR-like correction burst exposed sensitivity to unpunctuated text. Generic, idempotent formatting supplies word boundaries; it does not identify direction words or parse correction intent. Qwen still selects each axis.

**Impact:** F06, with F05/F07 regression coverage; UC03/UC04/UC05; Adapter preserved. [DecisionEngine.decide()](../tools/semif/server.py), [bridge tests](../tools/semif/test_server.py), [LiveSemIfTest](../src/test/java/com/sayswear/agent/LiveSemIfTest.java) record the change. Twenty isolated cases and the original four-partial burst passed together before repeat support; wider language robustness remains unestablished.

## D17 — Replace checkpoint recovery with start recovery

**Original:** F10/UC06/SD05 included checkpoint activation and respawn at the latest checkpoint. `Level`/`GameSnapshot` exposed progress, `resetToCheckpoint()` restored it, and views showed checkpoints.

**Implemented change:** remove checkpoint geometry, saved positions, activation events and progress displays. F10 becomes **Fall Detection and Start Recovery**, retaining its ID. Every fall restores `Level.start()` with zero velocity and no held inputs, while preserving cancellation/freshness and the fall/respawn lifecycle.

**Reason / original need and scope:** the user requested that every fall lose all progress, matching the intended rage-game experience. Recovery still allows another attempt; preserving intermediate progress is deliberately removed, not claimed as implemented.

**Impact:** F10 and F01/F09 feedback/cleanup; UC06; State, Observer, Command and MVC preserved. [GameModel.evaluatePosition()/resetToStart()](../src/main/java/com/sayswear/game/GameModel.java), [Level.start()](../src/main/java/com/sayswear/game/Level.java), [FallingState.update()](../src/main/java/com/sayswear/game/FallingState.java), [RespawningState.update()](../src/main/java/com/sayswear/game/RespawningState.java), [ApplicationController.onGameEvent()](../src/main/java/com/sayswear/app/ApplicationController.java), [GameView.onGameEvent()](../src/main/java/com/sayswear/view/GameView.java) implement recovery. `GameSnapshot`/`GameEventType` no longer contain checkpoint data/events. Current UC06/SD05 show the replacement; 63-test, GUI and CLI regressions passed at this change.

## D18 — Add structured repeat memory and refresh UML

**Original:** context contained held directions and prior accepted text, but no structured movement history. Seven action types/51 labels could not explicitly request replay after a timed movement expired.

**Implemented change:** add `MovementIntent(type, directions, durationMs)` for accepted PRESS/SWITCH/TIMED_PRESS and expose nullable `ControlContext.lastMovement`. Add empty-direction, zero-duration `REPEAT_LAST` as the eighth type/52nd label. Java creates new commands from remembered semantics; absent history produces neutral feedback. Permit one repeat per utterance, including across intervening partial interpretations.

**Reason / original need:** the user requested `again`. Resolved memory preserves exact duration/direction after expiry without asking the model to reconstruct an old action. Ordinary stop/expiry/keep/no-action preserve memory; pause/fall/restart/current failure clear it. This extends F05/F08/F09 rather than inventing another counted feature.

**Impact:** F05/F08/F09; UC03/UC05, with reset behavior in UC06; Adapter and Command preserved. [MovementIntent](../src/main/java/com/sayswear/agent/MovementIntent.java), [ControlContext](../src/main/java/com/sayswear/agent/ControlContext.java), [ControlManager.accept()/plan()/reset()](../src/main/java/com/sayswear/control/ControlManager.java), [OpenJevAdapter.decide()](../src/main/java/com/sayswear/agent/OpenJevAdapter.java), [DecisionEngine.decide()/validate()](../tools/semif/server.py), [ApplicationController.acceptanceMessage()](../src/main/java/com/sayswear/app/ApplicationController.java) implement repeat. Restart an older running bridge to load the updated schema.

**Historical evidence/artifacts:** 73 Java tests, nine bridge tests, 20 unchanged baseline plus eight repeat/history CPU samples and packaged demo/live CLI checks passed. CPU samples used 60000 ms and live CPU CLI 30000 ms diagnostic caps, not the 2000 ms gameplay gate. At this stage original diagrams were archived under `docs/uml/stage1/` and 14 archived files were hash-checked while current diagrams were regenerated. Those archives/previews are now historical; current diagrams are UMLet SVGs.

## D19 — Keep the CLI display in place while editing input

**Original:** full ASCII frames printed about once per second, scrolling the terminal while the user typed; Windows launched CLI through Gradle-forwarded streams.

**Implemented change:** use JLine 3.30.16 `Status` for a fixed bottom panel and `LineReader` for editable input. Resize the map, include the JNI terminal provider and launch the installed distribution directly after `installDist`. Quiet, redirected and unsupported-terminal sessions use plain nonperiodic state/event output.

**Reason / original need:** the user requested a stable game display that preserves partial commands. Direct startup exposes actual terminal capabilities. Renderers continue consuming shared snapshots; no second simulation or interpreter is added. This supersedes D06's scrolling interactive output.

**Impact:** F01/F04 presentation and F09/F10 feedback; UC02, with UC06 recovery display; MVC and Observer preserved. [CliView.open()/readCommand()/refreshDashboard()/updateDashboard()/close()](../src/main/java/com/sayswear/view/CliView.java), [Main.runCli()](../src/main/java/com/sayswear/app/Main.java), [build.gradle](../build.gradle), [run.ps1](../run.ps1) implement the change. Seven CLI tests, full 76-test suite, packaged demo, automatic piped fallback, actual PowerShell quiet launch and native Windows ConPTY checks passed. Specific native Linux terminal interaction remains unverified.

**UML impact:** class/SD02 show terminal ownership, redraw, editable input, fallback and cleanup; SD05 distinguishes ordinary throttled refresh from lifecycle events. Earlier regeneration/checks are historical; current corresponding SVGs retain those responsibilities.

## D20 — Add Linux launch and model-setup entry points

**Original:** recorded setup/launch automation was primarily PowerShell; portable Java source alone did not provide equivalent Linux bootstrap commands.

**Implemented change:** add [run.sh](../run.sh), [setup-semif.sh](../scripts/setup-semif.sh), [run-semif.sh](../scripts/run-semif.sh) and [setup-asr.sh](../scripts/setup-asr.sh). These locate the repository, honor documented options/environment and provide Java launch, explicit CPU/GPU model setup, and pinned ASR download/extraction. The ASR script uses `verify_archive()` for size/hash checks and an extraction-completion marker so failed extraction is repaired on rerun.

**Reason / original need:** support users building/running on Linux without translating Windows-specific paths, virtual-environment layout and native DLL handling. This is a supporting deployment change, not a new game feature or a rewrite of the Java application.

**Impact:** setup for F01–F10; UC01/UC02 deployment prerequisite; no pattern added or replaced. [ASR setup](asr-setup.md), [SemIf setup](semif-setup.md), [AppConfig.parse()](../src/main/java/com/sayswear/app/AppConfig.java) document configuration. Recorded Git Bash/fixture checks verify selected script control flow, including interrupted extraction; they do not establish native Linux inference, audio or terminal behavior. Consult [validation](validation.md) for exact executed scope.

## Preserved responsibilities and remaining completion work

The human chooses every direction and turn. AI receives control context, never level geometry or a destination, and returns only a bounded semantic proposal. Java owns schema/freshness validation, memory replay, cancellation, timing, simulation, fall detection and recovery. Both frontends share that core.

No design pattern has been replaced: MVC, Command, Adapter, Observer and State retain their roles with the refinements above. No checkpoint functionality is silently claimed after its removal. All feature videos and accessible timestamps remain pending. Specific microphone coverage and current default-budget GPU verification remain unverified; general user gameplay acceptance is recorded without extrapolating those details. Full Stage 3 testing/KUMA evaluation is a separate deliverable.
