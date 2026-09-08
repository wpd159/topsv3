import assert from 'node:assert/strict'
import { File } from 'node:buffer'
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import ts from 'typescript'
import sharp from 'sharp'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sourceRoot = path.join(frontendRoot, 'src')

function source(relativePath) {
  return fs.readFileSync(path.join(sourceRoot, relativePath), 'utf8')
}

function normalized(value) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
}

const api = source('lib/meus-anuncios-api.ts')
const apiContract = source('lib/api-contract.ts')
const adminDocumentsApi = source('features/admin-documentos/api.ts')
const wizardApi = source('features/anuncio-wizard/api.ts')
const listPage = source('app/(private-routes)/meus-anuncios/page.tsx')
const card = source('components/anuncios/meu-anuncio-card.tsx')
const detail = source('components/anuncios/meu-anuncio-detalhe-view.tsx')
const lifecycle = source('components/anuncios/meu-anuncio-acoes-ciclo-vida.tsx')
const wizard = source('features/anuncio-wizard/anuncio-wizard.tsx')
const wizardPhotos = source('features/anuncio-wizard/components/wizard-step-fotos.tsx')
const combined = `${listPage}\n${card}\n${detail}\n${lifecycle}`
const normalizedCombined = normalized(combined)
const singleUpload = api.slice(
  api.indexOf('export async function enviarMinhaMidia'),
  api.indexOf('const mediaBatchIdempotencyKeys'),
)
const batchUpload = api.slice(
  api.indexOf('export async function enviarMinhasMidiasEmLote'),
  api.indexOf('export function reordenarMinhasMidias'),
)
const createUpload = wizard.slice(
  wizard.indexOf('const arquivos = [...state.fotos, ...state.videos]'),
  wizard.indexOf("await syncProgress('concluido'", wizard.indexOf('const arquivos = [...state.fotos, ...state.videos]')),
)
const persistedUpload = wizardPhotos.slice(
  wizardPhotos.indexOf('const uploadPersisted = async'),
  wizardPhotos.indexOf('const move = async'),
)

for (const field of [
  'titulo',
  'descricao',
  'categoria',
  'preco',
  'whatsapp',
  'locaisAtendimento',
  'servicos',
  'status',
  'statusModeracao',
  'localizacao',
  'capa',
  'midias',
  'visualizacoes',
  'reprovacao',
  'acoesPermitidas',
]) {
  assert.match(api, new RegExp(`\\b${field}:`), `Campo ausente do MeuAnuncio: ${field}`)
}

assert.match(listPage, /href="\/anunciar\/wizard"/)
assert.match(normalized(listPage), /Publicar novo anuncio/)
assert.match(card, /anuncioPreco\(anuncio\.preco\)/)
assert.match(card, /\/meus-anuncios\/\$\{encodeURIComponent\(anuncio\.slug\)\}/)
assert.match(card, /\/meus-anuncios\/\$\{encodeURIComponent\(anuncio\.slug\)\}\/editar/)
assert.match(card, /\/meus-anuncios\/\$\{encodeURIComponent\(anuncio\.slug\)\}\/monetizar/)
assert.match(card, /anuncio\.status === 'PUBLICADO'/)
assert.match(card, /anuncio\.reprovacao\.motivo/)
assert.match(card, /anuncio\.reprovacao\.decididoEm/)
assert.match(card, /anuncio\.acoesPermitidas\.corrigirEReenviar/)
assert.match(normalized(card), /Corrigir e reenviar/)
assert.match(normalized(card), /Detalhes/)
assert.match(normalized(card), /Editar/)
assert.match(normalized(card), /Monetizar/)
assert.match(lifecycle, /acoesPermitidas\.pausar/)
assert.match(lifecycle, /acoesPermitidas\.reativar/)
assert.match(lifecycle, /acoesPermitidas\.remover/)
assert.match(normalized(lifecycle), /Pausar/)
assert.match(normalized(lifecycle), /Reativar/)
assert.match(normalized(lifecycle), /Remover anuncio/)
assert.match(lifecycle, /setConfirmacao\('PAUSAR'\)/)
assert.match(lifecycle, /setConfirmacao\('REMOVER'\)/)
assert.match(lifecycle, /emExecucao\.current/)
assert.match(lifecycle, /disabled=\{processando !== null\}/)
assert.match(lifecycle, /onSuccess\(resultado, acao\)/)

for (const field of [
  'anuncio.titulo',
  'anuncio.preco',
  'anuncio.categoria',
  'anuncio.status',
  'anuncio.statusModeracao',
  'anuncio.descricao',
  'anuncio.localizacao?.bairro',
  'anuncio.localizacao?.cidade',
  'anuncio.localizacao?.uf',
  'anuncio.servicos',
  'anuncio.locaisAtendimento',
  'anuncio.whatsapp',
  'anuncio.capa',
  'anuncio.midias',
  'anuncio.visualizacoes',
  'anuncio.reprovacao',
]) {
  assert.ok(detail.includes(field), `Campo do detalhe nao renderizado: ${field}`)
}

assert.match(detail, /midia\.tipo !== 'FOTO'/)
assert.match(detail, /midia\.restrita/)
assert.match(detail, /midia\.visibilidadeMidia === 'RESTRITA_18'/)
assert.match(detail, /meuAnuncioUrlPublicaSegura\(midia\.urlPublica\)/)
assert.match(card, /PARAMETROS_DE_URL_ASSINADA/)
assert.match(card, /resolvida\.protocol !== 'https:'/)
assert.match(detail, /priority/)
assert.match(detail, /loading="lazy"/)
assert.match(normalized(detail), /Midia protegida/)
assert.match(normalized(detail), /aguardando moderacao/)
assert.doesNotMatch(combined, /objectKey|object_key|chaveObjeto|bucket|documentoKyc|dataNascimento/)
assert.match(card, /href=\{`\/anuncios\/\$\{encodeURIComponent\(anuncio\.slug\)\}`\}[\s\S]*Ver anúncio/)

assert.match(listPage, /error\.status === 401/)
assert.match(listPage, /error\.status === 403/)
assert.match(listPage, /error\.status === 404/)
assert.match(normalized(listPage), /Integracao indisponivel/)
assert.match(detail, /error\.status === 403/)
assert.match(detail, /error\.status === 404/)
assert.match(detail, /error\.status === 401/)
assert.match(normalized(detail), /Acesso negado/)
assert.match(normalized(detail), /Anuncio nao encontrado/)
assert.doesNotMatch(listPage, /mensagem:\s*error instanceof Error/)
assert.doesNotMatch(detail, /mensagem:\s*error instanceof Error/)
assert.match(normalized(detail), /Nenhum servico informado/)
assert.match(normalized(detail), /Nenhum local de atendimento informado/)
assert.match(detail, /anuncio\.reprovacao\.motivo/)
assert.match(detail, /anuncio\.reprovacao\.decididoEm/)
assert.match(detail, /acoesPermitidas\.corrigirEReenviar/)
assert.match(normalized(detail), /Corrigir e reenviar/)

for (const forbidden of [
  'Cliques',
  'CTR',
  'Excluir',
  'Performance',
  'Comprar creditos',
]) {
  assert.ok(!normalizedCombined.includes(normalized(forbidden)), `Acao fora do escopo adicionada: ${forbidden}`)
}

assert.doesNotMatch(combined, /document\.body\.style\.overflow|w-\[100vw\]|width\s*:\s*100vw/)
assert.match(card, /break-words/)
assert.match(detail, /min-w-0/)
assert.match(detail, /break-words/)
assert.match(listPage, /md:grid-cols-2/)
assert.doesNotMatch(combined, /fetch\s*\(/)
assert.doesNotMatch(combined, /\/api\/public/)

assert.match(api, /readonly code: string \| null = null/)
assert.match(api, /readonly requestId: string \| null = null/)
assert.match(api, /const usefulMessage = \[envelope\?\.message, envelope\?\.detail, envelope\?\.mensagem\]/)
assert.match(api, /nonBlankString\(envelope\?\.requestId\) \|\| xhr\.getResponseHeader\('X-Request-Id'\)/)
assert.match(api, /xhr\.status === 415 && unsupportedPhotoUpload/)
assert.match(api, /resolveUnsupportedPhotoUploadMessage\(candidateMessage\)/)
assert.match(api, /const unsupportedPhotoUpload = containsOnlyPhotoUploads\(\[arquivo\]\)/)
assert.match(api, /const unsupportedPhotoUpload = containsOnlyPhotoUploads\(arquivos\)/)
assert.match(api, /if \(isVideoUploadFile\(file\)\) return false/)
assert.match(api, /new Set\(\['jpg', 'jpeg', 'png', 'webp'\]\)/)
assert.match(api, /error\.code \? `Código: \$\{error\.code\}`/)
assert.match(api, /error\.requestId \? `Request ID: \$\{error\.requestId\}`/)
const safePhotoMessage = 'Não conseguimos enviar esta foto. Abra a imagem em um editor e salve uma nova cópia em JPG ou PNG. Depois, selecione essa cópia.'
assert.ok(apiContract.includes(safePhotoMessage))
assert.match(apiContract, /unsupportedPhotoUpload\?: boolean/)
assert.match(apiContract, /options\.unsupportedPhotoUpload/)
assert.match(apiContract, /Não foi possível processar o arquivo enviado\./)
assert.doesNotMatch(adminDocumentsApi, /unsupportedPhotoUpload/)
assert.doesNotMatch(wizardApi, /unsupportedPhotoUpload/)

const apiContractUrl = pathToFileURL(path.join(sourceRoot, 'lib/api-contract.ts')).href
const resolverRuntime = spawnSync(process.execPath, [
  '--no-warnings',
  '--experimental-strip-types',
  '--input-type=module',
  '--eval',
  `
    import assert from 'node:assert/strict'
    import {
      UNSUPPORTED_PHOTO_UPLOAD_MESSAGE,
      apiErrorFromResponse,
      resolveUnsupportedPhotoUploadMessage,
    } from ${JSON.stringify(apiContractUrl)}

    for (const genericMessage of [
      undefined,
      '',
      'Formato de arquivo não permitido.',
      'formato de arquivo nao permitido',
      'Unsupported Media Type',
      '  UNSUPPORTED MEDIA TYPE.  ',
    ]) {
      assert.equal(
        resolveUnsupportedPhotoUploadMessage(genericMessage),
        UNSUPPORTED_PHOTO_UPLOAD_MESSAGE,
      )
    }
    const specificMessage = 'O conteúdo enviado não corresponde ao formato declarado.'
    assert.equal(resolveUnsupportedPhotoUploadMessage(specificMessage), specificMessage)

    const genericResponse = new Response(JSON.stringify({
      message: 'Formato de arquivo não permitido.',
      code: 'MIDIA_FORMATO_INVALIDO',
      requestId: 'request-body-415',
    }), {
      status: 415,
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': 'request-header-ignorado',
      },
    })
    const genericError = await apiErrorFromResponse(genericResponse, {
      preserveServerMessage: true,
      unsupportedPhotoUpload: true,
    })
    assert.equal(genericError.message, UNSUPPORTED_PHOTO_UPLOAD_MESSAGE)
    assert.equal(genericError.status, 415)
    assert.equal(genericError.code, 'MIDIA_FORMATO_INVALIDO')
    assert.equal(genericError.requestId, 'request-body-415')

    const genericWithoutOptIn = await apiErrorFromResponse(new Response(JSON.stringify({
      message: 'Formato de arquivo não permitido.',
      code: 'DOCUMENTO_FORMATO_INVALIDO',
      requestId: 'request-documento-body',
    }), {
      status: 415,
      headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'request-documento-header' },
    }), { preserveServerMessage: true })
    assert.equal(genericWithoutOptIn.message, 'Formato de arquivo não permitido.')
    assert.equal(genericWithoutOptIn.status, 415)
    assert.equal(genericWithoutOptIn.code, 'DOCUMENTO_FORMATO_INVALIDO')
    assert.equal(genericWithoutOptIn.requestId, 'request-documento-body')
    assert.notEqual(genericWithoutOptIn.message, UNSUPPORTED_PHOTO_UPLOAD_MESSAGE)

    const neutralWithoutOptIn = await apiErrorFromResponse(new Response(null, {
      status: 415,
      headers: { 'X-Request-Id': 'request-neutral-header' },
    }))
    assert.equal(neutralWithoutOptIn.message, 'Não foi possível processar o arquivo enviado.')
    assert.equal(neutralWithoutOptIn.status, 415)
    assert.equal(neutralWithoutOptIn.code, null)
    assert.equal(neutralWithoutOptIn.requestId, 'request-neutral-header')

    const specificResponse = new Response(JSON.stringify({
      message: specificMessage,
      code: 'MIDIA_ASSINATURA_INVALIDA',
      requestId: 'request-specific-415',
    }), { status: 415, headers: { 'Content-Type': 'application/json' } })
    const specificError = await apiErrorFromResponse(specificResponse, { preserveServerMessage: true })
    assert.equal(specificError.message, specificMessage)
    assert.equal(specificError.status, 415)
    assert.equal(specificError.code, 'MIDIA_ASSINATURA_INVALIDA')
    assert.equal(specificError.requestId, 'request-specific-415')
    console.log('HTTP_415_RESOLVER_RESULT=OK')
  `,
], { encoding: 'utf8' })
assert.equal(resolverRuntime.status, 0, resolverRuntime.stderr || resolverRuntime.stdout)
assert.match(resolverRuntime.stdout, /HTTP_415_RESOLVER_RESULT=OK/)

const transpiledAdapter = ts.transpileModule(api, {
  compilerOptions: {
    target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.ESNext,
  },
}).outputText
const apiContractImport = "import { publicApiUrl, resolveUnsupportedPhotoUploadMessage } from '@/lib/api-contract';"
const visualizacoesImport = "import { parseVisualizacoesCanonicas, } from '@/lib/visualizacoes-canonicas';"
const photoValidationImport = "import { isSupportedUploadVideo, validatePhotoUpload } from '@/lib/photo-upload-validation';"
const transpiledPhotoValidation = ts.transpileModule(source('lib/photo-upload-validation.ts'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText
const photoValidationUrl = `data:text/javascript;base64,${Buffer.from(transpiledPhotoValidation).toString('base64')}`
assert.ok(transpiledAdapter.includes(apiContractImport), 'Import do contrato da API não localizado no adapter transpilado.')
assert.ok(transpiledAdapter.includes(visualizacoesImport), 'Import de visualizações não localizado no adapter transpilado.')
const adapterRuntimeSource = transpiledAdapter
  .replace(apiContractImport, `
    const publicApiUrl = (value) => value
    const resolveUnsupportedPhotoUploadMessage = (message) => {
      const normalizedMessage = typeof message === 'string'
        ? message.normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').toLowerCase().replace(/[.!?:;]+$/g, '').trim()
        : ''
      return !normalizedMessage || normalizedMessage === 'formato de arquivo nao permitido' || normalizedMessage === 'unsupported media type'
        ? ${JSON.stringify(safePhotoMessage)}
        : message
    }
  `)
  .replace(visualizacoesImport, 'const parseVisualizacoesCanonicas = (value) => value;')
  .replace(photoValidationImport, `import { isSupportedUploadVideo, validatePhotoUpload } from ${JSON.stringify(photoValidationUrl)};`)
assert.doesNotMatch(adapterRuntimeSource, /from ['"]@\//, 'O harness deve substituir todos os aliases usados pelo adapter.')

const adapterRuntimeUrl = `data:text/javascript;base64,${Buffer.from(adapterRuntimeSource).toString('base64')}`
const adapterRuntime = await import(adapterRuntimeUrl)
const pendingXhrResponses = []
const xhrRequests = []

class ContractXMLHttpRequest {
  upload = {}
  headers = new Map()
  responseHeaders = new Map()
  responseText = ''
  status = 0

  open(method, url) {
    this.method = method
    this.url = url
  }

  setRequestHeader(name, value) {
    this.headers.set(name.toLowerCase(), value)
  }

  getResponseHeader(name) {
    return this.responseHeaders.get(name.toLowerCase()) ?? null
  }

  send(body) {
    const response = pendingXhrResponses.shift()
    assert.ok(response, 'Cada envio do harness precisa de uma resposta preparada.')
    this.status = response.status
    this.responseText = response.body === undefined ? '' : JSON.stringify(response.body)
    this.responseHeaders = new Map(
      Object.entries(response.headers ?? {}).map(([name, value]) => [name.toLowerCase(), value]),
    )
    this.body = body
    xhrRequests.push(this)
    queueMicrotask(() => this.onload())
  }
}

globalThis.document = { cookie: 'XSRF-TOKEN=CHANGE_ME; ' }
globalThis.XMLHttpRequest = ContractXMLHttpRequest
// Browser decoding is mocked here; these tests exercise the actual request/error
// adapters with synthetic encoded fixtures, not browser codec compatibility.
globalThis.createImageBitmap = async () => ({ width: 2, height: 2, close() {} })

async function expectRejectedUpload(action, response) {
  pendingXhrResponses.push(response)
  let failure = null
  try {
    await action()
  } catch (error) {
    failure = error
  }
  assert.ok(failure instanceof adapterRuntime.MeusAnunciosApiError, 'HTTP não 2xx não pode virar sucesso.')
  return { error: failure, request: xhrRequests.at(-1) }
}

const generic415 = (bodyRequestId = undefined) => ({
  status: 415,
  body: {
    message: 'Formato de arquivo não permitido.',
    code: 'MIDIA_FORMATO_INVALIDO',
    ...(bodyRequestId ? { requestId: bodyRequestId } : {}),
  },
  headers: { 'X-Request-Id': 'request-header-415' },
})
const syntheticPixels = { create: { width: 2, height: 2, channels: 3, background: '#456789' } }
const photoByMime = new File([await sharp(syntheticPixels).jpeg().toBuffer()], 'arquivo.jpg', { type: 'image/jpeg', lastModified: 1 })
const photoByExtension = new File([await sharp(syntheticPixels).webp().toBuffer()], 'arquivo.WEBP', { type: '', lastModified: 2 })
const videoWithPhotoExtension = new File(['video'], 'video.mp4', { type: 'video/mp4', lastModified: 3 })
const videoMovWithoutMime = new File(['video-mov'], 'video.mov', { type: '', lastModified: 4 })
const unknownFile = new File(['video-nao-suportado'], 'arquivo.avi', { type: '', lastModified: 5 })

const firstPhotoFailure = await expectRejectedUpload(
  () => adapterRuntime.enviarMinhaMidia('anuncio', photoByMime),
  generic415('request-body-prioritario'),
)
assert.equal(firstPhotoFailure.error.status, 415)
assert.equal(firstPhotoFailure.error.message, safePhotoMessage)
assert.equal(firstPhotoFailure.error.code, 'MIDIA_FORMATO_INVALIDO')
assert.equal(firstPhotoFailure.error.requestId, 'request-body-prioritario')
assert.equal(adapterRuntime.meusAnunciosErrorMessage(firstPhotoFailure.error, 'Falha'), safePhotoMessage, 'Mensagem principal da foto não deve anexar metadados técnicos.')
assert.doesNotMatch(adapterRuntime.meusAnunciosErrorMessage(firstPhotoFailure.error, 'Falha'), /Código:|Request ID:/)
assert.equal(firstPhotoFailure.request.body.get('arquivo').name, photoByMime.name)
assert.equal(firstPhotoFailure.request.headers.get('x-xsrf-token'), 'CHANGE_ME')

const retriedPhotoFailure = await expectRejectedUpload(
  () => adapterRuntime.enviarMinhaMidia('anuncio', photoByMime),
  generic415('request-body-retry'),
)
assert.equal(
  retriedPhotoFailure.request.headers.get('idempotency-key'),
  firstPhotoFailure.request.headers.get('idempotency-key'),
  'A chave unitária deve permanecer após falha.',
)

const photoBatch = [photoByMime, photoByExtension]
const firstPhotoBatchFailure = await expectRejectedUpload(
  () => adapterRuntime.enviarMinhasMidiasEmLote('anuncio', photoBatch),
  generic415(),
)
assert.equal(firstPhotoBatchFailure.error.message, safePhotoMessage)
assert.equal(firstPhotoBatchFailure.request.body.getAll('arquivos').length, 2)
assert.equal(firstPhotoBatchFailure.error.requestId, 'request-header-415')

const retriedPhotoBatchFailure = await expectRejectedUpload(
  () => adapterRuntime.enviarMinhasMidiasEmLote('anuncio', photoBatch),
  generic415(),
)
assert.equal(
  retriedPhotoBatchFailure.request.headers.get('idempotency-key'),
  firstPhotoBatchFailure.request.headers.get('idempotency-key'),
  'A chave do lote deve permanecer após falha.',
)

const videoFailure = await expectRejectedUpload(
  () => adapterRuntime.enviarMinhaMidia('anuncio', videoWithPhotoExtension),
  generic415(),
)
assert.equal(videoFailure.error.message, 'Formato de arquivo não permitido.')
assert.doesNotMatch(videoFailure.error.message, /JPG|PNG|WebP/)
assert.equal(videoFailure.error.code, 'MIDIA_FORMATO_INVALIDO')
assert.equal(videoFailure.error.requestId, 'request-header-415')
assert.match(adapterRuntime.meusAnunciosErrorMessage(videoFailure.error, 'Falha'), /Código: MIDIA_FORMATO_INVALIDO/, 'Tratamento de erro de vídeo permanece separado.')

const movFailure = await expectRejectedUpload(
  () => adapterRuntime.enviarMinhaMidia('anuncio', videoMovWithoutMime),
  { status: 415, body: {}, headers: {} },
)
assert.equal(movFailure.error.message, 'Não foi possível enviar a mídia (HTTP 415).')
assert.doesNotMatch(movFailure.error.message, /JPG|PNG|WebP/)

const mixedFailure = await expectRejectedUpload(
  () => adapterRuntime.enviarMinhasMidiasEmLote('anuncio', [photoByMime, videoWithPhotoExtension]),
  generic415(),
)
assert.equal(mixedFailure.error.message, 'Formato de arquivo não permitido.')
assert.doesNotMatch(mixedFailure.error.message, /JPG|PNG|WebP/)
assert.match(adapterRuntime.meusAnunciosErrorMessage(mixedFailure.error, 'Falha'), /Request ID:/, 'Lote misto mantém o tratamento anterior.')

const safeSpecificMessage = 'O conteúdo enviado não corresponde ao formato declarado.'
const unknownFailure = await expectRejectedUpload(
  () => adapterRuntime.enviarMinhaMidia('anuncio', unknownFile),
  {
    status: 415,
    body: { message: safeSpecificMessage, code: 'MIDIA_ASSINATURA_INVALIDA' },
    headers: { 'X-Request-Id': 'request-unknown-header' },
  },
)
assert.equal(unknownFailure.error.message, safeSpecificMessage)
assert.equal(unknownFailure.error.code, 'MIDIA_ASSINATURA_INVALIDA')
assert.equal(unknownFailure.error.requestId, 'request-unknown-header')
assert.doesNotMatch(unknownFailure.error.message, /JPG|PNG|WebP/)

for (const upload of [singleUpload, batchUpload]) {
  assert.match(upload, /new XMLHttpRequest\(\)/)
  assert.match(upload, /xhr\.setRequestHeader\('Idempotency-Key'/)
  assert.match(upload, /xhr\.onerror = \(\) => reject\(uploadErrorFromXhr\(/)
  assert.match(upload, /reject\(uploadErrorFromXhr\(/)
  assert.doesNotMatch(upload, /setRequestHeader\('Content-Type'/)
  assert.ok(upload.indexOf('if (xhr.status < 200 || xhr.status >= 300)') < upload.indexOf('IdempotencyKeys.delete'), 'Chave so pode ser limpa apos a validacao de 2xx.')
  assert.equal((upload.match(/IdempotencyKeys\.delete/g) ?? []).length, 1, 'Cada XHR deve limpar sua chave apenas no caminho 2xx.')
}
assert.match(singleUpload, /form\.append\('arquivo', arquivo\)/)
assert.match(batchUpload, /arquivos\.forEach\(\(arquivo\) => form\.append\('arquivos', arquivo\)\)/)
assert.match(singleUpload, /mediaUploadIdempotencyKey\(arquivo\)/)
assert.match(batchUpload, /mediaBatchIdempotencyKeys\.get\(signature\) \|\| crypto\.randomUUID\(\)/)

assert.match(wizard, /meusAnunciosErrorMessage\(error, 'Falha ao enviar o lote\.'\)/)
assert.ok(createUpload.indexOf('await enviarMinhasMidiasEmLote') < createUpload.indexOf('setFotos([])'), 'Criacao deve limpar arquivos apenas depois do 2xx.')
const createFailure = createUpload.slice(createUpload.indexOf('} catch (error)'), createUpload.indexOf('\n      }', createUpload.indexOf('} catch (error)')))
assert.doesNotMatch(createFailure, /setFotos\(\[\]\)|setVideos\(\[\]\)/)
assert.match(wizardPhotos, /meusAnunciosErrorMessage\(error, 'Falha ao enviar os arquivos\.'\)/)
assert.match(wizardPhotos, /const \[pendingPersistedFiles, setPendingPersistedFiles\] = useState<File\[]>\(\[\]\)/)
assert.ok(persistedUpload.indexOf('await enviarMinhasMidiasEmLote') < persistedUpload.indexOf('updatePendingFiles([])'), 'Edicao deve limpar a selecao apenas depois do 2xx.')
const persistedFailure = persistedUpload.slice(persistedUpload.indexOf('} catch (error)'), persistedUpload.indexOf('} finally'))
assert.doesNotMatch(persistedFailure, /setPendingPersistedFiles\(\[\]\)|updatePendingFiles\(\[\]\)/)
assert.match(wizardPhotos, /onClick=\{\(\) => void uploadPersisted\(pendingPersistedFiles\)\}/)
assert.match(normalized(wizardPhotos), /Tentar enviar novamente/)

console.log('PAINEL_ANUNCIANTE_FASE_1B_RESULT=OK')
