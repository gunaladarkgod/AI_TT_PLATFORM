"""Regression tests for local service readiness checks."""

import importlib.util
import os
import unittest
from pathlib import Path
from unittest.mock import Mock, patch


MODULE_PATH = Path(__file__).with_name("ai-training-platform-launcher.py")
SPEC = importlib.util.spec_from_file_location("linux_launcher", MODULE_PATH)
launcher = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(launcher)


class ReadinessTests(unittest.TestCase):
    def test_local_checks_ignore_http_proxy(self):
        connection = Mock()
        connection.getresponse.side_effect = [Mock(status=200), Mock(status=302), Mock(status=404)]
        with patch.dict(os.environ, {"http_proxy": "http://127.0.0.1:1", "no_proxy": ""}), \
                patch.object(launcher.http.client, "HTTPConnection", return_value=connection) as factory:
            self.assertTrue(launcher.http_ready(8009, "/health"))
            self.assertTrue(launcher.http_ready(5173, "/dist"))
            self.assertFalse(launcher.http_ready(5173, "/missing"))
        self.assertEqual(factory.call_args_list[0].args, ("127.0.0.1", 8009))
        self.assertEqual(factory.call_args_list[1].args, ("127.0.0.1", 5173))
        self.assertEqual([call.args[1] for call in connection.request.call_args_list],
                         ["/health", "/dist", "/missing"])
        self.assertEqual(connection.close.call_count, 3)


if __name__ == "__main__":
    unittest.main()
