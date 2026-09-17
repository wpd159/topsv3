import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8')

const api = read('src/lib/public-metrics-api.ts')
const detail = read('src/app/(public-routes)/anuncios/[slug]/anuncio-detalhes.tsx')
const sidebar = read('src/app/(public-routes)/anuncios/[slug]/componentes/sidebar.tsx')
const card = read('src/components/anuncios/anuncio-card.tsx')

assert.match(api, /publicCsrfHeaders\(\)/, 'Metricas publicas devem reutilizar o CSRF canonico.')
assert.match(api, /headers\.set\('Idempotency-Key', idempotencyKey\)/)
assert.match(api, /for \(let tentativa = 0; tentativa < 2;/, 'Retry deve ser unico e reutilizar a mesma chave.')
assert.match(api, /keepalive: true/, 'Registro deve sobreviver a navegacao externa.')
assert.match(api, /\/visualizacao`/)
assert.match(api, /\/clique-whatsapp`/)

assert.match(detail, /registrarVisualizacaoPublica\(slug, chaveRegistro\.chave\)/)
assert.match(detail, /visualizacaoRegistradaParaId\.current === anuncio\.id/)
assert.match(detail, /visualizacaoFetchParaId\.current === anuncio\.id/)
assert.doesNotMatch(
  detail,
  /fetch\(.*\/visualizacao/s,
  'O detalhe nao deve manter um segundo adapter de visualizacao.',
)

for (const source of [sidebar, card]) {
  assert.match(source, /whatsappInFlight\.current/, 'Duplo clique deve ser bloqueado.')
  assert.match(source, /registrarCliqueWhatsappPublico\(/)
  assert.match(source, /if \(!payload\.registrado\)/, 'Falha isolada da metrica deve ser informada.')
  assert.match(source, /openWhatsAppWarning\(\{ url: payload\.whatsappUrl \}\)/)
  assert.match(source, /error instanceof ApiContractError && error\.status === 403/)
  assert.match(source, /void requestWhatsApp\(\)/, 'Fluxo deve retomar depois do age gate.')
}

assert.doesNotMatch(card, /window[^\n]*gtag/, 'O card deve usar o controle central de consentimento do GA4.')

function deferred() {
  let resolve
  let reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

async function flush() {
  for (let turn = 0; turn < 10; turn += 1) await Promise.resolve()
}

class ApiContractError extends Error {
  constructor(status) {
    super('Falha sintetica')
    this.status = status
  }
}

const compiledCard = ts.transpileModule(card, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022,
    jsx: ts.JsxEmit.ReactJSX,
  },
}).outputText

function scenario({ previewMode = false, analyticsBlocked = false } = {}) {
  const hooks = []
  let hookIndex = 0
  let effects = []
  let sequence = 0
  const attempts = []
  const events = []
  const requests = []
  const warnings = []
  const errors = []
  const notices = []
  const jsx = (type, props) => ({ type, props })
  const modules = {
    'react/jsx-runtime': { jsx, jsxs: jsx, Fragment: 'Fragment' },
    react: {
      useMemo: (factory) => factory(),
      useEffect: (effect) => effects.push(effect),
      useState: (initial) => {
        const index = hookIndex++
        if (!(index in hooks)) hooks[index] = typeof initial === 'function' ? initial() : initial
        return [hooks[index], (next) => { hooks[index] = typeof next === 'function' ? next(hooks[index]) : next }]
      },
      useRef: (initial) => {
        const index = hookIndex++
        if (!(index in hooks)) hooks[index] = { current: initial }
        return hooks[index]
      },
    },
    'next/image': { default: 'Image' },
    'next/link': { default: 'Link' },
    'next/navigation': { useRouter: () => ({ push: () => assert.fail('O teste de WhatsApp nao deve navegar.') }) },
    sonner: { toast: { error: (message) => errors.push(message), warning: (message) => notices.push(message) } },
    '@/components/ui/button': { Button: 'Button' },
    '@/components/compliance/sensitive-image': { SensitiveImage: 'SensitiveImage' },
    '@/components/compliance/visitor-verification-modal': { VisitorVerificationModal: 'VisitorVerificationModal' },
    '@/components/anuncios/favorito-button': { FavoritoButton: 'FavoritoButton' },
    '@/components/anuncios/anuncio-card-video': { AnuncioCardVideo: 'AnuncioCardVideo' },
    '@/components/anuncios/imagem-proprietario': { ImagemProprietario: 'ImagemProprietario' },
    '@/lib/media/public-media': { selecionarGaleriaPublicaSegura: () => [] },
    '@/components/site/whatsapp-safety-provider': { useWhatsAppSafety: () => ({ openWhatsAppWarning: (request) => warnings.push(request) }) },
    '@/context/AuthContext': { useAuth: () => ({ usuario: null }) },
    '@/lib/text/encoding': { corrigirTextoCorrompido: (value) => value },
    '@/lib/api-contract': { ApiContractError },
    '@/lib/analytics/ga4': {
      registrarEventoGA4: (event, params) => {
        attempts.push({ event, params })
        if (analyticsBlocked) return false
        events.push({ event, params })
        return true
      },
    },
    '@/lib/public-metrics-api': {
      novaChaveMetricaPublica: () => `chave-sintetica-${++sequence}`,
      registrarCliqueWhatsappPublico: (slug, key) => {
        const request = { slug, key, ...deferred() }
        requests.push(request)
        return request.promise
      },
    },
    '@/lib/visualizacoes-canonicas': { formatarVisualizacoesCanonicas: () => '0' },
    '@heroicons/react/24/solid': Object.fromEntries([
      'MapPinIcon', 'ChatBubbleLeftIcon', 'ChevronLeftIcon', 'ChevronRightIcon',
      'EyeIcon', 'PhotoIcon', 'PlayCircleIcon', 'CalendarDaysIcon',
    ].map((name) => [name, name])),
  }
  const compiledModule = { exports: {} }
  new Function('module', 'exports', 'require', compiledCard)(compiledModule, compiledModule.exports, (specifier) => {
    assert.ok(Object.hasOwn(modules, specifier), `Import nao simulado: ${specifier}`)
    return modules[specifier]
  })

  function render() {
    hookIndex = 0
    effects = []
    const tree = compiledModule.exports.AnuncioCard({
      id: 'anuncio-sintetico', slug: 'nome-sintetico-no-slug', nome: 'Nome sintetico',
      valor: 'A combinar', whatsappCardEnabled: true, previewMode,
    })
    for (const effect of effects) effect()
    const elements = []
    function visit(node) {
      if (Array.isArray(node)) return node.forEach(visit)
      if (!node || typeof node !== 'object') return
      elements.push(node)
      visit(node.props?.children)
    }
    visit(tree)
    return {
      button: elements.find((element) => element.type === 'Button' && element.props.children === 'WhatsApp'),
      modal: elements.find((element) => element.type === 'VisitorVerificationModal'),
    }
  }

  return { render, attempts, events, requests, warnings, errors, notices }
}

const click = (button) => button.props.onClick({ stopPropagation() {} })
const available = { disponivel: true, registrado: true, whatsappUrl: 'https://example.test/contato-sintetico' }

const single = scenario()
const firstRender = single.render()
single.render()
assert.equal(single.attempts.length, 0, 'Render e remontagem de efeitos nao podem emitir cliques.')
assert.equal(single.requests.length, 0)
click(firstRender.button)
assert.deepEqual(single.events, [{
  event: 'click_whatsapp',
  params: { event_category: 'engagement', event_label: 'card_anuncio' },
}], 'Um clique deve emitir a intencao com dimensao fixa, sem o slug.')
assert.equal(single.requests.length, 1)
click(firstRender.button)
const pending = single.render()
assert.equal(pending.button.props.disabled, true)
click(pending.button)
assert.equal(single.events.length, 1, 'Reentradas durante a requisicao nao podem duplicar GA4.')
assert.equal(single.requests.length, 1, 'Reentradas preservam a protecao funcional existente.')
single.requests[0].resolve(available)
await flush()
assert.deepEqual(single.warnings, [{ url: available.whatsappUrl }])
click(single.render().button)
assert.equal(single.events.length, 2, 'Um novo clique apos concluir a acao deve ser contado.')
assert.equal(single.requests.length, 2)
assert.notEqual(single.requests[0].key, single.requests[1].key)
single.requests[1].resolve(available)
await flush()

const verified = scenario()
click(verified.render().button)
verified.requests[0].reject(new ApiContractError(403))
await flush()
const verification = verified.render()
assert.equal(verification.modal.props.open, true)
click(verification.button)
assert.equal(verified.attempts.length, 1, 'Modal de verificacao aberto nao permite nova intencao de clique.')
assert.equal(verified.requests.length, 1)
verification.modal.props.onVerified()
assert.equal(verified.requests.length, 2, 'Verificacao concluida deve retomar o contato.')
assert.equal(verified.requests[0].key, verified.requests[1].key, 'Retomada preserva a chave idempotente.')
assert.equal(verified.attempts.length, 1, 'Retomada de 403 nao e outro clique.')
verified.requests[1].resolve(available)
await flush()
assert.deepEqual(verified.warnings, [{ url: available.whatsappUrl }])
assert.equal(verified.errors.length, 0)
assert.equal(verified.render().modal.props.open, false)

const preview = scenario({ previewMode: true })
const previewRender = preview.render()
assert.equal(previewRender.button, undefined, 'Preview nao oferece o contato publico.')
previewRender.modal.props.onVerified()
await flush()
assert.equal(preview.attempts.length, 0)
assert.equal(preview.requests.length, 0)

const blocked = scenario({ analyticsBlocked: true })
click(blocked.render().button)
assert.equal(blocked.events.length, 0)
assert.equal(blocked.requests.length, 1, 'GA4 bloqueado nao pode impedir a acao funcional.')
blocked.requests[0].resolve(available)
await flush()
assert.deepEqual(blocked.warnings, [{ url: available.whatsappUrl }])
assert.equal(blocked.errors.length, 0)

const unavailable = scenario()
click(unavailable.render().button)
unavailable.requests[0].resolve({ disponivel: false })
await flush()
assert.equal(unavailable.events.length, 1, 'Evento mede intencao de clique mesmo quando o contato esta indisponivel.')
assert.equal(unavailable.warnings.length, 0)
assert.equal(unavailable.errors.length, 1)

console.log('OK metricas publicas: contratos preservados; GA4 por clique, sem duplicar verificacao, preview ou render; nenhum acesso externo')
