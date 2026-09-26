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
  const exporting = /\/exportacao$|\/midias\/[^/]+\/arquivo$/.test(parsed.pathname)
  if (exporting && !session.canExport) return reply(403)
  if (parsed.searchParams.get('finalidade') !== finalidade) return reply(400)

  auditEvents.push({ path: parsed.pathname, finalidade })
  if (exporting) return reply(200, exportacao)
  if (parsed.pathname === '/api/admin/registros/publicidade') {
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
