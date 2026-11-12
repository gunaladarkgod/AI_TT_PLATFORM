"""Platform-independent regression tests for Windows launcher helpers."""

import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).with_name("ai-training-platform-launcher.py")
SPEC = importlib.util.spec_from_file_location("windows_launcher", MODULE_PATH)
launcher = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(launcher)


class LauncherHelperTests(unittest.TestCase):
    def test_frontend_build_is_required_when_sources_are_newer(self):
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

    def test_cmd_wrapper_quotes_paths_with_spaces(self):
        command = launcher.windows_command(r"C:\Program Files\Maven\bin\mvn.cmd", "-f", r"C:\AI Platform\backend\pom.xml")
        self.assertEqual(command[:3], [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/s"])
        self.assertIn('"C:\\Program Files\\Maven\\bin\\mvn.cmd"', command[-1])
        self.assertIn('"C:\\AI Platform\\backend\\pom.xml"', command[-1])


if __name__ == "__main__":
    unittest.main()
