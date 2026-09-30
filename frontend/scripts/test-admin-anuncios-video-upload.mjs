import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import ts from 'typescript'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const sourceRoot = path.resolve(scriptDirectory, '../src')
const source = (name) => readFileSync(path.resolve(sourceRoot, name), 'utf8')

function runtimeModule(name, imports = {}) {
  const compiled = ts.transpileModule(source(name), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
    fileName: name,
    reportDiagnostics: true,
  })
  assert.deepEqual((compiled.diagnostics ?? []).filter((item) => item.category === ts.DiagnosticCategory.Error), [])
  const module = { exports: {} }
  new Function('require', 'module', 'exports', compiled.outputText)((specifier) => {
    assert.ok(Object.hasOwn(imports, specifier), `Fronteira nao declarada: ${name}: ${specifier}`)
    return imports[specifier]
  }, module, module.exports)
  return module.exports
}

const contract = runtimeModule('lib/api-contract.ts')
const api = runtimeModule('features/admin-anuncios/api.ts', {
  '@/lib/api-contract': contract,
  '@/lib/text/encoding': { corrigirEstruturaTexto: (body) => body },
  '@/lib/photo-upload-validation': {
    validatePhotoUpload: async () => ({ valid: true }),
    isSupportedUploadVideo: (file) => /\.(mp4|mov)$/i.test(file.name.trim()),
  },
  './queue-context': { adminAdQueueFilters: () => ({}) },
})

const detail = source('features/admin-anuncios/admin-anuncio-moderacao.tsx')
const photoUploader = source('features/admin-anuncios/admin-anuncio-midia-uploader.tsx')
const videoUploader = source('features/admin-anuncios/admin-anuncio-video-uploader.tsx')
const types = source('features/admin-anuncios/types.ts')
assert.ok(detail.includes('<AdminAnuncioMidiaUploader') && detail.includes('<AdminAnuncioVideoUploader'))
assert.ok(detail.includes('key={`video:${ad.id}`}'), 'Cada anuncio deve ter um estado de selecao de video isolado.')
assert.ok(videoUploader.includes('Adicionar vídeo'))
assert.ok(videoUploader.includes('sempre RESTRITA_18'), 'A orientacao deve explicitar a classificacao fixa do video.')
assert.ok(!videoUploader.includes('100 MiB'), 'A UI nao pode presumir o teto configurado no backend.')
assert.ok(types.includes("tipo: 'VIDEO'"), 'O DTO de video deve diferir do DTO de foto.')
assert.ok(photoUploader.includes('uploadAdminAdMedia') && !photoUploader.includes('uploadAdminAdVideo'))

const video = new File(['video sintético'], 'clip.mp4', { type: 'video/mp4' })
const canonical = {
  midiaId: 'media-1', anuncioId: 'ad-1', tipo: 'VIDEO', finalidade: 'GALERIA', ordem: 0,
  status: 'PENDENTE', statusArquivo: 'PENDENTE', idempotente: false, requestId: 'request-1',
}

assert.equal(api.adminVideoUploadIssue(video), null)
assert.match(api.adminVideoUploadIssue(new File([], 'vazio.mp4')), /vazio/)
assert.match(api.adminVideoUploadIssue(new File(['x'], 'fora.webm')), /MP4 ou MOV/)
assert.equal(api.adminVideoUploadIssue({ name: 'grande.mov', size: 100 * 1024 * 1024 + 1 }), null,
  'O tamanho deve ser decidido pelo servidor, pois a configuracao pode variar.')

const requests = []
class SyntheticXhr {
  constructor() {
    this.upload = {}
    this.headers = {}
    this.status = 0
    this.responseText = ''
    requests.push(this)
  }
  open(method, url) { this.method = method; this.url = url }
  setRequestHeader(name, value) { this.headers[name] = value }
  getResponseHeader(name) { return name === 'X-Request-Id' ? 'header-request' : null }
  send(body) { this.body = body }
  progress(loaded, total) { this.upload.onprogress({ lengthComputable: true, loaded, total }) }
  respond(status, body) {
    this.status = status
    this.responseText = typeof body === 'string' ? body : JSON.stringify(body)
    this.onload()
  }
}
globalThis.XMLHttpRequest = SyntheticXhr
globalThis.document = { cookie: ['XSRF-TOKEN', 'CHANGE_ME'].join('=') }
const flush = () => new Promise((resolve) => setImmediate(resolve))

const progress = []
const first = api.uploadAdminAdVideo('ad-1', video, 'operation-1', (value) => progress.push(value))
await flush()
assert.equal(requests.length, 1)
const xhr = requests[0]
assert.equal(xhr.method, 'POST')
assert.equal(xhr.url, '/api/admin/anuncios/ad-1/midias')
assert.equal(xhr.withCredentials, true)
assert.equal(xhr.headers['Idempotency-Key'], 'operation-1')
assert.equal(xhr.headers['X-XSRF-TOKEN'], 'CHANGE_ME')
assert.equal(xhr.headers.Accept, 'application/json')
assert.equal(xhr.headers['Content-Type'], undefined, 'O browser deve criar o boundary multipart.')
assert.equal([...xhr.body.entries()].length, 1)
assert.equal([...xhr.body.entries()][0][0], 'arquivo')
assert.equal([...xhr.body.entries()][0][1].name, video.name)
xhr.progress(100, 100)
assert.deepEqual(progress, [99], 'A transferencia completa ainda nao e sucesso.')
xhr.respond(200, canonical)
assert.deepEqual(await first, canonical)
assert.deepEqual(progress, [99], 'Somente a UI pode mostrar 100 depois da recarga canonica.')

async function responseCase(status, body, expected) {
  const pending = api.uploadAdminAdVideo('ad-1', video, 'operation-1')
  await flush()
  requests.at(-1).respond(status, body)
  await assert.rejects(pending, expected)
}

await responseCase(200, { ...canonical, anuncioId: 'outro-ad' }, (error) =>
  error instanceof contract.ApiContractError && error.status === 502 && error.retryable)
await responseCase(200, { ...canonical, tipo: 'FOTO' }, (error) => error.code === 'ADMIN_VIDEO_UPLOAD_INVALID_RESPONSE')
await responseCase(200, { ...canonical, status: 'PUBLICAVEL' }, (error) => error.code === 'ADMIN_VIDEO_UPLOAD_INVALID_RESPONSE')
await responseCase(200, '{invalido', (error) => error.code === 'ADMIN_VIDEO_UPLOAD_INVALID_RESPONSE')
const replay = api.uploadAdminAdVideo('ad-1', video, 'operation-1')
await flush()
const replayBody = { ...canonical, status: 'PUBLICAVEL', statusArquivo: 'VALIDADO', idempotente: true }
requests.at(-1).respond(200, replayBody)
assert.deepEqual(await replay, replayBody, 'Uma repeticao canonica pode refletir moderacao posterior.')

await responseCase(409, { message: 'Benefício Vídeo necessário', code: 'VIDEO_BENEFIT_REQUIRED', requestId: 'request-409' },
  (error) => error.kind === 'CONFLICT' && error.code === 'VIDEO_BENEFIT_REQUIRED' && error.requestId === 'request-409')
await responseCase(415, { message: 'Arquivo incompatível', code: 'UNSUPPORTED_VIDEO' },
  (error) => error.status === 415 && error.message === 'Arquivo incompatível')
await responseCase(413, { message: 'Arquivo grande', code: 'TOO_LARGE', requestId: 'request-413' },
  (error) => error.status === 413 && error.kind === 'INVALID_REQUEST'
    && /limite permitido pelo servidor/.test(error.message)
    && error.code === 'TOO_LARGE' && error.requestId === 'request-413')
await responseCase(503, { message: 'Storage indisponível' },
  (error) => error.status === 503 && error.retryable)

const network = api.uploadAdminAdVideo('ad-1', video, 'operation-1')
await flush()
requests.at(-1).onerror()
await assert.rejects(network, (error) => error.kind === 'NETWORK_FAILURE' && error.retryable)
const noStatus = api.uploadAdminAdVideo('ad-1', video, 'operation-1')
await flush()
requests.at(-1).respond(0, '')
await assert.rejects(noStatus, (error) => error.kind === 'NETWORK_FAILURE' && error.retryable)

const beforeLocalFailure = requests.length
await assert.rejects(api.uploadAdminAdVideo('ad-1', new File([], 'vazio.mp4'), 'operation-1'),
  (error) => error.code === 'ADMIN_VIDEO_UPLOAD_LOCAL_INVALID')
await assert.rejects(api.uploadAdminAdVideo('ad-1', video, 'key with space'),
  (error) => error.kind === 'INVALID_REQUEST')
assert.equal(requests.length, beforeLocalFailure, 'Falha local nao deve iniciar upload.')

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function componentRunner(component, props) {
  const slots = []
  let index = 0, dirty = false, tree
  const react = {
    useState(initialValue) {
      const slot = index++
      slots[slot] ??= { value: typeof initialValue === 'function' ? initialValue() : initialValue }
      return [slots[slot].value, (next) => {
        const value = typeof next === 'function' ? next(slots[slot].value) : next
        dirty ||= !Object.is(value, slots[slot].value)
        slots[slot].value = value
      }]
    },
    useRef(initialValue) { const slot = index++; return slots[slot] ??= { current: initialValue } },
  }
  function render() { index = 0; dirty = false; tree = component(props) }
  const runner = { react, render, get tree() { return tree }, async settle() {
    for (let attempt = 0; attempt < 10; attempt++) {
      await flush()
      if (dirty) render()
    }
    assert.equal(dirty, false)
  } }
  return runner
}

const jsx = { jsx: (type, props) => ({ type, props: props ?? {} }), jsxs: (type, props) => ({ type, props: props ?? {} }) }
const uploads = []
const reloads = []
const uploadApi = {
  adminVideoUploadIssue: api.adminVideoUploadIssue,
  uploadAdminAdVideo(adId, file, key, onProgress) {
    const pending = deferred()
    uploads.push({ adId, file, key, onProgress, ...pending })
    return pending.promise
  },
}
const onReload = () => {
  const pending = deferred()
  reloads.push(pending)
  return pending.promise
}
const componentModule = runtimeModule('features/admin-anuncios/admin-anuncio-video-uploader.tsx', {
  react: { useRef: (...args) => runner.react.useRef(...args), useState: (...args) => runner.react.useState(...args) },
  'react/jsx-runtime': jsx,
  'lucide-react': { Loader2: 'Loader2', Upload: 'Upload' },
  '@/components/forms/file-picker': { FilePicker: 'FilePicker' },
  '@/components/ui/button': { Button: 'Button' },
  '@/lib/api-contract': contract,
  './api': uploadApi,
})
const runner = componentRunner(componentModule.AdminAnuncioVideoUploader, { anuncioId: 'ad-1', onReload })
runner.render()

function nodes(tree) {
  if (tree == null || typeof tree === 'boolean') return []
  if (Array.isArray(tree)) return tree.flatMap(nodes)
  if (typeof tree !== 'object') return [tree]
  return [tree, ...nodes(tree.props?.children)]
}
function picker() { return nodes(runner.tree).find((node) => node.type === 'FilePicker') }
function button() { return nodes(runner.tree).find((node) => node.type === 'Button' && node.props.onClick) }
function text() { return nodes(runner.tree).filter((node) => typeof node === 'string' || typeof node === 'number').join(' ') }

picker().props.onSelect([video])
await runner.settle()
assert.equal(picker().props.files[0], video)
button().props.onClick()
button().props.onClick()
await runner.settle()
assert.equal(uploads.length, 1, 'Duplo clique nao pode duplicar POST.')
assert.equal(picker().props.files[0], video)
uploads[0].onProgress(99)
await runner.settle()
assert.match(text(), /99\s*%/)
assert.ok(!text().includes('Vídeo enviado e lista atualizada'))
uploads[0].resolve(canonical)
await runner.settle()
assert.equal(reloads.length, 1)
assert.equal(picker().props.files[0], video, 'A selecao permanece ate a recarga canonica.')
assert.ok(!text().includes('Vídeo enviado e lista atualizada'))
reloads[0].reject(new Error('Falha sintética da recarga'))
await runner.settle()
assert.equal(picker().props.files[0], video, 'Falha de recarga nao perde o arquivo.')
assert.ok(button().props.disabled === false)
button().props.onClick()
await runner.settle()
assert.equal(uploads.length, 2)
assert.equal(uploads[1].file, video)
assert.equal(uploads[1].key, uploads[0].key, 'Retry deve preservar a chave da mesma tentativa logica.')
uploads[1].resolve({ ...canonical, idempotente: true })
await runner.settle()
assert.equal(reloads.length, 2)
reloads[1].resolve()
await runner.settle()
assert.deepEqual(picker().props.files, [])
assert.match(text(), /já estava pendente; lista atualizada/)
assert.ok(!text().includes('Vídeo enviado e lista atualizada'), 'Replay pendente nao deve parecer um novo upload.')

picker().props.onSelect([video])
await runner.settle()
globalThis.window = { location: { pathname: '/admin/anuncios/ad-2' } }
button().props.onClick()
await runner.settle()
assert.equal(uploads.length, 2, 'O detalhe A oculto nao pode enviar video enquanto B esta ativo.')
globalThis.window.location.pathname = '/admin/anuncios/ad-1'
button().props.onClick()
await runner.settle()
assert.equal(uploads.length, 3)
uploads[2].reject(new contract.ApiContractError('Benefício ausente', 'CONFLICT', 409))
await runner.settle()
assert.equal(picker().props.files[0], video, 'Conflito nao perde o arquivo selecionado.')
assert.equal(button().props.disabled, false, 'Conflito resolvido pode repetir a mesma operacao.')
button().props.onClick()
await runner.settle()
assert.equal(uploads[3].key, uploads[2].key)
uploads[3].reject(new contract.ApiContractError('Formato incompatível', 'INVALID_REQUEST', 415))
await runner.settle()
assert.equal(picker().props.files[0], video, 'Erro definitivo tambem preserva a selecao ate substituicao explicita.')
assert.equal(button().props.disabled, true)

picker().props.onSelect([video])
await runner.settle()
button().props.onClick()
await runner.settle()
globalThis.window.location.pathname = '/admin/anuncios/ad-2'
uploads[4].resolve(canonical)
await runner.settle()
assert.equal(reloads.length, 2, 'A resposta de A nao pode recarregar o detalhe B.')
assert.equal(picker().props.files[0], video, 'Mudanca de rota apos o POST preserva o arquivo e a chave.')
assert.match(text(), /anúncio exibido mudou/)
globalThis.window.location.pathname = '/admin/anuncios/ad-1'
button().props.onClick()
await runner.settle()
assert.equal(uploads[5].key, uploads[4].key)
uploads[5].resolve({ ...canonical, idempotente: true })
await runner.settle()
assert.equal(reloads.length, 3)
globalThis.window.location.pathname = '/admin/anuncios/ad-2'
reloads[2].resolve()
await runner.settle()
assert.equal(picker().props.files[0], video, 'A rota deve ser conferida novamente apos a recarga.')

for (const [status, statusArquivo] of [['REJEITADA', 'REJEITADO'], ['REMOVIDA', 'REMOVIDO']]) {
  globalThis.window.location.pathname = '/admin/anuncios/ad-1'
  picker().props.onSelect([video])
  await runner.settle()
  const previousKey = uploads.at(-1).key
  button().props.onClick()
  await runner.settle()
  const terminalUpload = uploads.at(-1)
  assert.notEqual(terminalUpload.key, previousKey, 'Nova selecao inicia outra tentativa logica.')
  terminalUpload.resolve({ ...canonical, idempotente: true, status, statusArquivo })
  await runner.settle()
  const terminalReload = reloads.at(-1)
  assert.equal(picker().props.files[0], video, 'Replay terminal preserva a selecao antes da recarga.')
  assert.ok(!text().includes('Nenhum vídeo novo foi adicionado'), 'A mensagem exige recarga concluida.')
  terminalReload.resolve()
  await runner.settle()
  assert.equal(picker().props.files[0], video, 'Replay terminal preserva o arquivo para nova selecao explicita.')
  assert.match(text(), /Nenhum vídeo novo foi adicionado/)
  assert.match(text(), new RegExp(status))
  assert.equal(button().props.disabled, true, 'Replay terminal nao pode reenviar a mesma chave.')
  assert.ok(!text().includes('Vídeo enviado e lista atualizada'), 'Replay terminal nao pode ser apresentado como novo upload.')
}

picker().props.onSelect([video])
await runner.settle()
button().props.onClick()
await runner.settle()
uploads.at(-1).resolve(canonical)
await runner.settle()
assert.equal(picker().props.files[0], video)
assert.ok(!text().includes('Vídeo enviado e lista atualizada'))
reloads.at(-1).resolve()
await runner.settle()
assert.deepEqual(picker().props.files, [])
assert.match(text(), /Vídeo enviado e lista atualizada/)

console.log('ADMIN_VIDEO_UPLOAD_RESULT=OK transport=multipart,csrf,idempotency,99,serverLimit canonical=creation,replay,mismatch,error ui=retry,sameFileKey,reloadBeforeSuccess,hiddenRoute,routeChange,terminalReplay photo=preserved')
