#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
export APP_WORKSPACE_ROOT="${APP_WORKSPACE_ROOT:-$WORKSPACE_ROOT}"
mkdir -p "$WORKSPACE_ROOT/logs/launcher"
if ! python3 -c 'import sys, tkinter; sys.exit(0 if sys.version_info >= (3, 8) else 1)' >/dev/null 2>&1; then
  message="启动器需要 Python 3.8+ 和 Tkinter。请查看 launcher/linux/README.md。"
  echo "$message" >> "$WORKSPACE_ROOT/logs/launcher/launcher.log"
  if command -v zenity >/dev/null 2>&1; then
    zenity --error --text="$message"
  elif command -v notify-send >/dev/null 2>&1; then
    notify-send "AI 训练平台启动器" "$message"
  fi
  exit 1
fi
exec python3 "$SCRIPT_DIR/ai-training-platform-launcher.py" "$@" >> "$WORKSPACE_ROOT/logs/launcher/launcher.log" 2>&1
