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
assert.match(modal, /confirmarAceiteGlobal\(pathname \|\| '\/'\)/)
assert.doesNotMatch(modal, /acceptGlobalAgeGate|recarregarStatusVisitante|notificarMudancaVerificacao/)
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
assert.match(verification, /recarregarStatusVisitante\(\)/)
assert.doesNotMatch(verification, /getVisitorStatus/)
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
assert.match(access, /acceptGlobalAgeGate/)
assert.match(access, /getVisitorStatus/)
assert.match(access, /CACHE_TTL_MS/)
assert.match(access, /let statusGeneration = 0/)
assert.match(access, /if \(pendingRequest\) return pendingRequest\.promise/)
assert.match(access, /requestGeneration !== statusGeneration/)
assert.match(access, /authoritativeStatusAfter\(requestGeneration\)/)
assert.match(access, /latestRequest\.generation > requestGeneration/)
assert.match(access, /AGE_GATE_ACCEPTANCE_NOT_CONFIRMED/)
assert.match(access, /statusGeneration \+= 1/)
assert.match(
  access,
  /export function recarregarStatusVisitante\(\): Promise<StatusVisitante> \{\s*limparCacheStatusVisitante\(\)\s*return refreshStatus\(\)\s*\}/,
)
assert.doesNotMatch(`${api}\n${access}`, /localStorage|sessionStorage/)
assert.doesNotMatch(`${api}\n${access}\n${verification}`, /\/idade\/confirmar|\/idade\/status/)
assert.match(sensitiveImage, /\/compliance\/visitor\/media\//)
assert.match(sensitiveImage, /const request = force \? obterStatusVisitante\(true\) : obterStatusVisitante\(\)/)
assert.match(sensitiveImage, /void request\s*\.then/)
assert.match(sensitiveImage, /generation !== obterGeracaoStatusVisitante\(\)/)
assert.match(sensitiveImage, /sequence !== requestSequence\.current/)
assert.match(sensitiveImage, /if \(!force\) setErro\(false\)/)
assert.equal(
  (sensitiveImage.match(/obterStatusVisitante\(true\)/g) ?? []).length,
  1,
  'Erro de mídia protegida pode consultar o status autoritativo uma única vez.',
)
assert.match(sensitiveImage, /statusCheckedAfterError\.current/)
assert.doesNotMatch(sensitiveImage, /setSessionAuthorized\(false\)/)
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
  /import \{[\s\S]*?\} from '@\/lib\/compliance\/age-gate-api'/,
  [
    'const acceptGlobalAgeGate = (originPath) => globalThis.__ageGateAcceptRequest(originPath)',
    'const getVisitorStatus = () => globalThis.__ageGateStatusRequest()',
    'type VisitorAccessStatus = any',
  ].join('\n'),
).replace(
  /import type \{ StatusEscopoVisitante \} from '@\/lib\/compliance\/visitor-access-policy'/,
  'type StatusEscopoVisitante = any',
).replace(
  /export \{[\s\S]*?\} from '@\/lib\/compliance\/visitor-access-policy'/,
  [
    '// A política central possui cobertura própria; este harness exercita o cache concorrente.',
    'export const statusSatisfazEscopo = () => false',
    'export type EscopoAcessoVisitante = any',
    'export type NivelAcessoVisitante = any',
  ].join('\n'),
)
const compiledAccess = ts.transpileModule(instrumentedAccess, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText
let pendingStatusRequests = []
let pendingAcceptRequests = []
let moduleSequence = 0

function resetRequests() {
  pendingStatusRequests = []
  pendingAcceptRequests = []
}

globalThis.__ageGateStatusRequest = () => new Promise((resolve, reject) => {
  pendingStatusRequests.push({ resolve, reject })
})
globalThis.__ageGateAcceptRequest = () => new Promise((resolve, reject) => {
  pendingAcceptRequests.push({ resolve, reject })
})

function visitorStatus(globalAccepted) {
  return {
    globalAccepted,
    verified: false,
    explicitVerified: false,
    state: globalAccepted ? 'GLOBAL_ACEITO' : 'GLOBAL_NAO_ACEITO',
  }
}

async function loadAccessModule() {
  moduleSequence += 1
  return import(
    `data:text/javascript;base64,${Buffer.from(compiledAccess).toString('base64')}#${moduleSequence}`
  )
}

// A: a leitura antiga termina depois da nova e deve receber a decisao vigente.
resetRequests()
const accessA = await loadAccessModule()
const staleAfterNew = accessA.obterStatusVisitante(true)
const newBeforeStale = accessA.recarregarStatusVisitante()
assert.equal(pendingStatusRequests.length, 2)
pendingStatusRequests[1].resolve(visitorStatus(true))
assert.equal((await newBeforeStale).globalAccepted, true)
pendingStatusRequests[0].resolve(visitorStatus(false))
assert.equal((await staleAfterNew).globalAccepted, true)
assert.equal((await accessA.obterStatusVisitante()).globalAccepted, true)

// B: consumidores simultaneos compartilham uma unica consulta canonica.
resetRequests()
const accessB = await loadAccessModule()
const simultaneousOne = accessB.obterStatusVisitante(true)
const simultaneousTwo = accessB.obterStatusVisitante(true)
assert.equal(pendingStatusRequests.length, 1)
pendingStatusRequests[0].resolve(visitorStatus(true))
assert.equal((await simultaneousOne).globalAccepted, true)
assert.equal((await simultaneousTwo).globalAccepted, true)

// C: o POST invalida a leitura pendente; a resposta antiga aguarda a revisao nova.
resetRequests()
const accessC = await loadAccessModule()
const pendingBeforeMutation = accessC.obterStatusVisitante(true)
const confirmation = accessC.confirmarAceiteGlobal('/')
assert.equal(pendingAcceptRequests.length, 1)
pendingAcceptRequests[0].resolve({ accepted: true, state: 'GLOBAL_ACEITO' })
await Promise.resolve()
assert.equal(pendingStatusRequests.length, 2)
pendingStatusRequests[0].resolve(visitorStatus(false))
pendingStatusRequests[1].resolve(visitorStatus(true))
assert.equal((await pendingBeforeMutation).globalAccepted, true)
assert.equal((await confirmation).globalAccepted, true)

// D: refetch automatico posterior nao regride o aceite confirmado.
const automaticRefetch = accessC.recarregarStatusVisitante()
assert.equal(pendingStatusRequests.length, 3)
pendingStatusRequests[2].resolve(visitorStatus(true))
assert.equal((await automaticRefetch).globalAccepted, true)
assert.equal((await accessC.obterStatusVisitante()).globalAccepted, true)

// E: uma nova instancia, equivalente a reload/navegacao, reconhece o cookie no servidor.
resetRequests()
const accessE = await loadAccessModule()
const afterReload = accessE.obterStatusVisitante(true)
pendingStatusRequests[0].resolve(visitorStatus(true))
assert.equal((await afterReload).globalAccepted, true)

// Uma confirmacao que nao reaparece no estado canonico continua fail-closed.
resetRequests()
const accessFailClosed = await loadAccessModule()
const rejectedConfirmation = accessFailClosed.confirmarAceiteGlobal('/')
pendingAcceptRequests[0].resolve({ accepted: true, state: 'GLOBAL_ACEITO' })
await Promise.resolve()
pendingStatusRequests[0].resolve(visitorStatus(false))
await assert.rejects(rejectedConfirmation, /AGE_GATE_ACCEPTANCE_NOT_CONFIRMED/)

delete globalThis.__ageGateStatusRequest
delete globalThis.__ageGateAcceptRequest

console.log('OK_AGE_GATE_BACKEND_FONTE_UNICA')
