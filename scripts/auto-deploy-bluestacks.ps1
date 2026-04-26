<#
Automate: trigger CI build on GitHub Actions, wait for artifact, download APK, install to BlueStacks via adb.
Requires:
 - PowerShell 7+ recommended
 - A GitHub personal access token with "repo" scope (set environment variable GITHUB_TOKEN or pass as -Token)
 - adb available on PATH

Usage:
  # Ensure token is set in env
  $env:GITHUB_TOKEN = '<your_token>'
  .\scripts\auto-deploy-bluestacks.ps1

#>
param(
    [string]$WorkflowFile = 'android-ci.yml',
    [string]$Branch = 'main',
    [string]$ArtifactName = 'app-debug-apk',
    [string]$BlueStacksHost = '127.0.0.1:5555',
    [int]$PollIntervalSeconds = 6,
    [int]$TimeoutMinutes = 15,
    [string]$Token = $env:GITHUB_TOKEN
)

function Write-Err($msg){ Write-Host $msg -ForegroundColor Red }
function Write-Info($msg){ Write-Host $msg -ForegroundColor Cyan }

if (-not $Token) {
    Write-Err "GITHUB_TOKEN environment variable is not set. Create a token and set GITHUB_TOKEN=<token>."
    exit 2
}

# Determine repo from git remote
$originUrl = (git remote get-url origin) 2>$null
if (-not $originUrl) {
    Write-Err "Unable to determine git origin URL. Run this script from the repo working directory."
    exit 3
}

# parse URL forms: git@github.com:owner/repo.git or https://github.com/owner/repo.git
if ($originUrl -match '^git@github.com:(.+)/(.+?)(\.git)?$') {
    $owner = $matches[1]; $repo = $matches[2]
} elseif ($originUrl -match '^https://github.com/(.+)/(.+?)(\.git)?$') {
    $owner = $matches[1]; $repo = $matches[2]
} else {
    Write-Err "Unsupported origin URL format: $originUrl"
    exit 4
}
$repoFull = "$owner/$repo"
Write-Info "Repo: $repoFull"

$headers = @{
    Authorization = "token $Token"
    Accept = 'application/vnd.github+json'
    'User-Agent' = 'auto-deploy-script'
}

# Trigger workflow dispatch
$dispatchUrl = "https://api.github.com/repos/$repoFull/actions/workflows/$WorkflowFile/dispatches"
$body = @{ ref = $Branch } | ConvertTo-Json
Write-Info "Triggering workflow $WorkflowFile on branch $Branch..."
try {
    Invoke-RestMethod -Uri $dispatchUrl -Method Post -Headers $headers -Body $body
} catch {
    Write-Err "Failed to trigger workflow: $_"
    exit 5
}

# Poll for the most recent run started after now-1minute
$startTime = (Get-Date).ToUniversalTime().AddMinutes(-2)
$runId = $null
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
Write-Info "Waiting for workflow run to start..."
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3
    $runsUrl = "https://api.github.com/repos/$repoFull/actions/runs?event=workflow_dispatch&per_page=10"
    $resp = Invoke-RestMethod -Uri $runsUrl -Headers $headers -Method Get
    if ($null -eq $resp.workflow_runs) { continue }
    foreach ($r in $resp.workflow_runs) {
        $created = [DateTime]::Parse($r.created_at).ToUniversalTime()
        if ($created -ge $startTime) {
            $runId = $r.id
            break
        }
    }
    if ($runId) { break }
}

if (-not $runId) {
    Write-Err "Timed out waiting for a workflow run to be created."
    exit 6
}
Write-Info "Found workflow run id: $runId"

# Poll run status
$runUrl = "https://api.github.com/repos/$repoFull/actions/runs/$runId"
$status = ''
Write-Info "Waiting for workflow run to complete (timeout ${TimeoutMinutes}m)..."
while ((Get-Date) -lt $deadline) {
    $run = Invoke-RestMethod -Uri $runUrl -Headers $headers -Method Get
    $status = $run.status
    $conclusion = $run.conclusion
    Write-Info "Run status: $status, conclusion: $conclusion"
    if ($status -eq 'completed') { break }
    Start-Sleep -Seconds $PollIntervalSeconds
}

if ($status -ne 'completed') {
    Write-Err "Workflow run did not complete in time."
    exit 7
}

if ($conclusion -ne 'success') {
    Write-Err "Workflow finished with conclusion: $conclusion"
    # proceed to attempt artifact download anyway
}

# List artifacts
$artifactsUrl = "https://api.github.com/repos/$repoFull/actions/runs/$runId/artifacts"
$arts = Invoke-RestMethod -Uri $artifactsUrl -Headers $headers -Method Get
$artifact = $arts.artifacts | Where-Object { $_.name -eq $ArtifactName } | Select-Object -First 1
if (-not $artifact) {
    Write-Err "Artifact '$ArtifactName' not found in run $runId"
    exit 8
}
Write-Info "Found artifact id: $($artifact.id), size: $($artifact.size_in_bytes)"

# Download artifact (zip)
$downloadUrl = "https://api.github.com/repos/$repoFull/actions/artifacts/$($artifact.id)/zip"
$tempZip = Join-Path -Path $env:TEMP -ChildPath "artifact_$($artifact.id).zip"
Write-Info "Downloading artifact to $tempZip ..."
Invoke-RestMethod -Uri $downloadUrl -Headers $headers -OutFile $tempZip -Method Get

# Extract
$extractDir = Join-Path -Path $env:TEMP -ChildPath "artifact_$($artifact.id)"
if (Test-Path $extractDir) { Remove-Item $extractDir -Recurse -Force }
New-Item -ItemType Directory -Path $extractDir | Out-Null
Write-Info "Extracting artifact..."
Expand-Archive -Path $tempZip -DestinationPath $extractDir

# Find APK
$apk = Get-ChildItem -Path $extractDir -Recurse -Filter '*.apk' | Select-Object -First 1
if (-not $apk) {
    Write-Err "No APK found inside artifact. Contents:"
    Get-ChildItem -Path $extractDir -Recurse | ForEach-Object { Write-Host $_.FullName }
    exit 9
}
$apkPath = $apk.FullName
Write-Info "APK located: $apkPath"

# Install via adb
Write-Info "Connecting to BlueStacks at $BlueStacksHost"
& adb connect $BlueStacksHost
Start-Sleep -Seconds 1
& adb devices

Write-Info "Installing APK..."
& adb install -r $apkPath
if ($LASTEXITCODE -eq 0) {
    Write-Host "APK installed successfully." -ForegroundColor Green
    exit 0
} else {
    Write-Err "adb install returned exit code $LASTEXITCODE"
    exit 10
}
