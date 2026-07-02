Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

$entityRel = "backend/src/main/java/br/com/topsdojob/v3/persistence/entity"
$repositoryRel = "backend/src/main/java/br/com/topsdojob/v3/persistence/repository"
$migrationRel = "backend/src/main/resources/db/migration"
$entityDir = Join-Path $repoRoot ($entityRel -replace '/', [IO.Path]::DirectorySeparatorChar)
$repositoryDir = Join-Path $repoRoot ($repositoryRel -replace '/', [IO.Path]::DirectorySeparatorChar)
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

function Test-AllowedBinaryClassificationMigration {
  param([string]$RelPath)

  $allowed = @(
    "backend/src/main/resources/db/migration/V004__anuncios.sql",
    "backend/src/main/resources/db/migration/V005__midia_stories_documentos.sql",
    "backend/src/main/resources/db/migration/V009__metricas.sql"
  )
  if ($allowed -notcontains $RelPath) {
    return $false
  }

  $full = Join-Path $repoRoot ($RelPath -replace '/', [IO.Path]::DirectorySeparatorChar)
  if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
    return $false
  }

  $text = [System.IO.File]::ReadAllText($full)
  return -not ($text -match '(SAFE|SEMI|ADULT)')
}

Add-Check "diretorio de entidades JPA existe" (Test-Path -LiteralPath $entityDir -PathType Container) $entityRel
Add-Check "diretorio de repositories JPA existe" (Test-Path -LiteralPath $repositoryDir -PathType Container) $repositoryRel

$entityFiles = @()
if (Test-Path -LiteralPath $entityDir -PathType Container) {
  $entityFiles = @(Get-ChildItem -LiteralPath $entityDir -Recurse -File -Filter "*Entity.java" | Sort-Object FullName)
}
Add-Check "entidades JPA encontradas" ($entityFiles.Count -gt 0) "total: $($entityFiles.Count)"

$entityAnnotationOk = $true
$tableAnnotationOk = $true
$constructorOk = $true
$columnAnnotationOk = $true
$getterOk = $true
$jsonbOk = $true
$bigDecimalOk = $true
$entityFailures = New-Object System.Collections.Generic.List[string]

foreach ($file in $entityFiles) {
  $rel = Get-RelativePath $file.FullName
  $text = [System.IO.File]::ReadAllText($file.FullName)
  $className = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)

  if ($text -notmatch '(?m)^\s*@Entity\s*$') {
    $entityAnnotationOk = $false
    $entityFailures.Add("$rel sem @Entity")
  }
  if ($text -notmatch '(?m)^\s*@Table\(name = "[a-z0-9_]+"\)\s*$') {
    $tableAnnotationOk = $false
    $entityFailures.Add("$rel sem @Table explicito")
  }
  if ($text -notmatch "(?m)^\s+protected\s+$([regex]::Escape($className))\s*\(\)\s*\{") {
    $constructorOk = $false
    $entityFailures.Add("$rel sem construtor protegido explicito")
  }

  $topLevelText = $text
  $nestedIndex = $text.IndexOf("`n  public static class ")
  if ($nestedIndex -ge 0) {
    $topLevelText = $text.Substring(0, $nestedIndex)
  }

  $fields = [regex]::Matches($topLevelText, '(?ms)(?<annotations>(?:^\s+@[^\r\n]+\r?\n)+)^\s+private\s+(?<type>[A-Za-z0-9_.$<>]+)\s+(?<name>[a-z][A-Za-z0-9_]*)\s*;')
  foreach ($field in $fields) {
    $annotations = [string]$field.Groups["annotations"].Value
    $type = [string]$field.Groups["type"].Value
    $name = [string]$field.Groups["name"].Value
    $getterName = "get$($name.Substring(0, 1).ToUpperInvariant())$($name.Substring(1))"

    if ($annotations -notmatch '@Column\(') {
      $columnAnnotationOk = $false
      $entityFailures.Add("$rel campo $name sem @Column")
    }
    if ($topLevelText -notmatch "(?m)^\s+public\s+$([regex]::Escape($type))\s+$getterName\s*\(\)\s*\{") {
      $getterOk = $false
      $entityFailures.Add("$rel campo $name sem getter publico")
    }
    if ($annotations -match 'columnDefinition = "jsonb"' -and $annotations -notmatch '@JdbcTypeCode\(SqlTypes\.JSON\)') {
      $jsonbOk = $false
      $entityFailures.Add("$rel campo $name jsonb sem @JdbcTypeCode(SqlTypes.JSON)")
    }
    if ($type -eq "BigDecimal" -and $annotations -notmatch 'precision\s*=\s*[0-9]+' -and $annotations -notmatch 'scale\s*=\s*[0-9]+') {
      $bigDecimalOk = $false
      $entityFailures.Add("$rel campo $name BigDecimal sem precision/scale")
    }
  }
}

Add-Check "entidades possuem @Entity" $entityAnnotationOk "arquivos: $($entityFiles.Count)"
Add-Check "entidades possuem @Table explicito" $tableAnnotationOk "nomes fisicos preservados"
Add-Check "entidades possuem construtor protected explicito" $constructorOk "exigencia JPA"
Add-Check "campos persistidos possuem @Column" $columnAnnotationOk "campos top-level"
Add-Check "campos persistidos possuem getters publicos" $getterOk "sem setters publicos em massa"
Add-Check "campos jsonb possuem @JdbcTypeCode(SqlTypes.JSON)" $jsonbOk "mantendo columnDefinition jsonb"
Add-Check "campos BigDecimal possuem precision/scale" $bigDecimalOk "dinheiro e numericos relevantes"

$persistenceText = ""
if (Test-Path -LiteralPath $entityDir -PathType Container) {
  foreach ($file in Get-ChildItem -LiteralPath $entityDir -Recurse -File -Filter "*.java") {
    $persistenceText += [System.IO.File]::ReadAllText($file.FullName)
    $persistenceText += "`n"
  }
}
Add-Check "sem CascadeType" (-not ($persistenceText -match '\bCascadeType\b')) "cascade proibido nesta fase"
Add-Check "sem orphanRemoval" (-not ($persistenceText -match '\borphanRemoval\b')) "orphanRemoval proibido nesta fase"
Add-Check "sem FetchType.EAGER" (-not ($persistenceText -match 'FetchType\s*\.\s*EAGER')) "EAGER proibido nesta fase"
Add-Check "sem setters publicos em entidades" (-not ($persistenceText -match '(?m)^\s+public\s+void\s+set[A-Z]')) "sem mutacao publica em massa"

$repositoryFiles = @()
if (Test-Path -LiteralPath $repositoryDir -PathType Container) {
  $repositoryFiles = @(Get-ChildItem -LiteralPath $repositoryDir -File -Filter "*Repository.java" | Sort-Object FullName)
}
$repositoriesMinimalOk = $true
$repositoryFailures = New-Object System.Collections.Generic.List[string]
foreach ($file in $repositoryFiles) {
  $rel = Get-RelativePath $file.FullName
  $text = [System.IO.File]::ReadAllText($file.FullName)
  if ($text -notmatch 'extends\s+JpaRepository<[^>]+,\s*(UUID|[A-Za-z0-9_]+Entity\.[A-Za-z0-9_]+Id)>') {
    $repositoriesMinimalOk = $false
    $repositoryFailures.Add("$rel nao estende JpaRepository<..., UUID|IdClass>")
  }
  if ($text -match '@Query|nativeQuery|EntityManager|JdbcTemplate|createQuery|@Modifying|@Lock|delete[A-Z]|\bdelete\s*\(|remove[A-Z]|\bremove\s*\(|update[A-Z]|\bupdate\s*\(') {
    $repositoriesMinimalOk = $false
    $repositoryFailures.Add("$rel contem query nativa, escrita, lock ou acesso manual")
  }
}
Add-Check "repositories continuam read-only simples" $repositoriesMinimalOk "total: $($repositoryFiles.Count)"

$changedFiles = @(& git diff --name-only HEAD --)
$changedJava = @($changedFiles | Where-Object { $_ -like "backend/src/main/java/*.java" -or $_ -like "backend/src/main/java/*" })
$changedSqlOrMigration = @($changedFiles | Where-Object {
  ($_ -like "$migrationRel/*" -or $_ -like "*.sql") -and
  $_ -notlike "scripts/local/dados-sinteticos/*" -and
  -not (Test-AllowedBinaryClassificationMigration $_)
})
Add-Check "sem SQL de schema indevido ou migration nao autorizada" ($changedSqlOrMigration.Count -eq 0) "arquivos: $($changedSqlOrMigration.Count)"

$changedJavaText = ""
foreach ($rel in $changedJava) {
  $full = Join-Path $repoRoot ($rel -replace '/', [IO.Path]::DirectorySeparatorChar)
  if (Test-Path -LiteralPath $full -PathType Leaf) {
    $changedJavaText += [System.IO.File]::ReadAllText($full)
    $changedJavaText += "`n"
  }
}

$changedControllerService = @($changedJava | Where-Object {
  $_ -notlike "backend/src/main/java/br/com/topsdojob/v3/persistence/*" -and $_ -match '(Controller|Service|Importador|Importer)'
})
$controllerServiceNaoPermitido = @($changedControllerService | Where-Object {
  $_ -notlike "backend/src/main/java/br/com/topsdojob/v3/application/publico/service/*" -and
  $_ -notlike "backend/src/main/java/br/com/topsdojob/v3/application/admin/auth/*" -and
  $_ -notlike "backend/src/main/java/br/com/topsdojob/v3/application/admin/moderacao/*" -and
  $_ -notlike "backend/src/main/java/br/com/topsdojob/v3/application/admin/readonly/*" -and
  $_ -notlike "backend/src/main/java/br/com/topsdojob/v3/web/admin/auth/*" -and
  $_ -notlike "backend/src/main/java/br/com/topsdojob/v3/web/admin/moderacao/*" -and
  $_ -notlike "backend/src/main/java/br/com/topsdojob/v3/web/admin/readonly/*" -and
  $_ -notlike "backend/src/main/java/br/com/topsdojob/v3/security/*" -and
  $_ -notlike "backend/src/main/java/br/com/topsdojob/v3/web/publico/*" -and
  $_ -notlike "backend/src/main/java/br/com/topsdojob/v3/platform/*"
})
$publicoReadOnlyText = ""
foreach ($rel in @($changedJava | Where-Object {
  $_ -like "backend/src/main/java/br/com/topsdojob/v3/application/publico/*" -or
  $_ -like "backend/src/main/java/br/com/topsdojob/v3/web/publico/*"
})) {
  $full = Join-Path $repoRoot ($rel -replace '/', [IO.Path]::DirectorySeparatorChar)
  if (Test-Path -LiteralPath $full -PathType Leaf) {
    $publicoReadOnlyText += [System.IO.File]::ReadAllText($full)
    $publicoReadOnlyText += "`n"
  }
}
Add-Check "controllers/services limitados a API publica de leitura" ($controllerServiceNaoPermitido.Count -eq 0) "fora do escopo publico: $($controllerServiceNaoPermitido.Count)"
$publicoSemMetricasLocais = $publicoReadOnlyText
$publicoSemMetricasLocais = $publicoSemMetricasLocais -replace '@PostMapping\(\s*"/\{slug\}/visualizacao"\s*\)', ''
$publicoSemMetricasLocais = $publicoSemMetricasLocais -replace '@PostMapping\(\s*"/\{slug\}/clique-whatsapp"\s*\)', ''
$publicoSemMetricasLocais = $publicoSemMetricasLocais -replace '@PostMapping\(\s*"/confirmar"\s*\)', ''
Add-Check "sem endpoint de acao critica na API publica" (-not ($publicoSemMetricasLocais -match '@PostMapping|@PutMapping|@PatchMapping|@DeleteMapping|@Modifying|@Lock|Pagamento|Pix|/moderacao|Aprovacao|Reprovacao|Aprovar|Reprovar|Admin')) "GET publico e POST local de metrica/WhatsApp"
$changedJavaTextSemContatoAutorizado = $changedJavaText -replace 'https://wa\.me/', ''
Add-Check "sem uso de banco ou rede em Java alterado" (-not ($changedJavaTextSemContatoAutorizado -match 'DriverManager|DataSource|JdbcTemplate|EntityManager|RestTemplate|WebClient|HttpClient|Socket|URLConnection|https?://')) "camada persistence passiva"
Add-Check "sem indicio de dado real em Java alterado" (-not ($changedJavaText -match '\b[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2}\b|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}')) "sem CPF/e-mail literal"

foreach ($failure in $entityFailures) {
  Write-Host "FALHA_ENTIDADE: $failure"
}
foreach ($failure in $repositoryFailures) {
  Write-Host "FALHA_REPOSITORY: $failure"
}

$checks | Format-Table -AutoSize
$failed = @($checks | Where-Object { $_.Resultado -ne "OK" })
Write-Host "Total de verificacoes: $($checks.Count)"
Write-Host "Verificacoes OK: $(($checks | Where-Object { $_.Resultado -eq "OK" }).Count)"
Write-Host "Verificacoes com falha: $($failed.Count)"

if ($failed.Count -gt 0) {
  Write-Host "VALIDATION_RESULT=FALHA_PERSISTENCIA_JPA_ESTATICA"
  exit 1
}

Write-Host "VALIDATION_RESULT=OK_PERSISTENCIA_JPA_ESTATICA"
exit 0
