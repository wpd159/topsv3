import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

// Local React DOM/browser integration. No personal browser profile or remote API.
// The caller supplies an already installed Playwright; dependencies stay frozen.
const require = createRequire(import.meta.url)
const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = path.join(frontend, 'src')
const nodeModules = path.join(frontend, 'node_modules')
const task = process.env.TOPS_UI_EVIDENCE_ROOT || os.tmpdir()
const evidence = fs.mkdtempSync(path.join(task, 'ultima-foto-dom-'))
const write = (name, bytes) => fs.writeFileSync(path.join(evidence, name), bytes, { flag: 'wx' })
const { chromium, webkit } = require(process.env.TOPS_PLAYWRIGHT_MODULE || 'playwright')
const sharp = require('sharp')
const bundledWebpack = require('next/dist/compiled/webpack/webpack')
bundledWebpack.init()
const { webpack } = bundledWebpack
const results = []
const requests = []
const unexpected = []
const browserErrors = []
let browser
let server
let compiler
let failure
const cleanupErrors = []

const lifecycle = (slug = 'anuncio-sintetico', status = 'PENDENTE_REVISAO') => ({
  id: slug === 'outro-anuncio' ? 'ad-second' : 'ad-first', slug, status, statusModeracao: status === 'PUBLICADO' ? 'APROVADO' : 'PENDENTE', atualizadoEm: '2026-09-08T12:00:00Z',
  acoesPermitidas: { pausar: false, reativar: false, remover: status !== 'REMOVIDO', corrigirEReenviar: false },
})
const photo = (id) => ({ id, tipo: 'FOTO', ordem: 0, status: 'PENDENTE', visibilidadeMidia: null, previewUrl: '/synthetic-preview', restrita: false, ocultaPorLimite: false })
const media = (count = 1, slug = 'anuncio-sintetico', status = 'PENDENTE_REVISAO') => ({
  anuncio: lifecycle(slug, status), fotosValidasAtivasTotal: count,
  midias: Array.from({ length: count }, (_, index) => photo(`foto-${index + 1}`)),
  limites: { maxFotos: 4, fotosAtivas: count, fotosDisponiveis: 4 - count, maxVideos: 0, videosAtivos: 0, videosDisponiveis: 0, fotosExtrasAtivo: false, videoAtivo: false, maxFotoBytes: 20971520, maxVideoBytes: 104857600 },
})
const advertisement = (state) => ({
  ...state.anuncio, titulo: `Anúncio sintético ${state.anuncio.id}`, descricao: 'Descrição sintética suficientemente longa para validar os campos da edição.',
  categoria: 'MASSAGENS', preco: 100, whatsapp: null, linkConteudo: null, locaisAtendimento: ['A_COMBINAR'], servicos: ['MASSAGEM_TANTRICA'], atendimentoExclusivamenteVirtual: false,
  localizacao: { uf: 'SP', cidade: 'Cidade sintética', cidadeSlug: 'cidade-sintetica', bairro: 'Bairro sintético', bairroSlug: 'bairro-sintetico', enderecoResumido: null },
  capa: null, midias: state.midias.map((item) => ({ ...item, finalidade: 'GALERIA', urlPublica: null })),
  visualizacoes: { total: 0, situacao: 'ZERO_LEGITIMO' }, reprovacao: null, beneficiosPremium: [], storyAtivo: null,
})
const deferred = () => {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}
const waitFor = async (predicate, label) => {
  const deadline = performance.now() + 5000
  while (!(await predicate())) {
    if (performance.now() >= deadline) throw Error(`Observation timeout: ${label}`)
    await new Promise((resolve) => setTimeout(resolve, 20))
  }
}
const bounded = (promise, label) => {
  let timer
  return Promise.race([promise, new Promise((_, reject) => { timer = setTimeout(() => reject(Error(`Cleanup timeout: ${label}`)), 10000) })]).finally(() => clearTimeout(timer))
}

write('loader.cjs', `const ts=require(${JSON.stringify(require.resolve('typescript'))});module.exports=function(source){return ts.transpileModule(source,{fileName:this.resourcePath,compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext,jsx:ts.JsxEmit.ReactJSX}}).outputText}`)
write('auth.js', `const usuario={id:'synthetic-user',dataNascimento:'1990-01-01'};const value={usuario,carregando:false,refresh:async()=>{}};export const useAuth=()=>value;`)
write('navigation.js', `export const useRouter=()=>({push:(path)=>{window.__events.push({kind:'navigate',path})}});`)
write('toast.js', `export const toast=Object.fromEntries(['success','error','warning'].map(kind=>[kind,message=>window.__events.push({kind,message})]));`)
write('localidades.js', `const value={estados:[],cidades:[],bairros:[],loadCidades:async()=>{},loadBairros:async()=>{},setCidades(){},setBairros(){}};export const useLocalidades=()=>value;`)
write('preview.js', 'export const WizardPreview=()=>null;')
write('server-actions.js', `export const revalidarCacheCatalogoPublico=async(event)=>{window.__events.push({kind:'catalog-revalidation',event:event??null});if(window.__failCatalogRevalidation)throw Error('Falha sintética de invalidação');};`)
write('entry.tsx', `
import React,{useState} from 'react';
import {createRoot} from 'react-dom/client';
import AnuncioWizard from ${JSON.stringify(path.join(source, 'features/anuncio-wizard/anuncio-wizard.tsx'))};
import {WizardStepFotos} from ${JSON.stringify(path.join(source, 'features/anuncio-wizard/components/wizard-step-fotos.tsx'))};
import {listarMinhasMidias} from ${JSON.stringify(path.join(source, 'lib/meus-anuncios-api.ts'))};
window.__events=[];window.__snapshotResponses=0;
function App(){
 const [slug,setSlug]=useState('anuncio-sintetico');
 const [snapshot,setSnapshot]=useState(undefined);
 const standalone=new URLSearchParams(location.search).has('standalone');
 if(standalone)return <><button onClick={async()=>{setSnapshot(await listarMinhasMidias(slug));window.__snapshotResponses++}}>Atualizar snapshot sintético</button><WizardStepFotos slug={slug} initialFiles={[]} fotoNomes={[]} videosNovos={[]} onChange={()=>{}} onChangeVideosNovos={()=>{}} persistedState={snapshot} onPersistedChange={setSnapshot}/></>;
 return <><button onClick={()=>setSlug('outro-anuncio')}>Trocar alvo sintético</button><AnuncioWizard mode="edit" slug={slug}/></>;
}
createRoot(document.getElementById('root')).render(<App/>);
`)

try {
  compiler = webpack({
    mode: 'development', target: 'web', devtool: false, context: frontend, entry: path.join(evidence, 'entry.tsx'),
    output: { path: evidence, filename: 'bundle.js' },
    resolve: {
      extensions: ['.tsx', '.ts', '.jsx', '.js'], modules: [nodeModules],
      alias: {
        'next/navigation$': path.join(evidence, 'navigation.js'), sonner: path.join(evidence, 'toast.js'),
        '@/context/AuthContext$': path.join(evidence, 'auth.js'), '@/hooks/useLocalidades$': path.join(evidence, 'localidades.js'),
        '@/app/(painel-admin)/admin/anuncios/actions$': path.join(evidence, 'server-actions.js'),
        '@': source,
      },
    },
    module: { rules: [{ test: /\.[jt]sx?$/, exclude: /node_modules/, use: path.join(evidence, 'loader.cjs') }] },
    plugins: [
      new webpack.DefinePlugin({ 'process.env': JSON.stringify({ NODE_ENV: 'development', NEXT_PUBLIC_API_URL: '/api/public' }) }),
      new webpack.NormalModuleReplacementPlugin(/^\.\/components\/wizard-preview$/, path.join(evidence, 'preview.js')),
    ],
  })
  const stats = await new Promise((resolve, reject) => compiler.run((error, value) => error ? reject(error) : resolve(value)))
  write('webpack.json', JSON.stringify(stats.toJson({ all: false, errors: true, warnings: true }), null, 2))
  assert.equal(stats.hasErrors(), false, stats.toString({ all: false, errors: true }))
  const bundle = fs.readFileSync(path.join(evidence, 'bundle.js'))
  server = http.createServer((request, response) => {
    if (request.url?.startsWith('/bundle.js')) { response.setHeader('Content-Type', 'text/javascript'); response.end(bundle); return }
    response.setHeader('Content-Type', 'text/html; charset=utf-8')
    response.end('<!doctype html><html><body><div id="root"></div><script src="/bundle.js"></script></body></html>')
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  const browserName = process.env.TOPS_UI_BROWSER_NAME === 'webkit' ? 'webkit' : 'chromium'
  browser = browserName === 'webkit'
    ? await webkit.launch({ headless: true })
    : await chromium.launch({ channel: process.env.TOPS_UI_BROWSER_CHANNEL || 'chrome', headless: true })
  write('browser.txt', `${browserName} ${browser.version()}\nReact DOM mounted; auth/router/localidades synthetic; all API transport intercepted.\n`)

  async function scenario(name, action, options = {}) {
    const state = { current: media(options.count ?? 1, 'anuncio-sintetico', options.status), readGate: options.holdInitialRead ? deferred() : null, deleteGate: null, patchGate: null, uploadGate: null, invalidDelete: false, deleteFailure: null, capturedRead: null, uploadAttempts: 0, patchFailure: null }
    if (options.maxFotos) state.current.limites = { ...state.current.limites, maxFotos: options.maxFotos, fotosDisponiveis: options.maxFotos - (options.count ?? 1), fotosExtrasAtivo: true }
    if (options.validCount !== undefined) state.current.fotosValidasAtivasTotal = options.validCount
    if (options.video) state.current.midias.push({ ...photo('video-1'), tipo: 'VIDEO' })
    if (options.status === 'PUBLICADO') state.current.midias.forEach((item) => { item.status = 'PUBLICAVEL'; item.visibilidadeMidia = 'LIVRE' })
    const start = requests.length
    const context = await browser.newContext({ serviceWorkers: 'block',
      ...(options.mobile ? { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true } : {}) })
    const page = await context.newPage()
    if (options.restoredDescription !== undefined) {
      await page.addInitScript((description) => {
        const form = { titulo: 'Anúncio sintético ad-first', categoria: 'MASSAGENS', descricao: description,
          preco: 'R$ 100,00', locaisAtendimento: ['A_COMBINAR'], servicos: ['MASSAGEM_TANTRICA'],
          estadoId: 'SP', cidadeId: 'cidade-sintetica', bairroId: 'bairro-sintetico', estadoNome: 'SP', estadoUf: 'SP',
          cidadeNome: 'Cidade sintética', bairroNome: 'Bairro sintético' }
        window.localStorage.setItem('topsdojob:anuncio-wizard:v3:synthetic-user:edit:anuncio-sintetico',
          JSON.stringify({ version: 5, savedAt: Date.now(),
            sourceVersion: 'ad-first:anuncio-sintetico:2026-09-08T12:00:00Z:PENDENTE_REVISAO:PENDENTE',
            state: { currentStep: 'kyc', form } }))
      }, options.restoredDescription)
    }
    page.on('pageerror', (error) => browserErrors.push({ name, message: error.message }))
    await context.route('**/*', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      if (url.origin !== origin) { unexpected.push({ name, method: request.method(), origin: url.origin }); await route.abort(); return }
      if (!url.pathname.startsWith('/api/')) {
        if (url.pathname === '/synthetic-preview') { await route.fulfill({ status: 404, body: 'Synthetic broken preview' }); return }
        await route.continue(); return
      }
      const jsonRequest = request.headers()['content-type']?.includes('application/json')
      const entry = { name, method: request.method(), path: url.pathname, body: jsonRequest ? request.postDataJSON() : null, idempotencyKey: request.headers()['idempotency-key'] ?? null }
      requests.push(entry)
      const slug = url.pathname.includes('/outro-anuncio') ? 'outro-anuncio' : 'anuncio-sintetico'
      const current = slug === 'outro-anuncio' ? media(2, slug) : state.current
      let body
      if (url.pathname.endsWith('/auth/me')) body = { id: 'synthetic-user' }
      else if (url.pathname.endsWith('/minha-conta/kyc')) body = { prontoParaEnviarAnuncio: true, nomeCivil: 'Pessoa sintética', cpfPreenchido: true, dataNascimento: '1990-01-01', documentos: [], status: 'APROVADO' }
      else if (url.pathname.endsWith('/categorias-home')) body = [{ identificador: 'MASSAGENS', titulo: 'Massagens', ativo: true }]
      else if (url.pathname.endsWith('/wizard-progress/sync')) body = { id: 'progress-synthetic', atualizadoEm: '2026-09-08T12:00:00Z' }
      else if (url.pathname.endsWith('/midias') && request.method() === 'GET') {
        body = structuredClone(current)
        if (state.readGate && slug === 'anuncio-sintetico') { state.capturedRead = body; await state.readGate.promise }
      } else if (url.pathname.includes('/midias/') && request.method() === 'DELETE') {
        assert.ok(['/api/public/minha-conta/anuncios/anuncio-sintetico/midias/foto-1', '/api/public/minha-conta/anuncios/anuncio-sintetico/midias/video-1'].includes(url.pathname))
        if (state.deleteGate) await state.deleteGate.promise
        if (state.deleteFailure === 'rejected') { await route.fulfill({ status: 409, contentType: 'application/json', body: '{"message":"Exclusão recusada para este teste","code":"ULTIMA_FOTO_APROVADA"}' }); return }
        const remaining = current.midias.filter((item) => item.id !== url.pathname.split('/').at(-1))
        const remainingPhotos = remaining.filter((item) => item.tipo === 'FOTO').length
        state.current = { ...media(remainingPhotos, slug, remainingPhotos ? 'PENDENTE_REVISAO' : 'REMOVIDO'), midias: remaining }
        if (state.deleteFailure === 'abort') { await route.abort('connectionreset'); return }
        if (state.deleteFailure === 'truncated') { await route.fulfill({ status: 200, contentType: 'application/json', body: '{"midias":' }); return }
        if (state.deleteFailure === 'gateway') { await route.fulfill({ status: 503, contentType: 'application/json', body: '{"message":"Resposta indisponível"}' }); return }
        body = state.invalidDelete ? { midias: [], limites: {} } : state.current
      } else if (url.pathname.endsWith('/midias/lote') && request.method() === 'POST') {
        if (state.uploadGate) await state.uploadGate.promise
        state.uploadAttempts++
        if (options.uploadMode === 'success' || options.uploadMode === 'ambiguous') {
          if (state.uploadAttempts === 1) {
            const nextCount = (options.count ?? 1) + (options.uploadCount ?? 1)
            state.current = media(nextCount, slug)
            if (options.maxFotos) state.current.limites = { ...state.current.limites,
              maxFotos: options.maxFotos, fotosDisponiveis: options.maxFotos - nextCount, fotosExtrasAtivo: true }
          }
          if (options.uploadMode === 'ambiguous' && state.uploadAttempts === 1) {
            await route.fulfill({ status: 503, contentType: 'application/json', body: '{"message":"Resposta perdida após confirmação sintética"}' }); return
          }
          body = state.current
        } else {
          await route.fulfill({ status: 503, contentType: 'application/json', body: '{"message":"Falha transitória sintética"}' }); return
        }
      } else if (/\/minha-conta\/anuncios\/[^/]+$/.test(url.pathname)) {
        if (request.method() === 'PATCH' && state.patchGate) await state.patchGate.promise
        if (request.method() === 'PATCH' && state.patchFailure) {
          await route.fulfill({ status: state.patchFailure.status, contentType: 'application/json', body: JSON.stringify(state.patchFailure.body) }); return
        }
        body = advertisement(slug === 'outro-anuncio' ? current : state.current)
      } else { unexpected.push(entry); await route.abort(); return }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
    })
    try {
      await page.goto(`${origin}/${options.standalone ? '?standalone=1' : ''}`)
      if (options.holdInitialRead) await waitFor(() => state.capturedRead !== null, 'GET inicial anterior capturado')
      else if (!options.standalone) await page.getByRole('heading', { name: 'Editar anúncio', exact: true }).waitFor()
      else await page.getByRole('button', { name: 'Remover mídia', exact: true }).first().waitFor()
      await action({ page, state, entries: () => requests.slice(start), context })
      assert.equal(browserErrors.filter((item) => item.name === name).length, 0, JSON.stringify(browserErrors))
      results.push({ name, result: 'PASS', requests: requests.length - start })
    } catch (error) {
      write(`${name}.html`, await page.content())
      throw error
    } finally {
      state.readGate?.resolve(); state.deleteGate?.resolve(); state.patchGate?.resolve(); state.uploadGate?.resolve()
      await context.close()
    }
  }
  const photoStep = async (page) => page.getByRole('button', { name: 'Fotos', exact: true }).click()
  const selectSyntheticPhotos = async (page, count) => {
    const jpeg = await sharp({ create: { width: 2, height: 2, channels: 3, background: '#667788' } }).jpeg().toBuffer()
    const chooser = page.waitForEvent('filechooser')
    await page.getByRole('button', { name: 'Selecionar fotos do anúncio', exact: true }).click()
    await (await chooser).setFiles(Array.from({ length: count }, (_, index) => ({
      name: `pendente-${index + 1}.jpg`, mimeType: 'image/jpeg', buffer: jpeg,
    })))
    await page.getByText(`pendente-${count}.jpg: Arquivo pronto para envio.`, { exact: true }).waitFor()
  }
  const deleteCount = (entries) => entries().filter((item) => item.method === 'DELETE').length
  const completedCount = (entries) => entries().filter((item) => item.path.endsWith('/wizard-progress/sync') && item.body?.ultimoStep === 'concluido').length
  const assertSingleInvalidation = async (page, entries, publicSnapshot = false) => {
    const notifications = (await page.evaluate(() => window.__events)).filter((item) => item.kind === 'catalog-revalidation')
    assert.equal(notifications.length, 1)
    if (publicSnapshot) {
      assert.equal(notifications[0].event.eventType, 'RETIRADA')
      assert.deepEqual(notifications[0].event.urls, [
        `${origin}/anuncios`, `${origin}/acompanhantes`, `${origin}/anuncios/anuncio-sintetico`,
        `${origin}/acompanhantes/sp`, `${origin}/acompanhantes/sp/cidade-sintetica`,
        `${origin}/acompanhantes/sp/cidade-sintetica/bairro-sintetico`,
      ])
    } else {
      assert.equal(notifications[0].event, null, 'No potentially private URL or location payload is sent to IndexNow.')
    }
    const deleteIndex = entries().findIndex((item) => item.method === 'DELETE')
    assert.ok(deleteIndex >= 0)
    assert.equal(entries().slice(deleteIndex + 1).some((item) => item.method === 'GET'
      && item.path.endsWith('/anuncio-sintetico')), false, 'Invalidation does not need a now-404 detail GET.')
  }

  await scenario('selecao-edicao-sobrevive-etapas', async ({ page, entries }) => {
    await photoStep(page)
    await selectSyntheticPhotos(page, 1)
    await page.getByRole('button', { name: 'Revise seu anúncio', exact: true }).click()
    await photoStep(page)
    assert.equal(await page.getByRole('button', { name: 'Remover pendente-1.jpg', exact: true }).count(), 1)
    assert.equal(entries().filter((item) => item.path.endsWith('/midias/lote')).length, 0)
  })

  await scenario('salvar-pendentes-mobile-sem-patch', async ({ page, entries }) => {
    await photoStep(page)
    await selectSyntheticPhotos(page, 1)
    await page.getByRole('button', { name: 'Confirmação de identidade', exact: true }).click()
    await page.getByRole('button', { name: 'Salvar alterações', exact: true }).click()
    const notice = page.getByRole('alert').getByText('Você selecionou arquivos que ainda não foram enviados. Clique em Enviar arquivos para concluir.', { exact: true })
    await notice.waitFor()
    assert.equal(await notice.evaluate((item) => item === document.activeElement), true)
    assert.equal(await page.getByRole('button', { name: 'Remover pendente-1.jpg', exact: true }).count(), 1)
    assert.equal(entries().filter((item) => item.method === 'PATCH').length, 0)
    assert.equal(entries().filter((item) => item.path.endsWith('/midias/lote')).length, 0)
  }, { mobile: true })

  await scenario('tres-mais-sete-upload-patch-recusa', async ({ page, state, entries }) => {
    await photoStep(page)
    await page.getByText('Fotos: 3/10', { exact: true }).waitFor()
    await selectSyntheticPhotos(page, 7)
    await page.getByRole('button', { name: 'Enviar arquivos', exact: true }).click()
    await page.getByText('Fotos: 10/10', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Remover pendente-7.jpg', exact: true }).count(), 0)
    state.patchFailure = { status: 400, body: { code: 'BAD_REQUEST', field: 'titulo', ruleCode: 'CONTATO_NAO_PERMITIDO',
      message: 'valor privado sintetico NAO_EXIBIR', requestId: 'synthetic-rid' } }
    await page.getByRole('button', { name: 'Confirmação de identidade', exact: true }).click()
    await page.getByRole('button', { name: 'Salvar alterações', exact: true }).click()
    const alert = page.getByRole('alert').getByText(/Remova dados de contato do nome do anúncio/)
    await alert.waitFor()
    assert.equal(await alert.evaluate((item) => item === document.activeElement), true)
    assert.equal(await page.locator('input[aria-invalid="true"]').count(), 1)
    assert.equal(entries().filter((item) => item.method === 'PATCH').length, 1)
    assert.equal(entries().filter((item) => item.path.endsWith('/midias/lote')).length, 1)
    assert.doesNotMatch(await page.locator('body').innerText(), /NAO_EXIBIR/)
    await page.getByRole('button', { name: 'Confirmação de identidade', exact: true }).click()
    await page.getByRole('button', { name: 'Salvar alterações', exact: true }).click()
    assert.equal(entries().filter((item) => item.path.endsWith('/midias/lote')).length, 1)
  }, { count: 3, maxFotos: 10, uploadMode: 'success', uploadCount: 7 })

  await scenario('beneficio-dez-recusa-excedente', async ({ page, entries }) => {
    await photoStep(page)
    await selectSyntheticPhotos(page, 8)
    await page.getByRole('button', { name: 'Enviar arquivos', exact: true }).click()
    await page.getByText('Você atingiu o limite de fotos deste anúncio.', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Remover pendente-8.jpg', exact: true }).count(), 1)
    assert.equal(entries().filter((item) => item.path.endsWith('/midias/lote')).length, 0)
  }, { count: 3, maxFotos: 10 })

  await scenario('upload-ambiguo-reconcilia-antes-replay', async ({ page, entries }) => {
    await photoStep(page)
    await selectSyntheticPhotos(page, 1)
    await page.getByRole('button', { name: 'Enviar arquivos', exact: true }).click()
    await page.getByRole('button', { name: 'Tentar enviar novamente', exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Remover pendente-1.jpg', exact: true }).count(), 1)
    await page.getByRole('button', { name: 'Tentar enviar novamente', exact: true }).click()
    await page.getByText('Fotos: 2/4', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Remover pendente-1.jpg', exact: true }).count(), 0)
    const calls = entries().filter((item) => item.path.endsWith('/midias') && item.method === 'GET'
      || item.path.endsWith('/midias/lote') && item.method === 'POST')
    const posts = calls.filter((item) => item.method === 'POST')
    assert.equal(posts.length, 2)
    assert.equal(posts[0].idempotencyKey, posts[1].idempotencyKey)
    const firstPost = calls.indexOf(posts[0]); const secondPost = calls.indexOf(posts[1])
    assert.ok(calls.slice(firstPost + 1, secondPost).some((item) => item.method === 'GET'))
  }, { uploadMode: 'ambiguous' })

  for (const length of [492, 500, 501]) {
    await scenario(`descricao-restaurada-${length}`, async ({ page, entries }) => {
      await page.getByRole('button', { name: 'Confirmação de identidade', exact: true }).click()
      await page.getByRole('button', { name: 'Salvar alterações', exact: true }).click()
      if (length <= 500) {
        await waitFor(() => entries().some((item) => item.method === 'PATCH'), `PATCH descricao ${length}`)
        assert.equal(entries().find((item) => item.method === 'PATCH').body.descricao.length, length)
      } else {
        await page.getByRole('button', { name: 'Continuar', exact: true }).waitFor()
        assert.equal(entries().filter((item) => item.method === 'PATCH').length, 0)
        assert.equal(await page.locator('textarea[maxlength="500"]').inputValue(), 'A'.repeat(501))
        assert.ok((await page.evaluate(() => window.__events)).some((event) => event.kind === 'warning'
          && event.message === 'A descrição deve ter entre 20 e 500 caracteres.'))
      }
    }, { restoredDescription: 'A'.repeat(length) })
  }

  for (const { name, description, valid } of [
    { name: 'minimo-canonico-20', description: `${'A'.repeat(18)}\u0001B`, valid: true },
    { name: 'abaixo-minimo-canonico-19', description: `${'A'.repeat(19)}\u0001`, valid: false },
    { name: 'bruto-501-canonico-500', description: `${'A'.repeat(500)}\u0001`, valid: true },
  ]) {
    await scenario(name, async ({ page, entries }) => {
      await page.getByRole('button', { name: 'Confirmação de identidade', exact: true }).click()
      await page.getByRole('button', { name: 'Salvar alterações', exact: true }).click()
      if (valid) {
        await waitFor(() => entries().some((item) => item.method === 'PATCH'), `PATCH ${name}`)
        assert.equal(entries().find((item) => item.method === 'PATCH').body.descricao, description)
      } else {
        await waitFor(async () => (await page.evaluate(() => window.__events)).some((event) => event.kind === 'warning'
          && event.message === 'A descrição deve ter entre 20 e 500 caracteres.'), `validação ${name}`)
        assert.equal(entries().filter((item) => item.method === 'PATCH').length, 0)
        assert.equal(await page.locator('textarea[maxlength="500"]').inputValue(), description)
      }
    }, { restoredDescription: description })
  }

  await scenario('patch-desconhecido-nao-vaza-resposta', async ({ page, state, entries }) => {
    state.patchFailure = { status: 400, body: { code: 'BAD_REQUEST', field: 'descricao', ruleCode: 'REGRA_NAO_RECONHECIDA',
      message: 'NAO_EXIBIR dados sensiveis', requestId: 'nao exibir <request>' } }
    await page.getByRole('button', { name: 'Confirmação de identidade', exact: true }).click()
    await page.getByRole('button', { name: 'Salvar alterações', exact: true }).click()
    await waitFor(() => entries().some((item) => item.method === 'PATCH'), 'PATCH com erro desconhecido')
    await waitFor(async () => (await page.evaluate(() => window.__events)).some((event) => event.kind === 'error'), 'toast genérico')
    const events = await page.evaluate(() => window.__events)
    assert.ok(events.some((event) => event.kind === 'error'
      && event.message === 'Não foi possível salvar as alterações. Revise os dados e tente novamente.'))
    assert.doesNotMatch(`${await page.locator('body').innerText()} ${JSON.stringify(events)}`, /NAO_EXIBIR|REGRA_NAO_RECONHECIDA|nao exibir/)
    assert.equal(await page.locator('input[aria-invalid="true"], textarea[aria-invalid="true"]').count(), 0)
  })

  await scenario('cancelar-sem-delete', async ({ page, entries }) => {
    await photoStep(page)
    await page.getByRole('button', { name: 'Remover mídia', exact: true }).click()
    const dialog = page.getByRole('alertdialog')
    await dialog.getByRole('heading', { name: 'Excluir a última foto?', exact: true }).waitFor()
    await dialog.getByText('Ao excluir esta foto, seu anúncio será encerrado. Deseja continuar?', { exact: true }).waitFor()
    await dialog.getByText('Para trocar a foto, envie a nova antes de excluir a atual.', { exact: true }).waitFor()
    await dialog.getByText('Se o anúncio já estiver aprovado, aguarde a aprovação da nova foto antes de excluir a última foto aprovada.', { exact: true }).waitFor()
    assert.equal(await dialog.getByRole('button', { name: 'Cancelar', exact: true }).evaluate((button) => button === document.activeElement), true)
    await dialog.getByRole('button', { name: 'Cancelar', exact: true }).click()
    assert.equal(deleteCount(entries), 0)
    await page.getByRole('button', { name: 'Revise seu anúncio', exact: true }).click()
    await page.getByText('1 foto selecionada', { exact: true }).waitFor()
    await page.goto('about:blank')
    assert.equal(deleteCount(entries), 0, 'Desmontagem/erro de preview não envia DELETE.')
  })
  await scenario('confirmar-encerra-sem-finalizar', async ({ page, state, entries }) => {
    await photoStep(page)
    state.deleteGate = deferred()
    await page.getByRole('button', { name: 'Remover mídia', exact: true }).click()
    await page.getByRole('button', { name: 'Excluir foto e encerrar anúncio', exact: true }).evaluate((button) => { button.click(); button.click() })
    await waitFor(() => deleteCount(entries) === 1, 'DELETE iniciado')
    assert.equal(await page.getByRole('button', { name: 'Confirmação de identidade', exact: true, includeHidden: true }).isDisabled(), true)
    assert.equal(await page.getByRole('button', { name: 'Adicionar benefício', exact: true, includeHidden: true }).isDisabled(), true)
    assert.equal(await page.getByRole('heading', { name: 'Anúncio encerrado', exact: true }).count(), 0)
    state.deleteGate.resolve()
    await page.getByRole('heading', { name: 'Anúncio encerrado', exact: true }).waitFor()
    await page.getByText('Seu anúncio foi encerrado e não está mais disponível.', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Voltar para Meus anúncios', exact: true }).waitFor()
    assert.equal(deleteCount(entries), 1)
    assert.equal(entries().filter((item) => item.method === 'PATCH').length, 0)
    assert.equal(completedCount(entries), 0)
    assert.equal((await page.evaluate(() => window.__events)).filter((item) => item.kind === 'success').length, 0)
    await assertSingleInvalidation(page, entries)
  })
  for (const actionFails of [false, true]) {
    await scenario(`retirada-publicado-action-${actionFails ? 'falha' : 'sucesso'}`, async ({ page, entries }) => {
      await photoStep(page)
      await page.evaluate((failure) => { window.__failCatalogRevalidation = failure }, actionFails)
      await page.getByRole('button', { name: 'Remover mídia', exact: true }).click()
      await page.getByRole('button', { name: 'Excluir foto e encerrar anúncio', exact: true }).evaluate((button) => { button.click(); button.click() })
      await page.getByRole('heading', { name: 'Anúncio encerrado', exact: true }).waitFor()
      await page.evaluate(() => new Promise(requestAnimationFrame))
      await assertSingleInvalidation(page, entries, true)
      assert.equal(deleteCount(entries), 1)
      assert.equal(completedCount(entries), 0)
      assert.equal(entries().filter((item) => item.method === 'PATCH').length, 0)
      assert.equal((await page.evaluate(() => window.__events)).filter((item) => item.kind === 'success').length, 0)
    }, { status: 'PUBLICADO' })
  }
  await scenario('resumo-atualizado-outra-foto', async ({ page, entries }) => {
    await photoStep(page)
    await page.getByRole('button', { name: 'Remover mídia', exact: true }).first().click()
    await page.getByRole('heading', { name: 'Excluir foto?', exact: true }).waitFor()
    await page.getByText('Ao excluir a última foto, seu anúncio será encerrado. Deseja continuar?', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Excluir foto', exact: true }).click()
    await page.getByRole('button', { name: 'Revise seu anúncio', exact: true }).click()
    await page.getByText('1 foto selecionada', { exact: true }).waitFor()
    assert.equal(deleteCount(entries), 1)
    await page.getByRole('button', { name: 'Confirmação de identidade', exact: true }).click()
    await page.getByRole('button', { name: 'Salvar alterações', exact: true }).click()
    await waitFor(() => completedCount(entries) === 1, 'conclusão com foto persistida')
  }, { count: 2 })
  await scenario('snapshot-atrasado-nao-reabre', async ({ page, state, entries }) => {
    state.readGate = deferred()
    await page.getByRole('button', { name: 'Atualizar snapshot sintético', exact: true }).click()
    await waitFor(() => state.capturedRead !== null, 'snapshot anterior capturado')
    await page.getByRole('button', { name: 'Remover mídia', exact: true }).click()
    await page.getByRole('button', { name: 'Excluir foto e encerrar anúncio', exact: true }).click()
    await waitFor(() => state.current.anuncio.status === 'REMOVIDO', 'estado encerrado')
    await page.getByText('Seu anúncio foi encerrado e não está mais disponível.', { exact: true }).waitFor()
    state.readGate.resolve()
    await page.waitForFunction(() => window.__snapshotResponses === 1)
    await page.evaluate(() => new Promise(requestAnimationFrame))
    await page.getByText('Seu anúncio foi encerrado e não está mais disponível.', { exact: true }).waitFor()
    await page.getByRole('heading', { name: 'Anúncio encerrado', exact: true }).waitFor()
    await page.getByRole('button', { name: 'Voltar para Meus anúncios', exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Remover mídia', exact: true }).count(), 0)
    assert.equal(deleteCount(entries), 1)
  }, { standalone: true })
  await scenario('resposta-incompleta-nao-finaliza', async ({ page, state, entries }) => {
    await photoStep(page)
    state.invalidDelete = true
    await page.getByRole('button', { name: 'Remover mídia', exact: true }).click()
    await page.getByRole('button', { name: 'Excluir foto e encerrar anúncio', exact: true }).click()
    await page.getByText('Não foi possível confirmar o estado do anúncio. Volte para Meus anúncios para conferir antes de continuar.', { exact: true }).waitFor()
    assert.equal(completedCount(entries), 0)
    assert.equal((await page.evaluate(() => window.__events)).filter((item) => item.kind === 'success').length, 0)
  })
  for (const failureMode of ['abort', 'truncated', 'gateway']) {
    await scenario(`delete-confirmado-resposta-${failureMode}`, async ({ page, state, entries }) => {
      await photoStep(page)
      state.deleteFailure = failureMode
      await page.getByRole('button', { name: 'Remover mídia', exact: true }).click()
      await page.getByRole('button', { name: 'Excluir foto e encerrar anúncio', exact: true }).click()
      await page.getByText('Não foi possível confirmar o estado do anúncio. Volte para Meus anúncios para conferir antes de continuar.', { exact: true }).waitFor()
      assert.equal(state.current.anuncio.status, 'REMOVIDO', 'Synthetic server committed before the response fault.')
      assert.equal(deleteCount(entries), 1)
      assert.equal(await page.getByRole('button', { name: 'Salvar alterações', exact: true }).count(), 0)
      assert.equal(completedCount(entries), 0)
      assert.equal(entries().filter((item) => item.method === 'PATCH').length, 0)
      assert.equal((await page.evaluate(() => window.__events)).filter((item) => item.kind === 'success').length, 0)
    })
  }
  await scenario('contagem-antiga-servidor-encerra', async ({ page, state, entries }) => {
    await photoStep(page)
    assert.equal(await page.getByRole('button', { name: 'Remover mídia', exact: true }).count(), 2)
    state.current = media(1) // Another authorized request removed the other photo, outside this browser.
    state.current.anuncio.status = 'PUBLICADO' // Approval may also have happened after this browser loaded PENDENTE.
    await page.getByRole('button', { name: 'Remover mídia', exact: true }).first().click()
    await page.getByRole('alertdialog').getByText('Ao excluir a última foto, seu anúncio será encerrado. Deseja continuar?', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Excluir foto', exact: true }).click()
    await page.getByRole('heading', { name: 'Anúncio encerrado', exact: true }).waitFor()
    assert.equal(completedCount(entries), 0)
    await assertSingleInvalidation(page, entries)
  }, { count: 2 })
  await scenario('carregamento-antigo-outro-anuncio', async ({ page, state, entries }) => {
    await page.getByRole('button', { name: 'Trocar alvo sintético', exact: true }).click()
    await page.getByRole('heading', { name: 'Editar anúncio', exact: true }).waitFor()
    await photoStep(page)
    assert.equal(await page.getByRole('button', { name: 'Remover mídia', exact: true }).count(), 2)
    const oldResponse = page.waitForResponse((response) => response.url().endsWith('/anuncio-sintetico/midias'))
    state.readGate.resolve()
    await (await oldResponse).finished()
    await page.evaluate(() => new Promise(requestAnimationFrame))
    assert.equal(await page.getByRole('button', { name: 'Remover mídia', exact: true }).count(), 2)
    await page.getByRole('button', { name: 'Revise seu anúncio', exact: true }).click()
    await page.getByText('2 fotos selecionadas', { exact: true }).waitFor()
    assert.equal(deleteCount(entries), 0)
  }, { holdInitialRead: true })
  await scenario('upload-antigo-nao-contamina-outro-anuncio', async ({ page, state, entries }) => {
    await photoStep(page)
    await selectSyntheticPhotos(page, 1)
    state.uploadGate = deferred()
    await page.getByRole('button', { name: 'Enviar arquivos', exact: true }).click()
    await waitFor(() => entries().some((item) => item.path.endsWith('/anuncio-sintetico/midias/lote')), 'POST do anúncio antigo')
    await page.getByRole('button', { name: 'Trocar alvo sintético', exact: true }).click()
    await page.getByRole('heading', { name: 'Editar anúncio', exact: true }).waitFor()
    await photoStep(page)
    await page.getByText('Fotos: 2/4', { exact: true }).waitFor()
    state.uploadGate.resolve()
    await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 100)))
    assert.equal(await page.getByRole('button', { name: 'Remover pendente-1.jpg', exact: true }).count(), 0)
    assert.equal(await page.getByText('Fotos: 2/4', { exact: true }).count(), 1)
    assert.equal(entries().filter((item) => item.path.endsWith('/outro-anuncio/midias/lote')).length, 0)
  }, { uploadMode: 'success' })
  await scenario('patch-duplo-clique-tardio-nao-navega', async ({ page, state, entries }) => {
    state.patchGate = deferred()
    await page.getByRole('button', { name: 'Confirmação de identidade', exact: true }).click()
    await page.evaluate(() => {
      const save = [...document.querySelectorAll('button')].find((item) => item.textContent?.trim() === 'Salvar alterações')
      if (!save) throw Error('Botão Salvar alterações ausente')
      save.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }))
      save.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }))
    })
    await waitFor(() => entries().some((item) => item.method === 'PATCH'), 'PATCH após dois eventos de clique')
    assert.equal(entries().filter((item) => item.method === 'PATCH').length, 1)
    await page.getByRole('button', { name: 'Trocar alvo sintético', exact: true }).click()
    state.patchGate.resolve()
    await page.getByRole('button', { name: 'Fotos', exact: true }).waitFor({ state: 'visible' })
    await waitFor(async () => await page.getByRole('button', { name: 'Fotos', exact: true }).isEnabled(), 'liberação do novo editor')
    await photoStep(page)
    await page.getByText('Fotos: 2/4', { exact: true }).waitFor()
    await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 100)))
    assert.equal(entries().filter((item) => item.method === 'PATCH').length, 1)
    assert.equal((await page.evaluate(() => window.__events)).filter((event) => event.kind === 'navigate'
      || event.kind === 'success').length, 0, 'A resposta PATCH antiga não pode concluir o wizard novo.')
  })
  await scenario('upload-transitorio-sem-delete', async ({ page, state, entries }) => {
    await photoStep(page)
    const jpeg = await sharp({ create: { width: 2, height: 2, channels: 3, background: '#667788' } }).jpeg().toBuffer()
    const fileChooser = page.waitForEvent('filechooser')
    await page.getByRole('button', { name: 'Selecionar fotos do anúncio', exact: true }).click()
    await (await fileChooser).setFiles({ name: 'sintetica.jpg', mimeType: 'image/jpeg', buffer: jpeg })
    state.uploadGate = deferred()
    await page.getByRole('button', { name: 'Enviar arquivos', exact: true }).click()
    await waitFor(() => entries().some((entry) => entry.path.endsWith('/midias/lote')), 'upload real XHR iniciado')
    assert.equal(await page.getByRole('button', { name: 'Enviando...', exact: true }).isDisabled(), true)
    await page.getByRole('button', { name: 'Enviando...', exact: true }).click({ force: true })
    assert.equal(entries().filter((entry) => entry.path.endsWith('/midias/lote')).length, 1,
      'Double click while an upload is pending cannot launch a second POST.')
    assert.equal(await page.getByRole('button', { name: 'Remover mídia', exact: true }).isDisabled(), true)
    assert.equal(await page.getByRole('button', { name: 'Adicionar benefício', exact: true }).isDisabled(), true)
    assert.equal(await page.getByRole('button', { name: 'Confirmação de identidade', exact: true }).isDisabled(), true)
    state.uploadGate.resolve()
    await page.getByRole('button', { name: 'Tentar enviar novamente', exact: true }).click()
    await page.getByRole('button', { name: 'Tentar enviar novamente', exact: true }).waitFor()
    const uploads = entries().filter((entry) => entry.path.endsWith('/midias/lote'))
    assert.equal(uploads.length, 2)
    assert.ok(uploads[0].idempotencyKey)
    assert.equal(uploads[1].idempotencyKey, uploads[0].idempotencyKey)
    await page.getByText('sintetica.jpg: Arquivo pronto para envio.', { exact: true }).waitFor()
    assert.equal(deleteCount(entries), 0)
    assert.equal(completedCount(entries), 0)
    await page.goto('about:blank')
    assert.equal(deleteCount(entries), 0, 'Failure and unmount never compensate by DELETE.')
  })
  for (const options of [{ count: 2, validCount: 1 }, { count: 1, validCount: 0 }]) {
    await scenario(`identidade-valida-incerta-${options.count}-${options.validCount}`, async ({ page, entries }) => {
      await photoStep(page)
      await page.getByRole('button', { name: 'Remover mídia', exact: true }).first().click()
      await page.getByRole('heading', { name: 'Excluir foto?', exact: true }).waitFor()
      await page.getByText('Ao excluir a última foto, seu anúncio será encerrado. Deseja continuar?', { exact: true }).waitFor()
      await page.getByRole('button', { name: 'Cancelar', exact: true }).click()
      assert.equal(deleteCount(entries), 0)
    }, options)
  }
  for (const close of ['Fechar', 'Escape', 'fora']) {
    await scenario(`fechamento-sem-delete-${close}`, async ({ page, entries }) => {
      await photoStep(page)
      await page.getByRole('button', { name: 'Remover mídia', exact: true }).click()
      const dialog = page.getByRole('alertdialog')
      await dialog.waitFor()
      assert.equal(deleteCount(entries), 0)
      if (close === 'Fechar') await dialog.getByRole('button', { name: 'Fechar', exact: true }).click()
      else if (close === 'Escape') await page.keyboard.press('Escape')
      else await page.mouse.click(1, 1)
      await page.evaluate(() => new Promise(requestAnimationFrame))
      assert.equal(deleteCount(entries), 0)
      if (await dialog.isVisible()) await dialog.getByRole('button', { name: 'Cancelar', exact: true }).click()
      await dialog.waitFor({ state: 'hidden' })
      assert.equal(deleteCount(entries), 0)
    })
  }
  await scenario('video-nao-anuncia-encerramento', async ({ page, entries }) => {
    await photoStep(page)
    await page.getByRole('button', { name: 'Remover mídia', exact: true }).last().click()
    const dialog = page.getByRole('alertdialog')
    await dialog.getByRole('heading', { name: 'Excluir vídeo do anúncio?', exact: true }).waitFor()
    assert.doesNotMatch(await dialog.innerText(), /encerrado|última foto|trocar a foto/)
    assert.equal(deleteCount(entries), 0)
    await dialog.getByRole('button', { name: 'Excluir vídeo', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    assert.equal(deleteCount(entries), 1)
    assert.equal(await page.getByRole('heading', { name: 'Anúncio encerrado', exact: true }).count(), 0)
    assert.equal(await page.getByRole('button', { name: 'Remover mídia', exact: true }).count(), 1)
  }, { video: true })
  await scenario('substituta-local-nao-muda-confirmacao', async ({ page, entries }) => {
    await photoStep(page)
    const jpeg = await sharp({ create: { width: 2, height: 2, channels: 3, background: '#667788' } }).jpeg().toBuffer()
    const chooser = page.waitForEvent('filechooser')
    await page.getByRole('button', { name: 'Selecionar fotos do anúncio', exact: true }).click()
    await (await chooser).setFiles({ name: 'substituta-local.jpg', mimeType: 'image/jpeg', buffer: jpeg })
    await page.getByText('substituta-local.jpg: Arquivo pronto para envio.', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Remover mídia', exact: true }).click()
    await page.getByRole('heading', { name: 'Excluir a última foto?', exact: true }).waitFor()
    await page.getByRole('button', { name: 'Cancelar', exact: true }).click()
    await page.getByRole('button', { name: 'Remover substituta-local.jpg', exact: true }).click()
    assert.equal(await page.getByRole('alertdialog').count(), 0)
    assert.equal(deleteCount(entries), 0)
    assert.equal(entries().filter((item) => item.path.endsWith('/midias/lote')).length, 0)
  })
  await scenario('recusa-deterministica-nao-encerra', async ({ page, state, entries }) => {
    await photoStep(page)
    state.deleteFailure = 'rejected'
    await page.getByRole('button', { name: 'Remover mídia', exact: true }).click()
    await page.getByRole('button', { name: 'Excluir foto e encerrar anúncio', exact: true }).click()
    await page.getByText(/Exclusão recusada para este teste/).waitFor()
    assert.equal(deleteCount(entries), 1)
    assert.equal(await page.getByRole('heading', { name: 'Anúncio encerrado', exact: true }).count(), 0)
    assert.equal(state.current.anuncio.status, 'PENDENTE_REVISAO')
    assert.equal(completedCount(entries), 0)
  })
  assert.equal(results.length, 35, 'Preserve the original scenarios and the editor hotfix regressions.')
  assert.deepEqual(unexpected, [], 'Nenhuma consulta externa ou rota não declarada é aceita.')
} catch (error) {
  failure = error
} finally {
  if (browser) { try { await bounded(browser.close(), 'browser') } catch (error) { cleanupErrors.push(String(error)) } }
  if (server) {
    try {
      server.closeAllConnections()
      await bounded(new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())), 'loopback server')
      assert.equal(server.listening, false)
    } catch (error) { cleanupErrors.push(String(error)) }
  }
  if (compiler) { try { await bounded(new Promise((resolve, reject) => compiler.close((error) => error ? reject(error) : resolve())), 'webpack') } catch (error) { cleanupErrors.push(String(error)) } }
  write('outcome.json', JSON.stringify({ result: !failure && !cleanupErrors.length ? 'PASS' : 'FAIL', cases: results, failure: failure?.stack, cleanupErrors, unexpected, browserErrors, requests, realReactDOM: true, syntheticTransportOnly: true, evidence }, null, 2))
  console.log(`ULTIMA_FOTO_DOM_EVIDENCE=${evidence}`)
}
if (failure) throw failure
assert.deepEqual(cleanupErrors, [])
console.log(`ULTIMA_FOTO_DOM_RESULT=PASS cases=${results.length} realReactDOM=true externalRequests=0 cleanup=PASS`)
