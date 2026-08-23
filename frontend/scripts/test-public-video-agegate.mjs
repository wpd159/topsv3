import assert from "node:assert/strict"
import { readFile } from "node:fs/promises"
import { resolve } from "node:path"
import { fileURLToPath } from "node:url"
import ts from "typescript"

const frontendRoot = resolve(fileURLToPath(new URL("..", import.meta.url)))
const paths = {
  policy: "src/lib/compliance/visitor-access-policy.ts",
  access: "src/lib/compliance/visitor-access.ts",
  modal: "src/components/compliance/visitor-verification-modal.tsx",
  image: "src/components/compliance/sensitive-image.tsx",
  video: "src/components/compliance/sensitive-video.tsx",
  media: "src/lib/media/public-media.ts",
  gallery: "src/app/(public-routes)/anuncios/[slug]/componentes/header-tabs.tsx",
}
const entries = await Promise.all(
  Object.entries(paths).map(async ([name, relativePath]) => [
    name,
    await readFile(resolve(frontendRoot, relativePath), "utf8"),
  ]),
)
const sources = Object.fromEntries(entries)

const transpiledPolicy = ts.transpileModule(sources.policy, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText
const policy = await import(
  `data:text/javascript;base64,${Buffer.from(transpiledPolicy).toString("base64")}`
)

const transpiledMedia = ts.transpileModule(sources.media, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText
const media = await import(
  `data:text/javascript;base64,${Buffer.from(transpiledMedia).toString("base64")}`
)

const now = Date.parse("2026-08-22T12:00:00Z")
const future = "2026-08-22T13:00:00Z"
const expired = "2026-08-22T11:00:00Z"
const reinforced = {
  globalAccepted: true,
  verified: true,
  level: "REINFORCED",
  expiresAt: future,
}

assert.equal(
  policy.statusSatisfazEscopo(reinforced, "MIDIA_RESTRITA", "REINFORCED", now),
  true,
)
assert.equal(
  policy.statusSatisfazEscopo(
    { ...reinforced, level: "STRONG" },
    "MIDIA_RESTRITA",
    "REINFORCED",
    now,
  ),
  true,
)
assert.equal(
  policy.statusSatisfazEscopo(
    { ...reinforced, level: "LIGHT" },
    "MIDIA_RESTRITA",
    "REINFORCED",
    now,
  ),
  false,
)
assert.equal(
  policy.statusSatisfazEscopo(
    { ...reinforced, expiresAt: expired },
    "MIDIA_RESTRITA",
    "REINFORCED",
    now,
  ),
  false,
)
assert.equal(
  policy.statusSatisfazEscopo(
    { ...reinforced, globalAccepted: false },
    "MIDIA_RESTRITA",
    "REINFORCED",
    now,
  ),
  false,
)
assert.equal(
  policy.statusSatisfazEscopo(reinforced, "CONTEUDO_EXPLICITO", "STRONG", now),
  false,
)
assert.equal(
  policy.statusSatisfazEscopo(
    {
      ...reinforced,
      explicitVerified: true,
      explicitLevel: "STRONG",
      explicitExpiresAt: future,
    },
    "CONTEUDO_EXPLICITO",
    "STRONG",
    now,
  ),
  true,
)

assert.match(sources.gallery, /if \(midia\.tipo === 'VIDEO'\)/)
assert.match(sources.gallery, /<SensitiveVideo/)
assert.match(sources.gallery, /thumbnail: item\.tipo === 'VIDEO'/)
assert.doesNotMatch(sources.gallery, /<video/)
assert.doesNotMatch(sources.gallery, /fontePublicaSegura/)

assert.match(sources.video, /<video/)
assert.match(sources.video, /controls/)
assert.match(sources.video, /playsInline/)
assert.match(sources.video, /preload="metadata"/)
assert.match(sources.video, /mimeTypeVideoDeclaravel\(midia\.mimeType\)/)
assert.doesNotMatch(sources.video, /autoPlay/)
assert.doesNotMatch(sources.video, /\bloop\b/)
assert.doesNotMatch(sources.video, /next\/image|SensitiveImage/)
assert.match(sources.video, /thumbnail \|\| blocked\s*\? null/)
assert.match(sources.video, /compliance\/visitor\/media/)
assert.match(sources.video, /Mídia indisponível/)
assert.match(sources.video, /Tentar novamente/)
assert.equal((sources.video.match(/obterStatusVisitante\(true\)/g) ?? []).length, 1)
assert.doesNotMatch(sources.video, /setSessionAuthorized\(false\)/)
assert.doesNotMatch(sources.image, /setSessionAuthorized\(false\)/)
assert.match(sources.image, /Tentar novamente/)

assert.equal(media.mimeTypeVideoDeclaravel("video/quicktime"), undefined)
assert.equal(media.mimeTypeVideoDeclaravel(" VIDEO/QUICKTIME; codecs=h264 "), undefined)
assert.equal(media.mimeTypeVideoDeclaravel("video/mp4"), "video/mp4")
assert.equal(media.mimeTypeVideoDeclaravel("image/jpeg"), undefined)

const statusIndex = sources.modal.indexOf("const global = await obterStatusVisitante(true)")
const policyIndex = sources.modal.indexOf("if (statusSatisfazEscopo(global, scope, level))", statusIndex)
const challengeIndex = sources.modal.indexOf("const challengeKey =", policyIndex)
const createIndex = sources.modal.indexOf("createVisitorChallenge", challengeIndex)
assert.ok(statusIndex >= 0)
assert.ok(policyIndex > statusIndex)
assert.ok(challengeIndex > policyIndex)
assert.ok(createIndex > challengeIndex)
assert.match(sources.modal, /notificarMudancaVerificacao\(global\)/)
assert.match(sources.video, /AGE_VERIFICATION_CHANGED_EVENT/)
assert.match(sources.image, /AGE_VERIFICATION_CHANGED_EVENT/)

for (const source of Object.values(sources)) {
  assert.doesNotMatch(source, /localStorage|sessionStorage/)
}

console.log("PUBLIC_VIDEO_AGE_GATE_CHECKS=45")
console.log("PUBLIC_VIDEO_AGE_GATE_RESULT=OK")
