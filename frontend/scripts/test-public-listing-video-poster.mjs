import assert from "node:assert/strict"
import { readFile } from "node:fs/promises"
import { resolve } from "node:path"
import ts from "typescript"

const helperPath = resolve("src/lib/media/public-media.ts")
const cardPath = resolve("src/components/anuncios/anuncio-card.tsx")
const videoCardPath = resolve("src/components/anuncios/anuncio-card-video.tsx")
const sensitiveVideoPath = resolve("src/components/compliance/sensitive-video.tsx")

const helperSource = await readFile(helperPath, "utf8")
const transpiled = ts.transpileModule(helperSource, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText
const helper = await import(`data:text/javascript;base64,${Buffer.from(transpiled).toString("base64")}`)

const cardSource = await readFile(cardPath, "utf8")
const videoCardSource = await readFile(videoCardPath, "utf8")
const sensitiveVideoSource = await readFile(sensitiveVideoPath, "utf8")

const SAFE_R2 = "https://pub-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.r2.dev"
let checks = 0

function equal(actual, expected, label) {
  assert.equal(actual, expected, label)
  checks += 1
}

function ok(value, label) {
  assert.ok(value, label)
  checks += 1
}

function midia(overrides = {}) {
  return {
    id: "media-default",
    tipo: "FOTO",
    finalidade: "GALERIA",
    ordem: 1,
    visibilidadeMidia: "LIVRE",
    autorizada: true,
    urlPublica: `${SAFE_R2}/foto.webp`,
    previewUrl: null,
    mimeType: "image/webp",
    ...overrides,
  }
}

function video(overrides = {}) {
  return midia({
    id: "video",
    tipo: "VIDEO",
    ordem: 0,
    urlPublica: `${SAFE_R2}/video.mp4`,
    mimeType: "video/mp4",
    ...overrides,
  })
}

function selecionar(videoAtual, midiasDoAnuncio, autorizacaoValida = videoAtual.autorizada) {
  return helper.selecionarCapaVideoCard({
    video: videoAtual,
    midiasDoAnuncio,
    autorizacaoValida,
    altText: "Capa do vídeo do anúncio",
  })
}

const fotoLivre = midia({ id: "foto-livre", urlPublica: `${SAFE_R2}/livre.webp` })
const fotoLivrePosterior = midia({ id: "foto-posterior", ordem: 9, urlPublica: `${SAFE_R2}/posterior.webp` })
const fotoRestrita = midia({
  id: "foto-restrita",
  ordem: 2,
  visibilidadeMidia: "RESTRITA_18",
  autorizada: false,
  urlPublica: null,
  previewUrl: `${SAFE_R2}/preview-seguro.webp`,
})

const propria = selecionar(
  video({ previewUrl: `${SAFE_R2}/poster-video.webp` }),
  [fotoLivre],
)
equal(propria.origem, "VIDEO_POSTER_EXISTENTE", "poster próprio deve ter prioridade")
equal(propria.url, `${SAFE_R2}/poster-video.webp`, "poster próprio deve ser preservado")
equal(propria.classificacao, "PUBLICA", "poster de vídeo LIVRE deve ser público")

const fallbackLivre = selecionar(video({ previewUrl: null }), [fotoLivre])
equal(fallbackLivre.origem, "FOTO_PUBLICA_ELEGIVEL", "foto LIVRE deve ser fallback")
equal(fallbackLivre.url, `${SAFE_R2}/livre.webp`, "fallback deve vir do mesmo conjunto do anúncio")

const previewRestrito = selecionar(
  video({ visibilidadeMidia: "RESTRITA_18", autorizada: false, previewUrl: null }),
  [fotoRestrita],
  false,
)
equal(previewRestrito.origem, "PREVIEW_PUBLICO_RESTRITO", "preview restrito seguro deve ser aceito")
equal(previewRestrito.classificacao, "PREVIEW_RESTRITO", "preview deve continuar classificado como restrito")

const previewAutorizado = selecionar(
  video({ visibilidadeMidia: "RESTRITA_18", autorizada: true, previewUrl: null }),
  [fotoRestrita],
  true,
)
equal(previewAutorizado.url, previewRestrito.url, "autorização não deve trocar preview por original privado")
equal(previewAutorizado.classificacao, "RESTRITA_AUTORIZADA", "estado autorizado deve ser identificado")

const placeholder = selecionar(video({ previewUrl: null }), [])
equal(placeholder.origem, "PLACEHOLDER_NEUTRO", "ausência de imagem deve usar placeholder")
equal(placeholder.podeExibir, false, "placeholder não deve fingir que possui URL")
equal(placeholder.altText, "Vídeo", "placeholder deve ter texto acessível")

const posterAssinado = selecionar(
  video({ previewUrl: `${SAFE_R2}/poster.webp?assinatura=EXEMPLO_NAO_REAL` }),
  [fotoLivre],
)
equal(posterAssinado.origem, "FOTO_PUBLICA_ELEGIVEL", "URL assinada não pode ser poster")

const hostPrivado = selecionar(
  video({ previewUrl: "https://conta.r2.cloudflarestorage.com/bucket/video.webp" }),
  [],
)
equal(hostPrivado.origem, "PLACEHOLDER_NEUTRO", "host privado não pode ser poster")

const endpointProtegido = selecionar(
  video({ previewUrl: "/api/public/compliance/visitor/media/video" }),
  [],
)
equal(endpointProtegido.origem, "PLACEHOLDER_NEUTRO", "endpoint protegido não pode ser pré-entregue como poster")

const fotoPrivada = selecionar(video({ previewUrl: null }), [
  midia({ visibilidadeMidia: "RESTRITA_18", autorizada: true, urlPublica: "https://privado.example/foto.webp", previewUrl: null }),
])
equal(fotoPrivada.origem, "PLACEHOLDER_NEUTRO", "foto privada nunca deve ser selecionada")

const tiposForaDoEscopo = selecionar(video({ previewUrl: null }), [
  midia({ id: "story", tipo: "STORY", urlPublica: `${SAFE_R2}/story.webp` }),
  midia({ id: "kyc", tipo: "KYC", urlPublica: `${SAFE_R2}/kyc.webp` }),
])
equal(tiposForaDoEscopo.origem, "PLACEHOLDER_NEUTRO", "Story e KYC devem ser ignorados")

const fallbackOrdenado = selecionar(video({ previewUrl: null }), [
  fotoLivrePosterior,
  midia({ id: "foto-primeira", ordem: 2, urlPublica: `${SAFE_R2}/primeira.webp` }),
])
equal(fallbackOrdenado.url, `${SAFE_R2}/primeira.webp`, "menor ordem deve vencer")

const fallbackDesempatado = selecionar(video({ previewUrl: null }), [
  midia({ id: "b", ordem: 3, urlPublica: `${SAFE_R2}/b.webp` }),
  midia({ id: "a", ordem: 3, urlPublica: `${SAFE_R2}/a.webp` }),
])
equal(fallbackDesempatado.url, `${SAFE_R2}/a.webp`, "ID deve desempatar deterministicamente")
assert.deepEqual(
  selecionar(video({ previewUrl: null }), [fotoLivrePosterior, fotoLivre]),
  selecionar(video({ previewUrl: null }), [fotoLivrePosterior, fotoLivre]),
  "mesma entrada deve produzir a mesma capa",
)
checks += 1

const fotoExternaNaoFornecida = midia({ id: "outro-anuncio", urlPublica: `${SAFE_R2}/externa.webp` })
const somenteMidiasDoCard = selecionar(video({ previewUrl: null }), [fotoLivre])
equal(somenteMidiasDoCard.url === fotoExternaNaoFornecida.url, false, "mídia de outro anúncio fora do DTO do card não deve ser selecionada")

ok(!helperSource.includes("fetch("), "helper não deve adicionar fetch por card")
ok(cardSource.includes("midiasDoAnuncio: midiasSeguras"), "card deve limitar o helper às mídias do próprio anúncio")
ok(cardSource.includes("capa={capaVideoAtual ?? undefined}"), "card deve entregar a capa selecionada ao vídeo")
ok(videoCardSource.includes("<Image"), "capa deve usar o mecanismo normal de imagem")
ok(videoCardSource.includes('preload="none"'), "vídeo deve manter preload none")
ok(!videoCardSource.includes("autoPlay"), "card não pode habilitar autoplay")
ok(videoCardSource.includes("pointer-events-none"), "capa não pode bloquear o gesto de reprodução")
ok(videoCardSource.includes("PLACEHOLDER_NEUTRO"), "card deve declarar placeholder neutro")
ok(videoCardSource.includes('aria-label="Vídeo"'), "placeholder deve ser acessível")
ok(videoCardSource.includes("setPlaying(false)"), "pause, fim e erro devem restaurar a capa")
ok(sensitiveVideoSource.includes("onRetry?.()"), "retry deve notificar o card")
ok(sensitiveVideoSource.includes("onError?.(event.currentTarget)"), "erro deve notificar o card")
ok(sensitiveVideoSource.includes("publicApiUrl(`/compliance/visitor/media/"), "RESTRITA_18 deve continuar no endpoint protegido")
ok(!videoCardSource.includes("video.urlPublica"), "capa não pode reutilizar o original do vídeo")

console.log(`test-public-listing-video-poster: OK (${checks} verificações)`)
