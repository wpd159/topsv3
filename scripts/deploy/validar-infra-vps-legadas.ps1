Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "INFRA_LEGACY_VPS_CONTRACT=FAIL"
  exit 1
}

function Read-RepoFile {
  param([string]$Path)
  $fullPath = Join-Path $repoRoot ($Path -replace '/', [IO.Path]::DirectorySeparatorChar)
  if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
    throw "Arquivo obrigatorio ausente: $Path"
  }
  return [IO.File]::ReadAllText($fullPath, [Text.UTF8Encoding]::new($false, $true))
}

function Repo-Path {
  param([string]$Path)
  return Join-Path $repoRoot ($Path -replace '/', [IO.Path]::DirectorySeparatorChar)
}

$ci = Read-RepoFile ".github/workflows/ci.yml"
$production = Read-RepoFile ".github/workflows/deploy-production.yml"
$failures = [Collections.Generic.List[string]]::new()

function Assert-Contract {
  param([bool]$Condition, [string]$Message)
  if (-not $Condition) {
    $failures.Add($Message)
  }
}

Assert-Contract (-not (Test-Path -LiteralPath (Repo-Path ".github/workflows/deploy-preprod.yml"))) "workflow de deploy legado ainda existe"
Assert-Contract (-not (Test-Path -LiteralPath (Repo-Path "scripts/deploy/validar-deploy-preprod-local.ps1"))) "validador remoto legado ainda existe"

foreach ($required in @(
    "name: CI Tops do Job V3",
    "pull_request:",
    "mvn --batch-mode --no-transfer-progress verify",
    "npm run test:health-readiness",
    "npm run lint",
    "npm run build",
    "validar-infra-vps-legadas.ps1"
  )) {
  Assert-Contract ($ci.Contains($required)) "CI nao contem $required"
}

foreach ($forbidden in @(
    "PREPROD_",
    "environment: preprod",
    "/opt/topsv3/preprod",
    "v3.esle.cloud",
    " ssh ",
    " scp ",
    " rsync "
  )) {
  Assert-Contract (-not $ci.Contains($forbidden)) "CI contem dependencia remota: $forbidden"
}

foreach ($required in @(
    "environment: production",
    "group: topsv3-production",
    "TOPSDOJOB_PROD_SSH_HOST",
    "TOPSDOJOB_PROD_SSH_PORT",
    "TOPSDOJOB_PROD_SSH_USER",
    "TOPSDOJOB_PROD_SSH_PRIVATE_KEY",
    "TOPSDOJOB_PROD_SSH_HOST_KEY",
    "TOPSDOJOB_PROD_TARGET_IDENTITY_SHA256",
    "/opt/topsv3/identity/production-target",
    "PRODUCTION_TARGET_GUARD=PASS"
  )) {
  Assert-Contract ($production.Contains($required)) "deploy de producao nao contem $required"
}

foreach ($legacySecret in @(
    "secrets.PRODUCTION_HOST",
    "secrets.PRODUCTION_USER",
    "secrets.PRODUCTION_SSH_PORT",
    "secrets.PRODUCTION_SSH_IDENTITY",
    "secrets.PRODUCTION_SSH_HOST_KEY",
    "secrets.VPS_HOST"
  )) {
  Assert-Contract (-not $production.Contains($legacySecret)) "deploy ainda usa secret generico: $legacySecret"
}

$guard = $production.IndexOf("name: Validate canonical production target")
$indexNow = $production.IndexOf("name: Synchronize IndexNow key in production runtime")
$upload = $production.IndexOf("name: Upload immutable release")
Assert-Contract ($guard -ge 0) "target guard ausente"
Assert-Contract ($guard -lt $indexNow) "target guard ocorre depois de mutacao remota"
Assert-Contract ($guard -lt $upload) "target guard ocorre depois do upload"

if ($failures.Count -gt 0) {
  foreach ($failure in $failures) {
    Write-Error $failure
  }
  Write-Host "INFRA_LEGACY_VPS_CONTRACT=FAIL"
  exit 1
}

Write-Host "INFRA_LEGACY_VPS_CONTRACT=PASS"
