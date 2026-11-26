import json
import unittest

from engine_output import opencode_result


class OpenCodeOutputTest(unittest.TestCase):
    model = "opencode/test-free"

    def test_http_failure_has_no_success_receipt(self):
        data = json.dumps({"type":"error","error":{"status":429,"message":"private provider detail"}})
        with self.assertRaisesRegex(ValueError,"^allowance$"):
            opencode_result(data,1,self.model)

    def test_exported_assistant_model_must_match_the_same_session(self):
        stream = "\n".join(json.dumps({"sessionID":"ses_test","type":kind,"part":part})
                           for kind,part in [("text",{"text":"ready"}),("step_finish",{"reason":"stop"})])
        info = {"role":"assistant","providerID":"opencode","modelID":"test-free"}
        transcript = {"info":{"id":"ses_test"},"messages":[{"info":info,"parts":[]}]}
        self.assertEqual(opencode_result(stream,0,self.model,lambda _:transcript),"ready")
        info["modelID"]="unselected"
        with self.assertRaisesRegex(ValueError,"model"):
            opencode_result(stream,0,self.model,lambda _:transcript)
        transcript["info"]["id"]="ses_other"
        with self.assertRaisesRegex(ValueError,"model"):
            opencode_result(stream,0,self.model,lambda _:transcript)
