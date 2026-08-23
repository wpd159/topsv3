import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const modal = fs.readFileSync(
  path.join(root, 'src/components/modals/age-gate-modal.tsx'),
  'utf8',
)

assert.match(modal, /h-\[100dvh\]/)
assert.match(modal, /max-h-\[100dvh\]/)
assert.doesNotMatch(modal, /(?:^|[^d])100vh/)
assert.match(modal, /flex-col/)
assert.match(modal, /overflow-hidden/)

assert.match(modal, /data-age-gate-header/)
assert.match(modal, /data-age-gate-body/)
assert.match(modal, /min-h-0 flex-1 overflow-y-auto/)
assert.match(modal, /data-age-gate-footer/)
assert.match(modal, /shrink-0/)
assert.match(modal, /safe-area-inset-top/)
assert.match(modal, /safe-area-inset-bottom/)
assert.match(modal, /safe-area-inset-left/)
assert.match(modal, /safe-area-inset-right/)

assert.match(modal, /sm:h-auto/)
assert.match(modal, /sm:max-h-\[85vh\]/)
assert.match(modal, /sm:max-w-md/)
assert.match(modal, /sm:rounded-2xl/)
assert.match(modal, /sm:p-6/)
assert.match(modal, /sm:overflow-y-auto/)

assert.match(modal, /Ao clicar em <b>Aceitar<\/b>/)
assert.match(modal, /Termos de Uso/)
assert.match(modal, /confirmarAceiteGlobal\(pathname \|\| '\/'\)/)
assert.doesNotMatch(modal, /document\.body\.style|overflow\s*=\s*['"]hidden/)

console.log('OK_AGE_GATE_MOBILE_LAYOUT')
