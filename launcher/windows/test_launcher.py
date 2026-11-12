"""Platform-independent regression tests for Windows launcher helpers."""

import importlib.util
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).with_name("ai-training-platform-launcher.py")
SPEC = importlib.util.spec_from_file_location("windows_launcher", MODULE_PATH)
launcher = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(launcher)


class LauncherHelperTests(unittest.TestCase):
    def test_frozen_executable_finds_workspace_ancestor(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "backend").mkdir()
            (root / "fronternd").mkdir()
            executable = root / "launcher" / "windows" / "dist" / "launcher.exe"
            with patch.dict(os.environ, {"APP_WORKSPACE_ROOT": ""}), \
                    patch.object(launcher.sys, "frozen", True, create=True), \
                    patch.object(launcher.sys, "executable", str(executable)):
                self.assertEqual(launcher.find_workspace(), root.resolve())

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
        with tempfile.TemporaryDirectory(prefix="launcher cmd test ") as temp:
            script = Path(temp) / "print-args.cmd"
            script.write_text("@echo off\r\necho %~1\r\necho %~2\r\n", encoding="ascii")
            command = launcher.windows_command(str(script), "first argument", "second")
            result = subprocess.run(command, capture_output=True, text=True, check=True)

        self.assertEqual(result.stdout.splitlines(), ["first argument", "second"])


if __name__ == "__main__":
    unittest.main()
