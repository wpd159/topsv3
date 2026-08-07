import assert from 'node:assert/strict'
import { access, readFile } from 'node:fs/promises'

const [
  viewerSource,
  catalogPageSource,
  catalogClientSource,
  catalogGridSource,
  apiSource,
  controllerSource,
  repositorySource,
  serviceSource,
  guardSource,
  openApiSource,
] = await Promise.all([
  readFile(new URL('../src/components/stories/story-viewer-dialog.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/app/(public-routes)/anuncios/page.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/app/(public-routes)/anuncios/anuncios-page-client.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/anuncios/anuncios-grid.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/lib/public-catalog-api.ts', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/web/publico/AnuncioPublicoController.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/persistence/repository/AnuncioRepository.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/service/ListagemPublicaConsultaService.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/service/RotaPublicaGuard.java', import.meta.url), 'utf8'),
  readFile(new URL('../../contracts/openapi/topsdojob-v3-local.yaml', import.meta.url), 'utf8'),
])

await assert.rejects(
  access(new URL('../src/app/(public-routes)/anuncios/usuario/[username]/page.tsx', import.meta.url)),
  (error) => error?.code === 'ENOENT',
)
await assert.rejects(
  access(new URL('../src/app/(public-routes)/anuncios/usuario/[username]/anuncios-usuario-client.tsx', import.meta.url)),
  (error) => error?.code === 'ENOENT',
)

assert.match(viewerSource, /router\.push\(`\/anuncios\?anunciante=\$\{encodeURIComponent\(loginViewer\)\}`\)/)
assert.match(viewerSource, /const rotuloDestino = podeNavegarPerfil \? "Ver anunciante"/)
assert.doesNotMatch(viewerSource, /\/anuncios\/usuario\/|usuarioId|profileToken/)

assert.match(catalogPageSource, /anunciante\?: string/)
assert.match(catalogPageSource, /const anunciante = \(searchParams\.anunciante \|\| ""\)\.trim\(\)/)
assert.match(catalogPageSource, /listarAnunciosPublicos\([\s\S]*undefined,\s*anunciante,/)
assert.match(catalogClientSource, /searchParams\.get\("anunciante"\)/)
assert.match(catalogClientSource, /params\.delete\("anunciante"\)/)
assert.match(catalogClientSource, /router\.push\(query \? `\/anuncios\?\$\{query\}` : "\/anuncios"/)
assert.match(catalogClientSource, /anunciante=\{anuncianteParam\}/)
assert.match(catalogGridSource, /anunciante\?: string/)
assert.match(catalogGridSource, /listarAnunciosPublicos\([\s\S]*ordemSeed,\s*anunciante,/)
assert.match(catalogGridSource, /Nenhum an.ncio encontrado\./)

assert.match(apiSource, /if \(anunciante\?\.trim\(\)\) query\.set\('anunciante', anunciante\.trim\(\)\)/)
assert.doesNotMatch(apiSource, /listarAnunciosPublicosPorUsuario|\/anuncios\/usuario\//)
assert.match(controllerSource, /@RequestParam\(required = false\) String anunciante/)
assert.doesNotMatch(controllerSource, /@GetMapping\("\/usuario\/\{username\}"\)|listarPorUsuario/)

assert.match(repositorySource, /where lower\(btrim\(u\.nome\)\) = :username/)
assert.doesNotMatch(repositorySource, /topsv3-public-user-v1/)
assert.doesNotMatch(repositorySource, /findPublicosPorUsuarioOrdenados/)
const canonicalMethodIndex = repositorySource.indexOf('Page<AnuncioEntity> findPublicosOrdenados(')
const canonicalQueryStart = repositorySource.lastIndexOf('    @Query(', canonicalMethodIndex)
assert.ok(canonicalQueryStart >= 0 && canonicalMethodIndex > canonicalQueryStart)
const canonicalQuerySource = repositorySource.slice(canonicalQueryStart, canonicalMethodIndex)
assert.match(canonicalQuerySource, /and \(:usuarioId is null or a\.usuario_id = :usuarioId\)/)
assert.doesNotMatch(canonicalQuerySource, /documento_busca_anuncio/)
assert.equal(
  (serviceSource.match(/anuncioRepository\.findPublicosOrdenados\(/g) ?? []).length,
  1,
  'O catálogo deve possuir uma única consulta canônica com filtro opcional de anunciante.',
)
assert.match(serviceSource, /UUID usuarioId = usuarioId\(anunciante\)/)
assert.match(serviceSource, /findUsuarioPublicoPorUsername\(username\)/)
assert.doesNotMatch(serviceSource, /porUsuario\(/)
assert.match(guardSource, /normalized\.length\(\) < 3/)
assert.match(guardSource, /normalized\.length\(\) > 120/)
assert.doesNotMatch(guardSource, /\[0-9a-f\]\{32\}/)
assert.match(openApiSource, /- name: anunciante[\s\S]*in: query/)
assert.doesNotMatch(openApiSource, /\/api\/public\/anuncios\/usuario\/\{username\}/)

console.log('STORY_ADVERTISER_CTA_RESULT=OK')
