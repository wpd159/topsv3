import fs from 'node:fs'
import path from 'node:path'

const root = process.cwd()

function read(relative) {
  return fs.readFileSync(path.join(root, relative), 'utf8')
}

function requireText(source, expected, label) {
  if (!source.includes(expected)) throw new Error(`${label}: ausente ${expected}`)
}

function rejectText(source, forbidden, label) {
  if (source.includes(forbidden)) throw new Error(`${label}: encontrado ${forbidden}`)
}

const admin = read('src/app/(painel-admin)/admin/avisos/page.tsx')
const table = read('src/app/(painel-admin)/admin/avisos/avisos-table.tsx')
const api = read('src/lib/aviso-api.ts')
const manager = read('src/components/site/site-popup-manager.tsx')
const detail = read('src/app/(public-routes)/anuncios/[slug]/componentes/avisos-administracao.tsx')
const chrome = read('src/components/layout/public-chrome.tsx')
const contracts = read('src/lib/api-contract.ts')

for (const action of ['Novo aviso', 'Editar aviso', 'Publicar', 'Retirar de publicacao', 'Arquivar']) {
  requireText(`${admin}\n${table}`, action, `acao administrativa ${action}`)
}
for (const state of ['Carregando avisos', 'Nenhum aviso encontrado', 'Tentar novamente']) {
  requireText(admin, state, `estado de interface ${state}`)
}
for (const local of ['SITE', 'LOGIN_POPUP', 'ANUNCIO_RODAPE']) {
  requireText(`${admin}\n${api}\n${manager}\n${detail}`, local, `local ${local}`)
}
for (const frequency of ['SEMPRE', 'UMA_VEZ', 'DIARIO']) {
  requireText(`${admin}\n${manager}`, frequency, `frequencia ${frequency}`)
}

requireText(api, 'getAdminMutationHeaders', 'CSRF administrativo')
requireText(api, "'Idempotency-Key': idempotencyKey", 'idempotencia da criacao')
requireText(api, 'cache: \'no-store\'', 'retirada e publicacao sem cache falso')
requireText(admin, "timeZone: 'America/Sao_Paulo'", 'timezone da vigencia')
requireText(manager, 'shouldDisplay', 'persistencia da dispensa')
requireText(manager, 'localStorage', 'frequencia preservada no navegador')
requireText(manager, 'tops:login-success', 'popup apos login')
requireText(`${manager}\n${detail}`, 'Disponivel ate', 'vigencia exibida ao usuario')
requireText(chrome, '<SitePopupManager />', 'manager montado no chrome publico e privado')
requireText(detail, 'window.setInterval', 'carrossel comprovado da producao')
requireText(detail, 'if (!avisos.length) return null', 'vazio legitimo sem placeholder')
requireText(admin, 'sm:grid-cols-2', 'formulario responsivo')
requireText(admin, 'lg:grid-cols', 'filtros responsivos')
requireText(table, 'overflow-x-auto', 'tabela protegida no mobile')
rejectText(`${admin}\n${table}\n${detail}`, 'PendingActionFeedback', 'contrato pendente removido')
rejectText(contracts, "notices: 'Avisos e FAQ'", 'marcador de contrato pendente removido')
rejectText(`${admin}\n${api}`, 'email', 'sem comunicacao externa')
rejectText(`${admin}\n${api}`, 'push', 'sem notificacao push')

console.log('AVISOS_V3_TESTS_OK')
