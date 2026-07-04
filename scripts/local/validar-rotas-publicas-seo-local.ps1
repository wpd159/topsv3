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
  return (Get-Content -LiteralPath (Get-RepoPath $RelativePath) -Raw)
}

function Get-ExistingText {
  param([string[]]$RelativePaths)
  $buffer = New-Object System.Text.StringBuilder
  foreach ($path in $RelativePaths) {
    if (Test-RepoFile $path) {
      [void]$buffer.AppendLine((Get-RepoText $path))
    }
  }
  return $buffer.ToString()
}

function Get-RelativePath {
  param([string]$FullName)
  return ($FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/')
}

function Test-IgnoredBuildOutput {
  param([string]$FullName)
  $relative = Get-RelativePath $FullName
  return (
    $relative -like "backend/target/*" -or
    $relative -like "frontend/node_modules/*" -or
    $relative -like "frontend/.next/*" -or
    $relative -like "frontend/out/*" -or
    $relative -like "frontend/dist/*" -or
    $relative -like ".git/*"
  )
}

function Test-AllowedLocalSyntheticSql {
  param([string]$FullName)
  $relative = Get-RelativePath $FullName
  return ($relative -like "scripts/local/dados-sinteticos/*.sql")
}

$publicRouteFiles = @(
  "frontend/src/app/anuncios/[slug]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/[bairro]/page.tsx"
)

foreach ($file in $publicRouteFiles) {
  Add-Check "arquivo de rota publica existe: $file" (Test-RepoFile $file) "contrato publico local"
}

Add-Check "validador renderizado existe" (Test-RepoFile "scripts/local/validar-layout-publico-renderizado.ps1") "gate visual obrigatorio para layout publico"

$routeContracts = @(
  @{
    File = "frontend/src/app/anuncios/[slug]/page.tsx"
    Pattern = "/anuncios/[slug]"
    Title = "rota publica absoluta de anuncio"
    Helper = "buildAnuncioSeo"
    Breadcrumbs = "anuncioBreadcrumbs"
  },
  @{
    File = "frontend/src/app/acompanhantes/[uf]/[cidade]/page.tsx"
    Pattern = "/acompanhantes/[uf]/[cidade]"
    Title = "rota publica de cidade"
    Helper = "buildCitySeo"
    Breadcrumbs = "cityBreadcrumbs"
  },
  @{
    File = "frontend/src/app/acompanhantes/[uf]/[cidade]/[bairro]/page.tsx"
    Pattern = "/acompanhantes/[uf]/[cidade]/[bairro]"
    Title = "rota publica de bairro"
    Helper = "buildBairroSeo"
    Breadcrumbs = "bairroBreadcrumbs"
  }
)

foreach ($contract in $routeContracts) {
  if (Test-RepoFile $contract.File) {
    $text = Get-RepoText $contract.File
    $visibleText = (($text -split "`r?`n") | Where-Object { $_ -notmatch '^\s*import\s' -and $_ -notmatch '\sfrom\s+["'']' }) -join "`n"
    Add-Check "$($contract.Title) preserva $($contract.Pattern)" $true "arquivo existe no caminho da rota preservada"
    Add-Check "$($contract.Pattern) usa helper SEO publico" ($text.Contains($contract.Helper)) "metadata publica local centralizada"
    Add-Check "$($contract.Pattern) usa breadcrumbs" ($text.Contains($contract.Breadcrumbs)) "breadcrumbs obrigatorios"
    Add-Check "$($contract.Pattern) nao declara texto tecnico principal" (-not ($visibleText -match '(?i)SEO local|Texto SEO local|SEO por cidade e bairro|skeleton local|placeholder local|ambiente local|ambiente de validacao|ambiente de validação|api local|dados sinteticos|dados sintéticos|rota preservada|\bV3\b')) "pagina publica nao pode parecer tecnica"
  }
}

$forbiddenRouteRoots = @(
  "frontend/src/app/anuncio",
  "frontend/src/app/perfil",
  "frontend/src/app/acompanhante",
  "frontend/src/app/ads"
)

foreach ($path in $forbiddenRouteRoots) {
  Add-Check "rota alternativa proibida ausente: $path" (-not (Test-RepoDirectory $path)) "anuncios usam somente /anuncios/[slug]"
}

$frontendFiles = @(Get-ChildItem -LiteralPath (Get-RepoPath "frontend/src/app") -Recurse -File -ErrorAction SilentlyContinue)
$forbiddenRouteFiles = @($frontendFiles | Where-Object {
  $relative = $_.FullName.Substring((Get-RepoPath "frontend/src/app").Length).TrimStart('\', '/') -replace '\\', '/'
  $relative -match '^(anuncio|perfil|acompanhante|ads)(/|$)'
})
Add-Check "nenhum arquivo de rota alternativa proibida" ($forbiddenRouteFiles.Count -eq 0) "rotas proibidas encontradas: $($forbiddenRouteFiles.Count)"

$publicAndAdminFiles = @(
  "frontend/src/app/anuncios/[slug]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/[bairro]/page.tsx",
  "frontend/src/app/robots.ts",
  "frontend/src/app/sitemap.ts",
  "frontend/src/lib/seo/localSeo.ts",
  "frontend/src/modules/public/skeleton/PublicRouteShell.tsx",
  "frontend/src/modules/public/skeleton/SeoPlaceholder.tsx",
  "frontend/src/modules/admin/shell/AdminShell.tsx",
  "frontend/src/modules/admin/shell/AdminPlaceholderPage.tsx",
  "frontend/src/modules/admin/shell/AdminModuleCard.tsx",
  "frontend/src/modules/admin/shell/adminModules.ts"
)

$sourceText = Get-ExistingText $publicAndAdminFiles
Add-Check "links para rotas alternativas proibidas ausentes" (-not ($sourceText -match '["'']/(anuncio|perfil|acompanhante|ads)/')) "links internos nao podem introduzir rota paralela"
Add-Check "sem dominio de producao em rotas e SEO local" (-not ($sourceText -match 'https://(www\.)?topsdojob\.com')) "canonical ativo local nao deve emitir producao"
Add-Check "sem imagens reais nas rotas publicas" (-not ($sourceText -match '<img|next/image|\.(png|jpe?g|webp|gif|avif|svg)\b')) "rotas publicas locais nao carregam midia real"
Add-Check "sem chamadas externas nas rotas publicas" (-not ($sourceText -match 'fetch\(|axios|XMLHttpRequest|api\.openai|chatgpt|gemini|perplexity|https?://(?!localhost|127\.0\.0\.1)')) "nenhuma API externa deve ser chamada"
Add-Check "sem chamadas a backend de dominio" (-not ($sourceText -match '/api/(anuncios|acompanhantes|busca|search|admin|financeiro|pix|pagamentos)')) "sem backend funcional nesta fase"

$robotsPath = "frontend/src/app/robots.ts"
Add-Check "robots.ts existe" (Test-RepoFile $robotsPath) "arquivo local obrigatorio"
if (Test-RepoFile $robotsPath) {
  $robots = Get-RepoText $robotsPath
  Add-Check "robots local bloqueia indexacao" ($robots.Contains('disallow: "/"')) "ambiente local deve ser noindex"
  Add-Check "robots usa localUrl para sitemap" ($robots.Contains("localUrl(")) "sitemap local deve vir do helper"
  Add-Check "robots sem dominio de producao" (-not ($robots -match 'topsdojob\.com')) "sem URL canonica de producao no local"
}

$sitemapPath = "frontend/src/app/sitemap.ts"
Add-Check "sitemap.ts existe" (Test-RepoFile $sitemapPath) "arquivo local obrigatorio"
if (Test-RepoFile $sitemapPath) {
  $sitemap = Get-RepoText $sitemapPath
  Add-Check "sitemap local usa localUrl" ($sitemap.Contains("localUrl(")) "URLs locais devem vir do helper"
  Add-Check "sitemap preserva anuncio publico" ($sitemap.Contains('/anuncios/anuncio-exemplo')) "rota publica de anuncio presente no sitemap local"
  Add-Check "sitemap preserva paginas locais SEO" ($sitemap.Contains('/acompanhantes/go/goiania') -and $sitemap.Contains('/acompanhantes/go/goiania/setor-bueno')) "rotas publicas locais presentes"
  Add-Check "sitemap sem dominio de producao" (-not ($sitemap -match 'topsdojob\.com')) "sitemap local nao pode emitir producao"
  Add-Check "sitemap sem API/admin" (-not ($sitemap -match '["'']/(api|admin)')) "sitemap local nao deve expor API/admin"
  Add-Check "sitemap sem skeleton" (-not ($sitemap -match '(?i)skeleton')) "sitemap local nao deve conter rota fraca"
}

$localSeoPath = "frontend/src/lib/seo/localSeo.ts"
$publicEnvPath = "frontend/src/lib/config/publicEnv.ts"
Add-Check "helper SEO local existe" (Test-RepoFile $localSeoPath) "canonical local centralizado"
Add-Check "config publica local existe" (Test-RepoFile $publicEnvPath) "NEXT_PUBLIC_CANONICAL_DOMAIN centralizado"
if ((Test-RepoFile $localSeoPath) -and (Test-RepoFile $publicEnvPath)) {
  $localSeo = Get-RepoText $localSeoPath
  $publicEnv = Get-RepoText $publicEnvPath
  Add-Check "canonical usa NEXT_PUBLIC_CANONICAL_DOMAIN" ($publicEnv.Contains("NEXT_PUBLIC_CANONICAL_DOMAIN") -and $localSeo.Contains("publicEnv.canonicalDomain")) "dominio canonico configuravel por ambiente"
  Add-Check "canonical local tem fallback seguro" ($localSeo.Contains("http://localhost") -and $localSeo.Contains("LOCAL_FALLBACK_ORIGIN")) "fallback local esperado"
  Add-Check "ambiente local bloqueia canonical de producao" ($localSeo.Contains('publicEnv.appEnv === "local"') -and $localSeo.Contains("return LOCAL_FALLBACK_ORIGIN")) "local substitui dominio nao local"
Add-Check "metadata local aplica noindex" ($localSeo.Contains("index: false") -and $localSeo.Contains("follow: false")) "paginas publicas locais e admin ficam noindex"
}

$adminPageFiles = @(
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

$adminNoindexOk = $true
$adminAllExist = $true
foreach ($path in $adminPageFiles) {
  if (-not (Test-RepoFile $path)) {
    $adminAllExist = $false
    $adminNoindexOk = $false
  } else {
    $page = Get-RepoText $path
    if (-not $page.Contains("skeletonMetadata(")) { $adminNoindexOk = $false }
  }
}
Add-Check "paginas admin existem" $adminAllExist "admin skeleton estrutural da Fase 1C.6B"
Add-Check "paginas admin continuam noindex" $adminNoindexOk "skeletonMetadata aplica robots noindex"

$publicShell = if (Test-RepoFile "frontend/src/modules/public/skeleton/PublicRouteShell.tsx") { Get-RepoText "frontend/src/modules/public/skeleton/PublicRouteShell.tsx" } else { "" }
$adminShell = if (Test-RepoFile "frontend/src/modules/admin/shell/AdminShell.tsx") { Get-RepoText "frontend/src/modules/admin/shell/AdminShell.tsx" } else { "" }
Add-Check "public shell sem marcador tecnico principal" (-not ($publicShell -match '(?i)SKELETON LOCAL|Previa local|Rota preservada')) "frontend publico nao deve exibir texto tecnico"
Add-Check "admin skeleton marcado como temporario" ($adminShell.Contains("ADMIN SKELETON LOCAL") -and $adminShell.Contains("apenas estrutural")) "admin ainda nao e funcional"

$publicSeoPath = "frontend/src/lib/seo/publicSeo.ts"
if (Test-RepoFile $publicSeoPath) {
  $publicSeo = Get-RepoText $publicSeoPath
  Add-Check "breadcrumbs sem link para /acompanhantes raiz" (-not $publicSeo.Contains('href: "/acompanhantes"')) "rota /acompanhantes nao existe neste bloco"
  Add-Check "breadcrumbs sem link para /acompanhantes/[uf]" (-not $publicSeo.Contains('/acompanhantes/${routeSegment(ufLabel.toLowerCase())}')) "rota /acompanhantes/[uf] nao existe neste bloco"
}

$globalsPath = "frontend/src/app/globals.css"
if (Test-RepoFile $globalsPath) {
  $globals = Get-RepoText $globalsPath
  $anywhereCount = ([regex]::Matches($globals, 'overflow-wrap:\s*anywhere')).Count
  Add-Check "overflow-wrap anywhere restrito a token tecnico" ($anywhereCount -le 1 -and $globals.Contains(".route-pattern")) "H1, breadcrumbs, botoes e wizard nao podem quebrar letra por letra"
  Add-Check "public-shell tem largura renderizavel" ($globals.Contains("width: min(100%, 1120px)") -and $globals.Contains("max-inline-size: 1120px")) "shell publico nao pode colapsar em mini-coluna"
}

$sqlFiles = @(Get-ChildItem -LiteralPath $repoRoot -Recurse -File -Filter *.sql -ErrorAction SilentlyContinue | Where-Object { -not (Test-IgnoredBuildOutput $_.FullName) })
$migrationRel = "backend/src/main/resources/db/migration"
$migrationDir = Get-RepoPath $migrationRel
$sqlOutsideMigration = @($sqlFiles | Where-Object {
  (-not $_.FullName.StartsWith($migrationDir, [System.StringComparison]::OrdinalIgnoreCase)) -and
  (-not (Test-AllowedLocalSyntheticSql $_.FullName))
})
$invalidMigrationNames = @($sqlFiles | Where-Object {
  $_.FullName.StartsWith($migrationDir, [System.StringComparison]::OrdinalIgnoreCase) -and $_.Name -notmatch '^V[0-9]{3}__[a-z0-9_]+\.sql$'
})
Add-Check "SQL restrito a migrations Flyway da Fase 1D" (($sqlOutsideMigration.Count -eq 0) -and ($invalidMigrationNames.Count -eq 0)) "fora de migrations: $($sqlOutsideMigration.Count); nomes invalidos: $($invalidMigrationNames.Count)"
Add-Check "diretorio de migrations permitido apenas para auditoria 1D" (Test-RepoDirectory $migrationRel) "migrations existem, mas nao foram aplicadas"

$domainPath = "backend/src/main/java/br/com/topsdojob/v3/domain"
$domainFiles = @()
if (Test-RepoDirectory $domainPath) {
  $domainFiles = @(Get-ChildItem -LiteralPath (Get-RepoPath $domainPath) -Recurse -File -Filter *.java -ErrorAction SilentlyContinue)
}
$functionalDomainFiles = @($domainFiles | Where-Object { $_.Name -match '(Controller|Service|Repository)\.java$' })
$domainText = New-Object System.Text.StringBuilder
foreach ($file in $domainFiles) {
  [void]$domainText.AppendLine((Get-Content -LiteralPath $file.FullName -Raw))
}
Add-Check "dominio Bloco 3 sem controller/service/repository" ($functionalDomainFiles.Count -eq 0) "arquivos funcionais encontrados: $($functionalDomainFiles.Count)"
Add-Check "dominio Bloco 3 sem anotacoes JPA" (-not ($domainText.ToString() -match '@Entity|@Table|jakarta\.persistence')) "JPA permanece pendente"
Add-Check "dominio Bloco 3 sem Spring Data JPA" (-not ($domainText.ToString() -match 'JpaRepository|org\.springframework\.data\.jpa')) "repositories permanecem pendentes"

$forbiddenBackendPaths = @(
  "backend/src/main/java/br/com/topsdojob/v3/repository",
  "backend/src/main/java/br/com/topsdojob/v3/service"
)
foreach ($path in $forbiddenBackendPaths) {
  Add-Check "ausencia de $path" (-not (Test-RepoDirectory $path)) "sem camada funcional de dominio nesta fase"
}

$failed = @($checks | Where-Object { $_.Resultado -ne "OK" })

Write-Host "Validacao local de rotas publicas e SEO local"
Write-Host "Total de verificacoes: $($checks.Count)"
Write-Host "Verificacoes OK: $(@($checks | Where-Object { $_.Resultado -eq "OK" }).Count)"
Write-Host "Verificacoes com falha: $($failed.Count)"

if ($failed.Count -gt 0) {
  Write-Host ""
  Write-Host "Falhas encontradas:"
  foreach ($check in $failed) {
    Write-Host "- $($check.Nome): $($check.Detalhe)"
  }
  exit 1
}

Write-Host "Validacao concluida sem falhas. Rotas publicas e SEO local permanecem protegidos."
exit 0
