#!/usr/bin/env bash
# =============================================================================
# MMDet Python Runner（FastAPI + uvicorn）
#
# 训练任务仍由 Java → POST /api/runner/train 调度；本脚本负责启动 Runner 进程。
# ClearML：与 mmdet_runner_server.py 一致 —— 每个训练在启动子进程前 Task.init，并把
# CLEARML_* / CLEARML_TASK_ID 传给 MMDet 子进程，便于在 ClearML Web 查看实验与指标。
#
# 凭证加载顺序（与 Python 中 _candidate_clearml_env_files 一致）：
#   1) $MMDET_CLEARML_ENV_FILE（若设置且文件存在）
#   2) 本目录 env.clearml.local
#   3) 仓库 backend/env.clearml.local（推荐：与后端共用同一份）
#
# 密钥模板：backend/env.clearml.example → 复制为 backend/env.clearml.local
#
# 可选环境变量：
#   RUNNER_PYTHON      显式指定解释器（一般无需设置；默认优先 .conda_runner）
#   RUNNER_PIP_INSTALL 设为 1 时用当前 PY 执行 pip install -r requirements-uvicorn.txt
#   MMDET_PY_EXE       训练子进程解释器（仅传给 Python，见 mmdet_runner_server.py）
#
# 首次部署 Runner：建议在脚本目录创建独立 conda（避免系统 Python 混入 ~/.local 导致 clearml 导入失败）：
#   conda create -y -p "$(pwd)/.conda_runner" python=3.10 pip
#   RUNNER_PIP_INSTALL=1 ./start_runner.sh
#
# 建议在 MMDet 所用 conda 环境里也安装 clearml（与 Runner 所在环境可分离），训练脚本才能把指标写入同一 Task：
#   "$MMDET_PY_EXE" -m pip install 'clearml>=1.14.0'
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND_ENV="$REPO_ROOT/backend/env.clearml.local"
LOCAL_ENV="$SCRIPT_DIR/env.clearml.local"

_load_clearml_env() {
  local f=""
  if [[ -n "${MMDET_CLEARML_ENV_FILE:-}" && -f "${MMDET_CLEARML_ENV_FILE}" ]]; then
    f="${MMDET_CLEARML_ENV_FILE}"
  elif [[ -f "$LOCAL_ENV" ]]; then
    f="$LOCAL_ENV"
  elif [[ -f "$BACKEND_ENV" ]]; then
    f="$BACKEND_ENV"
  fi
  if [[ -z "$f" ]]; then
    echo "[start_runner] ClearML: 未找到 env 文件（可选）。搜索: MMDET_CLEARML_ENV_FILE → $LOCAL_ENV → $BACKEND_ENV" >&2
    return 0
  fi
  echo "[start_runner] ClearML: loading $f"
  set -a
  # shellcheck disable=SC1090
  source "$f"
  set +a
}

_load_clearml_env

# 禁止混入 ~/.local/lib/python*/site-packages（否则 numpy/pandas/clearml 二进制不匹配，Task.init 会因 ImportError 静默跳过）
export PYTHONNOUSERSITE=1

# 解释器优先级：RUNNER_PYTHON > 本目录独立 conda（推荐）> venv > 系统 python
CONDA_RUNNER_PY="$SCRIPT_DIR/.conda_runner/bin/python"
VENV_PY="$SCRIPT_DIR/.venv/bin/python"

PY="${RUNNER_PYTHON:-}"
if [[ -z "$PY" ]]; then
  if [[ -x "$CONDA_RUNNER_PY" ]]; then
    PY="$CONDA_RUNNER_PY"
  elif [[ -x "$VENV_PY" ]]; then
    PY="$VENV_PY"
  elif [[ -x /usr/bin/python3.10 ]]; then
    PY=/usr/bin/python3.10
  elif command -v python3.10 >/dev/null 2>&1; then
    PY="$(command -v python3.10)"
  elif command -v python3 >/dev/null 2>&1; then
    PY="$(command -v python3)"
  else
    echo "[start_runner] 未找到 python3.10/python3" >&2
    exit 1
  fi
fi

echo "[start_runner] using Python: $PY (PYTHONNOUSERSITE=$PYTHONNOUSERSITE)"

if [[ "${RUNNER_PIP_INSTALL:-}" == "1" ]]; then
  echo "[start_runner] RUNNER_PIP_INSTALL=1 → pip install -r requirements-uvicorn.txt"
  "$PY" -m pip install -r "$SCRIPT_DIR/requirements-uvicorn.txt"
fi

exec "$PY" -m uvicorn mmdet_runner_server:app --host 127.0.0.1 --port 8009
