param(
    [switch]$Stop,
    [switch]$NoBrowser,
    [switch]$NoDocker,
    [switch]$ForceDocker
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

function Invoke-QuietNative([scriptblock]$block) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        return (& $block 2>&1 | Out-String)
    } finally {
        $ErrorActionPreference = $prev
    }
}

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $Root

$RuntimeDir = Join-Path $Root ".local-runtime"
$PidsFile = Join-Path $RuntimeDir "pids.json"

function Write-Step([string]$msg) {
    Write-Host "[FinSentinel] $msg" -ForegroundColor Cyan
}

function Load-DotEnv([string]$envPath) {
    if (-not (Test-Path $envPath)) { return }
    foreach ($rawLine in Get-Content $envPath) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith("#")) { continue }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { continue }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
            $val = $val.Substring(1, $val.Length - 2)
        }
        # Expand ${VAR} placeholders safely
        $val = [regex]::Replace($val, '\$\{([A-Za-z0-9_]+)\}', {
            param($m)
            $ref = $m.Groups[1].Value
            $envVal = [Environment]::GetEnvironmentVariable($ref, "Process")
            if ($null -ne $envVal) { return $envVal }
            return $m.Value
        })
        [Environment]::SetEnvironmentVariable($key, $val, "Process")
    }
}

function Test-PortOpen([string]$targetHost, [int]$port) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect($targetHost, $port, $null, $null)
        $wait = $iar.AsyncWaitHandle.WaitOne(500, $false)
        if (-not $wait) { $client.Close(); return $false }
        $client.EndConnect($iar)
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

function Stop-TrackedProcesses {
    Write-Step "Stopping FinSentinel AI processes..."
    if (Test-Path $PidsFile) {
        try {
            $pids = Get-Content $PidsFile -Raw | ConvertFrom-Json
            foreach ($prop in @("frontend", "backend", "mailpit", "postgres")) {
                $procId = $pids.$prop
                if ($procId) {
                    $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
                    if ($p) {
                        if ($prop -eq "postgres") {
                            $pgCtl = Get-ChildItem (Join-Path $RuntimeDir "postgres") -Recurse -Filter "pg_ctl.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
                            $pgData = Join-Path $RuntimeDir "pgdata"
                            if ($pgCtl -and (Test-Path $pgData)) {
                                & $pgCtl.FullName -D $pgData stop -m fast | Out-Null
                                continue
                            }
                        }
                        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                        Write-Host "  Stopped $prop (PID $procId)"
                    }
                }
            }
        } catch {}
        Remove-Item $PidsFile -Force -ErrorAction SilentlyContinue
    }
    if ((Get-Command docker -ErrorAction SilentlyContinue)) {
        try {
            docker info *> $null
            if ($LASTEXITCODE -eq 0) {
                docker compose -f (Join-Path $Root "infra\docker-compose.yml") down *> $null
            }
        } catch {}
    }
    Write-Step "Shutdown complete."
}

if ($Stop) {
    Stop-TrackedProcesses
    exit 0
}

# 1. Ensure .env exists and load variables
$EnvFile = Join-Path $Root ".env"
$EnvExample = Join-Path $Root ".env.example"
if (-not (Test-Path $EnvFile) -and (Test-Path $EnvExample)) {
    Copy-Item $EnvExample $EnvFile
    Write-Step "Created .env from .env.example"
}
Load-DotEnv $EnvFile

New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null

# Check Docker fast path if explicitly requested or available & healthy
if (-not $NoDocker -and (Get-Command docker -ErrorAction SilentlyContinue)) {
    $dockerUp = $false
    try { docker info *> $null; if ($LASTEXITCODE -eq 0) { $dockerUp = $true } } catch {}
    if ($dockerUp -or $ForceDocker) {
        Write-Step "Docker daemon detected! Building & starting FinSentinel via Docker Compose..."
        docker compose --env-file $EnvFile -f (Join-Path $Root "infra\docker-compose.yml") up --build -d
        if ($LASTEXITCODE -eq 0) {
            Write-Step "Waiting for Backend (8080) and Frontend (5173)..."
            for ($i = 0; $i -lt 60; $i++) {
                try {
                    $h = Invoke-RestMethod "http://127.0.0.1:8080/actuator/health" -TimeoutSec 2
                    if ($h.status -eq "UP") { break }
                } catch {}
                Start-Sleep -Seconds 2
            }
            if (-not $NoBrowser) { Start-Process "http://localhost:5173" }
            Write-Step "FinSentinel AI is ready at http://localhost:5173 !"
            exit 0
        }
        Write-Warning "Docker Compose startup failed; falling back to zero-Docker portable setup."
    }
}

# 2. Portable Java 21 LTS (downloads Eclipse Temurin 21 if system Java < 21 or missing)
function Ensure-Java21 {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        $verText = Invoke-QuietNative { & java -version }
        if ($verText -match '"(2[1-9]|[3-9]\d)\.') {
            Write-Step "Java 21+ detected."
            return
        }
    }
    $jdkBase = Join-Path $RuntimeDir "jdk21"
    $existingJava = Get-ChildItem $jdkBase -Recurse -Filter "java.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $existingJava) {
        Write-Step "Java 21 not found. Downloading portable Eclipse Temurin JDK 21 (one-time)..."
        New-Item -ItemType Directory -Force -Path $jdkBase | Out-Null
        $zip = Join-Path $RuntimeDir "temurin21.zip"
        $url = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
        Expand-Archive -Path $zip -DestinationPath $jdkBase -Force
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
        $existingJava = Get-ChildItem $jdkBase -Recurse -Filter "java.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    }
    if (-not $existingJava) { throw "Failed to provision Java 21 runtime." }
    $binDir = $existingJava.Directory.FullName
    $homeDir = $existingJava.Directory.Parent.FullName
    $env:JAVA_HOME = $homeDir
    $env:PATH = "$binDir;$env:PATH"
    Write-Step "Using portable Java 21 at $homeDir"
}

# 3. Portable Node.js 22 LTS
function Ensure-Node {
    $nodeCmd = Get-Command node -ErrorAction SilentlyContinue
    $npmCmd = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if ($nodeCmd -and $npmCmd) {
        $ver = (Invoke-QuietNative { & node -v }).Trim()
        if ($ver -match '^v(1[89]|2\d|3\d)\.') {
            Write-Step "Node.js found: $ver"
            return
        }
    }
    $nodeBase = Join-Path $RuntimeDir "node"
    $existingNode = Get-ChildItem $nodeBase -Recurse -Filter "node.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $existingNode) {
        Write-Step "Node.js 18+ not found. Downloading portable Node.js v22.14.0 (one-time)..."
        New-Item -ItemType Directory -Force -Path $nodeBase | Out-Null
        $zip = Join-Path $RuntimeDir "node22.zip"
        Invoke-WebRequest -Uri "https://nodejs.org/dist/v22.14.0/node-v22.14.0-win-x64.zip" -OutFile $zip -UseBasicParsing
        Expand-Archive -Path $zip -DestinationPath $nodeBase -Force
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
        $existingNode = Get-ChildItem $nodeBase -Recurse -Filter "node.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    }
    if (-not $existingNode) { throw "Failed to provision Node.js runtime." }
    $env:PATH = "$($existingNode.Directory.FullName);$env:PATH"
    Write-Step "Using portable Node.js at $($existingNode.Directory.FullName)"
}

# 4. Portable PostgreSQL 16 (unless DATABASE_URL points to Supabase/remote or local Postgres already listening)
$trackedPids = @{}
function Ensure-Postgres {
    $dbUrl = $env:DATABASE_URL
    if ($dbUrl -and ($dbUrl -notmatch 'localhost|127\.0\.0\.1')) {
        Write-Step "Remote DATABASE_URL detected (Supabase/external PostgreSQL); skipping local PostgreSQL startup."
        return
    }
    $pgHost = if ($env:POSTGRES_HOST) { $env:POSTGRES_HOST } else { "127.0.0.1" }
    $pgPort = if ($env:POSTGRES_PORT) { [int]$env:POSTGRES_PORT } else { 5432 }
    $pgDb = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "finsentinel" }
    $pgUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "finsentinel" }
    $pgPass = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "finsentinel_dev_local_change_me" }

    if (Test-PortOpen $pgHost $pgPort) {
        Write-Step "PostgreSQL is already listening on ${pgHost}:${pgPort}."
        return
    }

    $pgDir = Join-Path $RuntimeDir "postgres"
    $pgBin = Join-Path $pgDir "pgsql\bin"
    if (-not (Test-Path (Join-Path $pgBin "postgres.exe"))) {
        Write-Step "PostgreSQL not running locally. Downloading portable PostgreSQL 16.6 (one-time)..."
        New-Item -ItemType Directory -Force -Path $pgDir | Out-Null
        $zip = Join-Path $RuntimeDir "postgres16.zip"
        Invoke-WebRequest -Uri "https://get.enterprisedb.com/postgresql/postgresql-16.6-1-windows-x64-binaries.zip" -OutFile $zip -UseBasicParsing
        Expand-Archive -Path $zip -DestinationPath $pgDir -Force
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
    }

    $pgData = Join-Path $RuntimeDir "pgdata"
    if (-not (Test-Path (Join-Path $pgData "PG_VERSION"))) {
        Write-Step "Initializing local PostgreSQL 16 cluster..."
        Invoke-QuietNative { & (Join-Path $pgBin "initdb.exe") -D $pgData -U postgres -A trust --encoding=UTF8 --locale=C } | Out-Null
    }

    Write-Step "Starting local PostgreSQL 16 on port $pgPort..."
    $pgLog = Join-Path $RuntimeDir "postgres.log"
    $pgProc = Start-Process -FilePath (Join-Path $pgBin "postgres.exe") -ArgumentList "-D `"$pgData`" -p $pgPort" -WindowStyle Hidden -RedirectStandardOutput $pgLog -RedirectStandardError (Join-Path $RuntimeDir "postgres-err.log") -PassThru
    $trackedPids["postgres"] = $pgProc.Id

    for ($i = 0; $i -lt 30; $i++) {
        if (Test-PortOpen "127.0.0.1" $pgPort) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not (Test-PortOpen "127.0.0.1" $pgPort)) { throw "PostgreSQL failed to start on port $pgPort. Check $pgLog" }

    # Ensure application role & database exist
    $psql = Join-Path $pgBin "psql.exe"
    $roleExists = (Invoke-QuietNative { & $psql -h 127.0.0.1 -p $pgPort -U postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='$pgUser'" }).Trim()
    if ($roleExists -ne "1") {
        Invoke-QuietNative { & $psql -h 127.0.0.1 -p $pgPort -U postgres -c "CREATE ROLE $pgUser WITH LOGIN PASSWORD '$pgPass' SUPERUSER;" } | Out-Null
    } else {
        Invoke-QuietNative { & $psql -h 127.0.0.1 -p $pgPort -U postgres -c "ALTER ROLE $pgUser WITH PASSWORD '$pgPass';" } | Out-Null
    }
    $dbExists = (Invoke-QuietNative { & $psql -h 127.0.0.1 -p $pgPort -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$pgDb'" }).Trim()
    if ($dbExists -ne "1") {
        Invoke-QuietNative { & $psql -h 127.0.0.1 -p $pgPort -U postgres -c "CREATE DATABASE $pgDb OWNER $pgUser;" } | Out-Null
    }
    Write-Step "PostgreSQL ready ($pgDb)."
}

# 5. Optional Mailpit for local email verification testing
function Ensure-Mailpit {
    if ($env:MAIL_ENABLED -eq "false") { return }
    $mHost = if ($env:MAIL_HOST) { $env:MAIL_HOST } else { "127.0.0.1" }
    $mPort = if ($env:MAIL_PORT) { [int]$env:MAIL_PORT } else { 1025 }
    if ($mHost -notin @("localhost", "127.0.0.1")) { return }
    if (Test-PortOpen "127.0.0.1" $mPort) { return }

    try {
        $mpDir = Join-Path $RuntimeDir "mailpit"
        $mpExe = Join-Path $mpDir "mailpit.exe"
        if (-not (Test-Path $mpExe)) {
            Write-Step "Downloading lightweight local Mailpit SMTP test inbox..."
            New-Item -ItemType Directory -Force -Path $mpDir | Out-Null
            $zip = Join-Path $RuntimeDir "mailpit.zip"
            Invoke-WebRequest -Uri "https://github.com/axllent/mailpit/releases/download/v1.21.4/mailpit-windows-amd64.zip" -OutFile $zip -UseBasicParsing
            Expand-Archive -Path $zip -DestinationPath $mpDir -Force
            Remove-Item $zip -Force -ErrorAction SilentlyContinue
        }
        if (Test-Path $mpExe) {
            $mpProc = Start-Process -FilePath $mpExe -ArgumentList "--smtp 127.0.0.1:$mPort --listen 127.0.0.1:8025" -WindowStyle Hidden -PassThru
            $trackedPids["mailpit"] = $mpProc.Id
            Write-Step "Mailpit test inbox started at http://127.0.0.1:8025"
        }
    } catch {
        Write-Warning "Optional Mailpit skipped ($($_.Exception.Message)). App continues normally."
    }
}

Ensure-Java21
Ensure-Node
Ensure-Postgres
Ensure-Mailpit

# 6. Install all frontend + backend dependencies cleanly in one go
$frontendDir = Join-Path $Root "frontend"
$backendDir = Join-Path $Root "backend"

if (-not (Test-Path (Join-Path $frontendDir "node_modules\.bin\vite.cmd"))) {
    Write-Step "Installing frontend npm dependencies (first run)..."
    Push-Location $frontendDir
    $prevErr = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & npm.cmd install
        if ($LASTEXITCODE -ne 0) { throw "npm install failed." }
    } finally {
        $ErrorActionPreference = $prevErr
        Pop-Location
    }
}

$jarPath = Join-Path $backendDir "target\finsentinel-backend-1.0.0.jar"
if (-not (Test-Path $jarPath)) {
    Write-Step "Downloading Maven dependencies & packaging Spring Boot backend (first run, please wait)..."
    Push-Location $backendDir
    $prevErr = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & .\mvnw.cmd -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "Maven package failed." }
    } finally {
        $ErrorActionPreference = $prevErr
        Pop-Location
    }
}

# 7. Start Backend (8080) and Frontend (5173) automatically
$backendPort = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 }
if (-not (Test-PortOpen "127.0.0.1" $backendPort)) {
    Write-Step "Starting Spring Boot backend on port $backendPort..."
    $beProc = Start-Process -FilePath "java" -ArgumentList "-jar `"$jarPath`"" -WorkingDirectory $Root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $RuntimeDir "backend.log") -RedirectStandardError (Join-Path $RuntimeDir "backend-err.log") -PassThru
    $trackedPids["backend"] = $beProc.Id
} else {
    Write-Step "Backend port $backendPort is already listening."
}

if (-not (Test-PortOpen "127.0.0.1" 5173)) {
    Write-Step "Starting Vite React frontend on port 5173..."
    $feProc = Start-Process -FilePath "npm.cmd" -ArgumentList "run dev -- --host 127.0.0.1 --port 5173" -WorkingDirectory $frontendDir -WindowStyle Hidden -RedirectStandardOutput (Join-Path $RuntimeDir "frontend.log") -RedirectStandardError (Join-Path $RuntimeDir "frontend-err.log") -PassThru
    $trackedPids["frontend"] = $feProc.Id
} else {
    Write-Step "Frontend port 5173 is already listening."
}

$trackedPids | ConvertTo-Json | Set-Content $PidsFile

Write-Step "Waiting for Backend health check (http://127.0.0.1:${backendPort}/actuator/health)..."
$backendReady = $false
for ($i = 0; $i -lt 90; $i++) {
    try {
        $resp = Invoke-RestMethod "http://127.0.0.1:${backendPort}/actuator/health" -TimeoutSec 2
        if ($resp.status -eq "UP") { $backendReady = $true; break }
    } catch {}
    Start-Sleep -Seconds 2
}
if (-not $backendReady) {
    throw "Backend did not become healthy in time. Inspect $(Join-Path $RuntimeDir 'backend.log') and $(Join-Path $RuntimeDir 'backend-err.log')."
}

Write-Step "Waiting for Frontend (http://127.0.0.1:5173)..."
for ($i = 0; $i -lt 30; $i++) {
    if (Test-PortOpen "127.0.0.1" 5173) { break }
    Start-Sleep -Milliseconds 500
}

Write-Host ""
Write-Host "============================================================================" -ForegroundColor Green
Write-Host "  FinSentinel AI is LIVE!" -ForegroundColor Green
Write-Host "  App URL   : http://127.0.0.1:5173" -ForegroundColor Green
Write-Host "  API Health: http://127.0.0.1:${backendPort}/actuator/health" -ForegroundColor Green
Write-Host "  Swagger   : http://127.0.0.1:${backendPort}/swagger-ui.html" -ForegroundColor Green
Write-Host "  Stop App  : Run STOP_FINSENTINEL.bat" -ForegroundColor Green
Write-Host "============================================================================" -ForegroundColor Green

if (-not $NoBrowser) {
    Start-Process "http://127.0.0.1:5173"
}
