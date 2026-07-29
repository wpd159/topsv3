import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

const [
  api,
  types,
  wizard,
  summary,
  success,
  actions,
  backend,
  openapi,
] = await Promise.all([
  read('src/features/monetizacao-wizard/api.ts'),
  read('src/features/monetizacao-wizard/types.ts'),
  read('src/features/monetizacao-wizard/monetizacao-wizard.tsx'),
  read('src/features/monetizacao-wizard/components/monetizacao-step-resumo.tsx'),
  read('src/features/monetizacao-wizard/components/monetizacao-step-sucesso.tsx'),
  read('src/features/monetizacao-wizard/actions.ts'),
  read('../backend/src/main/java/br/com/topsdojob/v3/application/publico/premium/MinhaContaPremiumService.java'),
  read('../contracts/openapi/topsdojob-v3-local.yaml'),
])

assert.match(types, /MonetizacaoOpcaoCodigo = string/)
assert.doesNotMatch(api, /function isCatalogCode/)
assert.doesNotMatch(api, /\[\s*'ANUNCIO_TOPO'[\s\S]*'OCULTAR_IDADE'[\s\S]*\]\.includes/)
assert.match(api, /'Idempotency-Key': idempotencyKey/)
assert.match(wizard, /activationKeyRef/)
assert.match(wizard, /activationInFlightRef/)
assert.match(wizard, /revalidarCatalogoAposAtivacao/)
assert.match(wizard, /Passo 3 de 4/)
assert.doesNotMatch(wizard, /id: 'pagamento'/)

assert.match(summary, /anuncio\.titulo/)
assert.match(summary, /anuncio\.slug/)
assert.match(summary, /Nenhum crédito será descontado antes da confirmação/)
assert.match(summary, /Confirmar ativação/)
assert.match(summary, /saldoSuficiente/)
assert.match(summary, /item\.duracao\.dias/)
assert.match(summary, /item\.duracao\.creditos/)

assert.match(success, /item\.inicioEm/)
assert.match(success, /item\.fimEm/)
assert.match(success, /item\.duracaoDias/)
assert.match(success, /item\.efeitoPublico/)
assert.match(success, /item\.motivoIneficacia/)
assert.match(success, /result\.saldoAnterior/)
assert.match(success, /result\.saldoPosterior/)

assert.match(actions, /revalidateTag\(PUBLIC_CATALOG_CACHE_TAG\)/)
assert.match(backend, /PremiumBeneficioCodigo\.TODOS\.contains\(codigo\)/)
assert.match(backend, /@Transactional\s+public MinhaCompraPremiumResultadoDto comprar/)
assert.match(backend, /saldo de creditos insuficiente/)
assert.match(backend, /PREMIUM_COMPRA_CREDITOS/)
assert.match(backend, /validarRetry/)
assert.match(backend, /motivoIneficacia/)
const purchaseSchema = openapi.slice(
  openapi.indexOf('    MinhaCompraPremiumRequest:'),
  openapi.indexOf('    MinhaCompraPremiumResultado:')
)
assert.doesNotMatch(purchaseSchema, /duracaoDias: \{ type: integer, enum:/)

console.log('MONETIZACAO_BENEFICIOS_V3_TESTS_OK')
