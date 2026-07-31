import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import * as ts from 'typescript'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(scriptDirectory, '..')
const sourceRoot = path.join(frontendRoot, 'src')

function source(relativePath) {
  return readFileSync(path.resolve(sourceRoot, relativePath), 'utf8')
}

const cpfSource = source('lib/cpf-mask.ts')
const cpfModuleSource = ts.transpileModule(cpfSource, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText
const cpfModule = await import(
  `data:text/javascript;base64,${Buffer.from(cpfModuleSource).toString('base64')}`
)

assert.equal(cpfModule.isValidCpf('529.982.247-25'), true)
assert.equal(cpfModule.isValidCpf('529.982.247-24'), false)
assert.equal(cpfModule.isValidCpf('111.111.111-11'), false)
assert.equal(cpfModule.isValidCpf('123'), false)
assert.equal(cpfModule.isValidCpf('5299822472500'), false)

const wizardApi = source('features/anuncio-wizard/api.ts')
const wizardStore = source('features/anuncio-wizard/use-anuncio-wizard-store.ts')
const adminEdit = source('features/admin-usuarios/admin-usuario-edit-form.tsx')
const adminTypes = source('features/admin-usuarios/types.ts')

assert.ok(
  wizardApi.includes("normalizedCode === 'CPF_INVALIDO'"),
  'O wizard deve distinguir CPF invalido da resposta generica.',
)
assert.ok(
  wizardApi.includes("if (!isValidCpf(input.cpf))"),
  'O envio do KYC deve validar os digitos antes da requisicao.',
)
assert.ok(
  wizardStore.includes("!isValidCpf(kyc.cpf)"),
  'A etapa KYC nao pode validar apenas o tamanho do CPF.',
)
assert.ok(
  adminEdit.includes("if (cpf !== cpfDigits(detail.cpf || '')) payload.cpf = cpf"),
  'O admin deve enviar CPF somente quando o campo mudar.',
)
assert.ok(
  adminEdit.includes('if (!hasChangedFields(payload))'),
  'O admin nao deve produzir PATCH vazio.',
)
assert.ok(
  adminEdit.includes('payload.cpf !== undefined && !isValidCpf(payload.cpf)'),
  'O admin deve validar checksum somente quando o CPF for alterado.',
)
assert.match(adminTypes, /cpf\?: string/)

console.log('CPF: checksum, erros distintos e PATCH parcial validados.')
