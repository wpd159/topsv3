import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
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
  invalidSelection: false,
  mismatchedSelection: false,
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
    invalidSelection: false,
    mismatchedSelection: false,
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
    if (session.invalidSelection) return reply(400)
    return reply(200, { tipo: parsed.pathname.includes('/stories/') ? 'STORY' : 'ANUNCIO',
      geradoEm: '2026-09-29T03:00:00Z', fusoHorario: body.fusoHorario, responsavelId: 'admin-sintetico',
      finalidade, filtros: body.filtros, idsSelecionados: session.mismatchedSelection ? [] : body.ids, quantidade: session.incompleteReport ? 2 : 1,
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
configure({ invalidSelection: true }, syntheticCookie('EXEMPLO_NAO_REAL'))
await assert.rejects(api.prepararRelatorioRegistros('publicidade', finalidade, {}, [registro.id]), (error) => error.status === 400)
assert.deepEqual(JSON.parse(calls.at(-1).options.body).ids, [registro.id], 'ID fora do escopo é recusado; não há nova tentativa sem IDs.')
assert.equal(calls.filter((call) => call.url.includes('/relatorio')).length, 1)
configure({ mismatchedSelection: true }, syntheticCookie('EXEMPLO_NAO_REAL'))
await assert.rejects(api.prepararRelatorioRegistros('publicidade', finalidade, {}, [registro.id]), /registros incompatíveis/)
configure()
await assert.rejects(api.prepararRelatorioRegistros('publicidade', 'texto livre', {}), /Selecione a finalidade/)
assert.equal(calls.length, 0)

function loadTs(relative, imports) {
  const exported = { exports: {} }
  const js = ts.transpileModule(fs.readFileSync(path.join(frontendRoot, 'src', relative), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX },
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

const selectedIds = ['00000000-0000-4000-8000-000000000041', '00000000-0000-4000-8000-000000000001']
const selectingUser = '00000000-0000-4000-8000-000000000500'
const selected = { escopo: 'selecionados', ids: selectedIds, usuarioId: selectingUser, invalida: false }
const selectionUrl = utils.listQuery('publicidade', 2, finalidade, filters, selected)
assert.deepEqual(JSON.parse(JSON.stringify(utils.selectionState(new URLSearchParams(selectionUrl)))), selected)
assert.equal(utils.selectionAllowed(selected, { status: 'READY', session: { usuarioId: selectingUser } }), true)
for (const state of [{ status: 'EXPIRED' }, { status: 'ERROR' }, { status: 'LOADING' }, { status: 'READY', session: { usuarioId: 'outro-usuario' } }]) assert.equal(utils.selectionAllowed(selected, state), false)
for (const query of ['escopo=selecionados', 'escopo=selecionados&id=INVALIDO', 'escopo=todos&id=' + selectedIds[0], 'escopo=desconhecido']) {
  const invalid = utils.selectionState(new URLSearchParams(query))
  assert.equal(invalid.escopo, 'selecionados'); assert.equal(utils.selectionAllowed(invalid, { status: 'READY', session: { usuarioId: selectingUser } }), false)
}
const invalidAll = utils.selectionState(new URLSearchParams('escopo=todos&selecaoInvalida=1'))
assert.equal(invalidAll.escopo, 'todos'); assert.equal(utils.selectionAllowed(invalidAll, { status: 'READY', session: { usuarioId: selectingUser } }), false, 'Marcador inválido também bloqueia escopo todos.')
const expiredReturn = new URL(utils.clearedSelectionReturn('/admin/registros?' + selectionUrl), 'https://synthetic.invalid')
assert.equal(expiredReturn.searchParams.get('escopo'), 'selecionados'); assert.equal(expiredReturn.searchParams.getAll('id').length, 0)
assert.equal(utils.selectionAllowed(utils.selectionState(expiredReturn.searchParams), { status: 'READY', session: { usuarioId: selectingUser } }), false)
const tooMany = { ...selected, ids: Array.from({ length: 101 }, (_, index) => `00000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`) }
assert.equal(utils.selectionAllowed(tooMany, { status: 'READY', session: { usuarioId: selectingUser } }), false)

// The report must carry a hundred selected IDs once, not again inside a nested return URL.
const oneHundred = { ...selected, ids: tooMany.ids.slice(0, 100) }
const reportUrl = new URL('/admin/registros/relatorio?' + utils.listQuery('publicidade', 2, finalidade, filters, oneHundred), 'https://synthetic.invalid')
assert.equal(reportUrl.searchParams.getAll('id').length, 100)
assert.equal(reportUrl.searchParams.has('retorno'), false)
assert.ok(reportUrl.pathname.length + reportUrl.search.length < 8192)
const previousUrl = reportUrl.pathname + reportUrl.search + '&retorno=' + encodeURIComponent('/admin/registros?' + utils.listQuery('publicidade', 2, finalidade, filters, oneHundred))
assert.ok(previousUrl.length > 8192, 'A duplicação anterior ultrapassava o orçamento sintético de 8 KiB.')
const constrainedHttp = http.createServer({ maxHeaderSize: 8192 }, (request, response) => {
  const requested = new URL(request.url, 'http://synthetic.invalid')
  response.writeHead(requested.pathname === reportUrl.pathname ? 200 : 404, { 'Content-Type': 'application/json' })
  response.end(JSON.stringify({ ids: requested.searchParams.getAll('id').length, page: requested.searchParams.get('page') }))
})
await new Promise((resolve) => constrainedHttp.listen(0, '127.0.0.1', resolve))
try {
  const response = await fetch(`http://127.0.0.1:${constrainedHttp.address().port}${reportUrl.pathname}${reportUrl.search}`)
  assert.equal(response.status, 200, 'HTTP sintético com cabeçalho máximo conhecido aceita cem IDs sem duplicação.')
  assert.deepEqual(await response.json(), { ids: 100, page: '2' })
} finally { await new Promise((resolve, reject) => constrainedHttp.close((error) => error ? reject(error) : resolve())) }
assert.equal(utils.reportReturn(reportUrl.searchParams), '/admin/registros?' + utils.listQuery('publicidade', 2, finalidade, filters, oneHundred))
const detailReturn = '/admin/registros?' + utils.listQuery('publicidade', 2, finalidade, filters, oneHundred)
const detailUrl = new URL(`/admin/registros/publicidade/${selectedIds[0]}?finalidade=${finalidade}&retorno=${encodeURIComponent(detailReturn)}`, 'https://synthetic.invalid')
assert.equal(detailUrl.searchParams.getAll('id').length, 0, 'Detalhe recebe os IDs somente no retorno, não duplicados no nível principal.')
assert.equal(detailUrl.searchParams.get('retorno'), detailReturn)
assert.ok(detailUrl.pathname.length + detailUrl.search.length < 8192)
assert.equal(utils.safeReturn(detailUrl.searchParams.get('retorno')), detailReturn)
const legacyReport = new URLSearchParams(utils.listQuery('publicidade', 0, finalidade, filters, oneHundred))
legacyReport.set('retorno', detailReturn)
assert.equal(utils.reportReturn(legacyReport), detailReturn, 'Link legado coerente preserva página original.')
legacyReport.set('retorno', '/admin/registros?' + utils.listQuery('publicidade', 2, finalidade, filters, { escopo: 'todos', ids: [], usuarioId: null, invalida: false }))
assert.equal(utils.reportReturn(legacyReport), '/admin/registros?' + utils.listQuery('publicidade', 0, finalidade, filters, oneHundred), 'Retorno legado divergente não amplia selecionados para todos.')
legacyReport.set('retorno', '/admin/registros?' + utils.listQuery('publicidade', 2, 'APURACAO_INCIDENTE', filters, oneHundred))
assert.equal(utils.reportReturn(legacyReport), '/admin/registros?' + utils.listQuery('publicidade', 0, finalidade, filters, oneHundred), 'Retorno legado não troca finalidade.')
legacyReport.set('retorno', '/admin/registros?' + utils.listQuery('publicidade', 2, finalidade, { ...filters, termo: 'outra busca' }, oneHundred))
assert.equal(utils.reportReturn(legacyReport), '/admin/registros?' + utils.listQuery('publicidade', 0, finalidade, filters, oneHundred), 'Retorno legado não troca filtros.')
const clearedReportReturn = new URL(utils.reportReturn(reportUrl.searchParams, true), 'https://synthetic.invalid')
assert.equal(clearedReportReturn.searchParams.getAll('id').length, 0)
assert.equal(utils.selectionAllowed(utils.selectionState(clearedReportReturn.searchParams), { status: 'READY', session: { usuarioId: selectingUser } }), false)

const React = require('react'), { renderToStaticMarkup } = require('react-dom/server')
const content = loadTs('features/admin-registros/record-content.tsx', {
  'react': React, 'react/jsx-runtime': require('react/jsx-runtime'),
  'next/link': { default: () => null }, '@/components/ui/button': { Button: () => null },
  '@/lib/admin-auth-api': {}, '@/lib/admin-registros-api': api, './record-utils': utils,
})
const historical = { id: selectedIds[0], anuncioId: selectedIds[0], contratanteUsuarioId: selectingUser,
  ativacaoBeneficioId: null, grupoAtivacaoId: null, movimentoCreditoId: null, pagamentoId: null,
  natureza: 'ADMINISTRATIVA', relacaoMaterial: 'DESCONHECIDA', cobertura: 'PREVENTIVA',
  inicioEm: null, fimEm: null, retencaoAte: null, fimTipo: 'SEM_TERMINO_REGISTRADO', encerramentoMotivo: null,
  preservacoes: [], versoes: [{ id: selectedIds[1], numero: 1, capturadoEm: null, vigenteDesde: null, vigenteAte: null,
    motivo: 'CAPTURA', conteudoSha256: 'a'.repeat(64), midias: [],
    conteudo: { titulo: 'SIM', descricao: 'NAO\nPREVENTIVA', slug: 'PREVENTIVA',
      localizacao: { cidade: 'SIM', endereco_resumido: 'NAO\n  endereço <privado>' }, estado: 'NAO_ABRANGIDA' },
    contratante: { nomeCivil: 'SIM' }, comercial: { classificacao: 'NAO_ABRANGIDA' },
    segmentacao: { estado: 'NAO_AFERIDA_NA_CAPTURA' }, alcance: { estado: 'NAO_MENSURADO' } }] }
const rendered = renderToStaticMarkup(React.createElement(content.RegistroContent, { detail: historical, printable: true, privateSnapshots: true }))
for (const literal of ['SIM', 'NAO\nPREVENTIVA', 'PREVENTIVA', 'NAO\n  endereço &lt;privado&gt;']) assert.ok(rendered.includes(literal), literal)
assert.equal(rendered.includes('Sim (valor registrado)'), false, 'Título e nome iguais ao código SIM não podem ser traduzidos.')
assert.match(rendered, /Não abrangida \(classificação registrada\)/, 'Campo controlado permanece legível.')
assert.match(rendered, /NAO_ABRANGIDA/, 'Código controlado permanece disponível como informação secundária.')
assert.equal(historical.versoes[0].conteudo.titulo, 'SIM', 'JSON histórico continua intocado.')

assert.equal(utils.calendarBoundary('2026-09-29'), '2026-09-29T03:00:00.000Z')
assert.equal(utils.calendarBoundary('2026-09-29', true), '2026-09-30T03:00:00.000Z')
assert.equal(utils.calendarBoundary('2024-02-29', true), '2024-03-01T03:00:00.000Z')
assert.equal(utils.calendarBoundary('2026-12-31', true), '2027-01-01T03:00:00.000Z')
assert.equal(utils.calendarBoundary('2018-11-04'), '2018-11-04T03:00:00.000Z', 'Dia com meia-noite inexistente começa às 01:00 no fuso local.')
assert.equal(utils.calendarBoundary('2018-11-04', true), '2018-11-05T02:00:00.000Z')
const wholeDays = utils.calendarFilters(filters, '2026-09-01', '2026-09-29')
assert.equal(wholeDays.inicio, '2026-09-01T03:00:00.000Z'); assert.equal(wholeDays.fim, '2026-09-30T03:00:00.000Z')
assert.equal(utils.calendarDate(wholeDays.inicio), '2026-09-01'); assert.equal(utils.calendarDate(wholeDays.fim, true), '2026-09-29')
assert.equal(utils.calendarDate('2026-09-30T02:59:59.999Z'), '2026-09-29')
assert.equal(utils.calendarDate('2026-09-30T03:00:00.000Z'), '2026-09-30')
assert.throws(() => utils.calendarFilters(filters, '2026-09-30', '2026-09-29'), /inicial/)
assert.throws(() => utils.calendarBoundary('2026-02-30'), /válida/)
assert.equal(utils.benefitLabel('FOTOS_EXTRA_5'), 'Até 10 fotos')
assert.equal(utils.benefitLabel('ANUNCIO_TOPO'), 'Anúncio no topo')
assert.equal(utils.benefitLabel('STORIES'), 'Stories')
assert.equal(utils.benefitLabel('OCULTAR_IDADE'), 'Ocultar idade')
assert.equal(utils.benefitLabel('WHATSAPP_CARD'), 'WhatsApp no card')
assert.equal(utils.benefitLabel('CARROSSEL_FOTOS'), 'Carrossel de fotos')
assert.equal(utils.benefitLabel('VIDEO_1'), 'Vídeo no anúncio')
assert.equal(utils.benefitLabel('CODIGO_FUTURO'), 'Benefício com código registrado')
assert.equal(utils.endReasonLabel('LIMITE_PREVISTO'), 'Regra do término previsto')
assert.equal(utils.endReasonLabel('ENCERRAMENTO_REGISTRADO'), 'Motivo do encerramento')
console.log('Admin registros: seleção explícita preservada/invalidada sem virar todos, revalidação servidor e calendário São Paulo inclusivo OK')

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
