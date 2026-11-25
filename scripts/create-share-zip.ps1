param(
    [switch]$IncludeEnv,
    [string]$OutputZip = "dist-bundle\FinSentinel-AI-Share.zip"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$DestZip = if ([System.IO.Path]::IsPathRooted($OutputZip)) { $OutputZip } else { Join-Path $Root $OutputZip }
$StagingDir = Join-Path $Root "dist-bundle\_staging\FinSentinel-AI"

if (Test-Path (Join-Path $Root "dist-bundle\_staging")) {
    Remove-Item (Join-Path $Root "dist-bundle\_staging") -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $StagingDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path $DestZip -Parent) | Out-Null

# Clean source copy excluding heavy/generated artifacts & .git
$excludeDirs = @(
    ".git", ".local-runtime", "dist-bundle", "node_modules", "target",
    "dist", ".vite", "playwright-report", "test-results"
)

robocopy $Root $StagingDir /E /XD $excludeDirs /XF "*.zip" "*.log" ".env" ".env.local" "maven-wrapper.jar" | Out-Null
if ($LASTEXITCODE -ge 8) { throw "Robocopy staging failed with exit code $LASTEXITCODE" }

if ($IncludeEnv -and (Test-Path (Join-Path $Root ".env"))) {
    Copy-Item (Join-Path $Root ".env") (Join-Path $StagingDir ".env") -Force
    Write-Host "[Bundle] Included local .env inside private share archive." -ForegroundColor Yellow
}

if (Test-Path $DestZip) { Remove-Item $DestZip -Force }
Compress-Archive -Path (Join-Path $StagingDir "*") -DestinationPath $DestZip -CompressionLevel Optimal
Remove-Item (Join-Path $Root "dist-bundle\_staging") -Recurse -Force

$sizeMb = [math]::Round((Get-Item $DestZip).Length / 1MB, 2)
Write-Host "[Bundle] Created $DestZip ($sizeMb MB)" -ForegroundColor Green
