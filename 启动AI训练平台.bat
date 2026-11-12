@echo off
setlocal EnableExtensions
set "ROOT=%~dp0"
set "SCRIPT=%ROOT%launcher\windows\ai-training-platform-launcher.py"

where pyw.exe >nul 2>nul
if not errorlevel 1 (
  start "AI 训练平台启动器" pyw.exe -3 "%SCRIPT%"
  exit /b 0
)

where pythonw.exe >nul 2>nul
if not errorlevel 1 (
  start "AI 训练平台启动器" pythonw.exe "%SCRIPT%"
  exit /b 0
)

echo 未找到 pythonw.exe。请安装包含 Tkinter 的 Python 3，并启用 pythonw.exe。
pause
exit /b 1
