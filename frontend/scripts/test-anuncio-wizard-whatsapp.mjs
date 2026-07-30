import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(scriptDirectory, '..')

function source(file) {
  return readFileSync(path.join(frontendRoot, file), 'utf8')
}

const servicesStep = source('src/features/anuncio-wizard/components/wizard-step-servicos.tsx')
const store = source('src/features/anuncio-wizard/use-anuncio-wizard-store.ts')
const api = source('src/features/anuncio-wizard/api.ts')
const types = source('src/features/anuncio-wizard/types.ts')
const accountPage = source('src/app/(private-routes)/minha-conta/page.tsx')

assert.ok(!servicesStep.includes('<Field label="WhatsApp">'))
assert.ok(!servicesStep.includes('onPatch({ whatsapp:'))
assert.ok(!types.includes('whatsapp: string'))
assert.ok(!store.includes('form.whatsapp'))
assert.ok(!api.includes('whatsapp: state.whatsapp'))
assert.ok(accountPage.includes("const [telefone, setTelefone] = useState('')"))
assert.ok(accountPage.includes('updatePublicProfile({ username, telefone })'))
assert.ok(api.includes("publicApiUrl('/anunciar')"))

console.log('OK_WIZARD_USA_TELEFONE_CANONICO_DA_CONTA')
