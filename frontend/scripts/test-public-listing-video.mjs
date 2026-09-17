import assert from "node:assert/strict"
import { readFile } from "node:fs/promises"

const [cardSource, cardVideoSource, sensitiveVideoSource, sensitiveImageSource,
  restrictedOverlaySource, globalStyles, policySource, mapperSource] = await Promise.all([
  readFile(new URL("../src/components/anuncios/anuncio-card.tsx", import.meta.url), "utf8"),
  readFile(new URL("../src/components/anuncios/anuncio-card-video.tsx", import.meta.url), "utf8"),
  readFile(new URL("../src/components/compliance/sensitive-video.tsx", import.meta.url), "utf8"),
  readFile(new URL("../src/components/compliance/sensitive-image.tsx", import.meta.url), "utf8"),
  readFile(new URL("../src/components/compliance/restricted-media-overlay.tsx", import.meta.url), "utf8"),
  readFile(new URL("../src/app/globals.css", import.meta.url), "utf8"),
  readFile(
    new URL(
      "../../backend/src/main/java/br/com/topsdojob/v3/application/publico/mapper/MidiaPublicaSeguraPolicy.java",
      import.meta.url,
    ),
    "utf8",
  ),
  readFile(
    new URL(
      "../../backend/src/main/java/br/com/topsdojob/v3/application/publico/mapper/AnuncioPublicoMapper.java",
      import.meta.url,
    ),
    "utf8",
  ),
])

assert.match(policySource, /videoAtivo && "VIDEO"\.equals\(midia\.tipo\(\)\)/)
assert.match(policySource, /comparingInt\(this::prioridadeTipo\)/)
assert.match(policySource, /return foto == null \? List\.of\(video\) : List\.of\(video, foto\)/)
assert.match(mapperSource, /flags\.carrosselFotosAtivo\(\), flags\.videoAtivo\(\)/)

assert.match(cardSource, /midiaAtual\.tipo === "VIDEO"/)
assert.match(cardSource, /<AnuncioCardVideo/)
assert.match(cardSource, /carrosselDisponivel \|\| videoHabilitado/)
assert.match(cardSource, /aria-label="Mídia anterior"/)
assert.match(cardSource, /aria-label="Próxima mídia"/)
assert.doesNotMatch(cardSource, /new window\.Image\(\)/, "Unselected carousel media must not bypass the rendered image's lazy loading.")

assert.match(cardVideoSource, /let activeCardVideo: HTMLVideoElement \| null = null/)
assert.match(cardVideoSource, /activeCardVideo\.pause\(\)/)
assert.match(cardVideoSource, /preload="none"/)
assert.match(cardVideoSource, /onClick=\{\(event\) => event\.stopPropagation\(\)\}/)
assert.match(cardVideoSource, /Reproduzir vídeo/)
assert.match(cardVideoSource, /onAuthorizationChange=\{setSessionAuthorized\}/)
assert.match(cardVideoSource, /const showPoster = !blocked && !playing && !mediaFailed/)
assert.match(cardVideoSource, /onAbrirPaginaDoAnuncio=\{onAbrirPaginaDoAnuncio\}/)
assert.match(cardVideoSource, /pointer-events-auto/)
assert.match(cardVideoSource, /aria-label="Reproduzir vídeo"/)
assert.match(cardVideoSource, /void video\.play\(\)\.catch/)
assert.match(cardVideoSource, /midiaExigeConfirmacaoIdade\(midia\)/)
assert.doesNotMatch(cardVideoSource, /Confirmar maioridade/)
assert.doesNotMatch(cardVideoSource, /autoPlay/)

assert.match(sensitiveVideoSource, /VisitorVerificationModal/)
assert.match(sensitiveVideoSource, /scope="MIDIA_RESTRITA"/)
assert.match(sensitiveVideoSource, /preload=\{preload\}/)
assert.match(sensitiveVideoSource, /onVideoElementChange/)
assert.match(sensitiveVideoSource, /onAuthorizationChange\?: \(authorized: boolean\) => void/)
assert.match(sensitiveVideoSource, /onAuthorizationChange\?\.\(authorized\)/)
assert.match(sensitiveVideoSource, /<RestrictedMediaOverlay/)
assert.match(sensitiveVideoSource, /onAbrirPaginaDoAnuncio=\{onAbrirPaginaDoAnuncio\}/)
assert.doesNotMatch(sensitiveVideoSource, /autoPlay/)

assert.match(sensitiveImageSource, /<RestrictedMediaOverlay/)
assert.match(restrictedOverlaySource, /data-restricted-media-overlay/)
assert.match(restrictedOverlaySource, /Conteúdo restrito apenas para maiores de 18 anos/)
assert.match(restrictedOverlaySource, /A mídia original só será solicitada após confirmação válida de idade\./)
assert.match(restrictedOverlaySource, /Confirmar maioridade/)
assert.match(restrictedOverlaySource, /Abrir página do anúncio/)
assert.equal((sensitiveImageSource.match(/Conteúdo restrito apenas/g) ?? []).length, 0)
assert.equal((sensitiveVideoSource.match(/Conteúdo restrito apenas/g) ?? []).length, 0)

const restrictedOverlayRule = globalStyles.match(
  /\.compliance-restricted-overlay\s*\{([\s\S]*?)\}/,
)?.[1]
assert.ok(restrictedOverlayRule)
assert.match(restrictedOverlayRule, /inset:\s*0;/)
assert.match(restrictedOverlayRule, /background:/)
assert.doesNotMatch(restrictedOverlayRule, /backdrop-filter/i)

console.log("PUBLIC_LISTING_VIDEO_CHECKS=43")
console.log("PUBLIC_LISTING_VIDEO_RESULT=OK")
