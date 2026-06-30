Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "git-staged-utils.ps1")

if ($env:TOPSV3_SIMULAR_ERRO_CODIFICACAO -eq "1") {
  Exit-TopsOperational "erro operacional simulado do validador de codificacao."
}

$repoRoot = Get-TopsRepoRoot
Set-Location -LiteralPath $repoRoot

$staged = @(Get-TopsStagedFiles -RepoRoot $repoRoot)
if ($staged.Count -eq 0) {
  Write-Host "Nenhum arquivo staged para validar codificacao."
  exit 0
}

$indexMap = Get-TopsIndexEntryMap -RepoRoot $repoRoot
$findings = New-Object System.Collections.Generic.List[string]

foreach ($file in $staged) {
  $normalized = $file -replace '\\', '/'
  if (-not $indexMap.ContainsKey($normalized)) {
    Exit-TopsOperational "arquivo staged sem entrada no indice: $normalized"
  }

  Assert-TopsAllowedGitMode -Path $normalized -Mode $indexMap[$normalized].Mode
  $bytes = Get-TopsIndexBytes -RepoRoot $repoRoot -Path $normalized
  Add-TopsEncodingFindingsForBytes -Findings $findings -Path $normalized -Bytes $bytes
}

if ($findings.Count -gt 0) {
  Write-Host "Validacao de codificacao encontrou problemas:"
  $findings | Sort-Object -Unique | ForEach-Object { Write-Host " - $_" }
  exit 1
}

Write-Host "Validacao de codificacao concluida sem problemas."
exit 0
