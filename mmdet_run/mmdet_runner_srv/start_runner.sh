#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# IDE 启动时 PATH 可能极短，优先显式路径，可用环境变量覆盖
PY="${RUNNER_PYTHON:-}"
if [[ -z "$PY" ]]; then
  if [[ -x /usr/bin/python3.10 ]]; then
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
exec "$PY" -m uvicorn mmdet_runner_server:app --host 127.0.0.1 --port 8009
