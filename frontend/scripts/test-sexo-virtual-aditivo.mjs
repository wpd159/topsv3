import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(scriptDirectory, '..')
const repositoryRoot = path.resolve(frontendRoot, '..')

function frontendSource(file) {
  return readFileSync(path.join(frontendRoot, file), 'utf8')
}

function repositorySource(file) {
  return readFileSync(path.join(repositoryRoot, file), 'utf8')
}

const constants = frontendSource('src/features/anuncio-wizard/wizard-constants.ts')
const legacyEditConstants = frontendSource('src/components/anuncios/editar/constants.ts')
const servicesStep = frontendSource('src/features/anuncio-wizard/components/wizard-step-servicos.tsx')
const wizard = frontendSource('src/features/anuncio-wizard/anuncio-wizard.tsx')
const wizardApi = frontendSource('src/features/anuncio-wizard/api.ts')
const storage = frontendSource('src/features/anuncio-wizard/wizard-storage.ts')
const adminEdit = frontendSource('src/features/admin-anuncios/admin-anuncio-edit-form.tsx')
const adminDetail = frontendSource('src/features/admin-anuncios/admin-anuncio-moderacao.tsx')
const preview = frontendSource('src/features/anuncio-wizard/components/wizard-preview.tsx')
const review = frontendSource('src/features/anuncio-wizard/components/wizard-final-review.tsx')
const publicDetail = frontendSource('src/app/(public-routes)/anuncios/[slug]/componentes/main-content.tsx')
const normalizer = frontendSource('src/utils/normalizer.ts')
const createService = repositorySource(
  'backend/src/main/java/br/com/topsdojob/v3/application/publico/service/SolicitarAnuncioPublicoService.java',
)
const repository = repositorySource(
  'backend/src/main/java/br/com/topsdojob/v3/persistence/repository/AnuncioRepository.java',
)
const openapi = repositorySource('contracts/openapi/topsdojob-v3-local.yaml')

assert.ok(constants.includes("{ value: 'VENDA_DE_CONTEUDO', label: 'Atendimento Virtual' }"))
assert.ok(constants.includes("{ value: 'VIDEOCHAMADA', label: 'Atendimento Virtual' }"))
assert.ok(legacyEditConstants.includes("{ label: 'Atendimento Virtual', value: 'VENDA_DE_CONTEUDO' }"))
assert.ok(legacyEditConstants.includes("{ label: 'Atendimento Virtual', value: 'VIDEOCHAMADA' }"))
assert.ok(wizardApi.includes("publicApiUrl('/categorias-home')"))
assert.ok(wizardApi.includes("item.identificador.trim() === 'VENDA_DE_CONTEUDO'"))
assert.ok(wizardApi.includes("'Atendimento Virtual'"))
assert.ok(wizard.includes('fetchWizardCategories'))
assert.ok(wizard.includes('categoryCatalog'))
assert.ok(preview.includes('label="Atendimento Virtual"'))
assert.ok(review.includes('label="Atendimento Virtual"'))
assert.ok(adminDetail.includes("value === 'VENDA_DE_CONTEUDO' || value === 'VIDEOCHAMADA'"))
assert.ok(publicDetail.includes("VIDEOCHAMADA: 'Atendimento Virtual'"))
assert.ok(normalizer.includes("VENDA_DE_CONTEUDO: 'Atendimento Virtual'"))
assert.ok(servicesStep.includes("state.servicos.includes('VIDEOCHAMADA')"))
assert.ok(servicesStep.includes('Atendimento exclusivamente virtual'))
assert.ok(servicesStep.includes('Marque apenas se você não realiza atendimento presencial'))
assert.ok(wizard.includes("value === 'VIDEOCHAMADA' && !set.has(value)"))
assert.ok(wizard.includes('{ atendimentoExclusivamenteVirtual: false }'))
assert.ok(createService.includes('CategoriaAnuncio.VENDA_DE_CONTEUDO.name().equals(categoria)'))
assert.ok(createService.includes('categoria = CategoriaAnuncio.ACOMPANHANTE_FEMININA.name()'))
assert.ok(createService.includes('normalizados.add(ServicoAnuncio.VIDEOCHAMADA)'))
assert.ok(storage.includes('categoria: asString(form?.categoria)'))
assert.ok(storage.includes("servicos.includes('VIDEOCHAMADA')"))
assert.ok(adminEdit.includes("form.servicos.includes('VIDEOCHAMADA')"))
assert.ok(adminEdit.includes('atendimentoExclusivamenteVirtual: false'))
assert.ok(repository.includes(":categoria = 'VENDA_DE_CONTEUDO'"))
assert.ok(repository.includes("av.servico = 'VIDEOCHAMADA'"))
assert.ok(repository.includes('a.atendimento_exclusivamente_virtual = false'))
assert.ok(!repository.includes('join anuncio_servicos av'))
assert.ok(openapi.includes('MASSAGENS, VENDA_DE_CONTEUDO]'))
assert.ok(openapi.includes('description: So pode ser verdadeiro quando servicos contem VIDEOCHAMADA.'))
assert.ok(constants.includes("value: 'VENDA_DE_CONTEUDO'"))
assert.ok(constants.includes("value: 'VIDEOCHAMADA'"))
assert.ok(!wizardApi.includes("value: 'ATENDIMENTO_VIRTUAL'"))

console.log('OK_SEXO_VIRTUAL_CATALOGO_BACKEND_E_REGRA_ADITIVA')
