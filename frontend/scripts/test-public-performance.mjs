import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), "utf8")
}

const hero = source("src/components/layout/hero.tsx")
const publicHeader = source("src/components/layout/header.tsx")
const authenticatedHeader = source("src/components/layout/header-logado.tsx")
const headerSkeleton = source("src/components/layout/header-skeleton.tsx")
const footer = source("src/components/layout/footer.tsx")
const sensitiveImage = source("src/components/compliance/sensitive-image.tsx")
const card = source("src/components/anuncios/anuncio-card.tsx")
const grid = source("src/components/anuncios/anuncios-grid.tsx")
const paginatedListing = source("src/components/anuncios/listagem-publica-paginada.tsx")
const listingPage = source("src/app/(public-routes)/anuncios/page.tsx")
const listingPageClient = source("src/app/(public-routes)/anuncios/anuncios-page-client.tsx")
const statePage = source("src/app/(public-routes)/acompanhantes/[estado]/page.tsx")
const cityPage = source("src/app/(public-routes)/acompanhantes/[estado]/[cidade]/page.tsx")
const neighborhoodPage = source(
  "src/app/(public-routes)/acompanhantes/[estado]/[cidade]/[bairro]/page.tsx"
)
const gallery = source("src/app/(public-routes)/anuncios/[slug]/componentes/header-tabs.tsx")
const mainContent = source("src/app/(public-routes)/anuncios/[slug]/componentes/main-content.tsx")
const publicChrome = source("src/components/layout/public-chrome.tsx")
const home = source("src/app/(public-routes)/page.tsx")
const publicLayout = source("src/app/(public-routes)/layout.tsx")
const homeCategories = source("src/components/layout/categoria-section.tsx")
const categoryCard = source("src/components/layout/categoria-card.tsx")
const rootLayout = source("src/app/layout.tsx")
const nextConfig = source("next.config.ts")

assert.doesNotMatch(hero, /\bunoptimized\b/)
assert.match(hero, /\bpriority\b/)
assert.match(hero, /fetchPriority=['"]high['"]/)
for (const header of [publicHeader, authenticatedHeader]) {
  assert.doesNotMatch(header, /\bpriority\b/)
  assert.doesNotMatch(header, /fetchPriority=/)
  assert.match(header, /loading="lazy"/)
}
assert.match(headerSkeleton, /hidden lg:flex items-center gap-8/)
assert.match(headerSkeleton, /hidden lg:flex items-center gap-4/)
assert.match(headerSkeleton, /className="lg:hidden"/)
assert.doesNotMatch(headerSkeleton, /hidden md:flex/)
assert.match(footer, /loading="lazy"/)

assert.match(sensitiveImage, /midia\.visibilidadeMidia === "LIVRE"/)
assert.match(sensitiveImage, /midia\.autorizada/)
assert.match(sensitiveImage, /unoptimized=\{!otimizarImagemPublica \|\| imagemPublicaR2\(fonte\)\}/)
assert.match(sensitiveImage, /\^https\?:/)

assert.match(card, /mediaPriority\?: boolean/)
assert.match(card, /priority=\{mediaPriority\}/)
assert.match(card, /<EmptyMediaState priority=\{mediaPriority\}/)
for (const listing of [grid, paginatedListing]) assert.match(listing, /mediaPriority=\{index === 0\}/)
for (const page of [statePage, cityPage, neighborhoodPage]) {
  assert.match(page, /<ListagemPublicaPaginada/)
}
assert.match(listingPage, /await listarAnunciosPublicos\(/)
assert.match(listingPage, /initialData=\{initialData\}/)
assert.match(listingPageClient, /initialRequest=\{initialRequest\}/)
assert.match(grid, /initialRequestConsumedRef/)

assert.match(gallery, /sizes: '\(max-width: 640px\) 80px, 96px'/)
assert.match(gallery, /sizes: '\(max-width: 768px\) 90vw/)

assert.match(mainContent, /new IntersectionObserver/)
assert.match(mainContent, /rootMargin: '240px 0px'/)
assert.match(mainContent, /mapaCarregado \? \(/)
assert.match(mainContent, /title=\{`Mapa de \$\{localizacaoLabel\}`\}/)
assert.match(mainContent, /data-public-map/)

assert.match(publicChrome, /min-h-\[calc\(100svh-89px\)\]/)
assert.match(publicChrome, /md:min-h-\[calc\(100svh-129px\)\]/)

assert.doesNotMatch(home, /export const dynamic/)
assert.doesNotMatch(home, /export const revalidate/)
assert.match(publicLayout, /export const revalidate\s*=\s*0/)
assert.doesNotMatch(publicLayout, /export const dynamic/)
assert.doesNotMatch(home, /^[\s]*["']use client["']/m)
assert.doesNotMatch(home, /ssr\s*:\s*false/)
assert.match(home, /export const metadata/)
assert.match(home, /canonical:\s*buildPublicUrl\(["']\/["']\)/)
assert.match(rootLayout, /type="application\/ld\+json"/)
assert.match(rootLayout, /websiteSchema/)
assert.match(home, /await Promise\.all\(\[[\s\S]*buscarCidadesPopularesHome\(\)/)
assert.match(home, /descobrirLocalidadesPublicas\(\)/)
assert.match(home, /<CategoriasSection\s*\/>/)
assert.match(homeCategories, /await listarCategoriasHomePublicas\(\)/)
assert.match(homeCategories, /<CategoriaCard \{\.\.\.categoria\} \/>/)
assert.doesNotMatch(homeCategories, /<Link/)
assert.match(categoryCard, /import Link from "next\/link"/)
assert.match(categoryCard, /href=\{destino\}/)
assert.match(categoryCard, /aria-busy=\{pending\}/)
assert.match(categoryCard, /pendingRef\.current/)
assert.match(categoryCard, /setPending\(true\)/)
assert.match(categoryCard, /active:scale-\[0\.99\]/)
assert.match(categoryCard, /Abrindo\.\.\./)
assert.doesNotMatch(categoryCard, /router\.push|setTimeout|await /)
assert.doesNotMatch(home, /mock|fixture|fakeAnuncios|anunciosFake/i)
assert.match(nextConfig, /minimumCacheTTL:\s*3600/)

console.log("PUBLIC_PERFORMANCE_RESULT=OK")
