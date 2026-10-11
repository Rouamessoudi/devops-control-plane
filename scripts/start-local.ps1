$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
if (-not (Test-Path ".env")) {
  Copy-Item ".env.example" ".env"
  Write-Output "Fichier .env créé depuis .env.example. Remplacez les valeurs dev-only avant un usage partagé."
}
$bundledMaven = Join-Path $root ".tools\apache-maven-3.9.11\bin\mvn.cmd"
$maven = if (Test-Path $bundledMaven) { $bundledMaven } else { "mvn" }
& $maven -B -DskipTests package
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$env:COMPOSE_PARALLEL_LIMIT = "2"
docker compose up -d --build
