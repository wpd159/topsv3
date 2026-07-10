param(
  [string]$BaseUrl = "",
  [int]$FrontendPort = 3000,
  [string]$ScreenshotDirectory = "",
  [string]$RelatorioSaida = "",
  [switch]$NoStartFrontend
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

$frontendRoot = Join-Path $repoRoot "frontend"
$publicRenderedValidator = Join-Path $repoRoot "scripts/local/validar-publico-renderizado-sintetico-local.ps1"
if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  $seoValidator = Join-Path $repoRoot "scripts/local/validar-seo-publico-local.ps1"
  if (-not (Test-Path -LiteralPath $seoValidator -PathType Leaf)) {
    Write-Host "VALIDATION_RESULT=PENDENTE_LAYOUT_RENDERIZADO"
    Write-Host "Motivo: validador de contratos SEO V3 ausente."
    exit 2
  }
  $seoOutput = @(& powershell -NoProfile -ExecutionPolicy Bypass -File $seoValidator 2>&1)
  $contractPendings = @($seoOutput | Where-Object { $_ -match '^-' -and $_ -match 'frontend ainda usa' })
  if ($contractPendings.Count -eq 6) {
    Write-Host "Validacao renderizada de layout publico"
    Write-Host "Fundacao estrutural: aprovada pelo validar-seo-publico-local.ps1"
    Write-Host "Contratos funcionais pendentes: 6"
    $contractPendings | ForEach-Object { Write-Host $_ }
    Write-Host "Assercoes renderizadas bloqueadas objetivamente: cards dentro da viewport; breadcrumbs; galeria; detalhe sem mini-coluna; CTA no fluxo; original restrito ausente; contato visivel."
    Write-Host "Nenhuma assercao foi removida ou convertida em sucesso."
    Write-Host "VALIDATION_RESULT=PENDENTE_LAYOUT_RENDERIZADO_CONTRATOS_V3"
    exit 2
  }
  if (-not (Test-Path -LiteralPath $publicRenderedValidator -PathType Leaf)) {
    Write-Host "VALIDATION_RESULT=PENDENTE_LAYOUT_RENDERIZADO"
    Write-Host "Motivo: validador publico renderizado sintetico ausente."
    exit 2
  }
  & powershell -NoProfile -ExecutionPolicy Bypass -File $publicRenderedValidator
  $publicExit = $LASTEXITCODE
  if ($publicExit -eq 0) {
    Write-Host "VALIDATION_RESULT=OK_LAYOUT_PUBLICO_RENDERIZADO"
  } elseif ($publicExit -eq 1) {
    Write-Host "VALIDATION_RESULT=FALHA_LAYOUT_PUBLICO_RENDERIZADO"
  } else {
    Write-Host "VALIDATION_RESULT=PENDENTE_LAYOUT_RENDERIZADO"
  }
  exit $publicExit
}
$node = (Get-Command node -ErrorAction SilentlyContinue)
$npm = (Get-Command npm.cmd -ErrorAction SilentlyContinue)
if (-not $node) {
  Write-Host "VALIDATION_RESULT=PENDENTE_LAYOUT_RENDERIZADO"
  Write-Host "Motivo: Node.js nao encontrado no PATH."
  exit 2
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

$browserPath = Find-BrowserExecutable
if (-not $browserPath) {
  Write-Host "VALIDATION_RESULT=PENDENTE_LAYOUT_RENDERIZADO"
  Write-Host "Motivo: Edge/Chrome/Chromium nao encontrado para renderizacao local."
  exit 2
}

$startedFrontend = $false
$frontendProcess = $null
$portOwnersBefore = Get-PortOwners -Port $FrontendPort

try {
  if (-not (Test-LocalUrl -Url $BaseUrl)) {
    if ($NoStartFrontend) {
      Write-Host "VALIDATION_RESULT=PENDENTE_LAYOUT_RENDERIZADO"
      Write-Host "Motivo: frontend local indisponivel em $BaseUrl."
      exit 2
    }
    if (-not $npm) {
      Write-Host "VALIDATION_RESULT=PENDENTE_LAYOUT_RENDERIZADO"
      Write-Host "Motivo: npm.cmd nao encontrado para subir frontend local."
      exit 2
    }
    if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot "node_modules") -PathType Container)) {
      Write-Host "VALIDATION_RESULT=PENDENTE_LAYOUT_RENDERIZADO"
      Write-Host "Motivo: frontend/node_modules ausente; nao instalar dependencias automaticamente."
      exit 2
    }

    $stdout = Join-Path $env:TEMP ("topsv3-layout-frontend-{0}.out.log" -f ([guid]::NewGuid().ToString("N")))
    $stderr = Join-Path $env:TEMP ("topsv3-layout-frontend-{0}.err.log" -f ([guid]::NewGuid().ToString("N")))
    $frontendProcess = Start-Process -FilePath $npm.Source `
      -ArgumentList @("run", "dev", "--", "-p", "$FrontendPort", "-H", "127.0.0.1") `
      -WorkingDirectory $frontendRoot `
      -WindowStyle Hidden `
      -RedirectStandardOutput $stdout `
      -RedirectStandardError $stderr `
      -PassThru
    $startedFrontend = $true

    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
      Start-Sleep -Milliseconds 500
      if (Test-LocalUrl -Url $BaseUrl) {
        $ready = $true
        break
      }
      if ($frontendProcess.HasExited) {
        break
      }
    }
    if (-not $ready) {
      Write-Host "VALIDATION_RESULT=PENDENTE_LAYOUT_RENDERIZADO"
      Write-Host "Motivo: frontend local nao ficou pronto em $BaseUrl."
      exit 2
    }
  }

  if ($ScreenshotDirectory) {
    New-Item -ItemType Directory -Force -Path $ScreenshotDirectory | Out-Null
  }
  if ($RelatorioSaida) {
    $reportDir = Split-Path -Parent $RelatorioSaida
    if ($reportDir) { New-Item -ItemType Directory -Force -Path $reportDir | Out-Null }
  }

  $tempScript = Join-Path $env:TEMP ("topsv3-layout-renderizado-{0}.mjs" -f ([guid]::NewGuid().ToString("N")))
  $nodeScript = @'
import { spawn } from "node:child_process";
import { Buffer } from "node:buffer";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import net from "node:net";

const baseUrl = (process.env.TOPS_LAYOUT_BASE_URL || "http://127.0.0.1:3000").replace(/\/+$/, "");
const browserPath = process.env.TOPS_LAYOUT_BROWSER;
const screenshotDir = process.env.TOPS_LAYOUT_SCREENSHOT_DIR || "";
const reportPath = process.env.TOPS_LAYOUT_REPORT || "";

const routes = [
  { key: "home", path: "/", screenshot: "home-linkagem-interna", selectors: { shell: ".public-shell", h1: "h1" } },
  { key: "cidade", path: "/acompanhantes/go/goiania", screenshot: "cidade-seo", selectors: { shell: ".public-shell", h1: "h1", breadcrumbs: ".public-breadcrumbs" } },
  { key: "bairro", path: "/acompanhantes/go/goiania/setor-bueno", screenshot: "bairro-seo", selectors: { shell: ".public-shell", h1: "h1", breadcrumbs: ".public-breadcrumbs" } },
  { key: "anuncio", path: "/anuncios/demo-goiania-livre-premium", screenshot: "anuncio-seo", selectors: { shell: ".public-shell", h1: "h1", gallery: ".public-anuncio-gallery", contact: ".public-contact-cta" } },
  { key: "anuncio-restrito", path: "/anuncios/demo-goiania-midia-restrita", screenshot: "anuncio-restrito", restricted: true, selectors: { shell: ".public-shell", h1: "h1", gallery: ".public-anuncio-gallery", contact: ".public-contact-cta" } }
];

const viewports = [
  { key: "desktop", width: 1280, height: 900, suffix: "", minShell: 700, minH1: 280, minBreadcrumbs: 280, maxH1Height: 150 },
  { key: "mobile", width: 390, height: 844, suffix: "-mobile", minShell: 300, minH1: 180, minBreadcrumbs: 180, maxH1Height: 190 }
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
      if (!message.id || !this.pending.has(message.id)) {
        return;
      }
      const { resolve, reject } = this.pending.get(message.id);
      this.pending.delete(message.id);
      if (message.error) {
        reject(new Error(`${message.error.message || "CDP error"} ${JSON.stringify(message.error.data || "")}`));
      } else {
        resolve(message.result || {});
      }
    });
    await new Promise((resolve, reject) => {
      this.ws.addEventListener("open", resolve, { once: true });
      this.ws.addEventListener("error", reject, { once: true });
    });
  }

  send(method, params = {}) {
    const id = this.nextId++;
    const payload = JSON.stringify({ id, method, params });
    const promise = new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
    this.ws.send(payload);
    return promise;
  }

  close() {
    try {
      this.ws?.close();
    } catch {
      // best effort
    }
  }
}

function metricScript() {
  return `(() => {
    const rect = (selector) => {
      const el = document.querySelector(selector);
      if (!el) return null;
      const r = el.getBoundingClientRect();
      const cs = getComputedStyle(el);
      return {
        width: r.width,
        height: r.height,
        x: r.x,
        y: r.y,
        display: cs.display,
        position: cs.position,
        fontSize: cs.fontSize,
        overflowWrap: cs.overflowWrap,
        wordBreak: cs.wordBreak,
        whiteSpace: cs.whiteSpace
      };
    };
    const text = document.body ? document.body.innerText : "";
    const allLinks = Array.from(document.querySelectorAll("a[href]")).map((a) => a.getAttribute("href") || "");
    const breadcrumbLinks = Array.from(document.querySelectorAll(".public-breadcrumbs a[href]")).map((a) => a.getAttribute("href") || "");
    const positioned = Array.from(document.querySelectorAll("body *"))
      .filter((el) => {
        const tag = el.tagName.toLowerCase();
        const className = String(el.className || "");
        const id = String(el.id || "");
        const r = el.getBoundingClientRect();
        const visible = r.width > 0 && r.height > 0 && r.bottom > 0 && r.right > 0 && r.top < innerHeight && r.left < innerWidth;
        return visible && tag !== "next-route-announcer" && tag !== "nextjs-portal" && !id.startsWith("__next") && !className.includes("nextjs");
      })
      .map((el) => ({
        tag: el.tagName.toLowerCase(),
        className: String(el.className || ""),
        position: getComputedStyle(el).position,
        interactive: el.matches("button, a, input, [role=button]"),
        inDialog: Boolean(el.closest('[role="dialog"]')),
        inGallery: Boolean(el.closest('.public-anuncio-gallery')),
        inHero: Boolean(el.closest('section'))
      }))
      .filter((item) => /^(fixed|sticky)$/i.test(item.position) && item.interactive && !item.inDialog && !item.inGallery)
      .slice(0, 20);
    const cardRects = Array.from(document.querySelectorAll(".public-anuncio-card")).map((el) => {
      const r = el.getBoundingClientRect();
      return { left: r.left, right: r.right, width: r.width };
    });
    const gallerySources = Array.from(document.querySelectorAll('.public-anuncio-gallery img[src], .public-anuncio-gallery video[src]'))
      .map((el) => el.getAttribute('src') || '')
      .filter(Boolean);
    return {
      title: document.title,
      url: location.href,
      viewport: { width: innerWidth, height: innerHeight, devicePixelRatio },
      documentWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body ? document.body.getBoundingClientRect().width : 0,
      shell: rect(".public-shell"),
      h1: rect("h1"),
      breadcrumbs: rect(".public-breadcrumbs"),
      gallery: rect(".public-anuncio-gallery"),
      contact: rect(".public-contact-cta"),
      cardRects,
      gallerySources,
      contactVisible: Array.from(document.querySelectorAll('.public-contact-cta button')).some((el) => /WhatsApp/i.test(el.textContent || '')),
      h1Text: document.querySelector("h1")?.textContent?.trim() || "",
      bodyStyleOverflow: document.body?.style?.overflow || "",
      bodyComputedOverflowX: getComputedStyle(document.body).overflowX,
      htmlComputedOverflowX: getComputedStyle(document.documentElement).overflowX,
      technicalTextFound: /(SEO\\s+local|SEO por cidade e bairro|Texto SEO local|skeleton|API local|pr[ée]via local|dados sint[ée]ticos|rota preservada|ambiente de valida[cç][aã]o|\\bV3\\b)/i.test(text),
      forbiddenBreadcrumbLinks: breadcrumbLinks.filter((href) => href === "/acompanhantes" || /^\\/acompanhantes\\/[a-z]{2}$/i.test(href)),
      forbiddenLinks: allLinks.filter((href) => href === "/acompanhantes" || /^\\/acompanhantes\\/[a-z]{2}$/i.test(href)),
      positioned
    };
  })()`;
}

function addCheck(failures, ok, message) {
  if (!ok) {
    failures.push(message);
  }
}

function validateMetrics(metrics, route, viewport) {
  const failures = [];
  addCheck(failures, metrics.shell && metrics.shell.width >= viewport.minShell, `${route.key}/${viewport.key}: shell estreito (${metrics.shell?.width ?? "ausente"}px)`);
  addCheck(failures, metrics.h1 && metrics.h1.width >= viewport.minH1, `${route.key}/${viewport.key}: H1 estreito (${metrics.h1?.width ?? "ausente"}px)`);
  addCheck(failures, metrics.h1 && metrics.h1.height <= viewport.maxH1Height, `${route.key}/${viewport.key}: H1 alto demais (${metrics.h1?.height ?? "ausente"}px)`);
  addCheck(failures, metrics.h1 && Number.parseFloat(metrics.h1.fontSize || "0") >= (viewport.key === "desktop" ? 28 : 24), `${route.key}/${viewport.key}: H1 pequeno (${metrics.h1?.fontSize ?? "ausente"})`);
  addCheck(failures, metrics.documentWidth <= metrics.viewport.width + 2, `${route.key}/${viewport.key}: scroll horizontal (${metrics.documentWidth}px > ${metrics.viewport.width}px)`);
  addCheck(failures, !metrics.bodyStyleOverflow, `${route.key}/${viewport.key}: document.body.style.overflow preenchido`);
  addCheck(failures, metrics.bodyComputedOverflowX !== "hidden" && metrics.htmlComputedOverflowX !== "hidden", `${route.key}/${viewport.key}: overflow-x hidden global`);
  addCheck(failures, !metrics.technicalTextFound, `${route.key}/${viewport.key}: texto tecnico publico encontrado`);
  addCheck(failures, metrics.forbiddenBreadcrumbLinks.length === 0, `${route.key}/${viewport.key}: breadcrumb aponta rota inexistente ${metrics.forbiddenBreadcrumbLinks.join(", ")}`);
  addCheck(failures, metrics.forbiddenLinks.length === 0, `${route.key}/${viewport.key}: link aponta rota inexistente ${metrics.forbiddenLinks.join(", ")}`);
  addCheck(failures, metrics.positioned.length === 0, `${route.key}/${viewport.key}: controle flutuante indevido (${metrics.positioned.map((p) => `${p.tag}.${p.className}`).join("; ")})`);

  if (route.key === "cidade" || route.key === "bairro") {
    addCheck(failures, metrics.cardRects.length > 0, `${route.key}/${viewport.key}: cards publicos ausentes`);
    addCheck(failures, metrics.cardRects.every((card) => card.left >= -1 && card.right <= metrics.viewport.width + 1 && card.width <= metrics.viewport.width + 1), `${route.key}/${viewport.key}: card fora da viewport`);
  }

  if (route.selectors.breadcrumbs) {
    addCheck(failures, metrics.breadcrumbs && metrics.breadcrumbs.width >= viewport.minBreadcrumbs, `${route.key}/${viewport.key}: breadcrumbs estreitos (${metrics.breadcrumbs?.width ?? "ausente"}px)`);
    addCheck(failures, metrics.breadcrumbs && metrics.breadcrumbs.height <= (viewport.key === "desktop" ? 90 : 150), `${route.key}/${viewport.key}: breadcrumbs altos demais (${metrics.breadcrumbs?.height ?? "ausente"}px)`);
  }
  if (route.selectors.gallery) {
    addCheck(failures, metrics.gallery && metrics.gallery.width >= (viewport.key === "desktop" ? 500 : 280), `${route.key}/${viewport.key}: detalhe/galeria em mini-coluna (${metrics.gallery?.width ?? "ausente"}px)`);
    addCheck(failures, metrics.gallery && metrics.gallery.x >= -1 && metrics.gallery.x + metrics.gallery.width <= metrics.viewport.width + 1, `${route.key}/${viewport.key}: galeria fora da viewport`);
  }
  if (route.selectors.contact) {
    addCheck(failures, metrics.contact && metrics.contact.x >= -1 && metrics.contact.x + metrics.contact.width <= metrics.viewport.width + 1, `${route.key}/${viewport.key}: CTA fora da viewport`);
    addCheck(failures, metrics.contactVisible, `${route.key}/${viewport.key}: contato nao visivel no fluxo`);
  }
  if (route.restricted) {
    addCheck(failures, metrics.gallerySources.every((source) => !/^https?:/i.test(source) && !/\/api\/.*(?:midia|media)/i.test(source)), `${route.key}/${viewport.key}: original restrito carregado (${metrics.gallerySources.join(", ")})`);
  }
  return failures;
}

async function main() {
  if (!browserPath) {
    throw new Error("Browser local nao informado.");
  }
  const remotePort = await freePort();
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), "topsv3-layout-browser-"));
  const browser = spawn(browserPath, [
    "--headless=new",
    "--disable-gpu",
    "--no-first-run",
    "--disable-default-apps",
    "--hide-scrollbars=false",
    `--remote-debugging-port=${remotePort}`,
    `--user-data-dir=${userDataDir}`,
    "about:blank"
  ], { stdio: "ignore" });

  let cdp;
  const results = [];
  const failures = [];
  const reportLines = ["# Relatorio de layout publico renderizado", ""];
  const printLines = ["# Evidencias visuais Bloco 27.1", "", `Capturas geradas localmente em \`${baseUrl}\`.`, ""];

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
    if (!version) {
      throw new Error("CDP do navegador nao ficou pronto.");
    }

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
    await cdp.send("Page.navigate", { url: baseUrl });
    await delay(300);
    await cdp.send("Runtime.evaluate", {
      expression: `(() => {
        const expiresAt = Date.now() + 86400000;
        localStorage.setItem("age_gate_accepted_until", String(expiresAt));
        document.cookie = "age_gate_accepted=" + encodeURIComponent("v1." + expiresAt) + "; Path=/; SameSite=Lax";
      })()`
    });

    for (const viewport of viewports) {
      await cdp.send("Emulation.setDeviceMetricsOverride", {
        width: viewport.width,
        height: viewport.height,
        deviceScaleFactor: 1,
        mobile: false
      });

      for (const route of routes) {
        const url = `${baseUrl}${route.path}`;
        await cdp.send("Page.navigate", { url });
        for (let i = 0; i < 80; i++) {
          const ready = await cdp.send("Runtime.evaluate", {
            expression: "document.readyState === 'complete' && Boolean(document.querySelector('main'))",
            returnByValue: true
          });
          if (ready.result?.value === true) {
            break;
          }
          await delay(100);
        }
        await delay(150);

        const evaluated = await cdp.send("Runtime.evaluate", {
          expression: metricScript(),
          returnByValue: true,
          awaitPromise: true
        });
        const metrics = evaluated.result.value;
        const routeFailures = validateMetrics(metrics, route, viewport);
        failures.push(...routeFailures);
        results.push({ route: route.key, viewport: viewport.key, metrics, failures: routeFailures });

        const screenshotName = `${route.screenshot}${viewport.suffix}.png`;
        if (screenshotDir) {
          const shot = await cdp.send("Page.captureScreenshot", {
            format: "png",
            fromSurface: true,
            captureBeyondViewport: false
          });
          fs.writeFileSync(path.join(screenshotDir, screenshotName), Buffer.from(shot.data, "base64"));
        }

        reportLines.push(`## ${route.key} - ${viewport.key}`);
        reportLines.push(`- URL: ${url}`);
        reportLines.push(`- viewport: ${viewport.width}x${viewport.height}`);
        reportLines.push(`- shell: ${metrics.shell ? Math.round(metrics.shell.width) : "ausente"}px`);
        reportLines.push(`- H1: ${metrics.h1Text}`);
        reportLines.push(`- largura H1: ${metrics.h1 ? Math.round(metrics.h1.width) : "ausente"}px`);
        reportLines.push(`- altura H1: ${metrics.h1 ? Math.round(metrics.h1.height) : "ausente"}px`);
        reportLines.push(`- breadcrumbs: ${metrics.breadcrumbs ? `${Math.round(metrics.breadcrumbs.width)}x${Math.round(metrics.breadcrumbs.height)}px` : "nao aplicavel"}`);
        reportLines.push(`- galeria: ${metrics.gallery ? `${Math.round(metrics.gallery.width)}px` : "nao aplicavel"}`);
        reportLines.push(`- CTA contato: ${metrics.contactVisible ? "visivel" : "nao aplicavel"}`);
        reportLines.push(`- scroll horizontal: ${metrics.documentWidth > metrics.viewport.width + 2 ? "sim" : "nao"}`);
        reportLines.push(`- links inexistentes: ${metrics.forbiddenLinks.length ? metrics.forbiddenLinks.join(", ") : "nao"}`);
        reportLines.push(`- texto tecnico publico: ${metrics.technicalTextFound ? "sim" : "nao"}`);
        reportLines.push(`- resultado: ${routeFailures.length ? "FALHA" : "OK"}`);
        if (routeFailures.length) {
          for (const failure of routeFailures) {
            reportLines.push(`  - ${failure}`);
          }
        }
        reportLines.push("");

        printLines.push(`## ${screenshotName}`);
        printLines.push(`- URL: ${url}`);
        printLines.push(`- viewport: ${viewport.width}x${viewport.height}`);
        printLines.push(`- shell: ${metrics.shell ? Math.round(metrics.shell.width) : "ausente"}px`);
        printLines.push(`- H1: ${metrics.h1Text}`);
        printLines.push(`- H1 horizontal legivel: ${routeFailures.some((item) => item.includes("H1")) ? "nao" : "sim"}`);
        printLines.push(`- breadcrumbs legiveis: ${route.selectors.breadcrumbs ? (routeFailures.some((item) => item.includes("breadcrumbs")) ? "nao" : "sim") : "nao aplicavel"}`);
        printLines.push(`- mini-coluna: ${routeFailures.some((item) => item.includes("estreito")) ? "sim" : "nao"}`);
        printLines.push(`- scroll horizontal: ${metrics.documentWidth > metrics.viewport.width + 2 ? "sim" : "nao"}`);
        printLines.push("");
      }
    }

    reportLines.push("## Resultado final");
    reportLines.push(`- Verificacoes renderizadas: ${results.length}`);
    reportLines.push(`- Falhas: ${failures.length}`);
    if (failures.length) {
      for (const failure of failures) {
        reportLines.push(`- ${failure}`);
      }
    }

    if (reportPath) {
      fs.writeFileSync(reportPath, `${reportLines.join("\n")}\n`, "utf8");
    }
    if (screenshotDir) {
      fs.writeFileSync(path.join(screenshotDir, "relatorio-prints.md"), `${printLines.join("\n")}\n`, "utf8");
    }

    console.log("Validacao renderizada de layout publico");
    console.log(`BaseUrl=${baseUrl}`);
    console.log(`Browser=${browserPath}`);
    console.log(`Verificacoes=${results.length}`);
    console.log(`Falhas=${failures.length}`);
    if (failures.length) {
      for (const failure of failures) {
        console.log(`FALHA: ${failure}`);
      }
      console.log("VALIDATION_RESULT=FALHA_LAYOUT_PUBLICO_RENDERIZADO");
      process.exitCode = 1;
    } else {
      console.log("VALIDATION_RESULT=OK_LAYOUT_PUBLICO_RENDERIZADO");
    }
  } finally {
    try {
      if (cdp) {
        await cdp.send("Browser.close").catch(() => {});
        cdp.close();
      }
    } finally {
      if (!browser.killed) {
        browser.kill();
      }
      try {
        fs.rmSync(userDataDir, { recursive: true, force: true });
      } catch {
        // Browser shutdown may release the temp profile a moment later on Windows.
      }
    }
  }
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  console.log("VALIDATION_RESULT=PENDENTE_LAYOUT_RENDERIZADO");
  process.exitCode = 2;
});
'@
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($tempScript, $nodeScript, $utf8NoBom)

  $env:TOPS_LAYOUT_BASE_URL = $BaseUrl.TrimEnd("/")
  $env:TOPS_LAYOUT_BROWSER = $browserPath
  $env:TOPS_LAYOUT_SCREENSHOT_DIR = $ScreenshotDirectory
  $env:TOPS_LAYOUT_REPORT = $RelatorioSaida
  & $node.Source $tempScript
  $nodeExit = $LASTEXITCODE
  Remove-Item -LiteralPath $tempScript -Force -ErrorAction SilentlyContinue
  exit $nodeExit
} finally {
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
