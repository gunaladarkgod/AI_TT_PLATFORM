#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# 使用与 ~/.local/bin/uvicorn 相同的解释器，避免缺依赖；也可改为 conda 环境中的 python
exec python3.10 -m uvicorn mmdet_runner_server:app --host 127.0.0.1 --port 8009
