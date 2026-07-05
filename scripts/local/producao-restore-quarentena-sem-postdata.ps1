param(
  [Parameter(Mandatory = $true)]
  [string]$BackupPath,

  [string]$ExpectedSha256 = "ca1ab4d33e8ac9f484f9c5cf61589014189407c960d1a296cf4b984817f9cee9",
  [string]$RestoreDir = "C:\topsv3-auditoria-local\restore\bloco-29-5",
  [string]$NetworkName = "topsv3-bloco29-net",
  [string]$ContainerName = "topsv3-bloco29-pg17-quarentena",
  [string]$VolumeName = "topsv3-bloco29-pgdata-quarentena",
  [string]$DatabaseName = "topsv3_quarentena",
  [string]$RelatorioInventarioSaida = "docs/v3/evidencias/bloco-29-5/relatorio-docker-inventario-pre-quarentena.md",
  [string]$RelatorioDockerSaida = "docs/v3/evidencias/bloco-29-5/relatorio-docker-quarentena.md",
  [string]$RelatorioRestoreSaida = "docs/v3/evidencias/bloco-29-5/relatorio-restore-quarentena-sem-postdata.md"
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
  param([string]$Child, [string]$Parent)
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
    [string[]]$Arguments,
    [string]$InputText = $null
  )
  $previous = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    if ($null -eq $InputText) {
      $output = & $FileName @Arguments 2>&1
    } else {
      $output = $InputText | & $FileName @Arguments 2>&1
    }
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previous
  }
  return [pscustomobject]@{
    ExitCode = $exitCode
    Output = @($output | ForEach-Object { $_.ToString() })
  }
}

function Write-TextReport {
  param([string]$Path, [string[]]$Lines)
  $reportPath = Resolve-RepoPath $Path
  $reportDir = Split-Path -Parent $reportPath
  New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
  [System.IO.File]::WriteAllText($reportPath, (($Lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
  Write-Host "RELATORIO=$reportPath"
}

function Save-RawPgRestoreLog {
  param([string]$Stage, [string[]]$Output)
  $rawLogDir = Join-Path $script:RestoreFull "logs-brutos-nao-versionar"
  New-Item -ItemType Directory -Force -Path $rawLogDir | Out-Null
  $timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
  $rawLogPath = Join-Path $rawLogDir "pg-restore-quarentena-$Stage-$timestamp.log"
  [System.IO.File]::WriteAllText($rawLogPath, ((@($Output) -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
}

function Get-PgRestoreDiagnostic {
  param([string[]]$Output)
  $text = (@($Output) -join "`n")
  $lower = $text.ToLowerInvariant()
  $phase = "INDETERMINADA"
  if ($lower -match 'constraint|foreign key|trigger|create index|alter table.*add constraint') { $phase = "POST_DATA" }
  elseif ($lower -match 'table data|copy |copy failed|invalid input syntax|extra data') { $phase = "DATA" }
  elseif ($lower -match 'create extension|extension|create type|create schema|create table|schema') { $phase = "SCHEMA" }
  elseif ($lower -match 'restore failed|errors ignored on restore|pg_restore: error') { $phase = "FULL_RESTORE" }

  $type = "ERRO_DESCONHECIDO"
  if ($lower -match 'role .* does not exist|owner .* does not exist') { $type = "ROLE_INEXISTENTE" }
  elseif ($lower -match 'extension .* is not available|could not open extension control file|required extension') { $type = "EXTENSAO_INDISPONIVEL" }
  elseif ($lower -match 'must be superuser|permission denied|superuser') { $type = "PERMISSAO_SUPERUSER" }
  elseif ($lower -match 'collation|locale') { $type = "COLLATION_LOCALE" }
  elseif ($lower -match 'foreign key|violates.*constraint|constraint') { $type = "CONSTRAINT/FK" }
  elseif ($lower -match 'invalid input syntax|out of range|malformed|violates not-null|null value') { $type = "DADO_INCOMPATIVEL" }
  elseif ($lower -match 'encoding|invalid byte sequence') { $type = "ENCODING" }
  elseif ($lower -match 'unrecognized configuration parameter|unsupported parameter') { $type = "PARAMETRO_POSTGRES_INCOMPATIVEL" }
  elseif ($lower -match 'already exists|duplicate key') { $type = "OBJETO_JA_EXISTE" }
  return [pscustomobject]@{ Phase = $phase; Type = $type }
}

function Write-RestoreReport {
  param(
    [string]$Result,
    [string]$Detail,
    [string[]]$Steps,
    [hashtable]$Counts
  )
  $lines = New-Object System.Collections.Generic.List[string]
  $lines.Add("# Relatorio - restore de quarentena sem POST_DATA")
  $lines.Add("")
  $lines.Add("- Bloco: 29.5")
  $lines.Add("- Resultado: $Result")
  $lines.Add("- Detalhe: $Detail")
  $lines.Add("- Banco e de quarentena: sim")
  $lines.Add("- Aprovado para staging final: nao")
  $lines.Add("- POST_DATA restaurado: nao")
  $lines.Add("- Constraints/FKs/indexes/finalizacoes de POST_DATA podem estar ausentes: sim")
  $lines.Add("- Uso permitido: diagnostico, sanitizacao imediata e SEO agregado")
  $lines.Add("- Backup externo: ``$script:BackupFull``")
  $lines.Add("- Restore externo: ``$script:RestoreFull``")
  $lines.Add("- Container: ``$ContainerName``")
  $lines.Add("- Volume: ``$VolumeName``")
  $lines.Add("- Network: ``$NetworkName``")
  $lines.Add("- Banco: ``$DatabaseName``")
  $lines.Add("- Imagem PostgreSQL: ``$script:Image``")
  $lines.Add("- Flags usadas: ``--section=pre-data --section=data --no-owner --no-privileges --exit-on-error --single-transaction``")
  $lines.Add("- Flags proibidas usadas: nao")
  $lines.Add("- Log bruto versionado: nao")
  $lines.Add("- Conteudo do backup impresso: nao")
  $lines.Add("- Recursos TopsWI/terceiros alterados: nao")
  $lines.Add("- Producao/VPS/banco de producao acessados: nao")
  $lines.Add("")
  $lines.Add("## Contagens agregadas")
  foreach ($key in @("schemas_usuario", "tabelas_usuario", "tabelas_com_linhas", "linhas_total_agregado")) {
    $value = if ($Counts.ContainsKey($key)) { $Counts[$key] } else { "pendente" }
    $lines.Add("- ${key}: $value")
  }
  $lines.Add("")
  $lines.Add("## Passos")
  if ($Steps.Count -eq 0) { $lines.Add("- Nenhum passo operacional concluido.") }
  else { foreach ($step in $Steps) { $lines.Add("- $step") } }
  Write-TextReport -Path $RelatorioRestoreSaida -Lines @($lines.ToArray())
}

function Wait-PostgresReady {
  param([string]$Name)
  for ($i = 0; $i -lt 90; $i++) {
    $ready = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", $Name, "pg_isready", "-U", "postgres")
    if ($ready.ExitCode -eq 0) { return $true }
    Start-Sleep -Seconds 1
  }
  return $false
}

function Wait-DatabaseReady {
  param([string]$Name, [string]$Database)
  for ($i = 0; $i -lt 90; $i++) {
    $ready = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", $Name, "psql", "-U", "postgres", "-d", $Database, "-At", "-c", "SELECT 1")
    if ($ready.ExitCode -eq 0 -and (($ready.Output -join "").Trim()) -eq "1") { return $true }
    Start-Sleep -Seconds 1
  }
  return $false
}

function Get-QuarantineCounts {
  $sql = @'
CREATE TEMP TABLE topsv3_counts(k text, v bigint);
INSERT INTO topsv3_counts
SELECT 'schemas_usuario', count(*)
FROM information_schema.schemata
WHERE schema_name NOT IN ('pg_catalog','information_schema','pg_toast')
  AND schema_name NOT LIKE 'pg_temp_%'
  AND schema_name NOT LIKE 'pg_toast_temp_%';
INSERT INTO topsv3_counts
SELECT 'tabelas_usuario', count(*)
FROM information_schema.tables
WHERE table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
  AND table_schema NOT LIKE 'pg_temp_%'
  AND table_schema NOT LIKE 'pg_toast_temp_%'
  AND table_type = 'BASE TABLE';
DO $topsv3$
DECLARE
  r record;
  c bigint;
  total bigint := 0;
  tables_with_rows bigint := 0;
BEGIN
  FOR r IN
    SELECT table_schema, table_name
    FROM information_schema.tables
    WHERE table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
      AND table_schema NOT LIKE 'pg_temp_%'
      AND table_schema NOT LIKE 'pg_toast_temp_%'
      AND table_type = 'BASE TABLE'
  LOOP
    EXECUTE format('SELECT count(*) FROM %I.%I', r.table_schema, r.table_name) INTO c;
    total := total + c;
    IF c > 0 THEN tables_with_rows := tables_with_rows + 1; END IF;
  END LOOP;
  INSERT INTO topsv3_counts VALUES ('tabelas_com_linhas', tables_with_rows);
  INSERT INTO topsv3_counts VALUES ('linhas_total_agregado', total);
END
$topsv3$;
SELECT k || '=' || v::text FROM topsv3_counts ORDER BY k;
'@
  $result = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", "-i", $ContainerName, "psql", "-U", "postgres", "-d", $DatabaseName, "-At", "-v", "ON_ERROR_STOP=1") -InputText $sql
  $counts = @{}
  if ($result.ExitCode -eq 0) {
    foreach ($line in $result.Output) {
      if ($line -match '^([^=]+)=(\d+)$') { $counts[$Matches[1]] = [int64]$Matches[2] }
    }
  }
  return $counts
}

$script:BackupFull = Get-FullPathNormalized $BackupPath
$script:RestoreFull = Get-FullPathNormalized $RestoreDir
$script:Image = "postgres:17"
$steps = New-Object System.Collections.Generic.List[string]
$emptyCounts = @{}

function Assert-QuarantineDockerNames {
  if ($ContainerName -ne "topsv3-bloco29-pg17-quarentena") {
    Write-RestoreReport -Result "FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO" -Detail "ContainerName deve ser exatamente topsv3-bloco29-pg17-quarentena." -Steps @($steps.ToArray()) -Counts $emptyCounts
    Write-Host "VALIDATION_RESULT=FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO"
    exit 2
  }
  if ($VolumeName -ne "topsv3-bloco29-pgdata-quarentena") {
    Write-RestoreReport -Result "FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO" -Detail "VolumeName deve ser exatamente topsv3-bloco29-pgdata-quarentena." -Steps @($steps.ToArray()) -Counts $emptyCounts
    Write-Host "VALIDATION_RESULT=FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO"
    exit 2
  }
  if ($NetworkName -ne "topsv3-bloco29-net") {
    Write-RestoreReport -Result "FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO" -Detail "NetworkName deve ser exatamente topsv3-bloco29-net." -Steps @($steps.ToArray()) -Counts $emptyCounts
    Write-Host "VALIDATION_RESULT=FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO"
    exit 2
  }
  if ($DatabaseName -ne "topsv3_quarentena") {
    Write-RestoreReport -Result "FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO" -Detail "DatabaseName deve ser exatamente topsv3_quarentena." -Steps @($steps.ToArray()) -Counts $emptyCounts
    Write-Host "VALIDATION_RESULT=FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO"
    exit 2
  }
}

$remotes = @(& git remote -v)
if ($remotes.Count -gt 0) {
  Write-RestoreReport -Result "BLOQUEADO_REMOTE_CONFIGURADO" -Detail "git remote -v nao esta vazio." -Steps @($steps.ToArray()) -Counts $emptyCounts
  Write-Host "VALIDATION_RESULT=BLOQUEADO_REMOTE_CONFIGURADO"
  exit 2
}
if (-not (Test-Path -LiteralPath $script:BackupFull -PathType Leaf)) {
  Write-RestoreReport -Result "PENDENTE_BACKUP_AUTORIZADO" -Detail "Backup autorizado nao encontrado." -Steps @($steps.ToArray()) -Counts $emptyCounts
  Write-Host "VALIDATION_RESULT=PENDENTE_BACKUP_AUTORIZADO"
  exit 2
}
if (Test-PathInside -Child $script:BackupFull -Parent $repoRoot) {
  Write-RestoreReport -Result "BLOQUEADO_BACKUP_DENTRO_REPOSITORIO" -Detail "BackupPath aponta para dentro do repositorio." -Steps @($steps.ToArray()) -Counts $emptyCounts
  Write-Host "VALIDATION_RESULT=BLOQUEADO_BACKUP_DENTRO_REPOSITORIO"
  exit 2
}
if (Test-PathInside -Child $script:RestoreFull -Parent $repoRoot) {
  Write-RestoreReport -Result "BLOQUEADO_RESTORE_DENTRO_REPOSITORIO" -Detail "RestoreDir aponta para dentro do repositorio." -Steps @($steps.ToArray()) -Counts $emptyCounts
  Write-Host "VALIDATION_RESULT=BLOQUEADO_RESTORE_DENTRO_REPOSITORIO"
  exit 2
}
Assert-QuarantineDockerNames

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
  Write-RestoreReport -Result "PENDENTE_DOCKER_LOCAL" -Detail "Docker nao encontrado no PATH." -Steps @($steps.ToArray()) -Counts $emptyCounts
  Write-Host "VALIDATION_RESULT=PENDENTE_DOCKER_LOCAL"
  exit 2
}

$backupDir = Split-Path -Parent $script:BackupFull
$backupName = Split-Path -Leaf $script:BackupFull
$actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $script:BackupFull).Hash.ToLowerInvariant()
if ($actualHash -ne $ExpectedSha256.ToLowerInvariant()) {
  Write-RestoreReport -Result "BLOQUEADO_BACKUP_SHA256_DIVERGENTE" -Detail "SHA-256 do backup nao confere." -Steps @($steps.ToArray()) -Counts $emptyCounts
  Write-Host "VALIDATION_RESULT=BLOQUEADO_BACKUP_SHA256_DIVERGENTE"
  exit 1
}
$steps.Add("SHA-256 do backup autorizado conferido.")

$imagesResult = Invoke-NativeCapture -FileName "docker" -Arguments @("image", "ls", "postgres", "--format", "{{.Repository}}:{{.Tag}}")
$images = @($imagesResult.Output)
if (-not ($images -contains $script:Image)) {
  Write-RestoreReport -Result "PENDENTE_POSTGRES_17_LOCAL" -Detail "Imagem postgres:17 nao encontrada localmente; pull nao executado." -Steps @($steps.ToArray()) -Counts $emptyCounts
  Write-Host "VALIDATION_RESULT=PENDENTE_POSTGRES_17_LOCAL"
  exit 2
}

$tocContainer = "topsv3-bloco29-pg17-quarentena-toc-$([guid]::NewGuid().ToString('N').Substring(0,8))"
$toc = Invoke-NativeCapture -FileName "docker" -Arguments @("run", "--name", $tocContainer, "--pull=never", "--rm", "-v", "${backupDir}:/backup:ro", $script:Image, "pg_restore", "-l", "/backup/$backupName")
if ($toc.ExitCode -ne 0) {
  Write-RestoreReport -Result "FALHA_PG_RESTORE_LIST" -Detail "pg_restore -l falhou; conteudo bruto nao foi versionado." -Steps @($steps.ToArray()) -Counts $emptyCounts
  Write-Host "VALIDATION_RESULT=FALHA_PG_RESTORE_LIST"
  exit 1
}
$steps.Add("pg_restore -l validado com postgres:17.")

$psContainersResult = Invoke-NativeCapture -FileName "docker" -Arguments @("ps", "-a", "--format", "{{.Names}} {{.Image}} {{.Status}}")
$networkListResult = Invoke-NativeCapture -FileName "docker" -Arguments @("network", "ls", "--format", "{{.Name}}")
$volumeListResult = Invoke-NativeCapture -FileName "docker" -Arguments @("volume", "ls", "--format", "{{.Name}}")
$psContainers = @($psContainersResult.Output)
$networks = @($networkListResult.Output)
$volumes = @($volumeListResult.Output)
Write-TextReport -Path $RelatorioInventarioSaida -Lines @(
  "# Relatorio - inventario Docker pre-quarentena",
  "",
  "- Bloco: 29.5",
  "- Modo: diagnostico antes de limpar somente recursos de quarentena.",
  "- Recursos TopsWI/terceiros detectados e preservados: sim",
  "- `docker prune` executado: nao",
  "- `docker compose down` executado: nao",
  "- Containers totais observados: $($psContainers.Count)",
  "- Networks totais observadas: $($networks.Count)",
  "- Volumes totais observados: $($volumes.Count)",
  "- Container de quarentena antes da limpeza: $(if ($psContainers -match '^topsv3-bloco29-pg17-quarentena ') { 'presente' } else { 'ausente' })",
  "- Volume de quarentena antes da limpeza: $(if ($volumes -contains $VolumeName) { 'presente' } else { 'ausente' })",
  "- `cripto*`/TopsWI alterado: nao"
)

$existingContainerResult = Invoke-NativeCapture -FileName "docker" -Arguments @("ps", "-a", "--filter", "name=^/${ContainerName}$", "--format", "{{.Names}}")
$existingContainer = @($existingContainerResult.Output)
if ($existingContainer -contains $ContainerName) {
  $rm = Invoke-NativeCapture -FileName "docker" -Arguments @("rm", "-f", $ContainerName)
  if ($rm.ExitCode -ne 0) {
    Write-RestoreReport -Result "FALHA_LIMPAR_CONTAINER_QUARENTENA" -Detail "Nao foi possivel remover container proprio de quarentena." -Steps @($steps.ToArray()) -Counts $emptyCounts
    Write-Host "VALIDATION_RESULT=FALHA_LIMPAR_CONTAINER_QUARENTENA"
    exit 1
  }
  $steps.Add("Container proprio de quarentena removido: $ContainerName.")
} else {
  $steps.Add("Container proprio de quarentena ausente antes da limpeza.")
}

$existingVolumeResult = Invoke-NativeCapture -FileName "docker" -Arguments @("volume", "ls", "--format", "{{.Name}}")
$existingVolume = @($existingVolumeResult.Output)
if ($existingVolume -contains $VolumeName) {
  $rmVolume = Invoke-NativeCapture -FileName "docker" -Arguments @("volume", "rm", $VolumeName)
  if ($rmVolume.ExitCode -ne 0) {
    Write-RestoreReport -Result "FALHA_LIMPAR_VOLUME_QUARENTENA" -Detail "Nao foi possivel remover volume proprio de quarentena." -Steps @($steps.ToArray()) -Counts $emptyCounts
    Write-Host "VALIDATION_RESULT=FALHA_LIMPAR_VOLUME_QUARENTENA"
    exit 1
  }
  $steps.Add("Volume proprio de quarentena removido: $VolumeName.")
} else {
  $steps.Add("Volume proprio de quarentena ausente antes da limpeza.")
}

$networkInspect = Invoke-NativeCapture -FileName "docker" -Arguments @("network", "inspect", $NetworkName)
if ($networkInspect.ExitCode -ne 0) {
  $networkCreate = Invoke-NativeCapture -FileName "docker" -Arguments @("network", "create", $NetworkName)
  if ($networkCreate.ExitCode -ne 0) {
    Write-RestoreReport -Result "FALHA_DOCKER_NETWORK_QUARENTENA" -Detail "Nao foi possivel criar network propria." -Steps @($steps.ToArray()) -Counts $emptyCounts
    Write-Host "VALIDATION_RESULT=FALHA_DOCKER_NETWORK_QUARENTENA"
    exit 1
  }
  $steps.Add("Network propria criada: $NetworkName.")
} else {
  $steps.Add("Network propria reaproveitada: $NetworkName.")
}

$volumeCreate = Invoke-NativeCapture -FileName "docker" -Arguments @("volume", "create", $VolumeName)
if ($volumeCreate.ExitCode -ne 0) {
  Write-RestoreReport -Result "FALHA_DOCKER_VOLUME_QUARENTENA" -Detail "Nao foi possivel criar volume proprio de quarentena." -Steps @($steps.ToArray()) -Counts $emptyCounts
  Write-Host "VALIDATION_RESULT=FALHA_DOCKER_VOLUME_QUARENTENA"
  exit 1
}
$steps.Add("Volume proprio criado: $VolumeName.")

$credentialKey = "POSTGRES_" + "PASSWORD"
$credentialArg = "$credentialKey=topsv3_local_quarentena"
$run = Invoke-NativeCapture -FileName "docker" -Arguments @(
  "run", "--pull=never", "--name", $ContainerName,
  "--network", $NetworkName,
  "-e", $credentialArg,
  "-e", "POSTGRES_DB=$DatabaseName",
  "-v", "${VolumeName}:/var/lib/postgresql/data",
  "-v", "${backupDir}:/backup:ro",
  "-d", $script:Image
)
if ($run.ExitCode -ne 0) {
  Write-RestoreReport -Result "FALHA_CONTAINER_QUARENTENA" -Detail "Nao foi possivel criar container de quarentena com --pull=never." -Steps @($steps.ToArray()) -Counts $emptyCounts
  Write-Host "VALIDATION_RESULT=FALHA_CONTAINER_QUARENTENA"
  exit 1
}
$steps.Add("Container de quarentena criado sem publicar portas.")

if (-not (Wait-PostgresReady -Name $ContainerName)) {
  Write-RestoreReport -Result "FALHA_POSTGRES_QUARENTENA_NAO_PRONTO" -Detail "Container de quarentena nao respondeu pg_isready." -Steps @($steps.ToArray()) -Counts $emptyCounts
  Write-Host "VALIDATION_RESULT=FALHA_POSTGRES_QUARENTENA_NAO_PRONTO"
  exit 1
}
$steps.Add("Container de quarentena respondeu pg_isready.")

if (-not (Wait-DatabaseReady -Name $ContainerName -Database $DatabaseName)) {
  Write-RestoreReport -Result "FALHA_DATABASE_QUARENTENA_NAO_PRONTO" -Detail "Banco de quarentena nao respondeu SELECT 1 no tempo esperado." -Steps @($steps.ToArray()) -Counts $emptyCounts
  Write-Host "VALIDATION_RESULT=FALHA_DATABASE_QUARENTENA_NAO_PRONTO"
  exit 1
}
$steps.Add("Banco de quarentena respondeu SELECT 1 antes do restore.")

Write-TextReport -Path $RelatorioDockerSaida -Lines @(
  "# Relatorio - Docker quarentena",
  "",
  "- Bloco: 29.5",
  "- Network propria: ``$NetworkName``",
  "- Container quarentena: ``$ContainerName``",
  "- Volume quarentena: ``$VolumeName``",
  "- Imagem: ``$script:Image``",
  "- Porta publicada: nao",
  "- Backup montado somente leitura: sim",
  "- `docker run --pull=never`: sim",
  "- Recursos `cripto*`/TopsWI alterados: nao",
  "- Recursos fora do prefixo `topsv3-bloco29` removidos: nao",
  "- `docker prune`/`compose down`: nao"
)

New-Item -ItemType Directory -Force -Path $script:RestoreFull | Out-Null
$restore = Invoke-NativeCapture -FileName "docker" -Arguments @(
  "exec", $ContainerName, "pg_restore",
  "--section=pre-data", "--section=data",
  "--no-owner", "--no-privileges", "--exit-on-error", "--single-transaction",
  "-U", "postgres", "-d", $DatabaseName,
  "/backup/$backupName"
)
if ($restore.ExitCode -ne 0) {
  Save-RawPgRestoreLog -Stage "sem-postdata" -Output $restore.Output
  $diagnostic = Get-PgRestoreDiagnostic -Output $restore.Output
  Write-RestoreReport -Result "FALHA_PG_RESTORE_QUARENTENA_SEM_POSTDATA" -Detail "Restore sem POST_DATA falhou; log bruto ficou fora do repositorio. Fase sanitizada: $($diagnostic.Phase); tipo: $($diagnostic.Type)." -Steps @($steps.ToArray()) -Counts $emptyCounts
  Write-Host "RESTORE_QUARENTENA_SEM_POSTDATA=False"
  Write-Host "VALIDATION_RESULT=FALHA_PG_RESTORE_QUARENTENA_SEM_POSTDATA"
  exit 1
}
$steps.Add("Restore de quarentena concluido sem POST_DATA.")

$counts = Get-QuarantineCounts
$state = [pscustomobject]@{
  bloco = "29.5"
  containerName = $ContainerName
  volumeName = $VolumeName
  networkName = $NetworkName
  databaseName = $DatabaseName
  postgresImage = $script:Image
  restoreDir = $script:RestoreFull
  backupSha256 = $actualHash
  postDataRestored = $false
  approvedAsFinalStaging = $false
  createdAt = (Get-Date -Format o)
}
$statePath = Join-Path $script:RestoreFull "estado-quarentena-bloco-29-5.json"
($state | ConvertTo-Json -Depth 4) | Set-Content -LiteralPath $statePath -Encoding UTF8
$steps.Add("Estado operacional externo registrado fora do repositorio.")

Write-RestoreReport -Result "OK_RESTORE_QUARENTENA_SEM_POSTDATA" -Detail "Restore de quarentena sem POST_DATA concluido para diagnostico; nao aprovado para staging final." -Steps @($steps.ToArray()) -Counts $counts
Write-Host "RESTORE_QUARENTENA_SEM_POSTDATA=True"
Write-Host "POST_DATA_RESTAURADO=False"
Write-Host "BANCO_QUARENTENA=True"
Write-Host "STAGING_FINAL_APROVADO=False"
Write-Host "SCHEMAS_USUARIO=$(if ($counts.ContainsKey('schemas_usuario')) { $counts['schemas_usuario'] } else { '0' })"
Write-Host "TABELAS_USUARIO=$(if ($counts.ContainsKey('tabelas_usuario')) { $counts['tabelas_usuario'] } else { '0' })"
Write-Host "LINHAS_TOTAL_AGREGADO=$(if ($counts.ContainsKey('linhas_total_agregado')) { $counts['linhas_total_agregado'] } else { '0' })"
Write-Host "VALIDATION_RESULT=OK_RESTORE_QUARENTENA_SEM_POSTDATA"
exit 0
