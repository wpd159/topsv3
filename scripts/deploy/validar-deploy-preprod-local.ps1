Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "VALIDATION_RESULT=FALHA_DEPLOY_PREPROD_LOCAL"
  Write-Host "Motivo: repositorio Git nao encontrado."
  exit 1
}

function Resolve-RepoPath {
  param([string]$Path)
  return (Join-Path $repoRoot ($Path -replace '/', [IO.Path]::DirectorySeparatorChar))
}

$checks = New-Object System.Collections.Generic.List[object]

function Add-Check {
  param([string]$Nome, [bool]$Ok, [string]$Detalhe)
  $checks.Add([pscustomobject]@{ Nome = $Nome; Ok = $Ok; Detalhe = $Detalhe })
}

function Read-RequiredFile {
  param([string]$Path)
  $full = Resolve-RepoPath $Path
  Add-Check "arquivo existe: $Path" (Test-Path -LiteralPath $full -PathType Leaf) $full
  if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { return "" }
  return [System.IO.File]::ReadAllText($full, [System.Text.UTF8Encoding]::new($false, $true))
}

$workflow = Read-RequiredFile ".github/workflows/deploy-preprod.yml"
$compose = Read-RequiredFile "deploy/preprod/docker-compose.yml"
$envExample = Read-RequiredFile "deploy/preprod/preprod.env.example"
$gateway = Read-RequiredFile "deploy/preprod/nginx-preprod-local.conf"

foreach ($secret in @("PREPROD_HOST", "PREPROD_USER", "PREPROD_SSH_PORT", "PREPROD_SSH_PRIVATE_KEY")) {
  Add-Check "workflow referencia secret $secret" ($workflow -match [regex]::Escape("secrets.$secret")) "secret obrigatorio"
}

foreach ($required in @(
    "environment: preprod",
    "group: topsv3-preprod",
    "/opt/topsv3/preprod/releases",
    "/opt/topsv3/preprod/current",
    "/opt/topsv3/secrets/preprod.env",
    "/opt/topsv3/secrets/preprod-v3.override.yml",
    "deploy/preprod/docker-compose.yml",
    "127.0.0.1:23000",
    "127.0.0.1:28080",
    "flyway validate",
    "mv -Tf",
    "backend frontend gateway"
  )) {
  Add-Check "workflow contem $required" ($workflow -match [regex]::Escape($required)) "contrato de preproducao"
}

foreach ($forbidden in @(
    "HML_",
    "hml",
    "topsv3-hml",
    "deploy/hml",
    "hml.env",
    "/opt/topsv3/app",
    "13000",
    "18080",
    "validar-auth-publico-hml",
    "provisionar-admin-ficticio-hml",
    "app.hml-fixture.enabled=true",
    "app.hml-auth-smoke.enabled=true",
    "docker compose down",
    "--volumes",
    "docker volume rm",
    "systemctl reload nginx",
    "sites-enabled"
  )) {
  Add-Check "workflow nao contem $forbidden" (-not ($workflow -match [regex]::Escape($forbidden))) "sem dependencia operacional antiga ou acao destrutiva"
}

foreach ($exclude in @(
    ".git", "node_modules", ".next", "target", "topsv3-auditoria-local",
    "logs-brutos-nao-versionar", "*.dump", "**/*.dump", "*.backup", "**/*.backup",
    "*.log", "**/*.log", ".env", "**/.env", "*.pem", "**/*.pem",
    "*.key", "**/*.key", "*.crt", "**/*.crt"
  )) {
  Add-Check "workflow exclui $exclude" ($workflow -match [regex]::Escape($exclude)) "pacote sem artefatos sensiveis"
}

Add-Check "workflow valida Compose antes do deploy" ($workflow -match 'compose\[@\].*config --quiet') "compose real com override"
Add-Check "workflow constroi somente backend e frontend" ($workflow -match 'compose\[@\].*build backend frontend') "gateway usa imagem pinada"
Add-Check "workflow recria somente servicos da aplicacao" ($workflow -match 'up -d --no-deps --force-recreate backend frontend gateway') "PostgreSQL preservado"
Add-Check "workflow compara o ID do PostgreSQL" ($workflow -match 'postgres_id_before') "container de banco nao recriado"
Add-Check "workflow compara o volume PostgreSQL" ($workflow -match 'postgres_volume_before') "volume importado preservado"
Add-Check "workflow compara contagens do banco" ($workflow -match 'counts_after.*counts_before') "sem escrita operacional"
Add-Check "workflow verifica noindex" ($workflow -match 'X-Robots-Tag:.*noindex') "preproducao nao indexavel"
Add-Check "workflow verifica robots" ($workflow -match 'Disallow: /') "robots bloqueado"
Add-Check "workflow verifica dominio sem instalar Nginx" (($workflow -match 'https://v3\.esle\.cloud') -and -not ($workflow -match 'nginx -s reload|systemctl.*nginx|sites-available')) "upstream externo preservado"

Add-Check "compose usa PostgreSQL 17" ($compose -match 'postgres:17\.10-alpine') "banco importado"
Add-Check "compose usa projeto preprod" ($compose -match 'name:\s+topsv3-preprod') "isolamento Compose"
Add-Check "compose usa rede exclusiva" ($compose -match 'topsv3-preprod-net') "rede"
Add-Check "compose usa volume exclusivo" ($compose -match 'topsv3-preprod-postgres-data') "volume"
Add-Check "compose publica gateway somente em loopback" ($compose -match '127\.0\.0\.1:23000:23000') "gateway"
Add-Check "compose publica backend somente em loopback" ($compose -match '127\.0\.0\.1:28080:8080') "backend"
Add-Check "compose mantem Efi desabilitada" ($compose -match 'EFI_ENABLED:\s+["'']?false["'']?') "pagamentos externos bloqueados"
Add-Check "compose exige URL publica R2" ($compose -match 'R2_PUBLIC_BASE_URL:\s+\$\{R2_PUBLIC_BASE_URL:\?') "sem fallback"
Add-Check "compose desabilita fixtures" (($compose -match '--app\.hml-fixture\.enabled=false') -and ($compose -match '--app\.hml-auth-smoke\.enabled=false')) "sem dados automaticos"
Add-Check "compose desabilita Analytics" ($compose -match 'NEXT_PUBLIC_ANALYTICS_ENABLED:\s+["'']?false["'']?') "sem analytics"
Add-Check "gateway bloqueia webhook Efi" ($gateway -match 'webhooks/efi') "404 fail-closed"
Add-Check "gateway adiciona noindex" ($gateway -match 'X-Robots-Tag\s+"noindex') "cabecalho"
Add-Check "gateway bloqueia robots" ($gateway -match 'Disallow: /') "robots"
Add-Check "env example aponta somente para secrets externos" ($envExample -match '__PREENCHER_FORA_DO_GIT__') "sem segredo real"

$failed = @($checks | Where-Object { -not $_.Ok })
foreach ($check in $checks) {
  $status = if ($check.Ok) { "OK" } else { "FALHA" }
  Write-Host ("{0}: {1} - {2}" -f $status, $check.Nome, $check.Detalhe)
}

if ($failed.Count -gt 0) {
  Write-Host "VALIDATION_RESULT=FALHA_DEPLOY_PREPROD_LOCAL"
  exit 1
}

Write-Host "VALIDATION_RESULT=OK_DEPLOY_PREPROD_LOCAL"
