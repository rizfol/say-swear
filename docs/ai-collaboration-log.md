# AI-Human Collaboration Log

This is a representative record for Stage 2, following sections 9–10 of `Project-Instruction.pdf`. It records actual instructions, generated work, and corrections from this development session. It does not imply that the student has reviewed or accepted every generated source file.

**Latest user change:** C14 records explicit repeat support and the fix for overlapping diagram text. C12–C13 retain the earlier checkpoint-removal and start-arrow changes. Repeat tests and CPU semantic/CLI checks passed; All seven current diagrams rendered and the class layout passed preview/geometry checks. The updated default-budget GPU burst check remains outstanding. Prior outcomes below remain historical evidence in [validation](validation.md).

## Tools and models

| Tool/platform | Model | Purpose | Major project areas |
|---|---|---|---|
| OpenAI Codex coding agent, including delegated coding/review agents | GPT-6 model family, as identified by the session configuration. A precise served model snapshot is not exposed here. | Read the rubric/design, implement Java components, inspect interfaces and concurrency, author tests, and prepare traceability documentation. | Shared game/control core, JavaFX and CLI views, controller/agent integration work, adapter research, tests, build configuration, and these development records. |

The AI models intended to run **inside Say Swear** are distinct from the development assistant. The user selected **Qwen3.5 4B** and identified the intended backend as **SemIf**. The assistant then researched [openjev.com](https://openjev.com) and [TheoLeeCJ/SemIf-OpenJev](https://github.com/TheoLeeCJ/SemIf-OpenJev). Streaming recognition uses sherpa-onnx 1.13.5 and English streaming Zipformer. Final checks passed real ASR, 20 genuine model cases, the four-partial agent burst, and packaged live CLI interaction. Historical failures and remaining human/microphone review are recorded below and in [validation](validation.md).

## Representative interactions

### C01 — Preserve the approved design during implementation

- **Task:** Begin Stage 2 from the existing Stage 1 Java design.
- **Input/Instruction:** The user authorized implementation after the design stage, retaining the shared GUI/CLI core, bounded language interpretation, small game scope, and human responsibility for navigation.
- **AI Contribution:** Read the course instructions and original design; divided implementation into domain/control, provider/voice, presentation, and application coordination work. Created the game/control source and both presentation implementations without adding autonomous navigation.
- **Human Contribution:** Supplied the project concept, rubric, constraints, and implementation authorization. These requirements determined scope and the main architecture. Human review of the resulting Java implementation is still pending.
- **Outcome:** Source files follow the designed packages and responsibilities. The complete Java build/distribution assembly passed with 63 ordinary tests; seven Python bridge tests, JavaFX/native ASR smoke tests, 20 final model cases, the unchanged correction burst, and packaged real/demo CLI checks passed. Human code/usability review and microphone/gameplay checks remain pending.

### C02 — Correct the identity of the decision backend

- **Task:** Connect the intended language model through the project's adapter boundary.
- **Input/Instruction:** The user selected **Qwen3.5 4B** and clarified the intended backend with the reply **“SemIf.”**
- **AI Contribution:** Initial research had conflated unrelated projects named OpenJev, including the hosted API described in the original Stage 1 report. That provider assumption was incorrect. After the clarification, the assistant researched the official website/repository and redirected integration and adapter work to SemIf.
- **Human Contribution:** Identified the intended project as SemIf and selected the model. These human choices corrected the assistant's earlier provider assumption.
- **Outcome:** `OpenJevAdapter` remains the architectural class name. Its local bridge uses genuine SemIf commit `23cf1f39fc9534fe81437200959b6dfc7106e45a` and Qwen3.5 4B. Native compatibility was resolved and the final real-model gates passed; the development sequence is recorded in C08–C11.

### C03 — Implement two presentations over one game

- **Task:** Make the same game and control state accessible graphically and from a terminal.
- **Input/Instruction:** Preserve JavaFX for GUI play, typed natural-language CLI commands, no direct movement keys, and one shared application/game/agent core.
- **AI Contribution:** Implemented `GameView`, `JavaFxGameView`, `CliView`, and GUI styling. The GUI projects immutable game snapshots to a canvas and coalesces updates onto the JavaFX thread. The CLI formats the same snapshot, displays exact coordinates and held directions, throttles ordinary interactive frames, and suppresses periodic maps in scripted mode.
- **Human Contribution:** Required both frontends and rejected a design with separate implementations. The added GUI text-instruction field and session controls await human usability review.
- **Outcome:** Both implementations delegate actions to the shared controller. A later desktop smoke test passed actual text-field submission, player movement, Stop & release, and Restart run. The assistant visually inspected the running scene; student usability review remains pending. The desktop test used the explicitly labelled deterministic demo model.

### C04 — Make command cancellation and freshness explicit

- **Task:** Prevent older interpretations or timed releases from overriding newer instructions.
- **Input/Instruction:** Follow the Stage 1 distinction between bounded AI interpretation and deterministic Java validation/execution; preserve correction, combined inputs, timed commands, and fall cleanup.
- **AI Contribution:** Implemented `ControlManager`, command objects, and `ActionExecutor` using epoch/version/revision validation, compatible direction sets, semantic duplicate suppression, and cancellation before replacement. Retained authoritative held directions in `GameModel`.
- **Human Contribution:** Defined these behaviors in the design requirements and supplied examples such as “right — no, left” and “a little left.” Detailed source-level review and acceptance of the generated implementation remain pending.
- **Outcome:** Deterministic movement, cancellation, stale-result rejection, timer behavior, and fall/recovery checks passed. The expanded 63-test suite additionally verified agent context freezing, bounded concurrency/deadlines, reset behavior, HTTP-adapter validation, and voice-service lifecycles using controlled dependencies. Genuine model cases passed sampled contextual, corrective, combined, and timed instructions; the later burst investigation is recorded in C09.

### C05 — Write behavior-focused presentation tests

- **Task:** Check terminal output and input behavior without depending on a GUI or live model.
- **Input/Instruction:** Test meaningful behavior while keeping the presentation separate from game simulation.
- **AI Contribution:** Added `CliViewTest` cases for map/state fidelity, lifecycle output in scripted mode, deterministic throttling using an injected clock, and preservation of raw command text and EOF.
- **Human Contribution:** No claim of human test review or manual execution is made. Reviewing the assertions and actual results is pending.
- **Outcome:** All four CLI tests passed in the ordinary runs. The JavaFX smoke test also passed. A later packaged `scripts/check-cli.py --demo` run passed timed movement/expiry, state, restart, and quit checks; it used the explicit deterministic demo interpreter, not the model.

### C06 — Keep development records consistent with evidence

- **Task:** Prepare the Stage 2 feature mapping, collaboration record, and design-change explanation.
- **Input/Instruction:** Follow the full course instructions, identify implemented/planned files honestly, record the backend correction, and avoid claiming unverified model integration or human approval.
- **AI Contribution:** Inspected source and test reports; authored this log, the [feature mapping](feature-implementation-mapping.md), [design-change record](design-changes.md), and [validation record](validation.md). Replaced provisional test notes with verified outcomes and retained historical failures and remaining human checks.
- **Human Contribution:** Supplied the course instructions and corrected the intended provider/model identity. Final review of the documentation is pending.
- **Outcome:** The records distinguish generated source, final executed checks, historical failures/corrections, and actual human contributions. Every feature has an explicit implementation status with evidence and limits.

### C07 — Verify streaming ASR without microphone capture

- **Task:** Make ASR setup reproducible and exercise the actual native recognition adapter.
- **Input/Instruction:** Use the official sherpa-onnx streaming English model, preserve the Java abstraction, and verify with its bundled recording without recording the user's microphone.
- **AI Contribution:** Added a download/extraction script with a pinned observed archive checksum and a tagged native JUnit test. Verified the official asset metadata, downloaded/extracted the model, reran the cached setup path, and fed 20 ms sample chunks followed by silence through `SherpaOnnxAdapter`.
- **Human Contribution:** Specified streaming ASR and the adapter boundary in the original design. No human microphone test or review of recognition accuracy is claimed.
- **Outcome:** Native ASR passed with 17 partial transcripts and one endpoint for the 6.63-second sample, recognized the expected phrases, and returned no error callbacks. Live microphone performance and gameplay latency remain unverified.

### C08 — Investigate the local model's native runtime failure

- **Task:** Start genuine SemIf/Qwen3.5 4B inference and verify the Java adapter against it.
- **Input/Instruction:** Use the user-confirmed SemIf project and Qwen3.5 4B; report actual runtime results and preserve the Java control boundary.
- **AI Contribution:** Implemented the finite-option bridge, pinned dependencies, downloaded the model, and added six hardware-free bridge tests. Investigated an illegal-instruction failure after CUDA offload and identified an AVX512 CPU-library requirement incompatible with the Ryzen 7 5700X's AVX2 support. Created a project-local overlay using the compatible CPU DLL from the same-version official portable wheel alongside unchanged CUDA libraries. When genuine inference misread relative and combined commands, refined the SemIf questions/option descriptions and reran the eight cases; no parser fallback was added.
- **Human Contribution:** Selected the intended provider/model and corrected the earlier provider assumption. No human approval of the native-runtime workaround or model behavior is claimed.
- **Outcome at this earlier checkpoint:** Six bridge tests and eight model samples passed after Qwen3.5 4B Q4_K_M loaded with 34/34 layers on the RTX 2070 SUPER 8 GB. Sample times were 241.9–769.4 ms under a 15-second diagnostic deadline. These initial samples were later expanded; C11 records the final results.

### C09 — Measure queue-inclusive interpretation time

- **Task:** Check how evolving transcript partials behave under the application's decision deadline.
- **Input/Instruction:** Preserve two active requests plus the latest pending input, include queue time in the budget, and discard expired results.
- **AI Contribution:** Sent four same-utterance partials 180 ms apart through the actual `VoiceCommandAgent` and live SemIf/Qwen service. Compared 1000 ms and 2000 ms budgets; recorded pending replacement and each request's completion without treating stale proposals as game actions.
- **Human Contribution:** Required live correction and stale-action prevention in the design. Human review of the revised deadline and actual voice gameplay remains pending.
- **Outcome:** At 1000 ms, the latest request timed out and returned no action. An earlier 2000 ms run returned `SWITCH LEFT` with version 4 after 908.9 ms; the final expanded gate returned it after 990.7 ms. The default is now 2000 ms for queue/runtime overhead, with fail-closed expiry. This is an upper bound rather than a forced delay.

### C10 — Expand live checks after real CLI failures

- **Task:** Check basic direction symmetry and the complete CLI-to-game path instead of relying only on the first eight model samples.
- **Input/Instruction:** Preserve genuine AI interpretation, the fixed action vocabulary, and deterministic Java execution; retain failed observations honestly.
- **AI Contribution:** Ran the actual CLI through `Main`, agent, and adapter; `a little right` from neutral controls returned `NO_ACTION`. Expanded checks found timed right/down and bare up/down gaps. Some more explicit prompts still failed or contaminated an unmentioned axis. Replaced direction-set scoring with smaller independent axes using upstream full-prompt inference. Also replaced unreliable `System.console()` inference with explicit `--quiet-cli`, because Gradle forwards stdin for interactive launches.
- **Human Contribution:** Required natural-language directional control and both shared-core frontends. No human approval of the latest model formulation or a completed play session is claimed.
- **Outcome at this earlier checkpoint:** Independent axes passed 20 isolated cases at 247.7–518.0 ms, but the burst still misread unpunctuated `right no left please` as `RIGHT` after 1091.7 ms. This exposed the difference between timely and correct output. C11 records the retained fix and final rerun. CLI refresh now defaults on, with explicit quiet mode for scripts.

### C11 — Complete the unchanged live gate and packaged checks

- **Task:** Correct the ASR-like input presentation issue and rerun the complete live gate without weakening its latest-correction assertion.
- **Input/Instruction:** Keep interpretation in genuine SemIf/Qwen scoring, retain original transcript evidence, and verify the unchanged four-partial burst plus expanded direction cases.
- **AI Contribution:** Retained full-prompt readouts for action type, vertical axis, horizontal axis, and optional duration. Only when Qwen selects `SWITCH`, present axis text as comma-separated words after splitting commas/whitespace; preserve raw text in Java, the request, and type scoring. Added the idempotence check. This formatting does not identify direction words or parse correction intent. Ran model/agent gates, packaged CLI checks, and the documented launcher.
- **Human Contribution:** Supplied the original correction/continuous-input requirements and chose the model. Student review of this empirical formatting choice, source code, and actual microphone gameplay remains pending.
- **Outcome:** Seven Python tests and both live JUnit methods passed: 20 isolated cases at **242.2–503.1 ms**, plus the unchanged burst returning version 4 `SWITCH LEFT` after **990.7 ms** within 2000 ms. The packaged real-model CLI passed timed movement/expiry/state/restart/quit at x = 11.34; demo mode passed at x = 11.32. `scripts/run-semif.ps1` reached correct `/health` readiness in about nine seconds. Task-owned service processes were stopped afterward. These finite checks do not establish general language reliability or microphone-to-gameplay latency.

### C12 — Remove checkpoints from the rage game

- **Task:** Make every fall lose all progress along the single level.
- **Input/Instruction:** The user explicitly requested removal of checkpoints from the rage game.
- **AI Contribution:** Updated the shared game model to recover at `Level.start()` through `resetToStart()`, removed checkpoint data/events and GUI/CLI checkpoint displays, and revised the current feature mapping and report. Retained the State lifecycle, neutral-input recovery, and invalidation of decisions from before the fall. Prepared regression checks for the changed behavior.
- **Human Contribution:** Changed the approved gameplay scope by rejecting intermediate checkpoint saves and requiring every fall to return to the start. This is a direct user design decision; no broader source review or microphone playtest is implied.
- **Outcome:** F10 is **Fall Detection and Start Recovery**, retaining its ID among the ten features. D17 records the deviation, and UC06/SD05 remain links to the unchanged Stage 1 baseline. The updated full build passed all 63 ordinary tests, including repeated falls after level progress and stale-response rejection at the start. One JavaFX smoke test and visual inspection confirmed the revised display; distribution installation and the packaged demo CLI check passed, with timed movement reaching x = 11.36. Earlier model/ASR results remain historical evidence in [validation](validation.md). Human microphone/gameplay review remains outstanding.

### C13 — Identify the player at the start

- **Task:** Make the starting player position immediately visible in the GUI.
- **Input/Instruction:** The user requested a red arrow pointing at the player, with white **YOU ARE HERE** text inside it.
- **AI Contribution:** Added the cue to the JavaFX canvas, using the shared snapshot to show it while the player is active at the start with neutral controls. It hides when movement begins and returns after restart or respawn.
- **Human Contribution:** Specified the arrow, color, text, and target as a direct interface correction.
- **Outcome:** Compilation and distribution installation passed. A temporary JavaFX preview was visually inspected at the start and after movement: the red arrow points to the player, its white text fits inside, and the cue disappears during movement. The preview used actual game snapshots without opening a microphone or model connection. The README records the visibility rules; no new automated test was added for this presentation change.

### C14 — Repeat accepted movement and refresh current UML

- **Task:** Make `again` repeat the last accepted movement and bring current diagrams into line with the implementation.
- **Input/Instruction:** The user requested `again` support and a fix for overlapping text in the diagram SVGs. Archiving the original diagrams was the implementation team's choice to preserve the Stage 1 record.
- **AI Contribution:** Added `MovementIntent` and `ControlContext.lastMovement`, an explicit `REPEAT_LAST` proposal, and deterministic creation of fresh commands from remembered type/directions/duration. Retained memory after timer expiry, ordinary stop/release, `KEEP_CURRENT`, and `NO_ACTION`; clear it on reset/pause/fall/restart/current error. Added a once-per-utterance repeat guard and a harmless no-history message. Updated the Java/bridge schema to 52 actions and eight types, and the documentation; archived the original diagrams and prepared current sources/SVGs.
- **Human Contribution:** Identified the need to repeat previous movement and reported overlapping diagram text. No completed human code review or microphone test is inferred.
- **Outcome:** The full build/distribution passed with **73 ordinary Java tests**, and **nine bridge tests** passed. Packaged demo CLI verified timed repeat and harmless no-history behavior. A separate CPU service on port 8766 passed the unchanged 20 baseline cases plus eight repeat/history cases with a **60000 ms diagnostic cap**; packaged real CLI passed with a **30000 ms** budget. The task-owned CPU service on port 8766 was stopped; the existing user-owned GPU service on port 8765 was left untouched. The updated 2000 ms GPU burst check remains outstanding, and that service needs restarting for the new schema. Original report links target `uml/stage1/`, where all 14 source/SVG files were hash-verified against the originals. All seven current sources rendered to matching SVGs at `uml/`. The 3905 × 2235 class preview had no visible overlap; SVG geometry checks found 41 separate entity rectangles and all class text inside its containing rectangle. Browser preview was unavailable because the automation runtime assets were missing, so no browser inspection is claimed. [Validation](validation.md) separates these results from prior GPU timing evidence.

### C15 — Keep the CLI game display in place while typing

- **Task:** Stop periodic game maps from scrolling the terminal or disturbing an unfinished instruction.
- **Input/Instruction:** The user requested in-place game redraws with typing preserved. Scripted `--quiet-cli` behavior must continue to work.
- **AI Contribution:** Selected JLine 3.30.16 `Status` and `LineReader`, with the JNI provider compatible with Java 21. Added a fixed bottom game panel with a map fitted to the terminal and a separate input area. Updated the Windows CLI launcher to build with `installDist` and then invoke the packaged application directly; documented the corresponding Linux/macOS commands. Retained plain nonperiodic output for quiet, redirected, and unsupported-terminal sessions.
- **Human Contribution:** Reported the scrolling/input disruption and specified the desired terminal behavior. No completed human terminal usability review is inferred.
- **Outcome:** Seven CLI tests passed: four plain-output checks and three real-JLine integration checks using an independent JediTerm screen emulator. Background updates preserved the partial instruction and prompt row; resize/backspace, Ctrl+C pause, EOF, attributes, and scroll-region restoration passed. Packaged demo CLI passed timed expiry/repeat at x = 11.37 → 12.70, restart/no-history, and quit. Piped input/output automatically selected plain nonperiodic output without escape sequences. A JLine test-emulator cursor-down defect led to the test-only JediTerm 3.76 dependency; production remains JLine. The full build/installDist passed all 76 ordinary Java tests. A native Windows ConPTY check used the actual JNI provider: a partial instruction survived two seconds of redraw and completed correctly, timed movement/repeat expired, pause/restart/quit passed, and shutdown restored terminal state. The test tool initially set TERM=dumb and correctly triggered fallback; clearing it only in a diagnostic process enabled the native check without changing user settings. The actual PowerShell quiet-launch check also passed with exactly the initial/requested snapshots, no ANSI escapes, and exit 0; the build step preserved piped state/quit input. PowerShell syntax and git diff checks passed. Native Linux/macOS and human visual/usability review remain unverified. The shared agent, controls, and simulation are unchanged.

### C16 — Synchronize the UML after the CLI display change

- **Task:** Verify that all current UML diagrams still describe the implemented application.
- **Input/Instruction:** The user asked whether all UML diagrams had been updated after the CLI fix.
- **AI Contribution:** Reviewed all seven current diagrams. Added `CliView` terminal ownership, startup, redraw, and cleanup members to the class diagram; updated SD02 for the fixed panel, editable input, fallback, and shutdown order; corrected SD05 to distinguish throttled state frames from unconditional lifecycle refresh. Regenerated the three matching SVGs. The other four diagrams already matched the implementation.
- **Human Contribution:** Requested a consistency check. No human acceptance of the diagrams or terminal usability is inferred.
- **Outcome:** All seven current sources passed the existing PlantUML syntax check and have readable SVG XML. The class diagram is 3905 × 2283; its preview was visually inspected, and SVG geometry checks found 41 nonoverlapping entity rectangles with member text inside. All 14 archived Stage 1 files remain hash-identical to the original commit. No application code changed or application tests were rerun for this documentation update.

## Review and evidence still to record

The student remains responsible for understanding and accepting the final implementation. Record subsequent human review, rejected or corrected code, commands used for validation, actual outcomes, and successful/failed live-model and microphone sessions. Do not convert a pending entry into a successful outcome solely because source code exists or a deterministic demo works.
