# Windows 启动器

首次生成程序时，在 Windows 上双击 `build_exe.bat`。脚本会创建独立的打包环境、安装 PyInstaller、生成 `dist/启动AI训练平台.exe`，并在桌面创建快捷方式。之后直接双击桌面快捷方式或该 EXE，即可打开图形界面；不需要安装 Python，也不需要打开终端。根目录的 `启动AI训练平台.bat` 保留为兼容入口。

EXE 必须放在项目仓库内的 `launcher/windows/dist/`，快捷方式会自动把项目根目录设为工作目录。启动器仍需要读取当前仓库中的前后端源码和配置，因此不能把 EXE 单独移出仓库使用。

窗口提供 MySQL、后端和 Runner 状态，以及启动、停止、打开日志和打开平台操作。需要重新创建快捷方式时，双击 `create_desktop_shortcut.bat`。

首次使用或前端源码更新时，启动器会使用已安装的 Node.js 构建 `fronternd/dist/`。后端直接提供构建后的网页，因此不需要保留 Vite 开发服务器。Spring Boot 会按项目配置自动启动 Runner；MySQL 需要预先作为 Windows 服务启动。

构建 EXE 需要 Windows、Python 3（含 Tkinter）和可联网安装 PyInstaller。运行平台还需要 JDK 17、Maven（如果 `backend/target/` 中已有可运行 JAR 则不需要）、Node.js/npm 和 MySQL。首次准备环境和数据库仍需按项目根目录 README 完成。Runner 的 Python 由 `runner/start_runner.cmd` 按 `RUNNER_PYTHON`、项目虚拟环境、旧环境和系统 Python 的顺序选择。

日志保存在 `logs/launcher/`。停止按钮只会结束本启动器创建的后端进程树；外部已经运行的后端不会被停止，MySQL 始终由系统服务管理。
