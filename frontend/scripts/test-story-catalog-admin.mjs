import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8')
}

const [
  redirectPage,
  adminPage,
  adminApi,
  adminService,
  adminController,
  beneficioEntity,
  offerService,
  storyService,
  storyEntity,
  mediaEntity,
  entryState,
  meusAnunciosApi,
  ageGateProperties,
  openapi,
] = await Promise.all([
  source('../src/app/(painel-admin)/admin/beneficios-premium/page.tsx'),
  source('../src/app/(painel-admin)/admin/creditos/page.tsx'),
  source('../src/lib/admin-creditos-operacionais-api.ts'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/admin/premium/AdminPremiumCatalogoService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/web/admin/premium/AdminPremiumController.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/persistence/entity/premium/BeneficioPremiumEntity.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/MeuAnuncioStoryOfertaService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/MeuAnuncioStoryService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/persistence/entity/midia/StoryAnuncioEntity.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/persistence/entity/midia/AnuncioMidiaEntity.java'),
  source('../src/components/stories/story-entry-state.ts'),
  source('../src/lib/meus-anuncios-api.ts'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/compliance/ComplianceAgeGateProperties.java'),
  source('../../contracts/openapi/topsdojob-v3-local.yaml'),
])

const migrations = await readdir(new URL(
  '../../backend/src/main/resources/db/migration/',
  import.meta.url,
))
const storyDraft = adminPage.slice(
  adminPage.indexOf('function storyDraft'),
  adminPage.indexOf('function catalogoComStory'),
)
const optionDraft = adminPage.slice(
  adminPage.indexOf('function novaOpcao'),
  adminPage.indexOf('function inteiroFormulario'),
)
const updateSchema = openapi.slice(
  openapi.indexOf('AdminPremiumCatalogoOpcaoRequest:'),
  openapi.indexOf('AdminPlanoCredito:'),
)

let checks = 0
function check(name, callback) {
  try {
    callback()
    checks += 1
  } catch (error) {
    error.message = name + ': ' + error.message
    throw error
  }
}
function matches(value, pattern) { assert.match(value, pattern) }
function excludes(value, pattern) { assert.doesNotMatch(value, pattern) }

check('1. rota administrativa converge para a fonte unica', () => {
  matches(redirectPage, /redirect\('\/admin\/creditos#beneficios-premium'\)/)
  matches(adminPage, /id="beneficios-premium"/)
})

check('2. Stories ausente gera somente identidade sem valor comercial', () => {
  matches(storyDraft, /codigo: 'STORIES'/)
  matches(storyDraft, /opcoes: \[\]/)
  excludes(storyDraft, /duracaoDias|custoCreditos/)
})

check('3. nova opcao inicia integralmente em branco', () => {
  matches(optionDraft, /duracaoDias: ''/)
  matches(optionDraft, /custoCreditos: ''/)
  matches(optionDraft, /ordemExibicao: ''/)
  excludes(optionDraft, /duracaoDias:\s*\d|custoCreditos:\s*\d/)
})

check('4. catalogo permite configurar duracao custo ordem e disponibilidade', () => {
  matches(adminPage, /Duracao \(dias\)/)
  matches(adminPage, /Custo \(creditos\)/)
  matches(adminPage, /Adicionar opcao/)
  matches(adminPage, /checked=\{opcao\.ativo\}/)
})

check('5. adapter usa o mesmo recurso canonico para criar e atualizar', () => {
  matches(adminApi, /criarCatalogo:[\s\S]*'\/premium\/catalogo'[\s\S]*method: 'POST'/)
  matches(adminApi, /atualizarCatalogo:[\s\S]*premium\/catalogo\/\$\{id\}[\s\S]*method: 'PUT'/)
})

check('6. controller preserva ADMIN Premium RBAC e CSRF global', () => {
  matches(adminController, /@PostMapping\("\/catalogo"\)/)
  matches(adminController, /hasRole\('ADMIN'\) and hasAuthority\('PREMIUM_GERENCIAR'\)/)
})

check('7. service restringe criacao ao codigo canonico Stories', () => {
  matches(adminService, /if \(!STORIES\.equals\(codigo\)\)/)
  matches(adminService, /EscopoBeneficioPremium\.ANUNCIO/)
  matches(beneficioEntity, /criarCatalogo/)
})

check('8. duracao e dinamica sem lista comercial fixa', () => {
  matches(adminService, /inteiroPositivo\(opcao\.duracaoDias\(\)/)
  excludes(adminService, /DURACOES_PERMITIDAS|Set\.of\(1,\s*7,\s*14,\s*30\)/)
  matches(updateSchema, /duracaoDias: \{ type: integer, minimum: 1, maximum: 1000000 \}/)
  excludes(updateSchema, /enum: \[1, 7, 14, 30\]/)
})

check('9. opcao omitida e desativada sem exclusao', () => {
  matches(adminService, /!recebidas\.containsKey\(duracao\)[\s\S]*false/)
  excludes(adminService, /delete|remove/)
})

check('10. retry de criacao nao duplica beneficio', () => {
  matches(adminService, /findByCodigo\(codigo\)\.isPresent\(\)/)
  matches(adminService, /HttpStatus\.CONFLICT/)
})

check('11. direito adquirido antecede consulta ao catalogo ativo', () => {
  matches(offerService, /direito != null[\s\S]*catalogoService\.catalogoAtivo\(\)/)
})

check('12. catalogo ausente ou inativo fecha somente novas ativacoes', () => {
  matches(offerService, /NOVAS_ATIVACOES_INDISPONIVEIS/)
  matches(offerService, /catalogoService\.catalogoAtivo\(\)/)
})

check('13. estado publico nao comunica moderacao humana de Story', () => {
  matches(offerService, /"DISPONIVEL_PARA_PUBLICAR"/)
  matches(entryState, /new Set\(\['ATIVO', 'DISPONIVEL_PARA_PUBLICAR'\]\)/)
  matches(meusAnunciosApi, /\['DISPONIVEL_PARA_PUBLICAR', 'ATIVA'\]/)
})

check('14. publicacao direta cria Story publicado', () => {
  matches(storyService, /StoryAnuncioEntity\.criarAutogestao/)
  matches(storyEntity, /entity\.status = StatusStoryAnuncio\.PUBLICADO/)
  excludes(storyService, /AdminModeracao|ModeracaoAcaoService|remeterRevisao/)
})

check('15. upload de Story e publicavel e restrito por definicao', () => {
  const factory = mediaEntity.slice(
    mediaEntity.indexOf('criarStoryUploadValidado'),
    mediaEntity.indexOf('criarFixtureHomologacao'),
  )
  matches(factory, /StatusAnuncioMidia\.PUBLICAVEL/)
  matches(factory, /VisibilidadeMidia\.RESTRITA_18/)
})

check('16. age gate global permanece em sete dias', () => {
  matches(ageGateProperties, /private int globalTtlDays = 7;/)
  matches(ageGateProperties, /Duration\.ofDays\(globalTtlDays\)/)
})

check('17. OpenAPI possui um unico contrato de criacao Stories', () => {
  matches(openapi, /postAdminPremiumCatalogo/)
  matches(openapi, /codigo: \{ type: string, enum: \[STORIES\] \}/)
  matches(openapi, /enum: \[DISPONIVEL_PARA_PUBLICAR, ATIVA\]/)
})

check('18. nenhuma migration nova foi criada', () => {
  assert.equal(migrations.some((name) => /^V049__/.test(name)), false)
  assert.equal(migrations.some((name) => /^V048__stories_autogestao_modos_conteudo\.sql$/.test(name)), true)
})

check('19. concorrencia administrativa retorna conflito e serializa atualizacao', () => {
  matches(adminService, /saveAndFlush\(beneficio\)/)
  matches(adminService, /catch \(DataIntegrityViolationException exception\)/)
  matches(adminService, /findByIdForUpdate\(beneficioId\)/)
})

check('20. formulario bloqueia duplo clique e anuncia erro e loading', () => {
  matches(adminPage, /catalogoSaveLock\.current/)
  matches(adminPage, /role="alert"/)
  matches(adminPage, /aria-busy=\{catalogoSavingId === item\.id\}/)
})

check('21. alteracao de disponibilidade exige confirmacao', () => {
  matches(adminPage, /disponibilidadeCatalogoAlterada\(item\)[\s\S]*window\.confirm/)
})

assert.equal(checks, 21)
console.log('STORY_CATALOG_ADMIN_CHECKS=' + checks)
console.log('STORY_CATALOG_ADMIN_RESULT=OK')
