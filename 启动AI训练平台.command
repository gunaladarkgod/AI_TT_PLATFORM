#!/usr/bin/env bash
set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/启动AI训练平台.app"
if [ -d "$APP" ]; then
  open "$APP"
  exit 0
fi
LOG_FILE="${TMPDIR:-/tmp}/ai-training-platform-launcher.log"
PYTHON="$(command -v python3 2>/dev/null || true)"
if [ -z "$PYTHON" ] || ! "$PYTHON" -c 'import tkinter' >/dev/null 2>&1; then
  /usr/bin/osascript -e 'display dialog "未找到带 Tk 图形库的 Python 3。请安装 Python 3 后重试。" with title "AI 训练平台启动器" buttons {"确定"} default button "确定"' >/dev/null 2>&1 || true
  exit 1
fi
nohup "$PYTHON" "$ROOT/启动AI训练平台.py" >"$LOG_FILE" 2>&1 &
disown || true
exit 0
