param(
  [switch]$ExecutarCaptura,
  [int]$MaxUrls = 30,
  [int]$IntervaloSegundos = 2,
  [int]$TimeoutSegundos = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositório Git não encontrado."
  exit 2
}

$repoRoot = $repoRoot -replace '\\', '/'
$captureDir = Join-Path $repoRoot "docs/v3/conteudo-publico-capturado"
New-Item -ItemType Directory -Path $captureDir -Force | Out-Null

$baseUrl = "https://topsdojob.com"
$userAgent = "TopsDoJobV3-LocalAudit/1.0 (+captura-publica-somente-leitura)"
$blockedPathPrefixes = @(
  "/admin",
  "/login",
  "/api",
  "/dashboard",
  "/checkout",
  "/pagamento",
  "/webhook",
  "/moderacao"
)

$plannedPaths = @(
  "/",
  "/sobre",
  "/como-funciona",
  "/seguranca",
  "/anunciar",
  "/perguntas-frequentes",
  "/sitemap.xml",
  "/robots.txt"
)

function Write-Utf8NoBom {
  param(
    [string]$Path,
    [string]$Text
  )
  $Text = $Text -replace "`r`n", "`n"
  [System.IO.File]::WriteAllText($Path, $Text, (New-Object System.Text.UTF8Encoding($false)))
}

function ConvertTo-AbsolutePublicUrl {
  param([string]$Path)
  if ($Path -eq "/") { return $baseUrl + "/" }
  return $baseUrl + $Path
}

function Test-AllowedPublicUrl {
  param([string]$Url)
  try {
    $uri = [System.Uri]$Url
  } catch {
    return $false
  }

  if ($uri.Scheme -ne "https") { return $false }
  if ($uri.Host -ne "topsdojob.com") { return $false }
  if (-not [string]::IsNullOrWhiteSpace($uri.Query)) { return $false }
  foreach ($prefix in $blockedPathPrefixes) {
    if ($uri.AbsolutePath.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
      return $false
    }
  }
  return $true
}

function ConvertTo-SafeFileName {
  param([string]$Url)
  $uri = [System.Uri]$Url
  $path = $uri.AbsolutePath.Trim("/")
  if ([string]::IsNullOrWhiteSpace($path)) { $path = "home" }
  $safe = ($path -replace '[^A-Za-z0-9._-]+', '-').Trim("-")
  if ([string]::IsNullOrWhiteSpace($safe)) { return "home" }
  return $safe.ToLowerInvariant()
}

function ConvertFrom-HtmlEntity {
  param([AllowNull()][string]$Text)
  if ($null -eq $Text) { return "" }
  return [System.Net.WebUtility]::HtmlDecode($Text)
}

function Remove-HtmlNoise {
  param([string]$Html)
  $clean = [regex]::Replace($Html, '(?is)<script\b[^>]*>.*?</script>', ' ')
  $clean = [regex]::Replace($clean, '(?is)<style\b[^>]*>.*?</style>', ' ')
  $clean = [regex]::Replace($clean, '(?is)<noscript\b[^>]*>.*?</noscript>', ' ')
  $clean = [regex]::Replace($clean, '(?is)<!--.*?-->', ' ')
  return $clean
}

function Get-FirstMatchGroup {
  param(
    [string]$Text,
    [string]$Pattern,
    [string]$GroupName = "value"
  )
  $match = [regex]::Match($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Singleline)
  if (-not $match.Success) { return "" }
  return (ConvertFrom-HtmlEntity $match.Groups[$GroupName].Value).Trim()
}

function Get-AllHeadingText {
  param([string]$Html)
  $items = New-Object System.Collections.Generic.List[string]
  $matches = [regex]::Matches($Html, '(?is)<h[1-6]\b[^>]*>(?<value>.*?)</h[1-6]>')
  foreach ($match in $matches) {
    $value = [regex]::Replace($match.Groups["value"].Value, '(?is)<[^>]+>', ' ')
    $value = Sanitize-Text (ConvertFrom-HtmlEntity $value)
    if (-not [string]::IsNullOrWhiteSpace($value)) { $items.Add($value) }
  }
  return @($items | Select-Object -First 20)
}

function Sanitize-Text {
  param([AllowNull()][string]$Text)
  if ($null -eq $Text) { return "" }
  $result = ConvertFrom-HtmlEntity $Text
  $result = [regex]::Replace($result, '(?i)mailto:[^\s"<>]+', '[EMAIL_REMOVIDO]')
  $result = [regex]::Replace($result, '(?i)(whatsapp|wa\.me|api\.whatsapp\.com)[^\s"<>]*', '[WHATSAPP_REMOVIDO]')
  $result = [regex]::Replace($result, '\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b', '[EMAIL_REMOVIDO]', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
  $result = [regex]::Replace($result, '\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b', '[DOCUMENTO_REMOVIDO]')
  $result = [regex]::Replace($result, '(\+?55\s*)?(\(?\d{2}\)?\s*)?9?\d{4}[-\s.]?\d{4}', '[TELEFONE_REMOVIDO]')
  $result = [regex]::Replace($result, '\s+', ' ').Trim()
  return $result
}

function ConvertTo-PlainText {
  param([string]$Html)
  $clean = Remove-HtmlNoise $Html
  $clean = [regex]::Replace($clean, '(?is)<(br|p|div|section|article|li|h[1-6])\b[^>]*>', "`n")
  $clean = [regex]::Replace($clean, '(?is)<[^>]+>', ' ')
  $text = Sanitize-Text $clean
  if ($text.Length -gt 1400) { $text = $text.Substring(0, 1400).Trim() + "..." }
  return $text
}

function Test-ShouldSuppressMainText {
  param(
    [string]$Url,
    [string]$PlainText
  )
  $uri = [System.Uri]$Url
  if ($uri.AbsolutePath.StartsWith("/anuncios/", [System.StringComparison]::OrdinalIgnoreCase)) { return $true }
  if ($PlainText -match '\[TELEFONE_REMOVIDO\]|\[WHATSAPP_REMOVIDO\]|\[DOCUMENTO_REMOVIDO\]') { return $true }
  return $false
}

function Get-PageType {
  param([string]$Url)
  $path = ([System.Uri]$Url).AbsolutePath
  if ($path -eq "/") { return "HOME" }
  if ($path -eq "/sitemap.xml") { return "SITEMAP" }
  if ($path -eq "/robots.txt") { return "ROBOTS" }
  if ($path -in @("/sobre", "/como-funciona", "/seguranca", "/anunciar", "/perguntas-frequentes")) { return "INSTITUCIONAL" }
  if ($path.StartsWith("/acompanhantes/", [System.StringComparison]::OrdinalIgnoreCase)) { return "LOCALIDADE" }
  if ($path.StartsWith("/anuncios/", [System.StringComparison]::OrdinalIgnoreCase)) { return "ANUNCIO" }
  return "PUBLICA_OUTRA"
}

function New-CaptureObject {
  param(
    [string]$Url,
    [int]$StatusCode,
    [string]$ContentType,
    [string]$Body
  )
  $pageType = Get-PageType $Url
  $isText = $ContentType -match 'text/html|application/xhtml\+xml|application/xml|text/xml|text/plain|application/rss\+xml'

  $title = ""
  $description = ""
  $h1 = ""
  $headings = @()
  $mainText = ""
  $decision = "PENDENTE_REVISAO"
  $notes = New-Object System.Collections.Generic.List[string]

  if ($isText -and $Body) {
    if ($pageType -eq "SITEMAP") {
      $mainText = "SITEMAP_PUBLICO_LIDO_SOMENTE_PARA_DESCOBERTA"
      $decision = "PENDENTE_REVISAO"
      $notes.Add("Não foi armazenado payload bruto do sitemap.")
    } elseif ($pageType -eq "ROBOTS") {
      $mainText = Sanitize-Text $Body
      if ($mainText.Length -gt 1000) { $mainText = $mainText.Substring(0, 1000).Trim() + "..." }
      $decision = "ADAPTAR"
    } else {
      $bodyNoNoise = Remove-HtmlNoise $Body
      $title = Sanitize-Text (Get-FirstMatchGroup $bodyNoNoise '<title[^>]*>(?<value>.*?)</title>')
      $description = Sanitize-Text (Get-FirstMatchGroup $bodyNoNoise '<meta\s+[^>]*(?:name|property)=["''](?:description|og:description)["''][^>]*content=["''](?<value>.*?)["''][^>]*>')
      if ([string]::IsNullOrWhiteSpace($description)) {
        $description = Sanitize-Text (Get-FirstMatchGroup $bodyNoNoise '<meta\s+[^>]*content=["''](?<value>.*?)["''][^>]*(?:name|property)=["''](?:description|og:description)["''][^>]*>')
      }
      $headings = @(Get-AllHeadingText $bodyNoNoise)
      $h1 = @($headings | Select-Object -First 1)
      $plainText = ConvertTo-PlainText $bodyNoNoise
      if (Test-ShouldSuppressMainText -Url $Url -PlainText $plainText) {
        $mainText = "CONTEUDO_SENSIVEL_NAO_CAPTURADO"
        $decision = "PENDENTE_REVISAO"
        $notes.Add("Texto principal suprimido por conter padrão sensível ou rota de anúncio.")
      } else {
        $mainText = $plainText
        $decision = if ($pageType -eq "INSTITUCIONAL" -or $pageType -eq "HOME") { "ADAPTAR" } else { "PENDENTE_REVISAO" }
      }
    }
  } else {
    $notes.Add("Tipo de conteúdo não textual ou vazio; nada foi capturado além de metadados.")
  }

  return [pscustomobject]@{
    url = $Url
    statusHttp = $StatusCode
    contentType = $ContentType
    tipoPagina = $pageType
    title = $title
    metaDescription = $description
    h1 = $h1
    headings = $headings
    textoPrincipalSanitizado = $mainText
    observacoesSeo = @($notes)
    decisaoPreliminar = $decision
  }
}

function Get-SitemapPublicUrls {
  param([string]$Xml)
  $urls = New-Object System.Collections.Generic.List[string]
  $matches = [regex]::Matches($Xml, '(?is)<loc>\s*(?<url>.*?)\s*</loc>')
  foreach ($match in $matches) {
    $url = (ConvertFrom-HtmlEntity $match.Groups["url"].Value).Trim()
    if (Test-AllowedPublicUrl $url) { $urls.Add($url) }
  }
  return @($urls | Select-Object -Unique)
}

$plannedUrls = @($plannedPaths | ForEach-Object { ConvertTo-AbsolutePublicUrl $_ } | Select-Object -First $MaxUrls)
$ignored = New-Object System.Collections.Generic.List[object]
$captured = New-Object System.Collections.Generic.List[object]

foreach ($url in $plannedUrls) {
  if (-not (Test-AllowedPublicUrl $url)) {
    $ignored.Add([pscustomobject]@{ url = $url; motivo = "URL bloqueada por política local." })
  }
}

if (-not $ExecutarCaptura) {
  $planLines = New-Object System.Collections.Generic.List[string]
  $planLines.Add("# Plano de captura pública somente leitura")
  $planLines.Add("")
  $planLines.Add("- Modo: simulação")
  $planLines.Add("- Método permitido: GET")
  $planLines.Add("- Domínio permitido: $baseUrl")
  $planLines.Add("- Máximo de URLs: $MaxUrls")
  $planLines.Add("- Intervalo mínimo: $IntervaloSegundos segundos")
  $planLines.Add("")
  $planLines.Add("## URLs planejadas")
  foreach ($url in $plannedUrls) { $planLines.Add("- $url") }
  Write-Utf8NoBom -Path (Join-Path $captureDir "PLANO-CAPTURA.md") -Text (($planLines -join "`n") + "`n")
  Write-Host "Simulação concluída. Use -ExecutarCaptura para executar GET público limitado."
  exit 0
}

$consecutiveStopErrors = 0
foreach ($url in $plannedUrls) {
  if (-not (Test-AllowedPublicUrl $url)) {
    $ignored.Add([pscustomobject]@{ url = $url; motivo = "URL bloqueada por política local." })
    continue
  }

  Start-Sleep -Seconds $IntervaloSegundos

  try {
    $response = Invoke-WebRequest -Uri $url -Method Get -UserAgent $userAgent -TimeoutSec $TimeoutSegundos -UseBasicParsing -MaximumRedirection 3
    $statusCode = [int]$response.StatusCode
    $contentType = [string]$response.Headers["Content-Type"]
    $finalUrl = $response.BaseResponse.ResponseUri.AbsoluteUri
    if (-not (Test-AllowedPublicUrl $finalUrl)) {
      $ignored.Add([pscustomobject]@{ url = $url; motivo = "Redirecionou para URL fora do escopo permitido." })
      continue
    }

    $capture = New-CaptureObject -Url $finalUrl -StatusCode $statusCode -ContentType $contentType -Body ([string]$response.Content)
    $captured.Add($capture)

    $jsonPath = Join-Path $captureDir ((ConvertTo-SafeFileName $finalUrl) + ".json")
    $json = $capture | ConvertTo-Json -Depth 8
    Write-Utf8NoBom -Path $jsonPath -Text ($json + "`n")

    if ($capture.tipoPagina -eq "SITEMAP" -and $response.Content) {
      $sitemapUrls = @(Get-SitemapPublicUrls ([string]$response.Content))
      $selectedPreview = @($sitemapUrls | Where-Object {
        $path = ([System.Uri]$_).AbsolutePath
        $path.StartsWith("/acompanhantes/", [System.StringComparison]::OrdinalIgnoreCase) -or $path.StartsWith("/anuncios/", [System.StringComparison]::OrdinalIgnoreCase)
      } | Select-Object -First 20)
      foreach ($discovered in $selectedPreview) {
        $ignored.Add([pscustomobject]@{
          url = $discovered
          motivo = "Descoberta no sitemap, não capturada nesta fase por risco de telefone, conteúdo sensível ou volume."
        })
      }
    }

    $consecutiveStopErrors = 0
  } catch {
    $status = 0
    if ($_.Exception.Response) {
      try { $status = [int]$_.Exception.Response.StatusCode } catch { $status = 0 }
    }
    $ignored.Add([pscustomobject]@{ url = $url; motivo = "Falha de GET público ou timeout. Status: $status." })
    if ($status -eq 403 -or $status -eq 429 -or $status -ge 500) {
      $consecutiveStopErrors++
      if ($consecutiveStopErrors -ge 2) {
        $ignored.Add([pscustomobject]@{ url = $baseUrl; motivo = "Captura interrompida por erros 403/429/5xx repetidos." })
        break
      }
    }
  }
}

$summary = [pscustomobject]@{
  executadaEm = (Get-Date).ToString("o")
  modo = "captura"
  dominioPermitido = $baseUrl
  metodoPermitido = "GET"
  totalCapturado = $captured.Count
  totalIgnorado = $ignored.Count
  urlsCapturadas = @($captured.ToArray() | ForEach-Object { $_.url })
  urlsIgnoradas = @($ignored.ToArray())
}

Write-Utf8NoBom -Path (Join-Path $captureDir "resumo-captura.json") -Text (($summary | ConvertTo-Json -Depth 8) + "`n")

$reportLines = New-Object System.Collections.Generic.List[string]
$reportLines.Add("# Relatório da captura pública somente leitura")
$reportLines.Add("")
$reportLines.Add("- Domínio permitido: $baseUrl")
$reportLines.Add("- Método usado: GET")
$reportLines.Add("- Total capturado: $($captured.Count)")
$reportLines.Add("- Total ignorado: $($ignored.Count)")
$reportLines.Add("")
$reportLines.Add("## URLs capturadas")
foreach ($item in $captured.ToArray()) {
  $reportLines.Add("- $($item.url) | $($item.statusHttp) | $($item.tipoPagina) | $($item.decisaoPreliminar)")
}
$reportLines.Add("")
$reportLines.Add("## URLs ignoradas")
foreach ($item in $ignored.ToArray()) {
  $reportLines.Add("- $($item.url) | $($item.motivo)")
}

Write-Utf8NoBom -Path (Join-Path $captureDir "RELATORIO-CAPTURA.md") -Text (($reportLines -join "`n") + "`n")

Write-Host "Captura pública concluída. URLs capturadas: $($captured.Count). URLs ignoradas: $($ignored.Count)."
exit 0
