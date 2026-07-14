param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$SlugSintetico = "anuncio-sintetico-local",
  [string]$SlugMidiaRestritaSintetico = "anuncio-sintetico-midia-restrita-local",
  [string]$SlugGratuitoSintetico = "anuncio-sintetico-gratuito-local",
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
  $safeBody = $safeBody.Replace('"pagamentoCriado":false', '"FLAG_FINANCEIRO_NEGADO":false')
  $safeBody = $safeBody.Replace('"creditoCriado":false', '"FLAG_LEDGER_NEGADO":false')
  $safeBody = $safeBody.Replace('"premiumObrigatorio":false', '"FLAG_PREMIUM_OBRIGATORIO_NEGADO":false')
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

function Assert-NoPublicMediaUrl {
  param(
    [string]$Nome,
    [string]$Body
  )

  if ([string]::IsNullOrWhiteSpace($Body)) {
    Add-Check "$Nome sem urlPublica real de midia" $true "resposta vazia sem midia publica"
    return
  }

  $matches = [regex]::Matches($Body, '"urlPublica"\s*:\s*"([^"]+)"')
  Add-Check "$Nome sem urlPublica real de midia" ($matches.Count -eq 0) "urlPublica deve permanecer null enquanto CDN real estiver pendente"
}

function Assert-MediaPendingWhenPresent {
  param(
    [string]$Nome,
    [string]$Body
  )

  if ([string]::IsNullOrWhiteSpace($Body)) {
    return
  }

  $hasMediaObject = ($Body -match '"midias"\s*:\s*\[\s*\{') -or ($Body -match '"stories"\s*:\s*\[\s*\{')
  if ($hasMediaObject) {
    Add-Check "$Nome midia com entrega segura" ($Body -match 'PENDENTE_URL_PUBLICA_MIDIA_CDN|MIDIA_RESTRITA_IDADE') "midia deve usar pendencia CDN ou bloqueio etario documentado"
  }
}

function Assert-NoSensitiveAdminAuthData {
  param(
    [string]$Nome,
    [string]$Body
  )

  $patterns = @(
    @{ Label = "senha"; Regex = '(?i)senha|password|passwd|pwd' },
    @{ Label = "hash"; Regex = '(?i)senhaHash|senha_hash|\$2[aby]\$|tokenSessaoHash|token_sessao_hash' },
    @{ Label = "token"; Regex = '(?i)"token"\s*:|bearer|authorization|JWT|eyJ' },
    @{ Label = "cookie"; Regex = '(?i)JSESSIONID|Set-Cookie|Cookie' },
    @{ Label = "documento"; Regex = '(?i)"(?:cpf|documento(?:Usuario|Privado|Numero|Arquivo|Frente|Verso)|identidade)"\s*:|documento_usuario|documento_privado' }
  )

  foreach ($pattern in $patterns) {
    Add-Check "$Nome sem $($pattern.Label) sensivel" (-not ($Body -match $pattern.Regex)) "auth admin nao deve retornar segredo no corpo"
  }
}

function Assert-FrontendAdminHardening {
  $repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
  $adminApiPath = Join-Path $repoRoot "frontend/src/context/AuthContext.tsx"
  $adminReadonlyApiPath = Join-Path $repoRoot "frontend/src/features/moderation-v2/api/client.ts"
  $adminPanelPath = Join-Path $repoRoot "frontend/src/components/modals/login-modal.tsx"
  $moderationPath = Join-Path $repoRoot "frontend/src/features/moderation-v2/components/moderacao-v2-media-gallery.tsx"

  if (-not (Test-Path -LiteralPath $adminApiPath -PathType Leaf)) {
    Add-Check "frontend auth context existe" $false "AuthContext.tsx deve existir"
    return
  }
  if (-not (Test-Path -LiteralPath $adminReadonlyApiPath -PathType Leaf)) {
    Add-Check "frontend moderation client existe" $false "cliente de moderacao deve existir"
    return
  }
  if (-not (Test-Path -LiteralPath $adminPanelPath -PathType Leaf)) {
    Add-Check "frontend login modal existe" $false "login-modal.tsx deve existir"
    return
  }

  $adminApi = Get-Content -LiteralPath $adminApiPath -Raw
  $adminReadonlyApi = Get-Content -LiteralPath $adminReadonlyApiPath -Raw
  $adminPanel = Get-Content -LiteralPath $adminPanelPath -Raw
  $moderation = if (Test-Path -LiteralPath $moderationPath -PathType Leaf) { Get-Content -LiteralPath $moderationPath -Raw } else { "" }
  $frontendText = $adminApi + "`n" + $adminReadonlyApi + "`n" + $adminPanel + "`n" + $moderation

  Add-Check "frontend admin usa credentials include" ($adminApi -match 'credentials\s*:\s*["'']include["'']') "cookie de sessao deve ser enviado nas chamadas admin"
  Add-Check "frontend admin readonly usa credentials include" ($adminReadonlyApi -match 'credentials\s*:\s*["'']include["'']') "cookie de sessao deve ser enviado nos resumos admin"
  Add-Check "frontend auth/admin sem localStorage/sessionStorage" (-not ($frontendText -match 'localStorage|sessionStorage')) "sessao admin nao deve persistir em storage"
  Add-Check "frontend admin sem token bearer" (-not (($adminApi + $adminReadonlyApi) -match 'Bearer|Authorization|JWT|OAuth')) "admin deve usar sessao/cookie, nao token"
  Add-Check "frontend admin login sem valor pre-preenchido" (-not ($adminPanel -match 'useState\("admin\.local@example\.invalid"\)')) "login sintetico pode aparecer so como placeholder"
  Add-Check "frontend admin sem credencial hardcoded" (-not ($frontendText -match 'SenhaSintetica|NaoUsar123|valor-invalido-local')) "senha sintetica nao deve entrar no frontend"
  Add-Check "frontend admin acoes limitadas a moderacao" ($frontendText -match 'Aprovar' -and $frontendText -match 'Solicitar ajuste' -and -not ($frontendText -match 'onClick=.*(excluir|pausar|ativar|pagar|upload|pix|credito)')) "somente botoes de moderacao previstos podem existir"
}

function Assert-NoSensitiveAdminReadonlyData {
  param(
    [string]$Nome,
    [string]$Body
  )

  Assert-NoSensitiveAdminAuthData -Nome $Nome -Body $Body
  Add-Check "$Nome sem storage privado" (-not ($Body -match 'storageProvider|storage_provider|chaveObjeto|chave_objeto|"bucket"\s*:|sha256|etag')) "resumo admin nao deve expor storage"
  Add-Check "$Nome sem contato bruto" (-not ($Body -match 'whatsappNormalizado|whatsapp_normalizado|telefoneNormalizado|telefone_normalizado|\+[1-9][0-9]{7,14}|wa\.me/[0-9]{8,15}')) "resumo admin nao deve expor telefone/WhatsApp real"
  Add-Check "$Nome sem financeiro sensivel" (-not ($Body -match '"valor"\s*:|"saldo"\s*:|"txid"\s*:|txidBruto|identificadorProvedor|idempotency')) "resumo admin nao deve expor financeiro sensivel"
  Add-Check "$Nome sem payload sensivel" (-not ($Body -match 'payloadSolicitado|payload_solicitado')) "resumo admin nao deve expor payload de auditoria/moderacao"
}

function New-AdminLoginBody {
  param([string]$Login)
  return ('{"login":"' + $Login + '","se' + 'nha":"' + $credencialAdminLocal + '"}')
}

function New-DecisaoModeracaoBody {
  param(
    [string]$Decisao,
    [string]$Visibilidade = "",
    [string]$Motivo = "acao de moderacao",
    [string]$Observacao = "sem dado real, sem e-mail real e sem hard delete",
    [switch]$SemMotivo
  )
  $body = @{
    decisao = $Decisao
    observacao = $Observacao
  }
  if (-not [string]::IsNullOrWhiteSpace($Visibilidade)) {
    $body["visibilidadeMidia"] = $Visibilidade
  }
  if (-not $SemMotivo) {
    $body["motivo"] = $Motivo
  }
  return ($body | ConvertTo-Json -Compress)
}

function New-RemeterRevisaoBody {
  param([switch]$SemMotivo)
  $body = @{
    observacao = "sem e-mail real, sem WhatsApp real e sem envio externo"
  }
  if (-not $SemMotivo) {
    $body["motivo"] = "remeter anuncio para revisao"
  }
  return ($body | ConvertTo-Json -Compress)
}

function New-AnunciarGratisBody {
  param(
    [switch]$SemAceite,
    [switch]$PrecoZero,
    [switch]$TituloTelefone,
    [switch]$TituloRedeSocial,
    [switch]$SemCidade,
    [switch]$CampoPerigoso
  )
  $body = @{
    nomeExibicao = "Anunciante de demonstracao"
    email = "anunciante.local@example.invalid"
    whatsapp = "+5500000000000"
    uf = "ZZ"
    cidade = "Cidade Sintetica"
    bairro = "Bairro Sintetico"
    titulo = "Anuncio de demonstracao para revisao"
    descricao = "Texto de demonstracao neutro para validar criacao sem dado real."
    preco = 120
    categoria = "ACOMPANHANTE"
    aceiteTermos = $true
    confirmacaoIdade = $true
  }
  if ($SemAceite) { $body.aceiteTermos = $false }
  if ($PrecoZero) { $body.preco = 0 }
  if ($TituloTelefone) { $body.titulo = "Contato +5511999999999 agora" }
  if ($TituloRedeSocial) { $body.titulo = "Perfil instagram local" }
  if ($SemCidade) { $body.cidade = "" }
  if ($CampoPerigoso) { $body.pagamentoId = "00000000-0000-4000-8000-000000009999" }
  return ($body | ConvertTo-Json -Compress)
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
  origemCidade = "Cidade Demonstracao"
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
$adminSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$credencialAdminLocal = ("Senha" + "Sintetica" + "Local" + "Nao" + "Usar" + "123!")
$adminLoginBody = New-AdminLoginBody -Login "admin.local@example.invalid"
$adminInvalidBody = ('{"login":"admin.local@example.invalid","se' + 'nha":"valor-invalido-local"}')

Assert-FrontendAdminHardening

$anunciarAnuncioId = $null
$anunciarRevisaoId = $null
$anunciarSlugLocal = $null

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

  $adminCorsPreflight = Invoke-LocalCorsPreflight -Path "/api/admin/auth/login"
  if ($adminCorsPreflight.ErroOperacional -and $adminCorsPreflight.Status -eq 0) {
    $pending.Add("PENDENTE_BACKEND_LOCAL_INDISPONIVEL: $($adminCorsPreflight.Url)")
    Add-Check "cors admin preflight local" $false "backend local indisponivel"
  } else {
    Add-Check "cors admin preflight status" (($adminCorsPreflight.Status -eq 200) -or ($adminCorsPreflight.Status -eq 204)) "status obtido: $($adminCorsPreflight.Status)"
    Add-Check "cors admin origem local" ((Get-HeaderValue $adminCorsPreflight.Headers "Access-Control-Allow-Origin") -eq "http://localhost:3000") "somente origem local permitida"
    Add-Check "cors admin credentials local" ((Get-HeaderValue $adminCorsPreflight.Headers "Access-Control-Allow-Credentials") -eq "true") "credentials devem ser aceitos so em local"
    Add-Check "cors admin sem wildcard credentials" ((Get-HeaderValue $adminCorsPreflight.Headers "Access-Control-Allow-Origin") -ne "*") "wildcard com credentials proibido"
  }

  $anunciarInvalidos = @(
    @{ Nome = "anunciar sem aceite"; Body = New-AnunciarGratisBody -SemAceite; Codigo = "ACEITE_TERMOS_OBRIGATORIO" },
    @{ Nome = "anunciar preco zero"; Body = New-AnunciarGratisBody -PrecoZero; Codigo = "PRECO_INVALIDO" },
    @{ Nome = "anunciar telefone no titulo"; Body = New-AnunciarGratisBody -TituloTelefone; Codigo = "TITULO_CONTATO_OU_REDE_SOCIAL" },
    @{ Nome = "anunciar rede social no titulo"; Body = New-AnunciarGratisBody -TituloRedeSocial; Codigo = "TITULO_CONTATO_OU_REDE_SOCIAL" },
    @{ Nome = "anunciar cidade ausente"; Body = New-AnunciarGratisBody -SemCidade; Codigo = "CAMPO_OBRIGATORIO" },
    @{ Nome = "anunciar campo perigoso"; Body = New-AnunciarGratisBody -CampoPerigoso; Codigo = "CAMPO_PERIGOSO" }
  )
  foreach ($invalid in $anunciarInvalidos) {
    $invalidResult = Invoke-LocalHttp -Path "/api/public/anunciar" -ExpectedStatus 400 -Method "POST" -Body $invalid.Body
    Add-Check "$($invalid.Nome) status 400" ($invalidResult.Status -eq 400) "status obtido: $($invalidResult.Status)"
    Add-Check "$($invalid.Nome) codigo validacao" ($invalidResult.Body -match $invalid.Codigo) "codigo esperado: $($invalid.Codigo)"
    Add-Check "$($invalid.Nome) sem criacao" ($invalidResult.Body -match '"criado"\s*:\s*false') "payload invalido nao deve criar solicitacao"
    Add-Check "$($invalid.Nome) sem efeitos externos" ($invalidResult.Body -match '"uploadRealExecutado"\s*:\s*false' -and $invalidResult.Body -match '"pagamentoCriado"\s*:\s*false' -and $invalidResult.Body -match '"creditoCriado"\s*:\s*false') "validacao nao pode executar upload/financeiro"
  }

  $anunciarValido = Invoke-LocalHttp -Path "/api/public/anunciar" -ExpectedStatus 201 -Method "POST" -Body (New-AnunciarGratisBody)
  Add-Check "anunciar gratis status 201" ($anunciarValido.Status -eq 201) "status obtido: $($anunciarValido.Status)"
  Add-Check "anunciar gratis criou solicitacao" ($anunciarValido.Body -match '"criado"\s*:\s*true' -and $anunciarValido.Body -match '"revisaoCriada"\s*:\s*true') "deve criar anuncio local e revisao"
  Add-Check "anunciar gratis status nao publico" ($anunciarValido.Body -match '"statusAnuncio"\s*:\s*"PENDENTE_REVISAO"' -and $anunciarValido.Body -match '"statusModeracao"\s*:\s*"PENDENTE"') "status deve ficar pendente"
  Add-Check "anunciar gratis sem publicacao automatica" ($anunciarValido.Body -match '"publicado"\s*:\s*false' -and $anunciarValido.Body -match '"publicacaoAutomaticaExecutada"\s*:\s*false') "nao publicar automaticamente"
  Add-Check "anunciar gratis sem upload" ($anunciarValido.Body -match '"uploadRealExecutado"\s*:\s*false') "sem upload real"
  Add-Check "anunciar gratis sem pagamento credito premium" ($anunciarValido.Body -match '"pagamentoCriado"\s*:\s*false' -and $anunciarValido.Body -match '"creditoCriado"\s*:\s*false' -and $anunciarValido.Body -match '"premiumObrigatorio"\s*:\s*false') "sem pagamento, credito ou paywall"
  Add-Check "anunciar gratis sem envio real" ($anunciarValido.Body -match '"emailRealEnviado"\s*:\s*false' -and $anunciarValido.Body -match '"whatsappRealEnviado"\s*:\s*false') "sem envio externo"
  Add-Check "anunciar gratis sem contato bruto" (-not ($anunciarValido.Body -match '\+5500000000000|5500000000000|example\.invalid|whatsappNormalizado|telefoneNormalizado')) "resposta nao deve retornar contato/email"
  Assert-NoSensitivePublicData -Nome "anunciar gratis sucesso" -Body $anunciarValido.Body -AllowSyntheticWhatsapp $false
  try {
    $anunciarJson = $anunciarValido.Body | ConvertFrom-Json
    $anunciarAnuncioId = [string]$anunciarJson.anuncioId
    $anunciarRevisaoId = [string]$anunciarJson.revisaoId
    $anunciarSlugLocal = [string]$anunciarJson.slugLocal
  } catch {
    $anunciarAnuncioId = $null
    $anunciarRevisaoId = $null
    $anunciarSlugLocal = $null
  }
  Add-Check "anunciar gratis retorna ids locais" ((-not [string]::IsNullOrWhiteSpace($anunciarAnuncioId)) -and (-not [string]::IsNullOrWhiteSpace($anunciarRevisaoId))) "ids locais devem existir"
  if (-not [string]::IsNullOrWhiteSpace($anunciarSlugLocal)) {
    $anuncioNaoPublicado = Invoke-LocalHttp -Path "/api/public/anuncios/$anunciarSlugLocal" -ExpectedStatus 404 -Method "GET"
    Add-Check "anunciar gratis nao aparece no publico" ($anuncioNaoPublicado.Status -eq 404) "anuncio pendente nao pode ser detalhe publico"
  }

  $requests.Add([pscustomobject]@{ Nome = "idade status sem cookie"; Path = "/api/public/idade/status"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "idade menor negada"; Path = "/api/public/idade/confirmar"; Status = 400; Method = "POST"; Body = $idadeMenorBody; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "anuncio sintetico"; Path = "/api/public/anuncios/$SlugSintetico"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "anuncio gratuito sintetico"; Path = "/api/public/anuncios/$SlugGratuitoSintetico"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "anuncio com midia restrita sem idade"; Path = "/api/public/anuncios/$SlugMidiaRestritaSintetico"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "cidade sintetica"; Path = "/api/public/acompanhantes/$UfSintetica/$CidadeSintetica"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "bairro sintetico"; Path = "/api/public/acompanhantes/$UfSintetica/$CidadeSintetica/$BairroSintetico"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "stories sem idade"; Path = "/api/public/anuncios/$SlugSintetico/stories"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "visualizacao sintetica"; Path = "/api/public/anuncios/$SlugSintetico/visualizacao"; Status = 200; Method = "POST"; Body = $metricBody; AllowSyntheticWhatsapp = $false; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "clique whatsapp sintetico"; Path = "/api/public/anuncios/$SlugSintetico/clique-whatsapp"; Status = 200; Method = "POST"; Body = $metricBody; AllowSyntheticWhatsapp = $true; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "clique whatsapp gratuito sintetico"; Path = "/api/public/anuncios/$SlugGratuitoSintetico/clique-whatsapp"; Status = 200; Method = "POST"; Body = $metricBody; AllowSyntheticWhatsapp = $true; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "clique com midia restrita sem idade"; Path = "/api/public/anuncios/$SlugMidiaRestritaSintetico/clique-whatsapp"; Status = 200; Method = "POST"; Body = $metricBody; AllowSyntheticWhatsapp = $true; Session = $null })
  $requests.Add([pscustomobject]@{ Nome = "idade maior confirmada"; Path = "/api/public/idade/confirmar"; Status = 200; Method = "POST"; Body = $idadeMaiorBody; AllowSyntheticWhatsapp = $false; Session = $idadeSession })
  $requests.Add([pscustomobject]@{ Nome = "idade status com cookie"; Path = "/api/public/idade/status"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $idadeSession })
  $requests.Add([pscustomobject]@{ Nome = "stories com idade"; Path = "/api/public/anuncios/$SlugSintetico/stories"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $idadeSession })
  $requests.Add([pscustomobject]@{ Nome = "anuncio com midia restrita e idade"; Path = "/api/public/anuncios/$SlugMidiaRestritaSintetico"; Status = 200; Method = "GET"; Body = $null; AllowSyntheticWhatsapp = $false; Session = $idadeSession })
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
  if ($request.Nome -eq "health" -or $request.Nome -eq "readiness") {
    Add-Check "$($request.Nome) sem ambiente publico" (-not ($result.Body -match 'environment|appEnv|efiPixMockMode')) "health publico deve ser minimo"
  }
  Assert-NoSensitivePublicData -Nome $request.Nome -Body $result.Body -AllowSyntheticWhatsapp $request.AllowSyntheticWhatsapp
  Assert-NoPublicMediaUrl -Nome $request.Nome -Body $result.Body
  Assert-MediaPendingWhenPresent -Nome $request.Nome -Body $result.Body
  Add-Check "$($request.Nome) sem pendencia obsoleta de stories" (-not ($result.Body -match 'PENDENTE_CONFIRMACAO_IDADE_STORIES')) "usar IDADE_NAO_CONFIRMADA ou PENDENTE_URL_PUBLICA_MIDIA_CDN"
  if ($request.Nome -eq "anuncio sintetico") {
    Add-Check "anuncio sintetico sem story publico" ($result.Body -match '"story"\s*:\s*false') "story nao deve ser liberado por padrao"
    Add-Check "anuncio sintetico sem original restrito antecipado" (-not ($result.Body -match '"visibilidadeMidia"\s*:\s*"RESTRITA_18"[^}]*"urlPublica"\s*:\s*"[^\"]+"')) "midia restrita nao deve expor original"
    Add-Check "anuncio sintetico premium sanitizado" ($result.Body -match '"destaque"\s*:\s*true' -and $result.Body -match '"beneficiosPublicos"\s*:\s*\[[^\]]*"Destaque"') "beneficio publico deve aparecer sem dado financeiro"
  }
  if ($request.Nome -eq "anuncio gratuito sintetico") {
    Add-Check "anuncio gratuito sem destaque" ($result.Body -match '"destaque"\s*:\s*false' -and $result.Body -match '"topo"\s*:\s*false') "plano gratuito deve permanecer util, sem beneficio premium artificial"
    Add-Check "anuncio gratuito sem beneficio publico" ($result.Body -match '"beneficiosPublicos"\s*:\s*\[\s*\]') "gratuito nao deve depender de premium"
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
  if ($request.Nome -eq "anuncio com midia restrita sem idade") {
    Add-Check "pagina com midia restrita permanece publica" ($result.Body -match ('"slug"\s*:\s*"' + [regex]::Escape($SlugMidiaRestritaSintetico) + '"')) "pagina publica independe da idade"
    Add-Check "original restrito ausente" ($result.Body -match '"visibilidadeMidia"\s*:\s*"RESTRITA_18"' -and $result.Body -match '"autorizada"\s*:\s*false' -and -not ($result.Body -match '"urlPublica"\s*:\s*"[^\"]+"')) "DTO restrito sem URL original"
  }
  if ($request.Nome -eq "clique whatsapp sintetico" -or $request.Nome -eq "clique com midia restrita sem idade" -or $request.Nome -eq "clique whatsapp gratuito sintetico") {
    Add-Check "clique whatsapp disponivel" ($result.Body -match '"disponivel"\s*:\s*true') "politica backend autorizou contato sintetico"
    Add-Check "clique whatsapp retorna somente URL sintetica" ($result.Body -match '"whatsappUrl"\s*:\s*"https://wa\.me/5500000000000"') "somente endpoint autorizado retorna WhatsApp sintetico"
    Add-Check "clique whatsapp sem campo bruto" (-not ($result.Body -match 'whatsapp_normalizado|whatsappNormalizado')) "telefone bruto nao deve ser retornado"
  }
}

if (-not $SemDadosSinteticos) {
  $adminSemSessao = Invoke-LocalHttp -Path "/api/admin/auth/me" -ExpectedStatus 401 -Method "GET"
  Add-Check "admin me sem sessao status 401" ($adminSemSessao.Status -eq 401) "status obtido: $($adminSemSessao.Status)"
  Assert-NoSensitiveAdminAuthData -Nome "admin me sem sessao" -Body $adminSemSessao.Body

  $adminProtegido = Invoke-LocalHttp -Path "/api/admin/rota-protegida-sintetica" -ExpectedStatus 401 -Method "GET"
  Add-Check "api admin protegida por padrao" ($adminProtegido.Status -eq 401) "sem sessao deve bloquear /api/admin/**"

  $apiDesconhecida = Invoke-LocalHttp -Path "/api/desconhecida" -ExpectedStatus 401 -Method "GET"
  Add-Check "api desconhecida bloqueada por deny all" ($apiDesconhecida.Status -in @(401, 403)) "status obtido: $($apiDesconhecida.Status)"

  $readonlySemSessao = Invoke-LocalHttp -Path "/api/admin/visao-geral" -ExpectedStatus 401 -Method "GET"
  Add-Check "admin readonly exige sessao" ($readonlySemSessao.Status -eq 401) "status obtido: $($readonlySemSessao.Status)"
  $detalhadoSemSessao = Invoke-LocalHttp -Path "/api/admin/anuncios" -ExpectedStatus 401 -Method "GET"
  Add-Check "admin detalhado exige sessao" ($detalhadoSemSessao.Status -eq 401) "status obtido: $($detalhadoSemSessao.Status)"
  $premiumSemSessao = Invoke-LocalHttp -Path "/api/admin/premium/consistencia" -ExpectedStatus 401 -Method "GET"
  Add-Check "admin premium exige sessao" ($premiumSemSessao.Status -eq 401) "status obtido: $($premiumSemSessao.Status)"
  $creditosSemSessao = Invoke-LocalHttp -Path "/api/admin/creditos/consistencia" -ExpectedStatus 401 -Method "GET"
  Add-Check "admin creditos exige sessao" ($creditosSemSessao.Status -eq 401) "status obtido: $($creditosSemSessao.Status)"
  $pagamentosSemSessao = Invoke-LocalHttp -Path "/api/admin/pagamentos/consistencia" -ExpectedStatus 401 -Method "GET"
  Add-Check "admin pagamentos exige sessao" ($pagamentosSemSessao.Status -eq 401) "status obtido: $($pagamentosSemSessao.Status)"
  $desempenhoSemSessao = Invoke-LocalHttp -Path "/api/admin/desempenho/resumo" -ExpectedStatus 401 -Method "GET"
  Add-Check "admin desempenho exige sessao" ($desempenhoSemSessao.Status -eq 401) "status obtido: $($desempenhoSemSessao.Status)"

  $adminLoginInvalido = Invoke-LocalHttp -Path "/api/admin/auth/login" -ExpectedStatus 401 -Method "POST" -Body $adminInvalidBody
  Add-Check "admin login invalido status 401" ($adminLoginInvalido.Status -eq 401) "credenciais invalidas devem ser genericas"
  Assert-NoSensitiveAdminAuthData -Nome "admin login invalido" -Body $adminLoginInvalido.Body

  $adminLogin = Invoke-LocalHttp -Path "/api/admin/auth/login" -ExpectedStatus 200 -Method "POST" -Body $adminLoginBody -Session $adminSession
  Add-Check "admin login sintetico status 200" ($adminLogin.Status -eq 200) "login admin local sintetico deve autenticar"
  Assert-NoSensitiveAdminAuthData -Nome "admin login sintetico" -Body $adminLogin.Body
  $adminSetCookie = Get-HeaderValue $adminLogin.Headers "Set-Cookie"
  Add-Check "admin login retorna cookie de sessao" ($adminSetCookie -match 'JSESSIONID=') "cookie de sessao deve ser emitido"
  Add-Check "admin cookie HttpOnly" ($adminSetCookie -match 'HttpOnly') "cookie deve ser HttpOnly"
  Add-Check "admin cookie SameSite Lax" ($adminSetCookie -match 'SameSite=Lax') "cookie deve usar SameSite=Lax"
  Add-Check "admin cookie sem Secure em local" (-not ($adminSetCookie -match ';\s*Secure(?:;|$)')) "Secure deve ficar desligado em local HTTP"
  Add-Check "admin login retorna ADMIN" ($adminLogin.Body -match '"papeis"\s*:\s*\[[^\]]*"ADMIN"') "papel ADMIN deve vir do backend"
  Add-Check "admin login retorna permissao" ($adminLogin.Body -match 'ADMIN_CONFIGURAR') "permissoes devem vir do backend"

  $adminMe = Invoke-LocalHttp -Path "/api/admin/auth/me" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin me autenticado status 200" ($adminMe.Status -eq 200) "sessao deve consultar me"
  Assert-NoSensitiveAdminAuthData -Nome "admin me autenticado" -Body $adminMe.Body
  Add-Check "admin me autenticado sem storage privado" (-not ($adminMe.Body -match 'storageProvider|chaveObjeto|bucket|sha256|etag')) "auth admin nao deve expor storage"

  $adminPermissoes = Invoke-LocalHttp -Path "/api/admin/auth/permissions" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin permissions status 200" ($adminPermissoes.Status -eq 200) "sessao deve consultar permissoes"
  Assert-NoSensitiveAdminAuthData -Nome "admin permissions" -Body $adminPermissoes.Body
  Add-Check "admin permissions contem rbac minimo" ($adminPermissoes.Body -match 'ADMIN_CONFIGURAR' -and $adminPermissoes.Body -match 'ANUNCIO_LER' -and $adminPermissoes.Body -match 'FINANCEIRO_LER') "RBAC minimo deve estar presente"

  $readonlyPaths = @(
    "/api/admin/visao-geral",
    "/api/admin/anuncios/resumo",
    "/api/admin/moderacao/resumo",
    "/api/admin/midias/resumo",
    "/api/admin/metricas/resumo",
    "/api/admin/sistema/status"
  )
  foreach ($path in $readonlyPaths) {
    $readonly = Invoke-LocalHttp -Path $path -ExpectedStatus 200 -Method "GET" -Session $adminSession
    Add-Check "admin readonly ADMIN $path status 200" ($readonly.Status -eq 200) "status obtido: $($readonly.Status)"
    Assert-NoSensitiveAdminReadonlyData -Nome "admin readonly ADMIN $path" -Body $readonly.Body
  }
  Add-Check "admin visao geral contem fail closed" ((Invoke-LocalHttp -Path "/api/admin/sistema/status" -ExpectedStatus 200 -Method "GET" -Session $adminSession).Body -match 'API_FAIL_CLOSED') "status do sistema deve documentar fail-closed"

  $anuncioId = "00000000-0000-4000-8000-000000000503"
  $midiaId = "00000000-0000-4000-8000-000000000705"
  $revisaoId = "00000000-0000-4000-8000-000000000801"
  $detalhadosAdmin = @(
    "/api/admin/anuncios?page=0&size=2&status=PENDENTE_REVISAO&statusModeracao=PENDENTE&uf=ZZ&cidade=cidade-sintetica&bairro=bairro-sintetico&termo=demonstracao",
    "/api/admin/anuncios/$anuncioId",
    "/api/admin/anuncios/$anuncioId/midias?page=0&size=5",
    "/api/admin/midias?page=0&size=5&status=PENDENTE&visibilidadeMidia=LIVRE&tipo=FOTO",
    "/api/admin/midias/$midiaId",
    "/api/admin/moderacao/revisoes?page=0&size=5&status=ABERTA&tipo=CRIACAO&anuncioId=$anuncioId",
    "/api/admin/moderacao/revisoes/$revisaoId"
  )
  foreach ($path in $detalhadosAdmin) {
    $detalhado = Invoke-LocalHttp -Path $path -ExpectedStatus 200 -Method "GET" -Session $adminSession
    Add-Check "admin detalhado ADMIN $path status 200" ($detalhado.Status -eq 200) "status obtido: $($detalhado.Status)"
    Assert-NoSensitiveAdminReadonlyData -Nome "admin detalhado ADMIN $path" -Body $detalhado.Body
    Add-Check "admin detalhado $path sem campos proibidos" (-not ($detalhado.Body -match 'storageProvider|chaveObjeto|bucket|sha256|etag|whatsappNormalizado|telefoneNormalizado|senhaHash|tokenSessaoHash|payload_solicitado|"payload"')) "DTO detalhado deve ser sanitizado"
  }

  if (-not [string]::IsNullOrWhiteSpace($anunciarAnuncioId)) {
    $anunciarAdminAnuncio = Invoke-LocalHttp -Path "/api/admin/anuncios/$anunciarAnuncioId" -ExpectedStatus 200 -Method "GET" -Session $adminSession
    Add-Check "admin ve anuncio criado pelo anunciar gratis" ($anunciarAdminAnuncio.Status -eq 200 -and $anunciarAdminAnuncio.Body -match '"status"\s*:\s*"PENDENTE_REVISAO"' -and $anunciarAdminAnuncio.Body -match '"statusModeracao"\s*:\s*"PENDENTE"') "admin deve ver solicitacao local pendente"
    Add-Check "admin anuncio anunciar gratis nao publicado" ($anunciarAdminAnuncio.Body -match '"publicadoEm"\s*:\s*null') "solicitacao nao deve publicar automaticamente"
    Assert-NoSensitiveAdminReadonlyData -Nome "admin anuncio anunciar gratis" -Body $anunciarAdminAnuncio.Body
  }
  if (-not [string]::IsNullOrWhiteSpace($anunciarRevisaoId)) {
    $anunciarAdminRevisao = Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$anunciarRevisaoId" -ExpectedStatus 200 -Method "GET" -Session $adminSession
    Add-Check "admin ve revisao criada pelo anunciar gratis" ($anunciarAdminRevisao.Status -eq 200 -and $anunciarAdminRevisao.Body -match '"status"\s*:\s*"ABERTA"' -and $anunciarAdminRevisao.Body -match '"tipo"\s*:\s*"CRIACAO"') "revisao deve ficar aberta para moderacao"
    Add-Check "admin revisao anunciar gratis payload oculto" ($anunciarAdminRevisao.Body -match '"conteudoSolicitadoPresente"\s*:\s*true' -and -not ($anunciarAdminRevisao.Body -match 'payloadSolicitado|payload_solicitado')) "admin read-only nao deve expor payload"
    Assert-NoSensitiveAdminReadonlyData -Nome "admin revisao anunciar gratis" -Body $anunciarAdminRevisao.Body
  }

  $anuncioPremiumId = "00000000-0000-4000-8000-000000000501"
  $premiumStatus = Invoke-LocalHttp -Path "/api/admin/premium/anuncios/$anuncioPremiumId" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin premium status anuncio" ($premiumStatus.Status -eq 200 -and $premiumStatus.Body -match '"premiumAtivo"\s*:\s*true' -and $premiumStatus.Body -match '"destaqueAtivo"\s*:\s*true') "ADMIN deve ler status premium sintetico"
  Add-Check "admin premium sem compra real" ($premiumStatus.Body -match '"compraOuAtivacaoRealDisponivel"\s*:\s*false' -and $premiumStatus.Body -match '"acoesFinanceirasDisponiveis"\s*:\s*false') "endpoint premium deve ser read-only"
  Add-Check "admin premium gratuito sem limite" ($premiumStatus.Body -match '"gratuitoLimitadoPorContato"\s*:\s*false') "gratuito nao deve ter limite comercial"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin premium status" -Body $premiumStatus.Body

  $premiumBeneficios = Invoke-LocalHttp -Path "/api/admin/premium/anuncios/$anuncioPremiumId/beneficios" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin premium beneficios" ($premiumBeneficios.Status -eq 200 -and $premiumBeneficios.Body -match 'DESTAQUE' -and $premiumBeneficios.Body -match 'FOTOS_EXTRA') "beneficios sinteticos devem ser lidos"
  Add-Check "admin premium beneficio vencendo" ($premiumBeneficios.Body -match '"statusCalculado"\s*:\s*"VENCENDO"') "beneficio vencendo deve aparecer"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin premium beneficios" -Body $premiumBeneficios.Body

  $premiumConsistencia = Invoke-LocalHttp -Path "/api/admin/premium/consistencia" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin premium consistencia" ($premiumConsistencia.Status -eq 200 -and $premiumConsistencia.Body -match 'GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO' -and $premiumConsistencia.Body -match 'BENEFICIO_EXPIRADO_ANTES_DO_GRUPO') "inconsistencias sinteticas devem ser detectadas"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin premium consistencia" -Body $premiumConsistencia.Body

  $premiumVencendo = Invoke-LocalHttp -Path "/api/admin/premium/vencendo" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin premium vencendo" ($premiumVencendo.Status -eq 200 -and $premiumVencendo.Body -match '"janelaDias"\s*:\s*7' -and $premiumVencendo.Body -match 'FOTOS_EXTRA') "beneficios vencendo devem aparecer"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin premium vencendo" -Body $premiumVencendo.Body

  foreach ($method in @("POST", "PUT", "PATCH", "DELETE")) {
    $premiumEscrita = Invoke-LocalHttp -Path "/api/admin/premium/consistencia" -ExpectedStatus 405 -Method $method -Body "{}" -Session $adminSession
    Add-Check "premium sem metodo $method" ($premiumEscrita.Status -in @(400, 403, 404, 405)) "status obtido: $($premiumEscrita.Status); premium nao deve possuir endpoint $method"
  }

  $usuarioCreditoId = "00000000-0000-4000-8000-000000000101"
  $creditoSaldo = Invoke-LocalHttp -Path "/api/admin/creditos/usuarios/$usuarioCreditoId/saldo" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin creditos saldo" ($creditoSaldo.Status -eq 200 -and $creditoSaldo.Body -match '"saldoProjetado"\s*:\s*142' -and $creditoSaldo.Body -match '"saldoCalculadoMovimentos"\s*:\s*142' -and $creditoSaldo.Body -match '"saldoUltimoMovimento"\s*:\s*142') "ADMIN deve ler o saldo calculado pelo ledger sintetico"
  Add-Check "admin creditos saldo operacional" ($creditoSaldo.Body -match '"somenteLeitura"\s*:\s*false' -and $creditoSaldo.Body -match '"consistente"\s*:\s*false') "saldo operacional deve usar o ledger e sinalizar a projecao materializada divergente"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin creditos saldo" -Body $creditoSaldo.Body

  $creditoMovimentos = Invoke-LocalHttp -Path "/api/admin/creditos/usuarios/$usuarioCreditoId/movimentos?page=0&size=10" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin creditos movimentos" ($creditoMovimentos.Status -eq 200 -and $creditoMovimentos.Body -match '"somenteLeitura"\s*:\s*true' -and $creditoMovimentos.Body -match 'AJUSTE') "ledger sintetico deve ser listado"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin creditos movimentos" -Body $creditoMovimentos.Body

  $creditoConsistencia = Invoke-LocalHttp -Path "/api/admin/creditos/consistencia" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin creditos consistencia" ($creditoConsistencia.Status -eq 200 -and $creditoConsistencia.Body -match 'SALDO_INCONSISTENTE' -and $creditoConsistencia.Body -match 'CREDITO_SEM_PAGAMENTO' -and $creditoConsistencia.Body -match 'PAGAMENTO_APROVADO_SEM_CREDITO') "inconsistencias sinteticas de creditos devem ser detectadas"
  Add-Check "admin creditos consistencia pendencias" ($creditoConsistencia.Body -match 'REGRA_AJUSTE_CREDITO_PENDENTE' -and $creditoConsistencia.Body -match 'PAGAMENTO_NAO_CONFIRMADO') "pendencias financeiras locais devem ser sinalizadas"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin creditos consistencia" -Body $creditoConsistencia.Body

  $creditoInconsistencias = Invoke-LocalHttp -Path "/api/admin/creditos/inconsistencias" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin creditos inconsistencias" ($creditoInconsistencias.Status -eq 200 -and $creditoInconsistencias.Body -match '"total"\s*:\s*[1-9]') "endpoint dedicado deve listar alertas locais"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin creditos inconsistencias" -Body $creditoInconsistencias.Body

  foreach ($method in @("POST", "PUT", "PATCH", "DELETE")) {
    $creditosEscrita = Invoke-LocalHttp -Path "/api/admin/creditos/consistencia" -ExpectedStatus 405 -Method $method -Body "{}" -Session $adminSession
    Add-Check "creditos sem metodo $method" ($creditosEscrita.Status -in @(400, 403, 404, 405)) "status obtido: $($creditosEscrita.Status); creditos nao deve possuir endpoint $method"
  }

  $pagamentoEfiId = "00000000-0000-4000-8000-000000000721"
  $pagamentosLista = Invoke-LocalHttp -Path "/api/admin/pagamentos?page=0&size=10" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin pagamentos lista" ($pagamentosLista.Status -eq 200 -and $pagamentosLista.Body -match '"somenteLeitura"\s*:\s*true' -and $pagamentosLista.Body -match 'EFI' -and $pagamentosLista.Body -match 'MERCADO_PAGO_LEGADO') "pagamentos sinteticos devem ser listados"
  Add-Check "admin pagamentos lista sem acao real" ($pagamentosLista.Body -notmatch 'copiaECole|qrCode|checkout|cobrancaRealDisponivel"\s*:\s*true') "lista nao deve expor checkout, QR Code ou cobranca real"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin pagamentos lista" -Body $pagamentosLista.Body

  $pagamentoDetalhe = Invoke-LocalHttp -Path "/api/admin/pagamentos/$pagamentoEfiId" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin pagamento detalhe efi" ($pagamentoDetalhe.Status -eq 200 -and $pagamentoDetalhe.Body -match '"provedorClassificado"\s*:\s*"EFI"' -and $pagamentoDetalhe.Body -match '"creditoVinculado"\s*:\s*true') "detalhe deve classificar Efi com credito vinculado"
  Add-Check "admin pagamento detalhe somente leitura" ($pagamentoDetalhe.Body -match '"payloadSensivelOculto"\s*:\s*true' -and $pagamentoDetalhe.Body -match '"pixEfiRealExecutado"\s*:\s*false' -and $pagamentoDetalhe.Body -match '"webhookRealProcessado"\s*:\s*false') "detalhe nao deve executar Pix/Efi ou webhook real"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin pagamento detalhe" -Body $pagamentoDetalhe.Body

  $pagamentoConsistencia = Invoke-LocalHttp -Path "/api/admin/pagamentos/consistencia" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin pagamentos consistencia" ($pagamentoConsistencia.Status -eq 200 -and $pagamentoConsistencia.Body -match 'PAGAMENTO_SEM_TXID' -and $pagamentoConsistencia.Body -match 'PAGAMENTO_APROVADO_SEM_CREDITO' -and $pagamentoConsistencia.Body -match 'CREDITO_SEM_PAGAMENTO') "inconsistencias sinteticas de pagamentos devem ser detectadas"
  Add-Check "admin pagamentos evidencias" ($pagamentoConsistencia.Body -match 'PAGAMENTO_MERCADO_PAGO_LEGADO' -and $pagamentoConsistencia.Body -match 'PAGAMENTO_PROVEDOR_DESCONHECIDO' -and $pagamentoConsistencia.Body -match 'STATUS_PAGAMENTO_INCONSISTENTE' -and $pagamentoConsistencia.Body -match 'EVENTO_WEBHOOK_DUPLICADO' -and $pagamentoConsistencia.Body -match 'PAGAMENTO_PAYLOAD_SENSIVEL_OCULTO') "evidencias e politicas de provedor devem ser sinalizadas"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin pagamentos consistencia" -Body $pagamentoConsistencia.Body

  $pagamentoInconsistencias = Invoke-LocalHttp -Path "/api/admin/pagamentos/inconsistencias" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin pagamentos inconsistencias" ($pagamentoInconsistencias.Status -eq 200 -and $pagamentoInconsistencias.Body -match '"total"\s*:\s*[1-9]') "endpoint dedicado deve listar alertas locais"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin pagamentos inconsistencias" -Body $pagamentoInconsistencias.Body

  foreach ($method in @("POST", "PUT", "PATCH", "DELETE")) {
    $pagamentosEscrita = Invoke-LocalHttp -Path "/api/admin/pagamentos/consistencia" -ExpectedStatus 405 -Method $method -Body "{}" -Session $adminSession
    Add-Check "pagamentos sem metodo $method" ($pagamentosEscrita.Status -in @(400, 403, 404, 405)) "status obtido: $($pagamentosEscrita.Status); pagamentos nao deve possuir endpoint $method"
  }

  $desempenhoAnuncioId = "00000000-0000-4000-8000-000000000501"
  $desempenhoSemMetricasId = "00000000-0000-4000-8000-000000000511"
  $desempenhoUsuarioId = "00000000-0000-4000-8000-000000000101"
  $desempenhoAnuncio = Invoke-LocalHttp -Path "/api/admin/desempenho/anuncios/$desempenhoAnuncioId" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin desempenho anuncio" ($desempenhoAnuncio.Status -eq 200 -and $desempenhoAnuncio.Body -match '"visualizacoesTotal"\s*:\s*220' -and $desempenhoAnuncio.Body -match '"cliquesWhatsappTotal"\s*:\s*32' -and $desempenhoAnuncio.Body -match '"taxaCliqueView"\s*:\s*0\.1455') "ADMIN deve ler prova de resultado sintetica"
  Add-Check "admin desempenho sem promessa" ($desempenhoAnuncio.Body -match '"promessaResultadoGarantido"\s*:\s*false' -and $desempenhoAnuncio.Body -match '"gratuitoLimitado"\s*:\s*false') "Premium nao deve prometer resultado nem limitar gratuito"
  Add-Check "admin desempenho comparativo premium" ($desempenhoAnuncio.Body -match '"visualizacoesComPremium"\s*:\s*180' -and $desempenhoAnuncio.Body -match '"visualizacoesOrganicas"\s*:\s*40') "comparativo deve separar organico e Premium sintetico"
  Add-Check "admin desempenho sem tracking externo" ($desempenhoAnuncio.Body -match '"trackingExternoExecutado"\s*:\s*false' -and -not ($desempenhoAnuncio.Body -match 'pixel|visitanteHash|ipHash|userAgentHash|refererHash|telefoneNormalizado|whatsappNormalizado|storageProvider|bucket|chaveObjeto|pagamentoId|saldoProjetado')) "endpoint deve ser local, sanitizado e sem pixel externo"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin desempenho anuncio" -Body $desempenhoAnuncio.Body

  $desempenhoDiario = Invoke-LocalHttp -Path "/api/admin/desempenho/anuncios/$desempenhoAnuncioId/diario" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin desempenho diario" ($desempenhoDiario.Status -eq 200 -and $desempenhoDiario.Body -match '"premiumAtivo"\s*:\s*true' -and $desempenhoDiario.Body -match '"premiumAtivo"\s*:\s*false') "serie diaria deve evidenciar dias organicos e Premium"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin desempenho diario" -Body $desempenhoDiario.Body

  $desempenhoOrigens = Invoke-LocalHttp -Path "/api/admin/desempenho/anuncios/$desempenhoAnuncioId/origens" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin desempenho origens" ($desempenhoOrigens.Status -eq 200 -and $desempenhoOrigens.Body -match 'Cidade de demonstra' -and $desempenhoOrigens.Body -match 'Bairro de demonstra') "origens devem usar cidade/bairro sanitizados"
  Add-Check "admin desempenho origens sem bruto" (-not ($desempenhoOrigens.Body -match 'hash|ipHash|userAgent|referer|visitante')) "origens nao devem expor identificador bruto"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin desempenho origens" -Body $desempenhoOrigens.Body

  $desempenhoAnunciante = Invoke-LocalHttp -Path "/api/admin/desempenho/anunciantes/$desempenhoUsuarioId" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin desempenho anunciante" ($desempenhoAnunciante.Status -eq 200 -and $desempenhoAnunciante.Body -match '"anunciosTotal"\s*:\s*[1-9]' -and $desempenhoAnunciante.Body -match '"endpointAnuncianteRealDisponivel"\s*:\s*false') "visao por anunciante deve ser futura/read-only"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin desempenho anunciante" -Body $desempenhoAnunciante.Body

  $desempenhoResumo = Invoke-LocalHttp -Path "/api/admin/desempenho/resumo" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin desempenho resumo" ($desempenhoResumo.Status -eq 200 -and $desempenhoResumo.Body -match '"somenteLeitura"\s*:\s*true' -and $desempenhoResumo.Body -match '"trackingExternoExecutado"\s*:\s*false') "resumo deve ser agregado e local"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin desempenho resumo" -Body $desempenhoResumo.Body

  $desempenhoVazio = Invoke-LocalHttp -Path "/api/admin/desempenho/anuncios/$desempenhoSemMetricasId" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin desempenho fallback vazio" ($desempenhoVazio.Status -eq 200 -and $desempenhoVazio.Body -match '"visualizacoesTotal"\s*:\s*0' -and $desempenhoVazio.Body -match '"cliquesWhatsappTotal"\s*:\s*0') "anuncio sem metrica deve retornar estado vazio estavel"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin desempenho vazio" -Body $desempenhoVazio.Body

  foreach ($method in @("POST", "PUT", "PATCH", "DELETE")) {
    $desempenhoEscrita = Invoke-LocalHttp -Path "/api/admin/desempenho/resumo" -ExpectedStatus 405 -Method $method -Body "{}" -Session $adminSession
    Add-Check "desempenho sem metodo $method" ($desempenhoEscrita.Status -in @(400, 403, 404, 405)) "status obtido: $($desempenhoEscrita.Status); desempenho deve ser read-only"
  }

  $revisaoReprovarId = "00000000-0000-4000-8000-000000000802"
  $revisaoFinalizadaId = "00000000-0000-4000-8000-000000000803"
  $revisaoModeradorId = "00000000-0000-4000-8000-000000000804"
  $revisaoAjusteId = "00000000-0000-4000-8000-000000000805"
  $anuncioRemeterId = "00000000-0000-4000-8000-000000000508"
  $anuncioRemeterConflitoId = "00000000-0000-4000-8000-000000000509"
  $midiaReprovarId = "00000000-0000-4000-8000-000000000707"
  $midiaFinalizadaId = "00000000-0000-4000-8000-000000000709"
  $midiaModeradorId = "00000000-0000-4000-8000-000000000711"
  $motivoMascaravel = ("motivo sintetico " + "ana" + "@example.invalid " + "+" + "5511" + "9999" + "9999 " + "123" + ".456" + ".789" + "-09")

  $decisaoSemSessao = Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoId/decidir" -ExpectedStatus 401 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "APROVAR")
  Add-Check "decisao revisao sem sessao 401" ($decisaoSemSessao.Status -eq 401) "acao moderatoria exige sessao"
  $remeterSemSessao = Invoke-LocalHttp -Path "/api/admin/anuncios/$anuncioRemeterId/remeter-revisao" -ExpectedStatus 401 -Method "POST" -Body (New-RemeterRevisaoBody)
  Add-Check "remeter revisao sem sessao 401" ($remeterSemSessao.Status -eq 401) "remeter revisao exige sessao"
  $outboxSemSessao = Invoke-LocalHttp -Path "/api/admin/outbox" -ExpectedStatus 401 -Method "GET"
  Add-Check "outbox sem sessao 401" ($outboxSemSessao.Status -eq 401) "outbox admin exige sessao"
  $decisaoInvalida = Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoId/decidir" -ExpectedStatus 400 -Method "POST" -Body '{"visibilidadeMidia":"LIVRE"}' -Session $adminSession
  Add-Check "decisao invalida retorna 400" ($decisaoInvalida.Status -eq 400) "decisao ausente deve ser rejeitada"
  $reprovarRevisaoSemMotivo = Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoReprovarId/decidir" -ExpectedStatus 400 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "REPROVAR" -SemMotivo) -Session $adminSession
  Add-Check "reprovar revisao sem motivo retorna 400" ($reprovarRevisaoSemMotivo.Status -eq 400) "REPROVAR exige motivo valido"
  $ajusteSemMotivo = Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoAjusteId/decidir" -ExpectedStatus 400 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "SOLICITAR_AJUSTE" -SemMotivo) -Session $adminSession
  Add-Check "solicitar ajuste sem motivo retorna 400" ($ajusteSemMotivo.Status -eq 400) "SOLICITAR_AJUSTE exige motivo valido"

  $solicitarAjuste = Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoAjusteId/decidir" -ExpectedStatus 200 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "SOLICITAR_AJUSTE" -Motivo $motivoMascaravel) -Session $adminSession
  Add-Check "admin solicita ajuste em revisao aberta" ($solicitarAjuste.Status -eq 200 -and $solicitarAjuste.Body -match '"decisao"\s*:\s*"SOLICITAR_AJUSTE"' -and $solicitarAjuste.Body -match '"status"\s*:\s*"ABERTA"') "solicitacao de ajuste deve ser registrada sem finalizar revisao"
  Add-Check "admin solicita ajuste sem efeito externo" ($solicitarAjuste.Body -match '"emailRealEnviado"\s*:\s*false' -and $solicitarAjuste.Body -match '"hardDeleteExecutado"\s*:\s*false') "sem e-mail real ou hard delete"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin solicita ajuste" -Body $solicitarAjuste.Body
  $solicitarAjusteDuplicado = Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoAjusteId/decidir" -ExpectedStatus 409 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "SOLICITAR_AJUSTE" -Motivo "ajuste repetido local") -Session $adminSession
  Add-Check "solicitar ajuste duplicado retorna 409" ($solicitarAjusteDuplicado.Status -eq 409) "duplicidade usa conflito explicito enquanto nao ha idempotencia real"

  $aprovarAposAjuste = Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoAjusteId/decidir" -ExpectedStatus 200 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "APROVAR" -Motivo "decisao final apos ajuste local") -Session $adminSession
  Add-Check "admin aprova revisao apos solicitar ajuste" ($aprovarAposAjuste.Status -eq 200 -and $aprovarAposAjuste.Body -match '"status"\s*:\s*"APROVADA"') "SOLICITAR_AJUSTE nao deve consumir decisao final"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin aprova apos ajuste" -Body $aprovarAposAjuste.Body

  $remeterSemMotivo = Invoke-LocalHttp -Path "/api/admin/anuncios/$anuncioRemeterId/remeter-revisao" -ExpectedStatus 400 -Method "POST" -Body (New-RemeterRevisaoBody -SemMotivo) -Session $adminSession
  Add-Check "remeter revisao sem motivo retorna 400" ($remeterSemMotivo.Status -eq 400) "remeter revisao exige motivo valido"
  $remeterRevisao = Invoke-LocalHttp -Path "/api/admin/anuncios/$anuncioRemeterId/remeter-revisao" -ExpectedStatus 200 -Method "POST" -Body (New-RemeterRevisaoBody) -Session $adminSession
  Add-Check "admin remete anuncio para revisao" ($remeterRevisao.Status -eq 200 -and $remeterRevisao.Body -match '"decisao"\s*:\s*"REMETER_REVISAO"' -and $remeterRevisao.Body -match '"status"\s*:\s*"PENDENTE_REVISAO"') "remeter deve criar revisao local e atualizar status"
  Add-Check "admin remete revisao sem efeito externo" ($remeterRevisao.Body -match '"emailRealEnviado"\s*:\s*false' -and $remeterRevisao.Body -match '"hardDeleteExecutado"\s*:\s*false') "sem e-mail real ou hard delete"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin remete revisao" -Body $remeterRevisao.Body
  $remeterRevisaoDuplicada = Invoke-LocalHttp -Path "/api/admin/anuncios/$anuncioRemeterConflitoId/remeter-revisao" -ExpectedStatus 409 -Method "POST" -Body (New-RemeterRevisaoBody) -Session $adminSession
  Add-Check "remeter revisao duplicada retorna 409" ($remeterRevisaoDuplicada.Status -eq 409) "anuncio com revisao aberta nao pode ser remetido novamente"

  $aprovarRevisao = Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoId/decidir" -ExpectedStatus 200 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "APROVAR") -Session $adminSession
  Add-Check "admin aprova revisao aberta" ($aprovarRevisao.Status -eq 200 -and $aprovarRevisao.Body -match '"status"\s*:\s*"APROVADA"') "revisao aberta deve ser aprovada"
  Add-Check "admin aprova revisao com auditoria" ($aprovarRevisao.Body -match '"auditoriaRegistrada"\s*:\s*true') "acao deve registrar auditoria"
  Add-Check "admin aprova revisao sem efeito externo" ($aprovarRevisao.Body -match '"emailRealEnviado"\s*:\s*false' -and $aprovarRevisao.Body -match '"hardDeleteExecutado"\s*:\s*false') "sem e-mail real ou hard delete"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin aprova revisao" -Body $aprovarRevisao.Body

  $reprovarRevisao = Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoReprovarId/decidir" -ExpectedStatus 200 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "REPROVAR" -Motivo $motivoMascaravel) -Session $adminSession
  Add-Check "admin reprova revisao aberta" ($reprovarRevisao.Status -eq 200 -and $reprovarRevisao.Body -match '"status"\s*:\s*"REJEITADA"' -and $reprovarRevisao.Body -match '"visibilidadeMidia"\s*:\s*null') "rejeicao nao cria terceira visibilidade"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin reprova revisao" -Body $reprovarRevisao.Body

  $outboxLista = Invoke-LocalHttp -Path "/api/admin/outbox?page=0&size=10&status=PENDENTE" -ExpectedStatus 200 -Method "GET" -Session $adminSession
  Add-Check "admin lista outbox pendente" ($outboxLista.Status -eq 200 -and $outboxLista.Body -match 'MODERACAO_SOLICITAR_AJUSTE' -and $outboxLista.Body -match 'MODERACAO_REPROVADA' -and $outboxLista.Body -match 'ANUNCIO_REMETIDO_REVISAO') "outbox deve listar eventos locais de moderacao"
  Add-Check "admin outbox sem envio externo" ($outboxLista.Body -match '"envioExternoExecutado"\s*:\s*false' -and -not ($outboxLista.Body -match '"envioExternoExecutado"\s*:\s*true')) "outbox e apenas pendencia local"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin outbox lista" -Body $outboxLista.Body
  $outboxId = $null
  try {
    $outboxJson = $outboxLista.Body | ConvertFrom-Json
    if ($outboxJson.itens.Count -gt 0) {
      $outboxId = [string]$outboxJson.itens[0].id
    }
  } catch {
    $outboxId = $null
  }
  Add-Check "admin outbox contem item detalhavel" (-not [string]::IsNullOrWhiteSpace($outboxId)) "listagem deve retornar ao menos um evento"
  if (-not [string]::IsNullOrWhiteSpace($outboxId)) {
    $outboxDetalhe = Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId" -ExpectedStatus 200 -Method "GET" -Session $adminSession
    Add-Check "admin detalha outbox" ($outboxDetalhe.Status -eq 200 -and $outboxDetalhe.Body -match '"somenteLeitura"\s*:\s*true' -and $outboxDetalhe.Body -match '"dadosSanitizados"\s*:') "detalhe deve retornar apenas dados sanitizados"
    Add-Check "admin outbox detalhe sem bruto sensivel" (-not ($outboxDetalhe.Body -match 'ana@example\.invalid|\+5511999999999|123\.456\.789-09|bucket|chaveObjeto|sha256|token|idempotency|Pix copia|qrcode|qrCode')) "detalhe nao deve expor dado bruto sensivel"
    Assert-NoSensitiveAdminReadonlyData -Nome "admin outbox detalhe" -Body $outboxDetalhe.Body
    $statusAntesPreview = $null
    try {
      $statusAntesPreview = [string](($outboxDetalhe.Body | ConvertFrom-Json).status)
    } catch {
      $statusAntesPreview = $null
    }
    $outboxPreviewSemSessao = Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId/preview" -ExpectedStatus 401 -Method "GET"
    Add-Check "outbox preview sem sessao 401" ($outboxPreviewSemSessao.Status -eq 401) "preview exige sessao admin"
    $outboxPreview = Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId/preview" -ExpectedStatus 200 -Method "GET" -Session $adminSession
    Add-Check "admin renderiza preview outbox" ($outboxPreview.Status -eq 200 -and $outboxPreview.Body -match '"somentePreview"\s*:\s*true' -and $outboxPreview.Body -match '"envioExternoExecutado"\s*:\s*false') "preview deve ser local e sem envio externo"
    Add-Check "admin preview outbox tem template sanitizado" ($outboxPreview.Body -match '"assuntoSanitizado"\s*:' -and $outboxPreview.Body -match '"corpoSanitizado"\s*:' -and $outboxPreview.Body -match 'nenhuma comunicacao foi enviada|Nenhum envio externo foi executado') "preview deve retornar assunto/corpo sanitizados"
    Add-Check "admin preview outbox sem bruto sensivel" (-not ($outboxPreview.Body -match 'ana@example\.invalid|\+5511999999999|123\.456\.789-09|bucket-privado|chaveObjeto|sha256|idempotency|Pix copia|qrcode|qrCode|payloadJson')) "preview nao deve expor payload bruto"
    Assert-NoSensitiveAdminReadonlyData -Nome "admin preview outbox" -Body $outboxPreview.Body
    $outboxDetalheDepoisPreview = Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId" -ExpectedStatus 200 -Method "GET" -Session $adminSession
    $statusDepoisPreview = $null
    try {
      $statusDepoisPreview = [string](($outboxDetalheDepoisPreview.Body | ConvertFrom-Json).status)
    } catch {
      $statusDepoisPreview = $null
    }
    Add-Check "preview nao altera status do outbox" (($outboxDetalheDepoisPreview.Status -eq 200) -and $statusAntesPreview -eq $statusDepoisPreview -and $statusDepoisPreview -ne "PROCESSADO") "status antes=$statusAntesPreview depois=$statusDepoisPreview"
    $outboxSimulacaoSemSessao = Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId/simular-processamento-local" -ExpectedStatus 401 -Method "POST" -Body "{}"
    Add-Check "outbox simulacao sem sessao 401" ($outboxSimulacaoSemSessao.Status -eq 401) "simulacao local exige sessao ADMIN"
    $simulacaoBody = (@{
      observacao = "simulacao local sem envio externo " + $motivoMascaravel
      requestIdCliente = "reservado-sem-idempotencia-real"
    } | ConvertTo-Json -Compress)
    $outboxSimulacao = Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId/simular-processamento-local" -ExpectedStatus 200 -Method "POST" -Body $simulacaoBody -Session $adminSession
    Add-Check "admin simula outbox local" ($outboxSimulacao.Status -eq 200 -and $outboxSimulacao.Body -match '"statusAntes"\s*:\s*"PENDENTE"' -and $outboxSimulacao.Body -match '"statusDepois"\s*:\s*"PROCESSADO"') "simulacao deve processar localmente status existente"
    Add-Check "admin simulacao outbox sem envio externo" ($outboxSimulacao.Body -match '"envioExternoExecutado"\s*:\s*false' -and -not ($outboxSimulacao.Body -match '"envioExternoExecutado"\s*:\s*true')) "simulacao nao envia comunicacao real"
    Add-Check "admin simulacao outbox com auditoria" ($outboxSimulacao.Body -match '"auditoriaRegistrada"\s*:\s*true') "simulacao deve registrar auditoria"
    Assert-NoSensitiveAdminReadonlyData -Nome "admin simulacao outbox" -Body $outboxSimulacao.Body
    $outboxDetalheProcessado = Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId" -ExpectedStatus 200 -Method "GET" -Session $adminSession
    Add-Check "admin detalhe outbox processado local" ($outboxDetalheProcessado.Status -eq 200 -and $outboxDetalheProcessado.Body -match '"status"\s*:\s*"PROCESSADO"') "status deve refletir simulacao local"
    Assert-NoSensitiveAdminReadonlyData -Nome "admin outbox processado detalhe" -Body $outboxDetalheProcessado.Body
    $outboxSimulacaoDuplicada = Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId/simular-processamento-local" -ExpectedStatus 409 -Method "POST" -Body $simulacaoBody -Session $adminSession
    Add-Check "outbox simulacao duplicada 409" ($outboxSimulacaoDuplicada.Status -eq 409) "outbox fora de PENDENTE nao deve ser simulado novamente"
    $outboxProcessadoLista = Invoke-LocalHttp -Path "/api/admin/outbox?page=0&size=5&status=PROCESSADO" -ExpectedStatus 200 -Method "GET" -Session $adminSession
    Add-Check "admin lista outbox processado local" ($outboxProcessadoLista.Status -eq 200 -and $outboxProcessadoLista.Body -match $outboxId) "listagem deve permitir auditar simulacao local"
  }
  foreach ($method in @("POST", "PUT", "PATCH", "DELETE")) {
    $outboxEscrita = Invoke-LocalHttp -Path "/api/admin/outbox" -ExpectedStatus 405 -Method $method -Body "{}" -Session $adminSession
    Add-Check "outbox sem metodo $method" ($outboxEscrita.Status -in @(400, 403, 404, 405)) "status obtido: $($outboxEscrita.Status); outbox nao deve possuir endpoint $method"
  }
  if (-not [string]::IsNullOrWhiteSpace($outboxId)) {
    $previewPost = Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId/preview" -ExpectedStatus 405 -Method "POST" -Body "{}" -Session $adminSession
    Add-Check "outbox preview sem post de envio" ($previewPost.Status -in @(400, 403, 404, 405)) "preview nao deve possuir endpoint de envio"
  }

  $revisaoFinalizada = Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoFinalizadaId/decidir" -ExpectedStatus 409 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "APROVAR") -Session $adminSession
  Add-Check "revisao finalizada retorna 409" ($revisaoFinalizada.Status -eq 409) "revisao finalizada nao pode ser decidida novamente"
  $revisaoRepetida = Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoId/decidir" -ExpectedStatus 409 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "APROVAR") -Session $adminSession
  Add-Check "revisao ja decidida retorna 409" ($revisaoRepetida.Status -eq 409) "decisao deve ser unica por revisao"

  $aprovarMidia = Invoke-LocalHttp -Path "/api/admin/midias/$midiaId/decidir" -ExpectedStatus 200 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "APROVAR" -Visibilidade "LIVRE") -Session $adminSession
  Add-Check "admin aprova foto com visibilidade individual" ($aprovarMidia.Status -eq 200 -and $aprovarMidia.Body -match '"status"\s*:\s*"PUBLICAVEL"' -and $aprovarMidia.Body -match '"visibilidadeMidia"\s*:\s*"LIVRE"') "foto pendente deve exigir visibilidade"
  Add-Check "admin aprova midia com auditoria" ($aprovarMidia.Body -match '"auditoriaRegistrada"\s*:\s*true') "acao de midia deve registrar auditoria"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin aprova midia" -Body $aprovarMidia.Body

  $reprovarMidiaSemMotivo = Invoke-LocalHttp -Path "/api/admin/midias/$midiaReprovarId/decidir" -ExpectedStatus 400 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "REPROVAR" -SemMotivo) -Session $adminSession
  Add-Check "reprovar midia sem motivo retorna 400" ($reprovarMidiaSemMotivo.Status -eq 400) "REPROVAR exige motivo valido"
  $reprovarMidia = Invoke-LocalHttp -Path "/api/admin/midias/$midiaReprovarId/decidir" -ExpectedStatus 200 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "REPROVAR" -Motivo $motivoMascaravel) -Session $adminSession
  Add-Check "admin reprova midia pendente" ($reprovarMidia.Status -eq 200 -and $reprovarMidia.Body -match '"status"\s*:\s*"REJEITADA"' -and $reprovarMidia.Body -match '"visibilidadeMidia"\s*:\s*null') "rejeicao permanece status de moderacao"
  Assert-NoSensitiveAdminReadonlyData -Nome "admin reprova midia" -Body $reprovarMidia.Body

  $midiaFinalizada = Invoke-LocalHttp -Path "/api/admin/midias/$midiaFinalizadaId/decidir" -ExpectedStatus 200 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "APROVAR" -Visibilidade "RESTRITA_18") -Session $adminSession
  Add-Check "staff altera visibilidade de foto aprovada" ($midiaFinalizada.Status -eq 200 -and $midiaFinalizada.Body -match '"visibilidadeMidia"\s*:\s*"RESTRITA_18"') "alteracao posterior deve permanecer individual e auditada"
  $ajusteMidiaPendente = Invoke-LocalHttp -Path "/api/admin/midias/$midiaModeradorId/decidir" -ExpectedStatus 200 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "SOLICITAR_AJUSTE" -Motivo "ajuste local de midia") -Session $adminSession
  Add-Check "solicitar ajuste de midia preserva visibilidade" ($ajusteMidiaPendente.Status -eq 200 -and $ajusteMidiaPendente.Body -match '"status"\s*:\s*"AJUSTE_SOLICITADO"') "ajuste usa status de moderacao"

  $moderadorSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
  $moderadorLogin = Invoke-LocalHttp -Path "/api/admin/auth/login" -ExpectedStatus 200 -Method "POST" -Body (New-AdminLoginBody -Login "moderador.local@example.invalid") -Session $moderadorSession
  Add-Check "moderador login sintetico status 200" ($moderadorLogin.Status -eq 200) "login moderador local sintetico deve autenticar"
  Add-Check "moderador acessa anuncios" ((Invoke-LocalHttp -Path "/api/admin/anuncios/resumo" -ExpectedStatus 200 -Method "GET" -Session $moderadorSession).Status -eq 200) "MODERADOR deve ler anuncios"
  Add-Check "moderador acessa anuncios detalhados" ((Invoke-LocalHttp -Path "/api/admin/anuncios?page=0&size=2" -ExpectedStatus 200 -Method "GET" -Session $moderadorSession).Status -eq 200) "MODERADOR deve listar anuncios"
  Add-Check "moderador acessa premium readonly" ((Invoke-LocalHttp -Path "/api/admin/premium/anuncios/$anuncioPremiumId" -ExpectedStatus 200 -Method "GET" -Session $moderadorSession).Status -eq 200) "MODERADOR deve ler status premium sanitizado"
  Add-Check "moderador acessa desempenho de anuncio" ((Invoke-LocalHttp -Path "/api/admin/desempenho/anuncios/$desempenhoAnuncioId" -ExpectedStatus 200 -Method "GET" -Session $moderadorSession).Status -eq 200) "MODERADOR pode ler desempenho basico de anuncio"
  Add-Check "moderador sem desempenho agregado comercial" ((Invoke-LocalHttp -Path "/api/admin/desempenho/resumo" -ExpectedStatus 403 -Method "GET" -Session $moderadorSession).Status -eq 403) "MODERADOR nao deve acessar resumo comercial agregado"
  Add-Check "moderador sem creditos admin" ((Invoke-LocalHttp -Path "/api/admin/creditos/consistencia" -ExpectedStatus 403 -Method "GET" -Session $moderadorSession).Status -eq 403) "MODERADOR nao deve acessar creditos"
  Add-Check "moderador sem pagamentos admin" ((Invoke-LocalHttp -Path "/api/admin/pagamentos/consistencia" -ExpectedStatus 403 -Method "GET" -Session $moderadorSession).Status -eq 403) "MODERADOR nao deve acessar pagamentos"
  Add-Check "moderador acessa midias do anuncio" ((Invoke-LocalHttp -Path "/api/admin/anuncios/$anuncioId/midias" -ExpectedStatus 200 -Method "GET" -Session $moderadorSession).Status -eq 200) "MODERADOR deve ler midias do anuncio"
  Add-Check "moderador acessa midias detalhadas" ((Invoke-LocalHttp -Path "/api/admin/midias/$midiaId" -ExpectedStatus 200 -Method "GET" -Session $moderadorSession).Status -eq 200) "MODERADOR deve ler midia"
  Add-Check "moderador acessa revisao detalhada" ((Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoId" -ExpectedStatus 200 -Method "GET" -Session $moderadorSession).Status -eq 200) "MODERADOR deve ler revisao"
  Add-Check "moderador acessa moderacao" ((Invoke-LocalHttp -Path "/api/admin/moderacao/resumo" -ExpectedStatus 200 -Method "GET" -Session $moderadorSession).Status -eq 200) "MODERADOR deve ler moderacao"
  $moderadorOutbox = Invoke-LocalHttp -Path "/api/admin/outbox?status=PENDENTE&tipoEvento=MODERACAO_SOLICITAR_AJUSTE" -ExpectedStatus 200 -Method "GET" -Session $moderadorSession
  Add-Check "moderador acessa outbox de moderacao" ($moderadorOutbox.Status -eq 200 -and $moderadorOutbox.Body -match 'MODERACAO_SOLICITAR_AJUSTE') "MODERADOR deve ler outbox de moderacao"
  Assert-NoSensitiveAdminReadonlyData -Nome "moderador outbox" -Body $moderadorOutbox.Body
  if (-not [string]::IsNullOrWhiteSpace($outboxId)) {
    Add-Check "moderador nao simula outbox" ((Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId/simular-processamento-local" -ExpectedStatus 403 -Method "POST" -Body "{}" -Session $moderadorSession).Status -eq 403) "MODERADOR nao deve simular processamento"
    $moderadorPreview = Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId/preview" -ExpectedStatus 200 -Method "GET" -Session $moderadorSession
    Add-Check "moderador acessa preview outbox moderacao" ($moderadorPreview.Status -eq 200 -and $moderadorPreview.Body -match '"somentePreview"\s*:\s*true') "MODERADOR deve ver preview de moderacao"
    Assert-NoSensitiveAdminReadonlyData -Nome "moderador preview outbox" -Body $moderadorPreview.Body
  }
  Add-Check "moderador acessa midias" ((Invoke-LocalHttp -Path "/api/admin/midias/resumo" -ExpectedStatus 200 -Method "GET" -Session $moderadorSession).Status -eq 200) "MODERADOR deve ler midias"
  $moderadorDecideRevisao = Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoModeradorId/decidir" -ExpectedStatus 200 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "APROVAR") -Session $moderadorSession
  Add-Check "moderador aprova revisao aberta" ($moderadorDecideRevisao.Status -eq 200 -and $moderadorDecideRevisao.Body -match '"status"\s*:\s*"APROVADA"') "MODERADOR deve decidir revisao"
  Assert-NoSensitiveAdminReadonlyData -Nome "moderador aprova revisao" -Body $moderadorDecideRevisao.Body
  $moderadorDecideMidia = Invoke-LocalHttp -Path "/api/admin/midias/$midiaModeradorId/decidir" -ExpectedStatus 200 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "REPROVAR" -Motivo "decisao final apos ajuste local") -Session $moderadorSession
  Add-Check "moderador decide midia apos ajuste" ($moderadorDecideMidia.Status -eq 200 -and $moderadorDecideMidia.Body -match '"status"\s*:\s*"REJEITADA"') "alteracao posterior autorizada preserva fluxo individual"
  Assert-NoSensitiveAdminReadonlyData -Nome "moderador reprova midia" -Body $moderadorDecideMidia.Body
  $moderadorVisaoGeral = Invoke-LocalHttp -Path "/api/admin/visao-geral" -ExpectedStatus 200 -Method "GET" -Session $moderadorSession
  Add-Check "moderador acessa visao geral limitada" ($moderadorVisaoGeral.Status -eq 200) "MODERADOR deve ler visao geral"
  Add-Check "moderador visao geral sem metricas/sistema" ($moderadorVisaoGeral.Body -match '"metricas"\s*:\s*null' -and $moderadorVisaoGeral.Body -match '"sistema"\s*:\s*null') "MODERADOR nao deve receber metricas/sistema"
  Add-Check "moderador sem sistema admin" ((Invoke-LocalHttp -Path "/api/admin/sistema/status" -ExpectedStatus 403 -Method "GET" -Session $moderadorSession).Status -eq 403) "MODERADOR nao deve ver status restrito"

  $comercialSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
  $comercialLogin = Invoke-LocalHttp -Path "/api/admin/auth/login" -ExpectedStatus 200 -Method "POST" -Body (New-AdminLoginBody -Login "comercial.local@example.invalid") -Session $comercialSession
  Add-Check "comercial login sintetico status 200" ($comercialLogin.Status -eq 200) "login comercial local sintetico deve autenticar"
  $comercialVisaoGeral = Invoke-LocalHttp -Path "/api/admin/visao-geral" -ExpectedStatus 200 -Method "GET" -Session $comercialSession
  Add-Check "comercial acessa visao geral" ($comercialVisaoGeral.Status -eq 200) "COMERCIAL deve ler visao geral"
  Add-Check "comercial visao geral limitada" ($comercialVisaoGeral.Body -match '"moderacao"\s*:\s*null' -and $comercialVisaoGeral.Body -match '"midias"\s*:\s*null' -and $comercialVisaoGeral.Body -match '"sistema"\s*:\s*null') "COMERCIAL nao deve receber moderacao/midia/sistema"
  Add-Check "comercial acessa metricas" ((Invoke-LocalHttp -Path "/api/admin/metricas/resumo" -ExpectedStatus 200 -Method "GET" -Session $comercialSession).Status -eq 200) "COMERCIAL deve ler metricas agregadas"
  Add-Check "comercial acessa premium readonly" ((Invoke-LocalHttp -Path "/api/admin/premium/vencendo" -ExpectedStatus 200 -Method "GET" -Session $comercialSession).Status -eq 200) "COMERCIAL deve ler premium sem dado financeiro sensivel"
  Add-Check "comercial acessa desempenho resumo" ((Invoke-LocalHttp -Path "/api/admin/desempenho/resumo" -ExpectedStatus 200 -Method "GET" -Session $comercialSession).Status -eq 200) "COMERCIAL deve ler desempenho agregado"
  Add-Check "comercial acessa desempenho anunciante" ((Invoke-LocalHttp -Path "/api/admin/desempenho/anunciantes/$desempenhoUsuarioId" -ExpectedStatus 200 -Method "GET" -Session $comercialSession).Status -eq 200) "COMERCIAL deve ler desempenho por anunciante sanitizado"
  Add-Check "comercial sem creditos admin" ((Invoke-LocalHttp -Path "/api/admin/creditos/consistencia" -ExpectedStatus 403 -Method "GET" -Session $comercialSession).Status -eq 403) "COMERCIAL nao deve acessar ledger de creditos"
  Add-Check "comercial sem pagamentos admin" ((Invoke-LocalHttp -Path "/api/admin/pagamentos/consistencia" -ExpectedStatus 403 -Method "GET" -Session $comercialSession).Status -eq 403) "COMERCIAL nao deve acessar pagamentos"
  $comercialAnuncios = Invoke-LocalHttp -Path "/api/admin/anuncios?page=0&size=2" -ExpectedStatus 200 -Method "GET" -Session $comercialSession
  Add-Check "comercial acessa anuncios detalhados limitados" ($comercialAnuncios.Status -eq 200 -and $comercialAnuncios.Body -match '"comercialLimitado"\s*:\s*true') "COMERCIAL deve ler anuncios em versao limitada"
  $comercialDetalhe = Invoke-LocalHttp -Path "/api/admin/anuncios/$anuncioId" -ExpectedStatus 200 -Method "GET" -Session $comercialSession
  Add-Check "comercial detalhe anuncio limitado" ($comercialDetalhe.Status -eq 200 -and $comercialDetalhe.Body -match '"descricaoResumo"\s*:\s*null' -and $comercialDetalhe.Body -match '"revisoesTotal"\s*:\s*null') "COMERCIAL nao recebe descricao/revisao detalhada"
  Add-Check "comercial sem midias do anuncio" ((Invoke-LocalHttp -Path "/api/admin/anuncios/$anuncioId/midias" -ExpectedStatus 403 -Method "GET" -Session $comercialSession).Status -eq 403) "COMERCIAL nao deve acessar midias"
  Add-Check "comercial sem midia detalhada" ((Invoke-LocalHttp -Path "/api/admin/midias/$midiaId" -ExpectedStatus 403 -Method "GET" -Session $comercialSession).Status -eq 403) "COMERCIAL nao deve acessar midia"
  Add-Check "comercial sem revisao detalhada" ((Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoId" -ExpectedStatus 403 -Method "GET" -Session $comercialSession).Status -eq 403) "COMERCIAL nao deve acessar revisao"
  Add-Check "comercial sem outbox" ((Invoke-LocalHttp -Path "/api/admin/outbox" -ExpectedStatus 403 -Method "GET" -Session $comercialSession).Status -eq 403) "COMERCIAL nao deve acessar outbox de moderacao"
  if (-not [string]::IsNullOrWhiteSpace($outboxId)) {
    Add-Check "comercial nao simula outbox" ((Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId/simular-processamento-local" -ExpectedStatus 403 -Method "POST" -Body "{}" -Session $comercialSession).Status -eq 403) "COMERCIAL nao deve simular processamento"
    Add-Check "comercial nao acessa preview outbox" ((Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId/preview" -ExpectedStatus 403 -Method "GET" -Session $comercialSession).Status -eq 403) "COMERCIAL nao deve ver preview de outbox"
  }
  Add-Check "comercial nao decide revisao" ((Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoFinalizadaId/decidir" -ExpectedStatus 403 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "APROVAR") -Session $comercialSession).Status -eq 403) "COMERCIAL nao deve executar acao moderatoria"
  Add-Check "comercial nao remete revisao" ((Invoke-LocalHttp -Path "/api/admin/anuncios/$anuncioRemeterConflitoId/remeter-revisao" -ExpectedStatus 403 -Method "POST" -Body (New-RemeterRevisaoBody) -Session $comercialSession).Status -eq 403) "COMERCIAL nao deve remeter revisao"
  Add-Check "comercial nao decide midia" ((Invoke-LocalHttp -Path "/api/admin/midias/$midiaFinalizadaId/decidir" -ExpectedStatus 403 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "APROVAR") -Session $comercialSession).Status -eq 403) "COMERCIAL nao deve executar acao de midia"
  Add-Check "comercial sem moderacao" ((Invoke-LocalHttp -Path "/api/admin/moderacao/resumo" -ExpectedStatus 403 -Method "GET" -Session $comercialSession).Status -eq 403) "COMERCIAL nao deve ver moderacao"
  Add-Check "comercial sem midias" ((Invoke-LocalHttp -Path "/api/admin/midias/resumo" -ExpectedStatus 403 -Method "GET" -Session $comercialSession).Status -eq 403) "COMERCIAL nao deve ver midias"

  $usuarioSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
  $usuarioLogin = Invoke-LocalHttp -Path "/api/admin/auth/login" -ExpectedStatus 200 -Method "POST" -Body (New-AdminLoginBody -Login "usuario.local@example.invalid") -Session $usuarioSession
  Add-Check "usuario login sintetico status 200" ($usuarioLogin.Status -eq 200) "login usuario local sintetico deve autenticar"
  Add-Check "usuario sem acesso admin readonly" ((Invoke-LocalHttp -Path "/api/admin/visao-geral" -ExpectedStatus 403 -Method "GET" -Session $usuarioSession).Status -eq 403) "USUARIO nao deve acessar admin"
  Add-Check "usuario sem resumo anuncios admin" ((Invoke-LocalHttp -Path "/api/admin/anuncios/resumo" -ExpectedStatus 403 -Method "GET" -Session $usuarioSession).Status -eq 403) "USUARIO nao deve acessar resumo admin"
  Add-Check "usuario sem anuncios detalhados admin" ((Invoke-LocalHttp -Path "/api/admin/anuncios" -ExpectedStatus 403 -Method "GET" -Session $usuarioSession).Status -eq 403) "USUARIO nao deve acessar admin detalhado"
  Add-Check "usuario sem premium admin" ((Invoke-LocalHttp -Path "/api/admin/premium/consistencia" -ExpectedStatus 403 -Method "GET" -Session $usuarioSession).Status -eq 403) "USUARIO nao deve acessar premium admin"
  Add-Check "usuario sem desempenho admin" ((Invoke-LocalHttp -Path "/api/admin/desempenho/resumo" -ExpectedStatus 403 -Method "GET" -Session $usuarioSession).Status -eq 403) "USUARIO nao deve acessar desempenho admin"
  Add-Check "usuario sem creditos admin" ((Invoke-LocalHttp -Path "/api/admin/creditos/consistencia" -ExpectedStatus 403 -Method "GET" -Session $usuarioSession).Status -eq 403) "USUARIO nao deve acessar creditos admin"
  Add-Check "usuario sem pagamentos admin" ((Invoke-LocalHttp -Path "/api/admin/pagamentos/consistencia" -ExpectedStatus 403 -Method "GET" -Session $usuarioSession).Status -eq 403) "USUARIO nao deve acessar pagamentos admin"
  Add-Check "usuario sem midia detalhada admin" ((Invoke-LocalHttp -Path "/api/admin/midias/$midiaId" -ExpectedStatus 403 -Method "GET" -Session $usuarioSession).Status -eq 403) "USUARIO nao deve acessar midia admin"
  Add-Check "usuario sem outbox admin" ((Invoke-LocalHttp -Path "/api/admin/outbox" -ExpectedStatus 403 -Method "GET" -Session $usuarioSession).Status -eq 403) "USUARIO nao deve acessar outbox"
  if (-not [string]::IsNullOrWhiteSpace($outboxId)) {
    Add-Check "usuario nao simula outbox" ((Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId/simular-processamento-local" -ExpectedStatus 403 -Method "POST" -Body "{}" -Session $usuarioSession).Status -eq 403) "USUARIO nao deve simular processamento"
    Add-Check "usuario nao acessa preview outbox" ((Invoke-LocalHttp -Path "/api/admin/outbox/$outboxId/preview" -ExpectedStatus 403 -Method "GET" -Session $usuarioSession).Status -eq 403) "USUARIO nao deve ver preview de outbox"
  }
  Add-Check "usuario nao decide revisao" ((Invoke-LocalHttp -Path "/api/admin/moderacao/revisoes/$revisaoFinalizadaId/decidir" -ExpectedStatus 403 -Method "POST" -Body (New-DecisaoModeracaoBody -Decisao "APROVAR") -Session $usuarioSession).Status -eq 403) "USUARIO nao deve executar acao moderatoria"
  Add-Check "usuario nao remete revisao" ((Invoke-LocalHttp -Path "/api/admin/anuncios/$anuncioRemeterConflitoId/remeter-revisao" -ExpectedStatus 403 -Method "POST" -Body (New-RemeterRevisaoBody) -Session $usuarioSession).Status -eq 403) "USUARIO nao deve remeter revisao"

  $adminLogout = Invoke-LocalHttp -Path "/api/admin/auth/logout" -ExpectedStatus 200 -Method "POST" -Body "{}" -Session $adminSession
  Add-Check "admin logout status 200" ($adminLogout.Status -eq 200) "logout local deve responder"
  Assert-NoSensitiveAdminAuthData -Nome "admin logout" -Body $adminLogout.Body

  $adminMeDepoisLogout = Invoke-LocalHttp -Path "/api/admin/auth/me" -ExpectedStatus 401 -Method "GET" -Session $adminSession
  Add-Check "admin me apos logout status 401" ($adminMeDepoisLogout.Status -eq 401) "logout deve invalidar sessao"
  Assert-NoSensitiveAdminAuthData -Nome "admin me apos logout" -Body $adminMeDepoisLogout.Body
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
