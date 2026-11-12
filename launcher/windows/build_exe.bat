@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_exe.ps1"
if errorlevel 1 (
  echo.
  echo Launcher build failed. Review the message above.
  pause
  exit /b 1
)
echo.
echo Build completed. The desktop shortcut is ready.
pause
