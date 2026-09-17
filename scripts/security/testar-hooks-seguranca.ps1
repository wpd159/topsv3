param([switch]$GitHubTokenOnly)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "git-staged-utils.ps1")

$sourceRepo = Get-TopsRepoRoot
$repoRoot = $sourceRepo
$ps = Find-TopsPowerShell
$scannerCodificacao = Join-Path $repoRoot "scripts/security/verificar-codificacao.ps1"
$scannerArquivos = Join-Path $repoRoot "scripts/security/verificar-arquivos-proibidos.ps1"
$scannerSegredos = Join-Path $repoRoot "scripts/security/verificar-segredos.ps1"
$tempRoot = Join-Path $env:TEMP ("topsv3-testes-hooks-" + [guid]::NewGuid().ToString())
$results = New-Object System.Collections.Generic.List[object]

function Invoke-TestGit {
  param(
    [string]$Repo,
    [string[]]$GitArgs,
    [switch]$AllowFailure
  )
  $result = Invoke-TopsProcessBytes -FileName "git" -Arguments $GitArgs -WorkingDirectory $Repo
  if (-not $AllowFailure -and $result.ExitCode -ne 0) {
    throw "git $($GitArgs -join ' ') falhou no teste"
  }
  return $result
}

function New-TestRepo {
  $repo = Join-Path $tempRoot ([guid]::NewGuid().ToString())
  New-Item -ItemType Directory -Path $repo -Force | Out-Null
  Invoke-TestGit $repo @("init", "-b", "main") | Out-Null
  Invoke-TestGit $repo @("config", "user.name", "Teste") | Out-Null
  Invoke-TestGit $repo @("config", "user.email", "teste@example.invalid") | Out-Null
  return $repo
}

function Write-TestText {
  param(
    [string]$Repo,
    [string]$Path,
    [string]$Text,
    [switch]$Bom
  )
  $full = Join-Path $Repo ($Path -replace '/', [IO.Path]::DirectorySeparatorChar)
  $parent = Split-Path -Parent $full
  if ($parent) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  [IO.File]::WriteAllText($full, $Text, (New-Object System.Text.UTF8Encoding([bool]$Bom)))
}

function Write-TestBytes {
  param(
    [string]$Repo,
    [string]$Path,
    [byte[]]$Bytes
  )
  $full = Join-Path $Repo ($Path -replace '/', [IO.Path]::DirectorySeparatorChar)
  $parent = Split-Path -Parent $full
  if ($parent) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  [IO.File]::WriteAllBytes($full, $Bytes)
}

function Stage-TestPath {
  param(
    [string]$Repo,
    [string]$Path,
    [switch]$Force
  )
  if ($Force) {
    Invoke-TestGit $Repo @("add", "-f", "--", $Path) | Out-Null
  } else {
    Invoke-TestGit $Repo @("add", "--", $Path) | Out-Null
  }
}

function New-KeyName {
  param([string[]]$Parts)
  return ($Parts -join "")
}

function New-AssignmentLine {
  param(
    [string]$Key,
    [string]$Value,
    [string]$Delimiter = "=",
    [switch]$DoubleQuoted,
    [switch]$SingleQuoted
  )
  if ($DoubleQuoted) { return ($Key + $Delimiter + '"' + $Value + '"') }
  if ($SingleQuoted) { return ($Key + $Delimiter + "'" + $Value + "'") }
  return ($Key + $Delimiter + $Value)
}

function New-RealValue {
  return ("Valor" + "Com" + "Aparencia" + "Real" + "12345")
}

function New-SymbolPassword {
  return ("A!" + "senha" + "#forte" + '$' + "2026")
}

function New-SpacedPassword {
  return ("Frase " + "secreta " + "ficticia " + "2026!")
}

function New-LongSecretValue {
  return ("Valor-Ficticio!" + "Longo#2026")
}

function New-TokenValue {
  return ("tok_" + ("A" * 28) + "123")
}

function New-PrivateKeyText {
  $begin = "-----BEGIN " + "PRIVATE KEY" + "-----"
  $end = "-----END " + "PRIVATE KEY" + "-----"
  return ($begin + "`n" + ("A" * 80) + "`n" + $end + "`n")
}

function New-PostgresUrl {
  return ("postgresql://usuario:" + (New-RealValue) + "@localhost:5432/app")
}

function New-JsonSecretContent {
  param(
    [string]$Key,
    [switch]$Multiline,
    [string]$Value = (New-RealValue)
  )
  if ($Multiline) {
    return ("{`n  `"" + $Key + "`":`n  `"" + $Value + "`"`n}`n")
  }
  return ("{`n  `"" + $Key + "`": `"" + $Value + "`"`n}`n")
}

function New-YamlMultilineSecret {
  param([string]$Key)
  return ($Key + ":`n  `"" + (New-LongSecretValue) + "`"`n")
}

function Invoke-ScannerForTest {
  param(
    [string]$Repo,
    [string]$Scanner,
    [hashtable]$EnvVars
  )
  $effectiveEnv = @{}
  foreach ($key in $EnvVars.Keys) { $effectiveEnv[$key] = $EnvVars[$key] }
  if ($Scanner -eq $scannerSegredos -and -not $effectiveEnv.ContainsKey("TOPSV3_FORCAR_FALLBACK_GITLEAKS")) {
    $effectiveEnv["TOPSV3_FORCAR_FALLBACK_GITLEAKS"] = "1"
  }

  $old = @{}
  foreach ($key in $effectiveEnv.Keys) {
    $old[$key] = [Environment]::GetEnvironmentVariable($key, "Process")
    [Environment]::SetEnvironmentVariable($key, [string]$effectiveEnv[$key], "Process")
  }
  try {
    $result = Invoke-TopsProcessBytes -FileName $ps -Arguments @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $Scanner) -WorkingDirectory $Repo
    return [pscustomobject]@{
      ExitCode = $result.ExitCode
      Stdout = (ConvertFrom-TopsUtf8Strict -Bytes $result.Stdout -Context "stdout scanner")
      Stderr = (ConvertFrom-TopsUtf8Strict -Bytes $result.Stderr -Context "stderr scanner")
    }
  } finally {
    foreach ($key in $effectiveEnv.Keys) {
      [Environment]::SetEnvironmentVariable($key, $old[$key], "Process")
    }
  }
}

function Invoke-AllScanners {
  param([string]$Repo)
  foreach ($scanner in @($scannerCodificacao, $scannerArquivos, $scannerSegredos)) {
    $result = Invoke-ScannerForTest -Repo $Repo -Scanner $scanner -EnvVars @{}
    if ($result.ExitCode -ne 0) { return $result.ExitCode }
  }
  return 0
}

function Add-TestResult {
  param(
    [string]$Scenario,
    [string]$ExpectedScanner,
    [object]$ExpectedExitCode,
    [object]$ActualExitCode,
    [string]$Resultado = ""
  )
  if (-not $Resultado) {
    $Resultado = $(if ($ExpectedExitCode -eq $ActualExitCode) { "OK" } else { "FALHA" })
  }
  $results.Add([pscustomobject]@{
    Cenario = $Scenario
    ScannerEsperado = $ExpectedScanner
    ExitCodeEsperado = $ExpectedExitCode
    ExitCodeObtido = $ActualExitCode
    Resultado = $Resultado
  })
  Write-Host "[$Resultado] $Scenario | esperado: $ExpectedExitCode | obtido: $ActualExitCode"
}

function Run-BlockingCase {
  param(
    [string]$Scenario,
    [string]$ScannerName,
    [string]$ScannerPath,
    [int]$ExpectedExitCode,
    [scriptblock]$Arrange,
    [hashtable]$EnvVars = @{}
  )
  $repo = New-TestRepo
  & $Arrange $repo
  $actual = Invoke-ScannerForTest -Repo $repo -Scanner $ScannerPath -EnvVars $EnvVars
  Add-TestResult $Scenario $ScannerName $ExpectedExitCode $actual.ExitCode
}

function Run-PassingCase {
  param(
    [string]$Scenario,
    [scriptblock]$Arrange
  )
  $repo = New-TestRepo
  & $Arrange $repo
  $actual = Invoke-AllScanners -Repo $repo
  Add-TestResult $Scenario "todos" 0 $actual
}

function Stage-SyntheticModeEntry {
  param(
    [string]$Repo,
    [string]$Path,
    [string]$Mode
  )
  $blobPath = Join-Path $Repo "blob-content.txt"
  [IO.File]::WriteAllText($blobPath, "destino-simulado", (New-Object System.Text.UTF8Encoding($false)))
  $hash = (ConvertFrom-TopsUtf8Strict -Bytes (Invoke-TestGit $Repo @("hash-object", "-w", "blob-content.txt")).Stdout -Context "hash blob").Trim()
  Invoke-TestGit $Repo @("update-index", "--add", "--cacheinfo", "$Mode,$hash,$Path") | Out-Null
}

function Stage-SyntheticBytesEntry {
  param(
    [string]$Repo,
    [string]$Path,
    [byte[]]$Bytes,
    [string]$Mode = "100644"
  )
  Write-TestBytes $Repo $Path $Bytes
  $blobRel = "blob-" + [guid]::NewGuid().ToString() + ".bin"
  $blobPath = Join-Path $Repo $blobRel
  [IO.File]::WriteAllBytes($blobPath, $Bytes)
  $hash = (ConvertFrom-TopsUtf8Strict -Bytes (Invoke-TestGit $Repo @("hash-object", "-w", $blobRel)).Stdout -Context "hash blob").Trim()
  Invoke-TestGit $Repo @("update-index", "--add", "--cacheinfo", "$Mode,$hash,$Path") | Out-Null
}

function Copy-HookRuntime {
  param([string]$Repo)
  New-Item -ItemType Directory -Path (Join-Path $Repo ".githooks") -Force | Out-Null
  New-Item -ItemType Directory -Path (Join-Path $Repo "scripts/security") -Force | Out-Null
  Copy-Item -LiteralPath (Join-Path $sourceRepo ".githooks/pre-commit") -Destination (Join-Path $Repo ".githooks/pre-commit") -Force
  foreach ($file in @(
    "scripts/security/git-staged-utils.ps1",
    "scripts/security/verificar-codificacao.ps1",
    "scripts/security/verificar-arquivos-proibidos.ps1",
    "scripts/security/verificar-segredos.ps1"
  )) {
    Copy-Item -LiteralPath (Join-Path $sourceRepo $file) -Destination (Join-Path $Repo $file) -Force
  }
  Invoke-TestGit $Repo @("config", "core.hooksPath", ".githooks") | Out-Null
}

function Invoke-PreCommitHook {
  param(
    [string]$Repo,
    [string]$WorkingDirectory,
    [hashtable]$EnvVars = @{}
  )
  $gitCommand = Get-Command git -ErrorAction Stop
  $gitDir = Split-Path -Parent $gitCommand.Source
  $gitRoot = Split-Path -Parent $gitDir
  $shCandidates = @(
    (Join-Path $gitDir "sh.exe"),
    (Join-Path $gitRoot "bin\sh.exe"),
    (Join-Path $gitRoot "usr\bin\sh.exe"),
    "sh"
  )
  $shPath = $null
  foreach ($candidate in $shCandidates) {
    if ($candidate -eq "sh") {
      $cmd = Get-Command sh -ErrorAction SilentlyContinue
      if ($cmd) { $shPath = $cmd.Source; break }
    } elseif (Test-Path -LiteralPath $candidate -PathType Leaf) {
      $shPath = $candidate
      break
    }
  }
  if (-not $shPath) { Exit-TopsOperational "sh do Git não encontrado para teste do hook." }

  $old = @{}
  $effectiveEnv = @{ TOPSV3_FORCAR_FALLBACK_GITLEAKS = "1" }
  foreach ($key in $EnvVars.Keys) { $effectiveEnv[$key] = $EnvVars[$key] }
  foreach ($key in $effectiveEnv.Keys) {
    $old[$key] = [Environment]::GetEnvironmentVariable($key, "Process")
    [Environment]::SetEnvironmentVariable($key, [string]$effectiveEnv[$key], "Process")
  }
  try {
    $result = Invoke-TopsProcessBytes -FileName $shPath -Arguments @((Join-Path $Repo ".githooks/pre-commit")) -WorkingDirectory $WorkingDirectory
    return [pscustomobject]@{
      ExitCode = $result.ExitCode
      Stdout = (ConvertFrom-TopsUtf8Strict -Bytes $result.Stdout -Context "stdout hook")
      Stderr = (ConvertFrom-TopsUtf8Strict -Bytes $result.Stderr -Context "stderr hook")
    }
  } finally {
    foreach ($key in $effectiveEnv.Keys) {
      [Environment]::SetEnvironmentVariable($key, $old[$key], "Process")
    }
  }
}

function Test-HookOrder {
  param([string]$Output)
  $one = $Output.IndexOf("1/3")
  $two = $Output.IndexOf("2/3")
  $three = $Output.IndexOf("3/3")
  return ($one -ge 0 -and $two -gt $one -and $three -gt $two)
}

function Test-GitHubTokenReference {
  foreach ($reference in @('${{ github.token }}', '${{github.token}}', '"${{ github.token }}"')) {
    Run-PassingCase "GitHub token nativo completo: $reference" {
      param($repo)
      $line = New-AssignmentLine (New-KeyName @("GH_", "TOKEN")) $reference -Delimiter ": "
      Write-TestText $repo ".github/workflows/test.yml" ("env:`n  " + $line + "`n")
      Stage-TestPath $repo ".github/workflows/test.yml"
    }
  }
  foreach ($reference in @(
    '${{ github.token || ''literal'' }}',
    '${{ github.token }}suffix',
    'prefix${{ github.token }}',
    '${{ github.token_other }}',
    '${{ secrets.UNLISTED }}',
    (New-TokenValue)
  )) {
    Run-BlockingCase "GitHub token literal ou referencia nao permitida" "segredos" $scannerSegredos 1 {
      param($repo)
      $line = New-AssignmentLine (New-KeyName @("GH_", "TOKEN")) $reference -Delimiter ": "
      Write-TestText $repo ".github/workflows/test.yml" ("env:`n  " + $line + "`n")
      Stage-TestPath $repo ".github/workflows/test.yml"
    }
  }
  Run-BlockingCase "GitHub token nativo nao libera outro segredo na mesma linha" "segredos" $scannerSegredos 1 {
    param($repo)
    $line = (New-AssignmentLine (New-KeyName @("GH_", "TOKEN")) '${{ github.token }}' -Delimiter ": ")
    $line += " " + (New-AssignmentLine (New-KeyName @("client", "_secret")) (New-RealValue) -DoubleQuoted)
    Write-TestText $repo ".github/workflows/test.yml" $line
    Stage-TestPath $repo ".github/workflows/test.yml"
  }
}

New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

try {
  Test-GitHubTokenReference
  if ($GitHubTokenOnly) {
    if (@($results | Where-Object { $_.Resultado -ne "OK" }).Count -gt 0) { exit 1 }
    Write-Host "GitHub token reference regression: $($results.Count) scenarios PASS."
    exit 0
  }
  Run-BlockingCase ".env real" "arquivos" $scannerArquivos 1 {
    param($repo)
    Write-TestText $repo ".env" (New-AssignmentLine (New-KeyName @("APP_", "PASSWORD")) (New-RealValue))
    Stage-TestPath $repo ".env"
  }

  Run-BlockingCase ".p12" "arquivos" $scannerArquivos 1 {
    param($repo)
    Write-TestBytes $repo "certificado.p12" ([byte[]](1,2,3,4))
    Stage-TestPath $repo "certificado.p12"
  }

  Run-BlockingCase ".zip.example composto" "arquivos" $scannerArquivos 1 {
    param($repo)
    Write-TestText $repo "pacote.zip.example" "exemplo textual"
    Stage-TestPath $repo "pacote.zip.example"
  }

  Run-BlockingCase ".p12.example composto" "arquivos" $scannerArquivos 1 {
    param($repo)
    Write-TestText $repo "certificado.p12.example" "exemplo textual"
    Stage-TestPath $repo "certificado.p12.example"
  }

  Run-BlockingCase "chave privada" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo "docs/chave.txt" (New-PrivateKeyText)
    Stage-TestPath $repo "docs/chave.txt"
  }

  Run-BlockingCase "URL PostgreSQL com senha" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo "docs/db.txt" (New-PostgresUrl)
    Stage-TestPath $repo "docs/db.txt"
  }

  Run-BlockingCase "bearer token" "segredos" $scannerSegredos 1 {
    param($repo)
    $headerName = "Author" + "ization"
    $scheme = "Bear" + "er"
    Write-TestText $repo "docs/token.txt" ($headerName + ": " + $scheme + " " + (New-TokenValue))
    Stage-TestPath $repo "docs/token.txt"
  }

  Run-BlockingCase "JSON client_secret" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo "config/app.json" (New-JsonSecretContent (New-KeyName @("client", "_secret")))
    Stage-TestPath $repo "config/app.json"
  }

  Run-BlockingCase "JSON password" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo "config/app.json" (New-JsonSecretContent (New-KeyName @("pass", "word")))
    Stage-TestPath $repo "config/app.json"
  }

  Run-BlockingCase "senha com símbolos" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo "config/app.properties" (New-AssignmentLine (New-KeyName @("pass", "word")) (New-SymbolPassword) -DoubleQuoted)
    Stage-TestPath $repo "config/app.properties"
  }

  Run-BlockingCase "senha com espaços entre aspas" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo "config/app.properties" (New-AssignmentLine (New-KeyName @("pass", "word")) (New-SpacedPassword) -SingleQuoted)
    Stage-TestPath $repo "config/app.properties"
  }

  Run-BlockingCase "JSON multiline" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo "config/app.json" (New-JsonSecretContent (New-KeyName @("client", "_secret")) -Multiline -Value (New-LongSecretValue))
    Stage-TestPath $repo "config/app.json"
  }

  Run-BlockingCase "YAML multiline" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo "config/app.yml" (New-YamlMultilineSecret (New-KeyName @("client", "_secret")))
    Stage-TestPath $repo "config/app.yml"
  }

  Run-BlockingCase "segredo e CHANGE_ME na mesma linha" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo "config/app.properties" ((New-AssignmentLine (New-KeyName @("pass", "word")) (New-RealValue)) + " # trocar por CHANGE_ME")
    Stage-TestPath $repo "config/app.properties"
  }

  Run-BlockingCase "linha com formato de regra e secret fora do regex" "segredos" $scannerSegredos 1 {
    param($repo)
    $line = "[pscustomobject]@{ Categoria = `"x`"; Regex = 'abc'; GrupoValor = `"`" }, " + (New-AssignmentLine (New-KeyName @("pass", "word")) (New-RealValue) -DoubleQuoted)
    Write-TestText $repo "docs/regra.txt" $line
    Stage-TestPath $repo "docs/regra.txt"
  }

  Run-BlockingCase "Java token literal hardcoded" "segredos" $scannerSegredos 1 {
    param($repo)
    $tokenKey = New-KeyName @("to", "ken")
    Write-TestText $repo "backend/src/main/java/exemplo/TokenFixture.java" ('class TokenFixture { String ' + $tokenKey + ' = "' + (New-TokenValue) + '"; }')
    Stage-TestPath $repo "backend/src/main/java/exemplo/TokenFixture.java"
  }

  Run-BlockingCase "TypeScript token literal hardcoded" "segredos" $scannerSegredos 1 {
    param($repo)
    $tokenKey = New-KeyName @("to", "ken")
    Write-TestText $repo "frontend/token.ts" ('const ' + $tokenKey + ' = "' + (New-TokenValue) + '";' + "`n")
    Stage-TestPath $repo "frontend/token.ts"
  }

  Run-BlockingCase ".env.production.example com literal bloqueia" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo ".env.production.example" (New-AssignmentLine (New-KeyName @("pass", "word")) (New-RealValue))
    Stage-TestPath $repo ".env.production.example"
  }

  Run-BlockingCase "referência externa e literal na mesma linha bloqueia" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo "config/app.properties" ((New-AssignmentLine (New-KeyName @("client", "_secret")) '${EFI_CLIENT_SECRET}') + " " + (New-AssignmentLine "token" (New-TokenValue) -DoubleQuoted))
    Stage-TestPath $repo "config/app.properties"
  }

  Run-BlockingCase "fallback executado após gitleaks OK simulado" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo "config/app.properties" (New-AssignmentLine (New-KeyName @("pass", "word")) (New-RealValue))
    Stage-TestPath $repo "config/app.properties"
  } @{ TOPSV3_FORCAR_FALLBACK_GITLEAKS = "0"; TOPSV3_SIMULAR_GITLEAKS_EXITCODE = "0" }

  Run-BlockingCase "gitleaks OK simulado com fallback limpo" "segredos" $scannerSegredos 0 {
    param($repo)
    Write-TestText $repo "docs/limpo.md" "sem segredo"
    Stage-TestPath $repo "docs/limpo.md"
  } @{ TOPSV3_FORCAR_FALLBACK_GITLEAKS = "0"; TOPSV3_SIMULAR_GITLEAKS_EXITCODE = "0" }

  Run-BlockingCase "arquivo textual entre 5 e 10 MB contendo segredo" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo "docs/grande.txt" ((("A" * (6MB)) + "`n" + (New-AssignmentLine (New-KeyName @("pass", "word")) (New-RealValue))))
    Stage-TestPath $repo "docs/grande.txt"
  }

  Run-BlockingCase "archive .zip" "arquivos" $scannerArquivos 1 {
    param($repo)
    Write-TestBytes $repo "pacote.zip" ([byte[]](80,75,3,4))
    Stage-TestPath $repo "pacote.zip" -Force
  }

  Run-BlockingCase "archive .7z" "arquivos" $scannerArquivos 1 {
    param($repo)
    Write-TestBytes $repo "pacote.7z" ([byte[]](55,122,188,175))
    Stage-TestPath $repo "pacote.7z" -Force
  }

  Run-BlockingCase "UTF-8 inválido" "codificação" $scannerCodificacao 1 {
    param($repo)
    Write-TestBytes $repo "docs/invalido.md" ([byte[]](0xC3,0x28))
    Stage-TestPath $repo "docs/invalido.md"
  }

  Run-BlockingCase ".properties UTF-16LE com senha" "codificação" $scannerCodificacao 1 {
    param($repo)
    $bytes = [Text.Encoding]::Unicode.GetPreamble() + [Text.Encoding]::Unicode.GetBytes((New-AssignmentLine (New-KeyName @("pass", "word")) (New-RealValue)))
    Write-TestBytes $repo "config/app.properties" $bytes
    Stage-TestPath $repo "config/app.properties"
  }

  Run-BlockingCase ".json UTF-16LE com client_secret" "codificação" $scannerCodificacao 1 {
    param($repo)
    $bytes = [Text.Encoding]::Unicode.GetPreamble() + [Text.Encoding]::Unicode.GetBytes((New-JsonSecretContent (New-KeyName @("client", "_secret"))))
    Write-TestBytes $repo "config/app.json" $bytes
    Stage-TestPath $repo "config/app.json"
  }

  Run-BlockingCase ".md UTF-16BE" "codificação" $scannerCodificacao 1 {
    param($repo)
    $bytes = [Text.Encoding]::BigEndianUnicode.GetPreamble() + [Text.Encoding]::BigEndianUnicode.GetBytes("Texto valido")
    Write-TestBytes $repo "docs/texto.md" $bytes
    Stage-TestPath $repo "docs/texto.md"
  }

  Run-BlockingCase "arquivo textual UTF-8 com NUL" "codificação" $scannerCodificacao 1 {
    param($repo)
    Stage-SyntheticBytesEntry $repo "docs/com-nulo.md" ([byte[]](0x54,0x65,0x78,0x74,0x6f,0x00,0x0a))
  }

  Run-BlockingCase "mojibake" "codificação" $scannerCodificacao 1 {
    param($repo)
    $bad = "Corrupcao: " + ([string][char]0x00C3) + ([string][char]0x00A7)
    Write-TestText $repo "docs/mojibake.md" $bad
    Stage-TestPath $repo "docs/mojibake.md"
  }

  Run-BlockingCase "Responsável corrompido com interrogação" "codificação" $scannerCodificacao 1 {
    param($repo)
    Write-TestText $repo "docs/texto.md" ("Campo: Respons" + "?" + "vel")
    Stage-TestPath $repo "docs/texto.md"
  }

  Run-BlockingCase "aplicação corrompida com dupla interrogação" "codificação" $scannerCodificacao 1 {
    param($repo)
    Write-TestText $repo "docs/texto.md" ("Campo: aplica" + "??" + "o")
    Stage-TestPath $repo "docs/texto.md"
  }

  Run-BlockingCase "Validação em mojibake" "codificação" $scannerCodificacao 1 {
    param($repo)
    $bad = "Valida" + ([string][char]0x00C3) + ([string][char]0x00A7) + ([string][char]0x00C3) + ([string][char]0x00A3) + "o"
    Write-TestText $repo "docs/texto.md" $bad
    Stage-TestPath $repo "docs/texto.md"
  }

  Run-BlockingCase "U+FFFD" "codificação" $scannerCodificacao 1 {
    param($repo)
    Write-TestText $repo "docs/texto.md" ("Texto " + [string][char]0xFFFD)
    Stage-TestPath $repo "docs/texto.md"
  }

  Run-BlockingCase "segredo staged removido do working tree" "segredos" $scannerSegredos 1 {
    param($repo)
    Write-TestText $repo "config/app.properties" (New-AssignmentLine (New-KeyName @("pass", "word")) (New-RealValue))
    Stage-TestPath $repo "config/app.properties"
    Write-TestText $repo "config/app.properties" (New-AssignmentLine (New-KeyName @("pass", "word")) "CHANGE_ME")
  }

  Run-BlockingCase "erro ao listar staged" "codificação" $scannerCodificacao 2 {
    param($repo)
    Write-TestText $repo "docs/a.md" "ok"
    Stage-TestPath $repo "docs/a.md"
  } @{ TOPSV3_SIMULAR_FALHA_LISTAR_STAGED = "1" }

  Run-BlockingCase "erro ao ler blob" "codificação" $scannerCodificacao 2 {
    param($repo)
    Write-TestText $repo "docs/a.md" "ok"
    Stage-TestPath $repo "docs/a.md"
  } @{ TOPSV3_SIMULAR_FALHA_BLOB = "1" }

  Run-BlockingCase "erro operacional do scanner" "segredos" $scannerSegredos 2 {
    param($repo)
    Write-TestText $repo "docs/a.md" "ok"
    Stage-TestPath $repo "docs/a.md"
  } @{ TOPSV3_SIMULAR_ERRO_SCANNER = "1" }

  Run-BlockingCase "modo Git symlink 120000 simulado" "codificação" $scannerCodificacao 2 {
    param($repo)
    Stage-SyntheticModeEntry $repo "link-simulado" "120000"
  }

  Run-BlockingCase "modo Git gitlink 160000 simulado" "codificação" $scannerCodificacao 2 {
    param($repo)
    Invoke-TestGit $repo @("update-index", "--add", "--cacheinfo", "160000,aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa,submodulo") | Out-Null
  }

  Run-PassingCase ".env.example com CHANGE_ME" {
    param($repo)
    Write-TestText $repo ".env.example" (New-AssignmentLine (New-KeyName @("pass", "word")) "CHANGE_ME")
    Stage-TestPath $repo ".env.example"
  }

  Run-PassingCase ".env.production.example com placeholder" {
    param($repo)
    Write-TestText $repo ".env.production.example" (New-AssignmentLine (New-KeyName @("pass", "word")) "CHANGE_ME")
    Stage-TestPath $repo ".env.production.example"
  }

  Run-PassingCase ".env.production.example com referências externas" {
    param($repo)
    Write-TestText $repo ".env.production.example" ((New-AssignmentLine (New-KeyName @("client", "_secret")) '${EFI_CLIENT_SECRET}') + "`n" + (New-AssignmentLine (New-KeyName @("pass", "word")) '${DATABASE_PASSWORD}') + "`n")
    Stage-TestPath $repo ".env.production.example"
  }

  Run-PassingCase "YAML com referência externa default vazia" {
    param($repo)
    Write-TestText $repo "application.example.yml" ((New-KeyName @("client", "_secret")) + ": `${EFI_CLIENT_SECRET:}`n")
    Stage-TestPath $repo "application.example.yml"
  }

  Run-PassingCase "PowerShell com referência env externa" {
    param($repo)
    Write-TestText $repo "scripts/security/env-example.ps1" ((New-KeyName @("client", "_secret")) + '=$env:EFI_CLIENT_SECRET' + "`n") -Bom
    Stage-TestPath $repo "scripts/security/env-example.ps1"
  }

  Run-PassingCase "Java token gerado e System.getenv" {
    param($repo)
    $clientSecretKey = New-KeyName @("client", "Secret")
    $envName = "EFI_" + "CLIENT_" + "SECRET"
    $text = "class TokenFixture { String token = tokenService.generate(); String $clientSecretKey = System.getenv(`"$envName`"); }`n"
    Write-TestText $repo "backend/src/main/java/exemplo/TokenFixture.java" $text
    Stage-TestPath $repo "backend/src/main/java/exemplo/TokenFixture.java"
  }

  Run-PassingCase "TypeScript token de resposta e process.env" {
    param($repo)
    $text = "const token = response.token;`nconst clientSecret = process.env.EFI_CLIENT_SECRET;`n"
    Write-TestText $repo "frontend/token.ts" $text
    Stage-TestPath $repo "frontend/token.ts"
  }

  Run-PassingCase "Java environment.getProperty e config.get externos" {
    param($repo)
    $clientSecretKey = New-KeyName @("client", "Secret")
    $passwordKey = New-KeyName @("pass", "word")
    $efiEnv = "EFI_" + "CLIENT_" + "SECRET"
    $databaseEnv = "DATABASE_" + "PASSWORD"
    $text = "class ConfigFixture { String $clientSecretKey = environment.getProperty(`"$efiEnv`"); String $passwordKey = config.get(`"$databaseEnv`"); }`n"
    Write-TestText $repo "backend/src/main/java/exemplo/ConfigFixture.java" $text
    Stage-TestPath $repo "backend/src/main/java/exemplo/ConfigFixture.java"
  }

  Run-PassingCase "application.example.yml com placeholder" {
    param($repo)
    Write-TestText $repo "application.example.yml" ((New-KeyName @("client", "_secret")) + ": CHANGE_ME")
    Stage-TestPath $repo "application.example.yml"
  }

  Run-PassingCase "certificado-configuracao.example.yml textual" {
    param($repo)
    Write-TestText $repo "certificado-configuracao.example.yml" "arquivo: CHANGE_ME"
    Stage-TestPath $repo "certificado-configuracao.example.yml"
  }

  Run-PassingCase "EfiPixProvider.java" {
    param($repo)
    Write-TestText $repo "backend/src/main/java/exemplo/EfiPixProvider.java" "class EfiPixProvider {}"
    Stage-TestPath $repo "backend/src/main/java/exemplo/EfiPixProvider.java"
  }

  Run-PassingCase "EfiPixConfiguration.java" {
    param($repo)
    Write-TestText $repo "backend/src/main/java/exemplo/EfiPixConfiguration.java" "class EfiPixConfiguration {}"
    Stage-TestPath $repo "backend/src/main/java/exemplo/EfiPixConfiguration.java"
  }

  Run-PassingCase "PasswordService.java" {
    param($repo)
    Write-TestText $repo "backend/src/main/java/exemplo/PasswordService.java" "class PasswordService {}"
    Stage-TestPath $repo "backend/src/main/java/exemplo/PasswordService.java"
  }

  Run-PassingCase "CredentialPolicy.java" {
    param($repo)
    Write-TestText $repo "backend/src/main/java/exemplo/CredentialPolicy.java" "class CredentialPolicy {}"
    Stage-TestPath $repo "backend/src/main/java/exemplo/CredentialPolicy.java"
  }

  Run-PassingCase "documentação menciona client_secret sem valor" {
    param($repo)
    Write-TestText $repo "docs/integracao.md" "O campo client_secret será configurado fora do repositório."
    Stage-TestPath $repo "docs/integracao.md"
  }

  Run-PassingCase "JSON client_secret CHANGE_ME" {
    param($repo)
    Write-TestText $repo "config/app.json" (New-JsonSecretContent (New-KeyName @("client", "_secret")) -Value "CHANGE_ME")
    Stage-TestPath $repo "config/app.json"
  }

  Run-PassingCase "migration Flyway fictícia" {
    param($repo)
    Write-TestText $repo "backend/src/main/resources/db/migration/V1__baseline.sql" "-- fixture ficticia`nselect 1;`n"
    Stage-TestPath $repo "backend/src/main/resources/db/migration/V1__baseline.sql"
  }

  Run-PassingCase "caminho com espaço" {
    param($repo)
    Write-TestText $repo "docs/arquivo com espaco.md" "Texto valido."
    Stage-TestPath $repo "docs/arquivo com espaco.md"
  }

  Run-PassingCase "caminho com acento" {
    param($repo)
    Write-TestText $repo "docs/revisão-segurança.md" "Texto valido com acento."
    Stage-TestPath $repo "docs/revisão-segurança.md"
  }

  Run-PassingCase "perguntas legítimas terminadas em interrogação" {
    param($repo)
    Write-TestText $repo "docs/perguntas.md" "Esta funcionando?`nTudo certo?"
    Stage-TestPath $repo "docs/perguntas.md"
  }

  Run-PassingCase "Âmbito com A circunflexo legítimo" {
    param($repo)
    Write-TestText $repo "docs/ambito.md" (([string][char]0x00C2) + "mbito de aplicação")
    Stage-TestPath $repo "docs/ambito.md"
  }

  Run-PassingCase "URL com query string" {
    param($repo)
    Write-TestText $repo "docs/url.md" "https://exemplo.test/a?b=1"
    Stage-TestPath $repo "docs/url.md"
  }

  Run-PassingCase "TypeScript com operador interrogação" {
    param($repo)
    Write-TestText $repo "src/exemplo.ts" "const nome = item?.nome ? item.nome : `"sem nome`";"
    Stage-TestPath $repo "src/exemplo.ts"
  }

  Run-PassingCase "repositório sem staged files" {
    param($repo)
    Invoke-TestGit $repo @("commit", "--allow-empty", "-m", "baseline") | Out-Null
  }

  Run-PassingCase "scripts de segurança sem segredo" {
    param($repo)
    Write-TestText $repo "scripts/security/exemplo.ps1" "Write-Host `"ok`"`n" -Bom
    Stage-TestPath $repo "scripts/security/exemplo.ps1"
  }

  Run-PassingCase "texto português UTF-8 válido" {
    param($repo)
    Write-TestText $repo "docs/texto.md" "Aplicação, sessão, expiração, catálogo e validação."
    Stage-TestPath $repo "docs/texto.md"
  }

  Run-PassingCase "modo Git 100755 permitido" {
    param($repo)
    Write-TestText $repo "scripts/executavel.sh" "#!/usr/bin/env sh`necho ok`n"
    Stage-TestPath $repo "scripts/executavel.sh"
    Invoke-TestGit $repo @("update-index", "--chmod=+x", "scripts/executavel.sh") | Out-Null
  }

  $hookRepo = New-TestRepo
  Copy-HookRuntime $hookRepo
  Write-TestText $hookRepo "docs/limpo.md" "ok"
  Stage-TestPath $hookRepo "docs/limpo.md"
  $hookResult = Invoke-PreCommitHook -Repo $hookRepo -WorkingDirectory $hookRepo
  Add-TestResult "pre-commit real na raiz com arquivo limpo" "hook" 0 $hookResult.ExitCode $(if ($hookResult.ExitCode -eq 0 -and (Test-HookOrder $hookResult.Stdout)) { "OK" } else { "FALHA" })

  $hookRepoSub = New-TestRepo
  Copy-HookRuntime $hookRepoSub
  New-Item -ItemType Directory -Path (Join-Path $hookRepoSub "subdir") -Force | Out-Null
  Write-TestText $hookRepoSub "docs/limpo.md" "ok"
  Stage-TestPath $hookRepoSub "docs/limpo.md"
  $hookSubResult = Invoke-PreCommitHook -Repo $hookRepoSub -WorkingDirectory (Join-Path $hookRepoSub "subdir")
  Add-TestResult "pre-commit real a partir de subdiretório" "hook" 0 $hookSubResult.ExitCode $(if ($hookSubResult.ExitCode -eq 0 -and (Test-HookOrder $hookSubResult.Stdout)) { "OK" } else { "FALHA" })

  $hookSecretRepo = New-TestRepo
  Copy-HookRuntime $hookSecretRepo
  Write-TestText $hookSecretRepo "config/app.properties" (New-AssignmentLine (New-KeyName @("pass", "word")) (New-RealValue))
  Stage-TestPath $hookSecretRepo "config/app.properties"
  $hookSecretResult = Invoke-PreCommitHook -Repo $hookSecretRepo -WorkingDirectory $hookSecretRepo
  Add-TestResult "pre-commit real bloqueia secret staged" "hook" 1 $hookSecretResult.ExitCode

  $hookPsRepo = New-TestRepo
  Copy-HookRuntime $hookPsRepo
  Write-TestText $hookPsRepo "docs/limpo.md" "ok"
  Stage-TestPath $hookPsRepo "docs/limpo.md"
  $hookPsResult = Invoke-PreCommitHook -Repo $hookPsRepo -WorkingDirectory $hookPsRepo -EnvVars @{ TOPSV3_SIMULAR_POWERSHELL_AUSENTE = "1" }
  Add-TestResult "pre-commit bloqueia PowerShell ausente simulado" "hook" 2 $hookPsResult.ExitCode

  $gitleaks = Get-Command gitleaks -ErrorAction SilentlyContinue
  if (-not $gitleaks) {
    Add-TestResult "gitleaks real instalado" "gitleaks" "1" "-" "PENDENTE"
  } else {
    $gitleaksRepo = New-TestRepo
    Write-TestText $gitleaksRepo "config/app.properties" (New-AssignmentLine (New-KeyName @("pass", "word")) (New-RealValue))
    Stage-TestPath $gitleaksRepo "config/app.properties"
    $real = Invoke-ScannerForTest -Repo $gitleaksRepo -Scanner $scannerSegredos -EnvVars @{ TOPSV3_FORCAR_FALLBACK_GITLEAKS = "0" }
    Add-TestResult "gitleaks real instalado" "gitleaks" 1 $real.ExitCode
  }
} finally {
  if (Test-Path -LiteralPath $tempRoot) {
    $resolvedTempRoot = (Resolve-Path -LiteralPath $tempRoot).Path
    $expectedParent = [IO.Path]::GetFullPath($env:TEMP).TrimEnd('\', '/')
    if ((Split-Path -Parent $resolvedTempRoot) -ne $expectedParent -or
        (Split-Path -Leaf $resolvedTempRoot) -notmatch '^topsv3-testes-hooks-[a-f0-9-]{36}$') {
      throw "Cleanup recusado: diretorio temporario de teste inesperado."
    }
    Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force
  }
}

$report = New-Object System.Collections.Generic.List[string]
$report.Add("# Relatório de testes dos hooks")
$report.Add("")
$report.Add("- Total de cenários: $($results.Count)")
$report.Add("- Cenários OK: $(@($results | Where-Object { $_.Resultado -eq "OK" }).Count)")
$report.Add("- Cenários pendentes: $(@($results | Where-Object { $_.Resultado -eq "PENDENTE" }).Count)")
$report.Add("- Cenários com falha: $(@($results | Where-Object { $_.Resultado -eq "FALHA" }).Count)")
$report.Add("")
$report.Add("| Cenário | Scanner esperado | Exit code esperado | Exit code obtido | Resultado |")
$report.Add("| --- | --- | ---: | ---: | --- |")
foreach ($item in $results) {
  $report.Add("| $($item.Cenario) | $($item.ScannerEsperado) | $($item.ExitCodeEsperado) | $($item.ExitCodeObtido) | $($item.Resultado) |")
}
Write-TopsUtf8NoBomLines -Path (Join-Path $repoRoot "RELATORIO-TESTES-HOOKS.md") -Lines @($report)

$failed = @($results | Where-Object { $_.Resultado -eq "FALHA" })
if ($failed.Count -gt 0) {
  Write-Host "Testes de regressão dos hooks falharam: $($failed.Count)"
  exit 1
}

Write-Host "Todos os testes obrigatórios de regressão dos hooks foram aprovados."
exit 0
