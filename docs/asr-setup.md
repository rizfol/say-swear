# Streaming ASR Setup and Native Smoke Test

Say Swear uses the English `sherpa-onnx-streaming-zipformer-en-2023-06-26` model through `SherpaOnnxAdapter`. The [official model documentation](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html#csukuangfj-sherpa-onnx-streaming-zipformer-en-2023-06-26-english) identifies the archive, ONNX files, and bundled recording used below.

## Download the model

### Windows

From the repository root in Windows PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-asr.ps1
$env:SHERPA_MODEL_DIR = (Resolve-Path '.\models\sherpa-onnx-streaming-zipformer-en-2023-06-26').Path
```

The Windows script uses `curl.exe` and `tar.exe`.

### Linux

Install Bash, `curl`, `tar`, `bzip2`, and GNU coreutils (including `sha256sum` and `stat`) through your distribution's package manager. From the repository root:

```bash
./scripts/setup-asr.sh
```

Use `./scripts/setup-asr.sh --help` to view usage. The script finds the repository from its own location, so it can also be invoked by absolute path from another directory. It downloads the same fixed model as the Windows script; no Python environment or running service is needed, and no microphone is opened.

`./run.sh` starts from the repository root, so the default model directory works without an environment override. For a direct application launch from another working directory, set the absolute model path in your current shell:

```bash
export SHERPA_MODEL_DIR='/absolute/path/to/say-swear/models/sherpa-onnx-streaming-zipformer-en-2023-06-26'
```

Replace `/absolute/path/to/say-swear` with your checkout's location. Running a setup script as a child process cannot set this variable in its parent shell.

### Download verification and cache

Both scripts download the [official archive](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2), keep the compressed download in `.tools/downloads/`, and extract the model under `models/`. Both locations are excluded by `.gitignore`. A failed download can be resumed by running the script again; Linux resumes its `.part` file and verifies its size and digest before promoting it to the cached archive or extracting it.

After successful extraction and required-file checks, the Linux script writes `.setup-asr.sha256` inside the model directory. Later runs skip extraction only when that marker matches the pinned archive digest and all five required files remain nonempty. Interrupted extraction is retried even if it left nonempty partial files. A model installed previously by the Windows script is extracted once on its first Linux-script run to establish this marker.

The release asset is `191971614`, with a published size of **310,414,022 bytes** (about 296 MiB). The [GitHub release metadata](https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/tags/asr-models) did not publish a SHA-256 digest for this asset when checked. The script pins the digest measured from the official download during setup verification:

```text
639e25b578e9e997131402199419c13a941f8e4e198e2da1ce57dbf5cf401282
```

Both scripts check this digest and archive length, write a `.sha256` sidecar, validate archive member paths, and verify required extracted files. The pinned digest is a reproducibility record from the verified download, not a publisher-supplied checksum.

The model directory must contain:

```text
encoder-epoch-99-avg-1-chunk-16-left-128.onnx
decoder-epoch-99-avg-1-chunk-16-left-128.onnx
joiner-epoch-99-avg-1-chunk-16-left-128.onnx
tokens.txt
test_wavs/0.wav
```

## Run the optional native test

With the Java 21 build toolchain available and the environment variable set in the current terminal, run on Windows:

```powershell
.\gradlew.bat asrSmokeTest
```

On Linux, from the repository root with `JAVA_HOME`/`PATH` selecting Java 21:

```bash
export SHERPA_MODEL_DIR="$(pwd)/models/sherpa-onnx-streaming-zipformer-en-2023-06-26"
./gradlew asrSmokeTest
```

`SherpaNativeSmokeTest` is tagged `native-asr` and requires `SHERPA_MODEL_DIR`; ordinary unit tests exclude this tag. The test feeds the bundled recording in 20 ms chunks through the real Java adapter/native runtime, followed by three seconds of simulated silence. It requires changing partial transcripts before the recording ends, the documented sample phrases “AFTER EARLY NIGHTFALL” and “YELLOW LAMPS,” no error callback, and an utterance endpoint. It never opens a microphone.

A pass verifies native-library loading, model loading, chunked decoding, partial delivery, and endpoint handling for this recording. It does not measure microphone permissions, short directional-command accuracy, background-noise tolerance, or live gameplay latency. Those require a separate manual microphone session.

## Use voice in the GUI

Start the application with `SHERPA_MODEL_DIR` set, supply `--asr-model-dir` with the same model directory, or use `./run.sh` with the default downloaded location on Linux. Select **Start listening** when ready to allow microphone capture. **Stop & release** ends capture and clears held movement. [SemIf setup](semif-setup.md) configures the separate Python decision service; ASR setup does not start it.

## Verification record

The setup script successfully downloaded and extracted the official archive on Windows during development. The observed archive digest is pinned above. On September 30, 2026, `asrSmokeTest` passed using Java 21 and sherpa-onnx 1.13.5: the 6.63-second bundled recording produced **17 partial transcripts and one utterance endpoint**, with both expected phrases recognized and no error callbacks. This test used sample audio only; microphone capture and live gameplay remain separate manual checks.

On October 19, 2026, the Bash downloader passed syntax/help checks and isolated fixture checks using Git Bash and GNU utilities on Windows. The checks covered a fresh download/extraction, cached rerun, resuming a partial download, promoting a completed partial, size/checksum rejection, archive path rejection, and recovery after an interrupted extraction left nonempty truncated files. The script's production size/digest pins also matched the existing official archive. These checks used local data and did not alter the installed model.

Native recognition on Linux and a live Linux microphone session remain unverified. The script checks above do not establish those runtime behaviors.

The [validation record](validation.md) separately records the completed real decision-model, agent-burst, packaged CLI, GUI, and build checks. They do not replace the remaining microphone/gameplay and student-review checks.
