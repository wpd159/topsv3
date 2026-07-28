import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8')
const page = read('src/app/(painel-admin)/admin/staff/page.tsx')
const detail = read('src/app/(painel-admin)/admin/staff/[id]/page.tsx')
const edit = read('src/app/(painel-admin)/admin/staff/[id]/editar/page.tsx')
const modal = read('src/app/(painel-admin)/admin/components/novo-staff-modal.tsx')
const table = read('src/app/(painel-admin)/admin/components/staff-table.tsx')
const api = read('src/features/admin-staff/api.ts')
const openapi = read('../contracts/openapi/topsdojob-v3-local.yaml')
const controller = read('../backend/src/main/java/br/com/topsdojob/v3/web/admin/staff/AdminStaffController.java')
const service = read('../backend/src/main/java/br/com/topsdojob/v3/application/admin/staff/AdminStaffService.java')

const assertions = [
  ['placeholder removido', ![page, detail, edit].some((source) => source.includes('PENDING_BACKEND_CONTRACTS'))],
  ['lista paginada e filtros', page.includes('listAdminStaff') && page.includes('ordenacao') && page.includes('status')],
  ['criacao segura sem campo senha', modal.includes('redefinição') && !modal.includes('type="password"')],
  ['somente papeis permitidos', modal.includes('ADMIN') && modal.includes('MODERADOR') && !modal.includes('value="COMERCIAL"')],
  ['sem exclusao fisica', ![page, detail, edit, modal, table, api].some((source) => /excluir staff|deleteAdminStaff/i.test(source))],
  ['csrf nas mutacoes', api.includes('getAdminMutationHeaders')],
  ['protecao contra duplo clique', modal.includes('if (busy) return') && edit.includes('if (!detail || busy) return')],
  ['confirmacao ao desativar', edit.includes('Desativar conta de staff?')],
  ['permissoes efetivas humanas', detail.includes('Permissões efetivas') && detail.includes('permission.descricao')],
  ['estado mobile sem tabela larga', table.includes('md:hidden') && table.includes('hidden overflow-x-auto')],
  ['rbac exclusivo de admin', controller.includes("hasRole('ADMIN')") && controller.includes("ADMIN_CONFIGURAR")],
  ['ultimo admin protegido', service.includes('o ultimo administrador ativo nao pode ser desativado ou rebaixado')],
  ['sessoes invalidadas', service.includes('invalidarSessoesAposCommit')],
  ['openapi completo', openapi.includes('/api/admin/staff:') && openapi.includes('AdminStaffAtualizarRequest')],
]

const failed = assertions.filter(([, ok]) => !ok)
if (failed.length) {
  failed.forEach(([name]) => console.error(`FAIL: ${name}`))
  process.exit(1)
}
console.log(`OK: ${assertions.length} verificacoes direcionadas de staff`)
