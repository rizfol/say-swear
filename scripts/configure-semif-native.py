"""Pair matching official CUDA and AVX2 CPU DLLs for Windows x64.

Both v0.3.35 wheels use the same upstream commit. The CUDA wheel's CPU DLL
requires AVX512, which is absent on the tested Ryzen 5700X. Keep the installed
wheel intact and select a project-local overlay with LLAMA_CPP_LIB_PATH.
"""
from pathlib import Path
import hashlib
import importlib.metadata
import json
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CPU_WHEEL = "llama_cpp_python-0.3.35-py3-none-win_amd64.whl"
CPU_URL = "https://github.com/abetlen/llama-cpp-python/releases/download/v0.3.35/" + CPU_WHEEL
CPU_SHA256 = "31590ea000d5aff6f05f1e428048e72318a83709288159a5bd4dabec530080bb"
DLL_SHA256 = "cd91f4ed375998da4da57fedaab1b0638fba8b2af88e74a2632bc046e7fa4850"


def file_hash(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def configure():
    distribution = importlib.metadata.distribution("llama-cpp-python")
    if distribution.version != "0.3.35":
        raise RuntimeError("The overlay requires exactly llama-cpp-python 0.3.35.")
    wheel = ROOT / ".tools" / "downloads" / CPU_WHEEL
    wheel.parent.mkdir(parents=True, exist_ok=True)
    if not wheel.exists():
        temporary = wheel.with_suffix(".download")
        urllib.request.urlretrieve(CPU_URL, temporary)
        with temporary.open("rb") as source:
            if hashlib.file_digest(source, "sha256").hexdigest() != CPU_SHA256:
                raise RuntimeError("Downloaded CPU wheel failed SHA-256 verification.")
        temporary.replace(wheel)
    with wheel.open("rb") as source:
        if hashlib.file_digest(source, "sha256").hexdigest() != CPU_SHA256:
            raise RuntimeError("Cached CPU wheel failed SHA-256 verification.")
    with zipfile.ZipFile(wheel) as archive:
        cpu_dll = archive.read("llama_cpp/lib/ggml-cpu.dll")
    if hashlib.sha256(cpu_dll).hexdigest() != DLL_SHA256:
        raise RuntimeError("Portable CPU DLL failed SHA-256 verification.")
    target = ROOT / ".tools" / "semif-native-avx2"
    target.mkdir(parents=True, exist_ok=True)
    library = Path(distribution.locate_file("llama_cpp/lib"))
    if not (library / "llama.dll").exists():
        raise RuntimeError("Expected the official Windows x64 runtime wheel.")
    for dll in library.glob("*.dll"):
        if dll.name == "ggml-cpu.dll":
            continue
        destination = target / dll.name
        if not destination.exists() or file_hash(destination) != file_hash(dll):
            shutil.copy2(dll, destination)
    cpu_destination = target / "ggml-cpu.dll"
    if not cpu_destination.exists() or file_hash(cpu_destination) != DLL_SHA256:
        cpu_destination.write_bytes(cpu_dll)
    (target / "provenance.json").write_text(json.dumps({
        "llama_cpp_python": "0.3.35",
        "wheel_commit": "3691546f1c9e0c1bf93323dff02230bd959cf562",
        "llama_cpp_commit": "4df29be4f4c3673f428170fda944a5b19f743bb8",
        "cpu_wheel_url": CPU_URL, "cpu_wheel_sha256": CPU_SHA256,
        "portable_cpu_dll_sha256": DLL_SHA256,
        "scope": "Matching official CPU DLL copied over isolated copy of installed runtime DLLs.",
    }, indent=2), encoding="utf-8")
    print("Native runtime overlay:", target)


if __name__ == "__main__":
    configure()
