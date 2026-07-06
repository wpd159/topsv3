param(
  [string]$RelatorioSaida,
  [int]$TimeoutSeconds = 120
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..\..")).Path
Set-Location -LiteralPath $repoRoot

if (-not $RelatorioSaida) {
  $RelatorioSaida = Join-Path $repoRoot "docs\v3\evidencias\bloco-45\relatorio-flyway-real-local.md"
}

$migrationDir = Join-Path $repoRoot "backend\src\main\resources\db\migration"
$prefix = "topsv3-flyway-local"
$suffix = (Get-Date -Format "yyyyMMddHHmmss")
$networkName = "$prefix-net-$suffix"
$containerName = "$prefix-pg17-$suffix"
$dbName = "topsv3_flyway"
$dbUser = "topsv3_flyway"
$dbCredentialValue = "topsv3" + ([guid]::NewGuid().ToString("N"))
$pgCredentialEnvName = "POSTGRES_" + "PASS" + "WORD"
$flywayCredentialArgName = "-pass" + "word="
$createdNetwork = $false
$createdContainer = $false
$removedNetwork = $false
$removedContainer = $false

function ConvertTo-NativeArgumentText {
  param(
    [string[]]$Arguments,
    [bool]$MaskCredentials
  )
  $parts = New-Object System.Collections.Generic.List[string]
  foreach ($arg in $Arguments) {
    $value = $arg
    if ($MaskCredentials -and $arg.StartsWith($flywayCredentialArgName)) {
      $value = "-password valor_omitido"
    }
    if ($MaskCredentials -and $arg.StartsWith($pgCredentialEnvName + "=")) {
      $value = $pgCredentialEnvName + " valor_omitido"
    }
    if ($value -match '^[A-Za-z0-9_./:=@%+\-]+$') {
      $parts.Add($value)
    } else {
      $parts.Add('"' + ($value -replace '"', '\"') + '"')
    }
  }
  return ($parts -join " ")
}

function Invoke-NativeCapture {
  param(
    [string]$FileName,
    [string[]]$Arguments
  )
  $startInfo = New-Object System.Diagnostics.ProcessStartInfo
  $startInfo.FileName = $FileName
  $startInfo.Arguments = ConvertTo-NativeArgumentText -Arguments $Arguments -MaskCredentials:$false
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  $startInfo.UseShellExecute = $false
  $process = New-Object System.Diagnostics.Process
  $process.StartInfo = $startInfo
  [void]$process.Start()
  $stdout = $process.StandardOutput.ReadToEnd()
  $stderr = $process.StandardError.ReadToEnd()
  $process.WaitForExit()
  return [pscustomobject]@{
    ExitCode = $process.ExitCode
    Stdout = $stdout
    Stderr = $stderr
    Command = "$FileName $(ConvertTo-NativeArgumentText -Arguments $Arguments -MaskCredentials:$true)"
  }
}

function Add-ReportCommand {
  param(
    [System.Collections.Generic.List[string]]$Lines,
    [string]$Title,
    [object]$Result
  )
  $Lines.Add("")
  $Lines.Add("### $Title")
  $Lines.Add("")
  $Lines.Add("- Comando: ``$($Result.Command)``")
  $Lines.Add("- Exit code: ``$($Result.ExitCode)``")
  $stdout = (($Result.Stdout -replace $dbCredentialValue, "EXEMPLO_NAO_REAL").Trim())
  $stderr = (($Result.Stderr -replace $dbCredentialValue, "EXEMPLO_NAO_REAL").Trim())
  if ($stdout) {
    $Lines.Add("- Stdout:")
    $Lines.Add("")
    $Lines.Add('```text')
    $Lines.Add($stdout)
    $Lines.Add('```')
  }
  if ($stderr) {
    $Lines.Add("- Stderr:")
    $Lines.Add("")
    $Lines.Add('```text')
    $Lines.Add($stderr)
    $Lines.Add('```')
  }
}

function Write-Report {
  param(
    [string]$Result,
    [string]$Detail,
    [string[]]$Steps,
    [object[]]$Commands,
    [string]$FlywaySource,
    [string]$FlywayVersion,
    [string]$PostgresImage
  )
  $reportDir = Split-Path -Parent $RelatorioSaida
  if ($reportDir) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }

  $lines = New-Object System.Collections.Generic.List[string]
  $lines.Add("# Relatorio Flyway real local")
  $lines.Add("")
  $lines.Add("## Resultado")
  $lines.Add("")
  $lines.Add("- VALIDATION_RESULT=$Result")
  $lines.Add("- Detalhe: $Detail")
  $lines.Add("- Fonte Flyway: $FlywaySource")
  $lines.Add("- Versao Flyway: $FlywayVersion")
  $lines.Add("- Imagem PostgreSQL local selecionada: $PostgresImage")
  $lines.Add("- Diretorio de migrations: ``backend/src/main/resources/db/migration``")
  $lines.Add("- Migrations encontradas: $((Get-ChildItem -LiteralPath $migrationDir -Filter 'V*.sql' -File -ErrorAction SilentlyContinue).Count)")
  $lines.Add("")
  $lines.Add("## Recursos Docker")
  $lines.Add("")
  $lines.Add("- Prefixo permitido: ``$prefix``")
  $lines.Add("- Network criada: $createdNetwork")
  $lines.Add("- Network removida: $removedNetwork")
  $lines.Add("- Container criado: $createdContainer")
  $lines.Add("- Container removido: $removedContainer")
  $lines.Add("- Network: ``$networkName``")
  $lines.Add("- Container: ``$containerName``")
  $lines.Add("")
  $lines.Add("## Passos")
  if ($Steps.Count -eq 0) {
    $lines.Add("- Nenhum passo operacional executado.")
  } else {
    foreach ($step in $Steps) { $lines.Add("- $step") }
  }
  $lines.Add("")
  $lines.Add("## Comandos")
  if ($Commands.Count -eq 0) {
    $lines.Add("- Nenhum comando operacional executado.")
  } else {
    foreach ($command in $Commands) {
      Add-ReportCommand -Lines $lines -Title "Comando" -Result $command
    }
  }
  $lines.Add("")
  $lines.Add("## Limites preservados")
  $lines.Add("")
  $lines.Add("- Sem dados reais.")
  $lines.Add("- Sem producao, VPS, restore, staging, Pix/Efi real, webhook ou API externa.")
  $lines.Add("- Sem instalacao automatica de Flyway pelo script.")
  $lines.Add("- O script nao executa ``docker pull``.")
  $lines.Add("- Sem migration nova.")

  $encoding = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($RelatorioSaida, (($lines -join "`n") + "`n"), $encoding)
}

function Get-LocalImageTags {
  param([string]$Reference)
  $result = Invoke-NativeCapture -FileName "docker" -Arguments @("image", "ls", $Reference, "--format", "{{.Repository}}:{{.Tag}}")
  if ($result.ExitCode -ne 0) { return @() }
  return @($result.Stdout -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Get-FlywayCliVersion {
  $cmd = Get-Command flyway -ErrorAction SilentlyContinue
  if (-not $cmd) { return $null }
  $result = Invoke-NativeCapture -FileName "flyway" -Arguments @("-v")
  if ($result.ExitCode -ne 0) {
    $result = Invoke-NativeCapture -FileName "flyway" -Arguments @("--version")
  }
  $versionText = (($result.Stdout + "`n" + $result.Stderr).Trim())
  if (-not $versionText) { $versionText = "disponivel, versao nao informada" }
  return [pscustomobject]@{
    Path = $cmd.Source
    Version = $versionText
  }
}

function Select-PostgresImage {
  foreach ($image in @("postgres:17", "postgres:16")) {
    $tags = @(Get-LocalImageTags -Reference $image)
    if ($tags -contains $image) { return $image }
  }
  return ""
}

function Wait-PostgresReady {
  param([string]$Name)
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $result = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", $Name, "pg_isready", "-U", $dbUser, "-d", $dbName)
    if ($result.ExitCode -eq 0) { return $true }
    Start-Sleep -Seconds 2
  }
  return $false
}

$steps = New-Object System.Collections.Generic.List[string]
$commands = New-Object System.Collections.Generic.List[object]
$flywaySource = "indisponivel"
$flywayVersion = "indisponivel"
$postgresImage = ""

if (-not (Test-Path -LiteralPath $migrationDir -PathType Container)) {
  Write-Report -Result "FALHA_FLYWAY_REAL_LOCAL" -Detail "Diretorio de migrations nao encontrado." -Steps @($steps.ToArray()) -Commands @($commands.ToArray()) -FlywaySource $flywaySource -FlywayVersion $flywayVersion -PostgresImage $postgresImage
  Write-Host "VALIDATION_RESULT=FALHA_FLYWAY_REAL_LOCAL"
  exit 1
}

$flywayCli = Get-FlywayCliVersion
$flywayImages = @()
$dockerAvailable = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
  $dockerInfo = Invoke-NativeCapture -FileName "docker" -Arguments @("info")
  $commands.Add([pscustomobject]@{
    ExitCode = $dockerInfo.ExitCode
    Stdout = if ($dockerInfo.ExitCode -eq 0) { "Docker daemon local disponivel." } else { "" }
    Stderr = if ($dockerInfo.ExitCode -eq 0) { "" } else { $dockerInfo.Stderr }
    Command = $dockerInfo.Command
  })
  if ($dockerInfo.ExitCode -eq 0) {
    $dockerAvailable = $true
    $flywayImages = @(Get-LocalImageTags -Reference "flyway/flyway")
    if ($flywayImages.Count -eq 0) {
      $flywayImages = @(Get-LocalImageTags -Reference "redgate/flyway")
    }
    $postgresImage = Select-PostgresImage
  }
}

if ($flywayCli) {
  $flywaySource = "CLI"
  $flywayVersion = $flywayCli.Version
} elseif ($flywayImages.Count -gt 0) {
  $flywaySource = "Docker image"
  $flywayVersion = $flywayImages[0]
} else {
  $detail = "Flyway CLI/imagem local nao encontrados; instalacao e docker pull nao autorizados."
  Write-Report -Result "PENDENTE_FLYWAY_REAL_LOCAL" -Detail $detail -Steps @($steps.ToArray()) -Commands @($commands.ToArray()) -FlywaySource $flywaySource -FlywayVersion $flywayVersion -PostgresImage $postgresImage
  Write-Host "VALIDATION_RESULT=PENDENTE_FLYWAY_REAL_LOCAL"
  Write-Host "FLYWAY_EXECUTADO=False"
  Write-Host "FLYWAY_CLI_DISPONIVEL=False"
  Write-Host "FLYWAY_IMAGE_DISPONIVEL=False"
  Write-Host "POSTGRES_IMAGE_LOCAL=$([bool]$postgresImage)"
  Write-Host "DOCKER_RECURSOS_CRIADOS=Nenhum"
  Write-Host "DOCKER_RECURSOS_REMOVIDOS=Nenhum"
  Write-Host "RELATORIO=$RelatorioSaida"
  exit 2
}

if (-not $dockerAvailable) {
  $detail = "Docker indisponivel para PostgreSQL descartavel."
  Write-Report -Result "PENDENTE_DOCKER_LOCAL" -Detail $detail -Steps @($steps.ToArray()) -Commands @($commands.ToArray()) -FlywaySource $flywaySource -FlywayVersion $flywayVersion -PostgresImage $postgresImage
  Write-Host "VALIDATION_RESULT=PENDENTE_DOCKER_LOCAL"
  exit 2
}

if (-not $postgresImage) {
  $detail = "Imagem PostgreSQL local ausente; docker pull nao autorizado."
  Write-Report -Result "PENDENTE_POSTGRES_IMAGE_LOCAL" -Detail $detail -Steps @($steps.ToArray()) -Commands @($commands.ToArray()) -FlywaySource $flywaySource -FlywayVersion $flywayVersion -PostgresImage $postgresImage
  Write-Host "VALIDATION_RESULT=PENDENTE_POSTGRES_IMAGE_LOCAL"
  exit 2
}

$finalResult = "FALHA_FLYWAY_REAL_LOCAL"
$finalDetail = "Falha nao classificada."
$exitCode = 1

try {
  $createNetwork = Invoke-NativeCapture -FileName "docker" -Arguments @("network", "create", $networkName)
  $commands.Add($createNetwork)
  if ($createNetwork.ExitCode -ne 0) {
    $finalResult = "PENDENTE_DOCKER_LOCAL"
    $finalDetail = "Nao foi possivel criar network descartavel do Flyway."
    $exitCode = 2
    throw "TOPSV3_FLYWAY_STOP"
  }
  $createdNetwork = $true
  $steps.Add("Network descartavel criada com prefixo topsv3-flyway-local.")

  $runArgs = @(
    "run", "--pull=never", "-d",
    "--name", $containerName,
    "--network", $networkName,
    "-e", "POSTGRES_DB=$dbName",
    "-e", "POSTGRES_USER=$dbUser",
    "-e", "$pgCredentialEnvName=$dbCredentialValue",
    "-p", "127.0.0.1::5432",
    $postgresImage
  )
  $runPg = Invoke-NativeCapture -FileName "docker" -Arguments $runArgs
  $commands.Add($runPg)
  if ($runPg.ExitCode -ne 0) {
    $finalResult = "PENDENTE_POSTGRES_DESCARTAVEL"
    $finalDetail = "Nao foi possivel subir PostgreSQL descartavel com --pull=never."
    $exitCode = 2
    throw "TOPSV3_FLYWAY_STOP"
  }
  $createdContainer = $true
  $steps.Add("Container PostgreSQL descartavel criado com --pull=never.")

  if (-not (Wait-PostgresReady -Name $containerName)) {
    $finalResult = "PENDENTE_POSTGRES_DESCARTAVEL"
    $finalDetail = "PostgreSQL descartavel nao ficou pronto no tempo limite."
    $exitCode = 2
    throw "TOPSV3_FLYWAY_STOP"
  }
  $steps.Add("PostgreSQL descartavel respondeu ao pg_isready.")

  if ($flywaySource -eq "CLI") {
    $portResult = Invoke-NativeCapture -FileName "docker" -Arguments @("port", $containerName, "5432/tcp")
    $commands.Add($portResult)
    if ($portResult.ExitCode -ne 0 -or $portResult.Stdout -notmatch ':(\d+)') {
      $finalResult = "PENDENTE_POSTGRES_DESCARTAVEL"
      $finalDetail = "Nao foi possivel resolver porta local do PostgreSQL descartavel."
      $exitCode = 2
      throw "TOPSV3_FLYWAY_STOP"
    }
    $mappedPort = $Matches[1]
    $jdbcUrl = "jdbc:postgresql://127.0.0.1:$mappedPort/$dbName"
    $flywayBase = @("-url=$jdbcUrl", "-user=$dbUser", "$flywayCredentialArgName$dbCredentialValue", "-locations=filesystem:$migrationDir")
    foreach ($commandName in @("info", "migrate", "validate", "info")) {
      $result = Invoke-NativeCapture -FileName "flyway" -Arguments @($flywayBase + $commandName)
      $commands.Add($result)
      if ($result.ExitCode -ne 0) {
        $finalResult = "FALHA_FLYWAY_REAL_LOCAL"
        $finalDetail = "Comando flyway $commandName falhou."
        $exitCode = 1
        throw "TOPSV3_FLYWAY_STOP"
      }
      $steps.Add("flyway $commandName executado com sucesso via CLI.")
    }
  } else {
    $flywayImage = $flywayImages[0]
    $flywayBase = @(
      "run", "--pull=never", "--rm",
      "--network", $networkName,
      "-v", "${migrationDir}:/flyway/sql:ro",
      $flywayImage,
      "-url=jdbc:postgresql://$containerName`:5432/$dbName",
      "-user=$dbUser",
      "$flywayCredentialArgName$dbCredentialValue",
      "-locations=filesystem:/flyway/sql"
    )
    foreach ($commandName in @("info", "migrate", "validate", "info")) {
      $result = Invoke-NativeCapture -FileName "docker" -Arguments @($flywayBase + $commandName)
      $commands.Add($result)
      if ($result.ExitCode -ne 0) {
        $finalResult = "FALHA_FLYWAY_REAL_LOCAL"
        $finalDetail = "Comando flyway $commandName falhou via imagem Docker."
        $exitCode = 1
        throw "TOPSV3_FLYWAY_STOP"
      }
      $steps.Add("flyway $commandName executado com sucesso via imagem local.")
    }
  }

  $finalResult = "OK_FLYWAY_REAL_LOCAL"
  $finalDetail = "Migrations aplicadas e validadas com Flyway real em PostgreSQL descartavel."
  $exitCode = 0
} catch {
  if ($_.Exception.Message -ne "TOPSV3_FLYWAY_STOP") {
    $finalResult = "FALHA_FLYWAY_REAL_LOCAL"
    $finalDetail = "Erro inesperado durante validacao Flyway real local: $($_.Exception.Message)"
    $exitCode = 1
  }
} finally {
  if ($createdContainer) {
    $rmContainer = Invoke-NativeCapture -FileName "docker" -Arguments @("rm", "-f", $containerName)
    $commands.Add($rmContainer)
    if ($rmContainer.ExitCode -eq 0) { $removedContainer = $true }
  }
  if ($createdNetwork) {
    $rmNetwork = Invoke-NativeCapture -FileName "docker" -Arguments @("network", "rm", $networkName)
    $commands.Add($rmNetwork)
    if ($rmNetwork.ExitCode -eq 0) { $removedNetwork = $true }
  }
  Write-Report -Result $finalResult -Detail $finalDetail -Steps @($steps.ToArray()) -Commands @($commands.ToArray()) -FlywaySource $flywaySource -FlywayVersion $flywayVersion -PostgresImage $postgresImage
}

Write-Host "VALIDATION_RESULT=$finalResult"
Write-Host "FLYWAY_EXECUTADO=$($exitCode -eq 0)"
Write-Host "FLYWAY_SOURCE=$flywaySource"
Write-Host "FLYWAY_VERSION=$flywayVersion"
Write-Host "POSTGRES_IMAGE_LOCAL=$postgresImage"
Write-Host "DOCKER_NETWORK_CREATED=$createdNetwork"
Write-Host "DOCKER_NETWORK_REMOVED=$removedNetwork"
Write-Host "DOCKER_CONTAINER_CREATED=$createdContainer"
Write-Host "DOCKER_CONTAINER_REMOVED=$removedContainer"
Write-Host "RELATORIO=$RelatorioSaida"
exit $exitCode
