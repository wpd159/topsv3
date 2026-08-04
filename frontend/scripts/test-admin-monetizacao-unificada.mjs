import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8')
const files = Object.fromEntries(await Promise.all(Object.entries({
  page: '../src/app/(painel-admin)/admin/creditos/page.tsx',
  navigation: '../src/lib/admin-monetizacao-navigation.ts',
  sidebar: '../src/app/(painel-admin)/admin/components/sidebar/sidebar-links.tsx',
  sidebarUtils: '../src/app/(painel-admin)/admin/components/sidebar/sidebar-utils.ts',
  redirect: '../src/app/(painel-admin)/admin/beneficios-premium/page.tsx',
  story: '../src/app/(painel-admin)/admin/creditos/admin-story-configuracao-card.tsx',
  storyApi: '../src/lib/admin-stories-api.ts',
  premium: '../src/app/(painel-admin)/admin/creditos/admin-premium-catalogo.tsx',
  pacotes: '../src/app/(painel-admin)/admin/components/plano-credito-manager.tsx',
  saldos: '../src/app/(painel-admin)/admin/creditos/admin-saldos-ajustes.tsx',
  ativacoes: '../src/app/(painel-admin)/admin/creditos/admin-ativacoes-historico.tsx',
  api: '../src/lib/admin-creditos-operacionais-api.ts',
  tabs: '../src/components/ui/tabs.tsx',
  anuncios: '../src/app/(painel-admin)/admin/components/anuncios/anuncios-table.tsx',
  dashboard: '../../backend/src/main/java/br/com/topsdojob/v3/application/admin/dashboard/AdminDashboardAnalyticsService.java',
  premiumService: '../../backend/src/main/java/br/com/topsdojob/v3/application/admin/premium/AdminPremiumCatalogoService.java',
  premiumRepository: '../../backend/src/main/java/br/com/topsdojob/v3/persistence/repository/BeneficioPremiumRepository.java',
  premiumRequest: '../../backend/src/main/java/br/com/topsdojob/v3/application/admin/premium/dto/AdminPremiumCatalogoUpdateRequest.java',
  openapi: '../../contracts/openapi/topsdojob-v3-local.yaml',
}).map(async ([key, path]) => [key, await read(path)])))

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

check('1. menu possui uma entrada Monetização', () => {
  assert.equal((files.sidebar.match(/label: 'Monetização'/g) || []).length, 1)
  assert.match(files.sidebar, /label: 'Monetização'[\s\S]*href: '\/admin\/creditos'/)
})
check('2. entradas antigas saíram do menu', () => {
  assert.doesNotMatch(files.sidebar, /label: 'Planos e créditos'|label: 'Benefícios premium'/)
})
check('3. rota legada redireciona para Benefícios', () => {
  assert.match(files.redirect, /new URLSearchParams\(\{ aba: 'beneficios' \}\)/)
  assert.match(files.redirect, /redirect\(`\/admin\/creditos\?\$\{destino\.toString\(\)\}`\)/)
})
check('4. rota legada preserva somente anuncioId UUID válido e único', () => {
  assert.match(files.redirect, /anuncioIdLegadoSeguro/)
  assert.match(files.navigation, /UUID_CANONICO/)
  assert.match(files.navigation, /typeof value === 'string'/)
  assert.match(files.redirect, /destino\.set\('anuncioId', anuncioId\)/)
})
check('5. rota canônica exibe título Monetização', () => {
  assert.match(files.page, /<h1[^>]*>Monetização<\/h1>/)
})
check('6. aba padrão é Benefícios Premium', () => {
  assert.match(files.navigation, /: 'beneficios'/)
})
check('7. página possui as cinco abas obrigatórias', () => {
  for (const aba of ['stories', 'beneficios', 'pacotes', 'saldos', 'ativacoes']) {
    assert.match(files.page, new RegExp(`value="${aba}"`))
  }
})
check('8. Stories possui deep link estável', () => {
  assert.match(files.page, /adminMonetizacaoQuery\(searchParams, value\)/)
  assert.match(files.page, /value="stories"/)
})
check('9. Benefícios possui deep link estável e links internos canônicos', () => {
  assert.match(files.anuncios, /\/admin\/creditos\?aba=beneficios&anuncioId=/)
  assert.match(files.dashboard, /\/admin\/creditos\?aba=beneficios/)
})
check('10. URL acompanha tabs e suporta histórico', () => {
  assert.match(files.page, /router\.push\(/)
  assert.match(files.page, /router\.replace\(/)
  assert.match(files.page, /useSearchParams/)
  assert.match(files.page, /queryCanonica/)
})
check('11. query inválida recebe fallback seguro', () => {
  assert.match(files.navigation, /getAll\('aba'\)/)
  assert.match(files.navigation, /valores\.length === 1/)
  assert.match(files.navigation, /: 'beneficios'/)
})
check('12. cada aba monta apenas o componente do próprio domínio', () => {
  assert.match(files.page, /value="stories"[\s\S]*AdminStoryConfiguracaoCard/)
  assert.match(files.page, /value="beneficios"[\s\S]*AdminPremiumCatalogo/)
  assert.match(files.page, /value="pacotes"[\s\S]*PlanoCreditoManager/)
  assert.match(files.page, /value="saldos"[\s\S]*AdminSaldosAjustes/)
  assert.match(files.page, /value="ativacoes"[\s\S]*AdminAtivacoesHistorico/)
})
check('13. Stories usa GET e PUT próprios', () => {
  assert.match(files.storyApi, /fetchAdminStoryConfiguracao[\s\S]*'\/configuracao'/)
  assert.match(files.storyApi, /saveAdminStoryConfiguracao[\s\S]*method: 'PUT'/)
})
check('14. Stories mantém duração fixa de 24 horas', () => {
  assert.match(files.story, /24 horas — fixa/)
  assert.doesNotMatch(files.story, /setDuracao|name="duracao"|duracaoHoras:/)
})
check('15. custo vazio não equivale a zero', () => {
  assert.match(files.story, /if \(!value\.trim\(\)\) return null/)
  assert.match(files.story, /parsed >= 0/)
})
check('16. desativação de Stories preserva direitos e ativos', () => {
  assert.match(files.story, /Direitos já adquiridos e Stories ativos serão preservados/)
})
check('17. Stories fica fora do catálogo Premium genérico', () => {
  assert.match(files.premium, /item\.codigo !== 'STORIES'/)
  assert.doesNotMatch(files.premium, /AdminStory|\/stories/)
})
check('18. catálogo Premium oferece somente 1 7 14 e 30 dias', () => {
  assert.match(files.premium, /const DURACOES = \[1, 7, 14, 30\] as const/)
})
check('19. custos Premium vêm do backend sem preço padrão', () => {
  assert.match(files.premium, /String\(opcao\.custoCreditos\)/)
  assert.match(files.premium, /custoCreditos: ''/)
  assert.doesNotMatch(files.premium, /custoCreditos:\s*[1-9][0-9]*/)
})
check('20. salvamento Premium ocorre por benefício', () => {
  assert.match(files.premium, /atualizarCatalogo\(item\.id, payload\)/)
  assert.match(files.premium, /Salvar benefício/)
  assert.doesNotMatch(files.premium, /Salvar tudo/)
})
check('21. card Premium permite restaurar apenas seus valores', () => {
  assert.match(files.premium, /const restaurar = \(id: string\)/)
  assert.match(files.premium, /Restaurar valores/)
})
check('22. alterações não salvas e sucesso são isolados', () => {
  assert.match(files.premium, /Alterações não salvas/)
  assert.match(files.premium, /sucessos\[item\.id\]/)
  assert.match(files.story, /Alterações não salvas/)
})
check('23. concorrência Premium usa timestamp lock e 409', () => {
  assert.match(files.api, /atualizadoEm: string/)
  assert.match(files.premiumRequest, /OffsetDateTime atualizadoEm/)
  assert.match(files.premiumService, /findByIdForUpdate/)
  assert.match(files.premiumService, /HttpStatus\.CONFLICT/)
  assert.match(files.premium, /error\.status === 409/)
})
check('24. pacotes usam componente e endpoints próprios', () => {
  assert.match(files.pacotes, /Pacotes de créditos/)
  assert.match(files.api, /\/creditos\/pacotes/)
  assert.doesNotMatch(files.pacotes, /AdminCreditosApi\.(ajustar|ativacoes|catalogo)/)
})
check('25. saldos mantêm busca ledger motivo e idempotência', () => {
  assert.match(files.saldos, /buscarUsuarios/)
  assert.match(files.saldos, /AdminCreditosApi\.saldo/)
  assert.match(files.saldos, /AdminCreditosApi\.movimentos/)
  assert.match(files.saldos, /motivo\.trim\(\)\.length < 5/)
  assert.match(files.saldos, /ajusteTentativa/)
  assert.match(files.saldos, /estornoTentativas/)
  assert.match(files.saldos, /adminOperationKey/)
  assert.match(files.api, /Idempotency-Key/)
})
check('26. ativações e auditoria possuem fonte própria', () => {
  assert.match(files.ativacoes, /AdminCreditosApi\.ativacoes/)
  assert.match(files.ativacoes, /AdminCreditosApi\.auditoria/)
  assert.match(files.ativacoes, /cancelarAtivacao/)
  assert.match(files.ativacoes, /cancelamentoTentativas/)
})
check('27. concessão ADMIN não é classificada como receita', () => {
  assert.match(files.ativacoes, /não constituem receita/)
  assert.match(files.ativacoes, /ADMIN' \? ' \(não é receita\)'/)
})
check('28. loading e retry são isolados por aba', () => {
  assert.match(files.story, /Tentar novamente/)
  assert.match(files.premium, /Tentar novamente/)
  assert.match(files.saldos, /Tentar novamente/)
  assert.match(files.ativacoes, /Tentar novamente/)
})
check('29. buscas ignoram respostas obsoletas', () => {
  assert.match(files.pacotes, /cargaSeq = useRef\(0\)/)
  assert.match(files.pacotes, /seq === cargaSeq\.current/)
  assert.match(files.saldos, /buscaSeq/)
  assert.match(files.saldos, /usuarioSeq/)
  assert.match(files.saldos, /usuarioSelecionadoId\.current === usuario\.id/)
  assert.match(files.ativacoes, /buscaSeq/)
  assert.match(files.ativacoes, /ativacaoSeq/)
})
check('30. APIs mutáveis preservam CSRF sessão e no-store', () => {
  assert.match(files.api, /antiForgeryHeaderName/)
  assert.match(files.api, /credentials: 'include'/)
  assert.match(files.api, /cache: 'no-store'/)
})
check('31. duplo clique possui trava síncrona', () => {
  assert.match(files.story, /saveLock = useRef\(false\)/)
  assert.match(files.premium, /locks = useRef\(new Set<string>\(\)\)/)
  assert.match(files.pacotes, /saveLock = useRef\(false\)/)
  assert.match(files.pacotes, /statusLock = useRef\(false\)/)
  assert.match(files.saldos, /operacaoLock = useRef\(false\)/)
  assert.match(files.ativacoes, /cancelamentoLock = useRef\(new Set<string>\(\)\)/)
})
check('32. tabs e estados usam semântica acessível', () => {
  assert.match(files.tabs, /@radix-ui\/react-tabs/)
  assert.match(files.story + files.premium + files.saldos + files.ativacoes, /role="alert"/)
  assert.match(files.story + files.premium + files.saldos + files.ativacoes, /role="status"/)
})
check('33. layouts evitam tabela larga em 390 px', () => {
  assert.doesNotMatch(files.premium, /<table/)
  assert.match(files.pacotes, /md:hidden/)
  assert.match(files.saldos, /md:hidden/)
  assert.match(files.page, /overflow-x-auto/)
})
check('34. unificação não amplia RBAC financeiro', () => {
  assert.match(files.sidebarUtils, /'\/admin\/creditos'/)
  assert.match(files.sidebarUtils, /MODERATOR_RESTRICTED_ROUTES/)
})
check('35. não existe endpoint ou DTO monolítico de Monetização', () => {
  assert.doesNotMatch(files.api, /\/monetizacao\/admin|monetizacaoResumo|MonetizacaoAdminDto/i)
  assert.doesNotMatch(files.openapi, /\/api\/admin\/monetizacao:/)
})
check('36. OpenAPI documenta concorrência sem migration nova', () => {
  assert.match(files.openapi, /AdminPremiumCatalogoUpdateRequest:[\s\S]*atualizadoEm: \{ type: string, format: date-time \}/)
  assert.match(files.openapi, /PremiumCatalogo:[\s\S]*atualizadoEm: \{ type: string, format: date-time \}/)
})
check('37. query canônica descarta parâmetros alheios e duplicados', () => {
  assert.match(files.navigation, /new URLSearchParams\(\{ aba \}\)/)
  assert.match(files.navigation, /getAll\('anuncioId'\)/)
  assert.match(files.navigation, /anuncioIds\.length === 1/)
  assert.match(files.page, /searchParams\.toString\(\) === queryCanonica/)
})
check('38. opções Premium legadas permanecem visíveis e fora do payload canônico', () => {
  assert.match(files.premium, /opcoesLegadas/)
  assert.match(files.premium, /Opções legadas preservadas/)
  assert.match(files.premium, /não são convertidas ou excluídas/)
  assert.match(files.premium, /const opcoes = item\.opcoes/)
})
check('39. ativações possuem filtros completos e paginação local', () => {
  assert.match(files.ativacoes, /filtroAnuncio/)
  assert.match(files.ativacoes, /filtroOrigem/)
  assert.match(files.ativacoes, /periodoInicio/)
  assert.match(files.ativacoes, /ATIVACOES_POR_PAGINA/)
  assert.match(files.ativacoes, /paginadas/)
})
check('40. concorrência Premium normaliza a precisão do PostgreSQL', () => {
  assert.match(files.premiumService, /ChronoUnit\.MICROS/)
  assert.match(files.premiumService, /normalizarMarcador/)
  assert.match(files.premiumService, /plusNanos\(1_000\)/)
})
check('41. OpenAPI separa duração legada de request canônico e documenta 409', () => {
  assert.match(files.openapi, /PremiumOpcao:[\s\S]*duracaoDias: \{ type: integer, minimum: 1 \}/)
  assert.match(files.openapi, /AdminPremiumCatalogoOpcaoRequest:[\s\S]*duracaoDias: \{ type: integer, enum: \[1, 7, 14, 30\] \}/)
  const catalogoPath = files.openapi.slice(
    files.openapi.indexOf('/api/admin/premium/catalogo/{id}:'),
    files.openapi.indexOf('/api/admin/premium/ativacoes:'),
  )
  assert.match(catalogoPath, /"409":[\s\S]*Conflict/)
})

assert.equal(checks, 41)
console.log(`ADMIN_MONETIZACAO_UNIFICADA_CHECKS=${checks}`)
console.log('ADMIN_MONETIZACAO_UNIFICADA_RESULT=OK')