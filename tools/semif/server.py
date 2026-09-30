"""Local HTTP adapter for genuine SemIf option-logit scoring with Qwen3.5 4B.

SemIf is a Python inference dependency, not the application's game/control core.
No sampled answer text, command parser, movement simulation, or route planning lives here.
"""
from __future__ import annotations

import argparse
import importlib.metadata
import json
import math
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import threading
import time

SEMIF_REVISION = "23cf1f39fc9534fe81437200959b6dfc7106e45a"
MODEL = "Qwen/Qwen3.5-4B"
MODEL_REVISION = "851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a"
DIRECTIONS = ("UP", "DOWN", "LEFT", "RIGHT")
TYPES = {
    "PRESS": "Move continuously in a stated direction. The single words up, down, left, and right are complete valid commands.",
    "RELEASE": "Release only specified directions, keeping other directions active.",
    "SWITCH": "Reverse current direction (other way) or correct earlier movement (right, no, left).",
    "TIMED_PRESS": "A small or brief movement, such as a little left or a tiny bit up.",
    "KEEP_CURRENT": "Keep going: retain the current inputs and existing timer.",
    "REPEAT_LAST": "Again or do that again: request a fresh replay of the last movement, including its duration. This expresses repetition even if lastMovement is null.",
    "RELEASE_ALL": "Stop moving: release every direction.",
    "NO_ACTION": "Unclear, incomplete, contradictory, irrelevant, or autonomous navigation instruction.",
}


class DecisionEngine:
    """Factor the finite Java vocabulary into SemIf readouts of at most 16 options."""

    def __init__(self, scorer, model_name: str = MODEL):
        self.scorer = scorer
        self.model_name = model_name

    def decide(self, request: dict, deadline: float) -> dict:
        choices = self.validate(request)
        stages = []
        state = request["state"]
        rules = request["question"]

        def select(phase, question, options, evidence=None):
            if time.monotonic() >= deadline:
                raise TimeoutError("Decision deadline elapsed before the next readout.")
            row = {"id": phase, "state": state if evidence is None else evidence, "question": question, "options": options}
            scored = self.scorer.score(row)
            expected = [option["id"] for option in options]
            probabilities = scored.get("probabilities", [])
            if scored.get("option_ids") != expected or len(probabilities) != len(expected):
                raise RuntimeError("SemIf returned an inconsistent option distribution.")
            if any(not isinstance(p, (int, float)) or not math.isfinite(p) or p < 0 or p > 1
                   for p in probabilities) or not math.isclose(sum(probabilities), 1.0, abs_tol=1e-5):
                raise RuntimeError("SemIf returned invalid probabilities.")
            choice = expected[max(range(len(expected)), key=lambda index: probabilities[index])]
            stages.append({"phase": phase, "choice": choice, "probabilities": dict(zip(expected, probabilities)),
                           **{key: scored[key] for key in ("input_tokens", "forward_seconds", "total_seconds",
                                                          "prompt_sha256", "readout", "cache_hit") if key in scored}})
            return choice

        action_type = select("action-type", rules + "\nThe player is issuing commands in a four-direction game. "
                             "A single word up, down, left, or right is a complete instruction to move. "
                             "Which kind of action is the latest intention?",
                             [{"id": key, "description": value} for key, value in TYPES.items()])
        candidates = [choice for choice in choices if choice["type"] == action_type]
        if action_type not in {"KEEP_CURRENT", "REPEAT_LAST", "RELEASE_ALL", "NO_ACTION"}:
            selected_directions = []
            for axis, first, second in (("vertical", "UP", "DOWN"), ("horizontal", "LEFT", "RIGHT")):
                initial = next((direction for direction in state["heldDirections"] if direction in {first, second}), "NONE")
                criterion = (f"What {axis} direction does the latest command request? "
                             "Follow the final correction, if any. Other way means the opposite of the initial direction. "
                             "Choose no movement when this axis is not requested or the initial direction is NONE for a reversal. "
                             "Ignore how long the movement should last.")
                options = [{"id": first, "description": f"Move {first.lower()}."},
                           {"id": second, "description": f"Move {second.lower()}."},
                           {"id": "NONE", "description": f"No {axis} movement."}]
                # Present generic word boundaries for the model-selected SWITCH readout.
                # The request, first-stage evidence and Java source transcript remain unchanged.
                axis_text = ", ".join(filter(None, re.split(r"[\s,]+", state["transcript"].strip()))) \
                    if action_type == "SWITCH" else state["transcript"]
                evidence = {"initialDirectionOnThisAxis": initial, "transcript": axis_text}
                direction = select(axis, criterion, options, evidence)
                if direction != "NONE":
                    selected_directions.append(direction)
            if not selected_directions:
                candidates = [choice for choice in choices if choice["type"] == "NO_ACTION"]
            else:
                candidates = [choice for choice in candidates if set(choice["directions"]) == set(selected_directions)]
                if action_type == "TIMED_PRESS":
                    duration = select("duration", "Read evidence.transcript. Which brief movement duration "
                                      "does the player's latest intention request? Ignore direction words. "
                                      "Use 200 milliseconds for a little or an unspecified brief motion, "
                                      "100 for a tiny bit, and 400 for a longer brief motion.",
                                      [{"id": "100", "description": "A tiny bit: 100 milliseconds."},
                                       {"id": "200", "description": "A little or briefly: 200 milliseconds (default brief duration)."},
                                       {"id": "400", "description": "A longer brief movement: 400 milliseconds."}])
                    candidates = [choice for choice in candidates if choice["durationMs"] == int(duration)]
        if len(candidates) != 1:
            raise ValueError("The selected semantic action is absent or ambiguous in the allowed vocabulary.")
        if time.monotonic() >= deadline:
            raise TimeoutError("Decision deadline elapsed during inference.")
        return {"backend": "semif", "model": self.model_name, "choice": candidates[0]["id"],
                "stages": stages, "probabilityStatus": "conditional option scores, not calibrated confidence"}

    def validate(self, request):
        if not isinstance(request, dict) or request.get("model") != self.model_name:
            raise ValueError("The request must identify the loaded Qwen3.5 4B model.")
        state = request.get("state")
        if not isinstance(state, dict) or set(state) != {
            "transcript", "heldDirections", "previousCommand", "movementAllowed", "lastMovement"
        }:
            raise ValueError("State must contain only transcript and control context.")
        if not isinstance(state["transcript"], str) or not 0 < len(state["transcript"]) <= 8192:
            raise ValueError("Transcript must be nonempty and bounded.")
        if not isinstance(state["previousCommand"], str) or len(state["previousCommand"]) > 8192:
            raise ValueError("Previous command must be bounded text.")
        if not isinstance(state["movementAllowed"], bool):
            raise ValueError("movementAllowed must be a boolean.")
        self.validate_directions(state["heldDirections"], allow_empty=True)
        last_movement = state["lastMovement"]
        if last_movement is not None:
            if not isinstance(last_movement, dict) or set(last_movement) != {"type", "directions", "durationMs"}:
                raise ValueError("lastMovement must be null or a structured movement intent.")
            if last_movement["type"] not in {"PRESS", "SWITCH", "TIMED_PRESS"}:
                raise ValueError("Only accepted movement actions are repeatable.")
            self.validate_directions(last_movement["directions"], allow_empty=False)
            duration = last_movement["durationMs"]
            if type(duration) is not int or duration not in ({100, 200, 400}
                    if last_movement["type"] == "TIMED_PRESS" else {0}):
                raise ValueError("Invalid lastMovement duration.")
        if not isinstance(request.get("question"), str) or not 0 < len(request["question"]) <= 8192:
            raise ValueError("Question must be bounded text.")
        choices = request.get("choices")
        if not isinstance(choices, list) or len(choices) != 52:
            raise ValueError("The full 52-action Say Swear vocabulary is required.")
        ids, semantics = set(), set()
        for choice in choices:
            if not isinstance(choice, dict) or set(choice) != {"id", "type", "directions", "durationMs", "description"}:
                raise ValueError("Invalid action-choice schema.")
            if not isinstance(choice["id"], str) or not 0 < len(choice["id"]) <= 80 or choice["id"] in ids:
                raise ValueError("Action IDs must be unique bounded strings.")
            if choice["type"] not in TYPES or not isinstance(choice["description"], str):
                raise ValueError("Unsupported action type or description.")
            empty = choice["type"] in {"KEEP_CURRENT", "REPEAT_LAST", "RELEASE_ALL", "NO_ACTION"}
            self.validate_directions(choice["directions"], allow_empty=empty)
            if empty and choice["directions"]:
                raise ValueError("This action must not contain directions.")
            duration = choice["durationMs"]
            if type(duration) is not int or duration not in ({100, 200, 400} if choice["type"] == "TIMED_PRESS" else {0}):
                raise ValueError("Invalid action duration.")
            semantic = (choice["type"], tuple(sorted(choice["directions"])), duration)
            if semantic in semantics:
                raise ValueError("Duplicate semantic choice.")
            ids.add(choice["id"])
            semantics.add(semantic)
        return choices

    @staticmethod
    def validate_directions(directions, allow_empty):
        if not isinstance(directions, list) or any(d not in DIRECTIONS for d in directions):
            raise ValueError("Unknown direction.")
        if len(set(directions)) != len(directions) or len(directions) > 2 or (not directions and not allow_empty):
            raise ValueError("Invalid direction-set size.")
        if {"UP", "DOWN"} <= set(directions) or {"LEFT", "RIGHT"} <= set(directions):
            raise ValueError("Opposite directions are invalid.")


def load_scorer(args):
    """Use upstream tokenizer verification, full-prompt logits and normalization intact."""
    distribution = importlib.metadata.distribution("semif-phase1")
    origin = json.loads(distribution.read_text("direct_url.json") or "{}")
    if origin.get("vcs_info", {}).get("commit_id") != SEMIF_REVISION:
        raise RuntimeError("Install the pinned SemIf commit from docs/semif-setup.md.")
    from semif_phase1 import llamacpp_backend

    original_params = llamacpp_backend._cpu_model_params

    def model_params(library):
        params = original_params(library)
        if args.gpu_layers != 0:
            if not library.llama_supports_gpu_offload():
                raise RuntimeError("GPU offload was requested, but this llama.cpp library has no GPU backend.")
            params.n_gpu_layers = args.gpu_layers
        return params

    # Explicit project extension of upstream's CPU-only allocation setting. Scoring is unmodified.
    llamacpp_backend._cpu_model_params = model_params
    try:
        model, tokenizer, metadata = llamacpp_backend.load_model(
            str(args.tokenizer) if args.tokenizer else MODEL, MODEL_REVISION, args.gguf,
            threads=args.threads, context_tokens=args.max_tokens)
    finally:
        llamacpp_backend._cpu_model_params = original_params
    metadata["n_gpu_layers"] = args.gpu_layers
    metadata["project_extension"] = "GPU layer allocation" if args.gpu_layers else "none (upstream CPU allocation)"
    class FullPromptScorer:
        def score(self, row):
            return llamacpp_backend.score(model, tokenizer, row, metadata, args.max_tokens)
    scorer = FullPromptScorer()
    scorer.score({"id": "warmup", "state": "The player says stop.", "question": "Is stopping requested?",
                  "options": [{"id": "yes", "description": "Stop movement."}, {"id": "no", "description": "Keep moving."}]})
    return model, scorer, metadata


class LocalServer(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address, engine, metadata):
        super().__init__(address, Handler)
        self.engine = engine
        self.metadata = metadata
        self.inference_lock = threading.Lock()  # One stateful native context.
        self.capacity = threading.BoundedSemaphore(2)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/health":
            self.reply(404, {"error": "Unknown route."})
            return
        self.reply(200, {"ready": True, "backend": "semif", "model": MODEL,
                         "semifRevision": SEMIF_REVISION, "modelMetadata": self.server.metadata})

    def do_POST(self):
        if self.path != "/decide":
            self.reply(404, {"error": "Unknown route."})
            return
        if not self.server.capacity.acquire(blocking=False):
            self.reply(503, {"error": "Two decision requests are already active."})
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 65_536:
                raise ValueError("Request body must be between 1 and 65536 bytes.")
            request = json.loads(self.rfile.read(size))
            self.server.engine.validate(request)
            timeout_ms = request.get("timeoutMs", 2000)
            if type(timeout_ms) is not int or not 1 <= timeout_ms <= 120_000:
                raise ValueError("timeoutMs must be between 1 and 120000.")
            deadline = time.monotonic() + timeout_ms / 1000
            if not self.server.inference_lock.acquire(timeout=timeout_ms / 1000):
                raise TimeoutError("Decision expired while waiting for the model.")
            try:
                started = time.monotonic()
                result = self.server.engine.decide(request, deadline)
                result["totalSeconds"] = time.monotonic() - started
            finally:
                self.server.inference_lock.release()
            self.reply(200, result)
        except (ValueError, TypeError, KeyError) as error:
            self.reply(400, {"error": str(error)})
        except TimeoutError:
            self.reply(504, {"error": "Decision deadline exceeded."})
        except Exception:
            self.reply(500, {"error": "Local SemIf inference failed; inspect model/runtime configuration."})
        finally:
            self.server.capacity.release()

    def reply(self, status, payload):
        body = json.dumps(payload, allow_nan=False).encode("utf-8")
        try:
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass  # The Java caller may have reached its own earlier deadline.


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gguf", type=Path, required=True)
    parser.add_argument("--tokenizer", type=Path, help="Optional directory of the pinned reference tokenizer files.")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--threads", type=int, default=6)
    parser.add_argument("--max-tokens", type=int, default=4096)
    parser.add_argument("--gpu-layers", type=int, default=0,
                        help="0: official CPU path; -1: project extension requesting all layers on GPU.")
    args = parser.parse_args()
    if args.threads < 1 or args.max_tokens < 128 or args.gpu_layers < -1:
        parser.error("Invalid thread, context, or GPU layer setting.")
    print("Loading pinned SemIf with Qwen3.5 4B; no microphone or game controls are opened.", flush=True)
    model, scorer, metadata = load_scorer(args)
    server = LocalServer(("127.0.0.1", args.port), DecisionEngine(scorer), metadata)
    print(f"Ready: http://127.0.0.1:{args.port}/health", flush=True)
    try:
        server.serve_forever(poll_interval=0.25)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        model.close()


if __name__ == "__main__":
    main()
