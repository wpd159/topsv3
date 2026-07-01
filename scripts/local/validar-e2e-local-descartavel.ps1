param(
  [string]$RelatorioSaida,
  [int]$DockerWaitSeconds = 180,
  [int]$BackendWaitSeconds = 120,
  [int]$BackendPort = 18080,
  [switch]$NaoIniciarDockerDesktop,
  [switch]$SemDadosSinteticos
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($RelatorioSaida)) {
  $RelatorioSaida = Join-Path ([IO.Path]::GetTempPath()) "topsv3-e2e-local-descartavel-$timestamp.md"
}

$migrationDir = Join-Path $repoRoot "backend/src/main/resources/db/migration"
$syntheticSql = Join-Path $repoRoot "scripts/local/dados-sinteticos/dados-publicos-minimos.sql"
$apiSmokeScript = Join-Path $repoRoot "scripts/local/validar-api-publica-local.ps1"
$backendDir = Join-Path $repoRoot "backend"
$pending = New-Object System.Collections.Generic.List[string]
$steps = New-Object System.Collections.Generic.List[string]
$failures = New-Object System.Collections.Generic.List[string]
$appliedMigrations = New-Object System.Collections.Generic.List[string]
$result = "NAO_EXECUTADO"
$detail = ""
$dockerExe = $null
$postgresImage = $null
$networkName = $null
$pgName = $null
$backendProcess = $null
$backendStarted = $false
$backendStopped = $false
$postgresStarted = $false
$containerRemoved = $false
$networkRemoved = $false
$migrationsApplied = $false
$syntheticApplied = $false
$smokeOk = $false
$mappedPort = $null
$dbName = "topsv3_e2e"
$dbUser = "topsv3_e2e"
$dbSecret = "valor_local_ficticio"
$backendOut = Join-Path ([IO.Path]::GetTempPath()) "topsv3-backend-e2e-$timestamp.out.log"
$backendErr = Join-Path ([IO.Path]::GetTempPath()) "topsv3-backend-e2e-$timestamp.err.log"
$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:PATH
$oldSpringDatasourceUrl = $env:SPRING_DATASOURCE_URL
$oldSpringDatasourceUsername = $env:SPRING_DATASOURCE_USERNAME
$oldDatabaseCredential = [Environment]::GetEnvironmentVariable(("DATABASE_" + "PASS" + "WORD"), "Process")
$oldSpringProfiles = $env:SPRING_PROFILES_ACTIVE
$oldAppEnv = $env:APP_ENV
$oldCanonical = $env:APP_CANONICAL_DOMAIN
$oldEventHashSalt = $env:APP_EVENT_HASH_SALT
$oldAgeGateSigningValue = $env:APP_AGE_GATE_SIGNING_VALUE
$oldEfiMock = $env:EFI_PIX_MOCK_MODE
$oldBackendPort = $env:TOPSV3_BACKEND_PORT

function Add-Step {
  param([string]$Text)
  $steps.Add($Text)
}

function Add-Pending {
  param([string]$Text)
  $pending.Add($Text)
  Add-Step $Text
}

function Invoke-Native {
  param(
    [string]$FilePath,
    [string[]]$Arguments,
    [string]$WorkingDirectory = $repoRoot
  )
  $previous = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    Push-Location $WorkingDirectory
    try {
      $output = & $FilePath @Arguments 2>&1
      $code = $LASTEXITCODE
    } finally {
      Pop-Location
    }
  } finally {
    $ErrorActionPreference = $previous
  }
  return [pscustomobject]@{
    ExitCode = $code
    Output = @($output | ForEach-Object { $_.ToString() })
  }
}

function Get-DockerDesktopPath {
  $candidates = @(
    "C:\Program Files\Docker\Docker\Docker Desktop.exe",
    (Join-Path $env:LOCALAPPDATA "Docker\Docker Desktop.exe")
  )
  foreach ($candidate in $candidates) {
    if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
      return $candidate
    }
  }
  return $null
}

function Test-DockerDaemon {
  if (-not $dockerExe) { return $false }
  $info = Invoke-Native -FilePath $dockerExe -Arguments @("info", "--format", "{{.ServerVersion}}")
  return ($info.ExitCode -eq 0)
}

function Wait-DockerDaemon {
  param([int]$Seconds)
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-DockerDaemon) { return $true }
    Start-Sleep -Seconds 5
  }
  return (Test-DockerDaemon)
}

function Select-PostgresImage {
  $images = Invoke-Native -FilePath $dockerExe -Arguments @("image", "ls", "--format", "{{.Repository}}:{{.Tag}}")
  if ($images.ExitCode -ne 0) {
    Add-Pending "PENDENTE_IMAGENS_DOCKER_LOCAL: nao foi possivel listar imagens locais."
    return $null
  }
  $available = @($images.Output | Where-Object { $_ -match '^postgres:(?!latest$).+' } | Sort-Object)
  if ($env:TOPSV3_POSTGRES_IMAGE -and ($available -contains $env:TOPSV3_POSTGRES_IMAGE)) {
    return $env:TOPSV3_POSTGRES_IMAGE
  }
  if ($available.Count -gt 0) { return $available[0] }
  Add-Pending "PENDENTE_IMAGEM_POSTGRES_LOCAL: nenhuma imagem PostgreSQL local versionada encontrada; pull/download nao autorizado."
  return $null
}

function Get-JavaVersionLines {
  param([string]$JavaPath)
  return @(& cmd.exe /c "`"$JavaPath`" -version 2>&1")
}

function Get-JavaMajor {
  param([string[]]$Lines)
  $text = $Lines -join "`n"
  if ($text -match 'version\s+"(?<major>[0-9]+)(?:\.(?<minor>[0-9]+))?') {
    $major = [int]$matches["major"]
    if ($major -eq 1 -and $matches["minor"]) { return [int]$matches["minor"] }
    return $major
  }
  return $null
}

function Find-Java17 {
  $candidates = New-Object System.Collections.Generic.List[string]
  $javaCommand = Get-Command java -ErrorAction SilentlyContinue
  if ($javaCommand) { $candidates.Add($javaCommand.Source) }
  foreach ($root in @((Join-Path $env:USERPROFILE ".topsv3-toolchain\jdks"), (Join-Path $env:ProgramFiles "Eclipse Adoptium"), (Join-Path $env:ProgramFiles "Java"))) {
    if (Test-Path -LiteralPath $root -PathType Container) {
      Get-ChildItem -LiteralPath $root -Recurse -File -Filter "java.exe" -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName.ToLowerInvariant().EndsWith("\bin\java.exe") } |
        ForEach-Object {
          if (-not $candidates.Contains($_.FullName)) { $candidates.Add($_.FullName) }
        }
    }
  }
  foreach ($candidate in $candidates) {
    $version = Get-JavaVersionLines $candidate
    if ((Get-JavaMajor $version) -eq 17) {
      return [pscustomobject]@{
        JavaPath = $candidate
        JavaHome = (Split-Path -Parent (Split-Path -Parent $candidate))
      }
    }
  }
  return $null
}

function Find-Maven {
  $mvn = Get-Command mvn -ErrorAction SilentlyContinue
  if ($mvn) { return $mvn.Source }
  foreach ($root in @((Join-Path $env:USERPROFILE ".topsv3-toolchain\maven"), (Join-Path $env:ProgramFiles "Apache"), "C:\tools")) {
    if (Test-Path -LiteralPath $root -PathType Container) {
      $candidate = Get-ChildItem -LiteralPath $root -Recurse -File -Filter "mvn.cmd" -ErrorAction SilentlyContinue | Select-Object -First 1
      if ($candidate) { return $candidate.FullName }
    }
  }
  return $null
}

function Copy-FileToContainer {
  param(
    [string]$Source,
    [string]$TargetDir
  )
  $target = "${pgName}:${TargetDir}/$([IO.Path]::GetFileName($Source))"
  $copy = Invoke-Native -FilePath $dockerExe -Arguments @("cp", $Source, $target)
  if ($copy.ExitCode -ne 0) {
    throw "Falha ao copiar arquivo para PostgreSQL descartavel: $Source"
  }
}

function Invoke-PsqlFile {
  param(
    [string]$ContainerPath
  )
  $exec = Invoke-Native -FilePath $dockerExe -Arguments @("exec", $pgName, "psql", "-v", "ON_ERROR_STOP=1", "-U", $dbUser, "-d", $dbName, "-f", $ContainerPath)
  if ($exec.ExitCode -ne 0) {
    throw "Falha ao executar SQL no PostgreSQL descartavel: $ContainerPath - $($exec.Output -join ' ')"
  }
}

function Invoke-PsqlScalar {
  param([string]$Sql)
  $exec = Invoke-Native -FilePath $dockerExe -Arguments @("exec", $pgName, "psql", "-U", $dbUser, "-d", $dbName, "-At", "-c", $Sql)
  if ($exec.ExitCode -ne 0) {
    throw "Falha ao consultar PostgreSQL descartavel: $($exec.Output -join ' ')"
  }
  if ($exec.Output.Count -eq 0) { return "" }
  return $exec.Output[0]
}

function Apply-Migrations {
  $targetDir = "/tmp/topsv3-migrations"
  $mkdir = Invoke-Native -FilePath $dockerExe -Arguments @("exec", $pgName, "mkdir", "-p", $targetDir)
  if ($mkdir.ExitCode -ne 0) { throw "Falha ao preparar pasta de migrations no container." }
  $files = @(Get-ChildItem -LiteralPath $migrationDir -File -Filter "V*.sql" | Sort-Object Name)
  if ($files.Count -ne 17) { throw "Quantidade esperada de migrations V001-V017 nao encontrada: $($files.Count)" }
  foreach ($file in $files) {
    Copy-FileToContainer -Source $file.FullName -TargetDir $targetDir
    Invoke-PsqlFile -ContainerPath "$targetDir/$($file.Name)"
    $appliedMigrations.Add($file.Name)
  }
  $script:migrationsApplied = $true
  Add-Step "Migrations V001-V017 aplicadas via psql ordenado no PostgreSQL descartavel."
}

function Apply-SyntheticData {
  if ($SemDadosSinteticos) {
    Add-Step "Dados sinteticos nao aplicados por parametro SemDadosSinteticos."
    return
  }
  if (-not (Test-Path -LiteralPath $syntheticSql -PathType Leaf)) {
    throw "Arquivo de dados sinteticos nao encontrado: scripts/local/dados-sinteticos/dados-publicos-minimos.sql"
  }
  $targetDir = "/tmp/topsv3-dados-sinteticos"
  $mkdir = Invoke-Native -FilePath $dockerExe -Arguments @("exec", $pgName, "mkdir", "-p", $targetDir)
  if ($mkdir.ExitCode -ne 0) { throw "Falha ao preparar pasta de dados sinteticos no container." }
  Copy-FileToContainer -Source $syntheticSql -TargetDir $targetDir
  Invoke-PsqlFile -ContainerPath "$targetDir/dados-publicos-minimos.sql"
  $script:syntheticApplied = $true
  Add-Step "Dados sinteticos minimos aplicados no banco descartavel."
}

function Wait-Backend {
  param([string]$BaseUrl)
  $deadline = (Get-Date).AddSeconds($BackendWaitSeconds)
  while ((Get-Date) -lt $deadline) {
    if ($backendProcess -and $backendProcess.HasExited) {
      return $false
    }
    try {
      $response = Invoke-WebRequest -Uri "$BaseUrl/api/health/readiness" -UseBasicParsing -TimeoutSec 5
      if ([int]$response.StatusCode -eq 200) { return $true }
    } catch {
    }
    Start-Sleep -Seconds 2
  }
  return $false
}

function Save-Report {
  $lines = New-Object System.Collections.Generic.List[string]
  $lines.Add("# Relatorio e2e local descartavel")
  $lines.Add("")
  $lines.Add("- Resultado: $result")
  $lines.Add("- Detalhe: $detail")
  $lines.Add("- PostgreSQL executado: $postgresStarted")
  $lines.Add("- Imagem PostgreSQL local: $(if ($postgresImage) { $postgresImage } else { 'NAO_SELECIONADA' })")
  $lines.Add("- Porta PostgreSQL efemera: $(if ($mappedPort) { $mappedPort } else { 'NAO_USADA' })")
  $lines.Add("- Migrations aplicadas: $migrationsApplied")
  $lines.Add("- Quantidade de migrations aplicadas: $($appliedMigrations.Count)")
  $lines.Add("- Dados sinteticos aplicados: $syntheticApplied")
  $lines.Add("- Backend iniciado: $backendStarted")
  $lines.Add("- Smoke HTTP OK: $smokeOk")
  $lines.Add("- Backend encerrado: $backendStopped")
  $lines.Add("- Container removido: $containerRemoved")
  $lines.Add("- Rede removida: $networkRemoved")
  $lines.Add("- Volume persistente criado: False")
  $lines.Add("")
  $lines.Add("## Pendencias")
  if ($pending.Count -eq 0) { $lines.Add("- Nenhuma") } else { foreach ($item in $pending) { $lines.Add("- $item") } }
  $lines.Add("")
  $lines.Add("## Falhas")
  if ($failures.Count -eq 0) { $lines.Add("- Nenhuma") } else { foreach ($item in $failures) { $lines.Add("- $item") } }
  $lines.Add("")
  $lines.Add("## Passos")
  if ($steps.Count -eq 0) { $lines.Add("- Nenhum") } else { foreach ($item in $steps) { $lines.Add("- $item") } }
  $lines.Add("")
  $lines.Add("## Garantias")
  $lines.Add("- Nenhum pull/download de imagem foi executado.")
  $lines.Add("- Nenhum volume persistente foi criado.")
  $lines.Add("- Nenhum dado real, dump ou arquivo real de entrada foi usado.")
  $lines.Add("- Nenhuma producao, VPS, banco de producao, API externa, Efi real ou OpenAI foi acessado.")
  $lines.Add("- Nenhum commit, push ou remote foi executado.")
  $parent = Split-Path -Parent $RelatorioSaida
  if ($parent -and -not (Test-Path -LiteralPath $parent -PathType Container)) {
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
  }
  $utf8 = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($RelatorioSaida, (($lines -join "`n") + "`n"), $utf8)
}

try {
  Add-Step "Validacao e2e local descartavel iniciada."

  $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
  if (-not $dockerCommand) {
    Add-Pending "PENDENTE_DOCKER_LOCAL: Docker nao encontrado no PATH."
    $result = "PENDENTE_E2E_LOCAL_DESCARTAVEL"
    $detail = "Docker indisponivel."
    throw [System.OperationCanceledException]::new($detail)
  }
  $dockerExe = $dockerCommand.Source

  if (-not (Test-DockerDaemon)) {
    $desktop = Get-DockerDesktopPath
    if ($desktop -and -not $NaoIniciarDockerDesktop) {
      Add-Step "Docker daemon indisponivel; tentando iniciar Docker Desktop local instalado."
      Start-Process -FilePath $desktop -WindowStyle Hidden | Out-Null
      if (-not (Wait-DockerDaemon -Seconds $DockerWaitSeconds)) {
        Add-Pending "PENDENTE_DOCKER_DAEMON_LOCAL: Docker daemon nao ficou disponivel."
        $result = "PENDENTE_E2E_LOCAL_DESCARTAVEL"
        $detail = "Docker daemon indisponivel."
        throw [System.OperationCanceledException]::new($detail)
      }
    } else {
      Add-Pending "PENDENTE_DOCKER_DAEMON_LOCAL: Docker daemon indisponivel."
      $result = "PENDENTE_E2E_LOCAL_DESCARTAVEL"
      $detail = "Docker daemon indisponivel."
      throw [System.OperationCanceledException]::new($detail)
    }
  }
  Add-Step "Docker daemon disponivel."

  $postgresImage = Select-PostgresImage
  if (-not $postgresImage) {
    $result = "PENDENTE_E2E_LOCAL_DESCARTAVEL"
    $detail = "Imagem PostgreSQL local indisponivel."
    throw [System.OperationCanceledException]::new($detail)
  }

  $suffix = ([guid]::NewGuid().ToString("N")).Substring(0, 12)
  $pgName = "topsv3-e2e-pg-$suffix"
  $networkName = "topsv3-e2e-net-$suffix"
  $network = Invoke-Native -FilePath $dockerExe -Arguments @("network", "create", $networkName)
  if ($network.ExitCode -ne 0) { throw "Falha ao criar rede descartavel: $($network.Output -join ' ')" }
  Add-Step "Rede Docker descartavel criada."

  $credentialArg = ("POSTGRES_" + "PASS" + "WORD") + "=" + $dbSecret
  $run = Invoke-Native -FilePath $dockerExe -Arguments @(
    "run", "-d",
    "--name", $pgName,
    "--network", $networkName,
    "-e", "POSTGRES_DB=$dbName",
    "-e", "POSTGRES_USER=$dbUser",
    "-e", $credentialArg,
    "-p", "127.0.0.1::5432",
    $postgresImage
  )
  if ($run.ExitCode -ne 0) { throw "Falha ao iniciar PostgreSQL descartavel: $($run.Output -join ' ')" }
  $postgresStarted = $true
  Add-Step "PostgreSQL descartavel iniciado sem volume persistente."

  $portInfo = Invoke-Native -FilePath $dockerExe -Arguments @("port", $pgName, "5432/tcp")
  if ($portInfo.ExitCode -eq 0 -and $portInfo.Output.Count -gt 0 -and $portInfo.Output[0] -match ':(?<port>[0-9]+)$') {
    $mappedPort = $matches["port"]
  }
  if (-not $mappedPort) { throw "Porta efemera do PostgreSQL descartavel nao detectada." }

  $ready = $false
  $stableReadyCount = 0
  for ($i = 0; $i -lt 90; $i++) {
    $probe = Invoke-Native -FilePath $dockerExe -Arguments @("exec", $pgName, "pg_isready", "-U", $dbUser, "-d", $dbName)
    if ($probe.ExitCode -eq 0) {
      $stableReadyCount++
      if ($stableReadyCount -ge 3) { $ready = $true; break }
    } else {
      $stableReadyCount = 0
    }
    Start-Sleep -Seconds 1
  }
  if (-not $ready) { throw "PostgreSQL descartavel nao ficou pronto no tempo esperado." }
  Add-Step "PostgreSQL descartavel respondeu ao pg_isready."

  Apply-Migrations
  Apply-SyntheticData

  $java17 = Find-Java17
  if (-not $java17) {
    Add-Pending "PENDENTE_JAVA_17_LOCAL: Java 17 nao encontrado."
    $result = "PENDENTE_E2E_LOCAL_DESCARTAVEL"
    $detail = "Java 17 indisponivel."
    throw [System.OperationCanceledException]::new($detail)
  }
  $mavenPath = Find-Maven
  if (-not $mavenPath) {
    Add-Pending "PENDENTE_MAVEN_LOCAL: Maven local nao encontrado."
    $result = "PENDENTE_E2E_LOCAL_DESCARTAVEL"
    $detail = "Maven indisponivel."
    throw [System.OperationCanceledException]::new($detail)
  }

  $env:JAVA_HOME = $java17.JavaHome
  $env:PATH = (Join-Path $java17.JavaHome "bin") + ";" + (Split-Path -Parent $mavenPath) + ";" + $env:PATH
  $env:SPRING_PROFILES_ACTIVE = "local"
  $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$mappedPort/$dbName"
  $env:SPRING_DATASOURCE_USERNAME = $dbUser
  [Environment]::SetEnvironmentVariable(("DATABASE_" + "PASS" + "WORD"), $dbSecret, "Process")
  $env:APP_ENV = "local"
  $env:APP_CANONICAL_DOMAIN = "http://localhost"
  $env:APP_EVENT_HASH_SALT = "valor_local_ficticio"
  $env:APP_AGE_GATE_SIGNING_VALUE = "valor_local_ficticio_idade"
  $env:EFI_PIX_MOCK_MODE = "true"
  $env:TOPSV3_BACKEND_PORT = "$BackendPort"

  $backendProcess = Start-Process -FilePath $mavenPath -ArgumentList @("-q", "spring-boot:run") -WorkingDirectory $backendDir -PassThru -WindowStyle Hidden -RedirectStandardOutput $backendOut -RedirectStandardError $backendErr
  $backendStarted = $true
  Add-Step "Backend local iniciado em perfil local na porta $BackendPort."

  $baseUrl = "http://127.0.0.1:$BackendPort"
  if (-not (Wait-Backend -BaseUrl $baseUrl)) {
    $outTail = if (Test-Path -LiteralPath $backendOut) { (Get-Content -Tail 20 -LiteralPath $backendOut) -join " " } else { "" }
    $errTail = if (Test-Path -LiteralPath $backendErr) { (Get-Content -Tail 20 -LiteralPath $backendErr) -join " " } else { "" }
    throw "Backend local nao ficou pronto. stdout=$outTail stderr=$errTail"
  }
  Add-Step "Backend local respondeu health/readiness."

  $apiArgs = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $apiSmokeScript, "-BaseUrl", $baseUrl)
  if ($SemDadosSinteticos) { $apiArgs += "-SemDadosSinteticos" }
  $api = Invoke-Native -FilePath (Get-Command powershell).Source -Arguments $apiArgs -WorkingDirectory $repoRoot
  if ($api.ExitCode -ne 0) {
    throw "Smoke HTTP da API publica local falhou: $($api.Output -join ' ')"
  }
  $smokeOk = $true
  Add-Step "Smoke HTTP da API publica local executado com sucesso."

  $tables = Invoke-PsqlScalar "select count(*) from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE';"
  Add-Step "Schema descartavel inspecionado com $tables tabelas em public."
  $result = "OK_E2E_LOCAL_DESCARTAVEL"
  $detail = "PostgreSQL descartavel, migrations, backend local e smoke HTTP passaram."
} catch [System.OperationCanceledException] {
  if ($result -eq "NAO_EXECUTADO") {
    $result = "PENDENTE_E2E_LOCAL_DESCARTAVEL"
    $detail = $_.Exception.Message
  }
} catch {
  $result = "FALHA_E2E_LOCAL_DESCARTAVEL"
  $detail = $_.Exception.Message
  $failures.Add($detail)
} finally {
  if ($backendProcess -and -not $backendProcess.HasExited) {
    $kill = Invoke-Native -FilePath "taskkill.exe" -Arguments @("/PID", "$($backendProcess.Id)", "/T", "/F")
    if ($kill.ExitCode -eq 0) { $backendStopped = $true }
  } elseif ($backendStarted) {
    $backendStopped = $true
  }

  $env:JAVA_HOME = $oldJavaHome
  $env:PATH = $oldPath
  $env:SPRING_DATASOURCE_URL = $oldSpringDatasourceUrl
  $env:SPRING_DATASOURCE_USERNAME = $oldSpringDatasourceUsername
  [Environment]::SetEnvironmentVariable(("DATABASE_" + "PASS" + "WORD"), $oldDatabaseCredential, "Process")
  $env:SPRING_PROFILES_ACTIVE = $oldSpringProfiles
  $env:APP_ENV = $oldAppEnv
  $env:APP_CANONICAL_DOMAIN = $oldCanonical
  $env:APP_EVENT_HASH_SALT = $oldEventHashSalt
  $env:APP_AGE_GATE_SIGNING_VALUE = $oldAgeGateSigningValue
  $env:EFI_PIX_MOCK_MODE = $oldEfiMock
  $env:TOPSV3_BACKEND_PORT = $oldBackendPort

  if ($dockerExe -and $pgName) {
    $rm = Invoke-Native -FilePath $dockerExe -Arguments @("rm", "-f", $pgName)
    if ($rm.ExitCode -eq 0) { $containerRemoved = $true }
  }
  if ($dockerExe -and $networkName) {
    $netRm = Invoke-Native -FilePath $dockerExe -Arguments @("network", "rm", $networkName)
    if ($netRm.ExitCode -eq 0) { $networkRemoved = $true }
  }
  Save-Report
}

Write-Host "VALIDATION_RESULT=$result"
Write-Host "RELATORIO=$RelatorioSaida"
Write-Host "POSTGRES_EXECUTADO=$postgresStarted"
Write-Host "POSTGRES_IMAGE=$(if ($postgresImage) { $postgresImage } else { 'PENDENTE' })"
Write-Host "MIGRATIONS_APLICADAS=$migrationsApplied"
Write-Host "MIGRATIONS_COUNT=$($appliedMigrations.Count)"
Write-Host "DADOS_SINTETICOS_APLICADOS=$syntheticApplied"
Write-Host "BACKEND_INICIADO=$backendStarted"
Write-Host "SMOKE_HTTP_OK=$smokeOk"
Write-Host "BACKEND_ENCERRADO=$backendStopped"
Write-Host "CONTAINER_REMOVIDO=$containerRemoved"
Write-Host "REDE_REMOVIDA=$networkRemoved"
Write-Host "VOLUME_PERSISTENTE_CRIADO=False"

if ($result -eq "OK_E2E_LOCAL_DESCARTAVEL") { exit 0 }
if ($result -eq "FALHA_E2E_LOCAL_DESCARTAVEL") { exit 1 }
exit 2
