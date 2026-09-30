# Local SemIf / OpenJev with Qwen3.5 4B

Say Swear uses **[SemIf, formerly OpenJev](https://openjev.com/)** from [TheoLeeCJ/SemIf-OpenJev](https://github.com/TheoLeeCJ/SemIf-OpenJev). Its decision model is **Qwen/Qwen3.5-4B**. No hosted API account or key is needed. This is a local inference service; the game, agent scheduling, command validation, controls, CLI and JavaFX frontend are Java.

## Pinned components

| Component | Version / revision |
|---|---|
| SemIf upstream | `23cf1f39fc9534fe81437200959b6dfc7106e45a` |
| [Qwen reference model/tokenizer](https://huggingface.co/Qwen/Qwen3.5-4B) | `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a` |
| [Q4_K_M quantized weights](https://huggingface.co/bartowski/Qwen_Qwen3.5-4B-GGUF) | `4168f45a16a1290d65a4ec0fa312ae917a4c15d6` |
| GGUF filename | `Qwen_Qwen3.5-4B-Q4_K_M.gguf` |
| [llama-cpp-python](https://github.com/abetlen/llama-cpp-python/tree/v0.3.35) | `0.3.35` |
| Vendored llama.cpp | `4df29be4f4c3673f428170fda944a5b19f743bb8` (`b10454`) |

The setup scripts download about 3 GB of weights and store dependencies and caches under the repository's ignored `.tools/` and `models/` directories. Initial setup needs internet access; inference uses the downloaded files locally. Preserve upstream licenses/model attribution when redistributing dependencies or weights.

## Install and launch on Windows

Run from the repository root. Python 3.12 and a compatible native runtime are required. The NVIDIA setup uses CUDA. On this AVX2 machine, the setup combines matching official CPU/CUDA native artifacts as described below; a C++ build toolchain is not needed for that procedure.

The CUDA DLL overlay does **not** bundle the NVIDIA driver, CUDA runtime/cuBLAS, or Microsoft Visual C++ runtime. Install a compatible NVIDIA driver, CUDA 12 runtime libraries including cuBLAS (a CUDA 12 toolkit installation supplies them), and the Microsoft Visual C++ 2015–2022 x64 Redistributable. `CUDA_PATH` should point to the CUDA installation; its `bin` directory must be available to native DLL loading. The tested machine already had CUDA 12 toolkits and the required system runtime.

```powershell
.\scripts\setup-semif.ps1
.\scripts\run-semif.ps1
```

For a machine without a compatible NVIDIA CUDA runtime, choose CPU mode in **both** steps:

```powershell
.\scripts\setup-semif.ps1 -CpuOnly
.\scripts\run-semif.ps1 -CpuOnly
```

CPU mode still needs the Microsoft C++ runtime. Repeat-update semantic checks passed on CPU with longer diagnostic deadlines; responsiveness within the default 2000 ms budget has not been established.

The equivalent explicit service command is:

```powershell
$env:LLAMA_CPP_LIB_PATH = (Resolve-Path .tools/semif-native-avx2).Path
.\.tools\semif-venv\Scripts\python.exe tools/semif/server.py `
  --gguf models/qwen3.5-4b/Qwen_Qwen3.5-4B-Q4_K_M.gguf `
  --tokenizer models/qwen3.5-4b-tokenizer `
  --gpu-layers -1
```

The service binds only `127.0.0.1:8765`. Wait for `Ready` before starting game controls. Check readiness with:

```powershell
Invoke-RestMethod http://127.0.0.1:8765/health
```

`/health` reports the selected model, pinned SemIf revision, GGUF digest and runtime metadata. Loading and warmup happen before the endpoint becomes ready. Stop the service with Ctrl+C after closing the game. It never opens a microphone.

**Updating an existing session:** restart the bridge after installing the repeat-support code. The current request schema includes `state.lastMovement` and 52 choices; an older running service still expects 51 choices and the previous context fields. Stop that service with Ctrl+C in its terminal, run `scripts/run-semif.ps1` again, and wait for readiness before using the updated game.

### CPU / GPU allocation

Upstream SemIf's `llamacpp_backend` fixes `n_gpu_layers=0`. This repository makes one explicit allocation extension: `--gpu-layers -1` requests all supported layers on GPU. `--gpu-layers 0` retains upstream CPU allocation. Unsupported GPU offload fails at startup. The extension retains upstream prompt construction, reference/GGUF tokenizer verification, option-token verification, native forward pass and softmax scoring. The bridge uses the official full-prompt `score` function, with no restored-prefix cache.

The initial official Windows CUDA wheel advertised AVX512 and failed during context creation on the Ryzen 7 5700X, which supports AVX2. The verified fix preserves the original virtual environment and creates a separate native-library directory: CUDA libraries from the official `0.3.35` CUDA wheel, with only `ggml-cpu.dll` replaced by the portable AVX2 library from the official `0.3.35` CPU wheel. Both releases point to the same source commit and vendored llama.cpp revision. `LLAMA_CPP_LIB_PATH` selects this directory. The setup scripts record/check the artifact digests; do not substitute unrelated native versions, whose C API layout can differ.

[configure-semif-native.py](../scripts/configure-semif-native.py) verifies the CPU wheel and extracted DLL SHA-256 values, and writes `.tools/semif-native-avx2/provenance.json`. [run-semif.ps1](../scripts/run-semif.ps1) selects this directory automatically.

Before repeat support, service initialization, warmup and Java decision tests succeeded with this combination on an RTX 2070 SUPER (8 GB), with 34/34 layers offloaded. Current repeat semantics were checked separately on CPU with diagnostic deadlines. The updated default-budget GPU burst check remains outstanding.

## Java boundary and bounded decisions

`OpenJevAdapter` posts to `http://127.0.0.1:8765/decide`. The request contains the configured model, latest transcript, initially held directions, previous command text, structured `lastMovement`, movement permission, interpretation rules and the finite action vocabulary. `lastMovement` is `null` when no movement is remembered; otherwise it contains the accepted movement's type, directions, and duration. It is separate from the currently held inputs. **No level geometry, player coordinates or goal location is sent.**

SemIf directly compares option-token logits. It supports at most 16 options per readout, so the bridge factors Say Swear's 52 allowed semantic actions into:

1. Eight action types, including `REPEAT_LAST`.
2. When needed, the vertical direction: `UP`, `DOWN` or `NONE`.
3. When needed, the horizontal direction: `LEFT`, `RIGHT` or `NONE`.
4. For a timed action, one of 100, 200 or 400 milliseconds.

A command therefore needs one to four SemIf readouts. Each axis receives the initially held direction on that axis and latest transcript. The model chooses each new direction; the bridge combines those choices into the existing bounded vocabulary. Two `NONE` results map to `NO_ACTION`. This projection does not parse or interpret the transcript. The response contains `backend: "semif"`, the model ID and a single allowed choice such as `TIMED_UP_RIGHT_200`. The Java adapter verifies these fields and creates an unstamped semantic candidate. `VoiceCommandAgent` attaches local request metadata; the deterministic control layer validates and executes it. Neither the model nor this service mutates the game.

For a request such as `again`, Qwen can select `REPEAT_LAST` without choosing directions or duration again. That proposal has an empty direction set and duration zero. After freshness/schema checks, Java uses the remembered `MovementIntent` to create a fresh directional or timed command. Timer expiry, ordinary stop/release, `KEEP_CURRENT`, and `NO_ACTION` preserve this memory; reset/pause/fall/restart/current error clears it. A fresh utterance may repeat; duplicate partials of the same utterance cannot trigger another replay. With no remembered movement, the accepted proposal runs no commands and displays an explanatory message.

For a **model-selected `SWITCH`**, axis evidence presents visible word boundaries: split on commas/whitespace and join the resulting tokens with `, `. Thus `right no left please` is presented as `right, no, left, please`; already separated text gets the same representation. This is deterministic input formatting. It does not match direction words or select a movement. Qwen still chooses each axis option. The raw transcript stays unchanged in the Java request, first action-type readout and local request metadata. Keeping only the formatted presentation in the axis readout resolved the tested punctuation-free correction failures; duplicating raw and formatted text in that readout did not.

Returned option probabilities are conditional scores, not calibrated confidence. The model can misinterpret speech or text; bounded output limits the possible controls but does not guarantee correct interpretation.

## Timing and failure behavior

`DECISION_TIMEOUT_MS` configures the Java end-to-end budget, default **2000 ms**. This is a maximum waiting budget, not an artificial delay. The agent includes pending-queue time in this budget. It admits at most two active provider calls and retains only the newest pending input. Expired/reset logical results cannot act; their physical request slots remain occupied until the finite HTTP request completes. The bridge serializes inference because SemIf shares a native context, and admits at most two requests.

The service checks its own deadline between readouts and after inference. A native forward pass cannot be interrupted midway by Python; late results are discarded by Java. Warming the service avoids charging initial weight loading against a movement command. No latency target is promised by this implementation.

The repeat update passed **20 baseline plus eight repeat/history cases** on a separate CPU service on port 8766. Baseline requests took **828.8–2293.3 ms** and repeat/history requests took **822.9–883.2 ms**, under a **60000 ms diagnostic cap**. The packaged real-model CLI repeated a timed action successfully using **DECISION_TIMEOUT_MS=30000**. These establish sampled semantics and integration, not responsiveness within the default 2000 ms budget. **The updated GPU burst gate has not been rerun.**

Before repeat support, the GPU **20-instruction Java adapter check passed** at **242.2–503.1 ms**, with a 15-second diagnostic cap. Its four-part correction burst passed under the 2000 ms budget, returning the latest `SWITCH LEFT` after **990.7 ms**; an earlier 1000 ms burst expired. These are historical pre-repeat measurements. Prompt experiments and input-presentation failures are preserved in [validation](validation.md).

Unknown choices, malformed responses, missing services and timeouts fail closed. `--demo` is a separately selected limited deterministic interpreter for demonstrations/tests; it is not an automatic fallback or the AI model.

## Tests

```powershell
.\.tools\semif-venv\Scripts\python.exe -m unittest discover -s tools/semif -p test_server.py
.\gradlew.bat test
```

The Python bridge tests use fake logits and loopback HTTP; ordinary Java tests use fake HTTP responses, controlled futures and fake capture devices. They require no credentials and do not capture microphone audio. The repeat update passed **nine bridge tests and 73 ordinary Java tests**. These verify contracts and scheduling; separate real-model checks assess actual inference and selected interpretation cases. See [validation](validation.md).

With the real local service already ready, run the explicitly enabled Java adapter smoke test:

```powershell
$env:SAY_SWEAR_LIVE_SEMIF = '1'
.\gradlew.bat liveSemIfTest
```

This runs 20 direction/context cases, eight repeat/history cases, and the four-part correction burst. Repeat cases include `again` and `do that again` with timed, continuous, switched, and absent history, plus keep/stop controls. Isolated requests default to a 15-second diagnostic cap; `SEMIF_DIAGNOSTIC_TIMEOUT_MS` changes that cap. The burst uses the application's 2000 ms default; `SAY_SWEAR_BURST_TIMEOUT_MS` explicitly selects another diagnostic budget. Use `SEMIF_ENDPOINT` for a different loopback port. The latest CPU run executed the two isolated-case methods with a 60000 ms cap; it did not rerun the burst gate.
