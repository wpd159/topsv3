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

function workflowJob(workflow, name) {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return workflow.match(new RegExp(`\\n  ${escaped}:[\\s\\S]*?(?=\\n  [a-z][a-z0-9-]*:|$)`))?.[0] ?? ''
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
  const controller = fs.readFileSync(path.join(root, 'scripts/deploy/v2/controller.sh'), 'utf8')
  const artifact = fs.readFileSync(path.join(root, 'scripts/deploy/v2/artifact.sh'), 'utf8')
  const modeGuardJob = workflowJob(workflow, 'mode-guard')
  const buildOnceJob = workflowJob(workflow, 'build-once')
  const verifyJob = workflowJob(workflow, 'verify-clean-runner')
  const diagnosticJob = workflowJob(workflow, 'diagnose-gateway-listagem')
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
    /(?:backend\.)?topsdojob\.com/i,
    /cloudflarestorage\.com/i,
    /api\.efipay/i,
  ]) {
    if (forbidden.test(combined)) fail(`contrato V2 contem padrao proibido: ${forbidden}`)
  }

  if (!/runs-on:\s*ubuntu-24\.04/.test(workflow)) fail('runner nao fixado em ubuntu-24.04')
  if (!/minio:[\s\S]*?ports:\s*\n\s*- "127\.0\.0\.1:19000:9000"/.test(compose)) {
    fail('MinIO sintetico deve ser publicado na porta fixa do daemon isolado')
  }
  if ((compose.match(/networks: \[lab, host-access\]/g) ?? []).length !== 1 ||
      !/\n  host-access:\s*\n    driver: bridge/.test(compose)) {
    fail('somente o MinIO deve usar a bridge local de publicacao')
  }
  for (const service of ['backend', 'frontend', 'gateway']) {
    const block = compose.match(new RegExp(`\\n  ${service}:[\\s\\S]*?(?=\\n  [a-z-]+:|\\nnetworks:)`))?.[0] ?? ''
    if (/\n\s+ports:/.test(block)) fail(`${service} nao deve publicar porta no laboratorio`)
  }
  const prepareLab = controller.indexOf('v2_prepare_lab "${evidence_dir}"')
  const backendTests = controller.indexOf('v2_run_backend_tests "${evidence_dir}"')
  if (prepareLab < 0 || backendTests < 0 || prepareLab > backendTests) {
    fail('laboratorio sintetico deve preceder o Maven verify')
  }
  if (!lab.includes('runtime-fixtures')
      || !controller.includes('"${V2_ROOT}/backend/target" "${v048_backend}/target"')) {
    fail('fixtures runtime ou consolidacao V048 ausentes do controlador')
  }
  if (!artifact.includes('TEST_DEPENDENCIES=OFFLINE_VALIDATED')
      || !artifact.includes('test-dependencies.tar')
      || !artifact.includes('npm ci --offline')
      || !artifact.includes('mvn --offline')) {
    fail('artefato imutavel deve transportar dependencias Maven/npm validadas offline')
  }
  if ((controller.match(/mvn --offline/g) ?? []).length < 2
      || !controller.includes('npm ci --offline')
      || !/v2_run_frontend_tests\(\) \{[\s\S]*?docker run --rm --network none/.test(controller)) {
    fail('runners verify devem executar Maven e npm sem acesso a repositorios externos')
  }
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
  if (!modeGuardJob.includes("github.event.pull_request.head.sha || github.sha")) {
    fail('SHA de PR deve vir explicitamente da cabeca do PR')
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
  const diagnosticLabel = "contains(github.event.pull_request.labels.*.name, 'pipeline-v2-diagnostic')"
  if (modeGuardJob.includes(diagnosticLabel)
      || /^\s+if:/m.test(buildOnceJob)
      || !verifyJob.includes(`!${diagnosticLabel}`)
      || !diagnosticJob.includes(diagnosticLabel)
      || !diagnosticJob.includes('needs: build-once')) {
    fail('build-once deve ser comum; diagnostico ignora a matriz e depende diretamente dele')
  }
  if (!buildOnceJob.includes('artifact-name: ${{ steps.artifact-identity.outputs.name }}')
      || !buildOnceJob.includes('artifact-id: ${{ steps.release-artifact.outputs.artifact-id }}')
      || !buildOnceJob.includes("printf 'name=topsdojob-v2-release-%s-%s-%s\\n'")
      || !buildOnceJob.includes('name: ${{ steps.artifact-identity.outputs.name }}')) {
    fail('build-once deve expor artefato exclusivo por SHA, run e attempt')
  }
  if (!verifyJob.includes('name: ${{ needs.build-once.outputs.artifact-name }}')
      || !diagnosticJob.includes('name: ${{ needs.build-once.outputs.artifact-name }}')) {
    fail('runners devem baixar pelo output exato do build-once')
  }
  if (!diagnosticJob.includes('ARTIFACT_SOURCE_SHA: ${{ needs.build-once.outputs.source-sha }}')
      || !diagnosticJob.includes('CURRENT_ARTIFACT_ID: ${{ needs.build-once.outputs.artifact-id }}')
      || !diagnosticJob.includes('CURRENT_RUN_ID: ${{ github.run_id }}')) {
    fail('diagnostico deve validar proveniencia do mesmo run')
  }
  if (/run-id:|github-token:|repository:/.test(diagnosticJob)
      || /33288113610|9725168179|2a0fc6765c034a695854cc3a9a3c757f25005933/.test(workflow)) {
    fail('diagnostico ainda referencia artefato ou run anterior')
  }
  if (!diagnosticJob.includes('controller.sh diagnose')
      || diagnosticJob.includes('controller.sh build')) {
    fail('runner diagnostico deve consumir artefato sem rebuild')
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
  const [output, gitSha, archive, dependencyArchive, ...imageArgs] = args
  if (!/^[a-f0-9]{40}$/.test(gitSha ?? '')) fail('git SHA invalido para manifesto')
  if (!fs.statSync(archive).isFile() || fs.statSync(archive).size === 0) fail('TAR vazio')
  if (!fs.statSync(dependencyArchive).isFile() || fs.statSync(dependencyArchive).size === 0) {
    fail('TAR de dependencias vazio')
  }
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
    testDependencies: {
      name: path.basename(dependencyArchive),
      sha256: sha256(dependencyArchive),
      bytes: fs.statSync(dependencyArchive).size,
      offlineValidated: true,
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
  const dependencyArchive = path.join(bundle, manifest.testDependencies?.name ?? '')
  if (manifest.testDependencies?.offlineValidated !== true
    || !fs.existsSync(dependencyArchive)
    || fs.lstatSync(dependencyArchive).isSymbolicLink()) {
    fail('TAR de dependencias ausente, nao validado ou symlink')
  }
  if (sha256(dependencyArchive) !== manifest.testDependencies.sha256) {
    fail('hash do TAR de dependencias divergente')
  }
  if (fs.statSync(dependencyArchive).size !== manifest.testDependencies.bytes) {
    fail('tamanho do TAR de dependencias divergente')
  }
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
  const [listFile, output, ...requestedRoots] = args
  const discovered = JSON.parse(fs.readFileSync(listFile, 'utf8')).tests
  const reportRoots = requestedRoots.length > 0
    ? requestedRoots.map((entry) => path.resolve(entry))
    : [path.join(root, 'backend/target')]
  const reportMap = new Map()
  for (const reportRoot of reportRoots) {
    for (const directory of ['surefire-reports', 'failsafe-reports']) {
      for (const file of walk(path.join(reportRoot, directory),
        (candidate) => /^TEST-.*\.xml$/.test(path.basename(candidate)))) {
        reportMap.set(path.basename(file), file)
      }
    }
  }
  const reportFiles = [...reportMap.values()]
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
  const pngBytes = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64')
  const sourceObjectKey = 'hml/midias-aprovadas/synthetic/pipeline-v2-source.png'
  const sourceObject = path.join(objectRoot, ...sourceObjectKey.split('/'))
  fs.mkdirSync(path.dirname(sourceObject), { recursive: true, mode: 0o700 })
  fs.writeFileSync(sourceObject, pngBytes, { mode: 0o600 })
  const pngSha256 = crypto.createHash('sha256').update(pngBytes).digest('hex')
  const kycCache = path.join(outputDir, 'kyc-cache')
  fs.mkdirSync(kycCache, { recursive: true, mode: 0o700 })
  fs.writeFileSync(path.join(kycCache, `${pngSha256}.bin`), pngBytes, { mode: 0o600 })
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
  fs.writeFileSync(path.join(outputDir, 'fixture.json'), `${JSON.stringify({
    count,
    keys,
    sourceObjectKey,
    pngSha256,
  }, null, 2)}\n`, { mode: 0o600 })
  console.log(`FIXTURE_PREVIEWS=${count}`)
}

function md5(value) {
  return crypto.createHash('md5').update(value).digest('hex')
}

function uuidFromMd5(value) {
  const hex = md5(value)
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

function writePrivateFile(file, content) {
  fs.writeFileSync(file, content.endsWith('\n') ? content : `${content}\n`, {
    encoding: 'ascii',
    mode: 0o600,
  })
}

function runtimeFixtures(args) {
  const [outputDir, endpoint, fixtureDir] = args
  if (!/^https:\/\/127\.0\.0\.1:[1-9][0-9]{0,4}$/.test(endpoint ?? '')) {
    fail('endpoint MinIO local invalido')
  }
  if (fs.existsSync(outputDir)) fail('diretorio de fixtures runtime ja existe')
  const fixtureMetadata = JSON.parse(fs.readFileSync(path.join(fixtureDir, 'fixture.json'), 'utf8'))
  const png = fs.readFileSync(path.join(fixtureDir, 'objects', ...fixtureMetadata.sourceObjectKey.split('/')))
  const pngSha256 = crypto.createHash('sha256').update(png).digest('hex')
  if (pngSha256 !== fixtureMetadata.pngSha256) fail('PNG sintetico divergente')
  fs.mkdirSync(outputDir, { recursive: true, mode: 0o700 })

  const sourceUrl = `${endpoint}/topsdojob-v2-public/${fixtureMetadata.sourceObjectKey}`
  const documentReference = 'r2://pipeline-v2/kyc/documento-1.png'
  const documentReferenceHash = md5(documentReference)
  const sourceStorageReference = `r2://topsdojob-v2-public/${fixtureMetadata.sourceObjectKey}`
  const privateReferenceHash = crypto.createHash('sha256')
    .update(sourceStorageReference).digest('hex')
  const logicalMediaHash = crypto.createHash('sha256')
    .update('pipeline-v2-logical-media-5001').digest('hex')
  const envioHash = crypto.createHash('sha256').update('pipeline-v2-kyc-envio').digest('hex')
  const userV3Id = uuidFromMd5('legacy:usuario:1')
  const destinationPrivateKey = `hml/midias-pendentes/importacao/anuncios/101/midias/${logicalMediaHash}/original/${pngSha256}.png`
  const destinationDocumentKey = `hml/documentos/importacao/pipeline-v2/sha256/${pngSha256.slice(0, 2)}/${pngSha256}.png`

  writePrivateFile(path.join(outputDir, 'r2-public-input.tsv'), [
    '101',
    md5(sourceUrl),
    Buffer.from(sourceUrl, 'utf8').toString('base64'),
    'SAFE_PUBLIC',
    'PUBLIC_API_ANONYMOUS_NO_AGE_GATE',
  ].join('\t'))
  writePrivateFile(path.join(outputDir, 'r2-private-input.tsv'), [
    '101', logicalMediaHash, 'protected_media_assets', '5001', 'FOTO', 'ORIGINAL',
    'true', privateReferenceHash, 'PUBLIC_MEDIA', fixtureMetadata.sourceObjectKey, '0',
  ].join('\t'))
  writePrivateFile(path.join(outputDir, 'r2-kyc-input.tsv'), [
    '1', documentReferenceHash, documentReferenceHash, pngSha256, 'png',
    'PENDENTE', 'FRENTE', '', 'ELIGIVEL',
  ].join('\t'))
  fs.cpSync(path.join(fixtureDir, 'kyc-cache'), path.join(outputDir, 'kyc-cache'), {
    recursive: true,
    errorOnExist: true,
  })

  writePrivateFile(path.join(outputDir, 'import-private-media.tsv'), [
    '101', logicalMediaHash, 'protected_media_assets', '5001', 'FOTO', 'ORIGINAL',
    'true', privateReferenceHash, destinationPrivateKey, pngSha256, String(png.length),
    'image/png', '1', '1', '', '0', 'MIGRADA', '',
  ].join('\t'))
  writePrivateFile(path.join(outputDir, 'import-kyc.tsv'), [
    userV3Id, documentReferenceHash, documentReferenceHash, destinationDocumentKey,
    pngSha256, String(png.length), 'image/png', 'png', 'FRENTE', envioHash,
    'MIGRADA', '', 'PENDENTE',
  ].join('\t'))

  const sql = `
CREATE TABLE usuarios (
  id bigint PRIMARY KEY, username text, email text, senha text, status text, role text,
  is_verificado boolean, two_factor_ativo boolean, nome_completo text, cpf text,
  telefone text, data_nascimento date, advertiser_verification_status text,
  criado_em timestamp without time zone
);
CREATE TABLE anuncios (
  id bigint PRIMARY KEY, usuario_id bigint, slug text, titulo text, descricao text,
  status text, categoria text, preco numeric(12,2), criado_em timestamp without time zone,
  removido_logicamente_em timestamp without time zone, cidade_id bigint, bairro_id bigint,
  visualizacoes bigint
);
CREATE TABLE estado (id bigint PRIMARY KEY, uf text, nome text);
CREATE TABLE cidade (id bigint PRIMARY KEY, estado_id bigint, nome text, slug text);
CREATE TABLE bairro (id bigint PRIMARY KEY, cidade_id bigint, nome text);
CREATE TABLE anuncio_servicos (anuncio_id bigint, servico text);
CREATE TABLE anuncio_local_atendimento (anuncio_id bigint, local_atendimento text);
CREATE TABLE anuncio_fotos (anuncio_id bigint, url_foto text);
CREATE TABLE anuncio_videos (anuncio_id bigint, url_video text);
CREATE TABLE anuncio_view_log (id bigint PRIMARY KEY, anuncio_id bigint, visto_em timestamp without time zone);
CREATE TABLE cliques_whatsapp (id bigint PRIMARY KEY, anuncio_id bigint, data_clique timestamp without time zone);
CREATE TABLE protected_media_assets (
  id bigint PRIMARY KEY, anuncio_id bigint, media_type text, original_storage_ref text,
  legacy_original_url text, preview_public_url text
);
CREATE TABLE anuncio_revisions (
  id bigint PRIMARY KEY, anuncio_id bigint, status text, reviewed_at timestamp without time zone
);
CREATE TABLE stories (id bigint PRIMARY KEY, anuncio_id bigint, usuario_id bigint, midia_url text);
CREATE TABLE usuario_documentos (id bigint PRIMARY KEY, usuario_id bigint, documento_url text);
CREATE TABLE advertiser_verification_requests (
  id bigint PRIMARY KEY, usuario_id bigint, reviewed_by_user_id bigint,
  reviewed_at timestamp without time zone, requested_at timestamp without time zone
);
CREATE TABLE usuario_favoritos (usuario_id bigint, anuncio_id bigint);
CREATE TABLE feature_ativacao (
  id bigint PRIMARY KEY, usuario_id bigint, anuncio_id bigint, codigo text,
  creditos_cobrados integer, status text, ativado_em timestamp without time zone,
  expira_em timestamp without time zone
);
CREATE TABLE feature_catalogo (id bigint PRIMARY KEY, codigo text, ativo boolean);
CREATE TABLE feature_catalogo_duracoes (
  id bigint PRIMARY KEY, feature_catalogo_id bigint, duracao_dias integer, creditos integer
);
CREATE TABLE creditos_usuario (id bigint PRIMARY KEY, usuario_id bigint, saldo integer);
CREATE TABLE historico_creditos (
  id bigint PRIMARY KEY, usuario_id bigint, quantidade integer, tipo text,
  criado_em timestamp without time zone
);
CREATE TABLE pagamentos_mp (
  id bigint PRIMARY KEY, usuario_id bigint, provider text, status text,
  valor numeric(12,2), creditos integer
);
CREATE TABLE suporte_mensagens (id bigint PRIMARY KEY, enviado_por_id bigint);

INSERT INTO usuarios (
  id, username, email, senha, status, role, is_verificado, two_factor_ativo,
  nome_completo, cpf, telefone, data_nascimento, advertiser_verification_status, criado_em
) VALUES
  (1, 'pipeline-user-1', 'pipeline-user-1@example.test', '$2a$10$' || repeat('A', 53),
   'ATIVO', 'USER', true, false, 'Pessoa Pipeline Um', '52998224725', '11987654321',
   DATE '1990-01-01', 'PENDENTE', TIMESTAMP '2026-08-29 12:00:00'),
  (2, 'pipeline-user-2', 'pipeline-user-2@example.test', '$2a$10$' || repeat('B', 53),
   'ATIVO', 'USER', true, false, 'Pessoa Pipeline Dois', NULL, NULL,
   DATE '1992-02-02', 'NAO_INICIADO', TIMESTAMP '2026-08-29 12:00:00');
INSERT INTO estado VALUES (11, 'SP', 'Sao Paulo');
INSERT INTO cidade VALUES (21, 11, 'Sao Paulo', 'sao-paulo');
INSERT INTO bairro VALUES (31, 21, 'Centro');
INSERT INTO anuncios VALUES
  (101, 1, 'pipeline-anuncio-101', 'Anuncio Pipeline 101', 'Fixture sintetica',
   'ATIVO', 'ACOMPANHANTE_FEMININA', 100.00, TIMESTAMP '2026-08-29 12:00:00',
   NULL, 21, 31, 0),
  (102, 1, 'pipeline-anuncio-102', 'Anuncio Pipeline 102', 'Fixture sintetica',
   'ATIVO', 'ACOMPANHANTE_FEMININA', 110.00, TIMESTAMP '2026-08-29 12:01:00',
   NULL, 21, 31, 0),
  (103, 2, 'pipeline-anuncio-103', 'Anuncio Pipeline 103', 'Fixture sintetica',
   'ATIVO', 'ACOMPANHANTE_FEMININA', 120.00, TIMESTAMP '2026-08-29 12:02:00',
   NULL, 21, 31, 0);
INSERT INTO anuncio_servicos VALUES (101, 'ORAL');
INSERT INTO anuncio_local_atendimento VALUES (101, 'A_COMBINAR');
INSERT INTO protected_media_assets VALUES (
  5001, 101, 'FOTO', '${sourceStorageReference}', NULL, NULL
);
INSERT INTO usuario_documentos VALUES (7001, 1, '${documentReference}');
`
  writePrivateFile(path.join(outputDir, 'source-snapshot.sql'), sql.trimStart())
  writePrivateFile(path.join(outputDir, 'runtime-fixtures.json'), `${JSON.stringify({
    schemaVersion: 1,
    endpointKind: 'loopback-minio-tls',
    files: [
      'r2-public-input.tsv', 'r2-private-input.tsv', 'r2-kyc-input.tsv',
      'import-private-media.tsv', 'import-kyc.tsv', 'source-snapshot.sql',
    ],
  }, null, 2)}`)
  console.log('RUNTIME_FIXTURES=OK sourceRows=3 r2RealMutations=0')
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
  case 'runtime-fixtures': runtimeFixtures(args); break
  case 'file-policy': assertRegularLfFile(path.resolve(args[0])); console.log('FILE_POLICY=OK'); break
  default: fail(`comando desconhecido: ${command}`)
}
