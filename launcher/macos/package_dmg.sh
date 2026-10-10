#!/bin/sh
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP="$ROOT/启动AI训练平台.app"
DIST="$ROOT/launcher/macos/dist"
DMG="$DIST/启动AI训练平台-macOS.dmg"
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/ai-training-launcher-dmg.XXXXXX")"
trap 'rm -rf "$STAGE"' EXIT HUP INT TERM

"$ROOT/launcher/macos/build_app.sh"
mkdir -p "$DIST"
ditto "$APP" "$STAGE/AITrainingPlatformLauncher.app"
cp "$ROOT/launcher/macos/DMG_README.txt" "$STAGE/首次使用说明.txt"
hdiutil create \
  -volname "AI训练平台启动器" \
  -srcfolder "$STAGE" \
  -format UDZO \
  -imagekey zlib-level=9 \
  -ov "$DMG"
hdiutil verify "$DMG"

echo "created $DMG"
