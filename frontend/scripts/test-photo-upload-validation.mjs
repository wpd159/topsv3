import assert from 'node:assert/strict'
import { File } from 'node:buffer'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import sharp from 'sharp'
import ts from 'typescript'

const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../src')
const source = (name) => readFileSync(path.join(sourceRoot, name), 'utf8')

// Execute the actual TypeScript modules. Only platform/UI boundaries are mocked.
function moduleFromSource(name, imports = {}) {
  const compiled = ts.transpileModule(source(name), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
    fileName: name,
    reportDiagnostics: true,
  })
  assert.deepEqual((compiled.diagnostics ?? []).filter((item) => item.category === ts.DiagnosticCategory.Error), [])
  const module = { exports: {} }
  const require = (specifier) => {
    assert.ok(Object.hasOwn(imports, specifier), `Mock/import não declarado: ${name}: ${specifier}`)
    return imports[specifier]
  }
  new Function('require', 'module', 'exports', compiled.outputText)(require, module, module.exports)
  return module.exports
}

const tick = () => new Promise((resolve) => setImmediate(resolve))
const element = (type, props) => ({ type, props: props ?? {} })
const jsx = { jsx: element, jsxs: element, Fragment: 'Fragment' }
const iconMocks = new Proxy({}, { get: (_, key) => String(key) })

// Minimal deterministic hook runner: executes real component callbacks/effects,
// not React DOM/layout or browser image decoding. Dependencies retain identity.
function hooks(onRef) {
  const values = []
  const effects = []
  let index = 0
  let dirty = false
  let component
  let props
  let tree
  const changed = (previous, next) => !previous || !next || next.some((value, i) => !Object.is(value, previous[i]))
  const react = {
    useState(initial) {
      const slot = index++
      if (!values[slot]) values[slot] = { value: typeof initial === 'function' ? initial() : initial }
      return [values[slot].value, (next) => {
        const value = typeof next === 'function' ? next(values[slot].value) : next
        if (!Object.is(value, values[slot].value)) dirty = true
        values[slot].value = value
      }]
    },
    useReducer(reducer, initial) {
      const [value, setValue] = react.useState(initial)
      const dispatch = react.useCallback((action) => setValue((current) => reducer(current, action)), [])
      return [value, dispatch]
    },
    useRef(initial) {
      const slot = index++
      if (!values[slot]) {
        values[slot] = { current: initial }
        onRef?.(values[slot])
      }
      return values[slot]
    },
    useMemo(callback, dependencies) {
      const slot = index++
      if (!values[slot] || changed(values[slot].dependencies, dependencies)) {
        values[slot] = { value: callback(), dependencies }
      }
      return values[slot].value
    },
    useCallback(callback, dependencies) { return react.useMemo(() => callback, dependencies) },
    useEffect(callback, dependencies) {
      const slot = index++
      if (!values[slot] || changed(values[slot].dependencies, dependencies)) {
        const old = values[slot]
        values[slot] = { dependencies, cleanup: old?.cleanup }
        effects.push(() => {
          old?.cleanup?.()
          values[slot].cleanup = callback()
        })
      }
    },
  }
  function render() {
    index = 0
    dirty = false
    tree = component(props)
    effects.splice(0).forEach((run) => run())
    return tree
  }
  return {
    react,
    mount(nextComponent, nextProps) { component = nextComponent; props = nextProps; return render() },
    update(nextProps) { props = nextProps; return render() },
    render,
    async settle() {
      for (let iteration = 0; iteration < 12; iteration++) {
        await tick()
        if (dirty) render()
      }
      return tree
    },
    get tree() { return tree },
    unmount() { values.forEach((value) => value?.cleanup?.()) },
  }
}

function nodes(tree) {
  if (tree === null || tree === undefined || typeof tree === 'boolean') return []
  if (Array.isArray(tree)) return tree.flatMap(nodes)
  if (typeof tree !== 'object') return [tree]
  return [tree, ...nodes(tree.props?.children)]
}
const text = (tree) => nodes(tree).filter((node) => typeof node === 'string' || typeof node === 'number').join(' ')
const find = (tree, predicate, label) => {
  const found = nodes(tree).find((node) => typeof node === 'object' && predicate(node))
  assert.ok(found, `Controle ausente: ${label}`)
  return found
}
const picker = (tree, label) => find(tree, (node) => node.type === 'FilePicker' && node.props.ariaLabel === label, label)
const button = (tree, label) => find(tree, (node) => ['button', 'Button'].includes(node.type) && text(node).includes(label), label)

function selectThroughFilePicker(props, files, drop = false) {
  const runner = hooks()
  const { FilePicker } = moduleFromSource('components/forms/file-picker.tsx', {
    react: runner.react,
    'react/jsx-runtime': jsx,
    'lucide-react': iconMocks,
    '@/components/ui/button': { Button: 'Button' },
    '@/lib/utils': { cn: (...args) => args.filter(Boolean).join(' ') },
  })
  runner.mount(FilePicker, props)
  if (drop) {
    find(runner.tree, (node) => Boolean(node.props.onDrop), 'dropzone').props.onDrop({ preventDefault() {}, dataTransfer: { files } })
  } else {
    find(runner.tree, (node) => node.type === 'input' && node.props.type === 'file', 'file input').props.onChange({ currentTarget: { files } })
  }
  runner.unmount()
}

// sharp already belongs to this project's frozen dependencies. It only produces
// small, genuinely encoded, synthetic fixtures; no personal photos are loaded.
const pixels = { create: { width: 2, height: 2, channels: 3, background: '#527ea3' } }
const jpeg = await sharp(pixels).jpeg().toBuffer()
const png = await sharp(pixels).png().toBuffer()
const webp = await sharp(pixels).webp().toBuffer()
const trailer = Buffer.from('ffe000124a465858005741000000000000000000', 'hex')
assert.equal(trailer.length, 20)
assert.equal(jpeg.subarray(-2).toString('hex'), 'ffd9')
const incident = Buffer.concat([jpeg, trailer])
const fixture = (name, bytes = jpeg, type = 'image/jpeg') => new File([bytes], name, { type, lastModified: 17 })
const photo = moduleFromSource('lib/photo-upload-validation.ts')

let decoded = 0
let closed = 0
let dimensions = { width: 2, height: 2 }
let decodeFailure = false
globalThis.createImageBitmap = async (blob) => {
  decoded++
  assert.ok(['image/jpeg', 'image/png', 'image/webp'].includes(blob.type))
  if (decodeFailure) throw new Error('controlled browser decoder failure')
  return { ...dimensions, close() { closed++ } }
}

for (const [name, bytes] of [['normal.JPG', jpeg], ['normal.jpeg', jpeg], ['normal.png', png], ['normal.webp', webp]]) {
  assert.deepEqual(await photo.validatePhotoUpload(fixture(name, bytes)), { valid: true }, name)
}
for (const mime of ['', 'application/octet-stream', 'image/incorrect', 'video/mp4']) {
  assert.deepEqual(await photo.validatePhotoUpload(fixture(`mime-${mime || 'empty'}.jpg`, jpeg, mime)), { valid: true })
}
assert.equal(closed, decoded, 'Cada bitmap decodificado deve ser liberado.')
const beforeRejectedDecode = decoded
for (const [file, expectedMessage] of [
  [fixture('incident.jpeg', incident), photo.JPEG_COMPATIBILITY_MESSAGE],
  [fixture('truncated.jpg', jpeg.subarray(0, -2)), photo.JPEG_COMPATIBILITY_MESSAGE],
  [fixture('wrong.png', jpeg), photo.PHOTO_FORMAT_MESSAGE],
  [fixture('wrong.jpg', png), photo.PHOTO_FORMAT_MESSAGE],
  [fixture('unsupported.gif', Buffer.from('GIF89aunsupported')), photo.PHOTO_FORMAT_MESSAGE],
  [fixture('renamed.jpg', Buffer.from('GIF89aunsupported')), photo.PHOTO_FORMAT_MESSAGE],
  [fixture('renamed-avif.jpg', Buffer.from('0000001866747970617669660000000061766966', 'hex')), photo.PHOTO_FORMAT_MESSAGE],
]) {
  const result = await photo.validatePhotoUpload(file)
  assert.equal(result.valid, false, file.name)
  assert.equal(result.message, expectedMessage, file.name)
}
assert.equal(decoded, beforeRejectedDecode, 'Assinatura/trailer incompatível deve falhar antes do decoder.')
const empty = await photo.validatePhotoUpload(fixture('empty.jpg', Buffer.alloc(0)))
assert.equal(empty.valid, false)
assert.match(empty.message, /vazi/i)
const oversizedBytes = Buffer.alloc(photo.PHOTO_MAX_BYTES + 1)
jpeg.copy(oversizedBytes)
oversizedBytes.set([0xff, 0xd9], oversizedBytes.length - 2)
const oversized = await photo.validatePhotoUpload(fixture('large.jpg', oversizedBytes))
assert.equal(oversized.valid, false)
assert.match(oversized.message, /20 MiB \(20\.971\.520 bytes\)/)
const exactSize = oversizedBytes.subarray(1)
exactSize.set(jpeg.subarray(0, 2), 0)
assert.deepEqual(await photo.validatePhotoUpload(fixture('exact-size.jpg', exactSize)), { valid: true })
for (const [size, valid] of [
  [{ width: 20000, height: 2000 }, true],
  [{ width: 20001, height: 1 }, false],
  [{ width: 1, height: 20001 }, false],
  [{ width: 8000, height: 5001 }, false],
  [{ width: 0, height: 2 }, false],
]) {
  dimensions = size
  const result = await photo.validatePhotoUpload(fixture(`dimensions-${size.width}-${size.height}.jpg`))
  assert.equal(result.valid, valid)
  if (!valid && size.width > 0) assert.match(result.message, /20[. ]?000|40[. ]?000[. ]?000|40 milhões/)
}
dimensions = { width: 2, height: 2 }
decodeFailure = true
assert.equal((await photo.validatePhotoUpload(fixture('decoder-reject.jpg'))).valid, false)
decodeFailure = false
const bitmapMock = globalThis.createImageBitmap
const originalRevokeObjectURL = URL.revokeObjectURL
let revokedFallbackUrls = 0
globalThis.createImageBitmap = undefined
globalThis.Image = class {
  naturalWidth = 2
  naturalHeight = 2
  set src(_url) { queueMicrotask(() => this.onload()) }
}
URL.revokeObjectURL = (url) => { revokedFallbackUrls++; originalRevokeObjectURL(url) }
assert.deepEqual(await photo.validatePhotoUpload(fixture('fallback-image.png', png, '')), { valid: true })
assert.equal(revokedFallbackUrls, 1, 'Fallback Image deve liberar sua object URL.')
globalThis.createImageBitmap = bitmapMock
URL.revokeObjectURL = originalRevokeObjectURL
delete globalThis.Image
assert.equal(photo.isSupportedUploadVideo(fixture('clip.MOV', Buffer.from('video'), '')), true)
assert.equal(photo.isSupportedUploadVideo(fixture('clip.mp4', Buffer.from('video'), 'application/octet-stream')), true)
assert.equal(photo.isSupportedUploadVideo(fixture('renamed.jpg', Buffer.from('video'), 'video/mp4')), false)

const apiContract = moduleFromSource('lib/api-contract.ts')
const componentImports = (runner, extra = {}) => ({
  react: runner.react,
  'react/jsx-runtime': jsx,
  'lucide-react': iconMocks,
  '@/components/forms/file-picker': { FilePicker: 'FilePicker' },
  '@/components/ui/button': { Button: 'Button' },
  '@/components/ui/dialog': Object.fromEntries(['Dialog', 'DialogContent', 'DialogDescription', 'DialogFooter', 'DialogHeader', 'DialogTitle'].map((name) => [name, name])),
  '@/lib/photo-upload-validation': photo,
  '@/lib/api-contract': apiContract,
  ...extra,
})

console.log('PHOTO_HELPER_RESULT=OK syntheticEncodedFixtures=true browserDecoder=controlledMock')

const mediaResponse = {
  midias: [],
  limites: { fotosDisponiveis: 10, videosDisponiveis: 1, fotosAtivas: 0, maxFotos: 10, videosAtivos: 0, maxVideos: 1, videoAtivo: true, maxVideoBytes: 1024 },
  fotosValidasAtivasTotal: 0,
  anuncio: { id: 'created', slug: 'local', status: 'PENDENTE_REVISAO', statusModeracao: 'PENDENTE', atualizadoEm: null,
    acoesPermitidas: { pausar: false, reativar: false, remover: true, corrigirEReenviar: false } },
}
const requests = []
let nextResponse = { status: 200, body: mediaResponse }
let holdUploadResponse = false
class UploadXHR {
  upload = {}
  headers = new Map()
  open(method, url) { this.method = method; this.url = url }
  setRequestHeader(name, value) { this.headers.set(name.toLowerCase(), value) }
  getResponseHeader(name) { return name.toLowerCase() === 'x-request-id' ? 'photo-test-request' : null }
  send(body) {
    this.body = body
    this.status = nextResponse.status
    this.responseText = JSON.stringify(nextResponse.body)
    requests.push(this)
    if (!holdUploadResponse) queueMicrotask(() => this.status === 0 ? this.onerror() : this.onload())
  }
}
globalThis.document = { cookie: 'XSRF-TOKEN=CHANGE_ME; ' }
globalThis.XMLHttpRequest = UploadXHR
globalThis.fetch = async (url, options) => {
  requests.push({ url, ...options })
  return new Response(JSON.stringify(nextResponse.body), { status: nextResponse.status, headers: { 'Content-Type': 'application/json' } })
}
const advertiserApi = moduleFromSource('lib/meus-anuncios-api.ts', {
  '@/lib/api-contract': apiContract,
  '@/lib/public-auth-api': { getPublicSession: async () => ({ id: 'synthetic-owner', status: 'ATIVO' }) },
  '@/lib/photo-upload-validation': photo,
  '@/lib/visualizacoes-canonicas': { parseVisualizacoesCanonicas: (value) => value },
})
const adminApi = moduleFromSource('features/admin-anuncios/api.ts', {
  '@/lib/api-contract': apiContract,
  '@/lib/photo-upload-validation': photo,
  '@/lib/text/encoding': { corrigirEstruturaTexto: (value) => value },
  './queue-context': {},
})
const good = fixture('good.jpg')
const bad = fixture('trailer.jpeg', incident)
const video = fixture('video.mov', Buffer.from('existing video boundary'), '')
for (const action of [
  () => advertiserApi.enviarMinhaMidia('local', bad),
  () => advertiserApi.enviarMinhasMidiasEmLote('local', [good, bad]),
  () => advertiserApi.enviarMinhasMidiasEmLote('local', [good, video, bad]),
  () => adminApi.uploadAdminAdMedia('local', bad, 'no-upload'),
  ...[
    fixture('empty.jpg', Buffer.alloc(0)),
    fixture('oversized.jpg', oversizedBytes),
    fixture('wrong-extension.jpg', png),
    fixture('unsupported-renamed.jpg', Buffer.from('GIF89aunsupported')),
    fixture('fake-video.jpg', Buffer.from('GIF89aunsupported'), 'video/mp4'),
    fixture('not-a-video.heic', Buffer.from('unsupported-signature'), 'video/mp4'),
  ].flatMap((file) => [
    () => advertiserApi.enviarMinhaMidia('local', file),
    () => advertiserApi.enviarMinhasMidiasEmLote('local', [good, file]),
    () => adminApi.uploadAdminAdMedia('local', file, 'no-upload'),
  ]),
]) {
  await assert.rejects(action, (error) => error.code === 'PHOTO_UPLOAD_LOCAL_INVALID')
  assert.equal(requests.length, 0, 'Recusa local não pode chegar ao fetch/CSRF/XHR, nem enviar parte válida do lote.')
}
nextResponse = { status: 503, body: { message: 'Serviço temporariamente indisponível.', code: 'TEMPORARY' } }
await assert.rejects(advertiserApi.enviarMinhaMidia('local', good), (error) => error.status === 503)
assert.equal(requests.at(-1).headers.get('x-xsrf-token'), 'CHANGE_ME')
const retrySingleKey = requests.at(-1).headers.get('idempotency-key')
await assert.rejects(advertiserApi.enviarMinhaMidia('local', good), (error) => error.status === 503)
assert.equal(requests.at(-1).headers.get('idempotency-key'), retrySingleKey)
const replacementBytes = Buffer.from(jpeg)
replacementBytes[15] ^= 1 // Alter only synthetic JFIF density metadata, preserving length.
assert.notDeepEqual(replacementBytes, jpeg)
const replacement = fixture(good.name, replacementBytes)
assert.equal(replacement.size, good.size)
assert.equal(replacement.lastModified, good.lastModified)
await assert.rejects(advertiserApi.enviarMinhaMidia('local', replacement))
assert.notEqual(requests.at(-1).headers.get('idempotency-key'), retrySingleKey)
await assert.rejects(advertiserApi.enviarMinhasMidiasEmLote('local', [good, video]))
const retryBatchKey = requests.at(-1).headers.get('idempotency-key')
await assert.rejects(advertiserApi.enviarMinhasMidiasEmLote('local', [good, video]))
assert.equal(requests.at(-1).headers.get('idempotency-key'), retryBatchKey)
await assert.rejects(advertiserApi.enviarMinhasMidiasEmLote('local', [replacement, video]))
assert.notEqual(requests.at(-1).headers.get('idempotency-key'), retryBatchKey, 'Arquivo novo com mesmos metadados não herda chave do lote anterior.')
await assert.rejects(advertiserApi.enviarMinhasMidiasEmLote('local', [good, video], undefined, 'account-a:create:novo'))
const accountBatchKey = requests.at(-1).headers.get('idempotency-key')
await assert.rejects(advertiserApi.enviarMinhasMidiasEmLote('local', [good, video], undefined, 'account-b:create:novo'))
assert.notEqual(requests.at(-1).headers.get('idempotency-key'), accountBatchKey, 'Outra conta não herda a chave do mesmo File/lote.')
nextResponse = { status: 200, body: mediaResponse }
await advertiserApi.enviarMinhasMidiasEmLote('local', [good, video])
assert.equal(requests.at(-1).body.getAll('arquivos').length, 2, 'Vídeo continua no lote original sem conversão.')
console.log('PHOTO_UPLOAD_REQUEST_GUARDS_RESULT=OK invalidRequests=0 retryAndReplacementKeys=OK')

const adminRunner = hooks()
const adminUploads = []
let adminFailure = null
let reloadCount = 0
const { AdminAnuncioMidiaUploader } = moduleFromSource('features/admin-anuncios/admin-anuncio-midia-uploader.tsx', componentImports(adminRunner, {
  './api': { uploadAdminAdMedia: async (...args) => {
    adminUploads.push(args)
    if (adminFailure) throw adminFailure
    return { requestId: 'admin-success' }
  } },
}))
adminRunner.mount(AdminAnuncioMidiaUploader, { anuncioId: 'local', onReload: async () => { reloadCount++ } })
const adminPicker = () => picker(adminRunner.tree, 'Selecionar foto para o anúncio')
const adminSubmit = () => button(adminRunner.tree, 'Enviar foto')
assert.ok(adminPicker().props.accept.includes('.jpg'))
assert.ok(!adminPicker().props.accept.includes('video'))
assert.match(text(adminRunner.tree), /20 MiB/)
selectThroughFilePicker(adminPicker().props, [bad], true)
adminRunner.render()
assert.match(text(adminRunner.tree), /Verificando foto…/)
assert.equal(adminSubmit().props.disabled, true)
adminSubmit().props.onClick()
await adminRunner.settle()
assert.match(text(adminRunner.tree), /trailer.jpeg/)
assert.match(text(adminRunner.tree), /salve uma nova cópia/)
assert.doesNotMatch(text(adminRunner.tree), /Tentar novamente/)
adminSubmit().props.onClick()
await adminRunner.settle()
assert.equal(adminUploads.length, 0)
selectThroughFilePicker(adminPicker().props, [good])
await adminRunner.settle()
assert.equal(adminSubmit().props.disabled, false)
adminFailure = new apiContract.ApiContractError('Falha transitória', 'SERVER_ERROR', 503, true, 'admin-rid', 'TEMPORARY')
adminSubmit().props.onClick()
await adminRunner.settle()
const firstAdminKey = adminUploads.at(-1)[2]
button(adminRunner.tree, 'Tentar novamente').props.onClick()
await adminRunner.settle()
assert.equal(adminUploads.at(-1)[2], firstAdminKey)
assert.match(text(adminRunner.tree), /admin-rid/)
adminPicker().props.onSelect([replacement])
await adminRunner.settle()
adminSubmit().props.onClick()
await adminRunner.settle()
assert.notEqual(adminUploads.at(-1)[2], firstAdminKey)
adminFailure = new apiContract.ApiContractError('Recusada pelo servidor', 'INVALID_REQUEST', 415, false, '415-rid', 'FORMAT')
button(adminRunner.tree, 'Tentar novamente').props.onClick()
await adminRunner.settle()
assert.equal(adminSubmit().props.disabled, true)
assert.doesNotMatch(text(adminRunner.tree), /Tentar novamente/)
adminPicker().props.onRemove(0)
await adminRunner.settle()
assert.equal(adminPicker().props.files.length, 0)

function deferredFile(name, bytes = jpeg) {
  const file = fixture(name, bytes)
  const arrayBuffer = file.arrayBuffer.bind(file)
  let release
  const gate = new Promise((resolve) => { release = resolve })
  file.arrayBuffer = async () => { await gate; return arrayBuffer() }
  return { file, release }
}
const oldAdmin = deferredFile('old-admin.jpg')
const newAdmin = deferredFile('new-admin.jpg')
adminPicker().props.onSelect([good])
await adminRunner.settle()
const previouslyValidAdminSubmit = adminSubmit()
adminPicker().props.onSelect([oldAdmin.file])
adminRunner.render()
const oldSubmit = adminSubmit()
adminPicker().props.onSelect([newAdmin.file])
oldAdmin.release()
await adminRunner.settle()
assert.equal(adminSubmit().props.disabled, true, 'Validação velha não habilita arquivo novo ainda pendente.')
const adminBeforeRace = adminUploads.length
oldSubmit.props.onClick()
previouslyValidAdminSubmit.props.onClick()
await adminRunner.settle()
assert.equal(adminUploads.length, adminBeforeRace)
newAdmin.release()
await adminRunner.settle()
assert.equal(adminSubmit().props.disabled, false)
adminFailure = null
adminSubmit().props.onClick()
await adminRunner.settle()
assert.equal(adminUploads.at(-1)[1], newAdmin.file)
assert.equal(reloadCount, 1)
assert.equal(adminPicker().props.files.length, 0)
adminRunner.unmount()
console.log('PHOTO_ADMIN_COMPONENT_RESULT=OK blockedBeforeUpload=true delayedSelectionAndRetry=OK')

const documentFile = fixture('documento-sintetico.pdf', Buffer.from('%PDF-1.4 local synthetic'), 'application/pdf')
let selectedDocument
const decodeBeforeDocument = decoded
selectThroughFilePicker({
  ariaLabel: 'Selecionar documento', buttonLabel: 'Selecionar documento', accept: 'application/pdf',
  files: [], onSelect: (files) => { selectedDocument = files[0] }, onRemove() {},
}, [documentFile], true)
assert.equal(selectedDocument, documentFile)
assert.equal(decoded, decodeBeforeDocument, 'FilePicker compartilhado de documentos não recebe validação nem mensagens de fotos.')
const previewPickerRunner = hooks()
const { FilePicker: PreviewFilePicker } = moduleFromSource('components/forms/file-picker.tsx', {
  react: previewPickerRunner.react,
  'react/jsx-runtime': jsx,
  'lucide-react': iconMocks,
  '@/components/ui/button': { Button: 'Button' },
  '@/lib/utils': { cn: (...args) => args.filter(Boolean).join(' ') },
})
const sameNameA = fixture('mesmo-nome.jpg')
const sameNameB = fixture('mesmo-nome.jpg')
const previewRemoved = []
previewPickerRunner.mount(PreviewFilePicker, {
  ariaLabel: 'Fotos sintéticas', buttonLabel: 'Selecionar fotos', accept: 'image/*',
  files: [sameNameA, sameNameB], previewUrls: ['blob:primeira', 'blob:segunda'],
  onSelect() {}, onRemove: (index) => previewRemoved.push(index),
})
assert.deepEqual(nodes(previewPickerRunner.tree).filter((node) => node?.type === 'img').map((node) => node.props.src),
  ['blob:primeira', 'blob:segunda'], 'Miniaturas acompanham a posição, mesmo com nomes iguais.')
assert.match(find(previewPickerRunner.tree, (node) => node?.type === 'ul', 'lista de miniaturas').props.className, /grid-cols-2/)
find(previewPickerRunner.tree, (node) => node?.type === 'button' && node.props['aria-label'] === 'Remover foto 2: mesmo-nome.jpg', 'remoção da segunda foto').props.onClick()
assert.deepEqual(previewRemoved, [1])
find(previewPickerRunner.tree, (node) => node?.type === 'img' && node.props.src === 'blob:primeira', 'prévia com falha').props.onError()
previewPickerRunner.render()
assert.match(text(previewPickerRunner.tree), /Prévia indisponível/)
previewPickerRunner.unmount()
const documentPickerRunner = hooks()
const { FilePicker: DocumentFilePicker } = moduleFromSource('components/forms/file-picker.tsx', {
  react: documentPickerRunner.react,
  'react/jsx-runtime': jsx,
  'lucide-react': iconMocks,
  '@/components/ui/button': { Button: 'Button' },
  '@/lib/utils': { cn: (...args) => args.filter(Boolean).join(' ') },
})
documentPickerRunner.mount(DocumentFilePicker, {
  ariaLabel: 'Documento', buttonLabel: 'Selecionar documento', accept: 'application/pdf',
  files: [documentFile], onSelect() {}, onRemove() {},
})
assert.equal(nodes(documentPickerRunner.tree).filter((node) => node?.type === 'img').length, 0,
  'Seletores compartilhados sem opção de miniaturas conservam o comportamento anterior.')
documentPickerRunner.unmount()
console.log('PHOTO_FILE_PICKER_BOUNDARIES_RESULT=OK selectionAndDrop=true kycUnchanged=true')

const wizardTypes = moduleFromSource('features/anuncio-wizard/types.ts')
const wizardConstants = moduleFromSource('features/anuncio-wizard/wizard-constants.ts')
const wizardCacheFunctions = moduleFromSource('features/anuncio-wizard/wizard-storage.ts', { './types': wizardTypes })
const wizardProgressApi = moduleFromSource('features/anuncio-wizard/wizard-progress.ts', { '@/lib/api-contract': apiContract })
const wizardRunner = hooks()
const wizardCalls = { create: 0, upload: 0, kyc: 0, update: 0 }
let wizardUploadFailure = null
let wizardUploadStatus = 'PENDENTE_REVISAO'
const wizardErrors = []
const wizardSuccesses = [], wizardProgress = [], wizardNavigations = [], wizardUploadSlugs = []
let wizardCacheClears = 0, wizardProgressClears = 0, wizardResets = 0
const steps = wizardTypes.wizardStepIds.map((id) => ({ id, title: id, eyebrow: id }))
const wizardStore = {
  state: {
    form: { ...wizardTypes.initialWizardFormState, titulo: 'Anúncio sintético', descricaoPerfil: 'Descrição sintética para coordenação local.', fotos: [good, bad] },
    kyc: { ...wizardTypes.initialWizardKycState },
  },
  hydrated: true, currentIndex: 6, lastSavedAt: null,
  setFotos(files) { wizardStore.state.form = { ...wizardStore.state.form, fotos: files, fotoNomes: files.map((file) => file.name) } },
  setVideos(files) { wizardStore.state.form = { ...wizardStore.state.form, videos: files } },
  updateForm(patch) { wizardStore.state.form = { ...wizardStore.state.form, ...patch } },
  updateKyc(patch) { wizardStore.state.kyc = { ...wizardStore.state.kyc, ...patch } },
  setStep(step) { wizardStore.currentIndex = steps.findIndex((item) => item.id === step) },
  setDocumentos() {}, nextStep() {}, previousStep() {},
  reset() { wizardResets++ }, clearCurrentCache() { wizardCacheClears++ }, hydrateFromBackend() {},
}
const user = { id: 'synthetic-user', dataNascimento: '1990-01-01' }
const locations = { estados: [], cidades: [], bairros: [], loadBairros: async () => {}, loadCidades: async () => {} }
const wizardImports = componentImports(wizardRunner, {
  '@/app/(painel-admin)/admin/anuncios/actions': { revalidarCacheCatalogoPublico: async () => {} },
  '@/lib/seo/indexnow-client': { anuncioEstaPublicamenteIndexavel: () => false },
  'next/navigation': { useRouter: () => ({ push: (href) => wizardNavigations.push(href) }) },
  sonner: { toast: { error: (message) => wizardErrors.push(message), warning: (message) => wizardErrors.push(message), success: (message) => wizardSuccesses.push(message) } },
  '@/context/AuthContext': { useAuth: () => ({ usuario: user, carregando: false, refresh: async () => {} }) },
  '@/hooks/useLocalidades': { useLocalidades: () => locations },
  '@/lib/date/birth-date': { isoToBirthDate: (value) => value },
  '@/lib/utils': { cn: (...args) => args.filter(Boolean).join(' ') },
  '@/lib/meus-anuncios-api': {
    ...advertiserApi,
    atualizarMeuAnuncio: async () => { wizardCalls.update++; return { id: 'created', slug: 'synthetic' } },
    consultarLimitesMinhasMidias: async () => mediaResponse.limites,
    enviarMinhasMidiasEmLote: async (_slug, files) => {
      wizardCalls.upload++
      wizardUploadSlugs.push(_slug)
      assert.ok(!files.includes(bad))
      if (wizardUploadFailure) throw wizardUploadFailure
      return { ...mediaResponse, fotosValidasAtivasTotal: wizardUploadStatus === 'RASCUNHO' ? 0 : 1,
        anuncio: { ...mediaResponse.anuncio, slug: 'synthetic', status: wizardUploadStatus } }
    },
  },
  '@/utils/formatter': { formatCurrencyBRL: (value) => String(value) },
  './api': {
    fetchWizardCategories: async () => [{ value: 'SYNTHETIC', label: 'Synthetic' }],
    fetchWizardKycStatus: async () => ({ prontoParaEnviarAnuncio: true, nomeCivil: 'Synthetic', cpfPreenchido: true, dataNascimento: '1990-01-01' }),
    submitWizardKyc: async () => { wizardCalls.kyc++; return { prontoParaEnviarAnuncio: true } },
    submitWizardAnuncio: async () => { wizardCalls.create++; return { slugLocal: 'synthetic', anuncioId: 'created' } },
  },
  './wizard-constants': wizardConstants,
  './wizard-utils': { calculateAge: () => 36, formatWizardCategory: (value) => value },
  './wizard-storage': wizardCacheFunctions,
  './use-anuncio-wizard-store': {
    useAnuncioWizardStore: () => wizardStore,
    // Unrelated profile/KYC form rules are pre-satisfied. Photo coordination and
    // publication callbacks run from the actual component, not reimplemented.
    validateWizardKycState: () => null, validateWizardStep: () => null, wizardSteps: steps,
  },
  './types': wizardTypes,
  './wizard-progress': {
    ...wizardProgressApi,
    clearWizardProgressSessionId() { wizardProgressClears++ },
    createWizardProgressSessionId: () => 'synthetic-session',
    syncWizardProgress: async (value) => { wizardProgress.push(value) },
  },
})
for (const [file, name] of [
  ['wizard-final-review', 'WizardFinalReview'], ['wizard-preview', 'WizardPreview'],
  ['wizard-step-fotos', 'WizardStepFotos'], ['wizard-step-kyc', 'WizardStepKyc'],
  ['wizard-step-localizacao', 'WizardStepLocalizacao'], ['wizard-step-perfil', 'WizardStepPerfil'],
  ['wizard-step-premium', 'WizardStepPremium'], ['wizard-step-servicos', 'WizardStepServicos'],
]) wizardImports[`./components/${file}`] = { [name]: name }
const wizardSessionStorage = new Map()
globalThis.window = {
  requestAnimationFrame: (callback) => callback(),
  sessionStorage: {
    getItem: (key) => wizardSessionStorage.get(key) ?? null,
    setItem: (key, value) => wizardSessionStorage.set(key, String(value)),
    removeItem: (key) => wizardSessionStorage.delete(key),
  },
}
const { default: AnuncioWizard } = moduleFromSource('features/anuncio-wizard/anuncio-wizard.tsx', wizardImports)
const revokeWizardUrl = URL.revokeObjectURL
const revokedWizardUrls = []
URL.revokeObjectURL = (url) => { revokedWizardUrls.push(url); revokeWizardUrl(url) }
wizardRunner.mount(AnuncioWizard, {})
await wizardRunner.settle()
const publish = () => button(wizardRunner.tree, 'Enviar para moderação')
assert.equal(publish().props.disabled, true)
publish().props.onClick()
await wizardRunner.settle()
assert.equal(wizardCalls.create, 0, 'Wizard não pode criar anúncio nem iniciar upload com foto incompatível.')
assert.equal(wizardCalls.upload, 0)
assert.equal(wizardCalls.kyc, 0, 'Validação de foto deve ocorrer antes de mutações documentais.')
assert.deepEqual(wizardStore.state.form.fotos, [good, bad], 'Wizard preserva arquivos até retirada explícita.')
wizardStore.currentIndex = 3
wizardRunner.render()
const parentPhotoProps = find(wizardRunner.tree, (node) => node.type === 'WizardStepFotos', 'WizardStepFotos integrado ao parent').props
assert.deepEqual(parentPhotoProps.initialFiles, [good, bad])
assert.equal(parentPhotoProps.photoValidation[1].valid, false)
assert.equal(parentPhotoProps.previewUrls.length, 2)
assert.ok(parentPhotoProps.previewUrls.every((url) => url?.startsWith('blob:')))
const uploadBeforePreviewNavigation = wizardCalls.upload
parentPhotoProps.onChange([sameNameA, sameNameB])
wizardRunner.render()
assert.deepEqual(find(wizardRunner.tree, (node) => node.type === 'WizardStepFotos', 'seleção em transição').props.previewUrls,
  [undefined, undefined], 'URLs antigas nunca podem ser associadas a arquivos novos com o mesmo nome.')
await wizardRunner.settle()
const duplicatedPhotoProps = find(wizardRunner.tree, (node) => node.type === 'WizardStepFotos', 'nomes iguais').props
assert.deepEqual(duplicatedPhotoProps.initialFiles, [sameNameA, sameNameB])
assert.notEqual(duplicatedPhotoProps.previewUrls[0], duplicatedPhotoProps.previewUrls[1])
const secondPhotoUrl = duplicatedPhotoProps.previewUrls[1]
duplicatedPhotoProps.onChange([sameNameB])
wizardRunner.render()
assert.deepEqual(find(wizardRunner.tree, (node) => node.type === 'WizardStepFotos', 'remoção em transição').props.previewUrls,
  [undefined], 'Remover a primeira foto não pode mostrar sua miniatura na segunda.')
await wizardRunner.settle()
const remainingPhotoProps = find(wizardRunner.tree, (node) => node.type === 'WizardStepFotos', 'foto restante').props
assert.deepEqual(remainingPhotoProps.initialFiles, [sameNameB])
assert.ok(remainingPhotoProps.previewUrls[0]?.startsWith('blob:'))
assert.notEqual(remainingPhotoProps.previewUrls[0], secondPhotoUrl, 'Nova URL substitui a revogada pelo pai.')
assert.ok(revokedWizardUrls.includes(secondPhotoUrl), 'O pai revoga a URL anterior quando a seleção muda.')
wizardStore.currentIndex = 4
wizardRunner.render()
await wizardRunner.settle()
wizardStore.currentIndex = 3
wizardRunner.render()
await wizardRunner.settle()
const returnedPhotoProps = find(wizardRunner.tree, (node) => node.type === 'WizardStepFotos', 'retorno à etapa Fotos').props
assert.deepEqual(returnedPhotoProps.initialFiles, [sameNameB])
assert.equal(returnedPhotoProps.previewUrls[0], remainingPhotoProps.previewUrls[0])
assert.equal(wizardCalls.upload, uploadBeforePreviewNavigation, 'Seleção, remoção e navegação não iniciam upload.')
returnedPhotoProps.onChange([good, bad])
wizardRunner.render()
await wizardRunner.settle()
parentPhotoProps.onChange([good])
wizardStore.currentIndex = 6
wizardRunner.render()
await wizardRunner.settle()
assert.equal(publish().props.disabled, false)
const previouslyValidPublish = publish()
const delayedWizard = deferredFile('delayed-wizard.jpg')
wizardStore.setFotos([delayedWizard.file])
wizardRunner.render()
assert.equal(publish().props.disabled, true)
const stalePublish = publish()
wizardStore.setFotos([bad])
wizardRunner.render()
delayedWizard.release()
await wizardRunner.settle()
assert.equal(publish().props.disabled, true)
stalePublish.props.onClick()
previouslyValidPublish.props.onClick()
await wizardRunner.settle()
assert.equal(wizardCalls.create, 0, 'Callback/resultado antigo não pode submeter seleção nova inválida.')
wizardStore.setFotos([good])
wizardStore.currentIndex = 6
wizardRunner.render()
await wizardRunner.settle()
publish().props.onClick()
await wizardRunner.settle()
assert.equal(wizardCalls.create, 1)
assert.equal(wizardCalls.upload, 1)
assert.equal(wizardCalls.update, 1)

const transientWizardPhoto = fixture('transient-wizard.jpg')
wizardStore.setFotos([transientWizardPhoto])
wizardStore.currentIndex = 6
wizardRunner.render()
await wizardRunner.settle()
wizardUploadFailure = new advertiserApi.MeusAnunciosApiError('Falha transitória', 503, 'TEMPORARY', 'transient-rid')
const successesBeforeTransientFailure = wizardSuccesses.length
publish().props.onClick()
await wizardRunner.settle()
assert.equal(wizardCalls.create, 2)
assert.deepEqual(wizardStore.state.form.fotos, [transientWizardPhoto])
assert.equal(wizardSuccesses.length, successesBeforeTransientFailure, 'Upload falho não pode declarar publicação bem-sucedida.')
wizardStore.currentIndex = 6
wizardRunner.render()
await wizardRunner.settle()
assert.equal(publish().props.disabled, false, 'Falha transitória permite retry explícito do wizard.')
wizardUploadFailure = null
publish().props.onClick()
await wizardRunner.settle()
assert.equal(wizardCalls.create, 2, 'Retry não deve criar um segundo anúncio para a mesma publicação.')
assert.equal(wizardCalls.upload, 3)
assert.equal(wizardUploadSlugs.at(-1), wizardUploadSlugs.at(-2), 'Retry de upload mantém o slug criado antes da falha.')

const serverRejectedWizardPhoto = fixture('server-rejected-wizard.jpg')
wizardStore.setFotos([serverRejectedWizardPhoto])
wizardStore.currentIndex = 6
wizardRunner.render()
await wizardRunner.settle()
wizardUploadFailure = new advertiserApi.MeusAnunciosApiError(photo.JPEG_COMPATIBILITY_MESSAGE, 415, 'MIDIA_FORMATO_INVALIDO', 'photo415-rid', true)
publish().props.onClick()
await wizardRunner.settle()
assert.equal(wizardCalls.create, 3)
assert.deepEqual(wizardStore.state.form.fotos, [serverRejectedWizardPhoto])
wizardStore.currentIndex = 6
wizardRunner.render()
await wizardRunner.settle()
assert.equal(publish().props.disabled, true, 'HTTP 415 de foto não oferece retry dos mesmos bytes na criação.')
const beforeRejectedWizardRetry = wizardCalls.upload
publish().props.onClick()
await wizardRunner.settle()
assert.equal(wizardCalls.upload, beforeRejectedWizardRetry)
wizardStore.currentIndex = 3
wizardRunner.render()
const rejectedCreationProps = find(wizardRunner.tree, (node) => node.type === 'WizardStepFotos', 'WizardStepFotos rejeitado pelo servidor').props
assert.equal(rejectedCreationProps.photoValidation[0].valid, false)
assert.match(rejectedCreationProps.photoValidation[0].message, /servidor recusou este lote.*Remova ou substitua/)
rejectedCreationProps.onChange([fixture(serverRejectedWizardPhoto.name, replacementBytes)])
wizardStore.currentIndex = 6
wizardRunner.render()
await wizardRunner.settle()
assert.equal(publish().props.disabled, false)
wizardUploadFailure = null
publish().props.onClick()
await wizardRunner.settle()
assert.equal(wizardCalls.create, 3)
assert.equal(wizardCalls.upload, beforeRejectedWizardRetry + 1)

const unconfirmedPhoto = fixture('draft-unconfirmed.jpg')
wizardStore.setFotos([unconfirmedPhoto])
wizardStore.currentIndex = 6
wizardRunner.render()
await wizardRunner.settle()
const beforeUnconfirmed = {
  create: wizardCalls.create, update: wizardCalls.update, successes: wizardSuccesses.length,
  navigation: wizardNavigations.length, progress: wizardProgress.length,
  reset: wizardResets, clear: wizardCacheClears, progressClear: wizardProgressClears,
}
wizardUploadStatus = 'RASCUNHO'
publish().props.onClick()
await wizardRunner.settle()
assert.equal(wizardCalls.create, beforeUnconfirmed.create + 1)
assert.equal(wizardSuccesses.length, beforeUnconfirmed.successes, 'Resposta RASCUNHO sem foto válida não confirma envio para moderação.')
assert.equal(wizardNavigations.length, beforeUnconfirmed.navigation)
assert.equal(wizardResets, beforeUnconfirmed.reset)
assert.equal(wizardCacheClears, beforeUnconfirmed.clear)
assert.equal(wizardProgressClears, beforeUnconfirmed.progressClear)
assert.ok(wizardProgress.slice(beforeUnconfirmed.progress).every((entry) => entry.status !== 'AGUARDANDO_MODERACAO'))
assert.deepEqual(wizardStore.state.form.fotos, [unconfirmedPhoto], 'RASCUNHO mantém o File disponível para nova tentativa.')
assert.equal(steps[wizardStore.currentIndex].id, 'fotos')
assert.match(wizardErrors.at(-1), /não confirmou uma foto válida/)
const unconfirmedProps = find(wizardRunner.tree, (node) => node.type === 'WizardStepFotos', 'Fotos do rascunho não confirmado').props
assert.deepEqual(unconfirmedProps.initialFiles, [unconfirmedPhoto])
assert.ok(Object.values(unconfirmedProps.createErrors).some((message) => /não confirmou uma foto válida/.test(message)))
wizardStore.currentIndex = 6
wizardUploadStatus = 'PENDENTE_REVISAO'
wizardRunner.render()
await wizardRunner.settle()
publish().props.onClick()
await wizardRunner.settle()
assert.equal(wizardCalls.create, beforeUnconfirmed.create + 1, 'Retry do rascunho não cria outro anúncio.')
assert.equal(wizardCalls.update, beforeUnconfirmed.update + 1, 'Dados já sincronizados não são reenviados na tentativa de upload.')
assert.equal(wizardUploadSlugs.at(-1), wizardUploadSlugs.at(-2))
assert.deepEqual(wizardStore.state.form.fotos, [])
assert.equal(wizardSuccesses.length, beforeUnconfirmed.successes + 1)
assert.equal(wizardSuccesses.at(-1), 'Anúncio enviado para moderação.')
assert.equal(wizardNavigations.at(-1), '/meus-anuncios')
assert.equal(wizardProgress.at(-1).status, 'AGUARDANDO_MODERACAO')
console.log('PHOTO_WIZARD_DRAFT_UPLOAD_RESULT=OK unconfirmedDraftPreserved=true sameSlugRetry=true pendingReviewSuccess=true')
wizardRunner.unmount()
URL.revokeObjectURL = revokeWizardUrl
console.log('PHOTO_WIZARD_PUBLICATION_RESULT=OK createAndUploadBlocked=true asyncSelection=OK')

// Reopen an incomplete ad from the backend, save only text, then add a valid
// photo. Execute the real parent callbacks without mounting unrelated UI.
const draftRunner = hooks()
const draftCalls = [], draftSuccesses = [], draftErrors = [], draftProgress = [], draftNavigations = []
let draftCacheClears = 0, draftProgressClears = 0
const draftAd = {
  ...mediaResponse.anuncio, slug: 'reopened-draft', status: 'RASCUNHO',
  titulo: 'Rascunho reaberto', descricao: 'Descrição preservada do rascunho.', preco: 0,
  categoria: 'SYNTHETIC', locaisAtendimento: [], servicos: [], atendimentoExclusivamenteVirtual: false,
}
let draftSaveResponse = draftAd
const draftMedia = { ...mediaResponse, fotosValidasAtivasTotal: 0, anuncio: draftAd }
const draftStore = {
  state: { form: { ...wizardTypes.initialWizardFormState }, kyc: { ...wizardTypes.initialWizardKycState } },
  hydrated: true, currentIndex: 0, lastSavedAt: null,
  updateForm(patch) { draftStore.state.form = { ...draftStore.state.form, ...patch } },
  updateKyc(patch) { draftStore.state.kyc = { ...draftStore.state.kyc, ...patch } },
  setFotos(files) { draftStore.updateForm({ fotos: files, fotoNomes: files.map((file) => file.name) }) },
  setVideos(files) { draftStore.updateForm({ videos: files }) },
  setEditPendingMedia(scope, files) { draftStore.updateForm({ editPendingMediaScope: scope, editPendingMedia: files }) },
  setEditUploadUnconfirmed(value) { draftStore.updateForm({ editUploadUnconfirmed: value }) },
  setStep(step) { draftStore.currentIndex = steps.findIndex((item) => item.id === step) },
  clearCurrentCache() { draftCacheClears++ },
  hydrateFromBackend(state) { draftStore.state = { form: state.form, kyc: state.kyc }; draftStore.setStep(state.currentStep) },
  setDocumentos() {}, nextStep() {}, previousStep() {}, reset() {},
}
const draftImports = {
  ...wizardImports,
  react: draftRunner.react,
  'next/navigation': { useRouter: () => ({ push: (href) => draftNavigations.push(href) }) },
  sonner: { toast: { error: (message) => draftErrors.push(message), warning: (message) => draftErrors.push(message), success: (message) => draftSuccesses.push(message) } },
  '@/lib/meus-anuncios-api': {
    ...advertiserApi,
    buscarMeuAnuncio: async (slug) => { draftCalls.push(['GET', slug]); return draftAd },
    listarMinhasMidias: async (slug) => { draftCalls.push(['MEDIA', slug]); return draftMedia },
    atualizarMeuAnuncio: async (slug, payload) => { draftCalls.push(['UPDATE', slug, payload]); return draftSaveResponse },
  },
  './use-anuncio-wizard-store': { ...wizardImports['./use-anuncio-wizard-store'], useAnuncioWizardStore: () => draftStore },
  './wizard-progress': {
    ...wizardProgressApi,
    createWizardProgressSessionId: () => 'synthetic-draft-session',
    clearWizardProgressSessionId() { draftProgressClears++ },
    syncWizardProgress: async (value) => { draftProgress.push(value) },
  },
}
draftRunner.mount(moduleFromSource('features/anuncio-wizard/anuncio-wizard.tsx', draftImports).default,
  { mode: 'edit', slug: draftAd.slug })
await draftRunner.settle()
assert.equal(draftStore.state.form.titulo, draftAd.titulo, 'Reabertura carrega o rascunho do backend.')
assert.deepEqual(draftCalls, [['GET', draftAd.slug], ['MEDIA', draftAd.slug]])
draftStore.updateForm({ titulo: 'Dados alterados sem foto' })
draftStore.currentIndex = 6
draftRunner.render()
await draftRunner.settle()
button(draftRunner.tree, 'Salvar alterações').props.onClick()
await draftRunner.settle()
assert.deepEqual(draftErrors, [])
assert.equal(draftCalls.filter(([kind]) => kind === 'UPDATE').length, 1)
assert.equal(draftCalls.at(-1)[2].titulo, 'Dados alterados sem foto')
assert.equal(steps[draftStore.currentIndex].id, 'fotos', 'Salvar dados de um rascunho deve manter o editor na etapa de fotos.')
assert.deepEqual(draftNavigations, [])
assert.equal(draftCacheClears, 0)
assert.equal(draftProgressClears, 0)
assert.equal(draftSuccesses.at(-1), 'Dados salvos. Envie ao menos uma foto para encaminhar o anúncio à revisão.')
assert.ok(draftProgress.every((entry) => entry.status !== 'AGUARDANDO_MODERACAO'))
assert.equal(draftProgress.at(-1).status, 'EM_PREENCHIMENTO')
const reopenedPhotos = find(draftRunner.tree, (node) => node.type === 'WizardStepFotos', 'Fotos após salvar rascunho').props
assert.equal(reopenedPhotos.persistedState.anuncio.status, 'RASCUNHO')
assert.equal(reopenedPhotos.persistedState.anuncio.slug, draftAd.slug)

const pendingDraftPhoto = fixture('draft-pending-photo.jpg')
reopenedPhotos.onPendingFilesChange([pendingDraftPhoto])
draftStore.currentIndex = 6
draftRunner.render()
button(draftRunner.tree, 'Salvar alterações').props.onClick()
await draftRunner.settle()
assert.equal(draftCalls.filter(([kind]) => kind === 'UPDATE').length, 1, 'Foto pendente impede salvar/afirmar envio antes do upload.')
assert.deepEqual(draftStore.state.form.editPendingMedia, [pendingDraftPhoto])
const pendingDraftProps = find(draftRunner.tree, (node) => node.type === 'WizardStepFotos', 'Foto do rascunho pendente').props
assert.deepEqual(pendingDraftProps.pendingFiles, [pendingDraftPhoto])
assert.equal(pendingDraftProps.pendingSaveNotice, true)
draftSaveResponse = { ...draftAd, status: 'PENDENTE_REVISAO', titulo: 'Dados alterados sem foto' }
pendingDraftProps.onPersistedChange({ ...draftMedia, fotosValidasAtivasTotal: 1, anuncio: draftSaveResponse })
pendingDraftProps.onPendingFilesChange([])
draftStore.currentIndex = 6
draftRunner.render()
await draftRunner.settle()
button(draftRunner.tree, 'Salvar alterações').props.onClick()
await draftRunner.settle()
assert.equal(draftCalls.filter(([kind]) => kind === 'UPDATE').length, 2)
assert.equal(draftSuccesses.at(-1), 'Alterações salvas e enviadas para revisão.')
assert.deepEqual(draftNavigations, [`/meus-anuncios/${draftAd.slug}`])
assert.equal(draftProgress.at(-1).status, 'AGUARDANDO_MODERACAO')
assert.equal(draftCacheClears, 1)
assert.equal(draftProgressClears, 1)
draftRunner.unmount()
console.log('PHOTO_WIZARD_REOPENED_DRAFT_RESULT=OK dataOnlySaveNotModerated=true pendingFilesPreserved=true validPhotoReviewSuccess=true')

// A committed batch may lose its response. Exercise the real XHR/key helper,
// with authoritative capacity exhausted after the first request. On reload only
// operation metadata survives: Files do not, and recovery must use exact identity.
async function verifyWizardRecovery(reload, scenario = 'normal') {
  let accountId = `recovery-account-${reload}-${scenario}`
  const originalAccount = accountId
  const createdAd = { anuncioId: '10000000-0000-4000-8000-000000000001', slugLocal: `recovery-${reload}-${scenario}` }
  const files = [1, 2, 3, 4].map((index) => fixture(`recovery-${reload}-${index}.jpg`))
  const calls = { create: [], upload: [], recover: [], navigation: [], navigationMethods: [], success: [], errors: [] }
  const finishHandoff = scenario.startsWith('finish-')
  const originalLocalStorage = window.localStorage
  const cacheScope = { userId: originalAccount, mode: 'create' }
  let finishSnapshot
  let cacheDisabled = false
  if (finishHandoff) {
    const cacheValues = new Map()
    window.localStorage = {
      getItem: (key) => cacheValues.get(key) ?? null,
      setItem: (key, value) => cacheValues.set(key, String(value)),
      removeItem: (key) => {
        if (scenario === 'finish-cache-denied') throw new Error('synthetic cache cleanup denied')
        cacheValues.delete(key)
      },
    }
    wizardCacheFunctions.saveWizardCache(cacheScope, { ...wizardTypes.initialWizardState,
      form: { ...wizardTypes.initialWizardFormState, titulo: 'CREATE anterior ao refresh' } }, null)
  }
  let committed = false
  let creationCommitted = false
  let wrongUploadIdentity = scenario === 'upload-identity-mismatch'
  let wrongPatchIdentity = scenario === 'patch-identity-mismatch'
  let failStorage = scenario.startsWith('storage-')
  let releaseCreate
  const createResponse = new Promise((resolve) => { releaseCreate = resolve })
  const storage = window.sessionStorage
  window.sessionStorage = {
    ...storage,
    setItem(key, value) {
      if (failStorage && key.startsWith('topsdojob:wizard-creation:')
        && JSON.parse(value).phase === (scenario === 'storage-before' ? 'PENDING' : 'CONFIRMED')) {
        throw new Error('synthetic storage denied')
      }
      storage.setItem(key, value)
    },
  }
  const recoveryStore = {
    ...wizardStore,
    state: { form: { ...wizardTypes.initialWizardFormState, titulo: 'Metadados não são conteúdo', fotos: files, fotoNomes: files.map((file) => file.name) }, kyc: { ...wizardTypes.initialWizardKycState } },
    currentIndex: 6,
    setFotos(value) { recoveryStore.state.form = { ...recoveryStore.state.form, fotos: value } },
    setVideos(value) { recoveryStore.state.form = { ...recoveryStore.state.form, videos: value } },
    setStep(step) { recoveryStore.currentIndex = steps.findIndex((item) => item.id === step) },
    reset() {}, clearCurrentCache() { cacheDisabled = true },
  }
  const response = () => ({ ...mediaResponse, fotosValidasAtivasTotal: committed ? 4 : 0,
    midias: committed ? files.map((_file, index) => ({ id: `confirmed-photo-${index}`, tipo: 'FOTO' })) : [],
    limites: { ...mediaResponse.limites, maxFotos: 4, fotosDisponiveis: committed ? 0 : 4 },
    anuncio: { ...mediaResponse.anuncio, id: wrongUploadIdentity ? 'different-ad-id' : createdAd.anuncioId, slug: createdAd.slugLocal } })
  function mount() {
    const runner = hooks()
    const imports = {
      ...wizardImports,
      react: runner.react,
      '@/context/AuthContext': { useAuth: () => ({ usuario: { ...user, id: accountId }, carregando: false,
        refresh: async () => {
          if (finishHandoff) finishSnapshot = {
            cache: wizardCacheFunctions.loadWizardCache(cacheScope),
            operation: wizardProgressApi.loadWizardCreationOperation(`${originalAccount}:create:novo`),
            cacheDisabled,
          }
        } }) },
      'next/navigation': { useRouter: () => ({
        push: (href) => { calls.navigation.push(href); calls.navigationMethods.push('push') },
        replace: (href) => { calls.navigation.push(href); calls.navigationMethods.push('replace') },
      }) },
      sonner: { toast: { error: (message) => calls.errors.push(message), warning() {}, success: (message) => calls.success.push(message) } },
      './use-anuncio-wizard-store': { ...wizardImports['./use-anuncio-wizard-store'], useAnuncioWizardStore: () => recoveryStore },
      './wizard-progress': { ...wizardProgressApi, syncWizardProgress: async () => null },
      './api': {
        ...wizardImports['./api'],
        submitWizardAnuncio: async (_form, sessionId) => {
          calls.create.push(sessionId)
          if (scenario === 'unconfirmed-create' && calls.create.length === 1) throw new Error('synthetic CREATE unavailable')
          creationCommitted = true
          if (scenario === 'account-race') return createResponse
          if (scenario === 'lost-create-response' && calls.create.length === 1) throw new Error('synthetic CREATE response lost')
          return createdAd
        },
        recoverWizardAnuncio: async (sessionId) => {
          calls.recover.push(sessionId)
          if (!creationCommitted) return null
          if (reload && scenario === 'identity-mismatch') return { ...createdAd, anuncioId: 'another-id', slugLocal: 'another-ad' }
          if (reload && scenario === 'missing-confirmation') return null
          if (reload && scenario === 'recovery-refused') throw new Error('synthetic 409: vínculo indisponível')
          return { ...createdAd, status: 'PENDENTE_REVISAO', statusModeracao: 'PENDENTE' }
        },
      },
      '@/lib/meus-anuncios-api': {
        ...advertiserApi,
        atualizarMeuAnuncio: async () => ({ id: wrongPatchIdentity ? 'different-ad-id' : createdAd.anuncioId, slug: createdAd.slugLocal }),
        consultarLimitesMinhasMidias: async () => response().limites,
        enviarMinhasMidiasEmLote: async (...args) => {
          calls.upload.push(args)
          nextResponse = { status: committed ? 200 : 0, body: response() }
          committed = true
          return advertiserApi.enviarMinhasMidiasEmLote(...args)
        },
      },
    }
    runner.mount(moduleFromSource('features/anuncio-wizard/anuncio-wizard.tsx', imports).default, {})
    return runner
  }
  let runner = mount()
  await runner.settle()
  button(runner.tree, 'Enviar para moderação').props.onClick()
  await runner.settle()
  if (scenario === 'account-race') {
    assert.equal(calls.create.length, 1)
    accountId = `${accountId}-changed`
    runner.render()
    await runner.settle()
    releaseCreate(createdAd)
    await runner.settle()
    assert.equal(calls.upload.length, 0, 'CREATE antigo não pode iniciar upload depois de troca de conta.')
    assert.deepEqual(calls.navigation, [])
    assert.deepEqual(calls.success, [])
    assert.equal(wizardProgressApi.loadWizardCreationOperation(`${accountId}:create:novo`), null)
    assert.equal(wizardProgressApi.loadWizardCreationOperation(`${originalAccount}:create:novo`).anuncioId, createdAd.anuncioId)
    runner.unmount()
    window.sessionStorage = storage
    return
  }
  if (scenario === 'storage-before') {
    assert.equal(calls.create.length, 0, 'Falha ao guardar PENDING deve impedir o primeiro POST.')
    assert.equal(calls.upload.length, 0)
    assert.equal(calls.success.length, 0)
    assert.equal(wizardProgressApi.loadWizardCreationOperation(`${accountId}:create:novo`), null)
    runner.unmount()
    window.sessionStorage = storage
    return
  }
  if (scenario === 'storage-after' || scenario === 'unconfirmed-create' || scenario === 'patch-identity-mismatch') {
    assert.equal(calls.create.length, 1)
    assert.equal(calls.upload.length, 0)
    assert.equal(wizardProgressApi.loadWizardCreationOperation(`${accountId}:create:novo`).phase,
      scenario === 'patch-identity-mismatch' ? 'CONFIRMED' : 'PENDING')
    assert.ok(calls.errors.every((message) => !message.includes('Nenhum novo anúncio')))
    failStorage = false
    wrongPatchIdentity = false
    button(runner.tree, 'Enviar para moderação').props.onClick()
    await runner.settle()
  }
  assert.equal(calls.create.length, scenario === 'unconfirmed-create' ? 2 : 1)
  assert.equal(calls.upload.length, 1)
  assert.equal(calls.success.length, 0)
  assert.equal(calls.upload[0][3], `${accountId}:create:novo`, 'Idempotência de upload inclui o escopo da conta.')
  assert.ok(calls.create[0] && calls.create.every((sessionId) => sessionId === calls.create[0]), 'Todo POST usa a mesma sessão durável.')
  const operation = wizardProgressApi.loadWizardCreationOperation(`${accountId}:create:novo`)
  assert.deepEqual(Object.keys(operation).sort(), ['anuncioId', 'phase', 'sessionId', 'slugLocal'])
  assert.equal(operation.sessionId, calls.create[0])
  assert.ok(!JSON.stringify(operation).includes(recoveryStore.state.form.titulo))
  assert.ok(files.every((file) => !JSON.stringify(operation).includes(file.name)))
  const batchKey = requests.at(-1).headers.get('idempotency-key')
  if (reload) {
    runner.unmount()
    recoveryStore.state.form = { ...recoveryStore.state.form, fotos: [] }
    runner = mount()
    await runner.settle()
    if (['identity-mismatch', 'missing-confirmation', 'recovery-refused'].includes(scenario)) {
      assert.deepEqual(calls.navigation, [], 'Resposta de outra identidade não pode abrir editor nem criar anúncio.')
      assert.ok(nodes(runner.tree).some((node) => node?.props?.role === 'alert'))
    } else {
      assert.deepEqual(calls.navigation, [`/meus-anuncios/${createdAd.slugLocal}/editar`],
        'Reload deve recuperar a identidade confirmada e abrir o editor exato, sem depender de File/useRef.')
      assert.deepEqual(calls.navigationMethods, ['replace'], 'Retomada não deixa o CREATE antigo no histórico de navegação.')
    }
    assert.equal(calls.create.length, 1, 'Reload não pode criar outro anúncio.')
    assert.equal(calls.upload.length, 1, 'Reload não pode fabricar/repetir arquivos perdidos.')
    assert.ok(calls.recover.every((sessionId) => sessionId === calls.create[0]))
  } else {
    recoveryStore.currentIndex = 6
    runner.render()
    await runner.settle()
    button(runner.tree, 'Enviar para moderação').props.onClick()
    await runner.settle()
    assert.equal(calls.upload.length, 2, 'Zero vagas após commit não pode barrar replay do mesmo lote.')
    assert.equal(requests.at(-1).headers.get('idempotency-key'), batchKey, 'Replay deve preservar a chave exata do lote.')
    assert.equal(calls.create.length, scenario === 'unconfirmed-create' ? 2 : 1)
    if (scenario === 'upload-identity-mismatch') {
      assert.equal(calls.success.length, 0, 'Mesmo slug com outro id não confirma o upload.')
      wrongUploadIdentity = false
      recoveryStore.currentIndex = 6
      runner.render()
      await runner.settle()
      button(runner.tree, 'Enviar para moderação').props.onClick()
      await runner.settle()
      assert.equal(calls.upload.length, 3)
      assert.equal(requests.at(-1).headers.get('idempotency-key'), batchKey, 'Resposta inválida não descarta a chave idempotente.')
    }
    assert.equal(calls.success.length, 1)
    if (finishHandoff) {
      assert.ok(finishSnapshot, 'A prova deve alcançar o await refresh após o lote confirmado.')
      assert.equal(finishSnapshot.cacheDisabled, true, 'Autosave deve ser desativado antes de esquecer a identidade da criação.')
      if (scenario === 'finish-cache-denied') {
        assert.ok(finishSnapshot.cache)
        assert.equal(finishSnapshot.operation?.phase, 'CONFIRMED', 'Falha ao remover CREATE antigo deve manter a identidade durante refresh.')
      } else {
        assert.equal(finishSnapshot.cache, null, 'Antes de refresh, o CREATE antigo não pode sobreviver sem identidade.')
        assert.equal(finishSnapshot.operation, null)
      }
    }
  }
  runner.unmount()
  window.sessionStorage = storage
  if (finishHandoff) window.localStorage = originalLocalStorage
}
async function verifyWizardEditorHandoff(storageFails = false) {
  const wizardCacheApi = moduleFromSource('features/anuncio-wizard/wizard-storage.ts', { './types': wizardTypes })
  const cacheValues = new Map()
  window.localStorage = {
    getItem: (key) => cacheValues.get(key) ?? null,
    setItem: (key, value) => cacheValues.set(key, String(value)),
    removeItem: (key) => {
      if (storageFails) throw new Error('synthetic cache cleanup denied')
      cacheValues.delete(key)
    },
  }
  const owner = `handoff-account-${storageFails}`
  const identity = { anuncioId: '10000000-0000-4000-8000-000000000088', slugLocal: 'handoff-exact' }
  const cacheScope = { userId: owner, mode: 'create' }
  wizardCacheApi.saveWizardCache(cacheScope, { ...wizardTypes.initialWizardState,
    form: { ...wizardTypes.initialWizardFormState, titulo: 'Cache de CREATE anterior' } }, null)
  wizardProgressApi.saveWizardCreationOperation(`${owner}:create:novo`, {
    sessionId: 'handoff-session-001', phase: 'CONFIRMED', ...identity,
  })
  const runner = hooks()
  const ad = { ...draftAd, id: identity.anuncioId, slug: identity.slugLocal }
  const store = { ...draftStore, state: { form: { ...wizardTypes.initialWizardFormState }, kyc: { ...wizardTypes.initialWizardKycState } },
    hydrateFromBackend(value) { store.state = value }, currentIndex: 0 }
  const imports = {
    ...draftImports,
    react: runner.react,
    './wizard-storage': wizardCacheApi,
    '@/context/AuthContext': { useAuth: () => ({ usuario: { ...user, id: owner }, carregando: false, refresh: async () => {} }) },
    './use-anuncio-wizard-store': { ...draftImports['./use-anuncio-wizard-store'], useAnuncioWizardStore: () => store },
    '@/lib/meus-anuncios-api': { ...advertiserApi, buscarMeuAnuncio: async () => ad,
      listarMinhasMidias: async () => ({ ...draftMedia, anuncio: ad }) },
    './wizard-progress': { ...wizardProgressApi, syncWizardProgress: async () => null },
  }
  runner.mount(moduleFromSource('features/anuncio-wizard/anuncio-wizard.tsx', imports).default,
    { mode: 'edit', slug: identity.slugLocal })
  await runner.settle()
  if (storageFails) {
    assert.equal(wizardProgressApi.loadWizardCreationOperation(`${owner}:create:novo`).anuncioId, identity.anuncioId,
      'Falha ao limpar cache antigo mantém a identidade da operação, impedindo CREATE acidental.')
    assert.ok(wizardCacheApi.loadWizardCache(cacheScope))
  } else {
    assert.equal(wizardProgressApi.loadWizardCreationOperation(`${owner}:create:novo`), null)
    assert.equal(wizardCacheApi.loadWizardCache(cacheScope), null,
      'Handoff autenticado ao editor deve remover também o formulário antigo de CREATE, não só a identidade.')
  }
  runner.unmount()
}
async function verifyWizardAutosaveStopsBeforeRefresh() {
  const previousWindow = window
  const cacheValues = new Map()
  const timers = new Map()
  let timerId = 0
  globalThis.window = { ...previousWindow,
    localStorage: {
      getItem: (key) => cacheValues.get(key) ?? null,
      setItem: (key, value) => cacheValues.set(key, String(value)),
      removeItem: (key) => cacheValues.delete(key),
    },
    setTimeout: (callback, delay) => { const id = ++timerId; timers.set(id, { callback, delay }); return id },
    clearTimeout: (id) => timers.delete(id),
  }
  const runner = hooks()
  const { useAnuncioWizardStore } = moduleFromSource('features/anuncio-wizard/use-anuncio-wizard-store.ts', {
    react: runner.react,
    '@/lib/cpf-mask': { isValidCpf: () => true },
    './wizard-storage': wizardCacheFunctions,
    './types': wizardTypes,
  })
  const cacheScope = { userId: 'autosave-refresh-account', mode: 'create' }
  try {
    runner.mount(() => useAnuncioWizardStore({ cacheScope }), {})
    await runner.settle()
    runner.tree.updateForm({ titulo: 'Formulário anterior à criação confirmada' })
    await runner.settle()
    assert.ok([...timers.values()].some((timer) => timer.delay === 500), 'A prova deve armar o debounce real do store.')
    runner.tree.setFotos([])
    runner.tree.setVideos([])
    runner.tree.clearCurrentCache()
    await runner.settle()
    // Advance the controlled browser clock past the real 500 ms debounce while
    // refresh remains pending; no old CREATE data may be written again.
    for (const [id, timer] of timers) {
      if (timer.delay <= 600) { timers.delete(id); timer.callback() }
    }
    await runner.settle()
    assert.equal(wizardCacheFunctions.loadWizardCache(cacheScope), null)
    assert.equal(timers.size, 0, 'Autosave deve continuar desligado durante refresh pendente.')
  } finally {
    runner.unmount()
    globalThis.window = previousWindow
  }
}
const recoveryFailures = []
for (const [reload, scenario] of [[false, 'normal'], [true, 'normal'], [false, 'lost-create-response'],
  [false, 'unconfirmed-create'], [false, 'storage-before'], [false, 'storage-after'],
  [true, 'identity-mismatch'], [true, 'missing-confirmation'], [true, 'recovery-refused'],
  [false, 'account-race'], [false, 'patch-identity-mismatch'], [false, 'upload-identity-mismatch'],
  [false, 'finish-handoff'], [false, 'finish-cache-denied']]) {
  try { await verifyWizardRecovery(reload, scenario) } catch (error) { recoveryFailures.push(error); console.error(`PHOTO_WIZARD_RECOVERY_FAILURE reload=${reload} scenario=${scenario}: ${error.message}`) }
}
for (const storageFails of [false, true]) {
  try { await verifyWizardEditorHandoff(storageFails) } catch (error) { recoveryFailures.push(error); console.error(`PHOTO_WIZARD_HANDOFF_FAILURE: ${error.message}`) }
}
await verifyWizardAutosaveStopsBeforeRefresh()
assert.equal(recoveryFailures.length, 0, 'Retomada deve preservar identidade da criação e do lote.')
console.log('PHOTO_WIZARD_RECOVERY_RESULT=OK committedBatchLostResponse=true exactSessionReload=true noDuplicateCreate=true')

const realWizardApi = moduleFromSource('features/anuncio-wizard/api.ts', {
  '@/utils/image-upload': { UNSUPPORTED_IMAGE_MESSAGE: 'unsupported' },
  '@/lib/cpf-mask': { isValidCpf: () => true },
  '@/lib/date/birth-date': { birthDateToIso: (value) => value },
  '@/lib/api-contract': apiContract,
})
const exactWizardSession = '10000000-0000-4000-8000-000000000099'
const exactWizardAd = { anuncioId: '10000000-0000-4000-8000-000000000001', slugLocal: 'exact-recovery' }
nextResponse = { status: 201, body: exactWizardAd }
assert.deepEqual(await realWizardApi.submitWizardAnuncio(wizardTypes.initialWizardFormState, exactWizardSession), exactWizardAd)
assert.equal(requests.at(-1).headers['X-Wizard-Session-Id'], exactWizardSession)
assert.equal(requests.at(-1).credentials, 'include')
nextResponse = { status: 200, body: { anuncioId: exactWizardAd.anuncioId, slug: exactWizardAd.slugLocal, status: 'RASCUNHO', statusModeracao: 'NAO_ENVIADO' } }
assert.deepEqual(await realWizardApi.recoverWizardAnuncio(exactWizardSession), exactWizardAd)
assert.ok(requests.at(-1).url.endsWith(`/wizard-progress/${exactWizardSession}/anuncio`))
assert.equal(requests.at(-1).cache, 'no-store')
assert.equal(requests.at(-1).credentials, 'include')
nextResponse = { status: 404, body: {} }
assert.equal(await realWizardApi.recoverWizardAnuncio(exactWizardSession), null)
for (const status of [403, 409, 503]) {
  nextResponse = { status, body: {} }
  await assert.rejects(realWizardApi.recoverWizardAnuncio(exactWizardSession))
}
for (const body of [{ anuncioId: 'not-a-uuid', slug: 'exact-recovery' }, { anuncioId: exactWizardAd.anuncioId, slug: '' }]) {
  nextResponse = { status: 200, body }
  await assert.rejects(realWizardApi.recoverWizardAnuncio(exactWizardSession))
}
console.log('PHOTO_WIZARD_RECOVERY_CONTRACT_RESULT=OK sessionHeader=true noStoreExactGet=true invalidIdentityRefused=true')

const editorRefs = []
mediaResponse.anuncio.slug = 'synthetic'
const editorRunner = hooks((ref) => editorRefs.push(ref))
const editorUploads = []
const photoStepImports = (runner) => componentImports(runner, {
  '@/components/anuncios/editar/video-uploader': { VideoUploader: 'VideoUploader' },
  '@/lib/meus-anuncios-api': {
    ...advertiserApi,
    listarMinhasMidias: async () => mediaResponse,
    enviarMinhasMidiasEmLote: async (...args) => { editorUploads.push(args); return advertiserApi.enviarMinhasMidiasEmLote(...args) },
  },
  '@/lib/seo/indexnow-client': { anuncioEstaPublicamenteIndexavel: () => false },
  './wizard-ui': { StepPanel: 'StepPanel' },
})
const { WizardStepFotos } = moduleFromSource('features/anuncio-wizard/components/wizard-step-fotos.tsx', photoStepImports(editorRunner))
editorRunner.mount(WizardStepFotos, {
  slug: 'synthetic', initialFiles: [], fotoNomes: [], videosNovos: [], onChange() {}, onChangeVideosNovos() {},
})
await editorRunner.settle()
assert.equal(editorRefs.filter((ref) => ref.current instanceof WeakSet).length, 0,
  'A classificação foto/vídeo acompanha cada File na seleção, sem estado fraco restrito à etapa.')
const editorPhotos = () => picker(editorRunner.tree, 'Selecionar fotos do anúncio')
const editorVideos = () => picker(editorRunner.tree, 'Selecionar vídeo do anúncio')
const editorSubmit = () => find(editorRunner.tree, (node) => node.type === 'button' && /Enviar fotos|Verificando foto|Tentar enviar fotos novamente|Enviando/.test(text(node)), 'Enviar fotos')
assert.match(text(editorRunner.tree), /20 MiB/)
assert.match(editorVideos().props.accept, /video\/mp4.*\.mov/)
selectThroughFilePicker(editorPhotos().props, [good, bad], true)
editorRunner.render()
assert.match(text(editorRunner.tree), /Verificando foto…/)
assert.equal(editorSubmit().props.disabled, true)
const beforeEditorRequests = requests.length
editorSubmit().props.onClick()
await editorRunner.settle()
assert.deepEqual(editorPhotos().props.files, [good, bad])
assert.deepEqual(editorPhotos().props.files, [good, bad])
assert.match(text(editorRunner.tree), /trailer.jpeg/)
assert.equal(editorSubmit().props.disabled, true)
assert.doesNotMatch(text(editorRunner.tree), /Tentar enviar fotos novamente/)
assert.equal(editorUploads.length, 0)
assert.equal(requests.length, beforeEditorRequests)
selectThroughFilePicker(editorVideos().props, [video])
await editorRunner.settle()
assert.deepEqual(editorPhotos().props.files, [good, bad])
assert.deepEqual(editorVideos().props.files, [video])
editorPhotos().props.onRemove(1)
await editorRunner.settle()
assert.deepEqual(editorPhotos().props.files, [good])
assert.deepEqual(editorVideos().props.files, [video], 'Remoção de foto não altera vídeo pendente.')
assert.equal(editorSubmit().props.disabled, false)
assert.equal(editorUploads.length, 0, 'Remover inválida não deve disparar upload parcial automático.')
editorVideos().props.onRemove(0)
await editorRunner.settle()
assert.deepEqual(editorVideos().props.files, [])
const previouslyValidEditorSubmit = editorSubmit()
nextResponse = { status: 503, body: { message: 'Serviço temporariamente indisponível.' } }
editorSubmit().props.onClick()
await editorRunner.settle()
assert.deepEqual(editorUploads.at(-1)[1], [good])
assert.deepEqual(editorPhotos().props.files, [good])
const editorRetryKey = requests.at(-1).headers.get('idempotency-key')
button(editorRunner.tree, 'Tentar enviar fotos novamente').props.onClick()
await editorRunner.settle()
assert.equal(requests.at(-1).headers.get('idempotency-key'), editorRetryKey)
nextResponse = { status: 200, body: mediaResponse }
button(editorRunner.tree, 'Tentar enviar fotos novamente').props.onClick()
await editorRunner.settle()
assert.equal(editorPhotos().props.files.length, 0)
assert.equal(editorVideos().props.files.length, 0)
assert.equal(editorPhotos().props.files.length + editorVideos().props.files.length, 0,
  'A confirmação libera todas as seleções do lote.')

const bootstrapPhoto = fixture('csrf-network-retry.jpg')
selectThroughFilePicker(editorPhotos().props, [bootstrapPhoto])
await editorRunner.settle()
const originalFetch = globalThis.fetch
const originalCookie = globalThis.document.cookie
const requestsBeforeCsrfFailure = requests.length
let failedCsrfRequests = 0
globalThis.document.cookie = ''
globalThis.fetch = async (url) => {
  assert.match(String(url), /\/auth\/me$/)
  failedCsrfRequests++
  throw new TypeError('Controlled CSRF bootstrap network failure')
}
editorSubmit().props.onClick()
await editorRunner.settle()
assert.equal(failedCsrfRequests, 1)
assert.equal(requests.length, requestsBeforeCsrfFailure, 'Falha de bootstrap deve ocorrer antes do XHR de upload.')
assert.deepEqual(editorPhotos().props.files, [bootstrapPhoto])
assert.equal(button(editorRunner.tree, 'Tentar enviar fotos novamente').props.disabled, false, 'Falha de rede antes do XHR também permite retry.')
globalThis.fetch = originalFetch
globalThis.document.cookie = originalCookie
nextResponse = { status: 200, body: mediaResponse }
button(editorRunner.tree, 'Tentar enviar fotos novamente').props.onClick()
await editorRunner.settle()
assert.equal(requests.length, requestsBeforeCsrfFailure + 1)
assert.deepEqual(editorUploads.at(-1)[1], [bootstrapPhoto])
assert.equal(editorPhotos().props.files.length, 0)

const oldEditor = deferredFile('old-editor.jpg')
const newEditor = deferredFile('new-editor.jpg')
selectThroughFilePicker(editorPhotos().props, [oldEditor.file])
editorRunner.render()
const staleEditorSubmit = editorSubmit()
editorPhotos().props.onRemove(0)
editorRunner.render()
selectThroughFilePicker(editorPhotos().props, [newEditor.file])
editorRunner.render()
oldEditor.release()
await editorRunner.settle()
assert.equal(editorSubmit().props.disabled, true)
const uploadsBeforeEditorRace = editorUploads.length
staleEditorSubmit.props.onClick()
previouslyValidEditorSubmit.props.onClick()
await editorRunner.settle()
assert.equal(editorUploads.length, uploadsBeforeEditorRace)
newEditor.release()
await editorRunner.settle()
assert.equal(editorSubmit().props.disabled, false)
editorSubmit().props.onClick()
await editorRunner.settle()
assert.deepEqual(editorUploads.at(-1)[1], [newEditor.file])
selectThroughFilePicker(editorPhotos().props, [bad])
await editorRunner.settle()
editorPhotos().props.onRemove(0)
await editorRunner.settle()
selectThroughFilePicker(editorPhotos().props, [replacement])
await editorRunner.settle()
assert.equal(editorSubmit().props.disabled, false)
nextResponse = { status: 415, body: { message: 'Formato de arquivo não permitido.', code: 'MIDIA_FORMATO_INVALIDO', requestId: 'safe-415-id' } }
editorSubmit().props.onClick()
await editorRunner.settle()
assert.doesNotMatch(text(editorRunner.tree), /Tentar enviar fotos novamente/)
assert.equal(editorSubmit().props.disabled, true)
assert.match(text(editorRunner.tree), /salve uma nova cópia/)
assert.doesNotMatch(text(editorRunner.tree), /safe-415-id|MIDIA_FORMATO_INVALIDO|Código:|Request ID:/)
editorRunner.unmount()
console.log('PHOTO_EDITOR_COMPONENT_RESULT=OK mixedPreserved=true explicitSubmission=true staleResultsBlocked=true transientRetry=true')

const videoSuccessResponse = {
  ...mediaResponse,
  midias: [{ id: 'synthetic-video', tipo: 'VIDEO', status: 'PENDENTE', previewUrl: null }],
  limites: { ...mediaResponse.limites, videosAtivos: 1, videosDisponiveis: 0 },
}
const videoAttempted = new WeakSet()
const videoRunner = hooks()
const videoStep = moduleFromSource('features/anuncio-wizard/components/wizard-step-fotos.tsx', photoStepImports(videoRunner)).WizardStepFotos
const videoProps = {
  slug: 'synthetic', initialFiles: [], fotoNomes: [], videosNovos: [], onChange() {}, onChangeVideosNovos() {},
  persistedState: mediaResponse, pendingFiles: [], actorId: 'synthetic-owner', accountScope: 'synthetic-owner:edit:synthetic',
  uploadUnconfirmed: false,
  onPendingFilesChange(files) { videoProps.pendingFiles = files },
  onUploadUnconfirmedChange(value) { videoProps.uploadUnconfirmed = value },
  onAutoVideoStart(entry) {
    if (videoAttempted.has(entry)) return false
    videoAttempted.add(entry)
    return true
  },
}
const videoFetch = globalThis.fetch
globalThis.fetch = async (url, options) => {
  requests.push({ url, ...options })
  return new Response(JSON.stringify(mediaResponse), { status: 200, headers: { 'Content-Type': 'application/json' } })
}
nextResponse = { status: 200, body: videoSuccessResponse }
holdUploadResponse = true
videoRunner.mount(videoStep, videoProps)
selectThroughFilePicker(picker(videoRunner.tree, 'Selecionar fotos do anúncio').props, [bad])
videoRunner.update(videoProps)
selectThroughFilePicker(picker(videoRunner.tree, 'Selecionar vídeo do anúncio').props, [video])
videoRunner.update(videoProps)
await videoRunner.settle()
const videoUploads = editorUploads.filter((args) => args[1].length === 1 && args[1][0] === video)
assert.equal(videoUploads.length, 1, 'Selecionar vídeo válido deve iniciar exatamente um POST automático.')
assert.deepEqual(videoUploads[0][1], [video], 'Fotos pendentes ou persistidas não devem acompanhar o vídeo.')
assert.equal(videoUploads[0][4], mediaResponse.anuncio.id, 'Resposta deve ser vinculada ao anúncio esperado.')
assert.equal(videoUploads[0][5].actorId, 'synthetic-owner')
assert.equal(videoProps.pendingFiles.length, 2, 'Seleção permanece até resposta canônica.')
const localVideo = find(videoRunner.tree, (node) => node.type === 'video' && node.props['data-owner-video-local-preview'], 'prévia local do vídeo')
assert.match(localVideo.props.src, /^blob:/)
assert.equal(localVideo.props.autoPlay, undefined, 'Prévia não reproduz automaticamente.')
assert.equal(localVideo.props.preload, 'metadata')
assert.doesNotMatch(text(videoRunner.tree), /Enviar arquivos/)
const pendingVideoXhr = requests.filter((item) => item instanceof UploadXHR).at(-1)
const uploadProgress = []
const originalProgress = pendingVideoXhr.upload.onprogress
pendingVideoXhr.upload.onprogress = (event) => { uploadProgress.push(Math.min(99, Math.round(event.loaded / event.total * 100))); originalProgress(event) }
pendingVideoXhr.upload.onprogress({ lengthComputable: true, loaded: 2, total: 2 })
assert.deepEqual(uploadProgress, [99], 'Bytes enviados não significam confirmação do processamento.')
holdUploadResponse = false
pendingVideoXhr.onload()
await videoRunner.settle()
assert.deepEqual(videoProps.pendingFiles.map((entry) => entry.file), [bad], 'Confirmação do vídeo limpa somente o vídeo, preservando foto pendente.')
assert.equal(videoProps.uploadUnconfirmed, false)
videoRunner.unmount()
globalThis.fetch = videoFetch
console.log('OWNER_VIDEO_AUTO_UPLOAD_RESULT=OK isolatedVideo=true localPreview=true confirmedOnlyCleanup=true photoManual=true')

const retryVideoAttempts = new WeakSet()
const retryProps = {
  slug: 'synthetic', initialFiles: [], fotoNomes: [], videosNovos: [], onChange() {}, onChangeVideosNovos() {},
  persistedState: mediaResponse, pendingFiles: [], actorId: 'synthetic-owner', accountScope: 'synthetic-owner:edit:synthetic',
  uploadUnconfirmed: false,
  onPendingFilesChange(files) { retryProps.pendingFiles = files },
  onUploadUnconfirmedChange(value) { retryProps.uploadUnconfirmed = value },
  onAutoVideoStart(entry) {
    if (retryVideoAttempts.has(entry)) return false
    retryVideoAttempts.add(entry)
    return true
  },
}
globalThis.fetch = async (url, options) => {
  requests.push({ url, ...options })
  return new Response(JSON.stringify(mediaResponse), { status: 200, headers: { 'Content-Type': 'application/json' } })
}
const retryRunner = hooks()
const retryStep = moduleFromSource('features/anuncio-wizard/components/wizard-step-fotos.tsx', photoStepImports(retryRunner)).WizardStepFotos
retryRunner.mount(retryStep, retryProps)
nextResponse = { status: 503, body: { message: 'Falha sintética após o envio.' } }
selectThroughFilePicker(picker(retryRunner.tree, 'Selecionar vídeo do anúncio').props, [video])
retryRunner.update(retryProps)
await retryRunner.settle()
assert.equal(retryProps.uploadUnconfirmed, true, 'Resposta perdida exige reconciliação explícita.')
assert.equal(retryProps.pendingFiles[0].file, video, 'Arquivo e contexto permanecem na falha ambígua.')
const firstVideoXhr = requests.filter((item) => item instanceof UploadXHR).at(-1)
const retryKey = firstVideoXhr.headers.get('idempotency-key')
const sendsBeforeRemount = requests.filter((item) => item instanceof UploadXHR).length
retryRunner.unmount()
const remountRunner = hooks()
const remountStep = moduleFromSource('features/anuncio-wizard/components/wizard-step-fotos.tsx', photoStepImports(remountRunner)).WizardStepFotos
remountRunner.mount(remountStep, retryProps)
await remountRunner.settle()
assert.equal(requests.filter((item) => item instanceof UploadXHR).length, sendsBeforeRemount,
  'Retorno à etapa não repete automaticamente operação de resultado incerto.')
nextResponse = { status: 200, body: videoSuccessResponse }
button(remountRunner.tree, 'Conferir e repetir envio do vídeo').props.onClick()
await remountRunner.settle()
assert.equal(requests.filter((item) => item instanceof UploadXHR).at(-1).headers.get('idempotency-key'), retryKey,
  'Reconciliação explícita usa a chave da tentativa original.')
assert.equal(retryProps.pendingFiles.length, 0)
assert.equal(retryProps.uploadUnconfirmed, false)
remountRunner.unmount()
globalThis.fetch = videoFetch
console.log('OWNER_VIDEO_AMBIGUOUS_RESULT=OK noAutoRetryOnRemount=true sameKey=true explicitRecovery=true')

const videoGuardProps = {
  ...videoProps, pendingFiles: [], actorId: undefined, uploadUnconfirmed: false,
  onPendingFilesChange(files) { videoGuardProps.pendingFiles = files },
  onUploadUnconfirmedChange(value) { videoGuardProps.uploadUnconfirmed = value },
  onAutoVideoStart() { throw Error('Autoenvio não pode consumir a marca sem sessão.') },
}
const videoGuardRunner = hooks()
const videoGuardStep = moduleFromSource('features/anuncio-wizard/components/wizard-step-fotos.tsx', photoStepImports(videoGuardRunner)).WizardStepFotos
videoGuardRunner.mount(videoGuardStep, videoGuardProps)
const sendsBeforeMissingActor = requests.filter((item) => item instanceof UploadXHR).length
selectThroughFilePicker(picker(videoGuardRunner.tree, 'Selecionar vídeo do anúncio').props, [video])
videoGuardRunner.update(videoGuardProps)
await videoGuardRunner.settle()
assert.equal(requests.filter((item) => item instanceof UploadXHR).length, sendsBeforeMissingActor)
assert.match(text(videoGuardRunner.tree), /Entre novamente para confirmar a sessão/)
videoGuardRunner.unmount()

const videoLimitProps = {
  ...videoProps, pendingFiles: [], uploadUnconfirmed: false,
  persistedState: { ...mediaResponse, limites: { ...mediaResponse.limites, maxVideoBytes: 8 } },
  onPendingFilesChange(files) { videoLimitProps.pendingFiles = files },
  onUploadUnconfirmedChange(value) { videoLimitProps.uploadUnconfirmed = value },
  onAutoVideoStart() { throw Error('Vídeo acima do limite não pode iniciar envio.') },
}
const videoLimitRunner = hooks()
const videoLimitStep = moduleFromSource('features/anuncio-wizard/components/wizard-step-fotos.tsx', photoStepImports(videoLimitRunner)).WizardStepFotos
videoLimitRunner.mount(videoLimitStep, videoLimitProps)
const sendsBeforeLocalLimit = requests.filter((item) => item instanceof UploadXHR).length
selectThroughFilePicker(picker(videoLimitRunner.tree, 'Selecionar vídeo do anúncio').props, [video])
videoLimitRunner.update(videoLimitProps)
await videoLimitRunner.settle()
assert.equal(requests.filter((item) => item instanceof UploadXHR).length, sendsBeforeLocalLimit)
assert.match(text(videoLimitRunner.tree), /excede o limite de vídeo informado pelo aplicativo/)
assert.match(picker(videoLimitRunner.tree, 'Selecionar vídeo do anúncio').props.helperText, /Limite de vídeo informado pelo aplicativo/)
videoLimitRunner.unmount()

const video413Props = {
  ...videoProps, pendingFiles: [], uploadUnconfirmed: false,
  onPendingFilesChange(files) { video413Props.pendingFiles = files },
  onUploadUnconfirmedChange(value) { video413Props.uploadUnconfirmed = value },
  onAutoVideoStart: (() => { const attempted = new WeakSet(); return (entry) => {
    if (attempted.has(entry)) return false
    attempted.add(entry)
    return true
  } })(),
}
const video413Runner = hooks()
const video413Step = moduleFromSource('features/anuncio-wizard/components/wizard-step-fotos.tsx', photoStepImports(video413Runner)).WizardStepFotos
video413Runner.mount(video413Step, video413Props)
globalThis.fetch = async () => new Response(JSON.stringify(mediaResponse), { status: 200, headers: { 'Content-Type': 'application/json' } })
nextResponse = { status: 413, body: { message: 'Payload too large', code: 'PAYLOAD_TOO_LARGE', requestId: 'synthetic-413-id' } }
selectThroughFilePicker(picker(video413Runner.tree, 'Selecionar vídeo do anúncio').props, [video])
video413Runner.update(video413Props)
await video413Runner.settle()
assert.equal(video413Props.pendingFiles[0].file, video, '413 mantém o arquivo para diagnóstico e nova escolha.')
assert.match(text(video413Runner.tree), /causa exata da recusa não foi confirmada/i)
assert.match(text(video413Runner.tree), /Request ID: synthetic-413-id/)
assert.doesNotMatch(text(video413Runner.tree), /O vídeo selecionado .* excede o limite/)
video413Runner.unmount()

const video429Props = {
  ...videoProps, pendingFiles: [], uploadUnconfirmed: false,
  onPendingFilesChange(files) { video429Props.pendingFiles = files },
  onUploadUnconfirmedChange(value) { video429Props.uploadUnconfirmed = value },
  onAutoVideoStart: (() => { const attempted = new WeakSet(); return (entry) => {
    if (attempted.has(entry)) return false
    attempted.add(entry)
    return true
  } })(),
}
const video429Runner = hooks()
const video429Step = moduleFromSource('features/anuncio-wizard/components/wizard-step-fotos.tsx', photoStepImports(video429Runner)).WizardStepFotos
video429Runner.mount(video429Step, video429Props)
nextResponse = { status: 429, body: { message: 'Limite temporário de solicitações.' } }
selectThroughFilePicker(picker(video429Runner.tree, 'Selecionar vídeo do anúncio').props, [video])
video429Runner.update(video429Props)
await video429Runner.settle()
assert.equal(button(video429Runner.tree, 'Conferir e repetir envio do vídeo').props.disabled, false)
selectThroughFilePicker(picker(video429Runner.tree, 'Selecionar fotos do anúncio').props, [good])
video429Runner.update(video429Props)
await video429Runner.settle()
assert.equal(button(video429Runner.tree, 'Conferir e repetir envio do vídeo').props.disabled, false,
  'Selecionar foto separada não deve apagar o retry do vídeo que falhou.')
video429Runner.unmount()

const progressWithoutCanonicalVideo = []
const beforeMissingCanonical = requests.filter((item) => item instanceof UploadXHR).length
nextResponse = { status: 200, body: mediaResponse }
await assert.rejects(advertiserApi.enviarMinhasMidiasEmLote('synthetic', [video], (value) => progressWithoutCanonicalVideo.push(value),
  'synthetic-owner:edit:canonical-missing', mediaResponse.anuncio.id,
  { actorId: 'synthetic-owner', isCurrent: () => true }), (error) => error.code === 'MIDIAS_ESTADO_NAO_CONFIRMADO')
assert.equal(requests.filter((item) => item instanceof UploadXHR).length, beforeMissingCanonical + 1)
assert.doesNotMatch(progressWithoutCanonicalVideo.join(','), /100/, 'Resposta 2xx sem vídeo não confirma salvamento.')
const unconfirmedVideoKey = requests.filter((item) => item instanceof UploadXHR).at(-1).headers.get('idempotency-key')
nextResponse = { status: 200, body: videoSuccessResponse }
await advertiserApi.enviarMinhasMidiasEmLote('synthetic', [video], undefined,
  'synthetic-owner:edit:canonical-missing', mediaResponse.anuncio.id,
  { actorId: 'synthetic-owner', isCurrent: () => true, allowUnconfirmedRetry: true })
assert.equal(requests.filter((item) => item instanceof UploadXHR).at(-1).headers.get('idempotency-key'), unconfirmedVideoKey)

const beforeContextAbort = requests.filter((item) => item instanceof UploadXHR).length
let releaseCsrf
const pendingCsrf = new Promise((resolve) => { releaseCsrf = resolve })
globalThis.document.cookie = ''
globalThis.fetch = async () => pendingCsrf
let contextStillCurrent = true
const guardedRequest = advertiserApi.enviarMinhasMidiasEmLote('synthetic', [video], undefined, 'synthetic-owner:edit:synthetic',
  mediaResponse.anuncio.id, { actorId: 'synthetic-owner', isCurrent: () => contextStillCurrent })
await tick()
contextStillCurrent = false
releaseCsrf(new Response('{}', { status: 200 }))
await assert.rejects(guardedRequest, (error) => error.code === 'MIDIA_CONTEXT_CHANGED')
assert.equal(requests.filter((item) => item instanceof UploadXHR).length, beforeContextAbort,
  'Navegação/conta alterada durante CSRF não pode iniciar POST.')
globalThis.document.cookie = originalCookie
globalThis.fetch = videoFetch
console.log('OWNER_VIDEO_GUARDS_RESULT=OK noActorNoPost=true localLimit=true honest413=true canonicalRequired=true csrfContextAbort=true')

// Execute the creation selector itself as well as its parent's publication
// callbacks above, driving both native input/drop entry paths.
const creationRunner = hooks()
const creationStep = moduleFromSource('features/anuncio-wizard/components/wizard-step-fotos.tsx', photoStepImports(creationRunner)).WizardStepFotos
const creationFiles = []
const creationProps = {
  initialFiles: creationFiles, fotoNomes: [], videosNovos: [video],
  onChange(files) { creationProps.initialFiles = files; creationProps.fotoNomes = files.map((file) => file.name) },
  onChangeVideosNovos() {}, photoValidation: [], photoValidationPending: true,
}
creationRunner.mount(creationStep, creationProps)
selectThroughFilePicker(picker(creationRunner.tree, 'Selecionar fotos do anúncio').props, [good, bad], true)
creationRunner.update(creationProps)
assert.deepEqual(creationProps.initialFiles, [good, bad])
assert.match(text(creationRunner.tree), /Verificando foto…/)
creationProps.photoValidation = await Promise.all(creationProps.initialFiles.map(photo.validatePhotoUpload))
creationProps.photoValidationPending = false
creationRunner.update(creationProps)
assert.match(text(creationRunner.tree), /trailer.jpeg/)
assert.match(text(creationRunner.tree), /salve uma nova cópia/)
picker(creationRunner.tree, 'Selecionar fotos do anúncio').props.onRemove(1)
creationRunner.update(creationProps)
assert.deepEqual(creationProps.initialFiles, [good])
selectThroughFilePicker(picker(creationRunner.tree, 'Selecionar fotos do anúncio').props, [replacement])
creationRunner.update(creationProps)
assert.deepEqual(creationProps.initialFiles, [good, replacement])
assert.deepEqual(find(creationRunner.tree, (node) => node.type === 'VideoUploader', 'VideoUploader').props.newVideos, [video])
creationRunner.unmount()
console.log('PHOTO_CREATION_SELECTOR_RESULT=OK individualMessages=true validFilesPreserved=true videoBoundaryUnchanged=true')
console.log('PHOTO_UPLOAD_VALIDATION_RESULT=OK flows=wizard,advertiser-editor,admin browserDecoder=controlledMock')
