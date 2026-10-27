# Stage 2 Feature-to-Implementation Mapping

This mapping follows the Course Project Stage 2 Instructions: every Stage 1 feature is accounted for with its status, implementation files/responsibilities, key methods, patterns, behavior type and video reference. The [revised Stage 1 report](stage1-report.md) retains F01–F10 and UC01–UC06. The original design is preserved in Git history at commit `56da5fd95c43dde4760032e59a36787a2c74049c`; current [UMLet SVG diagrams](uml/) describe the implementation. [Design changes](design-changes.md) explain differences.

All ten features have connected implementations. **Implemented** does not imply a completed video or unrestricted reliability. The user reports having played the game and confirms that it works; no interface, microphone, command coverage or timing details were supplied. Specific microphone coverage and the updated 2000 ms GPU burst check remain unverified. Every demonstration is **PENDING — user recording, accessible link and timestamp required**.

## Feature index

| Feature | Stage 1 reference | Status/change | Type | Video |
|---|---|---|---|---|
| F01 — Real-Time Precision Movement | F01, same requirement | Implemented; CLI presentation refined | Deterministic | PENDING |
| F02 — Continuous Voice-Control Session | F02, same requirement | Implemented; lifetime/status refined | Deterministic session management | PENDING |
| F03 — Streaming Speech Transcription | F03, same requirement | Implemented | AI-based; Java delivers text | PENDING |
| F04 — Natural-Language Movement Interpretation | F04, same requirement | Implemented; local SemIf/Qwen provider | Hybrid | PENDING |
| F05 — Context-Aware Commands | F05, same requirement | Implemented; repeat memory added | Hybrid | PENDING |
| F06 — Live Command Correction / Interruption | F06, same requirement | Implemented; bounded scheduling | Hybrid | PENDING |
| F07 — Combined Directional Commands | F07, same requirement | Implemented | Hybrid | PENDING |
| F08 — Magnitude / Timed Commands | F08, same requirement | Implemented; timed replay added | Hybrid | PENDING |
| F09 — Control-State and Conflict Management | F09, same requirement | Implemented; freshness/replay guards | Deterministic | PENDING |
| F10 — Fall Detection and Start Recovery | F10 — Fall Detection and Checkpoint Recovery | Modified: every fall returns to start | Deterministic | PENDING |

GUI text entry, explicit demo mode, session controls, repeat memory, terminal redraw and Linux setup are accounted for in D04–D07 and D18–D20. They extend these features or support setup and are not counted as additional features to reach ten. Checkpoint progression is explicitly removed under D17; no original feature ID is silently omitted.

## F01 — Real-Time Precision Movement

**Reference/status:** Stage 1 F01; implemented, with D06/D10/D19 presentation/timestep refinements. **Design:** UC01/UC02; SD02/SD05. **Patterns:** MVC, Observer, State. **Type:** deterministic.

**Files, responsibilities and key methods:** [ApplicationController](../src/main/java/com/sayswear/app/ApplicationController.java) schedules `tickFromClock()`/`advance()`. [GameModel](../src/main/java/com/sayswear/game/GameModel.java) owns `update()`/`evaluatePosition()`. [ActiveState](../src/main/java/com/sayswear/game/ActiveState.java) subdivides `update()` for collision checks; [Player](../src/main/java/com/sayswear/game/Player.java) applies `move()` and normalized velocity. [Level](../src/main/java/com/sayswear/game/Level.java) supplies `isSafe()`/`isGoal()`. [JavaFxGameView](../src/main/java/com/sayswear/view/JavaFxGameView.java) and [CliView](../src/main/java/com/sayswear/view/CliView.java) implement snapshot `render()`.

**Input → collaboration → result:** accepted directional controls set the game's held inputs. Loop ticks advance the player, check the corridor and publish snapshots. JavaFX draws the level/player; the CLI presents an adaptive map and exact position/velocity. Neither view simulates movement. Diagonal speed is normalized.

**Verification:** saved movement/geometry/goal tests and terminal tests pass; historical GUI/packaged CLI checks exercised actual movement. **Video: PENDING.** Show movement in both interfaces, a diagonal and state metrics.

## F02 — Continuous Voice-Control Session

**Reference/status:** Stage 1 F02; implemented, refined by D11/D12. **Design:** UC01; SD01. **Patterns:** MVC, Adapter. **Type:** deterministic session management around ASR.

**Files, responsibilities and key methods:** [JavaFxGameView](../src/main/java/com/sayswear/view/JavaFxGameView.java) routes `requestStartVoice()`/`requestStopVoice()`. [ApplicationController](../src/main/java/com/sayswear/app/ApplicationController.java) coordinates `startVoiceControl()`, `stopVoiceControl()`, `handleVoiceReady()` and `handleVoiceFailure()`. [VoiceInputService](../src/main/java/com/sayswear/voice/VoiceInputService.java) owns `start(epoch)`, capture-worker `run(session)`, `stop()` and `close()`. [MicrophoneDevice](../src/main/java/com/sayswear/voice/MicrophoneDevice.java) opens Java Sound and supplies `readSamples()`, which the service's capture worker calls repeatedly.

**Input → collaboration → result:** Start listening creates a session and initializes recognition on a worker. After the microphone opens, readiness changes the visible status. Capture continues across utterances until stop/failure. Stop & release invalidates old work and releases controls/capture. CLI text substitutes for this audio entry point.

**Verification:** six controlled voice-service tests cover readiness, epoch isolation, nonblocking startup/stop, failure and PCM conversion. Specific real microphone session/recovery coverage remains unverified. **Video: PENDING.** Show starting, several utterances in one session, stopping and relevant unavailable-input behavior.

## F03 — Streaming Speech Transcription

**Reference/status:** Stage 1 F03; implemented. **Design:** UC01; SD01. **Patterns:** Adapter, MVC. **Type:** AI-based recognition with deterministic delivery.

**Files, responsibilities and key methods:** [StreamingSpeechRecognizer](../src/main/java/com/sayswear/voice/StreamingSpeechRecognizer.java) defines `start()`/`acceptAudio()`/`stop()`. [SherpaOnnxAdapter](../src/main/java/com/sayswear/voice/SherpaOnnxAdapter.java) loads the online model and decodes incrementally in `acceptAudio()`. [VoiceInputService](../src/main/java/com/sayswear/voice/VoiceInputService.java) forwards `partial()` and advances utterance identity in `endpoint()`. [TranscriptUpdate](../src/main/java/com/sayswear/agent/TranscriptUpdate.java) carries identity; [ApplicationController.handleTranscript()](../src/main/java/com/sayswear/app/ApplicationController.java) versions submissions. [JavaFxGameView.showTranscript()](../src/main/java/com/sayswear/view/JavaFxGameView.java) displays changing words.

**Input → collaboration → result:** successive audio chunks produce changed partial text before the utterance ends. The controller filters duplicates and submits current revisions. Endpoint detection starts a new utterance identity without requiring another recording. Transcript output is separate from the eventual accepted movement decision.

**Verification:** the historical native WAV check produced 17 partials, one endpoint, expected phrases and no errors; it opened no microphone. Live speech accuracy/latency remain unmeasured. **Video: PENDING.** Show partial words changing during speech, then the separate action feedback.

## F04 — Natural-Language Movement Interpretation

**Reference/status:** Stage 1 F04; implemented with local SemIf/Qwen instead of the original provider assumption (D01/D13). **Design:** UC01/UC02; SD01/SD02. **Patterns:** Adapter, Command, MVC. **Type:** hybrid.

**Files, responsibilities and key methods:** [VoiceCommandAgent.interpret()](../src/main/java/com/sayswear/agent/VoiceCommandAgent.java) schedules the [DecisionModel.decide()](../src/main/java/com/sayswear/agent/DecisionModel.java) contract. [OpenJevAdapter](../src/main/java/com/sayswear/agent/OpenJevAdapter.java) submits HTTP in `decide()` and validates the allowlisted output in `parseResponse()`. The [SemIf bridge](../tools/semif/server.py) runs `DecisionEngine.decide()` with genuine Qwen logits. [ControlManager.accept()/plan()](../src/main/java/com/sayswear/control/ControlManager.java) creates commands; [ActionExecutor.execute()](../src/main/java/com/sayswear/control/ActionExecutor.java) applies them.

**Input → collaboration → result:** spoken text or typed `go up` reaches the same agent. Qwen chooses a bounded semantic action; Java stamps trusted identity, validates it and changes the game through commands. The view shows accepted type/directions or no-action/error feedback. The model never calls game methods. `--demo` explicitly selects a deterministic parser and is not the AI implementation.

**Verification:** recorded genuine-model and packaged CLI checks exercised the complete path. Finite samples do not guarantee every paraphrase. Current input errors stop controls; an unclear `NO_ACTION` leaves existing movement unchanged. **Video: PENDING.** Use the real model for direction/stop and an unclear or unsupported instruction.

## F05 — Context-Aware Commands

**Reference/status:** Stage 1 F05; implemented and extended with repeat memory (D18). **Design:** UC03; SD03. **Patterns:** Adapter, Command. **Type:** hybrid.

**Files, responsibilities and key methods:** [ControlContext](../src/main/java/com/sayswear/agent/ControlContext.java) stores held directions, prior accepted text and [MovementIntent](../src/main/java/com/sayswear/agent/MovementIntent.java). [VoiceCommandAgent.interpret()](../src/main/java/com/sayswear/agent/VoiceCommandAgent.java) freezes one utterance's baseline; [OpenJevAdapter.decide()](../src/main/java/com/sayswear/agent/OpenJevAdapter.java) sends it to Qwen. [ControlManager.getContext()/accept()/plan()](../src/main/java/com/sayswear/control/ControlManager.java) maintain/replay memory; [ApplicationController.acceptanceMessage()](../src/main/java/com/sayswear/app/ApplicationController.java) explains missing history.

**Input → collaboration → result:** `other way` uses initially held directions; `keep going` preserves movement/timer. After timed movement expires, `again` asks Qwen for `REPEAT_LAST`; Java creates fresh commands from the stored direction/duration. With no history, repeat changes nothing and explains why. The model receives no map or goal.

**Verification:** frozen-context/history/no-history tests pass; eight recorded CPU repeat/history samples passed under a diagnostic deadline. Updated default-budget GPU verification is pending. **Video: PENDING.** Demonstrate reversal, keeping movement, repeat after expiry and repeat after restart without history.

## F06 — Live Command Correction / Interruption

**Reference/status:** Stage 1 F06; implemented with D11/D12/D15/D16 refinements. **Design:** UC04; SD04. **Patterns:** Adapter, Command. **Type:** hybrid.

**Files, responsibilities and key methods:** [ApplicationController.handleTranscript()/submitTextCommand()](../src/main/java/com/sayswear/app/ApplicationController.java) create newer versions. [VoiceCommandAgent.interpret()/reset()](../src/main/java/com/sayswear/agent/VoiceCommandAgent.java) bound work to two active calls and one latest pending input. [ControlManager.beginInput()/accept()/plan()](../src/main/java/com/sayswear/control/ControlManager.java) reject stale results and prepare replacements; [ActionExecutor.cancelAll()/execute()](../src/main/java/com/sayswear/control/ActionExecutor.java) replace active timed work.

**Input → collaboration → result:** `right — no, left` produces revised text. Qwen interprets the correction; only a matching version/epoch/control revision may act. Java cancels old timed work before applying replacement directions. A typed instruction supersedes remaining partials of an older spoken utterance. Cancellation cannot later release a newer command.

**Verification:** stale-result/replacement tests pass. The historical pre-repeat four-partial GPU burst returned LEFT after 990.7 ms within 2000 ms; this is not current repeat-schema timing evidence. Specific microphone correction coverage is unverified. **Video: PENDING.** Correct active movement and interrupt a timed action.

## F07 — Combined Directional Commands

**Reference/status:** Stage 1 F07; implemented. D13 describes axis readouts. **Design:** UC05; SD04. **Patterns:** Adapter, Command. **Type:** hybrid.

**Files, responsibilities and key methods:** [DecisionEngine.decide()](../tools/semif/server.py) obtains model-selected semantic directions; [OpenJevAdapter.decide()](../src/main/java/com/sayswear/agent/OpenJevAdapter.java) transmits the request and validates the returned choice. [ControlManager.validate()/plan()](../src/main/java/com/sayswear/control/ControlManager.java) enforce a compatible set. [KeyDownCommand.execute()](../src/main/java/com/sayswear/control/KeyDownCommand.java) calls `GameModel.press()`; [Player.refreshVelocity()](../src/main/java/com/sayswear/game/Player.java) normalizes speed.

**Input → collaboration → result:** `up and right` selects both axes. Java releases unwanted old inputs, presses the desired pair and displays diagonal movement/held directions. Opposing pairs are excluded from the provider vocabulary and rejected by Java validation.

**Verification:** compatibility/normalized-speed tests pass; recorded model samples covered all four perpendicular pairs. **Video: PENDING.** Show combined movement and the actual response to a contradictory instruction; do not claim universal language accuracy.

## F08 — Magnitude / Timed Commands

**Reference/status:** Stage 1 F08; implemented with timed replay under D18. **Design:** UC05/UC03; SD04/SD03. **Patterns:** Adapter, Command. **Type:** hybrid.

**Files, responsibilities and key methods:** [OpenJevAdapter.decide()](../src/main/java/com/sayswear/agent/OpenJevAdapter.java) limits semantic durations to 100/200/400 ms. [ControlManager.validate()/plan()/advance()](../src/main/java/com/sayswear/control/ControlManager.java) validate/create commands and change revision at expiry. [TimedPressCommand.execute()/advance()/cancel()](../src/main/java/com/sayswear/control/TimedPressCommand.java) own duration; [ActionExecutor.advance()](../src/main/java/com/sayswear/control/ActionExecutor.java) advances/removes timers.

**Input → collaboration → result:** `a little left` selects a 200 ms hold. Java presses, then releases the direction automatically. Equivalent partials within that utterance cannot extend its timer. A fresh `again` creates a new timed command from remembered semantics. Timing uses the simulation loop, not independent delayed callbacks.

**Verification:** expiry, quantization, interruption and repeat tests pass. Packaged live CPU CLI replay used a 30000 ms diagnostic interpretation budget; the movement hold remained 200 ms of simulation time. **Video: PENDING.** Show temporary movement, automatic release and fresh repeat after expiry.

## F09 — Control-State and Conflict Management

**Reference/status:** Stage 1 F09; implemented with failure/replay refinements. **Design:** UC01–UC06; SD01/SD03/SD04/SD05. **Patterns:** Command. **Type:** deterministic.

**Files, responsibilities and key methods:** [GameModel](../src/main/java/com/sayswear/game/GameModel.java) owns held inputs. [ControlManager.beginInput()/accept()/validate()/reset()](../src/main/java/com/sayswear/control/ControlManager.java) own version/epoch/revision and memory. [ActionDecision.withRequest()](../src/main/java/com/sayswear/agent/ActionDecision.java) receives local metadata from [VoiceCommandAgent](../src/main/java/com/sayswear/agent/VoiceCommandAgent.java). [ActionExecutor.cancelAll()](../src/main/java/com/sayswear/control/ActionExecutor.java), [ReleaseAllCommand.execute()](../src/main/java/com/sayswear/control/ReleaseAllCommand.java) and [ApplicationController.failCurrent()](../src/main/java/com/sayswear/app/ApplicationController.java) neutralize current failures.

**Input → collaboration → result:** each proposal is checked against current transcript/session/control revision before execution. Invalid current actions and current timeouts release controls; stale responses/failures cannot alter a newer session. Duplicate partials cannot rearm timers/repeats. Pause/fall/restart/current failure clear memory; ordinary stop, expiry and keep/no-action preserve it.

**Verification:** tests cover malformed/stale proposals, opposite directions, duplicate timers, expiry revision, reset/history and late failures. **Video: PENDING.** Show held-state feedback, interruption and current unavailable-service cleanup; explain the freshness guard without substituting mocked AI for real behavior.

## F10 — Fall Detection and Start Recovery

**Reference/status:** original F10 — Fall Detection and Checkpoint Recovery; modified and implemented. D17 removes checkpoint progression. **Design:** UC06; SD05. **Patterns:** State, Observer, Command. **Type:** deterministic.

**Files, responsibilities and key methods:** [GameModel.evaluatePosition()/resetToStart()](../src/main/java/com/sayswear/game/GameModel.java) detect the unsafe footprint and restore `Level.start()`. [FallingState.update()](../src/main/java/com/sayswear/game/FallingState.java) initiates reset; [RespawningState.update()](../src/main/java/com/sayswear/game/RespawningState.java) restores active play. [ApplicationController.onGameEvent()/invalidateControls()](../src/main/java/com/sayswear/app/ApplicationController.java) invalidate AI work and controls; [GameView.onGameEvent()](../src/main/java/com/sayswear/view/GameView.java) presents lifecycle feedback.

**Input → collaboration → result:** human-directed movement leaves the corridor. FallingState and the fall event trigger epoch invalidation, interpretation/timer cancellation and release. Later lifecycle ticks restore start position and zero velocity, then ActiveState. If listening was requested, voice capture restarts; movement requires fresh input. This deliberately changes recovery from preserving checkpoint progress to losing all progress on a fall.

**Verification:** saved tests check repeated falls after level progress return to start and reject pre-fall responses. Specific real microphone restart/recovery coverage remains unverified. **Video: PENDING.** Show a fall, neutral recovery at start and fresh movement afterward.

## Evidence and demonstration completion

The saved ordinary suite contains **76 passing tests**. Real-model, native-ASR and JavaFX smoke tasks are separate opt-in checks; [validation](validation.md) records their historical results and diagnostic deadlines. Demo results establish application wiring, not AI quality. General user gameplay acceptance does not identify a tested interface or microphone path. Finite samples cannot establish general language reliability.

Before submission, replace every PENDING video with an accessible link and labeled timestamp. Each segment must identify the feature, show actual input/output, explain AI versus deterministic work, and cover relevant alternatives/errors. The collection must show both GUI and CLI. Full Stage 3 testing and KUMA evaluation remain separate deliverables.
