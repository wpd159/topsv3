import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const ts = require('typescript')
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = fs.readFileSync(path.join(root, 'src/lib/compliance/age-gate-storage.ts'), 'utf8')
const modalSource = fs.readFileSync(path.join(root, 'src/components/modals/age-gate-modal.tsx'), 'utf8')
const output = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText

assert.doesNotMatch(modalSource, /compliance\/policies/)

const storageValues = new Map()
const cookieValues = new Map()
const localStorage = {
  getItem: (key) => storageValues.get(key) ?? null,
  setItem: (key, value) => storageValues.set(key, String(value)),
  removeItem: (key) => storageValues.delete(key),
}
const document = {
  get cookie() {
    return [...cookieValues].map(([key, value]) => `${key}=${value}`).join('; ')
  },
  set cookie(raw) {
    const parts = String(raw).split(';').map((part) => part.trim())
    const [nameValue, ...attributes] = parts
    const separator = nameValue.indexOf('=')
    const name = nameValue.slice(0, separator)
    const value = nameValue.slice(separator + 1)
    const expired = attributes.some((attribute) => attribute.toLowerCase() === 'max-age=0')
    if (expired) cookieValues.delete(name)
    else cookieValues.set(name, value)
  },
}
const module = { exports: {} }
const sandbox = {
  module,
  exports: module.exports,
  window: {
    localStorage,
    location: { hostname: 'v3.esle.cloud', protocol: 'https:' },
  },
  document,
  Date,
  Number,
  Math,
  RegExp,
  decodeURIComponent,
  encodeURIComponent,
}
vm.runInNewContext(output, sandbox, { filename: 'age-gate-storage.js' })

const { acceptAgeGate, clearAgeGateClient, readAgeGateClientStatus } = module.exports

assert.deepEqual(
  JSON.parse(JSON.stringify(readAgeGateClientStatus())),
  { accepted: false, expiresAt: null },
)

const accepted = await acceptAgeGate()
assert.equal(accepted.accepted, true)
assert.ok(accepted.expiresAt > Date.now())
assert.equal(readAgeGateClientStatus().accepted, true)
assert.match(document.cookie, /age_gate_accepted=/)
assert.ok(storageValues.has('age_gate_accepted_until'))

const requestsBeforeReload = readAgeGateClientStatus()
assert.equal(requestsBeforeReload.accepted, true)
assert.equal(requestsBeforeReload.expiresAt, accepted.expiresAt)

clearAgeGateClient()
assert.equal(readAgeGateClientStatus().accepted, false)
assert.equal(cookieValues.has('age_gate_accepted'), false)
assert.equal(storageValues.has('age_gate_accepted_until'), false)

console.log('OK_AGE_GATE_ACEITE_PERSISTIDO_SEM_CONTRATO_LEGADO')
