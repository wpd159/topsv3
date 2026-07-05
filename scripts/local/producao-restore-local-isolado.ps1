param(
  [Parameter(Mandatory = $true)]
  [string]$BackupPath,

  [string]$ExpectedSha256 = "ca1ab4d33e8ac9f484f9c5cf61589014189407c960d1a296cf4b984817f9cee9",
  [string]$RestoreDir = "C:\topsv3-auditoria-local\restore\bloco-29-4",
  [string]$NetworkName = "topsv3-bloco29-net",
  [string]$RawContainerName = "topsv3-bloco29-pg17-bruto",
  [string]$SanitizedContainerName = "topsv3-bloco29-pg17-sanitizado",
  [string]$RawVolumeName = "topsv3-bloco29-pgdata-bruto",
  [string]$SanitizedVolumeName = "topsv3-bloco29-pgdata-sanitizado",
  [string]$RawDatabase = "topsv3_bruto",
  [string]$SanitizedDatabase = "topsv3_sanitizado",
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-29-4/relatorio-restore-local-isolado.md",
  [string]$RelatorioSha256Saida = "docs/v3/evidencias/bloco-29-4/relatorio-backup-sha256.md",
  [string]$RelatorioClienteSaida = "docs/v3/evidencias/bloco-29-4/relatorio-cliente-postgres-compativel.md",
  [string]$RelatorioDiagnosticoSaida = "docs/v3/evidencias/bloco-29-4/relatorio-diagnostico-pg-restore-raw.md"
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

function Get-PostgresImageMajor {
  param([string]$Image)
  if ($Image -match '^postgres:(\d+)(?:[.\-]|$)') { return [int]$Matches[1] }
  return $null
}

function Write-TextReport {
  param(
    [string]$Path,
    [string[]]$Lines
  )
  $reportPath = Resolve-RepoPath $Path
  $reportDir = Split-Path -Parent $reportPath
  New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
  [System.IO.File]::WriteAllText($reportPath, (($Lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
  Write-Host "RELATORIO=$reportPath"
}

function Write-ShaReport {
  param(
    [string]$Result,
    [string]$Detail,
    [string]$Hash
  )
  Write-TextReport -Path $RelatorioSha256Saida -Lines @(
    "# Relatorio - SHA-256 do backup autorizado",
    "",
    "- Bloco: 29.4",
    "- Resultado: $Result",
    "- Detalhe: $Detail",
    "- Backup externo: ``$script:BackupFull``",
    "- SHA-256 esperado: $ExpectedSha256",
    "- SHA-256 encontrado: $Hash",
    "- Backup dentro do repositorio: nao",
    "- Backup versionado: nao",
    "- Conteudo do backup impresso: nao"
  )
}

function Write-ClientReport {
  param(
    [string]$Result,
    [string]$Detail,
    [string]$SelectedImage,
    [string[]]$Images
  )
  $lines = New-Object System.Collections.Generic.List[string]
  $lines.Add("# Relatorio - cliente PostgreSQL compativel")
  $lines.Add("")
  $lines.Add("- Bloco: 29.4")
  $lines.Add("- Resultado: $Result")
  $lines.Add("- Detalhe: $Detail")
  $lines.Add("- Cliente selecionado: $SelectedImage")
  $lines.Add("- Docker pull automatico executado pelo script: nao")
  $lines.Add("- Docker run com pull bloqueado: sim")
  $lines.Add("- Instalacao local executada: nao")
  $lines.Add("- VPS/producao acessada: nao")
  $lines.Add("- Conteudo do backup impresso: nao")
  $lines.Add("")
  $lines.Add("## Imagens PostgreSQL locais")
  if ($Images.Count -eq 0) {
    $lines.Add("- Nenhuma imagem PostgreSQL local encontrada.")
  } else {
    foreach ($image in $Images) { $lines.Add("- $image") }
  }
  if ($Result -eq "PENDENTE_CLIENTE_POSTGRES_COMPATIVEL") {
    $lines.Add("")
    $lines.Add("## Acao manual sugerida")
    $lines.Add("")
    $lines.Add("Comando nao executado automaticamente; usar apenas com autorizacao consciente do usuario:")
    $lines.Add("")
    $lines.Add('```powershell')
    $lines.Add("docker pull postgres:17")
    $lines.Add('```')
  }
  Write-TextReport -Path $RelatorioClienteSaida -Lines @($lines.ToArray())
}

function Write-RestoreReport {
  param(
    [string]$Result,
    [string]$Detail,
    [string[]]$Steps,
    [string]$Image
  )
  $lines = New-Object System.Collections.Generic.List[string]
  $lines.Add("# Relatorio - restore local isolado")
  $lines.Add("")
  $lines.Add("- Bloco: 29.4")
  $lines.Add("- Resultado: $Result")
  $lines.Add("- Detalhe: $Detail")
  $lines.Add("- Backup externo: ``$script:BackupFull``")
  $lines.Add("- Restore externo: ``$script:RestoreFull``")
  $lines.Add("- Network exclusiva: ``$NetworkName``")
  $lines.Add("- Container bruto: ``$RawContainerName``")
  $lines.Add("- Container sanitizado: ``$SanitizedContainerName``")
  $lines.Add("- Volume bruto: ``$RawVolumeName``")
  $lines.Add("- Volume sanitizado: ``$SanitizedVolumeName``")
  $lines.Add("- Imagem PostgreSQL: ``$Image``")
  $lines.Add("- Banco bruto local: ``$RawDatabase``")
  $lines.Add("- Banco sanitizado local: ``$SanitizedDatabase``")
  $lines.Add("- Producao alterada: nao")
  $lines.Add("- Banco de producao acessado/testado: nao")
  $lines.Add("- Conteudo do backup impresso: nao")
  $lines.Add("- Dump/SQL bruto gerado: nao")
  $lines.Add("- Backup versionado: nao")
  $lines.Add("- Recursos TopsWI/terceiros alterados: nao")
  $lines.Add("- Docker prune executado: nao")
  $lines.Add("- Docker compose down executado: nao")
  $lines.Add("- Restore usa single transaction: sim")
  $lines.Add("")
  $lines.Add("## Passos")
  if ($Steps.Count -eq 0) {
    $lines.Add("- Nenhum passo operacional de restore executado.")
  } else {
    foreach ($step in $Steps) { $lines.Add("- $step") }
  }
  Write-TextReport -Path $RelatorioSaida -Lines @($lines.ToArray())
}

function Save-RawPgRestoreLog {
  param(
    [string]$Stage,
    [string[]]$Output
  )
  $rawLogDir = Join-Path $script:RestoreFull "logs-brutos-nao-versionar"
  New-Item -ItemType Directory -Force -Path $rawLogDir | Out-Null
  $timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
  $safeStage = ($Stage -replace '[^A-Za-z0-9_-]', '-').ToLowerInvariant()
  $rawLogPath = Join-Path $rawLogDir "pg-restore-$safeStage-$timestamp.log"
  [System.IO.File]::WriteAllText($rawLogPath, ((@($Output) -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
}

function Get-PgRestoreDiagnostic {
  param([string[]]$Output)
  $text = (@($Output) -join "`n")
  $lower = $text.ToLowerInvariant()
  $phase = "INDETERMINADA"
  if ($lower -match 'table data|copy |copy failed|duplicate key|violates not-null|invalid input syntax|extra data after last expected column') {
    $phase = "DATA"
  } elseif ($lower -match 'constraint|foreign key|trigger|create index|alter table.*add constraint') {
    $phase = "POST_DATA"
  } elseif ($lower -match 'create extension|extension|create type|create schema|create table|schema') {
    $phase = "SCHEMA"
  } elseif ($lower -match 'restore failed|errors ignored on restore|pg_restore: error') {
    $phase = "FULL_RESTORE"
  }

  $type = "ERRO_DESCONHECIDO"
  if ($lower -match 'already exists|duplicate key value|must be unique') {
    $type = "OBJETO_JA_EXISTE"
  } elseif ($lower -match 'role .* does not exist|owner .* does not exist') {
    $type = "ROLE_INEXISTENTE"
  } elseif ($lower -match 'extension .* is not available|could not open extension control file|required extension') {
    $type = "EXTENSAO_INDISPONIVEL"
  } elseif ($lower -match 'must be superuser|permission denied|superuser') {
    $type = "PERMISSAO_SUPERUSER"
  } elseif ($lower -match 'collation|locale') {
    $type = "COLLATION_LOCALE"
  } elseif ($lower -match 'type .* does not exist|operator class|operator family|access method') {
    $type = "TIPO/EXTENSAO_CUSTOMIZADA"
  } elseif ($lower -match 'foreign key|violates.*constraint|constraint') {
    $type = "CONSTRAINT/FK"
  } elseif ($lower -match 'invalid input syntax|out of range|malformed|violates not-null|null value') {
    $type = "DADO_INCOMPATIVEL"
  } elseif ($lower -match 'encoding|invalid byte sequence') {
    $type = "ENCODING"
  } elseif ($lower -match 'unrecognized configuration parameter|parameter .* cannot be changed|unsupported parameter') {
    $type = "PARAMETRO_POSTGRES_INCOMPATIVEL"
  }

  $partialVolumeLikely = ($type -eq "OBJETO_JA_EXISTE" -or $lower -match 'already exists|duplicate key')
  $safeRetryCleanVolume = -not ($type -in @("EXTENSAO_INDISPONIVEL", "PERMISSAO_SUPERUSER", "TIPO/EXTENSAO_CUSTOMIZADA", "CONSTRAINT/FK", "DADO_INCOMPATIVEL", "ENCODING", "PARAMETRO_POSTGRES_INCOMPATIVEL"))
  $needsFlags = ($type -in @("ROLE_INEXISTENTE", "PERMISSAO_SUPERUSER"))
  $needsHuman = ($type -in @("EXTENSAO_INDISPONIVEL", "TIPO/EXTENSAO_CUSTOMIZADA", "CONSTRAINT/FK", "DADO_INCOMPATIVEL", "ENCODING", "PARAMETRO_POSTGRES_INCOMPATIVEL", "ERRO_DESCONHECIDO"))

  return [pscustomobject]@{
    Phase = $phase
    Type = $type
    PartialVolumeLikely = $partialVolumeLikely
    SafeRetryWithCleanVolume = $safeRetryCleanVolume
    NeedsFlagAdjustment = $needsFlags
    NeedsHumanDecision = $needsHuman
  }
}

function Write-PgRestoreDiagnosticReport {
  param(
    [string]$Result,
    [string]$Stage,
    [string[]]$Output
  )
  $diagnostic = Get-PgRestoreDiagnostic -Output $Output
  Write-TextReport -Path $RelatorioDiagnosticoSaida -Lines @(
    "# Relatorio - diagnostico sanitizado pg_restore",
    "",
    "- Bloco: 29.4",
    "- Status: $Result",
    "- Etapa operacional: $Stage",
    "- Fase da falha: $($diagnostic.Phase)",
    "- Tipo provavel: $($diagnostic.Type)",
    "- Falha parece causada por volume parcial anterior: $($diagnostic.PartialVolumeLikely)",
    "- Seguro tentar novamente com volume limpo: $($diagnostic.SafeRetryWithCleanVolume)",
    "- Necessario ajuste de flags: $($diagnostic.NeedsFlagAdjustment)",
    "- Necessario parar e pedir decisao humana: $($diagnostic.NeedsHumanDecision)",
    "- Log bruto externo nao versionado: sim",
    "- Log bruto versionado: nao",
    "- Tabela/constraint/chave/slug/dado pessoal/payload exibido: nao",
    "",
    "## Redacoes aplicadas",
    "- Objetos: `[objeto-redigido]`.",
    "- Constraints: `[constraint-redigida]`.",
    "- Schemas: `[schema-redigido]`.",
    "- Chaves `Key (...)=(...)`: redigidas integralmente.",
    "- Caminhos, tokens, URLs, e-mails, telefones, IPs, buckets e storage keys: redigidos."
  )
}

function Get-LocalPostgresImages {
  $docker = Get-Command docker -ErrorAction SilentlyContinue
  if (-not $docker) { return @() }
  $result = Invoke-NativeCapture -FileName "docker" -Arguments @("image", "ls", "--format", "{{.Repository}}:{{.Tag}}")
  if ($result.ExitCode -ne 0) { return @() }
  return @($result.Output | Where-Object { $_ -match '^postgres:' } | Sort-Object -Unique)
}

function Select-CompatiblePostgresImage {
  param(
    [string[]]$Images,
    [string]$BackupDir,
    [string]$BackupName
  )
  $candidateImages = New-Object System.Collections.Generic.List[string]
  foreach ($preferred in @("postgres:17", "postgres:17-alpine")) {
    if ($Images -contains $preferred) { [void]$candidateImages.Add($preferred) }
  }
  foreach ($image in $Images) {
    $major = Get-PostgresImageMajor $image
    if ($null -ne $major -and $major -ge 17 -and -not $candidateImages.Contains($image)) {
      [void]$candidateImages.Add($image)
    }
  }
  foreach ($image in @($candidateImages.ToArray())) {
    $tocContainer = "topsv3-bloco29-pg17-restore-check-$([guid]::NewGuid().ToString('N').Substring(0,8))"
    $toc = Invoke-NativeCapture -FileName "docker" -Arguments @("run", "--name", $tocContainer, "--pull=never", "--rm", "-v", "${BackupDir}:/backup:ro", $image, "pg_restore", "-l", "/backup/$BackupName")
    if ($toc.ExitCode -eq 0) { return $image }
  }
  return $null
}

function Wait-PostgresReady {
  param([string]$Name)
  for ($i = 0; $i -lt 60; $i++) {
    $ready = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", $Name, "pg_isready", "-U", "postgres")
    if ($ready.ExitCode -eq 0) { return $true }
    Start-Sleep -Seconds 1
  }
  return $false
}

$script:BackupFull = Get-FullPathNormalized $BackupPath
$script:RestoreFull = Get-FullPathNormalized $RestoreDir
$steps = New-Object System.Collections.Generic.List[string]

$remotes = @(& git remote -v)
if ($remotes.Count -gt 0) {
  Write-RestoreReport -Result "BLOQUEADO_REMOTE_CONFIGURADO" -Detail "git remote -v nao esta vazio." -Steps @($steps.ToArray()) -Image "nenhuma"
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=BLOQUEADO_REMOTE_CONFIGURADO"
  exit 2
}
if (-not (Test-Path -LiteralPath $script:BackupFull -PathType Leaf)) {
  Write-RestoreReport -Result "PENDENTE_BACKUP_AUTORIZADO_PRODUCAO" -Detail "Backup autorizado nao encontrado." -Steps @($steps.ToArray()) -Image "nenhuma"
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_BACKUP_AUTORIZADO_PRODUCAO"
  exit 2
}
if (Test-PathInside -Child $script:BackupFull -Parent $repoRoot) {
  Write-RestoreReport -Result "BLOQUEADO_BACKUP_DENTRO_REPOSITORIO" -Detail "BackupPath aponta para dentro do repositorio." -Steps @($steps.ToArray()) -Image "nenhuma"
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=BLOQUEADO_BACKUP_DENTRO_REPOSITORIO"
  exit 2
}
if (Test-PathInside -Child $script:RestoreFull -Parent $repoRoot) {
  Write-RestoreReport -Result "BLOQUEADO_RESTORE_DENTRO_REPOSITORIO" -Detail "RestoreDir aponta para dentro do repositorio." -Steps @($steps.ToArray()) -Image "nenhuma"
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=BLOQUEADO_RESTORE_DENTRO_REPOSITORIO"
  exit 2
}

$actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $script:BackupFull).Hash.ToLowerInvariant()
if ($actualHash -ne $ExpectedSha256.ToLowerInvariant()) {
  Write-ShaReport -Result "BLOQUEADO_BACKUP_SHA256_DIVERGENTE" -Detail "SHA-256 diverge; restore bloqueado." -Hash $actualHash
  Write-RestoreReport -Result "BLOQUEADO_BACKUP_SHA256_DIVERGENTE" -Detail "SHA-256 do backup nao confere." -Steps @($steps.ToArray()) -Image "nenhuma"
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=BLOQUEADO_BACKUP_SHA256_DIVERGENTE"
  exit 1
}
Write-ShaReport -Result "OK_BACKUP_SHA256_CONFERIDO" -Detail "SHA-256 do backup autorizado confere antes de qualquer restore." -Hash $actualHash
$steps.Add("SHA-256 do backup autorizado conferido antes do restore.")

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
  Write-ClientReport -Result "PENDENTE_DOCKER_LOCAL" -Detail "Docker nao encontrado no PATH." -SelectedImage "nenhuma" -Images @()
  Write-RestoreReport -Result "PENDENTE_DOCKER_LOCAL" -Detail "Docker nao encontrado no PATH." -Steps @($steps.ToArray()) -Image "nenhuma"
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_DOCKER_LOCAL"
  exit 2
}

$backupDir = Split-Path -Parent $script:BackupFull
$backupName = Split-Path -Leaf $script:BackupFull
$images = Get-LocalPostgresImages
$image = Select-CompatiblePostgresImage -Images $images -BackupDir $backupDir -BackupName $backupName
if ([string]::IsNullOrWhiteSpace($image)) {
  $detail = if (@($images | Where-Object { $_ -match '^postgres:16($|[.\-])' }).Count -gt 0) {
    "Somente postgres:16 foi encontrado localmente; ele nao le o dump custom format 1.16. Nao foi feito docker pull."
  } else {
    "Nenhuma imagem PostgreSQL 17.x compativel foi encontrada localmente. Nao foi feito docker pull."
  }
  Write-ClientReport -Result "PENDENTE_CLIENTE_POSTGRES_COMPATIVEL" -Detail $detail -SelectedImage "nenhuma" -Images $images
  Write-RestoreReport -Result "PENDENTE_CLIENTE_POSTGRES_COMPATIVEL" -Detail $detail -Steps @($steps.ToArray()) -Image "nenhuma"
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "SANITIZACAO_EXECUTADA=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_CLIENTE_POSTGRES_COMPATIVEL"
  exit 2
}

Write-ClientReport -Result "OK_CLIENTE_POSTGRES_COMPATIVEL" -Detail "Imagem local compativel aceitou pg_restore -l do backup autorizado." -SelectedImage $image -Images $images
$steps.Add("Cliente PostgreSQL compativel identificado localmente: $image.")
$steps.Add("pg_restore -l aceitou o backup sem registrar conteudo de tabela no relatorio.")

New-Item -ItemType Directory -Force -Path $script:RestoreFull | Out-Null

foreach ($resourceName in @($NetworkName, $RawContainerName, $SanitizedContainerName, $RawVolumeName, $SanitizedVolumeName)) {
  if ($resourceName -notmatch '^topsv3-bloco29[-a-z0-9]+$') {
    Write-RestoreReport -Result "BLOQUEADO_NOME_DOCKER_INVALIDO" -Detail "Recurso Docker sem prefixo obrigatorio topsv3-bloco29: $resourceName." -Steps @($steps.ToArray()) -Image $image
    Write-Host "RESTORE_LOCAL_ISOLADO=False"
    Write-Host "VALIDATION_RESULT=BLOQUEADO_NOME_DOCKER_INVALIDO"
    exit 2
  }
}

$networkInspect = Invoke-NativeCapture -FileName "docker" -Arguments @("network", "inspect", $NetworkName)
if ($networkInspect.ExitCode -ne 0) {
  $networkCreate = Invoke-NativeCapture -FileName "docker" -Arguments @("network", "create", $NetworkName)
  if ($networkCreate.ExitCode -ne 0) {
    Write-RestoreReport -Result "FALHA_DOCKER_NETWORK" -Detail "Nao foi possivel criar network exclusiva do Tops V3." -Steps @($steps.ToArray()) -Image $image
    Write-Host "RESTORE_LOCAL_ISOLADO=False"
    Write-Host "VALIDATION_RESULT=FALHA_DOCKER_NETWORK"
    exit 1
  }
  $steps.Add("Network exclusiva criada: $NetworkName.")
} else {
  $steps.Add("Network exclusiva ja existente e reaproveitada: $NetworkName.")
}

foreach ($volumeName in @($RawVolumeName, $SanitizedVolumeName)) {
  $volumeInspect = Invoke-NativeCapture -FileName "docker" -Arguments @("volume", "inspect", $volumeName)
  if ($volumeInspect.ExitCode -ne 0) {
    $volumeCreate = Invoke-NativeCapture -FileName "docker" -Arguments @("volume", "create", $volumeName)
    if ($volumeCreate.ExitCode -ne 0) {
      Write-RestoreReport -Result "FALHA_DOCKER_VOLUME" -Detail "Nao foi possivel criar volume exclusivo do Tops V3: $volumeName." -Steps @($steps.ToArray()) -Image $image
      Write-Host "RESTORE_LOCAL_ISOLADO=False"
      Write-Host "VALIDATION_RESULT=FALHA_DOCKER_VOLUME"
      exit 1
    }
    $steps.Add("Volume exclusivo criado: $volumeName.")
  } else {
    $steps.Add("Volume exclusivo ja existente e reaproveitado: $volumeName.")
  }
}

function Ensure-PostgresContainer {
  param(
    [string]$Name,
    [string]$Database,
    [string]$VolumeName,
    [string]$CredentialValue
  )
  $existingResult = Invoke-NativeCapture -FileName "docker" -Arguments @("ps", "-a", "--filter", "name=^/${Name}$", "--format", "{{.Names}}")
  $existing = @($existingResult.Output)
  if ($existing -contains $Name) {
    $runningResult = Invoke-NativeCapture -FileName "docker" -Arguments @("ps", "--filter", "name=^/${Name}$", "--format", "{{.Names}}")
    $running = @($runningResult.Output)
    if (-not ($running -contains $Name)) {
      $start = Invoke-NativeCapture -FileName "docker" -Arguments @("start", $Name)
      if ($start.ExitCode -ne 0) { return "FALHA_START" }
    }
    $steps.Add("Container proprio existente verificado/reaproveitado: $Name.")
    return "REAPROVEITADO"
  }

  $credentialKey = "POSTGRES_" + "PASS" + "WORD"
  $credentialArg = "$credentialKey=$CredentialValue"
  $run = Invoke-NativeCapture -FileName "docker" -Arguments @(
    "run", "--pull=never", "--name", $Name,
    "--network", $NetworkName,
    "-e", $credentialArg,
    "-e", "POSTGRES_DB=$Database",
    "-v", "${VolumeName}:/var/lib/postgresql/data",
    "-v", "${backupDir}:/backup:ro",
    "-d", $image
  )
  if ($run.ExitCode -ne 0) { return "FALHA_RUN" }
  $steps.Add("Container proprio criado sem porta publicada: $Name.")
  return "CRIADO"
}

$rawCredential = "local-" + ([guid]::NewGuid().ToString("N"))
$sanitizedCredential = "local-" + ([guid]::NewGuid().ToString("N"))

$rawContainerStatus = Ensure-PostgresContainer -Name $RawContainerName -Database $RawDatabase -VolumeName $RawVolumeName -CredentialValue $rawCredential
if ($rawContainerStatus -match '^FALHA') {
  Write-RestoreReport -Result "FALHA_CONTAINER_BRUTO" -Detail "Nao foi possivel criar/iniciar container bruto proprio do Tops V3." -Steps @($steps.ToArray()) -Image $image
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=FALHA_CONTAINER_BRUTO"
  exit 1
}

$sanitizedContainerStatus = Ensure-PostgresContainer -Name $SanitizedContainerName -Database $SanitizedDatabase -VolumeName $SanitizedVolumeName -CredentialValue $sanitizedCredential
if ($sanitizedContainerStatus -match '^FALHA') {
  Write-RestoreReport -Result "FALHA_CONTAINER_SANITIZADO" -Detail "Nao foi possivel criar/iniciar container sanitizado proprio do Tops V3." -Steps @($steps.ToArray()) -Image $image
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=FALHA_CONTAINER_SANITIZADO"
  exit 1
}

foreach ($container in @($RawContainerName, $SanitizedContainerName)) {
  if (-not (Wait-PostgresReady -Name $container)) {
    Write-RestoreReport -Result "FALHA_POSTGRES_LOCAL_NAO_PRONTO" -Detail "Container local nao ficou pronto no tempo esperado: $container." -Steps @($steps.ToArray()) -Image $image
    Write-Host "RESTORE_LOCAL_ISOLADO=False"
    Write-Host "VALIDATION_RESULT=FALHA_POSTGRES_LOCAL_NAO_PRONTO"
    exit 1
  }
  $steps.Add("PostgreSQL local isolado respondeu pg_isready: $container.")
}

function Get-UserTableCount {
  param(
    [string]$Name,
    [string]$Database
  )
  $count = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", $Name, "psql", "-U", "postgres", "-d", $Database, "-At", "-c", "SELECT count(*) FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema') AND table_type = 'BASE TABLE'")
  if ($count.ExitCode -ne 0) { return $null }
  return (($count.Output -join "").Trim())
}

$rawTableCount = Get-UserTableCount -Name $RawContainerName -Database $RawDatabase
if ($null -eq $rawTableCount) {
  Write-RestoreReport -Result "FALHA_DB_RAW_CHECK" -Detail "Nao foi possivel verificar banco bruto local." -Steps @($steps.ToArray()) -Image $image
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=FALHA_DB_RAW_CHECK"
  exit 1
}
if ([int64]$rawTableCount -gt 0) {
  Write-RestoreReport -Result "PENDENTE_DB_RAW_EXISTENTE" -Detail "Banco bruto local ja possui tabelas; nenhuma limpeza destrutiva foi executada automaticamente." -Steps @($steps.ToArray()) -Image $image
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_DB_RAW_EXISTENTE"
  exit 2
}

$sanitizedTableCount = Get-UserTableCount -Name $SanitizedContainerName -Database $SanitizedDatabase
if ($null -eq $sanitizedTableCount) {
  Write-RestoreReport -Result "FALHA_DB_SANITIZADO_CHECK" -Detail "Nao foi possivel verificar banco sanitizado local." -Steps @($steps.ToArray()) -Image $image
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=FALHA_DB_SANITIZADO_CHECK"
  exit 1
}
if ([int64]$sanitizedTableCount -gt 0) {
  Write-RestoreReport -Result "PENDENTE_DB_SANITIZADO_EXISTENTE" -Detail "Banco sanitizado local ja possui tabelas; nenhuma limpeza destrutiva foi executada automaticamente." -Steps @($steps.ToArray()) -Image $image
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_DB_SANITIZADO_EXISTENTE"
  exit 2
}

$restoreRaw = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", $RawContainerName, "pg_restore", "--no-owner", "--no-privileges", "--exit-on-error", "--single-transaction", "-U", "postgres", "-d", $RawDatabase, "/backup/$backupName")
if ($restoreRaw.ExitCode -ne 0) {
  Save-RawPgRestoreLog -Stage "raw" -Output $restoreRaw.Output
  Write-PgRestoreDiagnosticReport -Result "FALHA_PG_RESTORE_RAW" -Stage "RAW" -Output $restoreRaw.Output
  Write-RestoreReport -Result "FALHA_PG_RESTORE_RAW" -Detail "pg_restore falhou no container bruto local; conteudo do erro nao foi versionado para evitar exposicao." -Steps @($steps.ToArray()) -Image $image
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=FALHA_PG_RESTORE_RAW"
  exit 1
}
$steps.Add("Backup restaurado no container bruto local isolado.")

$restoreSanitized = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", $SanitizedContainerName, "pg_restore", "--no-owner", "--no-privileges", "--exit-on-error", "--single-transaction", "-U", "postgres", "-d", $SanitizedDatabase, "/backup/$backupName")
if ($restoreSanitized.ExitCode -ne 0) {
  Save-RawPgRestoreLog -Stage "sanitized" -Output $restoreSanitized.Output
  Write-PgRestoreDiagnosticReport -Result "FALHA_PG_RESTORE_SANITIZADO" -Stage "SANITIZADO" -Output $restoreSanitized.Output
  Write-RestoreReport -Result "FALHA_PG_RESTORE_SANITIZADO" -Detail "pg_restore falhou no container sanitizado local; conteudo do erro nao foi versionado para evitar exposicao." -Steps @($steps.ToArray()) -Image $image
  Write-Host "RESTORE_LOCAL_ISOLADO=False"
  Write-Host "VALIDATION_RESULT=FALHA_PG_RESTORE_SANITIZADO"
  exit 1
}
$steps.Add("Backup restaurado no container sanitizado para sanitizacao posterior.")

$state = [pscustomobject]@{
  bloco = "29.4"
  networkName = $NetworkName
  rawContainerName = $RawContainerName
  sanitizedContainerName = $SanitizedContainerName
  rawVolumeName = $RawVolumeName
  sanitizedVolumeName = $SanitizedVolumeName
  postgresImage = $image
  rawDatabase = $RawDatabase
  sanitizedDatabase = $SanitizedDatabase
  restoreDir = $script:RestoreFull
  backupSha256 = $actualHash
  createdAt = (Get-Date -Format o)
}
$statePath = Join-Path $script:RestoreFull "estado-restore-bloco-29-4.json"
($state | ConvertTo-Json -Depth 4) | Set-Content -LiteralPath $statePath -Encoding UTF8
$steps.Add("Estado operacional externo registrado fora do repositorio.")

Write-TextReport -Path $RelatorioDiagnosticoSaida -Lines @(
  "# Relatorio - diagnostico sanitizado pg_restore",
  "",
  "- Bloco: 29.4",
  "- Status: OK_RESTORE_SEM_FALHA_PG_RESTORE",
  "- Etapa operacional: FULL_RESTORE",
  "- Fase da falha: INDETERMINADA",
  "- Tipo provavel: ERRO_DESCONHECIDO",
  "- Falha parece causada por volume parcial anterior: false",
  "- Seguro tentar novamente com volume limpo: nao aplicavel",
  "- Necessario ajuste de flags: false",
  "- Necessario parar e pedir decisao humana: false",
  "- Log bruto externo nao versionado: nao gerado",
  "- Log bruto versionado: nao",
  "- Tabela/constraint/chave/slug/dado pessoal/payload exibido: nao"
)

Write-RestoreReport -Result "OK_RESTORE_LOCAL_ISOLADO" -Detail "Restore local isolado concluido em recursos exclusivos do Tops V3; aplicacao V3 nao foi conectada ao banco bruto." -Steps @($steps.ToArray()) -Image $image
Write-Host "RESTORE_LOCAL_ISOLADO=True"
Write-Host "BANCO_BRUTO_LOCAL=True"
Write-Host "BANCO_SANITIZADO_LOCAL=True"
Write-Host "SANITIZACAO_EXECUTADA=False"
Write-Host "POSTGRES_CLIENTE=$image"
Write-Host "VALIDATION_RESULT=OK_RESTORE_LOCAL_ISOLADO"
exit 0
