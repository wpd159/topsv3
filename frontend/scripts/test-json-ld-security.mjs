import assert from "node:assert/strict"
import { readFileSync, readdirSync } from "node:fs"
import { extname, join, relative } from "node:path"
import { fileURLToPath } from "node:url"
import ts from "typescript"

const frontendRoot = fileURLToPath(new URL("..", import.meta.url))
const serializerPath = join(frontendRoot, "src", "lib", "seo", "json-ld.ts")
const serializerSource = readFileSync(serializerPath, "utf8")
const rootLayoutSource = readFileSync(join(frontendRoot, "src", "app", "layout.tsx"), "utf8")

const { outputText } = ts.transpileModule(serializerSource, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022,
  },
})
const serializerModule = { exports: {} }
new Function("module", "exports", outputText)(serializerModule, serializerModule.exports)
const { serializeJsonLd } = serializerModule.exports

assert.equal(typeof serializeJsonLd, "function", "o serializador central deve ser executavel")

const payload = {
  titulo: "Teste </script><script>globalThis.comprometido=true</script>",
  html: "<img src=x onerror=alert(1)>",
  ampersand: "A & B",
  separadores: "linha\u2028paragrafo\u2029fim",
}
const serialized = serializeJsonLd(payload)

assert.doesNotMatch(serialized, /<\/script>/i)
assert.equal(serialized.includes("<"), false, "a saida nao pode conter < literal")
assert.equal(serialized.includes(">"), false, "a saida nao pode conter > literal")
assert.equal(serialized.includes("&"), false, "a saida nao pode conter & literal")
assert.equal(serialized.includes("\u2028"), false, "a saida nao pode conter U+2028 literal")
assert.equal(serialized.includes("\u2029"), false, "a saida nao pode conter U+2029 literal")
assert.deepEqual(JSON.parse(serialized), payload, "o conteudo logico deve ser preservado")
assert.throws(() => serializeJsonLd(undefined), TypeError)

function collectSourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return collectSourceFiles(path)
    return [".ts", ".tsx"].includes(extname(entry.name)) ? [path] : []
  })
}

const jsonLdFiles = collectSourceFiles(join(frontendRoot, "src")).filter((path) =>
  readFileSync(path, "utf8").includes("application/ld+json"),
)
assert.ok(jsonLdFiles.length > 0, "ao menos um renderizador JSON-LD deve existir")

for (const path of jsonLdFiles) {
  const source = readFileSync(path, "utf8")
  const scripts = source.match(/application\/ld\+json/g) ?? []
  const centralCalls = source.match(/__html:\s*serializeJsonLd\(/g) ?? []
  const label = relative(frontendRoot, path)

  assert.equal(
    centralCalls.length,
    scripts.length,
    `${label} deve usar o serializador central em cada script JSON-LD`,
  )
  assert.doesNotMatch(
    source,
    /dangerouslySetInnerHTML\s*=\s*\{\{[\s\S]{0,400}?JSON\.stringify\(/,
    `${label} nao pode serializar JSON-LD diretamente`,
  )
}

assert.doesNotMatch(
  rootLayoutSource,
  /SearchAction|potentialAction/,
  "o schema global nao pode reintroduzir a busca interna como SearchAction",
)

const blogBodySource = readFileSync(join(frontendRoot, 'src', 'lib', 'blog', 'safe-blog-body.tsx'), 'utf8')
const blogBodyModule = { exports: {} }
const blogBodyCode = ts.transpileModule(blogBodySource, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022,
    jsx: ts.JsxEmit.ReactJSX,
  },
}).outputText
new Function('module', 'exports', 'require', blogBodyCode)(blogBodyModule, blogBodyModule.exports, (name) => {
  if (name === '@/lib/public-site-assets') return { getPublicSiteOrigin: () => 'https://topsdojob.com' }
  if (name === 'react/jsx-runtime') return { jsx: (type, props) => ({ type, props }) }
  throw new Error(`Unexpected blog renderer dependency: ${name}`)
})
const renderBlogBody = (conteudo) => blogBodyModule.exports.SafeBlogPostBody({ conteudo }).props.dangerouslySetInnerHTML.__html

assert.equal(renderBlogBody('<p>Segurança &amp; privacidade &#38; respeito &#x26; cuidado.</p>'),
  '<p>Segurança &amp; privacidade &#38; respeito &#x26; cuidado.</p>')
assert.equal(renderBlogBody('<p>A & B e &lt;script&gt;texto&lt;/script&gt;.</p>'),
  '<p>A &amp; B e &lt;script&gt;texto&lt;/script&gt;.</p>')
assert.equal(renderBlogBody('<p>Ol&aacute; &copy; 2026, &amp;amp; e &#x3c;img&#x3e;.</p>'),
  '<p>Ol&aacute; &copy; 2026, &amp;amp; e &#x3c;img&#x3e;.</p>')
assert.match(renderBlogBody('<a href="/blog?x=1&amp;y=2">A &amp; B</a>'),
  /href="\/blog\?x=1&amp;y=2"[^>]*>A &amp; B<\/a>/)
assert.match(renderBlogBody('<a href="https://example.invalid/?x=1&amp;y=2">Externo</a>'),
  /href="https:\/\/example\.invalid\/\?x=1&amp;y=2" target="_blank" rel="noopener noreferrer"/)
for (const href of ['&#106;avascript:alert(1)', 'javascript&colon;alert(1)', '/&#47;example.invalid', '/&#X2F;example.invalid', '/&sol;example.invalid', '/&Tab;/example.invalid', '/&bsol;example.invalid', '/\\example.invalid']) {
  assert.doesNotMatch(renderBlogBody(`<a href="${href}">Bloqueado</a>`), /<a href=/,
    `o href HTML codificado ou normalizado nao pode contornar a allowlist: ${href}`)
}
assert.doesNotMatch(renderBlogBody('<script>alert(1)</script><p onclick="alert(1)">Seguro</p>'), /<script|onclick=/i)
assert.match(renderBlogBody('<a href="https://topsdojob.com/blog?x=1&#38;y=2">Interno</a>'),
  /href="\/blog\?x=1&amp;y=2"/)
assert.doesNotMatch(renderBlogBody('<a href="https://example.invalid/?q=&quot; onclick=&quot;alert(1)">Seguro</a>'), /" onclick=/i)
assert.match(renderBlogBody('[A &amp; B](/blog?x=1&y=2)'),
  /href="\/blog\?x=1&amp;y=2"[^>]*>A &amp;amp; B<\/a>/,
  'texto Markdown permanece literal; nao e reinterpretado como HTML')

console.log(`JSON_LD_SECURITY_RESULT=OK files=${jsonLdFiles.length} blog_entities=OK`)
