import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [feedSource, publicDetailSource, mapperSource, limitSource, viewerSource] = await Promise.all([
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/service/StoryFeedPublicoService.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/service/AnuncioPublicoConsultaService.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/mapper/MidiaPublicaMapper.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/midia/LimiteMidiasAnuncioService.java', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/stories/story-viewer-dialog.tsx', import.meta.url), 'utf8'),
])

assert.match(feedSource, /anuncioConsultaService\.midiasParaStory\(anuncio, true\)/)
assert.doesNotMatch(feedSource, /midiaMapper\.publicas\([\s\S]*Integer\.MAX_VALUE/)
assert.match(publicDetailSource, /midiasParaStory\([\s\S]*premiumMapper\.flags\(anuncio\)/)
assert.match(publicDetailSource, /premium\.fotosExtrasAtivo\(\) \? FOTOS_COM_EXTRA : FOTOS_BASE/)
assert.match(publicDetailSource, /premium\.videoAtivo\(\)/)
assert.match(limitSource, /FOTOS_BASE = 4/)
assert.match(limitSource, /FOTOS_COM_EXTRA = 10/)
assert.match(mapperSource, /vinculo\.getTipo\(\) != TipoAnuncioMidia\.VIDEO \|\| videoPermitido/)
assert.match(mapperSource, /fotos\.getAndIncrement\(\) < Math\.max\(0, maxFotos\)/)
assert.match(mapperSource, /Comparator[\s\S]*prioridadeTipo[\s\S]*AnuncioMidiaEntity::getOrdem/)

assert.match(viewerSource, /viewerItem\?\.modoConteudo === "ANUNCIO"/)
assert.match(viewerSource, /anuncioMidiaIndex/)
assert.match(viewerSource, /irParaAnuncioDoStory/)
assert.match(viewerSource, /viewerItem\.viewerState === \x22IDADE_NAO_CONFIRMADA\x22/)

console.log('STORY_ANUNCIO_MEDIA_LIMIT_RESULT=OK')
