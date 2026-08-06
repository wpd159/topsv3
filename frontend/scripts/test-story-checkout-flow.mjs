import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8')
}

const [dialog, api, publicationService, rightsService, controller, activationRepository, storyRepository, premiumService, premiumCatalog, migration, openapi] = await Promise.all([
  source('../src/components/stories/story-create-dialog.tsx'),
  source('../src/lib/minha-conta-stories-api.ts'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/MinhaContaStoriesPublicacaoService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/MinhaContaStoriesDireitoService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/web/publico/anunciante/MinhaContaStoriesController.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/persistence/repository/AtivacaoBeneficioRepository.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/persistence/repository/StoryAnuncioRepository.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/premium/MinhaContaPremiumService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/premium/PremiumCatalogoService.java'),
  source('../../backend/src/main/resources/db/migration/V050__stories_independentes_e_encerramento_logico.sql'),
  source('../../contracts/openapi/topsdojob-v3-local.yaml'),
])

let checks = 0
function check(name, callback) {
  try { callback(); checks += 1 } catch (error) { error.message = `${name}: ${error.message}`; throw error }
}
function matches(value, pattern) { assert.match(value, pattern) }
function excludes(value, pattern) { assert.doesNotMatch(value, pattern) }

check('1. oferta, ativacao e publicacao usam uma unica raiz', () => {
  matches(api, /const STORY_ROOT = '\/minha-conta\/stories'/)
  matches(api, /`\$\{STORY_ROOT\}\/oferta\?/)
  matches(api, /`\$\{STORY_ROOT\}\/ativacoes`/)
  matches(api, /publicApiUrl\(STORY_ROOT\)/)
})

check('2. payload usa modo e alvo condicional', () => {
  matches(api, /form\.append\('modoConteudo', modoConteudo\)/)
  matches(api, /modoConteudo === 'ANUNCIO' && anuncioId/)
  matches(api, /modoConteudo === 'MIDIA_UPLOAD' && arquivo/)
  excludes(api, /form\.append\('(usuarioId|slug|mediaId|objectKey|bucket)'/i)
})

check('3. proprietario vem da sessao', () => {
  matches(publicationService, /usuarioAutenticado\(authentication\)\.getId\(\)/)
  excludes(controller, /@RequestParam[^\n]*(?:usuarioId|email)/i)
})

check('4. exatamente uma midia e obrigatoria apenas no upload', () => {
  matches(publicationService, /arquivos\.size\(\) != 1/)
  matches(publicationService, /ANUNCIO nao aceita arquivo/)
  matches(dialog, /Envie exatamente um arquivo/)
  excludes(dialog.match(/<input[^>]*type="file"[^>]*\/>/)?.[0] ?? '', /\bmultiple\b/)
})

check('5. agregado possui as duas fabricas sem galeria', () => {
  matches(publicationService, /StoryAnuncioEntity\.criarAnuncio/)
  matches(publicationService, /StoryAnuncioEntity\.criarMidiaUpload/)
  excludes(publicationService, /AnuncioMidiaRepository|FinalidadeAnuncioMidia/)
})

check('6. storage usa ownership da conta e finalidade Story', () => {
  matches(publicationService, /stories\/contas\/" \+ usuarioId/)
  matches(publicationService, /StorageArea\.PRIVATE_MEDIA/)
  matches(publicationService, /uploadValidator\.validarStory/)
})

check('7. direitos respeitam conta, modo e anuncio', () => {
  matches(rightsService, /MIDIA_UPLOAD nao aceita anuncioId/)
  matches(rightsService, /ANUNCIO exige anuncioId/)
  matches(rightsService, /findByUsuarioIdOrderByCriadoEmDesc\(usuarioId\)/)
  matches(rightsService, /direitoMidiaUploadCompativel/)
  matches(rightsService, /existsByAtivacaoBeneficioIdAndDireitoPreservadoTrueAndEncerradoEmIsNotNull/)
  matches(rightsService, /findByAnuncioId\(anuncioId\)/)
})

check('8. direito consumido nao pode pertencer a dois Stories', () => {
  matches(rightsService, /existsByAtivacaoBeneficioIdAndDireitoPreservadoFalse/)
  matches(storyRepository, /existsByAtivacaoBeneficioIdAndDireitoPreservadoFalse/)
  matches(activationRepository, /findAguardandoUsoContaByCodigoForUpdate/)
})

check('9. compra e publicacao usam chaves diferentes', () => {
  matches(dialog, /publishKeyRef/)
  matches(dialog, /activationKeyRef/)
  matches(api, /Idempotency-Key/)
  matches(publicationService, /story-publicacao:/)
})

check('10. retry valida a mesma intencao', () => {
  matches(publicationService, /findByCriadoPorAndModoConteudoAndAnuncioIdAndIdempotencyKey/)
  matches(publicationService, /findByCriadoPorAndModoConteudoAndAnuncioIdIsNullAndIdempotencyKey/)
  matches(publicationService, /Idempotency-Key reutilizada com outra intencao/)
  matches(rightsService, /Idempotency-Key reutilizada com outra intencao/)
})

check('11. vigencia comeca na publicacao e dura 24 horas', () => {
  matches(rightsService, /publicadoEm\.plusHours\(DURACAO_HORAS\)/)
  matches(dialog, /Story por 24 horas/)
  matches(api, /offer\.duracaoHoras !== 24/)
})

check('12. falha depois da ativacao permite retry sem cobranca', () => {
  matches(dialog, /Nenhuma nova cobrança será feita/)
  matches(dialog, /activationCompleted/)
  matches(dialog, /publishStory\(true\)/)
})

check('13. um orquestrador publica ambos os modos', () => {
  matches(controller, /MinhaContaStoriesPublicacaoService/)
  matches(controller, /@RequestMapping\("\/api\/public\/minha-conta\/stories"\)/)
  matches(controller, /publicacaoService\.publicar/)
})

check('14. Stories permanece fora do Premium generico', () => {
  matches(premiumCatalog, /!PremiumBeneficioCodigo\.STORIES\.equals/)
  matches(premiumService, /Stories utiliza o fluxo proprio de publicacao/)
})

check('15. V050 e apenas estrutural', () => {
  excludes(migration, /\bINSERT\b|\bUPDATE\b|\bDELETE\b|custo_creditos|preco|duracao_dias/i)
  matches(migration, /story_anuncio/)
  excludes(migration, /create\s+table\s+story_(?:v2|conta|independente)/i)
})

check('16. OpenAPI documenta apenas a familia canonica', () => {
  matches(openapi, /\/api\/public\/minha-conta\/stories:/)
  matches(openapi, /\/api\/admin\/stories\/gestao:/)
  excludes(openapi, /\/api\/public\/minha-conta\/anuncios\/\{slug\}\/stories:/)
  excludes(openapi, /\/api\/admin\/stories\/selecao/)
})

assert.equal(checks, 16)
console.log(`STORY_CHECKOUT_FLOW_CHECKS=${checks}`)
console.log('STORY_CHECKOUT_FLOW_RESULT=OK')
