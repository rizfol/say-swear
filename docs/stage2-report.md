# Say Swear

**Stage 2 Implementation Report**

EECS 3311 - Fall 2026 | Status date: October 27, 2026 (Toronto)

**Review draft - submission evidence is still pending.** This report describes the public Stage 2 implementation. The user reports having played the game and confirms that it works properly. The instructor confirmed that the existing early commit history works for weekly-progress evidence. Videos/timestamps and focused source review remain pending; AI usage and cost figures are provisional student estimates where stated. Submission is due November 10, 2026.

This report accounts for all ten Stage 1 features, explains the five implemented patterns, and connects the current Java source to the revised UMLet diagrams. It separates recorded verification from evidence that still needs to be supplied.

<!-- Authoring: This is the editable report source. The builder expands maintained document includes below into the PDF. Run python scripts/build-stage2-report.py after updating mappings, recordings, review outcomes or other evidence. -->

## 1. Project overview and implementation status

### Problem, users, and scope

Say Swear is a small 2D precision game for players who want to navigate a narrow winding path using natural-language instructions. One player character moves over a void toward one goal. Leaving the safe path cancels movement and returns the character to the level start. The implemented scope contains one fixed level, four-direction movement, combined directions, bounded timed movements, falling, and start recovery. There are no checkpoints, enemies, jumping, inventory, procedural generation, autonomous navigation, or pathfinding.

The human chooses every turn and its timing. A JavaFX GUI displays the actual level and accepts continuous microphone input, with a text-instruction field for the same interpreter. A Java terminal CLI accepts natural-language text and displays a map, coordinates, velocity, held directions, transcript/action feedback, and lifecycle. Both frontends use the same Java 21 controller, game, agent, and control objects composed by `Main.compose()`; the CLI substitutes typed text for capture/transcription.

### AI integration and deterministic execution

GUI audio goes through `VoiceInputService` and `StreamingSpeechRecognizer`/`SherpaOnnxAdapter`. sherpa-onnx produces changing partial transcripts and endpoints. Audio is not streamed to SemIf. Eligible text and a frozen `ControlContext` go through `ApplicationController`, `VoiceCommandAgent`, and `DecisionModel`/`OpenJevAdapter` to the local SemIf service running Qwen3.5 4B. Python hosts that external inference runtime; the application, simulation, and frontends are Java.

SemIf scores finite alternatives from real model logits. The bridge composes one of 52 bounded semantic labels across eight action types, including `REPEAT_LAST`. The model receives transcript text, current held directions, prior accepted text, structured last movement, and movement eligibility. Java retains epoch/version/revision identity locally and stamps it on the returned decision. The request contains no map, position, route, start, or goal coordinates. The model interprets the human's command and selects a bounded intent. It does not directly call the game or choose how to reach the goal.

`ControlManager` checks schema, session epoch, transcript version, context revision, and player eligibility. It plans deterministic commands, cancels obsolete work, and executes through `ActionExecutor` and `GameCommand`. Java owns timer duration/expiry, repeat-memory resolution, conflict validation, simulation, collisions, observer cleanup, and fall recovery. `MovementIntent` remains available after a timed action releases its input, so a fresh `again` repeats the remembered movement rather than the release action.

The shared application loop serializes game/control changes. Worker threads handle microphone and HTTP work; JavaFX receives coalesced presentation updates. The current decision deadline is 2000 ms including agent queue time. New instructions supersede older requests; stale successes and failures cannot affect a newer session. The explicit `--demo` option uses a labelled deterministic parser for wiring checks. It is not an LLM and is never an automatic fallback.

### Implementation and evidence status

| Area | Current status | Evidence or remaining work |
|---|---|---|
| F01-F10 and both frontends | Implemented, with documented refinements | Complete feature/method/pattern mapping in section 2; genuine model and deterministic paths are distinguished. |
| Five course patterns | Implemented | Section 3 explains concrete participants and source methods; Appendix A contains the updated class diagram. |
| User gameplay | User-reported acceptance | The user states that they played the game and it works properly. Interface, microphone mode, individual scenarios, and code-review scope were not specified. |
| Ordinary Java verification | Recorded pass | Latest saved suite: October 19, 2026, 76 tests, zero failures/errors/skips. No application tests were rerun for report preparation. |
| Live and platform coverage | Partial recorded evidence | Native sample-audio ASR, historical real-model checks, and packaged CLI checks exist. Specific microphone/recovery, native Linux, and updated repeat-enabled 2000 ms GPU performance remain unverified. |
| AI tool accounting | Provisional student report | Approximately 6 billion Codex tokens and CAD $282.50 subscription spending are reported; local inference tokens and compute costs are unmeasured. Confirm period, currency, and allocation. |
| Weekly progress evidence | Instructor accepted existing history | Section 6 lists actual early code/test commits and the response to the student's early-completion question. Commit dates are unchanged. |
| Submission evidence | PENDING | All feature videos and detailed source-review outcomes remain to be supplied; AI accounting still needs final confirmation. |

### Reproduction and configuration

The repository includes a Gradle wrapper, Java dependencies, GUI/CLI launchers for Windows and Linux, and pinned setup instructions for SemIf and streaming ASR. Follow the root [README](../README.md), [SemIf setup](semif-setup.md), and [ASR setup](asr-setup.md). Start the real local service, wait for readiness, then launch GUI or CLI without demo mode. CLI play needs neither a microphone nor an ASR model.

Local inference requires no API key. `.env.example` contains safe environment-variable examples; the application does not auto-load `.env`. Configuration can be supplied through environment variables or launcher options. `.gitignore` excludes local environment files, downloaded weights, caches, logs, and build outputs. Report aggregate usage/cost totals with their accounting basis; private billing documents, account identifiers, and machine-specific paths are not needed.

Source references below are repository-relative paths. The supplied UMLet SVGs are preserved unchanged; their large-format PDF appendix pages allow readable zooming. Videos must eventually link to recordings accessible to the instructor and teaching assistants.

## 2. Feature to implementation mapping

All ten original feature IDs are accounted for. F10 replaces checkpoint recovery with return-to-start recovery. Repeat memory extends F05/F08/F09. Supporting input/session/display refinements are documented as changes, not counted as additional features.

[Maintained feature mapping](feature-implementation-mapping.md)

<!-- report-include: feature-implementation-mapping.md | before=## Evidence and demonstration completion -->

## 3. Implemented design patterns

[Maintained pattern explanations](implemented-design-patterns.md)

<!-- report-include: implemented-design-patterns.md -->

### Current UML and implementation contracts

The class model shows `ActionDecision`/`ControlContext` epoch and revision metadata, `ControlManager` freshness state, `ActionExecutor.activeTimed`, and the remaining duration owned by `TimedPressCommand`. These make concurrency and timer ownership structurally visible, as suggested by the Stage 1 feedback. F02/F03/F04 have separate acceptance criteria in sections 2 and 7.

| Diagram | Current UMLet SVG | PDF appendix |
|---|---|---|
| Class model | [class-diagram.svg](uml/class-diagram.svg) | A |
| Use cases UC01-UC06 | [use-case-diagram.svg](uml/use-case-diagram.svg) | B |
| SD01 - GUI voice command | [sd01-gui-voice-command.svg](uml/sequence-diagrams/sd01-gui-voice-command.svg) | C |
| SD02 - CLI text command | [sd02-cli-text-command.svg](uml/sequence-diagrams/sd02-cli-text-command.svg) | D |
| SD03 - Contextual command and repeat | [sd03-contextual-command.svg](uml/sequence-diagrams/sd03-contextual-command.svg) | E |
| SD04 - Correction, combined and timed actions | [sd04-live-correction.svg](uml/sequence-diagrams/sd04-live-correction.svg) | F |
| SD05 - Fall and start recovery | [sd05-fall-recovery.svg](uml/sequence-diagrams/sd05-fall-recovery.svg) | G |

## 4. Changes and rationale

The original Stage 1 snapshot is retained in Git history at `56da5fd95c43dde4760032e59a36787a2c74049c`. The revised design and current diagrams reflect the implemented local-model, repeat-memory, start-recovery, and terminal-display contracts. The following record explains changes against the earlier baseline rather than treating the revised design as proof that no changes occurred.

[Maintained change record](design-changes.md)

<!-- report-include: design-changes.md -->

## 5. AI tools and collaboration log

The tool/model/task/accounting table and current human contribution below are part of the maintained [collaboration log](ai-collaboration-log.md). Codex usage and subscription cost are student estimates, not measured platform exports. Local token and compute totals are identified as unavailable rather than assumed zero.

<!-- report-include: ai-collaboration-log.md | before=## Representative interactions -->

### Representative implementation interactions

| Interaction | Human contribution | AI contribution and actual result |
|---|---|---|
| C01 - Shared Java design | Specified small scope, both frontends, and human navigation; authorized implementation. | Implemented game/control, adapters, frontends, and controller coordination; recorded build and integration checks with their actual scope. |
| C02/C08-C11 - Correct and validate the backend | Selected Qwen3.5 4B and clarified that the intended backend was SemIf. | Corrected the earlier provider assumption, isolated native-runtime failures, factored bounded model choices, and recorded real inference/CLI cases separately from deterministic checks. |
| C04/C09 - Freshness and deadlines | Required live correction and interruption without stale actions. | Added epoch/version/revision validation, cancellation, bounded requests, and queue-inclusive expiry. Historical burst timing is preserved without claiming current repeat-enabled performance. |
| C12 - Start recovery | Rejected checkpoints because they conflicted with the rage-game concept. | Removed checkpoint progression and changed F10, state transitions, tests, views, and traceability to neutral start recovery. |
| C13 - Start marker | Requested a red arrow with white YOU ARE HERE text. | Added the presentation cue at the neutral starting position; no additional gameplay feature was counted. |
| C14 - Repeat after timer expiry | Identified that replaying the latest release action could make `again` ineffective; requested repeat support and diagram readability fixes. | Introduced `MovementIntent` and `REPEAT_LAST`; retained movement history after expiry, prevented duplicate same-utterance replay, and recorded repeat checks and remaining timing limits. |
| C15-C16 - Terminal display and diagrams | Reported scrolling CLI output and requested diagram consistency. | Added JLine in-place display and editable input, direct launch, plain fallback, terminal tests, and corresponding diagram changes. |
| C17 - Linux ASR setup | Identified the missing Linux setup script and requested it. | Added pinned/resumable/verified setup with fixture checks; recorded that native Linux recognition was not exercised. |

These representative interactions document actual scope decisions, corrections, generated work, and checks. The full log retains the original Task, Input/Instruction, AI Contribution, Human Contribution, and Outcome fields for C01-C17. The user's current gameplay acceptance is recorded above; it does not imply that every generated source file or test has received detailed human review.

## 6. Development and verification summary

### Current acceptance and weekly activity

<!-- report-include: validation.md | before=**Current diagram artifacts:** -->

### Executed verification and limits

| Check | Recorded outcome | What it establishes / limit |
|---|---|---|
| Ordinary Java suite and distribution | Latest saved October 19 suite: 76 passing tests, zero failures/errors/skips; build/installDist recorded as passing. | Domain/control, freshness/cancellation, agent scheduling, mock HTTP, capture lifecycle, and CLI behavior. Does not run a real LLM or microphone. |
| SemIf bridge contracts | Nine controlled-score tests passed after repeat support. | Request/schema/action composition and structured memory. Does not measure real-model language accuracy. |
| Native ASR sample | Real 6.63-second WAV yielded 17 changing partials and one endpoint, expected phrases, zero errors. | Actual sherpa-onnx native/model streaming recognition; no microphone was opened. |
| Earlier genuine-model checks | Twenty baseline cases passed; isolated requests 242.2-503.1 ms with a 15-second diagnostic deadline; four-partial burst returned latest LEFT correction after 990.7 ms within 2000 ms. | Historical pre-repeat semantic/agent/CLI evidence. Not a measurement of current repeat-enabled or microphone latency. |
| Repeat-enabled CPU checks | Twenty baseline plus eight repeat/history cases passed with a 60000 ms diagnostic cap; packaged real CLI used 30000 ms. | Genuine Java/SemIf/Qwen repeat semantics and execution. Does not establish the default 2000 ms performance gate. |
| GUI and packaged CLI | Recorded JavaFX text-entry, movement, Stop & release, restart, packaged real/demo CLI, native Windows terminal, and piped fallback checks passed. | Actual application wiring and terminal behavior; demo and genuine-model results remain distinguished. |
| Linux ASR setup | Bash/GNU fixture checks passed on Windows: fresh/cached, resume, invalid archive, unsafe path, and interrupted extraction cases. | Downloader/control flow. Native Linux recognition, GUI, and interactive terminal behavior remain unverified. |
| User gameplay | User reports having played the game and that it works properly. | General gameplay acceptance. Frontend, microphone mode, command coverage, detailed source review, and measured latency were not specified. |

Full commands, earlier failures, correction history, counts, diagnostic settings, and limits remain in the [validation record](validation.md). No application/model tests were rerun merely to prepare this report. Report generation checks document structure, links, diagram embedding, and rendered page layout separately from application verification.

### Important defects found and corrected

- **Incorrect provider identity:** replaced the unrelated hosted-service assumption with the user-selected local SemIf/Qwen runtime behind the same Java interface.
- **Native inference startup:** isolated an incompatible native instruction requirement and documented pinned compatible runtime setup without changing the application core.
- **Model direction/correction failures:** factored action/axes/duration readouts and used generic word-boundary formatting only for model-selected SWITCH axes; Qwen still chooses intent and directions.
- **Burst deadline failure:** increased the original queue-inclusive 1000 ms budget to 2000 ms after the unchanged correction burst expired; the historical pass and outstanding current repeat-enabled check remain distinct.
- **Repeat after automatic release:** introduced structured movement memory so expiry does not replace remembered movement with release, and same-utterance partials cannot rearm it repeatedly.
- **Scrolling CLI input:** replaced repeated full-screen printing with a fixed game panel and separate editable prompt, while retaining plain output for redirected/unsupported terminals.
- **Interrupted ASR extraction:** added a verified completion marker so a failed extraction is repaired rather than treating truncated files as ready.

### Focused human review and remaining submission work

The completed gameplay session is already accepted. Detailed source/test review has not yet been reported complete. Record its actual scope, corrections, and outcome in the collaboration log, including understanding of the shared core, the AI/execution boundary, freshness/cancellation, timer/repeat semantics, lifecycle recovery, and five pattern roles.

Feature video URLs/timestamps remain **PENDING**. The AI accounting section records the student's approximate token and subscription figures with its unmeasured categories and items to confirm. The instructor accepted the existing early commit history as weekly-progress evidence; section 6 gives its actual dates and links. Specific microphone/recovery coverage, native Linux runtime coverage, and repeat-enabled performance under the default 2000 ms deadline remain unverified. Sections 5-7 retain these gaps explicitly without inferring them from general gameplay acceptance.

Stage 2 requires basic implementation verification. Full deterministic/KUMA evaluation remains a later-stage requirement and is not represented as completed or included in this public Stage 2 report.

## 7. Demonstration index

<!-- report-include: stage2-demonstration-index.md -->

### Diagram appendices

The following pages embed the supplied UMLet SVGs as vectors without changing the diagram files. Pages use larger formats to preserve readable text at normal zoom. Source links are also indexed in section 3. These diagrams explain design and runtime contracts; they do not replace the required running-application videos.

<!-- report-diagram: uml/class-diagram.svg | Appendix A - UML class diagram -->
<!-- report-diagram: uml/use-case-diagram.svg | Appendix B - Use-case diagram -->
<!-- report-diagram: uml/sequence-diagrams/sd01-gui-voice-command.svg | Appendix C - SD01 GUI voice command -->
<!-- report-diagram: uml/sequence-diagrams/sd02-cli-text-command.svg | Appendix D - SD02 CLI text command -->
<!-- report-diagram: uml/sequence-diagrams/sd03-contextual-command.svg | Appendix E - SD03 contextual command and repeat -->
<!-- report-diagram: uml/sequence-diagrams/sd04-live-correction.svg | Appendix F - SD04 correction, combined and timed commands -->
<!-- report-diagram: uml/sequence-diagrams/sd05-fall-recovery.svg | Appendix G - SD05 fall and start recovery -->
