import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const middlewareSource = readFileSync(
  new URL('../src/middleware.ts', import.meta.url),
  'utf8',
)
const { outputText } = ts.transpileModule(middlewareSource, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022,
    esModuleInterop: true,
  },
})
const middlewareModule = { exports: {} }
new Function('module', 'exports', 'require', outputText)(
  middlewareModule,
  middlewareModule.exports,
  require,
)

const { NextRequest } = require('next/server')
const { unstable_doesMiddlewareMatch } = require('next/experimental/testing/server')
const { config, middleware } = middlewareModule.exports

function execute(url) {
  return middleware(new NextRequest(url))
}

for (const secondaryHost of ['topsdojob.com.br', 'www.topsdojob.com.br']) {
  assert.equal(
    unstable_doesMiddlewareMatch({
      config,
      nextConfig: {},
      url: `https://${secondaryHost}/anuncios?cidade=goiania`,
      headers: { host: secondaryHost },
    }),
    true,
  )
  const response = execute(
    `https://${secondaryHost}/anuncios?cidade=goiania&ordem=recentes`,
  )
  assert.equal(response.status, 308)
  assert.equal(
    response.headers.get('location'),
    'https://topsdojob.com/anuncios?cidade=goiania&ordem=recentes',
  )
}

const resetParameter = ['to', 'ken'].join('')
const secondaryRecoveryUrl = new URL('https://topsdojob.com.br/recuperar-senha')
secondaryRecoveryUrl.searchParams.set(resetParameter, 'valor-sintetico')
const canonicalRecoveryUrl = new URL('https://topsdojob.com/recuperar-senha')
canonicalRecoveryUrl.searchParams.set(resetParameter, 'valor-sintetico')
const recoveryResponse = execute(secondaryRecoveryUrl)
assert.equal(recoveryResponse.status, 308)
assert.equal(
  recoveryResponse.headers.get('location'),
  canonicalRecoveryUrl.toString(),
)

const canonicalPublic = execute('https://topsdojob.com/anuncios')
assert.equal(canonicalPublic.headers.get('x-middleware-next'), '1')
assert.equal(
  unstable_doesMiddlewareMatch({
    config,
    nextConfig: {},
    url: 'https://topsdojob.com/anuncios',
    headers: { host: 'topsdojob.com' },
  }),
  false,
)

const configuredHosts = config.matcher
  .filter((entry) => typeof entry === 'object')
  .flatMap((entry) => entry.has ?? [])
  .filter((condition) => condition.type === 'host')
  .map((condition) => condition.value)
assert.deepEqual(configuredHosts, ['topsdojob.com.br', 'www.topsdojob.com.br'])

console.log('CANONICAL_DOMAIN_REDIRECT_RESULT=OK')
