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

const calls = []
let response
const contractError = new Error('Acesso negado')
const module = { exports: {} }
const document = { cookie: 'XSRF-TOKEN=csrf-sintetico' }
vm.runInNewContext(compiled, {
  module,
  exports: module.exports,
  AbortController,
  Headers,
  document,
  fetch: async (url, options) => {
    calls.push({ url, options })
    return response
  },
  require: (specifier) => {
    assert.equal(specifier, '@/lib/api-contract')
    return {
      adminApiUrl: (endpoint) => `/api/admin${endpoint}`,
      apiErrorFromResponse: async () => contractError,
      ApiContractError: class ApiContractError extends Error {},
    }
  },
})

const api = module.exports
const finalidade = 'AUDITORIA_INTERNA'
const pagina = { itens: [], page: 1, size: 20, totalElements: 21, totalPages: 2, last: true }
response = { ok: true, json: async () => pagina }
assert.deepEqual(await api.listarRegistrosPublicidade(1, finalidade), pagina)
assert.equal(calls.at(-1).url, '/api/admin/registros/publicidade?page=1&size=20&finalidade=AUDITORIA_INTERNA')
assert.equal(calls.at(-1).options.credentials, 'include')
assert.equal(calls.at(-1).options.cache, 'no-store')
assert.equal(calls.at(-1).options.method, 'POST')
assert.equal(calls.at(-1).options.headers.get('X-XSRF-TOKEN'), 'csrf-sintetico')

const registro = { id: 'abc-123', anuncioId: 'anuncio-1', versoes: [] }
response = { ok: true, json: async () => registro }
assert.deepEqual(await api.detalharRegistroPublicidade(registro.id, finalidade), registro)
assert.equal(calls.at(-1).url, '/api/admin/registros/publicidade/abc-123?finalidade=AUDITORIA_INTERNA')

const exportacao = { bytes: 'arquivo sintético' }
response = { ok: true, blob: async () => exportacao }
assert.equal(await api.exportarRegistroPublicidade(registro.id, finalidade), exportacao)
assert.equal(calls.at(-1).url, '/api/admin/registros/publicidade/abc-123/exportacao?finalidade=AUDITORIA_INTERNA')

response = { ok: true, blob: async () => exportacao }
assert.equal(await api.baixarMidiaPublicidade(registro.id, 'midia-1', finalidade), exportacao)
assert.equal(calls.at(-1).url, '/api/admin/registros/publicidade/abc-123/midias/midia-1/arquivo?finalidade=AUDITORIA_INTERNA')
assert.equal(calls.at(-1).options.headers.get('Accept'), 'application/octet-stream')

const storyPagina = { itens: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true }
response = { ok: true, json: async () => storyPagina }
assert.deepEqual(await api.listarRegistrosStory(0, finalidade), storyPagina)
assert.equal(calls.at(-1).url, '/api/admin/registros/stories?page=0&size=20&finalidade=AUDITORIA_INTERNA')
const story = { id: 'story-registro-1', storyId: 'story-1', versoes: [] }
response = { ok: true, json: async () => story }
assert.deepEqual(await api.detalharRegistroStory(story.id, finalidade), story)
assert.equal(calls.at(-1).url, '/api/admin/registros/stories/story-registro-1?finalidade=AUDITORIA_INTERNA')
response = { ok: true, blob: async () => exportacao }
assert.equal(await api.exportarRegistroStory(story.id, finalidade), exportacao)
assert.equal(calls.at(-1).url, '/api/admin/registros/stories/story-registro-1/exportacao?finalidade=AUDITORIA_INTERNA')
response = { ok: true, blob: async () => exportacao }
assert.equal(await api.baixarMidiaStory(story.id, 'story-midia-1', finalidade), exportacao)
assert.equal(calls.at(-1).url, '/api/admin/registros/stories/story-registro-1/midias/story-midia-1/arquivo?finalidade=AUDITORIA_INTERNA')
assert.equal(calls.at(-1).options.method, 'POST')

const consultasAntesDaRecusa = calls.length
await assert.rejects(api.listarRegistrosPublicidade(0, undefined), /Selecione a finalidade/)
await assert.rejects(api.detalharRegistroPublicidade(registro.id, undefined), /Selecione a finalidade/)
await assert.rejects(api.exportarRegistroPublicidade(registro.id, 'texto livre'), /Selecione a finalidade/)
await assert.rejects(api.baixarMidiaPublicidade(registro.id, 'midia-1', undefined), /Selecione a finalidade/)
await assert.rejects(api.listarRegistrosStory(0, undefined), /Selecione a finalidade/)
await assert.rejects(api.detalharRegistroStory(story.id, 'texto livre'), /Selecione a finalidade/)
await assert.rejects(api.exportarRegistroStory(story.id, undefined), /Selecione a finalidade/)
await assert.rejects(api.baixarMidiaStory(story.id, 'story-midia-1', undefined), /Selecione a finalidade/)
assert.equal(calls.length, consultasAntesDaRecusa, 'finalidade ausente/invalida nao inicia transporte')

document.cookie = ''
response = { ok: true }
await assert.rejects(api.listarRegistrosPublicidade(0, finalidade), /proteção da sessão/)
assert.equal(calls.at(-1).url, '/api/admin/auth/me')
assert.equal(calls.at(-1).options.method, undefined)
document.cookie = 'XSRF-TOKEN=csrf-sintetico'

response = { ok: false, status: 403 }
await assert.rejects(api.listarRegistrosPublicidade(0, finalidade), contractError)
await assert.rejects(api.detalharRegistroPublicidade(registro.id, finalidade), contractError)
await assert.rejects(api.exportarRegistroPublicidade(registro.id, finalidade), contractError)
await assert.rejects(api.baixarMidiaPublicidade(registro.id, 'midia-1', finalidade), contractError)
await assert.rejects(api.listarRegistrosStory(0, finalidade), contractError)
await assert.rejects(api.detalharRegistroStory(story.id, finalidade), contractError)
await assert.rejects(api.exportarRegistroStory(story.id, finalidade), contractError)
await assert.rejects(api.baixarMidiaStory(story.id, 'story-midia-1', finalidade), contractError)

response = { ok: true, json: async () => ({ page: 0 }) }
await assert.rejects(api.listarRegistrosPublicidade(0, finalidade), /registros incompatíveis/)

console.log('Admin registros: contratos de consulta, exportação e acesso negado OK')
