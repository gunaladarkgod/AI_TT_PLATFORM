#!/bin/sh
set -eu
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP="$ROOT/启动AI训练平台.app"
mkdir -p "$APP/Contents/MacOS"
rm -f "$APP/Contents/MacOS/AITrainingLauncher"
if [ -d /Applications/Xcode.app/Contents/Developer ]; then
  export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
fi
SWIFTC="$(xcrun --find swiftc)"
SDKROOT="$(xcrun --sdk macosx --show-sdk-path)"
BUILD_DIR="${TMPDIR:-/tmp}/ai-training-platform-launcher-build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
for ARCH in arm64 x86_64; do
  MODULE_CACHE="$BUILD_DIR/module-cache-$ARCH"
  mkdir -p "$MODULE_CACHE"
  "$SWIFTC" -O -target "$ARCH-apple-macosx13.0" -sdk "$SDKROOT" -module-cache-path "$MODULE_CACHE" \
    "$ROOT/launcher/macos/Sources/AITrainingPlatformLauncher.swift" \
    -o "$BUILD_DIR/AITrainingPlatformLauncher-$ARCH" \
    -framework AppKit -framework Network
done
LIPO="$(xcrun --find lipo)"
"$LIPO" -create \
  "$BUILD_DIR/AITrainingPlatformLauncher-arm64" \
  "$BUILD_DIR/AITrainingPlatformLauncher-x86_64" \
  -output "$APP/Contents/MacOS/AITrainingPlatformLauncher"
cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDisplayName</key><string>AI 训练平台启动器</string>
    <key>CFBundleExecutable</key><string>AITrainingPlatformLauncher</string>
    <key>CFBundleIdentifier</key><string>com.xgls.ai-training-platform.launcher</string>
    <key>CFBundleName</key><string>AI Training Platform Launcher</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>1.0.0</string>
    <key>CFBundleVersion</key><string>1</string>
    <key>LSMinimumSystemVersion</key><string>13.0</string>
    <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
PLIST
chmod +x "$APP/Contents/MacOS/AITrainingPlatformLauncher"
echo "built $APP"
