param(
  [string]$BaseUrl = "https://topsdojob.com",
  [string]$OutputDir = "C:\topsv3-auditoria-local\seo\bloco-28",
  [string]$SanitizedReport = "docs/v3/evidencias/bloco-28/relatorio-inventario-producao-sanitizado.md",
  [int]$MaxExamplesPerType = 5,
  [int]$MaxChildSitemaps = 20
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

function Get-FullPathNormalized {
  param([string]$Path)
  return [System.IO.Path]::GetFullPath($Path).TrimEnd('\', '/')
}

function Test-PathInside {
  param(
    [string]$Child,
    [string]$Parent
  )
  $childFull = Get-FullPathNormalized $Child
  $parentFull = Get-FullPathNormalized $Parent
  return (
    $childFull.Equals($parentFull, [System.StringComparison]::OrdinalIgnoreCase) -or
    $childFull.StartsWith($parentFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)
  )
}

function Resolve-RepoPath {
  param([string]$Path)
  if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
  return (Join-Path $repoRoot ($Path -replace '/', [IO.Path]::DirectorySeparatorChar))
}

function Normalize-BaseUrl {
  param([string]$Url)
  $trimmed = $Url.Trim().TrimEnd("/")
  $uri = [Uri]$trimmed
  if ($uri.Scheme -ne "https") {
    throw "BaseUrl deve usar HTTPS: $Url"
  }
  return $trimmed
}

function Invoke-PublicGet {
  param([string]$Url)
  try {
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -MaximumRedirection 5 -TimeoutSec 25 -Headers @{
      "User-Agent" = "topsv3-seo-inventario-local/1.0 readonly"
    }
    return [pscustomobject]@{
      Ok = $true
      Url = $Url
      StatusCode = [int]$response.StatusCode
      Headers = $response.Headers
      Content = [string]$response.Content
      Error = ""
    }
  } catch {
    $statusCode = 0
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
      $statusCode = [int]$_.Exception.Response.StatusCode
    }
    return [pscustomobject]@{
      Ok = $false
      Url = $Url
      StatusCode = $statusCode
      Headers = @{}
      Content = ""
      Error = $_.Exception.Message
    }
  }
}

function Get-LocValues {
  param([string]$XmlText)
  $matches = [regex]::Matches($XmlText, "(?is)<loc>\s*([^<]+)\s*</loc>")
  $values = New-Object System.Collections.Generic.List[string]
  foreach ($match in $matches) {
    $value = [System.Net.WebUtility]::HtmlDecode($match.Groups[1].Value.Trim())
    if (-not [string]::IsNullOrWhiteSpace($value)) {
      $values.Add($value)
    }
  }
  return @($values | Select-Object -Unique)
}

function Get-UrlPath {
  param([string]$Url)
  try {
    $uri = [Uri]$Url
    $path = $uri.AbsolutePath
    if ([string]::IsNullOrWhiteSpace($path)) { return "/" }
    return $path.TrimEnd("/")
  } catch {
    return ""
  }
}

function Get-UrlType {
  param([string]$Url)
  $path = Get-UrlPath $Url
  if ($path -eq "" -or $path -eq "/") { return "home" }
  if ($path -match "^/(admin|api)(/|$)" -or $path -match "^/(login|painel|dashboard)(/|$)") {
    return "proibido/admin/api"
  }
  if ($path -match "^/acompanhantes/[^/]+/[^/]+$") { return "cidade" }
  if ($path -match "^/acompanhantes/[^/]+/[^/]+/[^/]+$") { return "bairro" }
  if ($path -match "^/anuncios/[^/]+$") { return "anuncio" }
  if ($path -in @("/anunciar", "/sobre", "/seguranca", "/como-funciona", "/perguntas-frequentes", "/contato", "/termos", "/privacidade")) {
    return "institucional"
  }
  if ($path -match "\.(jpg|jpeg|png|gif|webp|avif|mp4|mov|pdf|zip)$") { return "outros" }
  return "desconhecido"
}

function Get-SanitizedUrl {
  param(
    [string]$Url,
    [string]$Type,
    [int]$Index
  )
  $path = Get-UrlPath $Url
  switch ($Type) {
    "home" { return "/" }
    "cidade" {
      if ($path -match "^/acompanhantes/([^/]+)/([^/]+)$") {
        return "/acompanhantes/$($Matches[1])/$($Matches[2])"
      }
      return "/acompanhantes/[uf]/[cidade]"
    }
    "bairro" {
      if ($path -match "^/acompanhantes/([^/]+)/([^/]+)/([^/]+)$") {
        return "/acompanhantes/$($Matches[1])/$($Matches[2])/[bairro-publico-amostra-$Index]"
      }
      return "/acompanhantes/[uf]/[cidade]/[bairro-publico-amostra-$Index]"
    }
    "anuncio" { return "/anuncios/[slug-publico-amostra-$Index]" }
    "proibido/admin/api" {
      if ($path -match "^/([^/]+)") { return "/$($Matches[1])/[rota-sanitizada-$Index]" }
      return "/[rota-proibida-sanitizada-$Index]"
    }
    default {
      if ([string]::IsNullOrWhiteSpace($path)) { return "/[url-desconhecida-$Index]" }
      return $path
    }
  }
}

function Get-PlainText {
  param([string]$Html)
  $withoutTags = [regex]::Replace($Html, "<[^>]+>", " ")
  $decoded = [System.Net.WebUtility]::HtmlDecode($withoutTags)
  return ([regex]::Replace($decoded, "\s+", " ")).Trim()
}

function Get-FirstMatchValue {
  param(
    [string]$Text,
    [string]$Pattern
  )
  $match = [regex]::Match($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
  if (-not $match.Success) { return "" }
  if ($match.Groups.Count -gt 1) { return $match.Groups[1].Value.Trim() }
  return $match.Value.Trim()
}

function Get-AttrValue {
  param(
    [string]$Tag,
    [string]$Name
  )
  $doubleQuoted = [regex]::Match($Tag, "(?i)\b$([regex]::Escape($Name))\s*=\s*`"([^`"]*)`"")
  if ($doubleQuoted.Success) { return $doubleQuoted.Groups[1].Value.Trim() }
  $singleQuoted = [regex]::Match($Tag, "(?i)\b$([regex]::Escape($Name))\s*=\s*'([^']*)'")
  if ($singleQuoted.Success) { return $singleQuoted.Groups[1].Value.Trim() }
  return ""
}

function Get-FirstTagByAttribute {
  param(
    [string]$Html,
    [string]$TagName,
    [string]$AttributeName,
    [string]$AttributeValue
  )
  $matches = [regex]::Matches($Html, "(?is)<$TagName\b[^>]*>")
  foreach ($match in $matches) {
    $tag = $match.Value
    $value = Get-AttrValue -Tag $tag -Name $AttributeName
    if ($value.Equals($AttributeValue, [System.StringComparison]::OrdinalIgnoreCase)) {
      return $tag
    }
  }
  return ""
}

function Get-MetadataSummary {
  param(
    [string]$Url,
    [string]$Type,
    [int]$Index
  )
  $result = Invoke-PublicGet -Url $Url
  $sanitizedUrl = Get-SanitizedUrl -Url $Url -Type $Type -Index $Index
  if (-not $result.Ok) {
    return [pscustomobject]@{
      Tipo = $Type
      UrlSanitizada = $sanitizedUrl
      Status = $result.StatusCode
      Title = "PENDENTE"
      Description = "PENDENTE"
      Canonical = "PENDENTE"
      Robots = "PENDENTE"
      H1 = "PENDENTE"
      Observacao = "falha na leitura publica: $($result.Error)"
    }
  }

  $html = $result.Content
  $title = Get-PlainText (Get-FirstMatchValue -Text $html -Pattern "<title[^>]*>(.*?)</title>")
  $descriptionTag = Get-FirstTagByAttribute -Html $html -TagName "meta" -AttributeName "name" -AttributeValue "description"
  $description = Get-AttrValue -Tag $descriptionTag -Name "content"
  $canonicalTag = Get-FirstTagByAttribute -Html $html -TagName "link" -AttributeName "rel" -AttributeValue "canonical"
  $canonical = Get-AttrValue -Tag $canonicalTag -Name "href"
  $robotsTag = Get-FirstTagByAttribute -Html $html -TagName "meta" -AttributeName "name" -AttributeValue "robots"
  $robots = Get-AttrValue -Tag $robotsTag -Name "content"
  $h1 = Get-PlainText (Get-FirstMatchValue -Text $html -Pattern "<h1[^>]*>(.*?)</h1>")

  if ($Type -eq "anuncio") {
    $title = if ($title) { "[title-publico-de-anuncio-sanitizado]" } else { "AUSENTE" }
    $description = if ($description) { "[description-publica-de-anuncio-sanitizada]" } else { "AUSENTE" }
    $h1 = if ($h1) { "[h1-publico-de-anuncio-sanitizado]" } else { "AUSENTE" }
    if ($canonical) { $canonical = "/anuncios/[slug-publico-amostra-$Index]" }
  } else {
    if ($canonical -match "^https?://[^/]+/?$") {
      $canonical = "/"
    } elseif ($canonical -match "^https?://[^/]+(/.*)$") {
      $canonical = $Matches[1].TrimEnd("/")
      if ([string]::IsNullOrWhiteSpace($canonical)) { $canonical = "/" }
    }
  }

  return [pscustomobject]@{
    Tipo = $Type
    UrlSanitizada = $sanitizedUrl
    Status = $result.StatusCode
    Title = $(if ($title) { $title } else { "AUSENTE" })
    Description = $(if ($description) { $description } else { "AUSENTE" })
    Canonical = $(if ($canonical) { $canonical } else { "AUSENTE" })
    Robots = $(if ($robots) { $robots } else { "AUSENTE" })
    H1 = $(if ($h1) { $h1 } else { "AUSENTE" })
    Observacao = "metadados publicos limitados; sem HTML completo"
  }
}

function Add-SectionList {
  param(
    [System.Collections.Generic.List[string]]$Lines,
    [string]$Title,
    [string[]]$Items
  )
  $Lines.Add("")
  $Lines.Add("## $Title")
  if ($Items.Count -eq 0) {
    $Lines.Add("")
    $Lines.Add("- Nenhum item observado.")
    return
  }
  foreach ($item in $Items) {
    $Lines.Add("- ``$item``")
  }
}

$base = Normalize-BaseUrl -Url $BaseUrl
$outputFull = Get-FullPathNormalized $OutputDir
if (Test-PathInside -Child $outputFull -Parent $repoRoot) {
  Write-Host "ERRO: OutputDir aponta para dentro do repositorio. Use um diretorio externo."
  exit 2
}
New-Item -ItemType Directory -Force -Path $outputFull | Out-Null

$reportFull = Resolve-RepoPath $SanitizedReport
$reportDir = Split-Path -Parent $reportFull
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"
$robotsUrl = "$base/robots.txt"
$sitemapUrl = "$base/sitemap.xml"

$robots = Invoke-PublicGet -Url $robotsUrl
$sitemap = Invoke-PublicGet -Url $sitemapUrl

$rawSitemapPath = Join-Path $outputFull "sitemap-principal-bruto.xml"
$rawUrlsPath = Join-Path $outputFull "urls-classificadas-brutas.csv"
$rawMetadataPath = Join-Path $outputFull "metadados-publicos-sanitizados.csv"

if ($sitemap.Ok) {
  [System.IO.File]::WriteAllText($rawSitemapPath, $sitemap.Content, [System.Text.UTF8Encoding]::new($false))
}

$urls = New-Object System.Collections.Generic.List[string]
if ($sitemap.Ok) {
  foreach ($loc in (Get-LocValues -XmlText $sitemap.Content)) {
    $urls.Add($loc)
  }
}

$childSitemaps = @($urls | Where-Object { $_ -match "\.xml($|\?)" -and $_ -match "(?i)sitemap" } | Select-Object -First $MaxChildSitemaps)
foreach ($child in $childSitemaps) {
  $childResult = Invoke-PublicGet -Url $child
  if ($childResult.Ok) {
    $safeName = ([Uri]$child).Segments[-1] -replace "[^A-Za-z0-9_.-]", "_"
    if ([string]::IsNullOrWhiteSpace($safeName)) { $safeName = "sitemap-filho.xml" }
    [System.IO.File]::WriteAllText((Join-Path $outputFull $safeName), $childResult.Content, [System.Text.UTF8Encoding]::new($false))
    foreach ($loc in (Get-LocValues -XmlText $childResult.Content)) {
      $urls.Add($loc)
    }
  }
}

$urls = @($urls | Where-Object { $_ -match "^https?://" } | Select-Object -Unique)

$rows = New-Object System.Collections.Generic.List[object]
foreach ($url in $urls) {
  $rows.Add([pscustomobject]@{
    Url = $url
    Tipo = Get-UrlType -Url $url
    Caminho = Get-UrlPath -Url $url
  })
}
$rows | Export-Csv -LiteralPath $rawUrlsPath -NoTypeInformation -Encoding UTF8

$types = @("home", "cidade", "bairro", "anuncio", "institucional", "outros", "proibido/admin/api", "desconhecido")
$counts = [ordered]@{}
foreach ($type in $types) {
  $counts[$type] = @($rows | Where-Object { $_.Tipo -eq $type }).Count
}

$examples = [ordered]@{}
foreach ($type in $types) {
  $examples[$type] = @()
  $sampleRows = @($rows | Where-Object { $_.Tipo -eq $type } | Select-Object -First $MaxExamplesPerType)
  for ($i = 0; $i -lt $sampleRows.Count; $i++) {
    $examples[$type] += (Get-SanitizedUrl -Url $sampleRows[$i].Url -Type $type -Index ($i + 1))
  }
}

$metadataTargets = New-Object System.Collections.Generic.List[object]
foreach ($type in @("home", "cidade", "bairro", "anuncio", "institucional")) {
  $sampleRows = @($rows | Where-Object { $_.Tipo -eq $type } | Select-Object -First $MaxExamplesPerType)
  for ($i = 0; $i -lt $sampleRows.Count; $i++) {
    $metadataTargets.Add([pscustomobject]@{
      Tipo = $type
      Url = $sampleRows[$i].Url
      Index = ($i + 1)
    })
  }
}

if ($metadataTargets.Count -eq 0) {
  $metadataTargets.Add([pscustomobject]@{ Tipo = "home"; Url = $base; Index = 1 })
}

$metadata = New-Object System.Collections.Generic.List[object]
foreach ($target in $metadataTargets) {
  $metadata.Add((Get-MetadataSummary -Url $target.Url -Type $target.Tipo -Index $target.Index))
}
$metadata | Export-Csv -LiteralPath $rawMetadataPath -NoTypeInformation -Encoding UTF8

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Relatorio de inventario SEO de producao sanitizado")
$lines.Add("")
$lines.Add("- Bloco: 28")
$lines.Add("- Data local: $timestamp")
$lines.Add("- Base publica consultada: ``$base``")
$lines.Add("- Escopo: robots.txt, sitemap.xml, sitemaps filhos quando publicos e metadados publicos limitados.")
$lines.Add("- Politica: somente leitura, sem login, sem banco, sem SQL, sem dump, sem midia, sem formulario e sem alteracao de producao.")
$lines.Add("- Saida bruta externa: ``$outputFull``")
$lines.Add("- Lista bruta completa de anuncios reais versionada: nao.")
$lines.Add("")
$lines.Add("## Status HTTP publico")
$lines.Add("")
$lines.Add("| Recurso | Status | Observacao |")
$lines.Add("| --- | ---: | --- |")
$lines.Add("| robots.txt | $($robots.StatusCode) | $(if ($robots.Ok) { "lido" } else { "falha: $($robots.Error)" }) |")
$lines.Add("| sitemap.xml | $($sitemap.StatusCode) | $(if ($sitemap.Ok) { "lido" } else { "falha: $($sitemap.Error)" }) |")
$lines.Add("")
$lines.Add("## Contagem por tipo")
$lines.Add("")
$lines.Add("| Tipo | Total |")
$lines.Add("| --- | ---: |")
foreach ($type in $types) {
  $lines.Add("| $type | $($counts[$type]) |")
}
$lines.Add("")
$lines.Add("## Padroes de URL observados")
$lines.Add("")
$lines.Add("| Tipo | Padrao preservavel | Regra V3 |")
$lines.Add("| --- | --- | --- |")
$lines.Add("| home | ``/`` | manter quando aprovado para producao |")
$lines.Add("| cidade | ``/acompanhantes/[uf]/[cidade]`` | preservar e validar conteudo util |")
$lines.Add("| bairro | ``/acompanhantes/[uf]/[cidade]/[bairro]`` | preservar quando houver conteudo suficiente |")
$lines.Add("| anuncio | ``/anuncios/[slug]`` | preservar slug publico quando anuncio for indexavel |")
$lines.Add("| institucional | caminhos publicos sem login | manter ou redirecionar conforme mapa |")
$lines.Add("| proibido/admin/api | ``/admin`` e ``/api`` | nao indexar, nao colocar no sitemap |")

foreach ($type in $types) {
  Add-SectionList -Lines $lines -Title "Exemplos sanitizados - $type" -Items @($examples[$type])
}

$lines.Add("")
$lines.Add("## Metadados publicos limitados")
$lines.Add("")
$lines.Add("| Tipo | URL sanitizada | Status | Title | Description | Canonical | Robots | H1 |")
$lines.Add("| --- | --- | ---: | --- | --- | --- | --- | --- |")
foreach ($item in $metadata) {
  $lines.Add("| $($item.Tipo) | ``$($item.UrlSanitizada)`` | $($item.Status) | $($item.Title.Replace('|', '/')) | $($item.Description.Replace('|', '/')) | ``$($item.Canonical)`` | $($item.Robots.Replace('|', '/')) | $($item.H1.Replace('|', '/')) |")
}

$lines.Add("")
$lines.Add("## Riscos observados")
$lines.Add("")
$lines.Add("- Slugs de anuncio podem conter nome publico sensivel; por isso foram sanitizados nos documentos versionados.")
$lines.Add("- O sitemap publico pode conter volume de URLs dinamicas; a lista completa fica somente fora do repositorio.")
$lines.Add("- O cutover SEO depende de mapa completo, validacao de canonical, robots, sitemap e Search Console.")
$lines.Add("- Nenhuma URL de admin/API deve entrar no sitemap futuro.")
$lines.Add("")
$lines.Add("## Confirmacao")
$lines.Add("")
$lines.Add("- Producao alterada: nao.")
$lines.Add("- Login realizado: nao.")
$lines.Add("- Banco/SQL/dump acessado: nao.")
$lines.Add("- Midia real baixada: nao.")
$lines.Add("- Dados privados versionados: nao.")

[System.IO.File]::WriteAllText($reportFull, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))

Write-Host "SEO_INVENTARIO_RESULT=OK"
Write-Host "BASE_URL=$base"
Write-Host "RAW_OUTPUT_DIR=$outputFull"
Write-Host "SANITIZED_REPORT=$reportFull"
foreach ($type in $types) {
  Write-Host ("COUNT_{0}={1}" -f ($type.ToUpperInvariant() -replace "[^A-Z0-9]", "_"), $counts[$type])
}
Write-Host "ROBOTS_STATUS=$($robots.StatusCode)"
Write-Host "SITEMAP_STATUS=$($sitemap.StatusCode)"
exit 0
