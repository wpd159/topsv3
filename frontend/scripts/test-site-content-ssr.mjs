import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

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
assert.match(source.renderer, /Política de Verificação Etária/)
assert.match(source.renderer, /\/politicas\/verificacao-etaria/)
assert.match(source.renderer, /Termos de Uso/)
assert.match(source.renderer, /\/termos-de-uso/)
assert.match(source.renderer, /parsed\.origin !== CANONICAL_ORIGIN/)
assert.match(source.renderer, /parsed\.search/)
assert.match(source.renderer, /parsed\.hash/)
assert.doesNotMatch(source.renderer, /iframe|<script/)
assert.match(source.page, /institutionalLinks=\{contentKey === 'quem-somos'\}/)

assert.match(source.publicLayout, /await resolveAllPublicSiteContent\(\)/)
assert.match(source.publicLayout, /<SiteContentProvider entries=\{siteContent\}>/)
assert.match(source.publicLayout, /<WhatsAppSafetyProvider content=\{whatsappContent\}>/)
for (const clientSource of [source.footer, source.ageGate, source.whatsapp]) {
  assert.doesNotMatch(clientSource, /fetchAllPublicSiteContent|fetchPublicSiteContent/)
}
assert.match(source.footer, /useSiteContent\('footer-resumo-institucional'\)/)
assert.match(source.ageGate, /useSiteContent\('popup-login'\)/)
assert.match(source.ageGate, /SafeInstitutionalText/)
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

// Render the affected copy with real React and the existing body renderer.
// Only providers/UI wrappers are synthetic; no browser, network or real account.
const require = createRequire(import.meta.url)
function compileComponent(text, mocks = {}) {
  const { outputText } = ts.transpileModule(text, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  })
  const module = { exports: {} }
  new Function('require', 'module', 'exports', outputText)(
    (id) => Object.hasOwn(mocks, id) ? mocks[id] : require(id), module, module.exports,
  )
  return module.exports
}
const contactSource = await readFile(new URL('../src/app/(public-routes)/contato/contato-page-client.tsx', import.meta.url), 'utf8')
for (const usuario of [null, { id: 'fixture-editorial' }]) {
  const Contact = compileComponent(contactSource, {
    '@/context/AuthContext': { useAuth: () => ({ usuario }) },
    '@/components/ui/button': { Button: (props) => React.createElement('button', props) },
    '@/components/modals/login-modal': { LoginModal: () => null },
  }).default
  const html = renderToStaticMarkup(React.createElement(Contact))
  assert.equal((html.match(/contato@topsdojob\.com\.br/g) ?? []).length, 1)
  assert.match(html, /Atendimento, privacidade, questões jurídicas, denúncias e segurança:/)
  assert.match(html, /entre na sua conta/)
  assert.match(html, /Meus tickets/)
  assert.match(html, /FALAR COM O SUPORTE/)
  assert.doesNotMatch(html, /(?:juridico|denuncia|conntato)@/)
}
assert.match(contactSource, /if \(usuario\)[\s\S]+open-suporte-ticket[\s\S]+setLoginOpen\(true\)/)

const prepared = JSON.parse(await readFile(new URL('../../docs/v3/conteudo-institucional-revisado.json', import.meta.url), 'utf8'))
const { SafeSiteContentBody } = compileComponent(source.renderer, {
  'next/link': { default: (props) => React.createElement('a', props) },
})
assert.equal(new Set(prepared.map((entry) => entry.contentKey)).size, 9)
for (const entry of prepared) {
  assert.ok(entry.titulo && entry.corpo && entry.expectedContentHash)
  assert.match(entry.expectedContentHash, /^[a-f0-9]{64}$/)
  assert.doesNotMatch(entry.corpo, /(?:juridico|denuncia|conntato)@topsdojob\.com\.br/i)
  const html = renderToStaticMarkup(React.createElement(SafeSiteContentBody, {
    content: entry.corpo, institutionalLinks: entry.contentKey === 'quem-somos',
  }))
  assert.match(html, /data-site-content-state="published"/)
  assert.doesNotMatch(html, /<script|<iframe|href="javascript:/i)
  for (const [, href] of html.matchAll(/href="([^"]+)"/g)) {
    assert.ok(href.startsWith('/') || /^https?:\/\//.test(href), `link seguro: ${href}`)
  }
  // Existing mailto Markdown is not clickable in this renderer; preserve its
  // restriction while ensuring the prepared destination uses the sole mailbox.
  for (const [, address] of entry.corpo.matchAll(/mailto:([^\s)]+)/g)) {
    assert.equal(address, 'contato@topsdojob.com.br')
  }
}
const warning = prepared.find((entry) => entry.contentKey === 'texto-whatsapp').corpo
assert.match(warning, /informe o endereço do anúncio, se disponível/)
assert.doesNotMatch(warning, /informe seu endereço/)
assert.ok(prepared.find((entry) => entry.contentKey === 'verificacao').corpo.includes('não comprova a titularidade'))
assert.ok(prepared.find((entry) => entry.contentKey === 'privacidade-conteudo-restrito').corpo.includes('imediata e irreversível'))
const escaped = renderToStaticMarkup(React.createElement(SafeSiteContentBody, { content: '<script>fixture</script> [x](javascript:alert)' }))
assert.doesNotMatch(escaped, /<script|href="javascript:/i)

console.log(`OK_SITE_CONTENT_SSR prepared=${prepared.length} contactContexts=2`)
