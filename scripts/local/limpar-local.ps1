param(
  [switch]$Confirmar
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "tops-local-utils.ps1")

$repoRoot = Get-TopsLocalRepoRoot

if (-not $Confirmar) {
  Write-Host "Limpeza local não executada. Rode com -Confirmar para remover containers e volumes locais de storage-local."
  exit 0
}

$composeExitCode = Invoke-TopsLocalDockerCompose -RepoRoot $repoRoot -CommandArguments @("down", "--volumes", "--remove-orphans")
if ($composeExitCode -ne 0) { exit $composeExitCode }

$storageRoot = Join-Path $repoRoot "storage-local"
$resolvedRepo = [IO.Path]::GetFullPath($repoRoot).TrimEnd('\', '/')
$resolvedStorage = [IO.Path]::GetFullPath($storageRoot).TrimEnd('\', '/')
if (-not $resolvedStorage.StartsWith($resolvedRepo, [StringComparison]::OrdinalIgnoreCase)) {
  Write-Host "ERRO: storage-local resolvido fora do repositório."
  exit 2
}

if (Test-Path -LiteralPath $storageRoot) {
  Remove-Item -LiteralPath $storageRoot -Recurse -Force
}
Write-Host "Runtime local removido."
