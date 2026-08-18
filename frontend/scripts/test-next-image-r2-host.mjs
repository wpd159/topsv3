import assert from "node:assert/strict"
import { spawnSync } from "node:child_process"
import { readFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import ts from "typescript"

const scriptPath = fileURLToPath(import.meta.url)
const frontendRoot = fileURLToPath(new URL("../", import.meta.url))
const marker = "NEXT_IMAGE_R2_PATTERNS="

if (process.argv.includes("--probe")) {
  const nextConfigModule = await import("next/dist/server/config.js")
  const constantsModule = await import("next/constants.js")
  const loadConfig = nextConfigModule.default.default
  const config = await loadConfig(constantsModule.PHASE_PRODUCTION_BUILD, frontendRoot)
  process.stdout.write(`${marker}${JSON.stringify(config.images.remotePatterns)}\n`)
  process.exit(0)
}

function loadPatterns(r2PublicBaseUrl) {
  const env = { ...process.env }
  delete env.R2_PUBLIC_BASE_URL
  if (r2PublicBaseUrl !== undefined) env.R2_PUBLIC_BASE_URL = r2PublicBaseUrl

  const result = spawnSync(process.execPath, [scriptPath, "--probe"], {
    cwd: frontendRoot,
    env,
    encoding: "utf8",
  })

  return {
    ...result,
    patterns:
      result.status === 0
        ? JSON.parse(
            result.stdout
              .split(/\r?\n/)
              .find((line) => line.startsWith(marker))
              .slice(marker.length)
          )
        : null,
  }
}

function assertAccepted(origin, hostname) {
  const result = loadPatterns(origin)
  assert.equal(result.status, 0, result.stderr || result.stdout)
  assert.equal(
    result.patterns.filter(
      (pattern) => pattern.protocol === "https" && pattern.hostname === hostname
    ).length,
    1,
    `hostname ${hostname} deve aparecer uma unica vez`
  )
  return result.patterns
}

function assertRejected(origin) {
  const result = loadPatterns(origin)
  assert.notEqual(result.status, 0, `origem insegura aceita: ${origin}`)
  assert.match(
    `${result.stdout}\n${result.stderr}`,
    /R2_PUBLIC_BASE_URL deve/,
    `falha explicita ausente para ${origin}`
  )
}

const preprodHostname = "pub-d07c33901a354242a1d8bef7e4fd8052.r2.dev"
const productionHostname = "pub-567428d3703244d483815a05a1e0e0d9.r2.dev"

const preprodPatterns = assertAccepted(`https://${preprodHostname}`, preprodHostname)
assertAccepted(`https://${preprodHostname}/`, preprodHostname)
assertAccepted(`https://${productionHostname}`, productionHostname)

const missing = loadPatterns(undefined)
assert.equal(missing.status, 0, missing.stderr || missing.stdout)
assert.equal(
  missing.patterns.filter((pattern) => pattern.hostname === productionHostname).length,
  1,
  "origem publica da producao deve permanecer compativel"
)

const empty = loadPatterns("")
assert.equal(empty.status, 0, empty.stderr || empty.stdout)

for (const origin of [
  "not-a-url",
  "http://pub-d07c33901a354242a1d8bef7e4fd8052.r2.dev",
  "https://",
  "https://pub-d07c33901a354242a1d8bef7e4fd8052.r2.dev/arquivo.webp",
  "https://pub-d07c33901a354242a1d8bef7e4fd8052.r2.dev?parametro=temporario",
  "https://pub-d07c33901a354242a1d8bef7e4fd8052.r2.dev#fragmento",
  "https://example.com",
  "https://localhost",
  "https://127.0.0.1",
  "https://2eb7af56d1fc180174ab864e81adeacf.r2.cloudflarestorage.com",
]) {
  assertRejected(origin)
}

assert.equal(
  preprodPatterns.some(
    (pattern) => pattern.hostname.includes("*") || pattern.protocol.includes("*")
  ),
  false,
  "remotePatterns nao pode autorizar wildcard"
)
assert.equal(
  preprodPatterns.some((pattern) => pattern.hostname.endsWith(".r2.cloudflarestorage.com")),
  false,
  "remotePatterns nao pode autorizar endpoint privado do R2"
)

const publicMediaSource = readFileSync(
  new URL("../src/lib/media/public-media.ts", import.meta.url),
  "utf8"
)
const { outputText: publicMediaOutput } = ts.transpileModule(publicMediaSource, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022,
  },
})
const publicMediaModule = { exports: {} }
new Function("module", "exports", publicMediaOutput)(
  publicMediaModule,
  publicMediaModule.exports,
)
const { imagemPublicaR2 } = publicMediaModule.exports
const detailPageSource = readFileSync(
  new URL("../src/app/(public-routes)/anuncios/[slug]/page.tsx", import.meta.url),
  "utf8"
)
const sensitiveImageSource = readFileSync(
  new URL("../src/components/compliance/sensitive-image.tsx", import.meta.url),
  "utf8"
)
const relatedSource = readFileSync(
  new URL(
    "../src/app/(public-routes)/anuncios/[slug]/componentes/anuncios-relacionados.tsx",
    import.meta.url,
  ),
  "utf8"
)
const adminListSource = readFileSync(
  new URL("../src/features/admin-anuncios/admin-anuncios-list.tsx", import.meta.url),
  "utf8"
)
assert.match(publicMediaSource, /midia\.visibilidadeMidia === "LIVRE"/)
assert.match(publicMediaSource, /midia\.autorizada/)
assert.match(publicMediaSource, /Boolean\(fontePublicaSegura\(midia\)\)/)
assert.match(
  publicMediaSource,
  /midia\.autorizada\s*\?\s*midia\.urlPublica\s*\?\?\s*null\s*:\s*midia\.previewUrl\s*\?\?\s*null/,
)
assert.doesNotMatch(publicMediaSource, /objectKey|chaveObjeto|urlAssinada/i)
assert.match(detailPageSource, /selecionarImagemPublicaSeo/)
assert.match(detailPageSource, /\.\.\.\(imagemPublica/)
assert.equal(
  imagemPublicaR2(`https://${productionHostname}/hml/publica/foto.jpg`),
  true,
)
const sensitiveQueryName = ['to', 'ken'].join('')
for (const unsafeSource of [
  `http://${productionHostname}/foto.jpg`,
  `https://${productionHostname}/foto.jpg?${sensitiveQueryName}=valor`,
  `https://${productionHostname}/foto.jpg#fragmento`,
  'https://example.com/foto.jpg',
  '/imagem-local.jpg',
]) {
  assert.equal(imagemPublicaR2(unsafeSource), false, unsafeSource)
}
assert.match(
  sensitiveImageSource,
  /unoptimized=\{!otimizarImagemPublica \|\| imagemPublicaR2\(fonte\)\}/,
)
assert.match(relatedSource, /unoptimized=\{imagemPublicaR2\(fonte\)\}/)
assert.match(adminListSource, /unoptimized=\{imagemPublicaR2\(item\.miniaturaUrl\)\}/)
assert.doesNotMatch(
  readFileSync(new URL("../next.config.ts", import.meta.url), "utf8"),
  /unoptimized:\s*true/,
  "a otimizacao nao pode ser desabilitada globalmente",
)

console.log("NEXT_IMAGE_R2_RESULT=OK")
