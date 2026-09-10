import { readFile } from "node:fs/promises"
import { pathToFileURL } from "node:url"

const EXPECTED_MANIFEST_VERSION = 3
const EXPECTED_NEXT_VERSION = "15.5.24"
const GLOBAL_HEADER_SOURCE = "/:path*"
const EXPECTED_GLOBAL_HEADER_REGEX =
  "^(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$"
const MANIFEST_URL = new URL("../.next/routes-manifest.json", import.meta.url)
const PACKAGE_LOCK_URL = new URL("../package-lock.json", import.meta.url)

export const EXPECTED_SCOPED_NOINDEX_ROUTES = new Map([
  ["/acesso-negado", "^/acesso-negado(?:/)?$"],
  [
    "/admin/:path*",
    "^/admin(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  [
    "/anunciar/:path*",
    "^/anunciar(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  ["/api/:path*", "^/api(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$"],
  [
    "/chat/:path*",
    "^/chat(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  [
    "/checkout/:path*",
    "^/checkout(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  [
    "/creditos/:path*",
    "^/creditos(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  [
    "/documentos/:path*",
    "^/documentos(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  [
    "/favoritos/:path*",
    "^/favoritos(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  [
    "/internal/:path*",
    "^/internal(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  ["/kyc/:path*", "^/kyc(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$"],
  [
    "/meus-anuncios/:path*",
    "^/meus-anuncios(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  [
    "/meus-tickets/:path*",
    "^/meus-tickets(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  [
    "/minha-conta/:path*",
    "^/minha-conta(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  [
    "/painel/:path*",
    "^/painel(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  ["/planos-e-creditos", "^/planos-e-creditos(?:/)?$"],
  [
    "/preview/:path*",
    "^/preview(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  ["/registrar", "^/registrar(?:/)?$"],
  [
    "/uploads/:path*",
    "^/uploads(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
  [
    "/webhooks/:path*",
    "^/webhooks(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  ],
])

const HEADER_ROUTE_KEYS = new Set([
  "basePath",
  "has",
  "headers",
  "locale",
  "missing",
  "regex",
  "source",
])
const HEADER_KEYS = new Set(["key", "value"])
const CONDITION_KEYS = new Set(["key", "type", "value"])
const CONDITION_TYPES = new Set(["cookie", "header", "host", "query"])
const REQUIRED_BLOCKED_DIRECTIVES = ["noindex", "nofollow", "noarchive"]
const FLAG_DIRECTIVES = new Set([
  "all",
  "follow",
  "index",
  "indexifembedded",
  "noarchive",
  "nofollow",
  "noimageindex",
  "noindex",
  "none",
  "nosnippet",
  "notranslate",
])
const VALUE_DIRECTIVES = new Set([
  "max-image-preview",
  "max-snippet",
  "max-video-preview",
])
const INCOMPATIBLE_BLOCKED_DIRECTIVES = new Set([
  "all",
  "follow",
  "index",
  "indexifembedded",
])

class ArtifactValidationError extends Error {}

function fail(message) {
  throw new ArtifactValidationError(message)
}

function isRecord(value) {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function rejectUnknownKeys(value, expectedKeys, label) {
  if (Object.keys(value).some((key) => !expectedKeys.has(key))) {
    fail("Schema incompativel: campo desconhecido em " + label + ".")
  }
}

export function parseExpectedMode(argumentsList) {
  if (argumentsList.length !== 1) {
    fail("Informe exatamente um modo: blocked ou public.")
  }

  const [mode] = argumentsList
  if (mode !== "blocked" && mode !== "public") {
    fail("Modo invalido. Use blocked ou public.")
  }

  return mode
}

export function parseManifestText(rawManifest) {
  try {
    return JSON.parse(rawManifest)
  } catch {
    fail("routes-manifest.json contem JSON invalido.")
  }
}

async function loadManifest() {
  let rawManifest
  try {
    rawManifest = await readFile(MANIFEST_URL, "utf8")
  } catch (error) {
    if (error && typeof error === "object" && error.code === "ENOENT") {
      fail("routes-manifest.json ausente.")
    }
    fail("Nao foi possivel ler routes-manifest.json.")
  }

  return parseManifestText(rawManifest)
}

async function validateNextVersion() {
  let packageLock
  try {
    packageLock = JSON.parse(await readFile(PACKAGE_LOCK_URL, "utf8"))
  } catch {
    fail("Nao foi possivel validar package-lock.json.")
  }

  const nextPackage = packageLock?.packages?.["node_modules/next"]
  if (
    packageLock?.lockfileVersion !== 3 ||
    !isRecord(nextPackage) ||
    nextPackage.version !== EXPECTED_NEXT_VERSION
  ) {
    fail("Schema do Next.js incompativel; esperada a versao " + EXPECTED_NEXT_VERSION + ".")
  }
}

function validateRouteConditions(route, routeIndex) {
  let conditional = false

  for (const conditionName of ["has", "missing"]) {
    if (!Object.hasOwn(route, conditionName)) continue
    const conditions = route[conditionName]
    if (!Array.isArray(conditions)) {
      fail(
        "Schema incompativel: condicao " +
          conditionName +
          " invalida na regra " +
          routeIndex +
          ".",
      )
    }
    conditional ||= conditions.length > 0

    conditions.forEach((condition, conditionIndex) => {
      if (!isRecord(condition)) {
        fail(
          "Schema incompativel: condicao " +
            conditionName +
            "[" +
            conditionIndex +
            "] invalida na regra " +
            routeIndex +
            ".",
        )
      }
      rejectUnknownKeys(
        condition,
        CONDITION_KEYS,
        "condicao " + conditionName + "[" + conditionIndex + "]",
      )

      const isHost = condition.type === "host"
      const valid =
        CONDITION_TYPES.has(condition.type) &&
        (isHost
          ? typeof condition.value === "string" && condition.key === undefined
          : typeof condition.key === "string" &&
            (condition.value === undefined ||
              typeof condition.value === "string"))
      if (!valid) {
        fail(
          "Schema incompativel: condicao " +
            conditionName +
            "[" +
            conditionIndex +
            "] invalida na regra " +
            routeIndex +
            ".",
        )
      }
    })
  }

  return conditional
}

function validateHeader(header, routeIndex, headerIndex) {
  if (!isRecord(header)) {
    fail("Schema incompativel: header invalido na regra " + routeIndex + ".")
  }
  rejectUnknownKeys(
    header,
    HEADER_KEYS,
    "header " + headerIndex + " da regra " + routeIndex,
  )
  if (
    typeof header.key !== "string" ||
    !/^[a-zA-Z0-9!#$%&'*+.^_~|-]+$/.test(header.key) ||
    typeof header.value !== "string" ||
    header.value.trim() === ""
  ) {
    fail("Schema incompativel: header invalido na regra " + routeIndex + ".")
  }

  return { key: header.key.toLowerCase(), value: header.value }
}

function validateHeaderRoute(route, routeIndex) {
  if (!isRecord(route)) {
    fail("Schema incompativel: regra de headers " + routeIndex + " invalida.")
  }
  rejectUnknownKeys(route, HEADER_ROUTE_KEYS, "regra de headers " + routeIndex)
  if (
    typeof route.source !== "string" ||
    !route.source.startsWith("/") ||
    route.source.trim() !== route.source ||
    typeof route.regex !== "string" ||
    !route.regex.startsWith("^") ||
    !route.regex.endsWith("$") ||
    !Array.isArray(route.headers) ||
    route.headers.length === 0
  ) {
    fail("Schema incompativel: regra de headers " + routeIndex + " invalida.")
  }
  try {
    new RegExp(route.regex)
  } catch {
    fail("Schema incompativel: regex invalido na regra " + routeIndex + ".")
  }
  for (const optionName of ["basePath", "locale"]) {
    if (Object.hasOwn(route, optionName) && route[optionName] !== false) {
      fail(
        "Schema incompativel: " +
          optionName +
          " invalido na regra " +
          routeIndex +
          ".",
      )
    }
  }

  return {
    source: route.source,
    regex: route.regex,
    headers: route.headers.map((header, headerIndex) =>
      validateHeader(header, routeIndex, headerIndex),
    ),
    conditional: validateRouteConditions(route, routeIndex),
  }
}

function validateRelevantManifestSchema(manifest) {
  if (
    !isRecord(manifest) ||
    manifest.version !== EXPECTED_MANIFEST_VERSION ||
    manifest.basePath !== "" ||
    manifest.caseSensitive !== false ||
    !Array.isArray(manifest.headers)
  ) {
    fail("Schema minimo de routes-manifest.json incompativel.")
  }

  return manifest.headers.map(validateHeaderRoute)
}

function robotsHeader(route, routeIndex) {
  const matches = route.headers.filter(
    (header) => header.key === "x-robots-tag",
  )
  if (matches.length > 1) {
    fail("Politica X-Robots-Tag ambigua na regra " + routeIndex + ".")
  }
  return matches[0]
}

function isGlobalRoute(route) {
  return (
    route.source === GLOBAL_HEADER_SOURCE &&
    route.regex === EXPECTED_GLOBAL_HEADER_REGEX &&
    !route.conditional
  )
}

function classifyRobotsRoute(route) {
  if (route.source === GLOBAL_HEADER_SOURCE) {
    if (!isGlobalRoute(route)) {
      fail("Regra global possui source, regex ou conditions incoerentes.")
    }
    return "global"
  }

  const expectedRegex = EXPECTED_SCOPED_NOINDEX_ROUTES.get(route.source)
  if (expectedRegex === undefined) {
    fail("Regra X-Robots-Tag escopada nao aprovada: " + route.source + ".")
  }
  if (route.conditional || route.regex !== expectedRegex) {
    fail("Regra X-Robots-Tag aprovada possui regex ou conditions incoerentes.")
  }

  return "scoped"
}

function parseDirective(rawDirective) {
  const normalized = rawDirective.trim().toLowerCase()
  if (!normalized) {
    fail("X-Robots-Tag contem diretiva vazia.")
  }

  const separatorIndex = normalized.indexOf(":")
  const name = (
    separatorIndex === -1 ? normalized : normalized.slice(0, separatorIndex)
  ).trim()
  const value =
    separatorIndex === -1
      ? undefined
      : normalized.slice(separatorIndex + 1).trim().replace(/\s+/g, " ")

  if (!/^[a-z][a-z0-9_-]*$/.test(name)) {
    fail("X-Robots-Tag contem diretiva com nome invalido.")
  }

  return { name, value }
}

function validateDirective(directive) {
  if (FLAG_DIRECTIVES.has(directive.name)) {
    if (directive.value !== undefined) {
      fail("Diretiva " + directive.name + " possui valor inesperado.")
    }
    return
  }

  if (!VALUE_DIRECTIVES.has(directive.name) || !directive.value) {
    fail("Diretiva desconhecida ou escopo nao suportado: " + directive.name + ".")
  }

  if (
    directive.name === "max-image-preview" &&
    !new Set(["none", "standard", "large"]).has(directive.value)
  ) {
    fail("Diretiva max-image-preview possui valor invalido.")
  }
  if (
    ["max-snippet", "max-video-preview"].includes(directive.name) &&
    (!/^-?\d+$/.test(directive.value) || Number(directive.value) < -1)
  ) {
    fail("Diretiva " + directive.name + " possui valor invalido.")
  }
}

function parseRobotsHeader(value) {
  const directives = value.split(",").map(parseDirective)
  directives.forEach(validateDirective)
  return directives
}

function directiveBlocksIndexing(directive) {
  return directive.name === "noindex" || directive.name === "none"
}

function directiveLabel(directive) {
  return directive.value === undefined
    ? directive.name
    : directive.name + ":" + directive.value
}

function validateBlockedDirectives(directives, label) {
  const names = new Set(directives.map((directive) => directive.name))
  if (names.has("none")) {
    names.add("noindex")
    names.add("nofollow")
  }

  const missing = REQUIRED_BLOCKED_DIRECTIVES.filter(
    (directive) => !names.has(directive),
  )
  if (missing.length > 0) {
    fail(label + " incompleto; faltam " + missing.join(", ") + ".")
  }

  const incompatible = directives
    .filter((directive) =>
      INCOMPATIBLE_BLOCKED_DIRECTIVES.has(directive.name),
    )
    .map(directiveLabel)
  if (incompatible.length > 0) {
    fail(label + " contem diretivas incompativeis: " + incompatible.join(", ") + ".")
  }

  return directives
    .filter(
      (directive) =>
        !REQUIRED_BLOCKED_DIRECTIVES.includes(directive.name),
    )
    .map(directiveLabel)
}

function validatePublicGlobalDirectives(directives) {
  if (directives.some(directiveBlocksIndexing)) {
    fail("Artefato public contem bloqueio global de indexacao.")
  }
  return directives.map(directiveLabel)
}

export function validateArtifactManifest(manifest, expectedMode) {
  parseExpectedMode([expectedMode])
  const routes = validateRelevantManifestSchema(manifest)

  let globalDirectives = null
  let globalRobotsRouteIndex = -1
  const scopedSources = new Set()
  const scopedRobotsRouteIndexes = []
  const additionalDirectives = []

  routes.forEach((route, routeIndex) => {
    const header = robotsHeader(route, routeIndex)
    if (!header) return

    const scope = classifyRobotsRoute(route)
    const directives = parseRobotsHeader(header.value)
    if (scope === "global") {
      if (globalDirectives !== null) {
        fail("Politica global X-Robots-Tag ambigua.")
      }
      globalDirectives = directives
      globalRobotsRouteIndex = routeIndex
      return
    }

    if (scopedSources.has(route.source)) {
      fail("Regra X-Robots-Tag escopada duplicada: " + route.source + ".")
    }
    scopedSources.add(route.source)
    scopedRobotsRouteIndexes.push(routeIndex)
    additionalDirectives.push(
      ...validateBlockedDirectives(
        directives,
        "Bloqueio escopado de " + route.source,
      ),
    )
  })

  if (expectedMode === "blocked") {
    if (globalDirectives === null) {
      fail("Artefato blocked nao contem X-Robots-Tag global.")
    }
    additionalDirectives.push(
      ...validateBlockedDirectives(globalDirectives, "Bloqueio global"),
    )
  } else {
    if (globalDirectives !== null) {
      additionalDirectives.push(
        ...validatePublicGlobalDirectives(globalDirectives),
      )
      if (
        scopedRobotsRouteIndexes.some(
          (routeIndex) => routeIndex < globalRobotsRouteIndex,
        )
      ) {
        fail(
          "Regra global X-Robots-Tag posterior pode sobrescrever bloqueio escopado.",
        )
      }
    }

    const missingScopedSources = [...EXPECTED_SCOPED_NOINDEX_ROUTES.keys()].filter(
      (source) => !scopedSources.has(source),
    )
    if (
      scopedSources.size !== EXPECTED_SCOPED_NOINDEX_ROUTES.size ||
      missingScopedSources.length > 0
    ) {
      fail("Conjunto de regras X-Robots-Tag escopadas diverge da allowlist aprovada.")
    }
  }

  return {
    expectedMode,
    globalRuleCount: globalDirectives === null ? 0 : 1,
    scopedRobotsRuleCount: scopedSources.size,
    additionalDirectives: [...new Set(additionalDirectives)],
  }
}

async function main() {
  const expectedMode = parseExpectedMode(process.argv.slice(2))
  await validateNextVersion()
  const result = validateArtifactManifest(await loadManifest(), expectedMode)

  console.log("SEARCH_INDEXING_ARTIFACT_MODE=" + result.expectedMode)
  console.log(
    "SEARCH_INDEXING_ARTIFACT_GLOBAL_RULES=" + result.globalRuleCount,
  )
  console.log(
    "SEARCH_INDEXING_ARTIFACT_SCOPED_ROBOTS_RULES=" +
      result.scopedRobotsRuleCount,
  )
  if (result.additionalDirectives.length > 0) {
    console.log(
      "SEARCH_INDEXING_ARTIFACT_ADDITIONAL_DIRECTIVES=" +
        result.additionalDirectives.join(","),
    )
  }
  console.log("SEARCH_INDEXING_ARTIFACT_RESULT=OK")
}

const isDirectExecution =
  typeof process.argv[1] === "string" &&
  import.meta.url === pathToFileURL(process.argv[1]).href

if (isDirectExecution) {
  try {
    await main()
  } catch (error) {
    const message =
      error instanceof ArtifactValidationError
        ? error.message
        : "Falha inesperada ao validar o artefato."
    console.error("SEARCH_INDEXING_ARTIFACT_RESULT=ERROR: " + message)
    process.exitCode = 1
  }
}
