import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const files = {
  page: await readFile(
    new URL('../src/app/(private-routes)/creditos/page.tsx', import.meta.url),
    'utf8'
  ),
  api: await readFile(new URL('../src/lib/creditos-pix-api.ts', import.meta.url), 'utf8'),
  backend: await readFile(
    new URL(
      '../../backend/src/main/java/br/com/topsdojob/v3/application/publico/pagamento/EfiPagamentoService.java',
      import.meta.url
    ),
    'utf8'
  ),
  webhook: await readFile(
    new URL(
      '../../backend/src/main/java/br/com/topsdojob/v3/application/publico/pagamento/EfiPagamentoConciliacaoService.java',
      import.meta.url
    ),
    'utf8'
  ),
  openapi: await readFile(
    new URL('../../contracts/openapi/topsdojob-v3-local.yaml', import.meta.url),
    'utf8'
  ),
}

assert.match(files.page, /pacotesCredito \?\? \[\]\)\.filter\(\(plano\) => plano\.ativo\)/)
assert.match(files.page, /Gerar cobrança Pix/)
assert.match(files.page, /Retomar Pix/)
assert.match(files.page, /Pix copia e cola/)
assert.match(files.page, /Atualizar pagamento/)
assert.match(files.page, /Histórico de compras Pix/)
assert.match(files.page, /Histórico de créditos/)
assert.match(files.page, /PENDENTE/)
assert.match(files.page, /APROVADO/)
assert.match(files.page, /EXPIRADO/)
assert.match(files.page, /CANCELADO/)
assert.match(files.page, /FALHO/)
assert.match(files.page, /sessionStorage/)
assert.match(files.page, /15_000/)
assert.match(files.page, /sm:grid-cols-2/)
assert.doesNotMatch(files.page, /ContractState|INTEGRATION_MISSING/)
assert.doesNotMatch(files.page, /comprarBeneficios|Premium.*automatic/i)
assert.doesNotMatch(files.page, /R\$\s*\d/)

assert.match(files.api, /\/minha-conta\/pagamentos\/pix/)
assert.match(files.api, /\/minha-conta\/pagamentos/)
assert.match(files.api, /\/conciliar/)
assert.match(files.api, /Idempotency-Key/)
assert.match(files.api, /XSRF/)
assert.match(files.api, /credentials: 'include'/)
assert.doesNotMatch(files.api, /clientSecret|certificate|pixKey|usuarioId|txid/)

assert.match(files.backend, /saveAndFlush/)
assert.match(files.backend, /findByIdForUpdate/)
assert.match(files.backend, /findByIdempotencyKey/)
assert.match(files.backend, /statusPublico/)
assert.match(files.backend, /identificacaoSanitizada/)
assert.match(files.webhook, /conciliarWebhook/)
assert.match(files.webhook, /findByTxidForUpdate/)
assert.match(files.webhook, /OrigemMovimentoCredito\.PAGAMENTO/)

assert.match(files.openapi, /\/api\/public\/minha-conta\/pagamentos:/)
assert.match(files.openapi, /EfiPagamentoHistorico:/)
assert.match(files.openapi, /enum: \[PENDENTE, APROVADO, EXPIRADO, CANCELADO, FALHO\]/)

console.log('COMPRA_CREDITOS_PIX_V3_FRONTEND_OK')
