import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const repositoryRoot = path.resolve(frontendRoot, '..')

function frontend(file) {
  return readFileSync(path.join(frontendRoot, file), 'utf8')
}

function repository(file) {
  return readFileSync(path.join(repositoryRoot, file), 'utf8')
}

const wizard = frontend('src/features/anuncio-wizard/anuncio-wizard.tsx')
const constants = frontend('src/features/anuncio-wizard/wizard-constants.ts')
const store = frontend('src/features/anuncio-wizard/use-anuncio-wizard-store.ts')
const profile = frontend('src/features/anuncio-wizard/components/wizard-step-perfil.tsx')
const location = frontend('src/features/anuncio-wizard/components/wizard-step-localizacao.tsx')
const photos = frontend('src/features/anuncio-wizard/components/wizard-step-fotos.tsx')
const kyc = frontend('src/features/anuncio-wizard/components/wizard-step-kyc.tsx')
const premium = frontend('src/features/anuncio-wizard/components/wizard-step-premium.tsx')
const review = frontend('src/features/anuncio-wizard/components/wizard-final-review.tsx')
const wizardApi = frontend('src/features/anuncio-wizard/api.ts')
const gallery = frontend('src/components/anuncios/galeria-fotos.tsx')
const video = frontend('src/components/anuncios/editar/video-uploader.tsx')
const filePicker = frontend('src/components/forms/file-picker.tsx')
const accountSecurity = frontend('src/features/minha-conta/minha-conta-seguranca.tsx')
const authApi = frontend('src/lib/public-auth-api.ts')
const anuncioCard = frontend('src/components/anuncios/anuncio-card.tsx')
const anuncioSidebar = frontend('src/app/(public-routes)/anuncios/[slug]/componentes/sidebar.tsx')
const anuncioMainContent = frontend('src/app/(public-routes)/anuncios/[slug]/componentes/main-content.tsx')
const anuncioPublicPage = frontend('src/app/(public-routes)/anuncios/[slug]/page.tsx')
const openapi = repository('contracts/openapi/topsdojob-v3-local.yaml')

assert.ok(constants.includes('Conte um pouco sobre você'))
assert.ok(constants.includes('Uma boa descrição ajuda clientes a conhecerem seu estilo'))
assert.ok(profile.includes('Fale sobre seu estilo, atendimento, diferenciais e a experiência que você oferece.'))
assert.ok(profile.includes('Essa descrição será exibida no seu perfil. Seja clara, autêntica'))
assert.ok(profile.includes('maxLength={500}'))
assert.ok(profile.includes('minLength={10}') && profile.includes('maxLength={80}'))
assert.ok(store.includes('tituloLength < 10 || tituloLength > 80'))
assert.ok(store.includes("mode === 'create' && (descricao.length < 20 || descricao.length > 500)"))
assert.ok(wizardApi.includes('Array.isArray(parsed.erros)'))
assert.ok(wizardApi.includes('mapWizardValidationField(field, normalizedCode)'))

assert.ok(store.includes('Região de atendimento'))
assert.ok(constants.includes('Onde você atende?'))
assert.ok(location.includes('Sua localização'))
assert.ok(location.includes('Complemento ou ponto de referência'))
assert.ok(location.includes('Ex.: próximo ao Flamboyant'))
assert.ok(location.includes('Opcional. Esta informação será exibida publicamente. Informe um estabelecimento,'))
assert.ok(location.includes('local próprio ou ponto de referência que ajude o visitante a localizar seu atendimento.'))
assert.ok(location.includes('maxLength={120}'))

const wizardUi = [wizard, profile, location, photos, premium, review].join('\n')
for (const removed of [
  'PRIMEIRA IMPRESSÃO',
  'Seu perfil começa a ganhar forma aqui',
  'Os arquivos ficam somente neste navegador até o anúncio ser enviado.',
  'O backend valida os limites e formatos.',
]) {
  assert.ok(!wizardUi.includes(removed), `Texto removido voltou: ${removed}`)
}

assert.ok(wizardApi.includes("publicApiUrl('/categorias-home')"))
assert.ok(wizardApi.includes("publicApiUrl('/anunciar')"))
assert.ok(wizardApi.includes('enderecoResumido: state.pontoReferenciaTexto.trim() || null'))
assert.ok(!wizardApi.includes('whatsapp: state.whatsapp'))
assert.ok(wizard.includes("router.push('/minha-conta')"))
assert.ok(wizard.includes('if (!hydrated || isEdit || categoryLoading || categoryError || !state.categoria) return'))
assert.ok(wizard.includes("updateForm({ categoria: '' })"))
assert.ok(wizard.includes("pontoReferenciaTexto: anuncio.localizacao?.enderecoResumido || ''"))
assert.ok(wizard.includes('enderecoResumido: state.pontoReferenciaTexto.trim() || null'))
assert.ok(!wizard.includes('showReference={!isEdit}'))

for (const publicLocation of [anuncioCard, anuncioSidebar, anuncioMainContent]) {
  assert.ok(publicLocation.includes('const ponto = clean('))
  assert.ok(!publicLocation.includes('Endereço sintético local'))
  assert.ok(!publicLocation.includes('Endereco sintetico local'))
  assert.ok(!publicLocation.includes('dangerouslySetInnerHTML'))
}
assert.ok(anuncioCard.includes('return ponto ? `${base} · ${ponto}` : base'))
assert.ok(anuncioSidebar.includes("return ponto ? `${parts.join(' - ')} | ${ponto}` : parts.join(' - ')"))
assert.ok(anuncioMainContent.includes("return ponto ? `${parts.join(' - ')} | ${ponto}` : parts.join(' - ')"))
assert.ok(!anuncioPublicPage.includes('enderecoResumido'))
assert.ok(!anuncioPublicPage.includes('pontoReferenciaTexto'))
assert.ok(!anuncioPublicPage.includes('streetAddress'))

assert.ok(photos.includes('initialFiles.length > 4'))
assert.ok(wizard.includes('state.fotos.length > 4'))
assert.ok(wizard.includes('if (changed || files.length > 4 || invalidIndex >= 0'))
assert.ok(photos.includes('fotosNovas > persisted.limites.fotosDisponiveis'))
assert.ok(photos.includes('Você pode adicionar até 4 fotos gratuitamente.'))
assert.ok(photos.includes('Seu anúncio permite até 10 fotos com o benefício de fotos extras.'))
assert.ok(gallery.includes('selecionadas.length > disponivel'))
assert.ok(gallery.includes('Você atingiu o limite de fotos deste anúncio.'))
assert.ok(video.includes('Adicione um vídeo ao seu anúncio com o benefício Vídeo.'))
assert.ok(video.includes('Você pode adicionar 1 vídeo em MP4 ou MOV.'))

assert.ok(kyc.includes("state.documentoModo === 'PDF'"))
assert.ok(kyc.includes("state.documentoModo === 'FRENTE_VERSO'"))
assert.ok(kyc.includes('Trocar o formato removerá os arquivos já selecionados. Deseja continuar?'))
assert.ok(wizardApi.includes("fd.append('modoDocumento', input.documentoModo)"))
assert.ok(wizardApi.includes("fd.append('documentoUnico', input.documentos[0])"))
assert.ok(wizardApi.includes("fd.append('documentoFrente', input.documentos[0])"))
assert.ok(wizardApi.includes("fd.append('documentoVerso', input.documentos[1])"))

for (const source of [gallery, video, kyc]) {
  assert.ok(source.includes('FilePicker'))
}
assert.ok(filePicker.includes('type="file"'))
assert.ok(filePicker.includes('type="button"'))
assert.ok(filePicker.includes('event.dataTransfer.files'))
assert.ok(filePicker.includes('aria-label={ariaLabel}'))
assert.ok(filePicker.includes('{file.name} selecionado'))
assert.ok(filePicker.includes('w-full min-w-0 max-w-full space-y-3 overflow-hidden'))
assert.ok(filePicker.includes('flex-1 truncate'))
assert.ok(kyc.includes('block min-w-0 max-w-full space-y-2'))
assert.ok(kyc.includes('grid min-w-0 grid-cols-[minmax(0,1fr)]'))

assert.ok(review.includes('Seu anúncio'))
assert.ok(review.includes('Onde você atende'))
assert.ok(review.includes('Atendimento'))
assert.ok(review.includes('Serviços oferecidos'))
assert.ok(review.includes('Atendimento Virtual'))
assert.ok(premium.includes('Confirmação de identidade no primeiro anúncio'))
assert.ok(premium.includes('Publicar gratuitamente'))
assert.ok(premium.includes('Destacar meu anúncio'))

assert.ok(accountSecurity.includes('Segurança'))
assert.ok(accountSecurity.includes('Alterar senha'))
assert.ok(accountSecurity.includes('Zona de risco'))
assert.ok(accountSecurity.includes('Excluir minha conta'))
assert.ok(accountSecurity.includes('EXCLUIR MINHA CONTA'))
assert.ok(accountSecurity.includes('PasswordRequirements'))
assert.equal((accountSecurity.match(/<PasswordInput/g) ?? []).length, 4)
assert.ok(accountSecurity.includes('passwordLock.current'))
assert.ok(accountSecurity.includes('deleteLock.current'))
assert.ok(accountSecurity.includes("router.replace('/entrar')"))
assert.ok(accountSecurity.includes("router.replace('/')"))

assert.ok(authApi.includes("publicRequest<PublicAccountAction>('/minha-conta/seguranca/senha'"))
assert.ok(authApi.includes("publicRequest<MyAccountDeletionEligibility>('/minha-conta/seguranca/exclusao'"))
assert.ok(authApi.includes("'Idempotency-Key': idempotencyKey"))
assert.ok(frontend('src/lib/meus-anuncios-api.ts').includes('[envelope?.message, envelope?.detail, envelope?.mensagem]'))
assert.ok(frontend('src/lib/meus-anuncios-api.ts').includes('.map(nonBlankString)'))
assert.ok(frontend('src/lib/meus-anuncios-api.ts').includes('candidateMessage || fallback'))
assert.ok(frontend('src/lib/meus-anuncios-api.ts').includes("if (typeof body.message === 'string' && body.message.trim()) message = body.message"))
assert.ok(frontend('src/lib/meus-anuncios-api.ts').includes("if (typeof body.code === 'string' && body.code.trim()) code = body.code"))
assert.ok(frontend('src/lib/meus-anuncios-api.ts').includes("bodyRequestId || response.headers.get('X-Request-Id')"))
assert.ok(openapi.includes(['/api/public/minha-conta/seguranca/senha', ':'].join('')))
assert.ok(openapi.includes('/api/public/minha-conta/seguranca/exclusao:'))
assert.ok(openapi.includes('/api/public/minha-conta/anuncios/{slug}/midias/lote:'))

// Execute the actual JSX and modal handler using the existing AST/VM test pattern.
// Only component/transport boundaries are synthetic; no browser or remote call.
function executeFunction(text, name, scope) {
  const file = ts.createSourceFile('fixture.tsx', text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  assert.equal(file.parseDiagnostics.length, 0)
  const matches = []
  const visit = (node) => {
    if (ts.isFunctionDeclaration(node) && node.name?.text === name) matches.push(node)
    ts.forEachChild(node, visit)
  }
  visit(file)
  assert.equal(matches.length, 1, `Declaracao real de ${name}`)
  const node = matches[0]
  const expression = ts.factory.createFunctionExpression(
    node.modifiers?.filter((item) => item.kind === ts.SyntaxKind.AsyncKeyword),
    node.asteriskToken, undefined, node.typeParameters, node.parameters, node.type, node.body,
  )
  const printed = ts.createPrinter().printNode(ts.EmitHint.Expression, expression, file)
  const { outputText } = ts.transpileModule(`(${printed})`, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.React,
    jsxFactory: 'element', jsxFragmentFactory: 'Fragment',
  } })
  return runInNewContext(outputText, scope, { timeout: 1000 })
}

const preview = frontend('src/features/anuncio-wizard/components/wizard-preview.tsx')
assert.ok(wizard.includes('const hasExistingKyc = Boolean(kycStatus?.prontoParaEnviarAnuncio)'))
const kycService = repository('backend/src/main/java/br/com/topsdojob/v3/application/publico/kyc/KycPublicoService.java')
assert.ok(kycService.includes('List.of("PENDENTE", "EM_ANALISE", "APROVADO").contains(status)'))
const componentNames = ['Fragment', 'ChevronRight', 'Crown', 'FileText', 'ImageIcon', 'ShieldCheck',
  'Sparkles', 'X', 'AnuncioCard', 'ImagemProprietario', 'Dialog', 'DialogClose', 'DialogContent',
  'DialogDescription', 'DialogHeader', 'DialogTitle', 'PreviewTag']
const renderPreview = executeFunction(preview, 'WizardPreview', {
  ...Object.fromEntries(componentNames.map((name) => [name, name])),
  element: (type, props, ...children) => ({ type, props, children: children.flat(Infinity) }),
  cn: (...values) => values.filter(Boolean).join(' '),
})
for (const [status, ready] of [
  ['NAO_INICIADO', false], ['PENDENTE', true], ['EM_ANALISE', true], ['APROVADO', true],
  ['REJEITADO', false], ['AJUSTE_SOLICITADO', false], [null, false],
]) {
  const rendered = JSON.stringify(renderPreview({
    showMobile: true, showDesktop: true, renderDialog: true, open: true,
    previewMedia: [], hasExistingKyc: ready, premiumChoice: 'gratis', hasVirtual: false,
  }))
  assert.ok(rendered.includes(ready ? 'Documentação enviada' : 'Documentação a concluir'), String(status))
  assert.ok(rendered.includes(ready
    ? 'A publicação do anúncio depende da moderação.'
    : 'Conclua a etapa de documentação antes de enviar o anúncio.'))
  assert.doesNotMatch(rendered, /Conta (?:não )?verificada|Identidade confirmada|Documentação aprovada|pronta para publicar|concluiremos rapidamente/i)
}

const verification = repository('backend/src/main/java/br/com/topsdojob/v3/application/publico/compliance/ComplianceVisitorVerificationService.java')
const pendingMessage = verification.match(/private VisitorAccessStatusDto statusDocumentoPendente\([\s\S]*?"([^"\n]+)"\);/)?.[1]
assert.equal(pendingMessage, 'Envie o documento solicitado para análise.')
const visitorModal = frontend('src/components/compliance/visitor-verification-modal.tsx')
for (const accepted of [true, false]) {
  const steps = [], messages = [], errors = []
  let requests = 0
  const denied = () => assert.fail('Documento pendente nao deve liberar acesso ou anunciar sucesso')
  const verifyPending = executeFunction(visitorModal, 'verify', {
    challenge: { challengeId: 'fixture-document', requiresExplicitAcknowledgement: false },
    cpf: '0'.repeat(11), birthDate: '01/01/1990', birthDateConfirmation: '01/01/1990',
    adultAccepted: accepted, restrictedAccepted: accepted, privacyAccepted: accepted, explicitAccepted: false,
    operationRef: { current: 0 }, verifyKeyRef: { current: 'fixture-verify' },
    obterGeracaoStatusVisitante: () => 1, setSubmitting: () => {}, setError: (value) => errors.push(value),
    setStep: (value) => steps.push(value), setMessage: (value) => messages.push(value),
    verifyVisitor: async () => { requests++; return { state: 'DOCUMENT_PENDING', verified: false, reasonPublic: pendingMessage } },
    mapStatus: denied, notificarMudancaVerificacao: denied, toast: { success: denied },
    onVerified: denied, onOpenChange: denied,
  })
  await verifyPending()
  assert.equal(requests, accepted ? 1 : 0, 'Aceites continuam obrigatorios antes da chamada')
  assert.deepEqual(steps, accepted ? ['document'] : [])
  assert.deepEqual(messages, accepted ? [pendingMessage] : [])
  assert.deepEqual(errors, accepted ? [null] : ['Confirme todos os aceites obrigatorios.'])
}

console.log('OK_WIZARD_E_MINHA_CONTA_SEGURANCA')
