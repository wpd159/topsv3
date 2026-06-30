param(
  [Parameter(Mandatory = $true)]
  [string]$Fase,

  [Parameter(Mandatory = $true)]
  [string]$InventarioInicial,

  [string]$DiretorioRepositorio,

  [string]$DiretorioDestino,

  [string]$ResumoExecucao,

  [string]$MetadadosExecucao
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$scriptsRoot = Split-Path -Parent $scriptDir
. (Join-Path (Join-Path $scriptsRoot "security") "git-staged-utils.ps1")

function Fail-Package {
  param(
    [string]$Message,
    [string]$ZipPath
  )
  if ($ZipPath -and (Test-Path -LiteralPath $ZipPath)) {
    Remove-Item -LiteralPath $ZipPath -Force
  }
  Write-Host "ERRO: $Message"
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
  return ($childFull.Equals($parentFull, [System.StringComparison]::OrdinalIgnoreCase) -or $childFull.StartsWith($parentFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase))
}

function Get-ObjectPropertyValue {
  param(
    [object]$Object,
    [string]$Name
  )
  if ($Object.PSObject.Properties.Name -contains $Name) { return $Object.$Name }
  return ""
}

function Assert-NoUnstagedChanges {
  param([string]$RepoRoot)
  $trackedBytes = Invoke-TopsGitBytes -Arguments @("diff", "--name-only", "-z") -WorkingDirectory $RepoRoot -Context "nao foi possivel verificar alteracoes fora do indice"
  $untrackedBytes = Invoke-TopsGitBytes -Arguments @("ls-files", "--others", "--exclude-standard", "-z") -WorkingDirectory $RepoRoot -Context "nao foi possivel verificar arquivos novos fora do indice"
  $tracked = @(Split-TopsNulUtf8 -Bytes $trackedBytes -Context "alteracoes fora do indice")
  $untracked = @(Split-TopsNulUtf8 -Bytes $untrackedBytes -Context "arquivos novos fora do indice")
  if ($tracked.Count -gt 0 -or $untracked.Count -gt 0) {
    Fail-Package "Existem alterações fora do índice Git. Execute a revisão e o staging antes de gerar o pacote." $null
  }
}

function Test-InventoryBoolean {
  param([string]$Value)
  return ($Value -eq "True" -or $Value -eq "False")
}

function Assert-InventoryNumber {
  param(
    [string]$Value,
    [string]$Field,
    [string]$Path
  )
  $number = 0L
  if (-not [Int64]::TryParse($Value, [ref]$number) -or $number -lt 0) {
    Fail-Package "valor numerico invalido no inventario inicial ($Field): $Path" $null
  }
}

function Assert-ValidatedInventoryFieldSet {
  param(
    [object]$Row,
    [string[]]$Fields,
    [bool]$ShouldExist,
    [string]$Path
  )
  foreach ($field in $Fields) {
    $value = [string](Get-ObjectPropertyValue $Row $field)
    if ($ShouldExist -and [string]::IsNullOrWhiteSpace($value)) {
      Fail-Package "campo obrigatorio vazio no inventario inicial ($field): $Path" $null
    }
    if (-not $ShouldExist -and -not [string]::IsNullOrEmpty($value)) {
      Fail-Package "campo deve ficar vazio no inventario inicial ($field): $Path" $null
    }
  }
}

function Import-ValidatedInitialInventory {
  param([string]$Path)
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    Fail-Package "inventário inicial não encontrado: $Path" $null
  }

  $requiredColumns = @(
    "caminho_relativo",
    "arquivo_presente_no_workspace",
    "arquivo_presente_no_indice",
    "modo_git_indice",
    "tamanho_workspace",
    "tamanho_indice",
    "sha256_workspace",
    "sha256_indice"
  )
  $expectedHeader = '"' + ($requiredColumns -join '","') + '"'

  $bytes = [System.IO.File]::ReadAllBytes($Path)
  if (-not (Test-TopsUtf8Bom $bytes)) {
    Fail-Package "inventario inicial deve estar em UTF-8 com BOM." $null
  }
  try {
    $text = (Get-TopsUtf8StrictEncoding).GetString($bytes)
  } catch {
    Fail-Package "inventário inicial não é UTF-8 válido." $null
  }
  if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) { $text = $text.Substring(1) }

  $rawLines = New-Object System.Collections.Generic.List[string]
  foreach ($line in ($text.Split([string[]]@("`n"), [System.StringSplitOptions]::None))) { $rawLines.Add($line.TrimEnd("`r")) }
  if ($rawLines.Count -gt 0 -and $rawLines[$rawLines.Count - 1] -eq "") { $rawLines.RemoveAt($rawLines.Count - 1) }
  if ($rawLines.Count -lt 1) { Fail-Package "inventario inicial sem cabecalho." $null }
  foreach ($line in $rawLines) {
    if ($line.Trim() -eq "") { Fail-Package "inventario inicial contem linha vazia." $null }
  }
  if ($rawLines[0] -ne $expectedHeader) {
    Fail-Package "inventario inicial possui colunas ausentes, extras ou fora de ordem." $null
  }

  try {
    $rows = @($text | ConvertFrom-Csv)
  } catch {
    Fail-Package "inventario inicial nao pode ser parseado como CSV." $null
  }

  $seen = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::Ordinal)
  $seenCase = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::OrdinalIgnoreCase)
  foreach ($row in $rows) {
    $columns = @($row.PSObject.Properties.Name)
    if (($columns.Count -ne $requiredColumns.Count) -or (@(Compare-Object $requiredColumns $columns).Count -ne 0)) {
      Fail-Package "inventario inicial possui colunas ausentes ou extras." $null
    }
    for ($i = 0; $i -lt $requiredColumns.Count; $i++) {
      if ($columns[$i] -ne $requiredColumns[$i]) {
        Fail-Package "inventario inicial possui colunas fora de ordem." $null
      }
    }

    $rel = Assert-TopsSafeRelativePath -Path ([string]$row.caminho_relativo) -Context "caminho do inventario inicial"
    if (-not $seen.Add($rel)) { Fail-Package "caminho duplicado no inventario inicial: $rel" $null }
    if (-not $seenCase.Add($rel)) { Fail-Package "caminho duplicado por caixa no inventario inicial: $rel" $null }

    $workspaceFlag = [string]$row.arquivo_presente_no_workspace
    $indexFlag = [string]$row.arquivo_presente_no_indice
    if (-not (Test-InventoryBoolean $workspaceFlag)) { Fail-Package "booleano invalido no inventario inicial (arquivo_presente_no_workspace): $rel" $null }
    if (-not (Test-InventoryBoolean $indexFlag)) { Fail-Package "booleano invalido no inventario inicial (arquivo_presente_no_indice): $rel" $null }

    $workspacePresent = ($workspaceFlag -eq "True")
    $indexPresent = ($indexFlag -eq "True")
    Assert-ValidatedInventoryFieldSet -Row $row -Fields @("tamanho_workspace", "sha256_workspace") -ShouldExist:$workspacePresent -Path $rel
    Assert-ValidatedInventoryFieldSet -Row $row -Fields @("modo_git_indice", "tamanho_indice", "sha256_indice") -ShouldExist:$indexPresent -Path $rel

    foreach ($field in @("tamanho_workspace", "tamanho_indice")) {
      $value = [string](Get-ObjectPropertyValue $row $field)
      if (-not [string]::IsNullOrWhiteSpace($value)) { Assert-InventoryNumber -Value $value -Field $field -Path $rel }
    }
    foreach ($field in @("sha256_workspace", "sha256_indice")) {
      $value = [string](Get-ObjectPropertyValue $row $field)
      if (-not [string]::IsNullOrWhiteSpace($value) -and $value -notmatch '^[0-9a-f]{64}$') {
        Fail-Package "SHA-256 invalido no inventario inicial ($field): $rel" $null
      }
    }
    if ($indexPresent) { Assert-TopsAllowedGitMode -Path $rel -Mode ([string]$row.modo_git_indice) }
  }

  return @($rows)
}

function Get-WorkspaceBytes {
  param(
    [string]$RepoRoot,
    [string]$Path
  )
  $full = Join-Path $RepoRoot ($Path -replace '/', [IO.Path]::DirectorySeparatorChar)
  if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
    Fail-Package "arquivo candidato ausente no workspace: $Path" $null
  }
  return ,([System.IO.File]::ReadAllBytes($full))
}

function Test-ProhibitedPackagePath {
  param([string]$Path)
  $lower = ($Path -replace '\\', '/').ToLowerInvariant()
  $name = [IO.Path]::GetFileName($lower)
  if ($lower -like ".git/*" -or $lower -eq ".git") { return $true }
  if ($name -eq ".env" -or ($name -like ".env.*" -and -not (Test-TopsAllowedExampleTextPath $lower))) { return $true }
  if ($lower -match '(^|/)(storage-local|backups-local|uploads|logs|tmp|dumps|dados-reais|relatorios-reais|exports|exportacoes|node_modules|\.next|target|\.cache|\.git)(/|$)') { return $true }
  if (Test-TopsDangerousExtensionPath $lower) { return $true }
  if ($lower -match '(usuarios|usuários|users)[-_]?(export|dump|dados)|((export|dump|dados)[-_]?(usuarios|usuários|users))') { return $true }
  return $false
}

function Export-IndexFile {
  param(
    [string]$RepoRoot,
    [string]$RelativePath,
    [string]$DestinationRoot
  )
  $destination = Join-Path $DestinationRoot ($RelativePath -replace '/', [IO.Path]::DirectorySeparatorChar)
  $parent = Split-Path -Parent $destination
  if ($parent) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  $bytes = Get-TopsIndexBytes -RepoRoot $RepoRoot -Path $RelativePath
  [System.IO.File]::WriteAllBytes($destination, $bytes)
}

function Assert-PackageBytesClean {
  param(
    [string]$LogicalPath,
    [byte[]]$Bytes,
    [string]$ZipPath
  )
  $findings = New-Object System.Collections.Generic.List[string]
  Add-TopsEncodingFindingsForBytes -Findings $findings -Path $LogicalPath -Bytes $Bytes
  foreach ($finding in @(Get-TopsSecretFindingsForBytes -Path $LogicalPath -Bytes $Bytes -Context "pacote")) {
    $findings.Add($finding)
  }
  if ($findings.Count -gt 0) {
    Fail-Package ("arquivo do pacote reprovado pelos scanners: " + (($findings | Sort-Object -Unique) -join "; ")) $ZipPath
  }
}

function Read-Metadata {
  param(
    [string]$Path,
    [string]$FallbackObjective,
    [string]$Fase
  )
  if ($Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
      Fail-Package "arquivo JSON de metadados não encontrado: $Path" $null
    }
    $jsonBytes = [System.IO.File]::ReadAllBytes($Path)
    Assert-PackageBytesClean -LogicalPath "METADADOS-EXECUCAO.json" -Bytes $jsonBytes -ZipPath $null
    return ((ConvertFrom-TopsUtf8Strict -Bytes $jsonBytes -Context "metadados de execucao") | ConvertFrom-Json)
  }
  return [pscustomobject]@{
    projeto = "Tops do Job V3"
    fase = $Fase
    objetivo = $FallbackObjective
    codigo_aplicacao_criado = $null
    migration_criada = $null
    sql_criado = $null
    integracao_externa_acessada = $null
    fase_seguinte_iniciada = $null
    testes_executados = @()
    testes_aprovados = @()
    testes_com_falha = @()
    gitleaks_utilizado = $null
    fallback_utilizado = $null
    commit_executado = $null
    commit_hash = ""
    motivo_sem_commit = ""
  }
}

function Add-SummaryBooleanLine {
  param(
    [System.Collections.Generic.List[string]]$Lines,
    [string]$Label,
    [object]$Value
  )
  if ($null -eq $Value) {
    $Lines.Add("- ${Label}: nao informado")
  } else {
    $Lines.Add("- ${Label}: $Value")
  }
}

function Add-ListSection {
  param(
    [System.Collections.Generic.List[string]]$Lines,
    [string]$Title,
    [object[]]$Items
  )
  $Lines.Add("")
  $Lines.Add("## $Title")
  if ($Items.Count -eq 0) {
    $Lines.Add("- Nenhum")
  } else {
    foreach ($item in $Items) { $Lines.Add("- $item") }
  }
}

function Invoke-Scanner {
  param(
    [string]$PowerShellPath,
    [string]$RepoRoot,
    [string]$ScriptPath
  )
  $result = Invoke-TopsProcessBytes -FileName $PowerShellPath -Arguments @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $ScriptPath) -WorkingDirectory $RepoRoot
  return [pscustomobject]@{
    Comando = "$PowerShellPath -NoProfile -ExecutionPolicy Bypass -File $ScriptPath"
    ExitCode = $result.ExitCode
    Stdout = (ConvertFrom-TopsUtf8Strict -Bytes $result.Stdout -Context "stdout de $ScriptPath")
    Stderr = (ConvertFrom-TopsUtf8Strict -Bytes $result.Stderr -Context "stderr de $ScriptPath")
  }
}

function Get-ZipEntryBytes {
  param(
    [System.IO.Compression.ZipArchive]$Archive,
    [string]$EntryName
  )
  $entry = $Archive.GetEntry($EntryName)
  if (-not $entry) { return $null }
  $stream = $entry.Open()
  try {
    $memory = New-Object System.IO.MemoryStream
    $stream.CopyTo($memory)
    return ,$memory.ToArray()
  } finally {
    $stream.Dispose()
  }
}

function Set-ZipEntryBytes {
  param(
    [string]$ZipPath,
    [string]$EntryName,
    [byte[]]$Bytes
  )
  $archive = [System.IO.Compression.ZipFile]::Open($ZipPath, [System.IO.Compression.ZipArchiveMode]::Update)
  try {
    $existing = $archive.GetEntry($EntryName)
    if ($existing) { $existing.Delete() }
    $entry = $archive.CreateEntry($EntryName)
    $stream = $entry.Open()
    try {
      $stream.Write($Bytes, 0, $Bytes.Length)
    } finally {
      $stream.Dispose()
    }
  } finally {
    $archive.Dispose()
  }
}

function ConvertTo-Utf8Bytes {
  param(
    [string]$Text,
    [bool]$Bom
  )
  return ,((New-Object System.Text.UTF8Encoding($Bom)).GetBytes($Text))
}

function ConvertTo-ManifestCsvBytes {
  param(
    [object[]]$Rows,
    [bool]$Bom
  )
  $csv = @($Rows | ConvertTo-Csv -NoTypeInformation)
  return ,(ConvertTo-Utf8Bytes -Text (($csv -join "`n") + "`n") -Bom $Bom)
}

function Add-ZipEntryText {
  param(
    [string]$ZipPath,
    [string]$EntryName,
    [string]$Text
  )
  Set-ZipEntryBytes -ZipPath $ZipPath -EntryName $EntryName -Bytes (ConvertTo-Utf8Bytes -Text $Text -Bom $false)
}

function Update-ZipForSimulation {
  param(
    [string]$ZipPath,
    [object[]]$ManifestRows
  )
  if ($env:TOPSV3_SIMULAR_ZIP_EXTRA -eq "1") {
    Add-ZipEntryText -ZipPath $ZipPath -EntryName "EXTRA-INVALIDO.txt" -Text "extra"
  }
  if ($env:TOPSV3_SIMULAR_ZIP_TRAVERSAL_SLASH -eq "1") {
    Add-ZipEntryText -ZipPath $ZipPath -EntryName "../escape.txt" -Text "escape"
  }
  if ($env:TOPSV3_SIMULAR_ZIP_TRAVERSAL_BACKSLASH -eq "1") {
    Add-ZipEntryText -ZipPath $ZipPath -EntryName "..\escape.txt" -Text "escape"
  }
  if ($env:TOPSV3_SIMULAR_ZIP_DUP_CASE -eq "1" -and $ManifestRows.Count -gt 0) {
    $first = [string]$ManifestRows[0].caminho_relativo
    Add-ZipEntryText -ZipPath $ZipPath -EntryName $first.ToUpperInvariant() -Text "duplicado"
  }
  if ($env:TOPSV3_SIMULAR_ZIP_HASH_DIVERGENTE -eq "1" -and $ManifestRows.Count -gt 0) {
    Add-ZipEntryText -ZipPath $ZipPath -EntryName ([string]$ManifestRows[0].caminho_relativo) -Text "conteudo divergente"
  }

  $tampered = @($ManifestRows | ForEach-Object {
    [pscustomobject]@{
      caminho_relativo = $_.caminho_relativo
      tipo_alteracao = $_.tipo_alteracao
      tamanho_bytes = $_.tamanho_bytes
      sha256 = $_.sha256
    }
  })
  $changedManifest = $false
  $manifestBytes = $null

  if ($env:TOPSV3_SIMULAR_MANIFEST_HASH -eq "1" -and $tampered.Count -gt 0) {
    $tampered[0].sha256 = ("0" * 64)
    $changedManifest = $true
  }
  if ($env:TOPSV3_SIMULAR_MANIFEST_SIZE -eq "1" -and $tampered.Count -gt 0) {
    $tampered[0].tamanho_bytes = ([int64]$tampered[0].tamanho_bytes + 1)
    $changedManifest = $true
  }
  if ($env:TOPSV3_SIMULAR_MANIFEST_EXTRA_PATH -eq "1") {
    $tampered += [pscustomobject]@{ caminho_relativo = "EXTRA-NAO-EXISTE.md"; tipo_alteracao = "CRIADO"; tamanho_bytes = 1; sha256 = ("1" * 64) }
    $changedManifest = $true
  }
  if ($env:TOPSV3_SIMULAR_MANIFEST_MISSING_PATH -eq "1" -and $tampered.Count -gt 0) {
    $tampered = @($tampered | Select-Object -Skip 1)
    $changedManifest = $true
  }
  if ($env:TOPSV3_SIMULAR_MANIFEST_DUPLICATE -eq "1" -and $tampered.Count -gt 0) {
    $tampered += $tampered[0]
    $changedManifest = $true
  }
  if ($env:TOPSV3_SIMULAR_MANIFEST_BAD_TYPE -eq "1" -and $tampered.Count -gt 0) {
    $tampered[0].tipo_alteracao = "ALTERADO"
    $changedManifest = $true
  }
  if ($env:TOPSV3_SIMULAR_MANIFEST_MISSING_COLUMN -eq "1") {
    $lines = @('"caminho_relativo","tipo_alteracao","sha256"')
    foreach ($row in $tampered) { $lines += ('"{0}","{1}","{2}"' -f $row.caminho_relativo, $row.tipo_alteracao, $row.sha256) }
    $manifestBytes = ConvertTo-Utf8Bytes -Text (($lines -join "`n") + "`n") -Bom $true
  } elseif ($env:TOPSV3_SIMULAR_MANIFEST_EXTRA_COLUMN -eq "1") {
    $lines = @('"caminho_relativo","tipo_alteracao","tamanho_bytes","sha256","extra"')
    foreach ($row in $tampered) { $lines += ('"{0}","{1}","{2}","{3}","x"' -f $row.caminho_relativo, $row.tipo_alteracao, $row.tamanho_bytes, $row.sha256) }
    $manifestBytes = ConvertTo-Utf8Bytes -Text (($lines -join "`n") + "`n") -Bom $true
  } elseif ($env:TOPSV3_SIMULAR_MANIFEST_SEM_BOM -eq "1") {
    $manifestBytes = ConvertTo-ManifestCsvBytes -Rows $tampered -Bom $false
  } elseif ($changedManifest) {
    $manifestBytes = ConvertTo-ManifestCsvBytes -Rows $tampered -Bom $true
  }

  if ($manifestBytes) {
    Set-ZipEntryBytes -ZipPath $ZipPath -EntryName "MANIFESTO-ARQUIVOS.csv" -Bytes $manifestBytes
  }
}

function Test-SafeZipEntryName {
  param(
    [string]$EntryName,
    [System.Collections.Generic.List[string]]$Errors
  )
  if ([string]::IsNullOrWhiteSpace($EntryName)) {
    $Errors.Add("entrada vazia inesperada")
    return $false
  }
  if ($EntryName.Contains("\")) { $Errors.Add("entrada com barra invertida: $EntryName") }
  $normalized = $EntryName -replace '\\', '/'
  if ($normalized.StartsWith("/")) { $Errors.Add("entrada absoluta: $EntryName") }
  if ($normalized.StartsWith("//")) { $Errors.Add("entrada UNC: $EntryName") }
  if ($normalized -match '^[A-Za-z]:/') { $Errors.Add("entrada com drive absoluto: $EntryName") }
  if ($normalized -match '(^|/)\.\.(/|$)') { $Errors.Add("entrada com traversal: $EntryName") }
  if ($normalized.Contains("//")) { $Errors.Add("entrada com separador vazio: $EntryName") }
  foreach ($part in ($normalized.TrimEnd("/") -split "/")) {
    if ($part -eq "" -or $part -eq "." -or $part -eq "..") { $Errors.Add("segmento invalido: $EntryName") }
  }
  return ($Errors.Count -eq 0)
}

function Validate-RealManifest {
  param(
    [string]$ExtractRoot,
    [object[]]$ExpectedRows,
    [System.Collections.Generic.HashSet[string]]$ZipFiles,
    [System.Collections.Generic.List[string]]$Errors
  )
  $manifestPath = Join-Path $ExtractRoot "MANIFESTO-ARQUIVOS.csv"
  if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    $Errors.Add("MANIFESTO-ARQUIVOS.csv ausente")
    return
  }

  $bytes = [System.IO.File]::ReadAllBytes($manifestPath)
  if (-not (Test-TopsUtf8Bom $bytes)) {
    $Errors.Add("MANIFESTO-ARQUIVOS.csv sem BOM UTF-8")
    return
  }
  try {
    $text = (Get-TopsUtf8StrictEncoding).GetString($bytes)
  } catch {
    $Errors.Add("MANIFESTO-ARQUIVOS.csv não é UTF-8 válido")
    return
  }
  if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) { $text = $text.Substring(1) }

  $rawLines = New-Object System.Collections.Generic.List[string]
  foreach ($line in ($text.Split([string[]]@("`n"), [System.StringSplitOptions]::None))) { $rawLines.Add($line.TrimEnd("`r")) }
  if ($rawLines.Count -gt 0 -and $rawLines[$rawLines.Count - 1] -eq "") { $rawLines.RemoveAt($rawLines.Count - 1) }
  foreach ($line in $rawLines) {
    if ($line.Trim() -eq "") { $Errors.Add("linha vazia no manifesto real") }
  }
  if ($rawLines.Count -lt 2) {
    $Errors.Add("manifesto real sem linhas de dados")
    return
  }

  try {
    $realRows = @($text | ConvertFrom-Csv)
  } catch {
    $Errors.Add("manifesto real nao pode ser parseado como CSV")
    return
  }
  if ($realRows.Count -eq 0) {
    $Errors.Add("manifesto real sem registros")
    return
  }

  $requiredColumns = @("caminho_relativo", "tipo_alteracao", "tamanho_bytes", "sha256")
  $actualColumns = @($realRows[0].PSObject.Properties.Name)
  if (($actualColumns.Count -ne $requiredColumns.Count) -or (@(Compare-Object $requiredColumns $actualColumns).Count -ne 0)) {
    $Errors.Add("colunas invalidas no manifesto real")
    return
  }
  for ($i = 0; $i -lt $requiredColumns.Count; $i++) {
    if ($actualColumns[$i] -ne $requiredColumns[$i]) {
      $Errors.Add("ordem invalida de colunas no manifesto real")
      return
    }
  }

  $expectedByPath = @{}
  foreach ($row in $ExpectedRows) { $expectedByPath[[string]$row.caminho_relativo] = $row }
  $seen = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::Ordinal)
  $seenCase = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::OrdinalIgnoreCase)

  foreach ($row in $realRows) {
    $rel = ([string]$row.caminho_relativo) -replace '\\', '/'
    if ([string]::IsNullOrWhiteSpace($row.caminho_relativo)) { $Errors.Add("caminho vazio no manifesto real"); continue }
    if ([string]$row.caminho_relativo -ne $rel) { $Errors.Add("caminho com barra invertida no manifesto real: $($row.caminho_relativo)") }
    if ([IO.Path]::IsPathRooted([string]$row.caminho_relativo) -or $rel.StartsWith("/") -or $rel -match '^[A-Za-z]:/' -or $rel.StartsWith("//")) { $Errors.Add("caminho absoluto no manifesto real: $rel") }
    if ($rel -match '(^|/)\.\.(/|$)') { $Errors.Add("caminho com traversal no manifesto real: $rel") }
    if ($row.tipo_alteracao -notin @("CRIADO", "MODIFICADO")) { $Errors.Add("tipo de alteracao invalido no manifesto real: $rel") }
    $size = 0L
    if (-not [Int64]::TryParse([string]$row.tamanho_bytes, [ref]$size)) { $Errors.Add("tamanho nao numerico no manifesto real: $rel") }
    if ([string]$row.sha256 -notmatch '^[0-9a-f]{64}$') { $Errors.Add("SHA-256 invalido no manifesto real: $rel") }
    if (-not $seen.Add($rel)) { $Errors.Add("caminho duplicado no manifesto real: $rel") }
    if (-not $seenCase.Add($rel)) { $Errors.Add("caminho duplicado por caixa no manifesto real: $rel") }
    if (-not $ZipFiles.Contains($rel)) { $Errors.Add("manifesto declara entrada ausente no ZIP: $rel") }
    if (-not $expectedByPath.ContainsKey($rel)) { $Errors.Add("manifesto declara caminho não esperado: $rel") }

    $filePath = Join-Path $ExtractRoot ($rel -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (Test-Path -LiteralPath $filePath -PathType Leaf) {
      $fileBytes = [System.IO.File]::ReadAllBytes($filePath)
      $actualHash = Get-TopsSha256Bytes $fileBytes
      if ($fileBytes.Length -ne $size) { $Errors.Add("tamanho recalculado diverge do manifesto real: $rel") }
      if ($actualHash -ne [string]$row.sha256) { $Errors.Add("hash recalculado diverge do manifesto real: $rel") }
    }

    if ($expectedByPath.ContainsKey($rel)) {
      $expected = $expectedByPath[$rel]
      if ([string]$expected.tipo_alteracao -ne [string]$row.tipo_alteracao) { $Errors.Add("tipo diverge do manifesto esperado: $rel") }
      if ([int64]$expected.tamanho_bytes -ne $size) { $Errors.Add("tamanho diverge do manifesto esperado: $rel") }
      if ([string]$expected.sha256 -ne [string]$row.sha256) { $Errors.Add("hash diverge do manifesto esperado: $rel") }
    }
  }

  foreach ($expectedPath in $expectedByPath.Keys) {
    if (-not $seen.Contains($expectedPath)) { $Errors.Add("manifesto real omitiu caminho esperado: $expectedPath") }
  }
  if ($realRows.Count -ne $ExpectedRows.Count) { $Errors.Add("quantidade de linhas diverge do manifesto esperado") }
}

function Validate-ZipPackage {
  param(
    [string]$ZipPath,
    [string]$RepoRoot,
    [object[]]$ManifestRows,
    [string[]]$ExpectedControlEntries
  )

  $errors = New-Object System.Collections.Generic.List[string]
  Add-Type -AssemblyName System.IO.Compression
  Add-Type -AssemblyName System.IO.Compression.FileSystem

  if (-not (Test-Path -LiteralPath $ZipPath -PathType Leaf)) { $errors.Add("ZIP não existe") }
  if (Test-PathInside -Child $ZipPath -Parent $RepoRoot) { $errors.Add("ZIP esta dentro do repositorio") }

  $expectedFiles = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::Ordinal)
  foreach ($row in $ManifestRows) { [void]$expectedFiles.Add(($row.caminho_relativo -replace '\\', '/')) }
  foreach ($entry in $ExpectedControlEntries) { [void]$expectedFiles.Add($entry) }

  $zipFiles = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::Ordinal)
  $zipFilesCase = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::OrdinalIgnoreCase)
  $archive = [System.IO.Compression.ZipFile]::Open($ZipPath, [System.IO.Compression.ZipArchiveMode]::Read)
  try {
    foreach ($entry in $archive.Entries) {
      $original = $entry.FullName
      [void](Test-SafeZipEntryName -EntryName $original -Errors $errors)
      $normalized = ($original -replace '\\', '/').TrimEnd("/")
      if (-not $zipFiles.Add($normalized)) { $errors.Add("entrada duplicada: $original") }
      if (-not $zipFilesCase.Add($normalized)) { $errors.Add("entrada duplicada por caixa: $original") }
      if ($entry.Name -ne "" -and -not $expectedFiles.Contains($normalized)) { $errors.Add("entrada extra: $normalized") }
      if ($entry.Name -eq "" -and -not $expectedFiles.Contains($normalized)) { $errors.Add("diretorio extra: $normalized") }
    }
    foreach ($entry in $expectedFiles) {
      if (-not $zipFiles.Contains($entry)) { $errors.Add("entrada ausente: $entry") }
    }
  } finally {
    $archive.Dispose()
  }

  if ($errors.Count -eq 0) {
    $extractRoot = Join-Path $env:TEMP ("topsv3-validar-zip-" + [guid]::NewGuid().ToString())
    try {
      $archive = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
      try {
        foreach ($entry in $archive.Entries) {
          $normalized = ($entry.FullName -replace '\\', '/').TrimEnd("/")
          $destination = Join-Path $extractRoot ($normalized -replace '/', [IO.Path]::DirectorySeparatorChar)
          if (-not (Test-PathInside -Child $destination -Parent $extractRoot)) { $errors.Add("extracao sairia do temporario: $($entry.FullName)") }
        }
      } finally {
        $archive.Dispose()
      }
      if ($errors.Count -eq 0) {
        [System.IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $extractRoot)
      }

      if ($errors.Count -eq 0) {
        foreach ($control in $ExpectedControlEntries) {
          if (-not (Test-Path -LiteralPath (Join-Path $extractRoot $control) -PathType Leaf)) {
            $errors.Add("controle ausente: $control")
          }
        }

        Validate-RealManifest -ExtractRoot $extractRoot -ExpectedRows $ManifestRows -ZipFiles $zipFiles -Errors $errors

        $allFiles = Get-ChildItem -LiteralPath $extractRoot -Recurse -File
        foreach ($file in $allFiles) {
          if (-not (Test-PathInside -Child $file.FullName -Parent $extractRoot)) {
            $errors.Add("arquivo extraido fora do temporario: $($file.FullName)")
            continue
          }
          $rel = $file.FullName.Substring($extractRoot.Length).TrimStart('\', '/') -replace '\\', '/'
          $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
          if ($rel -match '(^|/)\.git(/|$)') { $errors.Add(".git encontrado no ZIP") }
          if (Test-ProhibitedPackagePath $rel) { $errors.Add("entrada proibida no ZIP: $rel") }
          if ($rel -notin $ExpectedControlEntries -and (Test-TopsPathIgnored -RepoRoot $RepoRoot -Path $rel)) { $errors.Add("arquivo ignorado no ZIP: $rel") }
          try {
            Assert-PackageBytesClean -LogicalPath $rel -Bytes $bytes -ZipPath $ZipPath
          } catch {
            $errors.Add("scanner do pacote falhou para: $rel")
          }
        }
      }
    } finally {
      if (Test-Path -LiteralPath $extractRoot) { Remove-Item -LiteralPath $extractRoot -Recurse -Force }
    }
  }

  if ($errors.Count -gt 0) {
    return [pscustomobject]@{ Valido = $false; Erros = @($errors | Sort-Object -Unique) }
  }
  return [pscustomobject]@{ Valido = $true; Erros = @() }
}

if ($DiretorioRepositorio) { Set-Location -LiteralPath $DiretorioRepositorio }
$repoRoot = Get-TopsRepoRoot
Set-Location -LiteralPath $repoRoot

if (-not $DiretorioDestino) { $DiretorioDestino = [Environment]::GetFolderPath("Desktop") }
if ([string]::IsNullOrWhiteSpace($DiretorioDestino)) { Fail-Package "nao foi possivel resolver a Area de Trabalho." $null }
New-Item -ItemType Directory -Path $DiretorioDestino -Force | Out-Null
if (Test-PathInside -Child $DiretorioDestino -Parent $repoRoot) {
  Fail-Package "DiretorioDestino nao pode ficar dentro do repositorio." $null
}

$initialRows = Import-ValidatedInitialInventory -Path $InventarioInicial
Assert-NoUnstagedChanges -RepoRoot $repoRoot

$metadata = Read-Metadata -Path $MetadadosExecucao -FallbackObjective $ResumoExecucao -Fase $Fase
$initialByPath = @{}
foreach ($row in $initialRows) { $initialByPath[$row.caminho_relativo] = $row }

$indexMap = Get-TopsIndexEntryMap -RepoRoot $repoRoot
$finalFiles = @(Get-TopsVersionableFiles -RepoRoot $repoRoot)
$removed = New-Object System.Collections.Generic.List[string]
foreach ($row in $initialRows) {
  if ($finalFiles -notcontains $row.caminho_relativo) { $removed.Add($row.caminho_relativo) }
}

$candidates = New-Object System.Collections.Generic.List[object]
foreach ($file in ($finalFiles | Sort-Object)) {
  $file = Assert-TopsSafeRelativePath -Path $file -Context "arquivo final"
  if (-not (Test-TopsPathStaged -RepoRoot $repoRoot -Path $file)) { continue }
  if (-not $indexMap.ContainsKey($file)) { Fail-Package "arquivo sem entrada no indice Git: $file" $null }
  $mode = $indexMap[$file].Mode
  Assert-TopsAllowedGitMode -Path $file -Mode $mode
  $indexBytes = Get-TopsIndexBytes -RepoRoot $repoRoot -Path $file
  $indexHash = Get-TopsSha256Bytes $indexBytes
  $workspaceBytes = Get-WorkspaceBytes -RepoRoot $repoRoot -Path $file
  $workspaceHash = Get-TopsSha256Bytes $workspaceBytes

  if ($workspaceHash -ne $indexHash) {
    Fail-Package "ha diferenca entre workspace e indice Git para: $file" $null
  }

  $type = $null
  if (-not $initialByPath.ContainsKey($file) -or (Get-ObjectPropertyValue $initialByPath[$file] "arquivo_presente_no_indice") -ne "True") {
    $type = "CRIADO"
  } elseif ((Get-ObjectPropertyValue $initialByPath[$file] "sha256_indice") -ne $indexHash -or (Get-ObjectPropertyValue $initialByPath[$file] "modo_git_indice") -ne $mode) {
    $type = "MODIFICADO"
  }
  if (-not $type) { continue }

  $full = Join-Path $repoRoot ($file -replace '/', [IO.Path]::DirectorySeparatorChar)
  if (-not (Test-PathInside -Child $full -Parent $repoRoot)) { Fail-Package "arquivo candidato fora da raiz: $file" $null }
  if (Test-TopsPathIgnored -RepoRoot $repoRoot -Path $file) { Fail-Package "arquivo candidato esta ignorado: $file" $null }
  if (Test-ProhibitedPackagePath $file) { Fail-Package "arquivo proibido nao pode entrar no pacote: $file" $null }

  $candidates.Add([pscustomobject]@{
    caminho_relativo = $file
    tipo_alteracao = $type
    tamanho_bytes = $indexBytes.Length
    sha256 = $indexHash
    modo_git = $mode
  })
}

if ($candidates.Count -eq 0) { Fail-Package "nenhum arquivo criado ou modificado no indice desde o inventario inicial." $null }

$ps = Find-TopsPowerShell
$validationCommands = New-Object System.Collections.Generic.List[object]
foreach ($script in @("scripts/security/verificar-codificacao.ps1", "scripts/security/verificar-arquivos-proibidos.ps1", "scripts/security/verificar-segredos.ps1")) {
  $result = Invoke-Scanner -PowerShellPath $ps -RepoRoot $repoRoot -ScriptPath $script
  $validationCommands.Add($result)
  if ($result.ExitCode -ne 0) { Fail-Package "validacao falhou antes do pacote: $script (exit $($result.ExitCode))" $null }
}

$timestamp = Get-Date -Format "yyyy-MM-dd-HHmmss-fff"
$zipName = "topsv3-fase-$Fase-$timestamp.zip"
$zipPath = Join-Path $DiretorioDestino $zipName
if (Test-Path -LiteralPath $zipPath) {
  $zipName = "topsv3-fase-$Fase-$timestamp-$([guid]::NewGuid().ToString('N')).zip"
  $zipPath = Join-Path $DiretorioDestino $zipName
}

$tempRoot = Join-Path $env:TEMP ("topsv3-pacote-" + [guid]::NewGuid().ToString())
$payloadRoot = Join-Path $tempRoot "payload"
New-Item -ItemType Directory -Path $payloadRoot -Force | Out-Null

try {
  foreach ($candidate in $candidates) {
    Export-IndexFile -RepoRoot $repoRoot -RelativePath $candidate.caminho_relativo -DestinationRoot $payloadRoot
  }

  $manifestRows = New-Object System.Collections.Generic.List[object]
  foreach ($candidate in $candidates | Sort-Object caminho_relativo) {
    $exported = Join-Path $payloadRoot ($candidate.caminho_relativo -replace '/', [IO.Path]::DirectorySeparatorChar)
    $bytes = [System.IO.File]::ReadAllBytes($exported)
    $manifestRows.Add([pscustomobject]@{
      caminho_relativo = $candidate.caminho_relativo
      tipo_alteracao = $candidate.tipo_alteracao
      tamanho_bytes = $bytes.Length
      sha256 = Get-TopsSha256Bytes $bytes
    })
  }

  $manifestPath = Join-Path $payloadRoot "MANIFESTO-ARQUIVOS.csv"
  Write-TopsUtf8BomCsv -Path $manifestPath -Rows @($manifestRows.ToArray())

  $created = @($manifestRows | Where-Object { $_.tipo_alteracao -eq "CRIADO" })
  $modified = @($manifestRows | Where-Object { $_.tipo_alteracao -eq "MODIFICADO" })
  $branch = (ConvertFrom-TopsUtf8Strict -Bytes (Invoke-TopsGitBytes -Arguments @("branch", "--show-current") -WorkingDirectory $repoRoot -Context "nao foi possivel obter branch") -Context "branch").Trim()

  $summaryLines = New-Object System.Collections.Generic.List[string]
  $summaryLines.Add("# Resumo da entrega")
  $summaryLines.Add("")
  $summaryLines.Add("- Projeto: $($metadata.projeto)")
  $summaryLines.Add("- Fase: $Fase")
  $summaryLines.Add("- Data e hora: $(Get-Date -Format o)")
  $summaryLines.Add("- Objetivo: $($metadata.objetivo)")
  $summaryLines.Add("- Workspace: $repoRoot")
  $summaryLines.Add("- Branch Git: $branch")
  $summaryLines.Add("- Arquivos criados: $($created.Count)")
  $summaryLines.Add("- Arquivos modificados: $($modified.Count)")
  $summaryLines.Add("- Arquivos removidos: $($removed.Count)")
  Add-SummaryBooleanLine $summaryLines "codigo_aplicacao_criado" $metadata.codigo_aplicacao_criado
  Add-SummaryBooleanLine $summaryLines "migration_criada" $metadata.migration_criada
  Add-SummaryBooleanLine $summaryLines "sql_criado" $metadata.sql_criado
  Add-SummaryBooleanLine $summaryLines "integracao_externa_acessada" $metadata.integracao_externa_acessada
  Add-SummaryBooleanLine $summaryLines "fase_seguinte_iniciada" $metadata.fase_seguinte_iniciada
  Add-SummaryBooleanLine $summaryLines "gitleaks_utilizado" $metadata.gitleaks_utilizado
  Add-SummaryBooleanLine $summaryLines "fallback_utilizado" $metadata.fallback_utilizado
  Add-SummaryBooleanLine $summaryLines "commit_executado" $metadata.commit_executado
  $summaryLines.Add("- commit_hash: $($metadata.commit_hash)")
  $summaryLines.Add("- motivo_sem_commit: $($metadata.motivo_sem_commit)")
  Add-ListSection $summaryLines "Testes executados" @($metadata.testes_executados)
  Add-ListSection $summaryLines "Testes aprovados" @($metadata.testes_aprovados)
  Add-ListSection $summaryLines "Testes com falha" @($metadata.testes_com_falha)
  Add-ListSection $summaryLines "Arquivos criados" @($created | ForEach-Object { $_.caminho_relativo })
  Add-ListSection $summaryLines "Arquivos modificados" @($modified | ForEach-Object { $_.caminho_relativo })
  Add-ListSection $summaryLines "Arquivos removidos" @($removed)
  Write-TopsUtf8NoBomLines -Path (Join-Path $payloadRoot "RESUMO-ENTREGA.md") -Lines @($summaryLines)

  $validationLines = New-Object System.Collections.Generic.List[string]
  $validationLines.Add("# Relatorio de validacoes")
  $validationLines.Add("")
  foreach ($command in $validationCommands) {
    $validationLines.Add("- Comando: ``" + $command.Comando + "``")
    $validationLines.Add("  Exit code: $($command.ExitCode)")
  }
  $validationLines.Add("- Testes aprovados: $(@($metadata.testes_aprovados).Count)")
  $validationLines.Add("- Testes com falha: $(@($metadata.testes_com_falha).Count)")
  $validationLines.Add("- Scanner utilizado: $(if ($metadata.gitleaks_utilizado) { 'gitleaks' } else { 'fallback local' })")
  $validationLines.Add("- Validacao de codificacao: executada antes do pacote")
  $validationLines.Add("- Validacao do manifesto real: CSV extraido do ZIP, importado e comparado")
  $validationLines.Add("- Scan dos controles do pacote: executado antes e depois da extracao")
  $validationLines.Add("- Valores sensíveis: não exibidos")
  Write-TopsUtf8NoBomLines -Path (Join-Path $payloadRoot "RELATORIO-VALIDACOES.md") -Lines @($validationLines)

  if ($env:TOPSV3_SIMULAR_CONTROLE_SECRET -eq "1") {
    $simulatedKey = ("pass" + "word")
    $simulatedValue = ("Valor-Ficticio!" + "Longo#2026")
    [System.IO.File]::AppendAllText((Join-Path $payloadRoot "RELATORIO-VALIDACOES.md"), ("`n" + $simulatedKey + "=`"" + $simulatedValue + "`"`n"), (New-Object System.Text.UTF8Encoding($false)))
  }

  foreach ($file in @(Get-ChildItem -LiteralPath $payloadRoot -Recurse -File)) {
    $rel = $file.FullName.Substring($payloadRoot.Length).TrimStart('\', '/') -replace '\\', '/'
    Assert-PackageBytesClean -LogicalPath $rel -Bytes ([System.IO.File]::ReadAllBytes($file.FullName)) -ZipPath $zipPath
  }

  Add-Type -AssemblyName System.IO.Compression
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zipStream = [IO.File]::Open($zipPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::ReadWrite)
  try {
    $archive = New-Object System.IO.Compression.ZipArchive($zipStream, [IO.Compression.ZipArchiveMode]::Create, $true)
    try {
      $filesToZip = [IO.Directory]::GetFiles($payloadRoot, "*", [IO.SearchOption]::AllDirectories)
      foreach ($fileToZip in $filesToZip) {
        $relative = $fileToZip.Substring($payloadRoot.Length).TrimStart('\', '/') -replace '\\', '/'
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $fileToZip, $relative, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
      }
    } finally {
      $archive.Dispose()
    }
  } finally {
    $zipStream.Dispose()
  }

  Update-ZipForSimulation -ZipPath $zipPath -ManifestRows @($manifestRows.ToArray())

  $validation = Validate-ZipPackage -ZipPath $zipPath -RepoRoot $repoRoot -ManifestRows @($manifestRows.ToArray()) -ExpectedControlEntries @("MANIFESTO-ARQUIVOS.csv", "RESUMO-ENTREGA.md", "RELATORIO-VALIDACOES.md")
  if (-not $validation.Valido) {
    Fail-Package ("validacao interna do ZIP falhou: " + (($validation.Erros) -join "; ")) $zipPath
  }

  $zipBytes = [System.IO.File]::ReadAllBytes($zipPath)
  $zipHash = Get-TopsSha256Bytes $zipBytes
  $zipSize = (Get-Item -LiteralPath $zipPath).Length
  $opened = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
  try {
    $entryCount = @($opened.Entries | Where-Object { $_.Name -ne "" }).Count
  } finally {
    $opened.Dispose()
  }

  Write-Output "ZIP_PATH=$zipPath"
  Write-Output "ZIP_NAME=$zipName"
  Write-Output "ZIP_SIZE_BYTES=$zipSize"
  Write-Output "ZIP_SHA256=$zipHash"
  Write-Output "CREATED_COUNT=$($created.Count)"
  Write-Output "MODIFIED_COUNT=$($modified.Count)"
  Write-Output "ENTRY_COUNT=$entryCount"
  Write-Output "VALIDATION_RESULT=OK"
} finally {
  if (Test-Path -LiteralPath $tempRoot) {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
  }
}
