"""Hardware-free tests of the SemIf bridge contract, not model accuracy tests."""
import copy
import json
from http.client import HTTPConnection
import threading
import time
import unittest

from server import DecisionEngine, LocalServer, MODEL


def request():
    direction_sets = [[d] for d in ("UP", "DOWN", "LEFT", "RIGHT")]
    direction_sets += [[v, h] for v in ("UP", "DOWN") for h in ("LEFT", "RIGHT")]
    choices = []
    for directions in direction_sets:
        suffix = "_".join(directions)
        for kind in ("PRESS", "RELEASE", "SWITCH"):
            choices.append(dict(id=kind + "_" + suffix, type=kind, directions=directions,
                                durationMs=0, description=kind + " " + suffix))
        for duration in (100, 200, 400):
            choices.append(dict(id="TIMED_" + suffix + "_" + str(duration), type="TIMED_PRESS",
                                directions=directions, durationMs=duration, description="Brief " + suffix))
    for kind in ("KEEP_CURRENT", "REPEAT_LAST", "RELEASE_ALL", "NO_ACTION"):
        choices.append(dict(id=kind, type=kind, directions=[], durationMs=0, description=kind))
    return {"model": MODEL, "state": {"transcript": "a little up and right", "heldDirections": ["LEFT"],
                                      "previousCommand": "left", "movementAllowed": True, "lastMovement": None},
            "question": "Interpret only the player's movement instruction.", "choices": choices, "timeoutMs": 1000}


class FakeScorer:
    def __init__(self, *selections):
        self.selections = iter(selections)
        self.rows = []

    def score(self, row):
        self.rows.append(copy.deepcopy(row))
        selected = next(self.selections)
        ids = [option["id"] for option in row["options"]]
        assert selected in ids
        return {"option_ids": ids, "probabilities": [float(value == selected) for value in ids],
                "input_tokens": 200, "forward_seconds": 0.01, "total_seconds": 0.012}


class EngineTest(unittest.TestCase):
    def test_combined_timed_action_uses_four_bounded_readouts(self):
        scorer = FakeScorer("TIMED_PRESS", "UP", "RIGHT", "200")
        result = DecisionEngine(scorer).decide(request(), time.monotonic() + 1)
        self.assertEqual(result["choice"], "TIMED_UP_RIGHT_200")
        self.assertEqual(result["backend"], "semif")
        self.assertEqual(result["model"], MODEL)
        self.assertEqual([len(row["options"]) for row in scorer.rows], [8, 3, 3, 3])
        self.assertEqual(scorer.rows[0]["state"], request()["state"])
        self.assertEqual(scorer.rows[1]["state"], {"initialDirectionOnThisAxis": "NONE", "transcript": "a little up and right"})
        self.assertEqual(scorer.rows[2]["state"], {"initialDirectionOnThisAxis": "LEFT", "transcript": "a little up and right"})
        self.assertEqual(scorer.rows[3]["state"], request()["state"])

    def test_stop_uses_one_readout_and_ambiguous_direction_becomes_no_action(self):
        stop = FakeScorer("RELEASE_ALL")
        self.assertEqual(DecisionEngine(stop).decide(request(), time.monotonic() + 1)["choice"], "RELEASE_ALL")
        self.assertEqual(len(stop.rows), 1)
        ambiguous = FakeScorer("PRESS", "NONE", "NONE")
        self.assertEqual(DecisionEngine(ambiguous).decide(request(), time.monotonic() + 1)["choice"], "NO_ACTION")

    def test_repeat_uses_one_model_readout_with_semantic_history_and_no_bridge_replay(self):
        payload = request()
        payload["state"].update(transcript="do that again", previousCommand="stop", heldDirections=[],
                                lastMovement={"type": "TIMED_PRESS", "directions": ["RIGHT"], "durationMs": 200})
        scorer = FakeScorer("REPEAT_LAST")
        result = DecisionEngine(scorer).decide(payload, time.monotonic() + 1)
        self.assertEqual(result["choice"], "REPEAT_LAST")
        self.assertEqual(len(scorer.rows), 1)
        self.assertEqual(scorer.rows[0]["state"], payload["state"])
        self.assertEqual(len(payload["choices"]), 52)
        payload["state"]["lastMovement"] = None
        no_history = FakeScorer("REPEAT_LAST")
        self.assertEqual(DecisionEngine(no_history).decide(payload, time.monotonic() + 1)["choice"], "REPEAT_LAST")
        self.assertEqual(len(no_history.rows), 1, "Only Java resolves or declines the requested replay.")

    def test_rejects_invalid_repeat_history_before_model_inference(self):
        for history in ("right", {}, {"type": "RELEASE_ALL", "directions": [], "durationMs": 0},
                        {"type": "REPEAT_LAST", "directions": ["RIGHT"], "durationMs": 0},
                        {"type": "PRESS", "directions": ["RIGHT", "LEFT"], "durationMs": 0},
                        {"type": "TIMED_PRESS", "directions": ["RIGHT"], "durationMs": 9999},
                        {"type": "PRESS", "directions": ["RIGHT"], "durationMs": 0, "epoch": 1}):
            payload = request()
            payload["state"]["lastMovement"] = history
            scorer = FakeScorer()
            with self.subTest(history=history), self.assertRaises(ValueError):
                DecisionEngine(scorer).decide(payload, time.monotonic() + 1)
            self.assertEqual(scorer.rows, [])

    def test_switch_word_boundaries_are_idempotent_and_preserve_the_raw_request(self):
        for text in ("right no left please", "right, no, left, please", " right,  no\tleft please "):
            payload = request()
            payload["state"]["transcript"] = text
            scorer = FakeScorer("SWITCH", "NONE", "LEFT")
            self.assertEqual(DecisionEngine(scorer).decide(payload, time.monotonic() + 1)["choice"], "SWITCH_LEFT")
            self.assertEqual(payload["state"]["transcript"], text)
            self.assertEqual(scorer.rows[0]["state"]["transcript"], text)
            for row in scorer.rows[1:]:
                self.assertEqual(row["state"]["transcript"], "right, no, left, please")

    def test_rejects_world_state_and_invalid_vocabulary_before_inference(self):
        invalid = []
        with_map = request()
        with_map["state"]["goalCoordinates"] = [1, 2]
        invalid.append(with_map)
        duplicate = request()
        duplicate["choices"][-1] = copy.deepcopy(duplicate["choices"][0])
        invalid.append(duplicate)
        opposed = request()
        opposed["choices"][0]["directions"] = ["UP", "DOWN"]
        invalid.append(opposed)
        unbounded = request()
        unbounded["choices"][0]["durationMs"] = 9999
        invalid.append(unbounded)
        for payload in invalid:
            scorer = FakeScorer()
            with self.subTest(payload=payload), self.assertRaises(ValueError):
                DecisionEngine(scorer).decide(payload, time.monotonic() + 1)
            self.assertEqual(scorer.rows, [])

    def test_deadline_prevents_any_inference(self):
        scorer = FakeScorer()
        with self.assertRaises(TimeoutError):
            DecisionEngine(scorer).decide(request(), time.monotonic() - 1)
        self.assertEqual(scorer.rows, [])

    def test_rejects_invalid_model_distribution(self):
        class InvalidScorer:
            def score(self, row):
                return {"option_ids": [x["id"] for x in row["options"]],
                        "probabilities": [float("nan")] * len(row["options"])}
        with self.assertRaises(RuntimeError):
            DecisionEngine(InvalidScorer()).decide(request(), time.monotonic() + 1)


class HttpTest(unittest.TestCase):
    def test_health_and_decision_contract_on_loopback(self):
        server = LocalServer(("127.0.0.1", 0), DecisionEngine(FakeScorer("RELEASE_ALL")), {"test": True})
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        connection = HTTPConnection("127.0.0.1", server.server_port, timeout=2)
        try:
            connection.request("GET", "/health")
            response = connection.getresponse()
            self.assertEqual(response.status, 200)
            self.assertTrue(json.loads(response.read())["ready"])
            connection.request("POST", "/decide", json.dumps(request()), {"Content-Type": "application/json"})
            response = connection.getresponse()
            self.assertEqual(response.status, 200)
            self.assertEqual(json.loads(response.read())["choice"], "RELEASE_ALL")
        finally:
            connection.close()
            server.shutdown()
            server.server_close()
            worker.join(2)


if __name__ == "__main__":
    unittest.main()
