# Applies the Quality Gate only when SonarQube is already answering.
# This script does not start SonarQube and does not print the token.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

try {
    $status = Invoke-WebRequest -Uri "http://127.0.0.1:9000/api/system/status" -UseBasicParsing -TimeoutSec 5
} catch {
    Write-Output "SonarQube ne repond pas sur 127.0.0.1:9000. Aucun Quality Gate n'a ete envoye."
    exit 0
}

if ($status.Content -notmatch '"status"\s*:\s*"UP"') {
    Write-Output "SonarQube a repondu, mais son statut n'est pas UP. Le Quality Gate n'a pas ete modifie."
    exit 0
}

$token = $env:DCP_SONAR_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    $line = Get-Content .env | Where-Object { $_ -like "DCP_SONAR_TOKEN=*" } | Select-Object -First 1
    if ($line) { $token = ($line -replace "^DCP_SONAR_TOKEN=", "").Trim() }
}
if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Output "SonarQube est UP. DCP_SONAR_TOKEN est vide, donc le Quality Gate n'a pas ete modifie."
    exit 0
}

$headers = @{ Authorization = "Bearer $token" }
$name = "DevOps Control Plane"
$encoded = [uri]::EscapeDataString($name)

function Send([string]$path) {
    try {
        Invoke-WebRequest -Uri "http://127.0.0.1:9000$path" -Method Post -Headers $headers -UseBasicParsing -TimeoutSec 20 | Out-Null
        Write-Output "ok"
    } catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        Write-Output "http $code"
    }
}

Write-Output "create $(Send "/api/qualitygates/create?name=$encoded")"
$conditions = @(
    @{ metric = "bugs"; op = "GT"; error = "0" },
    @{ metric = "vulnerabilities"; op = "GT"; error = "0" },
    @{ metric = "code_smells"; op = "GT"; error = "100" },
    @{ metric = "coverage"; op = "LT"; error = "30" },
    @{ metric = "duplicated_lines_density"; op = "GT"; error = "3" }
)
foreach ($condition in $conditions) {
    $path = "/api/qualitygates/create_condition?gateName=$encoded&metric=$($condition.metric)&op=$($condition.op)&error=$($condition.error)"
    Write-Output "$($condition.metric) $(Send $path)"
}
Write-Output "default $(Send "/api/qualitygates/set_as_default?name=$encoded")"
Write-Output "Quality Gate configure. Aucun resultat d'analyse n'est invente par ce script."
