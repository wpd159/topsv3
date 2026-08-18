import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const modal = fs.readFileSync(
  path.join(root, 'src/components/modals/age-gate-modal.tsx'),
  'utf8',
)
const verification = fs.readFileSync(
  path.join(root, 'src/components/compliance/visitor-verification-modal.tsx'),
  'utf8',
)
const api = fs.readFileSync(
  path.join(root, 'src/lib/compliance/age-gate-api.ts'),
  'utf8',
)
const access = fs.readFileSync(
  path.join(root, 'src/lib/compliance/visitor-access.ts'),
  'utf8',
)
const sensitiveImage = fs.readFileSync(
  path.join(root, 'src/components/compliance/sensitive-image.tsx'),
  'utf8',
)
const globalStyles = fs.readFileSync(
  path.join(root, 'src/app/globals.css'),
  'utf8',
)
const grid = fs.readFileSync(
  path.join(root, 'src/components/anuncios/anuncios-grid.tsx'),
  'utf8',
)
const sidebar = fs.readFileSync(
  path.join(root, 'src/app/(public-routes)/anuncios/[slug]/componentes/sidebar.tsx'),
  'utf8',
)

assert.doesNotMatch(modal, /getGlobalAgeGateStatus/)
assert.match(modal, /acceptGlobalAgeGate/)
assert.match(modal, /recarregarStatusVisitante\(\)/)
assert.match(modal, /notificarMudancaVerificacao\(visitorStatus\)/)
assert.match(modal, /Nao foi possivel confirmar o aceite\. Tente novamente\./)
assert.doesNotMatch(modal, /estado canonico/i)
assert.match(modal, /SafeInstitutionalText/)
assert.match(modal, />\s*Sair\s*</)
assert.match(modal, /Aceitar/)
assert.doesNotMatch(modal, /VisitorVerificationModal/)
assert.doesNotMatch(modal, /age-gate-storage|age_gate_accepted/)
assert.match(verification, /placeholder="dd\/mm\/aaaa"/)
assert.match(verification, /confirmacaoDataNascimento/)
assert.match(verification, /placeholder="000\.000\.000-00"/)
assert.match(verification, /aceiteMaioridade/)
assert.match(verification, /aceiteConteudoRestrito/)
assert.match(verification, /aceitePrivacidade/)
assert.match(verification, /submitVisitorDocument/)
assert.match(verification, /DOCUMENT_PENDING/)
assert.match(verification, /DOCUMENT_APPROVED/)
assert.match(verification, /12 \* 1024 \* 1024/)
assert.match(verification, /max-h-\[92vh\]/)
assert.match(verification, /overflow-y-auto/)
assert.match(verification, /obterStatusVisitante\(true\)/)
assert.doesNotMatch(verification, /getGlobalAgeGateStatus/)

for (const endpoint of [
  '/compliance/age-gate/accept',
  '/compliance/age-gate/status',
  '/compliance/visitor/challenge',
  '/compliance/visitor/verify',
  '/compliance/visitor/status',
  '/compliance/visitor/document',
  '/compliance/visitor/revoke',
]) {
  assert.match(api, new RegExp(endpoint.replaceAll('/', '\\/')))
}

assert.match(api, /XSRF/)
assert.match(api, /credentials: 'include'/)
assert.match(api, /response\.status === 410/)
assert.match(access, /getVisitorStatus/)
assert.match(access, /CACHE_TTL_MS/)
assert.match(access, /let statusGeneration = 0/)
assert.match(access, /if \(pendingRequest\) return pendingRequest/)
assert.match(access, /requestGeneration !== statusGeneration/)
assert.match(access, /cacheStatus \?\? \{ verified: false \}/)
assert.match(access, /statusGeneration \+= 1/)
assert.match(
  access,
  /export function recarregarStatusVisitante\(\): Promise<StatusVisitante> \{\s*limparCacheStatusVisitante\(\)\s*return refreshStatus\(\)\s*\}/,
)
assert.doesNotMatch(`${api}\n${access}`, /localStorage|sessionStorage/)
assert.doesNotMatch(`${api}\n${access}\n${verification}`, /\/idade\/confirmar|\/idade\/status/)
assert.match(sensitiveImage, /\/compliance\/visitor\/media\//)
assert.match(sensitiveImage, /void obterStatusVisitante\(\)/)
assert.doesNotMatch(sensitiveImage, /obterStatusVisitante\(true\)/)
assert.match(sensitiveImage, /loadingProtectedMedia/)
assert.match(sensitiveImage, /role="status"/)
assert.match(sensitiveImage, /Carregando conteúdo protegido/)
assert.match(sensitiveImage, /onLoad=\{\(\) =>/)
assert.doesNotMatch(grid, /onAccessUpdated=/)
assert.match(sidebar, /scope="WHATSAPP"/)
assert.doesNotMatch(sensitiveImage, /urlAssinada|chaveObjeto|private.*url/i)

const restrictedOverlayRule = globalStyles.match(
  /\.compliance-restricted-overlay\s*\{([\s\S]*?)\}/,
)?.[1]
assert.ok(restrictedOverlayRule, 'O overlay de conteudo restrito deve continuar definido.')
assert.match(restrictedOverlayRule, /background:/)
assert.doesNotMatch(
  restrictedOverlayRule,
  /backdrop-filter/i,
  'O overlay nao pode criar uma camada de backdrop que fique stale apos o desbloqueio.',
)

const instrumentedAccess = access.replace(
  /import \{\s*getVisitorStatus,\s*type VisitorAccessStatus,\s*\} from '@\/lib\/compliance\/age-gate-api'/,
  'const getVisitorStatus = () => globalThis.__ageGateStatusRequest()\ntype VisitorAccessStatus = any',
)
const compiledAccess = ts.transpileModule(instrumentedAccess, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText
const pendingStatusRequests = []
globalThis.__ageGateStatusRequest = () => new Promise((resolve) => {
  pendingStatusRequests.push(resolve)
})
const accessModule = await import(
  `data:text/javascript;base64,${Buffer.from(compiledAccess).toString('base64')}`
)
const staleRequest = accessModule.obterStatusVisitante(true)
const canonicalRequest = accessModule.recarregarStatusVisitante()
assert.equal(
  pendingStatusRequests.length,
  2,
  'A confirmacao pos-aceite deve iniciar uma consulta nova, sem reutilizar a request stale.',
)
pendingStatusRequests[0]({ globalAccepted: false, verified: false })
pendingStatusRequests[1]({ globalAccepted: true, verified: false })
await staleRequest
assert.equal((await canonicalRequest).globalAccepted, true)
assert.equal((await accessModule.obterStatusVisitante()).globalAccepted, true)
assert.equal(pendingStatusRequests.length, 2)
delete globalThis.__ageGateStatusRequest

console.log('OK_AGE_GATE_BACKEND_FONTE_UNICA')
