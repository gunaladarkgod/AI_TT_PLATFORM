@echo off
setlocal
set "ROOT=%~dp0"
where pyw >nul 2>nul
if %errorlevel% equ 0 (
  start "" /b pyw -3 "%ROOT%启动AI训练平台.py"
  exit /b 0
)
where pythonw >nul 2>nul
if %errorlevel% equ 0 (
  start "" /b pythonw "%ROOT%启动AI训练平台.py"
  exit /b 0
)
echo 未找到 pythonw.exe，请安装 Python 3 并重试。
pause
