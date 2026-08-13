import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const dashboardPath = new URL(
  '../src/app/(painel-admin)/admin/components/dashboard/strategic/StrategicAdminDashboard.tsx',
  import.meta.url,
)
const apiPath = new URL('../src/lib/admin-dashboard-api.ts', import.meta.url)
const pagePath = new URL('../src/app/(painel-admin)/admin/dashboard/page.tsx', import.meta.url)
const analyticsPath = new URL(
  '../src/app/(painel-admin)/admin/components/dashboard/strategic/AdminDashboardAnalytics.tsx',
  import.meta.url,
)
const components = [
  'StrategicPerformanceChart.tsx',
  'TopWhatsappHojeCard.tsx',
  'PriorityAlertsCard.tsx',
  'CommercialOpportunitiesCard.tsx',
  'StrategicConversionRankings.tsx',
  'StrategicAnalysisTables.tsx',
].map((name) => new URL(
  `../src/app/(painel-admin)/admin/components/dashboard/strategic/${name}`,
  import.meta.url,
))
const [dashboard, api, page, analytics, ...componentSources] = await Promise.all([
  readFile(dashboardPath, 'utf8'),
  readFile(apiPath, 'utf8'),
  readFile(pagePath, 'utf8'),
  readFile(analyticsPath, 'utf8'),
  ...components.map((path) => readFile(path, 'utf8')),
])
const analyticUi = componentSources.join('\n')

assert.match(page, /StrategicAdminDashboard/)
assert.doesNotMatch(dashboard, /BackendContractPendingError|dados simulados.*\d|mock|placeholder/i)
assert.doesNotMatch(dashboard, /fetchPremiumBenefitsDashboard|fetchDesempenhoDiario|fetchAdminPerformanceAnuncios/)
assert.match(dashboard, /getAdminUserIndicators/)
assert.match(dashboard, /fetchDashboardAnuncios/)
assert.match(dashboard, /fetchDashboardModeracao/)
assert.match(dashboard, /fetchDashboardMidias/)
assert.match(dashboard, /buscarIndicadoresTickets/)
assert.match(dashboard, /buscarIndicadoresDenuncias/)
assert.match(dashboard, /buscarIndicadoresSugestoes/)
assert.match(dashboard, /fetchDashboardHoje/)
assert.match(dashboard, /Promise\.allSettled\(requests\)/)
assert.match(dashboard, /ANUNCIO_LER/)
assert.match(dashboard, /ANUNCIO_MODERAR/)
assert.match(dashboard, /DOCUMENTO_REVISAR/)
assert.match(dashboard, /MIDIA_REVISAR/)
assert.match(dashboard, /SUPORTE_ATENDER/)
assert.match(dashboard, /\/admin\/anuncios\?situacao=PENDENTES_MODERACAO/)
assert.match(dashboard, /\/admin\/usuarios\?kyc=PENDENTE/)
assert.match(dashboard, /\/admin\/denuncias\?status=PENDENTE/)
assert.match(dashboard, /\/admin\/tickets\?status=ABERTOS/)
assert.match(dashboard, /\/admin\/sugestoes\?status=PENDENTE/)
assert.match(dashboard, /sm:grid-cols-2 xl:grid-cols-4/)
assert.doesNotMatch(dashboard, /overflow-x-auto|w-\[\d+px\]/)
assert.match(api, /\/dashboard\/hoje/)
assert.match(api, /\/dashboard\/desempenho-diario\?dias=/)
assert.match(api, /\/dashboard\/top-whatsapp-hoje\?limite=/)
assert.match(api, /\/dashboard\/analises/)
assert.match(api, /credentials: 'include'/)
assert.match(api, /cache: 'no-store'/)
assert.match(dashboard, /<AdminDashboardAnalytics/)
const cardsIndex = dashboard.indexOf('cards.map')
const performanceIndex = dashboard.indexOf('mode="performance"')
const analyticsIndex = dashboard.indexOf('mode="analytics"')
assert.ok(
  performanceIndex > -1 && performanceIndex < cardsIndex,
  'O grafico de desempenho deve aparecer antes dos cards de indicadores.',
)
assert.ok(
  analyticsIndex > cardsIndex,
  'Rankings e analises devem permanecer depois dos cards de indicadores.',
)
assert.equal((dashboard.match(/<AdminDashboardAnalytics/g) ?? []).length, 2)
assert.match(analytics, /mode: 'performance' \| 'analytics'/)
assert.match(analytics, /mode !== 'performance'/)
assert.match(analytics, /mode !== 'analytics'/)
assert.match(analytics, /dailyError/)
assert.match(analytics, /analysesError/)
assert.match(analytics, /papeis\.includes\('ADMIN'\)/)
assert.doesNotMatch(analytics + analyticUi, /admin-estatisticas-api|Modera[cç][aã]o v2|BackendContractPendingError/i)
assert.match(analyticUi, /\(\[7, 15, 30\] as const\)/)
assert.match(analyticUi, /Barras: visualizações/)
assert.match(analyticUi, /Linha: cliques no WhatsApp/)
assert.match(analyticUi, /Conversão:/)
assert.match(analyticUi, /data\?\.temMais/)
assert.match(analyticUi, /MAX_LIMIT = 48/)
assert.match(analyticUi, /\/admin\/anuncios\/\$\{item\.anuncioId\}/)
assert.match(analyticUi, /item\.publicado/)
assert.match(
  analyticUi,
  /classificacaoRows\.map[\s\S]*row\.cliquesWhatsapp\.toLocaleString\('pt-BR'\)/,
)
assert.match(analyticUi, /overflow-x-auto/)
assert.doesNotMatch(analyticUi, /mock|placeholder|GA4.*fetch|window\.innerWidth/i)

console.log('Dashboard administrativo: cards existentes e analytics reais, coesos e responsivos validados.')
