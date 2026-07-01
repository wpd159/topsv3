Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

function Invoke-LocalValidation {
  param([string]$ScriptRelativePath)

  $scriptPath = Join-Path $repoRoot ($ScriptRelativePath -replace '/', [IO.Path]::DirectorySeparatorChar)
  if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
    Write-Host "PENDENTE_SCRIPT_LOCAL: $ScriptRelativePath nao encontrado."
    return 2
  }

  Write-Host ""
  Write-Host "==> $ScriptRelativePath"
  $processInfo = New-Object System.Diagnostics.ProcessStartInfo
  $processInfo.FileName = "powershell"
  $processInfo.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`""
  $processInfo.RedirectStandardOutput = $true
  $processInfo.RedirectStandardError = $true
  $processInfo.UseShellExecute = $false

  $process = New-Object System.Diagnostics.Process
  $process.StartInfo = $processInfo
  [void]$process.Start()
  $stdout = $process.StandardOutput.ReadToEnd()
  $stderr = $process.StandardError.ReadToEnd()
  $process.WaitForExit()

  if (-not [string]::IsNullOrWhiteSpace($stdout)) {
    Write-Host $stdout.TrimEnd()
  }
  if (-not [string]::IsNullOrWhiteSpace($stderr)) {
    Write-Host $stderr.TrimEnd()
  }

  return [int]$process.ExitCode
}

Write-Host "Validacao local agregada de build backend/frontend"
Write-Host "Politica: nao instalar, nao baixar dependencias, nao acessar banco e nao acessar rede externa."
Write-Host "Para diagnostico detalhado, execute scripts/local/diagnosticar-toolchain-local.ps1."

$backendExit = Invoke-LocalValidation "scripts/local/validar-backend-build.ps1"
$frontendExit = Invoke-LocalValidation "scripts/local/validar-frontend-build.ps1"

Write-Host ""
Write-Host "Resumo build local:"
Write-Host "BACKEND_EXIT=$backendExit"
Write-Host "FRONTEND_EXIT=$frontendExit"

if ($backendExit -eq 1 -or $frontendExit -eq 1) {
  Write-Host "VALIDATION_RESULT=FALHA_BUILD_LOCAL"
  exit 1
}

if ($backendExit -eq 2 -or $frontendExit -eq 2) {
  Write-Host "VALIDATION_RESULT=PENDENTE_BUILD_LOCAL"
  exit 2
}

Write-Host "VALIDATION_RESULT=OK_BUILD_LOCAL"
exit 0
