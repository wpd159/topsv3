Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

$checks = New-Object System.Collections.Generic.List[object]
$pending = New-Object System.Collections.Generic.List[string]

function Get-RepoPath([string]$RelativePath) {
  return Join-Path $repoRoot ($RelativePath -replace '/', [IO.Path]::DirectorySeparatorChar)
}

function Test-RepoFile([string]$RelativePath) {
  return Test-Path -LiteralPath (Get-RepoPath $RelativePath) -PathType Leaf
}

function Get-RepoText([string]$RelativePath) {
  return [IO.File]::ReadAllText((Get-RepoPath $RelativePath), [Text.UTF8Encoding]::new($false, $true))
}

function Add-Check([string]$Nome, [bool]$Ok, [string]$Detalhe) {
  $checks.Add([pscustomobject]@{
    Nome = $Nome
    Resultado = $(if ($Ok) { "OK" } else { "FALHA" })
    Detalhe = $Detalhe
  })
}

$routeFiles = @(
  "frontend/src/app/(public-routes)/acompanhantes/[estado]/page.tsx",
  "frontend/src/app/(public-routes)/acompanhantes/[estado]/[cidade]/page.tsx",
  "frontend/src/app/(public-routes)/acompanhantes/[estado]/[cidade]/[bairro]/page.tsx",
  "frontend/src/app/(public-routes)/anuncios/[slug]/page.tsx"
)

foreach ($file in $routeFiles) {
  $exists = Test-RepoFile $file
  Add-Check "rota publica existe: $file" $exists "rota consolidada obrigatoria"
  if ($exists) {
    $text = Get-RepoText $file
    Add-Check "rota server-side: $file" (-not ($text -match '(?m)^\s*["'']use client["'']')) "HTML e metadata devem nascer no servidor"
    Add-Check "metadata publica: $file" ($text.Contains("generateMetadata") -or $text.Contains("metadata:")) "metadata deve existir"
    Add-Check "404 real: $file" $text.Contains("notFound()") "recurso inexistente nao pode virar soft 404"
  }
}

$anuncioDetailPath = "frontend/src/app/(public-routes)/anuncios/[slug]/page.tsx"
if (Test-RepoFile $anuncioDetailPath) {
  $anuncioDetail = Get-RepoText $anuncioDetailPath
  Add-Check "detalhe declara JSON-LD WebPage" (
    $anuncioDetail.Contains('"@type": "WebPage"') -and
    $anuncioDetail.Contains("primaryImageOfPage")
  ) "schema especifico deve complementar o WebSite global"
  Add-Check "detalhe reutiliza a imagem do Open Graph" (
    $anuncioDetail.Contains("const imagemPublica = selecionarImagemPublicaSeo(") -and
    $anuncioDetail.Contains("images: [{ url: imagemPublica") -and
    $anuncioDetail.Contains("url: imagemPublica")
  ) "og:image e primaryImageOfPage devem partir da mesma capa LIVRE"
  Add-Check "detalhe omite imagem primaria sem midia publica" (
    $anuncioDetail.Contains("...(imagemPublica")
  ) "logotipo nao pode ser fallback do primaryImageOfPage"
  Add-Check "detalhe nao cria schema comercial ou pessoal" (
    -not ($anuncioDetail -match '"@type":\s*"(Product|Person|LocalBusiness|Service)"')
  ) "somente WebPage e ImageObject sao admitidos nesta fase"
}

$rootLayoutPath = "frontend/src/app/layout.tsx"
Add-Check "schema WebSite global preservado" (
  (Test-RepoFile $rootLayoutPath) -and
  (Get-RepoText $rootLayoutPath).Contains('"@type": "WebSite"')
) "o novo WebPage deve complementar o schema global"

$urlHelperPath = "frontend/src/lib/seo/public-url.ts"
Add-Check "helper unico de origem publica" (Test-RepoFile $urlHelperPath) "public-url.ts obrigatorio"
if (Test-RepoFile $urlHelperPath) {
  $urlHelper = Get-RepoText $urlHelperPath
  Add-Check "origem configuravel por ambiente" ($urlHelper.Contains("NEXT_PUBLIC_SITE_URL") -and $urlHelper.Contains("getPublicSiteBaseUrl")) "canonical deve seguir o ambiente"
  Add-Check "segmentos normalizados" ($urlHelper.Contains("publicRouteSegment") -and $urlHelper.Contains("toLocaleLowerCase")) "UF/cidade/bairro sem concorrencia de caixa"
  Add-Check "pagina publica validada" ($urlHelper.Contains("parsePublicPage") -and $urlHelper.Contains("Number.isSafeInteger")) "query page invalida deve ser rejeitada"
}

$sitemapPath = "frontend/src/app/sitemap.ts"
$robotsPath = "frontend/src/app/robots.ts"
$notFoundPath = "frontend/src/app/not-found.tsx"

Add-Check "sitemap existe" (Test-RepoFile $sitemapPath) "sitemap obrigatorio"
if (Test-RepoFile $sitemapPath) {
  $sitemap = Get-RepoText $sitemapPath
  Add-Check "sitemap usa origem compartilhada" ($sitemap.Contains("getPublicSiteBaseUrl") -and $sitemap.Contains("buildPublicUrl")) "sem gerador canonical concorrente"
  Add-Check "sitemap usa descoberta SEO backend" $sitemap.Contains("descobrirAnunciosIndexaveisSitemap") "contrato leve deve concentrar indexabilidade no backend"
  Add-Check "sitemap sem N+1 de detalhes" (-not $sitemap.Contains("obterAnuncioPublicoPorSlug")) "sitemap nao pode consultar detalhe por anuncio"
  Add-Check "sitemap sem paginacao por estado" (-not $sitemap.Contains("listarPublicosPorEstado")) "descoberta SEO deve ocorrer em uma unica chamada"
  Add-Check "sitemap usa localidades publicas V3" $sitemap.Contains("descobrirLocalidadesPublicas") "nao criar localidade artificial"
  Add-Check "sitemap sem admin/API" (-not ($sitemap -match '["'']/(admin|api)(/|["''])')) "rotas privadas nao entram no sitemap"
}

Add-Check "robots existe" (Test-RepoFile $robotsPath) "robots obrigatorio"
if (Test-RepoFile $robotsPath) {
  $robots = Get-RepoText $robotsPath
  Add-Check "HML/local bloqueados" ($robots.Contains('disallow: "/"') -and $robots.Contains("isRealProductionDomain")) "fora de topsdojob.com deve haver bloqueio total"
  Add-Check "robots usa origem compartilhada" $robots.Contains("getPublicSiteBaseUrl") "origem unica"
  Add-Check "rotas privadas bloqueadas" ($robots.Contains('"/admin"') -and $robots.Contains('"/anunciar"') -and $robots.Contains('"/painel"')) "admin e areas privadas nao indexaveis"
}

Add-Check "404 com noindex" ((Test-RepoFile $notFoundPath) -and (Get-RepoText $notFoundPath).Contains("index: false")) "pagina de erro nao indexavel"

$schemaSources = @(
  "frontend/src/lib/seo/cidadeSeo.ts",
  "frontend/src/lib/seo/programmatic-content.ts",
  "frontend/src/lib/seo/seoContentGeneratorBairro.ts"
) | ForEach-Object { if (Test-RepoFile $_) { Get-RepoText $_ } } | Out-String
Add-Check "schemas compativeis presentes" ($schemaSources.Contains("BreadcrumbList") -and $schemaSources.Contains("ItemList") -and $schemaSources.Contains("FAQPage")) "breadcrumb, lista e FAQ visivel"

$homePath = "frontend/src/app/(public-routes)/page.tsx"
$securityComponentPath = "frontend/src/components/layout/seguranca-section.tsx"
Add-Check "bloco de confianca removido" (-not (Test-RepoFile $securityComponentPath)) "componente exclusivo nao pode ficar orfao"
if (Test-RepoFile $homePath) {
  Add-Check "home sem import removido" (-not (Get-RepoText $homePath).Contains("SegurancaSection")) "sem import ou wrapper orfao"
}

$headerPath = "frontend/src/components/layout/header.tsx"
if (Test-RepoFile $headerPath) {
  $header = Get-RepoText $headerPath
  Add-Check "neon restrito ao header" ($header.Contains("HEADER_CTA_NEON") -and $header.Contains("Registrar-se") -and ($header -match "PUBLICAR SEU AN.NCIO")) "efeito local nos dois CTAs"
}

$adapterPath = "frontend/src/lib/public-catalog-api.ts"
$adapterOk = Test-RepoFile $adapterPath
Add-Check "adapter publico unico existe" $adapterOk "public-catalog-api.ts obrigatorio"
if ($adapterOk) {
  $adapter = Get-RepoText $adapterPath
  $adapterFunctions = @(
    "listarPublicosPorEstado",
    "listarPublicosPorCidade",
    "obterAgregadoPublicoCidade",
    "listarPublicosPorBairro",
    "obterAnuncioPublicoPorSlug",
    "descobrirLocalidadesPublicas"
  )
  foreach ($function in $adapterFunctions) {
    Add-Check "adapter contem $function" $adapter.Contains("export async function $function") "contrato publico V3 obrigatorio"
  }
  Add-Check "adapter contem descobrirAnunciosIndexaveisSitemap" $adapter.Contains("export async function descobrirAnunciosIndexaveisSitemap") "contrato SEO leve obrigatorio"
  Add-Check "politica SEO nao duplicada no frontend" (-not (Test-RepoFile "frontend/src/lib/seo/anuncio-indexing.ts")) "indexabilidade pertence ao backend"
  Add-Check "adapter nao converte falha em vazio" (-not ($adapter -match '(?s)catch\s*\{\s*return\s*(\[\]|\{\})')) "falha HTTP deve permanecer falha"
}

$locationFilterPath = "frontend/src/components/anuncios/barra-localizacao.tsx"
Add-Check "filtros usam descoberta publica V3" (
  (Test-RepoFile $locationFilterPath) -and
  (Get-RepoText $locationFilterPath).Contains("descobrirLocalidadesPublicas") -and
  (-not (Get-RepoText $locationFilterPath).Contains("/localidades/estados"))
) "Home, filtros e sitemap devem compartilhar a descoberta publica"

$controllerSources = @(
  "backend/src/main/java/br/com/topsdojob/v3/web/publico/ListagemPublicaController.java",
  "backend/src/main/java/br/com/topsdojob/v3/web/publico/LocalidadePublicaController.java",
  "backend/src/main/java/br/com/topsdojob/v3/web/publico/AnuncioPublicoController.java"
) | ForEach-Object { if (Test-RepoFile $_) { Get-RepoText $_ } } | Out-String
Add-Check "backend publica os seis contratos" (
  $controllerSources.Contains('/api/public/acompanhantes') -and
  $controllerSources.Contains('/api/public/localidades') -and
  $controllerSources.Contains('/api/public/anuncios') -and
  $controllerSources.Contains('@GetMapping("/{uf}")') -and
  $controllerSources.Contains('@GetMapping("/{uf}/{cidade}")') -and
  $controllerSources.Contains('@GetMapping("/{uf}/{cidade}/{bairro}")') -and
  $controllerSources.Contains('@GetMapping("/{slug}")')
) "controllers V3 devem existir sem alias"

$legacyContracts = @(
  @{ Pattern = "/anuncios/por-estado/"; Nome = "listagem por estado" },
  @{ Pattern = "/anuncios/por-cidade/"; Nome = "listagem por cidade" },
  @{ Pattern = "/anuncios/seo/cidade/"; Nome = "agregado SEO de cidade" },
  @{ Pattern = "/anuncios/por-bairro/"; Nome = "listagem por bairro" },
  @{ Pattern = "/anuncios/publico/slug/"; Nome = "detalhe de anuncio" },
  @{ Pattern = "/anuncios/cidades-ativas"; Nome = "descoberta de localidades" },
  @{ Pattern = "/anuncios/cidades-por-estado/"; Nome = "cidades por estado" },
  @{ Pattern = "/anuncios/bairros-por-cidade/"; Nome = "bairros por cidade" }
)

$frontendSources = Get-ChildItem -LiteralPath (Get-RepoPath "frontend/src") -Recurse -File -Include *.ts,*.tsx |
  ForEach-Object { [IO.File]::ReadAllText($_.FullName, [Text.UTF8Encoding]::new($false, $false)) } |
  Out-String
foreach ($contract in $legacyContracts) {
  Add-Check "legado removido: $($contract.Nome)" (-not $frontendSources.Contains($contract.Pattern)) "frontend nao pode usar $($contract.Pattern)"
}

$failed = @($checks | Where-Object { $_.Resultado -ne "OK" })
Write-Host "Validacao SEO publico"
Write-Host "Total de verificacoes: $($checks.Count)"
Write-Host "Verificacoes OK: $(@($checks | Where-Object { $_.Resultado -eq 'OK' }).Count)"
Write-Host "Verificacoes com falha: $($failed.Count)"

if ($failed.Count -gt 0) {
  foreach ($check in $failed) {
    Write-Host "- $($check.Nome): $($check.Detalhe)"
  }
  Write-Host "VALIDATION_RESULT=FALHA_SEO_PUBLICO"
  exit 1
}

if ($pending.Count -gt 0) {
  Write-Host "Pendencias de contrato publico V3:"
  $pending | Sort-Object -Unique | ForEach-Object { Write-Host "- $_" }
  Write-Host "VALIDATION_RESULT=OK_SEO_ESTRUTURAL_COM_PENDENCIAS_CONTRATOS_V3"
  exit 0
}

Write-Host "VALIDATION_RESULT=OK_SEO_PUBLICO"
exit 0
