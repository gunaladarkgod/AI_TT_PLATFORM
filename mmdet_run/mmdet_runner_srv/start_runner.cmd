@echo off
setlocal EnableExtensions

rem =============================================================================
rem MMDet Python Runner for Windows (FastAPI + uvicorn)
rem
rem Optional environment variables:
rem   RUNNER_PYTHON       Explicit Python executable path.
rem   RUNNER_PIP_INSTALL  Set to 1 to run pip install -r requirements.txt.
rem =============================================================================

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
pushd "%SCRIPT_DIR%" >nul

set "REPO_ROOT=%SCRIPT_DIR%\..\.."

rem Avoid mixing user-site packages with the runner environment.
set "PYTHONNOUSERSITE=1"
set "PYTHONUTF8=1"

set "PY="
if defined RUNNER_PYTHON set "PY=%RUNNER_PYTHON%"

if not defined PY (
  if exist "%SCRIPT_DIR%\.conda_runner\python.exe" set "PY=%SCRIPT_DIR%\.conda_runner\python.exe"
)
if not defined PY (
  if exist "%SCRIPT_DIR%\.venv\Scripts\python.exe" set "PY=%SCRIPT_DIR%\.venv\Scripts\python.exe"
)
if not defined PY (
  if exist "%USERPROFILE%\.conda\envs\openmmlab\python.exe" set "PY=%USERPROFILE%\.conda\envs\openmmlab\python.exe"
)
if not defined PY (
  for /f "delims=" %%P in ('where python 2^>nul') do (
    if not defined PY set "PY=%%P"
  )
)
if not defined PY (
  for /f "delims=" %%P in ('where py 2^>nul') do (
    if not defined PY set "PY=%%P"
  )
)

if not defined PY (
  echo [start_runner.cmd] Python not found. Set RUNNER_PYTHON to your python.exe path. 1>&2
  popd >nul
  exit /b 1
)

echo [start_runner.cmd] using Python: %PY% (PYTHONNOUSERSITE=%PYTHONNOUSERSITE%)

"%PY%" -c "import fastapi, uvicorn; from mmengine.config import Config" >nul 2>&1
if errorlevel 1 (
  if "%RUNNER_AUTO_INSTALL%"=="0" (
    echo [start_runner.cmd] Runner dependencies are missing and RUNNER_AUTO_INSTALL is disabled. 1>&2
    popd >nul
    exit /b 1
  )
  echo [start_runner.cmd] Missing Runner dependencies, installing requirements.txt
  "%PY%" -m pip install -r "%SCRIPT_DIR%\requirements.txt"
  if errorlevel 1 (
    popd >nul
    exit /b 1
  )
)

rem Windows defaults derived from this repository; explicit environment variables still win.
if not defined MMDET_REPO_ROOT set "MMDET_REPO_ROOT=%REPO_ROOT%\mmdet_run\mmdetection-3.0.0"
if not defined MMDET_UPLOAD_ROOT set "MMDET_UPLOAD_ROOT=%REPO_ROOT%\mmdet_run\myfiles"
if not defined MMDET_WORK_ROOT set "MMDET_WORK_ROOT=%REPO_ROOT%\artifacts\mmdet_runs"

if not exist "%MMDET_UPLOAD_ROOT%" mkdir "%MMDET_UPLOAD_ROOT%"
if not exist "%MMDET_WORK_ROOT%" mkdir "%MMDET_WORK_ROOT%"

if "%RUNNER_PIP_INSTALL%"=="1" (
  echo [start_runner.cmd] RUNNER_PIP_INSTALL=1, installing requirements.txt
  "%PY%" -m pip install -r "%SCRIPT_DIR%\requirements.txt"
  if errorlevel 1 (
    popd >nul
    exit /b 1
  )
)

"%PY%" -m uvicorn mmdet_runner_server:app --host 127.0.0.1 --port 8009
set "EXIT_CODE=%ERRORLEVEL%"
popd >nul
exit /b %EXIT_CODE%
