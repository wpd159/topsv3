import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const sourceRoot = path.resolve(scriptDirectory, '../src/features/admin-anuncios')

function source(file) {
  return readFileSync(path.join(sourceRoot, file), 'utf8')
}

const detail = source('admin-anuncio-moderacao.tsx')
const api = source('api.ts')
const types = source('types.ts')

const selection = detail.slice(
  detail.indexOf('function selectPhotoDecision'),
  detail.indexOf('async function confirmPhotoBatch'),
)
const batch = detail.slice(
  detail.indexOf('async function confirmPhotoBatch'),
  detail.indexOf('async function confirmDecision'),
)
const mediaTab = detail.slice(
  detail.indexOf('<TabsContent value="midias">'),
  detail.indexOf('<TabsContent value="documentos">'),
)

assert.ok(selection.includes("choice: 'LIVRE' | 'RESTRITA_18' | 'EXCLUIR'"))
assert.ok(!selection.includes('decideAdminMedia') && !selection.includes('decideAdminPhotosBatch'))
assert.ok(selection.includes("choice === 'RESTRITA_18' ? previous?.observacao ?? '' : ''"))
assert.ok(detail.includes("selected === 'RESTRITA_18' ? ("))
assert.ok(detail.includes("selected === 'EXCLUIR' ? ("))
assert.ok(detail.includes('EXCLUIR FOTO'))
assert.ok(!mediaTab.includes('Rejeitar foto'))
assert.ok(mediaTab.includes('Confirmar decisões das fotos ({selectedPhotoCount})'))
assert.equal((detail.match(/<PhotoBatchDialog/g) ?? []).length, 1)
assert.ok(detail.includes('selectedPhotoCount === pendingPhotos.length'))
assert.ok(batch.includes('decideAdminPhotosBatch'))
assert.ok(batch.includes('pendingPhotos.map'))
assert.ok(batch.includes("decision.classificacao === 'RESTRITA_18'"))
assert.ok(batch.includes("filter((item) => item.resultado === 'FALHA')"))
assert.ok(batch.includes('failedIds.has(mediaId)'))
assert.ok(detail.includes('photoBatchLock.current'))
assert.ok(mediaTab.includes("item.tipo === 'VIDEO'"))
assert.ok(mediaTab.includes('Sempre RESTRITA_18'))
assert.ok(mediaTab.includes("title: 'Rejeitar vídeo'"))

assert.ok(api.includes('export function decideAdminPhotosBatch'))
assert.ok(api.includes('body: JSON.stringify({ fotos })'))
assert.ok(types.includes("decisao: 'APROVAR' | 'EXCLUIR'"))
assert.ok(types.includes("resultado: 'APROVADA' | 'EXCLUIDA' | 'JA_PROCESSADA' | 'FALHA'"))

console.log('Moderacao em lote de fotos: contrato frontend aprovado.')
