import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { randomUUID } from 'node:crypto'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

// Real React modal, API, status cache and UI primitives; only HTTP and toast are synthetic.
const require = createRequire(import.meta.url)
const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = path.join(frontend, 'src')
const evidence = fs.mkdtempSync(path.join(process.env.TOPS_UI_EVIDENCE_ROOT || os.tmpdir(), 'visitor-document-resume-'))
const write = (name, value) => fs.writeFileSync(path.join(evidence, name), value, { flag: 'wx' })
const { chromium } = require(process.env.TOPS_PLAYWRIGHT_MODULE || 'playwright')
const bundled = require('next/dist/compiled/webpack/webpack')
bundled.init()
const { webpack } = bundled
const results = [], unexpected = [], errors = []
let compiler, browser, server

write('loader.cjs', `const ts=require(${JSON.stringify(require.resolve('typescript'))});module.exports=function(source){return ts.transpileModule(source,{fileName:this.resourcePath,compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext,jsx:ts.JsxEmit.ReactJSX}}).outputText}`)
write('toast.js', 'export const toast={success(message){window.__toasts.push(message)}};')
write('entry.tsx', `
import React,{useState} from 'react';
import {createRoot} from 'react-dom/client';
import {VisitorVerificationModal} from ${JSON.stringify(process.env.TOPS_VISITOR_MODAL_SOURCE || path.join(source, 'components/compliance/visitor-verification-modal.tsx'))};
window.__verified=[];window.__toasts=[];
function App(){const[open,setOpen]=useState(false);const[ad,setAd]=useState('synthetic-ad');window.__changeContext=setAd;return <><button onClick={()=>setOpen(true)}>Abrir verificacao</button><VisitorVerificationModal open={open} onOpenChange={setOpen} onVerified={status=>window.__verified.push(status)} context={{anuncioId:ad,route:'/anuncios/'+ad}}/></>}
createRoot(document.getElementById('root')).render(<App/>);window.__ready=true;
`)

const denied = () => ({ globalAccepted: true, verified: false, explicitVerified: false, state: 'GLOBAL_ACEITO' })
try {
  compiler = webpack({ mode: 'development', target: 'web', devtool: false, context: frontend,
    entry: path.join(evidence, 'entry.tsx'), output: { path: evidence, filename: 'bundle.js' },
    resolve: { extensions: ['.tsx', '.ts', '.js'], modules: [path.join(frontend, 'node_modules')],
      alias: { '@': source, sonner: path.join(evidence, 'toast.js') } },
    module: { rules: [{ test: /\.[jt]sx?$/, exclude: /node_modules/, use: path.join(evidence, 'loader.cjs') }] },
    plugins: [new webpack.DefinePlugin({ 'process.env': JSON.stringify({ NODE_ENV: 'development', NEXT_PUBLIC_API_URL: '/api/public' }) })],
  })
  const stats = await new Promise((resolve, reject) => compiler.run((error, value) => error ? reject(error) : resolve(value)))
  assert.equal(stats.hasErrors(), false, stats.toString({ all: false, errors: true }))
  const bundle = fs.readFileSync(path.join(evidence, 'bundle.js'))
  server = http.createServer((request, response) => {
    if (request.url === '/bundle.js') { response.setHeader('Content-Type', 'text/javascript'); response.end(bundle); return }
    if (request.url === '/') { response.setHeader('Content-Type', 'text/html; charset=utf-8'); response.end('<!doctype html><meta charset="utf-8"><style>svg{width:20px;height:20px}label{display:block}input,button{min-height:24px}[role=dialog]{background:white;position:fixed;inset:0;overflow:auto;padding:20px}</style><div id="root"></div><script src="/bundle.js"></script>'); return }
    if (request.url === '/favicon.ico') { response.writeHead(204); response.end(); return }
    unexpected.push(request.url); response.writeHead(405); response.end()
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  browser = await chromium.launch({ headless: true })

  async function fixture(initialState = 'CHALLENGE_ACTIVE') {
    const context = await browser.newContext()
    await context.addCookies([{ name: 'XSRF-TOKEN', value: 'synthetic-csrf', url: origin }])
    await context.addInitScript(() => {
      Storage.prototype.setItem = () => { throw Error('Verification must not persist browser data') }
    })
    const page = await context.newPage()
    page.on('pageerror', error => errors.push(error.message))
    const challenges = new Map(), uploads = [], verifications = []
    let creations = 0
    let granted = false
    await context.route('**/*', async route => {
      const request = route.request(), url = new URL(request.url())
      if (url.origin !== origin) { unexpected.push(request.url()); return route.abort() }
      if (!url.pathname.startsWith('/api/')) return route.continue()
      const reply = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
      if (url.pathname.endsWith('/visitor/status')) {
        // The latest event in the session can belong to another case; document refresh
        // must use the context-bound challenge, not this deliberately unrelated status.
        return reply(granted ? { ...denied(), verified: true, level: 'REINFORCED', expiresAt: new Date(Date.now() + 60000).toISOString(), state: 'VERIFIED' }
          : { ...denied(), state: 'DOCUMENT_REJECTED', reasonPublic: 'Outro contexto sintetico' })
      }
      assert.equal(request.headers()['x-xsrf-token'], 'synthetic-csrf')
      if (url.pathname.endsWith('/visitor/challenge')) {
        const input = request.postDataJSON(), key = [input.anuncioId, input.scope, input.level, input.route].join('|')
        let challenge = challenges.get(key)
        if (!challenge) {
          challenge = { challengeId: randomUUID(), state: initialState, effectiveLevel: 'REINFORCED', scope: input.scope,
            expiresAt: new Date(Date.now() + 86400000).toISOString(), requiresExplicitAcknowledgement: false,
            documentRequired: true, maxAttempts: 3, documentStatus: null }
          challenges.set(key, challenge); creations++
        }
        return reply(challenge)
      }
      if (url.pathname.endsWith('/visitor/verify')) {
        const input = request.postDataJSON(), challenge = [...challenges.values()].find(item => item.challengeId === input.challengeId)
        assert.ok(challenge)
        assert.equal(input.dataNascimento, '01/01/1990')
        assert.equal(input.confirmacaoDataNascimento, '01/01/1990')
        assert.equal(input.cpf.replace(/\D/g, '').length, 11)
        assert.ok(input.aceiteMaioridade && input.aceiteConteudoRestrito && input.aceitePrivacidade)
        verifications.push({ challengeId: input.challengeId, idempotencyKey: input.idempotencyKey })
        if (challenge.state !== 'DOCUMENT_APPROVED') {
          challenge.state = 'DOCUMENT_PENDING'
          return reply({ ...denied(), state: 'DOCUMENT_PENDING', reasonPublic: 'Envie o documento sintetico.' }, 202)
        }
        granted = true
        return reply({ ...denied(), state: 'VERIFIED', verified: true, level: 'REINFORCED', expiresAt: new Date(Date.now() + 60000).toISOString() })
      }
      if (url.pathname.endsWith('/visitor/document')) {
        const challenge = [...challenges.values()].find(item => item.challengeId === url.searchParams.get('challengeId'))
        assert.ok(challenge)
        assert.notEqual(challenge.documentStatus, 'PENDING', 'No duplicate document upload')
        uploads.push(challenge.challengeId); challenge.documentStatus = 'PENDING'
        return reply({ challengeId: challenge.challengeId, submissionId: randomUUID(), state: 'DOCUMENT_PENDING', status: 'PENDING',
          createdAt: new Date().toISOString(), reasonPublic: 'Documento sintetico recebido.' }, 202)
      }
      unexpected.push(url.pathname); return route.abort()
    })
    await page.goto(origin)
    return { context, page, challenges, uploads, verifications, creations: () => creations }
  }
  async function birth(page) {
    await page.getByLabel('Data de nascimento', { exact: true }).fill('01/01/1990')
    await page.getByLabel('Confirme a data de nascimento').fill('01/01/1990')
    await page.getByRole('button', { name: 'Continuar', exact: true }).click()
  }
  async function identity(page) {
    await page.getByLabel('CPF', { exact: true }).fill('73194652000')
    for (const item of await page.locator('input[type=checkbox]').all()) await item.check()
    await page.getByRole('button', { name: 'Verificar', exact: true }).click()
  }
  async function pending(fixture) {
    await fixture.page.getByRole('button', { name: 'Abrir verificacao' }).click()
    await birth(fixture.page); await identity(fixture.page)
    await fixture.page.getByLabel('Documento para analise', { exact: true }).setInputFiles({
      name: 'sem-validade.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\nSINTETICO SEM VALIDADE\n%%EOF'),
    })
    await fixture.page.getByRole('button', { name: 'Enviar documento', exact: true }).click()
    await fixture.page.getByText('Documento sintetico recebido.', { exact: true }).waitFor()
    assert.equal(await fixture.page.locator('input[type=file]').count(), 0)
    assert.equal((await fixture.page.evaluate(() => window.__verified)).length, 0)
  }

  const awaitingUpload = await fixture()
  await awaitingUpload.page.getByRole('button', { name: 'Abrir verificacao' }).click()
  await birth(awaitingUpload.page); await identity(awaitingUpload.page)
  await awaitingUpload.page.getByLabel('Documento para analise', { exact: true }).waitFor()
  await awaitingUpload.page.getByRole('button', { name: 'Fechar', exact: true }).click()
  await awaitingUpload.page.getByRole('button', { name: 'Abrir verificacao' }).click()
  await awaitingUpload.page.getByLabel('Documento para analise', { exact: true }).waitFor()
  assert.equal(awaitingUpload.creations(), 1); assert.equal(awaitingUpload.uploads.length, 0)
  results.push('pending before upload: close and reopen returns to the same document request')
  await awaitingUpload.context.close()

  const reloaded = await fixture()
  await pending(reloaded)
  const originalId = reloaded.uploads[0]
  await reloaded.page.getByRole('button', { name: 'Fechar', exact: true }).click()
  await reloaded.page.getByRole('button', { name: 'Abrir verificacao' }).click()
  await reloaded.page.getByText('Documento recebido. Aguarde a analise.', { exact: true }).waitFor()
  assert.equal(await reloaded.page.locator('input[type=file]').count(), 0)
  assert.equal(await reloaded.page.getByLabel('Data de nascimento', { exact: true }).count(), 0)
  await reloaded.page.reload()
  for (const value of reloaded.challenges.values()) { value.state = 'DOCUMENT_APPROVED'; value.documentStatus = 'APPROVED' }
  await reloaded.page.getByRole('button', { name: 'Abrir verificacao' }).click()
  await reloaded.page.getByText('Documento aprovado. Confirme novamente os dados e aceites para emitir o acesso.', { exact: true }).waitFor()
  assert.equal((await reloaded.page.evaluate(() => window.__verified)).length, 0)
  await birth(reloaded.page); await identity(reloaded.page)
  await reloaded.page.waitForFunction(() => window.__verified.length === 1)
  assert.equal(reloaded.creations(), 1)
  assert.deepEqual(reloaded.uploads, [originalId])
  assert.deepEqual(reloaded.verifications.map(item => item.challengeId), [originalId, originalId])
  assert.notEqual(reloaded.verifications[0].idempotencyKey, reloaded.verifications[1].idempotencyKey)
  results.push('pending-close-reopen; reload-approve-resume-confirm; one challenge and document; no implicit grant')
  await reloaded.context.close()

  const opened = await fixture()
  await pending(opened)
  for (const value of opened.challenges.values()) { value.state = 'DOCUMENT_APPROVED'; value.documentStatus = 'APPROVED' }
  await opened.page.getByRole('button', { name: 'Atualizar situacao da analise' }).click()
  await opened.page.getByLabel('CPF', { exact: true }).waitFor()
  assert.equal(await opened.page.getByLabel('Data de nascimento', { exact: true }).count(), 0)
  assert.equal((await opened.page.evaluate(() => window.__verified)).length, 0)
  await opened.page.getByRole('button', { name: 'Verificar', exact: true }).click()
  await opened.page.waitForFunction(() => window.__verified.length === 1)
  assert.equal(opened.creations(), 1); assert.equal(opened.uploads.length, 1)
  results.push('modal kept open: contextual refresh preserves inputs and still requires confirmation')
  await opened.context.close()

  for (const state of ['BLOCKED', 'EXPIRED', 'VERIFIED']) {
    const refused = await fixture(state)
    await refused.page.getByRole('button', { name: 'Abrir verificacao' }).click()
    await refused.page.getByRole('alert').waitFor()
    assert.equal(await refused.page.locator('input').count(), 0)
    assert.equal((await refused.page.evaluate(() => window.__verified)).length, 0)
    assert.equal(refused.verifications.length, 0)
    await refused.context.close()
    results.push(`resumed ${state} is not access and does not start a data form`)
  }
  assert.deepEqual(unexpected, []); assert.deepEqual(errors, [])
  write('result.json', JSON.stringify({ passed: true, cases: results, browser: browser.version(), syntheticOnly: true }, null, 2))
  console.log('VISITOR_DOCUMENT_RESUME_RESULT=OK evidence=' + evidence)
} finally {
  await browser?.close()
  if (server) await new Promise(resolve => server.close(resolve))
  if (compiler) await new Promise((resolve, reject) => compiler.close(error => error ? reject(error) : resolve()))
}
