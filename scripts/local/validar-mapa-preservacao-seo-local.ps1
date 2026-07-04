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

function Get-CombinedText {
  param([string[]]$Paths)
  $builder = New-Object System.Text.StringBuilder
  foreach ($path in $Paths) {
    if (Test-RepoFile $path) {
      [void]$builder.AppendLine("===== $path =====")
      [void]$builder.AppendLine((Get-RepoText $path))
    }
  }
  return $builder.ToString()
}

$requiredDocs = @(
  "docs/v3/188-bloco-28-inventario-seo-preservacao.md",
  "docs/v3/189-checklist-bloco-28-inventario-seo-preservacao.md",
  "docs/v3/SEO-inventario-producao-sanitizado.md",
  "docs/v3/SEO-mapa-preservacao-urls-v3.md",
  "docs/v3/SEO-plano-redirecionamentos-301.md",
  "docs/v3/SEO-canonical-sitemap-robots-cutover.md",
  "docs/v3/SEO-baseline-search-console-template.md",
  "docs/v3/SEO-matriz-risco-perda-trafego.md",
  "docs/v3/SEO-cidades-prioritarias.md",
  "docs/v3/SEO-prioridade-central-v3.md",
  "docs/v3/SEO-mapa-preservacao-urls.md",
  "docs/v3/SEO-plano-cidade-bairro.md",
  "docs/v3/SEO-checklist-cutover.md",
  "docs/v3/SEO-baseline-search-console.md",
  "docs/v3/SEO-auditoria-producao-publica.md",
  "docs/v3/SEO-linkagem-interna-v3.md",
  "docs/v3/SEO-padroes-metadata-publica.md",
  "docs/v3/evidencias/bloco-28/relatorio-inventario-producao-sanitizado.md",
  "docs/v3/evidencias/bloco-28/relatorio-mapa-preservacao-seo.md",
  "docs/v3/evidencias/bloco-28/relatorio-validacao-mapa-seo.md",
  "docs/v3/evidencias/bloco-28/relatorio-consulta-producao-somente-leitura.md",
  "docs/v3/evidencias/bloco-28/relatorio-search-console-baseline.md"
)

foreach ($doc in $requiredDocs) {
  Add-Check "documento obrigatorio existe: $doc" (Test-RepoFile $doc) "arquivo requerido pelo Bloco 28"
}

$sddDocs = @(
  "docs/v3/SDD.md",
  "docs/v3/SDD-indice-rastreabilidade.md",
  "docs/v3/SDD-decisoes-consolidadas.md",
  "docs/v3/SDD-pendencias-gates.md",
  "README.md",
  "CONTRIBUTING.md"
)

foreach ($doc in $sddDocs) {
  Add-Check "documento de rastreabilidade existe: $doc" (Test-RepoFile $doc) "SDD/continuidade obrigatoria"
}

$combinedRequired = Get-CombinedText -Paths ($requiredDocs + $sddDocs)

Add-Check "SDD menciona SEO como prioridade central" ($combinedRequired -match "SEO como prioridade central|SEO segue como prioridade central|prioridade central da V3") "decisao precisa estar rastreavel"
Add-Check "mapa de preservacao existe" (Test-RepoFile "docs/v3/SEO-mapa-preservacao-urls-v3.md") "mapa sanitizado do Bloco 28"
Add-Check "checklist de cutover existe" (Test-RepoFile "docs/v3/SEO-checklist-cutover.md") "gate de cutover SEO"
Add-Check "baseline Search Console existe" ((Test-RepoFile "docs/v3/SEO-baseline-search-console.md") -and (Test-RepoFile "docs/v3/SEO-baseline-search-console-template.md")) "baseline e template precisam existir"

Add-Check "secao 301 documentada" ($combinedRequired -match "301|redirecionamento") "plano de redirecionamento obrigatorio"
Add-Check "secao canonical documentada" ($combinedRequired -match "canonical") "canonical obrigatorio"
Add-Check "secao sitemap documentada" ($combinedRequired -match "sitemap") "sitemap obrigatorio"
Add-Check "secao robots documentada" ($combinedRequired -match "robots") "robots obrigatorio"
Add-Check "secao rollback documentada" ($combinedRequired -match "rollback") "rollback obrigatorio"
Add-Check "secao Search Console documentada" ($combinedRequired -match "Search Console") "baseline/exportacao obrigatoria"
Add-Check "secao URLs cidade documentada" ($combinedRequired -match "/acompanhantes/\[uf\]/\[cidade\]") "padrao cidade obrigatorio"
Add-Check "secao URLs bairro documentada" ($combinedRequired -match "/acompanhantes/\[uf\]/\[cidade\]/\[bairro\]") "padrao bairro obrigatorio"
Add-Check "secao URLs anuncio documentada" ($combinedRequired -match "/anuncios/\[slug\]") "padrao anuncio obrigatorio"
Add-Check "inventario bruto externo documentado" ($combinedRequired -match "fora do repositorio|saida bruta externa|inventario bruto externo") "lista completa deve ficar fora do repo"
Add-Check "lista bruta de anuncios nao versionada documentada" ($combinedRequired -match "lista bruta.*anuncios.*nao.*versionada|nao versionar lista bruta") "protege slugs sensiveis"
Add-Check "cutover bloqueado ate mapa aprovado" ($combinedRequired -match "cutover SEO.*bloquead|mapa completo aprovado") "bloqueio de cutover obrigatorio"
Add-Check "exportacao manual Search Console pendente" ($combinedRequired -match "exportacao manual|exportar manualmente") "nao acessar Search Console automaticamente"

$seoDocFiles = @(Get-ChildItem -LiteralPath (Get-RepoPath "docs/v3") -File -Filter "SEO*.md" -ErrorAction SilentlyContinue)
$evidenceDocFiles = @(Get-ChildItem -LiteralPath (Get-RepoPath "docs/v3/evidencias/bloco-28") -File -Filter "*.md" -ErrorAction SilentlyContinue)
$scanFiles = @($seoDocFiles + $evidenceDocFiles)

$realAdUrlFindings = New-Object System.Collections.Generic.List[string]
$sensitiveFindings = New-Object System.Collections.Generic.List[string]
foreach ($file in $scanFiles) {
  $relative = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
  $lines = [System.IO.File]::ReadAllLines($file.FullName)
  for ($i = 0; $i -lt $lines.Length; $i++) {
    $line = $lines[$i]
    if ($line -match "https://topsdojob\.com/anuncios/[A-Za-z0-9][A-Za-z0-9-]{2,}") {
      $realAdUrlFindings.Add(("{0}:{1}" -f $relative, ($i + 1)))
    }
    if ($line -match "\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b") {
      $sensitiveFindings.Add(("{0}:{1}: cpf/documento" -f $relative, ($i + 1)))
    }
    if ($line -match "\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b" -and $line -notmatch "(?i)example\.|localhost|dominio|seu-dominio") {
      $sensitiveFindings.Add(("{0}:{1}: email" -f $relative, ($i + 1)))
    }
    if ($line -match "(?i)(token|secret|senha|password|bucket|storage key)\s*[:=]\s*['`"]?[A-Za-z0-9_./+=-]{8,}") {
      $sensitiveFindings.Add(("{0}:{1}: possivel segredo" -f $relative, ($i + 1)))
    }
    if ($line -match "(?i)(whatsapp|telefone).{0,20}(\+?55)?\s*\(?\d{2}\)?\s*9?\d{4}[-\s]?\d{4}") {
      $sensitiveFindings.Add(("{0}:{1}: telefone/whatsapp" -f $relative, ($i + 1)))
    }
  }
}

Add-Check "docs sem URL real bruta de anuncio" ($realAdUrlFindings.Count -eq 0) (($realAdUrlFindings | Select-Object -First 10) -join "; ")
Add-Check "docs SEO sem dado sensivel obvio" ($sensitiveFindings.Count -eq 0) (($sensitiveFindings | Select-Object -First 10) -join "; ")

$forbiddenRouteDirs = @(
  "frontend/src/app/anuncio",
  "frontend/src/app/perfil",
  "frontend/src/app/acompanhante",
  "frontend/src/app/ads"
)
foreach ($dir in $forbiddenRouteDirs) {
  Add-Check "rota proibida ausente: $dir" (-not (Test-RepoDirectory $dir)) "nao criar rota paralela"
}

$frontendRoutes = Get-CombinedText -Paths @(
  "frontend/src/app/sitemap.ts",
  "frontend/src/lib/seo/publicSeo.ts",
  "frontend/src/app/anuncios/[slug]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/page.tsx",
  "frontend/src/app/acompanhantes/[uf]/[cidade]/[bairro]/page.tsx"
)
foreach ($routePattern in @("/anuncio/[id]", "/perfil/[slug]", "/acompanhante/[slug]", "/ads/[slug]")) {
  $literal = [regex]::Escape($routePattern)
  Add-Check "padrao proibido ausente em frontend: $routePattern" (-not ($frontendRoutes -match $literal)) "rotas antigas nao devem voltar"
}

$failed = @($checks | Where-Object { $_.Resultado -ne "OK" })

Write-Host "Validacao mapa de preservacao SEO"
Write-Host "Total de verificacoes: $($checks.Count)"
Write-Host "Verificacoes OK: $(@($checks | Where-Object { $_.Resultado -eq "OK" }).Count)"
Write-Host "Verificacoes com falha: $($failed.Count)"

if ($failed.Count -gt 0) {
  Write-Host ""
  Write-Host "Falhas encontradas:"
  foreach ($check in $failed) {
    Write-Host "- $($check.Nome): $($check.Detalhe)"
  }
  Write-Host "VALIDATION_RESULT=FALHA_MAPA_PRESERVACAO_SEO"
  exit 1
}

Write-Host "VALIDATION_RESULT=OK_MAPA_PRESERVACAO_SEO"
exit 0
