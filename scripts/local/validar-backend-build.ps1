Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

$backendDir = Join-Path $repoRoot "backend"
$pomPath = Join-Path $backendDir "pom.xml"
$pending = New-Object System.Collections.Generic.List[string]

function Add-Pending {
  param([string]$Code, [string]$Message)
  $pending.Add($Code)
  Write-Host "${Code}: $Message"
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

function Find-Java17 {
  $candidates = New-Object System.Collections.Generic.List[string]
  $javaCommand = Get-Command java -ErrorAction SilentlyContinue
  if ($null -ne $javaCommand) {
    $candidates.Add($javaCommand.Source)
  }

  $roots = @(
    (Join-Path $env:USERPROFILE ".topsv3-toolchain\jdks"),
    (Join-Path $env:ProgramFiles "Eclipse Adoptium"),
    (Join-Path $env:ProgramFiles "Java"),
    (Join-Path $env:ProgramFiles "Microsoft"),
    (Join-Path $env:ProgramFiles "Oracle")
  )

  foreach ($root in $roots) {
    if (Test-Path -LiteralPath $root -PathType Container) {
      Get-ChildItem -LiteralPath $root -Recurse -File -Filter "java.exe" -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName.ToLowerInvariant().EndsWith("\bin\java.exe") } |
        ForEach-Object {
          if (-not $candidates.Contains($_.FullName)) {
            $candidates.Add($_.FullName)
          }
        }
    }
  }

  foreach ($candidate in $candidates) {
    $versionLines = Get-JavaVersionLines $candidate
    if ((Get-JavaMajorVersion $versionLines) -eq 17) {
      return [pscustomobject]@{
        JavaPath = $candidate
        JavaHome = (Split-Path -Parent (Split-Path -Parent $candidate))
        VersionLines = $versionLines
      }
    }
  }

  return $null
}

function Find-Maven {
  $mvnCommand = Get-Command mvn -ErrorAction SilentlyContinue
  if ($null -ne $mvnCommand) {
    return $mvnCommand.Source
  }

  $roots = @(
    (Join-Path $env:USERPROFILE ".topsv3-toolchain\maven"),
    (Join-Path $env:ProgramFiles "Apache"),
    (Join-Path $env:ProgramFiles "Maven"),
    "C:\tools"
  )

  foreach ($root in $roots) {
    if (Test-Path -LiteralPath $root -PathType Container) {
      $mvn = Get-ChildItem -LiteralPath $root -Recurse -File -Filter "mvn.cmd" -ErrorAction SilentlyContinue |
        Select-Object -First 1
      if ($null -ne $mvn) {
        return $mvn.FullName
      }
    }
  }

  return $null
}

Write-Host "Validacao local de build backend"
Write-Host "Repositorio: $repoRoot"
Write-Host "Politica: usar toolchain local ja instalada/localizada; sem banco, sem Flyway e sem aplicacao persistente."

if (-not (Test-Path -LiteralPath $pomPath -PathType Leaf)) {
  Add-Pending "PENDENTE_BACKEND_POM" "backend/pom.xml nao encontrado."
}

$java17 = Find-Java17
if ($null -eq $java17) {
  Add-Pending "PENDENTE_JAVA_17_LOCAL" "Java 17 LTS nao encontrado."
} else {
  Write-Host "JAVA_17_PATH=$($java17.JavaPath)"
  Write-Host "JAVA_HOME_PROCESS=$($java17.JavaHome)"
  foreach ($line in $java17.VersionLines) {
    Write-Host "JAVA_17_VERSION=$line"
  }
}

$mavenPath = Find-Maven
if ([string]::IsNullOrWhiteSpace($mavenPath)) {
  Add-Pending "PENDENTE_MAVEN_LOCAL" "Maven nao encontrado."
} else {
  Write-Host "MAVEN_PATH=$mavenPath"
}

if ($pending.Count -gt 0) {
  Write-Host "VALIDATION_RESULT=PENDENTE_BACKEND_BUILD_LOCAL"
  exit 2
}

$env:JAVA_HOME = $java17.JavaHome
$env:PATH = (Join-Path $java17.JavaHome "bin") + ";" + (Split-Path -Parent $mavenPath) + ";" + $env:PATH

Write-Host "Maven version:"
& $mavenPath -version
if ($LASTEXITCODE -ne 0) {
  Write-Host "VALIDATION_RESULT=FALHA_BACKEND_BUILD_LOCAL"
  exit 1
}

Push-Location $backendDir
try {
  Write-Host "Executando: mvn -q -DskipTests compile"
  & $mavenPath "-q" "-DskipTests" "compile"
  if ($LASTEXITCODE -ne 0) {
    Write-Host "VALIDATION_RESULT=FALHA_BACKEND_BUILD_LOCAL"
    exit 1
  }

  Write-Host "Executando: mvn -q test"
  & $mavenPath "-q" "test"
  $exitCode = $LASTEXITCODE
} finally {
  Pop-Location
}

if ($exitCode -eq 0) {
  Write-Host "VALIDATION_RESULT=OK_BACKEND_BUILD_LOCAL"
  exit 0
}

Write-Host "VALIDATION_RESULT=FALHA_BACKEND_BUILD_LOCAL"
exit 1
