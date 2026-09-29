import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const ts = require('typescript')
const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = fs.readFileSync(path.join(frontendRoot, 'src/lib/admin-registros-api.ts'), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText

const finalidade = 'AUDITORIA_INTERNA'
const pagina = { itens: [], page: 1, size: 20, totalElements: 21, totalPages: 2, last: true }
const storyPagina = { itens: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true }
const registro = { id: 'abc-123', anuncioId: 'anuncio-1', versoes: [] }
const story = { id: 'story-registro-1', storyId: 'story-1', versoes: [] }
const exportacao = { bytes: 'arquivo sintético' }
const calls = []
const auditEvents = []
const document = { cookie: '' }
const session = {
  environment: 'local',
  active: true,
  canRead: true,
  canExport: true,
  issueCsrfCookie: true,
  invalidPayload: false,
  reportTooLarge: false,
  incompleteReport: false,
}

function syntheticCookie(value) {
  return ['XSRF', 'TOKEN'].join('-') + '=' + value
}

class ApiContractError extends Error {
  constructor(message, kind, status) {
    super(message)
    this.kind = kind
    this.status = status
  }
}

function reply(status, payload) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => payload,
    blob: async () => payload,
  }
}

function configure(overrides = {}, cookie = '') {
  Object.assign(session, {
    environment: 'local',
    active: true,
    canRead: true,
    canExport: true,
    issueCsrfCookie: true,
    invalidPayload: false,
    reportTooLarge: false,
    incompleteReport: false,
  }, overrides)
  document.cookie = cookie
  calls.length = 0
  auditEvents.length = 0
}

async function syntheticServer(url, options = {}) {
  calls.push({ url, options })
  const parsed = new URL(url, 'https://synthetic.invalid')
  assert.equal(options.credentials, 'include')
  assert.equal(options.cache, 'no-store')
  if (parsed.pathname === '/api/admin/auth/me') {
    assert.equal(options.method, undefined)
    if (!session.active) return reply(401)
    if (session.environment === 'protected' && session.issueCsrfCookie) {
      document.cookie = syntheticCookie('EXEMPLO_NAO_REAL')
    }
    return reply(200, { id: 'admin-sintetico' })
  }

  assert.match(parsed.pathname, /^\/api\/admin\/registros\/(publicidade|stories)(\/|$)/)
  assert.equal(options.method, 'POST')
  if (session.environment === 'protected'
      && options.headers.get('X-XSRF-TOKEN') !== 'EXEMPLO_NAO_REAL') {
    return reply(403)
  }
  if (!session.active) return reply(401)
  if (!session.canRead) return reply(403)
  const reporting = /\/relatorio$/.test(parsed.pathname)
  const exporting = reporting || /\/exportacao$|\/midias\/[^/]+\/arquivo$/.test(parsed.pathname)
  if (exporting && !session.canExport) return reply(403)
  if (parsed.searchParams.get('finalidade') !== finalidade) return reply(400)

  auditEvents.push({ path: parsed.pathname, finalidade })
  if (reporting) {
    const body = JSON.parse(options.body)
    assert.equal(options.headers.get('Content-Type'), 'application/json')
    assert.equal(body.fusoHorario, 'America/Sao_Paulo')
    if (session.reportTooLarge) return reply(413)
    return reply(200, { tipo: parsed.pathname.includes('/stories/') ? 'STORY' : 'ANUNCIO',
      geradoEm: '2026-09-29T03:00:00Z', fusoHorario: body.fusoHorario, responsavelId: 'admin-sintetico',
      finalidade, filtros: body.filtros, idsSelecionados: body.ids, quantidade: session.incompleteReport ? 2 : 1,
      limiteRegistros: 100, lacunas: ['Acervo prospectivo; sem reconstrução do cadastro atual.'],
      registros: [parsed.pathname.includes('/stories/') ? story : registro] })
  }
  if (exporting) return reply(200, exportacao)
  if (parsed.pathname === '/api/admin/registros/publicidade') {
    if (parsed.searchParams.has('termo')) {
      // A matching historical record is beyond the first page in the unfiltered inventory.
      const stableInventory = Array.from({ length: 41 }, (_, index) => ({ id: `registro-${index + 1}`, titulo: `Título ${index + 1}` }))
      const found = stableInventory.filter((item) => item.titulo === parsed.searchParams.get('termo'))
      return reply(200, { itens: found, page: Number(parsed.searchParams.get('page')), size: 20,
        totalElements: found.length, totalPages: found.length ? 1 : 0, last: true })
    }
    return reply(200, session.invalidPayload ? { page: 0 } : pagina)
  }
  if (parsed.pathname === '/api/admin/registros/stories') return reply(200, storyPagina)
  return reply(200, parsed.pathname.startsWith('/api/admin/registros/stories/') ? story : registro)
}

const vmModule = { exports: {} }
vm.runInNewContext(compiled, {
  module: vmModule,
  exports: vmModule.exports,
  AbortController,
  Headers,
  URL,
  URLSearchParams,
  document,
  fetch: syntheticServer,
  require: (specifier) => {
    assert.equal(specifier, '@/lib/api-contract')
    return {
      adminApiUrl: (endpoint) => `/api/admin${endpoint}`,
      apiErrorFromResponse: async (response) => new ApiContractError(
        `Servidor recusou a operação (${response.status})`,
        response.status === 401 ? 'SESSION_REQUIRED' : 'ACCESS_DENIED',
        response.status,
      ),
      ApiContractError,
    }
  },
})

const api = vmModule.exports

async function rejectedStatus(operation, status) {
  await assert.rejects(operation, (error) => {
    assert.equal(error.status, status)
    assert.equal(error.kind, status === 401 ? 'SESSION_REQUIRED' : 'ACCESS_DENIED')
    return true
  })
}

// The local backend disables CSRF and /auth/me therefore does not issue a cookie.
// The session and permission decision must still come from the server.
configure()
assert.deepEqual(await api.listarRegistrosPublicidade(1, finalidade), pagina)
assert.deepEqual(calls.map((call) => call.url), [
  '/api/admin/auth/me',
  '/api/admin/registros/publicidade?page=1&size=20&finalidade=AUDITORIA_INTERNA',
])
assert.equal(calls[1].options.headers.has('X-XSRF-TOKEN'), false)
assert.equal(auditEvents.length, 1)

// A protected backend issues the CSRF cookie on /auth/me and checks the header.
configure({ environment: 'protected' })
assert.deepEqual(await api.listarRegistrosStory(0, finalidade), storyPagina)
assert.equal(document.cookie, syntheticCookie('EXEMPLO_NAO_REAL'))
assert.equal(calls[1].options.headers.get('X-XSRF-TOKEN'), 'EXEMPLO_NAO_REAL')
assert.equal(auditEvents.length, 1)

// An existing valid cookie is used directly; every operation still reaches server auth.
configure({ environment: 'protected' }, syntheticCookie('EXEMPLO_NAO_REAL'))
assert.deepEqual(await api.detalharRegistroPublicidade(registro.id, finalidade), registro)
assert.equal(calls.at(-1).url, '/api/admin/registros/publicidade/abc-123?finalidade=AUDITORIA_INTERNA')
assert.equal(calls.length, 1)
assert.equal(await api.exportarRegistroPublicidade(registro.id, finalidade), exportacao)
assert.equal(calls.at(-1).url, '/api/admin/registros/publicidade/abc-123/exportacao?finalidade=AUDITORIA_INTERNA')
assert.equal(await api.baixarMidiaPublicidade(registro.id, 'midia-1', finalidade), exportacao)
assert.equal(calls.at(-1).url, '/api/admin/registros/publicidade/abc-123/midias/midia-1/arquivo?finalidade=AUDITORIA_INTERNA')
assert.equal(calls.at(-1).options.headers.get('Accept'), 'application/octet-stream')
assert.deepEqual(await api.detalharRegistroStory(story.id, finalidade), story)
assert.equal(calls.at(-1).url, '/api/admin/registros/stories/story-registro-1?finalidade=AUDITORIA_INTERNA')
assert.equal(await api.exportarRegistroStory(story.id, finalidade), exportacao)
assert.equal(calls.at(-1).url, '/api/admin/registros/stories/story-registro-1/exportacao?finalidade=AUDITORIA_INTERNA')
assert.equal(await api.baixarMidiaStory(story.id, 'story-midia-1', finalidade), exportacao)
assert.equal(calls.at(-1).url, '/api/admin/registros/stories/story-registro-1/midias/story-midia-1/arquivo?finalidade=AUDITORIA_INTERNA')
assert.equal(auditEvents.length, 6)

// No issued token, or an invalid token, is rejected by the protected backend.
configure({ environment: 'protected', issueCsrfCookie: false })
await rejectedStatus(api.listarRegistrosPublicidade(0, finalidade), 403)
assert.equal(calls.length, 2)
assert.equal(calls[1].options.headers.has('X-XSRF-TOKEN'), false)
assert.equal(auditEvents.length, 0)
configure({ environment: 'protected' }, syntheticCookie('CHANGE_ME'))
await rejectedStatus(api.listarRegistrosPublicidade(0, finalidade), 403)
assert.equal(calls.length, 1)
assert.equal(calls[0].options.headers.get('X-XSRF-TOKEN'), 'CHANGE_ME')
assert.equal(auditEvents.length, 0)

// Expired sessions remain 401 with or without a CSRF cookie.
configure({ active: false })
await rejectedStatus(api.listarRegistrosPublicidade(0, finalidade), 401)
assert.equal(calls.length, 1)
assert.equal(calls[0].url, '/api/admin/auth/me')
configure({ environment: 'protected', active: false }, syntheticCookie('EXEMPLO_NAO_REAL'))
await rejectedStatus(api.listarRegistrosPublicidade(0, finalidade), 401)
assert.equal(calls.length, 1)

// Read and export permissions are separate server decisions.
configure({ canRead: false })
await rejectedStatus(api.listarRegistrosPublicidade(0, finalidade), 403)
await rejectedStatus(api.detalharRegistroStory(story.id, finalidade), 403)
assert.equal(auditEvents.length, 0)
configure({ canExport: false }, syntheticCookie('EXEMPLO_NAO_REAL'))
assert.deepEqual(await api.listarRegistrosPublicidade(1, finalidade), pagina)
await rejectedStatus(api.exportarRegistroPublicidade(registro.id, finalidade), 403)
await rejectedStatus(api.baixarMidiaStory(story.id, 'story-midia-1', finalidade), 403)
assert.equal(auditEvents.length, 1)

// Invalid purpose is rejected before any transport, including /auth/me.
configure()
await assert.rejects(api.listarRegistrosPublicidade(0, undefined), /Selecione a finalidade/)
await assert.rejects(api.detalharRegistroPublicidade(registro.id, undefined), /Selecione a finalidade/)
await assert.rejects(api.exportarRegistroPublicidade(registro.id, 'texto livre'), /Selecione a finalidade/)
await assert.rejects(api.baixarMidiaPublicidade(registro.id, 'midia-1', undefined), /Selecione a finalidade/)
await assert.rejects(api.listarRegistrosStory(0, undefined), /Selecione a finalidade/)
await assert.rejects(api.detalharRegistroStory(story.id, 'texto livre'), /Selecione a finalidade/)
await assert.rejects(api.exportarRegistroStory(story.id, undefined), /Selecione a finalidade/)
await assert.rejects(api.baixarMidiaStory(story.id, 'story-midia-1', undefined), /Selecione a finalidade/)
assert.equal(calls.length, 0)

configure({ invalidPayload: true }, syntheticCookie('EXEMPLO_NAO_REAL'))
await assert.rejects(api.listarRegistrosPublicidade(0, finalidade), /registros incompatíveis/)

console.log('Admin registros: local sem cookie, CSRF protegido, sessão, permissões e finalidade OK')

configure({ environment: 'protected' }, syntheticCookie('EXEMPLO_NAO_REAL'))
const filters = { termo: 'Título 41', anuncioId: 'anuncio-1', anuncianteId: 'proprietario-1',
  beneficio: 'STORIES', situacao: 'ENCERRADA', inicio: '2026-09-01T00:00:00-03:00',
  fim: '2026-10-01T00:00:00-03:00', ordenacao: 'ANTIGOS' }
const found = await api.listarRegistrosPublicidade(0, finalidade, undefined, filters)
assert.equal(found.totalElements, 1)
assert.equal(found.itens[0].id, 'registro-41', 'Busca no servidor encontra registro fora da primeira página sem baixar todo inventário.')
const sentFilters = new URL(calls.at(-1).url, 'https://synthetic.invalid').searchParams
for (const [name, value] of Object.entries(filters)) assert.equal(sentFilters.get(name), value)
await api.listarRegistrosStory(2, finalidade, undefined, filters)
assert.equal(new URL(calls.at(-1).url, 'https://synthetic.invalid').searchParams.get('page'), '2')
assert.equal(new URL(calls.at(-1).url, 'https://synthetic.invalid').searchParams.get('beneficio'), 'STORIES')
const report = await api.prepararRelatorioRegistros('publicidade', finalidade, filters, [registro.id])
assert.equal(report.registros.length, 1)
assert.equal(report.quantidade, 1)
assert.deepEqual(JSON.parse(calls.at(-1).options.body), { filtros: filters, ids: [registro.id], fusoHorario: 'America/Sao_Paulo' })
assert.equal(await api.prepararRelatorioRegistros('stories', finalidade, filters).then((value) => value.tipo), 'STORY')
assert.deepEqual(JSON.parse(calls.at(-1).options.body).ids, [], 'Sem seleção, servidor deve avaliar todo o escopo filtrado, não somente a página.')
configure({ canExport: false }, syntheticCookie('EXEMPLO_NAO_REAL'))
await rejectedStatus(api.prepararRelatorioRegistros('publicidade', finalidade, {}), 403)
configure({ active: false }, syntheticCookie('EXEMPLO_NAO_REAL'))
await rejectedStatus(api.prepararRelatorioRegistros('publicidade', finalidade, {}), 401)
configure({ reportTooLarge: true }, syntheticCookie('EXEMPLO_NAO_REAL'))
await assert.rejects(api.prepararRelatorioRegistros('publicidade', finalidade, {}), (error) => error.status === 413)
configure({ incompleteReport: true }, syntheticCookie('EXEMPLO_NAO_REAL'))
await assert.rejects(api.prepararRelatorioRegistros('publicidade', finalidade, {}), /registros incompatíveis/)
configure()
await assert.rejects(api.prepararRelatorioRegistros('publicidade', 'texto livre', {}), /Selecione a finalidade/)
assert.equal(calls.length, 0)

function loadTs(relative, imports) {
  const exported = { exports: {} }
  const js = ts.transpileModule(fs.readFileSync(path.join(frontendRoot, 'src', relative), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  vm.runInNewContext(js, { module: exported, exports: exported.exports, URL, URLSearchParams, Intl, Date,
    require: (name) => { assert.ok(name in imports, name); return imports[name] } })
  return exported.exports
}
const utils = loadTs('features/admin-registros/record-utils.ts', {
  '@/lib/api-contract': { ApiContractError }, '@/lib/admin-registros-api': api,
})
const decoded = utils.queryState(new URLSearchParams(utils.listQuery('stories', 2, finalidade, filters)))
assert.equal(decoded.familia, 'stories'); assert.equal(decoded.page, 2); assert.equal(decoded.finalidade, finalidade)
for (const [name, value] of Object.entries(filters)) assert.equal(decoded.filtros[name], value)
assert.equal(utils.safeReturn('/admin/registros?tipo=stories&page=2&termo=teste'), '/admin/registros?tipo=stories&page=2&termo=teste')
for (const unsafe of ['https://external.invalid/admin/registros', '//external.invalid', '/admin/registros/relatorio', '/admin/anuncios']) assert.equal(utils.safeReturn(unsafe), '/admin/registros')
assert.equal(utils.queryState(new URLSearchParams('page=Infinity')).page, 0)
assert.equal(utils.sessionState(null).status, 'EXPIRED')
assert.equal(utils.sessionState({ autenticado: true, papeis: ['ADMIN'], permissoes: [] }).status, 'DENIED')
const allowedSession = { autenticado: true, papeis: ['ADMIN'], permissoes: ['ARQUIVO_PUBLICIDADE_LER'] }
assert.equal(utils.sessionState(allowedSession).status, 'READY')
assert.match(utils.label('PREVENTIVA'), /não confirmado/)
assert.match(utils.label('DESCONHECIDA'), /não comprovada/)
assert.equal(utils.label('LIMITE_PREVISTO'), 'Término previsto')
assert.equal(utils.label('ENCERRAMENTO_REGISTRADO'), 'Encerramento registrado')
assert.equal(utils.label('PREVIEW_RESTRITO'), 'Prévia restrita')
assert.match(utils.label('SIM'), /valor registrado/)
assert.match(utils.label('NAO'), /valor registrado/)
assert.match(utils.label('REMUNERADA'), /não comprova pagamento/)
assert.match(utils.label('NAO_ABRANGIDA'), /classificação registrada/)

const birth = loadTs('lib/date/birth-date.ts', {})
const age = loadTs('features/admin-anuncios/owner-age.ts', { '@/lib/date/birth-date': birth })
assert.equal(age.ownerAge('2000-09-29', new Date('2026-09-29T02:59:59Z')), 25, 'Ainda dia 28 em Brasília.')
assert.equal(age.ownerAge('2000-09-29', new Date('2026-09-29T03:00:00Z')), 26)
assert.equal(age.ownerAge('2000-02-29', new Date('2026-02-28T12:00:00Z')), 25)
assert.equal(age.ownerAge('2000-02-29', new Date('2026-03-01T12:00:00Z')), 26)
for (const invalid of [null, '', '2026-02-30', '2030-01-01', '2000-09-29T00:00:00Z']) assert.equal(age.ownerAge(invalid, new Date('2026-09-29T12:00:00Z')), null)
assert.equal(age.ownerBirthLabel('2000-09-29'), '29/09/2000')
assert.equal(age.ownerBirthLabel('2026-02-30'), 'Não informado')
console.log('Admin registros: filtros servidor, escopo completo/413, retorno seguro, estados de sessão e idade civil OK')
if (process.argv.includes('--browser')) await import('./test-admin-registros-visual.mjs')
