Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

function Get-JavaVersionLines {
  param([string]$JavaPath)

  return @(& cmd.exe /c "`"$JavaPath`" -version 2>&1")
}

function Get-JavaMajorVersion {
  param([string[]]$VersionLines)

  $text = $VersionLines -join "`n"
  if ($text -match 'version\s+"(?<major>[0-9]+)(?:\.(?<minor>[0-9]+))?') {
    $major = [int]$matches["major"]
    if ($major -eq 1 -and $matches["minor"]) {
      return [int]$matches["minor"]
    }
    return $major
  }
  return $null
}

function Add-ExistingRoot {
  param(
    [System.Collections.Generic.List[string]]$Roots,
    [string]$Path
  )

  if (-not [string]::IsNullOrWhiteSpace($Path) -and (Test-Path -LiteralPath $Path -PathType Container)) {
    if (-not $Roots.Contains($Path)) {
      $Roots.Add($Path)
    }
  }
}

function Find-JavaInstallations {
  $roots = New-Object System.Collections.Generic.List[string]
  $toolchainRoot = Join-Path $env:USERPROFILE ".topsv3-toolchain"
  Add-ExistingRoot $roots (Join-Path $toolchainRoot "jdks")
  Add-ExistingRoot $roots (Join-Path $env:ProgramFiles "Eclipse Adoptium")
  Add-ExistingRoot $roots (Join-Path $env:ProgramFiles "Java")
  Add-ExistingRoot $roots (Join-Path $env:ProgramFiles "Microsoft")
  Add-ExistingRoot $roots (Join-Path $env:ProgramFiles "Oracle")
  Add-ExistingRoot $roots (Join-Path $env:ProgramFiles "Amazon Corretto")
  if (${env:ProgramFiles(x86)}) {
    Add-ExistingRoot $roots (Join-Path ${env:ProgramFiles(x86)} "Java")
    Add-ExistingRoot $roots (Join-Path ${env:ProgramFiles(x86)} "Microsoft")
  }

  $javaFiles = New-Object System.Collections.Generic.List[string]
  foreach ($root in $roots) {
    Get-ChildItem -LiteralPath $root -Recurse -File -Filter "java.exe" -ErrorAction SilentlyContinue |
      Where-Object { $_.FullName -match '\\bin\\java\.exe$' } |
      ForEach-Object {
        if (-not $javaFiles.Contains($_.FullName)) {
          $javaFiles.Add($_.FullName)
        }
      }
  }
  return @($javaFiles | Sort-Object)
}

function Find-MavenInstallations {
  $candidateRoots = New-Object System.Collections.Generic.List[string]
  $toolchainRoot = Join-Path $env:USERPROFILE ".topsv3-toolchain"
  Add-ExistingRoot $candidateRoots (Join-Path $toolchainRoot "maven")
  Add-ExistingRoot $candidateRoots (Join-Path $env:ProgramFiles "Apache")
  Add-ExistingRoot $candidateRoots (Join-Path $env:ProgramFiles "Maven")
  Add-ExistingRoot $candidateRoots "C:\tools"
  Add-ExistingRoot $candidateRoots "C:\ProgramData\chocolatey\lib"
  if (${env:ProgramFiles(x86)}) {
    Add-ExistingRoot $candidateRoots (Join-Path ${env:ProgramFiles(x86)} "Apache")
  }

  $mavenFiles = New-Object System.Collections.Generic.List[string]
  foreach ($root in $candidateRoots) {
    Get-ChildItem -LiteralPath $root -Recurse -File -Filter "mvn.cmd" -ErrorAction SilentlyContinue |
      ForEach-Object {
        if (-not $mavenFiles.Contains($_.FullName)) {
          $mavenFiles.Add($_.FullName)
        }
      }
  }
  return @($mavenFiles | Sort-Object)
}

function Write-ToolCommand {
  param([string]$Name)

  $command = Get-Command $Name -ErrorAction SilentlyContinue
  if ($null -eq $command) {
    Write-Host "$($Name.ToUpperInvariant())=PENDENTE"
  } else {
    Write-Host "$($Name.ToUpperInvariant())_PATH=$($command.Source)"
  }
}

Write-Host "Diagnostico local de toolchain"
Write-Host "Repositorio: $repoRoot"
Write-Host "Politica: diagnostico somente leitura; sem instalar, sem baixar, sem alterar PATH."
Write-Host ""

Write-Host "## Java no PATH"
$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if ($null -eq $javaCommand) {
  Write-Host "JAVA_PATH=PENDENTE"
} else {
  Write-Host "JAVA_PATH=$($javaCommand.Source)"
  $javaVersionLines = Get-JavaVersionLines $javaCommand.Source
  foreach ($line in $javaVersionLines) {
    Write-Host "JAVA_VERSION=$line"
  }
  $javaMajor = Get-JavaMajorVersion $javaVersionLines
  Write-Host "JAVA_MAJOR=$javaMajor"
}
Write-Host ""

Write-Host "## Java local fora do PATH"
$javaInstallations = @(Find-JavaInstallations)
$java17Found = $false
foreach ($javaPath in $javaInstallations) {
  $versionLines = Get-JavaVersionLines $javaPath
  $major = Get-JavaMajorVersion $versionLines
  if ($major -eq 17) {
    $java17Found = $true
  }
  Write-Host "JAVA_LOCAL_PATH=$javaPath"
  Write-Host "JAVA_LOCAL_MAJOR=$major"
}
if (-not $java17Found) {
  Write-Host "PENDENTE_JAVA_17_LOCAL: Java 17 LTS nao encontrado nos locais comuns pesquisados."
} else {
  Write-Host "JAVA_17_LOCAL_ENCONTRADO=true"
}
Write-Host ""

Write-Host "## Maven"
$mvnCommand = Get-Command mvn -ErrorAction SilentlyContinue
if ($null -eq $mvnCommand) {
  Write-Host "MAVEN_PATH=PENDENTE"
} else {
  Write-Host "MAVEN_PATH=$($mvnCommand.Source)"
  & cmd.exe /c "`"$($mvnCommand.Source)`" -version 2>&1" | Select-Object -First 4 | ForEach-Object { Write-Host "MAVEN_VERSION=$_" }
}

$mavenInstallations = @(Find-MavenInstallations)
if ($mavenInstallations.Count -eq 0) {
  Write-Host "PENDENTE_MAVEN_LOCAL: Maven nao encontrado no PATH nem nos locais comuns pesquisados."
} else {
  foreach ($mavenPath in $mavenInstallations) {
    Write-Host "MAVEN_LOCAL_PATH=$mavenPath"
  }
}
Write-Host ""

Write-Host "## Node e npm"
$nodeCommand = Get-Command node -ErrorAction SilentlyContinue
if ($null -eq $nodeCommand) {
  Write-Host "NODE_PATH=PENDENTE"
} else {
  Write-Host "NODE_PATH=$($nodeCommand.Source)"
  Write-Host "NODE_VERSION=$(& $nodeCommand.Source --version)"
}

$npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
if ($null -eq $npmCommand) {
  $npmCommand = Get-Command npm -ErrorAction SilentlyContinue
}
if ($null -eq $npmCommand) {
  Write-Host "NPM_PATH=PENDENTE"
} else {
  Write-Host "NPM_PATH=$($npmCommand.Source)"
  Write-Host "NPM_VERSION=$(& $npmCommand.Source --version)"
}

$nodeModulesPath = Join-Path $repoRoot "frontend\node_modules"
Write-Host "FRONTEND_NODE_MODULES=$(Test-Path -LiteralPath $nodeModulesPath -PathType Container)"
if (-not (Test-Path -LiteralPath $nodeModulesPath -PathType Container)) {
  Write-Host "PENDENTE_NODE_MODULES_LOCAL: frontend/node_modules ausente."
}
Write-Host ""

Write-Host "## Gerenciadores detectados apenas informativamente"
Write-ToolCommand "winget"
Write-ToolCommand "choco"
Write-ToolCommand "scoop"
Write-Host ""

Write-Host "## Comandos sugeridos para execucao manual/autorizada"
Write-Host "NAO_EXECUTADO: winget install EclipseAdoptium.Temurin.17.JDK"
Write-Host "NAO_EXECUTADO: winget install Apache.Maven"
Write-Host "NAO_EXECUTADO: choco install temurin17 -y"
Write-Host "NAO_EXECUTADO: choco install maven -y"
Write-Host "NAO_EXECUTADO: scoop install java/temurin17-jdk"
Write-Host "NAO_EXECUTADO: scoop install maven"
Write-Host "NAO_EXECUTADO: npm install"
Write-Host "NAO_EXECUTADO: npm ci"
Write-Host ""

Write-Host "VALIDATION_RESULT=OK_DIAGNOSTICO_TOOLCHAIN_LOCAL"
exit 0
