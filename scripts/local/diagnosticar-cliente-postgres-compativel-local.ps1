param(
  [string]$BackupPath = "C:\topsv3-auditoria-local\backups\topsdojob-db-20260529-163722.dump",
  [string]$ExpectedSha256 = "ca1ab4d33e8ac9f484f9c5cf61589014189407c960d1a296cf4b984817f9cee9",
  [int]$MinimumMajor = 17,
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-29-4/relatorio-cliente-postgres-compativel.md"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

function Resolve-RepoPath {
  param([string]$Path)
  if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
  return (Join-Path $repoRoot ($Path -replace '/', [IO.Path]::DirectorySeparatorChar))
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

function Invoke-NativeCapture {
  param(
    [string]$FileName,
    [string[]]$Arguments
  )
  $previous = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    $output = & $FileName @Arguments 2>&1
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previous
  }
  return [pscustomobject]@{
    ExitCode = $exitCode
    Output = @($output | ForEach-Object { $_.ToString() })
  }
}

function Get-PostgresMajorFromText {
  param([string]$Text)
  if ($Text -match 'PostgreSQL\)\s+(\d+)(?:\.|$)' -or $Text -match 'pg_restore.*\s(\d+)(?:\.|$)') {
    return [int]$Matches[1]
  }
  return $null
}

function Get-PostgresImageMajor {
  param([string]$Image)
  if ($Image -match '^postgres:(\d+)(?:[.\-]|$)') { return [int]$Matches[1] }
  return $null
}

function Write-ClientReport {
  param(
    [string]$Result,
    [string]$Detail,
    [string]$SelectedClient,
    [string]$SelectedVersion,
    [string[]]$LocalImages,
    [string[]]$CheckedClients
  )
  $reportPath = Resolve-RepoPath $RelatorioSaida
  $reportDir = Split-Path -Parent $reportPath
  New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
  $lines = New-Object System.Collections.Generic.List[string]
  $lines.Add("# Relatorio - cliente PostgreSQL compativel")
  $lines.Add("")
  $lines.Add("- Bloco: 29.4")
  $lines.Add("- Resultado: $Result")
  $lines.Add("- Detalhe: $Detail")
  $lines.Add("- Cliente minimo esperado: PostgreSQL $MinimumMajor.x")
  $lines.Add("- Cliente selecionado: $SelectedClient")
  $lines.Add("- Versao selecionada: $SelectedVersion")
  $lines.Add("- Docker pull automatico executado pelo script: nao")
  $lines.Add("- Docker run com pull bloqueado: sim")
  $lines.Add("- Instalacao local executada: nao")
  $lines.Add("- VPS/producao acessada: nao")
  $lines.Add("- Conteudo do backup impresso: nao")
  $lines.Add("")
  $lines.Add("## Imagens locais observadas")
  if ($LocalImages.Count -eq 0) {
    $lines.Add("- Nenhuma imagem Docker PostgreSQL local encontrada.")
  } else {
    foreach ($image in $LocalImages) { $lines.Add("- $image") }
  }
  $lines.Add("")
  $lines.Add("## Clientes testados")
  if ($CheckedClients.Count -eq 0) {
    $lines.Add("- Nenhum cliente compativel pode ser testado.")
  } else {
    foreach ($client in $CheckedClients) { $lines.Add("- $client") }
  }
  if ($Result -eq "PENDENTE_CLIENTE_POSTGRES_COMPATIVEL") {
    $lines.Add("")
    $lines.Add("## Acao manual sugerida")
    $lines.Add("")
    $lines.Add("O comando abaixo nao foi executado automaticamente. Ele so deve ser executado pelo usuario se houver autorizacao consciente para baixar a imagem:")
    $lines.Add("")
    $lines.Add('```powershell')
    $lines.Add("docker pull postgres:17")
    $lines.Add('```')
  }
  [System.IO.File]::WriteAllText($reportPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
  Write-Host "RELATORIO=$reportPath"
}

$backupFull = Get-FullPathNormalized $BackupPath
if (-not (Test-Path -LiteralPath $backupFull -PathType Leaf)) {
  Write-ClientReport -Result "PENDENTE_BACKUP_AUTORIZADO_PRODUCAO" -Detail "Backup autorizado nao encontrado no caminho externo informado." -SelectedClient "nenhum" -SelectedVersion "nao_aplicavel" -LocalImages @() -CheckedClients @()
  Write-Host "CLIENTE_POSTGRES_COMPATIVEL=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_BACKUP_AUTORIZADO_PRODUCAO"
  exit 2
}
if (Test-PathInside -Child $backupFull -Parent $repoRoot) {
  Write-ClientReport -Result "BLOQUEADO_BACKUP_DENTRO_REPOSITORIO" -Detail "BackupPath aponta para dentro do repositorio." -SelectedClient "nenhum" -SelectedVersion "nao_aplicavel" -LocalImages @() -CheckedClients @()
  Write-Host "CLIENTE_POSTGRES_COMPATIVEL=False"
  Write-Host "VALIDATION_RESULT=BLOQUEADO_BACKUP_DENTRO_REPOSITORIO"
  exit 2
}

$actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $backupFull).Hash.ToLowerInvariant()
if ($actualHash -ne $ExpectedSha256.ToLowerInvariant()) {
  Write-ClientReport -Result "BLOQUEADO_BACKUP_SHA256_DIVERGENTE" -Detail "SHA-256 do backup autorizado diverge do valor esperado; restore bloqueado antes de qualquer leitura operacional." -SelectedClient "nenhum" -SelectedVersion "nao_aplicavel" -LocalImages @() -CheckedClients @()
  Write-Host "CLIENTE_POSTGRES_COMPATIVEL=False"
  Write-Host "VALIDATION_RESULT=BLOQUEADO_BACKUP_SHA256_DIVERGENTE"
  exit 1
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
$localImages = @()
$checked = New-Object System.Collections.Generic.List[string]
if ($docker) {
  $imagesResult = Invoke-NativeCapture -FileName "docker" -Arguments @("image", "ls", "--format", "{{.Repository}}:{{.Tag}}")
  if ($imagesResult.ExitCode -eq 0) {
    $localImages = @($imagesResult.Output | Where-Object { $_ -match '^postgres:' } | Sort-Object -Unique)
  }
}

$candidateImages = New-Object System.Collections.Generic.List[string]
foreach ($preferred in @("postgres:17", "postgres:17-alpine")) {
  if ($localImages -contains $preferred) { [void]$candidateImages.Add($preferred) }
}
foreach ($image in $localImages) {
  $major = Get-PostgresImageMajor $image
  if ($null -ne $major -and $major -ge $MinimumMajor -and -not $candidateImages.Contains($image)) {
    [void]$candidateImages.Add($image)
  }
}

$backupDir = Split-Path -Parent $backupFull
$backupName = Split-Path -Leaf $backupFull
foreach ($image in @($candidateImages.ToArray())) {
  $versionContainer = "topsv3-bloco29-pg17-diagnostico-$([guid]::NewGuid().ToString('N').Substring(0,8))"
  $version = Invoke-NativeCapture -FileName "docker" -Arguments @("run", "--name", $versionContainer, "--pull=never", "--rm", $image, "pg_restore", "--version")
  $versionText = ($version.Output -join " ").Trim()
  $major = Get-PostgresMajorFromText $versionText
  $checked.Add("$image -> $versionText")
  if ($version.ExitCode -ne 0 -or $null -eq $major -or $major -lt $MinimumMajor) { continue }

  $tocContainer = "topsv3-bloco29-pg17-diagnostico-$([guid]::NewGuid().ToString('N').Substring(0,8))"
  $toc = Invoke-NativeCapture -FileName "docker" -Arguments @("run", "--name", $tocContainer, "--pull=never", "--rm", "-v", "${backupDir}:/backup:ro", $image, "pg_restore", "-l", "/backup/$backupName")
  if ($toc.ExitCode -eq 0) {
    Write-ClientReport -Result "OK_CLIENTE_POSTGRES_COMPATIVEL" -Detail "Cliente local aceitou pg_restore -l do backup autorizado sem imprimir conteudo de tabela no relatorio." -SelectedClient $image -SelectedVersion $versionText -LocalImages $localImages -CheckedClients @($checked.ToArray())
    Write-Host "CLIENTE_POSTGRES_COMPATIVEL=True"
    Write-Host "POSTGRES_CLIENTE=$image"
    Write-Host "POSTGRES_CLIENTE_VERSAO=$versionText"
    Write-Host "VALIDATION_RESULT=OK_CLIENTE_POSTGRES_COMPATIVEL"
    exit 0
  }
  $checked.Add("$image -> pg_restore -l rejeitou o backup sem conteudo impresso no relatorio")
}

$localPgRestore = Get-Command pg_restore -ErrorAction SilentlyContinue
if ($localPgRestore) {
  $version = Invoke-NativeCapture -FileName $localPgRestore.Source -Arguments @("--version")
  $versionText = ($version.Output -join " ").Trim()
  $major = Get-PostgresMajorFromText $versionText
  $checked.Add("local:$($localPgRestore.Source) -> $versionText")
  if ($version.ExitCode -eq 0 -and $null -ne $major -and $major -ge $MinimumMajor) {
    $toc = Invoke-NativeCapture -FileName $localPgRestore.Source -Arguments @("-l", $backupFull)
    if ($toc.ExitCode -eq 0) {
      Write-ClientReport -Result "OK_CLIENTE_POSTGRES_COMPATIVEL" -Detail "pg_restore local aceitou pg_restore -l do backup autorizado; para restore completo ainda e recomendado usar imagem PostgreSQL 17.x local isolada." -SelectedClient $localPgRestore.Source -SelectedVersion $versionText -LocalImages $localImages -CheckedClients @($checked.ToArray())
      Write-Host "CLIENTE_POSTGRES_COMPATIVEL=True"
      Write-Host "POSTGRES_CLIENTE=$($localPgRestore.Source)"
      Write-Host "POSTGRES_CLIENTE_VERSAO=$versionText"
      Write-Host "VALIDATION_RESULT=OK_CLIENTE_POSTGRES_COMPATIVEL"
      exit 0
    }
    $checked.Add("local:$($localPgRestore.Source) -> pg_restore -l rejeitou o backup sem conteudo impresso no relatorio")
  }
}

$detail = if ($localImages.Count -eq 0) {
  "Nenhuma imagem PostgreSQL local encontrada. Nao foi feito docker pull."
} elseif (@($localImages | Where-Object { $_ -match '^postgres:16($|[.\-])' }).Count -gt 0) {
  "Apenas cliente/imagem PostgreSQL anterior ao minimo foi encontrado localmente; postgres:16 nao le o dump custom format 1.16."
} else {
  "Nenhum cliente PostgreSQL local aceitou pg_restore -l do backup autorizado."
}
Write-ClientReport -Result "PENDENTE_CLIENTE_POSTGRES_COMPATIVEL" -Detail $detail -SelectedClient "nenhum" -SelectedVersion "nao_aplicavel" -LocalImages $localImages -CheckedClients @($checked.ToArray())
Write-Host "CLIENTE_POSTGRES_COMPATIVEL=False"
Write-Host "VALIDATION_RESULT=PENDENTE_CLIENTE_POSTGRES_COMPATIVEL"
exit 2
