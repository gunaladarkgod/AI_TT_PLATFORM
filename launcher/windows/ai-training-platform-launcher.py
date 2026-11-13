#!/usr/bin/env python3
"""Windows desktop launcher for AI_TT_PLATFORM."""

from __future__ import annotations

import http.client
import hashlib
import os
import queue
import shutil
import socket
import subprocess
import sys
import threading
import time
from collections import deque
from pathlib import Path
import tkinter as tk
from tkinter import messagebox, ttk


APP_NAME = "AI 训练平台启动器"
START_TIMEOUT = 180
RUNNER_WAIT_TIMEOUT = 45
BUILD_TIMEOUT = 240
POLL_INTERVAL = 0.5


def find_workspace() -> Path:
    configured = os.environ.get("APP_WORKSPACE_ROOT")
    if configured:
        candidate = Path(configured).expanduser().resolve()
        if (candidate / "backend").is_dir() and (candidate / "fronternd").is_dir():
            return candidate
    application = Path(sys.executable if getattr(sys, "frozen", False) else __file__).resolve()
    for candidate in (application.parent, *application.parents):
        if (candidate / "backend").is_dir() and (candidate / "fronternd").is_dir():
            return candidate
    return application.parent


ROOT = find_workspace()
FRONTEND_DIR = ROOT / "fronternd"
FRONTEND_INDEX = FRONTEND_DIR / "dist" / "index.html"
FRONTEND_DOCUMENTS = tuple(ROOT / "docs" / "v1.0" / name for name in (
    "使用说明文档v1.0.md",
    "技术说明文档.md",
    "GitHub协作流程.md",
    "算法集成规范.md",
))
LOG_DIR = ROOT / "logs" / "launcher"
LOG_DIR.mkdir(parents=True, exist_ok=True)


def append_log(message: str) -> None:
    line = f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {message}\n"
    try:
        with (LOG_DIR / "launcher.log").open("a", encoding="utf-8") as stream:
            stream.write(line)
    except OSError:
        pass


def log_failure(name: str, path: Path, reason: str) -> None:
    append_log(f"{name}: {reason}; detail log: {path}")
    try:
        with path.open("r", encoding="utf-8", errors="replace") as stream:
            for line in deque(stream, maxlen=20):
                append_log(f"{name}> {line.rstrip()}")
    except OSError:
        pass


def load_backend_environment(env: dict[str, str], env_file: Path | None = None) -> dict[str, str]:
    path = env_file or ROOT / "backend" / ".env.local"
    if not path.is_file():
        return env
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return env
    for raw_line in lines:
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if key in {"DB_URL", "DB_USERNAME", "DB_PASSWORD"} and key not in env:
            env[key] = value.strip().strip('"').strip("'")
    return env


def database_authentication_error(log_path: Path, start_offset: int = 0) -> bool:
    try:
        with log_path.open("rb") as stream:
            stream.seek(start_offset)
            stream.seek(0, os.SEEK_END)
            end = stream.tell()
            stream.seek(max(start_offset, end - 131072))
            tail = stream.read().decode("utf-8", errors="replace")
    except OSError:
        return False
    return "Access denied for user" in tail and "java.sql.SQLException" in tail


def backend_jar(root: Path = ROOT) -> Path | None:
    jars = [path for path in (root / "backend" / "target").glob("*.jar")
            if not path.name.endswith(("-sources.jar", "-javadoc.jar", "-plain.jar"))]
    return max(jars, key=lambda path: path.stat().st_mtime) if jars else None


def backend_source_signature(root: Path = ROOT) -> str:
    backend = root / "backend"
    sources = [backend / "pom.xml"]
    source_dir = backend / "src" / "main"
    if source_dir.is_dir():
        sources.extend(path for path in source_dir.rglob("*") if path.is_file())
    signature = hashlib.sha256()
    for path in sorted(sources):
        if not path.is_file():
            continue
        signature.update(path.relative_to(root).as_posix().encode("utf-8"))
        signature.update(b"\0")
        with path.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                signature.update(chunk)
        signature.update(b"\n")
    return signature.hexdigest()


def backend_needs_package(root: Path = ROOT) -> bool:
    if backend_jar(root) is None:
        return True
    signature_path = root / "backend" / "target" / "launcher-source.signature"
    try:
        return signature_path.read_text(encoding="ascii").strip() != backend_source_signature(root)
    except OSError:
        return True


def frontend_needs_build() -> bool:
    if not FRONTEND_INDEX.is_file():
        return True
    built_at = FRONTEND_INDEX.stat().st_mtime
    sources = [FRONTEND_DIR / name for name in
               ("index.html", "vite.config.js", "package.json", "package-lock.json")]
    for directory in (FRONTEND_DIR / "src", FRONTEND_DIR / "public"):
        if directory.is_dir():
            sources.extend(path for path in directory.rglob("*") if path.is_file())
    sources.extend(FRONTEND_DOCUMENTS)
    return any(path.is_file() and path.stat().st_mtime > built_at for path in sources)


def port_open(port: int) -> bool:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=0.7):
            return True
    except OSError:
        return False


def http_ready(port: int, path: str) -> bool:
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=2)
    try:
        connection.request("GET", path, headers={"Connection": "close"})
        return 200 <= connection.getresponse().status < 400
    except (OSError, http.client.HTTPException):
        return False
    finally:
        connection.close()


def find_command(*names: str) -> str | None:
    for name in names:
        found = shutil.which(name)
        if found:
            return found
    return None


def windows_command(command: str, *args: str) -> str:
    """Run .cmd/.bat files through cmd.exe without re-quoting its /c payload."""
    line = subprocess.list2cmdline([command, *args])
    comspec = subprocess.list2cmdline([os.environ.get("COMSPEC", "cmd.exe")])
    return f'{comspec} /d /s /c "{line}"'


class Service:
    def __init__(self, key: str, label: str, port: int, readiness):
        self.key = key
        self.label = label
        self.port = port
        self.readiness = readiness
        self.log_path = LOG_DIR / f"{key}.log"
        self.process: subprocess.Popen | None = None
        self.log_start_offset = 0
        self.external = False
        self.state = "stopped"


class Launcher(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(APP_NAME)
        self.geometry("560x350")
        self.minsize(500, 320)
        self.protocol("WM_DELETE_WINDOW", self.close)
        self.events: queue.Queue[tuple[str, object]] = queue.Queue()
        self.operation_lock = threading.Lock()
        self.cancel_start = threading.Event()
        self.probe_lock = threading.Lock()
        self.closing = False
        self.build_process: subprocess.Popen | None = None
        self.backend: Service | None = None
        self._build_ui()
        self.services = [
            Service("mysql", "MySQL", 3306, lambda: port_open(3306)),
            Service("backend", "后端", 8081, lambda: port_open(8081)),
            Service("runner", "Runner", 8009, lambda: http_ready(8009, "/health")),
        ]
        self.status_labels: dict[str, ttk.Label] = {}
        self.detail_labels: dict[str, ttk.Label] = {}
        self._build_services()
        self.after(150, self._drain_events)
        self.after(500, self._refresh_statuses)
        append_log(f"launcher opened; root={ROOT}")

    def _build_ui(self) -> None:
        style = ttk.Style(self)
        try:
            style.theme_use("vista")
        except tk.TclError:
            pass
        outer = ttk.Frame(self, padding=18)
        outer.pack(fill="both", expand=True)
        ttk.Label(outer, text=APP_NAME, font=("Segoe UI", 17, "bold")).pack(anchor="w")
        ttk.Label(outer, text=f"项目目录：{ROOT}", foreground="#666666").pack(anchor="w", pady=(4, 18))
        self.services_frame = ttk.LabelFrame(outer, text="服务状态", padding=12)
        self.services_frame.pack(fill="x")
        buttons = ttk.Frame(outer)
        buttons.pack(fill="x", pady=(22, 10))
        self.start_button = ttk.Button(buttons, text="启动全部", command=self.start_all)
        self.start_button.pack(side="left")
        self.stop_button = ttk.Button(buttons, text="停止全部", command=self.stop_all)
        self.stop_button.pack(side="left", padx=(10, 0))
        ttk.Button(buttons, text="打开启动日志", command=self.open_log).pack(side="left", padx=(10, 0))
        ttk.Button(buttons, text="数据库配置", command=self.open_database_config).pack(side="left", padx=(10, 0))
        ttk.Button(buttons, text="打开平台", command=self.open_platform).pack(side="right")
        self.message = ttk.Label(outer, text="就绪", foreground="#555555")
        self.message.pack(anchor="w", pady=(4, 0))

    def _build_services(self) -> None:
        for row, service in enumerate(self.services):
            ttk.Label(self.services_frame, text=service.label, width=12).grid(row=row, column=0, sticky="w", pady=5)
            status = ttk.Label(self.services_frame, text="未运行" if service.key == "mysql" else "已停止", width=12)
            status.grid(row=row, column=1, sticky="w", pady=5)
            detail = ttk.Label(self.services_frame, text="", foreground="#666666")
            detail.grid(row=row, column=2, sticky="w", padx=(12, 0), pady=5)
            self.status_labels[service.key] = status
            self.detail_labels[service.key] = detail

    def _emit_state(self, service: Service, state: str, detail: str = "") -> None:
        service.state = state
        self.events.put(("state", (service.key, state, detail)))

    def _drain_events(self) -> None:
        try:
            while True:
                event, value = self.events.get_nowait()
                if event == "message":
                    self.message.configure(text=str(value))
                elif event == "buttons":
                    enabled = bool(value)
                    self.start_button.configure(state="normal" if enabled else "disabled")
                    self.stop_button.configure(state="normal" if enabled else "disabled")
                elif event == "state":
                    key, state, detail = value
                    label = "未运行" if key == "mysql" and state == "stopped" else {
                        "ready": "已运行", "starting": "启动中", "stopping": "停止中",
                        "failed": "失败", "stopped": "已停止"}.get(state, state)
                    self.status_labels[key].configure(text=label)
                    self.detail_labels[key].configure(text=detail)
                elif event == "close":
                    self.closing = True
                    self.destroy()
                    return
        except queue.Empty:
            pass
        if not self.closing:
            self.after(150, self._drain_events)

    def _refresh_statuses(self) -> None:
        if not self.closing:
            if self.probe_lock.acquire(blocking=False):
                threading.Thread(target=self._probe_worker, daemon=True).start()
            self.after(2000, self._refresh_statuses)

    def _probe_worker(self) -> None:
        try:
            for service in self.services:
                if service.key == "mysql":
                    ready = service.readiness()
                    state = "ready" if ready else "stopped"
                    if state != service.state:
                        self._emit_state(service, state, "3306" if ready else "")
                elif self.operation_lock.locked():
                    continue
                elif service.key == "backend":
                    if service.process and service.process.poll() is not None and service.state in {"starting", "ready"}:
                        self._emit_state(service, "failed", f"进程已退出（{service.process.returncode}）")
                else:
                    ready = service.readiness()
                    if ready and service.state != "ready":
                        service.external = self.backend is None or self.backend.process is None
                        self._emit_state(service, "ready", "由后端管理" if not service.external else "已有服务响应")
                    elif not ready and service.state == "ready":
                        self._emit_state(service, "stopped", "")
        finally:
            self.probe_lock.release()

    def start_all(self) -> None:
        if not self.operation_lock.acquire(blocking=False):
            return
        self.cancel_start.clear()
        self.start_button.configure(state="disabled")
        self.stop_button.configure(state="normal")
        threading.Thread(target=self._start_worker, daemon=True).start()

    def _start_worker(self) -> None:
        try:
            if not self._prepare_frontend():
                return
            mysql = self.services[0]
            if not mysql.readiness():
                self._emit_state(mysql, "failed", "未检测到 3306 端口，请先启动 MySQL")
                self.events.put(("message", "MySQL 未运行，已跳过服务启动"))
                return
            self._emit_state(mysql, "ready", "3306")
            backend = self.services[1]
            runner = self.services[2]
            if backend.readiness():
                backend.external = backend.process is None
                self._emit_state(backend, "ready", "已有后端响应")
                if self._show_database_error(backend):
                    return
            else:
                if port_open(8081):
                    self._emit_state(backend, "failed", "端口 8081 被占用，但后端无响应")
                    log_failure(backend.key, backend.log_path, "端口 8081 被占用")
                    return
                command, missing = self._prepare_backend()
                if missing:
                    self._emit_state(backend, "failed", missing)
                    self.events.put(("message", missing))
                    log_failure(backend.key, backend.log_path, missing)
                    return
                self._emit_state(backend, "starting", "正在启动")
                env = os.environ.copy()
                load_backend_environment(env)
                env.update({"APP_WORKSPACE_ROOT": str(ROOT), "APP_REDIS_REQUIRED": "false",
                            "APP_REDIS_FALLBACK_MEMORY": "true", "RUNNER_AUTO_START": "true",
                            "RUNNER_AUTO_START_LOG": str(LOG_DIR / "runner.log"),
                            "STATIC_ROOT": str(FRONTEND_DIR), "PYTHONUTF8": "1"})
                self.backend = backend
                backend.log_start_offset = backend.log_path.stat().st_size if backend.log_path.exists() else 0
                with backend.log_path.open("a", encoding="utf-8") as stream:
                    backend.process = subprocess.Popen(command, cwd=ROOT / "backend", env=env,
                        stdin=subprocess.DEVNULL, stdout=stream, stderr=subprocess.STDOUT,
                        creationflags=subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.CREATE_NO_WINDOW)
                append_log(f"started backend: {command}")
                deadline = time.monotonic() + START_TIMEOUT
                while time.monotonic() < deadline and not self.cancel_start.is_set():
                    if backend.readiness():
                        self._emit_state(backend, "ready", "127.0.0.1:8081")
                        if self._show_database_error(backend):
                            return
                        break
                    if backend.process.poll() is not None:
                        reason = f"进程已退出（{backend.process.returncode}），请查看日志"
                        self._emit_state(backend, "failed", reason)
                        log_failure(backend.key, backend.log_path, reason)
                        return
                    time.sleep(POLL_INTERVAL)
                else:
                    if not self.cancel_start.is_set():
                        self._emit_state(backend, "failed", "启动超时，请查看日志")
                        log_failure(backend.key, backend.log_path, "启动超时")
                    return
            if self.cancel_start.is_set():
                return
            self._emit_state(runner, "starting", "等待后端自动启动 Runner")
            deadline = time.monotonic() + RUNNER_WAIT_TIMEOUT
            while time.monotonic() < deadline and not self.cancel_start.is_set():
                if backend.process is not None and database_authentication_error(
                        backend.log_path, backend.log_start_offset):
                    self._show_database_error(backend)
                    return
                if runner.readiness():
                    self._emit_state(runner, "ready", "127.0.0.1:8009")
                    break
                time.sleep(POLL_INTERVAL)
            else:
                if not self.cancel_start.is_set():
                    self._emit_state(runner, "failed", "Runner 暂不可用；训练功能不可用")
                    log_failure(runner.key, runner.log_path, "后端启动后 Runner 未就绪")
                    self.events.put(("message", "后端已启动；Runner 暂不可用，可先打开平台。训练功能就绪前请检查 Runner 日志。"))
                    return
            self.events.put(("message", "服务状态检查完成"))
        except Exception as exc:
            append_log(f"start worker failed: {exc!r}")
            self.events.put(("message", f"启动失败：{exc}，请查看日志"))
        finally:
            self.operation_lock.release()
            self.events.put(("stop_after_cancel" if self.cancel_start.is_set() else "buttons", True))

    def _show_database_error(self, backend: Service) -> bool:
        if backend.process is None or not database_authentication_error(
                backend.log_path, backend.log_start_offset):
            return False
        detail = "MySQL 账号或密码不匹配"
        self._emit_state(backend, "failed", detail)
        self._emit_state(self.services[2], "failed", "数据库未就绪，Runner 自动启动尚未开始")
        self.events.put(("message", "MySQL 拒绝了数据库登录。请打开“数据库配置”填写正确账号和密码，再停止并重新启动后端。"))
        append_log("backend startup blocked: MySQL rejected configured credentials")
        return True

    def _prepare_backend(self) -> tuple[list[str] | None, str]:
        java = find_command("java.exe", "java")
        if not java:
            return None, "未找到 Java，请安装 JDK 17 并配置 JAVA_HOME/PATH"

        jar = backend_jar()
        if backend_needs_package():
            maven = find_command("mvn.cmd", "mvn.bat", "mvn")
            if not maven:
                if jar:
                    return None, "后端源码已更新，但未找到 Maven；请安装 Maven 后重新启动"
                return None, "未找到 Maven，且 backend/target 中没有可运行 JAR"
            if not self._package_backend(maven):
                return None, "后端打包失败，请查看 logs/launcher/backend-build.log"
            jar = backend_jar()
            if jar is None:
                return None, "Maven 已完成但没有生成可运行 JAR"
        if jar:
            return [java, "-jar", str(jar)], ""
        return None, "backend/target 中没有可运行 JAR"

    def _package_backend(self, maven: str) -> bool:
        log_path = LOG_DIR / "backend-build.log"
        source_signature = backend_source_signature()
        self.events.put(("message", "首次启动或后端源码有更新，正在打包后端…"))
        append_log("backend package started")
        args = ["-f", str(ROOT / "backend" / "pom.xml"), "-Dmaven.test.skip=true", "package"]
        command = windows_command(maven, *args) if maven.lower().endswith((".cmd", ".bat")) else [maven, *args]
        try:
            with log_path.open("a", encoding="utf-8") as stream:
                self.build_process = subprocess.Popen(command, cwd=ROOT / "backend",
                    stdin=subprocess.DEVNULL, stdout=stream, stderr=subprocess.STDOUT,
                    creationflags=subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.CREATE_NO_WINDOW)
            deadline = time.monotonic() + BUILD_TIMEOUT
            while self.build_process.poll() is None and time.monotonic() < deadline:
                if self.cancel_start.is_set():
                    self._terminate_process_tree(self.build_process)
                    return False
                time.sleep(0.2)
            if self.build_process.poll() is None:
                self._terminate_process_tree(self.build_process)
                log_failure("backend package", log_path, "打包超时")
                self.events.put(("message", "后端打包超时，请查看日志"))
                return False
            if self.build_process.returncode != 0:
                log_failure("backend package", log_path, f"打包失败（退出码 {self.build_process.returncode}）")
                self.events.put(("message", "后端打包失败，请查看日志"))
                return False
            signature_path = ROOT / "backend" / "target" / "launcher-source.signature"
            try:
                signature_path.write_text(source_signature, encoding="ascii")
            except OSError as exc:
                append_log(f"backend build signature could not be saved: {exc}")
            append_log("backend package completed")
            return True
        except OSError as exc:
            log_failure("backend package", log_path, str(exc))
            self.events.put(("message", f"后端打包失败：{exc}"))
            return False
        finally:
            self.build_process = None

    def _prepare_frontend(self) -> bool:
        if not frontend_needs_build():
            return True
        node = find_command("node.exe", "node")
        npm = find_command("npm.cmd", "npm.bat", "npm")
        vite = FRONTEND_DIR / "node_modules" / "vite" / "bin" / "vite.js"
        log_path = LOG_DIR / "frontend-build.log"
        if not node or not npm:
            reason = "前端尚未构建；请先安装 Node.js 和 npm"
            self.events.put(("message", reason))
            append_log(reason)
            return False
        if not vite.is_file():
            self.events.put(("message", "正在安装前端依赖…"))
            try:
                log_path.parent.mkdir(parents=True, exist_ok=True)
                with log_path.open("a", encoding="utf-8") as stream:
                    install_args = ["install", "--no-audit", "--no-fund", "--prefer-offline"]
                    command = windows_command(npm, *install_args) if npm.lower().endswith((".cmd", ".bat")) else [npm, *install_args]
                    self.build_process = subprocess.Popen(command, cwd=FRONTEND_DIR,
                        stdin=subprocess.DEVNULL, stdout=stream, stderr=subprocess.STDOUT,
                        creationflags=subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.CREATE_NO_WINDOW)
                deadline = time.monotonic() + BUILD_TIMEOUT
                while self.build_process.poll() is None and time.monotonic() < deadline:
                    if self.cancel_start.is_set():
                        self._terminate_process_tree(self.build_process)
                        return False
                    time.sleep(0.2)
                if self.build_process.poll() is None:
                    self._terminate_process_tree(self.build_process)
                    log_failure("frontend install", log_path, "依赖安装超时")
                    self.events.put(("message", "前端依赖安装超时，请查看日志"))
                    return False
                if self.build_process.returncode != 0 or not vite.is_file():
                    reason = f"前端依赖安装失败（退出码 {self.build_process.returncode}）"
                    log_failure("frontend install", log_path, reason)
                    self.events.put(("message", reason))
                    return False
            except OSError as exc:
                log_failure("frontend install", log_path, str(exc))
                self.events.put(("message", f"前端依赖安装失败：{exc}"))
                return False
            finally:
                self.build_process = None
        self.events.put(("message", "正在构建前端网页…"))
        try:
            with log_path.open("a", encoding="utf-8") as stream:
                self.build_process = subprocess.Popen([node, str(vite), "build"], cwd=FRONTEND_DIR,
                    stdin=subprocess.DEVNULL, stdout=stream, stderr=subprocess.STDOUT,
                    creationflags=subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.CREATE_NO_WINDOW)
            deadline = time.monotonic() + BUILD_TIMEOUT
            while self.build_process.poll() is None:
                if self.cancel_start.is_set() or time.monotonic() >= deadline:
                    self._terminate_process_tree(self.build_process)
                    if self.cancel_start.is_set():
                        return False
                    log_failure("frontend build", log_path, "构建超时")
                    self.events.put(("message", "前端构建超时，请查看日志"))
                    return False
                time.sleep(0.2)
            if self.build_process.returncode != 0 or not FRONTEND_INDEX.is_file():
                reason = f"前端构建失败（退出码 {self.build_process.returncode}）"
                log_failure("frontend build", log_path, reason)
                self.events.put(("message", reason))
                return False
            append_log("frontend build completed")
            return True
        except OSError as exc:
            log_failure("frontend build", log_path, str(exc))
            self.events.put(("message", f"前端构建失败：{exc}"))
            return False
        finally:
            self.build_process = None

    def _terminate_process_tree(self, process: subprocess.Popen | None) -> None:
        if process is None or process.poll() is not None:
            return
        subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            pass

    def stop_all(self) -> None:
        if not self.operation_lock.acquire(blocking=False):
            self.cancel_start.set()
            self.stop_button.configure(state="disabled")
            self.message.configure(text="正在取消启动…")
            return
        self.start_button.configure(state="disabled")
        self.stop_button.configure(state="disabled")
        threading.Thread(target=self._stop_worker, daemon=True).start()

    def _stop_worker(self) -> None:
        try:
            self.events.put(("message", "正在停止本启动器创建的后端进程…"))
            if self.build_process:
                self._terminate_process_tree(self.build_process)
            for service in (self.services[1], self.services[2]):
                process = service.process
                if service.external:
                    ready = service.readiness()
                    self._emit_state(service, "ready" if ready else "stopped",
                                     "已有服务，未停止" if ready else "")
                    continue
                if process is None:
                    self._emit_state(service, "stopped", "")
                    continue
                self._emit_state(service, "stopping", "")
                self._terminate_process_tree(process)
                service.process = None
                service.external = False
                self._emit_state(service, "stopped" if not service.readiness() else "failed",
                    "" if not service.readiness() else "端口仍有响应，请检查日志")
            self.events.put(("message", "已停止本启动器创建的服务"))
        finally:
            self.operation_lock.release()
            self.events.put(("buttons", True))

    def open_log(self) -> None:
        path = LOG_DIR / "launcher.log"
        path.touch(exist_ok=True)
        os.startfile(path)

    def open_database_config(self) -> None:
        path = ROOT / "backend" / ".env.local"
        if not path.exists():
            path.write_text(
                "# Local backend settings. This file is ignored by Git.\n"
                "# Set the credentials used by your local MySQL server.\n"
                "DB_USERNAME=root\n"
                "DB_PASSWORD=\n",
                encoding="utf-8",
            )
        subprocess.Popen(["notepad.exe", str(path)])

    def open_platform(self) -> None:
        if not FRONTEND_INDEX.is_file():
            messagebox.showerror(APP_NAME, "网页尚未构建，请先点击“启动全部”。")
            return
        if not http_ready(8081, "/dist/index.html"):
            messagebox.showerror(APP_NAME, "后端尚未提供网页，请检查后端状态和日志。")
            return
        os.startfile("http://127.0.0.1:8081/")

    def close(self) -> None:
        if self.operation_lock.locked():
            messagebox.showinfo(APP_NAME, "请等待当前启动或停止操作完成。")
            return
        if any(service.process is not None and not service.external for service in self.services[1:]):
            self.start_button.configure(state="disabled")
            self.stop_button.configure(state="disabled")
            if self.operation_lock.acquire(blocking=False):
                threading.Thread(target=self._stop_then_close, daemon=True).start()
            return
        self.closing = True
        self.destroy()

    def _stop_then_close(self) -> None:
        try:
            self._stop_worker_body()
        finally:
            self.operation_lock.release()
            self.events.put(("close", None))

    def _stop_worker_body(self) -> None:
        for service in (self.services[1], self.services[2]):
            process = service.process
            if service.external:
                ready = service.readiness()
                self._emit_state(service, "ready" if ready else "stopped",
                                 "已有服务，未停止" if ready else "")
            elif process is not None:
                self._terminate_process_tree(process)
                service.process = None
                self._emit_state(service, "stopped" if not service.readiness() else "failed", "")


def main() -> int:
    if os.name != "nt":
        print("This launcher is for Windows.", file=sys.stderr)
        return 1
    if not (ROOT / "backend").is_dir() or not (ROOT / "fronternd").is_dir():
        messagebox.showerror(APP_NAME, f"无法识别项目根目录：{ROOT}")
        return 1
    Launcher().mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
