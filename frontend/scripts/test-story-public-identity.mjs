import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [feedSource, premiumSource, ageSource, typesSource, viewerSource] = await Promise.all([
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/service/StoryFeedPublicoService.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/premium/PremiumPublicoMapper.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/service/IdadeAnunciantePublicaService.java', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/stories/stories-types.ts', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/stories/story-viewer-dialog.tsx', import.meta.url), 'utf8'),
])

assert.match(feedSource, /identidadesPorUsuario\(usuarios\.stream\(\)\.map\(UsuarioStory::usuario\)\.toList\(\)\)/)
assert.match(feedSource, /identidades\.get\(item\.usuario\(\)\.getId\(\)\)/)
assert.match(feedSource, /usuariosComIdadeOcultaNosStories\(usuariosPorId\.keySet\(\)\)/)
assert.match(feedSource, /viewerMidiaIndependente[\s\S]*identidadeStory\(usuario\)/)
assert.match(feedSource, /viewerAnuncio[\s\S]*identidadeStory\(usuario\)/)
assert.doesNotMatch(feedSource, /item\.anuncio\(\) == null \? null : idades/)
assert.doesNotMatch(feedSource, /MessageDigest|HexFormat|topsv3-public-user-v1/)

assert.match(premiumSource, /usuariosComIdadeOcultaNosStories/)
assert.match(premiumSource, /consultarCalculadosPorUsuario\(usuarioIds\)/)
assert.match(premiumSource, /OCULTAR_IDADE\.equals\(item\.beneficio\(\)\.getCodigo\(\)\)/)
assert.match(premiumSource, /PremiumBeneficioStatusCalculado\.ATIVO/)
assert.match(premiumSource, /PremiumBeneficioStatusCalculado\.VENCENDO/)
assert.match(ageSource, /resolverPorUsuarios/)
assert.match(ageSource, /ocultos\.contains\(usuario\.getId\(\)\)/)

assert.match(typesSource, /return idade == null \? nome : `\$\{nome\}, \$\{idade\} anos`/)
assert.match(viewerSource, /const rotuloPerfil = loginViewer \? `@\$\{loginViewer\}` : rotuloBundle/)
assert.match(viewerSource, /rotuloPublicoComIdade\(/)
assert.doesNotMatch(viewerSource, /anuncioTitulo[^\n]*usuarioUsername/)

console.log('STORY_PUBLIC_IDENTITY_RESULT=OK')
