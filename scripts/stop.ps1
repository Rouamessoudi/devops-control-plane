# Arrête les conteneurs de DevOps Control Plane. Ne supprime ni conteneur, ni volume, ni donnée.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Output "Docker n'est pas disponible."
    exit 1
}

$compose = @("-f", "docker-compose.yml")
if (Test-Path "docker-compose.k8s.yml") {
    $compose += @("-f", "docker-compose.k8s.yml")
}
docker compose @compose stop
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$kind = docker inspect -f "{{.State.Status}}" dcp-control-plane 2>$null
if ($LASTEXITCODE -eq 0 -and $kind -eq "running") {
    docker stop dcp-control-plane | Out-Null
    Write-Output "Nœud kind arrêté avec docker stop. Le cluster et ses volumes sont conservés."
}

Write-Output "DevOps Control Plane est arrêté. Les volumes Docker et les données MySQL sont conservés."
Write-Output "Relance : scripts\start.ps1"
