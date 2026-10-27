# Stage 2 Demonstration Index

**Status: PENDING USER RECORDINGS.** No video link or timestamp has been supplied. The user has played the game and reports that it works properly; this is gameplay acceptance, not a substitute for the rubric's feature videos. Fill every row and regenerate `stage2-report.pdf` before submission.

One recording can cover several features. Give each segment a visible feature ID/name, show the actual input and running result, briefly explain the AI role, and include a relevant alternative/error when practical. Collectively show the JavaFX GUI and the Java CLI. For model-dependent demonstrations, run the genuine local SemIf/Qwen service with demo mode disabled. F02/F03 need actual microphone capture and changing transcripts; typed input bypasses them.

| Feature | Interface and actual demonstration | Result / alternative to show | Video URL | Time (start-end) |
|---|---|---|---|---|
| F01 - Real-Time Precision Movement | GUI and CLI: issue a direction and inspect the shared position, velocity, held set, and lifecycle. | Continuous movement; neutral state after stop. Show GUI level and CLI map/state. | PENDING | PENDING |
| F02 - Continuous Voice-Control Session | GUI: Start listening, speak successive instructions without restarting capture, then Stop & release. | Visible readiness/listening/stopped status; capture and movement stop. If capture is unavailable, show the error rather than claiming a successful session. | PENDING | PENDING |
| F03 - Streaming Speech Transcription | GUI microphone: speak an instruction and correction while the transcript is visible. | Changing partial text before the utterance finishes; continued capture for a new utterance. Explain that ASR makes text, and SemIf interprets that text separately. | PENDING | PENDING |
| F04 - Natural-Language Movement Interpretation | CLI with genuine model: enter `right`, `go up`, and `stop`; inspect accepted-action messages and resulting state. | Model-selected bounded actions become deterministic movement. Include an unrelated instruction and explain `NO_ACTION` preserves existing movement; use `/pause` to stop immediately. | PENDING | PENDING |
| F05 - Context-Aware Commands | GUI or CLI: hold right, request `other way`; try `keep going`; after a timed action ends, request `again`. | Reversal from held context, continuation without replay, and replay from stored movement. After restart, `again` gives the no-history message. | PENDING | PENDING |
| F06 - Live Command Correction / Interruption | GUI speech or CLI text: correct active movement with `right - no, left`, then another instruction. | New valid instruction replaces movement; earlier held directions/timers do not reappear. Explain the epoch/version/revision checks from code; a video alone cannot prove arbitrary response ordering. | PENDING | PENDING |
| F07 - Combined Directional Commands | GUI or CLI: enter `up and right` from a suitable safe position. | Two perpendicular held directions and diagonal movement; explain normalized speed. Follow with a single direction or stop to show replacement. | PENDING | PENDING |
| F08 - Magnitude / Timed Commands | GUI or CLI: request `a little right`, wait for release, then say/type a fresh `again`. | A 200 ms hold expires automatically; repeat starts a fresh full-duration hold even though the held set was empty. | PENDING | PENDING |
| F09 - Control-State and Conflict Management | GUI Stop & release or CLI `/pause` during movement, then restart and try `again`. | Immediate neutral controls, cleared repeat memory, and no old movement resuming. Briefly show the relevant freshness/cancellation code or test as supporting evidence alongside the running app. | PENDING | PENDING |
| F10 - Fall Detection and Start Recovery | GUI or CLI: deliberately leave the safe path after making progress. | Fall and return to the level start with zero velocity/held inputs; `again` has no history. If demonstrating voice, show requested capture recovering under a fresh session or report the actual failure. | PENDING | PENDING |

## Separate acceptance criteria for F02, F03, and F04

- **F02 - capture/session:** one start maintains successive audio input, visible status reflects readiness, and stopping releases capture and controls. Correct recognition is not necessary to establish the capture lifecycle itself.
- **F03 - transcription:** audio produces changing visible partial text before recording completion and distinct utterances at endpoints. Movement interpretation is not necessary to establish incremental transcription.
- **F04 - interpretation:** typed natural language, without a microphone, reaches real SemIf/Qwen and produces a bounded accepted action followed by actual game movement or stop. ASR correctness is independent of this criterion.

## Before marking videos complete

- Replace every PENDING URL and timestamp with actual evidence. Use timestamped links for segments in a longer recording.
- Check that the instructor and teaching assistants can open links without an access request.
- Keep feature IDs/names visible or stated, show the input and resulting application behavior, and distinguish model inference from deterministic execution.
- State the actual timeout/runtime settings used. A slower diagnostic run is not evidence that the default 2000 ms deadline passes.
- Do not expose credentials, personal filesystem paths, private repository content, or unrelated windows in the recordings.
- Update the feature mapping and regenerate the PDF after adding the links. Review the final PDF's links as well as this index.
