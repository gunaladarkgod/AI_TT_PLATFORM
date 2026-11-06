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

    def test_preflight_rejects_invalid_yaml_before_writing_snapshot(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            source = base / "data.yaml"
            source.write_text("names: [unclosed", encoding="utf-8")
            output = base / "run"
            with self.assertRaisesRegex(ValueError, r"Dataset YAML syntax error .*line 1"):
                resolve_dataset_yaml(str(source), output, validate_splits=True)
            self.assertFalse(output.exists())

    def test_preflight_checks_train_and_val_sources(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            source = base / "data.yaml"
            train = base / "images" / "train"
            val = base / "images" / "val"
            output = base / "run"
            source.write_text("train: images/train\nval: images/val\n", encoding="utf-8")

            with self.assertRaisesRegex(FileNotFoundError, "'train'.*does not exist"):
                resolve_dataset_yaml(str(source), output, validate_splits=True)
            train.mkdir(parents=True)
            with self.assertRaisesRegex(ValueError, "'train'.*no supported image files"):
                resolve_dataset_yaml(str(source), output, validate_splits=True)
            (train / "image.JPG").touch()

            with self.assertRaisesRegex(FileNotFoundError, "'val'.*does not exist"):
                resolve_dataset_yaml(str(source), output, validate_splits=True)
            val.mkdir()
            with self.assertRaisesRegex(ValueError, "'val'.*no supported image files"):
                resolve_dataset_yaml(str(source), output, validate_splits=True)
            (val / "image.png").touch()

            self.assertTrue(resolve_dataset_yaml(str(source), output, validate_splits=True).is_file())

    def test_preflight_accepts_lists_of_image_sources(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            source = base / "data.yaml"
            images = base / "images"
            images.mkdir()
            (images / "sample.webp").touch()
            (base / "images.txt").write_text("./images/sample.webp\n", encoding="utf-8")
            source.write_text("train: [images, images.txt]\nval: images.txt\n", encoding="utf-8")

            self.assertTrue(resolve_dataset_yaml(str(source), base / "run", validate_splits=True).is_file())


if __name__ == "__main__":
    unittest.main()
