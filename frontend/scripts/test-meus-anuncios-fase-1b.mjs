import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sourceRoot = path.join(frontendRoot, 'src')

function source(relativePath) {
  return fs.readFileSync(path.join(sourceRoot, relativePath), 'utf8')
}

function normalized(value) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
}

const api = source('lib/meus-anuncios-api.ts')
const listPage = source('app/(private-routes)/meus-anuncios/page.tsx')
const card = source('components/anuncios/meu-anuncio-card.tsx')
const detail = source('components/anuncios/meu-anuncio-detalhe-view.tsx')
const lifecycle = source('components/anuncios/meu-anuncio-acoes-ciclo-vida.tsx')
const combined = `${listPage}\n${card}\n${detail}\n${lifecycle}`
const normalizedCombined = normalized(combined)

for (const field of [
  'titulo',
  'descricao',
  'categoria',
  'preco',
  'whatsapp',
  'locaisAtendimento',
  'servicos',
  'status',
  'statusModeracao',
  'localizacao',
  'capa',
  'midias',
  'visualizacoes',
  'acoesPermitidas',
]) {
  assert.match(api, new RegExp(`\\b${field}:`), `Campo ausente do MeuAnuncio: ${field}`)
}

assert.match(listPage, /href="\/anunciar\/wizard"/)
assert.match(normalized(listPage), /Publicar novo anuncio/)
assert.match(card, /anuncioPreco\(anuncio\.preco\)/)
assert.match(card, /\/meus-anuncios\/\$\{encodeURIComponent\(anuncio\.slug\)\}/)
assert.match(card, /\/meus-anuncios\/\$\{encodeURIComponent\(anuncio\.slug\)\}\/editar/)
assert.match(card, /\/meus-anuncios\/\$\{encodeURIComponent\(anuncio\.slug\)\}\/monetizar/)
assert.match(card, /anuncio\.status === 'PUBLICADO'/)
assert.match(normalized(card), /Detalhes/)
assert.match(normalized(card), /Editar/)
assert.match(normalized(card), /Monetizar/)
assert.match(lifecycle, /acoesPermitidas\.pausar/)
assert.match(lifecycle, /acoesPermitidas\.reativar/)
assert.match(lifecycle, /acoesPermitidas\.remover/)
assert.match(normalized(lifecycle), /Pausar/)
assert.match(normalized(lifecycle), /Reativar/)
assert.match(normalized(lifecycle), /Remover anuncio/)
assert.match(lifecycle, /setConfirmacao\('PAUSAR'\)/)
assert.match(lifecycle, /setConfirmacao\('REMOVER'\)/)
assert.match(lifecycle, /emExecucao\.current/)
assert.match(lifecycle, /disabled=\{processando !== null\}/)
assert.match(lifecycle, /onSuccess\(resultado, acao\)/)

for (const field of [
  'anuncio.titulo',
  'anuncio.preco',
  'anuncio.categoria',
  'anuncio.status',
  'anuncio.statusModeracao',
  'anuncio.descricao',
  'anuncio.localizacao?.bairro',
  'anuncio.localizacao?.cidade',
  'anuncio.localizacao?.uf',
  'anuncio.servicos',
  'anuncio.locaisAtendimento',
  'anuncio.whatsapp',
  'anuncio.capa',
  'anuncio.midias',
  'anuncio.visualizacoes',
]) {
  assert.ok(detail.includes(field), `Campo do detalhe nao renderizado: ${field}`)
}

assert.match(detail, /midia\.tipo !== 'FOTO'/)
assert.match(detail, /midia\.restrita/)
assert.match(detail, /midia\.visibilidadeMidia === 'RESTRITA_18'/)
assert.match(detail, /meuAnuncioUrlPublicaSegura\(midia\.urlPublica\)/)
assert.match(card, /PARAMETROS_DE_URL_ASSINADA/)
assert.match(card, /resolvida\.protocol !== 'https:'/)
assert.match(detail, /priority/)
assert.match(detail, /loading="lazy"/)
assert.match(normalized(detail), /Midia protegida/)
assert.match(normalized(detail), /aguardando moderacao/)
assert.doesNotMatch(combined, /objectKey|object_key|chaveObjeto|bucket|documentoKyc|dataNascimento/)
assert.doesNotMatch(combined, /\/anuncios\/\$\{.*\}(?!\/editar|\/monetizar)/)

assert.match(listPage, /error\.status === 401/)
assert.match(listPage, /error\.status === 403/)
assert.match(listPage, /error\.status === 404/)
assert.match(normalized(listPage), /Integracao indisponivel/)
assert.match(detail, /error\.status === 403/)
assert.match(detail, /error\.status === 404/)
assert.match(detail, /error\.status === 401/)
assert.match(normalized(detail), /Acesso negado/)
assert.match(normalized(detail), /Anuncio nao encontrado/)
assert.doesNotMatch(listPage, /mensagem:\s*error instanceof Error/)
assert.doesNotMatch(detail, /mensagem:\s*error instanceof Error/)
assert.match(normalized(detail), /Nenhum servico informado/)
assert.match(normalized(detail), /Nenhum local de atendimento informado/)

for (const forbidden of [
  'Cliques',
  'CTR',
  'Excluir',
  'Story',
  'Performance',
  'Comprar creditos',
]) {
  assert.ok(!normalizedCombined.includes(normalized(forbidden)), `Acao fora do escopo adicionada: ${forbidden}`)
}

assert.doesNotMatch(combined, /document\.body\.style\.overflow|w-\[100vw\]|width\s*:\s*100vw/)
assert.match(card, /break-words/)
assert.match(detail, /min-w-0/)
assert.match(detail, /break-words/)
assert.match(listPage, /md:grid-cols-2/)
assert.doesNotMatch(combined, /fetch\s*\(/)
assert.doesNotMatch(combined, /\/api\/public/)

console.log('PAINEL_ANUNCIANTE_FASE_1B_RESULT=OK')
