Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "git-staged-utils.ps1")

function Add-Finding {
  param(
    [System.Collections.Generic.List[string]]$Findings,
    [string]$Message
  )
  $Findings.Add($Message)
}

$repoRoot = Get-TopsRepoRoot
Set-Location -LiteralPath $repoRoot

$staged = @(Get-TopsStagedFiles -RepoRoot $repoRoot)
if ($staged.Count -eq 0) {
  Write-Host "Nenhum arquivo staged para verificar."
  exit 0
}

$indexMap = Get-TopsIndexEntryMap -RepoRoot $repoRoot
$errors = New-Object System.Collections.Generic.List[string]
$maxBytes = 10MB

$blockedDirectories = @(
  "storage-local",
  "backups-local",
  "uploads",
  "logs",
  "tmp",
  "dumps",
  "dados-reais",
  "relatorios-reais",
  "exports",
  "exportacoes",
  "node_modules",
  ".next",
  "target",
  ".cache"
)

foreach ($file in $staged) {
  $normalized = $file -replace '\\', '/'
  if (-not $indexMap.ContainsKey($normalized)) {
    Exit-TopsOperational "arquivo staged sem entrada no indice: $normalized"
  }

  Assert-TopsAllowedGitMode -Path $normalized -Mode $indexMap[$normalized].Mode

  $lower = $normalized.ToLowerInvariant()
  $baseName = [IO.Path]::GetFileName($normalized)
  $baseLower = $baseName.ToLowerInvariant()
  $isExampleText = Test-TopsAllowedExampleTextPath $normalized
  $size = Get-TopsIndexSize -RepoRoot $repoRoot -Path $normalized

  if ($baseLower -eq ".env" -or ($baseLower -like ".env.*" -and -not $isExampleText)) {
    Add-Finding $errors "Arquivo de ambiente real bloqueado: $normalized"
  }

  foreach ($dir in $blockedDirectories) {
    if ($lower -eq $dir -or $lower.StartsWith("$dir/") -or $lower.Contains("/$dir/")) {
      Add-Finding $errors "Arquivo em diretorio proibido bloqueado: $normalized"
      break
    }
  }

  if (Test-TopsDangerousExtensionPath $normalized) {
    Add-Finding $errors "Extensao sensivel, archive, dump ou banco bloqueado: $normalized"
  }

  if ($baseLower -like "*.example" -and -not $isExampleText) {
    Add-Finding $errors "Arquivo .example fora da politica textual permitida: $normalized"
  }

  if (-not $isExampleText -and $baseLower -match '^(credentials|credenciais|secrets|segredos|client-secret|private-key|certificado-producao|certificado-prod)(\..+)?$') {
    Add-Finding $errors "Nome completo indica material sensivel real: $normalized"
  }

  if ($lower -match '(^|/)(usuarios|usuários|users)[-_]?(export|dump|dados)|(^|/)(export|dump|dados)[-_]?(usuarios|usuários|users)') {
    Add-Finding $errors "Nome indica possivel exportacao de usuarios: $normalized"
  }

  if ($size -gt $maxBytes) {
    Add-Finding $errors "Arquivo excessivamente grande bloqueado ($([Math]::Round($size / 1MB, 2)) MB): $normalized"
  }
}

if ($errors.Count -gt 0) {
  Write-Host "Verificacao de arquivos proibidos falhou:"
  $errors | Sort-Object -Unique | ForEach-Object { Write-Host " - $_" }
  exit 1
}

Write-Host "Verificacao de arquivos proibidos concluida sem bloqueios."
exit 0
