# -*- coding: utf-8 -*-
# uvicorn mmdet_runner_server:app --host 127.0.0.1 --port 8009

import os
import sys
import re
import json
import shlex
import subprocess
from datetime import datetime
from pathlib import Path
from typing import Dict, Optional, Tuple

from fastapi import FastAPI, Query
from fastapi.responses import JSONResponse

app = FastAPI(title="MMDet Runner Server (sync)")

# 1) 训练用的 Python 解释器
PY_EXE = "/home/omen1/miniconda3/envs/platform_mmdet/bin/python"
# 2) mmdetection 仓库根目录
REPO_ROOT = "/home/omen1/AI_TT_Platform/mmdet_run/mmdetection-3.0.0"
# 3) train.py 路径（用 REPO_ROOT 拼出来，避免写两份）
TRAIN_PY = str(Path(REPO_ROOT) / "tools" / "train.py")
# 4) 前端/Java 上传的配置文件根目录
ROOT_UPLOAD = "/home/omen1/AI_TT_Platform/mmdet_run/myfiles"
# 5) 训练产出目录根路径
DEFAULT_WORK_ROOT = "/home/omen1/AI_TT_Platform/artifacts/mmdet_runs"

# 追加的可选参数（保持你之前成功用过的设置）
EXTRA_ARGS = ["--cfg-options", "default_scope=mmdet"]

# =========================
# 工具函数
# =========================
def now_str() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")

def ts_for_path() -> str:
    return datetime.now().strftime("%Y%m%d_%H%M%S_%f")

def fmt_cmd(cmd: list) -> str:
    """用于日志/返回的命令串（带空格的参数加引号）"""
    parts = []
    for c in cmd:
        if " " in c or "\t" in c:
            parts.append(f"\"{c}\"")
        else:
            parts.append(c)
    return " ".join(parts)

def ensure_dir(p: Path):
    p.mkdir(parents=True, exist_ok=True)

def make_env() -> Dict[str, str]:
    env = os.environ.copy()
    env["PYTHONUNBUFFERED"] = "1"
    env["PYTHONIOENCODING"] = "utf-8"
    env["KMP_DUPLICATE_LIB_OK"] = "TRUE"
    old_py = env.get("PYTHONPATH", "")
    env["PYTHONPATH"] = REPO_ROOT if not old_py else REPO_ROOT + os.pathsep + old_py
    return env

def find_cfg_path(run_id: str) -> Path:
    # D:/.../myfiles/modelcfg/{runId}/combined_base.py
    return Path(ROOT_UPLOAD) / "modelcfg" / run_id / "combined_base.py"

def make_work_dir(run_id: str) -> Path:
    # D:/xgls/artifacts/mmdet_runs/{runId}_from_pyserver_sync_{ts}
    return Path(DEFAULT_WORK_ROOT) / f"{run_id}_from_pyserver_sync_{ts_for_path()}"

def write_header(log_path: Path, repo_root: str, work_dir: str, cfg_path: str, cmd_str: str):
    header = (
        f"[server] start={now_str()}\n"
        f"[server] repo_root={repo_root}\n"
        f"[server] work_dir={work_dir}\n"
        f"[server] cfg_path={cfg_path}\n"
        f"[server] cmd={cmd_str}\n"
        "=== PROCESS START ===\n"
    )
    with open(log_path, "a", encoding="utf-8") as f:
        f.write(header)

# —— 新增：把负数裁成 0，并统一为 4 位小数文本 ——
def clamp_nonneg_str(val: Optional[str]) -> Optional[str]:
    if val is None:
        return None
    try:
        v = float(val)
    except Exception:
        return val
    if v < 0:
        v = 0.0
    return f"{v:.4f}"

def parse_coco_from_log(text: str) -> Optional[str]:
    """
    从日志中解析 COCO 指标。
    - 仅匹配形如 "coco/bbox_mAP: <数值>" 的键值，避免误把句子里的 epoch 数当结果；
    - 允许负号，但会在输出前把负数裁为 0；
    """
    re_opts = re.IGNORECASE | re.MULTILINE
    pat_map = {
        "bbox_mAP":    r"(?:^|\s)(?:coco/)?bbox_mAP(?!_)\s*:\s*(-?\d+(?:\.\d+)?)",
        "bbox_mAP_50": r"(?:^|\s)(?:coco/)?bbox_mAP_50\s*:\s*(-?\d+(?:\.\d+)?)",
        "bbox_mAP_75": r"(?:^|\s)(?:coco/)?bbox_mAP_75\s*:\s*(-?\d+(?:\.\d+)?)",
        "bbox_mAP_s":  r"(?:^|\s)(?:coco/)?bbox_mAP_s\s*:\s*(-?\d+(?:\.\d+)?)",
        "bbox_mAP_m":  r"(?:^|\s)(?:coco/)?bbox_mAP_m\s*:\s*(-?\d+(?:\.\d+)?)",
        "bbox_mAP_l":  r"(?:^|\s)(?:coco/)?bbox_mAP_l\s*:\s*(-?\d+(?:\.\d+)?)",
    }

    metrics = {}
    for k, pat in pat_map.items():
        hits = re.findall(pat, text, flags=re_opts)
        if hits:
            metrics[k] = hits[-1]

    if metrics.get("bbox_mAP") is not None:
        # 裁掉负数并统一格式
        for k in list(metrics.keys()):
            metrics[k] = clamp_nonneg_str(metrics[k])
        return (
            "COCO metrics (bbox) | "
            f"mAP: {metrics.get('bbox_mAP')}  "
            f"AP50: {metrics.get('bbox_mAP_50', '0.0000' if metrics else 'NA')}  "
            f"AP75: {metrics.get('bbox_mAP_75', '0.0000' if metrics else 'NA')}  "
            f"APs: {metrics.get('bbox_mAP_s', '0.0000' if metrics else 'NA')}  "
            f"APm: {metrics.get('bbox_mAP_m', '0.0000' if metrics else 'NA')}  "
            f"APl: {metrics.get('bbox_mAP_l', '0.0000' if metrics else 'NA')}"
        )

    # copypaste 行（允许负号，输出前裁 0）
    m = re.search(
        r"bbox_mAP_copypaste:\s*(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)",
        text,
    )
    if m:
        a, ap50, ap75, aps, apm, apl = (clamp_nonneg_str(x) for x in m.groups())
        return (
            "COCO metrics (bbox, copypaste) | "
            f"mAP: {a}  AP50: {ap50}  AP75: {ap75}  APs: {aps}  APm: {apm}  APl: {apl}"
        )

    # pycocotools 打印（一般无负数，仍做一下裁剪）
    m2 = re.search(
        r"Average Precision\s*\(AP\)\s*@\[.*?\]\s*=\s*([-\d\.]+)",
        text,
        flags=re.DOTALL,
    )
    if m2:
        ap = clamp_nonneg_str(m2.group(1))
        ap50_list = re.findall(r"IoU=0\.50\s*\|\s*area=.*?=\s*([-\d\.]+)", text)
        ap75_list = re.findall(r"IoU=0\.75\s*\|\s*area=.*?=\s*([-\d\.]+)", text)
        line = f"COCO metrics (pycocotools) | mAP: {ap}"
        if ap50_list:
            line += f"  AP50: {clamp_nonneg_str(ap50_list[-1])}"
        if ap75_list:
            line += f"  AP75: {clamp_nonneg_str(ap75_list[-1])}"
        return line

    return None

def write_coco_txt(work_dir: Path, text: str) -> Path:
    out = work_dir / "coco_metrics.txt"
    content = (
        f"[generated_at] {now_str()}\n"
        f"{text}\n"
    )
    with open(out, "w", encoding="utf-8") as f:
        f.write(content)
    return out

# =========================
# API
# =========================
@app.post("/api/runner/train")
def start_train(
        runId: str = Query(..., description="前端/Java 只需传 runId")
):
    # 1) 计算 cfg_path
    cfg_path = find_cfg_path(runId)
    if not cfg_path.exists():
        return JSONResponse(
            content={"ok": False, "error": f"cfg_path not found: {str(cfg_path)}"},
            status_code=400,
        )

    # 2) 准备 work_dir & 日志文件
    work_dir = make_work_dir(runId)
    ensure_dir(work_dir)
    log_path = work_dir / "train.log"

    # 3) 组装命令
    cmd = [PY_EXE, "-u", TRAIN_PY, str(cfg_path), "--work-dir", str(work_dir), "--launcher", "none", *EXTRA_ARGS]
    cmd_str = fmt_cmd(cmd)

    # 4) 写 header 并启动进程（stdout/err 重定向到日志）
    ensure_dir(log_path.parent)
    write_header(log_path, REPO_ROOT, str(work_dir), str(cfg_path), cmd_str)

    env = make_env()
    try:
        with open(log_path, "a", encoding="utf-8") as log_f:
            proc = subprocess.Popen(
                cmd,
                cwd=REPO_ROOT,
                stdout=log_f,
                stderr=subprocess.STDOUT,
                env=env,
                shell=False,
                close_fds=False,
            )
            # 5) 同步等待到训练结束
            exit_code = proc.wait()
    except Exception as e:
        return JSONResponse(
            content={"ok": False, "error": f"failed to start or wait process: {e}"},
            status_code=500,
        )

    # 6) 读取日志并解析 COCO 指标
    try:
        text = Path(log_path).read_text(encoding="utf-8", errors="ignore")
    except Exception:
        text = ""

    parsed = parse_coco_from_log(text)

    # 7) 兜底提示
    if parsed is None:
        parsed = (
            "Training finished, but COCO metrics were not found in logs. "
            "Please check evaluator settings or search 'bbox_mAP' in the log."
        )

    # 8) 落盘 coco_metrics.txt
    results_file = write_coco_txt(work_dir, parsed)

    # 9) 返回 JSON
    resp = {
        "ok": exit_code == 0,
        "exit_code": exit_code,
        "pid": proc.pid if 'proc' in locals() else None,
        "cfg_path": str(cfg_path),
        "work_dir": str(work_dir),
        "log": str(log_path),
        "cmd": cmd_str,
        "repo_root": REPO_ROOT,
        "results_file": str(results_file),
        "results_txt": parsed,
    }
    return JSONResponse(content=resp, status_code=200 if exit_code == 0 else 500)


# 健康检查
@app.get("/health")
def health():
    return {"ok": True, "time": now_str()}


if __name__ == "__main__":
    try:
        import uvicorn
        uvicorn.run("mmdet_runner_server:app", host="127.0.0.1", port=8009, reload=False)
    except Exception as e:
        print(f"Failed to run uvicorn: {e}", file=sys.stderr)
        print("You can launch with: uvicorn mmdet_runner_server:app --host 127.0.0.1 --port 8009")
