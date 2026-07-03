Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

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

function Get-RepoPath {
  param([string]$RelativePath)
  return (Join-Path $repoRoot ($RelativePath -replace '/', [IO.Path]::DirectorySeparatorChar))
}

function Test-RepoFile {
  param([string]$RelativePath)
  return (Test-Path -LiteralPath (Get-RepoPath $RelativePath) -PathType Leaf)
}

function Test-RepoDirectory {
  param([string]$RelativePath)
  return (Test-Path -LiteralPath (Get-RepoPath $RelativePath) -PathType Container)
}

function Get-RepoText {
  param([string]$RelativePath)
  return [System.IO.File]::ReadAllText((Get-RepoPath $RelativePath))
}

function Add-TextCheck {
  param(
    [string]$Path,
    [string]$Pattern,
    [string]$Nome,
    [bool]$ShouldMatch,
    [string]$Detalhe
  )
  if (-not (Test-RepoFile $Path)) {
    Add-Check $Nome $false "arquivo ausente: $Path"
    return
  }
  $text = Get-RepoText $Path
  $matched = ($text -match $Pattern)
  Add-Check $Nome ($matched -eq $ShouldMatch) $Detalhe
}

$publicRouteFiles = @(
  "frontend/src/app/page.tsx",
  "frontend/src/app/anuncios/[slug]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/[bairro]/page.tsx",
  "frontend/src/app/anunciar/page.tsx"
)

foreach ($file in $publicRouteFiles) {
  Add-Check "rota publica existe: $file" (Test-RepoFile $file) "rota publica preservada"
}

$forbiddenRouteRoots = @(
  "frontend/src/app/anuncio",
  "frontend/src/app/acompanhante",
  "frontend/src/app/perfil",
  "frontend/src/app/ads"
)
foreach ($path in $forbiddenRouteRoots) {
  Add-Check "rota alternativa proibida ausente: $path" (-not (Test-RepoDirectory $path)) "nao criar rota paralela"
}

Add-TextCheck "frontend/src/lib/seo/localSeo.ts" "LOCAL_FALLBACK_ORIGIN" "canonical local centralizado" $true "helper deve concentrar origem local"
Add-TextCheck "frontend/src/lib/seo/localSeo.ts" "publicEnv\.appEnv === `"local`"" "canonical local protege producao" $true "local substitui dominio nao local"
Add-TextCheck "frontend/src/lib/seo/localSeo.ts" "topsdojob\.com" "helper SEO sem dominio de producao hardcoded" $false "canonical local nao deve fixar producao"

$sitemapPath = "frontend/src/app/sitemap.ts"
Add-Check "sitemap.ts existe" (Test-RepoFile $sitemapPath) "sitemap local obrigatorio"
if (Test-RepoFile $sitemapPath) {
  $sitemap = Get-RepoText $sitemapPath
  Add-Check "sitemap usa localUrl" ($sitemap.Contains("localUrl(")) "URLs locais devem vir do helper"
  Add-Check "sitemap preserva anuncio" ($sitemap.Contains('/anuncios/skeleton-local')) "rota /anuncios/[slug] presente"
  Add-Check "sitemap preserva cidade" ($sitemap.Contains('/acompanhantes/xx/local')) "rota de cidade presente"
  Add-Check "sitemap preserva bairro" ($sitemap.Contains('/acompanhantes/xx/local/skeleton')) "rota de bairro presente"
  Add-Check "sitemap preserva anunciar" ($sitemap.Contains('/anunciar')) "rota /anunciar presente"
  Add-Check "sitemap sem dominio de producao" (-not ($sitemap -match 'topsdojob\.com')) "nao emitir producao no local"
  Add-Check "sitemap sem rota API" (-not ($sitemap -match '["'']/api/')) "API nao deve entrar no sitemap"
}

$robotsPath = "frontend/src/app/robots.ts"
Add-Check "robots.ts existe" (Test-RepoFile $robotsPath) "robots local obrigatorio"
if (Test-RepoFile $robotsPath) {
  $robots = Get-RepoText $robotsPath
  Add-Check "robots bloqueia indexacao local" ($robots.Contains('disallow: "/"')) "local deve ser noindex"
  Add-Check "robots aponta sitemap local" ($robots.Contains("localUrl(")) "sitemap deve usar origem local"
  Add-Check "robots sem dominio de producao" (-not ($robots -match 'topsdojob\.com')) "robots local nao deve fixar producao"
}

$adminPages = @(Get-ChildItem -LiteralPath (Get-RepoPath "frontend/src/app/admin") -Recurse -File -Filter "page.tsx" -ErrorAction SilentlyContinue)
$adminNoindexOk = $true
foreach ($page in $adminPages) {
  $text = [System.IO.File]::ReadAllText($page.FullName)
  if (-not $text.Contains("skeletonMetadata(")) {
    $adminNoindexOk = $false
  }
}
Add-Check "paginas admin seguem noindex local" ($adminPages.Count -gt 0 -and $adminNoindexOk) "admin deve usar skeletonMetadata"

$publicTextFiles = @(
  "frontend/src/app/anuncios/[slug]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/[bairro]/page.tsx",
  "frontend/src/app/anunciar/page.tsx",
  "frontend/src/modules/public/components/PublicAnunciarForm.tsx",
  "frontend/src/modules/public/components/PublicAnunciarWizard.tsx",
  "frontend/src/modules/public/components/PublicSeoTextBlock.tsx",
  "frontend/src/modules/public/skeleton/SeoPlaceholder.tsx",
  "frontend/src/modules/public/skeleton/PublicRouteShell.tsx"
)

$technicalFindings = New-Object System.Collections.Generic.List[string]
foreach ($file in $publicTextFiles) {
  if (-not (Test-RepoFile $file)) { continue }
  $lines = [System.IO.File]::ReadAllLines((Get-RepoPath $file))
  for ($i = 0; $i -lt $lines.Length; $i++) {
    $line = $lines[$i]
    if ($line.Contains("data-debug-label")) { continue }
    if ($line -match '(?i)skeleton local|api local') {
      $technicalFindings.Add(("{0}:{1}: {2}" -f $file, ($i + 1), $line.Trim()))
    }
  }
}
Add-Check "textos publicos sem skeleton/API local" ($technicalFindings.Count -eq 0) (($technicalFindings | Select-Object -First 5) -join "; ")

$linksText = ""
foreach ($file in @(
    "frontend/src/modules/public/components/PublicAnuncioCard.tsx",
    "frontend/src/modules/public/components/PublicHomeHero.tsx"
  )) {
  if (Test-RepoFile $file) {
    $linksText += [Environment]::NewLine + (Get-RepoText $file)
  }
}
Add-Check "link de anuncio preservado" ($linksText.Contains('/anuncios/${item.slug}') -or $linksText.Contains('/anuncios/')) "cards devem apontar para /anuncios/[slug]"
Add-Check "link de cidade preservado" ($linksText.Contains('/acompanhantes/zz/cidade-sintetica')) "home deve preservar rota por cidade"
Add-Check "link de bairro preservado" ($linksText.Contains('/acompanhantes/zz/cidade-sintetica/bairro-sintetico')) "home deve preservar rota por bairro"

$seoDocs = @(
  "docs/v3/SEO-prioridade-central-v3.md",
  "docs/v3/SEO-mapa-preservacao-urls.md",
  "docs/v3/SEO-plano-cidade-bairro.md",
  "docs/v3/SEO-checklist-cutover.md",
  "docs/v3/SEO-baseline-search-console.md"
)
foreach ($doc in $seoDocs) {
  Add-Check "documento SEO existe: $doc" (Test-RepoFile $doc) "documentacao central de SEO"
}

if (Test-RepoFile "docs/v3/SEO-baseline-search-console.md") {
  $baseline = Get-RepoText "docs/v3/SEO-baseline-search-console.md"
  Add-Check "baseline Search Console 24h registrado" ($baseline.Contains("92 cliques") -and $baseline.Contains("432 impressoes") -and $baseline.Contains("21.3%")) "baseline informado pelo usuario"
  Add-Check "baseline aponta intencao local" ($baseline.Contains("acompanhante em [cidade]")) "meta de intencao local documentada"
}

if (Test-RepoFile "docs/v3/SEO-checklist-cutover.md") {
  $cutover = Get-RepoText "docs/v3/SEO-checklist-cutover.md"
  Add-Check "noindex local tratado como pendencia de cutover" ($cutover.Contains("remover noindex somente no cutover aprovado")) "evita noindex indevido em producao futura"
}

$failed = @($checks | Where-Object { $_.Resultado -ne "OK" })

Write-Host "Validacao SEO publico local"
Write-Host "Total de verificacoes: $($checks.Count)"
Write-Host "Verificacoes OK: $(@($checks | Where-Object { $_.Resultado -eq "OK" }).Count)"
Write-Host "Verificacoes com falha: $($failed.Count)"

if ($failed.Count -gt 0) {
  Write-Host ""
  Write-Host "Falhas encontradas:"
  foreach ($check in $failed) {
    Write-Host "- $($check.Nome): $($check.Detalhe)"
  }
  Write-Host "VALIDATION_RESULT=FALHA_SEO_PUBLICO_LOCAL"
  exit 1
}

Write-Host "VALIDATION_RESULT=OK_SEO_PUBLICO_LOCAL"
exit 0
