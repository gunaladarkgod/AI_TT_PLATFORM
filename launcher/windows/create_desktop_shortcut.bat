@echo off
setlocal
set "ROOT=%~dp0..\.."
set "EXE=%~dp0启动AI训练平台.exe"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0create_desktop_shortcut.ps1" -ExecutablePath "%EXE%" -WorkspaceRoot "%ROOT%"
if errorlevel 1 (
  echo.
  echo Could not create the desktop shortcut. Build the EXE first.
  pause
  exit /b 1
)
echo Desktop shortcut created.
pause
