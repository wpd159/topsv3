Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

$migrationRel = "backend/src/main/resources/db/migration"
$migrationDir = Join-Path $repoRoot ($migrationRel -replace '/', [IO.Path]::DirectorySeparatorChar)
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

function Remove-SqlLineComments {
  param([string]$Text)
  return ([regex]::Replace($Text, '(?m)--.*$', ''))
}

Add-Check "diretorio de migrations existe" (Test-Path -LiteralPath $migrationDir -PathType Container) $migrationRel

$allSql = @(Get-ChildItem -LiteralPath $repoRoot -Recurse -File -Filter *.sql -ErrorAction SilentlyContinue | Where-Object { -not (Test-IgnoredBuildOutput $_.FullName) })
$outsideSql = @($allSql | Where-Object {
  ((Get-RelativePath $_.FullName) -notlike "$migrationRel/*") -and
  (-not (Test-AllowedLocalSyntheticSql $_.FullName))
})
Add-Check "arquivos .sql apenas no diretorio de migrations" ($outsideSql.Count -eq 0) "fora do diretorio: $($outsideSql.Count)"

$files = @()
if (Test-Path -LiteralPath $migrationDir -PathType Container) {
  $files = @(Get-ChildItem -LiteralPath $migrationDir -File -Filter "V*.sql" | Sort-Object Name)
}

Add-Check "migrations V*.sql encontradas" ($files.Count -gt 0) "total: $($files.Count)"

$namePatternOk = $true
$versions = New-Object System.Collections.Generic.List[int]
foreach ($file in $files) {
  if ($file.Name -notmatch '^V(?<n>[0-9]{3})__[a-z0-9_]+\.sql$') {
    $namePatternOk = $false
  } else {
    $versions.Add([int]$matches["n"])
  }
}
Add-Check "nomes seguem VNNN__descricao.sql" $namePatternOk "padrao Flyway local"

$sequenceOk = $true
if ($versions.Count -gt 0) {
  $sorted = @($versions | Sort-Object)
  for ($i = 0; $i -lt $sorted.Count; $i++) {
    if ($sorted[$i] -ne ($i + 1)) { $sequenceOk = $false }
  }
}
Add-Check "versoes sequenciais sem lacuna" $sequenceOk "esperado V001..V$('{0:D3}' -f $versions.Count)"

$allText = ""
foreach ($file in $files) {
  $allText += [System.IO.File]::ReadAllText($file.FullName)
  $allText += "`n"
}
$textNoComments = Remove-SqlLineComments $allText

Add-Check "sem DROP TABLE" (-not ($textNoComments -match '(?i)\bDROP\s+TABLE\b')) "migration destrutiva proibida"
Add-Check "sem TRUNCATE" (-not ($textNoComments -match '(?i)\bTRUNCATE\b')) "migration destrutiva proibida"
Add-Check "sem DELETE FROM" (-not ($textNoComments -match '(?i)\bDELETE\s+FROM\b')) "remocao de dados proibida"
Add-Check "sem INSERT INTO" (-not ($textNoComments -match '(?i)\bINSERT\s+INTO\b')) "seed ou dado real proibido"
Add-Check "sem CREATE TABLE IF NOT EXISTS" (-not ($textNoComments -match '(?i)CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS')) "schema divergente deve falhar"
Add-Check "sem CREATE TABLE LIKE INCLUDING ALL" (-not ($textNoComments -match '(?i)CREATE\s+TABLE\s+[a-z0-9_]+\s*\(\s*LIKE\s+[a-z0-9_]+\s+INCLUDING\s+ALL\s*\)')) "staging deve ser explicito"
Add-Check "sem URLs em migrations" (-not ($textNoComments -match '(?i)https?://')) "URL operacional proibida"
Add-Check "sem dominio de producao em migrations" (-not ($textNoComments -match '(?i)topsdojob\.com')) "dominio de producao proibido"
Add-Check "sem termos de segredo atribuidos" (-not ($textNoComments -match '(?i)(client[_-]?secret|api[_-]?key|access[_-]?token|refresh[_-]?token|private[_-]?key|certificate|certificado|p12|password|passwd|pwd)\s*[:=]')) "segredo hardcoded proibido"
Add-Check "sem CPF literal" (-not ($textNoComments -match '\b[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2}\b')) "dado pessoal real proibido"
Add-Check "sem e-mail literal" (-not ($textNoComments -match '\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b')) "dado pessoal real proibido"
Add-Check "sem float/double/real para dinheiro ou creditos" (-not ($textNoComments -match '(?i)\b(float|double|real)\b')) "usar numeric ou integer/bigint"

$createTableMatches = [regex]::Matches($textNoComments, '(?is)CREATE\s+TABLE\s+(?<name>[a-z0-9_]+)\s*(?<body>\([^;]+?\));')
$tables = New-Object System.Collections.Generic.List[string]
$pkOk = $true
$createdOk = $true
$statusCheckOk = $true
$createdExempt = @(
  "documento_busca_anuncio",
  "saldo_credito_usuario",
  "agregado_visualizacao_diaria",
  "agregado_clique_whatsapp_diario"
)

foreach ($match in $createTableMatches) {
  $tableName = [string]$match.Groups["name"].Value
  $body = [string]$match.Groups["body"].Value
  $tables.Add($tableName)
  $isLikeTable = ($body -match '(?i)\bLIKE\b')

  if (-not $isLikeTable -and $body -notmatch '(?i)\bPRIMARY\s+KEY\b') {
    $pkOk = $false
  }
  if (-not $isLikeTable -and -not ($createdExempt -contains $tableName) -and $body -notmatch '(?i)\b(criado_em|criada_em|recebido_em)\b') {
    $createdOk = $false
  }
  if (-not $isLikeTable -and $body -match '(?i)\bstatus\s+text\b' -and $body -notmatch '(?i)status_.*_chk|_status_chk') {
    $statusCheckOk = $false
  }
}

Add-Check "CREATE TABLEs possuem primary key" $pkOk "inclui chaves compostas e tabelas LIKE"
Add-Check "tabelas principais possuem marco temporal de criacao" $createdOk "criado_em, criada_em, recebido_em ou excecao documentada"
Add-Check "status criticos possuem CHECK" $statusCheckOk "status text deve ter constraint"
Add-Check "uso de timestamptz presente" ($textNoComments -match '(?i)\btimestamptz\b') "instantes devem usar timestamptz"
Add-Check "CHECK constraints presentes" ($textNoComments -match '(?i)\bCHECK\s*\(') "constraints criticas esperadas"
Add-Check "indices presentes" ($textNoComments -match '(?i)\bCREATE\s+(UNIQUE\s+)?INDEX\b') "indices locais esperados"
Add-Check "comentarios SQL presentes" ($textNoComments -match '(?i)\bCOMMENT\s+ON\b') "tabelas e colunas criticas documentadas"

$expectedTables = @(
  "usuario", "credencial_usuario", "papel_usuario", "permissao", "papel_permissao", "sessao_usuario", "token_seguranca",
  "estado", "cidade", "bairro", "anuncio_localizacao", "anuncio", "anuncio_status_historico", "documento_busca_anuncio",
  "arquivo_midia", "anuncio_midia", "documento_usuario", "documento_usuario_acesso", "story_anuncio", "revisao_anuncio", "anuncio_midia_revisao", "decisao_moderacao",
  "beneficio_premium", "beneficio_premium_opcao", "grupo_ativacao_beneficio", "ativacao_beneficio", "movimento_credito", "saldo_credito_usuario",
  "plano_credito", "pagamento", "pagamento_evento", "pagamento_webhook", "pagamento_conciliacao",
  "evento_visualizacao", "agregado_visualizacao_diaria", "clique_whatsapp", "agregado_clique_whatsapp_diario", "evento_verificacao_etaria",
  "seo_url", "seo_metadado", "seo_redirect", "seo_conteudo_pagina", "banner_espaco", "banner", "banner_versao",
  "comercial_status", "comercial_contato", "comercial_interacao", "ticket_suporte", "mensagem_suporte",
  "auditoria_evento", "outbox_evento", "backup_politica", "backup_execucao", "backup_artefato", "backup_teste_restauracao",
  "importacao_execucao", "importacao_mapeamento", "importacao_pendencia", "stg_usuario", "stg_anuncio", "stg_localidade", "stg_midia", "stg_story", "stg_pagamento", "stg_credito", "stg_premium", "stg_url"
)
$missingTables = @($expectedTables | Where-Object { -not ($tables -contains $_) })
Add-Check "tabelas esperadas presentes" ($missingTables.Count -eq 0) "ausentes: $($missingTables -join ', ')"

Add-Check "Premium modelado sem paywall agressivo" (-not ($textNoComments -match '(?i)limite_diario|limite_contato|limite_whatsapp|paywall')) "beneficios sao aditivos"
Add-Check "metricas preservaveis modeladas" (($tables -contains "evento_visualizacao") -and ($tables -contains "clique_whatsapp")) "visualizacoes e cliques WhatsApp"

$failed = @($checks | Where-Object { $_.Resultado -ne "OK" })

Write-Host "Validacao estatica de migrations SQL"
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

Write-Host "Validacao estatica concluida sem falhas. Nenhum banco foi acessado."
exit 0
