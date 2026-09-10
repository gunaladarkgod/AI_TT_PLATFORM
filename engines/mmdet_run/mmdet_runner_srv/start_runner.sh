#!/usr/bin/env bash
# =============================================================================
# MMDet Python Runner（FastAPI + uvicorn）
#
# 训练任务仍由 Java → POST /api/runner/train 调度；本脚本负责启动 Runner 进程。
# 可选环境变量：
#   RUNNER_PYTHON      显式指定解释器（一般无需设置；默认优先 .conda_runner）
#   RUNNER_PIP_INSTALL 设为 1 时用当前 PY 执行 pip install -r requirements.txt
#
# 首次部署 Runner：建议在脚本目录创建独立 conda：
#   conda create -y -p "$(pwd)/.conda_runner" python=3.10 pip
#   RUNNER_PIP_INSTALL=1 ./start_runner.sh
#
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
WORKSPACE_ROOT="${APP_WORKSPACE_ROOT:-$(cd "$SCRIPT_DIR/../../.." && pwd)}"
export APP_WORKSPACE_ROOT="$WORKSPACE_ROOT"

# 禁止 Runner 混入用户级 site-packages，保证依赖来自独立环境。
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

# 默认路径始终指向当前项目内的 engines 目录；显式环境变量仍可覆盖。
export MMDET_REPO_ROOT="${MMDET_REPO_ROOT:-$WORKSPACE_ROOT/engines/mmdet_run/mmdetection-3.0.0}"
export MMDET_UPLOAD_ROOT="${MMDET_UPLOAD_ROOT:-$WORKSPACE_ROOT/engines/mmdet_run/myfiles}"
export MMDET_WORK_ROOT="${MMDET_WORK_ROOT:-$WORKSPACE_ROOT/artifacts/mmdet_runs}"

if ! "$PY" -c "import fastapi, uvicorn; from mmengine.config import Config" >/dev/null 2>&1; then
  if [[ "${RUNNER_AUTO_INSTALL:-1}" != "1" ]]; then
    echo "[start_runner] Runner 依赖缺失，且 RUNNER_AUTO_INSTALL 已关闭" >&2
    exit 1
  fi
  echo "[start_runner] 检测到 Runner 依赖缺失，开始自动安装 requirements.txt"
  "$PY" -m pip install -r "$SCRIPT_DIR/requirements.txt"
elif [[ "${RUNNER_PIP_INSTALL:-}" == "1" ]]; then
  echo "[start_runner] RUNNER_PIP_INSTALL=1 → pip install -r requirements.txt"
  "$PY" -m pip install -r "$SCRIPT_DIR/requirements.txt"
fi

exec "$PY" -m uvicorn mmdet_runner_server:app --host 127.0.0.1 --port 8009
