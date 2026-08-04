import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const files = {
  page: await readFile(new URL('../src/app/(painel-admin)/admin/creditos/page.tsx', import.meta.url), 'utf8'),
  manager: await readFile(new URL('../src/app/(painel-admin)/admin/components/plano-credito-manager.tsx', import.meta.url), 'utf8'),
  api: await readFile(new URL('../src/lib/admin-creditos-operacionais-api.ts', import.meta.url), 'utf8'),
  openapi: await readFile(new URL('../../contracts/openapi/topsdojob-v3-local.yaml', import.meta.url), 'utf8'),
}

assert.match(files.page, /PlanoCreditoManager/)
assert.match(files.manager, /Pacotes de créditos/)
assert.match(files.manager, /Novo pacote/)
assert.match(files.manager, /Buscar por nome ou codigo/)
assert.match(files.manager, /Todos os status/)
assert.match(files.manager, /Ativos/)
assert.match(files.manager, /Inativos/)
assert.match(files.manager, /Desativar este plano\?/)
assert.match(files.manager, /Nenhum pacote de créditos cadastrado/)
assert.match(files.manager, /Tentar novamente/)
assert.match(files.manager, /md:hidden/)
assert.match(files.manager, /hidden overflow-x-auto md:block/)
assert.match(files.manager, /saveLock = useRef\(false\)/)
assert.match(files.manager, /if \(saveLock\.current \|\| salvando\) return/)
assert.match(files.manager, /statusLock = useRef\(false\)/)
assert.match(files.manager, /if \(statusLock\.current \|\| alterandoStatusId\) return/)
assert.match(files.manager, /cargaSeq = useRef\(0\)/)
assert.match(files.manager, /seq === cargaSeq\.current/)
assert.match(files.manager, /comprasConfirmadas/)
assert.match(files.manager, /Intl\.NumberFormat\('pt-BR'/)
assert.doesNotMatch(files.manager, /Integra[cç][aã]o pendente/i)
assert.doesNotMatch(files.manager, /AdminCreditosApi\.(ajustar|estornar)|\/pagamentos|webhook|ledger/i)

assert.match(files.api, /\/creditos\/pacotes\?busca=/)
assert.match(files.api, /detalharPacote/)
assert.match(files.api, /criarPacote/)
assert.match(files.api, /atualizarPacote/)
assert.match(files.api, /ativarPacote/)
assert.match(files.api, /desativarPacote/)
assert.match(files.api, /readAntiForgeryValue/)

assert.match(files.openapi, /\/api\/admin\/creditos\/pacotes:/)
assert.match(files.openapi, /\/api\/admin\/creditos\/pacotes\/\{id\}\/ativacao:/)
assert.match(files.openapi, /\/api\/admin\/creditos\/pacotes\/\{id\}\/desativacao:/)
assert.match(files.openapi, /AdminPlanoCreditoCriarRequest/)
assert.match(files.openapi, /AdminPlanoCreditoAtualizarRequest/)
assert.match(files.openapi, /AdminPlanoCreditoStatusRequest/)
assert.match(files.openapi, /comprasConfirmadas/)

console.log('PLANOS_CREDITOS_ADMIN_FRONTEND_OK')