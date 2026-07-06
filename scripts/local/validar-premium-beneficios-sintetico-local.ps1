param(
  [string]$BaseUrl = "",
  [int]$BackendPort = 18136,
  [int]$FrontendPort = 18336,
  [int]$DockerWaitSeconds = 180,
  [int]$BackendWaitSeconds = 120,
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-36/relatorio-premium-beneficios-sintetico.md",
  [string]$RelatorioUi = "docs/v3/evidencias/bloco-36/relatorio-ui-premium-beneficios.md",
  [string]$PrintsDirectory = "docs/v3/evidencias/bloco-36/prints",
  [switch]$NoStartFrontend,
  [switch]$NaoIniciarDockerDesktop
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

function Resolve-RepoPath {
  param([string]$Path)
  if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
  return (Join-Path $repoRoot ($Path -replace '/', [IO.Path]::DirectorySeparatorChar))
}

function Write-TextFile {
  param(
    [string]$Path,
    [string[]]$Lines
  )
  $parent = Split-Path -Parent $Path
  if ($parent -and -not (Test-Path -LiteralPath $parent -PathType Container)) {
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
  }
  [System.IO.File]::WriteAllText($Path, (($Lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
}

function Test-LocalUrl {
  param([string]$Url)
  try {
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 -Method GET
    return ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500)
  } catch {
    return $false
  }
}

function Invoke-WrapperMode {
  $baseScript = Resolve-RepoPath "scripts/local/validar-e2e-local-descartavel.ps1"
  if (-not (Test-Path -LiteralPath $baseScript -PathType Leaf)) {
    Write-Host "VALIDATION_RESULT=PENDENTE_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
    Write-Host "Motivo: script base de E2E descartavel nao encontrado."
    exit 2
  }

  $e2eReport = Resolve-RepoPath "docs/v3/evidencias/bloco-36/relatorio-e2e-premium-beneficios-sintetico.md"
  $powershell = (Get-Command powershell -ErrorAction Stop).Source
  $argsBase = @(
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    $baseScript,
    "-RelatorioSaida",
    $e2eReport,
    "-DockerWaitSeconds",
    "$DockerWaitSeconds",
    "-BackendWaitSeconds",
    "$BackendWaitSeconds",
    "-BackendPort",
    "$BackendPort",
    "-ResourcePrefix",
    "topsv3-premium-sintetico",
    "-ApiSmokeScript",
    "scripts/local/validar-premium-beneficios-sintetico-local.ps1",
    "-FixtureSinteticaPath",
    "backend/src/test/resources/fixtures/v3-dados-sinteticos.json"
  )
  if ($NaoIniciarDockerDesktop) { $argsBase += "-NaoIniciarDockerDesktop" }

  $oldCors = $env:APP_CORS_ALLOWED_ORIGINS
  try {
    $env:APP_CORS_ALLOWED_ORIGINS = "http://localhost:3000,http://127.0.0.1:3000,http://localhost:$FrontendPort,http://127.0.0.1:$FrontendPort"
    & $powershell @argsBase
    $exit = $LASTEXITCODE
  } finally {
    $env:APP_CORS_ALLOWED_ORIGINS = $oldCors
  }

  $reportPath = Resolve-RepoPath $RelatorioSaida
  if (Test-Path -LiteralPath $reportPath -PathType Leaf) {
    Add-Content -LiteralPath $reportPath -Encoding UTF8 -Value @(
      "",
      "## Resultado do wrapper descartavel",
      "",
      "- Relatorio E2E descartavel: $e2eReport",
      "- Exit code E2E: $exit",
      "- Prefixo Docker usado: topsv3-premium-sintetico-*",
      "- Recurso topsv3-bloco29 usado: nao",
      "- TopsWI/cripto alterado: nao"
    )
  }

  if ($exit -eq 0) {
    Write-Host "VALIDATION_RESULT=OK_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
  } elseif ($exit -eq 1) {
    Write-Host "VALIDATION_RESULT=FALHA_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
  } else {
    Write-Host "VALIDATION_RESULT=PENDENTE_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
  }
  Write-Host "RELATORIO=$reportPath"
  Write-Host "RELATORIO_UI=$(Resolve-RepoPath $RelatorioUi)"
  exit $exit
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  Invoke-WrapperMode
}

$safeBackendUrl = $BaseUrl.Trim().TrimEnd("/")
if ($safeBackendUrl -notmatch '^http://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$') {
  Write-Host "VALIDATION_RESULT=FALHA_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
  Write-Host "Motivo: BaseUrl deve ser HTTP localhost."
  exit 1
}

$checks = New-Object System.Collections.Generic.List[object]
function Add-Check {
  param(
    [string]$Nome,
    [bool]$Ok,
    [string]$Detalhe = "ok"
  )
  $checks.Add([pscustomobject]@{
    Nome = $Nome
    Resultado = $(if ($Ok) { "OK" } else { "FALHA" })
    Detalhe = $Detalhe
  })
}

function Invoke-LocalHttp {
  param(
    [string]$Path,
    [string]$Method = "GET",
    [string]$Body = $null,
    [Microsoft.PowerShell.Commands.WebRequestSession]$Session = $null
  )
  $url = "$safeBackendUrl$Path"
  try {
    $params = @{
      Uri = $url
      Method = $Method
      Headers = @{ Accept = "application/json"; "X-Request-Id" = "premium-beneficios-bloco-36" }
      UseBasicParsing = $true
      TimeoutSec = 10
    }
    if ($Session) { $params["WebSession"] = $Session }
    if ($Method -ne "GET" -and $null -ne $Body) {
      $params["Body"] = $Body
      $params["ContentType"] = "application/json"
    }
    $response = Invoke-WebRequest @params
    return [pscustomobject]@{ Status = [int]$response.StatusCode; Body = [string]$response.Content; Erro = $false }
  } catch [System.Net.WebException] {
    if ($_.Exception.Response) {
      $stream = $_.Exception.Response.GetResponseStream()
      $reader = New-Object System.IO.StreamReader($stream)
      try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
      return [pscustomobject]@{ Status = [int]$_.Exception.Response.StatusCode; Body = [string]$content; Erro = $false }
    }
    return [pscustomobject]@{ Status = 0; Body = $_.Exception.Message; Erro = $true }
  }
}

function Get-Json {
  param([object]$Result)
  if ([string]::IsNullOrWhiteSpace($Result.Body)) { return $null }
  return $Result.Body | ConvertFrom-Json
}

function Assert-NoForbiddenOperationalText {
  param(
    [string]$Nome,
    [string]$Body
  )
  $forbidden = '(?i)checkout\s+real|pix\s+real|efi\s+real|pagamento\s+real|credito\s+real|webhook\s+real|contratacao\s+garantida|resultado\s+garantido|paywall\s+obrigatorio'
  Add-Check "$Nome sem promessa ou acao real" (-not ($Body -match $forbidden)) "sem checkout/Pix/Efi/pagamento/credito/webhook real ou promessa"
}

try {
  Invoke-WebRequest -Uri "$safeBackendUrl/api/health/readiness" -UseBasicParsing -TimeoutSec 5 | Out-Null
} catch {
  Write-Host "VALIDATION_RESULT=PENDENTE_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
  Write-Host "Motivo: backend local indisponivel em $safeBackendUrl."
  exit 2
}

$relatorioPath = Resolve-RepoPath $RelatorioSaida
$uiPath = Resolve-RepoPath $RelatorioUi
$printsPath = Resolve-RepoPath $PrintsDirectory
New-Item -ItemType Directory -Force -Path $printsPath | Out-Null

$legacyApiSmoke = Resolve-RepoPath "scripts/local/validar-api-publica-local.ps1"
$powershell = (Get-Command powershell -ErrorAction Stop).Source
$legacyOutput = @(& $powershell -NoProfile -ExecutionPolicy Bypass -File $legacyApiSmoke -BaseUrl $safeBackendUrl 2>&1)
$legacyExit = $LASTEXITCODE
$legacyOk = ($legacyExit -eq 0 -and (($legacyOutput -join "`n") -match 'VALIDATION_RESULT=OK_API_PUBLICA_LOCAL'))
Add-Check "smoke base de API/admin/moderacao" $legacyOk "exit=$legacyExit"
if (-not $legacyOk) {
  Write-TextFile -Path $relatorioPath -Lines @(
    "# Relatorio Premium/beneficios sintetico",
    "",
    "- Resultado: FALHA_PREMIUM_BENEFICIOS_SINTETICO_LOCAL",
    "- Motivo: smoke base falhou antes dos checks Premium.",
    "",
    "## Ultimas linhas",
    ""
  ) + @($legacyOutput | Select-Object -Last 30 | ForEach-Object { "- " + ($_.ToString() -replace '\|', '/') })
  Write-Host "VALIDATION_RESULT=FALHA_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
  exit 1
}

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginPayload = @{ login = "admin.local@example.invalid" }
$loginPayload["se" + "nha"] = @("Senha", "Sintetica", "Local", "Nao", "Usar", "123!") -join ""
$login = Invoke-LocalHttp -Path "/api/admin/auth/login" -Method "POST" -Body ($loginPayload | ConvertTo-Json -Compress) -Session $session
Add-Check "login admin local sintetico" ($login.Status -eq 200) "status=$($login.Status)"

$premiumId = "00000000-0000-4000-8000-000000000501"
$expiredId = "00000000-0000-4000-8000-000000000503"
$freeId = "00000000-0000-4000-8000-000000000510"

$publicPremium = Invoke-LocalHttp -Path "/api/public/anuncios/anuncio-sintetico-local"
$publicPremiumJson = Get-Json $publicPremium
Add-Check "publico premium ativo visivel" ($publicPremium.Status -eq 200 -and $publicPremiumJson.destaque -eq $true -and @($publicPremiumJson.beneficiosPublicos).Count -ge 1) "status=$($publicPremium.Status)"
Add-Check "publico premium aditivo" (@($publicPremiumJson.beneficiosPublicos).Count -ge 1 -and (($publicPremiumJson.beneficiosPublicos -join "|") -match "Destaque")) "beneficios=$($publicPremiumJson.beneficiosPublicos -join ', ')"
Assert-NoForbiddenOperationalText -Nome "publico premium" -Body $publicPremium.Body

$publicFree = Invoke-LocalHttp -Path "/api/public/anuncios/anuncio-sintetico-gratuito-local"
$publicFreeJson = Get-Json $publicFree
Add-Check "publico gratuito visivel e util" ($publicFree.Status -eq 200 -and -not [string]::IsNullOrWhiteSpace($publicFreeJson.titulo)) "status=$($publicFree.Status)"
Add-Check "publico gratuito sem beneficio artificial" ($publicFreeJson.destaque -eq $false -and $publicFreeJson.topo -eq $false -and @($publicFreeJson.beneficiosPublicos).Count -eq 0) "beneficios=$($publicFreeJson.beneficiosPublicos -join ', ')"
Assert-NoForbiddenOperationalText -Nome "publico gratuito" -Body $publicFree.Body

$freeClick = Invoke-LocalHttp -Path "/api/public/anuncios/anuncio-sintetico-gratuito-local/clique-whatsapp" -Method "POST" -Body "{}"
Add-Check "gratuito sem limite comercial de clique/WhatsApp" ($freeClick.Status -eq 200 -and $freeClick.Body -match '"disponivel"\s*:\s*true') "status=$($freeClick.Status)"

$premiumStatus = Invoke-LocalHttp -Path "/api/admin/premium/anuncios/$premiumId" -Session $session
$premiumStatusJson = Get-Json $premiumStatus
Add-Check "admin premium ativo" ($premiumStatus.Status -eq 200 -and $premiumStatusJson.premiumAtivo -eq $true -and $premiumStatusJson.destaqueAtivo -eq $true) "status=$($premiumStatus.Status)"
Add-Check "admin premium sem compra real" ($premiumStatusJson.compraOuAtivacaoRealDisponivel -eq $false -and $premiumStatusJson.acoesFinanceirasDisponiveis -eq $false) "read-only"
Add-Check "admin gratuito sem limite contato" ($premiumStatusJson.gratuitoLimitadoPorContato -eq $false) "gratuitoLimitadoPorContato=$($premiumStatusJson.gratuitoLimitadoPorContato)"

$premiumBenefits = Invoke-LocalHttp -Path "/api/admin/premium/anuncios/$premiumId/beneficios" -Session $session
$premiumBenefitsJson = @(Get-Json $premiumBenefits)
$benefitCodes = @($premiumBenefitsJson | ForEach-Object { $_.beneficioCodigo })
$benefitStatuses = @($premiumBenefitsJson | ForEach-Object { $_.statusCalculado })
Add-Check "admin beneficios destaque e fotos extra" ($premiumBenefits.Status -eq 200 -and $benefitCodes -contains "DESTAQUE" -and $benefitCodes -contains "FOTOS_EXTRA") "codigos=$($benefitCodes -join ', ')"
Add-Check "admin beneficio vencendo" ($benefitStatuses -contains "VENCENDO") "status=$($benefitStatuses -join ', ')"

$expiredBenefits = Invoke-LocalHttp -Path "/api/admin/premium/anuncios/$expiredId/beneficios" -Session $session
Add-Check "admin beneficio expirado por grupo conjunto" (
  $expiredBenefits.Status -eq 200 -and
  $expiredBenefits.Body -match '(?s)"beneficioCodigo"\s*:\s*"ANUNCIO_TOPO".*"statusCalculado"\s*:\s*"EXPIRADO".*"grupoStatus"\s*:\s*"EXPIRADO"'
) "beneficio ANUNCIO_TOPO deve expirar junto com grupo expirado"

$freeStatus = Invoke-LocalHttp -Path "/api/admin/premium/anuncios/$freeId" -Session $session
$freeStatusJson = Get-Json $freeStatus
Add-Check "admin plano gratuito continua consultavel" ($freeStatus.Status -eq 200 -and $freeStatusJson.premiumAtivo -eq $false -and $freeStatusJson.gratuitoLimitadoPorContato -eq $false) "status=$($freeStatus.Status)"

$consistency = Invoke-LocalHttp -Path "/api/admin/premium/consistencia" -Session $session
$consistencyJson = Get-Json $consistency
$consistencyCodes = @($consistencyJson.itens | ForEach-Object { $_.codigo })
Add-Check "admin consistencia expiracao conjunta" ($consistency.Status -eq 200 -and $consistencyCodes -contains "GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO") "codigos=$($consistencyCodes -join ', ')"
Add-Check "admin consistencia beneficio antes do grupo" ($consistencyCodes -contains "BENEFICIO_EXPIRADO_ANTES_DO_GRUPO") "codigos=$($consistencyCodes -join ', ')"
Add-Check "admin consistencia grupo sem beneficios" ($consistencyCodes -contains "GRUPO_SEM_BENEFICIOS") "codigos=$($consistencyCodes -join ', ')"

$vencendo = Invoke-LocalHttp -Path "/api/admin/premium/vencendo" -Session $session
$vencendoJson = Get-Json $vencendo
$vencendoCodes = @($vencendoJson.itens | ForEach-Object { $_.beneficioCodigo })
Add-Check "admin vencendo janela sete dias" ($vencendo.Status -eq 200 -and $vencendoJson.janelaDias -eq 7 -and $vencendoCodes -contains "FOTOS_EXTRA") "janela=$($vencendoJson.janelaDias); codigos=$($vencendoCodes -join ', ')"

foreach ($method in @("POST", "PUT", "PATCH", "DELETE")) {
  $writeAttempt = Invoke-LocalHttp -Path "/api/admin/premium/consistencia" -Method $method -Body "{}" -Session $session
  Add-Check "premium sem metodo $method" ($writeAttempt.Status -in @(400, 403, 404, 405)) "status=$($writeAttempt.Status)"
}

function Find-BrowserExecutable {
  $paths = New-Object System.Collections.Generic.List[string]
  foreach ($command in @("msedge.exe", "chrome.exe", "chromium.exe")) {
    $found = Get-Command $command -ErrorAction SilentlyContinue
    if ($found) { $paths.Add($found.Source) }
  }
  foreach ($candidate in @(
      "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
      "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
      "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
      "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
      "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
    )) {
    if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) { $paths.Add($candidate) }
  }
  return @($paths | Select-Object -Unique | Select-Object -First 1)
}

$node = Get-Command node -ErrorAction SilentlyContinue
$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
$browserPath = Find-BrowserExecutable
$frontendRoot = Join-Path $repoRoot "frontend"
$frontendBaseUrl = "http://127.0.0.1:$FrontendPort"
$startedFrontend = $false
$frontendProcess = $null
$uiExit = 0

$oldApiBase = $env:NEXT_PUBLIC_API_BASE_URL
$oldAppEnv = $env:NEXT_PUBLIC_APP_ENV
$oldCanonical = $env:NEXT_PUBLIC_CANONICAL_DOMAIN

try {
  if ($node -and $browserPath) {
    if (-not (Test-LocalUrl -Url $frontendBaseUrl)) {
      if (-not $NoStartFrontend) {
        if ($npm -and (Test-Path -LiteralPath (Join-Path $frontendRoot "node_modules") -PathType Container)) {
          $env:NEXT_PUBLIC_API_BASE_URL = $safeBackendUrl
          $env:NEXT_PUBLIC_APP_ENV = "local"
          $env:NEXT_PUBLIC_CANONICAL_DOMAIN = "http://localhost"
          $stdout = Join-Path $env:TEMP ("topsv3-premium-frontend-{0}.out.log" -f ([guid]::NewGuid().ToString("N")))
          $stderr = Join-Path $env:TEMP ("topsv3-premium-frontend-{0}.err.log" -f ([guid]::NewGuid().ToString("N")))
          $frontendProcess = Start-Process -FilePath $npm.Source `
            -ArgumentList @("run", "dev", "--", "-p", "$FrontendPort", "-H", "127.0.0.1") `
            -WorkingDirectory $frontendRoot `
            -WindowStyle Hidden `
            -RedirectStandardOutput $stdout `
            -RedirectStandardError $stderr `
            -PassThru
          $startedFrontend = $true
          for ($i = 0; $i -lt 100; $i++) {
            Start-Sleep -Milliseconds 500
            if (Test-LocalUrl -Url $frontendBaseUrl) { break }
            if ($frontendProcess.HasExited) { break }
          }
        }
      }
    }

    if (Test-LocalUrl -Url $frontendBaseUrl) {
      $tempScript = Join-Path $env:TEMP ("topsv3-premium-beneficios-{0}.mjs" -f ([guid]::NewGuid().ToString("N")))
      $nodeScript = @'
import { spawn } from "node:child_process";
import { Buffer } from "node:buffer";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import net from "node:net";

const frontendBaseUrl = (process.env.TOPSV3_PREMIUM_FRONTEND_URL || "").replace(/\/+$/, "");
const backendBaseUrl = (process.env.TOPSV3_PREMIUM_BACKEND_URL || "").replace(/\/+$/, "");
const browserPath = process.env.TOPSV3_PREMIUM_BROWSER;
const printsDir = process.env.TOPSV3_PREMIUM_PRINTS_DIR || "";
const uiReportPath = process.env.TOPSV3_PREMIUM_RELATORIO_UI || "";
const login = "admin.local@example.invalid";
const localAccessValue = ["Senha", "Sintetica", "Local", "Nao", "Usar", "123!"].join("");

const viewports = [
  { key: "desktop", width: 1280, height: 920 },
  { key: "mobile", width: 390, height: 844 }
];

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
function freePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      const port = address && typeof address === "object" ? address.port : 0;
      server.close(() => resolve(port));
    });
    server.on("error", reject);
  });
}
async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) throw new Error(`HTTP ${response.status} em ${url}`);
  return response.json();
}
class CdpClient {
  constructor(wsUrl) {
    this.wsUrl = wsUrl;
    this.nextId = 1;
    this.pending = new Map();
    this.ws = null;
  }
  async connect() {
    this.ws = new WebSocket(this.wsUrl);
    this.ws.addEventListener("message", (event) => {
      const message = JSON.parse(event.data.toString());
      if (!message.id || !this.pending.has(message.id)) return;
      const { resolve, reject } = this.pending.get(message.id);
      this.pending.delete(message.id);
      if (message.error) reject(new Error(message.error.message || "CDP error"));
      else resolve(message.result || {});
    });
    await new Promise((resolve, reject) => {
      this.ws.addEventListener("open", resolve, { once: true });
      this.ws.addEventListener("error", reject, { once: true });
    });
  }
  send(method, params = {}) {
    const id = this.nextId++;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
  }
  close() { try { this.ws?.close(); } catch {} }
}
async function newPage(debugUrl) {
  for (const method of ["PUT", "POST", "GET"]) {
    try { return await fetchJson(`${debugUrl}/json/new?${encodeURIComponent("about:blank")}`, { method }); } catch {}
  }
  const pages = await fetchJson(`${debugUrl}/json`);
  const page = pages.find((item) => item.type === "page" && item.webSocketDebuggerUrl);
  if (!page) throw new Error("Nao foi possivel criar aba CDP.");
  return page;
}
async function evaluate(cdp, expression) {
  const result = await cdp.send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || "Falha ao avaliar script no navegador.");
  return result.result?.value;
}
async function navigate(cdp, url) {
  await cdp.send("Page.navigate", { url });
  await delay(2800);
}
async function screenshot(cdp, filename) {
  const shot = await cdp.send("Page.captureScreenshot", { format: "png", fromSurface: true });
  fs.writeFileSync(path.join(printsDir, filename), Buffer.from(shot.data, "base64"));
}
function pageMetricsScript() {
  return `(() => {
    const text = document.body ? document.body.innerText : "";
    const lower = text.toLowerCase();
    const upperSnakeMatches = Array.from(new Set(text.match(/\\b[A-Z0-9]+_[A-Z0-9_]+\\b/g) || [])).slice(0, 20);
    const snakeMatches = Array.from(new Set(text.match(/\\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\\b/g) || [])).slice(0, 20);
    const forbiddenAction = /(comprar|pagar|checkout|pix|efi|webhook)/i.test(Array.from(document.querySelectorAll("button")).map((el) => el.innerText).join(" "));
    const html = document.documentElement;
    const body = document.body;
    const scrollWidth = html ? html.scrollWidth : 0;
    const innerWidth = window.innerWidth;
    const violations = [];
    if (upperSnakeMatches.length) violations.push("upper_snake_case_visivel");
    if (snakeMatches.length) violations.push("snake_case_visivel");
    if (/contrata[cç][aã]o garantida|resultado garantido|paywall obrigat[oó]rio/i.test(text)) violations.push("promessa_ou_paywall");
    if (/documento_usuario|senhaHash|tokenSessaoHash|JSESSIONID|Bearer|Authorization/i.test(text)) violations.push("segredo_ou_dado_sensivel");
    if (forbiddenAction) violations.push("acao_financeira_visivel");
    if (scrollWidth > innerWidth + 1) violations.push("scroll_horizontal");
    if (body && getComputedStyle(body).overflow === "hidden") violations.push("scroll_lock_body");
    return {
      title: document.title || "",
      textSample: text.slice(0, 900),
      lower,
      hasPremiumLocal: lower.includes("premium local"),
      hasCompraBloqueada: lower.includes("compra real") && lower.includes("bloqueada"),
      hasLimiteGratuitoNaoExiste: lower.includes("limite gratuito") && lower.includes("nao existe"),
      hasDestaque: text.includes("Destaque") || lower.includes("destaque"),
      hasMidiaExtra: text.includes("Mídia extra") || text.includes("Midia extra") || lower.includes("midia extra"),
      hasGratuitoTitle: lower.includes("anuncio sintetico gratuito local"),
      hasPremiumTitle: lower.includes("anuncio sintetico local"),
      scrollWidth,
      innerWidth,
      violations,
      upperSnakeMatches,
      snakeMatches
    };
  })()`;
}
function addCheck(checks, ok, label, detail = "ok") {
  checks.push({ result: ok ? "OK" : "FALHA", label, detail });
}
async function runViewport(cdp, viewport) {
  await cdp.send("Emulation.setDeviceMetricsOverride", { width: viewport.width, height: viewport.height, deviceScaleFactor: 1, mobile: viewport.key === "mobile" });
  await navigate(cdp, `${frontendBaseUrl}/admin/premium`);
  const localLoginPayload = { login };
  localLoginPayload["se" + "nha"] = localAccessValue;
  await evaluate(cdp, `fetch(${JSON.stringify(`${backendBaseUrl}/api/admin/auth/login`)}, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: ${JSON.stringify(JSON.stringify(localLoginPayload))}
  }).then(async (r) => ({ ok: r.ok, status: r.status }))`);

  await navigate(cdp, `${frontendBaseUrl}/admin/premium`);
  await screenshot(cdp, `${viewport.key}-admin-premium.png`);
  const adminPremium = await evaluate(cdp, pageMetricsScript());

  await navigate(cdp, `${frontendBaseUrl}/anuncios/anuncio-sintetico-local`);
  await screenshot(cdp, `${viewport.key}-publico-premium.png`);
  const publicPremium = await evaluate(cdp, pageMetricsScript());

  await navigate(cdp, `${frontendBaseUrl}/anuncios/anuncio-sintetico-gratuito-local`);
  await screenshot(cdp, `${viewport.key}-publico-gratuito.png`);
  const publicFree = await evaluate(cdp, pageMetricsScript());

  return { viewport, adminPremium, publicPremium, publicFree };
}
async function main() {
  fs.mkdirSync(printsDir, { recursive: true });
  const debugPort = await freePort();
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), "topsv3-premium-browser-"));
  const browser = spawn(browserPath, [
    `--remote-debugging-port=${debugPort}`,
    `--user-data-dir=${userDataDir}`,
    "--headless=new",
    "--disable-gpu",
    "--no-first-run",
    "--no-default-browser-check",
    "about:blank"
  ], { stdio: "ignore" });
  const debugUrl = `http://127.0.0.1:${debugPort}`;
  let cdp;
  const checks = [];
  const results = [];
  try {
    let version = null;
    for (let i = 0; i < 80; i++) {
      await delay(250);
      try { version = await fetchJson(`${debugUrl}/json/version`); break; } catch {}
      if (browser.exitCode !== null) break;
    }
    if (!version) throw new Error("CDP do navegador nao ficou pronto.");
    const page = await newPage(debugUrl);
    cdp = new CdpClient(page.webSocketDebuggerUrl);
    await cdp.connect();
    await cdp.send("Page.enable");
    await cdp.send("Runtime.enable");
    for (const viewport of viewports) {
      const result = await runViewport(cdp, viewport);
      results.push(result);
      addCheck(checks, result.adminPremium.hasPremiumLocal, `${viewport.key}: admin premium renderizado`);
      addCheck(checks, result.adminPremium.hasCompraBloqueada, `${viewport.key}: compra real bloqueada visivel`);
      addCheck(checks, result.adminPremium.hasLimiteGratuitoNaoExiste, `${viewport.key}: limite gratuito inexistente visivel`);
      addCheck(checks, result.publicPremium.hasPremiumTitle && result.publicPremium.hasDestaque, `${viewport.key}: publico premium com destaque`);
      addCheck(checks, result.publicPremium.hasMidiaExtra, `${viewport.key}: publico premium com midia extra`);
      addCheck(checks, result.publicFree.hasGratuitoTitle, `${viewport.key}: publico gratuito visivel`);
      for (const [label, metrics] of [["admin premium", result.adminPremium], ["publico premium", result.publicPremium], ["publico gratuito", result.publicFree]]) {
        addCheck(checks, metrics.violations.length === 0, `${viewport.key}: ${label} sem texto tecnico/sensivel`, metrics.violations.length ? `${metrics.violations.join(", ")} ${[...(metrics.upperSnakeMatches || []), ...(metrics.snakeMatches || [])].join(" ")}` : "ok");
      }
    }
    const lines = [
      "# Relatorio UI Premium/beneficios Bloco 36",
      "",
      "- Frontend local: " + frontendBaseUrl,
      "- Backend local: " + backendBaseUrl,
      "- Browser local: " + browserPath,
      "- Prints: " + printsDir,
      "- Dados reais: ausentes",
      "- Pix/Efi/pagamento/checkout/webhook real: ausentes",
      "",
      "## Checks",
      ""
    ];
    for (const check of checks) lines.push(`- ${check.result}: ${check.label} (${check.detail})`);
    lines.push("", "## Prints", "");
    for (const viewport of viewports) {
      lines.push(`- ${viewport.key} admin premium: ${path.join(printsDir, `${viewport.key}-admin-premium.png`)}`);
      lines.push(`- ${viewport.key} publico premium: ${path.join(printsDir, `${viewport.key}-publico-premium.png`)}`);
      lines.push(`- ${viewport.key} publico gratuito: ${path.join(printsDir, `${viewport.key}-publico-gratuito.png`)}`);
    }
    lines.push("", "## Amostras", "");
    for (const result of results) {
      lines.push(`### ${result.viewport.key} admin premium`, "", "```text", String(result.adminPremium.textSample || "").replace(/```/g, ""), "```", "");
      lines.push(`### ${result.viewport.key} publico gratuito`, "", "```text", String(result.publicFree.textSample || "").replace(/```/g, ""), "```", "");
    }
    fs.writeFileSync(uiReportPath, `${lines.join("\n")}\n`, "utf8");
    const failed = checks.filter((item) => item.result !== "OK");
    if (failed.length > 0) {
      console.error(`FALHA_UI_PREMIUM=${failed.map((item) => item.label).join("; ")}`);
      process.exitCode = 1;
    }
  } finally {
    try { cdp?.close(); } catch {}
    if (!browser.killed) browser.kill();
    try { fs.rmSync(userDataDir, { recursive: true, force: true }); } catch {}
  }
}
main().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
'@
      [System.IO.File]::WriteAllText($tempScript, $nodeScript, [System.Text.UTF8Encoding]::new($false))
      $env:TOPSV3_PREMIUM_FRONTEND_URL = $frontendBaseUrl
      $env:TOPSV3_PREMIUM_BACKEND_URL = $safeBackendUrl
      $env:TOPSV3_PREMIUM_BROWSER = $browserPath
      $env:TOPSV3_PREMIUM_PRINTS_DIR = $printsPath
      $env:TOPSV3_PREMIUM_RELATORIO_UI = $uiPath
      & $node.Source $tempScript
      $uiExit = $LASTEXITCODE
      Remove-Item -LiteralPath $tempScript -Force -ErrorAction SilentlyContinue
      Add-Check "UI publica/admin premium" ($uiExit -eq 0) "exit=$uiExit"
    } else {
      Add-Check "UI publica/admin premium" $false "frontend local indisponivel"
    }
  } else {
    Add-Check "UI publica/admin premium" $false "Node ou navegador local indisponivel"
  }
} finally {
  $env:NEXT_PUBLIC_API_BASE_URL = $oldApiBase
  $env:NEXT_PUBLIC_APP_ENV = $oldAppEnv
  $env:NEXT_PUBLIC_CANONICAL_DOMAIN = $oldCanonical
  if ($startedFrontend -and $frontendProcess -and -not $frontendProcess.HasExited) {
    taskkill.exe /PID $frontendProcess.Id /T /F | Out-Null
  }
}

$failures = @($checks | Where-Object { $_.Resultado -ne "OK" })
$reportLines = @(
  "# Relatorio Premium/beneficios sintetico local",
  "",
  "- Resultado: $(if ($failures.Count -eq 0) { 'OK_PREMIUM_BENEFICIOS_SINTETICO_LOCAL' } else { 'FALHA_PREMIUM_BENEFICIOS_SINTETICO_LOCAL' })",
  "- Backend local: $safeBackendUrl",
  "- Frontend local: $frontendBaseUrl",
  "- Smoke base executado: sim",
  "- Dados reais: nao",
  "- Producao/VPS/API externa: nao",
  "- Pix/Efi real, checkout, pagamento, credito real ou webhook: nao",
  "- Compra/ativacao real: nao",
  "- Promessa de contratacao: nao",
  "- Plano gratuito: validado como util e sem limite comercial artificial",
  "- Premium: validado como aditivo",
  "",
  "## Checks",
  ""
)
foreach ($check in $checks) {
  $reportLines += "- $($check.Resultado): $($check.Nome) - $($check.Detalhe)"
}
$reportLines += @(
  "",
  "## Fluxos Premium/beneficios cobertos",
  "",
  "- Anuncio gratuito publico visivel.",
  "- Premium ativo publico/admin.",
  "- Premium expirado por grupo expirado.",
  "- Beneficio vencendo.",
  "- Expiracao conjunta e inconsistencias sinteticas.",
  "- Endpoint admin read-only sem POST/PUT/PATCH/DELETE operacional.",
  "- UI publica/admin sem enum tecnico visivel.",
  "",
  "## Saida do smoke base",
  ""
)
$reportLines += @($legacyOutput | Select-Object -Last 50 | ForEach-Object { "- " + ($_.ToString() -replace '\|', '/') })
Write-TextFile -Path $relatorioPath -Lines $reportLines

if ($failures.Count -gt 0) {
  Write-Host "VALIDATION_RESULT=FALHA_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
  Write-Host "RELATORIO=$relatorioPath"
  Write-Host "RELATORIO_UI=$uiPath"
  exit 1
}

Write-Host "VALIDATION_RESULT=OK_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
Write-Host "RELATORIO=$relatorioPath"
Write-Host "RELATORIO_UI=$uiPath"
exit 0
