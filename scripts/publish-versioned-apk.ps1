param(
    [switch]$SkipBuild,
    [int]$Keep = 5
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..")
Set-Location $repoRoot

$gradleKtsPath = Join-Path $repoRoot "app\build.gradle.kts"
$gradleText = Get-Content $gradleKtsPath -Raw

$versionNameMatch = [regex]::Match($gradleText, 'versionName\s*=\s*"([^"]+)"')
$versionCodeMatch = [regex]::Match($gradleText, 'versionCode\s*=\s*(\d+)')

if (-not $versionNameMatch.Success -or -not $versionCodeMatch.Success) {
    throw "Unable to parse versionName/versionCode from app/build.gradle.kts"
}

$versionName = $versionNameMatch.Groups[1].Value
$versionCode = $versionCodeMatch.Groups[1].Value

if (-not $SkipBuild) {
    Write-Host "Building debug APK..."
    & ".\gradlew.bat" "app:assembleDebug" "--no-daemon"
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed"
    }
}

$sourceApk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $sourceApk)) {
    throw "Source APK not found at $sourceApk"
}

$downloadsDir = Join-Path $repoRoot "docs\downloads"
if (-not (Test-Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}

$buildStamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss")
$versionedFileName = "Artist-Haven-v$versionName-b$versionCode-$buildStamp-debug.apk"
$versionedApkPath = Join-Path $downloadsDir $versionedFileName
$legacyApkPath = Join-Path $downloadsDir "Artist-Haven-debug.apk"

Copy-Item $sourceApk $versionedApkPath -Force
Copy-Item $sourceApk $legacyApkPath -Force

$apkItem = Get-Item $versionedApkPath
$hash = (Get-FileHash $versionedApkPath -Algorithm SHA256).Hash
$builtAtUtc = (Get-Date).ToUniversalTime().ToString("o")

$latest = [ordered]@{
    versionName = $versionName
    versionCode = [int]$versionCode
    file = $versionedFileName
    relativeUrl = "/Artist-haven/docs/downloads/$versionedFileName"
    sizeBytes = $apkItem.Length
    sha256 = $hash
    builtAtUtc = $builtAtUtc
}

$latestJsonPath = Join-Path $downloadsDir "latest.json"
$latest | ConvertTo-Json | Set-Content -Path $latestJsonPath -Encoding UTF8

# Keep only the latest N versioned APK files.
$allVersioned = Get-ChildItem $downloadsDir -Filter "Artist-Haven-v*-b*-*-debug.apk" |
    Sort-Object LastWriteTime -Descending
if ($allVersioned.Count -gt $Keep) {
    $allVersioned | Select-Object -Skip $Keep | Remove-Item -Force
}


Write-Host "Published APK: $versionedApkPath"
Write-Host "Legacy APK:    $legacyApkPath"
Write-Host "latest.json:   $latestJsonPath"
Write-Host "SHA256:        $hash"

