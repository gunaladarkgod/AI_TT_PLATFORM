#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
nohup bash "$SCRIPT_DIR/launch.sh" >/dev/null 2>&1 &
disown || true
