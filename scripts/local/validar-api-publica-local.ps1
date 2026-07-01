param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$SlugSintetico = "anuncio-sintetico-local",
  [string]$SlugBloqueadoSintetico = "anuncio-sintetico-bloqueado-local",
  [string]$UfSintetica = "zz",
  [string]$CidadeSintetica = "cidade-sintetica",
  [string]$BairroSintetico = "bairro-sintetico",
  [switch]$SemDadosSinteticos
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$checks = New-Object System.Collections.Generic.List[object]
$pending = New-Object System.Collections.Generic.List[string]
$failures = New-Object System.Collections.Generic.List[string]

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
  if (-not $Ok) {
    $failures.Add("${Nome}: ${Detalhe}")
  }
}

function Resolve-LocalBaseUrl {
  param([string]$Value)
  $trimmed = $Value.Trim().TrimEnd("/")
  if ($trimmed -notmatch '^http://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$') {
    throw "BaseUrl deve apontar somente para localhost HTTP."
  }
  return $trimmed
}

function New-Url {
  param([string]$Path)
  if ($Path.StartsWith("/")) {
    return "$script:SafeBaseUrl$Path"
  }
  return "$script:SafeBaseUrl/$Path"
}

function Invoke-LocalHttp {
  param(
    [string]$Path,
    [int]$ExpectedStatus,
    [string]$Method = "GET",
    [string]$Body = $null,
    [Microsoft.PowerShell.Commands.WebRequestSession]$Session = $null
  )

  $url = New-Url $Path
  try {
    $params = @{
      Uri = $url
      Method = $Method
      Headers = @{ Accept = "application/json"; "X-Request-Id" = "smoke-local-bloco-9" }
      UseBasicParsing = $true
      TimeoutSec = 10
    }
    if ($null -ne $Session) {
      $params["WebSession"] = $Session
    }
    if ($Method -ne "GET" -and $null -ne $Body) {
      $params["Body"] = $Body
      $params["ContentType"] = "application/json"
    }
    $response = Invoke-WebRequest @params
    return [pscustomobject]@{
      Url = $url
      Status = [int]$response.StatusCode
      Body = [string]$response.Content
      Headers = $response.Headers
      ErroOperacional = $false
    }
  } catch [System.Net.WebException] {
    if ($_.Exception.Response) {
      $stream = $_.Exception.Response.GetResponseStream()
      $reader = New-Object System.IO.StreamReader($stream)
      try {
        $body = $reader.ReadToEnd()
      } finally {
        $reader.Dispose()
      }
      return [pscustomobject]@{
        Url = $url
        Status = [int]$_.Exception.Response.StatusCode
        Body = [string]$body
        Headers = $_.Exception.Response.Headers
        ErroOperacional = $false
      }
    }
    return [pscustomobject]@{
      Url = $url
      Status = 0
      Body = $_.Exception.Message
      Headers = $null
      ErroOperacional = $true
    }
  }
}

function Invoke-LocalCorsPreflight {
  param([string]$Path)

  $url = New-Url $Path
  try {
    $response = Invoke-WebRequest -Uri $url -Method OPTIONS -Headers @{
      Origin = "http://localhost:3000"
      "Access-Control-Request-Method" = "POST"
      "Access-Control-Request-Headers" = "Content-Type,X-Request-Id"
    } -UseBasicParsing -TimeoutSec 10
    return [pscustomobject]@{
      Url = $url
      Status = [int]$response.StatusCode
      Headers = $response.Headers
      ErroOperacional = $false
    }
  } catch [System.Net.WebException] {
    if ($_.Exception.Response) {
      return [pscustomobject]@{
        Url = $url
        Status = [int]$_.Exception.Response.StatusCode
        Headers = $_.Exception.Response.Headers
        ErroOperacional = $false
      }
    }
    return [pscustomobject]@{
      Url = $url
      Status = 0
      Headers = $null
      ErroOperacional = $true
    }
  }
}

function Get-HeaderValue {
  param(
    [object]$Headers,
    [string]$Name
  )
  if ($null -eq $Headers) { return "" }
  $value = $Headers[$Name]
  if ($null -eq $value) { return "" }
  if ($value -is [array]) { return ($value -join "; ") }
  return [string]$value
}

function Assert-NoSensitivePublicData {
  param(
    [string]$Nome,
    [string]$Body,
    [bool]$AllowSyntheticWhatsapp = $false
  )

  $safeBody = $Body.Replace("PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO", "PENDENTE_POLITICA_EXPOSICAO_CONTATO_PUBLICO")
  if ($AllowSyntheticWhatsapp) {
    $safeBody = $safeBody.Replace("https://wa.me/5500000000000", "URL_WHATSAPP_SINTETICA_AUTORIZADA")
    $safeBody = $safeBody.Replace("+5500000000000", "TELEFONE_SINTETICO_AUTORIZADO")
    $safeBody = $safeBody.Replace("5500000000000", "TELEFONE_SINTETICO_AUTORIZADO")
  }
  $patterns = @(
    @{ Label = "CPF"; Regex = '\b[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2}\b' },
    @{ Label = "e-mail"; Regex = '(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}' },
    @{ Label = "telefone ou URL WhatsApp nao autorizada"; Regex = '(?i)\+[1-9][0-9]{7,14}|wa\.me/[0-9]{8,15}|5500000000000' },
    @{ Label = "campo WhatsApp publico"; Regex = '(?i)whatsapp(Normalizado|Publico|_normalizado|_publico)|"contatoPublico"\s*:\s*"[^\"]+"' },
    @{ Label = "documento privado"; Regex = '(?i)documentoUsuario|documento_privado|cpf|identidade' },
    @{ Label = "storage key"; Regex = '(?i)storageProvider|storage_provider|chaveObjeto|chave_objeto|storage key' },
    @{ Label = "bucket"; Regex = '(?i)"bucket"\s*:' },
    @{ Label = "hash interno"; Regex = '(?i)sha256|etag|tokenSessaoHash|senhaHash|ipHash|userAgentHash' },
    @{ Label = "pagamento"; Regex = '(?i)pagamento|payment' },
    @{ Label = "saldo de credito"; Regex = '(?i)saldo|credito' },
    @{ Label = "auditoria administrativa"; Regex = '(?i)auditoria|atorUsuario|payload' }
  )

  foreach ($pattern in $patterns) {
    Add-Check "$Nome sem $($pattern.Label)" (-not ($safeBody -match $pattern.Regex)) "nenhum dado sensivel publico deve aparecer"
  }
}

try {
  $script:SafeBaseUrl = Resolve-LocalBaseUrl $BaseUrl
} catch {
  Write-Host "VALIDATION_RESULT=FALHA_API_PUBLICA_LOCAL"
  Write-Host "ERRO=$($_.Exception.Message)"
  exit 1
}

$encodedSitemap = [uri]::EscapeDataString("/sitemap.xml")
$encodedRobots = [uri]::EscapeDataString("/robots.txt")
$encodedPerfil = [uri]::EscapeDataString("/perfil/slug")
$encodedUrlProducao = [uri]::EscapeDataString("https://topsdojob.com/anuncios/x")

$requests = New-Object System.Collections.Generic.List[object]
$requests.Add([pscustomobject]@{ Nome = "health"; Path = "/api/health"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false })
$requests.Add([pscustomobject]@{ Nome = "readiness"; Path = "/api/health/readiness"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false })
$requests.Add([pscustomobject]@{ Nome = "seo sitemap"; Path = "/api/public/seo/rota?caminho=$encodedSitemap"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false })
$requests.Add([pscustomobject]@{ Nome = "seo robots"; Path = "/api/public/seo/rota?caminho=$encodedRobots"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false })

$metricBody = (@{
  visitanteLocalId = "visitante-sintetico-local"
  origemPais = "BR"
  origemUf = "ZZ"
  origemCidade = "Cidade Sintetica"
  dispositivo = "DESKTOP"
} | ConvertTo-Json -Compress)
$idadeMenorBody = (@{
  dataNascimento = ((Get-Date).Date.AddYears(-17).ToString("yyyy-MM-dd"))
  declaracaoMaioridade = $true
} | ConvertTo-Json -Compress)
$idadeMaiorBody = (@{
  dataNascimento = "1990-01-01"
  declaracaoMaioridade = $true
} | ConvertTo-Json -Compress)
$idadeSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession

if (-not $SemDadosSinteticos) {
  $corsPreflight = Invoke-LocalCorsPreflight -Path "/api/public/idade/confirmar"
  if ($corsPreflight.ErroOperacional -and $corsPreflight.Status -eq 0) {
    $pending.Add("PENDENTE_BACKEND_LOCAL_INDISPONIVEL: $($corsPreflight.Url)")
    Add-Check "cors preflight local" $false "backend local indisponivel"
  } else {
    Add-Check "cors preflight status" (($corsPreflight.Status -eq 200) -or ($corsPreflight.Status -eq 204)) "status obtido: $($corsPreflight.Status)"
    Add-Check "cors origem local" ((Get-HeaderValue $corsPreflight.Headers "Access-Control-Allow-Origin") -eq "http://localhost:3000") "somente origem local permitida"
    Add-Check "cors credentials local" ((Get-HeaderValue $corsPreflight.Headers "Access-Control-Allow-Credentials") -eq "true") "credentials devem ser aceitos so em local"
    Add-Check "cors sem wildcard credentials" ((Get-HeaderValue $corsPreflight.Headers "Access-Control-Allow-Origin") -ne "*") "wildcard com credentials proibido"
    Add-Check "cors metodo post" ((Get-HeaderValue $corsPreflight.Headers "Access-Control-Allow-Methods") -match "POST") "POST deve ser permitido para idade local"
  }

  $requests.Add([pscustomobject]@{ Nome = "idade status sem cookie"; Path = "/api/public/idade/status"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "idade menor negada"; Path = "/api/public/idade/confirmar"; Status = 400; Method = "POST"; Body = $idadeMenorBody; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "anuncio sintetico"; Path = "/api/public/anuncios/$SlugSintetico"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "anuncio bloqueado sem idade"; Path = "/api/public/anuncios/$SlugBloqueadoSintetico"; Status = 404; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "cidade sintetica"; Path = "/api/public/acompanhantes/$UfSintetica/$CidadeSintetica"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "bairro sintetico"; Path = "/api/public/acompanhantes/$UfSintetica/$CidadeSintetica/$BairroSintetico"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "stories sem idade"; Path = "/api/public/anuncios/$SlugSintetico/stories"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "visualizacao sintetica"; Path = "/api/public/anuncios/$SlugSintetico/visualizacao"; Status = 200; Method = "POST"; Body = $metricBody; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "clique whatsapp sintetico"; Path = "/api/public/anuncios/$SlugSintetico/clique-whatsapp"; Status = 200; Method = "POST"; Body = $metricBody; AllowSyntheticWhatsapp = $true; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "clique bloqueado sem idade"; Path = "/api/public/anuncios/$SlugBloqueadoSintetico/clique-whatsapp"; Status = 404; Method = "POST"; Body = $metricBody; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "idade maior confirmada"; Path = "/api/public/idade/confirmar"; Status = 200; Method = "POST"; Body = $idadeMaiorBody; AllowSyntheticWhatsapp = $false; Session = $idadeSession })
  $requests.Add([pscustomobject]@{ Nome = "idade status com cookie"; Path = "/api/public/idade/status"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $idadeSession })
  $requests.Add([pscustomobject]@{ Nome = "stories com idade"; Path = "/api/public/anuncios/$SlugSintetico/stories"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $idadeSession })
  $requests.Add([pscustomobject]@{ Nome = "anuncio bloqueado com idade"; Path = "/api/public/anuncios/$SlugBloqueadoSintetico"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $idadeSession })
  $requests.Add([pscustomobject]@{ Nome = "clique bloqueado com idade"; Path = "/api/public/anuncios/$SlugBloqueadoSintetico/clique-whatsapp"; Status = 200; Method = "POST"; Body = $metricBody; AllowSyntheticWhatsapp = $true; Session = $idadeSession })
}

$requests.Add([pscustomobject]@{ Nome = "seo rota proibida perfil"; Path = "/api/public/seo/rota?caminho=$encodedPerfil"; Status = 400; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false })
$requests.Add([pscustomobject]@{ Nome = "seo url absoluta producao"; Path = "/api/public/seo/rota?caminho=$encodedUrlProducao"; Status = 400; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false })

foreach ($request in $requests) {
  $session = if ($request.PSObject.Properties.Name -contains "Session") { $request.Session } else { $null }
  $result = Invoke-LocalHttp -Path $request.Path -ExpectedStatus $request.Status -Method $request.Method -Body $request.Body -Session $session
  if ($result.ErroOperacional -and $result.Status -eq 0) {
    $pending.Add("PENDENTE_BACKEND_LOCAL_INDISPONIVEL: $($result.Url)")
    Add-Check $request.Nome $false "backend local indisponivel"
    continue
  }
  Add-Check "$($request.Nome) status $($request.Status)" ($result.Status -eq $request.Status) "status obtido: $($result.Status)"
  Assert-NoSensitivePublicData -Nome $request.Nome -Body $result.Body -AllowSyntheticWhatsapp $request.AllowSyntheticWhatsapp
  Add-Check "$($request.Nome) sem pendencia obsoleta de stories" (-not ($result.Body -match 'PENDENTE_CONFIRMACAO_IDADE_STORIES')) "usar IDADE_NAO_CONFIRMADA ou PENDENTE_URL_PUBLICA_MIDIA_CDN"
  if ($request.Nome -eq "anuncio sintetico") {
    Add-Check "anuncio sintetico sem story publico" ($result.Body -match '"story"\s*:\s*false') "story nao deve ser liberado por padrao"
    Add-Check "anuncio sintetico sem midia bloqueada" ($result.Body -match '"midias"\s*:\s*\[\s*\]') "midia BLOQUEADO e story nao devem aparecer"
  }
  if ($request.Nome -eq "idade status sem cookie") {
    Add-Check "idade sem cookie nao confirmada" ($result.Body -match '"confirmada"\s*:\s*false') "idade nao deve ser confirmada sem cookie"
  }
  if ($request.Nome -eq "idade maior confirmada") {
    $setCookie = Get-HeaderValue $result.Headers "Set-Cookie"
    Add-Check "idade maior cria confirmacao" ($result.Body -match '"confirmada"\s*:\s*true') "confirmacao local deve ser aceita"
    Add-Check "idade retorna Set-Cookie" ($setCookie -match 'topsv3_idade_confirmada=') "cookie de idade deve ser emitido"
    Add-Check "idade cookie HttpOnly" ($setCookie -match 'HttpOnly') "cookie deve ser HttpOnly"
    Add-Check "idade cookie SameSite Lax" ($setCookie -match 'SameSite=Lax') "cookie deve usar SameSite=Lax"
    Add-Check "idade cookie sem Secure em local" (-not ($setCookie -match ';\s*Secure(?:;|$)')) "Secure deve ficar desligado em local HTTP"
  }
  if ($request.Nome -eq "idade status com cookie") {
    Add-Check "idade com cookie confirmada" ($result.Body -match '"confirmada"\s*:\s*true') "cookie assinado deve ser aceito"
  }
  if ($request.Nome -eq "stories sem idade") {
    Add-Check "stories bloqueados sem idade" ($result.Body -match '"autorizado"\s*:\s*false' -and $result.Body -match '"stories"\s*:\s*\[\s*\]') "stories exigem idade confirmada"
    Add-Check "stories sem idade usam motivo atual" ($result.Body -match 'IDADE_NAO_CONFIRMADA') "pendencia antiga de confirmacao nao deve aparecer"
  }
  if ($request.Nome -eq "stories com idade") {
    Add-Check "stories liberados com idade" ($result.Body -match '"autorizado"\s*:\s*true' -and $result.Body -match '"stories"\s*:\s*\[') "backend liberou stories autorizados"
    Add-Check "stories indicam CDN pendente" ($result.Body -match 'PENDENTE_URL_PUBLICA_MIDIA_CDN') "sem URL publica real de midia"
  }
  if ($request.Nome -eq "visualizacao sintetica") {
    Add-Check "visualizacao registrada" ($result.Body -match '"registrado"\s*:\s*true') "evento_visualizacao deve ser registrado"
    Add-Check "visualizacao indica CDN pendente" ($result.Body -match 'PENDENTE_URL_PUBLICA_MIDIA_CDN') "story sem URL publica real de midia"
  }
  if ($request.Nome -eq "anuncio bloqueado com idade") {
    Add-Check "conteudo bloqueado liberavel com idade" ($result.Body -match ('"slug"\s*:\s*"' + [regex]::Escape($SlugBloqueadoSintetico) + '"')) "backend liberou detalhe apos idade"
  }
  if ($request.Nome -eq "clique whatsapp sintetico" -or $request.Nome -eq "clique bloqueado com idade") {
    Add-Check "clique whatsapp disponivel" ($result.Body -match '"disponivel"\s*:\s*true') "politica backend autorizou contato sintetico"
    Add-Check "clique whatsapp retorna somente URL sintetica" ($result.Body -match '"whatsappUrl"\s*:\s*"https://wa\.me/5500000000000"') "somente endpoint autorizado retorna WhatsApp sintetico"
    Add-Check "clique whatsapp sem campo bruto" (-not ($result.Body -match 'whatsapp_normalizado|whatsappNormalizado')) "telefone bruto nao deve ser retornado"
  }
}

Write-Host "Validacao smoke HTTP da API publica local"
Write-Host "BaseUrl=$SafeBaseUrl"
Write-Host "Total de verificacoes: $($checks.Count)"
Write-Host "Verificacoes OK: $(@($checks | Where-Object { $_.Resultado -eq 'OK' }).Count)"
Write-Host "Verificacoes com falha: $($failures.Count)"

if ($pending.Count -gt 0) {
  $pending | Sort-Object -Unique | ForEach-Object { Write-Host $_ }
  Write-Host "VALIDATION_RESULT=PENDENTE_API_PUBLICA_LOCAL"
  exit 2
}

if ($failures.Count -gt 0) {
  $failures | Sort-Object -Unique | ForEach-Object { Write-Host "FALHA: $_" }
  Write-Host "VALIDATION_RESULT=FALHA_API_PUBLICA_LOCAL"
  exit 1
}

Write-Host "VALIDATION_RESULT=OK_API_PUBLICA_LOCAL"
exit 0
