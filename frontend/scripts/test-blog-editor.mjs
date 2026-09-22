import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

// Real form, React, admin API client, error handling, Radix preview and safe body.
// HTTP transport and the Next server action are synthetic boundaries. This does
// not prove backend authorization, storage or a production publication.
export async function verifyBlogEditor() {
  const require = createRequire(import.meta.url)
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  const source = path.join(root, 'src')
  const baseline = process.argv.includes('--baseline')
  const cacheRetryOnly = process.argv.includes('--cache-retry-only')
  const evidence = fs.mkdtempSync(path.join(process.env.TOPS_UI_EVIDENCE_ROOT || os.tmpdir(), baseline ? 'blog-editor-before-' : 'blog-editor-after-'))
  const write = (name, value) => fs.writeFileSync(path.join(evidence, name), value, { flag: 'wx' })
  const json = (name, value) => write(name, JSON.stringify(value, null, 2))
  const digest = bytes => createHash('sha256').update(bytes).digest('hex')
  const formPath = 'src/app/(painel-admin)/admin/blog/components/blog-post-form.tsx'
  const categoriesPath = 'src/app/(painel-admin)/admin/blog/categorias/page.tsx'
  const listPath = 'src/app/(painel-admin)/admin/blog/page.tsx'
  const rendererPath = 'src/lib/blog/safe-blog-body.tsx'
  const editedSources = [formPath, categoriesPath, listPath, rendererPath]
  const inputs = [...editedSources, 'src/lib/admin-blog-api.ts', 'src/lib/api-contract.ts',
    'src/components/feedback/contract-state.tsx',
    'src/components/ui/dialog.tsx', 'src/app/globals.css', 'package-lock.json',
    'scripts/test-blog-v3.mjs', 'scripts/test-blog-editor.mjs']
  const hashes = () => Object.fromEntries(inputs.map(name => [name, digest(fs.readFileSync(path.join(root, name)))]))
  const beforeHashes = hashes()
  json('inputs-before.json', beforeHashes)
  const overrides = {}
  if (baseline && (process.env.TOPS_BLOG_BASE_DIR || process.env.TOPS_BLOG_BASE_REF)) {
    for (const name of editedSources) overrides[path.join(root, name)] = process.env.TOPS_BLOG_BASE_DIR
      ? fs.readFileSync(path.join(process.env.TOPS_BLOG_BASE_DIR, name.slice('src/'.length)), 'utf8')
      : execFileSync('git', ['show', `${process.env.TOPS_BLOG_BASE_REF}:frontend/${name}`], { cwd: root, encoding: 'utf8' })
  }
  json('source-overrides.json', overrides)
  json('tested-inputs.json', { mode: baseline ? 'baseline-observation-not-approval' : 'regression',
    selection: cacheRetryOnly ? 'cache-retry-only' : 'all',
    baseRef: process.env.TOPS_BLOG_BASE_REF || null,
    formSha256: digest(overrides[path.join(root, formPath)] ?? fs.readFileSync(path.join(root, formPath))),
    testedSourceHashes: Object.fromEntries(editedSources.map(name => [name, digest(overrides[path.join(root, name)] ?? fs.readFileSync(path.join(root, name)))])),
    sourceHashes: beforeHashes, syntheticTransport: true, syntheticServerAction: true, realAndroid: false })
  const playwrightModule = process.env.TOPS_PLAYWRIGHT_MODULE || 'playwright'
  const { chromium } = require(playwrightModule)
  const playwrightVersion = createRequire(require.resolve(playwrightModule))('./package.json').version
  const bundled = require('next/dist/compiled/webpack/webpack')
  bundled.init()
  const { webpack } = bundled
  const postcss = require('postcss'), tailwind = require('@tailwindcss/postcss')
  const results = [], unexpected = [], browserErrors = [], cleanupErrors = []
  const contexts = new Set()
  const cleanup = { contextsClosed: 0, browserClosed: false, serverClosed: false, compilerClosed: false }
  let compiler, browser, server, failure
  async function closeResource(resource, operation, complete) {
    let timer
    try {
      await Promise.race([operation(), new Promise((_, reject) => { timer = setTimeout(() => reject(Error('Cleanup timeout: ' + resource)), 10000) })])
      complete()
    } catch (error) { cleanupErrors.push({ resource, error: error.stack }) }
    finally { clearTimeout(timer) }
  }
  const content = '<h2>Conteúdo editorial sintético</h2><p>AT&amp;T &copy; 2026</p><p><a href="/blog/exemplo?a=1&amp;b=2">Link &amp; interno</a></p><p>'
    + 'Texto completo para revisão, sem dados reais. '.repeat(35) + '</p><p>MARCADOR-FINAL-INTEGRAL</p>'
  const category = { id: 'categoria-sintetica', nome: 'Categoria sintética', slug: 'categoria-sintetica', ativa: true, ordem: 1, versao: 2, postCountPublicados: 0 }
  const draft = { id: 'post-sintetico', titulo: 'Post editorial sintético', slug: 'post-editorial-sintetico', categoriaId: category.id,
    categoria: category.nome, categoriaSlug: category.slug, autorNome: 'Equipe sintética', resumo: 'Resumo sintético para conferência.',
    conteudo: content, status: 'RASCUNHO', versao: 7, imagemUrl: null, imagemCapaId: null, ogImageUrl: null, imagemOgId: null,
    seoTitle: 'Título sintético', seoDescription: 'Descrição sintética', sitemapPriority: 0.7, changeFrequency: 'weekly' }
  write('loader.cjs', `const fs=require('node:fs');const overrides=JSON.parse(fs.readFileSync(${JSON.stringify(path.join(evidence, 'source-overrides.json'))},'utf8'));const ts=require(${JSON.stringify(require.resolve('typescript'))});module.exports=function(source){return ts.transpileModule(overrides[this.resourcePath]??source,{fileName:this.resourcePath,compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext,jsx:ts.JsxEmit.ReactJSX}}).outputText}`)
  write('navigation.js', `export const useRouter=()=>({replace(url){window.__navigation.push({method:'replace',url});window.__remountEditor?.(url)},push(url){window.__navigation.push({method:'push',url})},refresh(){window.__navigation.push({method:'refresh'})}});`)
  write('link.tsx', `import React from 'react';export default function Link({href,children,...props}){return <a href={href} {...props}>{children}</a>}`)
  write('server-actions.js', `export async function revalidarBlogPublico(slug){
    const call={slug,synthetic:true,state:'started'};window.__revalidation.push(call);
    try{
      if(window.__revalidationFailures>0){window.__revalidationFailures--;throw new Error('Falha sintética na revalidação pública')}
      if(window.__fixture.deferCacheRetry&&window.__revalidation.length===2){
        call.state='pending';await new Promise((resolve,reject)=>{window.__releaseCacheRetry=outcome=>{
          delete window.__releaseCacheRetry;outcome==='success'?resolve():reject(new Error('Falha sintética na retomada da revalidação'))
        }});
      }
      call.state='succeeded';return {ok:true};
    }catch(error){call.state='failed';throw error}
  }`)
  write('entry.tsx', `import React,{useEffect,useState} from 'react';import{createRoot}from'react-dom/client';import{Toaster}from'sonner';
    import{BlogPostForm}from ${JSON.stringify(path.join(root, formPath))};
    import CategoriesPage from ${JSON.stringify(path.join(root, categoriesPath))};import ListPage from ${JSON.stringify(path.join(root, listPath))};
    window.__navigation=[];window.__revalidation=[];window.__revalidationFailures=window.__fixture.revalidationFailures||0;
    window.__editorGeneration=0;window.__postLoadFinishedGeneration=-1;
    function Fixture(){
      const[editor,setEditor]=useState({mode:window.__fixture.mode,generation:0});
      useEffect(()=>{window.__remountEditor=url=>{
        if(!window.__fixture.remountOnReplace)return;
        if(url!=='/admin/blog/post-sintetico/editar')throw new Error('Unexpected editor destination');
        const generation=++window.__editorGeneration;
        setEditor({mode:'edit',generation});
      };return()=>{delete window.__remountEditor}},[]);
      return <>{window.__fixture.view==='categories'?<CategoriesPage/>:window.__fixture.view==='list'?<ListPage/>:
        <div data-fixture-editor-generation={editor.generation}><BlogPostForm key={editor.generation} mode={editor.mode} postId={editor.mode==='edit'?'post-sintetico':undefined}/></div>}<Toaster/></>;
    }
    createRoot(document.getElementById('root')).render(<Fixture/>);window.__ready=true;
  `)
  try {
    const cssFile = path.join(source, 'app/globals.css')
    const css = await postcss([tailwind({ base: root })]).process(fs.readFileSync(cssFile, 'utf8'), { from: cssFile })
    write('styles.css', css.css)
    compiler = webpack({ mode: 'development', target: 'web', devtool: false, context: root,
      entry: path.join(evidence, 'entry.tsx'), output: { path: evidence, filename: 'bundle.js' },
      resolve: { extensions: ['.tsx', '.ts', '.js'], modules: [path.join(root, 'node_modules')], alias: {
        '@': source, 'next/navigation$': path.join(evidence, 'navigation.js'), 'next/link$': path.join(evidence, 'link.tsx'),
      } },
      module: { rules: [{ test: /\.[jt]sx?$/, exclude: /node_modules/, use: path.join(evidence, 'loader.cjs') }] },
      plugins: [new webpack.DefinePlugin({ 'process.env': JSON.stringify({ NODE_ENV: 'development', NEXT_PUBLIC_API_URL: '/api/public', NEXT_PUBLIC_SITE_URL: 'http://fixture.invalid' }) }),
        new webpack.NormalModuleReplacementPlugin(/^\.\.?\/actions$/, resource => {
          if (editedSources.some(name => resource.context === path.dirname(path.join(root, name)))) resource.request = path.join(evidence, 'server-actions.js')
        })],
    })
    const stats = await new Promise((resolve, reject) => compiler.run((error, value) => error ? reject(error) : resolve(value)))
    json('webpack.json', stats.toJson({ all: false, errors: true, warnings: true }))
    assert.equal(stats.hasErrors(), false, stats.toString({ all: false, errors: true }))
    const bundle = fs.readFileSync(path.join(evidence, 'bundle.js'))
    server = http.createServer((request, response) => {
      const routes = {
        '/': ['text/html; charset=utf-8', '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/styles.css"></head><body><main class="mx-auto max-w-6xl p-4"><div id="root"></div></main><script src="/bundle.js"></script></body></html>'],
        '/bundle.js': ['text/javascript', bundle], '/styles.css': ['text/css', css.css],
      }
      if (request.url === '/favicon.ico') { response.writeHead(204); response.end(); return }
      const route = routes[request.url]
      if (!route) { unexpected.push({ boundary: 'server', url: request.url }); response.writeHead(405); response.end(); return }
      response.setHeader('Content-Type', route[0]); response.end(route[1])
    })
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
    const origin = `http://127.0.0.1:${server.address().port}`
    browser = await chromium.launch({ headless: true })
    async function scenario(name, config, run) {
      if (cacheRetryOnly && !name.startsWith('revalidation-after-create-')) return
      const result = { name, checks: [], observations: [], requests: [], syntheticServerAction: true }
      results.push(result)
      const context = await browser.newContext({ viewport: { width: config.mobile ? 390 : 1366, height: config.mobile ? 844 : 900 },
        hasTouch: Boolean(config.mobile), isMobile: Boolean(config.mobile), deviceScaleFactor: 1, serviceWorkers: 'block' })
      contexts.add(context)
      let stored = { ...draft, status: config.view === 'list' ? 'PUBLICADO' : draft.status }, publicationAttempts = 0
      let storedCategories = [category]
      let page
      const trackedRequests = new WeakMap()
      // Observe body completion before any gesture. When the router boundary
      // remounts the real editor, its own GET reloads only the persisted post.
      context.on('requestfinished', async request => {
        const entry = trackedRequests.get(request)
        if (!entry) return
        entry.finished = true
        if (config.remountOnReplace && entry.method === 'GET' && entry.path === '/api/admin/blog-posts/post-sintetico') {
          try {
            await page.evaluate(() => { window.__postLoadFinishedGeneration = window.__editorGeneration })
          } catch (error) {
            if (!page.isClosed()) browserErrors.push({ name, message: error.message })
          }
        }
      })
      await context.addInitScript(value => { window.__fixture = value }, { mode: config.mode || 'edit', view: config.view || 'editor',
        revalidationFailures: config.revalidationFailures || 0, deferCacheRetry: Boolean(config.deferCacheRetry), remountOnReplace: Boolean(config.remountOnReplace) })
      await context.addCookies([{ name: 'XSRF-TOKEN', value: 'EXEMPLO_NAO_REAL', url: origin }])
      // Before first Page/navigation; Analytics SDK is not part of this bundle.
      await context.route('**/*', async route => {
        const request = route.request(), url = new URL(request.url()), method = request.method()
        if (url.origin !== origin) { unexpected.push({ boundary: 'browser', url: request.url() }); return route.abort() }
        if (!url.pathname.startsWith('/api/')) return route.continue()
        const entry = { method, path: url.pathname, body: request.postData() ? JSON.parse(request.postData()) : null }
        result.requests.push(entry)
        trackedRequests.set(request, entry)
        const respond = (status, body) => { entry.status = status; return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) }) }
        if (method !== 'GET') {
          const headers = await request.allHeaders()
          assert.equal(headers['x-xsrf-token'], 'EXEMPLO_NAO_REAL')
          const cookies = new Map((headers.cookie || '').split('; ').map(cookie => cookie.split('=')))
          assert.equal(cookies.get('XSRF-TOKEN'), 'EXEMPLO_NAO_REAL')
        }
        if (method === 'GET' && url.pathname === '/api/admin/blog-categorias') return respond(200, storedCategories)
        if (method === 'POST' && url.pathname === '/api/admin/blog-categorias') {
          const created = { ...category, ...entry.body, id: 'categoria-criada', versao: 1 }
          storedCategories = [...storedCategories, created]; return respond(200, created)
        }
        if (method === 'PUT' && url.pathname === '/api/admin/blog-categorias/categoria-criada') {
          const updated = { ...storedCategories.find(item => item.id === 'categoria-criada'), ...entry.body, versao: 2 }
          storedCategories = storedCategories.map(item => item.id === updated.id ? updated : item); return respond(200, updated)
        }
        if (method === 'GET' && url.pathname === '/api/admin/blog-posts') return respond(200, [stored])
        if (method === 'GET' && url.pathname === '/api/admin/blog-posts/post-sintetico') return config.loadFailure
          ? respond(config.loadFailure, { message: 'Falha sintética ao carregar o post.' }) : respond(200, stored)
        if (method === 'POST' && url.pathname === '/api/admin/blog-posts') {
          stored = { ...draft, ...entry.body, id: 'post-sintetico', status: 'RASCUNHO', versao: 1 }
          return respond(200, stored)
        }
        if (method === 'PUT' && url.pathname === '/api/admin/blog-posts/post-sintetico') {
          if (config.saveFailure) return respond(config.saveFailure, { message: 'Falha sintética ao salvar o post.' })
          stored = { ...stored, ...entry.body, versao: stored.versao + 1 }; return respond(200, stored)
        }
        if (method === 'POST' && url.pathname === '/api/admin/blog-posts/post-sintetico/publicar') {
          publicationAttempts++
          if (config.rejectFirstPublication && publicationAttempts === 1) return respond(422, { message: 'Publicação sintética recusada.' })
          stored = { ...stored, status: 'PUBLICADO', versao: stored.versao + 1 }; return respond(200, stored)
        }
        if (method === 'POST' && url.pathname === '/api/admin/blog-posts/post-sintetico/retirar') {
          stored = { ...stored, status: 'RASCUNHO', versao: stored.versao + 1 }; return respond(200, stored)
        }
        unexpected.push(entry); return route.abort()
      })
      page = await context.newPage()
      page.setDefaultTimeout(5000)
      page.on('pageerror', error => browserErrors.push({ name, message: error.message }))
      const check = (label, condition) => { result.checks.push({ label, passed: Boolean(condition) }) }
      async function snapshot(label) {
        const observed = await page.evaluate(() => ({
          text: document.body.innerText, content: document.querySelector('#post-content')?.value ?? null,
          title: document.querySelector('#post-title')?.value ?? null,
          categoryName: document.querySelector('#category-name')?.value ?? null,
          alerts: [...document.querySelectorAll('[role="alert"],[role="status"]')].map(node => node.textContent),
          successToasts: [...document.querySelectorAll('[data-sonner-toast][data-type="success"]')].map(node => node.textContent),
          navigation: window.__navigation, revalidation: window.__revalidation,
          editorGeneration: window.__editorGeneration, postLoadFinishedGeneration: window.__postLoadFinishedGeneration,
          mountedEditorGeneration: document.querySelector('[data-fixture-editor-generation]')?.getAttribute('data-fixture-editor-generation'),
          analyticsAbsent: typeof window.gtag === 'undefined' && !document.querySelector('script[src*="googletagmanager"],script[src*="google-analytics"]'),
          viewport: { width: innerWidth, height: innerHeight, touch: navigator.maxTouchPoints },
        }))
        const screenshot = `${name}-${label}.png`
        await page.screenshot({ path: path.join(evidence, screenshot), fullPage: true })
        result.observations.push({ label, ...observed, screenshot, screenshotSha256: digest(fs.readFileSync(path.join(evidence, screenshot))) })
        check(label + ': Analytics absent', observed.analyticsAbsent)
        return observed
      }
      async function settle() {
        await page.waitForFunction(() => !/Carregando(?: post)?\.\.\.|Processando\.\.\.|Salvando\.\.\./.test(document.body.innerText))
        await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
        if (config.remountOnReplace) {
          const generation = await page.evaluate(() => window.__editorGeneration)
          if (generation > 0) {
            await page.waitForFunction(expected => window.__postLoadFinishedGeneration === expected
              && document.querySelector('[data-fixture-editor-generation]')?.getAttribute('data-fixture-editor-generation') === String(expected)
              && Boolean(document.querySelector('#post-title')), generation)
          }
        }
      }
      async function click(label) {
        const response = page.waitForResponse(value => value.url().startsWith(origin + '/api/admin/blog-') && value.request().method() !== 'GET')
        await page.getByRole('button', { name: label, exact: true }).click()
        await response
        await settle()
      }
      try {
        const responses = [page.waitForResponse(response => response.url().endsWith(config.view === 'list' ? '/api/admin/blog-posts' : '/api/admin/blog-categorias'))]
        if (!config.view && config.mode !== 'create') responses.push(page.waitForResponse(response => response.url().endsWith('/api/admin/blog-posts/post-sintetico')))
        await page.goto(origin)
        await Promise.all(responses)
        await page.waitForFunction(() => window.__ready)
        await settle()
        await run({ page, check, snapshot, click, settle, requests: result.requests })
        result.result = result.checks.every(item => item.passed) ? 'PASS' : baseline ? 'BASELINE_DEFECT_OBSERVED' : 'FAIL'
      } catch (error) {
        result.result = 'ERROR'; result.error = error.stack
        await page.screenshot({ path: path.join(evidence, name + '-failure.png'), fullPage: true }).catch(() => {})
      } finally {
        await closeResource('context:' + name, () => context.close(), () => { contexts.delete(context); cleanup.contextsClosed++ })
      }
    }
    for (const status of [404, 503]) await scenario('load-' + status, { loadFailure: status }, async ({ page, check, snapshot, requests }) => {
      const observed = await snapshot('loaded')
      check('load failure shown', /carregar/i.test(observed.text))
      check('no editable form', await page.locator('#post-title,#post-content').count() === 0)
      check('no create or update mutation', requests.every(item => item.method === 'GET'))
    })
    await scenario('save-503', { saveFailure: 503 }, async ({ page, check, snapshot, click, requests }) => {
      const changed = content + '<p>EDIÇÃO-PRESERVADA</p>'
      await page.locator('#post-content').fill(changed)
      await click('Salvar rascunho')
      const observed = await snapshot('rejected')
      check('save failure distinguished from load', /salvar/i.test(observed.alerts.join(' ')) && !/carregar/i.test(observed.alerts.join(' ')))
      check('unsaved complete body preserved', observed.content === changed)
      check('no false success', observed.successToasts.length === 0)
      check('one failing PUT only', requests.filter(item => item.method === 'PUT').length === 1 && requests.every(item => item.method !== 'POST'))
    })
    async function fillNew(page) {
      await page.locator('#post-title').fill(draft.titulo)
      await page.locator('#post-content').fill(content)
      await page.getByRole('combobox').first().click()
      await page.getByRole('option', { name: category.nome }).click()
    }
    await scenario('create-publish-rejected-resume', { mode: 'create', rejectFirstPublication: true }, async ({ page, check, snapshot, click, requests }) => {
      await fillNew(page)
      await click('Publicar')
      const rejected = await snapshot('publication-rejected')
      check('saved draft visible after publication refusal', /Status:\s*RASCUNHO/.test(rejected.text))
      check('complete body retained', rejected.content === content)
      check('publication refusal is not success', rejected.successToasts.length === 0)
      // The editor corrects the rejected publication input; this is not an
      // automatic retry or a claim that repeating an invalid request fixes it.
      await page.locator('#post-summary').fill(draft.resumo)
      await page.locator('#seo-title').fill(draft.seoTitle)
      await page.locator('#seo-description').fill(draft.seoDescription)
      await click('Publicar')
      const resumed = await snapshot('resumed')
      const updates = requests.filter(item => item.method === 'PUT')
      check('resume keeps saved ID and version', updates.length === 1 && updates[0].path.endsWith('/post-sintetico') && updates[0].body.versao === 1)
      check('no duplicate create', requests.filter(item => item.method === 'POST' && item.path === '/api/admin/blog-posts').length === 1)
      check('all mutations retain full body', requests.filter(item => item.body?.conteudo).every(item => item.body.conteudo === content))
      check('resumed publication succeeds', /Status:\s*PUBLICADO/.test(resumed.text) && resumed.successToasts.some(text => /Post publicado com sucesso/.test(text)))
      check('resumed creation navigates to saved editor', resumed.navigation.some(item => item.method === 'replace' && item.url === '/admin/blog/post-sintetico/editar'))
    })
    await scenario('publish-withdraw', {}, async ({ check, snapshot, click, requests }) => {
      await click('Publicar')
      const published = await snapshot('published')
      check('publication confirmed', /Status:\s*PUBLICADO/.test(published.text) && published.successToasts.some(text => /Post publicado com sucesso/.test(text)))
      await click('Retirar de publicação')
      const withdrawn = await snapshot('withdrawn')
      check('withdrawal confirmed', /Status:\s*RASCUNHO/.test(withdrawn.text) && withdrawn.successToasts.some(text => /Post retirado da publicação/.test(text)))
      check('one publish and one withdrawal', requests.filter(item => item.path.endsWith('/publicar')).length === 1 && requests.filter(item => item.path.endsWith('/retirar')).length === 1)
    })
    await scenario('draft-not-published', {}, async ({ check, snapshot, click, requests }) => {
      await click('Salvar rascunho')
      const observed = await snapshot('saved')
      check('draft saved only', /Status:\s*RASCUNHO/.test(observed.text) && observed.successToasts.some(text => /Rascunho salvo/.test(text)))
      check('no published success', !observed.successToasts.some(text => /publicad/i.test(text)))
      check('no publication request', !requests.some(item => item.path.endsWith('/publicar')))
    })
    for (const retryOutcome of ['success', 'failure']) await scenario('revalidation-after-create-' + retryOutcome,
      { mode: 'create', revalidationFailures: 1, deferCacheRetry: true, remountOnReplace: true }, async ({ page, check, snapshot, click, settle, requests }) => {
      await fillNew(page)
      await click('Salvar rascunho')
      const observed = await snapshot('revalidation-failed')
      check('committed state retained', /Status:\s*RASCUNHO/.test(observed.text) && observed.content === content)
      check('post-commit failure distinguished', /salv|conclu/i.test(observed.alerts.join(' ')) && /atualiza|revalida|cache/i.test(observed.alerts.join(' ')) && !/carregar/i.test(observed.alerts.join(' ')))
      check('single creation despite revalidation failure', requests.filter(item => item.method === 'POST').length === 1 && requests.filter(item => item.method === 'PUT').length === 0)
      check('one failed server action', observed.revalidation.length === 1)
      check('no premature success', observed.successToasts.length === 0)
      const retry = page.getByRole('button', { name: 'Atualizar exibição pública', exact: true })
      const retryExists = await retry.count() === 1
      check('dedicated cache retry available', retryExists)
      if (retryExists) {
        const titleBefore = draft.titulo + ' — edição anterior à retomada'
        const bodyBefore = content + '<p>EDIÇÃO-ANTES-DO-RETRY</p>'
        await page.locator('#post-title').fill(titleBefore)
        await page.locator('#post-content').fill(bodyBefore)
        const beforeRetry = await snapshot('edited-before-retry')
        check('local edits exist before retry', beforeRetry.title === titleBefore && beforeRetry.content === bodyBefore)
        await retry.click()
        await page.waitForFunction(() => window.__revalidation[1]?.state === 'pending' && typeof window.__releaseCacheRetry === 'function')
        const pending = await snapshot('retry-pending')
        check('retry starts without replacing edits', pending.title === titleBefore && pending.content === bodyBefore)
        const titleDuring = draft.titulo + ' — edição durante a retomada'
        const bodyDuring = bodyBefore + '<p>EDIÇÃO-DURANTE-DO-RETRY</p>'
        await page.locator('#post-title').fill(titleDuring)
        await page.locator('#post-content').fill(bodyDuring)
        const editedPending = await snapshot('edited-while-retry-pending')
        check('edits remain possible during pending revalidation', editedPending.title === titleDuring && editedPending.content === bodyDuring)
        await page.evaluate(outcome => window.__releaseCacheRetry(outcome), retryOutcome)
        await page.waitForFunction(expected => window.__revalidation[1]?.state === expected, retryOutcome === 'success' ? 'succeeded' : 'failed')
        await settle()
        const retried = await snapshot('cache-only-retry')
        check('retry is only revalidation', retried.revalidation.length === 2 && requests.filter(item => item.method !== 'GET').length === 1)
        check('retry does not navigate away from local changes', retried.navigation.length === 0 && retried.editorGeneration === 0)
        check('title edited during retry survives its result', retried.title === titleDuring)
        check('complete body edited during retry survives its result', retried.content === bodyDuring)
        check('cache retry does not claim a new save', retried.successToasts.length === 0)
        if (retryOutcome === 'failure') check('failed retry keeps cache error visible', /atualiza|revalida/i.test(retried.alerts.join(' ')))
        const mutationsBeforeSave = requests.filter(item => item.method !== 'GET')
        check('no hidden update or publication during retry', mutationsBeforeSave.length === 1
          && mutationsBeforeSave[0].method === 'POST' && mutationsBeforeSave[0].path === '/api/admin/blog-posts')
        await click('Salvar rascunho')
        const saved = await snapshot('explicit-save-after-retry')
        const updates = requests.filter(item => item.method === 'PUT')
        check('explicit save reuses committed ID and version', updates.length === 1
          && updates[0].path === '/api/admin/blog-posts/post-sintetico' && updates[0].body.versao === 1)
        check('explicit save contains the latest local edits', updates.length === 1
          && updates[0].body.titulo === titleDuring && updates[0].body.conteudo === bodyDuring)
        check('saved editor retains complete latest text', saved.title === titleDuring && saved.content === bodyDuring)
        check('no duplicate create or unintended publication', requests.filter(item => item.method === 'POST').length === 1)
      }
    })
    await scenario('category-create-revalidation', { view: 'categories', revalidationFailures: 1 }, async ({ page, check, snapshot, click, settle, requests }) => {
      await page.locator('#category-name').fill('Categoria criada sintética')
      await click('Nova categoria')
      const observed = await snapshot('saved-refresh-failed')
      check('category commit distinguished from failure', /Categoria salva/.test(observed.alerts.join(' ')) && !/carregar/i.test(observed.alerts.join(' ')))
      check('category saved identity retained for editing', await page.getByRole('button', { name: 'Salvar alterações', exact: true }).count() === 1)
      check('category form preserved', observed.categoryName === 'Categoria criada sintética')
      check('no premature category success', observed.successToasts.length === 0)
      const retry = page.getByRole('button', { name: 'Atualizar exibição pública', exact: true })
      check('category cache-only retry available', await retry.count() === 1)
      if (await retry.count() === 1) {
        await retry.click(); await settle()
        const retried = await snapshot('cache-only-retry')
        check('category retry does not repeat mutation', retried.revalidation.length === 2 && requests.filter(item => item.method !== 'GET').length === 1)
        await click('Salvar alterações')
        const updates = requests.filter(item => item.method === 'PUT')
        check('next explicit category save uses saved ID and version', updates.length === 1 && updates[0].path.endsWith('/categoria-criada') && updates[0].body.versao === 1)
        check('category creation happens once', requests.filter(item => item.method === 'POST').length === 1)
      }
    })
    await scenario('list-withdraw-revalidation', { view: 'list', revalidationFailures: 1 }, async ({ page, check, snapshot, click, settle, requests }) => {
      await click('Retirar')
      const observed = await snapshot('withdrawn-refresh-failed')
      check('list shows committed draft status', await page.getByRole('cell', { name: 'RASCUNHO', exact: true }).count() === 1)
      check('list does not expose draft public link', await page.getByRole('link', { name: 'Ver', exact: true }).count() === 0)
      check('list commit distinguished from failure', /Alteração salva/.test(observed.alerts.join(' ')) && !/carregar/i.test(observed.alerts.join(' ')))
      check('no premature withdrawal success', observed.successToasts.length === 0)
      const retry = page.getByRole('button', { name: 'Atualizar exibição pública', exact: true })
      check('list cache-only retry available', await retry.count() === 1)
      if (await retry.count() === 1) {
        await retry.click(); await settle()
        const retried = await snapshot('cache-only-retry')
        check('list cache retry does not repeat withdrawal', retried.revalidation.length === 2 && requests.filter(item => item.method !== 'GET').length === 1)
      }
    })
    for (const mobile of [false, true]) await scenario(mobile ? 'preview-mobile' : 'preview-desktop', { mobile }, async ({ page, check, snapshot }) => {
      const button = page.getByRole('button', { name: 'Visualizar', exact: true })
      if (mobile) await button.tap(); else await button.click()
      await page.getByRole('dialog').waitFor()
      const observed = await snapshot('open')
      const layout = await page.getByRole('dialog').evaluate(node => {
        const rect = node.getBoundingClientRect()
        return { width: rect.width, height: rect.height, left: rect.left, right: rect.right,
          horizontalOverflow: node.scrollWidth > node.clientWidth || document.documentElement.scrollWidth > innerWidth,
          renderedBody: node.querySelector('article')?.textContent, scripts: node.querySelectorAll('script').length,
          links: [...node.querySelectorAll('article a')].map(link => ({ href: link.getAttribute('href'), text: link.textContent })) }
      })
      check('actual preview complete', layout.renderedBody?.includes('MARCADOR-FINAL-INTEGRAL'))
      check('preview within viewport without horizontal overflow', layout.left >= -1 && layout.right <= observed.viewport.width + 1 && !layout.horizontalOverflow)
      check('no script in rendered body', layout.scripts === 0)
      check('HTML entities render as intended text', layout.renderedBody?.includes('AT&T © 2026'))
      check('internal link entity preserved semantically', layout.links.some(link => link.href === '/blog/exemplo?a=1&b=2' && link.text === 'Link & interno'))
      if (mobile) check('mobile touch emulated', observed.viewport.touch > 0)
      results.at(-1).layout = layout
    })
    assert.deepEqual(unexpected, [], 'No external or unplanned requests')
    assert.deepEqual(browserErrors, [], 'No browser runtime errors')
    assert.equal(results.filter(item => item.result === 'ERROR').length, 0, 'Harness errors; see result.json')
    assert.equal(results.filter(item => item.result === 'FAIL').length, 0, 'Regression failed; see result.json')
  } catch (error) { failure = error }
  finally {
    for (const context of contexts) await closeResource('remaining-context', () => context.close(), () => { contexts.delete(context); cleanup.contextsClosed++ })
    if (browser) await closeResource('browser', () => browser.close(), () => { cleanup.browserClosed = true })
    if (server) await closeResource('server', () => new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve())), () => { cleanup.serverClosed = true })
    if (compiler) await closeResource('compiler', () => new Promise((resolve, reject) => compiler.close(error => error ? reject(error) : resolve())), () => { cleanup.compilerClosed = true })
    const afterHashes = hashes()
    json('inputs-after.json', afterHashes)
    try { assert.deepEqual(afterHashes, beforeHashes, 'Source inputs changed during execution') } catch (error) { failure ||= error }
    if (!failure && cleanupErrors.length) failure = Error('Cleanup failed')
    json('result.json', { result: failure ? 'FAIL' : baseline ? 'BASELINE_RECORDED_NOT_APPROVED' : 'PASS', baseline,
      selection: cacheRetryOnly ? 'cache-retry-only' : 'all',
      browser: browser?.version(), node: process.version, playwright: playwrightVersion,
      syntheticTransport: true, syntheticServerAction: true, realAndroid: false, results, unexpected, browserErrors, cleanup, cleanupErrors, failure: failure?.stack })
  }
  console.log('BLOG_EDITOR_BROWSER_EVIDENCE=' + evidence)
  if (failure) throw failure
  console.log(baseline ? 'BLOG_EDITOR_BASELINE_RECORDED_NOT_APPROVED' : 'BLOG_EDITOR_BROWSER_RESULT=OK cases=' + results.length)
}
