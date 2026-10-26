#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
APPLICATION_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
DESTINATION="$APPLICATION_DIR/ai-training-platform-launcher.desktop"
mkdir -p "$APPLICATION_DIR"
python3 - "$SCRIPT_DIR" "$WORKSPACE_ROOT" "$DESTINATION" <<'PY'
import sys
from pathlib import Path

script_dir, workspace_root, destination = map(Path, sys.argv[1:])
command = str(script_dir / "launch.sh")
# Desktop Entry Exec uses double quotes and treats percent signs specially.
command = '"' + command.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("`", "\\`").replace("%", "%%") + '"'
template = (script_dir / "ai-training-platform.desktop").read_text(encoding="utf-8")
entry = template.replace("__LAUNCH_COMMAND__", command).replace("__WORKSPACE_ROOT__", str(workspace_root))
destination.write_text(entry, encoding="utf-8")
PY
chmod +x "$SCRIPT_DIR/launch.sh" "$SCRIPT_DIR/ai-training-platform-launcher.py" "$DESTINATION"
echo "已安装桌面入口：$DESTINATION"
