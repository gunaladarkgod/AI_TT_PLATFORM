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
$distDir = $launcherDir
$buildDir = Join-Path $launcherDir '.build\work'
$specDir = Join-Path $launcherDir '.build\spec'
$pyInstallerArgs = @(
    '--noconfirm', '--clean', '--onefile', '--windowed',
    '--name', '启动AI训练平台', '--specpath', $specDir, '--distpath', $distDir,
    '--workpath', $buildDir
)

$pythonPrefix = & $venvPython -c "import sys; print(sys.base_prefix)"
$tclDll = Join-Path $pythonPrefix 'Library\bin\tcl86t.dll'
$tkDll = Join-Path $pythonPrefix 'Library\bin\tk86t.dll'
$tclData = Join-Path $pythonPrefix 'Library\lib\tcl8.6'
$tkData = Join-Path $pythonPrefix 'Library\lib\tk8.6'
if ((Test-Path $tclDll) -and (Test-Path $tkDll) -and (Test-Path $tclData) -and (Test-Path $tkData)) {
    $pyInstallerArgs += @('--add-binary', "$tclDll;.", '--add-binary', "$tkDll;.",
        '--add-data', "$tclData;tcl/tcl8.6", '--add-data', "$tkData;tcl/tk8.6")
}

& $venvPython -m PyInstaller @pyInstallerArgs $sourceFile
if ($LASTEXITCODE -ne 0) { throw 'PyInstaller failed to build the launcher.' }

$exePath = Join-Path $launcherDir '启动AI训练平台.exe'
& (Join-Path $launcherDir 'create_desktop_shortcut.ps1') -ExecutablePath $exePath -WorkspaceRoot $workspaceRoot
Write-Host "Created: $exePath"
Write-Host 'Created a desktop shortcut.'
