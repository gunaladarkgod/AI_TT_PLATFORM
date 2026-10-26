import builtins
import importlib
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import patch


class _App:
    def __init__(self, *args, **kwargs):
        pass

    def get(self, *args, **kwargs):
        return lambda function: function

    post = get


class RunnerIsolationTest(unittest.TestCase):
    def test_import_and_engine_paths_without_mmengine(self):
        fastapi = types.ModuleType("fastapi")
        fastapi.FastAPI = _App
        fastapi.Body = lambda *args, **kwargs: None
        fastapi.Query = lambda *args, **kwargs: None
        responses = types.ModuleType("fastapi.responses")
        responses.JSONResponse = object
        real_import = builtins.__import__

        def no_mmengine(name, *args, **kwargs):
            if name == "mmengine" or name.startswith("mmengine."):
                raise ModuleNotFoundError("mmengine intentionally unavailable")
            return real_import(name, *args, **kwargs)

        with patch.dict(sys.modules, {"fastapi": fastapi, "fastapi.responses": responses}), \
             patch.dict("os.environ", {"APP_WORKSPACE_ROOT": ""}), \
             patch("builtins.__import__", side_effect=no_mmengine):
            sys.modules.pop("mmdet_runner_server", None)
            runner = importlib.import_module("mmdet_runner_server")
            self.assertEqual(runner._REPO_ROOT_DIR, Path(__file__).resolve().parent.parent)
            self.assertEqual(runner._RUNNER_DIR / "ultralytics_train_worker.py",
                             Path(__file__).resolve().parent / "ultralytics_train_worker.py")
            self.assertTrue((runner._RUNNER_DIR / "ultralytics_train_worker.py").is_file())
            with self.assertRaisesRegex(RuntimeError, "requirements-mmdet.txt"):
                runner.template_defaults(runner.ROOT_UPLOAD, "DETR")


if __name__ == "__main__":
    unittest.main()
