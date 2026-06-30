Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Exit-TopsOperational {
  param([string]$Message)
  Write-Host "ERRO: $Message"
  exit 2
}

function ConvertTo-TopsCommandLineArgument {
  param([AllowNull()][string]$Argument)

  if ($null -eq $Argument) { $Argument = "" }
  if ($Argument.Length -gt 0 -and $Argument -notmatch '[\s"]') { return $Argument }

  $result = '"'
  $backslashes = 0
  foreach ($char in $Argument.ToCharArray()) {
    if ($char -eq [char]92) {
      $backslashes++
      continue
    }
    if ($char -eq [char]34) {
      if ($backslashes -gt 0) { $result += ('\' * ($backslashes * 2)) }
      $result += '\'
      $result += '"'
      $backslashes = 0
      continue
    }
    if ($backslashes -gt 0) { $result += ('\' * $backslashes) }
    $backslashes = 0
    $result += $char
  }
  if ($backslashes -gt 0) { $result += ('\' * ($backslashes * 2)) }
  $result += '"'
  return $result
}

function Join-TopsCommandLineArguments {
  param([string[]]$Arguments)
  return (($Arguments | ForEach-Object { ConvertTo-TopsCommandLineArgument $_ }) -join " ")
}

function Invoke-TopsProcessBytes {
  param(
    [string]$FileName,
    [string[]]$Arguments,
    [string]$WorkingDirectory
  )

  $psi = New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName = $FileName
  $psi.Arguments = Join-TopsCommandLineArguments $Arguments
  if ($WorkingDirectory) { $psi.WorkingDirectory = $WorkingDirectory }
  $psi.UseShellExecute = $false
  $psi.RedirectStandardOutput = $true
  $psi.RedirectStandardError = $true

  $process = New-Object System.Diagnostics.Process
  $process.StartInfo = $psi
  [void]$process.Start()

  $stdoutMemory = New-Object System.IO.MemoryStream
  $stderrMemory = New-Object System.IO.MemoryStream
  $stdoutTask = $process.StandardOutput.BaseStream.CopyToAsync($stdoutMemory)
  $stderrTask = $process.StandardError.BaseStream.CopyToAsync($stderrMemory)
  $process.WaitForExit()
  $stdoutTask.Wait()
  $stderrTask.Wait()

  return [pscustomobject]@{
    ExitCode = $process.ExitCode
    Stdout = $stdoutMemory.ToArray()
    Stderr = $stderrMemory.ToArray()
  }
}

function Get-TopsUtf8StrictEncoding {
  return (New-Object System.Text.UTF8Encoding($false, $true))
}

function ConvertFrom-TopsUtf8Strict {
  param(
    [byte[]]$Bytes,
    [string]$Context
  )
  try {
    return (Get-TopsUtf8StrictEncoding).GetString($Bytes)
  } catch {
    Exit-TopsOperational "UTF-8 invalido ao decodificar $Context."
  }
}

function Split-TopsNulUtf8 {
  param(
    [byte[]]$Bytes,
    [string]$Context
  )
  $encoding = Get-TopsUtf8StrictEncoding
  $items = New-Object System.Collections.Generic.List[string]
  $start = 0
  for ($i = 0; $i -lt $Bytes.Length; $i++) {
    if ($Bytes[$i] -eq 0) {
      if ($i -gt $start) {
        try {
          $items.Add($encoding.GetString($Bytes, $start, $i - $start))
        } catch {
          Exit-TopsOperational "UTF-8 invalido em lista NUL do Git ($Context)."
        }
      }
      $start = $i + 1
    }
  }
  if ($start -lt $Bytes.Length) {
    try {
      $items.Add($encoding.GetString($Bytes, $start, $Bytes.Length - $start))
    } catch {
      Exit-TopsOperational "UTF-8 invalido em lista NUL do Git ($Context)."
    }
  }
  return @($items | ForEach-Object { $_ -replace '\\', '/' })
}

function Invoke-TopsGitBytes {
  param(
    [string[]]$Arguments,
    [string]$WorkingDirectory,
    [string]$Context
  )
  $result = Invoke-TopsProcessBytes -FileName "git" -Arguments $Arguments -WorkingDirectory $WorkingDirectory
  if ($result.ExitCode -ne 0) {
    Exit-TopsOperational "$Context. Git retornou exit code $($result.ExitCode)."
  }
  return ,$result.Stdout
}

function Get-TopsRepoRoot {
  $result = Invoke-TopsProcessBytes -FileName "git" -Arguments @("rev-parse", "--show-toplevel") -WorkingDirectory (Get-Location).Path
  if ($result.ExitCode -ne 0 -or $result.Stdout.Length -eq 0) {
    Exit-TopsOperational "nao foi possivel determinar a raiz do repositorio Git."
  }
  return (ConvertFrom-TopsUtf8Strict -Bytes $result.Stdout -Context "raiz Git").Trim() -replace '\\', '/'
}

function Assert-TopsSafeRelativePath {
  param(
    [AllowNull()][string]$Path,
    [string]$Context = "caminho"
  )
  if ([string]::IsNullOrWhiteSpace($Path)) {
    Exit-TopsOperational "$Context vazio ou invalido."
  }
  $normalized = $Path -replace '\\', '/'
  if ($Path -ne $normalized) {
    Exit-TopsOperational "$Context contem barra invertida: $Path"
  }
  if ([IO.Path]::IsPathRooted($Path) -or $normalized.StartsWith("/") -or $normalized.StartsWith("//") -or $normalized -match '^[A-Za-z]:') {
    Exit-TopsOperational "$Context absoluto bloqueado: $Path"
  }
  if ($normalized.Contains("//")) {
    Exit-TopsOperational "$Context contem segmento vazio: $Path"
  }
  if ($normalized -match '(^|/)\.\.(/|$)') {
    Exit-TopsOperational "$Context contem traversal: $Path"
  }
  foreach ($part in ($normalized -split "/")) {
    if ($part -eq "" -or $part -eq "." -or $part -eq "..") {
      Exit-TopsOperational "$Context contem segmento invalido: $Path"
    }
  }
  return $normalized
}

function Get-TopsStagedFiles {
  param([string]$RepoRoot)
  if ($env:TOPSV3_SIMULAR_FALHA_LISTAR_STAGED -eq "1") {
    Exit-TopsOperational "falha simulada ao listar arquivos staged."
  }
  $bytes = Invoke-TopsGitBytes -Arguments @("diff", "--cached", "--name-only", "-z", "--diff-filter=ACMR") -WorkingDirectory $RepoRoot -Context "nao foi possivel listar arquivos staged"
  return @(Split-TopsNulUtf8 -Bytes $bytes -Context "arquivos staged")
}

function Get-TopsVersionableFiles {
  param([string]$RepoRoot)
  $bytes = Invoke-TopsGitBytes -Arguments @("ls-files", "-z", "--cached", "--others", "--exclude-standard") -WorkingDirectory $RepoRoot -Context "nao foi possivel listar arquivos versionados ou versionaveis"
  return @(Split-TopsNulUtf8 -Bytes $bytes -Context "arquivos versionaveis")
}

function Get-TopsCachedFiles {
  param([string]$RepoRoot)
  $bytes = Invoke-TopsGitBytes -Arguments @("ls-files", "-z", "--cached") -WorkingDirectory $RepoRoot -Context "nao foi possivel listar arquivos no indice"
  return @(Split-TopsNulUtf8 -Bytes $bytes -Context "arquivos no indice")
}

function Get-TopsIndexEntries {
  param([string]$RepoRoot)
  $bytes = Invoke-TopsGitBytes -Arguments @("ls-files", "--stage", "-z") -WorkingDirectory $RepoRoot -Context "nao foi possivel listar modos do indice"
  $items = @(Split-TopsNulUtf8 -Bytes $bytes -Context "modos do indice")
  $entries = New-Object System.Collections.Generic.List[object]
  foreach ($item in $items) {
    $tab = $item.IndexOf("`t")
    if ($tab -lt 0) { Exit-TopsOperational "formato inesperado de entrada do indice Git." }
    $meta = $item.Substring(0, $tab).Split(" ")
    if ($meta.Count -lt 3) { Exit-TopsOperational "metadados incompletos no indice Git." }
    $entries.Add([pscustomobject]@{
      Path = ($item.Substring($tab + 1) -replace '\\', '/')
      Mode = $meta[0]
      ObjectId = $meta[1]
      Stage = $meta[2]
    })
  }
  return $entries.ToArray()
}

function Get-TopsIndexEntryMap {
  param([string]$RepoRoot)
  $map = @{}
  foreach ($entry in @(Get-TopsIndexEntries -RepoRoot $RepoRoot)) {
    $map[$entry.Path] = $entry
  }
  return $map
}

function Assert-TopsAllowedGitMode {
  param(
    [string]$Path,
    [string]$Mode
  )
  if ($Mode -eq "100644" -or $Mode -eq "100755") { return }
  if ($Mode -eq "120000") { Exit-TopsOperational "symlink staged bloqueado: $Path" }
  if ($Mode -eq "160000") { Exit-TopsOperational "gitlink/submodule staged bloqueado: $Path" }
  Exit-TopsOperational "modo Git desconhecido bloqueado ($Mode): $Path"
}

function Get-TopsIndexBytes {
  param(
    [string]$RepoRoot,
    [string]$Path
  )
  if ($env:TOPSV3_SIMULAR_FALHA_BLOB -eq "1") {
    Exit-TopsOperational "falha simulada ao ler blob staged."
  }
  return ,(Invoke-TopsGitBytes -Arguments @("show", ":$Path") -WorkingDirectory $RepoRoot -Context "nao foi possivel ler o blob staged: $Path")
}

function Get-TopsIndexSize {
  param(
    [string]$RepoRoot,
    [string]$Path
  )
  if ($env:TOPSV3_SIMULAR_FALHA_BLOB -eq "1") {
    Exit-TopsOperational "falha simulada ao ler blob staged."
  }
  $bytes = Invoke-TopsGitBytes -Arguments @("cat-file", "-s", ":$Path") -WorkingDirectory $RepoRoot -Context "nao foi possivel obter o tamanho do blob staged: $Path"
  $text = (ConvertFrom-TopsUtf8Strict -Bytes $bytes -Context "tamanho do blob").Trim()
  $size = 0L
  if (-not [Int64]::TryParse($text, [ref]$size)) {
    Exit-TopsOperational "tamanho invalido retornado pelo Git para: $Path"
  }
  return $size
}

function Get-TopsSha256Bytes {
  param([byte[]]$Bytes)
  $sha = [System.Security.Cryptography.SHA256]::Create()
  try {
    return ([BitConverter]::ToString($sha.ComputeHash($Bytes)).Replace("-", "").ToLowerInvariant())
  } finally {
    $sha.Dispose()
  }
}

function Test-TopsBinaryBytes {
  param([byte[]]$Bytes)
  $limit = [Math]::Min($Bytes.Length, 8192)
  for ($i = 0; $i -lt $limit; $i++) {
    if ($Bytes[$i] -eq 0) { return $true }
  }
  return $false
}

function Test-TopsUtf8Bom {
  param([byte[]]$Bytes)
  return ($Bytes.Length -ge 3 -and $Bytes[0] -eq 0xEF -and $Bytes[1] -eq 0xBB -and $Bytes[2] -eq 0xBF)
}

function Test-TopsUtf16OrUtf32Bom {
  param([byte[]]$Bytes)
  if ($Bytes.Length -ge 4) {
    if ($Bytes[0] -eq 0xFF -and $Bytes[1] -eq 0xFE -and $Bytes[2] -eq 0x00 -and $Bytes[3] -eq 0x00) { return $true }
    if ($Bytes[0] -eq 0x00 -and $Bytes[1] -eq 0x00 -and $Bytes[2] -eq 0xFE -and $Bytes[3] -eq 0xFF) { return $true }
  }
  if ($Bytes.Length -ge 2) {
    if ($Bytes[0] -eq 0xFF -and $Bytes[1] -eq 0xFE) { return $true }
    if ($Bytes[0] -eq 0xFE -and $Bytes[1] -eq 0xFF) { return $true }
  }
  return $false
}

function Test-TopsTextualPath {
  param([string]$Path)
  $name = [IO.Path]::GetFileName($Path).ToLowerInvariant()
  $ext = [IO.Path]::GetExtension($Path).ToLowerInvariant()
  if ($name -in @(".editorconfig", ".gitignore", ".gitattributes", ".env.example", ".env.local.example")) { return $true }
  if ($name -like "*.example") { return $true }
  return ($ext -in @(".ps1", ".md", ".sh", ".toml", ".json", ".yml", ".yaml", ".txt", ".csv", ".sql", ".java", ".ts", ".tsx", ".js", ".jsx", ".css", ".html", ".properties"))
}

function Test-TopsNaturalTextPath {
  param([string]$Path)
  $name = [IO.Path]::GetFileName($Path).ToLowerInvariant()
  $ext = [IO.Path]::GetExtension($Path).ToLowerInvariant()
  return ($name -in @("readme.md", "contributing.md", "security.md") -or $ext -in @(".md", ".txt", ".csv"))
}

function Test-TopsMojibakeSequence {
  param([string]$Text)
  for ($i = 0; $i -lt ($Text.Length - 1); $i++) {
    $current = [int][char]$Text[$i]
    $next = [int][char]$Text[$i + 1]
    if ($current -eq 0x00C3 -and $next -in @(0x0080, 0x0081, 0x0087, 0x0089, 0x008D, 0x0093, 0x009A, 0x00A1, 0x00A3, 0x00A7, 0x00A9, 0x00AD, 0x00B3, 0x00BA, 0x00AA, 0x00B5, 0x0192, 0x201A)) {
      return $true
    }
    if ($current -eq 0x00C2 -and ($next -ge 0x0080 -and $next -le 0x00BF)) {
      return $true
    }
  }
  return $false
}

function Test-TopsDangerousExtensionPath {
  param([string]$Path)
  $lower = ($Path -replace '\\', '/').ToLowerInvariant()
  $name = [IO.Path]::GetFileName($lower)
  $dangerous = @(".pem", ".key", ".p12", ".pfx", ".pkcs12", ".jks", ".keystore", ".crt", ".cer", ".der", ".kdbx", ".ovpn", ".pgpass", ".envrc", ".dump", ".backup", ".sqlite", ".sqlite3", ".db", ".log", ".zip", ".7z", ".rar", ".tar", ".tgz", ".gz", ".bz2", ".xz", ".iso")
  foreach ($ext in $dangerous) {
    if ($name.EndsWith($ext) -or $name.Contains($ext + ".")) { return $true }
  }
  if ($name.Contains(".sql.gz") -or $name.Contains(".tar.gz")) { return $true }
  return $false
}

function Test-TopsAllowedExampleTextPath {
  param([string]$Path)
  $name = [IO.Path]::GetFileName($Path).ToLowerInvariant()
  if ($name -match '^\.env(\.[a-z0-9_-]+)*\.example$') { return $true }
  if ($name -match '\.example\.(json|ya?ml|toml|properties|txt|md)$') { return $true }
  if ($name -match '^[^.]+\.example$') { return $true }
  return $false
}

function Add-TopsEncodingFindingsForBytes {
  param(
    [System.Collections.Generic.List[string]]$Findings,
    [string]$Path,
    [byte[]]$Bytes
  )
  if (-not (Test-TopsTextualPath $Path)) { return }
  $ext = [IO.Path]::GetExtension($Path).ToLowerInvariant()
  $name = [IO.Path]::GetFileName($Path).ToLowerInvariant()
  if (Test-TopsUtf16OrUtf32Bom $Bytes) {
    $Findings.Add("${Path}:1 - UTF-16/UTF-32 bloqueado em arquivo textual")
    return
  }
  for ($i = 0; $i -lt $Bytes.Length; $i++) {
    if ($Bytes[$i] -eq 0) {
      $Findings.Add("${Path}:1 - byte NUL bloqueado em arquivo textual")
      return
    }
    if (($Bytes[$i] -lt 0x20) -and ($Bytes[$i] -notin @(0x09, 0x0A, 0x0D))) {
      $Findings.Add("${Path}:1 - controle invalido bloqueado em arquivo textual")
      return
    }
  }
  $evenNul = 0
  $oddNul = 0
  $sample = [Math]::Min($Bytes.Length, 200)
  for ($i = 0; $i -lt $sample; $i++) {
    if ($Bytes[$i] -eq 0) {
      if (($i % 2) -eq 0) { $evenNul++ } else { $oddNul++ }
    }
  }
  if ($evenNul -gt 5 -or $oddNul -gt 5) {
    $Findings.Add("${Path}:1 - padrao provavel de UTF-16 sem BOM")
    return
  }
  $hasBom = Test-TopsUtf8Bom $Bytes
  if ($ext -eq ".ps1" -and -not $hasBom) { $Findings.Add("${Path}:1 - BOM UTF-8 ausente em script PowerShell") }
  if (($ext -in @(".md", ".sh", ".toml", ".json", ".yml", ".yaml") -or $name -in @(".editorconfig", ".gitignore", ".gitattributes")) -and $hasBom) { $Findings.Add("${Path}:1 - BOM UTF-8 nao permitido para este tipo textual") }
  if ($ext -eq ".csv" -and -not $hasBom) { $Findings.Add("${Path}:1 - CSV deve usar UTF-8 com BOM para consumo no Windows") }
  try {
    $text = (Get-TopsUtf8StrictEncoding).GetString($Bytes)
  } catch {
    $Findings.Add("${Path}:0 - UTF-8 invalido")
    return
  }
  $lines = $text -split "`r?`n"
  for ($i = 0; $i -lt $lines.Count; $i++) {
    $lineNumber = $i + 1
    $line = $lines[$i]
    if ($line.IndexOf([char]0xFFFD) -ge 0) { $Findings.Add("${Path}:$lineNumber - caractere de substituicao U+FFFD") }
    if (Test-TopsMojibakeSequence $line) { $Findings.Add("${Path}:$lineNumber - sequencia tipica de mojibake") }
    if ((Test-TopsNaturalTextPath $Path) -and ($line -match '\p{L}\?\p{L}' -or $line -match '\p{L}\?\?+\p{L}')) {
      if ($line -notmatch 'https?://\S+\?\S+') {
        $Findings.Add("${Path}:$lineNumber - ponto de interrogacao corrompido dentro de palavra")
      }
    }
  }
}

function Test-TopsAllowedPlaceholderValue {
  param([string]$Value)
  $clean = ConvertTo-TopsSecretCandidateValue $Value
  return (($clean -in @("CHANGE_ME", "EXEMPLO_NAO_REAL", "<valor-ficticio>", "<valor-fictício>", "xxxxxxxx", "valor_local_ficticio")) -or (Test-TopsAllowedExternalSecretReference $clean))
}

function ConvertTo-TopsSecretCandidateValue {
  param([AllowNull()][string]$Value)
  if ($null -eq $Value) { return "" }
  $clean = $Value.Trim().Trim('"').Trim("'").Trim()
  while ($clean.EndsWith(";") -or $clean.EndsWith(",")) {
    $clean = $clean.Substring(0, $clean.Length - 1).Trim()
  }
  return $clean
}

function Test-TopsAllowedExternalSecretReference {
  param([AllowNull()][string]$Value)
  $clean = ConvertTo-TopsSecretCandidateValue $Value
  $allowedNames = @("EFI_CLIENT_SECRET", "DATABASE_PASSWORD")
  if ($clean -match '^\$\{(?<name>[A-Z0-9_]+)(?::)?\}$') {
    return ($matches["name"] -in $allowedNames)
  }
  if ($clean -match '^\$env:(?<name>[A-Z0-9_]+)$') {
    return ($matches["name"] -in $allowedNames)
  }
  if ($clean -match '^process\.env\.(?<name>[A-Z0-9_]+)$') {
    return ($matches["name"] -in $allowedNames)
  }
  if ($clean -match '^System\.getenv\("(?<name>[A-Z0-9_]+)"\)$') {
    return ($matches["name"] -in $allowedNames)
  }
  if ($clean -match '^environment\.getProperty\("(?<name>[A-Z0-9_]+)"\)$') {
    return ($matches["name"] -in $allowedNames)
  }
  if ($clean -match '^config\.get\("(?<name>[A-Z0-9_]+)"\)$') {
    return ($matches["name"] -in $allowedNames)
  }
  return $false
}

function Test-TopsAllowedCodeReferenceValue {
  param([AllowNull()][string]$Value)
  $clean = ConvertTo-TopsSecretCandidateValue $Value
  if (Test-TopsAllowedExternalSecretReference $clean) { return $true }
  if ($clean -match '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$') { return $true }
  if ($clean -match '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*\(\)$') { return $true }
  return $false
}

function Test-TopsAllowedSensitiveAssignmentValue {
  param(
    [AllowNull()][string]$Value,
    [bool]$Quoted
  )
  if (Test-TopsAllowedPlaceholderValue $Value) { return $true }
  if ($Quoted) { return $false }
  return (Test-TopsAllowedCodeReferenceValue $Value)
}

function Test-TopsSensitiveKey {
  param([string]$Key)
  return ($Key -match '^(password|passwd|pwd|senha|token|access_token|refresh_token|client_secret|clientsecret|api_key|api_token|secret_key|authorization|private_key|certificate|certificado)$' -or $Key -match '(^|[_-])(password|passwd|pwd|senha|token|access[_-]?token|refresh[_-]?token|client[_-]?secret|clientsecret|api[_-]?key|api[_-]?token|secret[_-]?key|authorization|private[_-]?key|certificate|certificado)$')
}

function Add-TopsSecretFinding {
  param(
    [System.Collections.Generic.List[string]]$Findings,
    [string]$Path,
    [int]$Line,
    [string]$Category
  )
  $Findings.Add("${Path}:$Line - $Category - remova o valor e use placeholder aprovado quando for exemplo.")
}

function Add-TopsJsonSecretFindings {
  param(
    [System.Collections.Generic.List[string]]$Findings,
    [string]$Path,
    [object]$Node
  )
  if ($null -eq $Node) { return }
  if ($Node -is [System.Array]) {
    foreach ($item in $Node) { Add-TopsJsonSecretFindings -Findings $Findings -Path $Path -Node $item }
    return
  }
  if ($Node -is [System.Management.Automation.PSCustomObject]) {
    foreach ($prop in $Node.PSObject.Properties) {
      if ((Test-TopsSensitiveKey $prop.Name) -and $null -ne $prop.Value -and -not (Test-TopsAllowedPlaceholderValue ([string]$prop.Value))) {
        Add-TopsSecretFinding $Findings $Path 1 "JSON com chave sensivel"
      }
      Add-TopsJsonSecretFindings -Findings $Findings -Path $Path -Node $prop.Value
    }
  }
}

function Get-TopsSecretFindingsForBytes {
  param(
    [string]$Path,
    [byte[]]$Bytes,
    [string]$Context
  )
  $findings = New-Object System.Collections.Generic.List[string]
  if (Test-TopsTextualPath $Path) {
    $encodingFindings = New-Object System.Collections.Generic.List[string]
    Add-TopsEncodingFindingsForBytes -Findings $encodingFindings -Path $Path -Bytes $Bytes
    if ($encodingFindings.Count -gt 0) {
      foreach ($finding in $encodingFindings) { $findings.Add($finding) }
      return @($findings)
    }
  } elseif (Test-TopsBinaryBytes $Bytes) {
    return @()
  }
  try {
    $text = (Get-TopsUtf8StrictEncoding).GetString($Bytes)
  } catch {
    return @("${Path}:0 - UTF-8 invalido durante scan de secrets")
  }

  $logicalExt = [IO.Path]::GetExtension($Path).ToLowerInvariant()
  if ($logicalExt -eq ".json") {
    try {
      $json = $text | ConvertFrom-Json
      Add-TopsJsonSecretFindings -Findings $findings -Path $Path -Node $json
    } catch {
    }
  }

  $secretRules = @(
    [pscustomobject]@{ Categoria = "private key"; Regex = '-----BEGIN ([A-Z ]*)PRIVATE KEY-----'; GrupoValor = "" },
    [pscustomobject]@{ Categoria = "AWS/S3/R2 access key"; Regex = '\bAKIA[0-9A-Z]{16}\b'; GrupoValor = "" },
    [pscustomobject]@{ Categoria = "Bearer token"; Regex = '(?i)\bbearer\s+(?<valor>[^\s''"]{20,})'; GrupoValor = "valor" },
    [pscustomobject]@{ Categoria = "authorization header"; Regex = '(?i)\bauthorization\b["'']?\s*[:=]\s*["'']?(?<valor>Bearer\s+[^''"#\r\n]{10,})'; GrupoValor = "valor" },
    [pscustomobject]@{ Categoria = "PostgreSQL com senha"; Regex = '(?i)postgres(?:ql)?://[^:\s]+:[^@\s]+@[^/\s]+/[^\s''"]+'; GrupoValor = "" },
    [pscustomobject]@{ Categoria = "JWT hardcoded"; Regex = '\beyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}\b'; GrupoValor = "" },
    [pscustomobject]@{ Categoria = "chave ou certificado codificado"; Regex = '(?i)(certificate|certificado|private[_-]?key|chave[_-]?privada)\s*[:=]\s*(?:"(?<dq>[^"]{10,})"|''(?<sq>[^'']{10,})''|(?<bare>[^#\r\n]{10,}))'; GrupoValor = "valor" },
    [pscustomobject]@{ Categoria = "atribuicao sensivel"; Regex = '(?i)(^|[^\w${])["'']?(?<key>[A-Za-z0-9_-]*(?:client[_-]?secret|clientSecret|access[_-]?token|refresh[_-]?token|api[_-]?key|api[_-]?token|secret[_-]?key|authorization|private[_-]?key|certificate|certificado|secret|senha|password|passwd|pwd|token))["'']?\s*[:=]\s*(?:"(?<dq>[^"]*)"|''(?<sq>[^'']*)''|(?<bare>[^#;\r\n]+))'; GrupoValor = "valor" }
  )

  $lines = $text -split "`r?`n"
  for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    foreach ($rule in $secretRules) {
      $matches = [regex]::Matches($line, $rule.Regex)
      foreach ($match in $matches) {
        $value = $null
        $quoted = $false
        if ($match.Groups["dq"].Success) { $value = $match.Groups["dq"].Value; $quoted = $true }
        elseif ($match.Groups["sq"].Success) { $value = $match.Groups["sq"].Value; $quoted = $true }
        elseif ($match.Groups["bare"].Success) { $value = $match.Groups["bare"].Value.Trim() }
        elseif ($rule.GrupoValor -and $match.Groups[$rule.GrupoValor].Success) { $value = $match.Groups[$rule.GrupoValor].Value }
        if ($rule.Categoria -eq "atribuicao sensivel" -and $value -and (Test-TopsAllowedSensitiveAssignmentValue -Value $value -Quoted $quoted)) { continue }
        if ($rule.Categoria -ne "atribuicao sensivel" -and $value -and (Test-TopsAllowedPlaceholderValue $value)) { continue }
        Add-TopsSecretFinding $findings $Path ($i + 1) $rule.Categoria
      }
    }
    if ($line -match '(?i)["'']?(password|passwd|pwd|senha|token|access_token|refresh_token|client_secret|api_key|secret_key|authorization|private_key|certificate|certificado)["'']?\s*:\s*$') {
      for ($j = $i + 1; $j -lt $lines.Count; $j++) {
        $next = $lines[$j].Trim()
        if ($next -eq "" -or $next -eq "{" -or $next -eq "}") { continue }
        if ($next -match '^["''](?<quoted>[^"'']+)["'']') {
          if (-not (Test-TopsAllowedPlaceholderValue $matches["quoted"])) { Add-TopsSecretFinding $findings $Path ($j + 1) "valor sensivel multiline" }
        } elseif ($next.Length -ge 10) {
          if (-not (Test-TopsAllowedPlaceholderValue $next)) { Add-TopsSecretFinding $findings $Path ($j + 1) "valor sensivel multiline" }
        }
        break
      }
    }
  }
  return @($findings | Sort-Object -Unique)
}

function Assert-TopsNoSecretFindingsForBytes {
  param(
    [string]$Path,
    [byte[]]$Bytes,
    [string]$Context
  )
  $findings = @(Get-TopsSecretFindingsForBytes -Path $Path -Bytes $Bytes -Context $Context)
  if ($findings.Count -gt 0) {
    Write-Host "Scan local encontrou possiveis secrets em $Context. Valores nao foram exibidos:"
    $findings | ForEach-Object { Write-Host " - $_" }
    exit 1
  }
}

function Write-TopsUtf8BomCsv {
  param(
    [string]$Path,
    [object[]]$Rows
  )
  $csv = @($Rows | ConvertTo-Csv -NoTypeInformation)
  [System.IO.File]::WriteAllText($Path, (($csv -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding($true)))
}

function Test-TopsPathStaged {
  param(
    [string]$RepoRoot,
    [string]$Path
  )
  $result = Invoke-TopsProcessBytes -FileName "git" -Arguments @("ls-files", "--cached", "--error-unmatch", "--", $Path) -WorkingDirectory $RepoRoot
  return ($result.ExitCode -eq 0)
}

function Test-TopsPathIgnored {
  param(
    [string]$RepoRoot,
    [string]$Path
  )
  $result = Invoke-TopsProcessBytes -FileName "git" -Arguments @("check-ignore", "-q", "--no-index", "--", $Path) -WorkingDirectory $RepoRoot
  if ($result.ExitCode -eq 0) { return $true }
  if ($result.ExitCode -eq 1) { return $false }
  Exit-TopsOperational "nao foi possivel verificar se o arquivo esta ignorado: $Path"
}

function Find-TopsPowerShell {
  foreach ($candidate in @("pwsh", "powershell.exe", "powershell")) {
    $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
  }
  Exit-TopsOperational "PowerShell não encontrado."
}

function Write-TopsUtf8NoBomLines {
  param(
    [string]$Path,
    [string[]]$Lines
  )
  [System.IO.File]::WriteAllText($Path, (($Lines -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding($false)))
}
