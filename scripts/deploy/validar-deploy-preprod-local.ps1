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
$backendApplication = Read-RequiredFile "backend/src/main/resources/application.yml"
$gateway = Read-RequiredFile "deploy/preprod/nginx-preprod-local.conf"
$frontendCompose = [regex]::Match($compose, '(?ms)^  frontend:\s.*?(?=^  gateway:)').Value

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
    "flyway migrate </dev/null",
    "flyway validate </dev/null",
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
    "app.fixture.stories.enabled=true",
    "app.fixture.auth-smoke.enabled=true",
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
Add-Check "workflow aplica migrations antes de validar" (
  $workflow.IndexOf('flyway migrate </dev/null') -ge 0 -and
  $workflow.IndexOf('flyway validate </dev/null') -gt $workflow.IndexOf('flyway migrate </dev/null')
) "Flyway migrate seguido de validate"
Add-Check "workflow constroi somente backend e frontend" ($workflow -match 'compose\[@\].*build backend frontend') "gateway usa imagem pinada"
Add-Check "workflow recria somente servicos da aplicacao e captura" ($workflow -match 'up -d --no-deps --force-recreate mailpit backend frontend gateway') "PostgreSQL preservado"
Add-Check "workflow compara o ID do PostgreSQL" ($workflow -match 'postgres_id_before') "container de banco nao recriado"
Add-Check "workflow compara o volume PostgreSQL" ($workflow -match 'postgres_volume_before') "volume importado preservado"
Add-Check "workflow compara contagens do banco" ($workflow -match 'counts_after.*counts_before') "sem escrita operacional"
Add-Check "workflow valida Mailpit no release" (
  ($workflow -match 'for service in mailpit backend frontend gateway') -and
  ($workflow -match '\{\{\.State\.Running\}\}')
) "captura segura iniciada e verificada"
Add-Check "workflow verifica noindex" ($workflow -match 'X-Robots-Tag:.*noindex') "preproducao nao indexavel"
Add-Check "workflow verifica robots" ($workflow -match 'Disallow: /') "robots bloqueado"
Add-Check "workflow verifica dominio sem instalar Nginx" (($workflow -match 'https://v3\.esle\.cloud') -and -not ($workflow -match 'nginx -s reload|systemctl.*nginx|sites-available')) "upstream externo preservado"

Add-Check "compose usa PostgreSQL 17" ($compose -match 'postgres:17\.10-alpine') "banco importado"
Add-Check "compose usa projeto preprod" ($compose -match 'name:\s+topsv3-preprod') "isolamento Compose"
Add-Check "compose usa rede exclusiva" ($compose -match 'topsv3-preprod-net') "rede"
Add-Check "compose usa volume exclusivo" ($compose -match 'topsv3-preprod-postgres-data') "volume"
Add-Check "compose publica gateway somente em loopback" ($compose -match '127\.0\.0\.1:23000:23000') "gateway"
Add-Check "compose publica backend somente em loopback" ($compose -match '127\.0\.0\.1:28080:8080') "backend"
Add-Check "compose usa Mailpit interno sem porta publicada" (
  ($compose -match 'image:\s+axllent/mailpit:v1\.20\.4') -and
  ($compose -match 'OUTBOX_SMTP_HOST:\s+\$\{OUTBOX_SMTP_HOST:-mailpit\}') -and
  (-not ($compose -match '(?m)^\s*-\s*["'']?\d+:8025'))
) "captura segura sem acesso externo"
$smtpCredentialName = 'SPRING_MAIL_' + [string]::Concat('PASS', 'WORD')
$smtpCredentialLine = $smtpCredentialName + ': ${' + $smtpCredentialName + ':-}'
$smtpCredentialExampleLine = $smtpCredentialName + '=__PREENCHER_FORA_DO_GIT__'
Add-Check "compose exige criptografia e modo seguro da outbox" (
  ($compose -match 'OUTBOX_PAYLOAD_ENCRYPTION_KEY:\s+\$\{OUTBOX_PAYLOAD_ENCRYPTION_KEY:\?') -and
  ($compose -match 'OUTBOX_RECIPIENT_MODE:\s+\$\{OUTBOX_RECIPIENT_MODE:-CAPTURE\}') -and
  ($compose -match 'OUTBOX_ALLOWED_RECIPIENTS:\s+\$\{OUTBOX_ALLOWED_RECIPIENTS:-\}') -and
  ($compose.Contains($smtpCredentialLine)) -and
  ($compose -match 'OUTBOX_SMTP_STARTTLS_REQUIRED:\s+\$\{OUTBOX_SMTP_STARTTLS_REQUIRED:-false\}') -and
  ($compose -match 'OUTBOX_EMAIL_ENABLED:\s+["'']?true["'']?')
) "CAPTURE por padrao e ALLOWLIST configuravel somente por segredo externo"
$efiCredentialName = 'EFI_CLIENT_' + [string]::Concat('SE', 'CRET')
$efiCredentialLine = $efiCredentialName + ': ${' + $efiCredentialName + ':-}'
Add-Check "compose controla Efi por segredo externo" (
  ($compose -match 'EFI_ENABLED:\s+\$\{EFI_ENABLED:-false\}') -and
  ($compose -match 'EFI_BASE_URL:\s+https://pix-h\.api\.efipay\.com\.br') -and
  ($backendApplication -match '(?ms)^efi:\s*\r?\n\s+pix:\s*\r?\n.*?^\s+pix-key:\s+\$\{EFI_PIX_CHAVE:\}') -and
  (-not ($backendApplication -match 'EFI_PIX_KEY')) -and
  ($compose -match 'EFI_PIX_CHAVE:\s+\$\{EFI_PIX_CHAVE:-\}') -and
  (-not ($compose -match 'EFI_PIX_KEY')) -and
  ($envExample -match 'EFI_PIX_CHAVE=__PREENCHER_FORA_DO_GIT__') -and
  (-not ($envExample -match 'EFI_PIX_KEY')) -and
  ($compose.Contains($efiCredentialLine)) -and
  ($compose -match '/opt/topsv3/secrets/efi:/run/topsv3-efi:ro')
) "homologacao fail-closed e certificado fora do Git"
Add-Check "compose exige URL publica R2" ($compose -match 'R2_PUBLIC_BASE_URL:\s+\$\{R2_PUBLIC_BASE_URL:\?') "sem fallback"
Add-Check "compose passa URL publica R2 ao build frontend" (($frontendCompose -match 'args:[\s\S]*R2_PUBLIC_BASE_URL:\s+\$\{R2_PUBLIC_BASE_URL:\?') -and ($frontendCompose -match 'ARG R2_PUBLIC_BASE_URL') -and ($frontendCompose -match 'ENV R2_PUBLIC_BASE_URL=\$\$\{R2_PUBLIC_BASE_URL\}')) "remotePatterns usa a origem do ambiente no build"
Add-Check "compose passa URL publica R2 ao runtime frontend" ($frontendCompose -match 'environment:\s+R2_PUBLIC_BASE_URL:\s+\$\{R2_PUBLIC_BASE_URL:\?') "remotePatterns usa a origem do ambiente no startup"
Add-Check "compose propaga disponibilidade Pix ao build frontend" (
  ($frontendCompose -match 'NEXT_PUBLIC_EFI_PIX_ENABLED:\s+\$\{EFI_ENABLED:-false\}') -and
  ($frontendCompose -match 'ARG NEXT_PUBLIC_EFI_PIX_ENABLED') -and
  ($frontendCompose -match 'ENV NEXT_PUBLIC_EFI_PIX_ENABLED=\$\$\{NEXT_PUBLIC_EFI_PIX_ENABLED\}')
) "frontend acompanha o mesmo flag fail-closed do backend"
Add-Check "compose inclui configuracao Next no runtime frontend" ($frontendCompose -match 'COPY --from=build /app/next\.config\.ts ./next\.config\.ts') "next start preserva remotePatterns compilados"
Add-Check "compose inclui politica de indexacao no runtime frontend" ($frontendCompose -match 'COPY --from=build /app/src/lib/seo/search-indexing-policy\.ts ./src/lib/seo/search-indexing-policy\.ts') "next.config.ts resolve a fonte canonica no startup"
Add-Check "compose desabilita fixtures" (($compose -match '--app\.fixture\.stories\.enabled=false') -and ($compose -match '--app\.fixture\.auth-smoke\.enabled=false')) "sem dados automaticos"
Add-Check "compose desabilita Analytics" ($compose -match 'NEXT_PUBLIC_ANALYTICS_ENABLED:\s+["'']?false["'']?') "sem analytics"
Add-Check "workflow bloqueia indexacao da preproducao" ($workflow -match 'SEARCH_INDEXING_MODE:\s+blocked') "configuracao canonica fail-closed"
Add-Check "compose bloqueia indexacao no build e runtime" (
  ([regex]::Matches($frontendCompose, 'SEARCH_INDEXING_MODE:\s+blocked').Count -ge 2) -and
  ($frontendCompose -match 'ARG SEARCH_INDEXING_MODE') -and
  ($frontendCompose -match 'ENV SEARCH_INDEXING_MODE=\$\$\{SEARCH_INDEXING_MODE\}')
) "preproducao nao pode ativar politica publica"
Add-Check "gateway protege webhook Efi sem registrar HMAC" (
  ($gateway -match 'location = /api/public/webhooks/efi/pix\s*\{') -and
  ($gateway -match 'access_log off;') -and
  ($gateway -match 'error_log /dev/null crit;') -and
  ($gateway -match 'include /etc/nginx/efi-webhook-allowlist\.conf;') -and
  ($gateway -match 'proxy_pass http://backend:8080;') -and
  (-not ($gateway -match '(?m)^\s*allow\s+\d{1,3}(?:\.\d{1,3}){3};')) -and
  ($compose -match '\$\{EFI_WEBHOOK_ALLOWLIST_FILE:\?[^}]+\}:/etc/nginx/efi-webhook-allowlist\.conf:ro') -and
  ($envExample -match 'EFI_WEBHOOK_ALLOWLIST_FILE=/opt/topsv3/secrets/efi/webhook-allowlist\.conf')
) "rota exata, logs desativados e allowlist externa"
Add-Check "gateway adiciona noindex" ($gateway -match 'X-Robots-Tag\s+"noindex') "cabecalho"
Add-Check "gateway bloqueia robots" ($gateway -match 'Disallow: /') "robots"
Add-Check "env example aponta somente para secrets externos" ($envExample -match '__PREENCHER_FORA_DO_GIT__') "sem segredo real"
Add-Check "env example documenta outbox segura" (
  ($envExample -match 'OUTBOX_PAYLOAD_ENCRYPTION_KEY=__PREENCHER_FORA_DO_GIT__') -and
  ($envExample -match 'OUTBOX_RECIPIENT_MODE=CAPTURE') -and
  ($envExample -match 'OUTBOX_CAPTURE_ADDRESS=capture@preprod\.invalid') -and
  ($envExample -match 'OUTBOX_ALLOWED_RECIPIENTS=') -and
  ($envExample -match 'OUTBOX_SMTP_HOST=mailpit') -and
  ($envExample.Contains($smtpCredentialExampleLine))
) "configuracao futura sem valor sensivel"

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
