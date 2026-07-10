param(
  [string]$FixturePath = "backend/src/test/resources/fixtures/v3-dados-sinteticos.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

function Resolve-RepoPath {
  param([string]$Path)
  if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
  return (Join-Path $repoRoot ($Path -replace '/', [IO.Path]::DirectorySeparatorChar))
}

function Get-StringValues {
  param([object]$Value)
  $items = New-Object System.Collections.Generic.List[string]
  if ($null -eq $Value) { return @($items.ToArray()) }
  if ($Value -is [string]) {
    $items.Add($Value)
    return @($items.ToArray())
  }
  if ($Value -is [System.Collections.IDictionary]) {
    foreach ($key in $Value.Keys) {
      foreach ($item in @(Get-StringValues $Value[$key])) { $items.Add($item) }
    }
    return @($items.ToArray())
  }
  if ($Value -is [System.Collections.IEnumerable] -and -not ($Value -is [string])) {
    foreach ($entry in $Value) {
      foreach ($item in @(Get-StringValues $entry)) { $items.Add($item) }
    }
    return @($items.ToArray())
  }
  if ($Value.PSObject -and $Value.PSObject.Properties) {
    foreach ($property in $Value.PSObject.Properties) {
      foreach ($item in @(Get-StringValues $property.Value)) { $items.Add($item) }
    }
  }
  return @($items.ToArray())
}

function Add-Failure {
  param([string]$Message)
  $script:failures.Add($Message)
}

function Assert-True {
  param([bool]$Condition, [string]$Message)
  if (-not $Condition) { Add-Failure $Message }
}

$fixtureFull = Resolve-RepoPath $FixturePath
if (-not (Test-Path -LiteralPath $fixtureFull -PathType Leaf)) {
  Write-Host "VALIDATION_RESULT=PENDENTE_FIXTURE_SINTETICA"
  exit 2
}

$data = Get-Content -LiteralPath $fixtureFull -Raw | ConvertFrom-Json
$failures = New-Object System.Collections.Generic.List[string]

$cidades = @($data.cidades)
$bairros = @($data.bairros)
$anuncios = @($data.anuncios)
$midias = @($data.midias)
$beneficios = @($data.premiumBeneficios)
$metricas = @($data.metricasAgregadas)
$rotas = @($data.rotasCobertas)
$adminCasos = @($data.adminCasos)
$strings = @(Get-StringValues $data)
$joined = $strings -join "`n"

Assert-True ($data.localOnly -eq $true) "Fixture deve ser localOnly."
Assert-True ($data.noRealData -eq $true) "Fixture deve declarar noRealData."
Assert-True ($cidades.Count -ge 5) "Fixture deve conter ao menos 5 cidades sinteticas."
Assert-True ($bairros.Count -ge 8) "Fixture deve conter bairros suficientes."
Assert-True ($anuncios.Count -ge 10) "Fixture deve conter anuncios sinteticos suficientes."
Assert-True (@($cidades | Where-Object { $_.slug -eq "goiania-go" }).Count -eq 1) "Goiania/GO sintetica ausente."
Assert-True (@($cidades | Where-Object { $_.slug -eq "aparecida-de-goiania-go" }).Count -eq 1) "Aparecida de Goiania/GO sintetica ausente."
Assert-True (@($cidades | Where-Object { $_.slug -eq "brasilia-df" }).Count -eq 1) "Brasilia/DF sintetica ausente."
Assert-True (@($cidades | Where-Object { $_.slug -eq "anapolis-go" }).Count -eq 1) "Anapolis/GO sintetica ausente."
foreach ($status in @("ATIVO", "PAUSADO", "PENDENTE", "REJEITADO", "INVALIDO_CONTROLE")) {
  Assert-True (@($anuncios | Where-Object { $_.status -eq $status }).Count -ge 1) "Status sintetico ausente: $status."
}
foreach ($visibilidade in @("LIVRE", "RESTRITA_18")) {
  Assert-True (@($midias | Where-Object { $_.visibilidade -eq $visibilidade }).Count -ge 1) "Visibilidade sintetica ausente: $visibilidade."
}
Assert-True (@($midias | Where-Object { $_.tipo -eq "FOTO" -and $_.visibilidade -eq "LIVRE" }).Count -ge 1) "Foto LIVRE sintetica ausente."
Assert-True (@($midias | Where-Object { $_.tipo -eq "FOTO" -and $_.visibilidade -eq "RESTRITA_18" }).Count -ge 1) "Foto RESTRITA_18 sintetica ausente."
Assert-True (@($midias | Where-Object { $_.tipo -eq "VIDEO" -and $_.visibilidade -eq "RESTRITA_18" }).Count -ge 1) "Video deve ser RESTRITA_18."
Assert-True (@($midias | Where-Object { $_.tipo -eq "STORY" -and $_.visibilidade -eq "RESTRITA_18" }).Count -ge 1) "Story deve ser RESTRITA_18."
Assert-True (@($midias | Where-Object { $_.statusModeracao -eq "PENDENTE" -and $null -eq $_.visibilidade }).Count -ge 1) "Midia pendente deve permanecer sem visibilidade publicada."
foreach ($plano in @("GRATUITO", "PREMIUM_ATIVO", "PREMIUM_EXPIRADO")) {
  Assert-True (@($anuncios | Where-Object { $_.plano -eq $plano }).Count -ge 1) "Plano sintetico ausente: $plano."
}
Assert-True (@($anuncios | Where-Object { $null -eq $_.bairroSlug }).Count -ge 1) "Caso sem bairro ausente."
Assert-True (@($anuncios | Where-Object { $_.bairroSlug }).Count -ge 1) "Caso com bairro ausente."
Assert-True (@($anuncios | Where-Object { $_.seo -eq "FORTE" }).Count -ge 1) "Caso SEO forte ausente."
Assert-True (@($anuncios | Where-Object { $_.seo -eq "FRACO" }).Count -ge 1) "Caso SEO fraco ausente."
Assert-True (@($anuncios | Where-Object { $_.midia -eq "PLACEHOLDER_INSUFICIENTE" }).Count -ge 1) "Caso sem midia suficiente ausente."
Assert-True (@($anuncios | Where-Object { $_.slug -eq "demo-goiania-midia-restrita" -and $_.whatsappPublico -eq "PLACEHOLDER_NAO_DISCAVEL" }).Count -eq 1) "Anuncio ativo com midia restrita deve manter contato mediado."
Assert-True ($beneficios.Count -ge 5) "Beneficios premium sinteticos insuficientes."
Assert-True (@($beneficios | Where-Object { $_.status -eq "ATIVO" }).Count -ge 1) "Beneficio ativo ausente."
Assert-True (@($beneficios | Where-Object { $_.status -eq "EXPIRADO" }).Count -ge 1) "Beneficio expirado ausente."
Assert-True ($metricas.Count -ge 4) "Metricas agregadas sinteticas insuficientes."
Assert-True (@($metricas | Where-Object { $_.origem -eq "ORGANICO_LOCAL" }).Count -ge 1) "Metrica organica local ausente."
Assert-True (@($metricas | Where-Object { $_.origem -eq "PREMIUM_LOCAL" }).Count -ge 1) "Metrica premium local ausente."
Assert-True ($rotas.Count -ge 8) "Rotas sinteticas cobertas insuficientes."
Assert-True ($adminCasos.Count -ge 4) "Casos admin sinteticos insuficientes."
Assert-True ($data.regrasSeguranca.frontendDecideVisibilidade -eq $false) "Frontend nao pode decidir visibilidade."
Assert-True ($data.regrasSeguranca.backendFonteVisibilidade -eq $true) "Backend deve ser fonte da visibilidade."
Assert-True ($data.regrasSeguranca.contatoIndependeIdade -eq $true) "Contato de anuncio publico ativo deve independer da idade."
Assert-True ($data.regrasSeguranca.originalRestritoSemIdade -eq $false) "Original restrito nao pode ser entregue sem idade."

$forbiddenPatterns = @(
  @{ Label = "CPF"; Regex = '(^|[^0-9])[0-9]{3}\.?[0-9]{3}\.?[0-9]{3}-?[0-9]{2}([^0-9]|$)' },
  @{ Label = "e-mail real"; Regex = '(?i)[a-z0-9._%+-]+@(?!example\.test\b)[a-z0-9.-]+\.[a-z]{2,}' },
  @{ Label = "telefone real"; Regex = '(\+?55)?[\s-]?\(?[1-9]{2}\)?[\s-]?9?[0-9]{4}[\s-]?[0-9]{4}' },
  @{ Label = "IP bruto"; Regex = '(^|[^0-9])([0-9]{1,3}\.){3}[0-9]{1,3}([^0-9]|$)' },
  @{ Label = "URL externa ou privada"; Regex = '(?i)https?://|s3://|r2://|amazonaws|cloudflare|storage\.googleapis' },
  @{ Label = "segredo/token/senha/certificado"; Regex = '(?i)(secret|token|senha|password|certificado|private key)' },
  @{ Label = "payload financeiro Pix/Efi"; Regex = '(?i)(pix payload|efi payload|txid real|webhook real)' },
  @{ Label = "documento real"; Regex = '(?i)(cpf real|rg real|documento real|selfie real)' }
)

foreach ($pattern in $forbiddenPatterns) {
  if ($joined -match $pattern.Regex) {
    Add-Failure "Padrao proibido encontrado: $($pattern.Label)."
  }
}

Write-Host "CIDADES=$($cidades.Count)"
Write-Host "BAIRROS=$($bairros.Count)"
Write-Host "ANUNCIOS=$($anuncios.Count)"
Write-Host "MIDIAS_LIVRE=$(@($midias | Where-Object { $_.visibilidade -eq 'LIVRE' }).Count)"
Write-Host "MIDIAS_RESTRITA_18=$(@($midias | Where-Object { $_.visibilidade -eq 'RESTRITA_18' }).Count)"
Write-Host "PREMIUM_ATIVO=$(@($anuncios | Where-Object { $_.plano -eq 'PREMIUM_ATIVO' }).Count)"
Write-Host "PREMIUM_EXPIRADO=$(@($anuncios | Where-Object { $_.plano -eq 'PREMIUM_EXPIRADO' }).Count)"
Write-Host "GRATUITO=$(@($anuncios | Where-Object { $_.plano -eq 'GRATUITO' }).Count)"
Write-Host "METRICAS_AGREGADAS=$($metricas.Count)"

if ($failures.Count -gt 0) {
  foreach ($failure in $failures) { Write-Host "FALHA: $failure" }
  Write-Host "VALIDATION_RESULT=FALHA_DADOS_SINTETICOS_LOCAL"
  exit 1
}

Write-Host "VALIDATION_RESULT=OK_DADOS_SINTETICOS_LOCAL"
exit 0
