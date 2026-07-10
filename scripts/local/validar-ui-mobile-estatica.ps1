Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

$frontendRoot = Join-Path $repoRoot "frontend/src"
if (-not (Test-Path -LiteralPath $frontendRoot -PathType Container)) {
  Write-Host "ERRO: frontend/src nao encontrado."
  exit 2
}

function Get-RelativePath {
  param([string]$FullName)
  return ($FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/')
}

function Test-PublicRuntimeFile {
  param([string]$RelativePath)
  return (
    $RelativePath -eq "frontend/src/app/layout.tsx" -or
    $RelativePath -eq "frontend/src/app/globals.css" -or
    $RelativePath -like "frontend/src/app/(public-routes)/*" -or
    $RelativePath -like "frontend/src/components/anuncios/*" -or
    $RelativePath -like "frontend/src/components/compliance/*" -or
    $RelativePath -like "frontend/src/components/layout/*" -or
    $RelativePath -like "frontend/src/components/site/*" -or
    $RelativePath -like "frontend/src/components/stories/*"
  )
}

$failures = New-Object System.Collections.Generic.List[object]
function Add-Failure {
  param([string]$File, [int]$Line, [string]$Category, [string]$Snippet)
  $failures.Add([pscustomobject]@{
    Arquivo = $File
    Linha = $Line
    Categoria = $Category
    Trecho = $Snippet.Trim()
  })
}

$allFiles = @(Get-ChildItem -LiteralPath $frontendRoot -Recurse -File -Include *.css,*.ts,*.tsx -ErrorAction SilentlyContinue | Sort-Object FullName)
$publicFiles = @($allFiles | Where-Object { Test-PublicRuntimeFile (Get-RelativePath $_.FullName) })

foreach ($file in $allFiles) {
  $relative = Get-RelativePath $file.FullName
  $lines = [System.IO.File]::ReadAllLines($file.FullName)
  for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match 'document\s*\.\s*body\s*\.\s*style\s*\.\s*overflow') {
      Add-Failure -File $relative -Line ($i + 1) -Category "scroll-lock-imperativo" -Snippet $lines[$i]
    }
  }
}

foreach ($file in $publicFiles) {
  $relative = Get-RelativePath $file.FullName
  $lines = [System.IO.File]::ReadAllLines($file.FullName)
  for ($i = 0; $i -lt $lines.Length; $i++) {
    $line = $lines[$i]
    if ($line -match '(?i)(?:^|[\s"''])w-\[100vw\](?:[\s"'']|$)' -or $line -match '(?i)(?<!max-)width\s*:\s*100vw') {
      Add-Failure -File $relative -Line ($i + 1) -Category "largura-publica-100vw" -Snippet $line
    }
    if ($line -match '(?i)animation-iteration-count\s*:\s*infinite' -or $line -match '(?i)\banimation\s*:[^;]*\binfinite\b') {
      Add-Failure -File $relative -Line ($i + 1) -Category "animacao-publica-infinita" -Snippet $line
    }
  }
}

$globalsPath = Join-Path $repoRoot "frontend/src/app/globals.css"
$globalsText = if (Test-Path -LiteralPath $globalsPath -PathType Leaf) { [System.IO.File]::ReadAllText($globalsPath) } else { "" }
$globalOverflowPattern = '(?is)(?:^|\})\s*(?:html|body|:root|html\s*,\s*body)\s*\{[^}]*overflow(?:-x)?\s*:\s*hidden'
if ($globalsText -match $globalOverflowPattern) {
  Add-Failure -File "frontend/src/app/globals.css" -Line 1 -Category "overflow-global-oculto" -Snippet "html/body/root com overflow hidden"
}

$hasTextContainment = $globalsText -match '(?i)overflow-wrap\s*:\s*(?:break-word|anywhere)' -or
  $globalsText -match '(?i)word-break\s*:\s*break-word'
if (-not $hasTextContainment) {
  Add-Failure -File "frontend/src/app/globals.css" -Line 1 -Category "contencao-texto-publico-ausente" -Snippet "overflow-wrap/word-break nao definido"
}

Write-Host "Validacao estatica de UI mobile"
Write-Host "Arquivos totais analisados para scroll lock: $($allFiles.Count)"
Write-Host "Arquivos publicos analisados: $($publicFiles.Count)"
Write-Host "Falhas materiais: $($failures.Count)"
Write-Host "Regras: sem document.body.style.overflow; sem overflow global oculto; sem largura publica 100vw; sem animacao infinita agressiva; com contencao de texto."

if ($failures.Count -gt 0) {
  foreach ($failure in $failures) {
    Write-Host ("- {0}:{1} [{2}] {3}" -f $failure.Arquivo, $failure.Linha, $failure.Categoria, $failure.Trecho)
  }
  Write-Host "VALIDATION_RESULT=FALHA_UI_MOBILE_ESTATICA"
  exit 1
}

Write-Host "VALIDATION_RESULT=OK_UI_MOBILE_ESTATICA"
exit 0
