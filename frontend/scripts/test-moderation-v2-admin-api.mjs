import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const clientPath = path.resolve(scriptDirectory, '../src/features/moderation-v2/api/client.ts')
const source = readFileSync(clientPath, 'utf8')

assert.ok(
  source.includes("const backendRoot = apiBase().replace(/\\/api\\/public$/, '')"),
  'O cliente administrativo deve remover o sufixo /api/public da origem configurada.'
)
assert.ok(
  source.includes('return `${backendRoot}/api/admin${path}`'),
  'As rotas de moderacao devem usar uma unica raiz /api/admin.'
)
assert.ok(
  !source.includes('`${apiBase()}/api/admin'),
  'A base publica nao pode ser concatenada diretamente com /api/admin.'
)
assert.ok(
  source.includes('`${apiBase()}/anuncios/staff`'),
  'Os contratos de anuncios staff existentes devem permanecer na base publica configurada.'
)

console.log('Moderacao v2: raiz administrativa validada sem /api/public/api/admin.')
