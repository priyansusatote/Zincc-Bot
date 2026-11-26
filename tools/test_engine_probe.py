import unittest
from unittest.mock import Mock

from engine_probe import confirm_selection, permissions_denied


class EngineSelectionTests(unittest.TestCase):
    def test_specific_allow_after_wildcard_deny_blocks_preflight(self):
        deny = {"action": "*", "resource": "*", "effect": "deny"}
        allow = {"action": "read", "resource": "*.txt", "effect": "allow"}
        self.assertTrue(permissions_denied([allow, deny]))
        self.assertFalse(permissions_denied([deny, allow]))
        self.assertFalse(permissions_denied([]))

    def test_switch_requires_acknowledgement_of_exact_catalog_model(self):
        option = {"id": "choice", "category": "model", "currentValue": "old",
                  "options": [{"value": "old"}, {"value": "requested"}]}
        session = {"sessionId": "fixture", "configOptions": [option]}
        rpc = Mock()
        self.assertFalse(confirm_selection(rpc, session, "unknown"))
        rpc.call.assert_not_called()
        for response in ({}, {"configOptions": [option]}):
            rpc.call.return_value = response
            self.assertFalse(confirm_selection(rpc, session, "requested"))
        rpc.call.return_value = {"configOptions": [{**option, "currentValue": "requested"}]}
        self.assertTrue(confirm_selection(rpc, session, "requested"))
        self.assertTrue(all(call.args[0] == "session/set_config_option" for call in rpc.call.call_args_list))


if __name__ == "__main__":
    unittest.main()
