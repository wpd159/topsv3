import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = fs.readFileSync(path.join(root, 'src/lib/meus-anuncios-api.ts'), 'utf8')

assert.match(source, /new WeakMap<File, string>\(\)/)
assert.match(source, /crypto\.randomUUID\(\)/)
assert.match(source, /setRequestHeader\('Idempotency-Key', mediaUploadIdempotencyKey\(arquivo\)\)/)
assert.match(source, /mediaUploadIdempotencyKeys\.delete\(arquivo\)/)
assert.doesNotMatch(source, /localStorage.*Idempotency-Key|Idempotency-Key.*localStorage/s)

console.log('OK upload reutiliza chave no retry e a descarta apos sucesso sem persistencia local')
