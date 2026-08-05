import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8')
}

const [page, card, dialog, management, entryState, genericWizard, storyFiles] = await Promise.all([
  source('../src/app/(private-routes)/meus-anuncios/page.tsx'),
  source('../src/components/anuncios/meu-anuncio-card.tsx'),
  source('../src/components/stories/story-create-dialog.tsx'),
  source('../src/components/stories/meus-stories-panel.tsx'),
  source('../src/components/stories/story-entry-state.ts'),
  source('../src/features/monetizacao-wizard/monetizacao-wizard.tsx'),
  readdir(new URL('../src/components/stories/', import.meta.url)),
])

let checks = 0
function check(name, callback) {
  try { callback(); checks += 1 } catch (error) { error.message = `${name}: ${error.message}`; throw error }
}
function matches(value, pattern) { assert.match(value, pattern) }
function excludes(value, pattern) { assert.doesNotMatch(value, pattern) }

check('1. entrada global antecede novo anuncio', () => {
  matches(page, /Publicar nos Stories[\s\S]*href="\/anunciar\/wizard"[\s\S]*Publicar novo anúncio/)
})

check('2. entrada global funciona sem anuncios', () => {
  matches(page, /storyGlobalDisabled = loading \|\| Boolean\(erro\)/)
  matches(page, /Você já pode enviar uma mídia aos Stories/)
  excludes(page, /storyGlobalDisabled[^\n]*anuncios\.length/)
})

check('3. upload entra diretamente na etapa de arquivo', () => {
  matches(dialog, /chooseMode\('MIDIA_UPLOAD'\)/)
  matches(dialog, /setAnuncioId\(null\)[\s\S]*setStep\('CONFIGURAR_MIDIA'\)/)
})

check('4. card abre o modal com o proprio anuncio', () => {
  matches(card, /onStoryOpen\(anuncio\.id, event\.currentTarget\)/)
  matches(page, /setStoryTargetId\(anuncioId\)[\s\S]*setStoryDialogOpen\(true\)/)
})

check('5. varios anuncios usam a etapa interna do mesmo modal', () => {
  matches(dialog, /eligibleAnuncios\.length === 1/)
  matches(dialog, /setStep\('SELECIONAR_ANUNCIO'\)/)
  matches(dialog, /eligibleAnuncios\.map/)
})

check('6. modo ANUNCIO nao recebe arquivo', () => {
  matches(dialog, /arquivo: mode === 'MIDIA_UPLOAD' \? file : null/)
  matches(dialog, /anuncioId: mode === 'ANUNCIO' \? anuncioId : null/)
})

check('7. modal usa exclusivamente o adapter canonico', () => {
  matches(dialog, /from '@\/lib\/minha-conta-stories-api'/)
  matches(dialog, /publicarMinhaContaStory/)
  matches(dialog, /ativarMinhaContaStory/)
  excludes(dialog, /fetch\(|XMLHttpRequest|['"]\/api\//)
})

check('8. existe um unico modal de criacao', () => {
  assert.equal((page.match(/<StoryCreateDialog/g) || []).length, 1)
  const creationDialogs = storyFiles.filter((name) => /^story-.*(?:create|publication|upload|selector)-dialog\.tsx$/.test(name))
  assert.deepEqual(creationDialogs, ['story-create-dialog.tsx'])
})

check('9. Meus Stories e global e independente dos cards', () => {
  matches(page, /<MeusStoriesPanel/)
  matches(management, /Story independente/)
  matches(management, /story\.modoConteudo === 'ANUNCIO'/)
})

check('10. publicacao e encerramento atualizam sem reload', () => {
  matches(page, /setStoryRefreshVersion/)
  matches(management, /await load\(targetPage\)/)
  excludes(page + management, /location\.reload/)
})

check('11. fluxo do card preserva elegibilidade canonica', () => {
  matches(entryState, /anuncio\.status !== 'PUBLICADO' \|\| anuncio\.statusModeracao !== 'APROVADO'/)
  matches(card, /disabled=\{storyEntry\.disabled\}/)
})

check('12. foco retorna ao gatilho correto', () => {
  matches(page, /storyReturnFocusRef/)
  matches(dialog, /onCloseAutoFocus/)
  matches(dialog, /returnFocusTo\.focus\(\)/)
})

check('13. controles empilham no mobile', () => {
  matches(page, /flex flex-col justify-end gap-2 sm:flex-row/)
  matches(dialog, /w-\[calc\(100vw-1rem\)\]/)
  excludes(page + dialog, /w-\[100vw\]|min-w-\[[4-9][0-9]{2}px\]/)
})

check('14. controles possuem semantica acessivel', () => {
  matches(dialog, /type="button"/)
  matches(dialog, /role="progressbar"/)
  matches(dialog + management, /role="alert"/)
  matches(dialog + management, /role="status"/)
})

check('15. nao ha controles interativos aninhados', () => {
  excludes(page + dialog, /<Link\b[^>]*>(?:(?!<\/Link>)[\s\S])*<button/)
  excludes(page + dialog, /<button\b[^>]*>(?:(?!<\/button>)[\s\S])*<Link/)
})

check('16. wizard Premium generico continua sem Stories', () => {
  excludes(genericWizard, /beneficioCodigo:\s*'STORIES'|codigo:\s*'STORIES'/)
})

assert.equal(checks, 16)
console.log(`STORY_ENTRY_POINTS_CHECKS=${checks}`)
console.log('STORY_ENTRY_POINTS_RESULT=OK')
