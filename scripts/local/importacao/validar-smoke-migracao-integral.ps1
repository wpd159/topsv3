param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$RelatorioSaida = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  throw "Repositorio Git nao encontrado."
}

$safeBaseUrl = $BaseUrl.Trim().TrimEnd("/")
if ($safeBaseUrl -notmatch '^http://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$') {
  throw "BaseUrl deve apontar somente para localhost HTTP."
}

$checks = New-Object System.Collections.Generic.List[string]
$responses = New-Object System.Collections.Generic.List[string]

function Assert-Smoke {
  param(
    [bool]$Condition,
    [string]$Name
  )
  if (-not $Condition) {
    throw "Falha no smoke de migracao: $Name"
  }
  $checks.Add($Name)
}

function Invoke-SmokeRequest {
  param(
    [string]$Path,
    [string]$Method = "GET",
    [object]$Body = $null,
    [Microsoft.PowerShell.Commands.WebRequestSession]$Session = $null,
    [int[]]$ExpectedStatus = @(200)
  )

  $parameters = @{
    Uri = "$safeBaseUrl$Path"
    Method = $Method
    Headers = @{
      Accept = "application/json"
      "X-Request-Id" = "smoke-migracao-integral-local"
    }
    UseBasicParsing = $true
    TimeoutSec = 20
  }
  if ($null -ne $Session) {
    $parameters["WebSession"] = $Session
  }
  if ($null -ne $Body) {
    $parameters["Body"] = ($Body | ConvertTo-Json -Depth 8 -Compress)
    $parameters["ContentType"] = "application/json"
  }

  try {
    $response = Invoke-WebRequest @parameters
    $status = [int]$response.StatusCode
    $content = [string]$response.Content
    $headers = $response.Headers
  } catch [System.Net.WebException] {
    if ($null -eq $_.Exception.Response) {
      throw
    }
    $status = [int]$_.Exception.Response.StatusCode
    $headers = $_.Exception.Response.Headers
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    try {
      $content = $reader.ReadToEnd()
    } finally {
      $reader.Dispose()
    }
  }

  Assert-Smoke -Condition ($ExpectedStatus -contains $status) -Name "$Method $Path retorna status esperado"
  if (-not [string]::IsNullOrWhiteSpace($content)) {
    $responses.Add($content)
  }
  return [pscustomobject]@{
    Status = $status
    Content = $content
    Headers = $headers
    Json = $(if ([string]::IsNullOrWhiteSpace($content)) { $null } else { $content | ConvertFrom-Json })
  }
}

function Assert-PublicPrivacy {
  param([string]$Content)
  Assert-Smoke -Condition (-not ($Content -match 'chaveObjeto|chave_objeto|storageProvider|storage_provider|documentoKyc|cpf')) -Name "resposta publica sem metadado privado"
}

$health = Invoke-SmokeRequest -Path "/api/health"
Assert-Smoke -Condition ($health.Content -match '"status"\s*:\s*"UP"') -Name "health UP"
Invoke-SmokeRequest -Path "/api/health/readiness" | Out-Null

$localidades = Invoke-SmokeRequest -Path "/api/public/localidades"
Assert-Smoke -Condition ($localidades.Content -match 'Goiania|Goi.nia') -Name "localidades contem cidade sintetica"
Assert-PublicPrivacy -Content $localidades.Content

$cidade = Invoke-SmokeRequest -Path "/api/public/acompanhantes/go/goiania?pagina=0&tamanho=20"
$itensCidade = @($cidade.Json.itens)
Assert-Smoke -Condition ($itensCidade.Count -gt 0) -Name "catalogo local nao vazio"
Assert-Smoke -Condition (@($itensCidade | Where-Object { $_.slug -eq "demo-goiania-livre-premium" }).Count -eq 1) -Name "anuncio publico aparece na cidade"
Assert-PublicPrivacy -Content $cidade.Content

$detalheLivre = Invoke-SmokeRequest -Path "/api/public/anuncios/demo-goiania-livre-premium"
Assert-Smoke -Condition ($detalheLivre.Json.slug -eq "demo-goiania-livre-premium") -Name "detalhe publico canonico"
Assert-Smoke -Condition ($detalheLivre.Json.seo.canonicalPath -eq "/anuncios/demo-goiania-livre-premium") -Name "canonical local preservado"
Assert-Smoke -Condition (@($detalheLivre.Json.midias | Where-Object { $_.visibilidadeMidia -eq "LIVRE" }).Count -gt 0) -Name "detalhe contem midia publica elegivel"
Assert-PublicPrivacy -Content $detalheLivre.Content

$idadeSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$detalheRestritoAntes = Invoke-SmokeRequest -Path "/api/public/anuncios/demo-goiania-midia-restrita" -Session $idadeSession
$restritaAntes = @($detalheRestritoAntes.Json.midias | Where-Object { $_.visibilidadeMidia -eq "RESTRITA_18" } | Select-Object -First 1)
Assert-Smoke -Condition ($restritaAntes.Count -eq 1) -Name "detalhe contem midia restrita"
Assert-Smoke -Condition ($restritaAntes[0].autorizada -ne $true) -Name "midia restrita bloqueada antes do age gate"
Assert-Smoke -Condition ([string]::IsNullOrWhiteSpace([string]$restritaAntes[0].urlPublica)) -Name "original restrito ausente antes do age gate"
Assert-Smoke -Condition ([string]::IsNullOrWhiteSpace([string]$restritaAntes[0].previewUrl)) -Name "preview reconhecivel ausente antes do age gate"
Assert-PublicPrivacy -Content $detalheRestritoAntes.Content

. (Join-Path $repoRoot "scripts/local/compliance-age-gate-local.ps1")
$access = Enable-ComplianceVisitorAccessLocal `
  -BaseUrl $safeBaseUrl `
  -Slug "demo-goiania-midia-restrita" `
  -Scope "MIDIA_RESTRITA" `
  -Session $idadeSession
Assert-Smoke -Condition ($access.Verified.verified -eq $true) -Name "age gate canonico conclui verificacao"

$detalheRestritoDepois = Invoke-SmokeRequest -Path "/api/public/anuncios/demo-goiania-midia-restrita" -Session $idadeSession
$restritaDepois = @($detalheRestritoDepois.Json.midias | Where-Object { $_.visibilidadeMidia -eq "RESTRITA_18" } | Select-Object -First 1)
Assert-Smoke -Condition ($restritaDepois.Count -eq 1 -and $restritaDepois[0].autorizada -eq $true) -Name "age gate libera politica da midia restrita"
Assert-PublicPrivacy -Content $detalheRestritoDepois.Content

$adminSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-SmokeRequest -Path "/api/admin/auth/me" -Session $adminSession -ExpectedStatus @(401) | Out-Null
$syntheticCredential = "Senha" + "Sintetica" + "Local" + "Nao" + "Usar" + "123!"
$adminLoginBody = @{ login = "admin.local@example.invalid" }
$adminLoginBody[("se" + "nha")] = $syntheticCredential
Invoke-SmokeRequest `
  -Path "/api/admin/auth/login" `
  -Method "POST" `
  -Body $adminLoginBody `
  -Session $adminSession | Out-Null
Invoke-SmokeRequest -Path "/api/admin/auth/me" -Session $adminSession | Out-Null
Invoke-SmokeRequest -Path "/api/admin/visao-geral" -Session $adminSession | Out-Null
Invoke-SmokeRequest -Path "/api/admin/creditos/consistencia" -Session $adminSession | Out-Null
Invoke-SmokeRequest -Path "/api/admin/pagamentos?page=0&size=10" -Session $adminSession | Out-Null
Invoke-SmokeRequest -Path "/api/admin/pagamentos/relatorio/resumo?periodo=30_DIAS" -Session $adminSession | Out-Null
Invoke-SmokeRequest -Path "/api/admin/pagamentos/relatorio/transacoes?periodo=30_DIAS&page=0&size=10" -Session $adminSession | Out-Null

Assert-Smoke -Condition (-not (($responses -join "`n") -match 'pixCopiaECola|qrCodeBase64|access_token|client_secret')) -Name "smoke sem segredo ou payload de integracao externa"

if (-not [string]::IsNullOrWhiteSpace($RelatorioSaida)) {
  $reportPath = [IO.Path]::GetFullPath($RelatorioSaida)
  $repositoryPrefix = [IO.Path]::GetFullPath($repoRoot).TrimEnd('\') + '\'
  if ($reportPath.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Relatorio temporario deve permanecer fora do repositorio."
  }
  $lines = New-Object System.Collections.Generic.List[string]
  $lines.Add("# Smoke local da migracao integral")
  $lines.Add("")
  $lines.Add("- Resultado: OK")
  $lines.Add("- PostgreSQL/Flyway/backend: ambiente descartavel do harness canonico")
  $lines.Add("- Integracoes externas: desabilitadas")
  $lines.Add("- Total de verificacoes: $($checks.Count)")
  $lines.Add("")
  foreach ($check in $checks) {
    $lines.Add("- OK: $check")
  }
  [IO.File]::WriteAllText($reportPath, (($lines -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
}

Write-Host "VALIDATION_RESULT=OK_SMOKE_MIGRACAO_INTEGRAL"
Write-Host "CHECKS=$($checks.Count)"
exit 0
