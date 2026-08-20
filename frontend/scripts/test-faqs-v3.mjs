import fs from 'node:fs'
import path from 'node:path'

const root = process.cwd()

function read(relative) {
  return fs.readFileSync(path.join(root, relative), 'utf8')
}

function requireText(source, expected, label) {
  if (!source.includes(expected)) {
    throw new Error(`${label}: ausente ${expected}`)
  }
}

function rejectText(source, forbidden, label) {
  if (source.includes(forbidden)) {
    throw new Error(`${label}: encontrado ${forbidden}`)
  }
}

const publicPage = read('src/app/(public-routes)/faq/page.tsx')
const publicLayout = read('src/app/(public-routes)/layout.tsx')
const homePage = read('src/app/(public-routes)/page.tsx')
const footer = read('src/components/layout/footer.tsx')
const adminPage = read('src/app/(painel-admin)/admin/faqs/page.tsx')
const publicApi = read('src/lib/faq-public-api.ts')
const adminApi = read('src/lib/admin-faq-api.ts')

rejectText(publicPage, "'use client'", 'FAQ publica deve ser SSR')
rejectText(publicPage, "export const dynamic = 'force-dynamic'", 'FAQ nao deve forcar no-store')
requireText(publicLayout, 'export const revalidate = 0', 'layout publico renderizado por requisicao')
rejectText(publicLayout, 'export const dynamic', 'layout publico nao deve forcar no-store')
rejectText(publicLayout, 'export const fetchCache', 'layout publico nao deve forcar fetch cache')
requireText(publicPage, '<details', 'conteudo legivel sem JavaScript')
requireText(publicPage, "'@type': 'FAQPage'", 'JSON-LD FAQPage')
requireText(publicPage, 'visiveis.map', 'JSON-LD corresponde ao conteudo visivel')
requireText(publicPage, 'canonical: buildPublicUrl', 'canonical preservada')
requireText(publicApi, "import 'server-only'", 'FAQ publica isolada no servidor')
requireText(publicApi, 'PUBLIC_FAQ_REVALIDATE_SECONDS = 3_600', 'TTL explicito da FAQ')
requireText(publicApi, "mode: 'revalidate'", 'cache seletivo da FAQ')
requireText(publicApi, 'tags: [PUBLIC_FAQ_CACHE_TAG]', 'tag de invalidacao da FAQ')
rejectText(publicApi, "cache: 'no-store'", 'FAQ estavel nao deve ignorar o cache seletivo')
requireText(publicApi, "'/faqs'", 'contrato publico')
rejectText(publicPage, 'Pergunta e resposta não carregadas', 'placeholder removido')
rejectText(publicPage, 'CATEGORIES', 'FAQ hardcoded removida')

requireText(homePage, 'listarFaqsPublicadas', 'Home usa fonte canonica das FAQs')
requireText(homePage, '<HomeFaqSection faqs={faqs} />', 'secao de FAQ presente na Home')
requireText(homePage, 'if (faqs.length === 0) return null', 'estado vazio seguro na Home')
requireText(homePage, '<details', 'perguntas e respostas SSR na Home')
requireText(homePage, 'faqs.map', 'conteudo visivel derivado da fonte canonica')
requireText(homePage, '"@type": "FAQPage"', 'JSON-LD FAQPage na Home')
requireText(homePage, 'mainEntity: faqs.map', 'JSON-LD corresponde ao conteudo visivel da Home')
requireText(homePage, 'min-w-0', 'accordion responsivo sem overflow')
rejectText(homePage, 'const faqItems', 'FAQ paralela hardcoded ausente na Home')

requireText(footer, 'Perguntas Frequentes', 'link explicito da FAQ no rodape')
requireText(footer, 'href="/faq"', 'destino canonico da FAQ no rodape')
if (footer.indexOf('Perguntas Frequentes') < footer.indexOf('Blog')) {
  throw new Error('link da FAQ deve aparecer abaixo de Blog no rodape')
}

for (const action of [
  'Nova FAQ',
  'Editar FAQ',
  'Publicar',
  'Retirar',
  'Arquivar FAQ',
  'Mover para cima',
  'Mover para baixo',
]) {
  requireText(adminPage, action, `acao administrativa ${action}`)
}

requireText(adminApi, '/faqs', 'contrato administrativo')
requireText(adminApi, 'getAdminMutationHeaders', 'CSRF administrativo')
requireText(adminPage, 'disabled={Boolean(operando)', 'protecao contra duplo clique')
requireText(adminPage, 'lg:grid-cols', 'layout desktop')
requireText(adminPage, 'flex-wrap', 'layout mobile sem overflow')

console.log('FAQ V3: contrato admin/publico, SSR, SEO e estados aprovados.')
