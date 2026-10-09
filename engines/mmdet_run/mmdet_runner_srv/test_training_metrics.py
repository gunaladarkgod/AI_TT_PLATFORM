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

    def test_missing_metrics_has_empty_state(self):
        with tempfile.TemporaryDirectory() as directory:
            metrics = read_training_metrics(Path(directory), "custom")
            self.assertEqual(metrics["series"], [])
            self.assertIn("未提供", metrics["message"])


if __name__ == "__main__":
    unittest.main()
