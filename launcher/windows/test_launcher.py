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
                    patch.object(launcher, "FRONTEND_INDEX", index), patch.object(launcher, "FRONTEND_DOCUMENTS", ()):
                self.assertTrue(launcher.frontend_needs_build())
                index.parent.mkdir()
                index.write_text("built", encoding="utf-8")
                os.utime(source, (100, 100))
                os.utime(index, (110, 110))
                self.assertFalse(launcher.frontend_needs_build())
                os.utime(source, (120, 120))
                self.assertTrue(launcher.frontend_needs_build())

    def test_frontend_build_tracks_embedded_docs_but_not_unrelated_docs(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            frontend = root / "fronternd"
            index = frontend / "dist" / "index.html"
            index.parent.mkdir(parents=True)
            index.write_text("built", encoding="utf-8")
            docs = root / "docs" / "v1.0"
            docs.mkdir(parents=True)
            embedded_doc = docs / "技术说明文档.md"
            embedded_doc.write_text("embedded", encoding="utf-8")
            unrelated_doc = docs / "选择性克隆指南.md"
            unrelated_doc.write_text("unrelated", encoding="utf-8")
            os.utime(index, (120, 120))
            os.utime(embedded_doc, (100, 100))
            os.utime(unrelated_doc, (100, 100))
            watched_docs = tuple(docs / name for name in (
                "使用说明文档v1.0.md", "技术说明文档.md", "GitHub协作流程.md", "算法集成规范.md"))

            with patch.object(launcher, "ROOT", root), patch.object(launcher, "FRONTEND_DIR", frontend), \
                    patch.object(launcher, "FRONTEND_INDEX", index), patch.object(launcher, "FRONTEND_DOCUMENTS", watched_docs):
                os.utime(unrelated_doc, (130, 130))
                self.assertFalse(launcher.frontend_needs_build())
                os.utime(embedded_doc, (140, 140))
                self.assertTrue(launcher.frontend_needs_build())

    def test_cmd_wrapper_quotes_paths_with_spaces(self):
        with tempfile.TemporaryDirectory(prefix="launcher cmd test ") as temp:
            script = Path(temp) / "print-args.cmd"
            script.write_text("@echo off\r\necho %~1\r\necho %~2\r\n", encoding="ascii")
            command = launcher.windows_command(str(script), "first argument", "second")
            result = subprocess.run(command, capture_output=True, text=True, check=True)

        self.assertEqual(result.stdout.splitlines(), ["first argument", "second"])

    def test_backend_environment_file_supplies_database_settings(self):
        with tempfile.TemporaryDirectory() as temp:
            env_file = Path(temp) / ".env.local"
            env_file.write_text(
                "# local settings\nDB_USERNAME=platform_user\nDB_PASSWORD='local secret'\nOTHER=value\n",
                encoding="utf-8",
            )
            env = launcher.load_backend_environment({}, env_file)

        self.assertEqual(env, {"DB_USERNAME": "platform_user", "DB_PASSWORD": "local secret"})

    def test_process_environment_overrides_local_database_settings(self):
        with tempfile.TemporaryDirectory() as temp:
            env_file = Path(temp) / ".env.local"
            env_file.write_text("DB_USERNAME=file_user\nDB_PASSWORD=file_password\n", encoding="utf-8")
            env = launcher.load_backend_environment({"DB_USERNAME": "process_user"}, env_file)

        self.assertEqual(env["DB_USERNAME"], "process_user")
        self.assertEqual(env["DB_PASSWORD"], "file_password")

    def test_database_authentication_error_is_detected_without_reading_entire_log(self):
        with tempfile.TemporaryDirectory() as temp:
            log_path = Path(temp) / "backend.log"
            log_path.write_text("noise\njava.sql.SQLException: Access denied for user 'root'", encoding="utf-8")
            self.assertTrue(launcher.database_authentication_error(log_path))

    def test_old_database_authentication_error_is_ignored_after_start_offset(self):
        with tempfile.TemporaryDirectory() as temp:
            log_path = Path(temp) / "backend.log"
            old_error = "java.sql.SQLException: Access denied for user 'root'\n"
            log_path.write_text(old_error + "Application started\n", encoding="utf-8")
            self.assertFalse(launcher.database_authentication_error(log_path, len(old_error)))

    def test_backend_is_packaged_only_when_missing_or_source_signature_changes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            backend = root / "backend"
            source = backend / "src" / "main" / "java" / "App.java"
            source.parent.mkdir(parents=True)
            source.write_text("class App {}", encoding="utf-8")
            self.assertTrue(launcher.backend_needs_package(root))

            pom = backend / "pom.xml"
            pom.write_text("<project />", encoding="utf-8")
            jar = backend / "target" / "app.jar"
            jar.parent.mkdir()
            jar.write_bytes(b"jar")
            os.utime(source, (100, 100))
            os.utime(pom, (100, 100))
            signature = backend / "target" / "launcher-source.signature"
            signature.write_text(launcher.backend_source_signature(root), encoding="ascii")
            self.assertFalse(launcher.backend_needs_package(root))

            source.write_text("class App { int version = 2; }", encoding="utf-8")
            self.assertTrue(launcher.backend_needs_package(root))

    def test_backend_jar_ignores_non_executable_artifacts(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "backend" / "target"
            target.mkdir(parents=True)
            executable = target / "web_ai-1.0.jar"
            executable.touch()
            (target / "web_ai-1.0-sources.jar").touch()
            (target / "web_ai-1.0-plain.jar").touch()

            self.assertEqual(launcher.backend_jar(Path(temp)), executable)


if __name__ == "__main__":
    unittest.main()
