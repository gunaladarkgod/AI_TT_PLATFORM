"""Regression tests for local service readiness checks."""

import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
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
            self.assertTrue(launcher.http_ready(8081, "/dist/index.html"))
            self.assertFalse(launcher.http_ready(8081, "/missing"))
        self.assertEqual(factory.call_args_list[0].args, ("127.0.0.1", 8009))
        self.assertEqual(factory.call_args_list[1].args, ("127.0.0.1", 8081))
        self.assertEqual([call.args[1] for call in connection.request.call_args_list],
                         ["/health", "/dist/index.html", "/missing"])
        self.assertEqual(connection.close.call_count, 3)

    def test_frontend_build_only_when_sources_change(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            frontend = root / "fronternd"
            source = frontend / "src" / "main.js"
            source.parent.mkdir(parents=True)
            source.write_text("source", encoding="utf-8")
            index = frontend / "dist" / "index.html"
            with patch.object(launcher, "ROOT", root), patch.object(launcher, "FRONTEND_DIR", frontend), \
                    patch.object(launcher, "FRONTEND_INDEX", index):
                self.assertTrue(launcher.frontend_needs_build())
                index.parent.mkdir()
                index.write_text("built", encoding="utf-8")
                os.utime(source, (100, 100))
                os.utime(index, (110, 110))
                self.assertFalse(launcher.frontend_needs_build())
                os.utime(source, (120, 120))
                self.assertTrue(launcher.frontend_needs_build())

    def test_service_list_has_no_vite_process(self):
        app = SimpleNamespace(_backend_command=lambda: ["mvn", "spring-boot:run"])
        services = launcher.Launcher._create_services(app)
        self.assertEqual([service.key for service in services], ["mysql", "backend", "runner"])

    def test_open_platform_uses_backend(self):
        app = SimpleNamespace(_open_path=Mock())
        with tempfile.TemporaryDirectory() as temp:
            index = Path(temp) / "index.html"
            index.write_text("built", encoding="utf-8")
            with patch.object(launcher, "FRONTEND_INDEX", index), \
                    patch.object(launcher, "http_ready", return_value=True) as ready:
                launcher.Launcher.open_platform(app)
        ready.assert_called_once_with(8081, "/dist/index.html")
        app._open_path.assert_called_once_with("http://127.0.0.1:8081/")


if __name__ == "__main__":
    unittest.main()
