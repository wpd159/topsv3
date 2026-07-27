function New-ComplianceSyntheticCpf {
  $digits = @(7, 3, 1, 9, 4, 6, 5, 2, 0)
  for ($length = 9; $length -le 10; $length++) {
    $sum = 0
    $weight = $length + 1
    for ($index = 0; $index -lt $length; $index++) {
      $sum += $digits[$index] * $weight
      $weight--
    }
    $remainder = $sum % 11
    $digits += $(if ($remainder -lt 2) { 0 } else { 11 - $remainder })
  }
  return ($digits -join "")
}

function Get-ComplianceCsrfValue {
  param(
    [Parameter(Mandatory = $true)]
    [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl
  )

  $uri = [Uri]($BaseUrl.TrimEnd("/") + "/")
  $cookie = $Session.Cookies.GetCookies($uri) |
    Where-Object { $_.Name -eq "XSRF-TOKEN" } |
    Select-Object -First 1
  if ($null -eq $cookie) {
    return $null
  }
  return [Uri]::UnescapeDataString($cookie.Value)
}

function Invoke-ComplianceAgeGateRequest {
  param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [Parameter(Mandatory = $true)]
    [string]$Path,
    [Parameter(Mandatory = $true)]
    [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
    [ValidateSet("GET", "POST")]
    [string]$Method = "GET",
    [object]$Body = $null
  )

  $headers = @{
    Accept = "application/json"
    "X-Request-Id" = "age-gate-completo-local"
  }
  if ($Method -eq "POST") {
    $csrf = Get-ComplianceCsrfValue -Session $Session -BaseUrl $BaseUrl
    if (-not [string]::IsNullOrWhiteSpace($csrf)) {
      $headers["X-XSRF-TOKEN"] = $csrf
    }
  }
  $parameters = @{
    Uri = $BaseUrl.TrimEnd("/") + $Path
    Method = $Method
    Headers = $headers
    WebSession = $Session
    UseBasicParsing = $true
    TimeoutSec = 20
  }
  if ($null -ne $Body) {
    $parameters["Body"] = ($Body | ConvertTo-Json -Depth 8 -Compress)
    $parameters["ContentType"] = "application/json"
  }
  return Invoke-WebRequest @parameters
}

function Enable-ComplianceVisitorAccessLocal {
  param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [Parameter(Mandatory = $true)]
    [string]$Slug,
    [ValidateSet("MIDIA_RESTRITA", "WHATSAPP", "STORY", "CONTEUDO_EXPLICITO")]
    [string]$Scope = "MIDIA_RESTRITA",
    [Microsoft.PowerShell.Commands.WebRequestSession]$Session = $null
  )

  if ($null -eq $Session) {
    $Session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
  }

  $globalStatus = Invoke-ComplianceAgeGateRequest `
    -BaseUrl $BaseUrl `
    -Path "/api/public/compliance/age-gate/status" `
    -Session $Session
  if ([int]$globalStatus.StatusCode -ne 200) {
    throw "status global do age gate indisponivel"
  }

  $accepted = Invoke-ComplianceAgeGateRequest `
    -BaseUrl $BaseUrl `
    -Path "/api/public/compliance/age-gate/accept" `
    -Session $Session `
    -Method "POST" `
    -Body @{ originPath = "/anuncios/$Slug" }
  if ([int]$accepted.StatusCode -ne 200) {
    throw "aceite global do age gate recusado"
  }

  $detailResponse = Invoke-ComplianceAgeGateRequest `
    -BaseUrl $BaseUrl `
    -Path "/api/public/anuncios/$Slug" `
    -Session $Session
  $detail = $detailResponse.Content | ConvertFrom-Json
  if ([string]::IsNullOrWhiteSpace([string]$detail.id)) {
    throw "anuncio sintetico sem identificador canonico"
  }

  $mediaId = $null
  if ($Scope -eq "MIDIA_RESTRITA" -or $Scope -eq "CONTEUDO_EXPLICITO") {
    $media = @($detail.midias) |
      Where-Object { $_.visibilidadeMidia -eq "RESTRITA_18" } |
      Select-Object -First 1
    if ($null -eq $media -or [string]::IsNullOrWhiteSpace([string]$media.id)) {
      throw "anuncio sintetico sem midia restrita para o challenge"
    }
    $mediaId = [string]$media.id
  }

  $challengeBody = @{
    level = $(if ($Scope -eq "CONTEUDO_EXPLICITO") { "STRONG" } else { "REINFORCED" })
    scope = $Scope
    anuncioId = [string]$detail.id
    route = "/anuncios/$Slug"
    idempotencyKey = "challenge-$([Guid]::NewGuid().ToString('N'))"
  }
  if ($null -ne $mediaId) {
    $challengeBody["midiaId"] = $mediaId
  }
  if ($Scope -eq "STORY") {
    $feedResponse = Invoke-ComplianceAgeGateRequest `
      -BaseUrl $BaseUrl `
      -Path "/api/public/stories/ativos" `
      -Session $Session
    $story = @($feedResponse.Content | ConvertFrom-Json) |
      ForEach-Object { @($_.itens) } |
      Where-Object { [string]$_.anuncioId -eq [string]$detail.id } |
      Select-Object -First 1
    if ($null -eq $story -or [string]::IsNullOrWhiteSpace([string]$story.storyId)) {
      throw "anuncio sintetico sem Story publico canonico para o challenge"
    }
    $challengeBody["storyId"] = [string]$story.storyId
  }

  $challengeResponse = Invoke-ComplianceAgeGateRequest `
    -BaseUrl $BaseUrl `
    -Path "/api/public/compliance/visitor/challenge" `
    -Session $Session `
    -Method "POST" `
    -Body $challengeBody
  $challenge = $challengeResponse.Content | ConvertFrom-Json
  if ([string]::IsNullOrWhiteSpace([string]$challenge.challengeId)) {
    throw "challenge reforcado nao foi criado"
  }

  $verifyResponse = Invoke-ComplianceAgeGateRequest `
    -BaseUrl $BaseUrl `
    -Path "/api/public/compliance/visitor/verify" `
    -Session $Session `
    -Method "POST" `
    -Body @{
      challengeId = [string]$challenge.challengeId
      dataNascimento = "01/01/1990"
      confirmacaoDataNascimento = "01/01/1990"
      cpf = New-ComplianceSyntheticCpf
      aceiteMaioridade = $true
      aceiteConteudoRestrito = $true
      aceitePrivacidade = $true
      confirmacaoExplicita = ($Scope -eq "CONTEUDO_EXPLICITO")
      idempotencyKey = "verify-$([Guid]::NewGuid().ToString('N'))"
    }
  $verified = $verifyResponse.Content | ConvertFrom-Json
  if ($verified.verified -ne $true) {
    throw "verificacao reforcada nao concluiu em VERIFIED"
  }

  return [pscustomobject]@{
    Session = $Session
    GlobalStatus = $globalStatus.Content | ConvertFrom-Json
    Accepted = $accepted.Content | ConvertFrom-Json
    Challenge = $challenge
    Verified = $verified
    AnuncioId = [string]$detail.id
    MidiaId = $mediaId
    AcceptHeaders = $accepted.Headers
    VerifyHeaders = $verifyResponse.Headers
  }
}
