import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sourceRoot = path.join(frontendRoot, 'src')

function source(relativePath) {
  return fs.readFileSync(path.join(sourceRoot, relativePath), 'utf8')
}

const adapter = source('lib/favoritos-api.ts')
const provider = source('context/FavoritosContext.tsx')
const button = source('components/anuncios/favorito-button.tsx')
const card = source('components/anuncios/anuncio-card.tsx')
const detail = source('app/(public-routes)/anuncios/[slug]/componentes/header-tabs.tsx')
const page = source('app/(private-routes)/favoritos/page.tsx')
const combined = [adapter, provider, button, card, detail, page].join('\n')

assert.match(adapter, /'\/minha-conta\/favoritos'/)
assert.match(adapter, /method: 'PUT'/)
assert.match(adapter, /method: 'DELETE'/)
assert.match(adapter, /credentials: 'include'/)
assert.match(adapter, /XSRF/)
assert.match(provider, /\(await incluirFavorito\(slug\)\)\.favorito/)
assert.match(provider, /\(await removerFavorito\(slug\)\)\.favorito/)
assert.match(provider, /pendentesRef\.current\.has\(slug\)/)
assert.match(provider, /setErro\(message\)/)
assert.match(button, /HeartIcon as HeartOutline/)
assert.match(button, /HeartIcon as HeartSolid/)
assert.match(button, /aria-pressed=\{favorito\}/)
assert.match(button, /aria-busy=\{pendente\}/)
assert.match(button, /disabled=\{carregandoSessao \|\| pendente\}/)
assert.match(card, /<FavoritoButton slug=\{slugRota\}/)
assert.match(detail, /<FavoritoButton slug=\{anuncio\.slug\}/)
assert.match(page, /useFavoritos\(\)/)
assert.match(page, /Tentar novamente/)
assert.match(page, /Nenhum anúncio favoritado ainda/)
assert.doesNotMatch(combined, /localStorage|sessionStorage/)
assert.doesNotMatch(combined, /\/favoritar|usuarioId/)
assert.doesNotMatch(combined, /Ã[£¡º©ª]|â€“|â€™/)

console.log('FAVORITOS_V3_RESULT=OK')
