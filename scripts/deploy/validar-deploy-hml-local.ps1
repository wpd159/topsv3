Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "VALIDATION_RESULT=FALHA_DEPLOY_HML_LOCAL"
  Write-Host "Motivo: repositorio Git nao encontrado."
  exit 1
}

function Resolve-RepoPath {
  param([string]$Path)
  return (Join-Path $repoRoot ($Path -replace '/', [IO.Path]::DirectorySeparatorChar))
}

$checks = New-Object System.Collections.Generic.List[object]

function Add-Check {
  param(
    [string]$Nome,
    [bool]$Ok,
    [string]$Detalhe
  )
  $checks.Add([pscustomobject]@{ Nome = $Nome; Ok = $Ok; Detalhe = $Detalhe })
}

function Read-RequiredFile {
  param([string]$Path)
  $full = Resolve-RepoPath $Path
  Add-Check "arquivo existe: $Path" (Test-Path -LiteralPath $full -PathType Leaf) $full
  if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
    return ""
  }
  return [System.IO.File]::ReadAllText($full, [System.Text.UTF8Encoding]::new($false, $true))
}

$workflow = Read-RequiredFile ".github/workflows/deploy-hml.yml"
$compose = Read-RequiredFile "deploy/hml/docker-compose.yml"
$nginx = Read-RequiredFile "deploy/hml/nginx-v3-esle-cloud.conf"
$envExample = Read-RequiredFile "deploy/hml/hml.env.example"
$applicationHml = Read-RequiredFile "backend/src/main/resources/application-homologacao.yml"
$healthController = Read-RequiredFile "backend/src/main/java/br/com/topsdojob/v3/platform/health/HealthController.java"

foreach ($secret in @(
    "HML_HOST",
    "HML_USER",
    "HML_SSH_PORT",
    "HML_SSH_PRIVATE_KEY",
    "HML_DOMAIN",
    "HML_DEPLOY_PATH"
  )) {
  Add-Check "workflow referencia secret $secret" ($workflow -match [regex]::Escape("secrets.$secret")) "secret obrigatorio"
}

foreach ($exclude in @(
    ".git",
    "node_modules",
    ".next",
    "target",
    "topsv3-auditoria-local",
    "logs-brutos-nao-versionar",
    "*.dump",
    "**/*.dump",
    "*.backup",
    "**/*.backup",
    "*.log",
    "**/*.log",
    ".env",
    "**/.env",
    "*.pem",
    "**/*.pem",
    "*.key",
    "**/*.key",
    "*.crt",
    "**/*.crt"
  )) {
  Add-Check "workflow exclui $exclude" ($workflow -match [regex]::Escape($exclude)) "exclusao de pacote SSH"
}

Add-Check "workflow exige usuario topsv3" ($workflow -match 'test "\$\{HML_USER\}" = "topsv3"') "usuario HML"
Add-Check "workflow exige dominio v3.esle.cloud" ($workflow -match 'test "\$\{HML_DOMAIN\}" = "v3\.esle\.cloud"') "dominio HML"
Add-Check "workflow exige deploy path controlado" ($workflow -match '/opt/topsv3/app/current') "deploy path"
Add-Check "workflow falha sem hml.env externo" ($workflow -match 'test -f /opt/topsv3/secrets/hml\.env') "secrets externos"
Add-Check "workflow executa compose build" ($workflow -match 'docker compose .* build') "build"
Add-Check "workflow executa compose up" ($workflow -match 'docker compose .* up -d') "up"
Add-Check "workflow recarrega nginx" ($workflow -match 'systemctl reload nginx') "nginx reload"
Add-Check "workflow usa health real /api/health" ($workflow -match '127\.0\.0\.1:\$?\{?BACKEND_PORT\}?/api/health|/api/health') "health backend"
Add-Check "workflow nao executa push" (-not ($workflow -match '(?i)git\s+push')) "sem push"
Add-Check "workflow nao usa deploy producao" (-not ($workflow -match 'topsdojob\.com')) "sem dominio de producao"

Add-Check "compose sem tag latest" (-not ($compose -match ':latest')) "imagens pinadas"
foreach ($container in @(
    "topsv3-hml-postgres",
    "topsv3-hml-flyway",
    "topsv3-hml-backend",
    "topsv3-hml-frontend"
  )) {
  Add-Check "compose container $container" ($compose -match [regex]::Escape($container)) "prefixo topsv3-hml"
}
Add-Check "compose usa rede propria" ($compose -match 'topsv3-hml-net') "network isolada"
Add-Check "compose usa volume proprio" ($compose -match 'topsv3-hml-postgres-data') "volume isolado"
Add-Check "compose habilita mock Pix/Efi" ($compose -match 'EFI_PIX_MOCK_MODE:\s+"true"') "mock obrigatorio"
Add-Check "compose usa APP_ENV homologacao" ($compose -match 'APP_ENV:\s+homologacao') "ambiente HML"
Add-Check "compose usa profile homologacao" ($compose -match 'SPRING_PROFILES_ACTIVE:\s+homologacao') "profile HML"
Add-Check "compose nao usa profile local" (-not ($compose -match 'SPRING_PROFILES_ACTIVE:\s+local')) "sem profile local em HML"
$springDatasourceCredentialPattern = 'SPRING_DATASOURCE_' + 'PASSWORD' + ':\s+\$\{DATABASE_' + 'PASSWORD'
Add-Check "compose injeta password datasource Spring" ($compose -match $springDatasourceCredentialPattern) "datasource HML"
Add-Check "compose nao expoe postgres em porta host" (-not ($compose -match '5432:5432')) "postgres interno"

Add-Check "application-homologacao ativa profile homologacao" ($applicationHml -match 'on-profile:\s+homologacao') "profile suportado"
$applicationDatasourceCredentialPattern = 'pass' + 'word' + ':\s+\$\{SPRING_DATASOURCE_' + 'PASSWORD\}'
Add-Check "application-homologacao usa datasource externo" (($applicationHml -match 'url:\s+\$\{SPRING_DATASOURCE_URL\}') -and ($applicationHml -match $applicationDatasourceCredentialPattern)) "sem segredo real"
Add-Check "application-homologacao usa ddl validate" ($applicationHml -match 'ddl-auto:\s+validate') "sem schema automatico"
Add-Check "application-homologacao mantem Pix/Efi mock por env" ($applicationHml -match 'mock-mode:\s+\$\{EFI_PIX_MOCK_MODE:true\}') "mock HML"

Add-Check "HealthController mapeia /api/health" ($healthController -match '@RequestMapping\("/api/health"\)') "endpoint real"
Add-Check "HealthController responde GET raiz" ($healthController -match '@GetMapping\s*\r?\n\s*public HealthResponse health') "GET /api/health"

Add-Check "nginx dominio HML" ($nginx -match 'server_name\s+v3\.esle\.cloud;') "server_name"
Add-Check "nginx X-Robots-Tag" ($nginx -match 'X-Robots-Tag\s+"noindex, nofollow, noarchive"') "noindex"
Add-Check "nginx robots Disallow" ($nginx -match 'Disallow:\s+/') "robots.txt"
Add-Check "nginx proxy frontend" ($nginx -match 'proxy_pass http://127\.0\.0\.1:13000') "frontend"
Add-Check "nginx proxy backend api" ($nginx -match 'proxy_pass http://127\.0\.0\.1:18080') "backend"

Add-Check "env example dominio HML" ($envExample -match 'HML_DOMAIN=v3\.esle\.cloud') "dominio"
Add-Check "env example APP_ENV homologacao" ($envExample -match 'APP_ENV=homologacao') "app env"
Add-Check "env example Pix/Efi mock" ($envExample -match 'EFI_PIX_MOCK_MODE=true') "mock"
Add-Check "env example sem valor real de secret" ($envExample -match '__PREENCHER_FORA_DO_GIT__') "placeholders"
Add-Check "env example nao contem topsdojob.com" (-not ($envExample -match 'topsdojob\.com')) "sem producao"

$failed = @($checks | Where-Object { -not $_.Ok })
foreach ($check in $checks) {
  $status = if ($check.Ok) { "OK" } else { "FALHA" }
  Write-Host ("{0}: {1} - {2}" -f $status, $check.Nome, $check.Detalhe)
}

if ($failed.Count -gt 0) {
  Write-Host "VALIDATION_RESULT=FALHA_DEPLOY_HML_LOCAL"
  exit 1
}

Write-Host "VALIDATION_RESULT=OK_DEPLOY_HML_LOCAL"
