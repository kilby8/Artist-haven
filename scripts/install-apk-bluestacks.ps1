# Install the debug APK into BlueStacks (Windows PowerShell)
# Usage: Open PowerShell as the user and run this from repo root:
#   .\scripts\install-apk-bluestacks.ps1

$repoRoot = Resolve-Path (Join-Path -Path $PSScriptRoot -ChildPath "..")
$latestJsonPath = Join-Path $repoRoot "docs\downloads\latest.json"

$apkPath = $null
if (Test-Path $latestJsonPath) {
    try {
        $latest = Get-Content $latestJsonPath -Raw | ConvertFrom-Json
        $candidate = Join-Path $repoRoot ("docs\\downloads\\" + $latest.file)
        $apkPath = Resolve-Path $candidate -ErrorAction SilentlyContinue
    } catch {
        # Ignore malformed metadata and continue to fallback locations.
    }
}

if (-not $apkPath) {
    $legacyPublished = Join-Path $repoRoot "docs\downloads\Artist-Haven-debug.apk"
    $apkPath = Resolve-Path $legacyPublished -ErrorAction SilentlyContinue
}

if (-not $apkPath) {
    $buildOutput = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
    $apkPath = Resolve-Path $buildOutput -ErrorAction SilentlyContinue
}

if (-not $apkPath) {
    Write-Host "APK not found. Publish first: .\scripts\publish-versioned-apk.ps1" -ForegroundColor Yellow
    exit 1
}

# Try to find adb
$adb = "adb"
try {
    & $adb version > $null 2>&1
} catch {
    Write-Host "adb not found in PATH. Ensure Android SDK platform-tools are installed and adb is on PATH." -ForegroundColor Red
    exit 2
}

# Connect to BlueStacks
Write-Host "Connecting to BlueStacks ADB at 127.0.0.1:5555..."
& $adb connect 127.0.0.1:5555
Start-Sleep -Seconds 1

# Show devices
& $adb devices

# Install (replace)
Write-Host "Installing APK: $apkPath"
& $adb install -r $apkPath

if ($LASTEXITCODE -eq 0) {
    Write-Host "APK installed successfully." -ForegroundColor Green
} else {
    Write-Host "adb install failed. Check output above." -ForegroundColor Red
}
