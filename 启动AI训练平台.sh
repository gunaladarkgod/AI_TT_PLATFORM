#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
nohup python3 "$ROOT/启动AI训练平台.py" >/dev/null 2>&1 &
disown || true
