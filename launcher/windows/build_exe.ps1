$ErrorActionPreference = 'Stop'

$launcherDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceRoot = (Resolve-Path (Join-Path $launcherDir '..\..')).Path
$venvDir = Join-Path $launcherDir '.build-venv'
$venvPython = Join-Path $venvDir 'Scripts\python.exe'

if (-not (Test-Path $venvPython)) {
    if (Get-Command py -ErrorAction SilentlyContinue) {
        & py -3 -c "import tkinter" 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'Install Python 3 with Tcl/Tk support first.' }
        & py -3 -m venv $venvDir
    } elseif (Get-Command python -ErrorAction SilentlyContinue) {
        & python -c "import tkinter" 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'Install Python 3 with Tcl/Tk support first.' }
        & python -m venv $venvDir
    } else {
        throw 'Python 3 was not found. Install Python 3 with Tcl/Tk support first.'
    }
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the packaging environment.' }
}

& $venvPython -m pip install --disable-pip-version-check pyinstaller
if ($LASTEXITCODE -ne 0) { throw 'Could not install PyInstaller.' }

$sourceFile = Join-Path $launcherDir 'ai-training-platform-launcher.py'
$distDir = Join-Path $launcherDir 'dist'
$buildDir = Join-Path $launcherDir 'build'
& $venvPython -m PyInstaller --noconfirm --clean --onefile --windowed `
    --name '启动AI训练平台' --specpath $launcherDir --distpath $distDir `
    --workpath $buildDir $sourceFile
if ($LASTEXITCODE -ne 0) { throw 'PyInstaller failed to build the launcher.' }

$exePath = Join-Path $distDir '启动AI训练平台.exe'
& (Join-Path $launcherDir 'create_desktop_shortcut.ps1') -ExecutablePath $exePath -WorkspaceRoot $workspaceRoot
Write-Host "Created: $exePath"
Write-Host 'Created a desktop shortcut.'
