import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from component_training import resolve_component, validated_parameters, check_source, ROOT

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
        self.assertTrue(Path(selected['model']).is_relative_to(self.path))
        self.assertFalse((self.root / 'local-components/baselines').exists())
        with self.assertRaises(ValueError):
            resolve_component(dict(self.payload, component_commit='old'), self.root)
        run.return_value.returncode = 1
        with self.assertRaises(ValueError): resolve_component(self.payload, self.root)

    def test_parameter_validation(self):
        for invalid in ({'epochs':0}, {'epochs':True}, {'epochs':1.5}, {'lr0':float('nan')}, {'project':'escape'}, {'amp':'yes'}):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                validated_parameters(self.adapters[0], invalid)
        self.assertEqual(1, validated_parameters(self.adapters[0], {'epochs':1})['epochs'])

    def test_probe_imports_selected_source_in_task_interpreter(self):
        selected = dict(self.adapters[1], source_root=str(self.path))
        info = check_source(sys.executable, selected)
        self.assertEqual('8.4.165', info['version'])
        self.assertTrue(Path(info['source']).is_relative_to(self.path))
        selected['engine_version'] = 'wrong'
        with self.assertRaises(ValueError): check_source(sys.executable, selected)
