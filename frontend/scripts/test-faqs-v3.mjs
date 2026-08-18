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
const homePage = read('src/app/(public-routes)/page.tsx')
const adminPage = read('src/app/(painel-admin)/admin/faqs/page.tsx')
const publicApi = read('src/lib/faq-public-api.ts')
const adminApi = read('src/lib/admin-faq-api.ts')

rejectText(publicPage, "'use client'", 'FAQ publica deve ser SSR')
requireText(publicPage, "export const dynamic = 'force-dynamic'", 'FAQ publica dinamica')
requireText(publicPage, '<details', 'conteudo legivel sem JavaScript')
requireText(publicPage, "'@type': 'FAQPage'", 'JSON-LD FAQPage')
requireText(publicPage, 'visiveis.map', 'JSON-LD corresponde ao conteudo visivel')
requireText(publicPage, 'canonical: buildPublicUrl', 'canonical preservada')
requireText(publicApi, "cache: 'no-store'", 'atualizacao sem deploy')
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
