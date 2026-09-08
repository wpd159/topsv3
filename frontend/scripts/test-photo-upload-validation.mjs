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
  '@/lib/photo-upload-validation': photo,
  '@/lib/api-contract': apiContract,
  ...extra,
})

console.log('PHOTO_HELPER_RESULT=OK syntheticEncodedFixtures=true browserDecoder=controlledMock')

const mediaResponse = { midias: [], limites: { fotosDisponiveis: 10, videosDisponiveis: 1, fotosAtivas: 0, maxFotos: 10, videosAtivos: 0, maxVideos: 1, videoAtivo: true } }
const requests = []
let nextResponse = { status: 200, body: mediaResponse }
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
    queueMicrotask(() => this.status === 0 ? this.onerror() : this.onload())
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
console.log('PHOTO_FILE_PICKER_BOUNDARIES_RESULT=OK selectionAndDrop=true kycUnchanged=true')

const wizardTypes = moduleFromSource('features/anuncio-wizard/types.ts')
const wizardConstants = moduleFromSource('features/anuncio-wizard/wizard-constants.ts')
const wizardRunner = hooks()
const wizardCalls = { create: 0, upload: 0, kyc: 0, update: 0 }
let wizardUploadFailure = null
const wizardErrors = []
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
  setDocumentos() {}, nextStep() {}, previousStep() {}, reset() {}, clearCurrentCache() {}, hydrateFromBackend() {},
}
const user = { id: 'synthetic-user', dataNascimento: '1990-01-01' }
const locations = { estados: [], cidades: [], bairros: [], loadBairros: async () => {}, loadCidades: async () => {} }
const wizardImports = componentImports(wizardRunner, {
  'next/navigation': { useRouter: () => ({ push() {} }) },
  sonner: { toast: { error: (message) => wizardErrors.push(message), warning: (message) => wizardErrors.push(message), success() {} } },
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
      assert.ok(!files.includes(bad))
      if (wizardUploadFailure) throw wizardUploadFailure
      return mediaResponse
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
  './use-anuncio-wizard-store': {
    useAnuncioWizardStore: () => wizardStore,
    // Unrelated profile/KYC form rules are pre-satisfied. Photo coordination and
    // publication callbacks run from the actual component, not reimplemented.
    validateWizardKycState: () => null, validateWizardStep: () => null, wizardSteps: steps,
  },
  './types': wizardTypes,
  './wizard-progress': { clearWizardProgressSessionId() {}, createWizardProgressSessionId: () => 'synthetic-session', syncWizardProgress: async () => {} },
})
for (const [file, name] of [
  ['wizard-final-review', 'WizardFinalReview'], ['wizard-preview', 'WizardPreview'],
  ['wizard-step-fotos', 'WizardStepFotos'], ['wizard-step-kyc', 'WizardStepKyc'],
  ['wizard-step-localizacao', 'WizardStepLocalizacao'], ['wizard-step-perfil', 'WizardStepPerfil'],
  ['wizard-step-premium', 'WizardStepPremium'], ['wizard-step-servicos', 'WizardStepServicos'],
]) wizardImports[`./components/${file}`] = { [name]: name }
globalThis.window = { requestAnimationFrame: (callback) => callback() }
const { default: AnuncioWizard } = moduleFromSource('features/anuncio-wizard/anuncio-wizard.tsx', wizardImports)
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
publish().props.onClick()
await wizardRunner.settle()
assert.equal(wizardCalls.create, 2)
assert.deepEqual(wizardStore.state.form.fotos, [transientWizardPhoto])
wizardStore.currentIndex = 6
wizardRunner.render()
await wizardRunner.settle()
assert.equal(publish().props.disabled, false, 'Falha transitória permite retry explícito do wizard.')
wizardUploadFailure = null
publish().props.onClick()
await wizardRunner.settle()
assert.equal(wizardCalls.create, 2, 'Retry não deve criar um segundo anúncio para a mesma publicação.')
assert.equal(wizardCalls.upload, 3)

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
wizardRunner.unmount()
console.log('PHOTO_WIZARD_PUBLICATION_RESULT=OK createAndUploadBlocked=true asyncSelection=OK')

const editorRefs = []
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
const photoOriginRefs = editorRefs.filter((ref) => ref.current instanceof WeakSet)
assert.equal(photoOriginRefs.length, 1, 'A origem das fotos deve usar referência fraca, sem reter arquivos enviados até o unmount.')
const photoOriginRef = photoOriginRefs[0]
const editorPhotos = () => picker(editorRunner.tree, 'Selecionar fotos do anúncio')
const editorVideos = () => picker(editorRunner.tree, 'Selecionar vídeo do anúncio')
const editorSubmit = () => find(editorRunner.tree, (node) => node.type === 'button' && /Enviar arquivos|Verificando|Tentar enviar novamente|Enviando/.test(text(node)), 'Enviar arquivos')
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
assert.equal(photoOriginRef.current.has(good), true)
assert.equal(photoOriginRef.current.has(bad), true)
assert.match(text(editorRunner.tree), /trailer.jpeg/)
assert.equal(editorSubmit().props.disabled, true)
assert.doesNotMatch(text(editorRunner.tree), /Tentar enviar novamente/)
assert.equal(editorUploads.length, 0)
assert.equal(requests.length, beforeEditorRequests)
selectThroughFilePicker(editorVideos().props, [video])
await editorRunner.settle()
assert.deepEqual(editorPhotos().props.files, [good, bad])
assert.deepEqual(editorVideos().props.files, [video])
editorPhotos().props.onRemove(1)
await editorRunner.settle()
assert.deepEqual(editorPhotos().props.files, [good])
assert.equal(photoOriginRef.current.has(bad), false, 'Remoção deve limpar a associação de origem do arquivo recusado.')
assert.equal(photoOriginRef.current.has(good), true, 'Remoção da recusada preserva a origem da foto válida.')
assert.equal(editorSubmit().props.disabled, false)
assert.equal(editorUploads.length, 0, 'Remover inválida não deve disparar upload parcial automático.')
const previouslyValidEditorSubmit = editorSubmit()
nextResponse = { status: 503, body: { message: 'Serviço temporariamente indisponível.' } }
editorSubmit().props.onClick()
await editorRunner.settle()
assert.deepEqual(editorUploads.at(-1)[1], [good, video])
assert.deepEqual(editorPhotos().props.files, [good])
const editorRetryKey = requests.at(-1).headers.get('idempotency-key')
button(editorRunner.tree, 'Tentar enviar novamente').props.onClick()
await editorRunner.settle()
assert.equal(requests.at(-1).headers.get('idempotency-key'), editorRetryKey)
nextResponse = { status: 200, body: mediaResponse }
button(editorRunner.tree, 'Tentar enviar novamente').props.onClick()
await editorRunner.settle()
assert.equal(editorPhotos().props.files.length, 0)
assert.equal(editorVideos().props.files.length, 0)
assert.ok(photoOriginRef.current instanceof WeakSet, 'Após sucesso, a coleção de origem continua sem referências fortes aos arquivos liberados pela seleção.')

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
assert.equal(button(editorRunner.tree, 'Tentar enviar novamente').props.disabled, false, 'Falha de rede antes do XHR também permite retry.')
globalThis.fetch = originalFetch
globalThis.document.cookie = originalCookie
nextResponse = { status: 200, body: mediaResponse }
button(editorRunner.tree, 'Tentar enviar novamente').props.onClick()
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
assert.doesNotMatch(text(editorRunner.tree), /Tentar enviar novamente/)
assert.equal(editorSubmit().props.disabled, true)
assert.match(text(editorRunner.tree), /salve uma nova cópia/)
assert.doesNotMatch(text(editorRunner.tree), /safe-415-id|MIDIA_FORMATO_INVALIDO|Código:|Request ID:/)
editorRunner.unmount()
console.log('PHOTO_EDITOR_COMPONENT_RESULT=OK mixedPreserved=true explicitSubmission=true staleResultsBlocked=true transientRetry=true')

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
