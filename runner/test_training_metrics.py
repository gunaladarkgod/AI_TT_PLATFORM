import tempfile
import unittest
from pathlib import Path

from training_metrics import read_training_metrics


class TrainingMetricsTest(unittest.TestCase):
    def test_ultralytics_csv_ignores_partial_tail(self):
        with tempfile.TemporaryDirectory() as directory:
            work_dir = Path(directory)
            (work_dir / "results.csv").write_text(
                "epoch,train/box_loss,metrics/mAP50(B),lr/pg0\n"
                "1,1.2,0.31,0.001\n"
                "2,0.9,0.45,0.0008\n"
                "3,0.7,",
                encoding="utf-8",
            )
            metrics = read_training_metrics(work_dir, "ultralytics")
            self.assertEqual(metrics["axis"], "epoch")
            self.assertEqual(metrics["current_step"], 2)
            self.assertEqual(len(metrics["series"]), 3)
            self.assertEqual(metrics["series"][1]["points"][-1]["value"], 0.45)

    def test_mmdet_scalars_merge_train_and_validation_by_epoch(self):
        with tempfile.TemporaryDirectory() as directory:
            work_dir = Path(directory)
            scalars = work_dir / "20261009_120000" / "vis_data" / "scalars.json"
            scalars.parent.mkdir(parents=True)
            scalars.write_text(
                '{"epoch": 1, "step": 20, "loss": 2.1, "lr": 0.001}\n'
                '{"coco/bbox_mAP": 0.22, "step": 1}\n'
                '{"epoch": 2, "step": 40, "loss": 1.5, "lr": 0.0001}\n'
                '{"coco/bbox_mAP": 0.34, "step": 2}\n'
                '{"epoch": 3, "step":',
                encoding="utf-8",
            )
            metrics = read_training_metrics(work_dir, "mmdet")
            self.assertEqual(metrics["axis"], "epoch")
            self.assertEqual(metrics["current_step"], 2)
            accuracy = next(series for series in metrics["series"] if series["group"] == "accuracy")
            self.assertEqual(accuracy["points"][-1], {"step": 2.0, "value": 0.34})

    def test_mmdet_exposes_training_diagnostics(self):
        with tempfile.TemporaryDirectory() as directory:
            work_dir = Path(directory)
            scalars = work_dir / "20261009_120000" / "vis_data" / "scalars.json"
            scalars.parent.mkdir(parents=True)
            scalars.write_text(
                '{"epoch": 1, "step": 20, "acc": 68.4, "grad_norm": 4.2, '
                '"time": 0.82, "data_time": 0.13, "memory": 3874}\n',
                encoding="utf-8",
            )
            metrics = read_training_metrics(work_dir, "mmdet")
            by_group = {series["group"]: series for series in metrics["series"]}

            self.assertEqual(by_group["training_accuracy"]["label"], "分类头准确率")
            self.assertEqual(by_group["optimization"]["points"][-1]["value"], 4.2)
            performance = [series for series in metrics["series"] if series["group"] == "performance"]
            self.assertEqual({series["label"] for series in performance}, {"单次迭代耗时", "数据加载耗时"})
            self.assertEqual(by_group["resource"]["points"][-1]["value"], 3874.0)

    def test_mmdet_keeps_class_ap_series(self):
        with tempfile.TemporaryDirectory() as directory:
            work_dir = Path(directory)
            scalars = work_dir / "20261009_120000" / "vis_data" / "scalars.json"
            scalars.parent.mkdir(parents=True)
            scalars.write_text(
                '{"step": 1, "coco/bbox_mAP": 0.42, "coco/bbox_AP_cat": 0.51}\n',
                encoding="utf-8",
            )
            metrics = read_training_metrics(work_dir, "mmdet")
            labels = {series["label"] for series in metrics["series"]}
            self.assertIn("coco/bbox_AP_cat", labels)

    def test_missing_metrics_has_empty_state(self):
        with tempfile.TemporaryDirectory() as directory:
            metrics = read_training_metrics(Path(directory), "custom")
            self.assertEqual(metrics["series"], [])
            self.assertIn("未提供", metrics["message"])


if __name__ == "__main__":
    unittest.main()
