import assert from "node:assert/strict"
import { pathToFileURL } from "node:url"

import {
  EXPECTED_SCOPED_NOINDEX_ROUTES,
  parseExpectedMode,
  parseManifestText,
  validateArtifactManifest,
} from "./test-search-indexing-artifact.mjs"

const GLOBAL_SOURCE = "/:path*"
const GLOBAL_REGEX = "^(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$"
const BLOCKED_DIRECTIVES = "noindex, nofollow, noarchive"

const APPROVED_SCOPED_ROUTES = [
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
]

const CRITICAL_PUBLIC_ROUTES = [
  { path: "/", source: "/", regex: "^/(?:/)?$" },
  {
    path: "/anuncios",
    source: "/anuncios",
    regex: "^/anuncios(?:/)?$",
  },
  {
    path: "/anuncios/anuncio-teste",
    source: "/anuncios/anuncio-teste",
    regex: "^/anuncios/anuncio-teste(?:/)?$",
  },
  {
    path: "/acompanhantes",
    source: "/acompanhantes",
    regex: "^/acompanhantes(?:/)?$",
  },
  {
    path: "/acompanhantes/go/goiania",
    source: "/acompanhantes/go/goiania",
    regex: "^/acompanhantes/go/goiania(?:/)?$",
  },
  {
    path: "/acompanhantes/go/goiania/setor-bueno",
    source: "/acompanhantes/go/goiania/setor-bueno",
    regex: "^/acompanhantes/go/goiania/setor-bueno(?:/)?$",
  },
  { path: "/blog", source: "/blog", regex: "^/blog(?:/)?$" },
  {
    path: "/blog/guia-seguro",
    source: "/blog/guia-seguro",
    regex: "^/blog/guia-seguro(?:/)?$",
  },
  {
    path: "/contato",
    source: "/contato",
    regex: "^/contato(?:/)?$",
  },
  {
    path: "/politica-de-privacidade",
    source: "/politica-de-privacidade",
    regex: "^/politica-de-privacidade(?:/)?$",
  },
]

const PUBLIC_WILDCARD_ROUTES = [
  {
    source: "/acompanhantes/:path*",
    regex:
      "^/acompanhantes(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  },
  {
    source: "/blog/:path*",
    regex: "^/blog(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
  },
]

function robotsRoute(source, regex, value = BLOCKED_DIRECTIVES, key = "X-Robots-Tag") {
  return {
    source,
    regex,
    headers: [{ key, value }],
  }
}

function makeManifest({
  globalRobots = null,
  globalHeaderKey = "X-Robots-Tag",
  scopedRobots = BLOCKED_DIRECTIVES,
  scopedHeaderKey = "X-Robots-Tag",
} = {}) {
  const globalHeaders =
    globalRobots === null
      ? [{ key: "X-Content-Type-Options", value: "nosniff" }]
      : [{ key: globalHeaderKey, value: globalRobots }]

  return {
    version: 3,
    basePath: "",
    caseSensitive: false,
    headers: [
      { source: GLOBAL_SOURCE, regex: GLOBAL_REGEX, headers: globalHeaders },
      ...APPROVED_SCOPED_ROUTES.map(([source, regex]) =>
        robotsRoute(source, regex, scopedRobots, scopedHeaderKey),
      ),
    ],
  }
}

function addPublicRobotsRule(manifest, route, value = "noindex") {
  manifest.headers.push(robotsRoute(route.source, route.regex, value))
  return manifest
}

let testCount = 0

function test(name, callback) {
  try {
    callback()
    testCount += 1
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    throw new Error(name + ": " + message, { cause: error })
  }
}

function expectFailure(name, callback, expectedMessage) {
  test(name, () => {
    assert.throws(callback, expectedMessage)
  })
}

function runSelfTest() {
  expectFailure(
    "argumento ausente",
    () => parseExpectedMode([]),
    /Informe exatamente um modo/,
  )
  expectFailure(
    "argumento desconhecido",
    () => parseExpectedMode(["staging"]),
    /Modo invalido/,
  )
  expectFailure(
    "argumentos excedentes",
    () => parseExpectedMode(["blocked", "public"]),
    /Informe exatamente um modo/,
  )
  test("argumentos validos", () => {
    assert.equal(parseExpectedMode(["blocked"]), "blocked")
    assert.equal(parseExpectedMode(["public"]), "public")
  })

  expectFailure(
    "JSON invalido",
    () => parseManifestText('{"version":'),
    /JSON invalido/,
  )
  expectFailure(
    "schema invalido",
    () => validateArtifactManifest({ version: 2 }, "public"),
    /Schema minimo/,
  )

  test("allowlist source para regex independente e exata", () => {
    assert.equal(
      EXPECTED_SCOPED_NOINDEX_ROUTES.size,
      APPROVED_SCOPED_ROUTES.length,
    )
    for (const [source, regex] of APPROVED_SCOPED_ROUTES) {
      assert.equal(EXPECTED_SCOPED_NOINDEX_ROUTES.get(source), regex)
    }
    for (const route of CRITICAL_PUBLIC_ROUTES) {
      for (const [, regex] of APPROVED_SCOPED_ROUTES) {
        assert.equal(
          new RegExp(regex).test(route.path),
          false,
          regex + " must not match public route " + route.path,
        )
      }
    }
  })

  test("blocked contra blocked passa", () => {
    const result = validateArtifactManifest(
      makeManifest({ globalRobots: BLOCKED_DIRECTIVES }),
      "blocked",
    )
    assert.equal(result.expectedMode, "blocked")
  })
  expectFailure(
    "blocked contra public falha",
    () =>
      validateArtifactManifest(
        makeManifest({ globalRobots: BLOCKED_DIRECTIVES }),
        "public",
      ),
    /public contem bloqueio global/,
  )
  test("public contra public passa", () => {
    const result = validateArtifactManifest(makeManifest(), "public")
    assert.equal(result.expectedMode, "public")
    assert.equal(result.scopedRobotsRuleCount, APPROVED_SCOPED_ROUTES.length)
  })
  expectFailure(
    "public contra blocked falha",
    () => validateArtifactManifest(makeManifest(), "blocked"),
    /blocked nao contem X-Robots-Tag global/,
  )

  for (const route of CRITICAL_PUBLIC_ROUTES) {
    expectFailure(
      "noindex individual na rota publica critica " + route.path + " falha",
      () =>
        validateArtifactManifest(
          addPublicRobotsRule(makeManifest(), route),
          "public",
        ),
      /escopada nao aprovada/,
    )
  }

  for (const route of PUBLIC_WILDCARD_ROUTES) {
    expectFailure(
      "noindex wildcard publico " + route.source + " falha",
      () =>
        validateArtifactManifest(
          addPublicRobotsRule(makeManifest(), route),
          "public",
        ),
      /escopada nao aprovada/,
    )
  }

  expectFailure(
    "none individual em rota publica falha",
    () =>
      validateArtifactManifest(
        addPublicRobotsRule(makeManifest(), CRITICAL_PUBLIC_ROUTES[0], "none"),
        "public",
      ),
    /escopada nao aprovada/,
  )

  test("rota privada aprovada passa no conjunto public", () => {
    const manifest = makeManifest()
    const approvedRoute = manifest.headers.find(
      (route) => route.source === "/admin/:path*",
    )
    assert.deepEqual(approvedRoute, {
      source: "/admin/:path*",
      regex: EXPECTED_SCOPED_NOINDEX_ROUTES.get("/admin/:path*"),
      headers: [{ key: "X-Robots-Tag", value: BLOCKED_DIRECTIVES }],
    })
    const result = validateArtifactManifest(manifest, "public")
    assert.equal(result.scopedRobotsRuleCount, APPROVED_SCOPED_ROUTES.length)
  })

  expectFailure(
    "rota desconhecida falha",
    () =>
      validateArtifactManifest(
        addPublicRobotsRule(makeManifest(), {
          source: "/area-desconhecida/:path*",
          regex:
            "^/area-desconhecida(?:/((?:[^/]+?)(?:/(?:[^/]+?))*))?(?:/)?$",
        }),
        "public",
      ),
    /escopada nao aprovada/,
  )

  expectFailure(
    "regex divergente em rota aprovada falha",
    () => {
      const manifest = makeManifest()
      const route = manifest.headers.find(
        (candidate) => candidate.source === "/admin/:path*",
      )
      route.regex = "^/admin(?:/)?$"
      validateArtifactManifest(manifest, "public")
    },
    /regex ou conditions incoerentes/,
  )

  test("global public sem noindex passa", () => {
    const result = validateArtifactManifest(
      makeManifest({ globalRobots: "index, follow, max-image-preview: large" }),
      "public",
    )
    assert.deepEqual(result.additionalDirectives, [
      "index",
      "follow",
      "max-image-preview:large",
    ])
  })
  expectFailure(
    "global public com noindex falha",
    () =>
      validateArtifactManifest(
        makeManifest({ globalRobots: "follow, noindex" }),
        "public",
      ),
    /public contem bloqueio global/,
  )
  expectFailure(
    "global public com none falha",
    () =>
      validateArtifactManifest(
        makeManifest({ globalRobots: "none, noarchive" }),
        "public",
      ),
    /public contem bloqueio global/,
  )

  expectFailure(
    "rota aprovada ausente falha",
    () => {
      const manifest = makeManifest()
      manifest.headers = manifest.headers.filter(
        (route) => route.source !== "/admin/:path*",
      )
      validateArtifactManifest(manifest, "public")
    },
    /allowlist aprovada/,
  )
  expectFailure(
    "rota aprovada duplicada falha",
    () => {
      const manifest = makeManifest()
      const route = manifest.headers.find(
        (candidate) => candidate.source === "/admin/:path*",
      )
      manifest.headers.push(structuredClone(route))
      validateArtifactManifest(manifest, "public")
    },
    /escopada duplicada/,
  )
  test("public sem regra global de headers passa", () => {
    const manifest = makeManifest()
    manifest.headers = manifest.headers.filter(
      (route) => route.source !== GLOBAL_SOURCE,
    )
    const result = validateArtifactManifest(manifest, "public")
    assert.equal(result.globalRuleCount, 0)
  })
  expectFailure(
    "X-Robots-Tag global duplicado falha",
    () => {
      const manifest = makeManifest({ globalRobots: "index, follow" })
      manifest.headers.push(structuredClone(manifest.headers[0]))
      validateArtifactManifest(manifest, "public")
    },
    /global X-Robots-Tag ambigua/,
  )
  expectFailure(
    "X-Robots-Tag global posterior nao sobrescreve bloqueio privado",
    () => {
      const manifest = makeManifest({ globalRobots: "index, follow" })
      const globalRoute = manifest.headers.shift()
      manifest.headers.push(globalRoute)
      validateArtifactManifest(manifest, "public")
    },
    /posterior pode sobrescrever bloqueio escopado/,
  )

  test("caixa ordem e espacamento compativeis passam", () => {
    const result = validateArtifactManifest(
      makeManifest({
        globalRobots: "  NOARCHIVE , NoFollow,  NoIndex  ",
        globalHeaderKey: "x-RoBoTs-TaG",
        scopedRobots: " nofollow,  NOARCHIVE , NOINDEX ",
        scopedHeaderKey: "X-ROBOTS-TAG",
      }),
      "blocked",
    )
    assert.equal(result.scopedRobotsRuleCount, APPROVED_SCOPED_ROUTES.length)
  })

  test("none semantico permanece bloqueado e e informado", () => {
    const result = validateArtifactManifest(
      makeManifest({
        globalRobots: "none, noarchive",
        scopedRobots: "none, noarchive",
      }),
      "blocked",
    )
    assert.deepEqual(result.additionalDirectives, ["none"])
  })

  expectFailure(
    "override escopado fraco em blocked falha",
    () => {
      const manifest = makeManifest({ globalRobots: BLOCKED_DIRECTIVES })
      const route = manifest.headers.find(
        (candidate) => candidate.source === "/admin/:path*",
      )
      route.headers[0].value = "noindex, noarchive"
      validateArtifactManifest(manifest, "blocked")
    },
    /incompleto; faltam nofollow/,
  )

  for (const conditionName of ["has", "missing"]) {
    expectFailure(
      "condition " + conditionName + " em X-Robots aprovado falha",
      () => {
        const manifest = makeManifest()
        const route = manifest.headers.find(
          (candidate) => candidate.source === "/admin/:path*",
        )
        route[conditionName] = [
          { type: "header", key: "x-preview-mode", value: "1" },
        ]
        validateArtifactManifest(manifest, "public")
      },
      /regex ou conditions incoerentes/,
    )
  }
}

function main() {
  try {
    runSelfTest()
    console.log("SEARCH_INDEXING_ARTIFACT_SELFTEST_RESULT=OK tests=" + testCount)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    console.error("SEARCH_INDEXING_ARTIFACT_SELFTEST_RESULT=ERROR: " + message)
    process.exitCode = 1
  }
}

const isDirectExecution =
  typeof process.argv[1] === "string" &&
  import.meta.url === pathToFileURL(process.argv[1]).href

if (isDirectExecution) {
  main()
}
