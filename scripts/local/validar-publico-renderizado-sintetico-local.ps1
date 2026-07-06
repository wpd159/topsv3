param(
  [string]$BaseUrl = "",
  [int]$BackendPort = 18132,
  [int]$FrontendPort = 18332,
  [int]$DockerWaitSeconds = 180,
  [int]$BackendWaitSeconds = 120,
  [string]$RelatorioAuditoria = "docs/v3/evidencias/bloco-32-1/relatorio-auditoria-renderizada-pos-correcao.md",
  [string]$RelatorioSeo = "docs/v3/evidencias/bloco-32-1/relatorio-seo-renderizado-sintetico.md",
  [string]$RelatorioUi = "docs/v3/evidencias/bloco-32-1/relatorio-ui-mobile-desktop-sintetico.md",
  [string]$PrintsDirectory = "docs/v3/evidencias/bloco-32-1/prints",
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

function Invoke-WrapperMode {
  $baseScript = Resolve-RepoPath "scripts/local/validar-e2e-local-descartavel.ps1"
  if (-not (Test-Path -LiteralPath $baseScript -PathType Leaf)) {
    Write-Host "VALIDATION_RESULT=PENDENTE_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
    Write-Host "Motivo: script base de E2E descartavel nao encontrado."
    exit 2
  }

  $e2eReport = Resolve-RepoPath "docs/v3/evidencias/bloco-32-1/relatorio-e2e-renderizado-sintetico.md"
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
    "topsv3-render-sintetico",
    "-ApiSmokeScript",
    "scripts/local/validar-publico-renderizado-sintetico-local.ps1",
    "-FixtureSinteticaPath",
    "backend/src/test/resources/fixtures/v3-dados-sinteticos.json"
  )
  if ($NaoIniciarDockerDesktop) { $argsBase += "-NaoIniciarDockerDesktop" }

  & $powershell @argsBase
  $exit = $LASTEXITCODE
  if ($exit -eq 0) {
    Write-Host "VALIDATION_RESULT=OK_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
  } elseif ($exit -eq 1) {
    Write-Host "VALIDATION_RESULT=FALHA_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
  } else {
    Write-Host "VALIDATION_RESULT=PENDENTE_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
  }
  exit $exit
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  Invoke-WrapperMode
}

$safeBackendUrl = $BaseUrl.Trim().TrimEnd("/")
if ($safeBackendUrl -notmatch '^http://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$') {
  Write-Host "VALIDATION_RESULT=FALHA_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
  Write-Host "Motivo: BaseUrl deve ser HTTP localhost."
  exit 1
}

$node = Get-Command node -ErrorAction SilentlyContinue
$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
$browserPath = Find-BrowserExecutable
if (-not $node) {
  Write-Host "VALIDATION_RESULT=PENDENTE_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
  Write-Host "Motivo: Node.js nao encontrado no PATH."
  exit 2
}
if (-not $browserPath) {
  Write-Host "VALIDATION_RESULT=PENDENTE_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
  Write-Host "Motivo: Edge/Chrome/Chromium nao encontrado para renderizacao local."
  exit 2
}

$frontendRoot = Join-Path $repoRoot "frontend"
$frontendBaseUrl = "http://127.0.0.1:$FrontendPort"
$auditoriaPath = Resolve-RepoPath $RelatorioAuditoria
$seoPath = Resolve-RepoPath $RelatorioSeo
$uiPath = Resolve-RepoPath $RelatorioUi
$printsPath = Resolve-RepoPath $PrintsDirectory
foreach ($path in @($auditoriaPath, $seoPath, $uiPath, $printsPath)) {
  $parent = if ([System.IO.Path]::GetExtension($path)) { Split-Path -Parent $path } else { $path }
  if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
}

try {
  Invoke-WebRequest -Uri "$safeBackendUrl/api/health/readiness" -UseBasicParsing -TimeoutSec 5 | Out-Null
} catch {
  Write-Host "VALIDATION_RESULT=PENDENTE_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
  Write-Host "Motivo: backend local indisponivel em $safeBackendUrl."
  exit 2
}

$legacyApiSmoke = Resolve-RepoPath "scripts/local/validar-api-publica-local.ps1"
if (-not (Test-Path -LiteralPath $legacyApiSmoke -PathType Leaf)) {
  Write-Host "VALIDATION_RESULT=PENDENTE_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
  Write-Host "Motivo: smoke legado de API publica nao encontrado."
  exit 2
}
$powershell = (Get-Command powershell -ErrorAction Stop).Source
$legacyOutput = & $powershell -NoProfile -ExecutionPolicy Bypass -File $legacyApiSmoke -BaseUrl $safeBackendUrl 2>&1
if ($LASTEXITCODE -ne 0) {
  Write-Host "VALIDATION_RESULT=FALHA_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
  Write-Host "Motivo: smoke legado de API publica falhou antes da renderizacao."
  $legacyOutput | Select-Object -Last 12 | ForEach-Object { Write-Host $_ }
  exit 1
}

$startedFrontend = $false
$frontendProcess = $null
$portOwnersBefore = Get-PortOwners -Port $FrontendPort
$oldApiBase = $env:NEXT_PUBLIC_API_BASE_URL
$oldAppEnv = $env:NEXT_PUBLIC_APP_ENV
$oldCanonical = $env:NEXT_PUBLIC_CANONICAL_DOMAIN

try {
  if (-not (Test-LocalUrl -Url $frontendBaseUrl)) {
    if ($NoStartFrontend) {
      Write-Host "VALIDATION_RESULT=PENDENTE_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
      Write-Host "Motivo: frontend local indisponivel em $frontendBaseUrl."
      exit 2
    }
    if (-not $npm) {
      Write-Host "VALIDATION_RESULT=PENDENTE_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
      Write-Host "Motivo: npm.cmd nao encontrado para subir frontend local."
      exit 2
    }
    if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot "node_modules") -PathType Container)) {
      Write-Host "VALIDATION_RESULT=PENDENTE_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
      Write-Host "Motivo: frontend/node_modules ausente; nao instalar dependencias automaticamente."
      exit 2
    }

    $env:NEXT_PUBLIC_API_BASE_URL = $safeBackendUrl
    $env:NEXT_PUBLIC_APP_ENV = "local"
    $env:NEXT_PUBLIC_CANONICAL_DOMAIN = "http://localhost"
    $stdout = Join-Path $env:TEMP ("topsv3-render-frontend-{0}.out.log" -f ([guid]::NewGuid().ToString("N")))
    $stderr = Join-Path $env:TEMP ("topsv3-render-frontend-{0}.err.log" -f ([guid]::NewGuid().ToString("N")))
    $frontendProcess = Start-Process -FilePath $npm.Source `
      -ArgumentList @("run", "dev", "--", "-p", "$FrontendPort", "-H", "127.0.0.1") `
      -WorkingDirectory $frontendRoot `
      -WindowStyle Hidden `
      -RedirectStandardOutput $stdout `
      -RedirectStandardError $stderr `
      -PassThru
    $startedFrontend = $true

    $ready = $false
    for ($i = 0; $i -lt 80; $i++) {
      Start-Sleep -Milliseconds 500
      if (Test-LocalUrl -Url $frontendBaseUrl) {
        $ready = $true
        break
      }
      if ($frontendProcess.HasExited) { break }
    }
    if (-not $ready) {
      Write-Host "VALIDATION_RESULT=PENDENTE_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
      Write-Host "Motivo: frontend local nao ficou pronto em $frontendBaseUrl."
      exit 2
    }
  }

  $tempScript = Join-Path $env:TEMP ("topsv3-render-publico-{0}.mjs" -f ([guid]::NewGuid().ToString("N")))
  $nodeScript = @'
import { spawn } from "node:child_process";
import { Buffer } from "node:buffer";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import net from "node:net";

const frontendBaseUrl = (process.env.TOPSV3_RENDER_FRONTEND_URL || "").replace(/\/+$/, "");
const backendBaseUrl = (process.env.TOPSV3_RENDER_BACKEND_URL || "").replace(/\/+$/, "");
const browserPath = process.env.TOPSV3_RENDER_BROWSER;
const printsDir = process.env.TOPSV3_RENDER_PRINTS_DIR || "";
const auditoriaPath = process.env.TOPSV3_RENDER_RELATORIO_AUDITORIA || "";
const seoPath = process.env.TOPSV3_RENDER_RELATORIO_SEO || "";
const uiPath = process.env.TOPSV3_RENDER_RELATORIO_UI || "";

const routes = [
  { key: "home", path: "/", screenshot: "desktop-home", type: "page", h1: true, breadcrumbs: false },
  { key: "anunciar", path: "/anunciar", screenshot: "desktop-anunciar", type: "page", h1: true, breadcrumbs: false },
  { key: "cidade-goiania", path: "/acompanhantes/go/goiania", screenshot: "desktop-cidade-goiania", type: "page", h1: true, breadcrumbs: true, mustContain: "demo-goiania-livre-premium" },
  { key: "bairro-setor-bueno", path: "/acompanhantes/go/goiania/setor-bueno", screenshot: "desktop-bairro-setor-bueno", type: "page", h1: true, breadcrumbs: true, mustContain: "demo-goiania-livre-premium" },
  { key: "cidade-brasilia", path: "/acompanhantes/df/brasilia", screenshot: "desktop-cidade-brasilia", type: "page", h1: true, breadcrumbs: true, mustContain: "demo-brasilia-premium-topo" },
  { key: "anuncio-livre", path: "/anuncios/demo-goiania-livre-premium", screenshot: "desktop-anuncio-livre", type: "page", h1: true, breadcrumbs: true, mustContain: "Perfil de demonstra" },
  { key: "anuncio-bloqueado", path: "/anuncios/demo-goiania-bloqueado", screenshot: "desktop-anuncio-bloqueado", type: "page", h1: true, breadcrumbs: true, mustContain: "Demo Goi", mustNotContain: "wa.me" },
  { key: "sitemap", path: "/sitemap.xml", screenshot: "desktop-sitemap", type: "text", mustContain: "<urlset" },
  { key: "robots", path: "/robots.txt", screenshot: "desktop-robots", type: "text", mustContain: "Disallow" }
];

const viewports = [
  { key: "desktop", width: 1280, height: 900, suffix: "" },
  { key: "mobile", width: 390, height: 844, suffix: "-mobile" }
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

function pageMetricsScript() {
  return `(() => {
    const rect = (selector) => {
      const el = document.querySelector(selector);
      if (!el) return null;
      const r = el.getBoundingClientRect();
      const cs = getComputedStyle(el);
      return { width: r.width, height: r.height, x: r.x, y: r.y, position: cs.position };
    };
    const bodyText = document.body ? document.body.innerText : "";
    const technicalViolations = [];
    const addTechnicalViolation = (type) => {
      if (!technicalViolations.includes(type)) technicalViolations.push(type);
    };
    if (/(skeleton|\\blocal\\b|API local|mock|fixture|smoke test|descart[aá]vel|sint[eé]tic[oa]s?|placeholder t[eé]cnico|stack trace|Unhandled Runtime Error|JSON bruto|debug)/i.test(bodyText)) {
      addTechnicalViolation("texto_tecnico_generico");
    }
    if (/Metadados p[úu]blicos locais|Metadados publicos locais/i.test(bodyText)) {
      addTechnicalViolation("metadados_publicos_locais_visivel");
    }
    if (/\bANUNCIO\b/.test(bodyText)) {
      addTechnicalViolation("enum_anuncio_visivel");
    }
    if (/Autorizacao|autorizacao/i.test(bodyText)) {
      addTechnicalViolation("autorizacao_sem_acento_visivel");
    }
    if (/admin configurar/i.test(bodyText)) {
      addTechnicalViolation("permissao_admin_configurar_visivel");
    }
    if (/anuncio ler/i.test(bodyText)) {
      addTechnicalViolation("permissao_anuncio_ler_visivel");
    }
    if (/Preparar autorizacao/i.test(bodyText)) {
      addTechnicalViolation("descricao_autorizacao_sem_acento_visivel");
    }
    if (/\b(?:PENDENTE|FALHA|ERRO)_[A-Z0-9_]+\b/.test(bodyText)) {
      addTechnicalViolation("status_tecnico_upper_snake");
    }
    if (/\bWHATSAPP_PUBLICO\b/.test(bodyText)) {
      addTechnicalViolation("politica_whatsapp_publico");
    }
    if (/\bPOLITICA_EXPOSICAO\b/.test(bodyText)) {
      addTechnicalViolation("politica_exposicao_publica");
    }
    if (/\b(?:conteudo_autorizado|aguardando_idade)\b/.test(bodyText)) {
      addTechnicalViolation("estado_agegate_interno");
    }
    if (/\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\b/.test(bodyText)) {
      addTechnicalViolation("snake_case_visivel");
    }
    if (/\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b/.test(bodyText)) {
      addTechnicalViolation("upper_snake_case_visivel");
    }
    const links = Array.from(document.querySelectorAll("a[href]")).map((a) => a.getAttribute("href") || "");
    const positioned = Array.from(document.querySelectorAll("body *")).filter((el) => {
      const tag = el.tagName.toLowerCase();
      const className = String(el.className || "");
      const id = String(el.id || "");
      return tag !== "next-route-announcer" && tag !== "nextjs-portal" && !id.startsWith("__next") && !className.includes("nextjs");
    }).map((el) => {
      const cs = getComputedStyle(el);
      return { tag: el.tagName.toLowerCase(), className: String(el.className || ""), position: cs.position };
    }).filter((item) => /^(fixed|absolute|sticky)$/i.test(item.position)).slice(0, 20);
    return {
      title: document.title || "",
      description: document.querySelector('meta[name="description"]')?.getAttribute("content") || "",
      canonical: document.querySelector('link[rel="canonical"]')?.getAttribute("href") || "",
      h1: document.querySelector("h1")?.textContent?.trim() || "",
      bodyText,
      bodyTextStart: bodyText.slice(0, 500),
      documentWidth: document.documentElement.scrollWidth,
      viewportWidth: innerWidth,
      bodyStyleOverflow: document.body?.style?.overflow || "",
      htmlOverflowX: getComputedStyle(document.documentElement).overflowX,
      bodyOverflowX: getComputedStyle(document.body).overflowX,
      h1Rect: rect("h1"),
      shellRect: rect(".public-shell"),
      breadcrumbsRect: rect(".public-breadcrumbs"),
      cardCount: document.querySelectorAll(".public-anuncio-card").length,
      hasWhatsappUrl: /wa\\.me\\/|\\+55\\d{8,}/i.test(bodyText) || /wa\\.me\\/|\\+55\\d{8,}/i.test(document.documentElement.outerHTML),
      technicalText: technicalViolations.length > 0,
      technicalViolations,
      forbiddenRoutes: links.filter((href) => /^\\/anuncio\\//i.test(href) || /^\\/perfil\\//i.test(href) || /^\\/acompanhante\\//i.test(href) || /^\\/ads\\//i.test(href)),
      positioned
    };
  })()`;
}

function addCheck(checks, ok, label, detail) {
  checks.push({ resultado: ok ? "OK" : "FALHA", label, detail });
}

function validate(route, viewport, metrics, status, textBody) {
  const checks = [];
  addCheck(checks, status >= 200 && status < 400, "HTTP 2xx/3xx", `status=${status}`);
  const technicalDetail = Array.isArray(metrics.technicalViolations) && metrics.technicalViolations.length
    ? metrics.technicalViolations.join(", ")
    : "nenhuma categoria tecnica visivel";
  addCheck(checks, !metrics.technicalText, "sem texto tecnico/enum/status interno visivel", technicalDetail);
  addCheck(checks, !metrics.hasWhatsappUrl || route.key !== "anuncio-bloqueado", "BLOQUEADO sem WhatsApp publico", route.key === "anuncio-bloqueado" ? "sem wa.me/+55" : "nao aplicavel");
  addCheck(checks, metrics.documentWidth <= metrics.viewportWidth + 2, "sem scroll horizontal", `${metrics.documentWidth}px em ${metrics.viewportWidth}px`);
  addCheck(checks, !metrics.bodyStyleOverflow, "sem document.body.style.overflow", metrics.bodyStyleOverflow || "vazio");
  addCheck(checks, metrics.htmlOverflowX !== "hidden" && metrics.bodyOverflowX !== "hidden", "sem scroll lock global", `html=${metrics.htmlOverflowX}; body=${metrics.bodyOverflowX}`);
  addCheck(checks, metrics.positioned.length === 0, "sem elemento fixed/absolute/sticky publico", metrics.positioned.map((p) => `${p.tag}.${p.className}`).join("; ") || "nenhum");
  addCheck(checks, metrics.forbiddenRoutes.length === 0, "sem rotas publicas proibidas", metrics.forbiddenRoutes.join(", ") || "nenhuma");

  if (route.type === "page") {
    addCheck(checks, Boolean(metrics.title), "title presente", metrics.title);
    addCheck(checks, Boolean(metrics.description), "meta description presente", metrics.description);
    addCheck(checks, metrics.canonical.startsWith("http://localhost") || metrics.canonical.startsWith("http://127.0.0.1"), "canonical local seguro", metrics.canonical || "ausente");
    addCheck(checks, Boolean(metrics.h1), "H1 presente", metrics.h1 || "ausente");
    addCheck(checks, metrics.h1Rect && metrics.h1Rect.width >= (viewport.key === "desktop" ? 300 : 220), "H1 legivel", metrics.h1Rect ? `${Math.round(metrics.h1Rect.width)}x${Math.round(metrics.h1Rect.height)}` : "ausente");
    addCheck(checks, metrics.shellRect && metrics.shellRect.width >= (viewport.key === "desktop" ? 700 : 300), "shell dentro da viewport", metrics.shellRect ? `${Math.round(metrics.shellRect.width)}px` : "ausente");
    if (route.breadcrumbs) {
      addCheck(checks, metrics.breadcrumbsRect && metrics.breadcrumbsRect.width >= (viewport.key === "desktop" ? 300 : 220), "breadcrumbs legiveis", metrics.breadcrumbsRect ? `${Math.round(metrics.breadcrumbsRect.width)}px` : "ausente");
    }
    if (route.mustContain) {
      addCheck(checks, textBody.includes(route.mustContain), "conteudo sintetico esperado", route.mustContain);
    }
    if (route.mustNotContain) {
      addCheck(checks, !textBody.includes(route.mustNotContain), "conteudo proibido ausente", route.mustNotContain);
    }
  } else {
    addCheck(checks, !/^\\s*[\\{\\[]/.test(textBody), "sem JSON bruto", "resposta textual esperada");
    if (route.mustContain) {
      addCheck(checks, textBody.includes(route.mustContain), "conteudo esperado", route.mustContain);
    }
    addCheck(checks, !/topsdojob\\.com/i.test(textBody), "sem dominio de producao", "local seguro");
  }
  return checks;
}

async function main() {
  const remotePort = await freePort();
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), "topsv3-render-browser-"));
  const browser = spawn(browserPath, [
    "--headless=new",
    "--disable-gpu",
    "--no-first-run",
    "--disable-default-apps",
    `--remote-debugging-port=${remotePort}`,
    `--user-data-dir=${userDataDir}`,
    "about:blank"
  ], { stdio: "ignore" });

  let cdp;
  const results = [];
  const failures = [];

  try {
    let version = null;
    for (let i = 0; i < 80; i++) {
      try {
        version = await fetchJson(`http://127.0.0.1:${remotePort}/json/version`);
        break;
      } catch {
        await delay(100);
      }
    }
    if (!version) throw new Error("CDP do navegador nao ficou pronto.");

    let target;
    try {
      target = await fetchJson(`http://127.0.0.1:${remotePort}/json/new?about:blank`, { method: "PUT" });
    } catch {
      target = await fetchJson(`http://127.0.0.1:${remotePort}/json/new?about:blank`);
    }
    cdp = new CdpClient(target.webSocketDebuggerUrl);
    await cdp.connect();
    await cdp.send("Page.enable");
    await cdp.send("Runtime.enable");

    for (const viewport of viewports) {
      await cdp.send("Emulation.setDeviceMetricsOverride", {
        width: viewport.width,
        height: viewport.height,
        deviceScaleFactor: 1,
        mobile: viewport.key === "mobile"
      });

      for (const route of routes) {
        const url = `${frontendBaseUrl}${route.path}`;
        const http = await fetch(url);
        const textBody = await http.text();
        await cdp.send("Page.navigate", { url });
        for (let i = 0; i < 80; i++) {
          const ready = await cdp.send("Runtime.evaluate", {
            expression: "document.readyState === 'complete'",
            returnByValue: true
          });
          if (ready.result?.value === true) break;
          await delay(100);
        }
        await delay(150);

        const evaluated = await cdp.send("Runtime.evaluate", {
          expression: pageMetricsScript(),
          returnByValue: true,
          awaitPromise: true
        });
        const metrics = evaluated.result.value;
        const checks = validate(route, viewport, metrics, http.status, textBody);
        const routeFailures = checks.filter((item) => item.resultado === "FALHA");
        failures.push(...routeFailures.map((item) => `${route.key}/${viewport.key}: ${item.label} - ${item.detail}`));
        results.push({ route, viewport, metrics, status: http.status, checks });

        if (printsDir) {
          const shot = await cdp.send("Page.captureScreenshot", {
            format: "png",
            fromSurface: true,
            captureBeyondViewport: false
          });
          const suffix = viewport.key === "mobile" ? "mobile" : "desktop";
          const name = `${suffix}-${route.key}.png`;
          fs.writeFileSync(path.join(printsDir, name), Buffer.from(shot.data, "base64"));
        }
      }
    }
  } finally {
    try {
      if (cdp) {
        await cdp.send("Browser.close").catch(() => {});
        cdp.close();
      }
    } finally {
      if (!browser.killed) browser.kill();
      try { fs.rmSync(userDataDir, { recursive: true, force: true }); } catch {}
    }
  }

  const header = [
    "# Relatorio - auditoria renderizada publica sintetica",
    "",
    `- Resultado: ${failures.length ? "FALHA" : "OK"}`,
    `- Frontend: ${frontendBaseUrl}`,
    `- Backend sintetico: ${backendBaseUrl}`,
    "- Dados reais usados: nao",
    "- Producao/VPS/API externa acessadas: nao",
    "- Quarentena usada como staging final: nao",
    ""
  ];
  const auditLines = [...header, "## Rotas"];
  const seoLines = [
    "# Relatorio - SEO renderizado sintetico",
    "",
    `- Resultado: ${failures.length ? "FALHA" : "OK"}`,
    `- Frontend: ${frontendBaseUrl}`,
    "",
    "## Metadados"
  ];
  const uiLines = [
    "# Relatorio - UI mobile/desktop sintetica",
    "",
    `- Resultado: ${failures.length ? "FALHA" : "OK"}`,
    "- Prints versionados: sim",
    "",
    "## Viewports"
  ];

  for (const item of results) {
    const route = item.route;
    const viewport = item.viewport;
    const metrics = item.metrics;
    auditLines.push(`- ${route.path} (${viewport.key}): ${item.checks.every((check) => check.resultado === "OK") ? "OK" : "FALHA"}`);
    for (const check of item.checks) {
      auditLines.push(`  - ${check.resultado}: ${check.label} - ${check.detail}`);
    }
    if (route.type === "page") {
      seoLines.push(`- ${route.path} (${viewport.key}): title="${metrics.title}", description="${metrics.description}", h1="${metrics.h1}", canonical="${metrics.canonical}"`);
    } else {
      seoLines.push(`- ${route.path} (${viewport.key}): status=${item.status}, resposta textual local validada`);
    }
    uiLines.push(`- ${route.path} (${viewport.key}): larguraDocumento=${metrics.documentWidth}, viewport=${metrics.viewportWidth}, H1=${metrics.h1Rect ? `${Math.round(metrics.h1Rect.width)}x${Math.round(metrics.h1Rect.height)}` : "nao-aplicavel"}, cards=${metrics.cardCount}`);
  }

  auditLines.push("");
  auditLines.push("## Falhas");
  if (failures.length) failures.forEach((failure) => auditLines.push(`- ${failure}`));
  else auditLines.push("- Nenhuma");

  fs.writeFileSync(auditoriaPath, `${auditLines.join("\n")}\n`, "utf8");
  fs.writeFileSync(seoPath, `${seoLines.join("\n")}\n`, "utf8");
  fs.writeFileSync(uiPath, `${uiLines.join("\n")}\n`, "utf8");

  console.log("Validacao publica renderizada sintetica");
  console.log(`FRONTEND_BASE_URL=${frontendBaseUrl}`);
  console.log(`BACKEND_BASE_URL=${backendBaseUrl}`);
  console.log(`ROTAS=${routes.length}`);
  console.log(`VIEWPORTS=${viewports.length}`);
  console.log(`CHECKS=${results.reduce((sum, item) => sum + item.checks.length, 0)}`);
  console.log(`FALHAS=${failures.length}`);
  if (failures.length) {
    failures.forEach((failure) => console.log(`FALHA: ${failure}`));
    console.log("VALIDATION_RESULT=FALHA_PUBLICO_RENDERIZADO_SINTETICO_LOCAL");
    process.exitCode = 1;
  } else {
    console.log("VALIDATION_RESULT=OK_PUBLICO_RENDERIZADO_SINTETICO_LOCAL");
  }
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  console.log("VALIDATION_RESULT=PENDENTE_PUBLICO_RENDERIZADO_SINTETICO_LOCAL");
  process.exitCode = 2;
});
'@
  [System.IO.File]::WriteAllText($tempScript, $nodeScript, [System.Text.UTF8Encoding]::new($false))

  $env:TOPSV3_RENDER_FRONTEND_URL = $frontendBaseUrl
  $env:TOPSV3_RENDER_BACKEND_URL = $safeBackendUrl
  $env:TOPSV3_RENDER_BROWSER = $browserPath
  $env:TOPSV3_RENDER_PRINTS_DIR = $printsPath
  $env:TOPSV3_RENDER_RELATORIO_AUDITORIA = $auditoriaPath
  $env:TOPSV3_RENDER_RELATORIO_SEO = $seoPath
  $env:TOPSV3_RENDER_RELATORIO_UI = $uiPath
  & $node.Source $tempScript
  $nodeExit = $LASTEXITCODE
  Remove-Item -LiteralPath $tempScript -Force -ErrorAction SilentlyContinue
  exit $nodeExit
} finally {
  $env:NEXT_PUBLIC_API_BASE_URL = $oldApiBase
  $env:NEXT_PUBLIC_APP_ENV = $oldAppEnv
  $env:NEXT_PUBLIC_CANONICAL_DOMAIN = $oldCanonical
  if ($startedFrontend -and $frontendProcess -and -not $frontendProcess.HasExited) {
    Stop-Process -Id $frontendProcess.Id -Force -ErrorAction SilentlyContinue
  }
  if ($startedFrontend) {
    $portOwnersAfter = Get-PortOwners -Port $FrontendPort
    foreach ($ownerPid in $portOwnersAfter) {
      if ($portOwnersBefore -notcontains $ownerPid) {
        Stop-Process -Id $ownerPid -Force -ErrorAction SilentlyContinue
      }
    }
  }
}
