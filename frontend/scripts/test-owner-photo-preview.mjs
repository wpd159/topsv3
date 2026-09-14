import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

// Real React, owner components, Next Image and Next's optimizer parameter
// validator. Only unrelated providers and transport use synthetic fixtures.
// This proves rendering/refresh, not backend ownership authorization or R2.
const require = createRequire(import.meta.url)
const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = path.join(frontend, 'src')
// Next resolves tsconfig paths relative to the calling working directory.
process.chdir(frontend)
const evidence = fs.mkdtempSync(path.join(process.env.TOPS_UI_EVIDENCE_ROOT || os.tmpdir(), 'owner-photo-dom-'))
const write = (name, bytes) => fs.writeFileSync(path.join(evidence, name), bytes, { flag: 'wx' })
const digest = (bytes) => createHash('sha256').update(bytes).digest('hex')
const { chromium } = require(process.env.TOPS_PLAYWRIGHT_MODULE || 'playwright')
const sharp = require('sharp')
const { ImageOptimizerCache } = require('next/dist/server/image-optimizer')
const { PHASE_PRODUCTION_BUILD } = require('next/constants')
const loadConfig = require('next/dist/server/config').default
const bundledWebpack = require('next/dist/compiled/webpack/webpack')
bundledWebpack.init()
const { webpack } = bundledWebpack
const config = await loadConfig(PHASE_PRODUCTION_BUILD, frontend)
const baseRef = process.env.TOPS_OWNER_BASE_REF
const baseFiles = [
  'src/components/anuncios/anuncio-card.tsx',
  'src/components/anuncios/meu-anuncio-card.tsx',
  'src/components/compliance/sensitive-image.tsx',
  'src/features/anuncio-wizard/components/wizard-preview.tsx',
]
const readBase = (name) => execFileSync('git', ['show', `${baseRef}:frontend/${name}`], { cwd: frontend, encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 })
const inputs = [...baseFiles, 'src/components/anuncios/imagem-proprietario.tsx', 'next.config.ts', 'src/features/anuncio-wizard/anuncio-wizard.tsx', 'src/features/anuncio-wizard/components/wizard-step-fotos.tsx', 'src/lib/meus-anuncios-api.ts', 'src/lib/public-catalog-api.ts', 'src/lib/media/public-media.ts', 'src/lib/compliance/visitor-access.ts', 'scripts/test-owner-photo-preview.mjs', 'package-lock.json']
const inputHashes = Object.fromEntries(inputs.map((name) => [name, digest(fs.readFileSync(path.join(frontend, name)))]))
write('inputs-before.json', JSON.stringify(inputHashes, null, 2))
const privateOrigin = 'https://00000000000000000000000000000000.r2.cloudflarestorage.com'
const privateUrl = (photo = 'a', generation = 1) => `${privateOrigin}/synthetic-owner/photo-${photo}.png?X-Amz-Expires=300&X-Amz-${'Signature'}=EXEMPLO_NAO_REAL_${generation}`
const expiry = () => new Date(Date.now() + 300000).toISOString()
const lifecycle = { id: 'owner-ad', slug: 'anuncio-sintetico', status: 'PENDENTE_REVISAO', statusModeracao: 'PENDENTE', atualizadoEm: '2026-09-13T12:00:00Z', acoesPermitidas: { pausar: false, reativar: false, remover: true, corrigirEReenviar: false } }
const media = (url = privateUrl(), expiresAt = expiry()) => ({
  anuncio: lifecycle, fotosValidasAtivasTotal: 1,
  midias: [{ id: 'owner-photo', tipo: 'FOTO', ordem: 0, status: 'PENDENTE', visibilidadeMidia: 'RESTRITA_18', previewUrl: url, previewExpiraEm: expiresAt, restrita: true, ocultaPorLimite: false }],
  limites: { maxFotos: 4, fotosAtivas: 1, fotosDisponiveis: 3, maxVideos: 0, videosAtivos: 0, videosDisponiveis: 0, fotosExtrasAtivo: false, videoAtivo: false, maxFotoBytes: 20971520, maxVideoBytes: 104857600 },
})
const advertisement = (url = privateUrl(), expiresAt = expiry(), before = false) => ({
  ...lifecycle, titulo: 'Perfil de demonstração', descricao: 'Descrição sintética para conferir as fotos privadas do proprietário.', categoria: 'MASSAGENS', preco: 100,
  whatsapp: null, linkConteudo: null, locaisAtendimento: ['A_COMBINAR'], servicos: ['MASSAGEM_TANTRICA'], atendimentoExclusivamenteVirtual: false,
  localizacao: { uf: 'SP', cidade: 'Cidade exemplo', cidadeSlug: 'cidade-exemplo', bairro: 'Centro', bairroSlug: 'centro', enderecoResumido: null },
  capa: before ? null : { urlPublica: null, restrita: true, previewUrl: url, previewExpiraEm: expiresAt },
  midias: [{ id: 'owner-photo', tipo: 'FOTO', finalidade: 'CAPA', ordem: 0, status: 'PENDENTE', visibilidadeMidia: 'RESTRITA_18', urlPublica: null, restrita: true }],
  visualizacoes: { total: 0, situacao: 'ZERO_LEGITIMO' }, reprovacao: null, beneficiosPremium: [], storyAtivo: null,
})
const results = [], requests = [], unexpected = [], browserErrors = [], cleanupErrors = []
let browser, server, activeContext, failure
const bundles = new Map()
const compilers = []
const safeUrl = (value) => {
  if (value?.startsWith('blob:')) return 'blob:[synthetic]'
  const url = new URL(value, 'http://fixture.invalid')
  if (url.pathname === '/_next/image') return `${url.pathname}?url=${encodeURIComponent(safeUrl(url.searchParams.get('url')))}&w=${url.searchParams.get('w')}`
  return url.origin === privateOrigin ? `${url.origin}${url.pathname}?[synthetic-signature-redacted]` : url.pathname
}
const bounded = (promise, name) => {
  let timer
  return Promise.race([promise, new Promise((_, reject) => { timer = setTimeout(() => reject(Error(`Cleanup timeout: ${name}`)), 10000) })]).finally(() => clearTimeout(timer))
}
const waitFor = async (predicate, name) => {
  const deadline = performance.now() + 10000
  while (!predicate()) {
    assert.ok(performance.now() < deadline, `Observation timeout: ${name}`)
    await new Promise((resolve) => setTimeout(resolve, 20))
  }
}

write('navigation.js', 'export const useRouter=()=>({push:()=>{throw Error("Unexpected navigation")}});export const usePathname=()=>location.pathname;')
write('auth.js', "const value={usuario:{id:'synthetic-owner',dataNascimento:'1990-01-01'},carregando:false,refresh:async()=>{}};export const useAuth=()=>window.__authFixture??value;")
write('favorites.js', 'export const useFavoritos=()=>({isFavorito:()=>false,isPendente:()=>false,alternar:()=>{throw Error("Unexpected favorite mutation")}});')
write('toast.js', 'export const toast={success(){},error(){},warning(){}};')
write('localidades.js', 'const value={estados:[],cidades:[],bairros:[],loadCidades:async()=>{},loadBairros:async()=>{},setCidades(){},setBairros(){}};export const useLocalidades=()=>value;')
write('server-actions.js', 'export const revalidarCacheCatalogoPublico=async()=>{throw Error("Unexpected catalog mutation")};')
write('entry.tsx', `
import React,{useState,useEffect} from 'react';
import {createRoot} from 'react-dom/client';
import {flushSync} from 'react-dom';
import {WizardPreview} from ${JSON.stringify(path.join(source, 'features/anuncio-wizard/components/wizard-preview.tsx'))};
import {WizardStepFotos} from ${JSON.stringify(path.join(source, 'features/anuncio-wizard/components/wizard-step-fotos.tsx'))};
import AnuncioWizard from ${JSON.stringify(path.join(source, 'features/anuncio-wizard/anuncio-wizard.tsx'))};
import {MeuAnuncioCard} from ${JSON.stringify(path.join(source, 'components/anuncios/meu-anuncio-card.tsx'))};
import {AnuncioCard} from ${JSON.stringify(path.join(source, 'components/anuncios/anuncio-card.tsx'))};
import {buscarMeuAnuncio,listarMinhasMidias} from ${JSON.stringify(path.join(source, 'lib/meus-anuncios-api.ts'))};
import {obterAnuncioPublicoPorSlug} from ${JSON.stringify(path.join(source, 'lib/public-catalog-api.ts'))};
import {WhatsAppSafetyProvider} from ${JSON.stringify(path.join(source, 'components/site/whatsapp-safety-provider.tsx'))};
let root,setConfig;
function App({initial}){
 const [config,update]=useState(initial);setConfig=update;
 const [files,setFiles]=useState([]),[urls,setUrls]=useState([]),[open,setOpen]=useState(true);
 useEffect(()=>{const next=files.map(file=>URL.createObjectURL(file));setUrls(next);return()=>next.forEach(url=>URL.revokeObjectURL(url));},[files]);
 if(config.mode==='editor')return <AnuncioWizard key={config.generation} mode="edit" slug="anuncio-sintetico"/>;
 if(config.mode==='card')return <main id="owner-card"><MeuAnuncioCard anuncio={config.advertisement} onCicloVida={()=>{throw Error('Unexpected lifecycle mutation')}} onStoryOpen={()=>{throw Error('Unexpected story mutation')}}/></main>;
 if(config.mode==='public')return <main id="public-card"><AnuncioCard id="owner-ad" slug="anuncio-sintetico" nome="Perfil de demonstração" valor="R$ 100" previewImagens={[config.url]} midias={[]} previewMode={false}/></main>;
 const previewMedia=config.mode==='selection'?urls:[config.url];
 return <><div id="selection">{config.mode==='selection'&&<WizardStepFotos initialFiles={files} fotoNomes={files.map(file=>file.name)} onChange={setFiles} videosNovos={[]} onChangeVideosNovos={()=>{}}/>}</div><button id="open-preview" onClick={()=>setOpen(true)}>Abrir prévia</button><WizardPreview showMobile={false} showDesktop={config.mode==='preview'} renderDialog open={open} onOpenChange={setOpen} onOpenRequest={()=>setOpen(true)} highlightedPreview quietPreview={false} previewHintActive={false} previewTitle="Perfil de demonstração" previewPrice="R$ 100" previewDescription="Prévia das fotos privadas do proprietário." previewMedia={previewMedia} previewReference="" idade={30} hasVirtual={false} hasExistingKyc premiumChoice="gratis" estadoUf="SP" cidadeNome="Cidade exemplo" bairroNome="Centro"/></>;
}
window.__control={mount(config){window.__authFixture={usuario:config.actor==='anonymous'?null:{id:config.actor==='other'?'synthetic-other':'synthetic-owner',dataNascimento:'1990-01-01'},carregando:false,refresh:async()=>{}};root=createRoot(document.getElementById('root'));flushSync(()=>root.render(<WhatsAppSafetyProvider><App initial={config}/></WhatsAppSafetyProvider>));},update(patch){flushSync(()=>setConfig(current=>({...current,...patch})));},lifecycle(){flushSync(()=>setConfig(current=>({...current,advertisement:{...current.advertisement,status:'PAUSADO'}})));},async managementLookup(){return (await Promise.allSettled([buscarMeuAnuncio('anuncio-sintetico'),listarMinhasMidias('anuncio-sintetico')])).map(result=>result.status==='rejected'?{rejected:true,status:result.reason.status}:{rejected:false});},async publicLookup(){try{await obterAnuncioPublicoPorSlug('anuncio-sintetico');return {rejected:false};}catch(error){return {rejected:true,status:error.status};}},unmount(){if(root){flushSync(()=>root.unmount());root=null;}}};window.__ready=true;
`)

async function compile(variant) {
  const overrides = variant === 'before' ? Object.fromEntries(baseFiles.map((name) => [path.join(frontend, name), readBase(name)])) : {}
  write(`${variant}-overrides.json`, JSON.stringify(overrides))
  write(`${variant}-loader.cjs`, `const fs=require('node:fs');const overrides=JSON.parse(fs.readFileSync(${JSON.stringify(path.join(evidence, `${variant}-overrides.json`))},'utf8'));const ts=require(${JSON.stringify(require.resolve('typescript'))});module.exports=function(source){return ts.transpileModule(overrides[this.resourcePath]??source,{fileName:this.resourcePath,compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext,jsx:ts.JsxEmit.ReactJSX}}).outputText}`)
  const compiler = webpack({
    mode: 'none', target: 'web', devtool: false, context: frontend, entry: path.join(evidence, 'entry.tsx'),
    output: { path: evidence, filename: `${variant}.js` },
    resolve: { extensions: ['.tsx', '.ts', '.jsx', '.js'], modules: [path.join(frontend, 'node_modules')], alias: {
      'next/navigation$': path.join(evidence, 'navigation.js'), '@/context/AuthContext$': path.join(evidence, 'auth.js'),
      '@/context/FavoritosContext$': path.join(evidence, 'favorites.js'), '@/hooks/useLocalidades$': path.join(evidence, 'localidades.js'),
      '@/app/(painel-admin)/admin/anuncios/actions$': path.join(evidence, 'server-actions.js'), sonner: path.join(evidence, 'toast.js'), '@': source,
    } },
    module: { rules: [{ test: /\.[jt]sx?$/, exclude: /node_modules/, use: path.join(evidence, `${variant}-loader.cjs`) }] },
    plugins: [new webpack.DefinePlugin({ 'process.env': JSON.stringify({
      NODE_ENV: 'production', NEXT_PUBLIC_API_URL: '/api/public', NEXT_PUBLIC_SITE_URL: 'http://fixture.invalid',
      __NEXT_IMAGE_OPTS: config.images,
    }) })],
  })
  compilers.push(compiler)
  const stats = await new Promise((resolve, reject) => compiler.run((error, value) => error ? reject(error) : resolve(value)))
  write(`${variant}-webpack.json`, JSON.stringify(stats.toJson({ all: false, errors: true, warnings: true }), null, 2))
  assert.equal(stats.hasErrors(), false, stats.toString({ all: false, errors: true }))
  bundles.set(`/${variant}.js`, fs.readFileSync(path.join(evidence, `${variant}.js`)))
}

try {
  const syntheticImage = (width, height, color) => sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><rect width="100%" height="100%" fill="${color}"/><circle cx="${width / 2}" cy="${height / 3}" r="${width / 5}" fill="#fff" opacity=".8"/><path d="M0 ${height} L${width / 2} ${height / 2} L${width} ${height}Z" fill="#fff" opacity=".45"/></svg>`)).png().toBuffer()
  const imageA = await syntheticImage(480, 640, '#517789')
  const imageB = await syntheticImage(360, 480, '#B88258')
  const images = { a: imageA, b: imageB }
  write('synthetic-images.json', JSON.stringify({ a: { width: 480, height: 640, sha256: digest(imageA) }, b: { width: 360, height: 480, sha256: digest(imageB) } }))
  assert.equal(ImageOptimizerCache.validateParams({ headers: {} }, { url: privateUrl(), w: '640', q: '75' }, config, false).errorMessage, '"url" parameter is not allowed', 'Real optimizer must continue rejecting the private R2 account host.')
  if (baseRef) {
    assert.equal(readBase('next.config.ts').replace(/\r\n/g, '\n'), fs.readFileSync(path.join(frontend, 'next.config.ts'), 'utf8').replace(/\r\n/g, '\n'), 'Before/after must use the same actual Next config.')
    await compile('before')
  }
  await compile('after')
  const css = `body{font:14px system-ui;background:#f7f4ef;color:#20242a;margin:0;padding:24px}*{box-sizing:border-box}button{cursor:pointer}svg{width:20px;height:20px}p{line-height:1.5}article,.public-anuncio-card{width:330px;background:white;border:1px solid #ddd;border-radius:12px;overflow:hidden}article>div:first-child,.public-anuncio-card>div:first-child{position:relative;width:330px;height:440px;background:#eee}article>div+div,.public-anuncio-card>div+div{padding:16px}article>div:first-child>span{position:relative;z-index:2;display:inline-block;padding:5px;background:#fff8dd}article a{margin:8px}.public-anuncio-card>div:first-child>div:first-child{position:absolute;inset:0}.public-anuncio-card>div:first-child>div:first-child>div{height:100%;position:relative}.public-anuncio-card img,article img{object-fit:cover}.public-anuncio-card>div:first-child>div:not(:first-child){display:none}[data-slot=dialog-overlay]{position:fixed;inset:0;background:#0004}[role=dialog]{position:fixed;z-index:50;inset:24px;background:#f7f4ef;border-radius:24px;overflow:auto;padding:24px}[role=dialog] .public-anuncio-card{margin:20px auto}[role=dialog]>div>div:last-child{display:grid;grid-template-columns:360px 1fr;gap:30px}[role=dialog]>div>div:first-child{padding:5px 16px;border-bottom:1px solid #ddd}[role=dialog]h2{font-size:24px}[role=dialog]h3{font-size:16px}[role=dialog] ul{padding-left:20px}#owner-card{display:flex;justify-content:center}#selection{max-width:600px}#selection input{display:block}#root>div{max-width:1100px;margin:auto}img{max-width:100%}@media(max-width:700px){body{padding:12px}[role=dialog]{inset:8px;padding:12px}[role=dialog]>div>div:last-child{display:block}[role=dialog]>div>div:last-child>div+div{display:none}}`
  write('harness.css', css)
  server = http.createServer((request, response) => {
    const url = new URL(request.url, 'http://fixture.invalid')
    if (bundles.has(url.pathname)) { response.writeHead(200, { 'Content-Type': 'text/javascript' }); response.end(bundles.get(url.pathname)); return }
    if (url.pathname === '/' && ['before', 'after'].includes(url.searchParams.get('variant'))) {
      response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
      response.end(`<!doctype html><html><head><meta charset="utf-8"><style>${css}</style></head><body><div id="root"></div><script src="/${url.searchParams.get('variant')}.js"></script></body></html>`); return
    }
    if (url.pathname === '/_next/image') {
      const query = Object.fromEntries(url.searchParams)
      const validated = ImageOptimizerCache.validateParams(request, query, config, false)
      const entry = { boundary: 'real-next-validator', path: safeUrl(url.href), status: validated.errorMessage ? 400 : 200, error: validated.errorMessage ?? null }
      requests.push(entry)
      if (validated.errorMessage) { response.writeHead(400, { 'Content-Type': 'text/plain' }); response.end(validated.errorMessage); return }
      // Successful local placeholder bytes are synthetic; remote fetch is never implemented.
      if (query.url === '/icone-sem-foto.png') { response.writeHead(200, { 'Content-Type': 'image/png' }); response.end(imageB); return }
      unexpected.push(entry); response.writeHead(405); response.end(); return
    }
    if (url.pathname === '/favicon.ico') { response.writeHead(204); response.end(); return }
    unexpected.push({ boundary: 'server', method: request.method, path: safeUrl(url.href) }); response.writeHead(405); response.end()
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  const channel = process.env.TOPS_UI_BROWSER_CHANNEL
  browser = await chromium.launch({ headless: true, ...(channel && channel !== 'chromium' ? { channel } : {}) })
  write('runtime.json', JSON.stringify({ node: process.version, next: require('next/package.json').version, browser: browser.version(), realNextImage: true, realNextOptimizerValidator: true, realBackend: false, realR2: false, personalProfileUsed: false, beforeRef: baseRef ?? null, screenshotStyling: 'Fixed component harness CSS, not the full application layout' }))
  async function scenario(name, variant, configValue, action, viewport = { width: 1280, height: 900 }) {
    const start = requests.length
    const state = { mediaReads: 0, detailReads: 0, generation: 1, rejectGeneration: null, ...configValue.transport }
    const context = await browser.newContext({ serviceWorkers: 'block', viewport })
    activeContext = context
    const page = await context.newPage()
    page.setDefaultTimeout(10000)
    page.on('pageerror', (error) => browserErrors.push({ name, message: error.message }))
    await context.route('**/*', async (route) => {
      const request = route.request(), url = new URL(request.url())
      if (url.origin === privateOrigin && /^\/synthetic-owner\/photo-[ab]\.png$/.test(url.pathname)) {
        const denied = state.rejectGeneration !== null && url.searchParams.get('X-Amz-' + 'Signature') === `EXEMPLO_NAO_REAL_${state.rejectGeneration}`
        requests.push({ name, boundary: 'synthetic-private-image', path: safeUrl(url.href), generation: Number(url.searchParams.get('X-Amz-' + 'Signature')?.split('_').at(-1)), status: denied ? 403 : 200 })
        await route.fulfill({ status: denied ? 403 : 200, contentType: 'image/png', body: denied ? Buffer.from('Expired synthetic receipt') : images[url.pathname.includes('photo-b') ? 'b' : 'a'], headers: { 'cache-control': 'private, no-store' } }); return
      }
      if (url.origin !== origin) { unexpected.push({ name, boundary: 'browser', method: request.method(), path: safeUrl(url.href) }); await route.abort(); return }
      if (!url.pathname.startsWith('/api/')) { await route.continue(); return }
      const entry = { name, boundary: 'synthetic-api', method: request.method(), path: url.pathname }
      requests.push(entry)
      let body
      if (url.pathname.endsWith('/auth/me')) body = { id: 'synthetic-owner' }
      else if (url.pathname.endsWith('/minha-conta/kyc')) body = { prontoParaEnviarAnuncio: true, nomeCivil: 'Pessoa exemplo', cpfPreenchido: true, dataNascimento: '1990-01-01', documentos: [], status: 'APROVADO' }
      else if (url.pathname.endsWith('/categorias-home')) body = [{ identificador: 'MASSAGENS', titulo: 'Massagens', ativo: true }]
      else if (url.pathname.endsWith('/wizard-progress/sync')) body = { id: 'progress-synthetic', atualizadoEm: '2026-09-13T12:00:00Z' }
      else if (url.pathname.endsWith('/midias') && request.method() === 'GET') {
        state.mediaReads++
        if (state.mediaInitialStatus >= 400) {
          entry.status = state.mediaInitialStatus
          await route.fulfill({ status: state.mediaInitialStatus, contentType: 'application/json', body: JSON.stringify({ message: 'Acesso negado à fixture.' }) }); return
        }
        if (state.denyMediaRefresh && state.mediaReads > 1) {
          entry.status = 403
          await route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ message: 'Acesso negado à fixture.' }) }); return
        }
        const expiredFirst = state.expiredInitialMedia && state.mediaReads === 1
        body = media(privateUrl('a', expiredFirst ? 1 : state.generation), expiredFirst ? '2020-01-01T00:00:00Z' : expiry())
      }
      else if (url.pathname === '/api/public/minha-conta/anuncios/anuncio-sintetico' && request.method() === 'GET') {
        state.detailReads++
        if (state.detailStatus >= 400) {
          entry.status = state.detailStatus
          await route.fulfill({ status: state.detailStatus, contentType: 'application/json', body: JSON.stringify({ message: 'Acesso negado à fixture.' }) }); return
        }
        if (state.recoverImageOnDetail) state.rejectGeneration = null
        body = advertisement(privateUrl('a', state.generation), state.fixedExpiry ?? expiry())
      }
      else if (url.pathname === '/api/public/anuncios/anuncio-sintetico' && request.method() === 'GET') {
        entry.status = 404
        await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: 'Anúncio indisponível na fixture pública.' }) }); return
      }
      else { unexpected.push(entry); await route.abort(); return }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body), headers: { 'cache-control': 'private, no-store' } })
    })
    try {
      await page.goto(`${origin}/?variant=${variant}`)
      await page.waitForFunction(() => window.__ready)
      await page.evaluate((value) => window.__control.mount(value), configValue)
      await action({ page, state, entries: () => requests.slice(start) })
      if (variant === 'after') assert.equal(requests.slice(start).filter((item) => item.boundary === 'real-next-validator' && item.path.includes('cloudflarestorage')).length, 0, 'Owner private URLs must never be sent through the public optimizer.')
      assert.deepEqual(browserErrors.filter((item) => item.name === name), [])
      results.push({ name, result: 'PASS', requests: requests.slice(start) })
    } catch (error) {
      write(`${name}-failure.html`, await page.content())
      await page.screenshot({ path: path.join(evidence, `${name}-failure.png`) })
      throw error
    } finally {
      await page.evaluate(() => window.__control.unmount()).catch(() => {})
      await bounded(context.close(), name)
      activeContext = null
    }
  }
  const loaded = async (page, selector, expected) => {
    await page.waitForFunction(({ selector, expected }) => {
      const image = document.querySelector(selector)
      return image && image.complete && image.naturalWidth > 0 && image.currentSrc.includes(expected)
    }, { selector, expected })
    return page.locator(selector).evaluate((image) => ({ src: image.currentSrc, naturalWidth: image.naturalWidth, naturalHeight: image.naturalHeight }))
  }
  const capture = async (page, name) => {
    await page.screenshot({ path: path.join(evidence, `${name}.png`) })
    const observations = await page.locator('img').evaluateAll((images) => images.map((image) => ({ src: image.currentSrc || image.getAttribute('src'), width: image.naturalWidth, height: image.naturalHeight, complete: image.complete })))
    write(`${name}.json`, JSON.stringify(observations.map((item) => ({ ...item, src: safeUrl(item.src) })), null, 2))
  }
  if (baseRef) {
    await scenario('before-private-wizard-optimizer-rejection', 'before', { mode: 'preview', url: privateUrl() }, async ({ page, entries }) => {
      await waitFor(() => entries().some((item) => item.boundary === 'real-next-validator' && item.status === 400), 'real optimizer rejection')
      await page.getByText('Mídia indisponível', { exact: true }).waitFor()
      assert.equal(await page.locator('.public-anuncio-card img').count(), 0, 'The real SensitiveImage removes the failed image and shows its error state.')
      assert.ok(entries().some((item) => item.error === '"url" parameter is not allowed'))
      assert.equal((await loaded(page, 'aside img', privateOrigin)).naturalWidth, 480, 'The same real WizardPreview launcher loads identical bytes directly while its card hits the optimizer rejection.')
      assert.ok(entries().some((item) => item.boundary === 'synthetic-private-image' && item.status === 200))
      await capture(page, 'before-wizard')
    })
    await scenario('before-pending-owner-placeholder', 'before', { mode: 'card', advertisement: advertisement(privateUrl(), expiry(), true) }, async ({ page }) => {
      await loaded(page, '#owner-card img', '/_next/image')
      assert.match(await page.locator('#owner-card img').getAttribute('src'), /icone-sem-foto/)
      await capture(page, 'before-meus-anuncios')
    })
  }
  for (const viewport of [{ width: 1280, height: 900 }, { width: 390, height: 844 }]) {
    await scenario(`after-private-wizard-${viewport.width}`, 'after', { mode: 'preview', url: privateUrl() }, async ({ page, entries }) => {
      await loaded(page, '.public-anuncio-card img', privateOrigin)
      assert.ok(entries().some((item) => item.boundary === 'synthetic-private-image' && item.status === 200))
      assert.equal(entries().filter((item) => item.boundary === 'real-next-validator').length, 0)
      await capture(page, `after-wizard-${viewport.width}`)
    }, viewport)
  }
  await scenario('after-pending-owner-cover', 'after', { mode: 'card', advertisement: advertisement() }, async ({ page, entries }) => {
    const dimensions = await loaded(page, '#owner-card img', privateOrigin)
    assert.equal(dimensions.naturalWidth, 480)
    await page.getByText('Em revisão', { exact: true }).waitFor()
    assert.equal(entries().filter((item) => item.boundary === 'real-next-validator').length, 0)
    await capture(page, 'after-meus-anuncios')
  })
  await scenario('after-expired-cover-refetch', 'after', {
    mode: 'card', advertisement: advertisement(privateUrl('a', 1), '2020-01-01T00:00:00Z'), transport: { generation: 2 },
  }, async ({ page, state, entries }) => {
    await loaded(page, '#owner-card img', 'EXEMPLO_NAO_REAL_2')
    assert.equal(state.detailReads, 1)
    assert.equal(entries().filter((item) => item.boundary === 'synthetic-private-image' && item.generation === 1).length, 0, 'Expired sources must never reach the browser image transport.')
    await capture(page, 'after-cover-expired-refreshed')
  })
  await scenario('after-owner-image-error-refetch', 'after', {
    mode: 'card', advertisement: advertisement(), transport: { generation: 2, rejectGeneration: 1 },
  }, async ({ page, state, entries }) => {
    await loaded(page, '#owner-card img', 'EXEMPLO_NAO_REAL_2')
    assert.equal(state.detailReads, 1)
    assert.equal(entries().filter((item) => item.boundary === 'synthetic-private-image' && item.status === 403).length, 1)
    await capture(page, 'after-cover-load-error-refreshed')
  })
  await scenario('after-owner-refetch-403-fails-closed', 'after', {
    mode: 'card', advertisement: advertisement(privateUrl(), '2020-01-01T00:00:00Z'), transport: { detailStatus: 403 },
  }, async ({ page, state, entries }) => {
    await page.getByText('Não foi possível carregar esta foto.', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Atualizar foto', exact: true }).waitFor()
    assert.equal(state.detailReads, 1)
    assert.equal(await page.locator('#owner-card img').count(), 0)
    assert.equal(entries().filter((item) => item.boundary === 'synthetic-private-image').length, 0)
    await capture(page, 'after-cover-refresh-denied')
  })
  const sameReceiptExpiry = expiry()
  await scenario('after-same-url-error-recovery', 'after', {
    mode: 'card', advertisement: advertisement(privateUrl(), sameReceiptExpiry),
    transport: { rejectGeneration: 1, recoverImageOnDetail: true, fixedExpiry: sameReceiptExpiry },
  }, async ({ page, state, entries }) => {
    await loaded(page, '#owner-card img', 'EXEMPLO_NAO_REAL_1')
    assert.equal(state.detailReads, 1, 'A retry with unchanged URL and expiration must recover without a second metadata renewal.')
    assert.deepEqual(entries().filter((item) => item.boundary === 'synthetic-private-image').map((item) => item.status), [403, 200])
    await capture(page, 'after-same-url-recovered')
  })
  await scenario('after-lifecycle-preserves-renewed-cover', 'after', {
    mode: 'card', advertisement: advertisement(privateUrl(), '2020-01-01T00:00:00Z'), transport: { generation: 2 },
  }, async ({ page, state, entries }) => {
    await loaded(page, '#owner-card img', 'EXEMPLO_NAO_REAL_2')
    assert.equal(state.detailReads, 1)
    await page.evaluate(() => window.__control.lifecycle())
    await page.getByText('Pausado', { exact: true }).waitFor()
    await loaded(page, '#owner-card img', 'EXEMPLO_NAO_REAL_2')
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))))
    assert.equal(state.detailReads, 1, 'A lifecycle snapshot preserving the original cover must retain the renewed cover without returning to its expired source.')
    assert.equal(entries().filter((item) => item.boundary === 'synthetic-private-image' && item.generation === 1).length, 0)
    await capture(page, 'after-lifecycle-renewed-cover')
  })
  await scenario('after-local-selection-and-replacement', 'after', { mode: 'selection' }, async ({ page, entries }) => {
    await page.getByRole('button', { name: 'Fechar pré-visualização' }).click()
    const picker = page.locator('#selection input[type=file]').first()
    await picker.setInputFiles({ name: 'foto-a.png', mimeType: 'image/png', buffer: imageA })
    await page.locator('#open-preview').click()
    assert.equal((await loaded(page, '.public-anuncio-card img', 'blob:')).naturalWidth, 480)
    await capture(page, 'after-local-selection')
    await page.getByRole('button', { name: 'Fechar pré-visualização' }).click()
    await page.getByRole('button', { name: /Remover.*foto-a/ }).click()
    await picker.setInputFiles({ name: 'foto-b.png', mimeType: 'image/png', buffer: imageB })
    await page.locator('#open-preview').click()
    assert.equal((await loaded(page, '.public-anuncio-card img', 'blob:')).naturalWidth, 360)
    await capture(page, 'after-local-replacement')
    assert.equal(entries().filter((item) => item.boundary === 'synthetic-private-image').length, 0)
  })
  await scenario('after-reopened-editor-refetches-preview', 'after', { mode: 'editor', generation: 1 }, async ({ page, state }) => {
    await page.getByRole('heading', { name: 'Editar anúncio', exact: true }).waitFor()
    await page.getByRole('button', { name: 'Fotos', exact: true }).click()
    await loaded(page, 'img[alt="Prévia da mídia"]', privateOrigin)
    const initialReads = state.mediaReads
    state.rejectGeneration = 1
    state.generation = 2
    await page.evaluate(() => window.__control.update({ generation: 2 }))
    await page.getByRole('heading', { name: 'Editar anúncio', exact: true }).waitFor()
    await page.getByRole('button', { name: 'Fotos', exact: true }).click()
    const image = await loaded(page, 'img[alt="Prévia da mídia"]', 'EXEMPLO_NAO_REAL_2')
    assert.equal(image.naturalWidth, 480)
    assert.ok(state.mediaReads > initialReads, 'Real API adapter must fetch fresh owner media after reopening.')
    await capture(page, 'after-editor-reopened')
  })
  await scenario('after-editor-expired-preview-refetch', 'after', {
    mode: 'editor', generation: 1, transport: { expiredInitialMedia: true, generation: 2 },
  }, async ({ page, state, entries }) => {
    await page.getByRole('heading', { name: 'Editar anúncio', exact: true }).waitFor()
    await waitFor(() => state.mediaReads >= 2, 'parent expiry callback through real media API adapter')
    await page.getByRole('button', { name: /Pré-visualizar anúncio/ }).first().click()
    await loaded(page, '.public-anuncio-card img', 'EXEMPLO_NAO_REAL_2')
    assert.equal(entries().filter((item) => item.boundary === 'synthetic-private-image' && item.generation === 1).length, 0)
    await capture(page, 'after-editor-expired-preview')
  })
  await scenario('after-launcher-retry-is-not-nested', 'after', {
    mode: 'editor', generation: 1, transport: { expiredInitialMedia: true, denyMediaRefresh: true },
  }, async ({ page, state, entries }) => {
    await page.getByRole('heading', { name: 'Editar anúncio', exact: true }).waitFor()
    await waitFor(() => state.mediaReads >= 2, 'synthetic expiry renewal denial')
    await page.locator('aside').getByText('Não foi possível carregar esta foto.', { exact: true }).waitFor()
    assert.equal(await page.locator('button button').count(), 0, 'A launcher button must not contain an image retry button.')
    assert.equal(await page.locator('aside button').count(), 1, 'The launcher remains the single interactive element around its thumbnail.')
    assert.equal(entries().filter((item) => item.boundary === 'synthetic-private-image').length, 0)
    await capture(page, 'after-launcher-renewal-denied')
  })
  // Composed browser response contracts only. These fixtures do not implement
  // authorization; the real controller/service tests establish that boundary.
  await scenario('after-anonymous-management-401-no-private-image', 'after', {
    mode: 'editor', actor: 'anonymous', generation: 1, transport: { detailStatus: 401, mediaInitialStatus: 401 },
  }, async ({ page, entries }) => {
    await page.getByRole('heading', { name: 'Acesso restrito', exact: true }).waitFor()
    const failures = await page.evaluate(() => window.__control.managementLookup())
    assert.deepEqual(failures, [{ rejected: true, status: 401 }, { rejected: true, status: 401 }])
    assert.equal(entries().filter((item) => item.boundary === 'synthetic-private-image').length, 0)
    assert.equal(await page.locator('img').count(), 0)
    await capture(page, 'after-anonymous-response-denied')
  })
  await scenario('after-other-user-management-403-no-private-image', 'after', {
    mode: 'editor', actor: 'other', generation: 1, transport: { detailStatus: 403, mediaInitialStatus: 403 },
  }, async ({ page, entries }) => {
    await page.getByText('Você não tem permissão para editar este anúncio.', { exact: true }).waitFor()
    assert.equal(entries().filter((item) => item.boundary === 'synthetic-api' && item.status === 403 && item.path.includes('/minha-conta/anuncios/')).length, 2)
    assert.equal(entries().filter((item) => item.boundary === 'synthetic-private-image').length, 0)
    assert.equal(await page.locator('img').count(), 0)
    await capture(page, 'after-other-user-response-denied')
  })
  await scenario('after-pending-public-404-owner-preview-ignored', 'after', { mode: 'public', actor: 'anonymous', url: privateUrl() }, async ({ page, entries }) => {
    const failure = await page.evaluate(() => window.__control.publicLookup())
    assert.deepEqual(failure, { rejected: true, status: 404 })
    await loaded(page, '#public-card img', 'icone-sem-foto')
    assert.equal(entries().filter((item) => item.boundary === 'synthetic-private-image').length, 0, 'The public card must ignore the owner preview override outside previewMode.')
    await capture(page, 'after-pending-public-response-denied')
  })
  assert.deepEqual(unexpected, [])
} catch (error) { failure = error }
finally {
  if (activeContext) try { await bounded(activeContext.close(), 'active context') } catch (error) { cleanupErrors.push(String(error)) }
  if (browser) try { await bounded(browser.close(), 'browser') } catch (error) { cleanupErrors.push(String(error)) }
  if (server) try { server.closeAllConnections(); await bounded(new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())), 'server') } catch (error) { cleanupErrors.push(String(error)) }
  for (const compiler of compilers) try { await bounded(new Promise((resolve, reject) => compiler.close((error) => error ? reject(error) : resolve())), 'webpack') } catch (error) { cleanupErrors.push(String(error)) }
  const after = Object.fromEntries(inputs.map((name) => [name, digest(fs.readFileSync(path.join(frontend, name)))]))
  write('inputs-after.json', JSON.stringify(after, null, 2))
  const changedInputs = inputs.filter((name) => inputHashes[name] !== after[name])
  if (changedInputs.length) failure ||= Error('Application source changed while the focused browser evidence was being recorded: ' + changedInputs.join(', '))
  write('outcome.json', JSON.stringify({ result: !failure && !cleanupErrors.length ? 'PASS' : 'FAIL', cases: results, failure: failure?.stack, cleanupErrors, changedInputs, unexpected, browserErrors, requests, evidence, backendAuthorizationClaim: false, optimizerValidationReal: true }, null, 2))
  console.log('OWNER_PHOTO_PREVIEW_EVIDENCE=' + evidence)
}
if (failure) throw failure
assert.deepEqual(cleanupErrors, [])
console.log(`OWNER_PHOTO_PREVIEW_RESULT=PASS cases=${results.length} realNextOptimizerValidator=true externalRequests=0 cleanup=PASS`)
