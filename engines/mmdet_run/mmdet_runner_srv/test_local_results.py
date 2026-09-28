"""Run with the Runner Python: python -m unittest test_local_results.py."""
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import mmdet_runner_server as runner


class LocalResultsTest(unittest.TestCase):
    def test_training_writes_terminal_status_without_backend(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            work = root / "artifacts" / "custom"
            script = root / "train_fixture.py"
            script.write_text("import sys\nfrom pathlib import Path\nPath(sys.argv[1], 'best.pt').write_bytes(b'fixture')\n", encoding="utf-8")
            with patch.object(runner, "_REPO_ROOT_DIR", root), \
                    patch.dict(runner.ENGINE_WORK_ROOTS, {"custom": work}), \
                    patch.object(runner, "FIXED_WORK_ROOT", work), \
                    patch.object(runner, "_ACTIVE_PID_DIR", root / "pids"):
                response = runner.start_train("fixture", {
                    "engine": "custom", "runner_mode": "fixed", "fixed_python_path": sys.executable,
                    "fixed_exec_dir": str(root), "fixed_command_line": "train_fixture.py {work_dir}",
                })
            payload = json.loads(response.body)
            self.assertTrue(payload["ok"], payload)
            status = json.loads(Path(payload["work_dir"], "run_status.json").read_text())
            self.assertEqual(status["status"], "completed")
            self.assertEqual(status["exit_code"], 0)
            self.assertTrue(Path(payload["work_dir"], "best.pt").is_file())

    def test_failed_preflight_is_visible_on_disk(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with patch.object(runner, "_REPO_ROOT_DIR", root), \
                    patch.dict(runner.ENGINE_WORK_ROOTS, {"custom": root / "artifacts/custom"}), \
                    patch.object(runner, "FIXED_WORK_ROOT", root / "artifacts/custom"):
                response = runner.start_train("fixture", {
                    "engine": "custom", "runner_mode": "fixed", "fixed_python_path": "/nonexistent/python",
                    "fixed_exec_dir": str(root), "fixed_command_line": "missing.py",
                })
            self.assertFalse(json.loads(response.body)["ok"])
            states = list((root / "artifacts/custom").glob("*/run_status.json"))
            self.assertEqual(len(states), 1)
            self.assertEqual(json.loads(states[0].read_text())["status"], "failed")

    def test_inference_passes_exact_directory_to_resolver(self):
        with patch.object(runner, "find_result_work_dir", return_value=None) as resolve:
            runner.infer_result("fixture", None, "/tmp/runs", {"result_dir": "/tmp/runs/exact"})
            resolve.assert_called_once_with("fixture", None, "/tmp/runs", "/tmp/runs/exact")


if __name__ == "__main__":
    unittest.main()
