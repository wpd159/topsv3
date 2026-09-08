import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { runInNewContext } from "node:vm"
import ts from "typescript"

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), "utf8")
}

function only(nodes, description) {
  assert.equal(nodes.length, 1, description)
  return nodes[0]
}

function declaredFunction(parent, name) {
  return only(parent.statements.filter((node) =>
    ts.isFunctionDeclaration(node) && node.name?.text === name,
  ), `Expected the actual ${name} declaration`)
}

function executable(node, scope) {
  const expression = ts.isFunctionDeclaration(node)
    ? ts.factory.createFunctionExpression(undefined, node.asteriskToken, undefined,
      node.typeParameters, node.parameters, node.type, node.body)
    : node
  const text = ts.createPrinter({ removeComments: true }).printNode(
    ts.EmitHint.Expression, expression, node.getSourceFile(),
  )
  const { outputText } = ts.transpileModule(`(${text})`, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2022,
      jsx: ts.JsxEmit.React,
      jsxFactory: "element",
    },
  })
  return runInNewContext(outputText, scope, { timeout: 1000 })
}

function element(type, props, ...children) {
  return { type, props: props ?? {}, children: children.flat(Infinity) }
}

function elements(tree, type) {
  if (!tree || typeof tree !== "object") return []
  return [ ...(tree.type === type ? [tree] : []),
    ...(tree.children ?? []).flatMap((child) => elements(child, type)) ]
}

function liveRenderCalls(node) {
  if (ts.isParenthesizedExpression(node)) return liveRenderCalls(node.expression)
  if (ts.isJsxElement(node)) return node.children.flatMap(liveRenderCalls)
  // Only unconditional JSX children: never comments, attributes, callbacks or dead branches.
  if (ts.isJsxExpression(node) && node.expression && ts.isCallExpression(node.expression)
      && ts.isIdentifier(node.expression.expression)
      && node.expression.expression.text === "renderViewerBody"
      && node.expression.arguments.length === 0) return [node.expression]
  return []
}

// Exported only so deliberate in-memory mutations exercise this same contract.
export async function assertStoryVisibilityContract(viewerSource, playerSource) {
  const parse = (name, text) => {
    const file = ts.createSourceFile(name, text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
    assert.equal(file.parseDiagnostics.length, 0, `${name} must parse`)
    return file
  }
  const viewer = declaredFunction(parse("viewer.tsx", viewerSource), "StoryViewerDialog")
  assert.ok(viewer.modifiers?.some((node) => node.kind === ts.SyntaxKind.ExportKeyword))
  const returned = only(viewer.body.statements.filter(ts.isReturnStatement),
    "Expected the viewer's actual top-level JSX return")
  assert.equal(returned, viewer.body.statements.at(-1))
  assert.equal(liveRenderCalls(returned.expression).length, 1,
    "The exported viewer must render its body in an unconditional JSX child")
  const render = declaredFunction(viewer.body, "renderViewerBody")
  const callbackDeclaration = only(viewer.body.statements
    .filter(ts.isVariableStatement)
    .flatMap((statement) => [...statement.declarationList.declarations])
    .filter((node) => ts.isIdentifier(node.name) && node.name.text === "markCurrentStoryVisible"),
  "Expected the viewer's visibility callback")
  const callback = callbackDeclaration.initializer
  assert.ok(ts.isCallExpression(callback) && ts.isIdentifier(callback.expression)
    && callback.expression.text === "useCallback" && ts.isArrowFunction(callback.arguments[0]),
  "Visibility must use the actual useCallback body")

  // Execute the callback, rather than accepting guard text in comments/dead branches.
  for (const [item, feed] of [
    [null, { storyId: "s1" }],
    [{ viewerState: "IDADE_NAO_CONFIRMADA", midiaUrl: "/media" }, { storyId: "s1" }],
    [{ viewerState: "INDISPONIVEL", midiaUrl: "/media" }, { storyId: "s1" }],
    [{ viewerState: "ERRO_DADOS", midiaUrl: "/media" }, { storyId: "s1" }],
    [{ midiaUrl: "/media" }, { storyId: "s1" }],
    [{ viewerState: "LIBERADO", modoConteudo: "MIDIA_UPLOAD", midiaUrl: null }, { storyId: "s1" }],
    [{ viewerState: "LIBERADO", midiaUrl: "/media" }, null],
  ]) {
    const fail = () => assert.fail("Unavailable/unreleased content must not be marked visible")
    executable(callback.arguments[0], {
      currentFeedItem: feed, viewerItem: item, viewedStoryRef: { current: null },
      setMediaReady: fail, onStoryCurrent: fail,
    })()
  }
  for (const modoConteudo of ["MIDIA_UPLOAD", "ANUNCIO"]) {
    const feed = { storyId: "s1" }
    const viewed = { current: null }
    let notifications = 0
    let ready = 0
    const mark = executable(callback.arguments[0], {
      currentFeedItem: feed,
      viewerItem: { viewerState: "LIBERADO", modoConteudo,
        midiaUrl: modoConteudo === "ANUNCIO" ? null : "/media" },
      viewedStoryRef: viewed,
      setMediaReady: (value) => { assert.equal(value, true); ready++ },
      onStoryCurrent: (item) => {
        assert.equal(item, feed)
        assert.equal(viewed.current, String(feed.storyId), "Record identity before notifying")
        notifications++
      },
    })
    mark()
    mark()
    assert.equal(ready, 2)
    assert.equal(notifications, 1, "Do not notify the same story twice")
    feed.storyId = "s2"
    mark()
    assert.equal(notifications, 2, "Deduplication must not suppress a different story")
  }

  const mark = () => {}
  const noop = () => {}
  const StoryVideoPlayer = () => {}
  for (const modoConteudo of ["ANUNCIO", "MIDIA_UPLOAD"]) {
    for (const tipo of ["VIDEO", "FOTO"]) {
      const media = { id: "m1", tipo, urlPublica: "/media" }
      const tree = executable(render, {
        element, StoryVideoPlayer, Button: "Button", MapPinIcon: "MapPinIcon",
        currentFeedItem: { storyId: "s1" }, viewerLoading: false, viewerError: null,
        viewerItem: { storyId: "s1", viewerState: "LIBERADO", modoConteudo, tipo, midiaUrl: "/media" },
        mediaError: false, anuncioMidiaAtual: media, anuncioMidias: [media], anuncioMidiaIndex: 0,
        storySoundPreference: "AUDIBLE", setStorySoundPreference: noop,
        markCurrentStoryVisible: mark, nextVisibleContent: noop, setMediaError: noop,
        setVideoProg: noop, irParaAnuncioDoStory: noop, podeNavegarAnuncio: true,
      })()
      const rendered = only(elements(tree, tipo === "VIDEO" ? StoryVideoPlayer : "img"),
        `Expected the rendered ${modoConteudo}/${tipo} media`)
      assert.equal(rendered.props.src, "/media")
      assert.equal(rendered.props[tipo === "VIDEO" ? "onReady" : "onLoad"], mark,
        `${modoConteudo}/${tipo} must forward the actual visibility callback`)
    }
  }

  const player = declaredFunction(parse("player.tsx", playerSource), "StoryVideoPlayer")
  assert.ok(player.modifiers?.some((node) => node.kind === ts.SyntaxKind.ExportKeyword))
  let ready = 0
  const tree = executable(player, {
    element, SpeakerWaveIcon: "SpeakerWaveIcon", SpeakerXMarkIcon: "SpeakerXMarkIcon",
    useRef: (current) => ({ current }), useCallback: (fn) => fn,
    useEffect: (effect) => { effect() },
    useState: (initial) => {
      let value = typeof initial === "function" ? initial() : initial
      return [value, (next) => { value = typeof next === "function" ? next(value) : next }]
    },
    detectarDisponibilidadeAudio: () => "UNKNOWN", erroBloqueioAutoplay: () => false,
  })({
    mediaKey: "m1", src: "/media", soundPreference: "AUDIBLE",
    onReady: () => { ready++ }, onSoundPreferenceChange: noop,
    onEnded: noop, onError: noop, onProgress: noop,
  })
  const video = only(elements(tree, "video"), "Expected the player's rendered video")
  assert.equal(ready, 0, "Rendering/effects must not notify playback")
  const event = { currentTarget: { play: async () => {}, duration: 1, currentTime: 0 } }
  for (const [name, handler] of Object.entries(video.props)) {
    if (name.startsWith("on") && name !== "onPlaying" && typeof handler === "function") {
      await handler(event)
      assert.equal(ready, 0, `${name} must not replace actual playback notification`)
    }
  }
  assert.equal(typeof video.props.onPlaying, "function", "Expected the effective onPlaying handler")
  await video.props.onPlaying(event)
  assert.equal(ready, 1, "The effective onPlaying handler must call onReady")
}

const media = source("src/lib/media/public-media.ts")
const sensitiveImage = source("src/components/compliance/sensitive-image.tsx")
const card = source("src/components/anuncios/anuncio-card.tsx")
const detail = source("src/app/(public-routes)/anuncios/[slug]/page.tsx")
const detailHeader = source(
  "src/app/(public-routes)/anuncios/[slug]/componentes/header-tabs.tsx",
)
const stories = source("src/components/stories/stories-bar.tsx")
const storyViewer = source("src/components/stories/story-viewer-dialog.tsx")
const storyPlayer = source("src/components/stories/story-video-player.tsx")
const storyTypes = source("src/components/stories/stories-types.ts")
const storyPolicy = source("src/components/stories/story-access-policy.js")
const publicLayout = source("src/app/(public-routes)/layout.tsx")
const homePage = source("src/app/(public-routes)/page.tsx")
const acompanhantesLayout = source("src/app/(public-routes)/acompanhantes/layout.tsx")
const anunciosLayout = source("src/app/(public-routes)/anuncios/layout.tsx")
const publicApi = source("src/lib/public-catalog-api.ts")

assert.match(media, /previewUrl\?: string \| null/)
assert.match(
  media,
  /return midia\.autorizada \? midia\.urlPublica \?\? null : midia\.previewUrl \?\? null/,
)
assert.doesNotMatch(media, /filter:\s*blur|blur\(/i)
assert.doesNotMatch(sensitiveImage, /filter:\s*blur|blur\(/i)
assert.match(sensitiveImage, /fonteEhPreviewPublica/)
assert.match(
  sensitiveImage,
  /unoptimized=\{!otimizarImagemPublica \|\| imagemPublicaR2\(fonte\)\}/,
)

assert.match(card, /const nomeComIdade = idade != null/)
assert.match(card, /Foto de perfil de \$\{nomeExibido\}/)
assert.doesNotMatch(card, /alt=\{[^}]*idade/i)
assert.match(detailHeader, /nomeComIdade/)
assert.doesNotMatch(detailHeader, /alt=\{[^}]*idade/i)
assert.match(publicApi, /idade\?: number \| null/)

assert.match(detail, /selecionarImagemPublicaSeo/)
assert.match(detail, /fontePublicaSegura\(capa\)/)
assert.match(detail, /primaryImageOfPage/)
assert.match(detail, /images: \[\{ url: imagemPublica/)

assert.match(stories, /src=\{first\.previewUrl\}/)
assert.match(stories, /width=\{64\}/)
assert.match(stories, /rotuloPublicoComIdade/)
assert.match(stories, /sanitizeStoryFeedItem/)
assert.match(storyViewer, /sanitizeStoryViewerItem/)
assert.match(storyViewer, /onLoad=\{markCurrentStoryVisible\}/)
await assertStoryVisibilityContract(storyViewer, storyPlayer)
assert.doesNotMatch(storyViewer, /backgroundImage=/)
assert.doesNotMatch(storyViewer, /if \(open && currentFeedItem\) onStoryCurrent/)
assert.match(storyPolicy, /previewState === 'AVAILABLE'/)
assert.match(storyPolicy, /viewerState === 'LIBERADO'/)
assert.match(storyTypes, /idade == null \? nome : `\$\{nome\}, \$\{idade\} anos`/)
assert.doesNotMatch(stories, /filter:\s*blur|blur\(/i)
assert.doesNotMatch(storyViewer, /filter:\s*blur|blur\(/i)

assert.doesNotMatch(
  publicLayout,
  /rating:\s*['"]adult['"]/,
  "institutional public routes must not inherit the adult rating",
)
for (const adultRouteSource of [homePage, acompanhantesLayout, anunciosLayout]) {
  const ratingMatches = adultRouteSource.match(/rating:\s*['"]adult['"]/g) ?? []
  assert.equal(ratingMatches.length, 1, "adult route tree must declare adult rating exactly once")
}

console.log("RESTRICTED_MEDIA_AGE_SEO_RESULT=OK")
