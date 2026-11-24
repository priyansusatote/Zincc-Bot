import copy
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import harness_eval as harness


class ExperimentTest(unittest.TestCase):
    def test_numes_run_uses_provider_key_and_exact_free_model(self):
        model = "z-ai/glm-5.3-flash:free"
        route = "numes/" + model
        wire = {"model": model, "choices": [{"message": {"content": '{"answer":"5"}'}}],
                "usage": {"prompt_tokens": 7, "completion_tokens": 4}}
        with patch.dict(harness.os.environ, {"NUMES_API_KEY":"numes-test", "OPENROUTER_API_KEY":"other-test"}), \
                patch.object(harness.HTTP, "open", return_value=io.BytesIO(json.dumps(wire).encode())) as send:
            trial = harness.Trial(route, harness.key_from_env(route))
            self.assertEqual(trial.ask("Count synthetic events.", None), {"answer": "5"})
            request = send.call_args.args[0]
            self.assertEqual(request.full_url, "https://api.numes.ai/api/v1/chat/completions")
            self.assertEqual(request.get_header("Authorization"), "Bearer numes-test")
            self.assertEqual(json.loads(request.data)["model"], model)
            self.assertEqual((trial.input_tokens, trial.output_tokens), (7, 4))
            with self.assertRaises(ValueError):
                harness.Trial(route.removesuffix(":free"), "fixture").ask("count", None)
            self.assertEqual(send.call_count, 1)

    def test_read_plan_rechecks_grants_on_every_page(self):
        class Revoked(harness.Broker):
            def read(self, name, offset):
                rows = super().read(name, offset)
                self.grants.clear()
                return rows

        broker = Revoked()
        with self.assertRaises(ValueError):
            harness.dispatch({"tool": "report", "op": "sum"}, broker, True)
        self.assertEqual(broker.receipts, [("report", 0)])
        self.assertEqual(broker.discover(32), [])
        with self.assertRaises(ValueError):
            harness.dispatch({"tool": "inbox", "op": "write"}, harness.Broker(), True)

    def test_only_current_complete_better_cohort_can_be_selected_and_rolled_back(self):
        rows = [harness.evaluate_one("fixture", None, policy, case, size)
                for case in harness.CASES for size in (3,32) for policy in harness.STRATEGIES]
        self.assertFalse(harness.eligible(rows[:-1],"fixture","program"))
        regression = copy.deepcopy(rows)
        regression[-1]["correct"] = False
        self.assertFalse(harness.eligible(regression,"fixture","program"))
        with tempfile.TemporaryDirectory() as directory:
            db = harness.connection(Path(directory)/"ledger.db")
            with db:
                key=db.execute("INSERT INTO experiment(revision,results) VALUES(?,?)",
                               (harness.revision(),harness.encoded(rows))).lastrowid
            self.assertEqual(harness.active(db,"fixture"),"direct")
            harness.select(db,"fixture","program",key)
            self.assertEqual(harness.active(db,"fixture"),"program")
            self.assertEqual(harness.active(db,harness.ROUTES[0]),"direct")
            with db:
                db.execute("UPDATE experiment SET revision='retired'")
            self.assertEqual(harness.active(db,"fixture"),"direct")
            with self.assertRaises(ValueError):
                harness.select(db,"fixture","lookup",key)
            db.close()


if __name__ == "__main__":
    unittest.main()
