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

function isInside(parent, candidate) {
  const rel = path.relative(parent, candidate)
  return rel === '' || (rel !== '..' && !rel.startsWith(`..${path.sep}`) && !path.isAbsolute(rel))
}

function safeTreeFiles(directory) {
  const absoluteRoot = path.resolve(directory)
  if (!fs.existsSync(absoluteRoot)) fail('diretorio de artifact ausente')
  const rootStat = fs.lstatSync(absoluteRoot)
  if (!rootStat.isDirectory() || rootStat.isSymbolicLink()) {
    fail('diretorio de artifact inseguro')
  }
  const realRoot = fs.realpathSync(absoluteRoot)
  const files = []
  const visit = (current) => {
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const full = path.join(current, entry.name)
      const rel = path.relative(absoluteRoot, full).replaceAll('\\', '/')
      const stat = fs.lstatSync(full)
      if (stat.isSymbolicLink()) fail(`symlink proibido no artifact: ${rel}`)
      const real = fs.realpathSync(full)
      if (!isInside(realRoot, real)) fail(`caminho fora do artifact: ${rel}`)
      if (stat.isDirectory()) {
        visit(full)
      } else if (stat.isFile()) {
        files.push(rel)
      } else {
        fail(`entrada nao regular no artifact: ${rel}`)
      }
    }
  }
  visit(absoluteRoot)
  return files.sort()
}

function regularArtifactFile(bundle, name, label, allowEmpty = false) {
  if (typeof name !== 'string' || name.length === 0 || name.includes('\0')
      || path.isAbsolute(name) || path.basename(name) !== name) {
    fail(`${label} possui path traversal ou nome invalido`)
  }
  const absoluteBundle = path.resolve(bundle)
  const candidate = path.resolve(absoluteBundle, name)
  if (!isInside(absoluteBundle, candidate) || !fs.existsSync(candidate)) {
    fail(`${label} ausente ou fora da raiz`)
  }
  const stat = fs.lstatSync(candidate)
  if (!stat.isFile() || stat.isSymbolicLink()) fail(`${label} nao e arquivo regular`)
  const realBundle = fs.realpathSync(absoluteBundle)
  const realCandidate = fs.realpathSync(candidate)
  if (!isInside(realBundle, realCandidate)) fail(`${label} saiu da raiz por realpath`)
  try {
    fs.accessSync(candidate, fs.constants.R_OK)
  } catch {
    fail(`${label} nao pode ser lido`)
  }
  if (!allowEmpty && stat.size === 0) fail(`${label} vazio`)
  return candidate
}

function sameStringSet(actual, expected) {
  return Array.isArray(actual)
    && JSON.stringify([...actual].sort()) === JSON.stringify([...expected].sort())
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
    path.join(root, '.github/workflows/pipeline-v2-artifact-roundtrip.yml'),
    path.join(root, '.github/workflows/pipeline-production-v2.yml'),
    path.join(root, 'deploy/v2/images.lock.json'),
    path.join(root, 'deploy/v2/compose.yml'),
    ...walk(path.join(root, 'scripts/deploy/v2'), (file) => /\.(?:sh|mjs)$/.test(file)),
  ].sort()
}

function migrationFiles() {
  return walk(path.join(root, 'backend/src/main/resources/db/migration'),
    (file) => /^V\d+__.+\.sql$/.test(path.basename(file))).sort()
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
  const roundtripWorkflow = fs.readFileSync(
    path.join(root, '.github/workflows/pipeline-v2-artifact-roundtrip.yml'), 'utf8')
  const compose = fs.readFileSync(path.join(root, 'deploy/v2/compose.yml'), 'utf8')
  const lab = fs.readFileSync(path.join(root, 'scripts/deploy/v2/lab.sh'), 'utf8')
  const controller = fs.readFileSync(path.join(root, 'scripts/deploy/v2/controller.sh'), 'utf8')
  const artifact = fs.readFileSync(path.join(root, 'scripts/deploy/v2/artifact.sh'), 'utf8')
  const contractSource = fs.readFileSync(fileURLToPath(import.meta.url), 'utf8')
  const modeGuardJob = workflowJob(workflow, 'mode-guard')
  const buildOnceJob = workflowJob(workflow, 'build-once')
  const verifyJob = workflowJob(workflow, 'verify-clean-runner')
  const diagnosticJob = workflowJob(workflow, 'diagnose-gateway-listagem')
  const candidateJob = workflowJob(workflow, 'candidate-only')
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
    /TOPSDOJOB_PROD_/,
    /TOPSDOJOB_PREPROD_/,
    /secrets\.(?!TOPSDOJOB_CANDIDATE_)/,
    /C:\\topsv3/i,
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
  if (!compose.includes('INDEXNOW_KEY: ""')
      || !compose.includes('EFI_WEBHOOK_REGISTRATION_ENABLED: "false"')
      || !compose.includes('OUTBOX_EMAIL_ENABLED: "false"')
      || !compose.includes('SPRING_TASK_SCHEDULING_ENABLED: "false"')) {
    fail('efeitos externos da candidata nao estao neutralizados')
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
  for (const fragment of [
    'INSERT INTO estado (id,uf,nome,nome_normalizado,criado_em)',
    'INSERT INTO cidade (id,estado_id,nome,nome_normalizado,slug,criado_em)',
    'INSERT INTO anuncio_localizacao (anuncio_id,estado_id,cidade_id,bairro_id,endereco_resumido,criado_em,atualizado_em)',
    'INSERT INTO documento_busca_anuncio (anuncio_id,texto_busca,estado_id,cidade_id,bairro_id,categoria,preco,status_publicacao,tem_midia_valida,beneficios_ranking_json,ranking_base,atualizado_em)',
  ]) {
    if (!contractSource.includes(fragment)) fail(`fixture canonica incompleta: ${fragment}`)
  }
  const fixtureGate = lab.indexOf(
    'v2_fixture_integrity_gate | tee "${V2_RUNTIME_DIR}/reports/fixture-integrity-final.txt"')
  const firstCandidateStartup = lab.indexOf('v2_compose up -d --no-deps --no-build backend')
  if (fixtureGate < 0 || firstCandidateStartup < 0 || fixtureGate > firstCandidateStartup) {
    fail('gate de integridade da fixture deve preceder o startup da candidata')
  }
  for (const metric of [
    'PUBLICADOS_SEM_LOCALIZACAO',
    'LOCALIZACOES_INVALIDAS',
    'PUBLICADOS_SEM_DOCUMENTO_BUSCA',
    'TEXTOS_BUSCA_NULOS',
    'ANUNCIOS_PUBLICOS_VALIDOS',
  ]) {
    if (!lab.includes(metric)) fail(`metrica de integridade ausente: ${metric}`)
  }
  if (!lab.includes('FIXTURE_INTEGRITY_NEGATIVE_TESTS=5/5')) {
    fail('testes negativos da fixture devem cobrir os cinco invariantes')
  }
  const diagnoseMode = controller.match(/v2_diagnose_mode\(\) \{[\s\S]*?\n\}/)?.[0] ?? ''
  if (!diagnoseMode.includes('v2_run_backend_tests "${evidence_dir}"')
      || !lab.includes("v2_log 'CANDIDATE_GATES=14/14 HTTP_500_502_504=0'")) {
    fail('diagnostico deve provar CRITICAL_SKIPPED=0 e os 14 gates')
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
  if (!/options:\s*\n\s*- verify\s*\n\s*- candidate/.test(workflow)) {
    fail('workflow deve expor somente verify e candidate')
  }
  if (!/pull_request:\s*\n\s*branches:\s*\n\s*- main/.test(workflow)) {
    fail('workflow novo deve ser verificavel no PR antes de existir na main')
  }
  if (!/github\.event_name == 'pull_request' && 'verify' \|\| inputs\.mode/.test(workflow)) {
    fail('evento de PR deve permanecer estritamente em mode=verify')
  }
  if (!modeGuardJob.includes("github.event.pull_request.head.sha || github.sha")) {
    fail('SHA de PR deve vir explicitamente da cabeca do PR')
  }
  if (/\n\s*- (?:deploy|switch|rollback)\s*$/m.test(workflow)) {
    fail('modo de switch ou deploy continua proibido')
  }
  for (const action of Object.values(lock.actions)) {
    if (!workflow.includes(`uses: ${action}`)) fail(`action pinada ausente: ${action}`)
    if (!/@[a-f0-9]{40}$/.test(action)) fail(`action sem commit completo: ${action}`)
  }
  const allowedActions = new Set(Object.values(lock.actions))
  const usedActions = [...`${workflow}\n${roundtripWorkflow}`.matchAll(/^\s*uses:\s*(\S+)\s*$/gm)]
    .map((match) => match[1])
  if (usedActions.length === 0 || usedActions.some((action) => !allowedActions.has(action))) {
    fail(`workflow usa action nao pinada: ${JSON.stringify(usedActions)}`)
  }
  for (const required of [
    'artifact-contract-upload:',
    'artifact-contract-download:',
    'controller.sh artifact-fixture',
    'controller.sh artifact-roundtrip',
    'artifact-ids: ${{ needs.artifact-contract-upload.outputs.artifact-id }}',
    'digest-mismatch: error',
    'compression-level: 0',
    'overwrite: false',
    'include-hidden-files: false',
    'archive: true',
  ]) {
    if (!roundtripWorkflow.includes(required)) {
      fail(`round-trip real do artifact incompleto: ${required}`)
    }
  }
  if (/controller\.sh (?:build|verify|diagnose|candidate)|environment:|secrets\.|ssh|scp|backend\/|frontend\//.test(roundtripWorkflow)) {
    fail('round-trip do artifact acessa build, candidata ou recurso externo')
  }
  if (!roundtripWorkflow.includes(lock.actions.uploadArtifact)
      || !roundtripWorkflow.includes(lock.actions.downloadArtifact)
      || !roundtripWorkflow.includes(lock.actions.checkout)) {
    fail('round-trip nao reutiliza as actions pinadas do pipeline')
  }
  if (!/matrix:\s*\n\s*run:\s*\[1, 2, 3\]/.test(workflow)) {
    fail('workflow deve usar tres runners independentes')
  }
  const diagnosticLabel = "contains(github.event.pull_request.labels.*.name, 'pipeline-v2-diagnostic')"
  if (modeGuardJob.includes(diagnosticLabel)
      || !buildOnceJob.includes("needs.mode-guard.outputs.mode == 'verify'")
      || !verifyJob.includes(`!${diagnosticLabel}`)
      || !diagnosticJob.includes(diagnosticLabel)
      || !diagnosticJob.includes('needs: [mode-guard, build-once]')) {
    fail('build-once deve permanecer exclusivo de verify e alimentar o diagnostico')
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
  for (const required of [
    'deploy_sha:',
    'certification_run_id:',
    'confirmation:',
    'test "${CONFIRMATION}" = "CANDIDATE_ONLY"',
    "needs.mode-guard.outputs.mode == 'candidate'",
    'environment: candidate',
    'run-id: ${{ needs.mode-guard.outputs.certification-run-id }}',
    'github-token: ${{ github.token }}',
    'controller.sh candidate-package',
    'controller.sh candidate-remote',
    'TARGET_VERIFIED=production',
  ]) {
    if (!workflow.includes(required)) fail(`contrato candidate ausente: ${required}`)
  }
  const candidateSecrets = [...candidateJob.matchAll(/secrets\.(TOPSDOJOB_CANDIDATE_[A-Z0-9_]+)/g)]
    .map((match) => match[1])
  const expectedCandidateSecrets = new Set([
    'TOPSDOJOB_CANDIDATE_SSH_HOST',
    'TOPSDOJOB_CANDIDATE_SSH_PORT',
    'TOPSDOJOB_CANDIDATE_SSH_USER',
    'TOPSDOJOB_CANDIDATE_SSH_PRIVATE_KEY',
    'TOPSDOJOB_CANDIDATE_SSH_HOST_KEY',
    'TOPSDOJOB_CANDIDATE_TARGET_SHA256',
  ])
  if (new Set(candidateSecrets).size !== expectedCandidateSecrets.size
      || candidateSecrets.some((name) => !expectedCandidateSecrets.has(name))) {
    fail(`secrets candidate divergentes: ${JSON.stringify([...new Set(candidateSecrets)].sort())}`)
  }
  const targetGuard = candidateJob.indexOf('TARGET_VERIFIED=production')
  const firstUpload = candidateJob.indexOf('scp -4')
  if (targetGuard < 0 || firstUpload < 0 || targetGuard > firstUpload) {
    fail('target guard deve preceder qualquer upload candidate')
  }
  if (!candidateJob.includes('StrictHostKeyChecking=yes')
      || !candidateJob.includes('ssh-keyscan -4')
      || !candidateJob.includes('bash runtime/scripts/deploy/v2/controller.sh candidate-remote')
      || !candidateJob.includes('</dev/null')) {
    fail('host key, IPv4 ou stdin fechado ausente no candidate')
  }
  if (/nginx\s+-[st]|\/etc\/nginx|proxy_pass|upstream/.test(candidateJob)) {
    fail('job candidate nao pode alterar ou validar Nginx do host')
  }
  const candidateRemote = controller.match(/v2_candidate_remote_mode\(\) \{[\s\S]*?\n\}/)?.[0] ?? ''
  const candidatePackage = controller.match(/v2_candidate_package_mode\(\) \{[\s\S]*?\n\}/)?.[0] ?? ''
  if (!candidateRemote.includes('v2_create_readonly_production_backup')
      || !candidateRemote.includes('v2_prepare_candidate_from_backup')
      || !candidateRemote.includes('v2_candidate_backfill_isolated')
      || !candidateRemote.includes('v2_start_candidate_and_gates')
      || !candidatePackage.includes('v2_create_candidate_payload')) {
    fail('cadeia isolada da candidata esta incompleta')
  }
  if (/v2_build_release_artifact|v2_compose build|nginx\s+-[st]|switch|reload/.test(candidateRemote)
      || /v2_build_release_artifact|v2_compose build/.test(candidatePackage)) {
    fail('candidate contem rebuild, switch ou alteracao Nginx')
  }
  if (!artifact.includes('manifest-runtime-verify')
      || !artifact.includes('candidate-metadata-verify')
      || !artifact.includes('v2_resolve_artifact_root')
      || !artifact.includes('artifact-root-resolve')
      || !artifact.includes('ARTIFACT_ROOT_RESOLVED=YES')
      || !contractSource.includes('artifactLayoutVersion: 1')
      || !artifact.includes('CANDIDATE_ARTIFACT=VERIFIED')
      || !lab.includes('v2_compose run --rm --no-deps --pull never -T backend')
      || !lab.includes('REAL_R2_MUTATIONS=0')
      || !lab.includes('CANDIDATE_SHUTDOWN=GRACEFUL RESIDUALS=0')) {
    fail('proveniencia, isolamento externo ou cleanup candidate incompleto')
  }
  if (/find[\s\S]{0,160}release-manifest\.json[\s\S]{0,80}-(?:min|max)depth/.test(candidateJob)
      || /-(?:min|max)depth[\s\S]{0,160}release-manifest\.json/.test(candidateJob)
      || !candidateJob.includes('artifact-ids: ${{ steps.certified-artifact.outputs.artifact_id }}')
      || !candidateJob.includes('digest-mismatch: error')
      || !candidateJob.includes('actions/github-script@ed597411d8f924073f98dfc5c65a23a2325f34cd')) {
    fail('candidate ainda depende de profundidade ou identidade nao certificada')
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
  const [
    output,
    gitSha,
    archive,
    dependencyArchive,
    toolVersions,
    certificationRunId,
    certificationRunAttempt,
    certificationArtifactName,
    ...imageArgs
  ] = args
  if (!/^[a-f0-9]{40}$/.test(gitSha ?? '')) fail('git SHA invalido para manifesto')
  if (!/^[1-9][0-9]*$/.test(certificationRunId ?? '')) fail('run de certificacao invalido')
  if (!/^[1-9][0-9]*$/.test(certificationRunAttempt ?? '')) fail('attempt de certificacao invalido')
  const expectedArtifactName =
    `topsdojob-v2-release-${gitSha}-${certificationRunId}-${certificationRunAttempt}`
  if (certificationArtifactName !== expectedArtifactName) fail('nome do artefato de certificacao invalido')
  if (!fs.statSync(archive).isFile() || fs.statSync(archive).size === 0) fail('TAR vazio')
  if (!fs.statSync(dependencyArchive).isFile() || fs.statSync(dependencyArchive).size === 0) {
    fail('TAR de dependencias vazio')
  }
  if (!fs.statSync(toolVersions).isFile() || fs.statSync(toolVersions).size === 0) {
    fail('metadados de ferramentas vazios')
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
  const migrations = Object.fromEntries(migrationFiles().map((file) => [relative(file), sha256(file)]))
  if (Object.keys(migrations).length !== 53) fail('manifesto exige exatamente V001-V053')
  const archiveName = path.basename(archive)
  const dependencyArchiveName = path.basename(dependencyArchive)
  const toolVersionsName = path.basename(toolVersions)
  const certifiedFiles = [
    'release-manifest.json',
    'release-manifest.sha256',
    archiveName,
    dependencyArchiveName,
    toolVersionsName,
  ].sort()
  const candidateRuntimeFiles = certifiedFiles
    .filter((name) => name !== dependencyArchiveName)
  const manifest = {
    schemaVersion: 2,
    artifactLayoutVersion: 1,
    gitSha,
    certification: {
      runId: certificationRunId,
      runAttempt: certificationRunAttempt,
      artifactName: certificationArtifactName,
    },
    platform: lock.platform,
    archive: {
      name: archiveName,
      sha256: sha256(archive),
      bytes: fs.statSync(archive).size,
    },
    testDependencies: {
      name: dependencyArchiveName,
      sha256: sha256(dependencyArchive),
      bytes: fs.statSync(dependencyArchive).size,
      offlineValidated: true,
    },
    artifactLayout: {
      manifest: 'release-manifest.json',
      checksum: 'release-manifest.sha256',
      payloads: {
        releaseImages: archiveName,
        testDependencies: dependencyArchiveName,
      },
      metadata: {
        toolVersions: {
          name: toolVersionsName,
          sha256: sha256(toolVersions),
          bytes: fs.statSync(toolVersions).size,
        },
      },
      profiles: {
        certified: certifiedFiles,
        candidateRuntime: candidateRuntimeFiles,
      },
      unexpectedFilesAllowed: false,
    },
    images,
    tools: lock.tools,
    lockedImagesSha256: sha256(lockPath),
    sourceFiles: files,
    migrations,
    rebuildAllowedInVerify: false,
    rebuildAllowedOnCandidate: false,
  }
  fs.writeFileSync(output, `${JSON.stringify(manifest, null, 2)}\n`, { mode: 0o600 })
}

function verifyArtifactLayout(bundle, manifest, requireTestDependencies) {
  const layout = manifest.artifactLayout
  if (manifest.artifactLayoutVersion !== 1 || !layout || typeof layout !== 'object'
      || layout.manifest !== 'release-manifest.json'
      || layout.checksum !== 'release-manifest.sha256'
      || layout.unexpectedFilesAllowed !== false) {
    fail('contrato de layout do artifact invalido')
  }
  if (layout.payloads?.releaseImages !== manifest.archive?.name
      || layout.payloads?.testDependencies !== manifest.testDependencies?.name) {
    fail('payloads do layout divergem do manifesto')
  }
  const toolMetadata = layout.metadata?.toolVersions
  if (!toolMetadata || typeof toolMetadata !== 'object') {
    fail('metadados de ferramentas ausentes no layout')
  }
  const certifiedFiles = [
    layout.manifest,
    layout.checksum,
    manifest.archive.name,
    manifest.testDependencies.name,
    toolMetadata.name,
  ].sort()
  const runtimeFiles = certifiedFiles.filter((name) => name !== manifest.testDependencies.name)
  if (!sameStringSet(layout.profiles?.certified, certifiedFiles)
      || !sameStringSet(layout.profiles?.candidateRuntime, runtimeFiles)) {
    fail('perfis nominais do layout invalidos')
  }
  const expectedFiles = requireTestDependencies ? certifiedFiles : runtimeFiles
  const actualFiles = safeTreeFiles(bundle)
  if (!sameStringSet(actualFiles, expectedFiles)) {
    fail('arquivos presentes divergem do perfil nominal do artifact')
  }

  const manifestPath = regularArtifactFile(bundle, layout.manifest, 'manifesto')
  const checksumPath = regularArtifactFile(bundle, layout.checksum, 'checksums')
  const expectedChecksum = `${sha256(manifestPath)}  ${layout.manifest}`
  if (fs.readFileSync(checksumPath, 'utf8').trimEnd() !== expectedChecksum) {
    fail('checksum do manifesto divergente')
  }
  const toolVersions = regularArtifactFile(bundle, toolMetadata.name, 'metadados de ferramentas')
  if (!/^[a-f0-9]{64}$/.test(toolMetadata.sha256 ?? '')
      || sha256(toolVersions) !== toolMetadata.sha256
      || fs.statSync(toolVersions).size !== toolMetadata.bytes) {
    fail('metadados de ferramentas divergentes')
  }
}

function verifyManifest(bundle, expectedSha, expectedRunId, requireTestDependencies, emit = true) {
  const manifestPath = regularArtifactFile(bundle, 'release-manifest.json', 'manifesto')
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
  if (manifest.schemaVersion !== 2
      || manifest.artifactLayoutVersion !== 1
      || manifest.rebuildAllowedInVerify !== false
      || manifest.rebuildAllowedOnCandidate !== false) {
    fail('schema, layout ou politica de rebuild invalida no manifesto')
  }
  if (expectedSha && manifest.gitSha !== expectedSha) fail('SHA do manifesto divergente')
  if (!/^[a-f0-9]{40}$/.test(manifest.gitSha)) fail('SHA do manifesto invalido')
  if (!/^[1-9][0-9]*$/.test(manifest.certification?.runId ?? '')
      || !/^[1-9][0-9]*$/.test(manifest.certification?.runAttempt ?? '')) {
    fail('proveniencia de certificacao invalida')
  }
  if (expectedRunId && manifest.certification.runId !== expectedRunId) {
    fail('run de certificacao divergente')
  }
  const expectedArtifactName =
    `topsdojob-v2-release-${manifest.gitSha}-${manifest.certification.runId}-${manifest.certification.runAttempt}`
  if (manifest.certification.artifactName !== expectedArtifactName) {
    fail('identidade do artefato divergente')
  }

  verifyArtifactLayout(bundle, manifest, requireTestDependencies)
  const archive = regularArtifactFile(bundle, manifest.archive?.name, 'TAR de imagens')
  if (!/^[a-f0-9]{64}$/.test(manifest.archive?.sha256 ?? '')
      || sha256(archive) !== manifest.archive.sha256) {
    fail('hash do TAR divergente')
  }
  if (fs.statSync(archive).size !== manifest.archive.bytes) fail('tamanho do TAR divergente')
  if (requireTestDependencies) {
    const dependencyArchive = regularArtifactFile(
      bundle, manifest.testDependencies?.name, 'TAR de dependencias')
    if (manifest.testDependencies?.offlineValidated !== true) {
      fail('TAR de dependencias nao foi validado offline')
    }
    if (!/^[a-f0-9]{64}$/.test(manifest.testDependencies.sha256 ?? '')
        || sha256(dependencyArchive) !== manifest.testDependencies.sha256) {
      fail('hash do TAR de dependencias divergente')
    }
    if (fs.statSync(dependencyArchive).size !== manifest.testDependencies.bytes) {
      fail('tamanho do TAR de dependencias divergente')
    }
  }
  if (sha256(lockPath) !== manifest.lockedImagesSha256) fail('lock diverge do build')
  if (JSON.stringify(manifest.tools) !== JSON.stringify(lock.tools)) {
    fail('ferramentas do manifesto divergem do lock')
  }
  if (!Array.isArray(manifest.images) || manifest.images.length !== 3
      || manifest.images.some((image) => typeof image?.name !== 'string'
        || !/^sha256:[a-f0-9]{64}$/.test(image?.id ?? ''))) {
    fail('imagens de release invalidas')
  }
  const expectedSources = Object.fromEntries(
    technicalFiles().map((file) => [relative(file), sha256(file)]))
  if (JSON.stringify(manifest.sourceFiles) !== JSON.stringify(expectedSources)) {
    fail('fontes tecnicas divergem do build certificado')
  }
  const expectedMigrations = Object.fromEntries(
    migrationFiles().map((file) => [relative(file), sha256(file)]))
  if (JSON.stringify(manifest.migrations) !== JSON.stringify(expectedMigrations)) {
    fail('migrations divergem do build certificado')
  }
  if (emit) console.log(`MANIFEST_V2=OK sha=${manifest.gitSha} archive=${manifest.archive.sha256}`)
  return manifest
}

function manifestVerify(args) {
  const [bundle, expectedSha, expectedRunId] = args
  verifyManifest(bundle, expectedSha, expectedRunId, true)
}

function manifestRuntimeVerify(args) {
  const [bundle, expectedSha, expectedRunId] = args
  verifyManifest(bundle, expectedSha, expectedRunId, false)
}

function resolveArtifactRoot(extractionDirectory, expectedSha, expectedRunId) {
  const extractionRoot = path.resolve(extractionDirectory)
  if (!fs.existsSync(extractionRoot)) fail('diretorio de extracao ausente')
  const extractionStat = fs.lstatSync(extractionRoot)
  if (!extractionStat.isDirectory() || extractionStat.isSymbolicLink()) {
    fail('diretorio de extracao inseguro')
  }
  const extractionReal = fs.realpathSync(extractionRoot)
  const files = safeTreeFiles(extractionRoot)
  const manifests = files.filter((file) => path.basename(file) === 'release-manifest.json')
  if (manifests.length === 0) fail('nenhum manifesto encontrado')
  if (manifests.length !== 1) fail('multiplos manifestos encontrados')
  const manifest = path.resolve(extractionRoot, manifests[0])
  if (!isInside(extractionRoot, manifest)) fail('manifesto fora da extracao')
  const manifestStat = fs.lstatSync(manifest)
  if (!manifestStat.isFile() || manifestStat.isSymbolicLink()) {
    fail('manifesto resolvido nao e arquivo regular')
  }
  try {
    fs.accessSync(manifest, fs.constants.R_OK)
  } catch {
    fail('manifesto resolvido nao pode ser lido')
  }
  if (manifestStat.size === 0) fail('manifesto vazio')
  const artifactRoot = fs.realpathSync(path.dirname(manifest))
  if (!isInside(extractionReal, artifactRoot)) fail('raiz do artifact fora da extracao')
  for (const file of files) {
    const real = fs.realpathSync(path.join(extractionRoot, file))
    if (!isInside(artifactRoot, real)) fail('arquivo encontrado fora da raiz resolvida')
  }
  verifyManifest(artifactRoot, expectedSha, expectedRunId, true, false)
  return artifactRoot
}

function artifactRootResolve(args) {
  const [extractionDirectory, expectedSha, expectedRunId] = args
  if (!extractionDirectory || args.length > 3) fail('uso invalido do resolver de artifact')
  process.stdout.write(`${resolveArtifactRoot(extractionDirectory, expectedSha, expectedRunId)}\n`)
}

function artifactFixtureCreate(args) {
  const [output, gitSha, certificationRunId, certificationRunAttempt, artifactName] = args
  if (!output || args.length !== 5) fail('uso invalido da fixture de artifact')
  if (fs.existsSync(output)) fail('diretorio da fixture ja existe')
  fs.mkdirSync(output, { recursive: true, mode: 0o700 })
  const archive = path.join(output, 'release-images.tar')
  const dependencies = path.join(output, 'test-dependencies.tar')
  const versions = path.join(output, 'tool-versions.txt')
  fs.writeFileSync(archive, 'fixture-release-images\n', { mode: 0o600 })
  fs.writeFileSync(dependencies, 'fixture-test-dependencies\n', { mode: 0o600 })
  fs.writeFileSync(versions, 'fixture-tools=contract-only\n', { mode: 0o600 })
  const fakeIds = ['1', '2', '3'].map((digit) => `sha256:${digit.repeat(64)}`)
  manifestCreate([
    path.join(output, 'release-manifest.json'),
    gitSha,
    archive,
    dependencies,
    versions,
    certificationRunId,
    certificationRunAttempt,
    artifactName,
    `topsdojob-v2-backend:${gitSha}`, fakeIds[0],
    `topsdojob-v2-frontend:${gitSha}`, fakeIds[1],
    `topsdojob-v2-gateway:${gitSha}`, fakeIds[2],
  ])
  const manifestPath = path.join(output, 'release-manifest.json')
  fs.writeFileSync(path.join(output, 'release-manifest.sha256'),
    `${sha256(manifestPath)}  release-manifest.json\n`, { mode: 0o600 })
  verifyManifest(output, gitSha, certificationRunId, true, false)
  console.log('ARTIFACT_FIXTURE=OK')
}

function artifactFixtureMutate(args) {
  const [bundle, mutation] = args
  if (args.length !== 2 || mutation !== 'path-traversal') {
    fail('mutacao de fixture invalida')
  }
  const manifestPath = path.join(bundle, 'release-manifest.json')
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
  const previous = manifest.archive.name
  const traversal = '../release-images.tar'
  manifest.archive.name = traversal
  manifest.artifactLayout.payloads.releaseImages = traversal
  manifest.artifactLayout.profiles.certified = manifest.artifactLayout.profiles.certified
    .map((name) => name === previous ? traversal : name)
  manifest.artifactLayout.profiles.candidateRuntime = manifest.artifactLayout.profiles.candidateRuntime
    .map((name) => name === previous ? traversal : name)
  fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, { mode: 0o600 })
  fs.writeFileSync(path.join(bundle, 'release-manifest.sha256'),
    `${sha256(manifestPath)}  release-manifest.json\n`, { mode: 0o600 })
}

function artifactResolverTests(args) {
  const [testDirectory, sourceSha] = args
  if (args.length !== 2 || !/^[a-f0-9]{40}$/.test(sourceSha ?? '')
      || !path.basename(testDirectory ?? '').startsWith('topsdojob-v2-artifact-resolver-')) {
    fail('uso invalido dos testes do resolver')
  }
  const testRoot = path.resolve(testDirectory)
  if (fs.existsSync(testRoot)) fail('diretorio dos testes do resolver ja existe')
  fs.mkdirSync(testRoot, { recursive: true, mode: 0o700 })
  const runId = '123456789'
  const runAttempt = '1'
  const artifactName = `topsdojob-v2-release-${sourceSha}-${runId}-${runAttempt}`
  const fixture = path.join(testRoot, 'fixture')
  const copyFixture = (destination) => {
    fs.cpSync(fixture, destination, { recursive: true, errorOnExist: true })
    return destination
  }
  const expectPass = (name, extraction, expectedRoot) => {
    const resolved = resolveArtifactRoot(extraction, sourceSha, runId)
    if (resolved !== fs.realpathSync(expectedRoot)) fail(`caso deveria passar: ${name}`)
    console.log(`ARTIFACT_RESOLVER_TEST=${name}:PASS`)
  }
  const expectFail = (name, extraction) => {
    try {
      resolveArtifactRoot(extraction, sourceSha, runId)
    } catch {
      console.log(`ARTIFACT_RESOLVER_TEST=${name}:FAIL_EXPECTED`)
      return
    }
    fail(`caso deveria falhar: ${name}`)
  }

  try {
    artifactFixtureCreate([fixture, sourceSha, runId, runAttempt, artifactName])

    const depth0 = copyFixture(path.join(testRoot, 'depth-0'))
    expectPass('depth-0', depth0, depth0)

    const depth1Root = path.join(testRoot, 'depth-1')
    fs.mkdirSync(depth1Root)
    const depth1 = copyFixture(path.join(depth1Root, 'artifact'))
    expectPass('depth-1', depth1Root, depth1)

    const depth2Root = path.join(testRoot, 'depth-2')
    fs.mkdirSync(path.join(depth2Root, 'download'), { recursive: true })
    const depth2 = copyFixture(path.join(depth2Root, 'download', 'artifact'))
    expectPass('depth-2', depth2Root, depth2)

    const noManifest = copyFixture(path.join(testRoot, 'no-manifest'))
    fs.rmSync(path.join(noManifest, 'release-manifest.json'))
    expectFail('no-manifest', noManifest)

    const duplicateRoot = path.join(testRoot, 'duplicate-manifest')
    fs.mkdirSync(duplicateRoot)
    copyFixture(path.join(duplicateRoot, 'one'))
    copyFixture(path.join(duplicateRoot, 'two'))
    expectFail('duplicate-manifest', duplicateRoot)

    const manifestSymlink = copyFixture(path.join(testRoot, 'manifest-symlink'))
    fs.rmSync(path.join(manifestSymlink, 'release-manifest.json'))
    fs.symlinkSync(path.join(fixture, 'release-manifest.json'),
      path.join(manifestSymlink, 'release-manifest.json'))
    expectFail('manifest-symlink', manifestSymlink)

    const payloadSymlink = copyFixture(path.join(testRoot, 'payload-symlink'))
    fs.rmSync(path.join(payloadSymlink, 'release-images.tar'))
    fs.symlinkSync(path.join(fixture, 'release-images.tar'),
      path.join(payloadSymlink, 'release-images.tar'))
    expectFail('payload-symlink', payloadSymlink)

    const traversal = copyFixture(path.join(testRoot, 'path-traversal'))
    artifactFixtureMutate([traversal, 'path-traversal'])
    expectFail('path-traversal', traversal)

    const hashMismatch = copyFixture(path.join(testRoot, 'hash-mismatch'))
    fs.appendFileSync(path.join(hashMismatch, 'release-images.tar'), 'tampered\n')
    expectFail('hash-mismatch', hashMismatch)

    const payloadMissing = copyFixture(path.join(testRoot, 'payload-missing'))
    fs.rmSync(path.join(payloadMissing, 'release-images.tar'))
    expectFail('payload-missing', payloadMissing)

    const unexpected = copyFixture(path.join(testRoot, 'unexpected-extra'))
    fs.writeFileSync(path.join(unexpected, 'unexpected.txt'), 'unexpected\n')
    expectFail('unexpected-extra', unexpected)

    const realTree = copyFixture(path.join(testRoot, 'real-valid-tree'))
    expectPass('real-valid-tree', realTree, realTree)
    console.log('ARTIFACT_RESOLVER_TESTS=12/12')
  } finally {
    fs.rmSync(testRoot, { recursive: true, force: true })
  }
  if (fs.existsSync(testRoot)) fail('residuos dos testes do resolver')
  console.log('ARTIFACT_RESOLVER_RESIDUES=0')
}

function candidateMetadataVerify(args) {
  const [
    bundle,
    expectedSha,
    expectedRunId,
    expectedArtifactName,
    expectedArtifactId,
    expectedArtifactDigest,
    runFile,
    artifactsFile,
    output,
  ] = args
  const manifest = verifyManifest(bundle, expectedSha, expectedRunId, true)
  const run = JSON.parse(fs.readFileSync(runFile, 'utf8'))
  const artifacts = JSON.parse(fs.readFileSync(artifactsFile, 'utf8')).artifacts
  if (String(run.id) !== expectedRunId
      || run.head_sha !== expectedSha
      || run.conclusion !== 'success'
      || run.event !== 'workflow_dispatch'
      || run.path !== '.github/workflows/pipeline-production-v2.yml'
      || String(run.run_attempt) !== manifest.certification.runAttempt) {
    fail('run informado nao e uma certificacao V2 aprovada para o SHA')
  }
  const artifactName = manifest.certification.artifactName
  if (expectedArtifactName !== artifactName) {
    fail('nome baixado nao corresponde ao artefato certificado')
  }
  if (!Array.isArray(artifacts)) fail('lista de artefatos invalida')
  const matches = artifacts.filter((artifact) => artifact.name === artifactName)
  if (matches.length !== 1
      || !Number.isInteger(matches[0].id)
      || matches[0].id < 1
      || matches[0].expired === true
      || String(matches[0].id) !== expectedArtifactId
      || matches[0].digest !== expectedArtifactDigest
      || !/^sha256:[a-f0-9]{64}$/.test(matches[0].digest ?? '')) {
    fail('artefato certificado ausente, ambiguo, expirado ou divergente')
  }
  fs.writeFileSync(output, [
    `DEPLOY_SHA=${expectedSha}`,
    `CERTIFICATION_RUN_ID=${expectedRunId}`,
    `CERTIFICATION_RUN_ATTEMPT=${manifest.certification.runAttempt}`,
    `CERTIFIED_ARTIFACT_ID=${matches[0].id}`,
    `CERTIFIED_ARTIFACT_NAME=${artifactName}`,
    `CERTIFIED_ARTIFACT_DIGEST=${matches[0].digest}`,
    `CERTIFIED_MANIFEST_SHA256=${sha256(path.join(bundle, 'release-manifest.json'))}`,
    'REBUILD_ALLOWED=false',
    '',
  ].join('\n'), { encoding: 'ascii', mode: 0o600 })
}

function manifestImages(args) {
  const manifest = JSON.parse(fs.readFileSync(path.join(args[0], 'release-manifest.json'), 'utf8'))
  for (const image of manifest.images) console.log(`${image.name}|${image.id}`)
}

function manifestArtifactName(args) {
  const [bundle, expectedSha, expectedRunId] = args
  const manifest = verifyManifest(bundle, expectedSha, expectedRunId, true, false)
  console.log(manifest.certification.artifactName)
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
  const estadoId = '50000000-0000-4000-8000-000000000001'
  const cidadeId = '51000000-0000-4000-8000-000000000001'
  const bairroId = '52000000-0000-4000-8000-000000000001'
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
    `INSERT INTO estado (id,uf,nome,nome_normalizado,criado_em) VALUES ('${estadoId}','SP','Sao Paulo','sao paulo',now());`,
    `INSERT INTO cidade (id,estado_id,nome,nome_normalizado,slug,criado_em) VALUES ('${cidadeId}','${estadoId}','Sao Paulo','sao paulo','sao-paulo',now());`,
    `INSERT INTO bairro (id,cidade_id,nome,nome_normalizado,slug,criado_em) VALUES ('${bairroId}','${cidadeId}','Centro','centro','centro',now());`,
    "INSERT INTO usuario (id,nome,status,tipo_conta,criado_em,atualizado_em) VALUES ('10000000-0000-4000-8000-000000000001','Pipeline V2','ATIVO','ANUNCIANTE',now(),now());",
    `INSERT INTO anuncio (id,usuario_id,slug,titulo,descricao,status,status_moderacao,categoria,preco,publicado_em,ultima_publicacao_em,criado_em,atualizado_em) VALUES ('${anuncioId}','10000000-0000-4000-8000-000000000001','pipeline-v2-fixture','Pipeline V2 Fixture','Somente laboratorio','PUBLICADO','APROVADO','ACOMPANHANTE',100,now(),now(),now(),now());`,
    `INSERT INTO anuncio_localizacao (anuncio_id,estado_id,cidade_id,bairro_id,endereco_resumido,criado_em,atualizado_em) VALUES ('${anuncioId}','${estadoId}','${cidadeId}','${bairroId}','Centro',now(),now());`,
    `INSERT INTO documento_busca_anuncio (anuncio_id,texto_busca,estado_id,cidade_id,bairro_id,categoria,preco,status_publicacao,tem_midia_valida,beneficios_ranking_json,ranking_base,atualizado_em) VALUES ('${anuncioId}','pipeline v2 fixture sao paulo centro acompanhante','${estadoId}','${cidadeId}','${bairroId}','ACOMPANHANTE',100,'PUBLICAVEL',true,'{}'::jsonb,0,now());`,
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
    canonicalPublicAd: {
      anuncioId,
      estadoId,
      cidadeId,
      bairroId,
    },
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
try {
  switch (command) {
    case 'contract': contract(); break
    case 'manifest-create': manifestCreate(args); break
    case 'manifest-verify': manifestVerify(args); break
    case 'manifest-runtime-verify': manifestRuntimeVerify(args); break
    case 'artifact-root-resolve': artifactRootResolve(args); break
    case 'artifact-fixture-create': artifactFixtureCreate(args); break
    case 'artifact-fixture-mutate': artifactFixtureMutate(args); break
    case 'artifact-resolver-tests': artifactResolverTests(args); break
    case 'candidate-metadata-verify': candidateMetadataVerify(args); break
    case 'manifest-images': manifestImages(args); break
    case 'manifest-artifact-name': manifestArtifactName(args); break
    case 'critical-list': criticalList(args); break
    case 'critical-report': criticalReport(args); break
    case 'fixture': fixture(args); break
    case 'runtime-fixtures': runtimeFixtures(args); break
    case 'file-policy': assertRegularLfFile(path.resolve(args[0])); console.log('FILE_POLICY=OK'); break
    default: fail(`comando desconhecido: ${command}`)
  }
} catch (error) {
  const message = String(error?.message ?? 'falha desconhecida')
    .replaceAll(root, '<repo>')
    .replace(/[\r\n]+/g, ' ')
  console.error(`CONTRACT_V2_ERROR ${message}`)
  process.exitCode = 1
}
