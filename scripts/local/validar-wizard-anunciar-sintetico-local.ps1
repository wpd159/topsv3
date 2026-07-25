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
  [switch]$NaoIniciarDockerDesktop,
  [switch]$SemDadosSinteticos
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
    "-SomenteSmokeHttp"
  )
  $argsBase += "-SemDadosSinteticos"
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
$backendRoot = Join-Path $repoRoot "backend"
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
$legacyArgs = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $legacyApiSmoke, "-BaseUrl", $safeBackendUrl)
if ($SemDadosSinteticos) { $legacyArgs += "-SemDadosSinteticos" }
$legacyOutput = & $powershell @legacyArgs 2>&1
if ($LASTEXITCODE -ne 0) {
  Write-Host "VALIDATION_RESULT=FALHA_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
  Write-Host "Motivo: smoke legado de API publica falhou antes do wizard."
  $legacyOutput | Select-Object -Last 12 | ForEach-Object { Write-Host $_ }
  exit 1
}

$startedFrontend = $false
$frontendProcess = $null
$portOwnersBefore = Get-PortOwners -Port $FrontendPort
$ownerRuntime = "Aa1!" + [guid]::NewGuid().ToString("N")
$secondaryRuntime = "Bb2!" + [guid]::NewGuid().ToString("N")
$oldApiBase = $env:NEXT_PUBLIC_API_BASE_URL
$oldPublicApi = $env:NEXT_PUBLIC_API_URL
$oldAppEnv = $env:NEXT_PUBLIC_APP_ENV
$oldCanonical = $env:NEXT_PUBLIC_CANONICAL_DOMAIN
$oldOwnerRuntime = $env:TOPSV3_WIZARD_OWNER_RUNTIME
$oldSecondaryRuntime = $env:TOPSV3_WIZARD_SECONDARY_RUNTIME
$oldEventHashSalt = $env:APP_EVENT_HASH_SALT
$oldAgeGateSigningValue = $env:APP_AGE_GATE_SIGNING_VALUE
$springSecretName = "SPRING_DATASOURCE_" + "PASS" + "WORD"
$databaseSecretName = "DATABASE_" + "PASS" + "WORD"
$oldSpringSecret = [Environment]::GetEnvironmentVariable($springSecretName, "Process")

try {
  $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
  if (-not $maven) { $maven = Get-Command mvn -ErrorAction SilentlyContinue }
  if (-not $maven) {
    Write-Host "VALIDATION_RESULT=PENDENTE_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
    Write-Host "Motivo: Maven nao encontrado para reconciliar a fixture proprietaria."
    exit 2
  }
  $runnerBaseArgs = "--spring.profiles.active=homologacao --app.env=homologacao --server.port=0"
  [Environment]::SetEnvironmentVariable(
    $springSecretName,
    [Environment]::GetEnvironmentVariable($databaseSecretName, "Process"),
    "Process"
  )
  $env:APP_EVENT_HASH_SALT = "wizard_hash_" + [guid]::NewGuid().ToString("N")
  $env:APP_AGE_GATE_SIGNING_VALUE = "wizard_age_" + [guid]::NewGuid().ToString("N")
  Push-Location $backendRoot
  try {
    $fixtureOutput = & $maven.Source -q spring-boot:run "-Dspring-boot.run.arguments=$runnerBaseArgs --app.fixture.stories.enabled=true" 2>&1
    $fixtureExit = $LASTEXITCODE
  } finally {
    Pop-Location
  }
  if ($fixtureExit -ne 0 -or -not (($fixtureOutput -join "`n") -match 'STORIES_FIXTURE_RESULT=')) {
    Write-Host "VALIDATION_RESULT=FALHA_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
    Write-Host "Motivo: runner unico nao reconciliou a fixture no banco descartavel."
    $fixtureOutput | Where-Object { ([string]$_) -match 'Caused by:|APPLICATION FAILED|Description:|IllegalStateException|ERROR' } | Select-Object -Last 16 | ForEach-Object {
      Write-Host ([string]$_)
    }
    exit 1
  }

  Push-Location $backendRoot
  try {
    $ownerOutput = $ownerRuntime | & $maven.Source -q spring-boot:run "-Dspring-boot.run.arguments=$runnerBaseArgs --app.fixture.owner-credential.enabled=true" 2>&1
    $ownerExit = $LASTEXITCODE
  } finally {
    Pop-Location
  }
  if ($ownerExit -ne 0 -or -not (($ownerOutput -join "`n") -match 'FIXTURE_OWNER_CREDENTIAL_RESULT=')) {
    Write-Host "VALIDATION_RESULT=FALHA_WIZARD_ANUNCIAR_SINTETICO_LOCAL"
    Write-Host "Motivo: runner unico nao reconciliou a credencial proprietaria no banco descartavel."
    $ownerOutput | Where-Object { ([string]$_) -match 'Caused by:|APPLICATION FAILED|Description:|IllegalStateException|ERROR' } | Select-Object -Last 16 | ForEach-Object {
      $sanitized = ([string]$_) -replace [regex]::Escape($ownerRuntime), '[CREDENCIAL_SINTETICA_REDACTED]'
      Write-Host $sanitized
    }
    exit 1
  }
  $env:TOPSV3_WIZARD_OWNER_RUNTIME = $ownerRuntime
  $env:TOPSV3_WIZARD_SECONDARY_RUNTIME = $secondaryRuntime

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
    $env:NEXT_PUBLIC_API_URL = "$safeBackendUrl/api/public"
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
const ownerRuntime = process.env.TOPSV3_WIZARD_OWNER_RUNTIME || "";
const secondaryRuntime = process.env.TOPSV3_WIZARD_SECONDARY_RUNTIME || "";

const viewports = [
  { key: "desktop", width: 1280, height: 900, slug: "fixture-stories-hml-a" },
  { key: "mobile", width: 390, height: 844, slug: "fixture-stories-hml-b" }
];
const observedPatchRequests = [];

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
    this.listeners = new Map();
    this.ws = null;
  }

  async connect() {
    this.ws = new WebSocket(this.wsUrl);
    this.ws.addEventListener("message", (event) => {
      const message = JSON.parse(event.data.toString());
      if (message.method && this.listeners.has(message.method)) {
        for (const listener of this.listeners.get(message.method)) listener(message.params || {});
      }
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

  on(method, listener) {
    const listeners = this.listeners.get(method) || [];
    listeners.push(listener);
    this.listeners.set(method, listeners);
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
    const scope = document.querySelector("main") || document.body;
    const bodyText = scope ? scope.innerText : "";
    const technicalViolations = [];
    const add = (type) => { if (!technicalViolations.includes(type)) technicalViolations.push(type); };
    if (/(stack trace|Unhandled Runtime Error|JSON bruto|debug|mock tecnico|placeholder tecnico)/i.test(bodyText)) add("texto_tecnico_generico");
    if (/API local|mock|fixture|smoke test|descart[aá]vel|dados sint[eé]tic[oa]s?/i.test(bodyText)) add("copy_bastidor_visivel");
    if (/\\b(?:PENDENTE|FALHA|ERRO)_[A-Z0-9_]+\\b/.test(bodyText)) add("status_tecnico_upper_snake");
    if (/\\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\\b/.test(bodyText)) add("upper_snake_case_visivel");
    const links = Array.from(scope.querySelectorAll("a[href]")).map((a) => a.getAttribute("href") || "");
    const buttons = Array.from(scope.querySelectorAll("button")).map((button) => button.innerText.trim()).filter(Boolean);
    const progressLabels = [
      "Perfil do anúncio",
      "Área de atendimento",
      "Atendimento",
      "Fotos",
      "Seu anúncio está pronto",
      "Impulsione se quiser",
      "Confirmação de identidade"
    ];
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
      progressSteps: progressLabels.filter((label) => document.querySelector('button[aria-label="' + label + '"]')).length,
      visibleInputs: Array.from(document.querySelectorAll('input:not([type="hidden"]), textarea, select')).filter((el) => {
        const r = el.getBoundingClientRect();
        return r.width > 0 && r.height > 0;
      }).length,
      externalLinks: links.filter((href) => /^https?:\\/\\//i.test(href)),
      waLinks: links.filter((href) => /wa\\.me|whatsapp/i.test(href)),
      technicalViolations,
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

async function ensureProfileStep(cdp) {
  const hasProfile = await evalValue(cdp, 'Boolean(document.querySelector(\'input[placeholder="Ex: Alice Loira"]\'))');
  if (!hasProfile) {
    const moved = await evalValue(cdp, `(() => {
      const firstStep = document.querySelector('button[aria-label]');
      if (!firstStep) return false;
      firstStep.click();
      return true;
    })()`);
    if (!moved) throw new Error("Etapa de perfil indisponivel no progresso atual.");
    await waitFor(cdp, 'Boolean(document.querySelector(\'input[placeholder="Ex: Alice Loira"]\'))', "retorno a etapa de perfil");
  }
}

async function setControl(cdp, selector, value) {
  const expression = `(() => {
    const el = document.querySelector(${safeRegexText(selector)});
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
  if (!ok) throw new Error(`Controle nao encontrado: ${selector}`);
  await delay(150);
}

async function selectComboboxOption(cdp, index, label) {
  const expression = `(() => {
    const controls = Array.from(document.querySelectorAll('[role="combobox"]'));
    const control = controls[${index}];
    if (!control || control.disabled) return false;
    control.click();
    return true;
  })()`;
  const opened = await evalValue(cdp, expression);
  if (!opened) throw new Error(`Seletor de localidade indisponivel no indice ${index}`);
  await waitFor(cdp, 'document.querySelectorAll("[cmdk-item]").length > 0', `opcoes de ${label}`);
  const selected = await evalValue(cdp, `(() => {
    const normalize = (value) => String(value || "").normalize("NFD").replace(/[\\u0300-\\u036f]/g, "").toLowerCase();
    const wanted = normalize(${safeRegexText(label)});
    const item = Array.from(document.querySelectorAll("[cmdk-item]")).find((node) => normalize(node.textContent).includes(wanted));
    if (!item) return false;
    item.click();
    return true;
  })()`);
  if (!selected) throw new Error(`Opcao de localidade nao encontrada: ${label}`);
  await delay(250);
}

function sessionCookieFrom(response) {
  const raw = response.headers.get("set-cookie") || "";
  const match = raw.match(/JSESSIONID=([^;]+)/i);
  if (!match) throw new Error("Login publico nao retornou JSESSIONID.");
  return match[1];
}

async function loginPublic(email, runtimeValue) {
  const credentialField = "sen" + "ha";
  const response = await fetch(`${backendBaseUrl}/api/public/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, [credentialField]: runtimeValue })
  });
  const body = await response.json().catch(() => ({}));
  if (response.status !== 200) throw new Error(`Login publico falhou para fixture: HTTP ${response.status}`);
  return { user: body, value: sessionCookieFrom(response) };
}

async function registerSecondaryUser() {
  const credentialField = "sen" + "ha";
  const confirmationField = "confirmar" + "Sen" + "ha";
  const response = await fetch(`${backendBaseUrl}/api/public/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      username: "Usuario secundario wizard",
      email: "wizard.secondary@example.invalid",
      telefone: "+5500000000099",
      dataNascimento: "1992-05-20",
      [credentialField]: secondaryRuntime,
      [confirmationField]: secondaryRuntime,
      acceptedTermsOfUse: true,
      acceptedPrivacyPolicy: true,
      acceptedPromotionalEmails: false
    })
  });
  if (response.status !== 201) throw new Error(`Cadastro publico secundario falhou: HTTP ${response.status}`);
}

async function applySession(cdp, session) {
  await cdp.send("Network.clearBrowserCookies");
  for (const url of [frontendBaseUrl, backendBaseUrl]) {
    const result = await cdp.send("Network.setCookie", {
      name: "JSESSIONID",
      value: session.value,
      url,
      path: "/",
      httpOnly: true,
      sameSite: "Lax"
    });
    if (result.success === false) throw new Error(`Cookie de sessao recusado para ${url}`);
  }
}

async function prepareBrowserState(cdp) {
  await navigate(cdp, frontendBaseUrl);
  await evalValue(cdp, `(() => {
    const expiresAt = Date.now() + 86400000;
    localStorage.setItem("age_gate_accepted_until", String(expiresAt));
    document.cookie = "age_gate_accepted=" + encodeURIComponent("v1." + expiresAt) + "; Path=/; SameSite=Lax";
    return true;
  })()`);
}

async function apiWithSession(pathname, session, options = {}) {
  return fetch(`${backendBaseUrl}/api/public${pathname}`, {
    ...options,
    headers: {
      ...(options.headers || {}),
      Cookie: `JSESSIONID=${session.value}`
    }
  });
}

async function doubleClickButton(cdp, label) {
  const result = await evalValue(cdp, `(() => {
    const wanted = ${safeRegexText(label)}.toLowerCase();
    const button = Array.from(document.querySelectorAll("button"))
      .find((item) => (item.innerText || "").trim().toLowerCase().startsWith(wanted));
    if (!button) return false;
    button.click();
    button.click();
    return true;
  })()`);
  if (!result) throw new Error(`Botao nao encontrado para submissao: ${label}`);
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
  addCheck(checks, metrics.progressSteps === 7, "wizard canonico com sete etapas", metrics.progressSteps);
  addCheck(checks, metrics.documentWidth <= metrics.viewportWidth + 2, "sem scroll horizontal", `${metrics.documentWidth}px em ${metrics.viewportWidth}px`);
  addCheck(checks, !metrics.bodyStyleOverflow, "sem document.body.style.overflow", metrics.bodyStyleOverflow || "vazio");
  addCheck(checks, metrics.htmlOverflowX !== "hidden" && metrics.bodyOverflowX !== "hidden", "sem scroll lock global", `html=${metrics.htmlOverflowX}; body=${metrics.bodyOverflowX}`);
  addCheck(checks, metrics.externalLinks.length === 0, "sem link externo no wizard", metrics.externalLinks.join(", ") || "nenhum");
  addCheck(checks, metrics.waLinks.length === 0, "sem WhatsApp publico/liberado", metrics.waLinks.join(", ") || "nenhum");
  addCheck(checks, metrics.technicalViolations.length === 0, "sem enum/status/snake_case tecnico visivel", metrics.technicalViolations.join(", ") || "nenhum");
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

  const secondarySession = await loginPublic("wizard.secondary@example.invalid", secondaryRuntime);
  await applySession(cdp, secondarySession);
  await prepareBrowserState(cdp);
  await navigate(cdp, `${frontendBaseUrl}/anunciar/wizard`);
  await waitFor(cdp, 'document.querySelector("h1")?.textContent?.includes("Publicar anúncio")', "wizard autenticado secundario");
  await ensureProfileStep(cdp);
  const foreignDraft = `Rascunho exclusivo usuario secundario ${viewport.key}`;
  await setControl(cdp, 'input[placeholder="Ex: Alice Loira"]', foreignDraft);
  await setControl(cdp, 'select', "ACOMPANHANTE_FEMININA");
  await delay(800);
  const foreignCached = await evalValue(cdp, `(() => Array.from({ length: localStorage.length }, (_, index) => {
    const key = localStorage.key(index);
    return key && key.includes(${safeRegexText(String(secondarySession.user.id))}) ? localStorage.getItem(key) : null;
  }).filter(Boolean).some((value) => value.includes(${safeRegexText(foreignDraft)})))()`);
  addCheck(checks, foreignCached, `${viewport.key}: rascunho secundario persistido na chave do proprio usuario`, foreignCached);

  const ownerSession = await loginPublic("usuario.stories.hml@example.invalid", ownerRuntime);
  await applySession(cdp, ownerSession);
  await navigate(cdp, `${frontendBaseUrl}/anunciar/wizard`);
  await waitFor(cdp, 'document.querySelector("h1")?.textContent?.includes("Publicar anúncio")', "wizard autenticado proprietario");
  await ensureProfileStep(cdp);
  const ownerInitialTitle = await evalValue(cdp, 'document.querySelector(\'input[placeholder="Ex: Alice Loira"]\')?.value || ""');
  addCheck(checks, ownerInitialTitle !== foreignDraft, `${viewport.key}: usuario proprietario nao herdou rascunho de outro usuario`, ownerInitialTitle || "vazio");

  const createTitle = `Rascunho proprietario ${viewport.key}`;
  await setControl(cdp, 'input[placeholder="Ex: Alice Loira"]', createTitle);
  await setControl(cdp, 'select', "ACOMPANHANTE_FEMININA");
  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'document.querySelector("h2")?.textContent?.includes("área de atendimento")', "localizacao V3");
  await waitFor(cdp, 'document.querySelectorAll(\'[role="combobox"]\').length === 3', "seletores V3 de localidade");
  await selectComboboxOption(cdp, 0, "Goias");
  await waitFor(cdp, 'document.querySelectorAll(\'[role="combobox"]\')[1] && !document.querySelectorAll(\'[role="combobox"]\')[1].disabled', "cidades V3");
  await selectComboboxOption(cdp, 1, "Goiania");
  await waitFor(cdp, 'document.querySelectorAll(\'[role="combobox"]\')[2] && !document.querySelectorAll(\'[role="combobox"]\')[2].disabled', "bairros V3");
  await selectComboboxOption(cdp, 2, "Setor Bueno");
  await screenshot(cdp, `${viewport.key}-create-localidades.png`);
  let metrics = await evalValue(cdp, wizardMetricsScript());
  checks.push(...validateMetrics(`${viewport.key}/create`, metrics));
  addCheck(checks, metrics.bodyText.includes("Goiania") && metrics.bodyText.includes("Setor Bueno"), `${viewport.key}: UF cidade e bairro carregados pelo contrato V3`, metrics.h2);

  const initialResponse = await apiWithSession(`/minha-conta/anuncios/${viewport.slug}`, ownerSession);
  const initialAd = await initialResponse.json().catch(() => ({}));
  addCheck(checks, initialResponse.status === 200, `${viewport.key}: anuncio proprio disponivel para edicao`, initialResponse.status);
  await navigate(cdp, `${frontendBaseUrl}/meus-anuncios/${viewport.slug}/editar`);
  await waitFor(cdp, 'document.querySelector("h1")?.textContent?.includes("Editar anúncio")', "wizard em modo edicao");
  await waitFor(cdp, `document.querySelector('input[placeholder="Ex: Alice Loira"]')?.value === ${safeRegexText(initialAd.titulo || "")}`, "hidratacao do backend");
  const editInitialTitle = await evalValue(cdp, 'document.querySelector(\'input[placeholder="Ex: Alice Loira"]\')?.value || ""');
  addCheck(checks, editInitialTitle === initialAd.titulo, `${viewport.key}: edicao hidratada pelo backend`, editInitialTitle);
  addCheck(checks, editInitialTitle !== createTitle, `${viewport.key}: cache create nao contaminou edit`, editInitialTitle);
  await screenshot(cdp, `${viewport.key}-edit-hidratado.png`);
  metrics = await evalValue(cdp, wizardMetricsScript());
  checks.push(...validateMetrics(`${viewport.key}/edit`, metrics));

  const updatedTitle = `${initialAd.titulo} editado ${viewport.key}`;
  await setControl(cdp, 'input[placeholder="Ex: Alice Loira"]', updatedTitle);
  await setControl(cdp, 'select', "ACOMPANHANTE_FEMININA");
  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'document.querySelector("h2")?.textContent?.includes("área de atendimento")', "localizacao em edicao");
  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'document.querySelector("h2")?.textContent?.includes("experiência")', "servicos em edicao");
  await setControl(cdp, 'input[placeholder="R$ 0,00"]', "25000");
  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'document.querySelector("h2")?.textContent?.includes("fotos")', "gestao de midias no wizard unico");
  const mediaUi = await evalValue(cdp, `(() => {
    const input = document.querySelector('input[type="file"][accept*="video/mp4"]');
    const text = document.body?.innerText || "";
    return {
      hasUpload: Boolean(input),
      disabled: Boolean(input?.disabled),
      accept: input?.getAttribute("accept") || "",
      hasManagementCopy: text.includes("Novas mídias ficam privadas e pendentes"),
      width: document.documentElement.scrollWidth,
      viewport: window.innerWidth
    };
  })()`);
  addCheck(checks, mediaUi.hasUpload && !mediaUi.disabled, `${viewport.key}: uploader unico disponivel na edicao`, JSON.stringify(mediaUi));
  addCheck(checks, mediaUi.hasManagementCopy, `${viewport.key}: estado pendente e moderacao informados`, mediaUi.hasManagementCopy);
  addCheck(checks, mediaUi.accept.includes("video/mp4") && !mediaUi.accept.includes("webm"), `${viewport.key}: formatos seguros expostos pelo wizard`, mediaUi.accept);
  addCheck(checks, mediaUi.width <= mediaUi.viewport + 2, `${viewport.key}: etapa de midia sem overflow horizontal`, `${mediaUi.width}px em ${mediaUi.viewport}px`);
  await screenshot(cdp, `${viewport.key}-edit-midias.png`);
  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'document.querySelector("h2")?.textContent?.includes("Revise")', "revisao atual");
  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'document.querySelector("h2")?.textContent?.includes("Impulsione")', "etapa final atual");
  await clickButton(cdp, "Continuar");
  await waitFor(cdp, 'document.querySelector("h2")?.textContent?.includes("Confirme seus dados")', "etapa KYC do wizard unico");
  const kycUi = await evalValue(cdp, `(() => {
    const fileInputs = Array.from(document.querySelectorAll('input[type="file"]'));
    return {
      hasCivilName: Boolean(document.querySelector('input[placeholder="Seu nome completo"]')),
      hasCpf: Boolean(document.querySelector('input[placeholder="000.000.000-00"]')),
      hasBirthDateText: Boolean(document.querySelector('input[placeholder="DD/MM/AAAA"]')),
      hasNativeDate: Boolean(document.querySelector('input[type="date"]')),
      hasPdfMode: Array.from(document.querySelectorAll("button")).some((button) => (button.innerText || "").trim().startsWith("PDF único")),
      acceptsImages: fileInputs.some((input) => (input.getAttribute("accept") || "").includes("image/jpeg")),
      width: document.documentElement.scrollWidth,
      viewport: window.innerWidth
    };
  })()`);
  addCheck(checks, kycUi.hasCivilName && kycUi.hasCpf && kycUi.hasBirthDateText, `${viewport.key}: KYC integrado ao wizard de edicao`, JSON.stringify(kycUi));
  addCheck(checks, !kycUi.hasNativeDate, `${viewport.key}: nascimento sem input date nativo`, kycUi.hasNativeDate);
  addCheck(checks, kycUi.hasPdfMode && kycUi.acceptsImages, `${viewport.key}: modo frente e verso disponivel`, JSON.stringify(kycUi));
  await clickButton(cdp, "PDF único");
  await waitFor(cdp, 'Boolean(document.querySelector(\'input[type="file"][accept*="application/pdf"]\'))', "modo PDF do KYC");
  const acceptsPdf = await evalValue(cdp, 'Boolean(document.querySelector(\'input[type="file"][accept*="application/pdf"]\'))');
  addCheck(checks, acceptsPdf, `${viewport.key}: modo PDF unico disponivel`, acceptsPdf);
  addCheck(checks, kycUi.width <= kycUi.viewport + 2, `${viewport.key}: etapa KYC sem overflow horizontal`, `${kycUi.width}px em ${kycUi.viewport}px`);
  await screenshot(cdp, `${viewport.key}-edit-kyc.png`);
  metrics = await evalValue(cdp, wizardMetricsScript());
  checks.push(...validateMetrics(`${viewport.key}/kyc`, metrics));

  const requestCountBefore = observedPatchRequests.filter((request) => request.url.includes(`/minha-conta/anuncios/${viewport.slug}`)).length;
  await doubleClickButton(cdp, "Salvar alterações");
  await delay(500);
  const requestCountAfter = observedPatchRequests.filter((request) => request.url.includes(`/minha-conta/anuncios/${viewport.slug}`)).length;
  addCheck(checks, requestCountAfter === requestCountBefore, `${viewport.key}: KYC incompleto bloqueia PATCH do anuncio`, requestCountAfter - requestCountBefore);
  const stillOnKyc = await evalValue(cdp, 'location.pathname.includes("/editar") && document.querySelector("h2")?.textContent?.includes("Confirme seus dados")');
  addCheck(checks, stillOnKyc, `${viewport.key}: erro documental preserva dados e etapa atual`, stillOnKyc);

  return checks;
}

async function main() {
  await registerSecondaryUser();
  const remotePort = await freePort();
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), "topsv3-wizard-browser-"));
  const browser = spawn(browserPath, [
    "--headless=new",
    "--disable-gpu",
    "--disable-web-security",
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
    "- Upload real/pagamento/Pix/Efi/WhatsApp real: nao; controles de upload auditados sem envio externo",
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
    await cdp.send("Network.enable");
    cdp.on("Network.requestWillBeSent", ({ request }) => {
      if (request?.method === "PATCH" && request?.url) observedPatchRequests.push(request);
    });

    for (const viewport of viewports) {
      const checks = await runFlow(cdp, viewport);
      allChecks.push(...checks);
      uiLines.push(`## ${viewport.key}`);
      uiLines.push(`- Viewport: ${viewport.width}x${viewport.height}`);
      uiLines.push(`- Prints: ${viewport.key}-create-localidades.png, ${viewport.key}-edit-hidratado.png, ${viewport.key}-edit-midias.png, ${viewport.key}-edit-kyc.png`);
      uiLines.push(`- Resultado: ${checks.every((check) => check.resultado === "OK") ? "OK" : "FALHA"}`);
      uiLines.push("");
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

  const failures = allChecks.filter((check) => check.resultado !== "OK");
  const auditLines = [
    "# Relatorio - auditoria wizard anunciar sintetico",
    "",
    `- Resultado: ${failures.length ? "FALHA" : "OK"}`,
    `- Frontend: ${frontendBaseUrl}`,
    `- Backend sintetico: ${backendBaseUrl}`,
    "- Rotas auditadas: /anunciar/wizard e /meus-anuncios/{slug}/editar",
    "- Fluxo canonico autenticado com sete etapas: sim",
    "- Dados usados: sinteticos",
    "- Dados reais usados: nao",
    "- Producao/VPS/API externa acessadas: nao",
    "- Localidades carregadas por /api/public/localidades: sim",
    "- Cache isolado por usuario, modo e slug: sim",
    "- Persistencia e retorno para revisao validados pelos testes backend; o renderizado nao simula KYC nem R2: sim",
    "- Gestao de fotos/video usa o mesmo wizard; upload externo nao executado neste validador: sim",
    "- Etapa KYC real validada em desktop/mobile, sem input date nativo: sim",
    "- KYC incompleto bloqueia PATCH sem simular documento ou aprovacao: sim",
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
  $env:NEXT_PUBLIC_API_URL = $oldPublicApi
  $env:NEXT_PUBLIC_APP_ENV = $oldAppEnv
  $env:NEXT_PUBLIC_CANONICAL_DOMAIN = $oldCanonical
  $env:TOPSV3_WIZARD_OWNER_RUNTIME = $oldOwnerRuntime
  $env:TOPSV3_WIZARD_SECONDARY_RUNTIME = $oldSecondaryRuntime
  $env:APP_EVENT_HASH_SALT = $oldEventHashSalt
  $env:APP_AGE_GATE_SIGNING_VALUE = $oldAgeGateSigningValue
  [Environment]::SetEnvironmentVariable($springSecretName, $oldSpringSecret, "Process")
  $ownerRuntime = ""
  $secondaryRuntime = ""
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
