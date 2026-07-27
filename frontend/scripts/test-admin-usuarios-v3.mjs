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
const documents = source('features/admin-documentos/admin-kyc-document-grid.tsx')
const api = source('features/admin-usuarios/api.ts')
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
assert.ok(api.includes("`/usuarios/${encodeURIComponent(id)}`"), 'O detalhe deve usar o contrato canonico.')

for (const field of ['termo', 'status', 'kyc', 'ordenacao', 'page', 'size']) {
  assert.ok(list.includes(field), `Filtro ou paginacao ausente: ${field}.`)
}
for (const label of ['Nome, e-mail, CPF, telefone ou ID', 'Todos os estados', 'Todos os KYC', 'Mais recentes', 'Mais antigos']) {
  assert.ok(list.includes(label), `Controle da lista ausente: ${label}.`)
}
assert.ok(list.includes('md:hidden') && list.includes('hidden overflow-x-auto') && list.includes('md:block'), 'A lista deve ter layouts desktop e mobile.')
assert.ok(list.includes('Nenhum usuário encontrado'), 'Resposta 200 vazia deve ser um estado legitimo.')
assert.ok(list.includes('<ContractState error={error}'), 'Erro tecnico deve permitir nova tentativa.')
assert.ok(list.includes('retorno='), 'O detalhe deve preservar busca e pagina no retorno.')

for (const section of ['Dados cadastrais', 'Anúncios vinculados', 'KYC privado', 'Histórico administrativo']) {
  assert.ok(detail.includes(section), `Secao protegida ausente: ${section}.`)
}
assert.ok(detail.includes('AdminKycDocumentGrid'), 'KYC deve reutilizar a grade documental protegida.')
assert.ok(documents.includes('getAdminDocumentTemporaryUrl'), 'A visualizacao integral deve usar a URL temporaria privada existente.')
assert.ok(documents.includes('adminDocumentThumbnailUrl'), 'Os cards devem usar miniaturas protegidas, nao os arquivos integrais.')
assert.ok(!detail.includes('objectKey') && !detail.includes('bucket'), 'Storage privado nao pode aparecer na interface.')
assert.ok(detail.includes("usuario?.cargo === 'ADMIN'"), 'Acoes juridicas devem permanecer exclusivas de ADMIN.')
assert.ok(detail.includes('blockAdminAdAndUser') && detail.includes('unblockAdminUser'), 'Bloqueio e desbloqueio devem reutilizar o servico canonico.')
assert.ok(detail.includes('actionLock.current'), 'A mutacao deve impedir duplo clique.')
assert.ok(detail.includes('anuncioAncoraBloqueioId'), 'A acao deve reutilizar o anuncio ancora do contrato existente.')
assert.ok(!detail.includes('Excluir usuário'), 'A gestao nao pode introduzir exclusao fisica de usuario.')
assert.ok(detail.includes('flex flex-wrap') && detail.includes('sm:grid-cols-2'), 'O detalhe deve quebrar controles sem overflow em 390 px.')
assert.ok(edit.includes('MaskedPhoneInput') && edit.includes('phoneToE164BR'), 'A edicao deve manter mascara e enviar somente telefone normalizado.')
assert.ok(api.includes("method: 'PATCH'") && api.includes('JSON.stringify({ telefone })'), 'A atualizacao deve usar o contrato administrativo protegido.')

assert.ok(openapi.includes('/api/admin/usuarios:'), 'OpenAPI deve documentar a lista.')
assert.ok(openapi.includes('/api/admin/usuarios/{id}:'), 'OpenAPI deve documentar o detalhe.')
assert.ok(openapi.includes('AdminPaginaUsuarios') && openapi.includes('AdminUsuarioDetalhe'), 'Schemas administrativos de usuario devem estar documentados.')

console.log('Admin usuarios V3: lista, detalhe, KYC, RBAC e responsividade validados.')
