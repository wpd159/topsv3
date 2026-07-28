import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const dashboardPath = new URL(
  '../src/app/(painel-admin)/admin/components/dashboard/strategic/StrategicAdminDashboard.tsx',
  import.meta.url,
)
const apiPath = new URL('../src/lib/admin-dashboard-api.ts', import.meta.url)
const pagePath = new URL('../src/app/(painel-admin)/admin/dashboard/page.tsx', import.meta.url)
const [dashboard, api, page] = await Promise.all([
  readFile(dashboardPath, 'utf8'),
  readFile(apiPath, 'utf8'),
  readFile(pagePath, 'utf8'),
])

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
assert.match(api, /credentials: 'include'/)
assert.match(api, /cache: 'no-store'/)

console.log('Dashboard administrativo: fontes reais, RBAC, atalhos e falhas parciais validados.')
