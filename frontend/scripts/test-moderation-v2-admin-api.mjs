import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const clientPath = path.resolve(scriptDirectory, '../src/features/moderation-v2/api/client.ts')
const source = readFileSync(clientPath, 'utf8')

assert.ok(
  source.includes("adminApiUrl('/anuncios?page=0&size=100')"),
  'A listagem deve usar o contrato administrativo canonico de anuncios.'
)
assert.ok(
  source.includes("adminApiUrl('/moderacao/revisoes?page=0&size=100')"),
  'A fila deve usar o contrato administrativo canonico de revisoes.'
)
assert.ok(
  !source.includes('apiBase()'),
  'O adapter nao deve manter um resolvedor local de base URL.'
)
assert.ok(
  !source.includes('/anuncios/' + 'staff'),
  'O adapter nao deve manter a familia administrativa legada.'
)

console.log('Moderacao v2: contratos administrativos canonicos validados.')
