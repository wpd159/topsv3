import assert from "node:assert/strict"
import { readFileSync, readdirSync } from "node:fs"
import { extname, join, relative } from "node:path"
import { fileURLToPath } from "node:url"
import ts from "typescript"

const frontendRoot = fileURLToPath(new URL("..", import.meta.url))
const serializerPath = join(frontendRoot, "src", "lib", "seo", "json-ld.ts")
const serializerSource = readFileSync(serializerPath, "utf8")

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

console.log(`JSON_LD_SECURITY_RESULT=OK files=${jsonLdFiles.length}`)
