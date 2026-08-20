<#
.SYNOPSIS
    Brings DevPilot up on a machine that has only Docker.

.DESCRIPTION
    Creates .env from the template if it is missing, checks that Docker is running, builds the
    images and waits until the backend answers its health check. Everything it does is idempotent,
    so re-running it after a failure is safe.

    Without a DASHSCOPE_API_KEY the stack still starts and every read-only page works; only agent
    runs and knowledge search report that no model is configured. That is deliberate — a missing
    key must look like a missing key, not like a broken build.

.PARAMETER Reset
    Removes the database and vector volumes first, so the demo starts from the seeded state again.

.EXAMPLE
    ./scripts/bootstrap.ps1
    ./scripts/bootstrap.ps1 -Reset
#>
[CmdletBinding()]
param(
    [switch]$Reset
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Write-Step($message) {
    Write-Host "==> $message" -ForegroundColor Cyan
}

Write-Step 'Checking Docker'
try {
    docker info --format '{{.ServerVersion}}' | Out-Null
} catch {
    throw 'Docker is not reachable. Start Docker Desktop and run this script again.'
}

if (-not (Test-Path '.env')) {
    Write-Step 'Creating .env from .env.example'
    Copy-Item '.env.example' '.env'
    Write-Host '    Put your DASHSCOPE_API_KEY in .env to enable agents and knowledge search.' -ForegroundColor Yellow
}

# Compose reads .env itself; this only mirrors it into the current shell so the summary below can
# tell the operator whether a key is present.
$hasKey = (Select-String -Path '.env' -Pattern '^DASHSCOPE_API_KEY=.+' -Quiet)

if ($Reset) {
    Write-Step 'Removing existing containers and volumes'
    docker compose --profile full down -v
}

Write-Step 'Building and starting the stack (first run downloads images and dependencies)'
docker compose --profile full up -d --build

Write-Step 'Waiting for the backend health check'
$deadline = (Get-Date).AddMinutes(5)
$healthy = $false
while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/health' -TimeoutSec 5
        if ($response.code -eq 0) { $healthy = $true; break }
    } catch {
        Start-Sleep -Seconds 5
    }
}

if (-not $healthy) {
    docker compose --profile full logs --tail 50 backend
    throw 'The backend did not become healthy in time. The last 50 log lines are above.'
}

Write-Host ''
Write-Step 'DevPilot is up'
Write-Host '    Frontend : http://localhost:5173'
Write-Host '    Backend  : http://localhost:8080/api/v1/health'
Write-Host '    MySQL    : localhost:3307 (devpilot / devpilot)'
if ($hasKey) {
    Write-Host '    Model    : DASHSCOPE_API_KEY found — agents and knowledge search are enabled.' -ForegroundColor Green
} else {
    Write-Host '    Model    : no DASHSCOPE_API_KEY — read-only pages work, agent runs will refuse.' -ForegroundColor Yellow
}
Write-Host ''
Write-Host '    Stop with:  docker compose --profile full down'
