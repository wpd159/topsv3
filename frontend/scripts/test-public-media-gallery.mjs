import assert from "node:assert/strict"
import { readFile } from "node:fs/promises"
import { resolve } from "node:path"
import { fileURLToPath } from "node:url"
import ts from "typescript"

const frontendRoot = resolve(fileURLToPath(new URL("..", import.meta.url)))
const helperPath = resolve(frontendRoot, "src/lib/media/public-media.ts")
const galleryPath = resolve(frontendRoot, "src/app/(public-routes)/anuncios/[slug]/componentes/header-tabs.tsx")
const [helperSource, gallerySource] = await Promise.all([
  readFile(helperPath, "utf8"),
  readFile(galleryPath, "utf8"),
])

const transpiled = ts.transpileModule(helperSource, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText
const helper = await import(`data:text/javascript;base64,${Buffer.from(transpiled).toString("base64")}`)

const media = (id, tipo, ordem = 0) => ({
  id,
  tipo,
  ordem,
  visibilidadeMidia: "LIVRE",
  autorizada: true,
  urlPublica: `/media/${id}`,
})

const bugReal = [
  media("foto-0", "FOTO", 0),
  media("foto-1", "FOTO", 1),
  media("video-10", "VIDEO", 10),
]
assert.deepEqual(
  helper.ordenarGaleriaPublica(bugReal).map((item) => item.id),
  ["video-10", "foto-0", "foto-1"],
)

const grupos = [
  media("foto-nula", "FOTO", null),
  media("video-nulo", "VIDEO", null),
  media("foto-1", "FOTO", 1),
  media("video-2", "VIDEO", 2),
  media("story-0", "STORY", 0),
]
assert.deepEqual(
  helper.ordenarGaleriaPublica(grupos).map((item) => item.id),
  ["video-2", "video-nulo", "foto-1", "foto-nula"],
)

assert.deepEqual(
  helper.ordenarGaleriaPublica([
    media("video-b", "VIDEO", 3),
    media("video-a", "VIDEO", 3),
  ]).map((item) => item.id),
  ["video-a", "video-b"],
)

const apenasFotos = [media("foto-2", "FOTO", 2), media("foto-0", "FOTO", 0)]
assert.deepEqual(
  helper.ordenarGaleriaPublica(apenasFotos).map((item) => item.id),
  ["foto-0", "foto-2"],
)
assert.deepEqual(apenasFotos.map((item) => item.id), ["foto-2", "foto-0"])

assert.match(gallerySource, /ordenarGaleriaPublica\(anuncio\.midias\)/)
assert.match(gallerySource, /const mediaHero = midias\[mediaAtiva\]/)
assert.match(gallerySource, /midias\.map\(\(item, index\)/)
assert.match(gallerySource, /selecionarMedia\(mediaAtiva - 1\)/)
assert.match(gallerySource, /selecionarMedia\(mediaAtiva \+ 1\)/)
assert.match(gallerySource, /handleTouchEnd\(event, midias\.length, mediaAtiva, selecionarMedia\)/)
assert.doesNotMatch(gallerySource, /\.sort\(\(a, b\) => \(a\.ordem/)

assert.match(helperSource, /tipo === "FOTO" && Boolean\(fontePublicaSegura\(midia\)\)/)
assert.match(helperSource, /\.filter\(\(midia\) => midia\.tipo === "FOTO"/)

console.log("public media gallery ordering: OK")
