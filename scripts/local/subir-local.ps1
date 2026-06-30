Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "tops-local-utils.ps1")

$repoRoot = Get-TopsLocalRepoRoot
Initialize-TopsLocalRuntimeDirectories -RepoRoot $repoRoot
exit (Invoke-TopsLocalDockerCompose -RepoRoot $repoRoot -CommandArguments @("up", "-d"))
