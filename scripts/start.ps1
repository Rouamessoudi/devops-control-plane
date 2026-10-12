# Démarre DevOps Control Plane sans reconstruire les images et sans supprimer de données.
# L'interface Angular est servie à part si le port 4200 est libre.
param([switch]$Build)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Test-Http([string]$Url) {
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
        return $response.StatusCode
    } catch {
        return 0
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Output "Docker n'est pas disponible. Démarrez Docker Desktop, puis relancez ce script."
    exit 1
}
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Output "Le démon Docker ne répond pas. Démarrez Docker Desktop, puis relancez ce script."
    exit 1
}

$kind = docker inspect -f "{{.State.Running}}" dcp-control-plane 2>$null
if ($LASTEXITCODE -eq 0 -and $kind -eq "false") {
    Write-Output "Démarrage du nœud kind dcp-control-plane."
    docker start dcp-control-plane | Out-Null
}

$token = "infrastructure/monitoring/prometheus/k8s-token"
if (-not (Test-Path $token)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $token) | Out-Null
    Copy-Item "infrastructure/monitoring/prometheus/k8s-token.example" $token
    Write-Output "Jeton Prometheus absent. Un fichier vide a été créé. Les séries Kubernetes resteront vides tant qu'un jeton réel n'est pas écrit."
}

$compose = @("-f", "docker-compose.yml")
if (Test-Path "docker-compose.k8s.yml") {
    $compose += @("-f", "docker-compose.k8s.yml")
}
$up = @("compose") + $compose + @("up", "-d")
if ($Build) { $up += "--build" }
docker @up
if ($LASTEXITCODE -ne 0) {
    Write-Output "Le démarrage Compose a échoué. Aucun volume n'a été supprimé."
    exit $LASTEXITCODE
}

$gateway = 0
foreach ($try in 1..30) {
    $gateway = Test-Http "http://127.0.0.1:18080/actuator/health"
    if ($gateway -eq 200) { break }
    Start-Sleep -Seconds 2
}
if ($gateway -ne 200) {
    Write-Output "La passerelle http://127.0.0.1:18080 ne répond pas encore. Les conteneurs sont lancés ; consultez docker compose ps."
    exit 1
}

$angular = Test-Http "http://127.0.0.1:4200"
if ($angular -eq 0) {
    $ng = Join-Path $root "frontend"
    if (Test-Path (Join-Path $ng "package.json")) {
        Write-Output "Démarrage d'Angular sur http://127.0.0.1:4200."
        Start-Process -FilePath "cmd.exe" -ArgumentList "/c","npx ng serve --host 127.0.0.1 --port 4200" -WorkingDirectory $ng -WindowStyle Hidden
        foreach ($try in 1..30) {
            $angular = Test-Http "http://127.0.0.1:4200"
            if ($angular -ne 0) { break }
            Start-Sleep -Seconds 2
        }
    }
}

Write-Output "Passerelle : http://127.0.0.1:18080 (HTTP $gateway)"
if ($angular -eq 0) {
    Write-Output "Angular n'est pas joignable sur http://127.0.0.1:4200. Lancez npx ng serve dans frontend."
    exit 1
}
Write-Output "Application : http://127.0.0.1:4200"
$ready = kubectl --context kind-dcp get --raw=/readyz 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Output "Kubernetes kind-dcp : $ready"
} else {
    Write-Output "Kubernetes kind-dcp ne répond pas. Les pages Kubernetes afficheront l'indisponibilité du cluster. Les autres pages restent utilisables."
}
