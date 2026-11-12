@echo off
setlocal EnableExtensions
set "ROOT=%~dp0"
set "LAUNCHER=%ROOT%launcher\windows\dist\启动AI训练平台.exe"

if exist "%LAUNCHER%" (
  start "AI 训练平台启动器" "%LAUNCHER%"
  exit /b 0
)

echo 尚未生成图形启动器，请双击 launcher\windows\build_exe.bat 生成 EXE 和桌面快捷方式。
pause
exit /b 1
