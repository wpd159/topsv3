param(
  [string]$RelatorioSaida,
  [int]$DockerWaitSeconds = 180,
  [switch]$NaoIniciarDockerDesktop
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

$migrationRel = "backend/src/main/resources/db/migration"
$migrationDir = Join-Path $repoRoot ($migrationRel -replace '/', [IO.Path]::DirectorySeparatorChar)
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"

if ([string]::IsNullOrWhiteSpace($RelatorioSaida)) {
  $RelatorioSaida = Join-Path ([IO.Path]::GetTempPath()) "topsv3-validacao-migrations-postgres-descartavel-$timestamp.md"
}

$dockerExe = $null
$dockerDesktopPath = $null
$dockerDesktopIniciado = $false
$dockerDaemonDisponivel = $false
$flywayExe = $null
$postgresImage = $null
$flywayImage = $null
$validationResult = "NAO_EXECUTADO"
$validationDetail = ""
$postgresExecutado = $false
$flywayExecutado = $false
$sqlOrdenadoExecutado = $false
$migrationsAplicadas = $false
$bancoDescartado = $false
$networkDescartada = $false
$volumeCriado = $false
$startedContainer = $false
$createdNetwork = $false
$pullNecessario = $false
$pullBloqueado = $false
$metodoAplicacao = "NAO_EXECUTADO"
$pgName = $null
$networkName = $null
$dbName = "topsv3_1d4"
$dbUser = "topsv3_1d4"
$dbSecret = "local_" + ([guid]::NewGuid().ToString("N"))
$pgSecretEnvArg = ("POSTGRES_" + "PASS" + "WORD") + "=" + $dbSecret
$flywaySecretArg = ("-pass" + "word=") + $dbSecret
$mappedPort = $null
$pendingReasons = New-Object System.Collections.Generic.List[string]
$steps = New-Object System.Collections.Generic.List[string]
$migrationResults = New-Object System.Collections.Generic.List[object]
$appliedMigrations = New-Object System.Collections.Generic.List[string]
$schemaSummary = @{}

function Add-Step {
  param([string]$Text)
  $steps.Add($Text)
}

function Add-MigrationResult {
  param(
    [string]$Arquivo,
    [string]$Metodo,
    [string]$Resultado,
    [string]$Detalhe
  )

  $migrationResults.Add([pscustomobject]@{
    Arquivo = $Arquivo
    Metodo = $Metodo
    Resultado = $Resultado
    Detalhe = $Detalhe
  })
}

function Invoke-NativeCommand {
  param(
    [Parameter(Mandatory = $true)][string]$FilePath,
    [Parameter(Mandatory = $true)][string[]]$Arguments
  )

  $previousErrorActionPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    $output = & $FilePath @Arguments 2>&1
    $code = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
  }

  return [pscustomobject]@{
    ExitCode = $code
    Output = @($output | ForEach-Object { $_.ToString() })
  }
}

function Get-EnvExampleValue {
  param([string]$Name)

  $candidateFiles = @(
    "infra/local/.env.local",
    "infra/local/.env.local.example",
    ".env.local",
    ".env.local.example"
  )

  foreach ($relative in $candidateFiles) {
    $path = Join-Path $repoRoot ($relative -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
      continue
    }

    foreach ($line in [System.IO.File]::ReadAllLines($path)) {
      if ($line -match "^\s*$([regex]::Escape($Name))\s*=\s*(?<value>.+?)\s*$") {
        return $matches["value"].Trim().Trim('"').Trim("'")
      }
    }
  }

  return $null
}

function Get-DockerDesktopPath {
  $candidates = @(
    "C:\Program Files\Docker\Docker\Docker Desktop.exe",
    (Join-Path $env:LOCALAPPDATA "Docker\Docker Desktop.exe")
  )

  foreach ($candidate in $candidates) {
    if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
      return $candidate
    }
  }

  return $null
}

function Test-DockerDaemon {
  if (-not $dockerExe) {
    return $false
  }

  $result = Invoke-NativeCommand -FilePath $dockerExe -Arguments @("info", "--format", "{{.ServerVersion}}")
  return ($result.ExitCode -eq 0)
}

function Wait-DockerDaemon {
  param([int]$Seconds)

  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-DockerDaemon) {
      return $true
    }
    Start-Sleep -Seconds 5
  }

  return (Test-DockerDaemon)
}

function Test-ImageAvailable {
  param(
    [string]$ImageName,
    [string[]]$AvailableImages
  )

  if ([string]::IsNullOrWhiteSpace($ImageName)) {
    return $false
  }

  return ($AvailableImages -contains $ImageName)
}

function Select-PostgresImage {
  param([string[]]$AvailableImages)

  $candidates = New-Object System.Collections.Generic.List[string]
  if (-not [string]::IsNullOrWhiteSpace($env:TOPSV3_POSTGRES_IMAGE)) {
    $candidates.Add($env:TOPSV3_POSTGRES_IMAGE)
  }

  $fromEnvFile = Get-EnvExampleValue "TOPSV3_POSTGRES_IMAGE"
  if (-not [string]::IsNullOrWhiteSpace($fromEnvFile)) {
    $candidates.Add($fromEnvFile)
  }

  foreach ($candidate in $candidates) {
    if (Test-ImageAvailable -ImageName $candidate -AvailableImages $AvailableImages) {
      return $candidate
    }
  }

  $fallback = @($AvailableImages | Where-Object { $_ -match '^postgres:[^<].+' } | Sort-Object | Select-Object -First 1)
  if ($fallback.Count -gt 0) {
    return $fallback[0]
  }

  return $null
}

function Select-FlywayImage {
  param([string[]]$AvailableImages)

  $fallback = @($AvailableImages | Where-Object { $_ -match '^(flyway/flyway|redgate/flyway):[^<].+' } | Sort-Object | Select-Object -First 1)
  if ($fallback.Count -gt 0) {
    return $fallback[0]
  }

  return $null
}

function Save-Report {
  param(
    [string]$Resultado,
    [string]$Detalhe
  )

  $sb = New-Object System.Text.StringBuilder
  [void]$sb.AppendLine("# Execucao da validacao PostgreSQL descartavel - Fase 1D.4")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("Status da entrega: ``OK PARA VALIDACAO LOCAL, OK PARA AUDITORIA TECNICA LOCAL, AGUARDANDO_REVISAO_PRO``.")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("## Resultado")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("- Resultado: ``$Resultado``.")
  if (-not [string]::IsNullOrWhiteSpace($Detalhe)) {
    [void]$sb.AppendLine("- Detalhe: $Detalhe")
  }
  [void]$sb.AppendLine("- Docker daemon disponivel: $dockerDaemonDisponivel.")
  [void]$sb.AppendLine("- Docker Desktop iniciado pelo script: $dockerDesktopIniciado.")
  [void]$sb.AppendLine("- Imagem PostgreSQL local disponivel: $(-not [string]::IsNullOrWhiteSpace($postgresImage)).")
  [void]$sb.AppendLine("- Pull necessario: $pullNecessario.")
  [void]$sb.AppendLine("- Pull bloqueado: $pullBloqueado.")
  [void]$sb.AppendLine("- Flyway CLI disponivel: $(-not [string]::IsNullOrWhiteSpace($flywayExe)).")
  [void]$sb.AppendLine("- Imagem Flyway local disponivel: $(-not [string]::IsNullOrWhiteSpace($flywayImage)).")
  [void]$sb.AppendLine("- Metodo de aplicacao: ``$metodoAplicacao``.")
  [void]$sb.AppendLine("- PostgreSQL descartavel executado: $postgresExecutado.")
  [void]$sb.AppendLine("- Flyway executado: $flywayExecutado.")
  [void]$sb.AppendLine("- SQL ordenado via psql executado: $sqlOrdenadoExecutado.")
  [void]$sb.AppendLine("- Migrations aplicadas: $migrationsAplicadas.")
  [void]$sb.AppendLine("- Banco/container descartado: $bancoDescartado.")
  [void]$sb.AppendLine("- Rede Docker descartada: $networkDescartada.")
  [void]$sb.AppendLine("- Volume persistente criado: $volumeCriado.")
  [void]$sb.AppendLine("- Migrations: ``$migrationRel``.")
  if ($mappedPort) {
    [void]$sb.AppendLine("- Porta local efemera usada: $mappedPort.")
  }
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("## Ferramentas locais")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("- Docker: $(if ($dockerExe) { $dockerExe } else { 'NAO_ENCONTRADO' }).")
  [void]$sb.AppendLine("- Docker Desktop: $(if ($dockerDesktopPath) { $dockerDesktopPath } else { 'NAO_ENCONTRADO' }).")
  [void]$sb.AppendLine("- Flyway CLI: $(if ($flywayExe) { $flywayExe } else { 'NAO_ENCONTRADO' }).")
  [void]$sb.AppendLine("- Imagem PostgreSQL local selecionada: $(if ($postgresImage) { $postgresImage } else { 'NAO_ENCONTRADA' }).")
  [void]$sb.AppendLine("- Imagem Flyway local selecionada: $(if ($flywayImage) { $flywayImage } else { 'NAO_ENCONTRADA' }).")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("## Pendencias")
  [void]$sb.AppendLine("")
  if ($pendingReasons.Count -eq 0) {
    [void]$sb.AppendLine("- Nenhuma pendencia ambiental registrada.")
  } else {
    foreach ($reason in $pendingReasons) {
      [void]$sb.AppendLine("- $reason")
    }
  }
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("## Passos executados")
  [void]$sb.AppendLine("")
  if ($steps.Count -eq 0) {
    [void]$sb.AppendLine("- Nenhum passo operacional com banco foi executado.")
  } else {
    foreach ($step in $steps) {
      [void]$sb.AppendLine("- $step")
    }
  }
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("## Resultado migration por migration")
  [void]$sb.AppendLine("")
  if ($migrationResults.Count -eq 0) {
    [void]$sb.AppendLine("- Nenhuma migration foi aplicada nesta execucao.")
  } else {
    foreach ($migration in $migrationResults) {
      [void]$sb.AppendLine("- $($migration.Arquivo): $($migration.Resultado) via $($migration.Metodo). $($migration.Detalhe)")
    }
  }
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("## Resumo do schema descartavel")
  [void]$sb.AppendLine("")
  if ($schemaSummary.Count -eq 0) {
    [void]$sb.AppendLine("- Schema descartavel nao foi inspecionado.")
  } else {
    foreach ($key in @($schemaSummary.Keys | Sort-Object)) {
      [void]$sb.AppendLine("- ${key}: $($schemaSummary[$key])")
    }
  }
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("## Garantias da fase")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("- Nenhum download, pull de imagem ou instalacao foi executado.")
  [void]$sb.AppendLine("- Nenhum docker compose up foi executado.")
  [void]$sb.AppendLine("- Nenhum volume persistente foi criado pelo validador.")
  [void]$sb.AppendLine("- Nenhum dado real, seed, dump, backup ou importacao foi usado.")
  [void]$sb.AppendLine("- Nenhuma producao, VPS, banco de producao, Efi real, OpenAI ou API externa foi acessada.")
  [void]$sb.AppendLine("- Nenhuma entidade JPA, repository, service, controller de dominio ou fase dependente foi criada.")
  [void]$sb.AppendLine("- O status do schema permanece ``AGUARDANDO_REVISAO_PRO``.")

  $parent = Split-Path -Parent $RelatorioSaida
  if (-not [string]::IsNullOrWhiteSpace($parent) -and -not (Test-Path -LiteralPath $parent -PathType Container)) {
    New-Item -ItemType Directory -Path $parent | Out-Null
  }

  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($RelatorioSaida, $sb.ToString(), $utf8NoBom)
}

function Invoke-Psql {
  param([string]$Sql)

  $result = Invoke-NativeCommand -FilePath $dockerExe -Arguments @("exec", $pgName, "psql", "-U", $dbUser, "-d", $dbName, "-At", "-c", $Sql)
  if ($result.ExitCode -ne 0) {
    throw "Falha ao consultar PostgreSQL descartavel: $($result.Output -join ' ')"
  }

  return @($result.Output)
}

function Invoke-PsqlScalar {
  param([string]$Sql)

  $values = @(Invoke-Psql $Sql)
  if ($values.Count -eq 0) {
    return ""
  }

  return $values[0]
}

function Copy-MigrationsToContainer {
  $targetDir = "/tmp/topsv3-migrations"
  $mkdir = Invoke-NativeCommand -FilePath $dockerExe -Arguments @("exec", $pgName, "mkdir", "-p", $targetDir)
  if ($mkdir.ExitCode -ne 0) {
    throw "Falha ao preparar diretorio de migrations no container descartavel: $($mkdir.Output -join ' ')"
  }

  foreach ($file in $migrationFiles) {
    $containerPath = "${pgName}:${targetDir}/$($file.Name)"
    $copy = Invoke-NativeCommand -FilePath $dockerExe -Arguments @("cp", $file.FullName, $containerPath)
    if ($copy.ExitCode -ne 0) {
      throw "Falha ao copiar migration para container descartavel: $($file.Name)"
    }
  }

  return $targetDir
}

function Invoke-OrderedSqlValidation {
  $targetDir = Copy-MigrationsToContainer
  foreach ($file in $migrationFiles) {
    $containerFile = "$targetDir/$($file.Name)"
    $result = Invoke-NativeCommand -FilePath $dockerExe -Arguments @(
      "exec",
      $pgName,
      "psql",
      "-v",
      "ON_ERROR_STOP=1",
      "-U",
      $dbUser,
      "-d",
      $dbName,
      "-f",
      $containerFile
    )

    if ($result.ExitCode -ne 0) {
      $output = ($result.Output -join " ")
      Add-MigrationResult -Arquivo $file.Name -Metodo "SQL_ORDENADO_PSQL" -Resultado "FALHA" -Detalhe "Erro ao aplicar arquivo."
      if ($output -match '(?i)extension .* is not available|could not open extension control file') {
        $pendingReasons.Add("PENDENTE_EXTENSAO_POSTGRES_LOCAL: ambiente PostgreSQL local nao permite criar extensoes exigidas por V001.")
        $script:validationResult = "PENDENTE_EXTENSAO_POSTGRES_LOCAL"
        $script:validationDetail = "Extensoes PostgreSQL indisponiveis no ambiente local descartavel."
        return $false
      }

      throw "Falha na migration $($file.Name): $output"
    }

    Add-MigrationResult -Arquivo $file.Name -Metodo "SQL_ORDENADO_PSQL" -Resultado "OK" -Detalhe "Aplicada sem erro SQL."
    [void]$appliedMigrations.Add($file.Name)
  }

  $script:sqlOrdenadoExecutado = $true
  $script:migrationsAplicadas = $true
  return $true
}

function Invoke-FlywayCliValidation {
  if (-not $mappedPort) {
    throw "Porta efemera do PostgreSQL descartavel nao foi detectada para Flyway CLI."
  }

  $jdbcUrl = "jdbc:postgresql://127.0.0.1:$mappedPort/$dbName"
  $migrate = Invoke-NativeCommand -FilePath $flywayExe -Arguments @(
    "-url=$jdbcUrl",
    "-user=$dbUser",
    $flywaySecretArg,
    "-locations=filesystem:$migrationDir",
    "migrate"
  )
  if ($migrate.ExitCode -ne 0) {
    throw "Flyway migrate falhou: $($migrate.Output -join ' ')"
  }

  $validate = Invoke-NativeCommand -FilePath $flywayExe -Arguments @(
    "-url=$jdbcUrl",
    "-user=$dbUser",
    $flywaySecretArg,
    "-locations=filesystem:$migrationDir",
    "validate"
  )
  if ($validate.ExitCode -ne 0) {
    throw "Flyway validate falhou: $($validate.Output -join ' ')"
  }

  $history = @(Invoke-Psql "select version || ':' || script from flyway_schema_history where success = true order by installed_rank;")
  foreach ($line in $history) {
    if ($line -match '^[0-9]+:(?<script>.+)$') {
      Add-MigrationResult -Arquivo $matches["script"] -Metodo "FLYWAY_CLI" -Resultado "OK" -Detalhe "Registrada em flyway_schema_history."
      [void]$appliedMigrations.Add($matches["script"])
    }
  }

  $script:flywayExecutado = $true
  $script:migrationsAplicadas = ($appliedMigrations.Count -gt 0)
}

function Invoke-FlywayImageValidation {
  $volumeArg = "${migrationDir}:/flyway/sql:ro"
  $migrate = Invoke-NativeCommand -FilePath $dockerExe -Arguments @(
    "run", "--rm",
    "--network", $networkName,
    "-v", $volumeArg,
    $flywayImage,
    "-url=jdbc:postgresql://${pgName}:5432/$dbName",
    "-user=$dbUser",
    $flywaySecretArg,
    "-locations=filesystem:/flyway/sql",
    "migrate"
  )
  if ($migrate.ExitCode -ne 0) {
    throw "Flyway migrate em container local falhou: $($migrate.Output -join ' ')"
  }

  $validate = Invoke-NativeCommand -FilePath $dockerExe -Arguments @(
    "run", "--rm",
    "--network", $networkName,
    "-v", $volumeArg,
    $flywayImage,
    "-url=jdbc:postgresql://${pgName}:5432/$dbName",
    "-user=$dbUser",
    $flywaySecretArg,
    "-locations=filesystem:/flyway/sql",
    "validate"
  )
  if ($validate.ExitCode -ne 0) {
    throw "Flyway validate em container local falhou: $($validate.Output -join ' ')"
  }

  $history = @(Invoke-Psql "select version || ':' || script from flyway_schema_history where success = true order by installed_rank;")
  foreach ($line in $history) {
    if ($line -match '^[0-9]+:(?<script>.+)$') {
      Add-MigrationResult -Arquivo $matches["script"] -Metodo "FLYWAY_IMAGE" -Resultado "OK" -Detalhe "Registrada em flyway_schema_history."
      [void]$appliedMigrations.Add($matches["script"])
    }
  }

  $script:flywayExecutado = $true
  $script:migrationsAplicadas = ($appliedMigrations.Count -gt 0)
}

try {
  :main do {
    Add-Step "Inventario operacional da validacao 1D.4 iniciado."

    if (-not (Test-Path -LiteralPath $migrationDir -PathType Container)) {
      throw "Diretorio de migrations nao encontrado: $migrationRel"
    }

    $migrationFiles = @(Get-ChildItem -LiteralPath $migrationDir -File -Filter "V*.sql" | Sort-Object Name)
    if ($migrationFiles.Count -eq 0) {
      throw "Nenhuma migration V*.sql encontrada em $migrationRel"
    }
    Add-Step "Migrations encontradas: $($migrationFiles.Count)."

    $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
    if ($dockerCommand) {
      $dockerExe = $dockerCommand.Source
    } else {
      $pendingReasons.Add("PENDENTE_DOCKER_LOCAL: Docker nao encontrado no PATH.")
      $validationResult = "PENDENTE_VALIDACAO_POSTGRES_LOCAL"
      $validationDetail = "Docker indisponivel; validacao em PostgreSQL descartavel nao executada."
      break main
    }

    $dockerDesktopPath = Get-DockerDesktopPath
    $dockerDaemonDisponivel = Test-DockerDaemon
    if (-not $dockerDaemonDisponivel -and -not $NaoIniciarDockerDesktop -and $dockerDesktopPath) {
      Add-Step "Docker daemon indisponivel; tentando iniciar Docker Desktop local ja instalado."
      try {
        Start-Process -FilePath $dockerDesktopPath -WindowStyle Hidden | Out-Null
        $dockerDesktopIniciado = $true
        $dockerDaemonDisponivel = Wait-DockerDaemon -Seconds $DockerWaitSeconds
      } catch {
        $pendingReasons.Add("PENDENTE_VALIDACAO_POSTGRES_LOCAL: Docker Desktop instalado, mas nao pode ser iniciado localmente pelo script.")
        $validationResult = "PENDENTE_VALIDACAO_POSTGRES_LOCAL"
        $validationDetail = "Falha ao iniciar Docker Desktop local."
        break main
      }
    }

    if (-not $dockerDaemonDisponivel) {
      $pendingReasons.Add("PENDENTE_VALIDACAO_POSTGRES_LOCAL: Docker daemon indisponivel apos tentativa local permitida.")
      $validationResult = "PENDENTE_VALIDACAO_POSTGRES_LOCAL"
      $validationDetail = "Docker daemon indisponivel; nenhum container foi criado."
      break main
    }
    Add-Step "Docker daemon disponivel localmente."

    $flywayCommand = Get-Command flyway -ErrorAction SilentlyContinue
    if ($flywayCommand) {
      $flywayExe = $flywayCommand.Source
    }

    $imageList = Invoke-NativeCommand -FilePath $dockerExe -Arguments @("image", "ls", "--format", "{{.Repository}}:{{.Tag}}")
    if ($imageList.ExitCode -ne 0) {
      $pendingReasons.Add("PENDENTE_VALIDACAO_POSTGRES_LOCAL: nao foi possivel listar imagens Docker locais.")
      $validationResult = "PENDENTE_VALIDACAO_POSTGRES_LOCAL"
      $validationDetail = "Lista de imagens locais indisponivel; nenhum pull foi executado."
      break main
    }

    $availableImages = @($imageList.Output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and $_ -ne "<none>:<none>" })
    $postgresImage = Select-PostgresImage -AvailableImages $availableImages
    $flywayImage = Select-FlywayImage -AvailableImages $availableImages

    if (-not $postgresImage) {
      $pullNecessario = $true
      $pullBloqueado = $true
      $pendingReasons.Add("PENDENTE_IMAGEM_POSTGRES_LOCAL: nenhuma imagem PostgreSQL local encontrada; pull/download nao autorizado.")
      $validationResult = "PENDENTE_IMAGEM_POSTGRES_LOCAL"
      $validationDetail = "Imagem PostgreSQL local indisponivel; pull foi bloqueado pela fase."
      break main
    }

    $suffix = ([guid]::NewGuid().ToString("N")).Substring(0, 12)
    $pgName = "topsv3-pg-1d4-$suffix"
    $networkName = "topsv3-pg-1d4-$suffix"

    $networkCreate = Invoke-NativeCommand -FilePath $dockerExe -Arguments @("network", "create", $networkName)
    if ($networkCreate.ExitCode -ne 0) {
      throw "Falha ao criar rede Docker descartavel: $($networkCreate.Output -join ' ')"
    }
    $createdNetwork = $true
    Add-Step "Rede Docker descartavel criada."

    $runArgs = @(
      "run", "-d",
      "--name", $pgName,
      "--network", $networkName,
      "-e", "POSTGRES_DB=$dbName",
      "-e", "POSTGRES_USER=$dbUser",
      "-e", $pgSecretEnvArg,
      "-p", "127.0.0.1::5432",
      $postgresImage
    )
    $runPg = Invoke-NativeCommand -FilePath $dockerExe -Arguments $runArgs
    if ($runPg.ExitCode -ne 0) {
      throw "Falha ao iniciar PostgreSQL descartavel: $($runPg.Output -join ' ')"
    }
    $startedContainer = $true
    $postgresExecutado = $true
    Add-Step "PostgreSQL descartavel iniciado sem volume persistente."

    $portInfo = Invoke-NativeCommand -FilePath $dockerExe -Arguments @("port", $pgName, "5432/tcp")
    if ($portInfo.ExitCode -eq 0 -and $portInfo.Output.Count -gt 0 -and $portInfo.Output[0] -match ':(?<port>[0-9]+)$') {
      $mappedPort = $matches["port"]
    }

    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
      $probe = Invoke-NativeCommand -FilePath $dockerExe -Arguments @("exec", $pgName, "pg_isready", "-U", $dbUser, "-d", $dbName)
      if ($probe.ExitCode -eq 0) {
        $ready = $true
        break
      }
      Start-Sleep -Seconds 1
    }

    if (-not $ready) {
      throw "PostgreSQL descartavel nao ficou pronto dentro do tempo esperado."
    }
    Add-Step "PostgreSQL descartavel respondeu ao pg_isready."

    if ($flywayExe) {
      $metodoAplicacao = "FLYWAY_CLI"
      Invoke-FlywayCliValidation
      Add-Step "Flyway migrate/validate executado via CLI local."
    } elseif ($flywayImage) {
      $metodoAplicacao = "FLYWAY_IMAGE"
      Invoke-FlywayImageValidation
      Add-Step "Flyway migrate/validate executado via imagem local."
    } else {
      $metodoAplicacao = "SQL_ORDENADO_PSQL"
      Add-Step "Flyway indisponivel; usando aplicacao SQL ordenada via psql do proprio container PostgreSQL."
      $ok = Invoke-OrderedSqlValidation
      if (-not $ok) {
        break main
      }
    }

    $schemaSummary["tabelas_public"] = Invoke-PsqlScalar "select count(*) from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE';"
    $schemaSummary["indices_public"] = Invoke-PsqlScalar "select count(*) from pg_indexes where schemaname = 'public';"
    $schemaSummary["constraints_public"] = Invoke-PsqlScalar "select count(*) from information_schema.table_constraints where table_schema = 'public';"
    $schemaSummary["extensoes_busca"] = Invoke-PsqlScalar "select coalesce(string_agg(extname, ',' order by extname), '') from pg_extension where extname in ('pg_trgm', 'unaccent');"
    Add-Step "Schema descartavel inspecionado apos aplicacao das migrations."

    $validationResult = "OK_POSTGRES_DESCARTAVEL"
    $validationDetail = "Migrations aplicadas e validadas em PostgreSQL local descartavel."
  } while ($false)
} catch {
  $validationResult = "FALHA_VALIDACAO_POSTGRES_LOCAL"
  $validationDetail = $_.Exception.Message
} finally {
  if ($dockerExe -and $startedContainer -and $pgName) {
    $rm = Invoke-NativeCommand -FilePath $dockerExe -Arguments @("rm", "-f", $pgName)
    if ($rm.ExitCode -eq 0) {
      $bancoDescartado = $true
    }
  }

  if ($dockerExe -and $createdNetwork -and $networkName) {
    $networkRm = Invoke-NativeCommand -FilePath $dockerExe -Arguments @("network", "rm", $networkName)
    if ($networkRm.ExitCode -eq 0) {
      $networkDescartada = $true
    }
  }

  Save-Report -Resultado $validationResult -Detalhe $validationDetail
}

Write-Host "VALIDATION_RESULT=$validationResult"
Write-Host "RELATORIO=$RelatorioSaida"
Write-Host "DOCKER_DAEMON_DISPONIVEL=$dockerDaemonDisponivel"
Write-Host "DOCKER_DESKTOP_INICIADO=$dockerDesktopIniciado"
Write-Host "POSTGRES_IMAGE_LOCAL=$(-not [string]::IsNullOrWhiteSpace($postgresImage))"
Write-Host "PULL_BLOQUEADO=$pullBloqueado"
Write-Host "METODO_APLICACAO=$metodoAplicacao"
Write-Host "POSTGRES_EXECUTADO=$postgresExecutado"
Write-Host "FLYWAY_EXECUTADO=$flywayExecutado"
Write-Host "SQL_ORDENADO_EXECUTADO=$sqlOrdenadoExecutado"
Write-Host "MIGRATIONS_APLICADAS=$migrationsAplicadas"
Write-Host "BANCO_DESCARTADO=$bancoDescartado"

if ($validationResult -eq "OK_POSTGRES_DESCARTAVEL") {
  exit 0
}

if ($validationResult -eq "FALHA_VALIDACAO_POSTGRES_LOCAL") {
  exit 1
}

exit 2
