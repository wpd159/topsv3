import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

// Exercise the real edit-ad photo step and file picker with an in-browser
// recording. The transport and canonical owner response are synthetic.
const require = createRequire(import.meta.url)
const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = path.join(frontend, 'src')
const evidence = fs.mkdtempSync(path.join(process.env.TOPS_UI_EVIDENCE_ROOT || os.tmpdir(), 'owner-video-local-preview-'))
const write = (name, value) => fs.writeFileSync(path.join(evidence, name), value, { flag: 'wx' })
const { chromium } = require(process.env.TOPS_PLAYWRIGHT_MODULE || 'playwright')
const compiled = require('next/dist/compiled/webpack/webpack')
compiled.init()
const { webpack } = compiled
const postcss = require('postcss')
const tailwind = require('@tailwindcss/postcss')
const unexpected = [], browserErrors = [], cleanupErrors = []
let compiler, server, browser, context, failure, observation

async function closeResource(label, operation) {
  let timer
  try {
    await Promise.race([operation(), new Promise((_, reject) => {
      timer = setTimeout(() => reject(Error(`Cleanup timeout: ${label}`)), 10000)
    })])
  } catch (error) { cleanupErrors.push({ resource: label, error: String(error) }) }
  finally { clearTimeout(timer) }
}

write('loader.cjs', `const ts=require(${JSON.stringify(require.resolve('typescript'))});module.exports=function(source){return ts.transpileModule(source,{fileName:this.resourcePath,compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext,jsx:ts.JsxEmit.ReactJSX}}).outputText}`)
write('server-actions.js', 'export const revalidarCacheCatalogoPublico=async()=>{throw Error("Unexpected catalog mutation")};')
write('entry.tsx', `
import React,{useState} from 'react';
import{createRoot}from'react-dom/client';
import{WizardStepFotos}from ${JSON.stringify(path.join(source, 'features/anuncio-wizard/components/wizard-step-fotos.tsx'))};
const persisted={
 anuncio:{id:'synthetic-owner-ad',slug:'synthetic-owner-ad',status:'PENDENTE_REVISAO'},
 fotosValidasAtivasTotal:0,midias:[],
 limites:{maxFotos:4,fotosAtivas:0,fotosDisponiveis:4,maxVideos:1,videosAtivos:0,videosDisponiveis:1,fotosExtrasAtivo:false,videoAtivo:true,maxFotoBytes:20971520,maxVideoBytes:104857600}
};
function App(){
 const[pending,setPending]=useState([]);
 return <main className="mx-auto max-w-2xl p-4"><h1 className="mb-4 text-xl font-semibold">Editar anúncio sintético</h1><WizardStepFotos slug="synthetic-owner-ad" actorId="synthetic-owner" initialFiles={[]} fotoNomes={[]} onChange={()=>{}} videosNovos={[]} onChangeVideosNovos={()=>{}} persistedState={persisted} pendingFiles={pending} onPendingFilesChange={setPending}/></main>;
}
createRoot(document.getElementById('root')).render(<App/>);
`)

try {
  const cssPath = path.join(source, 'app/globals.css')
  const css = await postcss([tailwind({ base: frontend })]).process(fs.readFileSync(cssPath, 'utf8'), { from: cssPath })
  compiler = webpack({ mode: 'development', target: 'web', devtool: false, context: frontend,
    entry: path.join(evidence, 'entry.tsx'), output: { path: evidence, filename: 'bundle.js' },
    resolve: { extensions: ['.tsx', '.ts', '.js'], modules: [path.join(frontend, 'node_modules')], alias: {
      '@/app/(painel-admin)/admin/anuncios/actions$': path.join(evidence, 'server-actions.js'), '@': source,
    } },
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
      '/styles.css': ['text/css; charset=utf-8', css.css], '/bundle.js': ['text/javascript; charset=utf-8', bundle],
    }
    if (pathname === '/favicon.ico') { response.writeHead(204); response.end(); return }
    const route = routes[pathname]
    if (!route) { unexpected.push({ method: request.method, pathname }); response.writeHead(404); response.end(); return }
    response.writeHead(200, { 'Content-Type': route[0] }); response.end(route[1])
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  browser = await chromium.launch({ headless: true, ...(process.env.TOPS_CHROMIUM_BIN ? { executablePath: process.env.TOPS_CHROMIUM_BIN } : {}) })
  context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true, serviceWorkers: 'block' })
  await context.route('**/*', async (route) => {
    const url = new URL(route.request().url())
    if (url.origin === origin || ['blob:', 'data:'].includes(url.protocol)) return route.continue()
    unexpected.push({ method: route.request().method(), external: true }); await route.abort()
  })
  const page = await context.newPage()
  page.on('pageerror', (error) => browserErrors.push(String(error)))
  await page.goto(origin)
  await page.getByRole('button', { name: 'Selecionar vídeo do anúncio' }).waitFor()
  const recording = await page.evaluate(async () => {
    const canvas = document.createElement('canvas')
    canvas.width = 320; canvas.height = 180
    const context = canvas.getContext('2d')
    const draw = (frame) => {
      context.fillStyle = '#17112e'; context.fillRect(0, 0, 320, 180)
      context.fillStyle = '#ec4899'; context.fillRect(22 + frame * 7, 35, 80, 80)
      context.fillStyle = '#ffffff'; context.font = 'bold 24px sans-serif'
      context.fillText('VIDEO LOCAL', 100, 100)
    }
    draw(0)
    const stream = canvas.captureStream(15)
    const mimeType = ['video/webm;codecs=vp8', 'video/webm'].find((type) => MediaRecorder.isTypeSupported(type))
    if (!mimeType) throw Error('MediaRecorder does not support WebM in this browser')
    const chunks = []
    const recorder = new MediaRecorder(stream, { mimeType })
    recorder.addEventListener('dataavailable', (event) => { if (event.data.size) chunks.push(event.data) })
    const finished = new Promise((resolve, reject) => {
      recorder.addEventListener('stop', resolve, { once: true })
      recorder.addEventListener('error', reject, { once: true })
    })
    recorder.start()
    let frame = 0
    const ticker = setInterval(() => draw(++frame % 8), 40)
    await new Promise((resolve) => setTimeout(resolve, 650))
    clearInterval(ticker); recorder.stop(); await finished
    stream.getTracks().forEach((track) => track.stop())
    const blob = new Blob(chunks, { type: mimeType })
    // The edit picker accepts MP4/MOV. Chrome decodes these browser-generated
    // WebM bytes by container sniffing; no fixture or external media is used.
    const file = new File([blob], 'video-sintetico.mp4', { type: 'video/mp4' })
    const input = [...document.querySelectorAll('input[type=file]')].find((element) => element.accept.includes('video/mp4'))
    if (!input) throw Error('Owner video picker input missing')
    const transfer = new DataTransfer()
    transfer.items.add(file)
    input.files = transfer.files
    input.dispatchEvent(new Event('change', { bubbles: true }))
    return { recordingMimeType: mimeType, fileName: file.name, fileType: file.type, bytes: file.size }
  })
  assert.ok(recording.bytes > 0, 'Browser recording must contain bytes')
  const preview = page.locator('video[data-owner-video-local-preview]')
  await preview.waitFor()
  await page.waitForFunction(() => {
    const video = document.querySelector('video[data-owner-video-local-preview]')
    return video && video.videoWidth > 0 && video.videoHeight > 0 && video.readyState >= 2
  })
  observation = await preview.evaluate((video) => ({
    videoWidth: video.videoWidth, videoHeight: video.videoHeight,
    paused: video.paused, autoplay: video.autoplay, controls: video.controls,
    playsInline: video.playsInline, readyState: video.readyState,
    currentTime: video.currentTime, blobUrl: video.src.startsWith('blob:'),
  }))
  assert.ok(observation.videoWidth > 0 && observation.videoHeight > 0)
  assert.equal(observation.paused, true, 'Preview must not play automatically')
  assert.equal(observation.autoplay, false)
  assert.equal(observation.controls, true)
  assert.equal(observation.playsInline, true)
  assert.equal(observation.blobUrl, true)
  assert.equal(await page.getByText('video-sintetico.mp4: Arquivo pronto para envio.').count(), 1)
  assert.deepEqual(unexpected, [])
  assert.deepEqual(browserErrors, [])
  await page.screenshot({ path: path.join(evidence, 'owner-video-mobile.png'), fullPage: true })
  write('recording.json', JSON.stringify(recording, null, 2))
} catch (error) { failure = error }
finally {
  if (context) await closeResource('context', () => context.close())
  if (browser) await closeResource('browser', () => browser.close())
  if (server) await closeResource('server', () => new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())))
  if (compiler) await closeResource('compiler', () => new Promise((resolve, reject) => compiler.close((error) => error ? reject(error) : resolve())))
  write('results.json', JSON.stringify({ passed: !failure && !cleanupErrors.length, observation, unexpected, browserErrors, cleanupErrors, screenshot: 'owner-video-mobile.png', error: failure ? String(failure) : null }, null, 2))
}
if (failure) throw failure
assert.deepEqual(cleanupErrors, [])
console.log(JSON.stringify({ result: 'OK', evidence, ...observation }))
