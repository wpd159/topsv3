import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const paths = {
  library: new URL('../src/lib/site-content.ts', import.meta.url),
  page: new URL('../src/components/site-content/site-content-page.tsx', import.meta.url),
  renderer: new URL('../src/components/site-content/safe-site-content-body.tsx', import.meta.url),
  publicLayout: new URL('../src/app/(public-routes)/layout.tsx', import.meta.url),
  footer: new URL('../src/components/layout/footer.tsx', import.meta.url),
  ageGate: new URL('../src/components/modals/age-gate-modal.tsx', import.meta.url),
  whatsapp: new URL('../src/components/site/whatsapp-safety-provider.tsx', import.meta.url),
  cookies: new URL('../src/app/(public-routes)/cookies/page.tsx', import.meta.url),
  cookiePreferences: new URL(
    '../src/app/(public-routes)/cookies/cookie-preferences.tsx',
    import.meta.url,
  ),
  adminPage: new URL(
    '../src/app/(painel-admin)/admin/termos-footer/page.tsx',
    import.meta.url,
  ),
  adminAction: new URL(
    '../src/app/(painel-admin)/admin/termos-footer/actions.ts',
    import.meta.url,
  ),
}

const entries = await Promise.all(
  Object.entries(paths).map(async ([key, path]) => [key, await readFile(path, 'utf8')]),
)
const source = Object.fromEntries(entries)

assert.match(
  source.library,
  /PUBLIC_SITE_CONTENT_CACHE_TAG\s*=\s*['"]public-site-content['"]/,
  'o conteudo deve usar uma tag de cache exclusiva',
)
assert.match(
  source.library,
  /revalidate:\s*PUBLIC_SITE_CONTENT_REVALIDATE_SECONDS/,
  'o fetch SSR deve ter revalidacao temporal',
)
assert.match(
  source.library,
  /publicApiUrl\(['"]\/conteudos-site['"]\)/,
  'o frontend deve usar o contrato publico canonico',
)
assert.doesNotMatch(source.page, /['"]use client['"]/)
assert.doesNotMatch(source.page, /useEffect|useState/)
assert.match(source.page, /await resolvePublicSiteContent\(contentKey\)/)
assert.match(source.page, /SafeSiteContentBody/)

assert.doesNotMatch(source.renderer, /dangerouslySetInnerHTML/)
assert.match(source.renderer, /parsed\.protocol === 'http:' \|\| parsed\.protocol === 'https:'/)
assert.match(source.renderer, /href\.startsWith\('\/'\)/)
assert.doesNotMatch(source.renderer, /iframe|<script/)

assert.match(source.publicLayout, /await resolveAllPublicSiteContent\(\)/)
assert.match(source.publicLayout, /<SiteContentProvider entries=\{siteContent\}>/)
assert.match(source.publicLayout, /<WhatsAppSafetyProvider content=\{whatsappContent\}>/)
for (const clientSource of [source.footer, source.ageGate, source.whatsapp]) {
  assert.doesNotMatch(clientSource, /fetchAllPublicSiteContent|fetchPublicSiteContent/)
}
assert.match(source.footer, /useSiteContent\('footer-resumo-institucional'\)/)
assert.match(source.ageGate, /useSiteContent\('popup-login'\)/)
assert.match(source.whatsapp, /content = getUnavailableSiteContent\('texto-whatsapp'\)/)

assert.doesNotMatch(source.cookies, /['"]use client['"]/)
assert.match(source.cookies, /await resolvePublicSiteContent\('politica-cookies'\)/)
assert.doesNotMatch(source.cookiePreferences, /fetchPublicSiteContent|politica-cookies/)
assert.doesNotMatch(
  source.cookiePreferences,
  /cookieCatalog|Lista de cookies|Resumo rapido/,
  'o documento e o inventario juridico de cookies devem vir do painel',
)

assert.match(source.adminPage, /await salvarConteudoSiteAdmin\(/)
assert.match(source.adminPage, /await revalidarCacheConteudoSite\(\)/)
assert.match(
  source.adminAction,
  /revalidateTag\(PUBLIC_SITE_CONTENT_CACHE_TAG\)/,
  'a publicacao administrativa deve invalidar o cache',
)

const removedHardcodes = [
  'Atualize os termos de uso no painel administrativo.',
  'Atualize a politica de privacidade no painel administrativo.',
  'DEFAULT_LEGAL_NOTICE',
  'DEFAULT_FOOTER_COPY',
  'FALLBACK_POLICIES',
]
const allRelevantSource = Object.values(source).join('\n')
for (const hardcode of removedHardcodes) {
  assert.equal(
    allRelevantSource.includes(hardcode),
    false,
    `o hardcode juridico ${hardcode} deve ter sido removido`,
  )
}

console.log('OK_SITE_CONTENT_SSR')
