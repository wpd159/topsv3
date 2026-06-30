param(
  [string]$Servico,
  [int]$Tail = 200
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "tops-local-utils.ps1")

$repoRoot = Get-TopsLocalRepoRoot
$arguments = @("logs", "--tail", [string]$Tail)
if (-not [string]::IsNullOrWhiteSpace($Servico)) { $arguments += $Servico }
exit (Invoke-TopsLocalDockerCompose -RepoRoot $repoRoot -CommandArguments $arguments)
