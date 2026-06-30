Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "git-staged-utils.ps1")

if ($env:TOPSV3_SIMULAR_ERRO_SCANNER -eq "1") {
  Exit-TopsOperational "erro operacional simulado do scanner."
}

function New-GitleaksResult {
  param(
    [string]$Status,
    [int]$ExitCode,
    [string]$Detalhe
  )
  return [pscustomobject]@{
    Status = $Status
    ExitCode = $ExitCode
    Detalhe = $Detalhe
  }
}

function Invoke-GitleaksScan {
  if ($env:TOPSV3_FORCAR_FALLBACK_GITLEAKS -eq "1") {
    Write-Host "gitleaks PENDENTE: fallback forcado por variavel de ambiente."
    return (New-GitleaksResult -Status "PENDENTE" -ExitCode -1 -Detalhe "fallback forcado")
  }

  if (-not [string]::IsNullOrWhiteSpace($env:TOPSV3_SIMULAR_GITLEAKS_EXITCODE)) {
    $simulatedCode = 0
    if (-not [int]::TryParse($env:TOPSV3_SIMULAR_GITLEAKS_EXITCODE, [ref]$simulatedCode)) {
      return (New-GitleaksResult -Status "ERRO" -ExitCode 2 -Detalhe "simulacao invalida")
    }
    Write-Host "gitleaks simulado com exit code $simulatedCode."
    if ($simulatedCode -eq 0) { return (New-GitleaksResult -Status "OK" -ExitCode 0 -Detalhe "simulado") }
    if ($simulatedCode -eq 1) { return (New-GitleaksResult -Status "ACHADO" -ExitCode 1 -Detalhe "simulado") }
    return (New-GitleaksResult -Status "ERRO" -ExitCode $simulatedCode -Detalhe "simulado")
  }

  $cmd = Get-Command gitleaks -ErrorAction SilentlyContinue
  if (-not $cmd) {
    Write-Host "gitleaks PENDENTE: binário não encontrado no PATH."
    return (New-GitleaksResult -Status "PENDENTE" -ExitCode -1 -Detalhe "nao instalado")
  }

  $version = (& gitleaks version 2>$null)
  Write-Host "gitleaks encontrado: $version"
  & gitleaks git --help *> $null
  if ($LASTEXITCODE -ne 0) {
    return (New-GitleaksResult -Status "ERRO" -ExitCode $LASTEXITCODE -Detalhe "subcomando git indisponivel")
  }

  Write-Host "Executando: gitleaks git --pre-commit --redact --staged --no-banner"
  & gitleaks git --pre-commit --redact --staged --no-banner
  $code = $LASTEXITCODE
  if ($code -eq 0) { return (New-GitleaksResult -Status "OK" -ExitCode 0 -Detalhe "limpo") }
  if ($code -eq 1) { return (New-GitleaksResult -Status "ACHADO" -ExitCode 1 -Detalhe "achado") }
  return (New-GitleaksResult -Status "ERRO" -ExitCode $code -Detalhe "erro operacional")
}

$repoRoot = Get-TopsRepoRoot
Set-Location -LiteralPath $repoRoot

$gitleaksResult = Invoke-GitleaksScan
Write-Host "Executando verificacao local conservadora de secrets."

$files = @(Get-TopsStagedFiles -RepoRoot $repoRoot)
if ($files.Count -eq 0) {
  Write-Host "Nenhum arquivo staged para scan de secrets."
  if ($gitleaksResult.Status -eq "ACHADO") { exit 1 }
  if ($gitleaksResult.Status -eq "ERRO") { Exit-TopsOperational "erro operacional do gitleaks (exit code $($gitleaksResult.ExitCode)): $($gitleaksResult.Detalhe)." }
  exit 0
}

$indexMap = Get-TopsIndexEntryMap -RepoRoot $repoRoot
$findings = New-Object System.Collections.Generic.List[string]

foreach ($file in $files) {
  $normalized = $file -replace '\\', '/'
  if (-not $indexMap.ContainsKey($normalized)) {
    Exit-TopsOperational "arquivo staged sem entrada no indice: $normalized"
  }

  Assert-TopsAllowedGitMode -Path $normalized -Mode $indexMap[$normalized].Mode
  $size = Get-TopsIndexSize -RepoRoot $repoRoot -Path $normalized
  $bytes = Get-TopsIndexBytes -RepoRoot $repoRoot -Path $normalized

  if ($size -gt 10MB) {
    Add-TopsSecretFinding $findings $normalized 1 "arquivo textual acima de 10 MB deve ser removido"
    continue
  }

  foreach ($finding in @(Get-TopsSecretFindingsForBytes -Path $normalized -Bytes $bytes -Context "blob staged")) {
    $findings.Add($finding)
  }
}

if ($findings.Count -gt 0) {
  Write-Host "Scan local encontrou possiveis secrets. Valores nao foram exibidos:"
  $findings | Sort-Object -Unique | ForEach-Object { Write-Host " - $_" }
  exit 1
}

Write-Host "Scan local de secrets concluido sem achados."
if ($gitleaksResult.Status -eq "ACHADO") {
  Write-Host "gitleaks encontrou possivel segredo."
  exit 1
}
if ($gitleaksResult.Status -eq "ERRO") {
  Exit-TopsOperational "erro operacional do gitleaks (exit code $($gitleaksResult.ExitCode)): $($gitleaksResult.Detalhe)."
}
if ($gitleaksResult.Status -eq "OK") {
  Write-Host "gitleaks concluiu scan limpo e o fallback também foi executado."
} elseif ($gitleaksResult.Status -eq "PENDENTE") {
  Write-Host "gitleaks permaneceu PENDENTE; fallback local executado."
}
exit 0
