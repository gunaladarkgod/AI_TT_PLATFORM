# -*- coding: utf-8 -*-
# uvicorn mmdet_runner_server:app --host 127.0.0.1 --port 8009

import os
import sys
import re
import signal
import shlex
import shutil
import subprocess
import threading
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from fastapi import Body, FastAPI, Query
from fastapi.responses import JSONResponse

from mmdet_config_service import generate_config, list_templates, read_config

app = FastAPI(title="MMDet Runner Server (sync)")

_ACTIVE_PROCESSES: Dict[str, subprocess.Popen] = {}
_ACTIVE_PROCESSES_LOCK = threading.Lock()

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

# Runner 模式直接写在本文件中，不从环境变量或启动脚本读取。
# 可选值："original"（原 MMDet + ClearML 流程）/ "fixed"（固定命令且跳过 ClearML）。
RUNNER_MODE = "fixed"

# fixed 模式的全部执行信息也写在本文件中。
# 实际效果：在 FIXED_EXEC_DIR 中执行：
#   FIXED_PYTHON_PATH -u tools/runner_fixed_test.py --run-id ... --work-dir ...
FIXED_PYTHON_PATH = r"C:\Users\Guo Qinyao\.conda\envs\openmmlab\python.exe"
FIXED_EXEC_DIR = r"C:\Users\Guo Qinyao\Desktop\platform\AI_TT_PLATFORM\mmdet_run\mmdetection-3.0.0"
FIXED_COMMAND_LINE = "tools/runner_fixed_test.py --run-id {run_id} --work-dir {work_dir}"
FIXED_WORK_ROOT = r"C:\Users\Guo Qinyao\Desktop\platform\AI_TT_PLATFORM\artifacts\mmdet_runs"

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
    # D:/.../myfiles/modelcfg/{runId}/config.py
    return Path(ROOT_UPLOAD) / "modelcfg" / run_id / "config.py"


def make_work_dir(run_id: str) -> Path:
    # D:/xgls/artifacts/mmdet_runs/{runId}_from_pyserver_sync_{ts}
    root = FIXED_WORK_ROOT if current_runner_mode() == "fixed" else DEFAULT_WORK_ROOT
    return Path(root) / f"{run_id}_from_pyserver_sync_{ts_for_path()}"


def find_latest_train_log(run_id: str) -> Optional[Path]:
    """查找该 runId 最近一次运行产生的 train.log。"""
    if not run_id or run_id in (".", "..") or any(c in run_id for c in ("/", "\\")):
        raise ValueError("invalid runId")
    root = Path(FIXED_WORK_ROOT if current_runner_mode() == "fixed" else DEFAULT_WORK_ROOT)
    if not root.is_dir():
        return None
    prefix = f"{run_id}_from_pyserver_sync_"
    candidates = []
    for child in root.iterdir():
        if child.is_dir() and child.name.startswith(prefix):
            log_path = child / "train.log"
            if log_path.is_file():
                candidates.append(log_path)
    return max(candidates, key=lambda p: p.stat().st_mtime, default=None)


def find_result_work_dir(run_id: str, finished_at: Optional[str]) -> Optional[Path]:
    """在 Runner 输出根目录内定位与结果完成时间最接近的单个训练目录。"""
    if not run_id or run_id in (".", "..") or any(c in run_id for c in ("/", "\\")):
        raise ValueError("invalid runId")
    root = Path(FIXED_WORK_ROOT if current_runner_mode() == "fixed" else DEFAULT_WORK_ROOT).resolve()
    if not root.is_dir():
        return None
    prefix = f"{run_id}_from_pyserver_sync_"
    candidates = [child.resolve() for child in root.iterdir()
                  if child.is_dir() and child.name.startswith(prefix)]
    candidates = [child for child in candidates if child.parent == root]
    if not candidates:
        return None
    if not finished_at:
        return max(candidates, key=lambda p: p.stat().st_mtime)
    try:
        target_time = datetime.fromisoformat(finished_at).timestamp()
    except ValueError as exc:
        raise ValueError("invalid finishedAt") from exc
    return min(candidates, key=lambda p: abs(p.stat().st_mtime - target_time))


def current_runner_mode() -> str:
    if RUNNER_MODE not in ("original", "fixed"):
        raise ValueError(
            f"unsupported MMDET_RUNNER_MODE={RUNNER_MODE!r}; expected 'original' or 'fixed'"
        )
    return RUNNER_MODE


def build_fixed_execution(
    python_path: str,
    execution_dir: str,
    command_line: str,
    run_id: str,
    work_dir: Path,
) -> Tuple[List[str], str, str]:
    """接收固定 Python、执行目录和命令行模板，构造无 shell 的安全执行参数。"""
    rendered = command_line.format(
        run_id=shlex.quote(run_id),
        work_dir=shlex.quote(str(work_dir)),
    )
    command_args = shlex.split(rendered, posix=True)
    if not command_args:
        raise ValueError("FIXED_COMMAND_LINE cannot be empty")

    python_file = Path(python_path)
    cwd = Path(execution_dir)
    script = Path(command_args[0])
    if not script.is_absolute():
        script = cwd / script

    missing = [str(p) for p in (python_file, cwd, script) if not p.exists()]
    if missing:
        raise FileNotFoundError("missing: " + ", ".join(missing))

    cmd = [python_path, "-u", *command_args]
    return cmd, str(cwd), str(script.resolve())


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


def stop_process_tree(proc: subprocess.Popen) -> None:
    """跨平台停止训练进程及其子进程。"""
    if proc.poll() is not None:
        return
    if os.name == "nt":
        completed = subprocess.run(
            ["taskkill", "/PID", str(proc.pid), "/T", "/F"],
            capture_output=True,
            text=True,
            timeout=10,
            check=False,
        )
        if completed.returncode != 0 and proc.poll() is None:
            proc.kill()
    else:
        try:
            os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
        except ProcessLookupError:
            pass


# =========================
# API
# =========================
@app.get("/api/config/templates")
def config_templates():
    return api_response(True, 0, "ok", templates=list_templates(ROOT_UPLOAD))


@app.post("/api/config/generate")
def config_generate(payload: Dict[str, Any] = Body(...)):
    try:
        result = generate_config(ROOT_UPLOAD, payload)
        return api_response(True, 0, "config generated", **result)
    except (ValueError, FileNotFoundError) as e:
        return JSONResponse(
            content=api_response(False, 400, "config generation rejected", error=str(e)),
            status_code=400,
        )
    except Exception as e:
        return JSONResponse(
            content=api_response(False, 500, "config generation failed", error=f"{type(e).__name__}: {e}"),
            status_code=500,
        )


@app.get("/api/config/read")
def config_read(runId: str = Query(...), includeText: bool = Query(False)):
    try:
        result = read_config(ROOT_UPLOAD, runId, includeText)
        return api_response(True, 0, "ok", config=result)
    except (ValueError, FileNotFoundError) as e:
        return JSONResponse(
            content=api_response(False, 404, "config not found", error=str(e)),
            status_code=404,
        )
    except Exception as e:
        return JSONResponse(
            content=api_response(False, 500, "config read failed", error=f"{type(e).__name__}: {e}"),
            status_code=500,
        )


@app.post("/api/runner/train")
def start_train(
    runId: str = Query(..., description="前端/Java 只需传 runId")
):
    try:
        mode = current_runner_mode()
    except ValueError as e:
        return JSONResponse(
            content=api_response(False, 400, "invalid runner mode", error=str(e)),
            status_code=400,
        )

    # 1) 原始模式读取生成的 MMDet 配置；固定模式执行代码中写死的测试脚本。
    cfg_path = find_cfg_path(runId)
    if mode == "original" and not cfg_path.exists():
        return JSONResponse(
            content=api_response(False, 400, "cfg_path not found", error=f"cfg_path not found: {str(cfg_path)}"),
            status_code=400,
        )

    # 2) 准备 work_dir & 日志文件
    work_dir = make_work_dir(runId)
    ensure_dir(work_dir)
    log_path = work_dir / "train.log"

    # 3) 组装命令。original 分支保持原命令；fixed 分支完全使用固定值。
    if mode == "original":
        cmd = [PY_EXE, "-u", TRAIN_PY, str(cfg_path), "--work-dir", str(work_dir), "--launcher", "none", *EXTRA_ARGS]
        process_cwd = REPO_ROOT
        executed_script = TRAIN_PY
    else:
        try:
            cmd, process_cwd, executed_script = build_fixed_execution(
                FIXED_PYTHON_PATH,
                FIXED_EXEC_DIR,
                FIXED_COMMAND_LINE,
                runId,
                work_dir,
            )
        except (ValueError, FileNotFoundError) as e:
            return JSONResponse(
                content=api_response(False, 500, "fixed runner configuration invalid", error=str(e)),
                status_code=500,
            )
        cfg_path = Path(executed_script)
    cmd_str = fmt_cmd(cmd)

    # 4) 写 header 并 ClearML Task.init（调度仍在本 Runner HTTP 进程内）
    ensure_dir(log_path.parent)
    write_header(log_path, process_cwd, str(work_dir), str(cfg_path), cmd_str)
    _append_log(log_path, f"[server] runner_mode={mode}")
    _append_log(log_path, f"[server] executed_script={executed_script}")

    env = make_env()

    clearml_task = None
    clearml_task_id: Optional[str] = None
    if mode == "original":
        try:
            clearml_task, clearml_task_id = init_clearml_task(runId, env, log_path)
        except RuntimeError as e:
            return JSONResponse(
                content=api_response(False, 400, "clearml_required_failed", error=str(e)),
                status_code=400,
            )
    else:
        _append_log(log_path, "[clearml] skipped: fixed runner mode")

    exit_code = 1
    proc = None
    try:
        with open(log_path, "a", encoding="utf-8") as log_f:
            popen_options = {}
            if os.name == "nt":
                popen_options["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP
            else:
                popen_options["start_new_session"] = True
            proc = subprocess.Popen(
                cmd,
                cwd=process_cwd,
                stdout=log_f,
                stderr=subprocess.STDOUT,
                env=env,
                shell=False,
                close_fds=False,
                **popen_options,
            )
            with _ACTIVE_PROCESSES_LOCK:
                _ACTIVE_PROCESSES[runId] = proc
            try:
                exit_code = proc.wait()
            finally:
                with _ACTIVE_PROCESSES_LOCK:
                    if _ACTIVE_PROCESSES.get(runId) is proc:
                        _ACTIVE_PROCESSES.pop(runId, None)
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
        "repo_root": process_cwd,
        "runner_mode": mode,
        "executed_script": executed_script,
        "results_file": str(results_file),
        "results_txt": parsed,
        "clearml_task_id": clearml_task_id,
    }
    if exit_code != 0 and err_snippet:
        resp["error"] = err_snippet[:8000]

    return JSONResponse(content=resp, status_code=200)


@app.post("/api/runner/stop")
def stop_train(runId: str = Query(..., description="训练任务名称/runId")):
    if not runId or runId in (".", "..") or any(c in runId for c in ("/", "\\")):
        return JSONResponse(content=api_response(False, 400, "invalid runId"), status_code=400)
    with _ACTIVE_PROCESSES_LOCK:
        proc = _ACTIVE_PROCESSES.get(runId)
    if proc is None or proc.poll() is not None:
        return JSONResponse(
            content=api_response(False, 404, "active training process not found", run_id=runId),
            status_code=404,
        )
    pid = proc.pid
    try:
        stop_process_tree(proc)
        proc.wait(timeout=10)
    except (OSError, subprocess.TimeoutExpired) as e:
        return JSONResponse(
            content=api_response(False, 500, "failed to stop training process", error=str(e)),
            status_code=500,
        )
    return api_response(True, 0, "training stopped", run_id=runId, pid=pid, stopped=True)


@app.get("/api/runner/log/latest")
def latest_train_log(
    runId: str = Query(..., description="训练任务名称/runId"),
    tailLines: int = Query(1000, ge=10, le=5000),
):
    try:
        log_path = find_latest_train_log(runId)
    except ValueError as e:
        return JSONResponse(
            content=api_response(False, 400, "invalid runId", error=str(e)),
            status_code=400,
        )
    if log_path is None:
        return JSONResponse(
            content=api_response(False, 404, "train log not found", run_id=runId),
            status_code=404,
        )

    text = log_path.read_text(encoding="utf-8", errors="replace")
    all_lines = text.splitlines()
    stat = log_path.stat()
    return api_response(
        True,
        0,
        "ok",
        run_id=runId,
        runner_mode=current_runner_mode(),
        work_dir=str(log_path.parent),
        log_path=str(log_path),
        modified_time=datetime.fromtimestamp(stat.st_mtime).astimezone().isoformat(timespec="seconds"),
        total_lines=len(all_lines),
        returned_lines=min(len(all_lines), tailLines),
        content="\n".join(all_lines[-tailLines:]),
    )


@app.post("/api/runner/result/delete")
def delete_result_files(
    runId: str = Query(..., description="训练任务名称/runId"),
    finishedAt: Optional[str] = Query(None, description="结果完成时间，用于匹配对应训练目录"),
):
    try:
        work_dir = find_result_work_dir(runId, finishedAt)
    except ValueError as e:
        return JSONResponse(
            content=api_response(False, 400, "invalid delete request", error=str(e)),
            status_code=400,
        )
    if work_dir is None:
        return JSONResponse(
            content=api_response(False, 404, "result files not found", run_id=runId),
            status_code=404,
        )
    try:
        shutil.rmtree(work_dir)
    except OSError as e:
        return JSONResponse(
            content=api_response(False, 500, "delete result files failed", error=str(e)),
            status_code=500,
        )
    return api_response(True, 0, "deleted", deleted=True, work_dir=str(work_dir))


# 健康检查
@app.get("/health")
def health():
    try:
        mode = current_runner_mode()
        with _ACTIVE_PROCESSES_LOCK:
            active_runs = list(_ACTIVE_PROCESSES.keys())
        return api_response(True, 0, "ok", time=now_str(), runner_mode=mode, active_runs=active_runs)
    except ValueError as e:
        return api_response(False, 400, "invalid runner mode", time=now_str(), error=str(e))


if __name__ == "__main__":
    try:
        import uvicorn

        uvicorn.run("mmdet_runner_server:app", host="127.0.0.1", port=8009, reload=False)
    except Exception as e:
        print(f"Failed to run uvicorn: {e}", file=sys.stderr)
        print("You can launch with: uvicorn mmdet_runner_server:app --host 127.0.0.1 --port 8009")
