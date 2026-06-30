Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "tops-local-utils.ps1")

$repoRoot = Get-TopsLocalRepoRoot
exit (Invoke-TopsLocalDockerCompose -RepoRoot $repoRoot -CommandArguments @("config"))
