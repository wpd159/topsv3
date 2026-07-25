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

assert.ok(servicesStep.includes('<Field label="WhatsApp">'))
assert.ok(!servicesStep.includes('mode === \'edit\' ? <Field label="WhatsApp">'))
assert.ok(servicesStep.includes('onPatch({ whatsapp: event.target.value })'))
assert.ok(store.includes('!form.whatsapp.trim()'))
assert.ok(store.includes('horário, WhatsApp, local de atendimento'))
assert.ok(api.includes('whatsapp: state.whatsapp.trim()'))

console.log('Wizard de anuncio exige e envia WhatsApp tambem no cadastro.')
