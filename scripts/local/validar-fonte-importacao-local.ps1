param(
  [string]$DiretorioPacote
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Ok {
  param([string]$Message)
  Write-Host "OK: $Message"
}

function Write-Warn {
  param([string]$Message)
  Write-Host "ALERTA: $Message"
}

function Exit-Critical {
  param([string]$Message)
  Write-Host "BLOQUEIO: $Message"
  exit 1
}

function Exit-Operational {
  param([string]$Message)
  Write-Host "PENDENCIA_OPERACIONAL: $Message"
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
  return ($childFull.Equals($parentFull, [System.StringComparison]::OrdinalIgnoreCase) -or
    $childFull.StartsWith($parentFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase))
}

function Test-PublicSyncPath {
  param([string]$Path)
  $normalized = (Get-FullPathNormalized $Path).ToLowerInvariant()
  $markers = @(
    '\onedrive\',
    '\dropbox\',
    '\google drive\',
    '\googledrive\',
    '\icloud\',
    '\box\',
    '\public\'
  )
  foreach ($marker in $markers) {
    if ($normalized.Contains($marker)) {
      return $true
    }
  }
  return $false
}

$repoRoot = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Exit-Operational "repositorio Git nao encontrado."
}
$repoRoot = Get-FullPathNormalized $repoRoot.Trim()
$topsRoot = Get-FullPathNormalized "C:\topsv3"

if ([string]::IsNullOrWhiteSpace($DiretorioPacote)) {
  Write-Ok "nenhuma fonte real foi informada; nenhuma fonte real foi validada nesta fase."
  Write-Ok "o script nao le dump, CSV, JSON real, midia, pagamento, metrica, banco ou rede."
  exit 0
}

try {
  if (-not (Test-Path -LiteralPath $DiretorioPacote -PathType Container)) {
    Exit-Operational "diretorio do pacote nao encontrado ou nao e diretorio: $DiretorioPacote"
  }

  $item = Get-Item -LiteralPath $DiretorioPacote -Force
  $packageRoot = Get-FullPathNormalized $item.FullName

  if (Test-PathInside -Child $packageRoot -Parent $topsRoot) {
    Exit-Critical "diretorio do pacote esta dentro de C:\topsv3; fonte real deve ficar fora do workspace."
  }
  if (Test-PathInside -Child $packageRoot -Parent $repoRoot) {
    Exit-Critical "diretorio do pacote esta dentro do repositorio Git; fonte real nao pode ser versionavel."
  }

  if (Test-PublicSyncPath $packageRoot) {
    Write-Warn "diretorio parece estar em pasta sincronizada/publica; confirme protecao operacional antes de uso futuro."
  }

  $entries = @(Get-ChildItem -LiteralPath $packageRoot -Force -ErrorAction Stop)
  $files = @($entries | Where-Object { -not $_.PSIsContainer })
  $directories = @($entries | Where-Object { $_.PSIsContainer })
  $names = @($files | ForEach-Object { $_.Name.ToLowerInvariant() })

  $hasManifest = @($names | Where-Object { $_ -match '(manifesto|manifest|descritor|descriptor)' }).Count -gt 0
  $hasChecksums = @($names | Where-Object { $_ -match '(checksum|checksums|sha256)' }).Count -gt 0

  if (-not $hasManifest) {
    Exit-Critical "pacote informado nao possui manifesto nominal no primeiro nivel."
  }
  if (-not $hasChecksums) {
    Exit-Critical "pacote informado nao possui arquivo nominal de checksums no primeiro nivel."
  }

  Write-Ok "diretorio informado esta fora do repositorio e possui manifesto/checksums nominais."
  Write-Ok "metadados listados sem abrir conteudo: arquivos=$($files.Count); diretorios=$($directories.Count)."
  Write-Ok "nenhum checksum foi calculado e nenhum arquivo de origem foi aberto."
  exit 0
} catch {
  Exit-Operational "falha ao validar metadados do diretorio informado: $($_.Exception.Message)"
}
