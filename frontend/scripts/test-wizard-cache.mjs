import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const ts = require('typescript')
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = fs.readFileSync(path.join(root, 'src/features/anuncio-wizard/wizard-storage.ts'), 'utf8')
const output = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText

const emptyState = {
  currentStep: 'perfil',
  form: {
    titulo: '', categoria: '', descricaoPerfil: '', preco: '', horario: '',
    locaisAtendimento: [], servicos: [], atendimentoExclusivamenteVirtual: false,
    descricao: '', linkConteudo: '',
    estadoId: '', cidadeId: '', bairroId: '', estadoNome: '', estadoUf: '',
    cidadeNome: '', bairroNome: '', pontoReferenciaTexto: '', fotos: [], fotoNomes: [],
    videos: [], videoNomes: [], editPendingMedia: [], editPendingMediaScope: '', editUploadUnconfirmed: false,
    premiumChoice: 'gratis',
  },
  kyc: {
    nomeCompleto: '', dataNascimento: '', cpf: '', documentoModo: null,
    documentos: [], documentoNomes: [],
  },
}
const values = new Map()
const localStorage = {
  getItem: (key) => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, String(value)),
  removeItem: (key) => values.delete(key),
}
const module = { exports: {} }
const sandbox = {
  module,
  exports: module.exports,
  window: { localStorage },
  require: (specifier) => {
    if (specifier === './types') {
      return {
        initialWizardState: emptyState,
        wizardStepIds: ['perfil', 'localizacao', 'servicos', 'fotos', 'revisao', 'premium', 'kyc'],
      }
    }
    throw new Error(`Unexpected module: ${specifier}`)
  },
  Date,
  JSON,
  Set,
  encodeURIComponent,
  Error,
}
vm.runInNewContext(output, sandbox, { filename: 'wizard-storage.js' })

const {
  clearWizardCache,
  discardUnsafeLegacyWizardCache,
  loadWizardCache,
  saveWizardCache,
  wizardCacheKey,
} = module.exports

const userACreate = { userId: 'user-a', mode: 'create' }
const userBCreate = { userId: 'user-b', mode: 'create' }
const userAEditOne = { userId: 'user-a', mode: 'edit', slug: 'anuncio-um' }
const userAEditTwo = { userId: 'user-a', mode: 'edit', slug: 'anuncio-dois' }

assert.notEqual(wizardCacheKey(userACreate), wizardCacheKey(userBCreate))
assert.notEqual(wizardCacheKey(userACreate), wizardCacheKey(userAEditOne))
assert.notEqual(wizardCacheKey(userAEditOne), wizardCacheKey(userAEditTwo))
assert.match(source, /STORAGE_VERSION = 5/)
assert.ok(!source.includes('form?.whatsapp'))

saveWizardCache(userACreate, { ...emptyState, form: { ...emptyState.form, titulo: 'Rascunho A' } }, null)
saveWizardCache(userBCreate, { ...emptyState, form: { ...emptyState.form, titulo: 'Rascunho B' } }, null)
saveWizardCache(userAEditOne, { ...emptyState, form: { ...emptyState.form, titulo: 'Edicao um' } }, 'source-1')
saveWizardCache(userAEditTwo, { ...emptyState, form: { ...emptyState.form, titulo: 'Edicao dois' } }, 'source-2')
const selectedFile = { name: 'private-photo.jpg', contents: 'PRIVATE_FILE_BYTES_MUST_NOT_PERSIST' }
saveWizardCache(userAEditOne, { ...emptyState, form: { ...emptyState.form, titulo: 'Edicao um',
  editPendingMedia: [{ file: selectedFile, kind: 'photo' }],
  editPendingMediaScope: 'user-a:edit:anuncio-um', editUploadUnconfirmed: true,
} }, 'source-1')
const persistedEdit = localStorage.getItem(wizardCacheKey(userAEditOne))
assert.doesNotMatch(persistedEdit, /private-photo|PRIVATE_FILE_BYTES|editPendingMedia|editUploadUnconfirmed|editPendingMediaScope/)
assert.equal(loadWizardCache(userAEditOne).state.form.editPendingMedia.length, 0)
assert.equal(loadWizardCache(userAEditOne).state.form.editUploadUnconfirmed, false)

assert.equal(loadWizardCache(userACreate).state.form.titulo, 'Rascunho A')
assert.equal(loadWizardCache(userBCreate).state.form.titulo, 'Rascunho B')
assert.equal(loadWizardCache(userAEditOne).state.form.titulo, 'Edicao um')
assert.equal(loadWizardCache(userAEditTwo).state.form.titulo, 'Edicao dois')
assert.equal(loadWizardCache(userAEditOne).sourceVersion, 'source-1')

clearWizardCache(userAEditOne)
assert.equal(loadWizardCache(userAEditOne), null)
assert.equal(loadWizardCache(userAEditTwo).state.form.titulo, 'Edicao dois')
assert.equal(loadWizardCache(userBCreate).state.form.titulo, 'Rascunho B')

localStorage.setItem('topsdojob:anuncio-wizard:v2', '{"unsafe":true}')
discardUnsafeLegacyWizardCache()
assert.equal(localStorage.getItem('topsdojob:anuncio-wizard:v2'), null)

console.log('OK_WIZARD_CACHE_ISOLADO_USUARIO_MODO_SLUG')
