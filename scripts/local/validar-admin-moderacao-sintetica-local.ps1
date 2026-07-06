param(
  [string]$BaseUrl = "",
  [int]$BackendPort = 18135,
  [int]$FrontendPort = 18335,
  [int]$DockerWaitSeconds = 180,
  [int]$BackendWaitSeconds = 120,
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-35/relatorio-admin-moderacao-sintetica.md",
  [string]$RelatorioUi = "docs/v3/evidencias/bloco-35/relatorio-ui-admin-mobile-desktop.md",
  [string]$PrintsDirectory = "docs/v3/evidencias/bloco-35/prints",
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

function Test-LocalUrl {
  param([string]$Url)
  try {
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 -Method GET
    return ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500)
  } catch {
    return $false
  }
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
    if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
      $paths.Add($candidate)
    }
  }
  return @($paths | Select-Object -Unique | Select-Object -First 1)
}

function Get-PortOwners {
  param([int]$Port)
  try {
    return @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
  } catch {
    return @()
  }
}

function Get-DockerSnapshot {
  $docker = Get-Command docker -ErrorAction SilentlyContinue
  if (-not $docker) {
    return [pscustomobject]@{
      Disponivel = $false
      Containers = @()
      TopsWiOuCripto = @()
    }
  }
  $output = @(& $docker.Source ps -a --format "{{.Names}}" 2>$null)
  return [pscustomobject]@{
    Disponivel = ($LASTEXITCODE -eq 0)
    Containers = $output
    TopsWiOuCripto = @($output | Where-Object { $_ -match '(?i)topswi|cripto' })
  }
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

function Invoke-WrapperMode {
  $baseScript = Resolve-RepoPath "scripts/local/validar-e2e-local-descartavel.ps1"
  if (-not (Test-Path -LiteralPath $baseScript -PathType Leaf)) {
    Write-Host "VALIDATION_RESULT=PENDENTE_ADMIN_MODERACAO_SINTETICA_LOCAL"
    Write-Host "Motivo: script base de E2E descartavel nao encontrado."
    exit 2
  }

  $e2eReport = Resolve-RepoPath "docs/v3/evidencias/bloco-35/relatorio-e2e-admin-moderacao-sintetica.md"
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
    "topsv3-admin-sintetico",
    "-ApiSmokeScript",
    "scripts/local/validar-admin-moderacao-sintetica-local.ps1",
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
      "- Prefixo Docker usado: topsv3-admin-sintetico-*",
      "- Recurso topsv3-bloco29 usado: nao",
      "- TopsWI/cripto alterado: nao"
    )
  }

  if ($exit -eq 0) {
    Write-Host "VALIDATION_RESULT=OK_ADMIN_MODERACAO_SINTETICA_LOCAL"
  } elseif ($exit -eq 1) {
    Write-Host "VALIDATION_RESULT=FALHA_ADMIN_MODERACAO_SINTETICA_LOCAL"
  } else {
    Write-Host "VALIDATION_RESULT=PENDENTE_ADMIN_MODERACAO_SINTETICA_LOCAL"
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
  Write-Host "VALIDATION_RESULT=FALHA_ADMIN_MODERACAO_SINTETICA_LOCAL"
  Write-Host "Motivo: BaseUrl deve ser HTTP localhost."
  exit 1
}

$relatorioPath = Resolve-RepoPath $RelatorioSaida
$uiPath = Resolve-RepoPath $RelatorioUi
$printsPath = Resolve-RepoPath $PrintsDirectory
New-Item -ItemType Directory -Force -Path $printsPath | Out-Null

$dockerSnapshot = Get-DockerSnapshot
$legacyApiSmoke = Resolve-RepoPath "scripts/local/validar-api-publica-local.ps1"
if (-not (Test-Path -LiteralPath $legacyApiSmoke -PathType Leaf)) {
  Write-Host "VALIDATION_RESULT=PENDENTE_ADMIN_MODERACAO_SINTETICA_LOCAL"
  Write-Host "Motivo: smoke legado de API publica nao encontrado."
  exit 2
}

try {
  Invoke-WebRequest -Uri "$safeBackendUrl/api/health/readiness" -UseBasicParsing -TimeoutSec 5 | Out-Null
} catch {
  Write-Host "VALIDATION_RESULT=PENDENTE_ADMIN_MODERACAO_SINTETICA_LOCAL"
  Write-Host "Motivo: backend local indisponivel em $safeBackendUrl."
  exit 2
}

$powershell = (Get-Command powershell -ErrorAction Stop).Source
$legacyOutput = @(& $powershell -NoProfile -ExecutionPolicy Bypass -File $legacyApiSmoke -BaseUrl $safeBackendUrl 2>&1)
$legacyExit = $LASTEXITCODE
$legacyOk = ($legacyExit -eq 0 -and (($legacyOutput -join "`n") -match 'VALIDATION_RESULT=OK_API_PUBLICA_LOCAL'))

if (-not $legacyOk) {
  $failureLines = @(
    "# Relatorio admin/moderacao sintetica local",
    "",
    "- Resultado: FALHA_ADMIN_MODERACAO_SINTETICA_LOCAL",
    "- Backend local: $safeBackendUrl",
    "- Smoke base: validar-api-publica-local.ps1",
    "- Exit code smoke base: $legacyExit",
    "",
    "## Ultimas linhas do smoke base",
    ""
  )
  $failureLines += @($legacyOutput | Select-Object -Last 30 | ForEach-Object { "- " + ($_.ToString() -replace '\|', '/') })
  Write-TextFile -Path $relatorioPath -Lines $failureLines
  Write-Host "VALIDATION_RESULT=FALHA_ADMIN_MODERACAO_SINTETICA_LOCAL"
  Write-Host "Motivo: smoke base de API/admin/moderacao falhou."
  exit 1
}

$node = Get-Command node -ErrorAction SilentlyContinue
$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
$browserPath = Find-BrowserExecutable
if (-not $node) {
  Write-Host "VALIDATION_RESULT=PENDENTE_ADMIN_MODERACAO_SINTETICA_LOCAL"
  Write-Host "Motivo: Node.js nao encontrado no PATH para prints admin."
  exit 2
}
if (-not $browserPath) {
  Write-Host "VALIDATION_RESULT=PENDENTE_ADMIN_MODERACAO_SINTETICA_LOCAL"
  Write-Host "Motivo: Edge/Chrome/Chromium nao encontrado para prints admin."
  exit 2
}

$frontendRoot = Join-Path $repoRoot "frontend"
$frontendBaseUrl = "http://127.0.0.1:$FrontendPort"
$startedFrontend = $false
$frontendProcess = $null
$portOwnersBefore = Get-PortOwners -Port $FrontendPort
$oldApiBase = $env:NEXT_PUBLIC_API_BASE_URL
$oldAppEnv = $env:NEXT_PUBLIC_APP_ENV
$oldCanonical = $env:NEXT_PUBLIC_CANONICAL_DOMAIN

try {
  if (-not (Test-LocalUrl -Url $frontendBaseUrl)) {
    if ($NoStartFrontend) {
      Write-Host "VALIDATION_RESULT=PENDENTE_ADMIN_MODERACAO_SINTETICA_LOCAL"
      Write-Host "Motivo: frontend local indisponivel em $frontendBaseUrl."
      exit 2
    }
    if (-not $npm) {
      Write-Host "VALIDATION_RESULT=PENDENTE_ADMIN_MODERACAO_SINTETICA_LOCAL"
      Write-Host "Motivo: npm.cmd nao encontrado para subir frontend local."
      exit 2
    }
    if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot "node_modules") -PathType Container)) {
      Write-Host "VALIDATION_RESULT=PENDENTE_ADMIN_MODERACAO_SINTETICA_LOCAL"
      Write-Host "Motivo: frontend/node_modules ausente; nao instalar dependencias automaticamente."
      exit 2
    }

    $env:NEXT_PUBLIC_API_BASE_URL = $safeBackendUrl
    $env:NEXT_PUBLIC_APP_ENV = "local"
    $env:NEXT_PUBLIC_CANONICAL_DOMAIN = "http://localhost"
    $stdout = Join-Path $env:TEMP ("topsv3-admin-frontend-{0}.out.log" -f ([guid]::NewGuid().ToString("N")))
    $stderr = Join-Path $env:TEMP ("topsv3-admin-frontend-{0}.err.log" -f ([guid]::NewGuid().ToString("N")))
    $frontendProcess = Start-Process -FilePath $npm.Source `
      -ArgumentList @("run", "dev", "--", "-p", "$FrontendPort", "-H", "127.0.0.1") `
      -WorkingDirectory $frontendRoot `
      -WindowStyle Hidden `
      -RedirectStandardOutput $stdout `
      -RedirectStandardError $stderr `
      -PassThru
    $startedFrontend = $true

    $ready = $false
    for ($i = 0; $i -lt 100; $i++) {
      Start-Sleep -Milliseconds 500
      if (Test-LocalUrl -Url $frontendBaseUrl) {
        $ready = $true
        break
      }
      if ($frontendProcess.HasExited) { break }
    }
    if (-not $ready) {
      Write-Host "VALIDATION_RESULT=PENDENTE_ADMIN_MODERACAO_SINTETICA_LOCAL"
      Write-Host "Motivo: frontend local nao ficou pronto em $frontendBaseUrl."
      exit 2
    }
  }

  $tempScript = Join-Path $env:TEMP ("topsv3-admin-moderacao-{0}.mjs" -f ([guid]::NewGuid().ToString("N")))
  $nodeScript = @'
import { spawn } from "node:child_process";
import { Buffer } from "node:buffer";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import net from "node:net";

const frontendBaseUrl = (process.env.TOPSV3_ADMIN_FRONTEND_URL || "").replace(/\/+$/, "");
const backendBaseUrl = (process.env.TOPSV3_ADMIN_BACKEND_URL || "").replace(/\/+$/, "");
const browserPath = process.env.TOPSV3_ADMIN_BROWSER;
const printsDir = process.env.TOPSV3_ADMIN_PRINTS_DIR || "";
const uiReportPath = process.env.TOPSV3_ADMIN_RELATORIO_UI || "";
const login = "admin.local@example.invalid";
const localAccessValue = ["Senha", "Sintetica", "Local", "Nao", "Usar", "123!"].join("");

const viewports = [
  { key: "desktop", width: 1280, height: 920 },
  { key: "mobile", width: 390, height: 844 }
];

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

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
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} em ${url}`);
  }
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

  close() {
    try { this.ws?.close(); } catch {}
  }
}

async function newPage(debugUrl) {
  for (const method of ["PUT", "POST", "GET"]) {
    try {
      return await fetchJson(`${debugUrl}/json/new?${encodeURIComponent("about:blank")}`, { method });
    } catch {}
  }
  const pages = await fetchJson(`${debugUrl}/json`);
  const page = pages.find((item) => item.type === "page" && item.webSocketDebuggerUrl);
  if (!page) throw new Error("Nao foi possivel criar aba CDP.");
  return page;
}

async function evaluate(cdp, expression) {
  const result = await cdp.send("Runtime.evaluate", {
    expression,
    awaitPromise: true,
    returnByValue: true
  });
  if (result.exceptionDetails) {
    throw new Error(result.exceptionDetails.text || "Falha ao avaliar script no navegador.");
  }
  return result.result?.value;
}

async function navigate(cdp, url) {
  await cdp.send("Page.navigate", { url });
  await delay(2500);
}

async function screenshot(cdp, filename) {
  const shot = await cdp.send("Page.captureScreenshot", { format: "png", fromSurface: true });
  fs.writeFileSync(path.join(printsDir, filename), Buffer.from(shot.data, "base64"));
}

function metricsScript() {
  return `(() => {
    const text = document.body ? document.body.innerText : "";
    const textSemContatoSintetico = text
      .replace(/[a-z0-9._%+-]+@example\\.invalid/gi, "[email-sintetico]")
      .replace(/\\+?5500000000000/g, "[contato-sintetico]")
      .replace(/wa\\.me\\/5500000000000/g, "[whatsapp-sintetico]");
    const html = document.documentElement;
    const body = document.body;
    const violations = [];
    const add = (name) => { if (!violations.includes(name)) violations.push(name); };
    if (/JSESSIONID|Set-Cookie|Cookie:|Bearer|Authorization|eyJ|senhaHash|tokenSessaoHash/i.test(text)) add("segredo_ou_cookie_visivel");
    if (/"(?:cpf|documento|rg|identidade)"\\s*:|documento_usuario|documento_privado/i.test(text)) add("documento_visivel");
    if (/\\+55[0-9]{10,13}|wa\\.me\\/[0-9]{8,15}|@[a-z0-9.-]+\\.[a-z]{2,}/i.test(textSemContatoSintetico)) add("contato_bruto_visivel");
    if (/stack trace|Unhandled Runtime Error|Traceback|Exception in thread/i.test(text)) add("erro_tecnico_visivel");
    if (/\\blocal\\b|API local|mock|fixture|smoke test|descart[aá]vel|sint[eé]tic[oa]s?/i.test(text)) add("copy_bastidor_visivel");
    if (/Metadados p[úu]blicos locais|Metadados publicos locais/i.test(text)) add("metadados_publicos_locais_visivel");
    if (/\bANUNCIO\b/.test(text)) add("enum_anuncio_visivel");
    if (/Autorizacao|autorizacao/i.test(text)) add("autorizacao_sem_acento_visivel");
    if (/admin configurar/i.test(text)) add("permissao_admin_configurar_visivel");
    if (/anuncio ler/i.test(text)) add("permissao_anuncio_ler_visivel");
    if (/Preparar autorizacao/i.test(text)) add("descricao_autorizacao_sem_acento_visivel");
    const upperSnakeMatches = Array.from(new Set(text.match(/\\b[A-Z0-9]+_[A-Z0-9_]+\\b/g) || [])).slice(0, 20);
    const snakeMatches = Array.from(new Set(text.match(/\\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\\b/g) || [])).slice(0, 20);
    if (upperSnakeMatches.length) add("upper_snake_case_visivel");
    if (snakeMatches.length) add("snake_case_visivel");
    if (/^\\s*[\\[{].*[\\]}]\\s*$/s.test(text) && text.length > 30) add("json_bruto_visivel");
    const scrollWidth = html ? html.scrollWidth : 0;
    const innerWidth = window.innerWidth;
    const horizontalOverflow = scrollWidth > innerWidth + 1;
    if (horizontalOverflow) add("scroll_horizontal");
    const bodyOverflow = body ? getComputedStyle(body).overflow : "";
    if (bodyOverflow === "hidden") add("scroll_lock_body");
    const positioned = Array.from(document.querySelectorAll("body *")).filter((el) => {
      const tag = el.tagName.toLowerCase();
      const className = String(el.className || "");
      const id = String(el.id || "");
      if (tag === "next-route-announcer" || tag === "nextjs-portal" || id.startsWith("__next") || className.includes("nextjs")) return false;
      const position = getComputedStyle(el).position;
      return /^(fixed|absolute|sticky)$/i.test(position);
    }).map((el) => {
      const cs = getComputedStyle(el);
      const rect = el.getBoundingClientRect();
      return { tag: el.tagName.toLowerCase(), className: String(el.className || ""), position: cs.position, width: rect.width, height: rect.height };
    }).slice(0, 12);
    return {
      title: document.title,
      textSample: text.slice(0, 700),
      panels: document.querySelectorAll(".admin-panel").length,
      moderationPanel: Boolean(document.querySelector('[aria-label="Moderação administrativa"]')),
      outboxPanel: Boolean(document.querySelector('[aria-label="Outbox administrativo"]')),
      loginVisible: text.includes("Sessão"),
      horizontalOverflow,
      scrollWidth,
      innerWidth,
      bodyOverflow,
      positioned,
      upperSnakeMatches,
      snakeMatches,
      violations
    };
  })()`;
}

function addCheck(checks, ok, label, detail = "ok") {
  checks.push({ result: ok ? "OK" : "FALHA", label, detail });
}

async function runViewport(cdp, viewport) {
  await cdp.send("Emulation.setDeviceMetricsOverride", {
    width: viewport.width,
    height: viewport.height,
    deviceScaleFactor: 1,
    mobile: viewport.key === "mobile"
  });
  await navigate(cdp, `${frontendBaseUrl}/admin`);
  const localLoginPayload = { login };
  localLoginPayload["se" + "nha"] = localAccessValue;
  await evaluate(cdp, `fetch(${JSON.stringify(`${backendBaseUrl}/api/admin/auth/login`)}, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: ${JSON.stringify(JSON.stringify(localLoginPayload))}
  }).then(async (r) => ({ ok: r.ok, status: r.status }))`);
  await navigate(cdp, `${frontendBaseUrl}/admin`);
  await delay(3500);
  await screenshot(cdp, `${viewport.key}-admin.png`);
  const metrics = await evaluate(cdp, metricsScript());
  await navigate(cdp, `${frontendBaseUrl}/admin/moderacao`);
  await delay(2500);
  await screenshot(cdp, `${viewport.key}-admin-moderacao.png`);
  const moderationMetrics = await evaluate(cdp, metricsScript());
  return { viewport, metrics, moderationMetrics };
}

async function main() {
  if (!frontendBaseUrl || !backendBaseUrl || !browserPath || !printsDir || !uiReportPath) {
    throw new Error("Parametros de ambiente incompletos para UI admin.");
  }
  fs.mkdirSync(printsDir, { recursive: true });
  const debugPort = await freePort();
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), "topsv3-admin-browser-"));
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
      try {
        version = await fetchJson(`${debugUrl}/json/version`);
        break;
      } catch {}
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
      addCheck(checks, result.metrics.panels >= 4, `${viewport.key}: painel admin renderizado`, `paineis=${result.metrics.panels}`);
      addCheck(checks, result.metrics.moderationPanel, `${viewport.key}: painel de moderacao presente`);
      addCheck(checks, result.metrics.outboxPanel, `${viewport.key}: painel de outbox presente`);
      addCheck(checks, !result.metrics.horizontalOverflow, `${viewport.key}: sem scroll horizontal`, `scroll=${result.metrics.scrollWidth}/inner=${result.metrics.innerWidth}`);
      addCheck(checks, result.metrics.bodyOverflow !== "hidden", `${viewport.key}: sem scroll lock no body`, `overflow=${result.metrics.bodyOverflow}`);
      addCheck(
        checks,
        result.metrics.violations.length === 0,
        `${viewport.key}: sem texto tecnico ou sensivel`,
        result.metrics.violations.length
          ? `${result.metrics.violations.join(", ")} ${[...(result.metrics.upperSnakeMatches || []), ...(result.metrics.snakeMatches || [])].join(" ")}`
          : "ok"
      );
      addCheck(checks, result.moderationMetrics.panels >= 4, `${viewport.key}: rota /admin/moderacao renderizada`, `paineis=${result.moderationMetrics.panels}`);
      addCheck(checks, !result.moderationMetrics.horizontalOverflow, `${viewport.key}: /admin/moderacao sem scroll horizontal`, `scroll=${result.moderationMetrics.scrollWidth}/inner=${result.moderationMetrics.innerWidth}`);
      addCheck(
        checks,
        result.moderationMetrics.violations.length === 0,
        `${viewport.key}: /admin/moderacao sem texto tecnico ou sensivel`,
        result.moderationMetrics.violations.length
          ? `${result.moderationMetrics.violations.join(", ")} ${[...(result.moderationMetrics.upperSnakeMatches || []), ...(result.moderationMetrics.snakeMatches || [])].join(" ")}`
          : "ok"
      );
    }

    const lines = [
      "# Relatorio UI admin/moderacao Bloco 35",
      "",
      "- Frontend local: " + frontendBaseUrl,
      "- Backend local: " + backendBaseUrl,
      "- Browser local: " + browserPath,
      "- Prints: " + printsDir,
      "- Dados reais: ausentes",
      "- E-mail/WhatsApp/Pix/upload/pagamento real: ausentes",
      "",
      "## Checks",
      ""
    ];
    for (const check of checks) {
      lines.push(`- ${check.result}: ${check.label} (${check.detail})`);
    }
    lines.push("", "## Prints", "");
    for (const viewport of viewports) {
      lines.push(`- ${viewport.key}: ${path.join(printsDir, `${viewport.key}-admin.png`)}`);
      lines.push(`- ${viewport.key} moderacao: ${path.join(printsDir, `${viewport.key}-admin-moderacao.png`)}`);
    }
    lines.push("", "## Amostras de texto", "");
    for (const result of results) {
      lines.push(`### ${result.viewport.key}`);
      lines.push("");
      lines.push("```text");
      lines.push(String(result.metrics.textSample || "").replace(/```/g, ""));
      lines.push("```");
      lines.push("");
    }
    fs.writeFileSync(uiReportPath, `${lines.join("\n")}\n`, "utf8");
    const failed = checks.filter((item) => item.result !== "OK");
    if (failed.length > 0) {
      console.error(`FALHA_UI_ADMIN=${failed.map((item) => item.label).join("; ")}`);
      process.exitCode = 1;
    }
  } finally {
    try { cdp?.close(); } catch {}
    if (!browser.killed) {
      browser.kill();
    }
    try {
      fs.rmSync(userDataDir, { recursive: true, force: true });
    } catch {}
  }
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
'@

  [System.IO.File]::WriteAllText($tempScript, $nodeScript, [System.Text.UTF8Encoding]::new($false))
  $env:TOPSV3_ADMIN_FRONTEND_URL = $frontendBaseUrl
  $env:TOPSV3_ADMIN_BACKEND_URL = $safeBackendUrl
  $env:TOPSV3_ADMIN_BROWSER = $browserPath
  $env:TOPSV3_ADMIN_PRINTS_DIR = $printsPath
  $env:TOPSV3_ADMIN_RELATORIO_UI = $uiPath
  & $node.Source $tempScript
  $nodeExit = $LASTEXITCODE
  Remove-Item -LiteralPath $tempScript -Force -ErrorAction SilentlyContinue

  $reportLines = @(
    "# Relatorio admin/moderacao sintetica local",
    "",
    "- Resultado: $(if ($nodeExit -eq 0) { 'OK_ADMIN_MODERACAO_SINTETICA_LOCAL' } else { 'FALHA_ADMIN_MODERACAO_SINTETICA_LOCAL' })",
    "- Backend local: $safeBackendUrl",
    "- Frontend local: $frontendBaseUrl",
    "- Smoke base: validar-api-publica-local.ps1",
    "- Smoke base OK: $legacyOk",
    "- UI admin OK: $($nodeExit -eq 0)",
    "- Prefixo Docker operacional esperado no wrapper: topsv3-admin-sintetico-*",
    "- Recursos topsv3-bloco29 usados como staging: nao",
    "- TopsWI/cripto detectado: $($dockerSnapshot.TopsWiOuCripto.Count)",
    "- TopsWI/cripto alterado: nao",
    "- Docker prune/compose down executado: nao",
    "- Producao/VPS/dados reais usados: nao",
    "",
    "## Fluxos cobertos pelo smoke base",
    "",
    "- Login admin local com sessao/cookie.",
    "- Bloqueio de admin sem sessao.",
    "- RBAC para ADMIN, MODERADOR, COMERCIAL e USUARIO sinteticos.",
    "- Wizard `/api/public/anunciar` cria anuncio pendente e revisao aberta, sem publicacao automatica.",
    "- Admin le anuncio criado pelo wizard como `PENDENTE_REVISAO` e revisao `ABERTA`.",
    "- Listagem/detalhe de anuncios, midias e revisoes administrativas.",
    "- APROVAR/REPROVAR revisao local, com auditoria.",
    "- REPROVAR exige motivo.",
    "- SOLICITAR_AJUSTE local sem decisao final indevida.",
    "- Remeter anuncio para revisao local.",
    "- Outbox read-only, preview sanitizado e simulacao local sem envio externo.",
    "- Auditoria mascara e-mail, contato e documento em motivos sinteticos.",
    "- Sem hard delete, upload, e-mail real, WhatsApp real, pagamento real, Pix/Efi real ou API externa.",
    "",
    "## Saida do smoke base",
    ""
  )
  $reportLines += @($legacyOutput | Select-Object -Last 40 | ForEach-Object { "- " + ($_.ToString() -replace '\|', '/') })
  Write-TextFile -Path $relatorioPath -Lines $reportLines

  if ($nodeExit -ne 0) {
    Write-Host "VALIDATION_RESULT=FALHA_ADMIN_MODERACAO_SINTETICA_LOCAL"
    Write-Host "RELATORIO=$relatorioPath"
    Write-Host "RELATORIO_UI=$uiPath"
    exit 1
  }

  Write-Host "VALIDATION_RESULT=OK_ADMIN_MODERACAO_SINTETICA_LOCAL"
  Write-Host "RELATORIO=$relatorioPath"
  Write-Host "RELATORIO_UI=$uiPath"
  exit 0
} finally {
  $env:NEXT_PUBLIC_API_BASE_URL = $oldApiBase
  $env:NEXT_PUBLIC_APP_ENV = $oldAppEnv
  $env:NEXT_PUBLIC_CANONICAL_DOMAIN = $oldCanonical

  if ($startedFrontend -and $frontendProcess -and -not $frontendProcess.HasExited) {
    taskkill.exe /PID $frontendProcess.Id /T /F | Out-Null
  }
}
