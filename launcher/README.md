# 图形启动器

所有系统的启动入口、源码和打包脚本均放在本目录，项目根目录不再放置启动文件。

| 系统 | 日常启动入口 | 首次准备 |
| --- | --- | --- |
| macOS | `macos/启动AI训练平台.app` 或 DMG 中的 App | 已有 App 可直接打开；开发者可执行 `macos/build_app.sh` 构建 |
| Linux | 应用菜单中的“AI 训练平台启动器”，或 `linux/启动AI训练平台.sh` | 在文件管理器中执行 `linux/install_desktop.sh` 安装应用菜单入口 |
| Windows | 桌面快捷方式，或 `windows/dist/启动AI训练平台.exe` | 双击 `windows/build_exe.bat` 生成 EXE 和桌面快捷方式 |

macOS 的备用入口为 `macos/启动AI训练平台.command`，Windows 的备用入口为 `windows/启动AI训练平台.bat`。Linux 的两个入口均调用 `linux/launch.sh`，使用新版 Linux 启动器。

- [Linux 环境与使用说明](linux/README.md)
- [Windows 打包与使用说明](windows/README.md)
- macOS 分发包：`macos/dist/启动AI训练平台-macOS.dmg`；打包脚本：`macos/package_dmg.sh`。

`legacy/启动AI训练平台.py` 保留旧版通用 Tkinter 实现，供兼容和 macOS 备用入口回退使用。移动后仍从仓库根目录读取前后端和本机配置，日志继续写入根目录的 `logs/launcher/`。
