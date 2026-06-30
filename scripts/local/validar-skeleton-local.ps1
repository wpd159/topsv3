Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositório Git não encontrado."
  exit 2
}

$securityUtils = Join-Path $repoRoot "scripts/security/git-staged-utils.ps1"
. $securityUtils

$checks = New-Object System.Collections.Generic.List[object]

function Add-Check {
  param(
    [string]$Nome,
    [bool]$Ok,
    [string]$Detalhe
  )
  $checks.Add([pscustomobject]@{
    Nome = $Nome
    Resultado = $(if ($Ok) { "OK" } else { "FALHA" })
    Detalhe = $Detalhe
  })
}

function Test-RepoPath {
  param([string]$RelativePath)
  return (Test-Path -LiteralPath (Join-Path $repoRoot ($RelativePath -replace '/', [IO.Path]::DirectorySeparatorChar)))
}

$requiredFiles = @(
  "backend/pom.xml",
  "backend/src/main/java/br/com/topsdojob/v3/TopsDoJobBackendApplication.java",
  "backend/src/main/java/br/com/topsdojob/v3/platform/health/HealthController.java",
  "backend/src/main/java/br/com/topsdojob/v3/platform/request/RequestIdContext.java",
  "backend/src/main/java/br/com/topsdojob/v3/platform/request/RequestIdFilter.java",
  "backend/src/main/java/br/com/topsdojob/v3/platform/error/ApiErrorCode.java",
  "backend/src/main/java/br/com/topsdojob/v3/platform/error/ApiErrorResponse.java",
  "backend/src/main/java/br/com/topsdojob/v3/platform/error/GlobalExceptionHandler.java",
  "backend/src/main/java/br/com/topsdojob/v3/platform/web/LocalCorsConfiguration.java",
  "backend/src/main/resources/application.yml",
  "backend/src/main/resources/application-local.yml",
  "backend/src/main/resources/application-test.yml",
  "frontend/package.json",
  "frontend/next.config.mjs",
  "frontend/src/app/layout.tsx",
  "frontend/src/app/page.tsx",
  "frontend/src/app/health/page.tsx",
  "frontend/src/app/anuncios/[slug]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/[bairro]/page.tsx",
  "frontend/src/app/robots.ts",
  "frontend/src/app/sitemap.ts",
  "frontend/src/app/sobre/page.tsx",
  "frontend/src/app/como-funciona/page.tsx",
  "frontend/src/app/seguranca/page.tsx",
  "frontend/src/app/anunciar/page.tsx",
  "frontend/src/app/perguntas-frequentes/page.tsx",
  "frontend/src/app/admin/page.tsx",
  "frontend/src/app/admin/moderacao/page.tsx",
  "frontend/src/app/admin/anuncios/page.tsx",
  "frontend/src/app/admin/usuarios/page.tsx",
  "frontend/src/app/admin/midia/page.tsx",
  "frontend/src/app/admin/premium/page.tsx",
  "frontend/src/app/admin/creditos/page.tsx",
  "frontend/src/app/admin/financeiro/page.tsx",
  "frontend/src/app/admin/seo/page.tsx",
  "frontend/src/app/admin/banners/page.tsx",
  "frontend/src/app/admin/comercial/page.tsx",
  "frontend/src/app/admin/suporte/page.tsx",
  "frontend/src/app/admin/backup/page.tsx",
  "frontend/src/app/admin/auditoria/page.tsx",
  "frontend/public/llms.txt",
  "frontend/src/lib/api/client.ts",
  "frontend/src/lib/config/publicEnv.ts",
  "frontend/src/lib/seo/localSeo.ts",
  "frontend/src/lib/seo/schemaPlaceholders.ts",
  "frontend/src/modules/admin/shell/adminModules.ts",
  "frontend/src/modules/admin/shell/AdminShell.tsx",
  "frontend/src/modules/admin/shell/AdminModuleCard.tsx",
  "frontend/src/modules/admin/shell/AdminPlaceholderPage.tsx",
  "frontend/src/modules/public/skeleton/PublicRouteShell.tsx",
  "frontend/src/modules/public/skeleton/SeoPlaceholder.tsx",
  "contracts/openapi/topsdojob-v3-local.yaml",
  "infra/local/.env.local.example",
  "infra/local/docker-compose.local.yml"
)

foreach ($file in $requiredFiles) {
  Add-Check "arquivo obrigatório $file" (Test-RepoPath $file) "presença no skeleton"
}

$forbiddenPatterns = @(
  "backend/src/main/resources/db/migration",
  "backend/src/main/java/br/com/topsdojob/v3/domain",
  "backend/src/main/java/br/com/topsdojob/v3/repository",
  "backend/src/main/java/br/com/topsdojob/v3/service"
)
foreach ($path in $forbiddenPatterns) {
  Add-Check "ausência de $path" (-not (Test-RepoPath $path)) "fora do escopo da Fase 1B"
}

$sqlFiles = @(Get-ChildItem -LiteralPath $repoRoot -Recurse -File -Filter *.sql -ErrorAction SilentlyContinue)
Add-Check "nenhum SQL criado" ($sqlFiles.Count -eq 0) "arquivos .sql encontrados: $($sqlFiles.Count)"

$compose = Get-Content -LiteralPath (Join-Path $repoRoot "infra/local/docker-compose.local.yml") -Raw
Add-Check "compose sem latest" ($compose -notmatch ':latest\b') "imagens Docker devem estar pinadas"
Add-Check "compose usa env local" ($compose -match '\.env\.local\.example') "Compose consome infra/local/.env.local.example"
Add-Check "compose usa COMPOSE_PROJECT_NAME" ($compose.Contains('name: ${COMPOSE_PROJECT_NAME:-topsv3-local}')) "nome do projeto local usa variável padrão do Docker Compose"
Add-Check "healthcheck PostgreSQL usa variáveis do container" ($compose.Contains('pg_isready -U \"$${POSTGRES_USER}\" -d \"$${POSTGRES_DB}\"')) "healthcheck acompanha POSTGRES_USER e POSTGRES_DB"

$envLocal = Get-Content -LiteralPath (Join-Path $repoRoot "infra/local/.env.local.example") -Raw
foreach ($expected in @("EFI_PIX_MOCK_MODE=true", "APP_ENV=local", "APP_CANONICAL_DOMAIN=http://localhost")) {
  Add-Check "env contém $expected" ($envLocal.Contains($expected)) "variável local obrigatória"
}
Add-Check "env contém COMPOSE_PROJECT_NAME=topsv3-local" ($envLocal.Contains("COMPOSE_PROJECT_NAME=topsv3-local")) "nome local padronizado para Docker Compose"
Add-Check "env não contém TOPSV3_LOCAL_PROJECT" (-not $envLocal.Contains("TOPSV3_LOCAL_PROJECT")) "variável paralela removida"

$placeholder = "CHANGE" + "_ME"
$envChecks = @(
  @{ Nome = "senha PostgreSQL"; Key = ("POSTGRES" + "_PASSWORD"); Value = $placeholder },
  @{ Nome = "usuário MinIO"; Key = "MINIO_ROOT_USER"; Value = "topsv3_local" },
  @{ Nome = "senha MinIO"; Key = ("MINIO_ROOT" + "_PASSWORD"); Value = $placeholder }
)
foreach ($item in $envChecks) {
  $expected = $item.Key + "=" + $item.Value
  Add-Check "env local contém $($item.Nome)" ($envLocal.Contains($expected)) "variável consumida pelo Compose local"
}

$pom = Get-Content -LiteralPath (Join-Path $repoRoot "backend/pom.xml") -Raw
Add-Check "backend usa Java 17 LTS" ($pom -match '<java\.version>17</java\.version>') "padrão conservador da Fase 1C"

$controller = Get-Content -LiteralPath (Join-Path $repoRoot "backend/src/main/java/br/com/topsdojob/v3/platform/health/HealthController.java") -Raw
foreach ($endpoint in @('/api/health"', '/readiness"', '/liveness"')) {
  Add-Check "endpoint $endpoint presente" ($controller.Contains($endpoint)) "health check local obrigatório"
}
Add-Check "HealthController responde UP" ($controller.Contains('"UP"')) "status padronizado em contrato local"
Add-Check "HealthController inclui requestId" ($controller.Contains("requestId")) "contrato transversal da Fase 1C.3"

$openapi = Get-Content -LiteralPath (Join-Path $repoRoot "contracts/openapi/topsdojob-v3-local.yaml") -Raw
Add-Check "OpenAPI documenta status UP" ($openapi.Contains("const: UP")) "contrato coerente com HealthController"
Add-Check "OpenAPI documenta X-Request-Id" ($openapi.Contains("X-Request-Id")) "request id transversal documentado"
Add-Check "OpenAPI documenta ApiErrorResponse" ($openapi.Contains("ApiErrorResponse")) "erro padronizado documentado"

$robots = Get-Content -LiteralPath (Join-Path $repoRoot "frontend/src/app/robots.ts") -Raw
Add-Check "robots local bloqueia indexação" ($robots.Contains('disallow: "/"')) "ambiente local deve ficar noindex"

$sitemap = Get-Content -LiteralPath (Join-Path $repoRoot "frontend/src/app/sitemap.ts") -Raw
Add-Check "sitemap local usa localUrl" ($sitemap.Contains("localUrl(")) "URLs locais devem vir do helper seguro"

$localSeo = Get-Content -LiteralPath (Join-Path $repoRoot "frontend/src/lib/seo/localSeo.ts") -Raw
Add-Check "SEO local usa NEXT_PUBLIC_CANONICAL_DOMAIN" ($localSeo.Contains("publicEnv.canonicalDomain")) "canonical local usa configuração pública segura"
Add-Check "SEO local força noindex" ($localSeo.Contains("index: false") -and $localSeo.Contains("follow: false")) "páginas skeleton não devem indexar"

$frontendSkeletonPaths = @(
  "frontend/src/app/anuncios/[slug]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/[bairro]/page.tsx",
  "frontend/src/app/sobre/page.tsx",
  "frontend/src/app/como-funciona/page.tsx",
  "frontend/src/app/seguranca/page.tsx",
  "frontend/src/app/anunciar/page.tsx",
  "frontend/src/app/perguntas-frequentes/page.tsx",
  "frontend/src/app/admin/page.tsx",
  "frontend/src/app/admin/moderacao/page.tsx",
  "frontend/src/app/admin/anuncios/page.tsx",
  "frontend/src/app/admin/usuarios/page.tsx",
  "frontend/src/app/admin/midia/page.tsx",
  "frontend/src/app/admin/premium/page.tsx",
  "frontend/src/app/admin/creditos/page.tsx",
  "frontend/src/app/admin/financeiro/page.tsx",
  "frontend/src/app/admin/seo/page.tsx",
  "frontend/src/app/admin/banners/page.tsx",
  "frontend/src/app/admin/comercial/page.tsx",
  "frontend/src/app/admin/suporte/page.tsx",
  "frontend/src/app/admin/backup/page.tsx",
  "frontend/src/app/admin/auditoria/page.tsx",
  "frontend/src/app/robots.ts",
  "frontend/src/app/sitemap.ts",
  "frontend/src/lib/seo/localSeo.ts",
  "frontend/src/lib/seo/schemaPlaceholders.ts",
  "frontend/src/modules/admin/shell/adminModules.ts",
  "frontend/src/modules/admin/shell/AdminShell.tsx",
  "frontend/src/modules/admin/shell/AdminModuleCard.tsx",
  "frontend/src/modules/admin/shell/AdminPlaceholderPage.tsx",
  "frontend/src/modules/public/skeleton/PublicRouteShell.tsx",
  "frontend/src/modules/public/skeleton/SeoPlaceholder.tsx"
)
$frontendSkeletonText = ""
foreach ($path in $frontendSkeletonPaths) {
  $frontendSkeletonText += (Get-Content -LiteralPath (Join-Path $repoRoot $path) -Raw)
}
Add-Check "frontend skeleton sem domínio de produção" (-not ($frontendSkeletonText -match 'https://topsdojob\.com|https://www\.topsdojob\.com')) "canonical de produção proibido no local"
Add-Check "frontend skeleton sem backend de domínio" (-not ($frontendSkeletonText -match '/api/(anuncios|acompanhantes|busca|search)')) "Fase 1C.4 não chama API de domínio"
Add-Check "frontend institucional sem chamada externa" (-not ($frontendSkeletonText -match 'fetch\(|axios|XMLHttpRequest|api\.openai|chatgpt|gemini|perplexity')) "Fase 1C.5 não acessa IA ou API externa"

$llms = Get-Content -LiteralPath (Join-Path $repoRoot "frontend/public/llms.txt") -Raw
Add-Check "llms.txt identifica o projeto" ($llms.Contains("Tops do Job")) "guia institucional local"
Add-Check "llms.txt preserva rotas públicas" ($llms.Contains("/anuncios/[slug]") -and $llms.Contains("/acompanhantes/[uf]/[cidade]") -and $llms.Contains("/acompanhantes/[uf]/[cidade]/[bairro]")) "rotas públicas críticas"
Add-Check "llms.txt sem domínio de produção" (-not ($llms -match 'https://topsdojob\.com|https://www\.topsdojob\.com')) "guia local não deve emitir produção"

$adminPagePaths = @(
  "frontend/src/app/admin/page.tsx",
  "frontend/src/app/admin/moderacao/page.tsx",
  "frontend/src/app/admin/anuncios/page.tsx",
  "frontend/src/app/admin/usuarios/page.tsx",
  "frontend/src/app/admin/midia/page.tsx",
  "frontend/src/app/admin/premium/page.tsx",
  "frontend/src/app/admin/creditos/page.tsx",
  "frontend/src/app/admin/financeiro/page.tsx",
  "frontend/src/app/admin/seo/page.tsx",
  "frontend/src/app/admin/banners/page.tsx",
  "frontend/src/app/admin/comercial/page.tsx",
  "frontend/src/app/admin/suporte/page.tsx",
  "frontend/src/app/admin/backup/page.tsx",
  "frontend/src/app/admin/auditoria/page.tsx"
)
$adminText = ""
$adminNoindexOk = $true
foreach ($path in $adminPagePaths) {
  $pageText = Get-Content -LiteralPath (Join-Path $repoRoot $path) -Raw
  $adminText += $pageText
  if (-not $pageText.Contains("skeletonMetadata(")) { $adminNoindexOk = $false }
}
Add-Check "admin pages usam metadata noindex" $adminNoindexOk "skeletonMetadata aplica robots noindex"
Add-Check "admin shell sem chamada externa" (-not ($adminText -match 'fetch\(|axios|XMLHttpRequest|/api/|api\.openai|chatgpt|gemini|perplexity')) "admin skeleton não chama backend nem API externa"
Add-Check "admin shell sem ações reais" (-not ($adminText -match '<button|<form|onClick|type="submit"|method=')) "admin skeleton não expõe ação funcional"

$adminModules = Get-Content -LiteralPath (Join-Path $repoRoot "frontend/src/modules/admin/shell/adminModules.ts") -Raw
foreach ($module in @("moderacao", "anuncios", "usuarios", "midia", "premium", "creditos", "financeiro", "seo", "banners", "comercial", "suporte", "backup", "auditoria")) {
  Add-Check "admin module $module mapeado" ($adminModules.Contains("slug: `"$module`"")) "módulo admin previsto no SDD"
}

$reportLines = New-Object System.Collections.Generic.List[string]
$reportLines.Add("# Relatório de validação do skeleton local")
$reportLines.Add("")
$reportLines.Add("- Total de verificações: $($checks.Count)")
$reportLines.Add("- Verificações OK: $(@($checks | Where-Object { $_.Resultado -eq "OK" }).Count)")
$reportLines.Add("- Verificações com falha: $(@($checks | Where-Object { $_.Resultado -ne "OK" }).Count)")
$reportLines.Add("")
$reportLines.Add("| Verificação | Resultado | Detalhe |")
$reportLines.Add("| --- | --- | --- |")
foreach ($check in $checks) {
  $reportLines.Add("| $($check.Nome) | $($check.Resultado) | $($check.Detalhe) |")
}

Write-TopsUtf8NoBomLines -Path (Join-Path $repoRoot "RELATORIO-TESTES-SKELETON.md") -Lines @($reportLines)

$failed = @($checks | Where-Object { $_.Resultado -ne "OK" })
if ($failed.Count -gt 0) {
  Write-Host "Validação do skeleton local encontrou falhas: $($failed.Count)"
  exit 1
}

Write-Host "Validação do skeleton local concluída sem falhas."
exit 0
