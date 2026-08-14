[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$Pacote,

  [Parameter(Mandatory = $true)]
  [ValidateSet("DRY_RUN", "APPLY")]
  [string]$Modo,

  [ValidateRange(1, 10000)]
  [int]$TamanhoLote = 500
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  throw "Repositorio Git nao encontrado."
}

$repoRoot = [IO.Path]::GetFullPath($repoRoot)
$backendDir = Join-Path $repoRoot "backend"
$pacotePath = [IO.Path]::GetFullPath($Pacote)
if (-not (Test-Path -LiteralPath $pacotePath -PathType Leaf)) {
  throw "Pacote integral nao encontrado."
}
if ([IO.Path]::GetExtension($pacotePath) -ne ".json") {
  throw "O orquestrador aceita somente pacote integral JSON."
}
$repoPrefix = $repoRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
if ($pacotePath.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
  throw "O pacote real deve permanecer fora do repositorio."
}

foreach ($variavel in @(
  "SPRING_DATASOURCE_URL",
  "SPRING_DATASOURCE_USERNAME",
  "SPRING_DATASOURCE_PASSWORD"
)) {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($variavel))) {
    throw "Configuracao obrigatoria ausente: $variavel"
  }
}

$maven = Get-Command mvn -ErrorAction SilentlyContinue
if ($null -eq $maven) {
  $maven = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE ".m2\wrapper\dists") `
    -Recurse -File -Filter "mvn.cmd" -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending |
    Select-Object -First 1
}
if ($null -eq $maven) {
  throw "Maven nao encontrado."
}
$mavenPath = if ($maven.PSObject.Properties.Name -contains "Source") {
  $maven.Source
} else {
  $maven.FullName
}

$runnerArguments = @(
  "--spring.main.web-application-type=none",
  "--spring.task.scheduling.enabled=false",
  "--app.env=migracao-integral",
  "--app.outbox.email.enabled=false",
  "--app.storage.r2.enabled=false",
  "--efi.pix.enabled=false",
  "--efi.pix.reconciliation-enabled=false",
  "--efi.pix.webhook-registration-enabled=false",
  "--app.migracao.integral.enabled=true",
  "--app.migracao.integral.pacote=$pacotePath",
  "--app.migracao.integral.modo=$Modo",
  "--app.migracao.integral.tamanho-lote=$TamanhoLote"
) -join " "

Push-Location $backendDir
try {
  & $mavenPath -q spring-boot:run `
    "-Dspring-boot.run.profiles=migracao-integral" `
    "-Dspring-boot.run.arguments=$runnerArguments"
  if ($LASTEXITCODE -ne 0) {
    throw "Execucao canonica da migracao integral falhou."
  }
} finally {
  Pop-Location
}
