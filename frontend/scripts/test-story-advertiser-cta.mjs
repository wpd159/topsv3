import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [viewerSource, pageSource, clientSource, apiSource, repositorySource, guardSource] = await Promise.all([
  readFile(new URL('../src/components/stories/story-viewer-dialog.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/app/(public-routes)/anuncios/usuario/[username]/page.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/app/(public-routes)/anuncios/usuario/[username]/anuncios-usuario-client.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/lib/public-catalog-api.ts', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/persistence/repository/AnuncioRepository.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/service/RotaPublicaGuard.java', import.meta.url), 'utf8'),
])

assert.match(viewerSource, /router\.push\(`\/anuncios\/usuario\/\$\{encodeURIComponent\(loginViewer\)\}`\)/)
assert.match(viewerSource, /const rotuloDestino = podeNavegarPerfil \? "Ver anunciante"/)
assert.match(pageSource, /const publicUsername = rawUsername \? decodeURIComponent\(rawUsername\) : ""/)
assert.match(pageSource, /<AnunciosUsuarioClient publicUsername=\{publicUsername\} \/>/)
assert.match(clientSource, /export default function AnunciosUsuarioClient\(\{ publicUsername \}/)
assert.doesNotMatch(clientSource, /profileToken|usuarioPublicoId/)
assert.match(clientSource, /Nenhum an.ncio p.blico dispon.vel no momento\./)
assert.match(apiSource, /`\/anuncios\/usuario\/\$\{encodeURIComponent\(username\.trim\(\)\)\}/)

assert.match(repositorySource, /where lower\(btrim\(u\.nome\)\) = :username/)
assert.doesNotMatch(repositorySource, /topsv3-public-user-v1/)
assert.match(repositorySource, /findPublicosPorUsuarioOrdenados[\s\S]*anuncio_bloqueio_juridico/)
assert.match(repositorySource, /bloqueio\.anuncio_desbloqueado_em is null/)
assert.match(guardSource, /normalized\.length\(\) < 3/)
assert.match(guardSource, /normalized\.length\(\) > 120/)
assert.doesNotMatch(guardSource, /\[0-9a-f\]\{32\}/)

console.log('STORY_ADVERTISER_CTA_RESULT=OK')
