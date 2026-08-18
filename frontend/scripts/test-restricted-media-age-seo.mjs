import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), "utf8")
}

const media = source("src/lib/media/public-media.ts")
const sensitiveImage = source("src/components/compliance/sensitive-image.tsx")
const card = source("src/components/anuncios/anuncio-card.tsx")
const detail = source("src/app/(public-routes)/anuncios/[slug]/page.tsx")
const detailHeader = source(
  "src/app/(public-routes)/anuncios/[slug]/componentes/header-tabs.tsx",
)
const stories = source("src/components/stories/stories-bar.tsx")
const storyViewer = source("src/components/stories/story-viewer-dialog.tsx")
const storyTypes = source("src/components/stories/stories-types.ts")
const storyPolicy = source("src/components/stories/story-access-policy.js")
const publicLayout = source("src/app/(public-routes)/layout.tsx")
const homePage = source("src/app/(public-routes)/page.tsx")
const acompanhantesLayout = source("src/app/(public-routes)/acompanhantes/layout.tsx")
const anunciosLayout = source("src/app/(public-routes)/anuncios/layout.tsx")
const publicApi = source("src/lib/public-catalog-api.ts")

assert.match(media, /previewUrl\?: string \| null/)
assert.match(
  media,
  /return midia\.autorizada \? midia\.urlPublica \?\? null : midia\.previewUrl \?\? null/,
)
assert.doesNotMatch(media, /filter:\s*blur|blur\(/i)
assert.doesNotMatch(sensitiveImage, /filter:\s*blur|blur\(/i)
assert.match(sensitiveImage, /fonteEhPreviewPublica/)
assert.match(
  sensitiveImage,
  /unoptimized=\{!otimizarImagemPublica \|\| imagemPublicaR2\(fonte\)\}/,
)

assert.match(card, /const nomeComIdade = idade != null/)
assert.match(card, /Foto de perfil de \$\{nomeExibido\}/)
assert.doesNotMatch(card, /alt=\{[^}]*idade/i)
assert.match(detailHeader, /nomeComIdade/)
assert.doesNotMatch(detailHeader, /alt=\{[^}]*idade/i)
assert.match(publicApi, /idade\?: number \| null/)

assert.match(detail, /selecionarImagemPublicaSeo/)
assert.match(detail, /fontePublicaSegura\(capa\)/)
assert.match(detail, /primaryImageOfPage/)
assert.match(detail, /images: \[\{ url: imagemPublica/)

assert.match(stories, /src=\{first\.previewUrl\}/)
assert.match(stories, /width=\{64\}/)
assert.match(stories, /rotuloPublicoComIdade/)
assert.match(stories, /sanitizeStoryFeedItem/)
assert.match(storyViewer, /sanitizeStoryViewerItem/)
assert.match(storyViewer, /onLoad=\{markCurrentStoryVisible\}/)
assert.match(storyViewer, /onPlaying=\{markCurrentStoryVisible\}/)
assert.doesNotMatch(storyViewer, /backgroundImage=/)
assert.doesNotMatch(storyViewer, /if \(open && currentFeedItem\) onStoryCurrent/)
assert.match(storyPolicy, /previewState === 'AVAILABLE'/)
assert.match(storyPolicy, /viewerState === 'LIBERADO'/)
assert.match(storyTypes, /idade == null \? nome : `\$\{nome\}, \$\{idade\} anos`/)
assert.doesNotMatch(stories, /filter:\s*blur|blur\(/i)
assert.doesNotMatch(storyViewer, /filter:\s*blur|blur\(/i)

assert.doesNotMatch(
  publicLayout,
  /rating:\s*['"]adult['"]/,
  "institutional public routes must not inherit the adult rating",
)
for (const adultRouteSource of [homePage, acompanhantesLayout, anunciosLayout]) {
  const ratingMatches = adultRouteSource.match(/rating:\s*['"]adult['"]/g) ?? []
  assert.equal(ratingMatches.length, 1, "adult route tree must declare adult rating exactly once")
}

console.log("RESTRICTED_MEDIA_AGE_SEO_RESULT=OK")
