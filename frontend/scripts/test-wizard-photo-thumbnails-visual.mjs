import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

// Render the real shared picker with synthetic files. The actual wizard and
// its creation/upload recovery remain covered by test-photo-upload-validation.
const require = createRequire(import.meta.url)
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = path.join(root, 'src')
const evidence = fs.mkdtempSync(path.join(process.env.TOPS_UI_EVIDENCE_ROOT || os.tmpdir(), 'wizard-photo-thumbnails-'))
const write = (name, value) => fs.writeFileSync(path.join(evidence, name), value, { flag: 'wx' })
const { chromium } = require(process.env.TOPS_PLAYWRIGHT_MODULE || 'playwright')
const sharp = require('sharp')
const images = await Promise.all([
  sharp({ create: { width: 240, height: 360, channels: 3, background: '#8d596e' } }).png().toBuffer(),
  sharp({ create: { width: 360, height: 240, channels: 3, background: '#447d9c' } }).png().toBuffer(),
])
const compiled = require('next/dist/compiled/webpack/webpack')
compiled.init()
const { webpack } = compiled
const postcss = require('postcss')
const tailwind = require('@tailwindcss/postcss')
const results = [], unexpected = [], browserErrors = [], cleanupErrors = []
let compiler, server, browser, failure
const contexts = new Set()
const cleanup = { contextsClosed: 0, browserClosed: false, serverClosed: false, compilerClosed: false }

async function closeResource(label, operation, success) {
  let timer
  try {
    await Promise.race([operation(), new Promise((_, reject) => {
      timer = setTimeout(() => reject(Error('Cleanup timeout: ' + label)), 10000)
    })])
    success()
  } catch (error) { cleanupErrors.push({ resource: label, error: String(error) }) }
  finally { clearTimeout(timer) }
}

write('loader.cjs', `const ts=require(${JSON.stringify(require.resolve('typescript'))});module.exports=function(source){return ts.transpileModule(source,{fileName:this.resourcePath,compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext,jsx:ts.JsxEmit.ReactJSX}}).outputText}`)
write('entry.tsx', `
import React,{useEffect,useState} from 'react';
import{createRoot}from'react-dom/client';
import{FilePicker}from ${JSON.stringify(path.join(source, 'components/forms/file-picker.tsx'))};
const bytes=value=>Uint8Array.from(atob(value),part=>part.charCodeAt(0));
const first=new File([bytes(${JSON.stringify(images[0].toString('base64'))})],'mesmo-nome.png',{type:'image/png'});
const second=new File([bytes(${JSON.stringify(images[1].toString('base64'))})],'mesmo-nome.png',{type:'image/png'});
function App(){
 const[files,setFiles]=useState([first,second]),[entries,setEntries]=useState([]),[step,setStep]=useState('photos');
 useEffect(()=>{const next=files.map(file=>({file,url:URL.createObjectURL(file)}));setEntries(next);return()=>next.forEach(({url})=>URL.revokeObjectURL(url));},[files]);
 const urls=files.map((file,index)=>entries[index]?.file===file?entries[index].url:undefined);
 const before=location.search.includes('before');
 return <main className="mx-auto max-w-xl p-4"><h1 className="mb-4 text-xl font-semibold">Fotos do anúncio</h1>{step==='photos'?<><FilePicker ariaLabel="Selecionar fotos do anúncio" buttonLabel="Selecionar fotos" accept="image/*" multiple files={files} previewUrls={before?undefined:urls} onSelect={selected=>setFiles(current=>[...current,...selected])} onRemove={index=>setFiles(current=>current.filter((_,i)=>i!==index))}/><button onClick={()=>setStep('review')}>Continuar</button></>:<button onClick={()=>setStep('photos')}>Voltar às fotos</button>}<output>Seleção local; nenhum upload.</output></main>;
}
createRoot(document.getElementById('root')).render(<App/>);
`)

try {
  const cssPath = path.join(source, 'app/globals.css')
  const css = await postcss([tailwind({ base: root })]).process(fs.readFileSync(cssPath, 'utf8'), { from: cssPath })
  compiler = webpack({ mode: 'development', target: 'web', devtool: false, context: root,
    entry: path.join(evidence, 'entry.tsx'), output: { path: evidence, filename: 'bundle.js' },
    resolve: { extensions: ['.tsx', '.ts', '.js'], modules: [path.join(root, 'node_modules')], alias: { '@': source } },
    module: { rules: [{ test: /\.[jt]sx?$/, exclude: /node_modules/, use: path.join(evidence, 'loader.cjs') }] },
    plugins: [new webpack.DefinePlugin({ 'process.env': JSON.stringify({ NODE_ENV: 'development' }) })],
  })
  const stats = await new Promise((resolve, reject) => compiler.run((error, value) => error ? reject(error) : resolve(value)))
  assert.equal(stats.hasErrors(), false, stats.toString({ all: false, errors: true }))
  const bundle = fs.readFileSync(path.join(evidence, 'bundle.js'))
  server = http.createServer((request, response) => {
    const pathname = new URL(request.url, 'http://synthetic.invalid').pathname
    const routes = {
      '/': ['text/html; charset=utf-8', '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/styles.css"></head><body><div id="root"></div><script src="/bundle.js"></script></body></html>'],
      '/styles.css': ['text/css', css.css], '/bundle.js': ['text/javascript', bundle],
    }
    if (pathname === '/favicon.ico') { response.writeHead(204); response.end(); return }
    const route = routes[pathname]
    if (!route) { unexpected.push({ method: request.method, pathname }); response.writeHead(404); response.end(); return }
    response.writeHead(200, { 'Content-Type': route[0] }); response.end(route[1])
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  browser = await chromium.launch({ headless: true, ...(process.env.TOPS_CHROMIUM_BIN ? { executablePath: process.env.TOPS_CHROMIUM_BIN } : {}) })
  for (const [label, viewport, touch] of [
    ['before-mobile', { width: 375, height: 812 }, true],
    ['after-mobile', { width: 375, height: 812 }, true],
    ['after-desktop', { width: 1200, height: 850 }, false],
  ]) {
    const context = await browser.newContext({ viewport, hasTouch: touch, isMobile: touch })
    contexts.add(context)
    await context.route('**/*', async (route) => {
      const url = new URL(route.request().url())
      if (url.origin === origin || ['blob:', 'data:'].includes(url.protocol)) return route.continue()
      unexpected.push({ method: route.request().method(), external: true }); await route.abort()
    })
    const page = await context.newPage()
    page.on('pageerror', (error) => browserErrors.push(String(error)))
    await page.goto(`${origin}/?variant=${label}`)
    await page.getByRole('button', { name: 'Selecionar fotos do anúncio' }).waitFor()
    const before = label.startsWith('before')
    await page.waitForFunction((expected) => {
      const images = [...document.querySelectorAll('img')]
      return images.length === expected && images.every((image) => image.complete && image.naturalWidth > 0)
    }, before ? 0 : 2)
    const measure = await page.evaluate(() => ({
      viewport: innerWidth, scrollWidth: document.documentElement.scrollWidth,
      images: [...document.querySelectorAll('img')].map((image) => ({
        src: image.src, naturalWidth: image.naturalWidth,
        width: image.getBoundingClientRect().width, height: image.getBoundingClientRect().height,
        fit: getComputedStyle(image).objectFit,
      })),
    }))
    assert.equal(measure.viewport, viewport.width)
    assert.ok(measure.scrollWidth <= measure.viewport, label + ': sem overflow horizontal')
    if (!before) {
      assert.deepEqual(measure.images.map((image) => image.naturalWidth), [240, 360])
      assert.ok(measure.images.every((image) => image.width === 88 && image.height === 88 && image.fit === 'contain'))
    }
    await page.screenshot({ path: path.join(evidence, `${label}.png`), fullPage: true })
    if (label === 'after-mobile') {
      await page.getByRole('button', { name: 'Continuar', exact: true }).tap()
      await page.getByRole('button', { name: 'Voltar às fotos', exact: true }).tap()
      assert.deepEqual(await page.locator('img').evaluateAll((items) => items.map((item) => item.src)), measure.images.map((image) => image.src))
      await page.getByRole('button', { name: 'Remover foto 1: mesmo-nome.png', exact: true }).tap()
      await page.waitForFunction(() => document.querySelectorAll('img').length === 1 && document.querySelector('img').naturalWidth === 360)
      await page.locator('input[type=file]').setInputFiles({ name: 'mesmo-nome.png', mimeType: 'image/png', buffer: images[0] })
      await page.waitForFunction(() => [...document.querySelectorAll('img')].map((image) => image.naturalWidth).join(',') === '360,240')
    }
    results.push({ label, viewport, touch, ...measure, passed: true })
    await closeResource('context ' + label, () => context.close(), () => { contexts.delete(context); cleanup.contextsClosed += 1 })
  }
  assert.deepEqual(unexpected, [])
  assert.deepEqual(browserErrors, [])
} catch (error) { failure = error }
finally {
  for (const context of contexts) await closeResource('remaining context', () => context.close(), () => { cleanup.contextsClosed += 1 })
  if (browser) await closeResource('browser', () => browser.close(), () => { cleanup.browserClosed = true })
  if (server) await closeResource('server', () => new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())), () => { cleanup.serverClosed = true })
  if (compiler) await closeResource('compiler', () => new Promise((resolve, reject) => compiler.close((error) => error ? reject(error) : resolve())), () => { cleanup.compilerClosed = true })
  write('results.json', JSON.stringify({ passed: !failure && !cleanupErrors.length, node: process.version, results, unexpected, browserErrors, cleanup, cleanupErrors, limitations: ['Shared picker browser fixture; actual wizard recovery tested by hook harness.', 'Mobile touch emulation, not Android hardware.'], error: failure ? String(failure) : null }, null, 2))
}
if (failure) throw failure
assert.deepEqual(cleanupErrors, [])
console.log(JSON.stringify({ result: 'OK', evidence, cleanup, scenarios: results.length }))
