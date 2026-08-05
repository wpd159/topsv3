import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8')
}

const [dialog, page, api, management, admin, adminApi, viewer, accessPolicy, publicList, publicApi, types] = await Promise.all([
  source('../src/components/stories/story-create-dialog.tsx'),
  source('../src/app/(private-routes)/meus-anuncios/page.tsx'),
  source('../src/lib/minha-conta-stories-api.ts'),
  source('../src/components/stories/meus-stories-panel.tsx'),
  source('../src/components/stories/admin-stories-management.tsx'),
  source('../src/lib/admin-story-management-api.ts'),
  source('../src/components/stories/story-viewer-dialog.tsx'),
  source('../src/components/stories/story-access-policy.js'),
  source('../src/app/(public-routes)/anuncios/usuario/[username]/anuncios-usuario-client.tsx'),
  source('../src/lib/public-catalog-api.ts'),
  source('../src/components/stories/stories-types.ts'),
])

const policyUrl = `data:text/javascript;base64,${Buffer.from(accessPolicy).toString('base64')}`
const { canNavigateFromStory, sanitizeStoryViewerItem } = await import(policyUrl)

let checks = 0
function check(name, callback) {
  try { callback(); checks += 1 } catch (error) { error.message = `${name}: ${error.message}`; throw error }
}
function matches(value, pattern) { assert.match(value, pattern) }
function excludes(value, pattern) { assert.doesNotMatch(value, pattern) }

check('1. modal canonico possui somente os dois modos', () => {
  matches(dialog, /Como você quer publicar\?/)
  matches(dialog, /chooseMode\('MIDIA_UPLOAD'\)[\s\S]*Enviar uma mídia/)
  matches(dialog, /chooseMode\('ANUNCIO'\)[\s\S]*Promover anúncio/)
})

check('2. zero anuncios desabilita somente ANUNCIO', () => {
  matches(dialog, /disabled=\{eligibleAnuncios\.length === 0\}/)
  matches(dialog, /Publique uma foto ou vídeo exclusivo, sem precisar de anúncio\./)
  excludes(page, /storyGlobalDisabled\s*=\s*[^\n]*anuncios\.length/)
})

check('3. trocar para upload limpa o anuncio', () => {
  matches(dialog, /if \(nextMode === 'MIDIA_UPLOAD'\) \{[\s\S]*setAnuncioId\(null\)[\s\S]*setStep\('CONFIGURAR_MIDIA'\)/)
})

check('4. adapter envia uma unica raiz e campos condicionais', () => {
  matches(api, /const STORY_ROOT = '\/minha-conta\/stories'/)
  matches(api, /form\.append\('modoConteudo', modoConteudo\)/)
  matches(api, /modoConteudo === 'ANUNCIO' && anuncioId[\s\S]*form\.append\('anuncioId', anuncioId\)/)
  matches(api, /modoConteudo === 'MIDIA_UPLOAD' && arquivo[\s\S]*form\.append\('arquivo', arquivo\)/)
})

check('5. upload nao envia identificadores internos', () => {
  excludes(api, /form\.append\('(usuarioId|objectKey|bucket|mediaId)'/)
  matches(dialog, /Sem vínculo com anúncio\. CTA público: Ver anunciante\./)
})

check('6. viewer usa identidade publica', () => {
  matches(viewer, /rotuloDestino = podeNavegarPerfil \? "Ver anunciante" : "Ver anúncio"/)
  matches(viewer, /router\.push\(`\/anuncios\/usuario\/\$\{encodeURIComponent\(loginViewer\)\}`\)/)
  excludes(viewer, /\/anuncios\/usuario\/\$\{[^}]*usuarioId/)
})

check('7. CTA fica fail closed antes do gate', () => {
  assert.equal(canNavigateFromStory({ viewerState: 'IDADE_NAO_CONFIRMADA', modoConteudo: 'MIDIA_UPLOAD', midiaUrl: '/media' }, true, false), false)
  assert.equal(sanitizeStoryViewerItem({ viewerState: 'IDADE_NAO_CONFIRMADA', modoConteudo: 'MIDIA_UPLOAD', midiaUrl: '/media' }).midiaUrl, null)
})

check('8. CTA libera somente depois da midia pronta', () => {
  const item = { viewerState: 'LIBERADO', modoConteudo: 'MIDIA_UPLOAD', midiaUrl: '/api/public/compliance/visitor/media/stories/id' }
  assert.equal(canNavigateFromStory(item, false, false), false)
  assert.equal(canNavigateFromStory(item, true, false), true)
  assert.equal(canNavigateFromStory(item, true, true), false)
})

check('9. listagem publica usa username e estado vazio', () => {
  matches(publicApi, /\/anuncios\/usuario\/\$\{encodeURIComponent\(username\.trim\(\)\)\}/)
  matches(publicList, /Nenhum anúncio público disponível no momento\./)
})

check('10. Meus Stories preserva independentes e multiplicidade', () => {
  matches(management, /data\.itens\.map/)
  matches(management, /Story independente/)
  matches(management, /Mídia:/)
})

check('11. encerramento e descarte possuem textos canonicos', () => {
  matches(management, /Excluir este Story\?/)
  matches(management, /Descartar Story com falha\?/)
  matches(management, /direito permanecerá disponível para uma nova tentativa/)
  matches(management, /créditos utilizados não serão devolvidos automaticamente/)
})

check('12. encerramento atualiza sem reload', () => {
  matches(management, /await encerrarMeuStory/)
  matches(management, /await load\(targetPage\)/)
  excludes(management, /location\.reload|window\.location/)
})

check('13. administracao usa somente gestao e remover', () => {
  matches(adminApi, /`\/gestao\?\$\{query\.toString\(\)\}`/)
  matches(adminApi, /`\/\$\{encodeURIComponent\(storyId\)\}\/remover`/)
  matches(admin, /Motivo obrigatório/)
  matches(admin, /reason === 'OUTRO' && !description\.trim\(\)/)
})

check('14. interfaces respeitam viewport mobile', () => {
  matches(dialog, /w-\[calc\(100vw-1rem\)\]/)
  matches(management, /w-\[calc\(100vw-1rem\)\]/)
  matches(admin, /w-\[calc\(100vw-1rem\)\]/)
  excludes(dialog + management + admin, /w-\[100vw\]/)
})

check('15. DTO usa bundle opaco e nao UUID interno da conta', () => {
  matches(types, /bundleKey: string/)
  excludes(types, /\busuarioId\b/)
})

check('16. DOM nao recebe segredo de storage', () => {
  excludes(dialog + management + viewer + publicList, /objectKey|chaveObjeto|signedUrl|urlPrivada|privateBucket/)
})

check('17. strings Unicode sao reais', () => {
  excludes(dialog + management, /\\u00[0-9a-f]{2}/i)
  matches(management, /Início:/)
  matches(management, /Mídia:/)
})

check('18. impressao de cards nao registra visualizacao', () => {
  excludes(publicList, /registrarVisualizacao|visualizacoes\/registrar|POST[^\n]*visualiza/i)
})

assert.equal(checks, 18)
console.log(`STORY_CREATE_MODES_CHECKS=${checks}`)
console.log('STORY_CREATE_MODES_RESULT=OK')
