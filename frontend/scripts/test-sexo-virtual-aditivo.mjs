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
const storage = frontendSource('src/features/anuncio-wizard/wizard-storage.ts')
const adminEdit = frontendSource('src/features/admin-anuncios/admin-anuncio-edit-form.tsx')
const repository = repositorySource(
  'backend/src/main/java/br/com/topsdojob/v3/persistence/repository/AnuncioRepository.java',
)
const openapi = repositorySource('contracts/openapi/topsdojob-v3-local.yaml')

assert.ok(!constants.includes("{ value: 'VENDA_DE_CONTEUDO', label: 'Sexo Virtual' }"))
assert.ok(!legacyEditConstants.includes("value: 'VENDA_DE_CONTEUDO'"))
assert.ok(servicesStep.includes("state.servicos.includes('VIDEOCHAMADA')"))
assert.ok(servicesStep.includes('Atendimento exclusivamente virtual'))
assert.ok(servicesStep.includes('Marque apenas se você não realiza atendimento presencial'))
assert.ok(wizard.includes("value === 'VIDEOCHAMADA' && !set.has(value)"))
assert.ok(wizard.includes('{ atendimentoExclusivamenteVirtual: false }'))
assert.ok(storage.includes("legacyVirtualCategory ? 'ACOMPANHANTE_FEMININA'"))
assert.ok(storage.includes("servicos.push('VIDEOCHAMADA')"))
assert.ok(adminEdit.includes("form.servicos.includes('VIDEOCHAMADA')"))
assert.ok(adminEdit.includes('atendimentoExclusivamenteVirtual: false'))
assert.ok(repository.includes(":categoria = 'VENDA_DE_CONTEUDO'"))
assert.ok(repository.includes("av.servico = 'VIDEOCHAMADA'"))
assert.ok(repository.includes('a.atendimento_exclusivamente_virtual = false'))
assert.ok(!repository.includes('join anuncio_servicos av'))
assert.ok(openapi.includes(
  'enum: [ACOMPANHANTE_FEMININA, ACOMPANHANTE_MASCULINO, TRANSEX_TRAVESTIS, MASSAGENS]',
))
assert.ok(openapi.includes('description: So pode ser verdadeiro quando servicos contem VIDEOCHAMADA.'))

console.log('Sexo virtual aditivo: cadastro, edição e catálogo aprovados.')
