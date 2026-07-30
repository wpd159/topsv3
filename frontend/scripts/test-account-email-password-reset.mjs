import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const read = (path) => readFile(`${root}/${path}`, 'utf8')

const [
  modalSource,
  passwordInputSource,
  requirementsSource,
  registerSource,
  formatter,
] = await Promise.all([
  read('src/components/modals/recuperar-senha-modal.tsx'),
  read('src/components/auth/password-input.tsx'),
  read('src/components/auth/password-requirements.tsx'),
  read('src/components/auth/register-form.tsx'),
  import(new URL('../src/utils/formatter.ts', import.meta.url)),
])

assert.equal(Object.values(formatter.validatePassword('Nova@Forte9')).every(Boolean), true)
for (const invalid of [
  'Curta@1',
  'semmaiuscula@1',
  'SEMMINUSCULA@1',
  'SemNumero@',
  'SemSimbolo9',
  'Senha@1234',
]) {
  assert.equal(
    Object.values(formatter.validatePassword(invalid)).every(Boolean),
    false,
    `senha inválida aceita: ${invalid}`
  )
}

assert.match(registerSource, /PasswordInput/)
assert.match(registerSource, /PasswordRequirements/)
assert.doesNotMatch(registerSource, /const CREDENTIAL_RULES/)
assert.match(modalSource, /<PasswordRequirements/)
assert.equal((modalSource.match(/<PasswordInput/g) || []).length, 2)
assert.match(modalSource, /const valuesMatch = confirmationStarted/)
assert.match(modalSource, /As senhas coincidem\./)
assert.match(modalSource, /As senhas não coincidem\./)
assert.match(modalSource, /disabled=\{loading \|\| disabled\}/)
assert.match(modalSource, /type=\{submit \? 'submit' : 'button'\}/)
assert.match(modalSource, /max-h-\[calc\(100dvh-2rem\)\]/)
assert.doesNotMatch(modalSource, /transition-all|max-height|overflow-hidden.*opacity/)

assert.match(passwordInputSource, /type=\{visible \? 'text' : 'password'\}/)
assert.match(passwordInputSource, /autoComplete="new-password"/)
assert.match(passwordInputSource, /type="button"/)
assert.match(passwordInputSource, /aria-label=\{`\$\{visible \? 'Ocultar' : 'Mostrar'\}/)
assert.match(passwordInputSource, /useState\(false\)/)

for (const requirement of [
  'Pelo menos 8 caracteres',
  'Contém letra maiúscula',
  'Contém letra minúscula',
  'Contém número',
  'Contém pontuação ou símbolo',
  'Não contém sequências óbvias',
]) {
  assert.ok(requirementsSource.includes(requirement), `requisito ausente: ${requirement}`)
}

for (const errorMessage of [
  'A senha não atende aos requisitos de segurança exibidos.',
  'As senhas não coincidem.',
  'Este código já foi utilizado.',
  'Este código expirou.',
  'O código informado é inválido.',
  'O serviço está temporariamente indisponível.',
]) {
  assert.ok(modalSource.includes(errorMessage), `mensagem ausente: ${errorMessage}`)
}

console.log('account email/password reset frontend contract: OK')
