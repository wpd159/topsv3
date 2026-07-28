import assert from 'node:assert/strict'
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const sourceRoot = path.resolve(scriptDirectory, '../src')

function listFiles(directory) {
  return readdirSync(directory).flatMap((entry) => {
    const absolute = path.join(directory, entry)
    return statSync(absolute).isDirectory() ? listFiles(absolute) : [absolute]
  })
}

function source(relativePath) {
  return readFileSync(path.resolve(sourceRoot, relativePath), 'utf8')
}

const sourceFiles = listFiles(sourceRoot).filter((file) => /\.(?:ts|tsx)$/.test(file))
const combinedSource = sourceFiles.map((file) => readFileSync(file, 'utf8')).join('\n')

const forbiddenRoutes = [
  '/api/public/api/public',
  '/api/public/api/admin',
  '/localidades/estados',
  '/anuncios/staff',
  '/wizard-progress/sync',
  '/ws-suporte',
  '/chat/recentes',
  '/usuarios/username',
]

for (const route of forbiddenRoutes) {
  assert.ok(!combinedSource.includes(route), `Rota legada ou duplicada ativa: ${route}`)
}

const localBaseReaders = sourceFiles
  .filter((file) => readFileSync(file, 'utf8').includes('NEXT_PUBLIC_API_URL'))
  .map((file) => path.relative(sourceRoot, file).replaceAll('\\', '/'))
  .sort()

assert.deepEqual(
  localBaseReaders,
  ['app/sitemap.ts', 'lib/api-contract.ts'],
  'Adapters nao podem manter resolvedores locais de NEXT_PUBLIC_API_URL.'
)

const contract = source('lib/api-contract.ts')
for (const status of ['case 400:', 'case 401:', 'case 403:', 'case 404:', 'case 409:', 'response.status >= 500']) {
  assert.ok(contract.includes(status), `Tratamento HTTP ausente: ${status}`)
}
assert.ok(contract.includes("'NETWORK_FAILURE'"), 'Falha de rede deve ter estado proprio.')
assert.ok(contract.includes('requireArrayPayload'), 'Payload de lista deve ser validado explicitamente.')
assert.ok(contract.includes('BackendContractPendingError'), 'Contratos ausentes devem ser explicitos.')

const publicCatalog = source('lib/public-catalog-api.ts')
assert.ok(publicCatalog.includes('publicApiUrl('), 'Catalogo publico deve usar o resolvedor canonico.')
assert.ok(publicCatalog.includes('apiErrorFromResponse('), 'Catalogo publico deve preservar os status de erro.')
assert.ok(publicCatalog.includes('requireArrayPayload'), 'Listas publicas devem validar o payload.')

const grid = source('components/anuncios/anuncios-grid.tsx')
assert.ok(grid.includes('setError(fetchError)'), 'Falha do grid nao pode virar lista vazia.')
assert.ok(grid.includes('<ContractState error={error}'), 'Grid deve exibir erro de integracao.')
assert.ok(grid.includes('if (anuncios.length === 0)'), 'Resposta 200 vazia deve manter estado vazio legitimo.')

const locationFilter = source('components/anuncios/barra-localizacao.tsx')
assert.ok(locationFilter.includes('setErroLocalidades(error)'), 'Falha de localidades nao pode virar lista vazia silenciosa.')
assert.ok(locationFilter.includes('setReloadLocalidades((value) => value + 1)'), 'Filtro de localidades deve permitir nova tentativa.')

const payments = source('app/(painel-admin)/admin/components/financeiro/financeiro-table.tsx')
assert.ok(payments.includes('setErro(error)'), 'Falha financeira nao pode virar contador zero.')
assert.ok(payments.includes("'Contagem indisponivel'"), 'Contador financeiro deve diferenciar falha de zero.')

const sidebar = source('app/(painel-admin)/admin/components/sidebar/sidebar.tsx')
assert.equal((sidebar.match(/fetch\(/g) || []).length, 4, 'Sidebar deve consultar somente os quatro contratos reais.')
assert.ok(sidebar.includes("adminApiUrl('/moderacao/resumo')"), 'Sidebar deve consultar o resumo de moderacao real.')
assert.ok(sidebar.includes("adminApiUrl('/tickets/indicadores')"), 'Sidebar deve consultar os indicadores reais de tickets.')
assert.ok(sidebar.includes("adminApiUrl('/denuncias/indicadores')"), 'Sidebar deve consultar os indicadores reais de denuncias.')
assert.ok(sidebar.includes("adminApiUrl('/sugestoes/indicadores')"), 'Sidebar deve consultar os indicadores reais de sugestoes.')
assert.ok(sidebar.includes('Promise.all(['), 'Contadores reais devem compartilhar a mesma janela de atualizacao.')
assert.ok(sidebar.includes('if (inFlight) return inFlight'), 'Polling simultaneo deve ser deduplicado.')
assert.ok(sidebar.includes('tickets: null') && sidebar.includes('sugestoes: null'), 'Falhas de contador nao podem exibir zero falso.')

const moderation = source('features/moderation-v2/api/client.ts')
for (const endpoint of ['/anuncios?page=0&size=100', '/moderacao/revisoes?page=0&size=100', '/midias', '/documentos', '/premium']) {
  assert.ok(moderation.includes(endpoint), `Contrato canonico da moderacao ausente: ${endpoint}`)
}

const moderationDetail = source('features/moderation-v2/components/moderacao-v2-detail.tsx')
assert.ok(moderationDetail.includes('setAuditError(error)'), 'Falha de auditoria nao pode aparecer como trilha vazia.')
assert.ok(moderationDetail.includes('setPremiumError(error)'), 'Falha de Premium nao pode permanecer como loading infinito.')
assert.ok(moderationDetail.includes('FotosAnuncioSection'), 'Gestao administrativa de fotos deve permanecer alcancavel.')

const moderationGallery = source('features/moderation-v2/components/moderacao-v2-media-gallery.tsx')
for (const label of ['Aprovar midia', 'Solicitar ajuste', 'Rejeitar midia', 'Remover da revisao', 'Ampliar foto']) {
  const normalizedGallery = moderationGallery.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  assert.ok(normalizedGallery.includes(label), `Acao de midia da moderacao ausente: ${label}`)
}

const pendingFeedback = source('components/feedback/contract-state.tsx')
assert.ok(
  pendingFeedback.includes('CONTRATO_BACKEND_AUSENTE'),
  'Acao sem contrato deve informar explicitamente CONTRATO_BACKEND_AUSENTE.'
)

assert.ok(
  !existsSync(path.resolve(sourceRoot, 'components/feedback/pending-contract-page.tsx')),
  'A pagina generica pendente nao pode substituir telas funcionais completas.'
)

const preservedSurfaces = {
  'app/(painel-admin)/admin/usuarios/[id]/editar/page.tsx': [
    'Nome completo', 'Nome de usuario', 'E-mail', 'Telefone', 'CPF',
    'Data de nascimento', 'Salvar alteracoes', 'Documentos', 'Ver', 'Novo documento',
  ],
  'app/(painel-admin)/admin/blog/components/blog-post-form.tsx': [
    'Gerenciar categorias', 'Imagem destacada', 'Remover imagem', 'Imagem OG',
    'Remover imagem OG', 'Visualizar', 'Salvar como rascunho', 'SEO title',
    'SEO description', 'Sitemap priority', 'Change frequency',
  ],
  'app/(painel-admin)/admin/compliance/page.tsx': [
    'Auditoria administrativa', 'Exportar CSV', 'Aceites juridicos', 'Logs visitantes',
    'Documentos visitantes', 'Visualizar documento', 'Aprovar', 'Rejeitar',
    'Motivo da decisao', 'Historico da decisao', 'Risco por sessao', 'Eventos criticos',
    'Score', 'Falhas', 'Ultimo motivo', 'Salvar configuracoes',
  ],
  'components/chat/sidebar-chat.tsx': ['Nova conversa', 'Digite o username', 'Buscar conversa', 'Iniciar conversa'],
  'components/chat/chat.tsx': ['Ver anuncios', 'Digite uma mensagem', 'Enviar mensagem'],
  'app/(painel-admin)/admin/denuncias/page.tsx': [
    'Abrir detalhe', 'Com providencia', 'Sem providencia', 'Data inicial', 'Data final',
  ],
  'app/(painel-admin)/admin/faqs/page.tsx': ['Nova FAQ', 'Pergunta', 'Resposta', 'Categoria', 'Mover para cima', 'Excluir'],
  'app/(painel-admin)/admin/indicacoes/page.tsx': ['Editar creditos por indicacao', 'Total de indicacoes', 'ranking', 'Salvar'],
  'app/(painel-admin)/admin/registros/page.tsx': ['Todas as acoes', 'Modulo', 'Data inicial', 'Consultar detalhe', 'Proximo'],
  'app/(painel-admin)/admin/wizard-progress/page.tsx': ['Buscar anunciante', 'Todos os modos', 'Abrir anuncio', 'Abrir moderacao', 'Editar usuario'],
  'app/(painel-admin)/admin/components/novo-staff-modal.tsx': ['Nome completo', 'Nome de usuario', 'E-mail', 'CPF', 'Administrador', 'Moderador', 'Suporte', 'Mostrar', 'senha'],
  'app/(painel-admin)/admin/components/anuncios/admin-anuncio-dados-inline-editor.tsx': [
    'Titulo', 'Descricao', 'Categoria', 'Preco', 'UF', 'Cidade', 'Bairro',
    'Horario', 'Microrregiao', 'WhatsApp publico', 'Servicos', 'Locais de atendimento',
  ],
  'app/(painel-admin)/admin/components/fotos-anuncio-section.tsx': [
    'Fotos do anuncio', 'Adicionar', 'Remover (',
  ],
  'features/moderation-v2/components/moderacao-v2-detail.tsx': [
    'Anterior', 'Proximo', 'Aprovar', 'Reprovar', 'Pausar', 'Reativar', 'Ativar',
    'Editar', 'Beneficios', 'Remover', 'Excluir', 'Fila', 'Abrir editor do anuncio',
    'Ver auditoria', 'Confirmar exclusao', 'Cancelar',
  ],
  'app/(painel-admin)/admin/components/financeiro/financeiro-filtro.tsx': [
    'Status', 'Periodo', 'Filtrar',
  ],
  'app/(painel-admin)/admin/components/financeiro/financeiro-charts.tsx': [
    '7 dias', '30 dias', 'Mes atual', 'Receita no periodo', 'Transacoes aprovadas',
    'Creditos entregues', 'Grafico de receita e vendas',
  ],
  'app/(public-routes)/anuncios/[slug]/componentes/avisos-administracao.tsx': [
    'Aviso anterior', 'Proximo aviso', 'Ir para aviso 1',
  ],
  'app/(public-routes)/anuncios/usuario/[username]/anuncios-usuario-client.tsx': [
    'Foto anterior', 'Proxima foto', 'Ver anuncio', 'Chat', 'WhatsApp',
    'Adicionar aos favoritos', 'Confirmar maioridade', 'Aviso de seguranca', 'Cancelar',
    'Continuar', 'Voltar', 'Politica de Verificacao Etaria', 'Fechar',
  ],
  'app/(public-routes)/faq/page.tsx': [
    'Buscar', 'Falar com suporte', 'Limpar busca', 'Abrir ticket', 'LoginModal',
  ],
}

const ticketsAdmin = [
  source('app/(painel-admin)/admin/tickets/page.tsx'),
  source('app/(painel-admin)/admin/components/ticket-details-modal.tsx'),
  source('app/(painel-admin)/admin/components/ticket-chat-modal.tsx'),
].join('\n').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
for (const label of [
  'Atualizar', 'Abertos', 'Em andamento', 'Fechados', 'Limpar',
  'Responder', 'Ver detalhes', 'Alterar estado', 'Enviar resposta',
]) {
  assert.ok(ticketsAdmin.includes(label), `Controle funcional ausente em Tickets admin: ${label}`)
}
assert.ok(!ticketsAdmin.includes('PENDING_BACKEND_CONTRACTS'), 'Tickets admin nao podem permanecer pendentes.')

const ticketsUsuario = source('app/(private-routes)/meus-tickets/page.tsx')
  .normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '')
for (const label of [
  'Abrir ticket', 'Todos', 'Abertos', 'Encerrados', 'Encerrar ticket',
  'Enviar resposta', 'Tentar novamente',
]) {
  assert.ok(ticketsUsuario.includes(label), `Controle funcional ausente em Meus tickets: ${label}`)
}
assert.ok(!ticketsUsuario.includes('PENDING_BACKEND_CONTRACTS'), 'Meus tickets nao pode permanecer pendente.')
assert.ok(!ticketsUsuario.includes('type="file"'), 'Anexos nao comprovados nao devem ser inventados.')

const preservedSurfaceCompositions = {
  'app/(painel-admin)/admin/compliance/page.tsx': [
    'app/(painel-admin)/admin/compliance/visitor-documents.tsx',
    'app/(painel-admin)/admin/compliance/visitor-risk.tsx',
    'app/(painel-admin)/admin/compliance/visitor-age-logs.tsx',
  ],
}

for (const [relativePath, labels] of Object.entries(preservedSurfaces)) {
  const pageSource = [
    relativePath,
    ...(preservedSurfaceCompositions[relativePath] ?? []),
  ]
    .map(source)
    .join('\n')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
  for (const label of labels) {
    assert.ok(pageSource.includes(label), `Controle funcional ausente em ${relativePath}: ${label}`)
  }
  assert.ok(
    pageSource.includes('ContractState') || pageSource.includes('usePendingContractActions'),
    `Contrato ausente deve permanecer explicito em ${relativePath}`
  )
}

const canonicalSubstitutes = {
  'app/(painel-admin)/admin/usuarios/page.tsx': 'AdminUsuariosList',
  'app/(painel-admin)/admin/usuarios/[id]/page.tsx': 'AdminUsuarioDetail',
  'app/(painel-admin)/admin/anuncios/[id]/page.tsx': 'AdminAnuncioModeracao',
  'features/moderation-v2/components/anuncio-staff-edit-form.tsx': 'AdminAnuncioDadosInlineEditor',
  'app/(painel-admin)/admin/components/anuncios/admin-anuncio-stories-section.tsx': 'fetchAdminStorySelection',
  'app/(painel-admin)/admin/components/financeiro/financeiro-table.tsx': 'listarAdminPagamentos',
}

for (const [relativePath, substitute] of Object.entries(canonicalSubstitutes)) {
  assert.ok(source(relativePath).includes(substitute), `Substituto canonico ausente em ${relativePath}`)
}

assert.ok(
  !combinedSource.includes('disabled title="Contrato backend pendente"'),
  'Acoes preservadas devem responder com erro explicito, nao ficar indisponiveis sem interacao.'
)
assert.ok(
  !combinedSource.includes('/contrato-pendente'),
  'Contratos pendentes nao podem ser representados por identificadores ou rotas sinteticas.'
)

const wizardApi = source('features/anuncio-wizard/api.ts')
assert.ok(wizardApi.includes("publicApiUrl('/anunciar')"), 'Wizard deve usar URL publica canonica.')
assert.ok(wizardApi.includes('PENDING_BACKEND_CONTRACTS.wizardProfile'), 'Contrato ausente do perfil deve ser explicito.')

console.log('Saneamento contratual: rotas, erros, polling e funcoes pendentes validados.')
