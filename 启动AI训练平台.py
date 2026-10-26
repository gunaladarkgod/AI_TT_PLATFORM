#!/usr/bin/env python3
"""Graphical, cross-platform launcher for the local AI training platform."""

from __future__ import annotations

import os
import platform
import queue
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import webbrowser
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional
from tkinter import END, BOTH, LEFT, RIGHT, X, BooleanVar, StringVar, Tk, messagebox
from tkinter import ttk


ROOT = Path(__file__).resolve().parent
BACKEND_DIR = ROOT / "backend"
FRONTEND_DIR = ROOT / "fronternd"
ENV_FILE = BACKEND_DIR / ".env.local"
LOG_DIR = ROOT / "logs" / "launcher"
FRONTEND_URL = "http://127.0.0.1:5173/dist/"


@dataclass(frozen=True)
class ServiceSpec:
    key: str
    label: str
    port: int
    cwd: Path
    command: tuple[str, ...]
    log_name: str


SERVICES = (
    ServiceSpec("backend", "后端 Spring Boot", 8081, BACKEND_DIR, ("mvn", "-f", "pom.xml", "spring-boot:run"), "backend.log"),
    ServiceSpec("frontend", "前端 Vite", 5173, FRONTEND_DIR, ("npm", "run", "dev", "--", "--host", "127.0.0.1", "--port", "5173", "--strictPort"), "frontend.log"),
)


def load_local_env() -> dict[str, str]:
    env = os.environ.copy()
    if not ENV_FILE.is_file():
        return env
    for raw_line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key:
            env[key] = value
    return env


def command_path(command: str) -> Optional[str]:
    """Resolve Windows .cmd shims as well as regular executables."""
    path = shutil.which(command)
    if path:
        return path
    if platform.system() == "Windows":
        return shutil.which(f"{command}.cmd") or shutil.which(f"{command}.exe")
    return None


def port_is_open(port: int) -> bool:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=0.5):
            return True
    except OSError:
        return False


def http_is_ready(url: str) -> bool:
    try:
        with urllib.request.urlopen(url, timeout=2) as response:
            return 200 <= response.status < 500
    except (OSError, urllib.error.URLError):
        return False


def stop_process_tree(process: subprocess.Popen) -> None:
    if process.poll() is not None:
        return
    if platform.system() == "Windows":
        subprocess.run(
            ["taskkill", "/PID", str(process.pid), "/T", "/F"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        return
    try:
        os.killpg(process.pid, 15)
    except (ProcessLookupError, PermissionError):
        process.terminate()
    try:
        process.wait(timeout=8)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, 9)
        except (ProcessLookupError, PermissionError):
            process.kill()


class Launcher:
    def __init__(self, root: Tk) -> None:
        self.root = root
        self.root.title("AI 训练平台启动器")
        self.root.geometry("760x560")
        self.root.minsize(680, 480)
        self.events: queue.Queue[tuple[str, object]] = queue.Queue()
        self.processes: dict[str, subprocess.Popen] = {}
        self.owned_services: set[str] = set()
        self.running = False
        self.busy = False
        self.open_when_ready = True
        self.auto_open = BooleanVar(value=True)
        self.status_vars = {service.key: StringVar(value="未启动") for service in SERVICES}
        self.detail_vars = {service.key: StringVar(value=f"端口 {service.port}") for service in SERVICES}
        self.message_var = StringVar(value="准备就绪。点击“启动全部”开始运行平台。")
        self._build_ui()
        self.root.after(250, self._drain_events)
        self.root.after(1500, self._refresh_status)
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

    def _build_ui(self) -> None:
        header = ttk.Frame(self.root, padding=(20, 18, 20, 8))
        header.pack(fill=X)
        ttk.Label(header, text="AI 训练平台", font=("TkDefaultFont", 18, "bold")).pack(anchor="w")
        ttk.Label(header, text="跨平台本地服务启动器", foreground="#666666").pack(anchor="w", pady=(3, 0))

        actions = ttk.Frame(self.root, padding=(20, 10, 20, 8))
        actions.pack(fill=X)
        self.start_button = ttk.Button(actions, text="启动全部", command=self.start_all)
        self.start_button.pack(side=LEFT)
        self.stop_button = ttk.Button(actions, text="停止全部", command=self.stop_all, state="disabled")
        self.stop_button.pack(side=LEFT, padx=(8, 0))
        self.open_button = ttk.Button(actions, text="打开平台", command=lambda: webbrowser.open(FRONTEND_URL), state="disabled")
        self.open_button.pack(side=LEFT, padx=(8, 0))
        ttk.Checkbutton(actions, text="启动完成后打开浏览器", variable=self.auto_open).pack(side=RIGHT)

        services_frame = ttk.LabelFrame(self.root, text="服务状态", padding=10)
        services_frame.pack(fill=X, padx=20, pady=(0, 10))
        for service in SERVICES:
            row = ttk.Frame(services_frame)
            row.pack(fill=X, pady=4)
            ttk.Label(row, text=service.label, width=18).pack(side=LEFT)
            ttk.Label(row, textvariable=self.status_vars[service.key], width=12).pack(side=LEFT)
            ttk.Label(row, textvariable=self.detail_vars[service.key], foreground="#666666").pack(side=LEFT, padx=(8, 0))
        runner_row = ttk.Frame(services_frame)
        runner_row.pack(fill=X, pady=4)
        ttk.Label(runner_row, text="Python Runner", width=18).pack(side=LEFT)
        self.status_vars["runner"] = StringVar(value="未启动")
        self.detail_vars["runner"] = StringVar(value="端口 8009，随后端启动")
        ttk.Label(runner_row, textvariable=self.status_vars["runner"], width=12).pack(side=LEFT)
        ttk.Label(runner_row, textvariable=self.detail_vars["runner"], foreground="#666666").pack(side=LEFT, padx=(8, 0))
        mysql_row = ttk.Frame(services_frame)
        mysql_row.pack(fill=X, pady=4)
        ttk.Label(mysql_row, text="MySQL", width=18).pack(side=LEFT)
        self.status_vars["mysql"] = StringVar(value="未检查")
        self.detail_vars["mysql"] = StringVar(value="端口 3306")
        ttk.Label(mysql_row, textvariable=self.status_vars["mysql"], width=12).pack(side=LEFT)
        ttk.Label(mysql_row, textvariable=self.detail_vars["mysql"], foreground="#666666").pack(side=LEFT, padx=(8, 0))

        log_frame = ttk.LabelFrame(self.root, text="启动日志", padding=8)
        log_frame.pack(fill=BOTH, expand=True, padx=20, pady=(0, 10))
        self.log_text = ttk.Treeview(log_frame, columns=("time", "message"), show="headings", height=12)
        self.log_text.heading("time", text="时间")
        self.log_text.heading("message", text="消息")
        self.log_text.column("time", width=80, stretch=False)
        self.log_text.column("message", width=560)
        scrollbar = ttk.Scrollbar(log_frame, orient="vertical", command=self.log_text.yview)
        self.log_text.configure(yscrollcommand=scrollbar.set)
        self.log_text.pack(side=LEFT, fill=BOTH, expand=True)
        scrollbar.pack(side=RIGHT, fill="y")

        footer = ttk.Frame(self.root, padding=(20, 0, 20, 15))
        footer.pack(fill=X)
        ttk.Label(footer, textvariable=self.message_var, foreground="#555555").pack(side=LEFT, fill=X, expand=True)
        ttk.Button(footer, text="打开日志目录", command=lambda: self._open_path(LOG_DIR)).pack(side=RIGHT)

    def _append_log(self, message: str) -> None:
        timestamp = time.strftime("%H:%M:%S")
        self.log_text.insert("", END, values=(timestamp, message))
        children = self.log_text.get_children()
        if len(children) > 300:
            self.log_text.delete(children[0])
        self.log_text.yview_moveto(1)

    def _set_status(self, key: str, status: str, detail: Optional[str] = None) -> None:
        self.status_vars[key].set(status)
        if detail is not None:
            self.detail_vars[key].set(detail)

    def _drain_events(self) -> None:
        try:
            while True:
                event, value = self.events.get_nowait()
                if event == "log":
                    self._append_log(str(value))
                elif event == "status":
                    key, status, detail = value  # type: ignore[misc]
                    self._set_status(key, status, detail)
                elif event == "message":
                    self.message_var.set(str(value))
                elif event == "done":
                    self.busy = False
                    self.running = bool(value)
                    self.start_button.configure(state="disabled" if self.running else "normal")
                    self.stop_button.configure(state="normal" if self.running else "disabled")
                    self.open_button.configure(state="normal" if self.running else "disabled")
        except queue.Empty:
            pass
        self.root.after(250, self._drain_events)

    def _refresh_status(self) -> None:
        if port_is_open(3306):
            self._set_status("mysql", "正常", "3306 可连接")
        else:
            self._set_status("mysql", "未运行", "请启动 MySQL")
        if port_is_open(8081):
            self._set_status("backend", "正常", "http://127.0.0.1:8081")
        elif not self.busy:
            self._set_status("backend", "未启动", "端口 8081")
        if port_is_open(5173):
            self._set_status("frontend", "正常", FRONTEND_URL)
        elif not self.busy:
            self._set_status("frontend", "未启动", "端口 5173")
        if http_is_ready("http://127.0.0.1:8009/health"):
            self._set_status("runner", "正常", "http://127.0.0.1:8009/health")
        elif not self.busy:
            self._set_status("runner", "未启动", "端口 8009，随后端启动")
        self.root.after(1500, self._refresh_status)

    def _check_environment(self) -> bool:
        missing = []
        for command, label in (("java", "JDK 17"), ("mvn", "Maven"), ("npm", "Node.js")):
            if command_path(command) is None:
                missing.append(label)
        if not FRONTEND_DIR.is_dir() or not BACKEND_DIR.is_dir():
            missing.append("项目目录结构")
        if missing:
            message = "未找到：" + "、".join(missing) + "。请先安装或检查项目目录。"
            self.events.put(("message", message))
            self.events.put(("log", message))
            return False
        if not (FRONTEND_DIR / "node_modules").is_dir():
            self.events.put(("log", "首次启动：正在安装前端依赖，请稍候。"))
            try:
                subprocess.run([command_path("npm") or "npm", "ci"], cwd=FRONTEND_DIR, env=load_local_env(), check=True, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
            except (OSError, subprocess.CalledProcessError) as error:
                self.events.put(("log", f"前端依赖安装失败：{error}"))
                return False
        return True

    def _service_env(self) -> dict[str, str]:
        env = load_local_env()
        env.setdefault("DB_USERNAME", "root")
        env.setdefault("DB_PASSWORD", "123456")
        env.setdefault("APP_REDIS_REQUIRED", "false")
        env.setdefault("APP_REDIS_FALLBACK_MEMORY", "true")
        env.setdefault("RUNNER_AUTO_START", "true")
        env.setdefault("RUNNER_AUTO_STOP_ON_SHUTDOWN", "true")
        env.setdefault("APP_WORKSPACE_ROOT", str(ROOT))
        return env

    def _start_process(self, service: ServiceSpec, env: dict[str, str]) -> Optional[subprocess.Popen]:
        if port_is_open(service.port):
            self.events.put(("status", (service.key, "已在运行", f"端口 {service.port} 已被占用")))
            self.events.put(("log", f"检测到 {service.label} 已在运行，复用现有服务。"))
            return None
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        log_file = (LOG_DIR / service.log_name).open("ab")
        command = list(service.command)
        resolved = command_path(command[0])
        if resolved:
            command[0] = resolved
            if platform.system() == "Windows" and Path(resolved).suffix.lower() in {".cmd", ".bat"}:
                command = ["cmd.exe", "/d", "/c", resolved, *command[1:]]
        kwargs: dict[str, object] = {
            "cwd": service.cwd,
            "env": env,
            "stdout": log_file,
            "stderr": subprocess.STDOUT,
            "stdin": subprocess.DEVNULL,
            "close_fds": platform.system() != "Windows",
        }
        if platform.system() == "Windows":
            kwargs["creationflags"] = getattr(subprocess, "CREATE_NO_WINDOW", 0) | getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)
        else:
            kwargs["start_new_session"] = True
        try:
            process = subprocess.Popen(command, **kwargs)  # type: ignore[arg-type]
        except OSError as error:
            log_file.close()
            self.events.put(("log", f"{service.label} 启动失败：{error}"))
            self.events.put(("status", (service.key, "启动失败", str(error))))
            return None
        log_file.close()
        self.processes[service.key] = process
        self.owned_services.add(service.key)
        self.events.put(("status", (service.key, "启动中", f"PID {process.pid}，端口 {service.port}")))
        self.events.put(("log", f"已在后台启动 {service.label}（PID {process.pid}）。"))
        return process

    def _wait_for(self, key: str, predicate: Callable[[], bool], seconds: int, detail: str) -> bool:
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if predicate():
                self.events.put(("status", (key, "正常", detail)))
                return True
            process = self.processes.get(key)
            if process and process.poll() is not None:
                self.events.put(("status", (key, "启动失败", f"进程已退出，查看 logs/launcher")))
                return False
            time.sleep(0.5)
        self.events.put(("status", (key, "超时", detail)))
        return False

    def start_all(self) -> None:
        if self.busy:
            return
        self.busy = True
        self.open_when_ready = self.auto_open.get()
        self.start_button.configure(state="disabled")
        self.stop_button.configure(state="disabled")
        self._append_log("开始检查本机环境。")
        threading.Thread(target=self._start_all_worker, daemon=True).start()

    def _start_all_worker(self) -> None:
        try:
            if not self._check_environment():
                self.events.put(("done", False))
                return
            if not port_is_open(3306):
                self.events.put(("status", ("mysql", "未运行", "请先启动 MySQL 服务")))
                self.events.put(("message", "MySQL 未运行，平台后端无法完成初始化。"))
                self.events.put(("log", "启动已停止：未检测到 MySQL 3306。"))
                self.events.put(("done", False))
                return
            env = self._service_env()
            backend = next(service for service in SERVICES if service.key == "backend")
            frontend = next(service for service in SERVICES if service.key == "frontend")
            backend_process = self._start_process(backend, env)
            if backend_process is None and not port_is_open(8081):
                self.events.put(("message", "后端启动失败，请打开日志查看原因。"))
                self.events.put(("done", False))
                return
            if not self._wait_for("backend", lambda: port_is_open(8081), 120, "http://127.0.0.1:8081"):
                self.events.put(("message", "后端未能在限定时间内启动，请打开日志查看原因。"))
                self.events.put(("done", False))
                return
            self.events.put(("log", "后端已响应，等待 Python Runner。"))
            if not self._wait_for("runner", lambda: http_is_ready("http://127.0.0.1:8009/health"), 60, "http://127.0.0.1:8009/health"):
                self.events.put(("log", "Runner 尚未就绪，训练功能暂不可用；继续启动前端。"))
            frontend_process = self._start_process(frontend, env)
            if frontend_process is None and not port_is_open(5173):
                self.events.put(("message", "前端启动失败，请打开日志查看原因。"))
                self.events.put(("done", False))
                return
            if not self._wait_for("frontend", lambda: http_is_ready(FRONTEND_URL), 60, FRONTEND_URL):
                self.events.put(("message", "前端未能在限定时间内启动，请打开日志查看原因。"))
                self.events.put(("done", False))
                return
            self.events.put(("message", "平台已启动，可以开始使用。"))
            self.events.put(("log", "全部服务已就绪。"))
            if self.open_when_ready:
                webbrowser.open(FRONTEND_URL)
            self.events.put(("done", True))
        except Exception as error:  # 防止后台线程异常导致界面无响应
            self.events.put(("log", f"启动过程发生异常：{error}"))
            self.events.put(("message", "启动失败，请查看启动日志。"))
            self.events.put(("done", False))

    def stop_all(self) -> None:
        if self.busy:
            return
        self.busy = True
        self.stop_button.configure(state="disabled")
        threading.Thread(target=self._stop_all_worker, daemon=True).start()

    def _stop_all_worker(self) -> None:
        for key in ("frontend", "backend"):
            process = self.processes.pop(key, None)
            if process and key in self.owned_services:
                self.events.put(("log", f"正在停止 {key}（PID {process.pid}）。"))
                stop_process_tree(process)
        self.owned_services.clear()
        self.events.put(("message", "已停止本次启动器创建的服务。"))
        self.events.put(("done", False))

    def _open_path(self, path: Path) -> None:
        path.mkdir(parents=True, exist_ok=True)
        system = platform.system()
        try:
            if system == "Windows":
                os.startfile(path)  # type: ignore[attr-defined]
            elif system == "Darwin":
                subprocess.Popen(["open", str(path)])
            else:
                subprocess.Popen(["xdg-open", str(path)])
        except OSError as error:
            messagebox.showerror("无法打开目录", str(error))

    def _on_close(self) -> None:
        if self.processes and messagebox.askyesno("退出启动器", "仍有本次启动的服务在运行，是否停止后退出？"):
            self._stop_all_worker()
        elif self.processes:
            return
        self.root.destroy()


def main() -> int:
    if not BACKEND_DIR.is_dir() or not FRONTEND_DIR.is_dir():
        messagebox.showerror("项目目录不完整", f"未找到 backend 或 fronternd：\n{ROOT}")
        return 1
    root = Tk()
    ttk.Style(root).theme_use("clam")
    Launcher(root)
    root.mainloop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
