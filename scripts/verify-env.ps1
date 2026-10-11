$ErrorActionPreference = "Continue"
Write-Output "java $(java -version 2>&1 | Select-Object -First 1)"
Write-Output "node $(node -v)"
Write-Output "docker $(docker version --format '{{.Server.Version}}')"
$ports = 4200,13306,15000,13001,18080,18181,18082,18083,18084,18085,18086,18087,19090
foreach ($p in $ports) {
  $busy = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
  if ($busy) { "BUSY $p" } else { "FREE $p" }
}
