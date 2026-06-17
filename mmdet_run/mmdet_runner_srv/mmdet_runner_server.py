# -*- coding: utf-8 -*-
# uvicorn mmdet_runner_server:app --host 127.0.0.1 --port 8009

import os
import sys
import re
import subprocess
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from fastapi import FastAPI, Query
from fastapi.responses import JSONResponse

app = FastAPI(title="MMDet Runner Server (sync)")

# 1) 训练用的 Python 解释器
PY_EXE = os.getenv("MMDET_PY_EXE", "/home/omen1/miniconda3/envs/platform_mmdet/bin/python")
# 2) mmdetection 仓库根目录
REPO_ROOT = os.getenv("MMDET_REPO_ROOT", "/home/omen1/AI_TT_Platform/mmdet_run/mmdetection-3.0.0")
# 3) train.py 路径（用 REPO_ROOT 拼出来，避免写两份）
TRAIN_PY = str(Path(REPO_ROOT) / "tools" / "train.py")
# 4) 前端/Java 上传的配置文件根目录
ROOT_UPLOAD = os.getenv("MMDET_UPLOAD_ROOT", "/home/omen1/AI_TT_Platform/mmdet_run/myfiles")
# 5) 训练产出目录根路径
DEFAULT_WORK_ROOT = os.getenv("MMDET_WORK_ROOT", "/home/omen1/AI_TT_Platform/artifacts/mmdet_runs")

_RUNNER_DIR = Path(__file__).resolve().parent
_REPO_ROOT_DIR = _RUNNER_DIR.parent.parent

# 追加的可选参数（保持你之前成功用过的设置）
EXTRA_ARGS = ["--cfg-options", "default_scope=mmdet"]

# =========================
# ClearML：每个训练 run 在启动子进程前 Task.init，并把 CLEARML_* / CLEARML_TASK_ID 写入子进程 env
# =========================


def _append_log(log_path: Path, text: str) -> None:
    try:
        with open(log_path, "a", encoding="utf-8") as f:
            f.write(text.rstrip() + "\n")
    except Exception:
        pass


def _parse_dot_env(path: Path) -> Dict[str, str]:
    out: Dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        eq = line.find("=")
        if eq <= 0:
            continue
        k = line[:eq].strip()
        v = line[eq + 1 :].strip()
        if len(v) >= 2 and ((v[0] == v[-1] == '"') or (v[0] == v[-1] == "'")):
            v = v[1:-1]
        if k:
            out[k] = v
    return out


def _candidate_clearml_env_files() -> List[Path]:
    out: List[Path] = []
    override = os.getenv("MMDET_CLEARML_ENV_FILE", "").strip()
    if override:
        out.append(Path(override))
    out.extend(
        [
            _RUNNER_DIR / "env.clearml.local",
            _REPO_ROOT_DIR / "backend" / "env.clearml.local",
        ]
    )
    return out


def merge_clearml_into_env(env: Dict[str, str]) -> List[Path]:
    """合并所有存在的 ClearML env 文件（后者覆盖前者）；返回已加载路径列表。"""
    loaded: List[Path] = []
    for p in _candidate_clearml_env_files():
        try:
            if not p.is_file():
                continue
            for k, v in _parse_dot_env(p).items():
                env[k] = v
            loaded.append(p.resolve())
        except Exception:
            continue
    return loaded


def init_clearml_task(
    run_id: str, env: Dict[str, str], log_path: Path
) -> Tuple[Optional[Any], Optional[str]]:
    """创建 ClearML Task；失败时按 CLEARML_TRAINING_REQUIRED 决定是否中止。"""
    strict = os.getenv("CLEARML_TRAINING_REQUIRED", "false").lower() in ("1", "true", "yes")
    if os.getenv("CLEARML_DISABLE_TRAINING_HOOK", "").lower() in ("1", "true", "yes"):
        _append_log(log_path, "[clearml] disabled via CLEARML_DISABLE_TRAINING_HOOK")
        return None, None

    env_files_used = merge_clearml_into_env(env)
    if env_files_used:
        _append_log(log_path, "[clearml] merged env files: " + ", ".join(str(p) for p in env_files_used))

    if not env.get("CLEARML_API_ACCESS_KEY"):
        msg = "[clearml] CLEARML_API_ACCESS_KEY missing — training continues without ClearML Task"
        _append_log(log_path, msg)
        if strict:
            raise RuntimeError("CLEARML_TRAINING_REQUIRED but credentials missing")
        return None, None

    try:
        from clearml import Task
    except ImportError:
        msg = "[clearml] python package not installed (pip install clearml)"
        _append_log(log_path, msg)
        if strict:
            raise RuntimeError(msg)
        return None, None

    keys_to_push = {k: v for k, v in env.items() if k.startswith("CLEARML_")}
    backup = {k: os.environ.get(k) for k in keys_to_push}
    try:
        os.environ.update(keys_to_push)
        project = env.get("CLEARML_PROJECT_NAME", "AI-TT-Platform")
        task = Task.init(
            project_name=project,
            task_name=run_id,
            task_type=Task.TaskTypes.training,
            tags=["mmdet-runner"],
            reuse_last_task_id=False,
        )
        tid = task.id
        env["CLEARML_TASK_ID"] = tid
        _append_log(log_path, f"[clearml] Task.init ok task_id={tid} project={project}")
        return task, tid
    except Exception as e:
        _append_log(log_path, f"[clearml] Task.init error: {e}")
        if strict:
            raise
        return None, None
    finally:
        for k, old in backup.items():
            if old is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = old


def finalize_clearml_task(task: Any, exit_code: int, log_path: Path) -> None:
    if task is None:
        return
    try:
        task.get_logger().report_text(f"[mmdet-runner] train subprocess exit_code={exit_code}")
    except Exception:
        pass
    try:
        if exit_code == 0:
            if hasattr(task, "mark_completed"):
                task.mark_completed()
            elif hasattr(task, "completed"):
                task.completed()
        else:
            if hasattr(task, "mark_failed"):
                task.mark_failed(status_reason=f"exit_code={exit_code}")
    except Exception as e:
        _append_log(log_path, f"[clearml] finalize status warn: {e}")
    try:
        task.close()
    except Exception:
        pass


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
            parts.append(f'"{c}"')
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
        "bbox_mAP": r"(?:^|\s)(?:coco/)?bbox_mAP(?!_)\s*:\s*(-?\d+(?:\.\d+)?)",
        "bbox_mAP_50": r"(?:^|\s)(?:coco/)?bbox_mAP_50\s*:\s*(-?\d+(?:\.\d+)?)",
        "bbox_mAP_75": r"(?:^|\s)(?:coco/)?bbox_mAP_75\s*:\s*(-?\d+(?:\.\d+)?)",
        "bbox_mAP_s": r"(?:^|\s)(?:coco/)?bbox_mAP_s\s*:\s*(-?\d+(?:\.\d+)?)",
        "bbox_mAP_m": r"(?:^|\s)(?:coco/)?bbox_mAP_m\s*:\s*(-?\d+(?:\.\d+)?)",
        "bbox_mAP_l": r"(?:^|\s)(?:coco/)?bbox_mAP_l\s*:\s*(-?\d+(?:\.\d+)?)",
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


def extract_train_error_snippet(log_text: str, max_chars: int = 2600) -> str:
    """从完整 train.log 中提取可读错误摘要，便于 Java 写入备注并在前端排查。"""
    if not log_text:
        return ""
    tb_marker = "Traceback (most recent call last):"
    if tb_marker in log_text:
        i = log_text.rfind(tb_marker)
        snippet = log_text[i : i + max_chars].strip()
        return snippet
    lines = log_text.strip().splitlines()
    tail = "\n".join(lines[-40:])
    return tail[-max_chars:].strip()


def api_response(ok: bool, code: int, message: str, **kwargs):
    payload = {"ok": ok, "code": code, "message": message}
    payload.update(kwargs)
    return payload


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
            content=api_response(False, 400, "cfg_path not found", error=f"cfg_path not found: {str(cfg_path)}"),
            status_code=400,
        )

    # 2) 准备 work_dir & 日志文件
    work_dir = make_work_dir(runId)
    ensure_dir(work_dir)
    log_path = work_dir / "train.log"

    # 3) 组装命令
    cmd = [PY_EXE, "-u", TRAIN_PY, str(cfg_path), "--work-dir", str(work_dir), "--launcher", "none", *EXTRA_ARGS]
    cmd_str = fmt_cmd(cmd)

    # 4) 写 header 并 ClearML Task.init（调度仍在本 Runner HTTP 进程内）
    ensure_dir(log_path.parent)
    write_header(log_path, REPO_ROOT, str(work_dir), str(cfg_path), cmd_str)

    env = make_env()

    clearml_task = None
    clearml_task_id: Optional[str] = None
    try:
        clearml_task, clearml_task_id = init_clearml_task(runId, env, log_path)
    except RuntimeError as e:
        return JSONResponse(
            content=api_response(False, 400, "clearml_required_failed", error=str(e)),
            status_code=400,
        )

    exit_code = 1
    proc = None
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
            exit_code = proc.wait()
    except Exception as e:
        finalize_clearml_task(clearml_task, 1, log_path)
        return JSONResponse(
            content=api_response(False, 500, "failed to run process", error=f"failed to start or wait process: {e}"),
            status_code=500,
        )
    finalize_clearml_task(clearml_task, exit_code, log_path)

    # 6) 读取日志并解析 COCO 指标
    try:
        text = Path(log_path).read_text(encoding="utf-8", errors="ignore")
    except Exception:
        text = ""

    parsed = parse_coco_from_log(text)

    err_snippet = ""
    if exit_code != 0:
        err_snippet = extract_train_error_snippet(text)

    # 7) 兜底提示
    if parsed is None:
        if err_snippet:
            parsed = "Training failed. Last error from log:\n" + err_snippet[:1200]
        else:
            parsed = (
                "Training finished, but COCO metrics were not found in logs. "
                "Please check evaluator settings or search 'bbox_mAP' in the log."
            )

    # 8) 落盘 coco_metrics.txt
    results_file = write_coco_txt(work_dir, parsed)

    # 9) 返回 JSON（训练进程失败时使用 HTTP 200 + ok:false，避免误判成 Runner HTTP 异常；详见 train.log）
    resp = {
        **api_response(exit_code == 0, 0 if exit_code == 0 else 500, "training finished" if exit_code == 0 else "training failed"),
        "exit_code": exit_code,
        "pid": proc.pid if proc is not None else None,
        "cfg_path": str(cfg_path),
        "work_dir": str(work_dir),
        "log": str(log_path),
        "cmd": cmd_str,
        "repo_root": REPO_ROOT,
        "results_file": str(results_file),
        "results_txt": parsed,
        "clearml_task_id": clearml_task_id,
    }
    if exit_code != 0 and err_snippet:
        resp["error"] = err_snippet[:8000]

    return JSONResponse(content=resp, status_code=200)


# 健康检查
@app.get("/health")
def health():
    return api_response(True, 0, "ok", time=now_str())


if __name__ == "__main__":
    try:
        import uvicorn

        uvicorn.run("mmdet_runner_server:app", host="127.0.0.1", port=8009, reload=False)
    except Exception as e:
        print(f"Failed to run uvicorn: {e}", file=sys.stderr)
        print("You can launch with: uvicorn mmdet_runner_server:app --host 127.0.0.1 --port 8009")
