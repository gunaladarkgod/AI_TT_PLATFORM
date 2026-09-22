# -*- coding: utf-8 -*-
# uvicorn mmdet_runner_server:app --host 127.0.0.1 --port 8009

import os
import sys
import re
import json
import signal
import shlex
import shutil
import subprocess
import threading
import time
import base64
import mimetypes
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from fastapi import Body, FastAPI, Query
from fastapi.responses import JSONResponse

from mmdet_config_service import generate_config, list_templates, read_config, template_defaults

app = FastAPI(title="Platform Training Runner (sync)")

_ACTIVE_PROCESSES: Dict[str, subprocess.Popen] = {}
_ACTIVE_PROCESSES_LOCK = threading.Lock()

_RUNNER_DIR = Path(__file__).resolve().parent
_WORKSPACE_OVERRIDE = os.getenv("APP_WORKSPACE_ROOT", "").strip()
_REPO_ROOT_DIR = (Path(_WORKSPACE_OVERRIDE) if _WORKSPACE_OVERRIDE else _RUNNER_DIR.parent.parent.parent).expanduser().resolve()
_ENGINE_ROOT_DIR = _REPO_ROOT_DIR / "engines" / "mmdet_run"
_ACTIVE_PID_DIR = Path(os.getenv("MMDET_ACTIVE_PID_DIR", str(_ENGINE_ROOT_DIR / "logs" / "active_pids")))

# 新任务可为每个任务显式指定训练解释器；历史任务没有保存该字段时，
# 使用启动 Runner 的 MMDet Python 作为兼容回退，避免发布后只留下空目录。
# 1) mmdetection 仓库根目录
REPO_ROOT = os.getenv("MMDET_REPO_ROOT", str(_ENGINE_ROOT_DIR / "mmdetection-3.0.0"))
# 3) train.py 路径（用 REPO_ROOT 拼出来，避免写两份）
TRAIN_PY = str(Path(REPO_ROOT) / "tools" / "train.py")
# 4) 前端/Java 上传的配置文件根目录
ROOT_UPLOAD = os.getenv("MMDET_UPLOAD_ROOT", str(_ENGINE_ROOT_DIR / "myfiles"))
# 5) 训练产出目录根路径
DEFAULT_WORK_ROOT = os.getenv("MMDET_WORK_ROOT", str(_REPO_ROOT_DIR / "artifacts" / "mmdet_runs"))
DEFAULT_TRAINING_PYTHON = (
    os.getenv("MMDET_TRAINING_PYTHON")
    or os.getenv("MMDET_PY_EXE")
    or os.getenv("RUNNER_PYTHON")
    or sys.executable
)

# Runner 模式直接写在本文件中，不从环境变量或启动脚本读取。
# 可选值："original"（MMDet 标准流程）/ "fixed"（固定命令流程）。
RUNNER_MODE = "original"

# fixed 模式的全部执行信息也写在本文件中。
# 实际效果：在 FIXED_EXEC_DIR 中执行：
#   FIXED_PYTHON_PATH -u tools/runner_fixed_test.py --run-id ... --work-dir ...
FIXED_PYTHON_PATH = ""
FIXED_EXEC_DIR = str(_ENGINE_ROOT_DIR / "mmdetection-3.0.0")
FIXED_COMMAND_LINE = "tools/runner_fixed_test.py --run-id {run_id} --work-dir {work_dir}"
FIXED_WORK_ROOT = str(_REPO_ROOT_DIR / "artifacts" / "custom")

# 追加的可选参数（保持你之前成功用过的设置）
EXTRA_ARGS = ["--cfg-options", "default_scope=mmdet"]

def _append_log(log_path: Path, text: str) -> None:
    try:
        with open(log_path, "a", encoding="utf-8") as f:
            f.write(text.rstrip() + "\n")
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


def make_env(engine: str) -> Dict[str, str]:
    """Build child-process environment without leaking MMDet imports into other engines."""
    env = os.environ.copy()
    env["PYTHONUNBUFFERED"] = "1"
    env["PYTHONIOENCODING"] = "utf-8"
    env["KMP_DUPLICATE_LIB_OK"] = "TRUE"
    if engine == "mmdet":
        old_py = env.get("PYTHONPATH", "")
        env["PYTHONPATH"] = REPO_ROOT if not old_py else REPO_ROOT + os.pathsep + old_py
    return env


def find_cfg_path(run_id: str) -> Path:
    # D:/.../myfiles/modelcfg/{runId}/config.py
    return Path(ROOT_UPLOAD) / "modelcfg" / run_id / "config.py"


def make_work_dir(run_id: str, mode_override: Optional[str] = None, root_override: Optional[str] = None) -> Path:
    # D:/xgls/artifacts/mmdet_runs/{runId}_from_pyserver_sync_{ts}
    mode = current_runner_mode(mode_override)
    root = root_override or (FIXED_WORK_ROOT if mode == "fixed" else DEFAULT_WORK_ROOT)
    return Path(root) / f"{run_id}_from_pyserver_sync_{ts_for_path()}"


_CATALOG_SEGMENT = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")


def resolve_research_work_root(payload: Dict[str, Any], fallback_root: Path) -> Path:
    """Put catalogued research runs under artifacts/research/<direction>/<baseline>.

    Historical and ordinary platform tasks have no research identifiers and retain their
    existing output root.  The identifiers are deliberately restricted to catalog-style
    slugs so a task payload cannot escape the artifacts directory.
    """
    direction = str(payload.get("research_direction") or "").strip().lower()
    baseline = str(payload.get("research_baseline") or "").strip().lower()
    if not direction and not baseline:
        return fallback_root
    if not direction or not baseline:
        raise ValueError("research_direction and research_baseline must be provided together")
    if not _CATALOG_SEGMENT.fullmatch(direction) or not _CATALOG_SEGMENT.fullmatch(baseline):
        raise ValueError("research_direction and research_baseline must use lowercase catalog slugs")
    return (_REPO_ROOT_DIR / "artifacts" / "research" / direction / baseline).resolve()


def resolve_work_root(root_override: Optional[str], mode_override: Optional[str] = None) -> Path:
    """解析并限制训练产物目录到项目 artifacts 下，避免日志/删除操作越界。"""
    mode = current_runner_mode(mode_override)
    raw_root = root_override or (FIXED_WORK_ROOT if mode == "fixed" else DEFAULT_WORK_ROOT)
    root = Path(resolve_project_path(raw_root)).resolve()
    artifacts_root = (_REPO_ROOT_DIR / "artifacts").resolve()
    if root != artifacts_root and artifacts_root not in root.parents:
        raise ValueError("workRoot must be inside the project artifacts directory")
    return root


def find_latest_train_log(run_id: str, work_root: Optional[str] = None) -> Optional[Path]:
    """查找该 runId 最近一次运行产生的 train.log。"""
    if not run_id or run_id in (".", "..") or any(c in run_id for c in ("/", "\\")):
        raise ValueError("invalid runId")
    root = resolve_work_root(work_root)
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


def find_result_work_dir(
    run_id: str, finished_at: Optional[str], work_root: Optional[str] = None
) -> Optional[Path]:
    """在 Runner 输出根目录内定位与结果完成时间最接近的单个训练目录。"""
    if not run_id or run_id in (".", "..") or any(c in run_id for c in ("/", "\\")):
        raise ValueError("invalid runId")
    root = resolve_work_root(work_root)
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


def current_runner_mode(mode_override: Optional[str] = None) -> str:
    mode = (mode_override or RUNNER_MODE or "original").strip().lower()
    if mode not in ("original", "fixed"):
        raise ValueError(
            f"unsupported runner_mode={mode!r}; expected 'original' or 'fixed'"
        )
    return mode


def resolve_project_path(path_text: str) -> str:
    path = Path(path_text).expanduser()
    if not path.is_absolute():
        path = (_REPO_ROOT_DIR / path).resolve()
    return str(migrate_legacy_engine_path(path))


def migrate_legacy_engine_path(path: Path) -> Path:
    """兼容目录重组前已保存的 MMDet 固定 Runner 路径。

    历史任务会把 ``<workspace>/mmdet_run/...`` 写入数据库。仅当旧目标
    不存在、且新引擎目录中存在完全对应的文件时迁移，避免改写用户配置的
    外部执行目录或其他不存在路径。
    """
    legacy_engine_root = _REPO_ROOT_DIR / "mmdet_run"
    try:
        relative_path = path.resolve().relative_to(legacy_engine_root.resolve())
    except ValueError:
        return path

    migrated_path = _ENGINE_ROOT_DIR / relative_path
    if not path.exists() and migrated_path.exists():
        return migrated_path.resolve()
    return path


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


def build_ultralytics_execution(payload: Dict[str, Any], work_dir: Path) -> Tuple[List[str], str, str]:
    """Use the Python selected when the task was created; Runner never owns the engine environment."""
    if str(payload.get("data_format") or "").strip().lower() != "yolo":
        raise ValueError("ultralytics requires data_format=yolo")
    python_path = Path(str(payload.get("training_python_path") or "")).expanduser()
    if not python_path.is_file():
        raise FileNotFoundError(f"training Python not found: {python_path}")
    model = str(payload.get("ultralytics_model") or "").strip()
    data = str(payload.get("ultralytics_data") or "").strip()
    if not model:
        raise ValueError("missing ultralytics_model")
    if not data:
        raise ValueError("missing ultralytics_data")
    data_path = Path(resolve_project_path(data))
    if not data_path.is_file():
        raise FileNotFoundError(f"Ultralytics data YAML not found: {data_path}")
    try:
        dependency_check = subprocess.run(
            [str(python_path), "-c", "import ultralytics; print(ultralytics.__version__)"],
            capture_output=True,
            text=True,
            timeout=20,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        raise ValueError(f"timed out while checking ultralytics in task Python: {python_path}") from exc
    installed_version = (dependency_check.stdout or "").strip()
    if dependency_check.returncode != 0:
        detail = (dependency_check.stderr or dependency_check.stdout or "import failed").strip()
        raise ValueError(
            "task training Python cannot import ultralytics==8.4.115; "
            f"python={python_path}; detail={detail[:800]}"
        )
    if installed_version != "8.4.115":
        raise ValueError(
            "task training Python has an unsupported ultralytics version; "
            f"expected=8.4.115, actual={installed_version or 'unknown'}"
        )
    legacy_allowed = {"epochs", "imgsz", "batch", "device", "workers", "patience", "optimizer", "lr0", "lrf", "seed", "pretrained"}
    declared = payload.get("ultralytics_allowed_parameters")
    if declared is None:
        # Historical tasks predate research manifests; keep their established whitelist.
        allowed = legacy_allowed
    elif not isinstance(declared, list) or not all(isinstance(key, str) and key.strip() for key in declared):
        raise ValueError("ultralytics_allowed_parameters must be a list of declared parameter keys")
    else:
        allowed = {key.strip() for key in declared}
    parameters = payload.get("ultralytics_parameters") or {}
    if not isinstance(parameters, dict):
        raise ValueError("ultralytics_parameters must be an object")
    undeclared = sorted(str(key) for key in parameters if key not in allowed)
    if undeclared:
        raise ValueError("Ultralytics task contains undeclared parameters: " + ", ".join(undeclared))
    spec_path = work_dir / "ultralytics_run.json"
    spec_path.write_text(json.dumps({"model": model, "data": str(data_path), "work_dir": str(work_dir),
                                     "parameters": parameters,
                                     "improvements": payload.get("ultralytics_improvement_ids") or []}, ensure_ascii=False), encoding="utf-8")
    worker = _RUNNER_DIR / "ultralytics_train_worker.py"
    if not worker.is_file():
        raise FileNotFoundError(f"Ultralytics worker not found: {worker}")
    return [str(python_path), "-u", str(worker), "--spec", str(spec_path)], str(work_dir), str(worker)


def safe_upload_name(name: str) -> str:
    """Preserve only a harmless image filename inside the result directory."""
    candidate = Path(str(name or "image")).name
    stem = re.sub(r"[^A-Za-z0-9_-]+", "_", Path(candidate).stem).strip("._") or "image"
    suffix = Path(candidate).suffix.lower()
    if suffix not in {".jpg", ".jpeg", ".png", ".bmp", ".gif"}:
        suffix = ".png"
    return stem[:80] + suffix


def find_inference_checkpoint(work_dir: Path, engine: str) -> Optional[Path]:
    if engine == "ultralytics":
        preferred = work_dir / "weights" / "best.pt"
        if preferred.is_file():
            return preferred
        candidates = list(work_dir.rglob("*.pt"))
    else:
        candidates = list(work_dir.rglob("*.pth"))
    candidates = [path for path in candidates if path.is_file()]
    if not candidates:
        return None
    best = [path for path in candidates if path.name.lower().startswith("best")]
    return max(best or candidates, key=lambda path: path.stat().st_mtime)


def inference_engine(payload: Dict[str, Any]) -> str:
    engine = str(payload.get("engine") or "mmdet").strip().lower()
    if engine in {"mmdet", "ultralytics"}:
        return engine
    if engine in {"custom", "paper"}:
        raise ValueError("该训练结果未提供统一推理入口，暂不能执行模型推理")
    raise ValueError(f"unsupported inference engine: {engine}")


def read_inference_result(output_dir: Path) -> Dict[str, Any]:
    result_file = output_dir / "result.json"
    if not result_file.is_file():
        raise RuntimeError("inference worker did not write result.json")
    result = json.loads(result_file.read_text(encoding="utf-8"))
    image_path = Path(str(result.get("output_path") or "")).resolve()
    if output_dir not in image_path.parents or not image_path.is_file():
        raise RuntimeError("inference output image is missing or outside the result directory")
    image_bytes = image_path.read_bytes()
    if len(image_bytes) > 15 * 1024 * 1024:
        raise RuntimeError("inference output image is too large to return")
    return {
        "output_path": str(image_path),
        "image_base64": base64.b64encode(image_bytes).decode("ascii"),
        "image_mime": mimetypes.guess_type(image_path.name)[0] or "image/png",
        "detections": (result.get("detections") or [])[:200],
    }


@app.post("/api/runner/engines/ultralytics/check")
def check_ultralytics_environment(payload: Dict[str, Any] = Body(...)):
    """Check only the Python selected for a future task; never creates an environment."""
    python_path = Path(str(payload.get("training_python_path") or "")).expanduser()
    if not python_path.is_file():
        return JSONResponse(
            content=api_response(False, 400, "training Python not found", python=str(python_path)),
            status_code=400,
        )
    try:
        checked = subprocess.run(
            [str(python_path), "-c", "import ultralytics; print(ultralytics.__version__)"],
            capture_output=True,
            text=True,
            timeout=20,
            check=False,
        )
    except subprocess.TimeoutExpired:
        return JSONResponse(
            content=api_response(False, 408, "Ultralytics environment check timed out", python=str(python_path)),
            status_code=408,
        )
    version = (checked.stdout or "").strip()
    if checked.returncode != 0:
        detail = (checked.stderr or checked.stdout or "import failed").strip()
        return JSONResponse(
            content=api_response(
                False,
                400,
                "Ultralytics is unavailable in the selected Python",
                python=str(python_path),
                required_version="8.4.115",
                error=detail[:1200],
            ),
            status_code=400,
        )
    ok = version == "8.4.115"
    return JSONResponse(
        content=api_response(
            ok,
            0 if ok else 400,
            "Ultralytics environment is ready" if ok else "Ultralytics version does not match",
            python=str(python_path),
            version=version,
            required_version="8.4.115",
        ),
        status_code=200 if ok else 400,
    )


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


def active_pid_path(run_id: str) -> Path:
    safe = re.sub(r"[^\w\-.\u4e00-\u9fff]+", "_", run_id).strip("._")
    return _ACTIVE_PID_DIR / f"{safe}.pid"


def write_active_pid(run_id: str, pid: int) -> None:
    _ACTIVE_PID_DIR.mkdir(parents=True, exist_ok=True)
    active_pid_path(run_id).write_text(str(pid), encoding="utf-8")


def clear_active_pid(run_id: str, pid: Optional[int] = None) -> None:
    path = active_pid_path(run_id)
    try:
        if pid is None or not path.is_file() or path.read_text(encoding="utf-8").strip() == str(pid):
            path.unlink(missing_ok=True)
    except OSError:
        pass


def stop_persisted_pid(run_id: str) -> Optional[int]:
    path = active_pid_path(run_id)
    if not path.is_file():
        return None
    try:
        pid = int(path.read_text(encoding="utf-8").strip())
        if os.name == "nt":
            completed = subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"],
                                       capture_output=True, text=True, timeout=15, check=False)
            if completed.returncode != 0:
                raise OSError(completed.stderr.strip() or "taskkill failed")
        else:
            pgid = os.getpgid(pid)
            os.killpg(pgid, signal.SIGTERM)
            for _ in range(30):
                try:
                    os.kill(pid, 0)
                except ProcessLookupError:
                    break
                time.sleep(0.1)
            else:
                os.killpg(pgid, signal.SIGKILL)
        clear_active_pid(run_id, pid)
        return pid
    except ProcessLookupError:
        clear_active_pid(run_id)
        return None


# =========================
# API
# =========================
@app.get("/api/config/templates")
def config_templates():
    return api_response(True, 0, "ok", templates=list_templates(ROOT_UPLOAD))


@app.get("/api/config/template/defaults")
def config_template_defaults(template: str = Query(...)):
    try:
        result = template_defaults(ROOT_UPLOAD, template)
        return api_response(True, 0, "ok", defaults=result)
    except (ValueError, FileNotFoundError) as e:
        return JSONResponse(
            content=api_response(False, 404, "template not found", error=str(e)),
            status_code=404,
        )
    except Exception as e:
        return JSONResponse(
            content=api_response(False, 500, "template defaults read failed", error=f"{type(e).__name__}: {e}"),
            status_code=500,
        )


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
    runId: str = Query(..., description="前端/Java 只需传 runId"),
    payload: Optional[Dict[str, Any]] = Body(None),
):
    payload = payload or {}
    engine = str(payload.get("engine") or "mmdet").strip().lower()
    if engine not in ("mmdet", "ultralytics", "custom", "paper"):
        return JSONResponse(content=api_response(False, 400, "unsupported engine", error=engine), status_code=400)
    if engine == "paper":
        return JSONResponse(content=api_response(False, 400, "paper engine not ready",
                                                  error="paper baseline must pin upstream source and entry first"), status_code=400)
    try:
        mode = current_runner_mode(str(payload.get("runner_mode") or ""))
    except ValueError as e:
        return JSONResponse(
            content=api_response(False, 400, "invalid runner mode", error=str(e)),
            status_code=400,
        )

    # 每次发布都先创建独立运行目录和日志。这样即使参数校验未通过，
    # artifacts 下也会保留可追踪的失败原因，而不会产生空目录。
    configured_root = str(payload.get("runner_work_root") or "").strip()
    if not configured_root:
        configured_root = str(payload.get("fixed_work_root") or FIXED_WORK_ROOT) if mode == "fixed" else DEFAULT_WORK_ROOT
    try:
        base_work_root = resolve_work_root(configured_root, mode)
        work_root = resolve_research_work_root(payload, base_work_root)
    except ValueError as e:
        return JSONResponse(content=api_response(False, 400, "invalid artifact root", error=str(e)), status_code=400)
    work_dir = make_work_dir(runId, mode, str(work_root))
    ensure_dir(work_dir)
    log_path = work_dir / "train.log"
    _append_log(log_path, f"[server] request_at={now_str()}")
    _append_log(log_path, f"[server] run_id={runId}")
    _append_log(log_path, f"[server] engine={engine}")
    _append_log(log_path, f"[server] runner_mode={mode}")
    _append_log(log_path, f"[server] work_root={work_root}")
    _append_log(log_path, f"[server] work_dir={work_dir}")

    def reject_before_start(status_code: int, message: str, error: str) -> JSONResponse:
        _append_log(log_path, "=== PROCESS NOT STARTED ===")
        _append_log(log_path, f"[server] failure_stage=preflight")
        _append_log(log_path, f"[server] message={message}")
        _append_log(log_path, f"[server] error={error}")
        return JSONResponse(
            content=api_response(
                False,
                status_code,
                message,
                error=error,
                work_dir=str(work_dir),
                log=str(log_path),
                runner_mode=mode,
            ),
            status_code=status_code,
        )

    # 原始模式读取生成的 MMDet 配置；固定模式执行代码中写死的测试脚本。
    cfg_path = find_cfg_path(runId)
    if engine != "ultralytics" and mode == "original" and not cfg_path.exists():
        return reject_before_start(400, "cfg_path not found", f"cfg_path not found: {str(cfg_path)}")

    # 组装命令。original 分支保持原命令；fixed 分支完全使用固定值。
    if engine == "ultralytics":
        try:
            cmd, process_cwd, executed_script = build_ultralytics_execution(payload, work_dir)
        except (ValueError, FileNotFoundError) as e:
            return reject_before_start(400, "ultralytics task configuration invalid", str(e))
        cfg_path = Path(executed_script)
    elif mode == "original":
        training_python = str(payload.get("training_python_path") or "").strip()
        if not training_python:
            training_python = DEFAULT_TRAINING_PYTHON
            _append_log(
                log_path,
                f"[server] training_python_path missing; fallback_to_runner_python={training_python}",
            )
        python_file = Path(training_python).expanduser()
        if not python_file.is_file():
            return reject_before_start(400, "training Python not found", str(python_file))
        cmd = [str(python_file), "-u", TRAIN_PY, str(cfg_path), "--work-dir", str(work_dir), "--launcher", "none", *EXTRA_ARGS]
        process_cwd = REPO_ROOT
        executed_script = TRAIN_PY
    else:
        try:
            fixed_python = str(payload.get("fixed_python_path") or FIXED_PYTHON_PATH)
            fixed_exec_dir = resolve_project_path(str(payload.get("fixed_exec_dir") or FIXED_EXEC_DIR))
            fixed_command = str(payload.get("fixed_command_line") or FIXED_COMMAND_LINE)
            cmd, process_cwd, executed_script = build_fixed_execution(
                fixed_python,
                fixed_exec_dir,
                fixed_command,
                runId,
                work_dir,
            )
        except (ValueError, FileNotFoundError) as e:
            return reject_before_start(500, "fixed runner configuration invalid", str(e))
        cfg_path = Path(executed_script)
    cmd_str = fmt_cmd(cmd)

    # 写日志并启动训练子进程。
    try:
        ensure_dir(log_path.parent)
        write_header(log_path, process_cwd, str(work_dir), str(cfg_path), cmd_str)
        _append_log(log_path, f"[server] executed_script={executed_script}")
    except Exception as e:
        return reject_before_start(500, "failed to prepare training log", f"{type(e).__name__}: {e}")

    env = make_env(engine)

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
            write_active_pid(runId, proc.pid)
            try:
                exit_code = proc.wait()
            finally:
                with _ACTIVE_PROCESSES_LOCK:
                    if _ACTIVE_PROCESSES.get(runId) is proc:
                        _ACTIVE_PROCESSES.pop(runId, None)
                clear_active_pid(runId, proc.pid)
    except Exception as e:
        _append_log(log_path, "=== PROCESS START FAILED ===")
        _append_log(log_path, f"[server] failure_stage=process_start")
        _append_log(log_path, f"[server] error={type(e).__name__}: {e}")
        return JSONResponse(
            content=api_response(
                False,
                500,
                "failed to run process",
                error=f"failed to start or wait process: {e}",
                work_dir=str(work_dir),
                log=str(log_path),
                runner_mode=mode,
            ),
            status_code=500,
        )
    # 6) 读取日志并解析 COCO 指标
    try:
        text = Path(log_path).read_text(encoding="utf-8", errors="ignore")
    except Exception:
        text = ""

    parsed = parse_coco_from_log(text) if engine == "mmdet" else None

    err_snippet = ""
    if exit_code != 0:
        err_snippet = extract_train_error_snippet(text)

    # 7) 兜底提示
    if parsed is None:
        if err_snippet:
            parsed = "Training failed. Last error from log:\n" + err_snippet[:1200]
        elif engine == "ultralytics":
            parsed = "Ultralytics training finished. See results.csv and weights/best.pt in the run directory."
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
        "work_root": str(work_root),
        "log": str(log_path),
        "cmd": cmd_str,
        "repo_root": process_cwd,
        "runner_mode": mode,
        "executed_script": executed_script,
        "results_file": str(results_file),
        "results_txt": parsed,
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
        try:
            persisted_pid = stop_persisted_pid(runId)
        except OSError as e:
            return JSONResponse(content=api_response(False, 500, "failed to stop persisted training process", error=str(e)), status_code=500)
        if persisted_pid is not None:
            return api_response(True, 0, "training stopped from persisted pid", run_id=runId,
                                pid=persisted_pid, stopped=True, source="pid_file")
        return JSONResponse(content=api_response(False, 404, "active training process not found", run_id=runId), status_code=404)
    pid = proc.pid
    try:
        stop_process_tree(proc)
        proc.wait(timeout=10)
    except (OSError, subprocess.TimeoutExpired) as e:
        return JSONResponse(
            content=api_response(False, 500, "failed to stop training process", error=str(e)),
            status_code=500,
        )
    clear_active_pid(runId, pid)
    return api_response(True, 0, "training stopped", run_id=runId, pid=pid, stopped=True, source="memory")


@app.get("/api/runner/log/latest")
def latest_train_log(
    runId: str = Query(..., description="训练任务名称/runId"),
    tailLines: int = Query(1000, ge=10, le=5000),
    workRoot: Optional[str] = Query(None, description="任务输出根目录；固定模式任务使用保存的 artifacts/custom 路径"),
):
    try:
        log_path = find_latest_train_log(runId, workRoot)
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
    workRoot: Optional[str] = Query(None, description="任务输出根目录；用于定位自定义任务产物"),
):
    try:
        work_dir = find_result_work_dir(runId, finishedAt, workRoot)
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


def open_local_directory(directory: Path) -> None:
    """跨平台打开已由结果定位逻辑确认过的训练目录。"""
    if sys.platform.startswith("win"):
        os.startfile(str(directory))
        return
    if sys.platform == "darwin":
        subprocess.Popen(["open", str(directory)])
        return
    subprocess.Popen(["xdg-open", str(directory)])


@app.post("/api/runner/result/open")
def open_result_directory(
    runId: str = Query(..., description="训练任务名称/runId"),
    finishedAt: Optional[str] = Query(None, description="结果完成时间，用于匹配对应训练目录"),
    workRoot: Optional[str] = Query(None, description="任务输出根目录；用于定位自定义任务产物"),
):
    try:
        work_dir = find_result_work_dir(runId, finishedAt, workRoot)
    except ValueError as e:
        return JSONResponse(
            content=api_response(False, 400, "invalid open request", error=str(e)),
            status_code=400,
        )
    if work_dir is None:
        return JSONResponse(
            content=api_response(False, 404, "result files not found", run_id=runId),
            status_code=404,
        )
    # Runner 仅负责定位目录；实际打开由 Java 后端调用系统文件浏览器，
    # 避免 Windows 后台 Python 进程触发 WinError 5。
    return api_response(True, 0, "resolved", work_dir=str(work_dir))


@app.post("/api/runner/result/infer")
def infer_result(
    runId: str = Query(..., description="训练任务名称/runId"),
    finishedAt: Optional[str] = Query(None, description="用于定位单次训练结果"),
    workRoot: Optional[str] = Query(None, description="任务输出根目录"),
    payload: Optional[Dict[str, Any]] = Body(None),
):
    """Run one uploaded image through the exact checkpoint of a completed result."""
    payload = payload or {}
    try:
        work_dir = find_result_work_dir(runId, finishedAt, workRoot)
        if work_dir is None:
            raise FileNotFoundError("result files not found")
        engine = inference_engine(payload)
        encoded = str(payload.get("image_base64") or "")
        if not encoded:
            raise ValueError("missing inference image")
        image_bytes = base64.b64decode(encoded, validate=True)
        if not image_bytes or len(image_bytes) > 10 * 1024 * 1024:
            raise ValueError("inference image must be between 1 byte and 10 MB")
        checkpoint = find_inference_checkpoint(work_dir, engine)
        if checkpoint is None:
            raise FileNotFoundError("no trained checkpoint found for this result")
        inference_dir = work_dir / "inference" / ts_for_path()
        input_dir = inference_dir / "input"
        output_dir = inference_dir / "output"
        ensure_dir(input_dir)
        input_path = input_dir / safe_upload_name(payload.get("image_name"))
        input_path.write_bytes(image_bytes)
        if engine == "mmdet":
            config_path = work_dir / "config.py"
            if not config_path.is_file():
                raise FileNotFoundError("result config snapshot not found; this historical result cannot be inferred safely")
            python_path = Path(str(payload.get("training_python_path") or DEFAULT_TRAINING_PYTHON)).expanduser()
            worker = _RUNNER_DIR / "mmdet_infer_worker.py"
            command = [str(python_path), "-u", str(worker), "--config", str(config_path),
                       "--checkpoint", str(checkpoint), "--input", str(input_path),
                       "--output-dir", str(output_dir)]
        else:
            python_path = Path(str(payload.get("training_python_path") or "")).expanduser()
            worker = _RUNNER_DIR / "ultralytics_infer_worker.py"
            command = [str(python_path), "-u", str(worker), "--checkpoint", str(checkpoint),
                       "--input", str(input_path), "--output-dir", str(output_dir)]
        if not python_path.is_file():
            raise FileNotFoundError(f"inference Python not found: {python_path}")
        if not worker.is_file():
            raise FileNotFoundError(f"inference worker not found: {worker}")
        log_path = inference_dir / "inference.log"
        with log_path.open("w", encoding="utf-8") as log_file:
            completed = subprocess.run(
                command,
                cwd=REPO_ROOT if engine == "mmdet" else str(work_dir),
                env=make_env(engine),
                stdout=log_file,
                stderr=subprocess.STDOUT,
                timeout=180,
                check=False,
            )
        if completed.returncode != 0:
            detail = log_path.read_text(encoding="utf-8", errors="replace")[-4000:]
            raise RuntimeError("inference failed: " + detail)
        response = read_inference_result(output_dir)
        response.update({"run_id": runId, "engine": engine, "checkpoint": str(checkpoint),
                         "inference_dir": str(inference_dir), "log_path": str(log_path)})
        return api_response(True, 0, "inference finished", **response)
    except subprocess.TimeoutExpired:
        return JSONResponse(content=api_response(False, 408, "inference timed out"), status_code=408)
    except (ValueError, FileNotFoundError, RuntimeError) as e:
        return JSONResponse(content=api_response(False, 400, "inference failed", error=str(e)), status_code=400)
    except Exception as e:
        return JSONResponse(content=api_response(False, 500, "inference failed", error=f"{type(e).__name__}: {e}"), status_code=500)


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
