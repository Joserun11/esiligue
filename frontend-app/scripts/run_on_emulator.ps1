<#
Helper script to bring up backend via Docker, build the Android debug APK and install it on a running Android emulator.
Usage:
  Open PowerShell as the project user and run from any path:
  .\frontend-app\scripts\run_on_emulator.ps1

Requirements:
- Docker Desktop running
- Android SDK platform-tools (adb) on PATH
- An Android emulator already started (AVD) and visible in `adb devices`
#>

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir '..')
Write-Host "Repo root: $repoRoot"

# 1) Start backend (Docker Compose)
Write-Host "Starting backend (docker compose up -d --build) from repo root..."
Push-Location $repoRoot
try {
    docker compose up -d --build
} catch {
    Write-Error "Failed to run docker compose: $_"
    Pop-Location
    exit 1
}

# Wait for API to respond
Write-Host "Waiting for API at http://localhost:8080/api/usuarios to return 200..."
$ok = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $r = Invoke-WebRequest -Uri 'http://localhost:8080/api/usuarios' -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $ok = $true; break }
    } catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $ok) { Write-Warning "API did not become healthy in time, but continuing. Check docker compose logs." }

# 2) Build Android debug APK
Write-Host "Building Android debug APK..."
Push-Location (Join-Path $repoRoot 'frontend-app')
try {
    & .\gradlew.bat assembleDebug --no-daemon
} catch {
    Write-Error "Gradle build failed: $_"
    Pop-Location
    Pop-Location
    exit 1
}

# 3) Wait for emulator device
Write-Host "Waiting for an emulator device (adb)..."
$device = $null
for ($i = 0; $i -lt 30; $i++) {
    $devices = & adb devices | Select-String -Pattern "emulator-\d+|emulator-" -Quiet
    if ($devices) { $device = 'emulator'; break }
    Start-Sleep -Seconds 1
}
if (-not $device) { Write-Warning "No emulator detected in 'adb devices'. Make sure an AVD is running and visible to adb." }

# 4) Install APK
$apkPath = Join-Path (Join-Path $repoRoot 'frontend-app') 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apkPath)) { Write-Error "APK not found at $apkPath"; Pop-Location; Pop-Location; exit 1 }

try {
    Write-Host "Installing APK to device..."
    & adb install -r $apkPath
    Write-Host "APK installed."
} catch {
    Write-Error "adb install failed: $_"
}

Pop-Location
Pop-Location
Write-Host "Done. If the app did not start automatically, open the emulator and launch the app."