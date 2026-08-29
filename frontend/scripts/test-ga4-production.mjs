import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(scriptDir, '..')
const repoRoot = path.resolve(frontendRoot, '..')

function readRepoFile(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), 'utf8')
}

function collectSourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(directory, entry.name)
    if (entry.isDirectory()) return collectSourceFiles(fullPath)
    return /\.(?:ts|tsx)$/.test(entry.name) ? [fullPath] : []
  })
}

const layout = readRepoFile('frontend/src/app/layout.tsx')
const analytics = readRepoFile(
  'frontend/src/components/analytics/consent-aware-analytics.tsx'
)
const nextConfig = readRepoFile('frontend/next.config.ts')
const productionWorkflow = readRepoFile('.github/workflows/deploy-production.yml')
const ciWorkflow = readRepoFile('.github/workflows/ci.yml')
const productionCompose = readRepoFile('deploy/production/docker-compose.yml')
const runtimeSources = collectSourceFiles(path.join(frontendRoot, 'src'))
  .map((file) => readFileSync(file, 'utf8'))
  .join('\n')

assert.equal(
  (layout.match(/<ConsentAwareAnalytics\s*\/>/g) ?? []).length,
  1,
  'O layout deve montar a integracao consent-aware uma unica vez.'
)
assert.equal(
  (runtimeSources.match(/googletagmanager\.com\/gtag\/js/g) ?? []).length,
  1,
  'Deve existir um unico loader gtag no frontend.'
)
assert.equal(
  new Set(runtimeSources.match(/G-[A-Z0-9]+/g) ?? []).size,
  1,
  'Deve existir um unico Measurement ID no frontend.'
)
assert.equal(
  (runtimeSources.match(/GTM-[A-Z0-9]+/g) ?? []).length,
  0,
  'Google Tag Manager nao deve ser introduzido.'
)
assert.match(analytics, /NEXT_PUBLIC_ANALYTICS_ENABLED/)
assert.match(analytics, /cookie_consent/)
assert.match(analytics, /tops:cookie-consent-updated/)
assert.match(analytics, /gtag\('consent', 'default'/)
assert.match(analytics, /gtag\('config'/)
assert.match(analytics, /document\.createElement\('script'\)/)
assert.doesNotMatch(analytics, /@vercel\/analytics/)

assert.match(
  productionWorkflow,
  /NEXT_PUBLIC_ANALYTICS_ENABLED:\s+"true"/
)
assert.match(productionWorkflow, /node scripts\/test-ga4-production\.mjs/)
assert.doesNotMatch(
  productionWorkflow,
  /NEXT_PUBLIC_ANALYTICS_ENABLED:\s+"false"/
)
assert.match(
  ciWorkflow,
  /NEXT_PUBLIC_ANALYTICS_ENABLED:\s+"false"/
)
assert.equal(
  (productionCompose.match(
    /NEXT_PUBLIC_ANALYTICS_ENABLED:\s+"true"/g
  ) ?? []).length,
  2,
  'Build e runtime do Compose devem fixar GA4 habilitado em producao.'
)
assert.match(productionCompose, /ENV NEXT_PUBLIC_ANALYTICS_ENABLED=true/)
assert.doesNotMatch(
  productionCompose,
  /NEXT_PUBLIC_ANALYTICS_ENABLED[^\r\n]*false/
)
assert.doesNotMatch(
  productionCompose,
  /NEXT_PUBLIC_ANALYTICS_ENABLED:\s+\$\{/,
  'A configuracao canonica de producao nao pode ser desabilitada por env legado.'
)

for (const requiredOrigin of [
  'https://www.googletagmanager.com',
  'https://www.google-analytics.com',
  'https://region1.google-analytics.com',
]) {
  assert.ok(nextConfig.includes(requiredOrigin), `CSP ausente para ${requiredOrigin}`)
}

console.log('GA4_PRODUCTION_TEST=PASS')
