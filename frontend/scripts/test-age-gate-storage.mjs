import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const modal = fs.readFileSync(
  path.join(root, 'src/components/modals/age-gate-modal.tsx'),
  'utf8',
)
const verification = fs.readFileSync(
  path.join(root, 'src/components/compliance/visitor-verification-modal.tsx'),
  'utf8',
)
const access = fs.readFileSync(
  path.join(root, 'src/lib/compliance/visitor-access.ts'),
  'utf8',
)

assert.match(modal, /obterStatusVisitante\(true\)/)
assert.match(modal, /VisitorVerificationModal/)
assert.doesNotMatch(modal, /age-gate-storage|age_gate_accepted/)
assert.match(verification, /placeholder="dd\/mm\/aaaa"/)
assert.match(verification, /\/idade\/confirmar/)
assert.match(verification, /setVerified\(payload\.expiraEm, payload\.expiraEm\)/)
assert.match(access, /\/idade\/status/)
assert.match(access, /fonte de verdade/)
assert.doesNotMatch(access, /return mirrored/)

console.log('OK_AGE_GATE_BACKEND_FONTE_UNICA')
