# Stage 2 Demonstration Index

**Recording supplied November 6, 2026.** [Say Swear feature demonstration](https://youtu.be/Yo-rBAtVPxA) covers the ten features and both interfaces. The local 11:30 recording was reviewed through sampled frames, closer inspection of key action transitions, and a local speech-recognition transcript. The rows describe the observed demonstration, rather than an unexecuted recording plan.

**Playback metadata checked November 7:** YouTube returned player status `OK` without account credentials and a duration of 691 seconds, matching the local recording reviewed for these timecodes. The November 6 processing condition has cleared. The source demonstration predates the November 7 fixes to precise timed distance and CLI refresh; those changes preserve the demonstrated commands and feature scope.

| Feature | Demonstration shown | Visible result and scope | Video URL | Time (start-end) |
|---|---|---|---|---|
| F01 - Real-Time Precision Movement | GUI movement during spoken instructions; CLI movement at 10:18-11:30. | Player position changes in the displayed level and CLI map/state. | [Watch](https://youtu.be/Yo-rBAtVPxA?t=65) | 01:05-03:25 |
| F02 - Continuous Voice-Control Session | GUI Start listening, successive spoken instructions, and Stop & release. | One listening session accepts multiple utterances; listening and control status are visible. | [Watch](https://youtu.be/Yo-rBAtVPxA?t=83) | 01:23-03:25 |
| F03 - Streaming Speech Transcription | GUI microphone input with the transcript visible during speech. | Partial text changes while the speaker continues; transcription is distinct from action selection. | [Watch](https://youtu.be/Yo-rBAtVPxA?t=104) | 01:44-02:08 |
| F04 - Natural-Language Movement Interpretation | GUI text instructions: right, stop, and hello, using the displayed local SemIf/Qwen backend. | Directional movement, release, and unsupported-input no-action feedback. CLI use is shown separately at 10:18. | [Watch](https://youtu.be/Yo-rBAtVPxA?t=224) | 03:44-04:30 |
| F05 - Context-Aware Commands | GUI right followed by other way; remembered timed repeats also appear in F08. | RIGHT is replaced by LEFT using current context; later repeats use stored movement. | [Watch](https://youtu.be/Yo-rBAtVPxA?t=275) | 04:35-05:37 |
| F06 - Live Command Correction / Interruption | Shared F05/F06 segment: a newer other way instruction replaces active right movement. | The held direction and velocity reverse. This shows active replacement, not an artificial out-of-order response test. | [Watch](https://youtu.be/Yo-rBAtVPxA?t=275) | 04:35-05:37 |
| F07 - Combined Directional Commands | GUI text: a little up and right. | The accepted timed action contains UP and RIGHT; both position coordinates change. | [Watch](https://youtu.be/Yo-rBAtVPxA?t=337) | 05:37-06:23 |
| F08 - Magnitude / Timed Commands | GUI timed movement followed by fresh again instructions after expiry. | The displayed 200 ms action releases automatically; repeats move again after the original action has ended. | [Watch](https://youtu.be/Yo-rBAtVPxA?t=383) | 06:23-07:41 |
| F09 - Control-State and Conflict Management | GUI movement, Stop & release, and again. | Controls become neutral and again reports no previous movement. Deliberately reordered responses and device failures are not demonstrated. | [Watch](https://youtu.be/Yo-rBAtVPxA?t=467) | 07:47-08:17 |
| F10 - Fall Detection and Start Recovery | GUI upward movement leaves the safe path after progress. | Fall feedback and return to the start with neutral movement; the narration explains loss of progress and cleared controls. | [Watch](https://youtu.be/Yo-rBAtVPxA?t=498) | 08:18-09:20 |

**CLI:** [10:18-11:30](https://youtu.be/Yo-rBAtVPxA?t=618) shows the terminal interface, typed directional instructions, map/state updates and fall recovery through the same local-model core. F05/F06 intentionally share the contextual reversal segment, which demonstrates both interpreting relative direction and replacing active movement.

## Independent evidence for F02, F03, and F04

- **F02 - capture/session:** GUI listening remains active across multiple spoken instructions and exposes session status and stopping.
- **F03 - transcription:** changing visible partial text appears while speech continues, independently of whether every phrase becomes a movement instruction.
- **F04 - interpretation:** GUI typed instructions reach the displayed local SemIf/Qwen backend and produce accepted movement, release and no-action feedback. The separate CLI segment shows typed gameplay without a microphone.

## Interpretation and evidence limits

The recording is a finite demonstration, not a measured recognition-accuracy or latency benchmark. Its visible delays are inference limitations, not an intentionally added game mechanic. The application's default interpretation deadline is 2000 ms; the recording does not establish a new timed performance gate. Timed movement is bounded to 100/200/400 ms: the spoken example asking for a second visibly produces a 200 ms action and should not be read as arbitrary-duration support.

The recording contains a black transition around 09:34-10:18 before the CLI. The CLI link skips it. No code walkthrough is required for the feature video, and this recording does not establish completed source/test review. Detailed device-failure testing, native Linux coverage and the updated repeat-enabled GPU timing gate remain separate limitations.

The source recording, extracted frames/audio, transcript and review helpers remain local. They are not repository submission artifacts. Only the YouTube link and timestamps are included here. Rebuild the report after any link or timing correction.
