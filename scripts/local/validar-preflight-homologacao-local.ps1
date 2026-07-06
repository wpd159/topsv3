param(
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-50/relatorio-preflight-homologacao-local.md"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..\..")).Path
Set-Location -LiteralPath $repoRoot

function Read-Utf8Text {
  param([string]$RelativePath)
  $path = Join-Path $repoRoot $RelativePath
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    return ""
  }
  return [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
}

function Write-Utf8NoBom {
  param(
    [string]$Path,
    [string]$Content
  )
  $dir = Split-Path -Parent $Path
  if ($dir -and -not (Test-Path -LiteralPath $dir)) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
  }
  $encoding = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Add-PreflightItem {
  param(
    [string]$Frente,
    [string]$Classificacao,
    [string]$Status,
    [bool]$Ok,
    [string]$Detalhe,
    [string]$Evidencia
  )

  $script:items.Add([pscustomobject]@{
    Frente = $Frente
    Classificacao = $Classificacao
    Status = $Status
    Ok = $Ok
    Detalhe = $Detalhe
    Evidencia = $Evidencia
  }) | Out-Null
}

function ConvertTo-MarkdownCell {
  param([string]$Value)
  if ($null -eq $Value) {
    return ""
  }
  return (($Value -replace "\|", "/") -replace "`r?`n", " ").Trim()
}

function Test-TextContains {
  param(
    [string]$Text,
    [string]$Pattern
  )
  if ([string]::IsNullOrEmpty($Text)) {
    return $false
  }
  return ($Text -match $Pattern)
}

$script:items = New-Object System.Collections.Generic.List[object]

$trackedFiles = @()
$gitLsFiles = & git ls-files 2>&1
if ($LASTEXITCODE -eq 0) {
  $trackedFiles = @($gitLsFiles | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) })
}

$remoteOutput = ((& git remote -v 2>&1) | Out-String).Trim()
$remoteOk = [string]::IsNullOrWhiteSpace($remoteOutput)
Add-PreflightItem "Remote Git" "PRONTO_LOCALMENTE" $(if ($remoteOk) { "OK" } else { "FALHA_LOCAL" }) $remoteOk "Remote deve permanecer vazio no bloco local." "git remote -v"

$realEnvFiles = @($trackedFiles | Where-Object {
  $leaf = Split-Path -Leaf ([string]$_)
  ($leaf -like ".env*") -and ($leaf -notlike "*.example")
})
$envOk = ($realEnvFiles.Count -eq 0)
Add-PreflightItem "Secrets fora do codigo" "PRONTO_LOCALMENTE" $(if ($envOk) { "OK" } else { "FALHA_LOCAL" }) $envOk "Arquivos .env reais nao podem estar versionados; exemplos sao permitidos." $(if ($envOk) { ".env real ausente do Git" } else { ($realEnvFiles -join ", ") })

$forbiddenTracked = @($trackedFiles | Where-Object {
  $_ -match '(?i)\.(dump|backup|bak|sqlite|sqlite3|db)$' -or $_ -match '(?i)pg_restore.*\.(log|txt)$'
})
$forbiddenOk = ($forbiddenTracked.Count -eq 0)
Add-PreflightItem "Artefatos sensiveis versionados" "PRONTO_LOCALMENTE" $(if ($forbiddenOk) { "OK" } else { "FALHA_LOCAL" }) $forbiddenOk "Dump, backup, banco local e log bruto nao podem entrar no Git." $(if ($forbiddenOk) { "Nenhum artefato proibido rastreado" } else { ($forbiddenTracked -join ", ") })

$applicationYaml = Read-Utf8Text "backend/src/main/resources/application.yml"
$applicationLocalYaml = Read-Utf8Text "backend/src/main/resources/application-local.yml"
$localEnvExample = Read-Utf8Text "infra/local/.env.local.example"
$rootEnvExample = Read-Utf8Text ".env.local.example"
$frontendEnvExample = Read-Utf8Text "frontend/.env.local.example"
$securityConfig = Read-Utf8Text "backend/src/main/java/br/com/topsdojob/v3/security/config/SecurityConfig.java"
$preflightDoc = Read-Utf8Text "docs/v3/HOMOLOGACAO-preflight-local.md"
$matrixDoc = Read-Utf8Text "docs/v3/HOMOLOGACAO-matriz-prontidao.md"
$orderDoc = Read-Utf8Text "docs/v3/HOMOLOGACAO-ordem-proximos-blocos.md"
$sddPendencias = Read-Utf8Text "docs/v3/SDD-pendencias-gates.md"

$appEnvOk = (Test-TextContains $applicationYaml 'APP_ENV:nao_configurado') -and (Test-TextContains $applicationLocalYaml 'APP_ENV:local') -and (Test-TextContains $localEnvExample 'APP_ENV=local')
Add-PreflightItem "APP_ENV e perfis" "PRONTO_LOCALMENTE" $(if ($appEnvOk) { "OK" } else { "FALHA_LOCAL" }) $appEnvOk "Perfil local esta documentado; homologacao/producao seguem sem profile real no repositorio." "application.yml, application-local.yml, infra/local/.env.local.example"

$cookieOk = (Test-TextContains $applicationYaml 'http-only:\s*true') -and (Test-TextContains $applicationYaml 'same-site:\s*lax') -and (Test-TextContains $applicationYaml 'APP_ADMIN_SESSION_COOKIE_SECURE:true') -and (Test-TextContains $applicationLocalYaml 'APP_ADMIN_SESSION_COOKIE_SECURE:false')
Add-PreflightItem "Cookies de sessao" "PRONTO_LOCALMENTE" $(if ($cookieOk) { "OK" } else { "FALHA_LOCAL" }) $cookieOk "Default nao-local usa Secure=true, local pode usar Secure=false para smoke controlado, sempre HttpOnly/SameSite=Lax." "backend/src/main/resources/application*.yml"

$corsOk = (Test-TextContains $applicationYaml 'APP_CORS_ALLOWED_ORIGINS') -and (Test-TextContains $applicationLocalYaml 'localhost:3000') -and (Test-TextContains $localEnvExample 'APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127\.0\.0\.1:3000')
Add-PreflightItem "CORS" "PENDENTE_HOMOLOGACAO" $(if ($corsOk) { "PENDENTE" } else { "FALHA_LOCAL" }) $corsOk "CORS local esta restrito a localhost; lista definitiva de homologacao/producao ainda deve ser definida fora do codigo." "APP_CORS_ALLOWED_ORIGINS"

$csrfLocalOk = (Test-TextContains $securityConfig 'csrf\.disable\(\)') -and (Test-TextContains $securityConfig 'local')
$csrfNonLocalOk = Test-TextContains $securityConfig 'CookieCsrfTokenRepository\.withHttpOnlyFalse\(\)'
Add-PreflightItem "CSRF" "PENDENTE_HOMOLOGACAO" $(if ($csrfLocalOk -and $csrfNonLocalOk) { "PENDENTE" } else { "FALHA_LOCAL" }) ($csrfLocalOk -and $csrfNonLocalOk) "Local smoke permanece controlado; homologacao/producao exigem revisao Pro de CSRF real, HTTPS, cookie seguro e sessao." "SecurityConfig.java"

$efiMockOk = (Test-TextContains $applicationYaml 'EFI_PIX_MOCK_MODE:false') -and (Test-TextContains $applicationLocalYaml 'EFI_PIX_MOCK_MODE:true') -and (Test-TextContains $localEnvExample 'EFI_PIX_MOCK_MODE=true')
Add-PreflightItem "Pix/Efi" "BLOQUEANTE_PRODUCAO" $(if ($efiMockOk) { "BLOQUEANTE" } else { "FALHA_LOCAL" }) $efiMockOk "Local usa mock; Pix/Efi real, checkout e webhook dependem de homologacao propria e credenciais seguras." "EFI_PIX_MOCK_MODE"

$storageLocalOk = (Test-TextContains $localEnvExample 'TOPSV3_MINIO_IMAGE') -and (Test-Path -LiteralPath (Join-Path $repoRoot "docs/v3/125-bloco-11-midia-publica-cdn-local.md")) -and (Test-Path -LiteralPath (Join-Path $repoRoot "docs/v3/126-politica-url-publica-midia.md"))
Add-PreflightItem "Storage/CDN/upload" "BLOQUEANTE_PRODUCAO" $(if ($storageLocalOk) { "BLOQUEANTE" } else { "FALHA_LOCAL" }) $storageLocalOk "Ha apenas storage local/S3-compatible e politica; upload/CDN real ainda nao foi autorizado." "infra/local e docs de midia"

$mediaValidatorOk = Test-Path -LiteralPath (Join-Path $repoRoot "scripts/local/validar-midia-publica-sintetica-local.ps1")
Add-PreflightItem "Midia publica e documento privado" "PRONTO_LOCALMENTE" $(if ($mediaValidatorOk) { "OK" } else { "FALHA_LOCAL" }) $mediaValidatorOk "Fluxo sintetico valida que documento privado nao vira midia publica e que DTO publico nao expoe storage interno." "scripts/local/validar-midia-publica-sintetica-local.ps1"

$importerOk = (Test-Path -LiteralPath (Join-Path $repoRoot "scripts/local/validar-fonte-importacao-local.ps1")) -and (Test-TextContains $sddPendencias 'Gate de importador real')
Add-PreflightItem "Importador real" "BLOQUEANTE_PRODUCAO" $(if ($importerOk) { "BLOQUEANTE" } else { "FALHA_LOCAL" }) $importerOk "Contratos locais existem, mas fonte real e dry-run real continuam pendentes." "scripts/local/validar-fonte-importacao-local.ps1"

$seoDocsOk = (Test-Path -LiteralPath (Join-Path $repoRoot "docs/v3/SEO-canonical-sitemap-robots-cutover.md")) -and (Test-Path -LiteralPath (Join-Path $repoRoot "docs/v3/SEO-plano-redirecionamentos-301.md"))
Add-PreflightItem "SEO real" "PENDENTE_HOMOLOGACAO" $(if ($seoDocsOk) { "PENDENTE" } else { "FALHA_LOCAL" }) $seoDocsOk "SEO local e mapa base existem; canonical, sitemap, robots, 301 e Search Console finais dependem de homologacao/cutover." "docs/v3/SEO-*.md"

$restoreGateOk = (Test-TextContains $sddPendencias 'Bloco 29') -and (Test-TextContains $sddPendencias 'restore completo')
Add-PreflightItem "Bloco 29 / restore completo" "BLOQUEANTE_PRODUCAO" $(if ($restoreGateOk) { "BLOQUEANTE" } else { "FALHA_LOCAL" }) $restoreGateOk "Restore completo consistente segue pendente e nao pode ser substituido pela quarentena sem POST_DATA." "SDD-pendencias-gates.md"

$backupRollbackOk = (Test-TextContains $orderDoc 'backup') -and (Test-TextContains $orderDoc 'rollback')
Add-PreflightItem "Backup e rollback" "BLOQUEANTE_PRODUCAO" $(if ($backupRollbackOk) { "BLOQUEANTE" } else { "FALHA_LOCAL" }) $backupRollbackOk "Plano existe como ordem/gate; teste real de rollback ainda e pre-requisito para producao." "HOMOLOGACAO-ordem-proximos-blocos.md"

$observabilityOk = (Test-Path -LiteralPath (Join-Path $repoRoot "scripts/local/validar-observabilidade-auditoria-local.ps1")) -and (Test-TextContains $matrixDoc 'Observabilidade')
Add-PreflightItem "Observabilidade/auditoria" "PENDENTE_HOMOLOGACAO" $(if ($observabilityOk) { "PENDENTE" } else { "FALHA_LOCAL" }) $observabilityOk "Request-id e auditoria local foram validados; logs estruturados finais, retencao e auditoria JSON real seguem pendentes." "Bloco 49"

$gitleaksOk = Test-TextContains $matrixDoc 'gitleaks 8.30.1'
Add-PreflightItem "Gitleaks real" "PRONTO_LOCALMENTE" $(if ($gitleaksOk) { "OK" } else { "FALHA_LOCAL" }) $gitleaksOk "Gitleaks real validado localmente; gate operacional/CI ainda deve ser repetido em homologacao." "Bloco 44"

$flywayOk = Test-TextContains $matrixDoc 'OK_FLYWAY_REAL_LOCAL'
Add-PreflightItem "Flyway real local" "PRONTO_LOCALMENTE" $(if ($flywayOk) { "OK" } else { "FALHA_LOCAL" }) $flywayOk "Flyway real local validado via Docker; deve ser repetido no ambiente de homologacao antes de producao." "Bloco 47"

$profileDocsOk = (Test-Path -LiteralPath (Join-Path $repoRoot "infra/staging/README.md")) -and (Test-Path -LiteralPath (Join-Path $repoRoot "infra/producao/README.md"))
Add-PreflightItem "Staging/homologacao/producao" "PENDENTE_HOMOLOGACAO" $(if ($profileDocsOk) { "PENDENTE" } else { "FALHA_LOCAL" }) $profileDocsOk "Diretorios reservados existem; ambiente real nao foi criado nem acessado neste bloco." "infra/staging e infra/producao"

$proRequiredOk = (Test-TextContains $preflightDoc 'Pro obrigatorio') -or (Test-TextContains $sddPendencias 'Pro')
Add-PreflightItem "Revisao Pro" "BLOQUEANTE_PRODUCAO" $(if ($proRequiredOk) { "BLOQUEANTE" } else { "FALHA_LOCAL" }) $proRequiredOk "Pro/humano especializado segue obrigatorio antes de homologacao/cutover real com dados reais/sanitizados." "SDD e HOMOLOGACAO-preflight-local.md"

$exampleEnvOk = -not [string]::IsNullOrWhiteSpace($rootEnvExample) -and -not [string]::IsNullOrWhiteSpace($frontendEnvExample) -and -not [string]::IsNullOrWhiteSpace($localEnvExample)
Add-PreflightItem "Contratos de ambiente exemplo" "PRONTO_LOCALMENTE" $(if ($exampleEnvOk) { "OK" } else { "FALHA_LOCAL" }) $exampleEnvOk "Exemplos locais existem; valores reais continuam fora do repositorio." ".env.local.example, frontend/.env.local.example, infra/local/.env.local.example"

$hardFailures = @($items | Where-Object { -not $_.Ok })
$readyCount = @($items | Where-Object { $_.Classificacao -eq "PRONTO_LOCALMENTE" }).Count
$pendingCount = @($items | Where-Object { $_.Classificacao -eq "PENDENTE_HOMOLOGACAO" }).Count
$blockingCount = @($items | Where-Object { $_.Classificacao -eq "BLOQUEANTE_PRODUCAO" }).Count
$result = if ($hardFailures.Count -eq 0) { "OK_PREFLIGHT_HOMOLOGACAO_LOCAL" } else { "FALHA_PREFLIGHT_HOMOLOGACAO_LOCAL" }

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Relatorio preflight homologacao local")
$lines.Add("")
$lines.Add("## Resultado")
$lines.Add("")
$lines.Add("- VALIDATION_RESULT=$result")
$lines.Add("- Pronto localmente: $readyCount")
$lines.Add("- Pendente antes de homologacao: $pendingCount")
$lines.Add("- Bloqueante antes de producao: $blockingCount")
$lines.Add("- Falhas locais: $($hardFailures.Count)")
$lines.Add("- Escopo: local, sintetico e documental.")
$lines.Add("")
$lines.Add("## Itens avaliados")
$lines.Add("")
$lines.Add("| Frente | Classificacao | Status | Detalhe | Evidencia |")
$lines.Add("| --- | --- | --- | --- | --- |")
foreach ($item in $items) {
  $lines.Add("| $(ConvertTo-MarkdownCell $item.Frente) | $(ConvertTo-MarkdownCell $item.Classificacao) | $(ConvertTo-MarkdownCell $item.Status) | $(ConvertTo-MarkdownCell $item.Detalhe) | $(ConvertTo-MarkdownCell $item.Evidencia) |")
}
$lines.Add("")
$lines.Add("## Pendencias de homologacao")
$lines.Add("")
foreach ($item in @($items | Where-Object { $_.Classificacao -eq "PENDENTE_HOMOLOGACAO" })) {
  $lines.Add("- $($item.Frente): $($item.Detalhe)")
}
$lines.Add("")
$lines.Add("## Bloqueios de producao")
$lines.Add("")
foreach ($item in @($items | Where-Object { $_.Classificacao -eq "BLOQUEANTE_PRODUCAO" })) {
  $lines.Add("- $($item.Frente): $($item.Detalhe)")
}
$lines.Add("")
$lines.Add("## Limites preservados")
$lines.Add("")
$lines.Add("- Sem acesso a producao, VPS, restore, staging, Pix/Efi real, webhook, API externa, remote ou push.")
$lines.Add("- Sem dados reais, dump, backup, midia real, documento real ou credencial real no repositorio.")
$lines.Add("- O resultado local nao autoriza homologacao, cutover ou producao.")

$relatorioPath = if ([System.IO.Path]::IsPathRooted($RelatorioSaida)) { $RelatorioSaida } else { Join-Path $repoRoot $RelatorioSaida }
Write-Utf8NoBom -Path $relatorioPath -Content (($lines -join "`n") + "`n")

Write-Host "VALIDATION_RESULT=$result"
Write-Host "PRONTO_LOCALMENTE_COUNT=$readyCount"
Write-Host "PENDENTE_HOMOLOGACAO_COUNT=$pendingCount"
Write-Host "BLOQUEANTE_PRODUCAO_COUNT=$blockingCount"
Write-Host "FALHAS_LOCAIS=$($hardFailures.Count)"
Write-Host "RELATORIO=$relatorioPath"

if ($hardFailures.Count -eq 0) {
  exit 0
}

exit 1
