import io
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import patch
from ultralytics_train_worker import install_unicode_checkpoint_io

class UnicodeCheckpointIOTest(unittest.TestCase):
    def test_paths_file_objects_and_save_options(self):
        calls = []
        def original(obj, f, *args, **kwargs):
            self.assertTrue(hasattr(f, "write"))
            calls.append(kwargs)
            f.write(obj)
        torch = types.SimpleNamespace(save=original)
        with patch.dict(sys.modules, torch=torch), tempfile.TemporaryDirectory() as directory:
            install_unicode_checkpoint_io()
            wrapped = torch.save
            install_unicode_checkpoint_io()
            self.assertIs(wrapped, torch.save)
            file = Path(directory) / '中文根目录' / '中文任务' / '权重.pt'
            file.parent.mkdir(parents=True)
            torch.save(b'weights', f=file, _use_new_zipfile_serialization=True)
            self.assertEqual(b'weights', file.read_bytes())
            torch.save(b'updated', str(file))
            self.assertEqual(b'updated', file.read_bytes())
            stream = io.BytesIO()
            torch.save(b'stream', stream)
            self.assertEqual(b'stream', stream.getvalue())
            self.assertEqual(True, calls[0]['_use_new_zipfile_serialization'])
            with self.assertRaises(FileNotFoundError):
                torch.save(b'x', Path(directory)/'missing/file.pt')
