#!/usr/bin/env python3
"""Linux launcher for the AI training platform."""

from __future__ import annotations

import glob
import http.client
import os
import queue
import shutil
import signal
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
START_TIMEOUT = 120
BUILD_TIMEOUT = 180
POLL_INTERVAL = 0.5


def find_workspace() -> Path:
    configured = os.environ.get("APP_WORKSPACE_ROOT")
    if configured:
        candidate = Path(configured).expanduser().resolve()
        if (candidate / "backend").is_dir() and (candidate / "fronternd").is_dir():
            return candidate

    here = Path(__file__).resolve()
    for candidate in (here.parent, *here.parents):
        if (candidate / "backend").is_dir() and (candidate / "fronternd").is_dir():
            return candidate
    return here.parent.parent.parent


def prepare_process_path() -> None:
    """Include common user-level tool locations absent from desktop sessions."""
    home = Path.home()
    candidates = [home / ".local" / "bin", home / ".sdkman" / "candidates" / "java" / "current" / "bin",
                  home / ".sdkman" / "candidates" / "maven" / "current" / "bin"]
    node_versions = home / ".nvm" / "versions" / "node"
    if node_versions.is_dir():
        candidates.extend(sorted(node_versions.glob("*/bin"), reverse=True))
    existing = os.environ.get("PATH", "").split(os.pathsep)
    additions = [str(path) for path in candidates if path.is_dir() and str(path) not in existing]
    if additions:
        os.environ["PATH"] = os.pathsep.join([*additions, *existing])


ROOT = find_workspace()
FRONTEND_DIR = ROOT / "fronternd"
FRONTEND_INDEX = FRONTEND_DIR / "dist" / "index.html"
LOG_DIR = ROOT / "logs" / "launcher"
LOG_DIR.mkdir(parents=True, exist_ok=True)


def append_log(message: str) -> None:
    timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
    line = f"[{timestamp}] {message}\n"
    try:
        with (LOG_DIR / "launcher.log").open("a", encoding="utf-8") as stream:
            stream.write(line)
    except OSError:
        pass


def log_failure(name: str, log_path: Path, reason: str) -> None:
    append_log(f"{name}: {reason}; detail log: {log_path}")
    try:
        with log_path.open("r", encoding="utf-8", errors="replace") as stream:
            for line in deque(stream, maxlen=20):
                append_log(f"{name}> {line.rstrip()}")
    except OSError:
        pass


def frontend_needs_build() -> bool:
    if not FRONTEND_INDEX.is_file():
        return True
    built_at = FRONTEND_INDEX.stat().st_mtime
    sources = [FRONTEND_DIR / name for name in
               ("index.html", "vite.config.js", "package.json", "package-lock.json")]
    for directory in (FRONTEND_DIR / "src", FRONTEND_DIR / "public", ROOT / "docs" / "v1.0"):
        if directory.is_dir():
            sources.extend(path for path in directory.rglob("*") if path.is_file())
    return any(path.is_file() and path.stat().st_mtime > built_at for path in sources)


def port_open(port: int) -> bool:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=0.6):
            return True
    except OSError:
        return False


def http_ready(port: int, path: str) -> bool:
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=2)
    try:
        connection.request("GET", path, headers={"Connection": "close"})
        response = connection.getresponse()
        return 200 <= response.status < 400
    except (OSError, http.client.HTTPException):
        return False
    finally:
        connection.close()


class Service:
    def __init__(self, key: str, label: str, command: list[str] | None, cwd: Path,
                 log_name: str, readiness, port: int | None = None, prerequisite: str = ""):
        self.key = key
        self.label = label
        self.command = command
        self.cwd = cwd
        self.log_path = LOG_DIR / log_name
        self.readiness = readiness
        self.port = port
        self.prerequisite = prerequisite
        self.process: subprocess.Popen | None = None
        self.external = False
        self.state = "stopped"


class Launcher(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(APP_NAME)
        self.geometry("560x390")
        self.minsize(500, 340)
        self.protocol("WM_DELETE_WINDOW", self.close)
        self.operation_lock = threading.Lock()
        self.cancel_start = threading.Event()
        self.probe_lock = threading.Lock()
        self.events: queue.Queue[tuple[str, object]] = queue.Queue()
        self.closing = False
        self.build_process: subprocess.Popen | None = None
        self.services = self._create_services()
        self.status_labels: dict[str, ttk.Label] = {}
        self.detail_labels: dict[str, ttk.Label] = {}
        self._build_ui()
        self.after(150, self._drain_events)
        self.after(250, self._refresh_statuses)
        append_log(f"launcher opened; root={ROOT}")

    def _create_services(self) -> list[Service]:
        backend_dir = ROOT / "backend"
        runner_dir = ROOT / "engines" / "mmdet_run" / "mmdet_runner_srv"
        backend_cmd = self._backend_command()
        return [
            Service("mysql", "MySQL", None, ROOT, "mysql.log", lambda: port_open(3306), 3306),
            Service("backend", "后端", backend_cmd, backend_dir, "backend.log",
                    lambda: port_open(8081), 8081, "java"),
            Service("runner", "Runner", ["bash", str(runner_dir / "start_runner.sh")],
                    runner_dir, "runner.log", lambda: http_ready(8009, "/health"), 8009, "bash"),
        ]

    def _backend_command(self) -> list[str]:
        jars = [Path(path) for path in glob.glob(str(ROOT / "backend" / "target" / "*.jar"))
                if not Path(path).name.endswith(("-sources.jar", "-javadoc.jar", "-plain.jar"))]
        if jars:
            jar = max(jars, key=lambda path: path.stat().st_mtime)
            return ["java", "-jar", str(jar)]
        if shutil.which("mvn"):
            return ["mvn", "-f", str(ROOT / "backend" / "pom.xml"), "spring-boot:run"]
        return []

    def _build_ui(self) -> None:
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass
        outer = ttk.Frame(self, padding=22)
        outer.pack(fill="both", expand=True)
        ttk.Label(outer, text=APP_NAME, font=("TkDefaultFont", 18, "bold")).pack(anchor="w")
        ttk.Label(outer, text=f"项目目录：{ROOT}", foreground="#666666").pack(anchor="w", pady=(4, 18))

        services_frame = ttk.LabelFrame(outer, text="服务状态", padding=12)
        services_frame.pack(fill="x")
        for row, service in enumerate(self.services):
            ttk.Label(services_frame, text=service.label, width=12).grid(row=row, column=0, sticky="w", pady=5)
            status = ttk.Label(services_frame, text="未运行" if service.key == "mysql" else "已停止", width=12)
            status.grid(row=row, column=1, sticky="w", pady=5)
            detail = ttk.Label(services_frame, text="", foreground="#666666")
            detail.grid(row=row, column=2, sticky="w", padx=(12, 0), pady=5)
            self.status_labels[service.key] = status
            self.detail_labels[service.key] = detail

        buttons = ttk.Frame(outer)
        buttons.pack(fill="x", pady=(22, 10))
        self.start_button = ttk.Button(buttons, text="启动全部", command=self.start_all)
        self.start_button.pack(side="left")
        self.stop_button = ttk.Button(buttons, text="停止全部", command=self.stop_all)
        self.stop_button.pack(side="left", padx=(10, 0))
        ttk.Button(buttons, text="打开启动日志", command=self.open_log).pack(side="left", padx=(10, 0))
        ttk.Button(buttons, text="打开平台", command=self.open_platform).pack(side="right")
        self.message = ttk.Label(outer, text="就绪", foreground="#555555")
        self.message.pack(anchor="w", pady=(4, 0))

    def _set_message(self, text: str) -> None:
        self.message.configure(text=text)

    def _drain_events(self) -> None:
        try:
            while True:
                event, value = self.events.get_nowait()
                if event == "message":
                    self._set_message(str(value))
                elif event == "buttons":
                    enabled = bool(value)
                    self.start_button.configure(state="normal" if enabled else "disabled")
                    self.stop_button.configure(state="normal" if enabled else "disabled")
                elif event == "state":
                    key, state, detail = value
                    service = next(item for item in self.services if item.key == key)
                    service.state = state
                    label = "未运行" if key == "mysql" and state == "stopped" else {
                        "ready": "已运行", "starting": "启动中", "stopping": "停止中",
                        "failed": "失败", "stopped": "已停止"}.get(state, state)
                    self.status_labels[key].configure(text=label)
                    self.detail_labels[key].configure(text=detail)
                elif event == "close":
                    self.closing = True
                    self.destroy()
                    return
                elif event == "stop_after_cancel":
                    self.stop_all()
        except queue.Empty:
            pass
        if not self.closing:
            self.after(150, self._drain_events)

    def _emit_state(self, service: Service, state: str, detail: str = "") -> None:
        # Update the model immediately; the Tk event is drained asynchronously.
        # This prevents the worker's readiness loop from seeing a stale state.
        service.state = state
        self.events.put(("state", (service.key, state, detail)))

    def _refresh_statuses(self) -> None:
        if not self.closing:
            if self.probe_lock.acquire(blocking=False):
                threading.Thread(target=self._probe_status_worker, daemon=True).start()
            self.after(2000, self._refresh_statuses)

    def _probe_status_worker(self) -> None:
        try:
            for service in self.services:
                if service.key == "mysql":
                    ready = service.readiness()
                    if service.state != ("ready" if ready else "stopped"):
                        self._emit_state(service, "ready" if ready else "stopped", "3306" if ready else "")
                elif self.operation_lock.locked():
                    continue
                elif service.process is not None:
                    if service.process.poll() is not None and service.state in {"starting", "ready"}:
                        self._emit_state(service, "failed", f"进程已退出（{service.process.returncode}）")
                elif service.state != "failed":
                    ready = service.readiness()
                    if ready and service.state != "ready":
                        service.external = True
                        self._emit_state(service, "ready", "已有服务响应")
                    elif not ready and service.state == "ready":
                        service.external = False
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
            self.events.put(("message", "正在启动后端和 Runner…"))
            for service in self.services[1:]:
                if self.cancel_start.is_set():
                    break
                if service.readiness():
                    service.external = service.process is None
                    self._emit_state(service, "ready", "已有服务响应" if service.external else f"127.0.0.1:{service.port}")
                    continue
                service.external = False
                if service.process is not None and service.process.poll() is None:
                    self._emit_state(service, "starting", "继续等待已有进程")
                    continue
                service.process = None
                if service.port and port_open(service.port):
                    reason = f"端口 {service.port} 已被占用，但健康检查未通过"
                    self._emit_state(service, "failed", reason)
                    log_failure(service.key, service.log_path, reason)
                    continue
                if not service.command:
                    self._emit_state(service, "failed", "未找到 Maven，请安装 JDK 17 和 Maven")
                    log_failure(service.key, service.log_path, "未找到 Maven")
                    continue
                if not service.cwd.is_dir():
                    self._emit_state(service, "failed", f"目录不存在：{service.cwd}")
                    log_failure(service.key, service.log_path, f"目录不存在：{service.cwd}")
                    continue
                missing = self._missing_command(service)
                if missing:
                    self._emit_state(service, "failed", f"未找到 {missing}，请检查桌面会话的 PATH")
                    append_log(f"cannot start {service.key}: missing {missing}; PATH={os.environ.get('PATH', '')}")
                    continue
                self._emit_state(service, "starting", "进程已创建")
                env = os.environ.copy()
                env.update({"APP_WORKSPACE_ROOT": str(ROOT), "APP_REDIS_REQUIRED": "false",
                            "APP_REDIS_FALLBACK_MEMORY": "true", "RUNNER_AUTO_START": "false",
                            "PYTHONUNBUFFERED": "1"})
                if service.key == "backend":
                    env["STATIC_ROOT"] = str(FRONTEND_DIR)
                try:
                    log_stream = service.log_path.open("a", encoding="utf-8")
                    try:
                        service.process = subprocess.Popen(service.command, cwd=service.cwd, env=env,
                                                           stdin=subprocess.DEVNULL, stdout=log_stream,
                                                           stderr=subprocess.STDOUT, start_new_session=True)
                    finally:
                        log_stream.close()
                    append_log(f"started {service.key}: {' '.join(service.command)}")
                except OSError as exc:
                    self._emit_state(service, "failed", str(exc))
                    log_failure(service.key, service.log_path, str(exc))

            deadline = time.monotonic() + START_TIMEOUT
            while time.monotonic() < deadline and not self.cancel_start.is_set():
                pending = False
                for service in self.services[1:]:
                    if service.external or service.state in {"ready", "failed"}:
                        continue
                    pending = True
                    if service.readiness():
                        self._emit_state(service, "ready", f"127.0.0.1:{service.port}")
                    elif service.process is not None and service.process.poll() is not None:
                        self._emit_state(service, "failed", f"进程已退出（{service.process.returncode}），请查看日志")
                        log_failure(service.key, service.log_path, f"进程已退出（{service.process.returncode}）")
                if not pending:
                    break
                time.sleep(POLL_INTERVAL)
            if not self.cancel_start.is_set():
                for service in self.services[1:]:
                    if service.state == "starting":
                        self._emit_state(service, "failed", "启动超时，请查看日志")
                        log_failure(service.key, service.log_path, "启动超时")
                self.events.put(("message", "服务启动检查完成"))
        except Exception as exc:
            append_log(f"start worker failed: {exc!r}")
            self.events.put(("message", f"启动器遇到错误：{exc}；请查看日志"))
        finally:
            self.operation_lock.release()
            self.events.put(("stop_after_cancel" if self.cancel_start.is_set() else "buttons", True))

    def _prepare_frontend(self) -> bool:
        if not frontend_needs_build():
            return True
        log_path = LOG_DIR / "frontend-build.log"
        if not (FRONTEND_DIR / "node_modules" / ".bin" / "vite").exists():
            reason = "网页尚未构建，且未安装前端依赖"
            self.events.put(("message", reason))
            append_log(f"frontend build: {reason}")
            return False
        if not shutil.which("npm"):
            reason = "网页尚未构建，且桌面环境找不到 npm"
            self.events.put(("message", reason))
            append_log(f"frontend build: {reason}")
            return False

        self.events.put(("message", "正在构建网页，请稍候…"))
        append_log("frontend build started")
        try:
            with log_path.open("a", encoding="utf-8") as stream:
                self.build_process = subprocess.Popen(
                    ["npm", "run", "build"], cwd=FRONTEND_DIR, stdin=subprocess.DEVNULL,
                    stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
            deadline = time.monotonic() + BUILD_TIMEOUT
            while self.build_process.poll() is None:
                if self.cancel_start.is_set() or time.monotonic() >= deadline:
                    self._terminate_build()
                    if self.cancel_start.is_set():
                        return False
                    reason = "网页构建超时"
                    self.events.put(("message", reason))
                    log_failure("frontend build", log_path, reason)
                    return False
                time.sleep(0.2)
            if self.build_process.returncode != 0 or not FRONTEND_INDEX.is_file():
                reason = f"网页构建失败（退出码 {self.build_process.returncode}）"
                self.events.put(("message", reason))
                log_failure("frontend build", log_path, reason)
                return False
            append_log("frontend build completed")
            return True
        except OSError as exc:
            self.events.put(("message", f"网页构建失败：{exc}"))
            log_failure("frontend build", log_path, str(exc))
            return False
        finally:
            self.build_process = None

    def _terminate_build(self) -> None:
        process = self.build_process
        if process is None or process.poll() is not None:
            return
        try:
            os.killpg(process.pid, signal.SIGTERM)
            process.wait(timeout=2)
        except (OSError, subprocess.TimeoutExpired):
            try:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=2)
            except (OSError, subprocess.TimeoutExpired):
                pass

    def _missing_command(self, service: Service) -> str:
        if service.key == "backend":
            if not shutil.which("java"):
                return "java（JDK 17+）"
            if service.command and service.command[0] == "mvn" and not shutil.which("mvn"):
                return "mvn"
        elif service.prerequisite and not shutil.which(service.prerequisite):
            return service.prerequisite
        return ""

    def stop_all(self) -> None:
        if not self.operation_lock.acquire(blocking=False):
            self.cancel_start.set()
            self.stop_button.configure(state="disabled")
            self._set_message("正在取消启动并停止已创建的服务…")
            return
        self.start_button.configure(state="disabled")
        self.stop_button.configure(state="disabled")
        threading.Thread(target=self._stop_worker, daemon=True).start()

    def _stop_worker(self) -> None:
        try:
            self._stop_worker_body()
        finally:
            self.operation_lock.release()
            self.events.put(("buttons", True))

    def _stop_worker_body(self) -> None:
        self.events.put(("message", "正在停止由启动器启动的服务…"))
        for service in reversed(self.services[1:]):
            process = service.process
            if service.external:
                ready = service.readiness()
                self._emit_state(service, "ready" if ready else "stopped",
                                 "已有服务响应，未由启动器管理" if ready else "")
                continue
            if process is None:
                self._emit_state(service, "stopped", "")
                continue
            self._emit_state(service, "stopping", "")
            try:
                os.killpg(process.pid, signal.SIGTERM)
                for _ in range(40):
                    if not service.readiness():
                        break
                    time.sleep(0.2)
                if service.readiness():
                    os.killpg(process.pid, signal.SIGKILL)
                if process.poll() is None:
                    process.wait(timeout=2)
            except (OSError, subprocess.TimeoutExpired) as exc:
                append_log(f"stop {service.key}: {exc}")
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except OSError:
                    pass
            try:
                process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                append_log(f"stop {service.key}: process did not exit after SIGKILL")
            still_ready = service.readiness()
            self._emit_state(service, "stopped" if not still_ready else "failed",
                             "" if not still_ready else "端口仍有响应，请检查日志")
            service.process = None
            service.external = False
        self.events.put(("message", "已停止启动器管理的服务"))

    def open_log(self) -> None:
        path = LOG_DIR / "launcher.log"
        path.touch(exist_ok=True)
        self._open_path(path)

    def open_platform(self) -> None:
        if not FRONTEND_INDEX.is_file():
            messagebox.showerror(APP_NAME, "网页尚未构建，请先点击“启动全部”并查看启动日志。")
            return
        if not http_ready(8081, "/dist/index.html"):
            messagebox.showerror(APP_NAME, "后端尚未提供网页，请检查后端状态和启动日志。")
            return
        self._open_path("http://127.0.0.1:8081/")

    def _open_path(self, path) -> None:
        opener = shutil.which("xdg-open")
        if not opener:
            messagebox.showerror(APP_NAME, "系统未找到 xdg-open，无法打开文件或浏览器。")
            return
        try:
            subprocess.Popen([opener, str(path)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except OSError as exc:
            messagebox.showerror(APP_NAME, f"打开失败：{exc}")

    def close(self) -> None:
        if self.operation_lock.locked():
            messagebox.showinfo(APP_NAME, "请等待当前启动或停止操作完成。")
            return
        if any(service.process is not None for service in self.services[1:]):
            # Reuse the normal process-group cleanup before destroying Tk.
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


def main() -> int:
    if sys.version_info < (3, 8):
        print("Linux 启动器需要 Python 3.8 或更高版本。", file=sys.stderr)
        return 1
    if not (ROOT / "backend").is_dir() or not (ROOT / "fronternd").is_dir():
        print(f"无法识别项目根目录：{ROOT}", file=sys.stderr)
        return 1
    prepare_process_path()
    Launcher().mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
