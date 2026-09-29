import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

// Existing webpack/Playwright fixture pattern; real components and API, synthetic HTTP only.
const require = createRequire(import.meta.url)
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..'), source = path.join(root, 'src')
const evidence = fs.mkdtempSync(path.join(process.env.TOPS_UI_EVIDENCE_ROOT || os.tmpdir(), 'admin-registros-'))
const write = (name, value) => fs.writeFileSync(path.join(evidence, name), value, { flag: 'wx' })
const { chromium } = require(process.env.TOPS_PLAYWRIGHT_MODULE || 'playwright')
const compiled = require('next/dist/compiled/webpack/webpack'); compiled.init()
const { webpack } = compiled, postcss = require('postcss'), tailwind = require('@tailwindcss/postcss')
const id = (number) => `00000000-0000-4000-8000-${String(number).padStart(12, '0')}`
const inventory = Array.from({ length: 41 }, (_, index) => ({
  id: id(index + 1), tipo: 'ANUNCIO', anuncioId: id(600 + index), anuncianteId: id(500),
  titulo: `Registro sintético ${index + 1}`, slug: `captura-${index + 1}`, anuncianteNome: 'Nome público sintético',
  beneficioCodigo: 'FOTOS_EXTRA_5', status: 'ENCERRADA', natureza: 'ADMINISTRATIVA', cobertura: 'PREVENTIVA',
  relacaoMaterial: 'DESCONHECIDA', inicioEm: '2026-09-01T10:00:00Z', fimEm: '2026-09-20T10:00:00Z',
  fimTipo: 'ENCERRAMENTO_REGISTRADO', encerramentoMotivo: 'CANCELAMENTO', totalVersoes: 2,
  retencaoAte: '2027-09-20T10:00:00Z', preservacaoAtiva: false,
}))
function detail(item, story = false) {
  const longContent = item.id === id(41)
  return { ...item, ...(story ? { storyId: id(900), modoConteudo: 'MIDIA_UPLOAD' } : {}),
    contratanteUsuarioId: id(500), ativacaoBeneficioId: id(700), grupoAtivacaoId: id(800),
    movimentoCreditoId: null, pagamentoId: null,
    ...(longContent ? { campoAdicionalRegistro: 'METADADO_FUTURO_REGISTRO_EXEMPLO_NAO_REAL', fimTipo: 'LIMITE_PREVISTO', encerramentoMotivo: 'LIMITE_AUTOMATICO_ATIVACAO' } : {}),
    preservacoes: longContent ? [{ id: id(1300), fundamento: 'Fundamento sintético de preservação; não é decisão jurídica real.', responsavelUsuarioId: id(1), inicioEm: '2026-09-20T10:00:00Z', revisarEm: null }] : [],
    versoes: [1, 2].map((number) => ({
      id: id(1000 + number), numero: number, capturadoEm: '2026-09-01T10:00:00Z',
      vigenteDesde: '2026-09-01T10:00:00Z', vigenteAte: '2026-09-20T10:00:00Z', motivo: 'CAPTURA_SINTETICA',
      conteudo: { titulo: `${item.titulo} - versão ${number}`, slug: item.slug,
        descricao: longContent ? (`Parágrafo sintético ${number}: texto editorial completo para conferir a quebra de páginas sem truncamento. `.repeat(30) + `FIM_DESCRICAO_VERSAO_${number}`) : 'Descrição sintética integral, sem imagem real.',
        ...(longContent ? { localizacao: { cidade: 'Cidade sintética', uf: 'EX', bairro: null, campoFuturoLocal: 'LOCAL_ADICIONAL_EXEMPLO_NAO_REAL' }, campoFuturo: { valor: 'CAPTURA_ADICIONAL_EXEMPLO_NAO_REAL' } } : {}) },
      contratante: longContent && number === 2 ? null : { nomeCivil: 'Pessoa sintética', cpf: 'CPF_SINTETICO' },
      comercial: { beneficioCodigo: 'FOTOS_EXTRA_5', origem: 'ADMINISTRATIVA', movimentoCreditoId: null },
      segmentacao: { estado: 'NAO_AFERIDA_NA_CAPTURA' }, alcance: { estado: 'NAO_MENSURADO' },
      conteudoSha256: 'a'.repeat(64), ...(longContent ? { campoFuturoVersao: 'VERSAO_ADICIONAL_EXEMPLO_NAO_REAL' } : {}),
      midias: [{ id: id(1100 + number), variante: number === 1 ? 'ORIGINAL' : 'PREVIEW_RESTRITO', mimeType: 'image/jpeg',
        tamanhoBytes: 1234, ordem: 1, sha256: 'b'.repeat(64), arquivoUrl: '/api/admin/registros/arquivo-sintetico',
        ...(longContent ? { campoFuturoMidia: 'MIDIA_ADICIONAL_EXEMPLO_NAO_REAL' } : {}) }],
    })) }
}
let mode = 'allowed', compiler, server, browser, failure, pendingAuth
const calls = [], results = [], errors = [], unexpected = [], contexts = new Set(), cleanupErrors = []
const cleanup = { contextsClosed: 0, browserClosed: false, serverClosed: false, compilerClosed: false }
const chosenScenario = process.argv.find((value) => value.startsWith('--scenario='))?.split('=')[1]
const scenarios = ['loading', 'expired', 'failed', 'denied', 'reader', 'allowed', 'selection', 'calendar']
assert.ok(!chosenScenario || scenarios.includes(chosenScenario), 'Cenário focal precisa ser conhecido.')
async function closeResource(name, operation, complete) {
  let timer
  try { await Promise.race([operation(), new Promise((_, reject) => { timer = setTimeout(() => reject(Error(name + ' cleanup timeout')), 10000) })]); complete() }
  catch (error) { cleanupErrors.push({ name, error: String(error) }) }
  finally { clearTimeout(timer) }
}
write('loader.cjs', `const ts=require(${JSON.stringify(require.resolve('typescript'))});module.exports=function(source){return ts.transpileModule(source,{fileName:this.resourcePath,compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext,jsx:ts.JsxEmit.ReactJSX}}).outputText}`)
write('navigation.tsx', `import React,{useEffect,useState}from'react';export function useSearchParams(){const[href,setHref]=useState(location.href);useEffect(()=>{const update=()=>setHref(location.href);addEventListener('popstate',update);return()=>removeEventListener('popstate',update)},[]);return new URLSearchParams(new URL(href).search)}export function useRouter(){return{push:(url)=>{history.pushState({},'',url);dispatchEvent(new Event('popstate'))},replace:(url)=>{history.replaceState({},'',url);dispatchEvent(new Event('popstate'))}}}export function useParams(){const parts=location.pathname.split('/').filter(Boolean);return{tipo:parts[2],id:parts[3]}}`)
write('link.tsx', `import React from'react';import{useRouter}from './navigation';export default function Link({href,children,...props}){const router=useRouter();return<a href={href} {...props} onClick={event=>{if(event.button===0&&!event.ctrlKey&&!event.metaKey&&!event.shiftKey){event.preventDefault();router.push(href)}}}>{children}</a>}`)
write('entry.tsx', `import React from'react';import{createRoot}from'react-dom/client';import{useSearchParams}from'./navigation';import{AdminRegistrosList}from${JSON.stringify(path.join(source, 'features/admin-registros/admin-registros-list.tsx'))};import{AdminRegistroDetail}from${JSON.stringify(path.join(source, 'features/admin-registros/admin-registro-detail.tsx'))};import{AdminRegistroReport}from${JSON.stringify(path.join(source, 'features/admin-registros/admin-registro-report.tsx'))};function App(){useSearchParams();const page=location.pathname;return<div data-fixture-shell className="fixed mx-auto inset-0 flex flex-col lg:flex-row bg-[#151619] text-white overflow-hidden"><aside className="w-48 p-4">Painel sintético</aside><div className="flex-1 lg:mt-4 rounded-t-2xl lg:rounded-tl-2xl bg-[#F9FAFA] text-black overflow-hidden"><div className="h-full overflow-y-auto p-4 lg:p-8"><main className="mx-auto max-w-5xl p-4">{page.endsWith('/relatorio')?<AdminRegistroReport/>:page.startsWith('/admin/registros/publicidade/')||page.startsWith('/admin/registros/stories/')?<AdminRegistroDetail/>:<AdminRegistrosList/>}</main></div></div></div>}createRoot(document.getElementById('root')).render(<App/>);`)
try {
  const cssPath = path.join(source, 'app/globals.css')
  const css = await postcss([tailwind({ base: root })]).process(fs.readFileSync(cssPath, 'utf8'), { from: cssPath })
  compiler = webpack({ mode: 'development', target: 'web', devtool: false, context: root, entry: path.join(evidence, 'entry.tsx'),
    output: { path: evidence, filename: 'bundle.js' },
    resolve: { extensions: ['.tsx', '.ts', '.js'], modules: [path.join(root, 'node_modules')], alias: { '@': source,
      'next/navigation$': path.join(evidence, 'navigation.tsx'), 'next/link$': path.join(evidence, 'link.tsx') } },
    module: { rules: [{ test: /\.[jt]sx?$/, exclude: /node_modules/, use: path.join(evidence, 'loader.cjs') }] },
    plugins: [new webpack.DefinePlugin({ 'process.env': JSON.stringify({ NODE_ENV: 'development', NEXT_PUBLIC_API_URL: '/api/public' }) })],
  })
  const stats = await new Promise((resolve, reject) => compiler.run((error, value) => error ? reject(error) : resolve(value)))
  assert.equal(stats.hasErrors(), false, stats.toString({ all: false, errors: true }))
  const bundle = fs.readFileSync(path.join(evidence, 'bundle.js'))
  server = http.createServer(async (request, response) => {
    const url = new URL(request.url, 'http://synthetic.invalid'), pathname = url.pathname
    const json = (status, payload) => { response.writeHead(status, { 'Content-Type': 'application/json' }); response.end(JSON.stringify(payload)) }
    if (pathname === '/styles.css') { response.writeHead(200, { 'Content-Type': 'text/css' }); response.end(css.css); return }
    if (pathname === '/bundle.js') { response.writeHead(200, { 'Content-Type': 'text/javascript' }); response.end(bundle); return }
    if (pathname === '/favicon.ico') { response.writeHead(204); response.end(); return }
    if (pathname.startsWith('/admin/registros')) {
      response.writeHead(200, { 'Content-Type': 'text/html', 'Set-Cookie': 'XSRF-TOKEN=EXEMPLO_NAO_REAL; Path=/' })
      response.end('<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/styles.css"></head><body><div id="root"></div><script src="/bundle.js"></script></body></html>'); return
    }
    calls.push({ method: request.method, pathname, query: url.search })
    if (pathname === '/api/admin/auth/me') {
      const answer = () => json(mode === 'expired' ? 401 : mode === 'failed' ? 503 : 200,
        { autenticado: true, usuarioId: mode === 'different-user' ? id(2) : id(1), nome: 'Admin sintético', email: 'synthetic@example.invalid', papeis: ['ADMIN'],
          permissoes: mode === 'denied' ? [] : ['ARQUIVO_PUBLICIDADE_LER', 'ANUNCIO_LER', ...(mode === 'reader' ? [] : ['ARQUIVO_PUBLICIDADE_EXPORTAR'])] })
      if (mode === 'loading') pendingAuth = answer; else answer()
      return
    }
    if (!pathname.startsWith('/api/admin/registros/') || request.method !== 'POST') {
      unexpected.push({ method: request.method, pathname }); json(404, {}); return
    }
    assert.equal(request.headers['x-xsrf-token'], 'EXEMPLO_NAO_REAL')
    assert.ok(['AUDITORIA_INTERNA', 'APURACAO_INCIDENTE'].includes(url.searchParams.get('finalidade')))
    if (mode === 'expired') { json(401, {}); return }
    if (mode === 'denied') { json(403, {}); return }
    const story = pathname.includes('/stories')
    if (pathname.endsWith('/relatorio')) {
      if (mode === 'reader') { json(403, {}); return }
      const chunks = []; for await (const part of request) chunks.push(part)
      const body = JSON.parse(Buffer.concat(chunks).toString())
      calls.at(-1).body = body
      const matching = body.filtros.termo ? inventory.filter((item) => item.titulo === body.filtros.termo) : inventory
      if (body.ids.some((recordId) => !matching.some((item) => item.id === recordId))) { json(400, {}); return }
      const selected = body.ids.length ? matching.filter((item) => body.ids.includes(item.id)) : matching
      json(200, { tipo: story ? 'STORY' : 'ANUNCIO', geradoEm: '2026-09-29T03:00:00Z', fusoHorario: 'America/Sao_Paulo',
        responsavelId: id(1), finalidade: 'AUDITORIA_INTERNA', filtros: body.filtros, idsSelecionados: body.ids,
        quantidade: selected.length, limiteRegistros: 100, lacunas: ['Exemplo sintético; alcance não aferido.'], registros: selected.map((item) => detail(item, story)) }); return
    }
    if (/\/registros\/(publicidade|stories)\/[^/]+$/.test(pathname)) {
      const item = inventory.find((entry) => entry.id === pathname.split('/').at(-1))
      if (!item) { json(404, {}); return }
      const value = detail(item, story)
      value.versoes.forEach((version) => { version.contratante = null; version.comercial = null })
      value.preservacoes = []
      json(200, value); return
    }
    const term = url.searchParams.get('termo')
    const matching = term ? inventory.filter((item) => item.titulo === term) : inventory
    const page = Number(url.searchParams.get('page'))
    json(200, { itens: matching.slice(page * 20, (page + 1) * 20).map((item) => story ? { ...item, tipo: 'STORY', storyId: id(900), modoConteudo: 'ANUNCIO' } : item),
      page, size: 20, totalElements: matching.length, totalPages: Math.ceil(matching.length / 20), last: (page + 1) * 20 >= matching.length })
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  browser = await chromium.launch({ headless: true, ...(process.env.TOPS_CHROMIUM_BIN ? { executablePath: process.env.TOPS_CHROMIUM_BIN } : {}) })
  for (const scenario of scenarios.filter((value) => !chosenScenario || value === chosenScenario)) {
    mode = ['selection', 'calendar'].includes(scenario) ? 'allowed' : scenario; calls.length = 0
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } }); contexts.add(context)
    await context.route('**/*', async (route) => {
      if (new URL(route.request().url()).origin === origin) return route.continue()
      unexpected.push({ external: true }); await route.abort()
    })
    const page = await context.newPage(); page.on('pageerror', (error) => errors.push(String(error)))
    await page.goto(origin + '/admin/registros?tipo=publicidade&page=0&finalidade=AUDITORIA_INTERNA')
    if (scenario === 'selection') {
      await page.getByText(/41 registro\(s\) no filtro/).waitFor()
      await page.getByRole('checkbox', { name: 'Selecionar registro ' + inventory[0].id, exact: true }).check()
      await page.getByRole('link', { name: 'Próxima', exact: true }).click()
      await page.getByText(/Página 2 de 3/).waitFor()
      await page.getByRole('checkbox', { name: 'Selecionar registro ' + inventory[20].id, exact: true }).check()
      await page.getByRole('link', { name: 'Consultar captura', exact: true }).first().click()
      await page.getByText('Todas as versões preservadas (2)', { exact: true }).waitFor()
      await page.getByRole('link', { name: 'Voltar à consulta com filtros' }).click()
      await page.getByText(/Página 2 de 3/).waitFor()
      const returnedSelection = await page.getByRole('checkbox', { name: 'Selecionar registro ' + inventory[20].id, exact: true }).isChecked()
      write('selection-after-detail.json', JSON.stringify({ selectedBefore: [inventory[0].id, inventory[20].id], returnedSelection, url: page.url(), scope: await page.locator('body').innerText() }, null, 2))
      await page.screenshot({ path: path.join(evidence, 'selection-after-detail.png'), fullPage: true })
      assert.equal(returnedSelection, true, 'Retorno do detalhe deve preservar a seleção de ambas as páginas, sem convertê-la em todos os registros.')
      const selectedIds = [inventory[0].id, inventory[20].id], listUrl = page.url()
      assert.deepEqual(new URL(listUrl).searchParams.getAll('id'), selectedIds)
      assert.equal(new URL(listUrl).searchParams.get('escopo'), 'selecionados')
      await page.getByRole('link', { name: 'Anterior', exact: true }).click()
      await page.getByText(/Página 1 de 3/).waitFor()
      assert.equal(await page.getByRole('checkbox', { name: 'Selecionar registro ' + inventory[0].id, exact: true }).isChecked(), true)
      await page.getByRole('link', { name: 'Conferir escopo e preparar relatório imprimível', exact: true }).click()
      await page.getByText(/relatório abrangerá 2 selecionados explicitamente/).waitFor()
      assert.equal(calls.some((call) => call.pathname.endsWith('/relatorio')), false)
      await page.getByRole('button', { name: 'Preparar relatório completo', exact: true }).click()
      await page.getByText('Relatório preparado. Impressão ou recebimento não foram comprovados.', { exact: true }).waitFor()
      assert.equal(await page.locator('.record-detail').count(), 2)
      assert.deepEqual(calls.find((call) => call.pathname.endsWith('/relatorio')).body.ids, selectedIds)
      await page.getByRole('link', { name: 'Voltar à consulta com filtros' }).click()
      await page.getByText(/Página 1 de 3/).waitFor()
      assert.equal(await page.getByRole('checkbox', { name: 'Selecionar registro ' + inventory[0].id, exact: true }).isChecked(), true)
      await page.getByLabel('Busca em título, slug, nome público ou identificadores').fill('Registro sintético 41')
      await page.getByRole('button', { name: 'Consultar', exact: true }).click()
      await page.getByText(/1 registro\(s\) no filtro/).waitFor()
      assert.equal(new URL(page.url()).searchParams.getAll('id').length, 0)
      await page.getByText(/Seleção vazia, inválida ou incompatível/).waitFor()
      assert.equal(await page.getByRole('link', { name: /Conferir escopo e preparar/ }).count(), 0, 'Limpar filtros não troca seleção anterior silenciosamente por todos.')
      const returnUrl = '/admin/registros?tipo=publicidade&finalidade=AUDITORIA_INTERNA&termo=Registro+sint%C3%A9tico+41&escopo=selecionados&selecionador=' + id(1) + '&id=' + inventory[0].id
      await page.goto(origin + '/admin/registros/relatorio?' + new URL(returnUrl, origin).searchParams + '&retorno=' + encodeURIComponent(returnUrl))
      await page.getByText(/relatório abrangerá 1 selecionados explicitamente/).waitFor()
      const outOfScope = page.waitForResponse((response) => response.url().includes('/relatorio?') && response.status() === 400)
      await page.getByRole('button', { name: 'Preparar relatório completo', exact: true }).click(); await outOfScope
      await page.getByRole('button', { name: 'Preparar relatório completo', exact: true }).waitFor({ state: 'visible' })
      assert.equal(await page.locator('.record-report').count(), 0)
      const failedCall = calls.filter((call) => call.pathname.endsWith('/relatorio')).at(-1)
      assert.deepEqual(failedCall.body.ids, [inventory[0].id], 'Seleção fora do filtro é recusada, nunca repetida sem IDs.')
      assert.equal(calls.filter((call) => call.pathname.endsWith('/relatorio')).length, 2)
      mode = 'expired'; await page.goto(listUrl)
      await page.getByText(/Sessão expirada/).waitFor()
      await page.waitForFunction(() => new URL(location.href).searchParams.getAll('id').length === 0)
      assert.equal(new URL(page.url()).searchParams.get('escopo'), 'selecionados')
      mode = 'different-user'; await page.goto(listUrl)
      await page.getByText(/Seleção vazia, inválida ou incompatível/).waitFor()
      assert.equal(await page.getByRole('link', { name: /Conferir escopo e preparar/ }).count(), 0)
      mode = 'allowed'
      await page.goto(listUrl)
      await page.getByText(/Página 2 de 3/).waitFor()
      await page.getByRole('button', { name: 'Consultar', exact: true }).click()
      await page.getByText(/Página 1 de 3/).waitFor()
      assert.deepEqual(new URL(page.url()).searchParams.getAll('id'), selectedIds, 'Consulta sem alterar escopo preserva a seleção.')
      await page.getByRole('link', { name: 'Stories', exact: true }).click()
      await page.getByText('Veiculações de Stories', { exact: true }).waitFor()
      await page.getByText(/Seleção vazia, inválida ou incompatível/).waitFor()
      assert.equal(new URL(page.url()).searchParams.getAll('id').length, 0)
      await page.goto(listUrl)
      await page.getByText(/Página 2 de 3/).waitFor()
      await page.getByRole('combobox', { name: /^Finalidade do acesso privado/ }).selectOption('APURACAO_INCIDENTE')
      await page.getByRole('button', { name: 'Consultar', exact: true }).click()
      await page.getByText(/Seleção vazia, inválida ou incompatível/).waitFor()
      assert.equal(new URL(page.url()).searchParams.get('finalidade'), 'APURACAO_INCIDENTE')
      assert.equal(new URL(page.url()).searchParams.getAll('id').length, 0)
      await page.getByRole('button', { name: 'Usar todos os resultados filtrados', exact: true }).click()
      assert.equal(new URL(page.url()).searchParams.get('escopo'), 'todos')
      assert.equal(new URL(page.url()).searchParams.getAll('id').length, 0)
      await page.getByRole('link', { name: /Conferir escopo e preparar/ }).waitFor()
      const tooLarge = new URLSearchParams('tipo=publicidade&finalidade=AUDITORIA_INTERNA&escopo=selecionados&selecionador=' + id(1))
      for (let number = 1; number <= 101; number++) tooLarge.append('id', id(number))
      await page.goto(origin + '/admin/registros/relatorio?' + tooLarge)
      await page.getByText(/Escopo acima de 100/).waitFor()
      assert.equal(await page.getByRole('button', { name: 'Preparar relatório completo', exact: true }).isDisabled(), true)
      assert.equal(new URL(page.url()).searchParams.getAll('id').length, 101, 'Seleção excessiva é recusada, não truncada para cem.')
      assert.equal(calls.filter((call) => call.pathname.endsWith('/relatorio')).length, 2)
    } else if (scenario === 'calendar') {
      await page.getByText(/41 registro\(s\) no filtro/).waitFor()
      assert.equal(await page.getByLabel('De (dia inicial)', { exact: true }).getAttribute('type'), 'date')
      assert.equal(await page.getByLabel('Até (dia final inclusive)', { exact: true }).getAttribute('type'), 'date')
      await page.getByLabel('De (dia inicial)', { exact: true }).fill('2026-09-01')
      await page.getByLabel('Até (dia final inclusive)', { exact: true }).fill('2026-09-29')
      await page.getByRole('button', { name: 'Consultar', exact: true }).click()
      await page.waitForFunction(() => new URL(location.href).searchParams.get('fim') === '2026-09-30T03:00:00.000Z')
      await page.getByText(/41 registro\(s\) no filtro/).waitFor()
      assert.equal(new URL(page.url()).searchParams.get('inicio'), '2026-09-01T03:00:00.000Z')
      assert.ok(calls.some((call) => new URLSearchParams(call.query).get('fim') === '2026-09-30T03:00:00.000Z'))
      await page.screenshot({ path: path.join(evidence, 'calendar-inclusive.png'), fullPage: true })
    } else if (scenario === 'loading') {
      await page.getByText('Conferindo sessão e permissões...', { exact: true }).waitFor()
      assert.equal(calls.filter((call) => call.pathname.includes('/registros/')).length, 0)
      mode = 'allowed'; pendingAuth(); pendingAuth = null; await page.getByText('Veiculações de anúncios', { exact: true }).waitFor()
    } else if (['expired', 'failed', 'denied'].includes(scenario)) {
      await page.getByText(scenario === 'expired' ? /Sessão expirada/ : scenario === 'failed' ? /Isso não comprova falta de permissão/ : /não possui permissão para consultar/).waitFor()
      assert.equal(calls.filter((call) => call.pathname.includes('/registros/')).length, 0)
      await page.screenshot({ path: path.join(evidence, `session-${scenario}.png`) })
    } else {
      await page.getByText(/41 registro\(s\) no filtro/).waitFor()
      if (scenario === 'reader') {
        assert.equal(await page.getByRole('link', { name: /preparar relatório/ }).count(), 0)
        await page.goto(origin + '/admin/registros/relatorio?tipo=publicidade&finalidade=AUDITORIA_INTERNA')
        await page.getByText(/sem permissão de exportação/).waitFor()
        assert.equal(calls.some((call) => call.pathname.endsWith('/relatorio')), false)
      } else {
        await page.getByRole('link', { name: 'Próxima', exact: true }).click()
        await page.getByText(/Página 2 de 3/).waitFor()
        assert.equal(new URL(page.url()).searchParams.get('page'), '1')
        await page.getByLabel('Busca em título, slug, nome público ou identificadores').fill('Registro sintético 41')
        await page.getByRole('button', { name: 'Consultar', exact: true }).click()
        await page.getByText(/1 registro\(s\) no filtro/).waitFor()
        assert.equal(new URL(page.url()).searchParams.get('page'), '0')
        assert.equal(new URL(page.url()).searchParams.get('termo'), 'Registro sintético 41')
        await page.screenshot({ path: path.join(evidence, 'list-filtered.png'), fullPage: true })
        const listUrl = new URL(page.url()).pathname + new URL(page.url()).search
        await page.getByRole('link', { name: 'Consultar captura', exact: true }).click()
        await page.getByText('Todas as versões preservadas (2)', { exact: true }).waitFor()
        await page.getByText(/Uma lista redigida não comprova ausência de preservação/).waitFor()
        assert.equal(await page.getByText('CPF_SINTETICO', { exact: false }).count(), 0, 'Detalhe operacional permanece redigido, mesmo com EXPORTAR.')
        assert.equal(await page.getByRole('link', { name: 'Voltar à consulta com filtros' }).getAttribute('href'), listUrl)
        assert.equal(await page.getByRole('link', { name: 'Cadastro atual do anúncio', exact: true }).getAttribute('href'), '/admin/anuncios/' + inventory[40].anuncioId)
        assert.equal(await page.getByRole('link', { name: 'Cadastro atual do proprietário', exact: true }).getAttribute('href'), '/admin/usuarios/' + id(500))
        await page.screenshot({ path: path.join(evidence, 'detail-history.png'), fullPage: true })
        await page.getByRole('link', { name: 'Voltar à consulta com filtros' }).click()
        await page.getByText(/1 registro\(s\) no filtro/).waitFor()
        await page.getByRole('checkbox', { name: 'Selecionar registro ' + inventory[40].id, exact: true }).check()
        await page.getByRole('link', { name: 'Conferir escopo e preparar relatório imprimível', exact: true }).click()
        await page.getByText(/relatório abrangerá 1 selecionados explicitamente/).waitFor()
        assert.equal(calls.some((call) => call.pathname.endsWith('/relatorio')), false, 'Prévia de escopo não gera relatório automaticamente.')
        await page.getByRole('button', { name: 'Preparar relatório completo', exact: true }).click()
        await page.getByText('Relatório preparado. Impressão ou recebimento não foram comprovados.', { exact: true }).waitFor()
        assert.equal(await page.locator('.record-version').count(), 2)
        await page.locator('.record-report').getByText('Finalidade: Auditoria interna', { exact: true }).waitFor()
        await page.getByText(/Prévia restrita · image\/jpeg/).waitFor()
        assert.equal(await page.getByText('CPF_SINTETICO', { exact: false }).count(), 1)
        await page.getByText('Regra do término previsto', { exact: true }).waitFor()
        assert.equal(await page.getByText('Motivo do encerramento', { exact: true }).count(), 0)
        await page.locator('.record-report').getByText('Não registrado na captura', { exact: true }).first().waitFor()
        for (const text of ['FIM_DESCRICAO_VERSAO_1', 'FIM_DESCRICAO_VERSAO_2', 'CAPTURA_ADICIONAL_EXEMPLO_NAO_REAL', 'LOCAL_ADICIONAL_EXEMPLO_NAO_REAL', 'VERSAO_ADICIONAL_EXEMPLO_NAO_REAL', 'MIDIA_ADICIONAL_EXEMPLO_NAO_REAL', 'METADADO_FUTURO_REGISTRO_EXEMPLO_NAO_REAL']) assert.ok((await page.locator('.record-report').innerText()).includes(text), text + ' permanece integral na apresentação.')
        assert.equal(await page.locator('.record-report').getByText('/api/admin/registros/arquivo-sintetico', { exact: false }).count(), 0, 'Endereço de mídia não é acrescentado à apresentação; download continua explícito.')
        await page.getByText(/não nome histórico capturado/).first().waitFor()
        assert.equal(calls.filter((call) => call.pathname.endsWith('/relatorio')).length, 1)
        assert.equal(calls.some((call) => /\/arquivo$|\/exportacao$/.test(call.pathname)), false, 'Nenhum binário baixado por consultar/preparar relatório.')
        const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Baixar JSON do relatório preparado', exact: true }).click()])
        await download.saveAs(path.join(evidence, 'report-example.json'))
        const received = JSON.parse(fs.readFileSync(path.join(evidence, 'report-example.json'), 'utf8'))
        assert.equal(received.quantidade, 1); assert.equal(received.registros[0].versoes.length, 2)
        assert.deepEqual(received.registros[0], detail(inventory[40]), 'JSON técnico preserva todos os campos e bytes lógicos recebidos, incluindo adicionais e endpoints não apresentados.')
        assert.equal(calls.filter((call) => call.pathname.endsWith('/relatorio')).length, 1, 'Baixar JSON já recebido não gera outro relatório ou auditoria.')
        await page.emulateMedia({ media: 'print' })
        const printBounds = await page.locator('.record-report').evaluate((element) => {
          const ancestors = []; let node = element.parentElement
          while (node) { const style = getComputedStyle(node); ancestors.push({ tag: node.tagName, classes: node.className, position: style.position, overflowY: style.overflowY, clientHeight: node.clientHeight, scrollHeight: node.scrollHeight }); node = node.parentElement }
          return { height: element.getBoundingClientRect().height, ancestors }
        })
        write('print-ancestors.json', JSON.stringify(printBounds, null, 2))
        assert.ok(printBounds.ancestors.every((ancestor) => ancestor.position !== 'fixed' && !['hidden', 'scroll', 'auto'].includes(ancestor.overflowY)), 'Impressão precisa liberar ancestrais fixos e rolagem do shell administrativo.')
        await page.locator('.record-report').screenshot({ path: path.join(evidence, 'report-print.png') })
        const pdf = await page.pdf({ path: path.join(evidence, 'report-example.pdf'), format: 'A4', printBackground: false, preferCSSPageSize: true })
        const pageCount = [...pdf.toString('latin1').matchAll(/\/Type\s*\/Page\b/g)].length
        assert.ok(pageCount > 1, 'Relatório completo precisa ocupar múltiplas páginas no exemplo, sem corte pelo shell fixo.')
        write('print-pages.json', JSON.stringify({ pageCount, bytes: pdf.length, completeVersions: 2, selectedRecords: 1, longContent: true,
          missingContractorVersion: 2, completeHashes: ['a'.repeat(64), 'b'.repeat(64)], technicalFieldsPreserved: true }, null, 2))
        write('report-example.html', '<!doctype html><meta charset="utf-8"><title>Exemplo sintético de relatório</title>' + await page.locator('.record-report').evaluate((element) => element.outerHTML))
        await page.emulateMedia({ media: 'screen' })
        await page.getByRole('link', { name: 'Voltar à consulta com filtros' }).click()
        await page.getByRole('link', { name: 'Stories', exact: true }).click()
        await page.getByText('Veiculações de Stories', { exact: true }).waitFor()
        assert.ok(calls.some((call) => call.pathname === '/api/admin/registros/stories'))
      }
    }
    results.push({ scenario, passed: true, calls: [...calls] })
    await closeResource('context ' + scenario, () => context.close(), () => { contexts.delete(context); cleanup.contextsClosed += 1 })
  }
  assert.deepEqual(unexpected, []); assert.deepEqual(errors, [])
} catch (error) { failure = error }
finally {
  if (pendingAuth) { try { mode = 'expired'; pendingAuth() } catch {} }
  for (const context of contexts) await closeResource('remaining context', () => context.close(), () => { cleanup.contextsClosed += 1 })
  if (browser) await closeResource('browser', () => browser.close(), () => { cleanup.browserClosed = true })
  if (server) await closeResource('server', () => new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())), () => { cleanup.serverClosed = true })
  if (compiler) await closeResource('compiler', () => new Promise((resolve, reject) => compiler.close((error) => error ? reject(error) : resolve())), () => { cleanup.compilerClosed = true })
  write('results.json', JSON.stringify({ passed: !failure && !cleanupErrors.length, node: process.version, results, errors, unexpected, cleanup, cleanupErrors,
    limitations: ['Synthetic HTTP transport; backend PostgreSQL authorization/filter proof is separate.', 'Browser Next navigation shim; no personal session or production data.', 'Printable output prepared, not physically printed or received.'], error: failure ? String(failure) : null }, null, 2))
}
if (failure) throw failure
assert.deepEqual(cleanupErrors, [])
console.log(JSON.stringify({ result: 'OK', evidence, scenarios: results.length, cleanup }))
