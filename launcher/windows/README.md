# Windows 启动器

双击项目根目录的 `启动AI训练平台.bat` 打开图形启动器。Windows 需要安装包含 Tkinter 和 `pythonw.exe` 的 Python 3。窗口提供 MySQL、后端和 Runner 状态，以及启动、停止、打开日志和打开平台操作。

首次使用或前端源码更新时，启动器会使用已安装的 Node.js 构建 `fronternd/dist/`。后端直接提供构建后的网页，因此不需要保留 Vite 开发服务器。Spring Boot 会按项目配置自动启动 Runner；MySQL 需要预先作为 Windows 服务启动。

运行环境需要 Python 3（含 Tkinter）、JDK 17、Maven（如果 `backend/target/` 中已有可运行 JAR 则不需要）、Node.js/npm 和 MySQL。首次准备环境和数据库仍需按项目根目录 README 完成。Runner 的 Python 由 `runner/start_runner.cmd` 按 `RUNNER_PYTHON`、项目虚拟环境、旧环境和系统 Python 的顺序选择。

日志保存在 `logs/launcher/`。停止按钮只会结束本启动器创建的后端进程树；外部已经运行的后端不会被停止，MySQL 始终由系统服务管理。
