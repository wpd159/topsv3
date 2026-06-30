param(
  [string]$DiretorioRepositorio,
  [string]$DiretorioDestino,
  [string]$NomeArquivo = "INVENTARIO-INICIAL.csv"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$scriptsRoot = Split-Path -Parent $scriptDir
. (Join-Path (Join-Path $scriptsRoot "security") "git-staged-utils.ps1")

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
  return ($childFull.Equals($parentFull, [System.StringComparison]::OrdinalIgnoreCase) -or $childFull.StartsWith($parentFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase))
}

if ($DiretorioRepositorio) { Set-Location -LiteralPath $DiretorioRepositorio }
$repoRoot = Get-TopsRepoRoot
Set-Location -LiteralPath $repoRoot

if ([string]::IsNullOrWhiteSpace($DiretorioDestino)) {
  $DiretorioDestino = Join-Path $env:TEMP ("topsv3-inventario-inicial-" + (Get-Date -Format "yyyy-MM-dd-HHmmss-fff"))
}
New-Item -ItemType Directory -Path $DiretorioDestino -Force | Out-Null
if (Test-PathInside -Child $DiretorioDestino -Parent $repoRoot) {
  Exit-TopsOperational "DiretorioDestino do inventario nao pode ficar dentro do repositorio."
}

if ([string]::IsNullOrWhiteSpace($NomeArquivo) -or $NomeArquivo.Contains("\") -or $NomeArquivo.Contains("/") -or $NomeArquivo -match '^[A-Za-z]:') {
  Exit-TopsOperational "NomeArquivo do inventario invalido."
}

$inventoryPath = Join-Path $DiretorioDestino $NomeArquivo
$allFiles = @(Get-TopsVersionableFiles -RepoRoot $repoRoot | Sort-Object)
$cached = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::Ordinal)
foreach ($item in @(Get-TopsCachedFiles -RepoRoot $repoRoot)) {
  [void]$cached.Add((Assert-TopsSafeRelativePath -Path $item -Context "arquivo no indice"))
}
$indexMap = Get-TopsIndexEntryMap -RepoRoot $repoRoot

$seen = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::Ordinal)
$seenCase = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::OrdinalIgnoreCase)
$rows = New-Object System.Collections.Generic.List[object]

foreach ($file in $allFiles) {
  $rel = Assert-TopsSafeRelativePath -Path $file -Context "arquivo inventariado"
  if (-not $seen.Add($rel)) { Exit-TopsOperational "caminho duplicado no inventario inicial: $rel" }
  if (-not $seenCase.Add($rel)) { Exit-TopsOperational "caminho duplicado por caixa no inventario inicial: $rel" }

  $workspacePath = Join-Path $repoRoot ($rel -replace '/', [IO.Path]::DirectorySeparatorChar)
  if (-not (Test-PathInside -Child $workspacePath -Parent $repoRoot)) {
    Exit-TopsOperational "arquivo inventariado fora da raiz: $rel"
  }

  $workspacePresent = Test-Path -LiteralPath $workspacePath -PathType Leaf
  $workspaceSize = ""
  $workspaceHash = ""
  if ($workspacePresent) {
    $workspaceBytes = [System.IO.File]::ReadAllBytes($workspacePath)
    $workspaceSize = [string]$workspaceBytes.Length
    $workspaceHash = Get-TopsSha256Bytes $workspaceBytes
  }

  $indexPresent = $cached.Contains($rel)
  $indexMode = ""
  $indexSize = ""
  $indexHash = ""
  if ($indexPresent) {
    if (-not $indexMap.ContainsKey($rel)) { Exit-TopsOperational "arquivo inventariado sem entrada no indice: $rel" }
    $indexMode = $indexMap[$rel].Mode
    Assert-TopsAllowedGitMode -Path $rel -Mode $indexMode
    $indexBytes = Get-TopsIndexBytes -RepoRoot $repoRoot -Path $rel
    $indexSize = [string]$indexBytes.Length
    $indexHash = Get-TopsSha256Bytes $indexBytes
  }

  $rows.Add([pscustomobject]@{
    caminho_relativo = $rel
    arquivo_presente_no_workspace = [string]$workspacePresent
    arquivo_presente_no_indice = [string]$indexPresent
    modo_git_indice = $indexMode
    tamanho_workspace = $workspaceSize
    tamanho_indice = $indexSize
    sha256_workspace = $workspaceHash
    sha256_indice = $indexHash
  })
}

Write-TopsUtf8BomCsv -Path $inventoryPath -Rows @($rows.ToArray())
Write-Output "INVENTARIO_INICIAL=$inventoryPath"
Write-Output "TOTAL_ARQUIVOS=$($rows.Count)"
