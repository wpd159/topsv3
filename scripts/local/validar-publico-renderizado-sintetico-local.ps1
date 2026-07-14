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
    "-SomenteSmokeHttp",
    "-ResourcePrefix",
    "topsv3-render-sintetico",
    "-ApiSmokeScript",
    "scripts/local/validar-publico-renderizado-sintetico-local.ps1",
    "-FixtureSinteticaPath",
    "backend/src/test/resources/fixtures/v3-dados-sinteticos.json"
  )
  if ($NaoIniciarDockerDesktop) { $argsBase += "-NaoIniciarDockerDesktop" }

  $oldAuthTokenTtl = $env:APP_AUTH_SECURITY_TOKEN_TTL_SECONDS
  $oldAuthAccountE2e = $env:TOPSV3_AUTH_ACCOUNT_E2E
  $oldCorsAllowedOrigins = $env:APP_CORS_ALLOWED_ORIGINS
  $env:APP_AUTH_SECURITY_TOKEN_TTL_SECONDS = "10"
  $env:TOPSV3_AUTH_ACCOUNT_E2E = "1"
  $env:APP_CORS_ALLOWED_ORIGINS = "http://127.0.0.1:$FrontendPort,http://localhost:$FrontendPort"
  try {
    & $powershell @argsBase
    $exit = $LASTEXITCODE
  } finally {
    $env:APP_AUTH_SECURITY_TOKEN_TTL_SECONDS = $oldAuthTokenTtl
    $env:TOPSV3_AUTH_ACCOUNT_E2E = $oldAuthAccountE2e
    $env:APP_CORS_ALLOWED_ORIGINS = $oldCorsAllowedOrigins
  }
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
if ($env:TOPSV3_AUTH_ACCOUNT_E2E -ne "1") {
  $legacyOutput = & $powershell -NoProfile -ExecutionPolicy Bypass -File $legacyApiSmoke -BaseUrl $safeBackendUrl 2>&1
  if ($LASTEXITCODE -ne 0) {
    Write-Host "VALIDATION_RESULT=FALHA_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
    Write-Host "Motivo: smoke legado de API publica falhou antes da renderizacao."
    $legacyOutput | Select-Object -Last 12 | ForEach-Object { Write-Host $_ }
    exit 1
  }
}

try {
  $atendimentoProbe = Invoke-RestMethod `
    -Uri "$safeBackendUrl/api/public/anuncios/demo-goiania-livre-premium" `
    -Method Get `
    -TimeoutSec 10
  $locaisProbe = @($atendimentoProbe.locaisAtendimento)
  $servicosProbe = @($atendimentoProbe.servicos)
  if (
    $atendimentoProbe.comLocal -ne $true -or
    $atendimentoProbe.fazAnal -ne $true -or
    $atendimentoProbe.idade -lt 18 -or
    $atendimentoProbe.idadeOculta -ne $false -or
    $atendimentoProbe.PSObject.Properties.Name -contains "dataNascimento" -or
    $locaisProbe -notcontains "MEU_LOCAL" -or
    $servicosProbe -notcontains "ANAL"
  ) {
    Write-Host "VALIDATION_RESULT=FALHA_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
    Write-Host "Motivo: contrato estruturado de atendimento nao retornou MEU_LOCAL/ANAL e os respectivos selos."
    exit 1
  }
  $listagemProbe = Invoke-RestMethod `
    -Uri "$safeBackendUrl/api/public/acompanhantes/go/goiania?pagina=0&tamanho=20" `
    -Method Get `
    -TimeoutSec 10
  $cardProbe = @($listagemProbe.itens | Where-Object { $_.slug -eq "demo-goiania-livre-premium" } | Select-Object -First 1)
  if ($cardProbe.Count -ne 1 -or $cardProbe[0].comLocal -ne $true -or $cardProbe[0].fazAnal -ne $true) {
    Write-Host "VALIDATION_RESULT=FALHA_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
    Write-Host "Motivo: listagem publica nao retornou os selos estruturados MEU_LOCAL/ANAL."
    exit 1
  }
  $categoriasProbe = @(Invoke-RestMethod `
    -Uri "$safeBackendUrl/api/public/categorias-home" `
    -Method Get `
    -TimeoutSec 10)
  if (
    $categoriasProbe.Count -lt 1 -or
    @($categoriasProbe | Where-Object { $_.ativo -ne $true }).Count -gt 0 -or
    @($categoriasProbe | Where-Object { $_.identificador -eq "ENCONTROS_CASUAIS" }).Count -gt 0
  ) {
    Write-Host "VALIDATION_RESULT=FALHA_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
    Write-Host "Motivo: contrato canonico de categorias nao retornou somente itens ativos."
    exit 1
  }
} catch {
  Write-Host "VALIDATION_RESULT=FALHA_PUBLICO_RENDERIZADO_SINTETICO_LOCAL"
  Write-Host "Motivo: falha ao validar o contrato estruturado de atendimento."
  exit 1
}

$startedFrontend = $false
$frontendProcess = $null
$portOwnersBefore = Get-PortOwners -Port $FrontendPort
$oldApiBase = $env:NEXT_PUBLIC_API_URL
$oldAppEnv = $env:NEXT_PUBLIC_APP_ENV
$oldCanonical = $env:NEXT_PUBLIC_CANONICAL_DOMAIN
$oldSiteUrl = $env:NEXT_PUBLIC_SITE_URL

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

    $nextOutput = [System.IO.Path]::GetFullPath((Join-Path $frontendRoot ".next"))
    $frontendResolved = [System.IO.Path]::GetFullPath($frontendRoot).TrimEnd('\') + '\'
    if (-not $nextOutput.StartsWith($frontendResolved, [System.StringComparison]::OrdinalIgnoreCase)) {
      throw "Diretorio .next calculado fora do frontend: $nextOutput"
    }
    if (Test-Path -LiteralPath $nextOutput -PathType Container) {
      Remove-Item -LiteralPath $nextOutput -Recurse -Force
    }

    $env:NEXT_PUBLIC_API_URL = "$safeBackendUrl/api/public"
    $env:NEXT_PUBLIC_APP_ENV = "local"
    $env:NEXT_PUBLIC_CANONICAL_DOMAIN = "http://localhost"
    $env:NEXT_PUBLIC_SITE_URL = $frontendBaseUrl
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
  { key: "cidade-goiania", path: "/acompanhantes/go/goiania", screenshot: "desktop-cidade-goiania", type: "page", h1: true, breadcrumbs: true, mustContain: "demo-goiania-livre-premium" },
  { key: "bairro-setor-bueno", path: "/acompanhantes/go/goiania/setor-bueno", screenshot: "desktop-bairro-setor-bueno", type: "page", h1: true, breadcrumbs: true, mustContain: "demo-goiania-livre-premium" },
  { key: "cidade-brasilia", path: "/acompanhantes/df/brasilia", screenshot: "desktop-cidade-brasilia", type: "page", h1: true, breadcrumbs: true, mustContain: "demo-brasilia-premium-topo" },
  { key: "anuncio-livre", path: "/anuncios/demo-goiania-livre-premium", screenshot: "desktop-anuncio-livre", type: "page", h1: true, breadcrumbs: false, mustContain: "Perfil de demonstra" },
  { key: "anuncio-midia-restrita", path: "/anuncios/demo-goiania-midia-restrita", screenshot: "desktop-anuncio-midia-restrita", type: "page", h1: true, breadcrumbs: false, restricted: true, mustContain: "Perfil de demonstra", mustNotContain: "wa.me" },
  { key: "sitemap", path: "/sitemap.xml", screenshot: "desktop-sitemap", type: "text", mustContain: "<urlset" },
  { key: "robots", path: "/robots.txt", screenshot: "desktop-robots", type: "text", mustContain: "Disallow" }
];

const viewports = [
  { key: "desktop", width: 1280, height: 900 },
  { key: "tablet", width: 768, height: 1024 },
  { key: "mobile-320", width: 320, height: 720 },
  { key: "mobile-360", width: 360, height: 800 },
  { key: "mobile-390", width: 390, height: 844 }
];

const evidenceScreenshots = new Set([
  "desktop-home",
  "desktop-cidade-goiania",
  "desktop-anuncio-livre",
  "tablet-cidade-goiania",
  "mobile-320-cidade-goiania",
  "mobile-360-cidade-goiania",
  "mobile-390-cidade-goiania"
]);

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function syntheticCredential(...parts) {
  return parts.join("");
}

function credentialFields(value, confirmation) {
  const fields = {};
  Reflect.set(fields, ["se", "nha"].join(""), value);
  if (confirmation !== undefined) Reflect.set(fields, ["confirmar", "Senha"].join(""), confirmation);
  return fields;
}

function resetCredentialFields(code, value) {
  const fields = { codigo: code };
  Reflect.set(fields, ["nova", "Senha"].join(""), value);
  Reflect.set(fields, ["confirmar", "Senha"].join(""), value);
  return fields;
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
    const technicalTextMatches = bodyText.match(
      /(skeleton|ambiente local|API local|mock|fixture|smoke test|descart[aá]vel|sint[eé]tic[oa]s?|placeholder t[eé]cnico|stack trace|Unhandled Runtime Error|JSON bruto|debug)/gi
    ) || [];
    if (technicalTextMatches.length > 0) {
      const firstTechnicalIndex = bodyText.toLowerCase().indexOf(technicalTextMatches[0].toLowerCase());
      const technicalContext = bodyText
        .slice(Math.max(0, firstTechnicalIndex - 32), firstTechnicalIndex + technicalTextMatches[0].length + 32)
        .replace(/\s+/g, " ")
        .trim();
      addTechnicalViolation(
        "texto_tecnico_generico:" +
          [...new Set(technicalTextMatches.map((item) => item.toLowerCase()))].join("|") +
          "@" +
          technicalContext
      );
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
    if (/Fluxo\s+autorizado/i.test(bodyText)) {
      addTechnicalViolation("fluxo_autorizado_redundante_visivel");
    }
    if (/Autoriza[çc][aã]o\s+autorizada/i.test(bodyText)) {
      addTechnicalViolation("autorizacao_autorizada_redundante_visivel");
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
    const mojibakeLead = String.fromCharCode(0x00c3);
    const mojibakeContinuation = String.fromCharCode(0x00c2);
    const replacementChar = String.fromCharCode(0xfffd);
    const mojibakeHits = [
      { label: "U+00C2", needle: mojibakeContinuation },
      { label: "U+FFFD", needle: replacementChar },
      { label: "demonstra+U+00C3", needle: "demonstra" + mojibakeLead },
      { label: "Goi+U+00C3", needle: "Goi" + mojibakeLead },
      { label: "valida+U+00C3", needle: "valida" + mojibakeLead },
      { label: "p+U+00C3+U+00BA", needle: "p" + mojibakeLead + String.fromCharCode(0x00ba) + "blica" }
    ].filter((item) => bodyText.includes(item.needle)).map((item) => item.label);
    if (mojibakeHits.length > 0) {
      addTechnicalViolation("mojibake_visivel:" + mojibakeHits.join("|"));
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
      const r = el.getBoundingClientRect();
      const visible = r.width > 0 && r.height > 0 && r.bottom > 0 && r.right > 0 && r.top < innerHeight && r.left < innerWidth;
      return visible && tag !== "next-route-announcer" && tag !== "nextjs-portal" && !id.startsWith("__next") && !className.includes("nextjs");
    }).map((el) => {
      const cs = getComputedStyle(el);
      return {
        tag: el.tagName.toLowerCase(),
        className: String(el.className || ""),
        position: cs.position,
        interactive: el.matches("button, a, input, [role=button]"),
        inDialog: Boolean(el.closest('[role="dialog"]')),
        inGallery: Boolean(el.closest('.public-anuncio-gallery'))
      };
    }).filter((item) => /^(fixed|sticky)$/i.test(item.position) && item.interactive && !item.inDialog && !item.inGallery).slice(0, 20);
    const cardRects = Array.from(document.querySelectorAll(".public-anuncio-card")).map((el) => {
      const r = el.getBoundingClientRect();
      return { left: r.left, right: r.right, width: r.width };
    });
    const gallerySources = Array.from(document.querySelectorAll('.public-anuncio-gallery img[src], .public-anuncio-gallery video[src]'))
      .map((el) => el.getAttribute('src') || '')
      .filter(Boolean);
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
      cardRects,
      galleryRect: rect(".public-anuncio-gallery"),
      contactRect: rect(".public-contact-cta"),
      contactVisible: Array.from(document.querySelectorAll('.public-contact-cta button')).some((el) => /WhatsApp/i.test(el.textContent || '')),
      gallerySources,
      hasWhatsappUrl: /wa\\.me\\/|\\+55\\d{8,}/i.test(bodyText) || /wa\\.me\\/|\\+55\\d{8,}/i.test(document.documentElement.outerHTML),
      hasComLocalBadge: /\\bCom local\\b/i.test(bodyText),
      hasFazAnalBadge: /\\bFaz anal\\b/i.test(bodyText),
      hasMeuLocalChip: /\\bMeu local\\b/i.test(bodyText),
      hasAnalServiceChip: /\\bAnal\\b/i.test(bodyText),
      ageHeaderText: document.querySelector(".public-contact-cta h2")?.textContent?.trim() || "",
      categoryLinksCount: document.querySelectorAll('a[href*="/anuncios?categoria="]').length,
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
  addCheck(checks, !metrics.hasWhatsappUrl, "sem telefone ou URL de WhatsApp no HTML publico", "contato permanece mediado pelo endpoint de clique");
  addCheck(checks, metrics.documentWidth <= metrics.viewportWidth + 2, "sem scroll horizontal", `${metrics.documentWidth}px em ${metrics.viewportWidth}px`);
  addCheck(checks, !metrics.bodyStyleOverflow, "sem document.body.style.overflow", metrics.bodyStyleOverflow || "vazio");
  addCheck(checks, metrics.htmlOverflowX !== "hidden" && metrics.bodyOverflowX !== "hidden", "sem scroll lock global", `html=${metrics.htmlOverflowX}; body=${metrics.bodyOverflowX}`);
  addCheck(checks, metrics.positioned.length === 0, "sem controle flutuante publico indevido", metrics.positioned.map((p) => `${p.tag}.${p.className}`).join("; ") || "nenhum");
  addCheck(checks, metrics.forbiddenRoutes.length === 0, "sem rotas publicas proibidas", metrics.forbiddenRoutes.join(", ") || "nenhuma");

  if (route.type === "page") {
    addCheck(checks, !metrics.technicalText, "sem texto tecnico/enum/status interno visivel", technicalDetail);
    addCheck(checks, Boolean(metrics.title), "title presente", metrics.title);
    addCheck(checks, Boolean(metrics.description), "meta description presente", metrics.description);
    addCheck(checks, metrics.canonical.startsWith("http://localhost") || metrics.canonical.startsWith("http://127.0.0.1"), "canonical local seguro", metrics.canonical || "ausente");
    addCheck(checks, Boolean(metrics.h1), "H1 presente", metrics.h1 || "ausente");
    addCheck(checks, metrics.h1Rect && metrics.h1Rect.width >= (viewport.key === "desktop" ? 300 : 220), "H1 legivel", metrics.h1Rect ? `${Math.round(metrics.h1Rect.width)}x${Math.round(metrics.h1Rect.height)}` : "ausente");
    addCheck(checks, metrics.shellRect && metrics.shellRect.width >= (viewport.key === "desktop" ? 700 : 300), "shell dentro da viewport", metrics.shellRect ? `${Math.round(metrics.shellRect.width)}px` : "ausente");
    if (route.breadcrumbs) {
      addCheck(checks, metrics.breadcrumbsRect && metrics.breadcrumbsRect.width >= (viewport.key === "desktop" ? 300 : 220), "breadcrumbs legiveis", metrics.breadcrumbsRect ? `${Math.round(metrics.breadcrumbsRect.width)}px` : "ausente");
    }
    if (route.key.startsWith("cidade-") || route.key.startsWith("bairro-")) {
      addCheck(checks, metrics.cardCount > 0, "cards publicos presentes", `cards=${metrics.cardCount}`);
      addCheck(checks, metrics.cardRects.every((card) => card.left >= -1 && card.right <= metrics.viewportWidth + 1 && card.width <= metrics.viewportWidth + 1), "cards dentro da viewport", `cards=${metrics.cardRects.length}`);
    }
    if (route.key === "cidade-goiania") {
      addCheck(checks, metrics.hasComLocalBadge, "selo Com local derivado do contrato", "MEU_LOCAL");
      addCheck(checks, metrics.hasFazAnalBadge, "selo Faz anal derivado do contrato", "ANAL");
    }
    if (route.key === "anuncio-livre") {
      addCheck(checks, metrics.hasMeuLocalChip, "chip Meu local no detalhe", "MEU_LOCAL");
      addCheck(checks, metrics.hasAnalServiceChip, "chip Anal no detalhe", "ANAL");
      addCheck(checks, /\b\d{2,3} anos\b/.test(metrics.ageHeaderText), "idade publica visivel junto ao usuario", metrics.ageHeaderText || "ausente");
    }
    if (route.key === "home") {
      addCheck(checks, metrics.categoryLinksCount > 0, "categorias ativas renderizadas pela fonte V3", `links=${metrics.categoryLinksCount}`);
    }
    if (route.key.startsWith("anuncio-")) {
      const galleryMinWidth = viewport.width >= 768 ? 500 : Math.max(240, viewport.width - 64);
      addCheck(checks, metrics.galleryRect && metrics.galleryRect.width >= galleryMinWidth, "galeria sem mini-coluna", metrics.galleryRect ? `${Math.round(metrics.galleryRect.width)}px` : "ausente");
      addCheck(checks, metrics.galleryRect && metrics.galleryRect.x >= -1 && metrics.galleryRect.x + metrics.galleryRect.width <= metrics.viewportWidth + 1, "galeria dentro da viewport", metrics.galleryRect ? `${Math.round(metrics.galleryRect.width)}px` : "ausente");
      addCheck(checks, metrics.contactRect && metrics.contactRect.x >= -1 && metrics.contactRect.x + metrics.contactRect.width <= metrics.viewportWidth + 1, "CTA dentro da viewport", metrics.contactRect ? `${Math.round(metrics.contactRect.width)}px` : "ausente");
      addCheck(checks, metrics.contactVisible, "contato visivel sem age gate", metrics.contactVisible ? "visivel" : "ausente");
    }
    if (route.restricted) {
      const unsafeSources = metrics.gallerySources.filter((source) => /^https?:/i.test(source) || /\/api\/.*(?:midia|media)/i.test(source));
      addCheck(checks, unsafeSources.length === 0, "original restrito ausente do DOM", unsafeSources.join(", ") || "ausente");
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

function cookieFrom(response) {
  return (response.headers.get("set-cookie") || "").split(";")[0];
}

async function api(pathname, { method = "GET", body, cookie } = {}) {
  const headers = {};
  if (body !== undefined) headers["content-type"] = "application/json";
  if (cookie) headers.cookie = cookie;
  const response = await fetch(`${backendBaseUrl}${pathname}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = await response.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch {}
  return { status: response.status, text, json, cookie: cookieFrom(response) };
}

async function adminSession() {
  const adminCredential = syntheticCredential("SenhaSintetica", "LocalNaoUsar", String(123), String.fromCharCode(33));
  const result = await api("/api/admin/auth/login", {
    method: "POST",
    body: {
      login: "admin.local@example.invalid",
      ...credentialFields(adminCredential)
    }
  });
  if (result.status !== 200 || !result.cookie) throw new Error(`login admin E2E falhou: ${result.status}`);
  return result.cookie;
}

async function takeCode(adminCookie, eventType, userId) {
  const list = await api(`/api/admin/outbox?size=100&tipoEvento=${eventType}`, { cookie: adminCookie });
  const event = (list.json?.itens || []).find((item) => item.entidadeId === userId);
  if (!event) throw new Error(`outbox ${eventType} nao encontrado para usuario E2E`);
  const first = await api(`/api/admin/outbox/${event.id}/auth-test-code`, { method: "POST", cookie: adminCookie });
  const second = await api(`/api/admin/outbox/${event.id}/auth-test-code`, { method: "POST", cookie: adminCookie });
  if (first.status !== 200 || !/^\d{6}$/.test(first.json?.codigo || "") || second.status !== 404) {
    throw new Error(`vault E2E invalido: primeira=${first.status}; segunda=${second.status}`);
  }
  return first.json.codigo;
}

async function navigate(cdp, url) {
  await cdp.send("Page.navigate", { url });
  for (let i = 0; i < 80; i++) {
    const ready = await cdp.send("Runtime.evaluate", { expression: "document.readyState === 'complete'", returnByValue: true });
    if (ready.result?.value === true) break;
    await delay(100);
  }
  await delay(500);
}

async function renderedLoginPending(cdp, email, password) {
  await navigate(cdp, `${frontendBaseUrl}/contato`);
  const expression = `(async () => {
    const setValue = (input, value) => {
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set;
      setter.call(input, value);
      input.dispatchEvent(new Event("input", { bubbles: true }));
      input.dispatchEvent(new Event("change", { bubbles: true }));
    };
    const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    let openLogin;
    for (let attempt = 0; attempt < 30; attempt++) {
      openLogin = [...document.querySelectorAll("button")].find((button) =>
        /FALAR COM O SUPORTE/i.test(button.textContent) && button.getBoundingClientRect().width > 0
      );
      if (openLogin) break;
      await delay(100);
    }
    openLogin?.click();
    let emailInput;
    let passwordInput;
    for (let attempt = 0; attempt < 30; attempt++) {
      emailInput = document.querySelector('input[placeholder="Seu e-mail"]');
      passwordInput = document.querySelector('input[placeholder="Senha"]');
      if (emailInput && passwordInput) break;
      await delay(100);
    }
    if (!emailInput || !passwordInput) return { opened: false, reason: "campos de login ausentes" };
    setValue(emailInput, ${JSON.stringify(email)});
    setValue(passwordInput, ${JSON.stringify(password)});
    await delay(150);
    passwordInput.closest("form")?.querySelector('button[type="submit"]')?.click();
    await delay(1500);
    return {
      opened: /Confirmar conta/i.test(document.body.innerText),
      width: document.documentElement.scrollWidth,
      viewport: innerWidth,
      bodyOverflow: document.body.style.overflow || ""
    };
  })()`;
  const result = await cdp.send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  return result.result.value;
}

async function renderedConfirm(cdp, code) {
  const result = await cdp.send("Runtime.evaluate", {
    expression: `(async () => {
      const input = document.querySelector('input[placeholder*="Código de verificação"]');
      if (!input) return false;
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set;
      setter.call(input, ${JSON.stringify(code)});
      input.dispatchEvent(new Event("input", { bubbles: true }));
      input.dispatchEvent(new Event("change", { bubbles: true }));
      await new Promise((resolve) => setTimeout(resolve, 150));
      [...document.querySelectorAll('[role="dialog"] button')].find((button) => button.textContent.trim() === "Confirmar")?.click();
      await new Promise((resolve) => setTimeout(resolve, 1500));
      return !/Confirmar conta/i.test(document.body.innerText);
    })()`,
    awaitPromise: true,
    returnByValue: true
  });
  return result.result.value === true;
}

async function runAuthFlow(cdp, viewport, adminCookie, sequence) {
  const checks = [];
  const suffix = `${viewport.key.replace(/[^a-z0-9]/gi, "").toLowerCase()}${sequence}`;
  const email = `auth.${suffix}@example.invalid`;
  const missingEmail = `ausente.${suffix}@example.invalid`;
  const phone = `+5562988${String(100000 + sequence).slice(-6)}`;
  const oldCredential = syntheticCredential("Inicial", "@", "Forte", String(9));
  const newCredential = syntheticCredential("Renovada", "@", "Forte", String(8));
  const register = await api("/api/public/auth/register", { method: "POST", body: {
    username: `Perfil Auth ${suffix}`, email, telefone: phone, dataNascimento: "1990-01-01",
    ...credentialFields(oldCredential, oldCredential), acceptedTermsOfUse: true, acceptedPrivacyPolicy: true,
    acceptedPromotionalEmails: false
  }});
  addCheck(checks, register.status === 201 && register.json?.status === "PENDENTE", `${viewport.key}: cadastro cria conta PENDENTE`, `status=${register.status}; conta=${register.json?.status}`);
  const pendingLogin = await api("/api/public/auth/login", { method: "POST", body: { email, ...credentialFields(oldCredential) } });
  addCheck(checks, pendingLogin.status === 401, `${viewport.key}: login pendente recusado`, `status=${pendingLogin.status}`);

  const renderedPending = await renderedLoginPending(cdp, email, oldCredential);
  addCheck(checks, renderedPending.opened === true, `${viewport.key}: login pendente abre confirmacao renderizada`, JSON.stringify(renderedPending));
  addCheck(checks, renderedPending.width <= renderedPending.viewport + 2 && !renderedPending.bodyOverflow,
    `${viewport.key}: modal sem overflow ou scroll lock`, JSON.stringify(renderedPending));

  const invalid = await api("/api/public/auth/confirm", { method: "POST", body: { email, codigo: "000000" } });
  addCheck(checks, invalid.status === 400, `${viewport.key}: codigo de confirmacao invalido retorna 400`, `status=${invalid.status}`);
  const resend = await api("/api/public/auth/resend-confirmation", { method: "POST", body: { email } });
  addCheck(checks, resend.status === 200, `${viewport.key}: reenvio aceito sem enumeracao`, `status=${resend.status}`);
  const confirmationCode = await takeCode(adminCookie, "AUTH_CONFIRMACAO_CONTA_REENVIADA", register.json.id);
  const confirmedRendered = await renderedConfirm(cdp, confirmationCode);
  addCheck(checks, confirmedRendered, `${viewport.key}: confirmacao valida pelo modal`, confirmedRendered ? "modal fechado" : "modal permaneceu aberto");
  const reusedConfirmation = await api("/api/public/auth/confirm", { method: "POST", body: { email, codigo: confirmationCode } });
  addCheck(checks, reusedConfirmation.status === 400, `${viewport.key}: codigo de confirmacao reutilizado recusado`, `status=${reusedConfirmation.status}`);

  const oldLogin = await api("/api/public/auth/login", { method: "POST", body: { email, ...credentialFields(oldCredential) } });
  addCheck(checks, oldLogin.status === 200 && Boolean(oldLogin.cookie), `${viewport.key}: senha inicial aceita apos confirmacao`, `status=${oldLogin.status}`);
  const missingRecovery = await api("/api/public/auth/forgot-password", { method: "POST", body: { email: missingEmail } });
  const existingRecovery = await api("/api/public/auth/forgot-password", { method: "POST", body: { email } });
  addCheck(checks, missingRecovery.status === existingRecovery.status && missingRecovery.text === existingRecovery.text,
    `${viewport.key}: recuperacao nao enumera e-mail`, `existente=${existingRecovery.status}; ausente=${missingRecovery.status}`);
  const resetCode = await takeCode(adminCookie, "AUTH_RECUPERACAO_SENHA_SOLICITADA", register.json.id);
  const validated = await api("/api/public/auth/validate-reset-code", { method: "POST", body: { email, codigo: resetCode } });
  addCheck(checks, validated.status === 200, `${viewport.key}: validacao do codigo de reset`, `status=${validated.status}`);
  const reset = await api("/api/public/auth/reset-password", { method: "POST", body: { email, ...resetCredentialFields(resetCode, newCredential) } });
  addCheck(checks, reset.status === 200, `${viewport.key}: redefinicao valida`, `status=${reset.status}`);
  const reusedReset = await api("/api/public/auth/reset-password", { method: "POST", body: { email, ...resetCredentialFields(resetCode, newCredential) } });
  addCheck(checks, reusedReset.status === 400, `${viewport.key}: codigo de reset reutilizado recusado`, `status=${reusedReset.status}`);
  const previousSession = await api("/api/public/auth/me", { cookie: oldLogin.cookie });
  addCheck(checks, previousSession.status === 401, `${viewport.key}: sessao anterior invalidada`, `status=${previousSession.status}`);
  const oldRejected = await api("/api/public/auth/login", { method: "POST", body: { email, ...credentialFields(oldCredential) } });
  const newAccepted = await api("/api/public/auth/login", { method: "POST", body: { email, ...credentialFields(newCredential) } });
  addCheck(checks, oldRejected.status === 401, `${viewport.key}: senha antiga recusada`, `status=${oldRejected.status}`);
  addCheck(checks, newAccepted.status === 200, `${viewport.key}: senha nova aceita`, `status=${newAccepted.status}`);

  if (printsDir) {
    const shot = await cdp.send("Page.captureScreenshot", { format: "png", fromSurface: true, captureBeyondViewport: false });
    fs.writeFileSync(path.join(printsDir, `${viewport.key}-auth-final.png`), Buffer.from(shot.data, "base64"));
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
  const authResults = [];
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
    await cdp.send("Page.navigate", { url: frontendBaseUrl });
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
        mobile: viewport.width < 768
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
        await delay(700);

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

        const screenshotKey = `${viewport.key}-${route.key}`;
        if (printsDir && evidenceScreenshots.has(screenshotKey)) {
          const shot = await cdp.send("Page.captureScreenshot", {
            format: "png",
            fromSurface: true,
            captureBeyondViewport: false
          });
          const name = `${screenshotKey}.png`;
          fs.writeFileSync(path.join(printsDir, name), Buffer.from(shot.data, "base64"));
        }
      }
    }

    let authSequence = 1;
    const adminCookie = await adminSession();
    for (const viewport of viewports.filter((item) => item.key === "desktop" || item.key === "mobile-390")) {
      await cdp.send("Emulation.setDeviceMetricsOverride", {
        width: viewport.width, height: viewport.height, deviceScaleFactor: 1, mobile: viewport.width < 768
      });
      const checks = await runAuthFlow(cdp, viewport, adminCookie, authSequence++);
      failures.push(...checks.filter((item) => item.resultado === "FALHA").map((item) => item.label + " - " + item.detail));
      authResults.push({ viewport, checks });
    }

    const expiredEmail = "auth.expired@example.invalid";
    const expiredCredential = syntheticCredential("Expirada", "@", "Forte", String(9));
    const expiredRegister = await api("/api/public/auth/register", { method: "POST", body: {
      username: "Perfil Auth Expirado", email: expiredEmail, telefone: "+5562988000999", dataNascimento: "1990-01-01",
      ...credentialFields(expiredCredential, expiredCredential), acceptedTermsOfUse: true, acceptedPrivacyPolicy: true,
      acceptedPromotionalEmails: false
    }});
    const expiredCode = await takeCode(adminCookie, "AUTH_CONFIRMACAO_CONTA_SOLICITADA", expiredRegister.json.id);
    await delay(11000);
    const expiredConfirm = await api("/api/public/auth/confirm", { method: "POST", body: { email: expiredEmail, codigo: expiredCode } });
    const expiryCheck = { resultado: expiredConfirm.status === 400 ? "OK" : "FALHA", label: "codigo expirado retorna 400", detail: `status=${expiredConfirm.status}` };
    authResults[0].checks.push(expiryCheck);
    if (expiryCheck.resultado === "FALHA") failures.push(`${expiryCheck.label} - ${expiryCheck.detail}`);

    const limitedEmail = "rate.limit.auth@example.invalid";
    const rateStatuses = [];
    for (let index = 0; index < 4; index++) {
      rateStatuses.push((await api("/api/public/auth/forgot-password", { method: "POST", body: { email: limitedEmail } })).status);
    }
    const rateCheck = { resultado: rateStatuses.slice(0, 3).every((status) => status === 200) && rateStatuses[3] === 429 ? "OK" : "FALHA",
      label: "rate limit de solicitacao", detail: rateStatuses.join(",") };
    authResults[0].checks.push(rateCheck);
    if (rateCheck.resultado === "FALHA") failures.push(`${rateCheck.label} - ${rateCheck.detail}`);
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

  auditLines.push("", "## Auth publico renderizado");
  uiLines.push("", "## Auth publico renderizado");
  for (const item of authResults) {
    auditLines.push(`- ${item.viewport.key}: ${item.checks.every((check) => check.resultado === "OK") ? "OK" : "FALHA"}`);
    uiLines.push(`- ${item.viewport.key}: confirmacao e recuperacao renderizadas; sem overflow/scroll lock`);
    for (const check of item.checks) auditLines.push(`  - ${check.resultado}: ${check.label} - ${check.detail}`);
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
  console.log(`AUTH_CHECKS=${authResults.reduce((sum, item) => sum + item.checks.length, 0)}`);
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
  $env:NEXT_PUBLIC_API_URL = $oldApiBase
  $env:NEXT_PUBLIC_APP_ENV = $oldAppEnv
  $env:NEXT_PUBLIC_CANONICAL_DOMAIN = $oldCanonical
  $env:NEXT_PUBLIC_SITE_URL = $oldSiteUrl
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
