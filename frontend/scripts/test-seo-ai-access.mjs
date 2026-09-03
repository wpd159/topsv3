import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import ts from "typescript"

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), "utf8")
}

const ageGateRoutePolicySource = source("src/lib/compliance/age-gate-route-policy.ts")

function loadNationalSeoModule() {
  const { outputText } = ts.transpileModule(
    source("src/lib/seo/acompanhantes-national-seo.ts"),
    {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2022,
      },
    },
  )
  const module = { exports: {} }
  const require = (specifier) => {
    if (specifier === "@/lib/seo/public-url") {
      return {
        buildPublicUrl(path) {
          return new URL(path, "https://topsdojob.com/").toString()
        },
      }
    }
    throw new Error(`Unexpected import: ${specifier}`)
  }
  new Function("module", "exports", "require", outputText)(module, module.exports, require)
  return module.exports
}

function loadAgeGateRoutePolicyModule() {
  const { outputText } = ts.transpileModule(
    ageGateRoutePolicySource,
    {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2022,
      },
    },
  )
  const module = { exports: {} }
  new Function("module", "exports", outputText)(module, module.exports)
  return module.exports
}

const nationalSeo = loadNationalSeoModule()
const ageGateRoutePolicy = loadAgeGateRoutePolicyModule()
const descoberta = {
  estados: [
    {
      uf: "GO",
      nome: "Goiás",
      totalAnunciosAtivos: 7,
      cidades: [
        {
          nome: "Goiânia",
          slug: "goiania",
          totalAnunciosAtivos: 7,
          bairros: [
            { nome: "Centro", slug: "centro", totalAnunciosAtivos: 4 },
            { nome: "Setor Sul", slug: "setor-sul", totalAnunciosAtivos: 3 },
          ],
        },
      ],
    },
    {
      uf: "SP",
      nome: "São Paulo",
      totalAnunciosAtivos: 5,
      cidades: [
        {
          nome: "São Paulo",
          slug: "sao-paulo",
          totalAnunciosAtivos: 5,
          bairros: [{ nome: "Centro", slug: "centro", totalAnunciosAtivos: 5 }],
        },
      ],
    },
  ],
}

assert.deepEqual(
  nationalSeo.buildAcompanhantesNationalCoverage(descoberta, { totalItens: 12 }),
  { estados: 2, cidades: 2, bairros: 3, anuncios: 12 },
)
assert.throws(
  () => nationalSeo.buildAcompanhantesNationalCoverage(descoberta, { totalItens: -1 }),
  /Contagem pública inválida/,
)
assert.throws(
  () =>
    nationalSeo.buildAcompanhantesNationalCoverage(descoberta, {
      totalItens: Number.NaN,
    }),
  /Contagem pública inválida/,
)

const cidadesVisiveis = [
  {
    estadoUf: "GO",
    cidadeNome: "Goiânia",
    cidadeSlug: "goiania",
    totalAnunciosAtivos: 7,
  },
]
const schema = nationalSeo.buildAcompanhantesNationalStructuredData(cidadesVisiveis)
const schemaTypes = schema["@graph"].map((entry) => entry["@type"])
assert.deepEqual(schemaTypes, [
  "CollectionPage",
  "BreadcrumbList",
  "ItemList",
  "FAQPage",
])
assert.equal(schema["@graph"][2].numberOfItems, 1)
assert.equal(
  schema["@graph"][2].itemListElement[0].name,
  "Acompanhantes em Goiânia - GO",
)
assert.equal(
  schema["@graph"][2].itemListElement[0].url,
  "https://topsdojob.com/acompanhantes/go/goiania",
)
assert.equal(schema["@graph"][3].mainEntity.length, 6)
assert.ok(!JSON.stringify(schema).includes("WebSite"))
assert.ok(!JSON.stringify(schema).includes("LocalBusiness"))

const emptySchema = nationalSeo.buildAcompanhantesNationalStructuredData([])
assert.ok(!emptySchema["@graph"].some((entry) => entry["@type"] === "ItemList"))

const middlewareSource = source("src/middleware.ts")
const analyticsSource = source("src/lib/analytics/ga4.ts")
const publicLayoutSource = source("src/app/(public-routes)/layout.tsx")
const ageGateSource = source("src/components/modals/age-gate-modal.tsx")
const detailSource = source("src/app/(public-routes)/anuncios/[slug]/page.tsx")

for (const pathname of [
  "/acesso-negado",
  "/aviso-seguranca-whatsapp",
  "/consentimento-promocional",
  "/contato",
  "/cookies",
  "/faq",
  "/politica-de-privacidade",
  "/registrar",
  "/sobre",
  "/termos-de-uso",
  "/blog",
  "/blog/golpe-falsa-faccao-sites-acompanhantes",
  "/blog/categoria/seguranca",
  "/blog/cidade/tema/goiania",
  "/politicas",
  "/politicas/verificacao-etaria",
]) {
  assert.equal(
    ageGateRoutePolicy.isAgeGateExemptPath(pathname),
    true,
    `${pathname} deve ficar sem age gate`,
  )
}

for (const pathname of [
  undefined,
  null,
  "",
  "/",
  "/anuncios",
  "/anuncios/perfil",
  "/acompanhantes",
  "/acompanhantes/go/goiania",
  "/checkout/creditos/plano",
  "/planos-e-creditos",
  "/blogue",
  "/politicas-publicas",
  "/sobre/equipe",
]) {
  assert.equal(
    ageGateRoutePolicy.isAgeGateExemptPath(pathname),
    false,
    `${pathname} deve continuar com age gate`,
  )
}

assert.match(analyticsSource, /params\.get\(["']utm_source["']\)/)
assert.doesNotMatch(middlewareSource, /utm_source|utm_/i)
assert.doesNotMatch(
  `${middlewareSource}\n${publicLayoutSource}\n${ageGateRoutePolicySource}\n${ageGateSource}\n${detailSource}`,
  /Googlebot|Google-Extended|OAI-SearchBot|GPTBot|ChatGPT-User|Applebot|bingbot/i,
)
assert.match(publicLayoutSource, /<AgeGateModal/)
assert.match(publicLayoutSource, /<PublicChrome>\{children\}<\/PublicChrome>/)
assert.doesNotMatch(publicLayoutSource, /if\s*\([^)]*AgeGate/)
const ageGateExemptionCheckIndex = ageGateSource.indexOf("if (ageGateExempt)")
const ageGateStatusRequestIndex = ageGateSource.indexOf("void obterStatusVisitante(true)")
assert.ok(
  ageGateExemptionCheckIndex >= 0 &&
    ageGateStatusRequestIndex >= 0 &&
    ageGateExemptionCheckIndex < ageGateStatusRequestIndex,
  "a excecao editorial deve ser decidida antes da consulta do age gate",
)
assert.match(ageGateSource, /open=\{!ageGateExempt && open\}/)
assert.match(detailSource, /obterAnuncioPublicoPorSlug/)
assert.match(detailSource, /application\/ld\+json/)

console.log("SEO_AI_ACCESS_RESULT=OK")
