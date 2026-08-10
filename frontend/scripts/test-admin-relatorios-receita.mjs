import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const files = {
  page: await readFile(new URL('../src/app/(painel-admin)/admin/financeiro/page.tsx', import.meta.url), 'utf8'),
  api: await readFile(new URL('../src/lib/admin-pagamentos-api.ts', import.meta.url), 'utf8'),
  cards: await readFile(new URL('../src/app/(painel-admin)/admin/components/financeiro/financeiro-cards.tsx', import.meta.url), 'utf8'),
  chart: await readFile(new URL('../src/app/(painel-admin)/admin/components/financeiro/financeiro-charts.tsx', import.meta.url), 'utf8'),
  filters: await readFile(new URL('../src/app/(painel-admin)/admin/components/financeiro/financeiro-filtro.tsx', import.meta.url), 'utf8'),
  table: await readFile(new URL('../src/app/(painel-admin)/admin/components/financeiro/financeiro-table.tsx', import.meta.url), 'utf8'),
  usuarioUtils: await readFile(new URL('../src/app/(painel-admin)/admin/components/admin-usuarios-utils.ts', import.meta.url), 'utf8'),
  openapi: await readFile(new URL('../../contracts/openapi/topsdojob-v3-local.yaml', import.meta.url), 'utf8'),
}

assert.match(files.api, /pagamentos\/relatorio\/resumo/)
assert.match(files.api, /pagamentos\/relatorio\/transacoes/)
assert.match(files.api, /credentials: 'include'/)
assert.match(files.page, /useSearchParams/)
assert.match(files.page, /resumoErro/)
assert.match(files.page, /tabelaErro/)
assert.doesNotMatch(files.page, /RankingUsuarios/)
assert.match(files.cards, /Receita confirmada/)
assert.match(files.cards, /Pagamentos confirmados/)
assert.match(files.cards, /Pendentes/)
assert.match(files.cards, /Falhos/)
assert.match(files.chart, /ComposedChart/)
assert.match(files.chart, /Receita por pacote/)
assert.match(files.filters, /PERSONALIZADO/)
assert.match(files.filters, /EXPIRADO/)
assert.match(files.filters, /ESTORNADO/)
assert.match(files.filters, /America\/Sao_Paulo|Período/)
assert.match(files.table, /identificadorExternoMascarado/)
assert.match(files.table, /receitaConfirmadaFiltrada/)
assert.match(files.api, /usuarioId: string/)
assert.match(files.api, /usuarioEmail: string \| null/)
assert.match(files.api, /usuarioWhatsapp: string \| null/)
assert.doesNotMatch(files.api, /usuarioEmailMascarado/)
assert.match(files.table, /\/admin\/usuarios\/\$\{encodeURIComponent\(item\.usuarioId\)\}/)
assert.match(files.table, /buildWhatsAppUrl\(item\.usuarioWhatsapp\)/)
assert.match(files.table, /WhatsApp não informado/)
assert.match(files.usuarioUtils, /https:\/\/wa\.me\/\$\{normalized\}/)
const frontendSources = [files.page, files.api, files.cards, files.chart, files.filters, files.table, files.usuarioUtils].join('\n')
assert.doesNotMatch(frontendSources, /Contrato pendente|Integração pendente|Saldo Efi/)
assert.doesNotMatch(frontendSources, /\b(?:Efí|Efi|EFI|Gerencianet|PSP|OAuth|mTLS|certificado)\b|provedor externo|sejaefi|payloadlocation/i)
assert.doesNotMatch(frontendSources, /CPF|chave Pix|payload integral/)
assert.match(files.openapi, /\/api\/admin\/pagamentos\/relatorio\/resumo:/)
assert.match(files.openapi, /\/api\/admin\/pagamentos\/relatorio\/transacoes:/)
assert.match(files.openapi, /AdminRelatorioReceitaResumo/)
assert.match(files.openapi, /AdminRelatorioReceitaTransacao/)
assert.match(files.openapi, /usuarioId:/)
assert.match(files.openapi, /usuarioEmail:/)
assert.match(files.openapi, /usuarioWhatsapp:/)
assert.doesNotMatch(files.openapi, /usuarioEmailMascarado:/)
assert.match(files.openapi, /enum: \[TODOS, CONFIRMADO, PENDENTE, FALHO, CANCELADO, EXPIRADO, ESTORNADO, LEGADO\]/)

console.log('RELATORIOS_RECEITA_FRONTEND_OK')
