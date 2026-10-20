"""Download pinned local inference assets; no application/game logic lives here."""
from pathlib import Path
import os

ROOT = Path(__file__).resolve().parents[1]
os.environ.setdefault("HF_HOME", str(ROOT / "models" / "hf-cache"))
os.environ.setdefault("HF_HUB_DISABLE_SYMLINKS_WARNING", "1")

from huggingface_hub import hf_hub_download, snapshot_download

GGUF_REVISION = "4168f45a16a1290d65a4ec0fa312ae917a4c15d6"
TOKENIZER_REVISION = "851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a"

if __name__ == "__main__":
    model = hf_hub_download(
        repo_id="bartowski/Qwen_Qwen3.5-4B-GGUF",
        filename="Qwen_Qwen3.5-4B-Q4_K_M.gguf",
        revision=GGUF_REVISION,
        local_dir=ROOT / "models" / "qwen3.5-4b",
    )
    tokenizer = snapshot_download(
        repo_id="Qwen/Qwen3.5-4B",
        revision=TOKENIZER_REVISION,
        allow_patterns=["*.json", "*.jinja", "vocab.*", "merges.txt", "LICENSE", "README.md"],
        local_dir=ROOT / "models" / "qwen3.5-4b-tokenizer",
    )
    print("GGUF:", model)
    print("Reference tokenizer:", tokenizer)
