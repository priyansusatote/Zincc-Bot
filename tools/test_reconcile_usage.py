import unittest
from reconcile_usage import reconcile


class ReconciliationTest(unittest.TestCase):
    def setUp(self):
        self.receipts = [
            dict(attempt="a", provider="A", model="m", state="completed", input_tokens="10", output_tokens="4"),
            dict(attempt="b", provider="A", model="m", state="interrupted", input_tokens="5", output_tokens="1")]
        self.statement = [dict(provider="A", model="m", input_tokens="15", output_tokens="5", billed_usd="0.08")]
        self.labels = [dict(attempt="a", success="1", confidence="0.8"), dict(attempt="b", success="0", confidence="0.4")]

    def test_failed_attempt_is_paid_and_only_verified_success_counts(self):
        report = reconcile(self.receipts, self.statement, self.labels)
        self.assertEqual(report["model_usd_per_verified_success"], "0.08")
        self.assertEqual(report["brier_score"], "0.10")
        self.assertEqual(report["models"][0]["status"], "matched")

    def test_missing_receipts_statements_labels_and_mismatch(self):
        self.assertIsNone(reconcile(self.receipts, self.statement, [])["model_usd_per_verified_success"])
        self.assertIsNone(reconcile(self.receipts, [], self.labels)["billed_usd"])
        self.receipts[1]["input_tokens"] = ""
        report = reconcile(self.receipts, self.statement, self.labels)
        self.assertEqual(report["models"][0]["input_tokens"]["statement_minus_known"], 5)
        self.assertEqual(report["models"][0]["status"], "missing_receipts")
        self.assertIsNone(report["model_usd_per_verified_success"])
        self.receipts[1]["input_tokens"] = "7"
        self.assertEqual(reconcile(self.receipts, self.statement, [])["models"][0]["status"], "mismatch")

    def test_duplicate_attempts_are_rejected(self):
        with self.assertRaises(ValueError):
            reconcile(self.receipts * 2, self.statement, [])
