Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

$frontendRoot = Join-Path $repoRoot "frontend/src"
$docsToCheck = @(
  "docs/v3/161-diretriz-ui-mobile-bloco-21.md",
  "docs/v3/162-checklist-validacao-mobile-bloco-21.md"
)

function Get-RelativePath {
  param([string]$FullName)
  return ($FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/')
}

function Get-DocumentationText {
  $buffer = New-Object System.Text.StringBuilder
  foreach ($rel in $docsToCheck) {
    $full = Join-Path $repoRoot ($rel -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (Test-Path -LiteralPath $full -PathType Leaf) {
      [void]$buffer.AppendLine([System.IO.File]::ReadAllText($full))
    }
  }
  return $buffer.ToString()
}

function Test-DocumentedFinding {
  param(
    [string]$RelativePath,
    [string]$Category
  )
  $docs = Get-DocumentationText
  return ($docs.Contains($RelativePath) -and $docs.Contains($Category))
}

function Add-Finding {
  param(
    [System.Collections.Generic.List[object]]$Findings,
    [string]$RelativePath,
    [int]$Line,
    [string]$Category,
    [string]$Snippet
  )
  $documented = Test-DocumentedFinding -RelativePath $RelativePath -Category $Category
  $Findings.Add([pscustomobject]@{
    Arquivo = $RelativePath
    Linha = $Line
    Categoria = $Category
    Documentado = $(if ($documented) { "SIM" } else { "NAO" })
    Trecho = $Snippet.Trim()
  })
}

$patterns = @(
  [pscustomobject]@{
    Categoria = "document.body.style.overflow"
    Regex = 'document\s*\.\s*body\s*\.\s*style\s*\.\s*overflow'
  },
  [pscustomobject]@{
    Categoria = "overflow-hidden-global"
    Regex = '(?i)^\s*(html|body|:root|html\s*,\s*body)\s*\{[^}]*overflow\s*:\s*hidden'
  },
  [pscustomobject]@{
    Categoria = "position-fixed"
    Regex = '(?i)position\s*:\s*fixed'
  },
  [pscustomobject]@{
    Categoria = "position-absolute"
    Regex = '(?i)position\s*:\s*absolute'
  },
  [pscustomobject]@{
    Categoria = "position-sticky"
    Regex = '(?i)position\s*:\s*sticky'
  },
  [pscustomobject]@{
    Categoria = "100vw"
    Regex = '(?i)\b100vw\b'
  },
  [pscustomobject]@{
    Categoria = "keyframes"
    Regex = '(?i)@keyframes'
  },
  [pscustomobject]@{
    Categoria = "animation"
    Regex = '(?i)\banimation(?:-name|-duration|-timing-function|-iteration-count|-delay|-fill-mode|-direction)?\s*:'
  },
  [pscustomobject]@{
    Categoria = "transform-publico"
    Regex = '(?i)\b(transform\s*:|translate(?:X|Y|3d)?\s*\()'
  }
)

$findings = New-Object System.Collections.Generic.List[object]

if (-not (Test-Path -LiteralPath $frontendRoot -PathType Container)) {
  Write-Host "ERRO: frontend/src nao encontrado."
  exit 2
}

$files = @(Get-ChildItem -LiteralPath $frontendRoot -Recurse -File -Include *.css,*.ts,*.tsx -ErrorAction SilentlyContinue | Sort-Object FullName)
foreach ($file in $files) {
  $relative = Get-RelativePath $file.FullName
  $lines = [System.IO.File]::ReadAllLines($file.FullName)
  for ($i = 0; $i -lt $lines.Length; $i++) {
    $line = $lines[$i]
    foreach ($pattern in $patterns) {
      if ($line -match $pattern.Regex) {
        Add-Finding -Findings $findings -RelativePath $relative -Line ($i + 1) -Category $pattern.Categoria -Snippet $line
      }
    }
  }
}

$globalsPath = Join-Path $repoRoot "frontend/src/app/globals.css"
if (Test-Path -LiteralPath $globalsPath -PathType Leaf) {
  $globalsText = [System.IO.File]::ReadAllText($globalsPath)
  $anywhereMatches = @([regex]::Matches($globalsText, 'overflow-wrap:\s*anywhere'))
  if ($anywhereMatches.Count -gt 1 -or (-not $globalsText.Contains(".route-pattern"))) {
    Add-Finding -Findings $findings `
      -RelativePath "frontend/src/app/globals.css" `
      -Line 1 `
      -Category "overflow-wrap-anywhere-publico" `
      -Snippet "overflow-wrap:anywhere deve ficar restrito a token tecnico, nunca a H1, breadcrumbs, botoes ou wizard publico."
  }
}

$undocumented = @($findings | Where-Object { $_.Documentado -ne "SIM" })

Write-Host "Validacao estatica de UI mobile"
Write-Host "Arquivos analisados: $($files.Count)"
Write-Host "Alertas encontrados: $($findings.Count)"
Write-Host "Alertas documentados: $(@($findings | Where-Object { $_.Documentado -eq "SIM" }).Count)"
Write-Host "Alertas sem justificativa: $($undocumented.Count)"

if ($findings.Count -gt 0) {
  Write-Host ""
  Write-Host "Alertas:"
  foreach ($finding in $findings) {
    Write-Host ("- {0}:{1} [{2}] documentado={3}" -f $finding.Arquivo, $finding.Linha, $finding.Categoria, $finding.Documentado)
  }
}

if ($undocumented.Count -gt 0) {
  Write-Host "VALIDATION_RESULT=PENDENTE_UI_MOBILE_ESTATICA"
  exit 2
}

Write-Host "VALIDATION_RESULT=OK_UI_MOBILE_ESTATICA"
exit 0
