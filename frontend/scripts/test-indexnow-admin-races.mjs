import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'
import ts from 'typescript'

const userSource = readFileSync(new URL('../src/features/admin-usuarios/admin-usuario-delete-dialog.tsx', import.meta.url), 'utf8')
const adSource = readFileSync(new URL('../src/features/admin-anuncios/admin-anuncio-moderacao.tsx', import.meta.url), 'utf8')
const clientSource = readFileSync(new URL('../src/lib/seo/indexnow-client.ts', import.meta.url), 'utf8')

function functionSource(source, name) {
  const file = ts.createSourceFile('component.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  const matches = []
  function visit(node) {
    if (ts.isFunctionDeclaration(node) && node.name?.text === name) matches.push(node.getText(file))
    ts.forEachChild(node, visit)
  }
  visit(file)
  assert.equal(matches.length, 1, `Expected one real handler: ${name}`)
  return matches[0]
}

function compile(source) {
  return ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
}

function loadHandler(source, name, bindings) {
  return new Function(...Object.keys(bindings), `${compile(functionSource(source, name))}\nreturn ${name}`)(
    ...Object.values(bindings),
  )
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

async function flush() {
  for (let turn = 0; turn < 20; turn += 1) await Promise.resolve()
}

const publicAd = (id) => ({
  id, slug: `sintetico-${id}`, status: 'PUBLICADO',
  anunciante: { id: 'usuario-sintetico' },
  localizacao: { uf: 'SC', cidade: 'Cidade Sintetica', bairro: 'Bairro Sintetico' },
})

function scenario(overrides = {}) {
  const calls = []
  const events = []
  const errors = []
  const external = deferred()
  const compiledModule = { exports: {} }
  new Function('module', 'exports', 'require', 'process', 'window', compile(clientSource))(
    compiledModule,
    compiledModule.exports,
    (specifier) => {
      assert.ok(specifier.endsWith('/admin/anuncios/actions'))
      return { revalidarCacheCatalogoPublico: (event) => {
        calls.push('notify')
        events.push(event)
        return external.promise
      } }
    },
    { env: { NEXT_PUBLIC_SITE_URL: 'https://catalogo.example.test' } },
    undefined,
  )
  const bindings = {
    ...compiledModule.exports,
    eligibility: { podeExcluir: true }, confirmation: 'EXCLUIR', reason: 'Motivo sintetico',
    lock: { current: false }, idempotencyKey: { current: 'evento-sintetico' },
    usuarioId: 'usuario-sintetico',
    setBusy: (value) => calls.push(`busy:${value}`),
    setError: (error) => { if (error) errors.push(error) },
    setEligibility: () => {},
    normalizeApiError: (error) => error,
    AdminUserDeletionError: class extends Error {},
    getAdminUser: async () => {
      calls.push('capture-user')
      return { anuncios: [{ id: 'a', status: 'PUBLICADO' }, { id: 'b', status: 'PUBLICADO' }, { id: 'draft', status: 'RASCUNHO' }] }
    },
    getAdminAd: async (id) => { calls.push(`capture-ad:${id}`); return publicAd(id) },
    deleteAdminUser: async () => { calls.push('delete'); return { resultado: 'SUCESSO' } },
    revalidarCacheCatalogoPublico: async () => { calls.push('cache') },
    onSuccess: async () => { calls.push('success') },
    onOpenChange: (open) => calls.push(`open:${open}`),
    ad: publicAd('a'), legalIntent: { kind: 'BLOCK_USER' }, legalBusy: false,
    setLegalBusy: (value) => calls.push(`busy:${value}`),
    setLegalActionError: (error) => { if (error) errors.push(error) },
    setLegalIntent: (intent) => { if (!intent) calls.push('closed') },
    load: async () => { calls.push('load') },
    blockAdminAdAndUser: async () => { calls.push('block-user'); return { executadoEm: '2026-01-01T00:00:00Z' } },
    blockAdminAd: async () => { calls.push('block-ad'); return { executadoEm: '2026-01-01T00:00:00Z' } },
    ...overrides,
  }
  bindings.indexNowContext = loadHandler(adSource, 'indexNowContext', {})
  return {
    calls, events, errors, external,
    runDelete: () => loadHandler(userSource, 'confirmDelete', bindings)(),
    runBlock: () => loadHandler(adSource, 'confirmLegalAction', bindings)('OUTRA_INTERVENCAO', 'Motivo sintetico', ''),
  }
}

function assertWithdrawal(test, expectedIds) {
  assert.equal(test.events.length, 1)
  assert.equal(test.events[0].eventType, 'RETIRADA')
  assert.deepEqual(
    test.events[0].urls.filter((url) => url.includes('/anuncios/')).sort(),
    expectedIds.map((id) => `https://catalogo.example.test/anuncios/sintetico-${id}`).sort(),
  )
}

export async function runIndexNowAdminRaceTests() {
  let checks = 0
  for (const operation of ['Delete', 'Block']) {
    // The old Promise race starts the mutation while either capture is unresolved.
    const user = deferred()
    const secondAd = deferred()
    let lookupStarted = false
    const test = scenario({
      getAdminUser: () => user.promise,
      getAdminAd: async (id) => {
        if (id === 'b') { lookupStarted = true; return secondAd.promise }
        assert.notEqual(id, 'draft', 'Nonpublic ads must retain the existing filter')
        return publicAd(id)
      },
    })
    let completed = false
    const run = test[`run${operation}`]().then(() => { completed = true })
    await flush()
    assert.equal(test.calls.includes(operation === 'Delete' ? 'delete' : 'block-user'), false)
    user.resolve({ anuncios: [{ id: 'a', status: 'PUBLICADO' }, { id: 'b', status: 'PUBLICADO' }, { id: 'draft', status: 'RASCUNHO' }] })
    await flush()
    assert.equal(lookupStarted, true)
    assert.equal(test.calls.includes(operation === 'Delete' ? 'delete' : 'block-user'), false)
    secondAd.resolve(publicAd('b'))
    await flush()
    assert.equal(completed, true, 'Interface must finish while the external notification remains pending')
    await run
    assertWithdrawal(test, ['a', 'b'])
    assert.ok(test.calls.indexOf('notify') > test.calls.indexOf(operation === 'Delete' ? 'delete' : 'block-user'))
    assert.equal(test.errors.length, 0)
    test.external.resolve()
    checks += 1

    const failure = new Error('synthetic mutation failure')
    const failed = scenario({
      [operation === 'Delete' ? 'deleteAdminUser' : 'blockAdminAdAndUser']: async () => { throw failure },
    })
    await failed[`run${operation}`]()
    await flush()
    assert.deepEqual(failed.errors, [failure])
    assert.equal(failed.events.length, 0, 'Failed mutation cannot notify a withdrawal')
    assert.equal(failed.calls.includes(operation === 'Delete' ? 'success' : 'closed'), false)
    checks += 1

    const captureFailed = scenario({ getAdminUser: async () => { throw new Error('synthetic capture failure') } })
    await captureFailed[`run${operation}`]()
    await flush()
    assert.equal(captureFailed.errors.length, 0)
    assert.ok(captureFailed.calls.includes(operation === 'Delete' ? 'success' : 'closed'))
    if (operation === 'Delete') assert.equal(captureFailed.events.length, 0)
    else assertWithdrawal(captureFailed, ['a']) // Existing detail is a safe previous-context fallback.
    captureFailed.external.resolve()
    checks += 1

    const partialCapture = scenario({
      getAdminAd: async (id) => {
        if (id === 'b') throw new Error('synthetic detail failure')
        return publicAd(id)
      },
    })
    await partialCapture[`run${operation}`]()
    await flush()
    assertWithdrawal(partialCapture, ['a'])
    assert.equal(partialCapture.errors.length, 0)
    partialCapture.external.resolve()
    checks += 1

    const notificationFailed = scenario()
    await notificationFailed[`run${operation}`]()
    notificationFailed.external.reject(new Error('synthetic IndexNow failure'))
    await flush()
    assert.equal(notificationFailed.errors.length, 0)
    assert.ok(notificationFailed.calls.includes(operation === 'Delete' ? 'success' : 'closed'))
    checks += 1
  }

  const cacheFailed = scenario({ revalidarCacheCatalogoPublico: async () => { throw new Error('synthetic cache failure') } })
  await cacheFailed.runDelete()
  await flush()
  assertWithdrawal(cacheFailed, ['a', 'b'])
  assert.equal(cacheFailed.errors.length, 0)
  assert.ok(cacheFailed.calls.includes('success'))
  assert.ok(cacheFailed.calls.includes('open:false'))
  cacheFailed.external.resolve()
  checks += 1

  // Direct ad blocking already reuses the pre-mutation detail; preserve that path.
  const direct = scenario({ legalIntent: { kind: 'BLOCK_AD' } })
  await direct.runBlock()
  await flush()
  assertWithdrawal(direct, ['a'])
  assert.equal(direct.calls.includes('capture-user'), false)
  assert.ok(direct.calls.indexOf('notify') > direct.calls.indexOf('block-ad'))
  direct.external.resolve()
  const directFailed = scenario({
    legalIntent: { kind: 'BLOCK_AD' },
    blockAdminAd: async () => { throw new Error('synthetic block failure') },
  })
  await directFailed.runBlock()
  await flush()
  assert.equal(directFailed.events.length, 0)
  assert.equal(directFailed.errors.length, 1)
  checks += 1

  console.log(`IndexNow admin: ${checks} casos aprovados; handlers reais, transporte simulado, nenhuma chamada externa.`)
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  await runIndexNowAdminRaceTests()
}
