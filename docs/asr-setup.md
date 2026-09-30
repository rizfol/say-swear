# Streaming ASR Setup and Native Smoke Test

Say Swear uses the English `sherpa-onnx-streaming-zipformer-en-2023-06-26` model through `SherpaOnnxAdapter`. The [official model documentation](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html#csukuangfj-sherpa-onnx-streaming-zipformer-en-2023-06-26-english) identifies the archive, ONNX files, and bundled recording used below.

## Download the model

From the repository root in Windows PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-asr.ps1
$env:SHERPA_MODEL_DIR = (Resolve-Path '.\models\sherpa-onnx-streaming-zipformer-en-2023-06-26').Path
```

The script uses Windows `curl.exe` and `tar.exe`. It downloads the [official archive](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2), keeps the compressed download in `.tools/downloads/`, and extracts the model under `models/`. Both locations are excluded by `.gitignore`. A failed download can be resumed by running the script again.

The release asset is `191971614`, with a published size of **310,414,022 bytes** (about 296 MiB). The [GitHub release metadata](https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/tags/asr-models) did not publish a SHA-256 digest for this asset when checked. The script pins the digest measured from the official download during setup verification:

```text
639e25b578e9e997131402199419c13a941f8e4e198e2da1ce57dbf5cf401282
```

It checks this digest and archive length, writes a `.sha256` sidecar, validates archive member paths, and verifies required extracted files. The pinned digest is a reproducibility record from the verified download, not a publisher-supplied checksum.

The model directory must contain:

```text
encoder-epoch-99-avg-1-chunk-16-left-128.onnx
decoder-epoch-99-avg-1-chunk-16-left-128.onnx
joiner-epoch-99-avg-1-chunk-16-left-128.onnx
tokens.txt
test_wavs/0.wav
```

## Run the optional native test

With the Java 21 build toolchain available and the environment variable set in the current terminal:

```powershell
.\gradlew.bat asrSmokeTest
```

`SherpaNativeSmokeTest` is tagged `native-asr` and requires `SHERPA_MODEL_DIR`; ordinary unit tests exclude this tag. The test feeds the bundled recording in 20 ms chunks through the real Java adapter/native runtime, followed by three seconds of simulated silence. It requires changing partial transcripts before the recording ends, the documented sample phrases “AFTER EARLY NIGHTFALL” and “YELLOW LAMPS,” no error callback, and an utterance endpoint. It never opens a microphone.

A pass verifies native-library loading, model loading, chunked decoding, partial delivery, and endpoint handling for this recording. It does not measure microphone permissions, short directional-command accuracy, background-noise tolerance, or live gameplay latency. Those require a separate manual microphone session.

## Use voice in the GUI

Start the application with `SHERPA_MODEL_DIR` set, or supply `--asr-model-dir` with the same model directory. Select **Start listening** when ready to allow microphone capture. **Stop & release** ends capture and clears held movement. Decision-model setup is separate from ASR setup.

## Verification record

The setup script successfully downloaded and extracted the official archive on Windows during development. The observed archive digest is pinned above. On September 30, 2026, `asrSmokeTest` passed using Java 21 and sherpa-onnx 1.13.5: the 6.63-second bundled recording produced **17 partial transcripts and one utterance endpoint**, with both expected phrases recognized and no error callbacks. This test used sample audio only; microphone capture and live gameplay remain separate manual checks.

The [validation record](validation.md) separately records the completed real decision-model, agent-burst, packaged CLI, GUI, and build checks. They do not replace the remaining microphone/gameplay and student-review checks.
