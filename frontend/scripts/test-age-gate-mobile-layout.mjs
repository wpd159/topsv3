import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const baseline = process.argv.includes('--baseline')
const modal = fs.readFileSync(
  path.join(root, 'src/components/modals/age-gate-modal.tsx'),
  'utf8',
)

if (baseline) assert.match(modal, /h-\[100dvh\]/)
else {
  assert.doesNotMatch(modal, /(?:^|\s)h-\[100dvh\]/)
  assert.match(modal, /flex h-auto max-h-\[100dvh\]/)
}
assert.match(modal, /max-h-\[100dvh\]/)
assert.doesNotMatch(modal, /(?:^|[^d])100vh/)
assert.match(modal, /flex-col/)
assert.match(modal, baseline ? /overflow-hidden/ : /overflow-x-hidden overflow-y-auto/)

assert.match(modal, /data-age-gate-header/)
assert.match(modal, /data-age-gate-body/)
assert.match(modal, baseline ? /min-h-0 flex-1 overflow-y-auto/ : /min-h-24 flex-auto overflow-y-auto/)
assert.match(modal, /data-age-gate-footer/)
assert.match(modal, /shrink-0/)
assert.match(modal, /safe-area-inset-top/)
assert.match(modal, /safe-area-inset-bottom/)
assert.match(modal, /safe-area-inset-left/)
assert.match(modal, /safe-area-inset-right/)

assert.match(modal, /sm:h-auto/)
assert.match(modal, /sm:max-h-\[85vh\]/)
assert.match(modal, /sm:max-w-md/)
assert.match(modal, /sm:rounded-2xl/)
assert.match(modal, /sm:p-6/)
assert.match(modal, /sm:overflow-y-auto/)

assert.match(modal, /Ao escolher uma opção para entrar, declaro que sou maior de 18 anos/)
assert.match(modal, /Aceitar todos os cookies e entrar/)
assert.match(modal, /Entrar somente com os necessários/)
assert.match(modal, /Personalizar cookies/)
assert.match(modal, /Salvar preferências e entrar/)
assert.match(modal, /Termos de Uso/)
assert.match(modal, /confirmarAceiteGlobal\(pathname \|\| '\/'\)/)
assert.doesNotMatch(modal, /document\.body\.style|overflow\s*=\s*['"]hidden/)

console.log('OK_AGE_GATE_MOBILE_LAYOUT')

if (process.argv.includes('--browser')) await verifyBrowserLayout()

async function verifyBrowserLayout() {
  const require = createRequire(import.meta.url)
  const { chromium } = require(process.env.TOPS_PLAYWRIGHT_MODULE || 'playwright')
  const compiled = require('next/dist/compiled/webpack/webpack')
  compiled.init()
  const { webpack } = compiled
  const postcss = require('postcss')
  const tailwind = require('@tailwindcss/postcss')
  const evidence = fs.mkdtempSync(path.join(process.env.TOPS_UI_EVIDENCE_ROOT || os.tmpdir(), baseline ? 'age-gate-mobile-before-' : 'age-gate-mobile-after-'))
  const write = (name, value) => fs.writeFileSync(path.join(evidence, name), value, { flag: 'wx' })
  const source = path.join(root, 'src')
  const results = [], unexpected = [], browserErrors = [], cleanupErrors = []
  const contexts = new Set()
  const cleanup = { contextsClosed: 0, browserClosed: false, serverClosed: false, compilerClosed: false }
  let compiler, browser, server, failure
  async function closeResource(label, operation, success) {
    let timer
    try {
      await Promise.race([operation(), new Promise((_, reject) => {
        timer = setTimeout(() => reject(Error('Cleanup timeout: ' + label)), 10000)
      })])
      success()
    } catch (error) { cleanupErrors.push({ resource: label, error: error.stack }) }
    finally { clearTimeout(timer) }
  }
  write('modal-source.tsx', modal)
  write('loader.cjs', `const ts=require(${JSON.stringify(require.resolve('typescript'))});module.exports=function(source){return ts.transpileModule(source,{fileName:this.resourcePath,compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext,jsx:ts.JsxEmit.ReactJSX}}).outputText}`)
  write('navigation.js', 'export const usePathname=()=>location.pathname;')
  write('entry.tsx', `
import React,{useEffect,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {AgeGateModal} from ${JSON.stringify(path.join(source, 'components/modals/age-gate-modal.tsx'))};
import {SiteContentProvider} from ${JSON.stringify(path.join(source, 'components/site-content/site-content-provider.tsx'))};
import {obterStatusVisitante} from ${JSON.stringify(path.join(source, 'lib/compliance/visitor-access.ts'))};
const text='Aviso sintetico para maiores de 18 anos. Conteudo restrito a adultos. Leia as condicoes antes de continuar. ';
window.dataLayer=[];
// This second real cache consumer shares the modal's pending HTTP status. Its
// committed marker lets absence assertions wait for resolved React state.
function StatusCommitted(){const[ready,setReady]=useState(false);useEffect(()=>{void obterStatusVisitante().then(()=>setReady(true))},[]);return <span data-fixture-status-committed={ready}/>}
createRoot(document.getElementById('root')).render(<SiteContentProvider entries={[{contentKey:'popup-login',titulo:'Aviso de maioridade',corpo:text.repeat(window.__fixture.longNotice?12:2)}]}><AgeGateModal denyRedirect='/saida-sintetica'/><StatusCommitted/></SiteContentProvider>);
window.__ready=true;
`)
  const consent = { necessary: true, functional: false, analytics: false, marketing: false, ts: 17 }
  try {
    const cssFile = path.join(source, 'app/globals.css')
    const css = await postcss([tailwind({ base: root })]).process(fs.readFileSync(cssFile, 'utf8'), { from: cssFile })
    write('styles.css', css.css)
    compiler = webpack({ mode: 'development', target: 'web', devtool: false, context: root,
      entry: path.join(evidence, 'entry.tsx'), output: { path: evidence, filename: 'bundle.js' },
      resolve: { extensions: ['.tsx', '.ts', '.js'], modules: [path.join(root, 'node_modules')],
        alias: { '@': source, 'next/navigation$': path.join(evidence, 'navigation.js') } },
      module: { rules: [{ test: /\.[jt]sx?$/, exclude: /node_modules/, use: path.join(evidence, 'loader.cjs') }] },
      plugins: [new webpack.DefinePlugin({ 'process.env': JSON.stringify({ NODE_ENV: 'development', NEXT_PUBLIC_API_URL: '/api/public' }) })],
    })
    const stats = await new Promise((resolve, reject) => compiler.run((error, value) => error ? reject(error) : resolve(value)))
    assert.equal(stats.hasErrors(), false, stats.toString({ all: false, errors: true }))
    const bundle = fs.readFileSync(path.join(evidence, 'bundle.js'))
    server = http.createServer((request, response) => {
      const routes = {
        '/': ['text/html; charset=utf-8', '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><link rel="stylesheet" href="/styles.css"></head><body><main><h1>Pagina sintetica</h1><a href="/fora-do-modal">Link externo ao modal</a></main><div id="root"></div><script src="/bundle.js"></script></body></html>'],
        '/bundle.js': ['text/javascript', bundle], '/styles.css': ['text/css', css.css],
      }
      if (request.url === '/favicon.ico') { response.writeHead(204); response.end(); return }
      const route = routes[request.url]
      if (!route) { unexpected.push(request.url); response.writeHead(405); response.end(); return }
      response.setHeader('Content-Type', route[0]); response.end(route[1])
    })
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
    const origin = `http://127.0.0.1:${server.address().port}`
    browser = await chromium.launch({ headless: true })
    async function scenario(name, config) {
      const context = await browser.newContext({ viewport: { width: config.width || 390, height: config.height || 844 },
        isMobile: !config.desktop, hasTouch: !config.desktop, deviceScaleFactor: 1 })
      contexts.add(context)
      let age = config.age === true, ageCalls = 0, statusCalls = 0
      await context.addInitScript(value => { window.__fixture = value }, { longNotice: config.longNotice === true })
      await context.addCookies([{ name: 'XSRF-TOKEN', value: 'synthetic-csrf', url: origin },
        ...(config.chosen ? [{ name: 'cookie_consent', value: encodeURIComponent(JSON.stringify(consent)), url: origin }] : [])])
      await context.route('**/*', async route => {
        const request = route.request(), url = new URL(request.url())
        if (url.origin !== origin) { unexpected.push(request.url()); return route.abort() }
        if (!url.pathname.startsWith('/api/')) return route.continue()
        const json = body => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
        if (url.pathname === '/api/public/compliance/visitor/status') {
          statusCalls++
          return json({ globalAccepted: age, verified: false, explicitVerified: false, state: age ? 'GLOBAL_ACEITO' : 'GLOBAL_NAO_ACEITO' })
        }
        if (url.pathname === '/api/public/compliance/age-gate/accept') {
          assert.equal(request.headers()['x-xsrf-token'], 'synthetic-csrf'); ageCalls++
          if (config.error) return route.fulfill({ status: 503, contentType: 'application/json', body: '{"message":"Falha sintetica"}' })
          age = true; return json({ accepted: true, state: 'GLOBAL_ACEITO' })
        }
        unexpected.push(url.pathname); return route.abort()
      })
      const page = await context.newPage()
      page.on('pageerror', error => browserErrors.push(error.message))
      try {
        await page.goto(origin)
        await page.waitForFunction(() => window.__ready)
        await page.locator('[data-fixture-status-committed="true"]').waitFor({ state: 'attached' })
        if (config.age && config.chosen) {
          await page.waitForFunction(() => document.querySelector('[data-age-gate-layout]') === null)
          assert.equal(statusCalls, 1)
          results.push({ name, noModal: true, ageCalls, consentPreserved: true }); return
        }
        await page.locator('[data-age-gate-layout]').waitFor()
        if (config.customize) await page.getByRole('button', { name: 'Personalizar cookies', exact: true }).tap()
        if (config.error) await page.getByRole('button', { name: 'Entrar somente com os necessários', exact: true }).tap()
        if (config.error) await page.getByRole('alert').waitFor()
        await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
        const measure = () => page.evaluate(() => {
          const rect = node => { const r = node.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height, bottom: r.bottom, right: r.right } }
          const dialog = document.querySelector('[data-age-gate-layout]'), body = document.querySelector('[data-age-gate-body]')
          return { viewport: { width: innerWidth, height: innerHeight, visualHeight: visualViewport.height, touch: navigator.maxTouchPoints },
            dialog: rect(dialog), body: { ...rect(body), clientHeight: body.clientHeight, scrollHeight: body.scrollHeight },
            footer: rect(document.querySelector('[data-age-gate-footer]')),
            horizontalOverflow: document.documentElement.scrollWidth > innerWidth || dialog.scrollWidth > dialog.clientWidth || body.scrollWidth > body.clientWidth,
            buttons: [...document.querySelectorAll('[data-age-gate-footer] button')].map(node => ({ text: node.textContent.trim(), ...rect(node) })),
            title: dialog.getAttribute('aria-labelledby'), description: dialog.getAttribute('aria-describedby'),
            css: { dialogHeight: getComputedStyle(dialog).height, dialogMaxHeight: getComputedStyle(dialog).maxHeight, bodyFlex: getComputedStyle(body).flex },
          }
        })
        const initial = await measure()
        await page.screenshot({ path: path.join(evidence, name + '.png'), fullPage: true })
        assert.ok(initial.title && initial.description, name + ': accessible name and description')
        assert.equal(initial.horizontalOverflow, false, name + ': no horizontal overflow')
        assert.ok(initial.dialog.y >= -1 && initial.dialog.bottom <= initial.viewport.visualHeight + 1, name + ': dialog within visible viewport')
        if (!baseline && !config.desktop) {
          assert.ok(initial.body.clientHeight >= 48, name + ': readable scroll area survives reduced height')
          if (config.age && !config.customize) assert.ok(initial.dialog.height < initial.viewport.visualHeight * 0.8, name + ': short cookie content does not fill the screen')
        }
        let touchScroll = null
        if (!config.desktop && initial.body.scrollHeight > initial.body.clientHeight && initial.body.clientHeight >= 48) {
          const body = page.locator('[data-age-gate-body]')
          await body.scrollIntoViewIfNeeded()
          const before = await body.evaluate(node => node.scrollTop)
          const bounds = await body.boundingBox()
          const session = await context.newCDPSession(page)
          try {
            const x = bounds.x + bounds.width / 2
            const from = Math.min(config.height || 844, bounds.y + bounds.height) - 10
            const to = Math.max(0, bounds.y) + 10
            await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y: from }] })
            for (let step = 1; step <= 8; step++) {
              await session.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x, y: from + (to - from) * step / 8 }] })
              await page.evaluate(() => new Promise(requestAnimationFrame))
            }
            // Hold at the final point before lifting, so the next tap is an action
            // rather than the gesture that stops momentum from a fast synthetic swipe.
            for (let hold = 0; hold < 6; hold++) {
              await session.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x, y: to }] })
              await page.evaluate(() => new Promise(requestAnimationFrame))
            }
            await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
            await page.waitForFunction(previous => document.querySelector('[data-age-gate-body]').scrollTop > previous, before)
            touchScroll = { before, after: await body.evaluate(node => node.scrollTop), input: 'emulated-touch-swipe' }
          } finally { await session.detach() }
        }
        let reachable = true
        for (const button of await page.locator('[data-age-gate-footer] button').all()) {
          await button.scrollIntoViewIfNeeded()
          reachable &&= await button.evaluate(node => {
            const r = node.getBoundingClientRect(), hit = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2)
            return r.y >= -1 && r.bottom <= visualViewport.height + 1 && (node === hit || node.contains(hit))
          })
        }
        if (!baseline) assert.ok(reachable, name + ': every footer button is reachable')
        if (config.customize) {
          for (const name of ['Funcionais', 'Analytics', 'Marketing']) {
            const option = page.getByRole('switch', { name, exact: true })
            if (baseline && initial.body.clientHeight < 48) break
            await option.scrollIntoViewIfNeeded(); await option.tap()
            await page.waitForFunction(label => [...document.querySelectorAll('[role="switch"]')]
              .some(node => node.getAttribute('aria-label') === label && node.getAttribute('aria-checked') === 'true'), name)
            assert.equal(await option.getAttribute('aria-checked'), 'true')
          }
          await page.screenshot({ path: path.join(evidence, name + '-options-scrolled.png'), fullPage: true })
        }
        await page.locator('[data-age-gate-footer] button').last().focus()
        for (let tab = 0; tab < 8; tab++) {
          await page.keyboard.press('Tab')
          assert.equal(await page.evaluate(() => document.querySelector('[data-age-gate-layout]').contains(document.activeElement)), true, name + ': focus cycles inside dialog')
        }
        if (config.error) assert.equal(await page.evaluate(() => document.cookie.includes('cookie_consent=')), false)
        if (!config.customize && !config.error) {
          const accept = page.getByRole('button', { name: config.chosen ? 'Aceitar' : 'Entrar somente com os necessários', exact: true })
          await accept.click()
          await page.locator('[data-age-gate-layout]').waitFor({ state: 'hidden' })
          assert.equal(ageCalls, config.age ? 0 : 1)
          const saved = await page.evaluate(() => JSON.parse(decodeURIComponent(document.cookie.match(/(?:^|;\s*)cookie_consent=([^;]*)/)[1])))
          assert.equal(saved.analytics, false); assert.equal(saved.marketing, false); assert.equal(saved.functional, false)
          if (config.chosen) assert.equal(saved.ts, 17)
          await page.reload()
          await page.locator('[data-fixture-status-committed="true"]').waitFor({ state: 'attached' })
          assert.equal(await page.locator('[data-age-gate-layout]').count(), 0, name + ': resolved choices do not reopen')
        }
        results.push({ name, initial, reachable, touchScroll, ageCalls, errorVisible: config.error || false,
          desktop: config.desktop || false, screenshotSha256: createHash('sha256').update(fs.readFileSync(path.join(evidence, name + '.png'))).digest('hex') })
      } catch (error) {
        await page.screenshot({ path: path.join(evidence, name + '-failure.png'), fullPage: true })
        throw error
      } finally {
        await closeResource('context:' + name, () => context.close(), () => { contexts.delete(context); cleanup.contextsClosed++ })
      }
    }
    await scenario('mobile-cookies-only', { age: true })
    await scenario('mobile-both', { longNotice: true })
    await scenario('mobile-customize-short', { age: true, customize: true, width: 360, height: 480 })
    await scenario('mobile-both-customize-tiny', { longNotice: true, customize: true, width: 320, height: 320 })
    await scenario('mobile-error', { error: true, width: 360, height: 480 })
    await scenario('mobile-age-only', { chosen: true })
    await scenario('mobile-resolved-refusal', { age: true, chosen: true })
    await scenario('desktop-cookies-only', { age: true, desktop: true, width: 1366, height: 900 })
    await scenario('desktop-both', { longNotice: true, desktop: true, width: 1366, height: 900 })
    assert.deepEqual(unexpected, []); assert.deepEqual(browserErrors, [])
  } catch (error) { failure = error }
  finally {
    for (const context of contexts) await closeResource('remaining-context', () => context.close(), () => { contexts.delete(context); cleanup.contextsClosed++ })
    if (browser) await closeResource('browser', () => browser.close(), () => { cleanup.browserClosed = true })
    if (server) await closeResource('server', () => new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve())), () => { cleanup.serverClosed = true })
    if (compiler) await closeResource('compiler', () => new Promise((resolve, reject) => compiler.close(error => error ? reject(error) : resolve())), () => { cleanup.compilerClosed = true })
    if (!failure && cleanupErrors.length) failure = Error('Browser regression cleanup failed; see result.json')
    write('result.json', JSON.stringify({ passed: !failure, baseline, modalSha256: createHash('sha256').update(modal).digest('hex'),
      mode: baseline ? 'baseline-capture-with-known-layout-defects' : 'regression',
      browser: browser?.version(), syntheticTransport: true, realAndroid: false, results, unexpected, browserErrors,
      cleanup, cleanupErrors, failure: failure?.stack }, null, 2))
  }
  console.log('AGE_GATE_MOBILE_BROWSER_EVIDENCE=' + evidence)
  if (failure) throw failure
  console.log('AGE_GATE_MOBILE_BROWSER_RESULT=OK cases=' + results.length)
}
