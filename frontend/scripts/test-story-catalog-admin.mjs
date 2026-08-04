import assert from 'node:assert/strict'
import { access, readFile, readdir } from 'node:fs/promises'

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8')
}

const [
  adminPage,
  storyCard,
  storyApi,
  storyController,
  storyService,
  storyRequest,
  storyDto,
  storyEntity,
  storyRepository,
  premiumCatalogService,
  adminPremiumCatalogService,
  adminPremiumOperationService,
  beneficioPremiumRepository,
  adminPremiumController,
  adminCreditsApi,
  genericWizardStep,
  migration,
  openapi,
] = await Promise.all([
  source('../src/app/(painel-admin)/admin/creditos/page.tsx'),
  source('../src/app/(painel-admin)/admin/creditos/admin-story-configuracao-card.tsx'),
  source('../src/lib/admin-stories-api.ts'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/web/admin/stories/AdminStoryConfiguracaoController.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/admin/stories/AdminStoryConfiguracaoService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/admin/stories/dto/AdminStoryConfiguracaoRequest.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/admin/stories/dto/AdminStoryConfiguracaoDto.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/persistence/entity/midia/StoryConfiguracaoComercialEntity.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/persistence/repository/StoryConfiguracaoComercialRepository.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/premium/PremiumCatalogoService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/admin/premium/AdminPremiumCatalogoService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/admin/premium/AdminPremiumOperacaoService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/persistence/repository/BeneficioPremiumRepository.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/web/admin/premium/AdminPremiumController.java'),
  source('../src/lib/admin-creditos-operacionais-api.ts'),
  source('../src/features/monetizacao-wizard/components/monetizacao-step-anuncio.tsx'),
  source('../../backend/src/main/resources/db/migration/V049__stories_configuracao_comercial.sql'),
  source('../../contracts/openapi/topsdojob-v3-local.yaml'),
])

const migrations = await readdir(new URL('../../backend/src/main/resources/db/migration/', import.meta.url))
const obsoleteOfferOptionExists = await access(new URL(
  '../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/dto/MeuAnuncioStoryOfertaOpcaoDto.java',
  import.meta.url,
)).then(() => true, () => false)
const configPath = openapi.slice(
  openapi.indexOf('/api/admin/stories/configuracao:'),
  openapi.indexOf('/api/admin/premium/catalogo:'),
)
const genericCatalogPath = openapi.slice(
  openapi.indexOf('/api/admin/premium/catalogo:'),
  openapi.indexOf('/api/admin/premium/catalogo/{id}:'),
)
const configRequestSchema = openapi.slice(
  openapi.indexOf('AdminStoryConfiguracaoRequest:'),
  openapi.indexOf('AdminPremiumCatalogoUpdateRequest:'),
)

let checks = 0
function check(name, callback) {
  try {
    callback()
    checks += 1
  } catch (error) {
    error.message = `${name}: ${error.message}`
    throw error
  }
}
function matches(value, pattern) { assert.match(value, pattern) }
function excludes(value, pattern) { assert.doesNotMatch(value, pattern) }

check('1. pagina administrativa possui cinco dominios separados', () => {
  matches(adminPage, /value="stories">Stories/)
  matches(adminPage, /value="beneficios">Benef.cios Premium/)
  matches(adminPage, /value="pacotes">Pacotes de cr.ditos/)
  matches(adminPage, /value="saldos">Saldos e ajustes/)
  matches(adminPage, /value="ativacoes">Ativa..es e hist.rico/)
})

check('2. Stories usa componente administrativo proprio', () => {
  matches(adminPage, /<AdminStoryConfiguracaoCard \/>/)
  matches(adminPage, /AdminPremiumCatalogo/)
})

check('3. card explica os dois modos comerciais', () => {
  matches(storyCard, /Permite promover um an.ncio ou publicar uma m.dia exclusiva por 24 horas/)
})

check('4. admin edita somente status e custo', () => {
  matches(storyCard, /Status comercial ativo/)
  matches(storyCard, /Custo por Story/)
  matches(storyCard, /checked=\{ativo\}/)
  matches(storyCard, /value=\{custo\}/)
})

check('5. duracao e fixa e somente leitura', () => {
  matches(storyCard, /Dura..o[\s\S]*24 horas . fixa/)
  excludes(storyCard, /name=["']duracao|setDuracao|duracaoDias/)
})

check('6. custo vazio difere de custo zero', () => {
  matches(storyCard, /if \(!value\.trim\(\)\) return null/)
  matches(storyCard, /Number\.isInteger\(parsed\) && parsed >= 0/)
})

check('7. alteracao nao salva fica visivel', () => {
  matches(storyCard, /const dirty = useMemo/)
  matches(storyCard, /Altera..es n.o salvas/)
})

check('8. desativacao exige confirmacao especifica', () => {
  matches(storyCard, /window\.confirm/)
  matches(storyCard, /Novas ativa..es de Stories ficar.o indispon.veis/)
  matches(storyCard, /Direitos j. adquiridos e Stories ativos ser.o preservados/)
})

check('9. duplo clique e bloqueado de modo sincrono', () => {
  matches(storyCard, /const saveLock = useRef\(false\)/)
  matches(storyCard, /if \(saveLock\.current \|\| !dirty\) return/)
  matches(storyCard, /saveLock\.current = true/)
  matches(storyCard, /saveLock\.current = false/)
})

check('10. loading sucesso e erro sao acessiveis', () => {
  matches(storyCard, /role="status"/)
  matches(storyCard, /role="alert"/)
  matches(storyCard, /errorRef\.current\?\.focus\(\)/)
  matches(storyCard, /aria-busy=\{saving\}/)
})

check('11. layout administrativo e responsivo', () => {
  matches(storyCard, /grid gap-4 sm:grid-cols-/)
  matches(storyCard, /flex flex-wrap/)
  excludes(storyCard, /min-w-\[[4-9][0-9]{2}px\]|w-\[[4-9][0-9]{2}px\]/)
})

check('12. adapter usa API propria GET e PUT', () => {
  matches(storyApi, /fetchAdminStoryConfiguracao[\s\S]*'\/configuracao'/)
  matches(storyApi, /saveAdminStoryConfiguracao[\s\S]*method: 'PUT'/)
})

check('13. adapter administrativo preserva sessao CSRF e no-store', () => {
  matches(storyApi, /credentials: 'include'/)
  matches(storyApi, /cache: 'no-store'/)
  matches(storyApi, /antiForgeryHeaderName/)
})

check('14. controller pertence ao dominio Stories', () => {
  matches(storyController, /@RequestMapping\("\/api\/admin\/stories\/configuracao"\)/)
  matches(storyController, /@GetMapping/)
  matches(storyController, /@PutMapping/)
})

check('15. controller exige ADMIN e PREMIUM_GERENCIAR', () => {
  matches(storyController, /hasRole\('ADMIN'\) and hasAuthority\('PREMIUM_GERENCIAR'\)/)
  matches(storyController, /@AuthenticationPrincipal AdminUserPrincipal/)
})

check('16. request administrativo nao aceita duracao ou codigo arbitrario', () => {
  matches(storyRequest, /Boolean ativo/)
  matches(storyRequest, /Integer custoCreditos/)
  matches(storyRequest, /Long versao/)
  excludes(storyRequest, /duracao|dias|opcaoId|codigoBeneficio|usuarioId/i)
})

check('17. resposta fixa 24 horas no dominio', () => {
  matches(storyService, /public static final int DURACAO_HORAS = 24/)
  matches(storyDto, /int duracaoHoras/)
})

check('18. ausencia de configuracao nao vira oferta gratuita', () => {
  matches(storyService, /false, false, null, DURACAO_HORAS, null, null/)
})

check('19. custo negativo e acima do limite sao rejeitados', () => {
  matches(storyService, /request\.custoCreditos\(\) < 0/)
  matches(storyService, /request\.custoCreditos\(\) > 1_000_000/)
})

check('20. custo zero explicito permanece permitido', () => {
  excludes(storyService, /custoCreditos\(\) <= 0/)
  matches(storyCard, /min=\{0\}/)
})

check('21. atualizacao usa lock e versao otimista', () => {
  matches(storyService, /configuracaoRepository\.findForUpdate\(\)/)
  matches(storyService, /Objects\.equals\(configuracao\.getVersao\(\), request\.versao\(\)\)/)
  matches(storyRepository, /@Lock\(LockModeType\.PESSIMISTIC_WRITE\)/)
})

check('22. alteracao administrativa e auditada', () => {
  matches(storyService, /STORY_CONFIGURACAO_ATUALIZAR/)
  matches(storyService, /requestId/)
  matches(storyService, /administrador\.usuarioId\(\)/)
})

check('23. identidade Premium e apenas tecnica', () => {
  matches(storyService, /Identidade tecnica para ledger e ativacoes de Stories/)
  matches(storyService, /inserirCatalogoSeAusente/)
  matches(storyService, /"ANUNCIO"/)
  excludes(storyService, /BeneficioPremiumOpcaoEntity|duracaoDias/)
})

check('24. entidade dedicada nao persiste duracao', () => {
  matches(storyEntity, /StoryConfiguracaoComercialEntity/)
  matches(storyEntity, /custoCreditos/)
  matches(storyEntity, /atualizadoPor/)
  excludes(storyEntity, /duracao|dias/i)
})

check('25. migration e estrutural e sem seed comercial', () => {
  matches(migration, /CREATE TABLE story_configuracao_comercial/)
  matches(migration, /CHECK \(id = 1\)/)
  matches(migration, /CHECK \(custo_creditos >= 0\)/)
  excludes(migration, /\bINSERT\b|duracao_(?:horas|dias)|preco_/i)
})

check('26. V049 foi adicionada sem alterar a identidade V048', () => {
  assert.equal(migrations.includes('V049__stories_configuracao_comercial.sql'), true)
  assert.equal(migrations.includes('V048__stories_autogestao_modos_conteudo.sql'), true)
})

check('27. catalogo Premium generico filtra Stories', () => {
  matches(premiumCatalogService, /filter\(item -> !PremiumBeneficioCodigo\.STORIES\.equals\(item\.getCodigo\(\)\)\)/)
})

check('28. wizard generico ignora Stories', () => {
  matches(genericWizardStep, /if \(feature\.codigo === 'STORIES'\) continue/)
})

check('29. controller Premium generico nao cria catalogo arbitrario', () => {
  excludes(adminPremiumController, /@PostMapping\("\/catalogo"\)/)
})

check('30. adapter Premium generico nao cria catalogo arbitrario', () => {
  excludes(adminCreditsApi, /criarCatalogo/)
  excludes(adminCreditsApi, /request<AdminPremiumCatalogo>\(\s*['"]\/premium\/catalogo['"][\s\S]{0,160}method:\s*'POST'/)
})

check('31. DTO de multiplas opcoes foi removido', () => {
  assert.equal(obsoleteOfferOptionExists, false)
})

check('32. OpenAPI documenta GET e PUT proprios', () => {
  matches(configPath, /get:/)
  matches(configPath, /put:/)
  matches(configPath, /AdminStoryConfiguracaoRequest/)
})

check('33. request OpenAPI nao aceita duracao', () => {
  matches(configRequestSchema, /required: \[ativo, custoCreditos\]/)
  excludes(configRequestSchema, /duracao|dias|opcaoId|codigoBeneficio|usuarioId/i)
})

check('34. resposta OpenAPI fixa 24 horas', () => {
  matches(openapi, /AdminStoryConfiguracao:[\s\S]*duracaoHoras: \{ type: integer, const: 24 \}/)
})

check('35. OpenAPI nao oferece POST no catalogo Premium generico', () => {
  matches(genericCatalogPath, /get:/)
  excludes(genericCatalogPath, /post:/)
})

check('36. identidade tecnica usa insercao atomica idempotente', () => {
  matches(storyService, /inserirCatalogoSeAusente/)
  matches(beneficioPremiumRepository, /ON CONFLICT DO NOTHING/)
  excludes(storyService, /beneficioRepository\.save(?:AndFlush)?\(/)
})

check('37. catalogo Premium generico rejeita Stories', () => {
  matches(adminPremiumCatalogService, /PremiumBeneficioCodigo\.STORIES/)
  matches(adminPremiumCatalogService, /HttpStatus\.CONFLICT/)
})

check('38. ativacao Premium generica simples e em lote rejeita Stories', () => {
  matches(adminPremiumOperationService, /beneficioAdministravel/)
  matches(adminPremiumOperationService, /PremiumBeneficioCodigo\.STORIES/)
  matches(adminPremiumOperationService, /Stories utiliza configuracao e ativacao proprias/)
})

assert.equal(checks, 38)
console.log(`STORY_CATALOG_ADMIN_CHECKS=${checks}`)
console.log('STORY_CATALOG_ADMIN_RESULT=OK')
