import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
const lockPath = path.join(root, 'deploy/v2/images.lock.json')
const lock = JSON.parse(fs.readFileSync(lockPath, 'utf8'))

function fail(message) {
  throw new Error(message)
}

function sha256(file) {
  const hash = crypto.createHash('sha256')
  hash.update(fs.readFileSync(file))
  return hash.digest('hex')
}

function walk(directory, predicate = () => true) {
  if (!fs.existsSync(directory)) return []
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(directory, entry.name)
    if (entry.isDirectory()) return walk(full, predicate)
    return predicate(full) ? [full] : []
  })
}

function relative(file) {
  return path.relative(root, file).replaceAll('\\', '/')
}

function technicalFiles() {
  return [
    path.join(root, '.github/workflows/pipeline-production-v2.yml'),
    path.join(root, 'deploy/v2/images.lock.json'),
    path.join(root, 'deploy/v2/compose.yml'),
    ...walk(path.join(root, 'scripts/deploy/v2'), (file) => /\.(?:sh|mjs)$/.test(file)),
  ].sort()
}

function assertRegularLfFile(file) {
  const stat = fs.lstatSync(file)
  if (!stat.isFile() || stat.isSymbolicLink()) fail(`arquivo inseguro: ${relative(file)}`)
  const content = fs.readFileSync(file)
  if (content.includes(Buffer.from('\r\n'))) fail(`CRLF proibido: ${relative(file)}`)
  if (process.platform !== 'win32' && (stat.mode & 0o022)) {
    fail(`arquivo group/world writable: ${relative(file)}`)
  }
}

function contract() {
  const required = technicalFiles()
  for (const file of required) {
    if (!fs.existsSync(file)) fail(`arquivo V2 ausente: ${relative(file)}`)
    assertRegularLfFile(file)
  }

  const helpers = walk(path.join(root, 'scripts/deploy/v2'), (file) =>
    /\.(?:sh|mjs)$/.test(file) && path.basename(file) !== 'controller.sh')
  if (helpers.length > 4) fail(`maximo de quatro helpers excedido: ${helpers.length}`)

  const workflowFile = path.join(root, '.github/workflows/pipeline-production-v2.yml')
  const workflow = fs.readFileSync(workflowFile, 'utf8')
  const compose = fs.readFileSync(path.join(root, 'deploy/v2/compose.yml'), 'utf8')
  const lab = fs.readFileSync(path.join(root, 'scripts/deploy/v2/lab.sh'), 'utf8')
  const runtimeFiles = required.filter((file) => path.basename(file) !== 'contract.mjs')
  const combined = runtimeFiles.map((file) => fs.readFileSync(file, 'utf8')).join('\n')
  const forbiddenLegacy = [
    'aguardar-postgres-efemero.sh',
    'ativar-release-atomica-production.sh',
    'capturar-snapshot-gate-banco-production.sql',
    'criar-backup-validado-production.sh',
    'executar-backfill-previews-production.sh',
    'executar-deploy-remoto-production.sh',
    'fixture-release-atomica.mjs',
    'gerenciar-staging-controlador-production.sh',
    'invocar-deploy-remoto-production.sh',
    'provisionar-admin-ficticio.sh',
    'testar-backfill-previews-production.sh',
    'testar-backup-validado-production.sh',
    'testar-gate-banco-production.sh',
    'testar-gate-flyway-production.sh',
    'testar-health-readiness-contract.mjs',
    'testar-release-atomica-production.sh',
    'testar-staging-controlador-production.sh',
    'testar-stdin-deploy-production.sh',
    'validar-auth-publico-fixture.sh',
    'validar-deploy-production-local.ps1',
    'validar-gate-banco-production.sh',
    'validar-gate-flyway-production.sh',
    'validar-infra-vps-legadas.ps1',
  ]
  for (const legacy of forbiddenLegacy) {
    if (combined.includes(legacy)) fail(`referencia a script legado: ${legacy}`)
  }

  for (const forbidden of [
    /ubuntu-latest/,
    /environment:\s*production/,
    /secrets\./,
    /\bssh\b/,
    /\bscp\b/,
    /\/opt\/topsv3/,
    /C:\\topsv3/i,
    /IndexNow/i,
    /npm audit/,
    /:\s*latest(?:\s|$)/,
  ]) {
    if (forbidden.test(combined)) fail(`contrato V2 contem padrao proibido: ${forbidden}`)
  }

  if (!/runs-on:\s*ubuntu-24\.04/.test(workflow)) fail('runner nao fixado em ubuntu-24.04')
  if (!/dockerfile_inline:\s*\|\n\s*ARG V2_MAVEN_IMAGE\n\s*ARG V2_JRE_IMAGE\n\s*FROM \$\$\{V2_MAVEN_IMAGE\} AS build/.test(compose)) {
    fail('ARGs globais do backend devem preceder o primeiro FROM')
  }
  if (!/options:\s*\n\s*- verify/.test(workflow)) fail('mode=verify nao e a unica opcao')
  if (!/pull_request:\s*\n\s*branches:\s*\n\s*- main/.test(workflow)) {
    fail('workflow novo deve ser verificavel no PR antes de existir na main')
  }
  if (!/github\.event_name == 'pull_request' && 'verify' \|\| inputs\.mode/.test(workflow)) {
    fail('evento de PR deve permanecer estritamente em mode=verify')
  }
  if (/\n\s*- (?:deploy|candidate|switch|rollback)\s*$/m.test(workflow)) {
    fail('modo mutavel exposto no workflow da Fase 1')
  }
  for (const action of Object.values(lock.actions)) {
    if (!workflow.includes(`uses: ${action}`)) fail(`action pinada ausente: ${action}`)
    if (!/@[a-f0-9]{40}$/.test(action)) fail(`action sem commit completo: ${action}`)
  }
  const allowedActions = new Set(Object.values(lock.actions))
  const usedActions = [...workflow.matchAll(/^\s*uses:\s*(\S+)\s*$/gm)].map((match) => match[1])
  if (usedActions.length === 0 || usedActions.some((action) => !allowedActions.has(action))) {
    fail(`workflow usa action nao pinada: ${JSON.stringify(usedActions)}`)
  }
  if (!/matrix:\s*\n\s*run:\s*\[1, 2, 3\]/.test(workflow)) {
    fail('workflow deve usar tres runners independentes')
  }
  const releaseArtifactName = 'name: topsdojob-v2-release-${{ needs.mode-guard.outputs.source-sha }}'
  if (workflow.split(releaseArtifactName).length - 1 !== 2) {
    fail('upload e download devem compartilhar o mesmo nome de artefato')
  }
  if (Object.keys(lock.images).length < 10) fail('lock de imagens incompleto')
  for (const [name, image] of Object.entries(lock.images)) {
    if (!/^sha256:[a-f0-9]{64}$/.test(image.digest)) fail(`digest invalido: ${name}`)
    if (!image.reference.endsWith(`@${image.digest}`)) fail(`reference divergente: ${name}`)
    if (/latest/i.test(image.tag) || /latest/i.test(image.reference)) fail(`latest proibido: ${name}`)
  }
  if (lock.candidateGates.length !== 14 || new Set(lock.candidateGates).size !== 14) {
    fail('lista nominal de candidate gates deve conter exatamente 14 entradas unicas')
  }
  const implementedGates = [...lab.matchAll(/^\s*v2_candidate_gate\s+([a-z0-9_]+)\b/gm)]
    .map((match) => match[1])
  if (JSON.stringify(implementedGates) !== JSON.stringify(lock.candidateGates)) {
    fail(`candidate gates divergem do lock: ${JSON.stringify(implementedGates)}`)
  }
  if (!Array.isArray(lock.nonCriticalSkipAllowlist)) fail('allowlist de skips invalida')
  for (const entry of lock.nonCriticalSkipAllowlist) {
    if (!entry || typeof entry !== 'object'
      || !entry.test || !entry.reason || !entry.owner
      || !/^\d{4}-\d{2}-\d{2}$/.test(entry.expiresOn ?? '')) {
      fail(`entrada incompleta na allowlist de skips: ${JSON.stringify(entry)}`)
    }
  }
  console.log(`CONTRACT_V2=OK helpers=${helpers.length} legacyCalls=0 gates=14`)
}

function manifestCreate(args) {
  const [output, gitSha, archive, ...imageArgs] = args
  if (!/^[a-f0-9]{40}$/.test(gitSha ?? '')) fail('git SHA invalido para manifesto')
  if (!fs.statSync(archive).isFile() || fs.statSync(archive).size === 0) fail('TAR vazio')
  if (imageArgs.length !== 6) fail('manifesto exige tres pares imagem/id')
  const images = []
  for (let index = 0; index < imageArgs.length; index += 2) {
    const name = imageArgs[index]
    const id = imageArgs[index + 1]
    if (!/^sha256:[a-f0-9]{64}$/.test(id)) fail(`image ID invalido: ${name}`)
    images.push({ name, id })
  }
  const files = Object.fromEntries(technicalFiles().map((file) => [relative(file), sha256(file)]))
  const manifest = {
    schemaVersion: 1,
    gitSha,
    platform: lock.platform,
    archive: {
      name: path.basename(archive),
      sha256: sha256(archive),
      bytes: fs.statSync(archive).size,
    },
    images,
    tools: lock.tools,
    lockedImagesSha256: sha256(lockPath),
    sourceFiles: files,
    rebuildAllowedInVerify: false,
  }
  fs.writeFileSync(output, `${JSON.stringify(manifest, null, 2)}\n`, { mode: 0o600 })
}

function manifestVerify(args) {
  const [bundle, expectedSha] = args
  const manifestPath = path.join(bundle, 'release-manifest.json')
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
  if (manifest.schemaVersion !== 1 || manifest.rebuildAllowedInVerify !== false) {
    fail('schema ou politica de rebuild invalida no manifesto')
  }
  if (expectedSha && manifest.gitSha !== expectedSha) fail('SHA do manifesto divergente')
  if (!/^[a-f0-9]{40}$/.test(manifest.gitSha)) fail('SHA do manifesto invalido')
  const archive = path.join(bundle, manifest.archive.name)
  if (!fs.existsSync(archive) || fs.lstatSync(archive).isSymbolicLink()) fail('TAR ausente ou symlink')
  if (sha256(archive) !== manifest.archive.sha256) fail('hash do TAR divergente')
  if (fs.statSync(archive).size !== manifest.archive.bytes) fail('tamanho do TAR divergente')
  if (sha256(lockPath) !== manifest.lockedImagesSha256) fail('lock diverge do build')
  if (!Array.isArray(manifest.images) || manifest.images.length !== 3) fail('imagens de release invalidas')
  for (const [file, expected] of Object.entries(manifest.sourceFiles)) {
    const absolute = path.join(root, file)
    if (!fs.existsSync(absolute) || sha256(absolute) !== expected) fail(`fonte diverge: ${file}`)
  }
  console.log(`MANIFEST_V2=OK sha=${manifest.gitSha} archive=${manifest.archive.sha256}`)
}

function manifestImages(args) {
  const manifest = JSON.parse(fs.readFileSync(path.join(args[0], 'release-manifest.json'), 'utf8'))
  for (const image of manifest.images) console.log(`${image.name}|${image.id}`)
}

function criticalList(args) {
  const output = args[0]
  const javaFiles = walk(path.join(root, 'backend/src/test/java'), (file) => file.endsWith('.java'))
  const tests = []
  for (const file of javaFiles) {
    const source = fs.readFileSync(file, 'utf8')
    if (!source.includes('EnabledIfEnvironmentVariable')) continue
    const variable = source.match(/@EnabledIfEnvironmentVariable\s*\([\s\S]*?named\s*=\s*"([A-Z0-9_]+)"[\s\S]*?\)/)?.[1]
    const className = source.match(/(?:public\s+)?class\s+([A-Za-z0-9_]+)/)?.[1]
    const packageName = source.match(/^package\s+([^;]+);/m)?.[1]
    if (!variable || !className || !packageName) fail(`guard critico ilegivel: ${relative(file)}`)
    tests.push({ variable, className, qualifiedName: `${packageName}.${className}`, source: relative(file) })
  }
  tests.sort((a, b) => a.qualifiedName.localeCompare(b.qualifiedName))
  if (tests.length === 0) fail('nenhum teste critico descoberto')
  const requiredSignals = [
    'V046_POSTGRES17_ENABLED', 'V048_POSTGRES17_ENABLED', 'V053_POSTGRES17_ENABLED',
    'PREVIEW_BACKFILL_POSTGRES17_ENABLED', 'PUBLIC_SEARCH_POSTGRES17_ENABLED',
    'OUTBOX_POSTGRES17_ENABLED', 'CHAT_INTERNO_POSTGRES17_ENABLED',
    'METRICA_PUBLICA_POSTGRES17_ENABLED', 'ANUNCIANTE_CONCURRENCY_POSTGRES17_ENABLED',
    'EFI_PAGAMENTO_POSTGRES17_ENABLED',
  ]
  for (const signal of requiredSignals) {
    if (!tests.some((test) => test.variable === signal)) fail(`integracao critica ausente: ${signal}`)
  }
  fs.writeFileSync(output, `${JSON.stringify({ generatedFromSource: true, tests }, null, 2)}\n`)
  for (const test of tests) console.log(`${test.variable}|${test.qualifiedName}`)
}

function suiteAttributes(xml) {
  const opening = xml.match(/<testsuite\b[^>]*>/)?.[0]
  if (!opening) return null
  const read = (name) => Number(opening.match(new RegExp(`${name}="(\\d+)"`))?.[1] ?? 0)
  return { tests: read('tests'), failures: read('failures'), errors: read('errors'), skipped: read('skipped') }
}

function criticalReport(args) {
  const [listFile, output] = args
  const discovered = JSON.parse(fs.readFileSync(listFile, 'utf8')).tests
  const reportFiles = [
    ...walk(path.join(root, 'backend/target/surefire-reports'), (file) => /^TEST-.*\.xml$/.test(path.basename(file))),
    ...walk(path.join(root, 'backend/target/failsafe-reports'), (file) => /^TEST-.*\.xml$/.test(path.basename(file))),
  ]
  const reportMap = new Map(reportFiles.map((file) => [path.basename(file), file]))
  const results = []
  for (const test of discovered) {
    const exact = `TEST-${test.qualifiedName}.xml`
    const file = reportMap.get(exact) ?? reportFiles.find((candidate) => path.basename(candidate).includes(test.className))
    if (!file) fail(`relatorio critico ausente: ${test.qualifiedName}`)
    const attributes = suiteAttributes(fs.readFileSync(file, 'utf8'))
    if (!attributes || attributes.tests < 1) fail(`teste critico nao executado: ${test.qualifiedName}`)
    if (attributes.skipped !== 0) fail(`teste critico skipped: ${test.qualifiedName}`)
    if (attributes.failures !== 0 || attributes.errors !== 0) fail(`teste critico falhou: ${test.qualifiedName}`)
    results.push({ ...test, ...attributes })
  }
  const allSkipped = reportFiles.flatMap((file) => {
    const xml = fs.readFileSync(file, 'utf8')
    const attrs = suiteAttributes(xml)
    return attrs?.skipped ? [{ file: path.basename(file), skipped: attrs.skipped }] : []
  })
  const allowlist = new Set(lock.nonCriticalSkipAllowlist.map((item) => item.test))
  const unapproved = allSkipped.filter((item) => !allowlist.has(item.file.replace(/^TEST-|\.xml$/g, '')))
  if (unapproved.length) fail(`skips nao autorizados: ${JSON.stringify(unapproved)}`)
  const evidence = {
    generatedFromSource: true,
    criticalTests: results.length,
    criticalSkipped: 0,
    nonCriticalSkipped: allSkipped.reduce((sum, item) => sum + item.skipped, 0),
    results,
  }
  fs.writeFileSync(output, `${JSON.stringify(evidence, null, 2)}\n`)
  console.log(`CRITICAL_TESTS=${results.length} CRITICAL_SKIPPED=0`)
}

function uuidFromIndex(prefix, index) {
  const tail = index.toString(16).padStart(12, '0')
  return `${prefix.padEnd(8, '0')}-0000-4000-8000-${tail}`
}

function fixture(args) {
  const [outputDir, rawCount] = args
  const count = Number(rawCount)
  if (!Number.isInteger(count) || count < 1 || count > 5000) fail('quantidade de fixture invalida')
  if (fs.existsSync(outputDir)) fail('diretorio de fixture ja existe')
  const objectRoot = path.join(outputDir, 'objects')
  fs.mkdirSync(objectRoot, { recursive: true, mode: 0o700 })
  const mediaRows = []
  const linkRows = []
  const keys = []
  const anuncioId = '20000000-0000-4000-8000-000000000001'
  for (let index = 1; index <= count; index += 1) {
    const mediaId = uuidFromIndex('30', index)
    const linkId = uuidFromIndex('40', index)
    const checksum = crypto.createHash('sha256').update(`pipeline-v2-source-${index}`).digest('hex')
    const derived = crypto.createHash('sha256').update(`${mediaId}:${checksum}:v1`).digest('hex').slice(0, 32)
    const previewKey = `hml/midias-aprovadas/restritas-borradas/v1/${derived}.jpg`
    const originalKey = `hml/midias-aprovadas/originais/${mediaId}.jpg`
    mediaRows.push(`('${mediaId}','R2','topsdojob-v2-public','${originalKey}','fixture-${index}.jpg','image/jpeg',8,1,1,NULL,'${checksum}',NULL,'VALIDADO',now())`)
    linkRows.push(`('${linkId}','${anuncioId}','${mediaId}','FOTO','GALERIA',${index - 1},'PUBLICAVEL','RESTRITA_18',now(),now())`)
    const objectFile = path.join(objectRoot, ...previewKey.split('/'))
    fs.mkdirSync(path.dirname(objectFile), { recursive: true })
    fs.writeFileSync(objectFile, 'preview\n', { mode: 0o600 })
    keys.push(previewKey)
  }
  const sql = [
    'BEGIN;',
    "INSERT INTO usuario (id,nome,status,tipo_conta,criado_em,atualizado_em) VALUES ('10000000-0000-4000-8000-000000000001','Pipeline V2','ATIVO','ANUNCIANTE',now(),now());",
    `INSERT INTO anuncio (id,usuario_id,slug,titulo,descricao,status,status_moderacao,categoria,preco,criado_em,atualizado_em) VALUES ('${anuncioId}','10000000-0000-4000-8000-000000000001','pipeline-v2-fixture','Pipeline V2 Fixture','Somente laboratorio','PUBLICADO','APROVADO','ACOMPANHANTE',100,now(),now());`,
    `INSERT INTO arquivo_midia (id,storage_provider,bucket,chave_objeto,nome_original,mime_type,tamanho_bytes,largura,altura,duracao_ms,sha256,etag,status_arquivo,criado_em) VALUES\n${mediaRows.join(',\n')};`,
    `INSERT INTO anuncio_midia (id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,status,visibilidade_midia,criado_em,atualizado_em) VALUES\n${linkRows.join(',\n')};`,
    'COMMIT;',
  ].join('\n')
  fs.writeFileSync(path.join(outputDir, 'fixture.sql'), `${sql}\n`, { mode: 0o600 })
  fs.writeFileSync(path.join(outputDir, 'fixture.json'), `${JSON.stringify({ count, keys }, null, 2)}\n`, { mode: 0o600 })
  console.log(`FIXTURE_PREVIEWS=${count}`)
}

const [command, ...args] = process.argv.slice(2)
switch (command) {
  case 'contract': contract(); break
  case 'manifest-create': manifestCreate(args); break
  case 'manifest-verify': manifestVerify(args); break
  case 'manifest-images': manifestImages(args); break
  case 'critical-list': criticalList(args); break
  case 'critical-report': criticalReport(args); break
  case 'fixture': fixture(args); break
  case 'file-policy': assertRegularLfFile(path.resolve(args[0])); console.log('FILE_POLICY=OK'); break
  default: fail(`comando desconhecido: ${command}`)
}
