import tempfile
import unittest
from pathlib import Path

import yaml

from ultralytics_train_worker import resolve_dataset_yaml


class DatasetPathsTest(unittest.TestCase):
    def test_root_variants_and_preserve_source(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            dataset = base / "数据集 with spaces"
            dataset.mkdir()
            yaml_dir = dataset / "configs"
            yaml_dir.mkdir()
            source = yaml_dir / "data.yaml"
            for root_value, expected in ((".", yaml_dir), ("..", dataset),
                                         (str(dataset), dataset), (None, yaml_dir)):
                with self.subTest(root=root_value):
                    data = {"train": ["images/train", "extra/train"],
                            "val": "images/test", "names": {0: "Person"}}
                    if root_value is not None:
                        data["path"] = root_value
                    original = yaml.safe_dump(data)
                    source.write_text(original, encoding="utf-8")
                    resolved = resolve_dataset_yaml(str(source), base / "unrelated-run")
                    actual = yaml.safe_load(resolved.read_text(encoding="utf-8"))
                    self.assertEqual(Path(actual.pop("path")), expected.resolve())
                    data.pop("path", None)
                    self.assertEqual(actual, data)
                    self.assertEqual(source.read_text(encoding="utf-8"), original)

    def test_invalid_yaml_and_missing_root(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            source = base / "data.yaml"
            for contents in ("- not a mapping", "path: [bad]", "path: missing"):
                source.write_text(contents, encoding="utf-8")
                with self.subTest(contents=contents), self.assertRaises((ValueError, FileNotFoundError)):
                    resolve_dataset_yaml(str(source), base / "run")


if __name__ == "__main__":
    unittest.main()
