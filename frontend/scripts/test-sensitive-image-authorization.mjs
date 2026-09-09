import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

// Persisted React/browser regression, not a production or standalone smoke.
// Security components, session API/policy, modal and gallery are real modules.
// Only unrelated navigation/auth/favorites/toast and HTTP transport are fixtures.
// Case17 additionally controls the real Scheduler passive callback, without
// replacing React effects. This local timing regression is not an integrated
// application/CI claim; the other sixteen cases leave this scheduler gate off.
const require = createRequire(import.meta.url)
const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = path.join(frontend, 'src')
const nodeModules = path.join(frontend, 'node_modules')
const evidence = fs.mkdtempSync(path.join(process.env.TOPS_UI_EVIDENCE_ROOT || os.tmpdir(), 'sensitive-image-dom-'))
const write = (name, bytes) => fs.writeFileSync(path.join(evidence, name), bytes, { flag: 'wx' })
const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex')
const { chromium } = require(process.env.TOPS_PLAYWRIGHT_MODULE || 'playwright')
const sharp = require('sharp')
const bundledWebpack = require('next/dist/compiled/webpack/webpack')
bundledWebpack.init()
const { webpack } = bundledWebpack
const inputs = [
  'src/components/compliance/sensitive-image.tsx',
  'src/components/compliance/visitor-verification-modal.tsx',
  'src/components/compliance/restricted-media-overlay.tsx',
  'src/app/(public-routes)/anuncios/[slug]/componentes/header-tabs.tsx',
  'src/lib/compliance/visitor-access.ts',
  'src/lib/compliance/visitor-access-policy.ts',
  'src/lib/compliance/age-gate-api.ts',
  'src/lib/media/public-media.ts',
  'package.json', 'package-lock.json', 'scripts/test-sensitive-image-authorization.mjs',
]
const inputHashes = Object.fromEntries(inputs.map((name) => [name, sha256(fs.readFileSync(path.join(frontend, name)))]))
write('inputs-before.json', JSON.stringify(inputHashes, null, 2))
const results = [], requests = [], unexpected = [], browserErrors = [], cleanupErrors = []
let browser, server, compiler, activeContext, failure
const deferred = () => {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}
const waitFor = async (predicate, label, timeout = 5000) => {
  const deadline = performance.now() + timeout
  while (!predicate()) {
    assert.ok(performance.now() < deadline, 'Observation timeout: ' + label)
    await new Promise((resolve) => setTimeout(resolve, 10))
  }
}
const bounded = (promise, label) => {
  let timer
  return Promise.race([promise, new Promise((_, reject) => { timer = setTimeout(() => reject(Error('Cleanup timeout: ' + label)), 10000) })]).finally(() => clearTimeout(timer))
}
const denied = () => ({ globalAccepted: true, verified: false, level: null, expiresAt: null, explicitVerified: false, explicitLevel: null, explicitExpiresAt: null, state: 'NAO_VERIFICADO' })
const granted = (milliseconds = 60000) => ({ ...denied(), verified: true, level: 'REINFORCED', expiresAt: new Date(Date.now() + milliseconds).toISOString(), state: 'VERIFIED' })
const protectedPath = (id) => '/api/public/compliance/visitor/media/' + id
const free = (id = 'free-a') => ({ id, tipo: 'FOTO', ordem: 0, visibilidadeMidia: 'LIVRE', autorizada: true, urlPublica: '/synthetic/' + id + '.png', previewUrl: null })
const restricted = (id = 'restricted-a', autorizada = false) => ({ id, tipo: 'FOTO', ordem: 1, visibilidadeMidia: 'RESTRITA_18', autorizada, urlPublica: null, previewUrl: '/synthetic/preview-' + id + '.png' })
const defaultConfig = () => ({ mode: 'image', anuncioId: 'advertisement-a', slug: 'advertisement-a', media: free(), midias: [free(), restricted(), { ...restricted('restricted-b'), ordem: 2 }] })

write('loader.cjs', `const ts=require(${JSON.stringify(require.resolve('typescript'))});module.exports=function(source){return ts.transpileModule(source,{fileName:this.resourcePath,compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext,jsx:ts.JsxEmit.ReactJSX}}).outputText}`)
write('navigation.js', 'export const useRouter=()=>({push:()=>{throw Error("Unexpected navigation")}});export const usePathname=()=>location.pathname;')
write('auth.js', 'export const useAuth=()=>({usuario:null,carregando:false});')
write('favorites.js', 'export const useFavoritos=()=>({isFavorito:()=>false,isPendente:()=>false,alternar:()=>{throw Error("Unexpected favorite mutation")}});')
write('toast.js', `export const toast=Object.fromEntries(['success','error','warning'].map(kind=>[kind,message=>window.__toast.push({kind,message})]));`)
write('entry.tsx', `
import React,{useState,useLayoutEffect,useEffect,startTransition} from 'react';
import {createRoot} from 'react-dom/client';
import {flushSync} from 'react-dom';
import {SensitiveImage} from ${JSON.stringify(path.join(source, 'components/compliance/sensitive-image.tsx'))};
import HeaderTabs from ${JSON.stringify(path.join(source, 'app/(public-routes)/anuncios/[slug]/componentes/header-tabs.tsx'))};
import {notificarMudancaVerificacao,AGE_VERIFICATION_CHANGED_EVENT} from ${JSON.stringify(path.join(source, 'lib/compliance/visitor-access.ts'))};
window.__toast=[];window.__sessionEvents=[];window.__commits=[];window.__phases=[];
window.addEventListener(AGE_VERIFICATION_CHANGED_EVENT,()=>window.__sessionEvents.push({at:performance.now(),phases:window.__phases.map(item=>({...item})),scheduler:window.__passiveGate.snapshot()}));
function BoundaryMarker({id}){
 useLayoutEffect(()=>{window.__phases.push({kind:'layout-mount',id,at:performance.now()});return()=>window.__phases.push({kind:'layout-cleanup',id,at:performance.now()})},[id]);
 useEffect(()=>{window.__phases.push({kind:'passive-mount',id,at:performance.now()});return()=>window.__phases.push({kind:'passive-cleanup',id,at:performance.now()})},[id]);
 return null;
}
let root;let update;
function App({initial}){
 const [config,setConfig]=useState(initial);const [photo,setPhoto]=useState(0);update=setConfig;
 useLayoutEffect(()=>{window.__currentConfig=config;window.__commits.push({ad:config.anuncioId,media:config.media.id,at:performance.now()})});
 return <><BoundaryMarker key={config.anuncioId} id={config.anuncioId}/>{config.mode==='gallery'
  ? <HeaderTabs anuncio={{id:config.anuncioId,slug:config.slug,nome:'Pessoa sintética',idade:25,cidade:'Cidade sintética',estadoUf:'SP',midias:config.midias}} imagemAtiva={photo} setImagemAtiva={setPhoto}/>
  : <div id="direct"><SensitiveImage midia={config.media} anuncioId={config.anuncioId} anuncioSlug={config.slug} alt="Imagem sintética" width={80} height={60}/></div>}</>;
}
window.__control={
 mount(config){if(root)throw Error('Already mounted');root=createRoot(document.getElementById('root'));flushSync(()=>root.render(<App initial={config}/>));},
 update(patch){flushSync(()=>update(current=>({...current,...patch})));},
 updateTransition(patch){startTransition(()=>update(current=>({...current,...patch})));},
 notify(status){notificarMudancaVerificacao(status);},
 unmount(){if(root){flushSync(()=>root.unmount());root=null;}}
};
window.__ready=true;
`)

write('scheduler-window.cjs', `
const native = require(${JSON.stringify(require.resolve('scheduler'))});
const events = [], held = [];
let enabled = false, nextId = 0;
function snapshot(){return {enabled,held:held.filter(item=>!item.released&&!item.cancelled).length,events:events.map(item=>({...item}))};}
function release(){
 enabled=false;
 for(const item of held)if(!item.released&&!item.cancelled){
  item.released=true;events.push({kind:'release-passive',id:item.id,at:performance.now()});
  item.nativeTask=native.unstable_scheduleCallback(item.priority,item.callback,item.options);
 }
}
window.__passiveGate={enable(){enabled=true;events.push({kind:'enable',at:performance.now()})},release,snapshot};
module.exports={...native,
 unstable_scheduleCallback(priority,callback,options){
  const code=Function.prototype.toString.call(callback);
  const passive=code.includes('flushPassiveEffects');
  if(enabled&&passive){
   if(priority!==native.unstable_NormalPriority||!code.includes('flushPassiveEffects(!0)')||!code.includes('return null'))
    throw Error('Unknown passive callback shape: cannot establish controlled scheduler boundary.');
   const item={id:--nextId,priority,callback,options,released:false,cancelled:false};
   held.push(item);events.push({kind:'held-passive',id:item.id,at:performance.now(),code});return item;
  }
  return native.unstable_scheduleCallback(priority,callback,options);
 },
 unstable_cancelCallback(task){
  if(held.includes(task)){task.cancelled=true;events.push({kind:'cancel-held',id:task.id,at:performance.now()});if(task.nativeTask)native.unstable_cancelCallback(task.nativeTask);return;}
  return native.unstable_cancelCallback(task);
 }
};
`)

try {
  const imageBytes = await sharp({ create: { width: 8, height: 6, channels: 3, background: '#647080' } }).png().toBuffer()
  write('synthetic-image.json', JSON.stringify({ format: 'png', width: 8, height: 6, bytes: imageBytes.length, sha256: sha256(imageBytes), syntheticOnly: true }))
  compiler = webpack({
    mode: 'development', target: 'web', devtool: false, context: frontend, entry: path.join(evidence, 'entry.tsx'),
    output: { path: evidence, filename: 'bundle.js' },
    resolve: { extensions: ['.tsx', '.ts', '.jsx', '.js'], modules: [nodeModules], alias: {
      'next/navigation$': path.join(evidence, 'navigation.js'),
      '@/context/AuthContext$': path.join(evidence, 'auth.js'),
      '@/context/FavoritosContext$': path.join(evidence, 'favorites.js'),
      sonner: path.join(evidence, 'toast.js'), 'scheduler$': path.join(evidence, 'scheduler-window.cjs'), '@': source,
    } },
    module: { rules: [{ test: /\.[jt]sx?$/, exclude: /node_modules/, use: path.join(evidence, 'loader.cjs') }] },
    plugins: [new webpack.DefinePlugin({ 'process.env': JSON.stringify({ NODE_ENV: 'development', NEXT_PUBLIC_API_URL: '/api/public' }) })],
  })
  const stats = await new Promise((resolve, reject) => compiler.run((error, value) => error ? reject(error) : resolve(value)))
  write('webpack.json', JSON.stringify(stats.toJson({ all: false, errors: true, warnings: true }), null, 2))
  assert.equal(stats.hasErrors(), false, stats.toString({ all: false, errors: true }))
  const bundle = fs.readFileSync(path.join(evidence, 'bundle.js'))
  const html = '<!doctype html><html><head><meta charset="utf-8"><style>body{font-family:sans-serif}#direct{position:relative;width:400px;height:300px}#direct>div{height:300px!important}.public-anuncio-gallery>section:first-of-type,.public-anuncio-gallery>section:first-of-type>div:first-child{position:relative;width:400px;height:300px}[aria-label="Mini galeria"]>div>div{display:flex}[aria-label="Mini galeria"]>div>div>div{position:relative;width:120px;height:100px}[aria-label^="Selecionar mídia"]{position:absolute;inset:0;z-index:30;min-width:100px;min-height:80px}.compliance-restricted-overlay{position:absolute;inset:0;z-index:20;background:#333d;color:white}.compliance-restricted-overlay p{margin:4px}.compliance-restricted-overlay button{min-height:30px}[role="dialog"]{position:fixed;inset:0;z-index:100;background:white;overflow:auto;padding:24px}input{display:block;min-height:22px}input[type="checkbox"]{display:inline-block}label{display:block}button{min-height:28px}svg{width:20px;height:20px}</style></head><body><div id="root"></div><script src="/bundle.js"></script></body></html>'
  server = http.createServer((request, response) => {
    if (request.method === 'GET' && request.url === '/bundle.js') { response.writeHead(200, { 'content-type': 'text/javascript' }); response.end(bundle); return }
    if (request.method === 'GET' && request.url === '/') { response.writeHead(200, { 'content-type': 'text/html; charset=utf-8' }); response.end(html); return }
    if (request.method === 'GET' && request.url === '/favicon.ico') { response.writeHead(204); response.end(); return }
    unexpected.push({ boundary: 'loopback-server', method: request.method, path: request.url })
    response.writeHead(405); response.end('No undeclared transport')
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  const channel = process.env.TOPS_UI_BROWSER_CHANNEL
  browser = await chromium.launch({ headless: true, ...(channel && channel !== 'chromium' ? { channel } : {}) })
  write('browser.json', JSON.stringify({ version: browser.version(), node: process.version, origin, realReactDOM: true, realNextImage: true, standaloneApplicationTest: false, personalProfileUsed: false }))
  const settle = async (page) => page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))))
  const view = (page, gallery = false) => gallery ? page.locator('.public-anuncio-gallery > section:first-of-type') : page.locator('#direct')
  const waitSource = async (page, expected, gallery = false) => {
    await page.waitForFunction(({ selector, expectedPath }) => {
      const img = document.querySelector(selector)
      if (!img?.complete || !img.naturalWidth || !img.currentSrc) return false
      const url = new URL(img.currentSrc)
      return url.pathname === expectedPath || (url.pathname === '/_next/image' && url.searchParams.get('url') === expectedPath)
    }, { selector: gallery ? '.public-anuncio-gallery > section:first-of-type img' : '#direct img', expectedPath: expected }, { timeout: 5000 })
  }
  const assertPreview = async (page, id, gallery = false) => {
    await waitSource(page, '/synthetic/preview-' + id + '.png', gallery)
    assert.equal(await view(page, gallery).locator('[data-restricted-media-overlay]').count(), 1)
  }
  const select = (page, number) => page.getByRole('button', { name: `Selecionar mídia ${number}`, exact: true }).click()

  async function scenario(name, action) {
    const state = { statuses: [], verifies: [], challenges: [], autoStatus: null, allowProtected: new Set(), grantExpiresAt: 0, enableChallenge: false, challengeMode: 'OK', enableVerify: false, closing: false }
    const start = requests.length
    const result = { name, result: 'FAIL', checkpoints: [] }
    const pending = new Set()
    const context = await browser.newContext({ serviceWorkers: 'block', viewport: { width: 1280, height: 900 } })
    activeContext = context
    context.setDefaultTimeout(5000)
    await context.addInitScript(() => {
      window.__sourceAttempts = []; window.__allowedProtected = []; window.__grantExpiresAt = 0
      function observe(kind, value) {
        for (const candidate of String(value || '').split(',')) {
          try {
            let url = new URL(candidate.trim().split(/\s+/)[0], location.href)
            if (url.pathname === '/_next/image') url = new URL(url.searchParams.get('url') || '', location.href)
            if (!url.pathname.startsWith('/api/public/compliance/visitor/media/')) continue
            window.__sourceAttempts.push({ kind, path: url.pathname, raw: String(value), at: performance.now(), allowed: window.__allowedProtected.includes(url.pathname) && Date.now() < window.__grantExpiresAt })
          } catch { /* Non-URL attributes are not media transport. */ }
        }
      }
      for (const key of ['src', 'srcset']) {
        const descriptor = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, key)
        if (descriptor?.set) Object.defineProperty(HTMLImageElement.prototype, key, { ...descriptor, set(value) { observe('image-property-' + key, value); descriptor.set.call(this, value) } })
      }
      for (const key of ['href', 'imageSrcset']) {
        const descriptor = Object.getOwnPropertyDescriptor(HTMLLinkElement.prototype, key)
        if (descriptor?.set) Object.defineProperty(HTMLLinkElement.prototype, key, { ...descriptor, set(value) { observe('preload-property-' + key, value); descriptor.set.call(this, value) } })
      }
      const original = Element.prototype.setAttribute
      Element.prototype.setAttribute = function (key, value) {
        if ((this.tagName === 'IMG' && ['src', 'srcset'].includes(key.toLowerCase())) || (this.tagName === 'LINK' && ['href', 'imagesrcset'].includes(key.toLowerCase()))) observe('attribute-' + key, value)
        return original.call(this, key, value)
      }
      new MutationObserver((mutations) => {
        for (const mutation of mutations) {
          if (mutation.type === 'attributes') observe('mutation-' + mutation.attributeName, mutation.target.getAttribute(mutation.attributeName))
          for (const node of mutation.addedNodes) if (node instanceof Element) {
            for (const element of [node, ...node.querySelectorAll('img,link')]) {
              if (element.tagName === 'IMG') { observe('insert-src', element.getAttribute('src')); observe('insert-srcset', element.getAttribute('srcset')) }
              if (element.tagName === 'LINK') { observe('insert-link', element.getAttribute('href')); observe('insert-link-imagesrcset', element.getAttribute('imagesrcset')) }
            }
          }
        }
      }).observe(document, { subtree: true, childList: true, attributes: true, attributeFilter: ['src', 'srcset', 'href', 'imagesrcset'] })
    })
    await context.routeWebSocket('**/*', (socket) => { unexpected.push({ name, boundary: 'websocket', url: socket.url() }); socket.close({ code: 1008, reason: 'No WebSocket fixture' }) })
    const page = await context.newPage()
    page.on('pageerror', (error) => browserErrors.push({ name, message: error.message }))
    async function json(route, status, body, headers = {}) { await route.fulfill({ status, contentType: 'application/json', headers, body: JSON.stringify(body) }) }
    async function handle(route) {
      const request = route.request(), url = new URL(request.url())
      const entry = { name, method: request.method(), path: url.pathname, resourceType: request.resourceType(), at: new Date().toISOString() }
      requests.push(entry)
      if (url.origin !== origin) { unexpected.push({ ...entry, origin: url.origin }); await route.abort(); return }
      if (entry.method === 'GET' && ['/', '/bundle.js', '/favicon.ico'].includes(url.pathname)) { await route.continue(); return }
      if (entry.method === 'GET' && url.pathname.startsWith('/api/public/compliance/visitor/media/')) {
        entry.protected = true
        entry.allowed = state.allowProtected.has(url.pathname) && Date.now() < state.grantExpiresAt
        if (!entry.allowed) { entry.blockedBeforeTransport = true; await json(route, 405, { code: 'SYNTHETIC_PRIVATE_TRANSPORT_DENIED' }); return }
        entry.syntheticResponse = true; entry.sha256 = sha256(imageBytes)
        await route.fulfill({ status: 200, contentType: 'image/png', body: imageBytes }); return
      }
      const imageInput = url.pathname === '/_next/image' ? url.searchParams.get('url') : url.pathname
      if (entry.method === 'GET' && /^\/synthetic\/(?:free-[ab]|preview-restricted-[ab])\.png$/.test(imageInput || '')) {
        entry.syntheticImage = true
        if (imageInput === '/synthetic/preview-restricted-b.png' && state.holdNextPreview) {
          entry.heldSyntheticPreview = true
          await state.holdNextPreview.promise
        }
        await route.fulfill({ status: 200, contentType: 'image/png', body: imageBytes }); return
      }
      if (entry.method === 'GET' && url.pathname === '/api/public/compliance/visitor/status') {
        const call = { gate: deferred(), done: false, entry }
        state.statuses.push(call); entry.statusCall = state.statuses.length - 1
        if (state.autoStatus) call.gate.resolve({ status: 200, body: state.autoStatus })
        const response = await call.gate.promise
        entry.responseStatus = response.status; entry.responseBody = response.body
        await json(route, response.status, response.body); call.done = true; return
      }
      if (entry.method === 'GET' && url.pathname === '/api/public/compliance/age-gate/status' && state.enableChallenge) {
        await json(route, 200, { accepted: true, state: 'GLOBAL_ACEITO' }, { 'set-cookie': 'XSRF-TOKEN=EXEMPLO_NAO_REAL; Path=/; SameSite=Lax' }); return
      }
      if (entry.method === 'POST' && url.pathname === '/api/public/compliance/visitor/challenge' && state.enableChallenge) {
        const body = request.postDataJSON(); entry.body = body; state.challenges.push(body)
        assert.equal(body.scope, 'MIDIA_RESTRITA'); assert.equal(body.level, 'REINFORCED')
        assert.ok(['advertisement-a', 'advertisement-b'].includes(body.anuncioId)); assert.ok(['restricted-a', 'restricted-b'].includes(body.midiaId))
        assert.ok(body.idempotencyKey); assert.equal(request.headers()['x-xsrf-token'], 'EXEMPLO_NAO_REAL')
        if (state.challengeMode === 'ERROR') { await json(route, 503, { message: 'Erro sintético de verificação' }); return }
        await json(route, 200, { challengeId: 'synthetic-challenge', state: state.challengeMode === 'BLOCKED' ? 'BLOCKED' : 'CREATED', effectiveLevel: 'REINFORCED', scope: 'MIDIA_RESTRITA', expiresAt: new Date(Date.now() + 60000).toISOString(), requiresExplicitAcknowledgement: false, documentRequired: false, maxAttempts: 3, reasonPublic: 'Verificação sintética negada' }); return
      }
      if (entry.method === 'POST' && url.pathname === '/api/public/compliance/visitor/verify' && state.enableVerify) {
        const body = request.postDataJSON(); entry.body = body
        assert.equal(body.challengeId, 'synthetic-challenge'); assert.equal(body.cpf, '000.000.000-00')
        assert.equal(body.aceiteMaioridade, true); assert.equal(body.aceiteConteudoRestrito, true); assert.equal(body.aceitePrivacidade, true)
        assert.ok(body.idempotencyKey); assert.equal(request.headers()['x-xsrf-token'], 'EXEMPLO_NAO_REAL')
        const call = { gate: deferred(), done: false, entry }; state.verifies.push(call)
        const response = await call.gate.promise; entry.responseStatus = response.status; entry.responseBody = response.body
        await json(route, response.status, response.body); call.done = true; return
      }
      unexpected.push(entry); await json(route, 405, { code: 'UNDECLARED_SYNTHETIC_TRANSPORT' })
    }
    await context.route('**/*', async (route) => {
      const operation = handle(route); pending.add(operation)
      try { await operation } catch (error) {
        if (!state.closing) unexpected.push({ name, boundary: 'handler', message: String(error) })
        try { await route.abort() } catch { /* A closing context may have already cancelled the request. */ }
      } finally { pending.delete(operation) }
    })
    const api = {
      page, state, result,
      entries: () => requests.slice(start),
      mount: async (config = defaultConfig()) => { await page.evaluate((value) => window.__control.mount(value), config); await settle(page) },
      update: async (patch) => { await page.evaluate((value) => window.__control.update(value), patch); await settle(page) },
      notify: async (status) => { await page.evaluate((value) => window.__control.notify(value), status); await settle(page) },
      releaseStatus: async (index, body, status = 200) => { await waitFor(() => state.statuses[index], 'status request' + index); state.statuses[index].gate.resolve({ status, body }); await waitFor(() => state.statuses[index].done, 'status response' + index); await settle(page) },
      releaseVerify: async (body, status = 200) => { await waitFor(() => state.verifies[0], 'verify request'); state.verifies[0].gate.resolve({ status, body }); await waitFor(() => state.verifies[0].done, 'verify response'); await settle(page) },
      allow: async (ids, expiresAt) => { state.allowProtected = new Set(ids.map(protectedPath)); state.grantExpiresAt = Date.parse(expiresAt); await page.evaluate(({ paths, expires }) => { window.__allowedProtected = paths; window.__grantExpiresAt = expires }, { paths: [...state.allowProtected], expires: state.grantExpiresAt }) },
      deny: async () => { state.allowProtected.clear(); state.grantExpiresAt = 0; await page.evaluate(() => { window.__allowedProtected = []; window.__grantExpiresAt = 0 }) },
      checkpoint: async (label) => { result.checkpoints.push({ label, sources: await page.locator('img').evaluateAll((images) => images.map((image) => ({ src: image.getAttribute('src'), srcset: image.getAttribute('srcset'), currentSrc: image.currentSrc, complete: image.complete, width: image.naturalWidth, height: image.naturalHeight }))), sourceAttempts: await page.evaluate(() => window.__sourceAttempts), requests: requests.slice(start).length }) },
    }
    try {
      // The guard and source observer precede navigation and every React mount.
      await page.goto(origin + '/')
      await page.waitForFunction(() => window.__ready === true)
      await action(api)
      await settle(page)
      result.sourceAttempts = await page.evaluate(() => window.__sourceAttempts)
      result.sessionEvents = await page.evaluate(() => window.__sessionEvents)
      result.protectedRequests = requests.slice(start).filter((entry) => entry.protected)
      assert.deepEqual(result.protectedRequests.filter((entry) => !entry.allowed), [], 'Anonymous protected request counts as failure even though intercepted.')
      assert.deepEqual(result.sourceAttempts.filter((entry) => !entry.allowed), [], 'No unauthorized original in src/srcset/preload, including the first render.')
      assert.deepEqual(unexpected.filter((entry) => entry.name === name), [])
      assert.deepEqual(browserErrors.filter((entry) => entry.name === name), [])
      result.result = 'PASS'
    } catch (error) {
      result.failure = error.stack || String(error)
      try { result.sourceAttempts = await page.evaluate(() => window.__sourceAttempts); write(name + '.html', await page.content()) } catch (captureError) { result.captureError = String(captureError) }
    } finally {
      state.closing = true
      try { await page.evaluate(() => window.__passiveGate?.release()) } catch (error) { cleanupErrors.push(name + ': scheduler-release: ' + String(error)) }
      state.holdNextPreview?.resolve()
      try { await page.evaluate(() => window.__control?.unmount()) } catch (error) { cleanupErrors.push(name + ': unmount: ' + String(error)) }
      for (const call of [...state.statuses, ...state.verifies]) if (!call.done) call.gate.resolve({ status: 403, body: denied() })
      try { await bounded(Promise.allSettled([...pending]), name + ': requests') } catch (error) { cleanupErrors.push(String(error)) }
      result.protectedRequests = requests.slice(start).filter((entry) => entry.protected)
      result.requests = requests.slice(start)
      try { await bounded(context.close(), name + ': context'); activeContext = null } catch (error) { cleanupErrors.push(String(error)) }
      results.push(result); write(name + '.json', JSON.stringify(result, null, 2))
    }
    assert.equal(cleanupErrors.length, 0, 'Do not start another scenario when cleanup is unproven.')
  }

  async function prepareModal(api, challengeMode = 'OK') {
    api.state.enableChallenge = true; api.state.enableVerify = true; api.state.challengeMode = challengeMode
    await api.mount({ ...defaultConfig(), media: restricted() })
    await api.releaseStatus(0, denied())
    await assertPreview(api.page, 'restricted-a')
    await api.page.getByRole('button', { name: 'Confirmar maioridade', exact: true }).click()
    await api.releaseStatus(1, denied())
    await waitFor(() => api.state.challenges.length === 1, 'real challenge POST')
    if (challengeMode !== 'OK') { await api.page.getByRole('alert').waitFor(); return }
    await api.page.getByLabel('Data de nascimento', { exact: true }).fill('01/01/1990')
    await api.page.getByLabel('Confirme a data de nascimento', { exact: true }).fill('01/01/1990')
    await api.page.getByRole('button', { name: 'Continuar', exact: true }).click()
    await api.page.getByLabel('CPF', { exact: true }).fill('000.000.000-00')
    for (let index = 0; index < 3; index++) await api.page.getByRole('checkbox').nth(index).check()
    await api.page.getByRole('button', { name: 'Verificar', exact: true }).click()
    await waitFor(() => api.state.verifies.length === 1, 'real deferred verification POST')
  }

  await scenario('gallery-free-restricted-slow-denied-return', async (api) => {
    await api.mount({ ...defaultConfig(), mode: 'gallery' })
    await waitFor(() => api.state.statuses.length === 1, 'pending anonymous session')
    await select(api.page, 2); await settle(api.page)
    await api.checkpoint('first-restricted-render-while-session-pending')
    await api.releaseStatus(0, denied())
    await assertPreview(api.page, 'restricted-a', true)
    await select(api.page, 3); await assertPreview(api.page, 'restricted-b', true)
    await select(api.page, 1); await waitSource(api.page, '/synthetic/free-a.png', true)
    assert.equal(await view(api.page, true).locator('[data-restricted-media-overlay]').count(), 0)
  })
  await scenario('restricted-restricted-anonymous', async (api) => {
    await api.mount({ ...defaultConfig(), media: restricted() })
    await api.releaseStatus(0, denied())
    await api.update({ media: restricted('restricted-b') })
    await assertPreview(api.page, 'restricted-b')
  })
  await scenario('old-response-other-context-after-new-denial', async (api) => {
    await api.mount({ ...defaultConfig(), media: restricted() })
    await waitFor(() => api.state.statuses.length === 1, 'old session pending')
    await api.update({ anuncioId: 'advertisement-b', slug: 'advertisement-b', media: restricted('restricted-b') })
    await api.notify(undefined)
    await api.releaseStatus(1, denied())
    await api.releaseStatus(0, granted())
    await assertPreview(api.page, 'restricted-b')
    await api.checkpoint('new-denial-survived-older-grant')
  })
  await scenario('legitimate-general-session-survives-clicks', async (api) => {
    await api.mount({ ...defaultConfig(), mode: 'gallery' })
    const grant = granted(); await api.allow(['restricted-a', 'restricted-b'], grant.expiresAt)
    await api.releaseStatus(0, grant)
    await select(api.page, 2); await waitSource(api.page, protectedPath('restricted-a'), true)
    assert.equal(await view(api.page, true).locator('[data-restricted-media-overlay]').count(), 0)
    await select(api.page, 3); await waitSource(api.page, protectedPath('restricted-b'), true)
    assert.equal(await view(api.page, true).locator('[data-restricted-media-overlay]').count(), 0)
    await select(api.page, 1); await waitSource(api.page, '/synthetic/free-a.png', true)
    assert.ok(api.entries().some((entry) => entry.protected && entry.allowed && entry.path === protectedPath('restricted-a')))
    assert.ok(api.entries().some((entry) => entry.protected && entry.allowed && entry.path === protectedPath('restricted-b')))
  })
  for (const [name, status] of [
    ['light-cannot-grant-reinforced', { ...granted(), level: 'LIGHT' }],
    ['explicit-token-cannot-grant-general', { ...denied(), explicitVerified: true, explicitLevel: 'STRONG', explicitExpiresAt: new Date(Date.now() + 60000).toISOString() }],
    ['expired-session-cannot-grant', { ...granted(), expiresAt: '2000-01-01T00:00:00Z' }],
  ]) await scenario(name, async (api) => {
    await api.mount({ ...defaultConfig(), media: restricted() })
    await api.releaseStatus(0, status)
    await assertPreview(api.page, 'restricted-a')
  })
  await scenario('revocation-overrides-stale-authorized-dto', async (api) => {
    await api.mount({ ...defaultConfig(), media: restricted('restricted-a', true) })
    const grant = granted(); await api.allow(['restricted-a'], grant.expiresAt)
    await api.releaseStatus(0, grant)
    await waitSource(api.page, protectedPath('restricted-a'))
    await api.deny(); await api.notify(denied())
    await assertPreview(api.page, 'restricted-a')
    await api.checkpoint('revoked-session-with-unchanged-dto-true')
  })
  await scenario('anonymous-session-error-does-not-inherit-free', async (api) => {
    await api.mount()
    await api.update({ media: restricted() })
    await api.releaseStatus(0, { message: 'Erro sintético de consulta' }, 503)
    await assertPreview(api.page, 'restricted-a')
    await api.update({ media: free('free-b') })
    await waitSource(api.page, '/synthetic/free-b.png')
  })
  for (const mode of ['BLOCKED', 'ERROR']) await scenario('modal-challenge-' + mode.toLowerCase(), async (api) => {
    await prepareModal(api, mode)
    assert.equal(api.state.verifies.length, 0)
    await api.page.getByRole('button', { name: 'Fechar', exact: true }).last().click()
    await assertPreview(api.page, 'restricted-a')
  })
  await scenario('modal-legitimate-verification-and-general-context', async (api) => {
    await prepareModal(api)
    const grant = granted(); await api.allow(['restricted-a', 'restricted-b'], grant.expiresAt)
    await api.releaseVerify(grant)
    await waitSource(api.page, protectedPath('restricted-a'))
    assert.equal(await api.page.getByRole('dialog').count(), 0)
    await api.update({ anuncioId: 'advertisement-b', slug: 'advertisement-b', media: restricted('restricted-b') })
    await waitSource(api.page, protectedPath('restricted-b'))
    assert.equal(await view(api.page).locator('[data-restricted-media-overlay]').count(), 0)
    assert.ok(api.entries().some((entry) => entry.protected && entry.allowed && entry.path === protectedPath('restricted-b')))
  })
  for (const transition of ['cancel', 'context', 'invalidate']) await scenario('modal-old-verification-after-' + transition, async (api) => {
    await prepareModal(api)
    const notifications = await api.page.evaluate(() => window.__sessionEvents.length)
    api.state.autoStatus = denied()
    if (transition === 'cancel') await api.page.getByRole('button', { name: 'Fechar', exact: true }).first().click()
    if (transition === 'context') await api.update({ anuncioId: 'advertisement-b', slug: 'advertisement-b', media: restricted('restricted-b') })
    if (transition === 'invalidate') await api.notify(denied())
    const expectedNotifications = notifications + (transition === 'invalidate' ? 1 : 0)
    await api.releaseVerify(granted())
    assert.equal(await api.page.evaluate(() => window.__sessionEvents.length), expectedNotifications, 'A stale verification may not republish a global authorization.')
    await assertPreview(api.page, transition === 'context' ? 'restricted-b' : 'restricted-a')
    await api.checkpoint('late-verification-discarded')
  })
  await scenario('live-session-expiration-removes-original', async (api) => {
    await api.mount({ ...defaultConfig(), media: restricted() })
    const grant = granted(2000); await api.allow(['restricted-a'], grant.expiresAt)
    await api.releaseStatus(0, grant)
    await waitSource(api.page, protectedPath('restricted-a'))
    await assertPreview(api.page, 'restricted-a')
    await api.checkpoint('expired-session-preview-restored')
  })
  await scenario('modal-verify-after-transition-commit-before-passive-cleanup', async (api) => {
    await prepareModal(api)
    const oldId = 'advertisement-a', nextId = 'advertisement-b'
    assert.equal(await api.page.evaluate((id) => window.__phases.some(item=>item.kind==='passive-mount'&&item.id===id), oldId), true)
    api.state.autoStatus = denied()
    // A new image's onLoad is deliberately pending, so it cannot schedule a
    // separate update that flushes passive effects before the verification.
    // This is controlled benign HTTP transport, not a patched React effect.
    api.state.holdNextPreview = deferred()
    await api.page.evaluate(({ nextId, media }) => {
      window.__passiveGate.enable()
      window.__control.updateTransition({ anuncioId: nextId, slug: nextId, media })
    }, { nextId, media: restricted('restricted-b') })
    await api.page.waitForFunction(({oldId,nextId}) =>
      window.__currentConfig.anuncioId === nextId
      && window.__phases.some(item=>item.kind==='layout-cleanup'&&item.id===oldId)
      && window.__phases.some(item=>item.kind==='layout-mount'&&item.id===nextId)
      && window.__passiveGate.snapshot().held > 0,
      {oldId,nextId}, {timeout:5000})
    const barrier = await api.page.evaluate(() => ({
      config: window.__currentConfig, phases: window.__phases.map(item=>({...item})),
      scheduler: window.__passiveGate.snapshot(), events: window.__sessionEvents.slice(),
      dialogCount: document.querySelectorAll('[role="dialog"]').length,
    }))
    api.result.lifecycle = { barrier, controlledSchedulingOnly: true, actualReactCommitAndCleanup: true }
    assert.equal(barrier.phases.some(item=>item.kind==='passive-cleanup'&&item.id===oldId), false,
      'The required commit-before-passive-cleanup interval must actually be established.')
    assert.equal(barrier.dialogCount, 0, 'The old verification dialog is already absent in the committed DOM.')
    assert.equal(barrier.events.length, 0)
    await api.releaseVerify(granted())
    const after = await api.page.evaluate(() => ({
      phases: window.__phases.map(item=>({...item})),
      scheduler: window.__passiveGate.snapshot(), events: window.__sessionEvents.slice(),
    }))
    api.result.lifecycle.afterVerify = after
    api.result.lifecycle.obsoleteNotificationsBeforePassiveCleanup = after.events.filter(event =>
      event.phases.some(item=>item.kind==='layout-cleanup'&&item.id===oldId)
      && !event.phases.some(item=>item.kind==='passive-cleanup'&&item.id===oldId))
    await api.checkpoint('old-verify-settled-in-established-passive-window')
    api.state.holdNextPreview.resolve()
    assert.deepEqual(api.result.lifecycle.obsoleteNotificationsBeforePassiveCleanup, [],
      'A verification whose modal was already removed by a real transition commit must not publish global authorization before passive cleanup.')
    assert.equal(after.events.length, 0, 'No delayed global authorization after the closed/context-replaced flow.')
    await api.page.evaluate(() => window.__passiveGate.release())
    await settle(api.page)
    await assertPreview(api.page, 'restricted-b')
  })
  assert.equal(results.length, 17)
  assert.ok(results.every((item) => item.result === 'PASS'), 'Every persisted authorization scenario must pass; all negative observations are preserved.')
  assert.deepEqual(unexpected, [])
} catch (error) { failure = error }
finally {
  if (activeContext) { try { await bounded(activeContext.close(), 'active context') } catch (error) { cleanupErrors.push(String(error)) } }
  if (browser) { try { await bounded(browser.close(), 'browser'); assert.equal(browser.isConnected(), false) } catch (error) { cleanupErrors.push(String(error)) } }
  if (server) { try { server.closeAllConnections(); await bounded(new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())), 'loopback server'); assert.equal(server.listening, false) } catch (error) { cleanupErrors.push(String(error)) } }
  if (compiler) { try { await bounded(new Promise((resolve, reject) => compiler.close((error) => error ? reject(error) : resolve())), 'webpack') } catch (error) { cleanupErrors.push(String(error)) } }
  const inputsAfter = Object.fromEntries(inputs.map((name) => [name, sha256(fs.readFileSync(path.join(frontend, name)))]))
  const changedInputs = inputs.filter((name) => inputHashes[name] !== inputsAfter[name])
  write('inputs-after.json', JSON.stringify(inputsAfter, null, 2))
  const result = !failure && !cleanupErrors.length && !changedInputs.length && results.length === 17 && results.every((item) => item.result === 'PASS') ? 'PASS' : 'FAIL'
  write('outcome.json', JSON.stringify({ result, cases: results, failure: failure?.stack, cleanupErrors, changedInputs, unexpected, browserErrors, requests, evidence, realReactDOM: true, realSecurityModules: true, guardInstalledBeforeNavigationAndMount: true, productionOrUserProfile: false, standaloneApplicationTest: false, workflowIntegrationClaim: 'No workflow edited: caller must state whether this persisted command is actually scheduled by CI.' }, null, 2))
  console.log('SENSITIVE_IMAGE_AUTH_EVIDENCE=' + evidence)
  if (changedInputs.length) failure ||= Error('Test inputs changed during execution.')
}
if (failure) throw failure
assert.deepEqual(cleanupErrors, [])
console.log('SENSITIVE_IMAGE_AUTH_RESULT=PASS cases=17 realReactDOM=true protectedAnonymousAttempts=0 cleanup=PASS')
