param(
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-31/relatorio-e2e-sintetico-local.md",
  [int]$DockerWaitSeconds = 180,
  [int]$BackendWaitSeconds = 120,
  [int]$BackendPort = 18131,
  [switch]$NaoIniciarDockerDesktop
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

$baseScript = Join-Path $repoRoot "scripts/local/validar-e2e-local-descartavel.ps1"
if (-not (Test-Path -LiteralPath $baseScript -PathType Leaf)) {
  Write-Host "VALIDATION_RESULT=PENDENTE_E2E_SINTETICO_LOCAL"
  Write-Host "Motivo: script base de E2E descartavel nao encontrado."
  exit 2
}

$relatorioFull = if ([System.IO.Path]::IsPathRooted($RelatorioSaida)) {
  $RelatorioSaida
} else {
  Join-Path $repoRoot ($RelatorioSaida -replace '/', [IO.Path]::DirectorySeparatorChar)
}

$argsBase = @(
  "-NoProfile",
  "-ExecutionPolicy",
  "Bypass",
  "-File",
  $baseScript,
  "-RelatorioSaida",
  $relatorioFull,
  "-DockerWaitSeconds",
  "$DockerWaitSeconds",
  "-BackendWaitSeconds",
  "$BackendWaitSeconds",
  "-BackendPort",
  "$BackendPort",
  "-ResourcePrefix",
  "topsv3-e2e-sintetico",
  "-ApiSmokeScript",
  "scripts/local/validar-api-publica-sintetica-local.ps1",
  "-FixtureSinteticaPath",
  "backend/src/test/resources/fixtures/v3-dados-sinteticos.json"
)

if ($NaoIniciarDockerDesktop) {
  $argsBase += "-NaoIniciarDockerDesktop"
}

$powershell = (Get-Command powershell -ErrorAction Stop).Source
& $powershell @argsBase
$exit = $LASTEXITCODE
if ($exit -eq 0) {
  Write-Host "VALIDATION_RESULT=OK_E2E_SINTETICO_LOCAL"
} elseif ($exit -eq 1) {
  Write-Host "VALIDATION_RESULT=FALHA_E2E_SINTETICO_LOCAL"
} else {
  Write-Host "VALIDATION_RESULT=PENDENTE_E2E_SINTETICO_LOCAL"
}
exit $exit
