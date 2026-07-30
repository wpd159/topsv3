import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(scriptDirectory, '..')
const sourceRoot = path.join(frontendRoot, 'src')
const repositoryRoot = path.resolve(frontendRoot, '..')

function source(relativePath) {
  return readFileSync(path.resolve(sourceRoot, relativePath), 'utf8')
}

const listPage = source('app/(painel-admin)/admin/usuarios/page.tsx')
const detailPage = source('app/(painel-admin)/admin/usuarios/[id]/page.tsx')
const editPage = source('app/(painel-admin)/admin/usuarios/[id]/editar/page.tsx')
const list = source('features/admin-usuarios/admin-usuarios-list.tsx')
const detail = source('features/admin-usuarios/admin-usuario-detail.tsx')
const edit = source('features/admin-usuarios/admin-usuario-edit-form.tsx')
const credit = source('features/admin-usuarios/admin-usuario-credit-dialog.tsx')
const deletion = source('features/admin-usuarios/admin-usuario-delete-dialog.tsx')
const upload = source('features/admin-documentos/admin-kyc-upload-dialog.tsx')
const documents = source('features/admin-documentos/admin-kyc-document-grid.tsx')
const api = source('features/admin-usuarios/api.ts')
const documentApi = source('features/admin-documentos/api.ts')
const creditApi = source('lib/admin-creditos-operacionais-api.ts')
const openapi = readFileSync(
  path.join(repositoryRoot, 'contracts/openapi/topsdojob-v3-local.yaml'),
  'utf8',
)

assert.ok(listPage.includes('AdminUsuariosList'), 'A rota deve usar a listagem administrativa real.')
assert.ok(detailPage.includes('AdminUsuarioDetail'), 'A rota de detalhe deve usar o contrato real.')
assert.ok(editPage.includes('AdminUsuarioEditForm'), 'A rota de edicao deve usar o formulario administrativo real.')
assert.ok(!listPage.includes('PENDING_BACKEND_CONTRACTS'), 'A listagem nao pode permanecer pendente.')
assert.ok(!detailPage.includes('PENDING_BACKEND_CONTRACTS'), 'O detalhe nao pode permanecer pendente.')

assert.ok(api.includes('adminApiUrl(path)'), 'O adapter deve reutilizar o resolvedor administrativo.')
assert.ok(api.includes("credentials: 'include'"), 'A sessao deve ser a unica fonte do operador.')
assert.ok(api.includes("cache: 'no-store'"), 'Dados pessoais nao podem entrar no cache do navegador.')
assert.ok(api.includes('apiErrorFromResponse(response)'), 'Falhas HTTP devem permanecer explicitas.')
assert.ok(api.includes("`/usuarios?${query.toString()}`"), 'A lista deve usar o contrato canonico.')
assert.ok(api.includes("'/usuarios/indicadores'"), 'Indicadores devem vir do backend.')
assert.ok(api.includes("method: 'PATCH'"), 'A edicao deve usar a mutacao administrativa protegida.')

for (const field of ['termo', 'status', 'kyc', 'grupo', 'uf', 'cidade', 'ordenacao', 'page', 'size']) {
  assert.ok(list.includes(field), `Filtro ou paginacao ausente: ${field}.`)
}
for (const label of [
  'Total de usuários',
  'Novos hoje',
  'Com anúncios',
  'Sem anúncios',
  'Nome, e-mail, CPF, telefone ou ID',
  'Todas as UFs',
  'Todas as cidades',
  'Cadastro mais recente',
  'Cadastro mais antigo',
]) {
  assert.ok(list.includes(label), `Controle da lista ausente: ${label}.`)
}
for (const action of ['Anúncios', 'Crédito', 'Ver']) {
  assert.ok(list.includes(action), `Acao da linha ausente: ${action}.`)
}
assert.ok(list.includes('[20, 30, 50, 100]'), 'A listagem deve oferecer os quatro tamanhos canonicos.')
assert.ok(list.includes('md:hidden') && list.includes('hidden overflow-x-auto'), 'A lista deve ter layouts desktop e mobile.')
assert.ok(list.includes('xl:grid-cols-12') && list.includes('sm:grid-cols-2'), 'Os filtros devem alinhar no desktop e empilhar responsivamente.')
assert.ok(list.includes('xl:items-end') && list.includes('min-h-4') && list.includes('h-10'), 'Labels, campos e Aplicar devem compartilhar linha de base e altura.')
assert.ok(list.includes('Nenhum usuário encontrado'), 'Resposta 200 vazia deve ser um estado legitimo.')
assert.ok(list.includes('<ContractState error={error}'), 'Erro tecnico deve permitir nova tentativa.')
assert.ok(list.includes('retorno='), 'O detalhe deve preservar busca e pagina no retorno.')
assert.ok(list.includes('user.potencialmenteExcluivel'), 'A lista deve oferecer exclusao apenas a contas potencialmente elegiveis.')

for (const section of [
  'Identificação',
  'Contato',
  'Dados pessoais',
  'Conta e KYC',
  'Endereço, localidades e anúncios',
  'Documentos KYC privados',
  'Histórico administrativo',
]) {
  assert.ok(detail.includes(section), `Secao protegida ausente: ${section}.`)
}
assert.ok(detail.includes('AdminKycDocumentGrid'), 'KYC deve reutilizar a grade documental protegida.')
assert.ok(detail.includes('AdminKycUploadDialog'), 'Inclusao e substituicao devem ficar na gestao do usuario.')
assert.ok(detail.includes('AdminUsuarioCreditDialog'), 'Saldo e ledger devem estar disponiveis no detalhe.')
assert.ok(!detail.includes('objectKey') && !detail.includes('bucket'), 'Storage privado nao pode aparecer na interface.')
assert.ok(detail.includes("usuario?.cargo === 'ADMIN'"), 'Mutacoes devem permanecer exclusivas de ADMIN.')
assert.ok(detail.includes('blockAdminAdAndUser') && detail.includes('unblockAdminUser'), 'Bloqueio deve reutilizar o servico canonico.')
assert.ok(detail.includes('actionLock.current'), 'A mutacao deve impedir duplo clique.')
assert.ok(detail.includes('AdminUsuarioDeleteDialog'), 'O detalhe deve reutilizar o modal seguro de exclusao.')

assert.ok(deletion.includes('Excluir usuário definitivamente'), 'O modal deve explicitar a irreversibilidade da exclusao.')
assert.ok(deletion.includes("confirmation !== 'EXCLUIR'"), 'A exclusao deve exigir confirmacao digitada.')
assert.ok(deletion.includes('getAdminUserDeletionEligibility'), 'O modal deve consultar a pre-validacao do backend.')
assert.ok(deletion.includes('idempotencyKey.current'), 'Retries devem reutilizar a mesma chave idempotente.')
assert.ok(deletion.includes('lock.current'), 'O modal deve impedir duplo clique.')
assert.ok(api.includes("method: 'DELETE'"), 'A exclusao deve usar o contrato DELETE canonico.')
assert.ok(api.includes("'Idempotency-Key': idempotencyKey"), 'A exclusao deve enviar a chave idempotente.')

for (const field of ['nome', 'nomeCivil', 'email', 'cpf', 'telefone', 'dataNascimento', 'versao']) {
  assert.ok(edit.includes(field), `Campo cadastral real ausente: ${field}.`)
}
assert.ok(edit.includes('MaskedPhoneInput') && edit.includes('phoneToE164BR'), 'Telefone deve manter mascara e persistir normalizado.')
assert.ok(edit.includes('maskCpf') && edit.includes('cpfDigits'), 'CPF deve manter mascara e persistir normalizado.')
assert.ok(edit.includes('BirthDateField') && edit.includes('birthDateToIso'), 'Nascimento deve usar mascara e persistir ISO.')
assert.ok(edit.includes('AdminUserFormError') && edit.includes('fieldErrors'), 'Erros do backend devem aparecer no campo correto.')

assert.ok(upload.includes('Adicionar documentos') && upload.includes('Substituir documentos'), 'Fluxos documentais administrativos devem estar visiveis.')
assert.ok(upload.includes('image/jpeg,image/png,application/pdf'), 'Imagem e PDF devem usar o validador canonico.')
assert.ok(documentApi.includes('Idempotency-Key') && documentApi.includes('multipart/form-data') === false, 'Upload deve ser idempotente e deixar o navegador definir o boundary.')
assert.ok(documents.includes('getAdminDocumentTemporaryUrl'), 'A visualizacao integral deve usar a URL temporaria privada existente.')
assert.ok(documents.includes('adminDocumentThumbnailUrl'), 'Os cards devem usar miniaturas protegidas.')
assert.ok(documents.includes('onReplace'), 'A grade compartilhada deve oferecer substituicao somente quando autorizada.')

assert.ok(credit.includes('Saldo atual') && credit.includes('Historico'), 'Modal deve exibir saldo e ledger.')
assert.ok(credit.includes('Adicionar') && credit.includes('Remover'), 'Modal deve oferecer as duas operacoes administrativas.')
assert.ok(credit.includes('window.confirm'), 'Ajuste de credito deve exigir confirmacao.')
assert.ok(credit.includes('actionLock.current'), 'Ajuste de credito deve bloquear duplo clique.')
assert.ok(creditApi.includes('Idempotency-Key') && creditApi.includes('idempotencyKey'), 'Retry deve reutilizar a chave idempotente.')

assert.ok(openapi.includes('/api/admin/usuarios/indicadores:'), 'OpenAPI deve documentar os indicadores.')
assert.ok(openapi.includes('/api/admin/usuarios/{id}/exclusao:'), 'OpenAPI deve documentar a pre-validacao.')
assert.ok(openapi.includes('AdminUsuarioExclusaoResultado'), 'OpenAPI deve documentar o resultado da exclusao.')
assert.ok(openapi.includes('/api/admin/documentos/usuarios/{usuarioId}/envios:'), 'OpenAPI deve documentar o upload privado.')
assert.ok(openapi.includes('AdminUsuarioIndicadores') && openapi.includes('AdminUsuarioAtualizacaoRequest'), 'Schemas completos devem estar documentados.')

console.log('Admin usuarios completo: edicao, documentos, filtros, creditos, privacidade e responsividade validados.')
