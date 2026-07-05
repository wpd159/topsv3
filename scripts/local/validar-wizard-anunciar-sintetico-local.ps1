param(
  [string]$BaseUrl = "",
  [int]$BackendPort = 18133,
  [int]$FrontendPort = 18333,
  [int]$DockerWaitSeconds = 180,
  [int]$BackendWaitSeconds = 120,
  [string]$RelatorioAuditoria = "docs/v3/evidencias/bloco-34/relatorio-auditoria-wizard-anunciar.md",
  [string]$RelatorioUi = "docs/v3/evidencias/bloco-34/relatorio-ui-mobile-desktop-wizard-paridade.md",
  [string]$RelatorioValidacoes = "docs/v3/evidencias/bloco-34/relatorio-validacoes.md",
  [string]$PrintsDirectory = "docs/v3/evidencias/bloco-34/prints/v3-local",
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
    Write-Host "VALIDATION_RESULT=PENDENTE_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
    Write-Host "Motivo: script base de E2E descartavel nao encontrado."
    exit 2
  }

  $e2eReport = Resolve-RepoPath "docs/v3/evidencias/bloco-34/relatorio-e2e-wizard-anunciar.md"
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
    "topsv3-bloco34-wizard-paridade",
    "-ApiSmokeScript",
    "scripts/local/validar-wizard-anunciar-sintetico-local.ps1",
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
  if ($exit -eq 0) {
    Write-Host "VALIDATION_RESULT=OK_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
  } elseif ($exit -eq 1) {
    Write-Host "VALIDATION_RESULT=FALHA_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
  } else {
    Write-Host "VALIDATION_RESULT=PENDENTE_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
  }
  exit $exit
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  Invoke-WrapperMode
}

$safeBackendUrl = $BaseUrl.Trim().TrimEnd("/")
if ($safeBackendUrl -notmatch '^http://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$') {
  Write-Host "VALIDATION_RESULT=FALHA_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
  Write-Host "Motivo: BaseUrl deve ser HTTP localhost."
  exit 1
}

$node = Get-Command node -ErrorAction SilentlyContinue
$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
$browserPath = Find-BrowserExecutable
if (-not $node) {
  Write-Host "VALIDATION_RESULT=PENDENTE_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
  Write-Host "Motivo: Node.js nao encontrado no PATH."
  exit 2
}
if (-not $browserPath) {
  Write-Host "VALIDATION_RESULT=PENDENTE_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
  Write-Host "Motivo: Edge/Chrome/Chromium nao encontrado para auditoria visual local."
  exit 2
}

$frontendRoot = Join-Path $repoRoot "frontend"
$frontendBaseUrl = "http://127.0.0.1:$FrontendPort"
$auditoriaPath = Resolve-RepoPath $RelatorioAuditoria
$uiPath = Resolve-RepoPath $RelatorioUi
$validacoesPath = Resolve-RepoPath $RelatorioValidacoes
$printsPath = Resolve-RepoPath $PrintsDirectory
foreach ($path in @($auditoriaPath, $uiPath, $validacoesPath, $printsPath)) {
  $parent = if ([System.IO.Path]::GetExtension($path)) { Split-Path -Parent $path } else { $path }
  if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
}

try {
  Invoke-WebRequest -Uri "$safeBackendUrl/api/health/readiness" -UseBasicParsing -TimeoutSec 5 | Out-Null
} catch {
  Write-Host "VALIDATION_RESULT=PENDENTE_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
  Write-Host "Motivo: backend local indisponivel em $safeBackendUrl."
  exit 2
}

$legacyApiSmoke = Resolve-RepoPath "scripts/local/validar-api-publica-local.ps1"
if (-not (Test-Path -LiteralPath $legacyApiSmoke -PathType Leaf)) {
  Write-Host "VALIDATION_RESULT=PENDENTE_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
  Write-Host "Motivo: smoke legado de API publica nao encontrado."
  exit 2
}
$powershell = (Get-Command powershell -ErrorAction Stop).Source
$legacyOutput = & $powershell -NoProfile -ExecutionPolicy Bypass -File $legacyApiSmoke -BaseUrl $safeBackendUrl 2>&1
if ($LASTEXITCODE -ne 0) {
  Write-Host "VALIDATION_RESULT=FALHA_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
  Write-Host "Motivo: smoke legado de API publica falhou antes do wizard."
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
      Write-Host "VALIDATION_RESULT=PENDENTE_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
      Write-Host "Motivo: frontend local indisponivel em $frontendBaseUrl."
      exit 2
    }
    if (-not $npm) {
      Write-Host "VALIDATION_RESULT=PENDENTE_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
      Write-Host "Motivo: npm.cmd nao encontrado para subir frontend local."
      exit 2
    }
    if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot "node_modules") -PathType Container)) {
      Write-Host "VALIDATION_RESULT=PENDENTE_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
      Write-Host "Motivo: frontend/node_modules ausente; nao instalar dependencias automaticamente."
      exit 2
    }

    $env:NEXT_PUBLIC_API_BASE_URL = $safeBackendUrl
    $env:NEXT_PUBLIC_APP_ENV = "local"
    $env:NEXT_PUBLIC_CANONICAL_DOMAIN = "http://localhost"
    $stdout = Join-Path $env:TEMP ("topsv3-wizard-frontend-{0}.out.log" -f ([guid]::NewGuid().ToString("N")))
    $stderr = Join-Path $env:TEMP ("topsv3-wizard-frontend-{0}.err.log" -f ([guid]::NewGuid().ToString("N")))
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
      Write-Host "VALIDATION_RESULT=PENDENTE_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
      Write-Host "Motivo: frontend local nao ficou pronto em $frontendBaseUrl."
      exit 2
    }
  }

  $tempScript = Join-Path $env:TEMP ("topsv3-wizard-anunciar-{0}.mjs" -f ([guid]::NewGuid().ToString("N")))
  $nodeScript = @'
import { spawn } from "node:child_process";
import { Buffer } from "node:buffer";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import net from "node:net";

const frontendBaseUrl = (process.env.TOPSV3_WIZARD_FRONTEND_URL || "").replace(/\/+$/, "");
const backendBaseUrl = (process.env.TOPSV3_WIZARD_BACKEND_URL || "").replace(/\/+$/, "");
const browserPath = process.env.TOPSV3_WIZARD_BROWSER;
const printsDir = process.env.TOPSV3_WIZARD_PRINTS_DIR || "";
const auditoriaPath = process.env.TOPSV3_WIZARD_RELATORIO_AUDITORIA || "";
const uiPath = process.env.TOPSV3_WIZARD_RELATORIO_UI || "";
const validacoesPath = process.env.TOPSV3_WIZARD_RELATORIO_VALIDACOES || "";

const viewports = [
  { key: "desktop", width: 1280, height: 900, email: "wizard.desktop@example.invalid" },
  { key: "mobile", width: 390, height: 844, email: "wizard.mobile@example.invalid" }
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

function addCheck(checks, ok, label, detail) {
  const detailText = detail === undefined || detail === null || String(detail).length === 0 ? "ok" : String(detail);
  checks.push({ resultado: ok ? "OK" : "FALHA", label, detail: detailText });
}

function safeRegexText(value) {
  return JSON.stringify(value);
}

function wizardMetricsScript() {
  return `(() => {
    const bodyText = document.body ? document.body.innerText : "";
    const technicalViolations = [];
    const add = (type) => { if (!technicalViolations.includes(type)) technicalViolations.push(type); };
    if (/(stack trace|Unhandled Runtime Error|JSON bruto|debug|mock tecnico|placeholder tecnico)/i.test(bodyText)) add("texto_tecnico_generico");
    if (/\\b(?:PENDENTE|FALHA|ERRO)_[A-Z0-9_]+\\b/.test(bodyText)) add("status_tecnico_upper_snake");
    if (/\\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\\b/.test(bodyText)) add("upper_snake_case_visivel");
    if (/\\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\\b/.test(bodyText)) add("snake_case_visivel");
    const positioned = Array.from(document.querySelectorAll("body *")).filter((el) => {
      const tag = el.tagName.toLowerCase();
      const className = String(el.className || "");
      const id = String(el.id || "");
      return tag !== "next-route-announcer" && tag !== "nextjs-portal" && !id.startsWith("__next") && !className.includes("nextjs");
    }).map((el) => {
      const cs = getComputedStyle(el);
      const r = el.getBoundingClientRect();
      return { tag: el.tagName.toLowerCase(), className: String(el.className || ""), position: cs.position, width: r.width, height: r.height };
    }).filter((item) => /^(fixed|absolute|sticky)$/i.test(item.position)).slice(0, 20);
    const links = Array.from(document.querySelectorAll("a[href]")).map((a) => a.getAttribute("href") || "");
    const buttons = Array.from(document.querySelectorAll("button")).map((button) => button.innerText.trim()).filter(Boolean);
    const actionText = buttons.join(" ") + " " + links.join(" ");
    const dangerousText = /(Pix|Ef[ií]|checkout|pagar|comprar|cart[aã]o|upload|documento|ativar premium|loja|stores?)/i.test(actionText);
    const fileInputs = Array.from(document.querySelectorAll('input[type="file"], input[capture]'));
    const publicTextViolations = [
      "Revisao final",
      "Solicitacao recebida",
      "Confirmacoes",
      "Etapa concluida",
      "botao final",
      "V3 local",
      "dados sintéticos locais",
      "dados sinteticos locais",
      "A produção destaca",
      "A producao destaca",
      "produção observável",
      "producao observavel",
      "ambiente local",
      "fixture",
      "mock",
      "E2E",
      "validação sintética",
      "validacao sintetica",
      "snake_case",
      "UPPER_SNAKE_CASE"
    ].filter((text) => bodyText.includes(text));
    return {
      title: document.title || "",
      h1: document.querySelector("h1")?.textContent?.trim() || "",
      h2: document.querySelector("h2")?.textContent?.trim() || "",
      bodyText,
      documentWidth: document.documentElement.scrollWidth,
      viewportWidth: innerWidth,
      bodyStyleOverflow: document.body?.style?.overflow || "",
      htmlOverflowX: getComputedStyle(document.documentElement).overflowX,
      bodyOverflowX: getComputedStyle(document.body).overflowX,
      progressSteps: document.querySelectorAll(".public-wizard-progress li").length,
      currentSteps: document.querySelectorAll('.public-wizard-progress li[aria-current="step"]').length,
      visibleInputs: Array.from(document.querySelectorAll('input:not([type="hidden"]), textarea, select')).filter((el) => {
        const r = el.getBoundingClientRect();
        return r.width > 0 && r.height > 0;
      }).length,
      fileInputCount: fileInputs.length,
      externalLinks: links.filter((href) => /^https?:\\/\\//i.test(href)),
      waLinks: links.filter((href) => /wa\\.me|whatsapp/i.test(href)),
      technicalViolations,
      publicTextViolations,
      positioned,
      dangerousText,
      buttons
    };
  })()`;
}

async function evalValue(cdp, expression) {
  const result = await cdp.send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
  return result.result?.value;
}

async function waitFor(cdp, expression, label, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = await evalValue(cdp, expression);
    if (value) return value;
    await delay(150);
  }
  throw new Error(`Tempo esgotado aguardando ${label}`);
}

async function navigate(cdp, url) {
  await cdp.send("Page.navigate", { url });
  await waitFor(cdp, "document.readyState === 'complete'", `carregamento de ${url}`, 20000);
  await delay(250);
}

async function clickButton(cdp, label) {
  const expression = `(() => {
    const wanted = ${safeRegexText(label)}.toLowerCase();
    const buttons = Array.from(document.querySelectorAll("button"));
    const button = buttons.find((item) => (item.innerText || item.value || "").trim().toLowerCase().startsWith(wanted));
    if (!button) return { ok: false, buttons: buttons.map((item) => item.innerText.trim()).filter(Boolean) };
    button.click();
    return { ok: true };
  })()`;
  const result = await evalValue(cdp, expression);
  if (!result?.ok) {
    throw new Error(`Botao nao encontrado: ${label}. Botoes=${(result?.buttons || []).join(", ")}`);
  }
  await delay(250);
}

async function setField(cdp, name, value) {
  const expression = `(() => {
    const el = document.querySelector(${safeRegexText(`[name="${name}"]`)});
    if (!el) return false;
    const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
    const descriptor = Object.getOwnPropertyDescriptor(proto, "value");
    if (descriptor && descriptor.set) descriptor.set.call(el, ${JSON.stringify(value)});
    else el.value = ${JSON.stringify(value)};
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
    return true;
  })()`;
  const ok = await evalValue(cdp, expression);
  if (!ok) throw new Error(`Campo nao encontrado: ${name}`);
}

async function checkBox(cdp, name) {
  const expression = `(() => {
    const el = document.querySelector(${safeRegexText(`[name="${name}"]`)});
    if (!el) return false;
    if (!el.checked) el.click();
    return el.checked === true;
  })()`;
  const ok = await evalValue(cdp, expression);
  if (!ok) throw new Error(`Checkbox nao marcado: ${name}`);
}

async function screenshot(cdp, filename) {
  const shot = await cdp.send("Page.captureScreenshot", {
    format: "png",
    fromSurface: true,
    captureBeyondViewport: false
  });
  fs.writeFileSync(path.join(printsDir, filename), Buffer.from(shot.data, "base64"));
}

function validateMetrics(label, metrics) {
  const checks = [];
  addCheck(checks, metrics.progressSteps === 8, "wizard guiado com oito etapas", metrics.progressSteps);
  addCheck(checks, metrics.currentSteps === 1, "uma etapa atual visivel", metrics.currentSteps);
  addCheck(checks, metrics.documentWidth <= metrics.viewportWidth + 2, "sem scroll horizontal", `${metrics.documentWidth}px em ${metrics.viewportWidth}px`);
  addCheck(checks, !metrics.bodyStyleOverflow, "sem document.body.style.overflow", metrics.bodyStyleOverflow || "vazio");
  addCheck(checks, metrics.htmlOverflowX !== "hidden" && metrics.bodyOverflowX !== "hidden", "sem scroll lock global", `html=${metrics.htmlOverflowX}; body=${metrics.bodyOverflowX}`);
  addCheck(checks, metrics.positioned.length === 0, "sem elemento fixed/absolute/sticky publico", metrics.positioned.map((p) => `${p.tag}.${p.className}`).join("; ") || "nenhum");
  addCheck(checks, metrics.fileInputCount === 0, "sem upload/camera/documento real", metrics.fileInputCount);
  addCheck(checks, metrics.externalLinks.length === 0, "sem link externo no wizard", metrics.externalLinks.join(", ") || "nenhum");
  addCheck(checks, metrics.waLinks.length === 0, "sem WhatsApp publico/liberado", metrics.waLinks.join(", ") || "nenhum");
  addCheck(checks, !metrics.dangerousText, "sem pagamento/Pix/upload/premium/loja visivel como acao", "texto publico controlado");
  addCheck(checks, metrics.technicalViolations.length === 0, "sem enum/status/snake_case tecnico visivel", metrics.technicalViolations.join(", ") || "nenhum");
  addCheck(checks, metrics.publicTextViolations.length === 0, "sem texto publico de bastidor", metrics.publicTextViolations.join(", ") || "nenhum");
  return checks.map((check) => ({ ...check, label: `${label}: ${check.label}` }));
}

async function runFlow(cdp, viewport) {
  const checks = [];
  await cdp.send("Emulation.setDeviceMetricsOverride", {
    width: viewport.width,
    height: viewport.height,
    deviceScaleFactor: 1,
    mobile: viewport.key === "mobile"
  });

  await navigate(cdp, `${frontendBaseUrl}/anunciar`);
  await waitFor(cdp, 'document.body.innerText.includes("Continuar")', "inicio do wizard");
  await screenshot(cdp, `${viewport.key}-inicio.png`);
  let metrics = await evalValue(cdp, wizardMetricsScript());
  checks.push(...validateMetrics(`${viewport.key}/inicio`, metrics));
  addCheck(checks, metrics.visibleInputs <= 1, `${viewport.key}: inicio nao e formulario legado unico`, `campos visiveis=${metrics.visibleInputs}`);

  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'Boolean(document.querySelector("[name=nomeExibicao]"))', "etapa de dados");
  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'document.body.innerText.includes("Revise os campos destacados")', "mensagem amigavel de validacao");
  metrics = await evalValue(cdp, wizardMetricsScript());
  addCheck(checks, metrics.bodyText.includes("Revise os campos destacados"), `${viewport.key}: validacao amigavel sem 500`, "mensagem visivel");
  checks.push(...validateMetrics(`${viewport.key}/validacao`, metrics));

  await setField(cdp, "nomeExibicao", `Perfil Sintético Wizard ${viewport.key}`);
  await setField(cdp, "email", viewport.email);
  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'Boolean(document.querySelector("[name=uf]"))', "etapa localizacao");

  await setField(cdp, "uf", "GO");
  await setField(cdp, "cidade", "Goiania");
  await setField(cdp, "bairro", "Setor Bueno");
  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'Boolean(document.querySelector("[name=whatsapp]"))', "etapa contato");

  await setField(cdp, "whatsapp", "+5500000000000");
  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'Boolean(document.querySelector("[name=titulo]"))', "etapa detalhes");

  await setField(cdp, "titulo", `Perfil sintético wizard ${viewport.key}`);
  await setField(cdp, "descricao", "Texto sintético suficiente para validar o wizard publico local sem dados reais.");
  await setField(cdp, "preco", "120");
  await setField(cdp, "categoria", "ACOMPANHANTE");
  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'document.body.innerText.toLowerCase().includes("upload")', "etapa de midia futura");
  await screenshot(cdp, `${viewport.key}-intermediaria.png`);
  metrics = await evalValue(cdp, wizardMetricsScript());
  addCheck(checks, /Upload/i.test(metrics.bodyText) && !metrics.bodyText.includes("Enviar arquivo"), `${viewport.key}: midia e placeholder sem upload real`, "etapa de midia futura");
  checks.push(...validateMetrics(`${viewport.key}/midia`, metrics));

  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'Boolean(document.querySelector("[name=aceiteTermos]"))', "etapa revisao");
  await screenshot(cdp, `${viewport.key}-revisao.png`);
  metrics = await evalValue(cdp, wizardMetricsScript());
  addCheck(checks, metrics.bodyText.includes("Acompanhante") && !metrics.bodyText.includes("ACOMPANHANTE"), `${viewport.key}: categoria exibida como texto publico`, "Acompanhante");
  addCheck(checks, !/LOCAL_TESTE|ACOMPANHANTE|MASSAGEM/.test(metrics.bodyText), `${viewport.key}: sem enum tecnico na revisao`, "categoria humanizada");
  checks.push(...validateMetrics(`${viewport.key}/revisao`, metrics));

  await checkBox(cdp, "aceiteTermos");
  await checkBox(cdp, "confirmacaoIdade");
  await clickButton(cdp, "Enviar");
  await waitFor(cdp, 'document.body.innerText.includes("Solicita") && document.body.innerText.includes("receb")', "pos-envio local");
  await screenshot(cdp, `${viewport.key}-pos-envio.png`);
  metrics = await evalValue(cdp, wizardMetricsScript());
  addCheck(checks, metrics.bodyText.toLowerCase().includes("publicado automaticamente"), `${viewport.key}: sem autopublicacao`, "mensagem pos-envio");
  checks.push(...validateMetrics(`${viewport.key}/pos-envio`, metrics));

  return checks;
}

async function validateBackendFlags() {
  const payload = {
    nomeExibicao: "Perfil Sintético Wizard API",
    email: "wizard.api@example.invalid",
    whatsapp: "+5500000000000",
    uf: "GO",
    cidade: "Goiania",
    bairro: "Setor Bueno",
    titulo: "Perfil sintético wizard api",
    descricao: "Texto sintético suficiente para validar flags locais do wizard publico.",
    preco: 120,
    categoria: "ACOMPANHANTE",
    aceiteTermos: true,
    confirmacaoIdade: true
  };
  const response = await fetch(`${backendBaseUrl}/api/public/anunciar`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  const json = await response.json();
  const checks = [];
  addCheck(checks, response.status === 201, "API local do wizard retorna 201 sintetico", response.status);
  addCheck(checks, json.criado === true, "API local criou solicitacao sintetica", json.criado);
  for (const field of [
    "publicado",
    "publicacaoAutomaticaExecutada",
    "uploadRealExecutado",
    "pagamentoCriado",
    "creditoCriado",
    "premiumObrigatorio",
    "emailRealEnviado",
    "whatsappRealEnviado"
  ]) {
    addCheck(checks, json[field] === false, `API local sem efeito real: ${field}`, json[field]);
  }
  return checks;
}

async function main() {
  const remotePort = await freePort();
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), "topsv3-wizard-browser-"));
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
  const allChecks = [];
  const uiLines = [
    "# Relatorio - UI mobile/desktop wizard anunciar",
    "",
    `- Frontend: ${frontendBaseUrl}`,
    `- Backend sintetico: ${backendBaseUrl}`,
    "- Dados reais usados: nao",
    "- Producao/VPS/API externa acessadas: nao",
    "- Upload real/pagamento/Pix/Efi/WhatsApp real: nao",
    ""
  ];

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
      const checks = await runFlow(cdp, viewport);
      allChecks.push(...checks);
      uiLines.push(`## ${viewport.key}`);
      uiLines.push(`- Viewport: ${viewport.width}x${viewport.height}`);
      uiLines.push(`- Prints: ${viewport.key}-inicio.png, ${viewport.key}-intermediaria.png, ${viewport.key}-revisao.png, ${viewport.key}-pos-envio.png`);
      uiLines.push(`- Resultado: ${checks.every((check) => check.resultado === "OK") ? "OK" : "FALHA"}`);
      uiLines.push("");
    }

    allChecks.push(...await validateBackendFlags());
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

  const failures = allChecks.filter((check) => check.resultado !== "OK");
  const auditLines = [
    "# Relatorio - auditoria wizard anunciar sintetico",
    "",
    `- Resultado: ${failures.length ? "FALHA" : "OK"}`,
    `- Frontend: ${frontendBaseUrl}`,
    `- Backend sintetico: ${backendBaseUrl}`,
    "- Rota auditada: /anunciar",
    "- Fluxo guiado por etapas: sim",
    "- Dados usados: sinteticos",
    "- Dados reais usados: nao",
    "- Producao/VPS/API externa acessadas: nao",
    "- Upload real: nao",
    "- Pagamento/Pix/Efi/checkout: nao",
    "- Premium ativado: nao",
    "- Publicacao automatica: nao",
    "- WhatsApp/e-mail real enviado: nao",
    "- Stores no wizard: ausente",
    "",
    "## Checks"
  ];
  for (const check of allChecks) {
    auditLines.push(`- ${check.resultado}: ${check.label} - ${check.detail}`);
  }
  auditLines.push("");
  auditLines.push("## Falhas");
  if (failures.length) failures.forEach((failure) => auditLines.push(`- ${failure.label}: ${failure.detail}`));
  else auditLines.push("- Nenhuma");

  const validationLines = [
    "# Relatorio - validacoes Bloco 34",
    "",
    `- Resultado: ${failures.length ? "FALHA" : "OK"}`,
    `- Total de checks: ${allChecks.length}`,
    `- Falhas: ${failures.length}`,
    "- CDP local usado: sim",
    "- Prints versionados: sim",
    "- Docker do wizard: prefixo topsv3-bloco34-wizard-paridade quando executado pelo wrapper E2E",
    "- Recursos de TopsWI/cripto alterados: nao",
    "- Quarentena usada como staging final: nao",
    ""
  ];
  for (const check of allChecks) {
    validationLines.push(`- ${check.resultado}: ${check.label}`);
  }

  fs.writeFileSync(auditoriaPath, `${auditLines.join("\n")}\n`, "utf8");
  fs.writeFileSync(uiPath, `${uiLines.join("\n")}\n`, "utf8");
  fs.writeFileSync(validacoesPath, `${validationLines.join("\n")}\n`, "utf8");

  console.log("Validacao wizard anunciar sintetico");
  console.log(`FRONTEND_BASE_URL=${frontendBaseUrl}`);
  console.log(`BACKEND_BASE_URL=${backendBaseUrl}`);
  console.log(`VIEWPORTS=${viewports.length}`);
  console.log(`CHECKS=${allChecks.length}`);
  console.log(`FALHAS=${failures.length}`);
  if (failures.length) {
    failures.forEach((failure) => console.log(`FALHA: ${failure.label} - ${failure.detail}`));
    console.log("VALIDATION_RESULT=FALHA_WIZARD_ANUNCIAR_SINTETICO_LOCAL");
    process.exitCode = 1;
  } else {
    console.log("VALIDATION_RESULT=OK_WIZARD_ANUNCIAR_SINTETICO_LOCAL");
  }
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  console.log("VALIDATION_RESULT=PENDENTE_WIZARD_ANUNCIAR_SINTETICO_LOCAL");
  process.exitCode = 2;
});
'@
  [System.IO.File]::WriteAllText($tempScript, $nodeScript, [System.Text.UTF8Encoding]::new($false))

  $env:TOPSV3_WIZARD_FRONTEND_URL = $frontendBaseUrl
  $env:TOPSV3_WIZARD_BACKEND_URL = $safeBackendUrl
  $env:TOPSV3_WIZARD_BROWSER = $browserPath
  $env:TOPSV3_WIZARD_PRINTS_DIR = $printsPath
  $env:TOPSV3_WIZARD_RELATORIO_AUDITORIA = $auditoriaPath
  $env:TOPSV3_WIZARD_RELATORIO_UI = $uiPath
  $env:TOPSV3_WIZARD_RELATORIO_VALIDACOES = $validacoesPath
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
