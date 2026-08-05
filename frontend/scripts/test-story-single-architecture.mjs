import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'

async function walk(url) {
  const entries = await readdir(url, { withFileTypes: true })
  const nested = await Promise.all(entries.map(async (entry) => {
    const child = new URL(`${entry.name}${entry.isDirectory() ? '/' : ''}`, url)
    return entry.isDirectory() ? walk(child) : [child]
  }))
  return nested.flat()
}

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8')
}

async function sourcesUnder(path, extension) {
  const files = (await walk(new URL(path, import.meta.url))).filter((url) => url.pathname.endsWith(extension))
  return Promise.all(files.map(async (url) => ({ url, text: await readFile(url, 'utf8') })))
}

const [frontendSources, backendSources, adapter, dialog, page, controller, security, migration, openapi] = await Promise.all([
  sourcesUnder('../src/', '.ts').then(async (ts) => ts.concat(await sourcesUnder('../src/', '.tsx'))),
  sourcesUnder('../../backend/src/main/java/', '.java'),
  source('../src/lib/minha-conta-stories-api.ts'),
  source('../src/components/stories/story-create-dialog.tsx'),
  source('../src/app/(private-routes)/meus-anuncios/page.tsx'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/web/publico/anunciante/MinhaContaStoriesController.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/security/config/SecurityConfig.java'),
  source('../../backend/src/main/resources/db/migration/V050__stories_independentes_e_encerramento_logico.sql'),
  source('../../contracts/openapi/topsdojob-v3-local.yaml'),
])

const frontend = frontendSources.map((item) => item.text).join('\n')
const backend = backendSources.map((item) => item.text).join('\n')
const root = ['/minha-conta', '/stories'].join('')
const oldPerAd = ['/minha-conta', '/anuncios/', '{slug}', '/stories'].join('')
const oldPublicPerAd = ['/api/public/anuncios/', '{slug}', '/stories'].join('')
const oldAdminSelection = ['/api/admin', '/stories/', 'selecao'].join('')
const oldServiceNames = [
  ['Meu', 'Anuncio', 'Story', 'Service'].join(''),
  ['Meu', 'Anuncio', 'Story', 'Ativacao', 'Service'].join(''),
  ['Meu', 'Anuncio', 'Story', 'Oferta', 'Service'].join(''),
  ['Minha', 'Conta', 'Story', 'Publicacao', 'Service'].join(''),
  ['Story', 'Publico', 'Service'].join(''),
]

let checks = 0
function check(name, callback) {
  try { callback(); checks += 1 } catch (error) { error.message = `${name}: ${error.message}`; throw error }
}

check('1. uma unica raiz frontend', () => {
  assert.match(adapter, new RegExp(`const STORY_ROOT = ['"]${root}['"]`))
  const declarations = frontend.match(/const STORY_ROOT =/g) || []
  assert.equal(declarations.length, 1)
})

check('2. uma unica familia de escrita no controller', () => {
  assert.match(controller, /@RequestMapping\("\/api\/public\/minha-conta\/stories"\)/)
  assert.equal((controller.match(/@PostMapping/g) || []).length, 2)
  assert.match(controller, /publicacaoService\.publicar/)
  assert.match(controller, /direitoService\.ativar/)
})

check('3. services escritores antigos nao existem', () => {
  const names = new Set(backendSources.map((item) => item.url.pathname.split('/').at(-1)?.replace(/\.java$/, '')))
  for (const oldName of oldServiceNames) assert.equal(names.has(oldName), false)
})

check('4. somente o orquestrador usa as fabricas de publicacao', () => {
  const adFactory = /StoryAnuncioEntity\.criarAnuncio\(/g
  const uploadFactory = /StoryAnuncioEntity\.criarMidiaUpload\(/g
  assert.equal((backend.match(adFactory) || []).length, 1)
  assert.equal((backend.match(uploadFactory) || []).length, 1)
  const owner = backendSources.find((item) => item.text.includes('StoryAnuncioEntity.criarAnuncio(')
    || item.text.includes('StoryAnuncioEntity.criarMidiaUpload('))
  assert.match(owner?.url.pathname ?? '', /MinhaContaStoriesPublicacaoService\.java$/)
})

check('5. runtime nao produz fixture historica de Story', () => {
  const historicalFactory = ['StoryAnuncioEntity', '.', 'criarFixture', 'Homologacao', '('].join('')
  assert.equal(backend.includes(historicalFactory), false)
})

check('6. MIDIA_UPLOAD nao escreve galeria', () => {
  const writer = backendSources.find((item) => item.url.pathname.endsWith('/MinhaContaStoriesPublicacaoService.java'))?.text ?? ''
  assert.doesNotMatch(writer, /AnuncioMidiaRepository|FinalidadeAnuncioMidia|anuncioMidiaId/)
  assert.match(writer, /StoryAnuncioEntity\.criarMidiaUpload/)
})

check('7. tabela e agregado principal permanecem unicos', () => {
  assert.match(migration, /story_anuncio/)
  assert.doesNotMatch(migration, /create\s+table\s+story_(?:v2|conta|independente)/i)
  assert.doesNotMatch(migration, /CREATE UNIQUE INDEX\s+\S*(?:status|ativo)\S*[\s\S]{0,180}ON story_anuncio \(criado_por\)/i)
})

check('8. V050 nao possui DML ou configuracao comercial', () => {
  assert.doesNotMatch(migration, /\b(?:INSERT|UPDATE|DELETE)\b/i)
  assert.doesNotMatch(migration, /custo_creditos|preco|duracao_dias/i)
})

check('9. OpenAPI nao possui familia antiga por anuncio', () => {
  assert.equal(openapi.includes(oldPerAd), false)
  assert.equal(openapi.includes(oldPublicPerAd), false)
  assert.doesNotMatch(openapi, /\/api\/public\/minha-conta\/anuncios\/\{slug\}\/stories/)
})

check('10. OpenAPI nao possui selecao administrativa antiga', () => {
  assert.equal(openapi.includes(oldAdminSelection), false)
  assert.match(openapi, /\/api\/admin\/stories\/gestao:/)
  assert.match(openapi, /\/api\/admin\/stories\/\{storyId\}\/remover:/)
})

check('11. SecurityConfig nao possui matcher antigo', () => {
  assert.equal(security.includes(oldAdminSelection), false)
  assert.doesNotMatch(security, /\/api\/public\/minha-conta\/anuncios\/\*\/stories/)
  assert.match(security, /\/api\/admin\/stories\/\*\/remover/)
})

check('12. frontend nao chama endpoint antigo', () => {
  assert.equal(frontend.includes(oldPerAd), false)
  assert.doesNotMatch(frontend, /\/minha-conta\/anuncios\/[^\s'"`]+\/stories/)
})

check('13. existe um unico adapter e um unico modal', () => {
  const adapters = frontendSources.filter((item) => item.url.pathname.endsWith('/minha-conta-stories-api.ts'))
  assert.equal(adapters.length, 1)
  const creationDialogs = frontendSources.filter((item) => /\/story-.*(?:create|publication|upload|selector)-dialog\.tsx$/.test(item.url.pathname))
  assert.deepEqual(creationDialogs.map((item) => item.url.pathname.split('/').at(-1)), ['story-create-dialog.tsx'])
})

check('14. uma unica maquina de criacao atende os dois modos', () => {
  assert.equal((dialog.match(/type Step =/g) || []).length, 1)
  assert.match(dialog, /chooseMode\('ANUNCIO'\)/)
  assert.match(dialog, /chooseMode\('MIDIA_UPLOAD'\)/)
  assert.equal((page.match(/<StoryCreateDialog/g) || []).length, 1)
})

check('15. card delega ao modal global', () => {
  assert.match(page, /onStoryOpen=\{abrirStory\}/)
  assert.match(page, /initialAnuncioId=\{storyTargetId\}/)
  assert.doesNotMatch(page, /fetch\([^\n]*stories|XMLHttpRequest/)
})

check('16. nao existe writer da selecao historica', () => {
  const oldFactory = ['StorySelecaoAdministrativaEntity', '.', 'nova', '('].join('')
  const oldActivation = ['StorySelecaoAdministrativaEntity', '.', 'ativar', '('].join('')
  assert.equal(backend.includes(oldFactory), false)
  assert.equal(backend.includes(oldActivation), false)
})

assert.equal(checks, 16)
console.log(`STORY_SINGLE_ARCHITECTURE_CHECKS=${checks}`)
console.log('STORY_SINGLE_ARCHITECTURE_RESULT=OK')
