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
const uploader = source('admin-anuncio-midia-uploader.tsx')
const api = source('api.ts')
const types = source('types.ts')
const contractState = readFileSync(
  path.resolve(scriptDirectory, '../src/components/feedback/contract-state.tsx'),
  'utf8',
)

const selection = detail.slice(
  detail.indexOf('function selectPhotoDecision'),
  detail.indexOf('async function confirmPhotoBatch'),
)
const batch = detail.slice(
  detail.indexOf('async function confirmPhotoBatch'),
  detail.indexOf('async function confirmDecision'),
)
const photoDelete = detail.slice(
  detail.indexOf('async function confirmPhotoDelete'),
  detail.indexOf('async function confirmDecision'),
)
const mediaTab = detail.slice(
  detail.indexOf('<TabsContent value="midias"'),
  detail.indexOf('<TabsContent value="documentos"'),
)
const uploadAdapterStart = api.indexOf('export async function uploadAdminAdMedia')
const uploadAdapterEnd = api.indexOf('export async function listAdminAdHistory', uploadAdapterStart)
assert.ok(uploadAdapterStart >= 0 && uploadAdapterEnd > uploadAdapterStart, 'O recorte deve alcançar o corpo real do adapter assíncrono de upload.')
const uploadAdapter = api.slice(uploadAdapterStart, uploadAdapterEnd)
const uploadSubmit = uploader.slice(
  uploader.indexOf('async function submit'),
  uploader.indexOf('\n  return ('),
)

assert.ok(selection.includes("choice: 'LIVRE' | 'RESTRITA_18'"))
assert.ok(!selection.includes('decideAdminMedia') && !selection.includes('decideAdminPhotosBatch'))
assert.ok(selection.includes("choice === 'RESTRITA_18' ? previous?.observacao ?? '' : ''"))
assert.ok(detail.includes("selected === 'RESTRITA_18' ? ("))
assert.ok(!selection.includes("'EXCLUIR'"))
assert.ok(detail.includes('PhotoDeleteDialog'))
assert.ok(detail.includes('deletablePhoto'))
assert.ok(detail.includes("item.status !== 'REMOVIDA'"))
assert.ok(detail.includes('Excluir foto'))
assert.ok(detail.includes("decisao: 'EXCLUIR'"))
assert.ok(detail.includes('photoDeleteLock.current'))
assert.ok(detail.includes("result.resultado === 'FALHA'"))
assert.ok(detail.includes('new ApiContractError('))
assert.ok(detail.includes("'CONFLICT'"))
assert.ok(!detail.includes("throw new Error(result?.motivo || 'A limpeza da foto não foi concluída.')"))
assert.ok(detail.includes('também estiver vinculado a um documento KYC ou a outro registro'))
assert.ok(detail.includes('Somente arquivos exclusivos e sem outras referências'))
assert.ok(!photoDelete.includes('setMedia('))
assert.ok(photoDelete.includes('await load(undefined, false)'))
assert.ok(photoDelete.indexOf('await load(undefined, false)') < photoDelete.indexOf('setPhotoDeleteTarget(null)'))
assert.ok(photoDelete.includes('revalidarCacheCatalogoPublico()'))
assert.ok(detail.includes("media.tipo === 'FOTO' ? 'aspect-video' : 'aspect-[16/7]'"))
assert.ok(detail.includes('object-contain'))
assert.ok(mediaTab.includes('data-admin-media-card') && detail.includes('data-admin-media-preview'))
assert.ok(mediaTab.includes('data-admin-media-metadata') && detail.includes('data-admin-photo-decision'))
assert.ok(detail.includes('<legend className="sr-only">Decisão individual da foto</legend>'))
assert.ok(detail.includes('>Decisão:</span>') && detail.includes('grid min-w-[13rem] flex-1 grid-cols-2'))
assert.ok(detail.includes('min-h-9') && detail.includes('focus-visible:ring-2'))
assert.ok(detail.includes('rows={2}') && detail.includes('placeholder="Observação individual desta foto"'))
assert.ok(mediaTab.includes('Rejeitar foto'))
assert.ok(mediaTab.includes("action: 'REPROVAR'"))
assert.ok(mediaTab.includes('Confirmar decisões das fotos ({selectedPhotoCount})'))
assert.equal((detail.match(/<PhotoBatchDialog/g) ?? []).length, 1)
assert.ok(detail.includes('selectedPhotoCount === pendingPhotos.length'))
assert.ok(batch.includes('decideAdminPhotosBatch'))
assert.ok(batch.includes('pendingPhotos.map'))
assert.ok(batch.includes("decision.classificacao === 'RESTRITA_18'"))
assert.ok(batch.includes("filter((item) => item.resultado === 'FALHA')"))
assert.ok(batch.includes('failedIds.has(mediaId)'))
assert.ok(batch.includes('await load(undefined, false)'))
assert.ok(batch.indexOf('await load(undefined, false)') < batch.indexOf('setPhotoBatchResult(response)'))
assert.ok(!batch.includes('setMedia('))
assert.ok(detail.includes('item.codigo ? ` (code: ${item.codigo})` : null'))
assert.ok(detail.includes('requestId: {photoBatchResult.requestId}'))
assert.ok(detail.includes('photoBatchLock.current'))
assert.ok(mediaTab.includes("item.tipo === 'VIDEO'"))
assert.ok(mediaTab.includes('Sempre RESTRITA_18'))
assert.ok(mediaTab.includes("title: 'Rejeitar vídeo'"))

assert.ok(api.includes('export async function decideAdminPhotosBatch'))
assert.ok(api.includes('body: JSON.stringify({ fotos })'))
assert.ok(api.includes('if (!response.ok && respostaValida)'))
assert.ok(api.includes("falha?.motivo || 'A operação não pôde ser concluída no estado atual.'"))
assert.ok(types.includes("decisao: 'APROVAR' | 'EXCLUIR'"))
assert.ok(types.includes("resultado: 'APROVADA' | 'EXCLUIDA' | 'JA_PROCESSADA' | 'FALHA'"))
assert.ok(types.includes('codigo: string | null'))
assert.ok(types.includes('fotosAprovadasTotal: number'))
assert.ok(types.includes('fotosAguardandoDecisaoTotal: number'))
assert.ok(types.includes('export type AdminAdMediaUploadResponse'))
assert.ok(types.includes('status: string') && types.includes('statusArquivo: string'))
assert.ok(detail.includes('disabled={headerBusy || photoApprovalBlocked}'))
assert.ok(detail.includes('Aprove ao menos uma foto antes de aprovar o anúncio.'))
assert.ok(detail.includes('Conclua a análise de todas as fotos antes de aprovar o anúncio.'))
assert.ok(detail.includes('const canUploadAdminMedia = isAdmin && canModerateAd && canModerateMedia && !removed'))
assert.ok(mediaTab.includes('<AdminAnuncioMidiaUploader'))
assert.ok(uploadAdapter.includes("form.append('arquivo', arquivo)"))
assert.ok(uploadAdapter.includes("headers: { 'Idempotency-Key': idempotencyKey }"))
assert.ok(!uploadAdapter.includes("'Content-Type'"))
assert.ok(uploadSubmit.indexOf('await uploadAdminAdMedia') < uploadSubmit.indexOf('await onReload()'))
assert.ok(uploadSubmit.indexOf('await onReload()') < uploadSubmit.indexOf('setArquivo(null)'))
assert.ok(uploader.includes('error.code') && uploader.includes('error.requestId'))
assert.match(contractState, /normalized\.kind === 'NETWORK_FAILURE'/)
assert.match(contractState, /normalized\.kind === 'CONFLICT'/)
assert.match(contractState, /Operacao nao concluida/)

console.log('Moderacao em lote de fotos: contrato frontend aprovado.')
