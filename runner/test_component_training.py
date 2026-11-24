import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from component_training import resolve_component, validated_parameters, check_source, is_within, local_source_digest, ROOT

class ComponentTrainingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'research-registry').mkdir()
        self.adapters = json.loads((ROOT / 'research-registry/training-adapters.json').read_text(encoding='utf-8'))
        (self.root / 'research-registry/training-adapters.json').write_text(json.dumps(self.adapters))
        self.path = self.root / 'local-components/algorithms/small-object/tdsc'
        (self.path / '.git').mkdir(parents=True)
        (self.path / 'ultralytics').mkdir()
        (self.path / 'ultralytics/__init__.py').write_text('__version__ = "8.4.165"')
        model = self.path / self.adapters[1]['model']
        model.parent.mkdir(parents=True)
        model.write_text('test')
        self.payload = dict(adapter_digest=hashlib.sha256((self.root / 'research-registry/training-adapters.json').read_bytes()).hexdigest(),component_id='small-object/tdsc', component_kind='algorithm', component_commit='abc')

    def test_missing_repository_and_unregistered_id_rejected(self):
        with self.assertRaises(ValueError):
            resolve_component(dict(self.payload, component_id='../evil'), self.root)
        (self.path / 'ultralytics/__init__.py').unlink()
        with self.assertRaises(ValueError): resolve_component(self.payload, self.root)

    @patch('component_training.revision', return_value='abc')
    @patch('component_training.subprocess.run')
    def test_pin_model_and_independent_algorithm(self, run, revision):
        run.return_value.returncode = 0
        selected = resolve_component(self.payload, self.root)
        self.assertTrue(is_within(selected['model'], self.path))
        self.assertFalse((self.root / 'local-components/baselines').exists())
        with self.assertRaises(ValueError):
            resolve_component(dict(self.payload, component_commit='old'), self.root)
        run.return_value.returncode = 1
        with self.assertRaises(ValueError): resolve_component(self.payload, self.root)

    @patch('component_training.revision', return_value='abc')
    @patch('component_training.subprocess.run')
    def test_local_component_training_configuration_overrides_model_and_weight(self, run, revision):
        run.return_value.returncode = 0
        model = 'ultralytics/cfg/models/v8/manual.yaml'
        (self.path / model).parent.mkdir(parents=True, exist_ok=True)
        (self.path / model).write_text('manual model')
        weight = self.path / 'teacher.pt'
        weight.write_text('teacher')
        saved = json.dumps(dict(model=model, pretrained='teacher.pt', component_commit='abc')).encode()
        config_path = self.root / 'local-components/training-configs/algorithms/small-object/tdsc.json'
        config_path.parent.mkdir(parents=True)
        config_path.write_bytes(saved)
        adapter_config = (self.root / 'research-registry/training-adapters.json').read_bytes()
        payload = dict(self.payload, adapter_digest=hashlib.sha256(adapter_config + saved).hexdigest())

        selected = resolve_component(payload, self.root)
        self.assertEqual(self.path / model, Path(selected['model']))
        self.assertEqual(weight, Path(selected['pretrained']))

    def test_local_upload_matches_version_and_detects_source_changes(self):
        local = self.root / "local-components/custom-algorithms/small-object/test-1"
        source = local / "source"
        (source / "ultralytics").mkdir(parents=True)
        (source / "ultralytics/__init__.py").write_text('__version__ = "8.4.165"')
        model = source / "model.yaml"
        model.write_text("model")
        signature = local_source_digest(source)
        config_bytes = json.dumps(dict(source="source", model="model.yaml", pretrained="",
                                       engine_version="8.4.165", source_digest=signature)).encode()
        (local / "training.json").write_bytes(config_bytes)
        config = (self.root / "research-registry/training-adapters.json").read_bytes()
        payload = dict(component_kind="custom", component_id="custom/small-object/test-1",
                       component_commit=signature, adapter_digest=hashlib.sha256(config + config_bytes).hexdigest())
        self.assertEqual(Path(resolve_component(payload, self.root)["model"]), model)
        model.write_text("changed")
        with self.assertRaisesRegex(ValueError, "files changed"):
            resolve_component(payload, self.root)

    def test_parameter_validation(self):
        for invalid in ({'epochs':0}, {'epochs':True}, {'epochs':1.5}, {'lr0':float('nan')}, {'project':'escape'}, {'amp':'yes'}):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                validated_parameters(self.adapters[0], invalid)
        self.assertEqual(1, validated_parameters(self.adapters[0], {'epochs':1})['epochs'])

    def test_probe_imports_selected_source_in_task_interpreter(self):
        selected = dict(self.adapters[1], source_root=str(self.path))
        info = check_source(sys.executable, selected)
        self.assertEqual('8.4.165', info['version'])
        self.assertTrue(is_within(info['source'], self.path))
        selected['engine_version'] = 'wrong'
        with self.assertRaises(ValueError): check_source(sys.executable, selected)
