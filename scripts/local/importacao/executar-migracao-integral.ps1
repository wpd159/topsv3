[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [ValidateSet(
    "PRODUZIR_MANIFESTO",
    "VALIDAR_MANIFESTO",
    "PRODUZIR_PACOTE",
    "VALIDAR_PACOTE",
    "EXECUTAR"
  )]
  [string]$Operacao,

  [string]$Pacote,

  [string]$DiretorioManifestos,

  [Parameter(Mandatory = $true)]
  [string]$OrigemId,

  [string]$ManifestoFaseCinco,

  [string]$SnapshotSha256,

  [string]$CapturadoEm,

  [string]$ExecucaoId,

  [ValidateSet("DRY_RUN", "APPLY")]
  [string]$Modo,

  [switch]$Retomar,

  [switch]$ConfirmarApply,

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
$repoPrefix = $repoRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$operacoesManifesto = @("PRODUZIR_MANIFESTO", "VALIDAR_MANIFESTO")
$operacoesPacote = @("PRODUZIR_PACOTE", "VALIDAR_PACOTE", "EXECUTAR")
$operacoesComBanco = @("PRODUZIR_MANIFESTO", "PRODUZIR_PACOTE", "EXECUTAR")

$pacotePath = $null
if ($Operacao -in $operacoesPacote) {
  if ([string]::IsNullOrWhiteSpace($Pacote)) {
    throw "Pacote obrigatorio para a operacao $Operacao."
  }
  $pacotePath = [IO.Path]::GetFullPath($Pacote)
  if ([IO.Path]::GetExtension($pacotePath) -ne ".json") {
    throw "O orquestrador aceita somente pacote integral JSON."
  }
  if ($pacotePath.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "O pacote real deve permanecer fora do repositorio."
  }
}

$manifestosPath = $null
$manifestosPrefix = $null
if ([string]::IsNullOrWhiteSpace($DiretorioManifestos)) {
  throw "Diretorio de manifests obrigatorio para a operacao $Operacao."
}
$manifestosPath = [IO.Path]::GetFullPath($DiretorioManifestos)
if (-not (Test-Path -LiteralPath $manifestosPath -PathType Container)) {
  throw "Diretorio de manifests nao encontrado."
}
$manifestosPrefix = $manifestosPath.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
if ($null -ne $pacotePath -and $pacotePath.StartsWith(
    $manifestosPrefix, [StringComparison]::OrdinalIgnoreCase)) {
  throw "O pacote nao pode pertencer ao diretorio de manifests."
}

$manifestoFaseCincoPath = $null
if ($Operacao -in @("PRODUZIR_MANIFESTO", "VALIDAR_MANIFESTO", "PRODUZIR_PACOTE")) {
  if ([string]::IsNullOrWhiteSpace($ManifestoFaseCinco)) {
    throw "ManifestoFaseCinco obrigatorio para a operacao $Operacao."
  }
  $manifestoFaseCincoPath = [IO.Path]::GetFullPath($ManifestoFaseCinco)
  if ([IO.Path]::GetExtension($manifestoFaseCincoPath) -ne ".json") {
    throw "O manifesto da Fase 5 deve ser JSON."
  }
  if ($manifestoFaseCincoPath.StartsWith(
      $repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "O manifesto real deve permanecer fora do repositorio."
  }
  if (-not $manifestoFaseCincoPath.StartsWith(
      $manifestosPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Manifesto da Fase 5 deve pertencer ao diretorio de manifests."
  }
}

if ($Operacao -eq "PRODUZIR_MANIFESTO") {
  foreach ($parametro in @("ManifestoFaseCinco", "SnapshotSha256", "CapturadoEm", "ExecucaoId")) {
    if ([string]::IsNullOrWhiteSpace((Get-Variable -Name $parametro -ValueOnly))) {
      throw "Parametro obrigatorio para PRODUZIR_MANIFESTO ausente: $parametro"
    }
  }
  if (Test-Path -LiteralPath $manifestoFaseCincoPath) {
    if (-not (Test-Path -LiteralPath $manifestoFaseCincoPath -PathType Leaf)) {
      throw "Destino do manifesto nao e um arquivo regular."
    }
  } elseif (-not (Test-Path -LiteralPath (
      [IO.Path]::GetDirectoryName($manifestoFaseCincoPath)) -PathType Container)) {
    throw "Diretorio de saida do manifesto nao existe."
  }
} elseif ($Operacao -eq "VALIDAR_MANIFESTO") {
  if ([string]::IsNullOrWhiteSpace($SnapshotSha256) -or
      -not (Test-Path -LiteralPath $manifestoFaseCincoPath -PathType Leaf)) {
    throw "Manifesto e hash do snapshot sao obrigatorios para VALIDAR_MANIFESTO."
  }
} elseif ($Operacao -eq "PRODUZIR_PACOTE") {
  foreach ($parametro in @("ManifestoFaseCinco", "SnapshotSha256", "CapturadoEm", "ExecucaoId")) {
    if ([string]::IsNullOrWhiteSpace((Get-Variable -Name $parametro -ValueOnly))) {
      throw "Parametro obrigatorio para PRODUZIR_PACOTE ausente: $parametro"
    }
  }
  if (-not (Test-Path -LiteralPath $manifestoFaseCincoPath -PathType Leaf)) {
    throw "Manifesto da Fase 5 nao encontrado."
  }
  if (Test-Path -LiteralPath $pacotePath) {
    if (-not (Test-Path -LiteralPath $pacotePath -PathType Leaf)) {
      throw "Destino do pacote nao e um arquivo regular."
    }
  } elseif (-not (Test-Path -LiteralPath ([IO.Path]::GetDirectoryName($pacotePath)) -PathType Container)) {
    throw "Diretorio de saida do pacote nao existe."
  }
} elseif (-not (Test-Path -LiteralPath $pacotePath -PathType Leaf)) {
  throw "Pacote integral nao encontrado."
}

if ($Operacao -eq "EXECUTAR") {
  if ([string]::IsNullOrWhiteSpace($Modo)) {
    throw "Modo obrigatorio para EXECUTAR."
  }
  if ([string]::IsNullOrWhiteSpace($ExecucaoId)) {
    throw "ExecucaoId obrigatorio para vincular a execucao ao pacote."
  }
  if ($Modo -eq "APPLY" -and -not $ConfirmarApply.IsPresent) {
    throw "APPLY exige -ConfirmarApply explicitamente."
  }
} elseif (-not [string]::IsNullOrWhiteSpace($Modo)) {
  throw "Modo somente pode ser informado na operacao EXECUTAR."
}

if ($Operacao -in $operacoesComBanco) {
  foreach ($variavel in @(
    "SPRING_DATASOURCE_URL",
    "SPRING_DATASOURCE_USERNAME",
    "SPRING_DATASOURCE_PASSWORD"
  )) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($variavel))) {
      throw "Configuracao obrigatoria ausente: $variavel"
    }
  }
}

if ($Operacao -in @("PRODUZIR_MANIFESTO", "PRODUZIR_PACOTE")) {
  $jdbcUrl = [Environment]::GetEnvironmentVariable("SPRING_DATASOURCE_URL")
  if ($jdbcUrl -notmatch '^jdbc:postgresql://(?<host>\[[^\]]+\]|[^/:]+)(?::\d+)?/') {
    throw "SPRING_DATASOURCE_URL nao e uma URL PostgreSQL valida."
  }
  $hostBanco = $Matches.host.Trim('[', ']')
  if ($hostBanco -notin @("localhost", "127.0.0.1", "::1")) {
    throw "$Operacao aceita somente snapshot restaurado em PostgreSQL de loopback."
  }
}

if ($Operacao -eq "PRODUZIR_MANIFESTO") {
  foreach ($variavel in @(
    "APP_MIGRACAO_INTEGRAL_STORAGE_FONTE_ENDPOINT",
    "APP_MIGRACAO_INTEGRAL_STORAGE_FONTE_ACCESS_KEY",
    "APP_MIGRACAO_INTEGRAL_STORAGE_FONTE_SIGNING_VALUE",
    "APP_MIGRACAO_INTEGRAL_STORAGE_FONTE_PUBLIC_MEDIA_BUCKET",
    "APP_MIGRACAO_INTEGRAL_STORAGE_FONTE_PRIVATE_MEDIA_BUCKET",
    "APP_MIGRACAO_INTEGRAL_STORAGE_FONTE_DOCUMENT_BUCKET",
    "APP_MIGRACAO_INTEGRAL_STORAGE_FONTE_PUBLIC_BASE_URL",
    "APP_MIGRACAO_INTEGRAL_STORAGE_DESTINO_PUBLIC_MEDIA_BUCKET",
    "APP_MIGRACAO_INTEGRAL_STORAGE_DESTINO_PRIVATE_MEDIA_BUCKET",
    "APP_MIGRACAO_INTEGRAL_STORAGE_DESTINO_DOCUMENT_BUCKET",
    "APP_MIGRACAO_INTEGRAL_STORAGE_DESTINO_PUBLIC_MEDIA_PREFIX",
    "APP_MIGRACAO_INTEGRAL_STORAGE_DESTINO_PRIVATE_MEDIA_PREFIX",
    "APP_MIGRACAO_INTEGRAL_STORAGE_DESTINO_DOCUMENT_PREFIX"
  )) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($variavel))) {
      throw "Configuracao externa obrigatoria ausente: $variavel"
    }
  }
}

if ($Operacao -in @(
  "PRODUZIR_MANIFESTO",
  "VALIDAR_MANIFESTO",
  "PRODUZIR_PACOTE",
  "VALIDAR_PACOTE",
  "EXECUTAR"
)) {
  foreach ($variavel in @(
    "APP_MIGRACAO_INTEGRAL_STORAGE_DESTINO_PUBLIC_MEDIA_BUCKET",
    "APP_MIGRACAO_INTEGRAL_STORAGE_DESTINO_PRIVATE_MEDIA_BUCKET",
    "APP_MIGRACAO_INTEGRAL_STORAGE_DESTINO_DOCUMENT_BUCKET"
  )) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($variavel))) {
      throw "Configuracao externa obrigatoria ausente: $variavel"
    }
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
  "--app.migracao.integral.operacao=$Operacao",
  "--app.migracao.integral.origem-id=$OrigemId",
  "--app.migracao.integral.tamanho-lote=$TamanhoLote"
)

if ($null -ne $pacotePath) {
  $runnerArguments += "--app.migracao.integral.pacote=`"$pacotePath`""
}
if ($null -ne $manifestosPath) {
  $runnerArguments += "--app.migracao.integral.diretorio-manifestos=`"$manifestosPath`""
}
if ($null -ne $manifestoFaseCincoPath) {
  $runnerArguments += "--app.migracao.integral.manifesto-fase-cinco=`"$manifestoFaseCincoPath`""
}

if ($Operacao -eq "PRODUZIR_MANIFESTO") {
  $runnerArguments += @(
    "--spring.flyway.enabled=false",
    "--spring.jpa.hibernate.ddl-auto=none",
    "--spring.sql.init.mode=never",
    "--app.migracao.integral.snapshot-sha256=$SnapshotSha256",
    "--app.migracao.integral.capturado-em=$CapturadoEm",
    "--app.migracao.integral.execucao-id=$ExecucaoId"
  )
}
if ($Operacao -eq "VALIDAR_MANIFESTO") {
  $runnerArguments += @(
    "--spring.flyway.enabled=false",
    "--spring.jpa.hibernate.ddl-auto=none",
    "--spring.sql.init.mode=never",
    "--app.migracao.integral.snapshot-sha256=$SnapshotSha256"
  )
}
if ($Operacao -eq "PRODUZIR_PACOTE") {
  $runnerArguments += @(
    "--spring.flyway.enabled=false",
    "--spring.jpa.hibernate.ddl-auto=none",
    "--spring.sql.init.mode=never",
    "--app.migracao.integral.snapshot-sha256=$SnapshotSha256",
    "--app.migracao.integral.capturado-em=$CapturadoEm",
    "--app.migracao.integral.execucao-id=$ExecucaoId"
  )
}
if ($Operacao -eq "VALIDAR_PACOTE") {
  $runnerArguments += @(
    "--spring.flyway.enabled=false",
    "--spring.jpa.hibernate.ddl-auto=none",
    "--spring.sql.init.mode=never"
  )
}
if ($Operacao -eq "EXECUTAR") {
  $runnerArguments += @(
    "--app.migracao.integral.execucao-id=$ExecucaoId",
    "--app.migracao.integral.modo=$Modo",
    "--app.migracao.integral.retomar=$($Retomar.IsPresent.ToString().ToLowerInvariant())",
    "--app.migracao.integral.confirmar-apply=$($ConfirmarApply.IsPresent.ToString().ToLowerInvariant())"
  )
}
$runnerArguments = $runnerArguments -join " "

Push-Location $backendDir
try {
  & $mavenPath -q spring-boot:run `
    "-Dspring-boot.run.main-class=br.com.topsdojob.v3.importacao.integracao.MigracaoIntegralApplication" `
    "-Dspring-boot.run.profiles=migracao-integral" `
    "-Dspring-boot.run.arguments=$runnerArguments"
  if ($LASTEXITCODE -ne 0) {
    throw "Execucao canonica da migracao integral falhou."
  }
} finally {
  Pop-Location
}
