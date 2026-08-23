import assert from "node:assert/strict"
import { readFile } from "node:fs/promises"

const [cardSource, cardVideoSource, sensitiveVideoSource, policySource, mapperSource] = await Promise.all([
  readFile(new URL("../src/components/anuncios/anuncio-card.tsx", import.meta.url), "utf8"),
  readFile(new URL("../src/components/anuncios/anuncio-card-video.tsx", import.meta.url), "utf8"),
  readFile(new URL("../src/components/compliance/sensitive-video.tsx", import.meta.url), "utf8"),
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
assert.match(cardSource, /if \(media\?\.tipo !== "FOTO"\) return/)

assert.match(cardVideoSource, /let activeCardVideo: HTMLVideoElement \| null = null/)
assert.match(cardVideoSource, /activeCardVideo\.pause\(\)/)
assert.match(cardVideoSource, /preload="none"/)
assert.match(cardVideoSource, /onClick=\{\(event\) => event\.stopPropagation\(\)\}/)
assert.match(cardVideoSource, /Reproduzir vídeo/)
assert.match(cardVideoSource, /onAuthorizationChange=\{setSessionAuthorized\}/)
assert.match(cardVideoSource, /pointer-events-auto/)
assert.match(cardVideoSource, /aria-label="Reproduzir vídeo"/)
assert.match(cardVideoSource, /void video\.play\(\)\.catch/)
assert.match(cardVideoSource, /midiaExigeConfirmacaoIdade\(midia\)/)
assert.doesNotMatch(cardVideoSource, /autoPlay/)

assert.match(sensitiveVideoSource, /VisitorVerificationModal/)
assert.match(sensitiveVideoSource, /scope="MIDIA_RESTRITA"/)
assert.match(sensitiveVideoSource, /preload=\{preload\}/)
assert.match(sensitiveVideoSource, /onVideoElementChange/)
assert.match(sensitiveVideoSource, /onAuthorizationChange\?: \(authorized: boolean\) => void/)
assert.match(sensitiveVideoSource, /onAuthorizationChange\?\.\(authorized\)/)
assert.doesNotMatch(sensitiveVideoSource, /autoPlay/)

console.log("PUBLIC_LISTING_VIDEO_CHECKS=28")
console.log("PUBLIC_LISTING_VIDEO_RESULT=OK")
