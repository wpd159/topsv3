param(
  [string]$BackupDir = "C:\topsv3-auditoria-local\backups",
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-29/relatorio-backup-autorizado.md"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

function Get-FullPathNormalized {
  param([string]$Path)
  return [System.IO.Path]::GetFullPath($Path).TrimEnd('\', '/')
}

function Test-PathInside {
  param(
    [string]$Child,
    [string]$Parent
  )
  $childFull = Get-FullPathNormalized $Child
  $parentFull = Get-FullPathNormalized $Parent
  return (
    $childFull.Equals($parentFull, [System.StringComparison]::OrdinalIgnoreCase) -or
    $childFull.StartsWith($parentFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)
  )
}

function Resolve-RepoPath {
  param([string]$Path)
  if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
  return (Join-Path $repoRoot ($Path -replace '/', [IO.Path]::DirectorySeparatorChar))
}

$backupFull = Get-FullPathNormalized $BackupDir
if (Test-PathInside -Child $backupFull -Parent $repoRoot) {
  Write-Host "ERRO: BackupDir aponta para dentro do repositorio."
  exit 2
}

New-Item -ItemType Directory -Force -Path $backupFull | Out-Null

$allowed = @("*.dump", "*.backup", "*.sql", "*.sql.gz", "*.tar", "*.gz", "*.zip")
$candidates = @(Get-ChildItem -LiteralPath $backupFull -File -Recurse -Include $allowed -ErrorAction SilentlyContinue |
  Where-Object { -not (Test-PathInside -Child $_.FullName -Parent $repoRoot) } |
  Sort-Object LastWriteTime -Descending)

$reportPath = Resolve-RepoPath $RelatorioSaida
$reportDir = Split-Path -Parent $reportPath
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Relatorio - backup autorizado")
$lines.Add("")
$lines.Add("- Bloco: 29")
$lines.Add("- Politica: localizar backup existente, sem gerar dump novo e sem abrir conteudo sensivel.")
$lines.Add("- Diretorio externo consultado: ``$backupFull``")
$lines.Add("- Diretorio dentro do repositorio: nao")
$lines.Add("")

if ($candidates.Count -eq 0) {
  $lines.Add("## Resultado")
  $lines.Add("")
  $lines.Add("PENDENTE_BACKUP_AUTORIZADO_PRODUCAO")
  $lines.Add("")
  $lines.Add("Nenhum backup autorizado foi encontrado no diretorio externo.")
  [System.IO.File]::WriteAllText($reportPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
  Write-Host "BACKUP_AUTORIZADO_LOCALIZADO=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_BACKUP_AUTORIZADO_PRODUCAO"
  exit 2
}

$selected = $candidates[0]
$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $selected.FullName).Hash.ToLowerInvariant()
$extension = [System.IO.Path]::GetExtension($selected.Name).ToLowerInvariant()
$compressed = ($selected.Name -match '\.gz$|\.zip$|\.tar$')
$kind = if ($selected.Name -match '\.dump$|\.backup$') { "postgres-custom-provavel" } elseif ($selected.Name -match '\.sql(\.gz)?$') { "postgres-plain-provavel" } else { "arquivo-compactado-ou-pacote" }

$lines.Add("## Resultado")
$lines.Add("")
$lines.Add("- Backup autorizado localizado: sim")
$lines.Add("- Caminho externo: ``$($selected.FullName)``")
$lines.Add("- Tamanho bytes: $($selected.Length)")
$lines.Add("- Data local do arquivo: $($selected.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss"))")
$lines.Add("- SHA-256: ``$hash``")
$lines.Add("- Tipo inferido: $kind")
$lines.Add("- Extensao: ``$extension``")
$lines.Add("- Comprimido/pacote: $compressed")
$lines.Add("- Conteudo impresso: nao")
$lines.Add("- Backup dentro do repositorio: nao")
$lines.Add("")
$lines.Add("## Observacoes")
$lines.Add("")
$lines.Add("- O arquivo bruto nao deve entrar no Git, ZIP, chat ou relatorio versionado alem deste resumo sanitizado.")
$lines.Add("- Se for pacote com midia/documentos, nao extrair midia/documentos reais.")

[System.IO.File]::WriteAllText($reportPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))

Write-Host "BACKUP_AUTORIZADO_LOCALIZADO=True"
Write-Host "BACKUP_PATH=$($selected.FullName)"
Write-Host "BACKUP_SIZE_BYTES=$($selected.Length)"
Write-Host "BACKUP_SHA256=$hash"
Write-Host "BACKUP_KIND=$kind"
Write-Host "BACKUP_COMPRESSED=$compressed"
Write-Host "RELATORIO=$reportPath"
Write-Host "VALIDATION_RESULT=OK_BACKUP_AUTORIZADO_LOCALIZADO"
exit 0
