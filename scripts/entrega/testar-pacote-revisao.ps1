Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$scriptsRoot = Split-Path -Parent $scriptDir
. (Join-Path (Join-Path $scriptsRoot "security") "git-staged-utils.ps1")

$sourceRepo = Get-TopsRepoRoot
$ps = Find-TopsPowerShell
$tempRoot = Join-Path $env:TEMP ("topsv3-testes-pacote-" + [guid]::NewGuid().ToString())
$results = New-Object System.Collections.Generic.List[object]

function Invoke-TestGit {
  param(
    [string]$Repo,
    [string[]]$GitArgs,
    [switch]$AllowFailure
  )
  $result = Invoke-TopsProcessBytes -FileName "git" -Arguments $GitArgs -WorkingDirectory $Repo
  if (-not $AllowFailure -and $result.ExitCode -ne 0) {
    throw "git $($GitArgs -join ' ') falhou"
  }
  return $result
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
    [switch]$DoubleQuoted
  )
  if ($DoubleQuoted) { return ($Key + $Delimiter + '"' + $Value + '"') }
  return ($Key + $Delimiter + $Value)
}

function New-RealValue {
  return ("Valor" + "Com" + "Aparencia" + "Real" + "12345")
}

function New-LongSecretValue {
  return ("Valor-Ficticio!" + "Longo#2026")
}

function Copy-PackageRuntime {
  param([string]$Repo)
  New-Item -ItemType Directory -Path (Join-Path $Repo "scripts/security") -Force | Out-Null
  New-Item -ItemType Directory -Path (Join-Path $Repo "scripts/entrega") -Force | Out-Null
  foreach ($file in @(
    "scripts/security/git-staged-utils.ps1",
    "scripts/security/verificar-codificacao.ps1",
    "scripts/security/verificar-arquivos-proibidos.ps1",
    "scripts/security/verificar-segredos.ps1",
    "scripts/entrega/criar-inventario-inicial.ps1",
    "scripts/entrega/criar-pacote-revisao.ps1"
  )) {
    Copy-Item -LiteralPath (Join-Path $sourceRepo $file) -Destination (Join-Path $Repo $file) -Force
  }
}

function New-PackageTestRepo {
  $repo = Join-Path $tempRoot ("repo-" + [guid]::NewGuid().ToString())
  New-Item -ItemType Directory -Path $repo -Force | Out-Null
  Invoke-TestGit $repo @("init", "-b", "main") | Out-Null
  Invoke-TestGit $repo @("config", "user.name", "Teste") | Out-Null
  Invoke-TestGit $repo @("config", "user.email", "teste@example.invalid") | Out-Null
  Copy-PackageRuntime $repo
  Write-TestText $repo ".gitignore" "*.zip`n*.7z`nignored.txt`n"
  Invoke-TestGit $repo @("add", ".") | Out-Null
  return $repo
}

function New-Inventory {
  param([string]$Repo)
  $inventoryDir = Join-Path $tempRoot ("inventarios\" + [guid]::NewGuid().ToString())
  New-Item -ItemType Directory -Path $inventoryDir -Force | Out-Null
  $result = Invoke-TopsProcessBytes -FileName $ps -Arguments @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "scripts/entrega/criar-inventario-inicial.ps1", "-DiretorioRepositorio", $Repo, "-DiretorioDestino", $inventoryDir) -WorkingDirectory $Repo
  if ($result.ExitCode -ne 0) {
    $stderr = ConvertFrom-TopsUtf8Strict -Bytes $result.Stderr -Context "stderr inventario"
    throw "geracao do inventario falhou: $stderr"
  }
  $stdout = ConvertFrom-TopsUtf8Strict -Bytes $result.Stdout -Context "stdout inventario"
  if ($stdout -notmatch 'INVENTARIO_INICIAL=(.+)') {
    throw "gerador de inventario nao informou o caminho"
  }
  return $matches[1].Trim()
}

function Get-InventoryText {
  param([string]$Inventory)
  $bytes = [IO.File]::ReadAllBytes($Inventory)
  $text = (Get-TopsUtf8StrictEncoding).GetString($bytes)
  if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) { $text = $text.Substring(1) }
  return $text
}

function Set-InventoryText {
  param(
    [string]$Inventory,
    [string]$Text,
    [switch]$NoBom
  )
  [IO.File]::WriteAllText($Inventory, $Text, (New-Object System.Text.UTF8Encoding(-not [bool]$NoBom)))
}

function New-Metadata {
  param([switch]$WithSensitiveFixture)
  $metadataDir = Join-Path $tempRoot "metadados"
  New-Item -ItemType Directory -Path $metadataDir -Force | Out-Null
  $path = Join-Path $metadataDir ("metadados-" + [guid]::NewGuid().ToString() + ".json")
  $meta = [ordered]@{
    projeto = "Tops do Job V3"
    fase = "teste"
    objetivo = "Teste automatizado do pacote"
    codigo_aplicacao_criado = $false
    migration_criada = $false
    sql_criado = $false
    integracao_externa_acessada = $false
    fase_seguinte_iniciada = $false
    testes_executados = @("testar-pacote-revisao.ps1")
    testes_aprovados = @("cenário em execução")
    testes_com_falha = @()
    gitleaks_utilizado = $false
    fallback_utilizado = $true
    commit_executado = $false
    commit_hash = ""
    motivo_sem_commit = "teste temporario"
  }
  if ($WithSensitiveFixture) {
    $sensitiveKey = New-KeyName @("client", "_secret")
    $meta[$sensitiveKey] = New-LongSecretValue
  }
  [IO.File]::WriteAllText($path, ($meta | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding($false)))
  return $path
}

function Invoke-Package {
  param(
    [string]$Repo,
    [string]$Inventory,
    [string]$Destination,
    [hashtable]$EnvVars = @{},
    [switch]$MetadataWithSensitiveFixture
  )
  $metadata = New-Metadata -WithSensitiveFixture:$MetadataWithSensitiveFixture
  $effectiveEnv = @{ TOPSV3_FORCAR_FALLBACK_GITLEAKS = "1" }
  foreach ($key in $EnvVars.Keys) { $effectiveEnv[$key] = $EnvVars[$key] }
  $old = @{}
  foreach ($key in $effectiveEnv.Keys) {
    $old[$key] = [Environment]::GetEnvironmentVariable($key, "Process")
    [Environment]::SetEnvironmentVariable($key, [string]$effectiveEnv[$key], "Process")
  }
  try {
    $result = Invoke-TopsProcessBytes -FileName $ps -Arguments @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "scripts/entrega/criar-pacote-revisao.ps1", "-Fase", "teste", "-InventarioInicial", $Inventory, "-DiretorioRepositorio", $Repo, "-DiretorioDestino", $Destination, "-MetadadosExecucao", $metadata) -WorkingDirectory $Repo
    $stdout = ConvertFrom-TopsUtf8Strict -Bytes $result.Stdout -Context "stdout pacote"
    return [pscustomobject]@{
      ExitCode = $result.ExitCode
      Stdout = $stdout
      Stderr = (ConvertFrom-TopsUtf8Strict -Bytes $result.Stderr -Context "stderr pacote")
      ZipPath = $(if ($stdout -match 'ZIP_PATH=(.+)') { $matches[1].Trim() } else { "" })
    }
  } finally {
    foreach ($key in $effectiveEnv.Keys) {
      [Environment]::SetEnvironmentVariable($key, $old[$key], "Process")
    }
  }
}

function Read-ZipEntryBytes {
  param(
    [string]$ZipPath,
    [string]$EntryName
  )
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $archive = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
  try {
    $entry = $archive.Entries | Where-Object { $_.FullName -eq $EntryName } | Select-Object -First 1
    if (-not $entry) { return $null }
    $stream = $entry.Open()
    try {
      $memory = New-Object System.IO.MemoryStream
      $stream.CopyTo($memory)
      return ,$memory.ToArray()
    } finally {
      $stream.Dispose()
    }
  } finally {
    $archive.Dispose()
  }
}

function Read-ZipEntryText {
  param(
    [string]$ZipPath,
    [string]$EntryName
  )
  $bytes = Read-ZipEntryBytes -ZipPath $ZipPath -EntryName $EntryName
  if ($null -eq $bytes) { return "" }
  return (Get-TopsUtf8StrictEncoding).GetString($bytes)
}

function Get-ZipManifest {
  param([string]$ZipPath)
  $extractRoot = Join-Path $tempRoot ("extracoes\" + [guid]::NewGuid().ToString())
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  try {
    [System.IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $extractRoot)
    return @(Import-Csv -LiteralPath (Join-Path $extractRoot "MANIFESTO-ARQUIVOS.csv"))
  } finally {
    if (Test-Path -LiteralPath $extractRoot) { Remove-Item -LiteralPath $extractRoot -Recurse -Force }
  }
}

function Add-PackageResult {
  param(
    [string]$Scenario,
    [int]$ExpectedExitCode,
    [int]$ActualExitCode,
    [bool]$ExtraCheck,
    [string]$Categoria
  )
  $ok = ($ExpectedExitCode -eq $ActualExitCode -and $ExtraCheck)
  $results.Add([pscustomobject]@{
    Cenario = $Scenario
    Categoria = $Categoria
    ExitCodeEsperado = $ExpectedExitCode
    ExitCodeObtido = $ActualExitCode
    Resultado = $(if ($ok) { "OK" } else { "FALHA" })
  })
  $status = if ($ok) { "OK" } else { "FALHA" }
  Write-Host "[$status] $Scenario | esperado: $ExpectedExitCode | obtido: $ActualExitCode"
}

function Run-PackageScenario {
  param(
    [string]$Scenario,
    [int]$ExpectedExitCode,
    [scriptblock]$Arrange,
    [scriptblock]$Check,
    [hashtable]$EnvVars = @{},
    [string]$Categoria = "geral",
    [switch]$MetadataWithSensitiveFixture
  )
  $repo = New-PackageTestRepo
  $destination = Join-Path $tempRoot ("dest-" + [guid]::NewGuid().ToString())
  New-Item -ItemType Directory -Path $destination -Force | Out-Null
  $inventory = New-Inventory $repo
  & $Arrange $repo $destination $inventory
  $result = Invoke-Package -Repo $repo -Inventory $inventory -Destination $destination -EnvVars $EnvVars -MetadataWithSensitiveFixture:$MetadataWithSensitiveFixture
  $extra = & $Check $result $destination
  Add-PackageResult $Scenario $ExpectedExitCode $result.ExitCode $extra $Categoria
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

New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
$suiteError = $null

try {
  Run-PackageScenario "arquivo criado e incluído" 0 {
    param($repo, $destination)
    Write-TestText $repo "novo.md" "Novo arquivo."
    Stage-TestPath $repo "novo.md"
  } {
    param($result, $destination)
    if ($result.ExitCode -ne 0) { return $false }
    $manifest = Get-ZipManifest $result.ZipPath
    return (@($manifest | Where-Object { $_.caminho_relativo -eq "novo.md" -and $_.tipo_alteracao -eq "CRIADO" }).Count -eq 1)
  } -Categoria "manifesto"

  Run-PackageScenario "inventário válido aceito" 0 {
    param($repo, $destination, $inventory)
    Write-TestText $repo "inventario-valido.md" "ok"
    Stage-TestPath $repo "inventario-valido.md"
  } {
    param($result, $destination)
    return ($result.ExitCode -eq 0 -and $result.Stdout -match "VALIDATION_RESULT=OK")
  } -Categoria "inventario"

  Run-PackageScenario "inventário sem BOM bloqueia" 2 {
    param($repo, $destination, $inventory)
    $text = Get-InventoryText $inventory
    Set-InventoryText -Inventory $inventory -Text $text -NoBom
    Write-TestText $repo "novo.md" "ok"
    Stage-TestPath $repo "novo.md"
  } { param($result, $destination) return ($result.ExitCode -eq 2) } -Categoria "inventario"

  Run-PackageScenario "inventário com coluna ausente bloqueia" 2 {
    param($repo, $destination, $inventory)
    $text = Get-InventoryText $inventory
    $text = $text -replace ',"sha256_indice"', ''
    Set-InventoryText -Inventory $inventory -Text $text
    Write-TestText $repo "novo.md" "ok"
    Stage-TestPath $repo "novo.md"
  } { param($result, $destination) return ($result.ExitCode -eq 2) } -Categoria "inventario"

  Run-PackageScenario "inventário com coluna extra bloqueia" 2 {
    param($repo, $destination, $inventory)
    $lines = [System.Collections.Generic.List[string]]::new()
    foreach ($line in ((Get-InventoryText $inventory).Split([string[]]@("`n"), [System.StringSplitOptions]::None))) {
      $lines.Add($line.TrimEnd("`r"))
    }
    if ($lines.Count -gt 0 -and $lines[$lines.Count - 1] -eq "") { $lines.RemoveAt($lines.Count - 1) }
    $lines[0] = $lines[0] + ',"extra"'
    for ($i = 1; $i -lt $lines.Count; $i++) { $lines[$i] = $lines[$i] + ',"x"' }
    Set-InventoryText -Inventory $inventory -Text (($lines -join "`n") + "`n")
    Write-TestText $repo "novo.md" "ok"
    Stage-TestPath $repo "novo.md"
  } { param($result, $destination) return ($result.ExitCode -eq 2) } -Categoria "inventario"

  Run-PackageScenario "inventário com hash inválido bloqueia" 2 {
    param($repo, $destination, $inventory)
    $text = [regex]::Replace((Get-InventoryText $inventory), '[0-9a-f]{64}', ('z' * 64), 1)
    Set-InventoryText -Inventory $inventory -Text $text
    Write-TestText $repo "novo.md" "ok"
    Stage-TestPath $repo "novo.md"
  } { param($result, $destination) return ($result.ExitCode -eq 2) } -Categoria "inventario"

  Run-PackageScenario "inventário com caminho duplicado bloqueia" 2 {
    param($repo, $destination, $inventory)
    $lines = [System.Collections.Generic.List[string]]::new()
    foreach ($line in ((Get-InventoryText $inventory).Split([string[]]@("`n"), [System.StringSplitOptions]::None))) {
      $lines.Add($line.TrimEnd("`r"))
    }
    if ($lines.Count -gt 0 -and $lines[$lines.Count - 1] -eq "") { $lines.RemoveAt($lines.Count - 1) }
    if ($lines.Count -gt 1) { $lines.Add($lines[1]) }
    Set-InventoryText -Inventory $inventory -Text (($lines -join "`n") + "`n")
    Write-TestText $repo "novo.md" "ok"
    Stage-TestPath $repo "novo.md"
  } { param($result, $destination) return ($result.ExitCode -eq 2) } -Categoria "inventario"

  Run-PackageScenario "alteração tracked unstaged bloqueia pacote" 2 {
    param($repo, $destination, $inventory)
    Write-TestText $repo ".gitignore" "*.zip`n*.7z`nignored.txt`nalterado-sem-stage.tmp`n"
  } {
    param($result, $destination)
    return ($result.ExitCode -eq 2 -and $result.Stdout.Contains("Existem alterações fora do índice Git. Execute a revisão e o staging antes de gerar o pacote."))
  } -Categoria "indice"

  Run-PackageScenario "arquivo novo unstaged bloqueia pacote" 2 {
    param($repo, $destination, $inventory)
    Write-TestText $repo "solto.md" "sem stage"
  } {
    param($result, $destination)
    return ($result.ExitCode -eq 2 -and $result.Stdout.Contains("Existem alterações fora do índice Git. Execute a revisão e o staging antes de gerar o pacote."))
  } -Categoria "indice"

  Run-PackageScenario "pacote OK quando tudo está staged" 0 {
    param($repo, $destination, $inventory)
    Write-TestText $repo "tudo-staged.md" "ok"
    Stage-TestPath $repo "tudo-staged.md"
  } {
    param($result, $destination)
    return ($result.ExitCode -eq 0 -and $result.Stdout -match "VALIDATION_RESULT=OK")
  } -Categoria "indice"

  $repoDelete = New-PackageTestRepo
  $destinationDelete = Join-Path $tempRoot ("dest-" + [guid]::NewGuid().ToString())
  New-Item -ItemType Directory -Path $destinationDelete -Force | Out-Null
  Write-TestText $repoDelete "remover.md" "antes"
  Stage-TestPath $repoDelete "remover.md"
  $inventoryDelete = New-Inventory $repoDelete
  Remove-Item -LiteralPath (Join-Path $repoDelete "remover.md") -Force
  $resultDelete = Invoke-Package -Repo $repoDelete -Inventory $inventoryDelete -Destination $destinationDelete
  Add-PackageResult "deleção unstaged bloqueia pacote" 2 $resultDelete.ExitCode ($resultDelete.Stdout.Contains("Existem alterações fora do índice Git. Execute a revisão e o staging antes de gerar o pacote.")) "indice"

  $repo = New-PackageTestRepo
  $destination = Join-Path $tempRoot ("dest-" + [guid]::NewGuid().ToString())
  New-Item -ItemType Directory -Path $destination -Force | Out-Null
  Write-TestText $repo "mod.md" "antes"
  Stage-TestPath $repo "mod.md"
  $inventory = New-Inventory $repo
  Write-TestText $repo "mod.md" "depois"
  Stage-TestPath $repo "mod.md"
  $result = Invoke-Package -Repo $repo -Inventory $inventory -Destination $destination
  $manifest = if ($result.ExitCode -eq 0) { Get-ZipManifest $result.ZipPath } else { @() }
  Add-PackageResult "arquivo modificado e incluído" 0 $result.ExitCode (@($manifest | Where-Object { $_.caminho_relativo -eq "mod.md" -and $_.tipo_alteracao -eq "MODIFICADO" }).Count -eq 1) "manifesto"

  $repo = New-PackageTestRepo
  $destination = Join-Path $tempRoot ("dest-" + [guid]::NewGuid().ToString())
  New-Item -ItemType Directory -Path $destination -Force | Out-Null
  Write-TestText $repo "anterior.md" "fase anterior"
  Stage-TestPath $repo "anterior.md"
  $inventory = New-Inventory $repo
  Write-TestText $repo "novo.md" "novo"
  Stage-TestPath $repo "novo.md"
  $result = Invoke-Package -Repo $repo -Inventory $inventory -Destination $destination
  $manifest = if ($result.ExitCode -eq 0) { Get-ZipManifest $result.ZipPath } else { @() }
  Add-PackageResult "staged anterior sem mudança excluído" 0 $result.ExitCode ((@($manifest | Where-Object { $_.caminho_relativo -eq "anterior.md" }).Count -eq 0) -and (@($manifest | Where-Object { $_.caminho_relativo -eq "novo.md" }).Count -eq 1)) "manifesto"

  $repo = New-PackageTestRepo
  $destination = Join-Path $tempRoot ("dest-" + [guid]::NewGuid().ToString())
  New-Item -ItemType Directory -Path $destination -Force | Out-Null
  Write-TestText $repo "alterado-antes.md" "conteudo anterior"
  Stage-TestPath $repo "alterado-antes.md"
  $inventory = New-Inventory $repo
  Write-TestText $repo "novo-pos-inventario.md" "novo"
  Stage-TestPath $repo "novo-pos-inventario.md"
  $result = Invoke-Package -Repo $repo -Inventory $inventory -Destination $destination
  $manifest = if ($result.ExitCode -eq 0) { Get-ZipManifest $result.ZipPath } else { @() }
  Add-PackageResult "arquivo alterado antes do inventário não entra" 0 $result.ExitCode ((@($manifest | Where-Object { $_.caminho_relativo -eq "alterado-antes.md" }).Count -eq 0) -and (@($manifest | Where-Object { $_.caminho_relativo -eq "novo-pos-inventario.md" }).Count -eq 1)) "manifesto"

  $repo = New-PackageTestRepo
  $destination = Join-Path $tempRoot ("dest-" + [guid]::NewGuid().ToString())
  New-Item -ItemType Directory -Path $destination -Force | Out-Null
  Write-TestText $repo "indice.md" "inicial"
  Stage-TestPath $repo "indice.md"
  $inventory = New-Inventory $repo
  Write-TestText $repo "indice.md" "staged"
  Stage-TestPath $repo "indice.md"
  Write-TestText $repo "indice.md" "inicial"
  $result = Invoke-Package -Repo $repo -Inventory $inventory -Destination $destination
  Add-PackageResult "mudança apenas no índice detectada como divergência" 2 $result.ExitCode $true "índice"

  $repo = New-PackageTestRepo
  $destination = Join-Path $tempRoot ("dest-" + [guid]::NewGuid().ToString())
  New-Item -ItemType Directory -Path $destination -Force | Out-Null
  Write-TestText $repo "diverge.md" "inicial"
  Stage-TestPath $repo "diverge.md"
  $inventory = New-Inventory $repo
  Write-TestText $repo "diverge.md" "staged"
  Stage-TestPath $repo "diverge.md"
  Write-TestText $repo "diverge.md" "workspace"
  $result = Invoke-Package -Repo $repo -Inventory $inventory -Destination $destination
  Add-PackageResult "divergência índice workspace bloqueia" 2 $result.ExitCode $true "índice"

  Run-PackageScenario "arquivo ignorado bloqueia" 2 {
    param($repo, $destination)
    Write-TestText $repo "ignored.txt" "ignorado"
    Stage-TestPath $repo "ignored.txt" -Force
  } { param($result, $destination) return ($result.ExitCode -eq 2) } -Categoria "proibidos"

  Run-PackageScenario "arquivo proibido bloqueia" 2 {
    param($repo, $destination)
    Write-TestBytes $repo "arquivo.zip" ([byte[]](80,75,3,4))
    Stage-TestPath $repo "arquivo.zip" -Force
  } { param($result, $destination) return ($result.ExitCode -eq 2) } -Categoria "proibidos"

  Run-PackageScenario ".zip.example bloqueia" 2 {
    param($repo, $destination)
    Write-TestText $repo "pacote.zip.example" "exemplo textual"
    Stage-TestPath $repo "pacote.zip.example"
  } { param($result, $destination) return ($result.ExitCode -eq 2) } -Categoria "proibidos"

  Run-PackageScenario ".p12.example bloqueia" 2 {
    param($repo, $destination)
    Write-TestText $repo "certificado.p12.example" "exemplo textual"
    Stage-TestPath $repo "certificado.p12.example"
  } { param($result, $destination) return ($result.ExitCode -eq 2) } -Categoria "proibidos"

  $repo = New-PackageTestRepo
  $destination = Join-Path $repo "destino-interno"
  New-Item -ItemType Directory -Path $destination -Force | Out-Null
  $inventory = New-Inventory $repo
  Write-TestText $repo "novo.md" "novo"
  Stage-TestPath $repo "novo.md"
  $result = Invoke-Package -Repo $repo -Inventory $inventory -Destination $destination
  Add-PackageResult "destino dentro do repositório bloqueia" 2 $result.ExitCode $true "caminho"

  $repo = New-PackageTestRepo
  $destination = Join-Path $tempRoot ("dest-" + [guid]::NewGuid().ToString())
  New-Item -ItemType Directory -Path $destination -Force | Out-Null
  $inventory = New-Inventory $repo
  Write-TestText $repo ".gitignore" "*.zip`n*.7z`nignored.txt`nnovo-ignore.tmp`n"
  Stage-TestPath $repo ".gitignore"
  $result = Invoke-Package -Repo $repo -Inventory $inventory -Destination $destination
  $manifest = if ($result.ExitCode -eq 0) { Get-ZipManifest $result.ZipPath } else { @() }
  Add-PackageResult "arquivo oculto gitignore incluído quando candidato" 0 $result.ExitCode (@($manifest | Where-Object { $_.caminho_relativo -eq ".gitignore" }).Count -eq 1) "manifesto"

  Run-PackageScenario "caminhos com espaços e acentos funcionam" 0 {
    param($repo, $destination)
    Write-TestText $repo "docs/arquivo com espaco.md" "ok"
    Write-TestText $repo "docs/revisão-segurança.md" "ok"
    Stage-TestPath $repo "docs/arquivo com espaco.md"
    Stage-TestPath $repo "docs/revisão-segurança.md"
  } {
    param($result, $destination)
    if ($result.ExitCode -ne 0) { return $false }
    $manifest = Get-ZipManifest $result.ZipPath
    return ((@($manifest | Where-Object { $_.caminho_relativo -eq "docs/arquivo com espaco.md" }).Count -eq 1) -and (@($manifest | Where-Object { $_.caminho_relativo -eq "docs/revisão-segurança.md" }).Count -eq 1))
  } -Categoria "unicode"

  Run-PackageScenario "manifesto confere" 0 {
    param($repo, $destination)
    Write-TestText $repo "manifesto.md" "ok"
    Stage-TestPath $repo "manifesto.md"
  } {
    param($result, $destination)
    return ($result.ExitCode -eq 0 -and $result.Stdout -match "VALIDATION_RESULT=OK")
  } -Categoria "manifesto"

  Run-PackageScenario "manifesto interno possui BOM" 0 {
    param($repo, $destination)
    Write-TestText $repo "bom.md" "ok"
    Stage-TestPath $repo "bom.md"
  } {
    param($result, $destination)
    if ($result.ExitCode -ne 0) { return $false }
    $bytes = Read-ZipEntryBytes $result.ZipPath "MANIFESTO-ARQUIVOS.csv"
    return ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
  } -Categoria "csv"

  Run-PackageScenario "hash do manifesto real corresponde ao arquivo interno" 0 {
    param($repo, $destination)
    Write-TestText $repo "hash-real.md" "ok"
    Stage-TestPath $repo "hash-real.md"
  } {
    param($result, $destination)
    if ($result.ExitCode -ne 0) { return $false }
    $manifest = Get-ZipManifest $result.ZipPath
    $row = $manifest | Where-Object { $_.caminho_relativo -eq "hash-real.md" } | Select-Object -First 1
    $bytes = Read-ZipEntryBytes $result.ZipPath "hash-real.md"
    return ($row -and $row.sha256 -eq (Get-TopsSha256Bytes $bytes))
  } -Categoria "manifesto"

  $negativeManifestCases = @(
    @{ Nome = "manifesto com hash alterado"; Env = @{ TOPSV3_SIMULAR_MANIFEST_HASH = "1" } },
    @{ Nome = "manifesto com tamanho alterado"; Env = @{ TOPSV3_SIMULAR_MANIFEST_SIZE = "1" } },
    @{ Nome = "manifesto com caminho adicional"; Env = @{ TOPSV3_SIMULAR_MANIFEST_EXTRA_PATH = "1" } },
    @{ Nome = "manifesto com caminho ausente"; Env = @{ TOPSV3_SIMULAR_MANIFEST_MISSING_PATH = "1" } },
    @{ Nome = "manifesto com coluna ausente"; Env = @{ TOPSV3_SIMULAR_MANIFEST_MISSING_COLUMN = "1" } },
    @{ Nome = "manifesto com coluna extra"; Env = @{ TOPSV3_SIMULAR_MANIFEST_EXTRA_COLUMN = "1" } },
    @{ Nome = "manifesto sem BOM"; Env = @{ TOPSV3_SIMULAR_MANIFEST_SEM_BOM = "1" } },
    @{ Nome = "manifesto com caminho duplicado"; Env = @{ TOPSV3_SIMULAR_MANIFEST_DUPLICATE = "1" } },
    @{ Nome = "manifesto com tipo inválido"; Env = @{ TOPSV3_SIMULAR_MANIFEST_BAD_TYPE = "1" } }
  )
  foreach ($case in $negativeManifestCases) {
    Run-PackageScenario $case.Nome 2 {
      param($repo, $destination)
      Write-TestText $repo "manifesto-negativo.md" "ok"
      Stage-TestPath $repo "manifesto-negativo.md"
    } {
      param($result, $destination)
      return (($result.ExitCode -eq 2) -and (@(Get-ChildItem -LiteralPath $destination -Filter "*.zip" -File).Count -eq 0))
    } -EnvVars $case.Env -Categoria "manifesto"
  }

  Run-PackageScenario "ZIP com entrada extra falha" 2 {
    param($repo, $destination)
    Write-TestText $repo "extra.md" "ok"
    Stage-TestPath $repo "extra.md"
  } {
    param($result, $destination)
    return (($result.ExitCode -eq 2) -and (@(Get-ChildItem -LiteralPath $destination -Filter "*.zip" -File).Count -eq 0))
  } -EnvVars @{ TOPSV3_SIMULAR_ZIP_EXTRA = "1" } -Categoria "zip"

  Run-PackageScenario "ZIP com hash divergente falha" 2 {
    param($repo, $destination)
    Write-TestText $repo "hash.md" "ok"
    Stage-TestPath $repo "hash.md"
  } {
    param($result, $destination)
    return (($result.ExitCode -eq 2) -and (@(Get-ChildItem -LiteralPath $destination -Filter "*.zip" -File).Count -eq 0))
  } -EnvVars @{ TOPSV3_SIMULAR_ZIP_HASH_DIVERGENTE = "1" } -Categoria "zip"

  Run-PackageScenario "traversal com slash bloqueia" 2 {
    param($repo, $destination)
    Write-TestText $repo "slash.md" "ok"
    Stage-TestPath $repo "slash.md"
  } {
    param($result, $destination)
    return (($result.ExitCode -eq 2) -and (@(Get-ChildItem -LiteralPath $destination -Filter "*.zip" -File).Count -eq 0))
  } -EnvVars @{ TOPSV3_SIMULAR_ZIP_TRAVERSAL_SLASH = "1" } -Categoria "zip"

  Run-PackageScenario "traversal com backslash bloqueia" 2 {
    param($repo, $destination)
    Write-TestText $repo "backslash.md" "ok"
    Stage-TestPath $repo "backslash.md"
  } {
    param($result, $destination)
    return (($result.ExitCode -eq 2) -and (@(Get-ChildItem -LiteralPath $destination -Filter "*.zip" -File).Count -eq 0))
  } -EnvVars @{ TOPSV3_SIMULAR_ZIP_TRAVERSAL_BACKSLASH = "1" } -Categoria "zip"

  Run-PackageScenario "entrada duplicada por caixa bloqueia" 2 {
    param($repo, $destination)
    Write-TestText $repo "case.md" "ok"
    Stage-TestPath $repo "case.md"
  } {
    param($result, $destination)
    return (($result.ExitCode -eq 2) -and (@(Get-ChildItem -LiteralPath $destination -Filter "*.zip" -File).Count -eq 0))
  } -EnvVars @{ TOPSV3_SIMULAR_ZIP_DUP_CASE = "1" } -Categoria "zip"

  Run-PackageScenario "controle contendo secret bloqueia" 2 {
    param($repo, $destination)
    Write-TestText $repo "controle.md" "ok"
    Stage-TestPath $repo "controle.md"
  } {
    param($result, $destination)
    return (($result.ExitCode -eq 2) -and (@(Get-ChildItem -LiteralPath $destination -Filter "*.zip" -File).Count -eq 0))
  } -EnvVars @{ (("TOPSV3_SIMULAR_CONTROLE_" + "SECRET")) = "1" } -Categoria "secrets"

  Run-PackageScenario "metadado contendo secret bloqueia" 2 {
    param($repo, $destination)
    Write-TestText $repo "metadata.md" "ok"
    Stage-TestPath $repo "metadata.md"
  } {
    param($result, $destination)
    return (($result.ExitCode -eq 2) -and (@(Get-ChildItem -LiteralPath $destination -Filter "*.zip" -File).Count -eq 0) -and ($result.Stdout -notmatch [regex]::Escape((New-LongSecretValue))))
  } -Categoria "secrets" -MetadataWithSensitiveFixture

  Run-PackageScenario "symlink staged bloqueia" 2 {
    param($repo, $destination)
    Stage-SyntheticModeEntry $repo "link-simulado" "120000"
  } { param($result, $destination) return ($result.ExitCode -eq 2) } -Categoria "modos-git"

  Run-PackageScenario "gitlink staged bloqueia" 2 {
    param($repo, $destination)
    Invoke-TestGit $repo @("update-index", "--add", "--cacheinfo", "160000,aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa,submodulo") | Out-Null
  } { param($result, $destination) return ($result.ExitCode -eq 2) } -Categoria "modos-git"

  Run-PackageScenario "ZIP parcial e excluído" 2 {
    param($repo, $destination)
    Write-TestText $repo "parcial.md" "ok"
    Stage-TestPath $repo "parcial.md"
  } {
    param($result, $destination)
    return (@(Get-ChildItem -LiteralPath $destination -Filter "*.zip" -File).Count -eq 0)
  } -EnvVars @{ TOPSV3_SIMULAR_ZIP_EXTRA = "1" } -Categoria "zip"

  Run-PackageScenario "resumo UTF-8 correto" 0 {
    param($repo, $destination)
    Write-TestText $repo "resumo.md" "aplicação"
    Stage-TestPath $repo "resumo.md"
  } {
    param($result, $destination)
    if ($result.ExitCode -ne 0) { return $false }
    $summary = Read-ZipEntryText $result.ZipPath "RESUMO-ENTREGA.md"
    return ($summary -match "Resumo da entrega" -and -not $summary.Contains([string][char]0x00C3) -and -not $summary.Contains([string][char]0x00C2) -and $summary -notmatch '\p{L}\?\p{L}')
  } -Categoria "codificação"

  Run-PackageScenario "lista de arquivos uma entrada por linha" 0 {
    param($repo, $destination)
    Write-TestText $repo "um.md" "um"
    Write-TestText $repo "dois.md" "dois"
    Stage-TestPath $repo "um.md"
    Stage-TestPath $repo "dois.md"
  } {
    param($result, $destination)
    if ($result.ExitCode -ne 0) { return $false }
    $summary = Read-ZipEntryText $result.ZipPath "RESUMO-ENTREGA.md"
    $lines = @($summary -split "`r?`n")
    return ($lines -contains "- um.md" -and $lines -contains "- dois.md")
  } -Categoria "resumo"

  $sharedDestination = Join-Path $tempRoot ("dest-" + [guid]::NewGuid().ToString())
  New-Item -ItemType Directory -Path $sharedDestination -Force | Out-Null
  foreach ($name in @("primeiro.md", "segundo.md")) {
    $repo = New-PackageTestRepo
    $inventory = New-Inventory $repo
    Write-TestText $repo $name "ok"
    Stage-TestPath $repo $name
    [void](Invoke-Package -Repo $repo -Inventory $inventory -Destination $sharedDestination)
    Start-Sleep -Milliseconds 5
  }
  Add-PackageResult "ZIP não sobrescreve pacote anterior" 0 0 (@(Get-ChildItem -LiteralPath $sharedDestination -Filter "*.zip" -File).Count -ge 2) "zip"

  $processCommand = "`$s='x'*200000; [Console]::Out.Write(`$s); [Console]::Error.Write(`$s); exit 0"
  $processResult = Invoke-TopsProcessBytes -FileName $ps -Arguments @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $processCommand) -WorkingDirectory $sourceRepo
  Add-PackageResult "stdout e stderr sem deadlock" 0 $processResult.ExitCode (($processResult.Stdout.Length -ge 200000) -and ($processResult.Stderr.Length -ge 200000)) "processo"

  $pwshCmd = Get-Command pwsh -ErrorAction SilentlyContinue
  if ($pwshCmd) {
    $csvDir = Join-Path $tempRoot ("csv-pwsh-" + [guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Path $csvDir -Force | Out-Null
    $csvPath = Join-Path $csvDir "teste.csv"
    $helperEscaped = (Join-Path $sourceRepo "scripts/security/git-staged-utils.ps1") -replace "'", "''"
    $csvEscaped = $csvPath -replace "'", "''"
    $cmd = ". '$helperEscaped'; Write-TopsUtf8BomCsv -Path '$csvEscaped' -Rows @([pscustomobject]@{a='b'})"
    $csvResult = Invoke-TopsProcessBytes -FileName "pwsh" -Arguments @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $cmd) -WorkingDirectory $sourceRepo
    $csvBytes = if (Test-Path -LiteralPath $csvPath) { [IO.File]::ReadAllBytes($csvPath) } else { [byte[]]@() }
    Add-PackageResult "CSV com BOM em pwsh quando disponível" 0 $csvResult.ExitCode (($csvBytes.Length -ge 3) -and $csvBytes[0] -eq 0xEF -and $csvBytes[1] -eq 0xBB -and $csvBytes[2] -eq 0xBF) "csv"
  } else {
    Add-PackageResult "CSV com BOM em pwsh quando disponível" 0 0 $true "csv-pendente"
  }
} catch {
  $suiteError = $_
  Add-PackageResult "erro inesperado na suite do pacote" 0 1 $false "infra"
} finally {
  if (Test-Path -LiteralPath $tempRoot) {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
  }
}

Add-PackageResult "temporários removidos após suíte" 0 0 (-not (Test-Path -LiteralPath $tempRoot)) "temporários"

$report = New-Object System.Collections.Generic.List[string]
$report.Add("# Relatório de testes do pacote")
$report.Add("")
$report.Add("- Total de cenários: $($results.Count)")
$report.Add("- Cenários OK: $(@($results | Where-Object { $_.Resultado -eq "OK" }).Count)")
$report.Add("- Cenários com falha: $(@($results | Where-Object { $_.Resultado -ne "OK" }).Count)")
$report.Add("")
$report.Add("| Cenário | Categoria | Exit code esperado | Exit code obtido | Resultado |")
$report.Add("| --- | --- | ---: | ---: | --- |")
foreach ($item in $results) {
  $report.Add("| $($item.Cenario) | $($item.Categoria) | $($item.ExitCodeEsperado) | $($item.ExitCodeObtido) | $($item.Resultado) |")
}
Write-TopsUtf8NoBomLines -Path (Join-Path $sourceRepo "RELATORIO-TESTES-PACOTE.md") -Lines @($report)

if ($suiteError) {
  Write-Host "Erro inesperado na suite do pacote: $suiteError"
}

$failed = @($results | Where-Object { $_.Resultado -ne "OK" })
if ($failed.Count -gt 0) {
  Write-Host "Testes do pacote falharam: $($failed.Count)"
  exit 1
}

Write-Host "Todos os testes do pacote foram aprovados."
exit 0
