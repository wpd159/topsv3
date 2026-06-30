Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-TopsLocalRepoRoot {
  $result = & git rev-parse --show-toplevel 2>$null
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($result)) {
    Write-Host "ERRO: execute este script dentro do repositório Git do Tops do Job V3."
    exit 2
  }
  return ($result.Trim() -replace '\\', '/')
}

function Assert-TopsDockerAvailable {
  $docker = Get-Command docker -ErrorAction SilentlyContinue
  if (-not $docker) {
    Write-Host "ERRO: Docker não encontrado no PATH. Instale e configure manualmente antes de usar a infraestrutura local."
    exit 2
  }
}

function Initialize-TopsLocalRuntimeDirectories {
  param([string]$RepoRoot)
  foreach ($relative in @("storage-local/postgres", "storage-local/minio", "storage-local/mailpit")) {
    $path = Join-Path $RepoRoot ($relative -replace '/', [IO.Path]::DirectorySeparatorChar)
    New-Item -ItemType Directory -Path $path -Force | Out-Null
  }
}

function Get-TopsLocalComposeArguments {
  param(
    [string]$RepoRoot,
    [string[]]$CommandArguments
  )
  $composeFile = Join-Path $RepoRoot "infra/local/docker-compose.local.yml"
  $arguments = @("compose", "--project-name", "topsv3-local")
  $envCandidates = @(
    (Join-Path $RepoRoot "infra/local/.env.local"),
    (Join-Path $RepoRoot ".env.local"),
    (Join-Path $RepoRoot "infra/local/.env.local.example")
  )
  foreach ($envFile in $envCandidates) {
    if (Test-Path -LiteralPath $envFile -PathType Leaf) {
      $arguments += @("--env-file", $envFile)
      break
    }
  }
  $arguments += @("-f", $composeFile)
  $arguments += $CommandArguments
  return $arguments
}

function Invoke-TopsLocalDockerCompose {
  param(
    [string]$RepoRoot,
    [string[]]$CommandArguments
  )
  Assert-TopsDockerAvailable
  $arguments = Get-TopsLocalComposeArguments -RepoRoot $RepoRoot -CommandArguments $CommandArguments
  & docker @arguments
  return $LASTEXITCODE
}
