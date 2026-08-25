import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const read = (path) => readFile(`${root}/${path}`, 'utf8')

const [adapter, registerForm] = await Promise.all([
  read('src/lib/public-auth-api.ts'),
  read('src/components/auth/register-form.tsx'),
])

assert.doesNotMatch(adapter, /checkDuplicidade|emailExistente|usernameExistente|telefoneExistente/)
assert.doesNotMatch(registerForm, /checkDuplicidade|verifyDuplicate|duplicateErrors|DuplicidadeResposta/)
assert.doesNotMatch(registerForm, /Este e-mail ja esta em uso|Este telefone ja esta cadastrado/)
assert.match(registerForm, /submitRegister/)
assert.match(registerForm, /toast\.error\(result\.message\)/)

console.log('public auth anti-enumeration frontend contract: OK')
