"""Fixed-mode Runner smoke test.

This script intentionally performs no model training. Its stdout is redirected by
the Runner into the normal train.log, proving the fixed Python/cwd/script chain.
"""

import argparse
import os
import sys
import time
from datetime import datetime
from pathlib import Path


def now() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--work-dir", required=True)
    args = parser.parse_args()

    started_at = now()
    work_dir = Path(args.work_dir).resolve()
    work_dir.mkdir(parents=True, exist_ok=True)

    lines = [
        "FIXED_RUNNER_TEST_SUCCESS",
        f"run_id={args.run_id}",
        f"started_at={started_at}",
        f"python={sys.executable}",
        f"cwd={os.getcwd()}",
        f"script={Path(__file__).resolve()}",
        f"work_dir={work_dir}",
        "message=固定模式测试成功：已正确执行 tools/runner_fixed_test.py",
    ]
    print("\n".join(lines), flush=True)

    # 模拟真实训练：每轮等待一段时间并输出一组逐步提升的 COCO 指标。
    metric_lines = []
    total_epochs = 10
    for epoch in range(1, total_epochs + 1):
        time.sleep(2)
        progress = epoch / total_epochs
        map_value = 0.05 + 0.45 * progress
        ap50 = 0.10 + 0.50 * progress
        ap75 = 0.03 + 0.40 * progress
        aps = 0.02 + 0.28 * progress
        apm = 0.04 + 0.41 * progress
        apl = 0.06 + 0.49 * progress
        epoch_output = (
            f"epoch={epoch}/{total_epochs} time={now()} "
            f"coco/bbox_mAP: {map_value:.4f} "
            f"coco/bbox_mAP_50: {ap50:.4f} "
            f"coco/bbox_mAP_75: {ap75:.4f} "
            f"coco/bbox_mAP_s: {aps:.4f} "
            f"coco/bbox_mAP_m: {apm:.4f} "
            f"coco/bbox_mAP_l: {apl:.4f}"
        )
        metric_lines.append(epoch_output)
        print(epoch_output, flush=True)

    finished_line = f"finished_at={now()}"
    print(finished_line, flush=True)
    output = "\n".join([*lines, *metric_lines, finished_line])
    (work_dir / "fixed_runner_test_result.txt").write_text(
        output + "\n", encoding="utf-8"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
