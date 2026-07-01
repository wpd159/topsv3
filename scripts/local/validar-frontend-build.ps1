Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

$frontendDir = Join-Path $repoRoot "frontend"
$packageJsonPath = Join-Path $frontendDir "package.json"
$nodeModulesPath = Join-Path $frontendDir "node_modules"
$pending = New-Object System.Collections.Generic.List[string]

function Add-Pending {
  param([string]$Code, [string]$Message)
  $pending.Add($Code)
  Write-Host "${Code}: $Message"
}

Write-Host "Validacao local de build frontend"
Write-Host "Repositorio: $repoRoot"
Write-Host "Politica: sem npm install, sem npm ci e sem download automatico de dependencias."

if (-not (Test-Path -LiteralPath $packageJsonPath -PathType Leaf)) {
  Add-Pending "PENDENTE_FRONTEND_PACKAGE_JSON" "frontend/package.json nao encontrado."
}

$nodeCommand = Get-Command node -ErrorAction SilentlyContinue
if ($null -eq $nodeCommand) {
  Add-Pending "PENDENTE_NODE_LOCAL" "node nao encontrado no PATH."
} else {
  Write-Host "NODE_PATH=$($nodeCommand.Source)"
  Write-Host "NODE_VERSION=$(& $nodeCommand.Source --version)"
}

$npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
if ($null -eq $npmCommand) {
  $npmCommand = Get-Command npm -ErrorAction SilentlyContinue
}
if ($null -eq $npmCommand) {
  Add-Pending "PENDENTE_NPM_LOCAL" "npm nao encontrado no PATH."
} else {
  Write-Host "NPM_PATH=$($npmCommand.Source)"
  Write-Host "NPM_VERSION=$(& $npmCommand.Source --version)"
}

if (-not (Test-Path -LiteralPath $nodeModulesPath -PathType Container)) {
  Add-Pending "PENDENTE_NODE_MODULES_LOCAL" "frontend/node_modules nao existe; npm install e npm ci continuam proibidos sem autorizacao."
}

if ($pending.Count -gt 0) {
  Write-Host "VALIDATION_RESULT=PENDENTE_FRONTEND_BUILD_LOCAL"
  exit 2
}

$package = Get-Content -Raw -LiteralPath $packageJsonPath | ConvertFrom-Json
$availableScripts = @()
foreach ($scriptName in @("lint", "typecheck", "build")) {
  if ($null -ne $package.scripts.PSObject.Properties[$scriptName]) {
    $availableScripts += $scriptName
  } else {
    Write-Host "SCRIPT_NAO_CONFIGURADO=$scriptName"
  }
}

if ($availableScripts.Count -eq 0) {
  Write-Host "Nenhum script lint/typecheck/build configurado em frontend/package.json."
  Write-Host "VALIDATION_RESULT=OK_FRONTEND_BUILD_LOCAL"
  exit 0
}

Push-Location $frontendDir
try {
  foreach ($scriptName in $availableScripts) {
    Write-Host "Executando npm run $scriptName sem instalar dependencias."
    & $npmCommand.Source "run" $scriptName
    if ($LASTEXITCODE -ne 0) {
      Write-Host "VALIDATION_RESULT=FALHA_FRONTEND_BUILD_LOCAL"
      exit 1
    }
  }
} finally {
  Pop-Location
}

Write-Host "VALIDATION_RESULT=OK_FRONTEND_BUILD_LOCAL"
exit 0
