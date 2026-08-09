import assert from 'node:assert/strict'
import { access, readFile } from 'node:fs/promises'

const files = {
  page: await readFile(
    new URL('../src/app/(private-routes)/creditos/page.tsx', import.meta.url),
    'utf8'
  ),
  api: await readFile(new URL('../src/lib/creditos-pix-api.ts', import.meta.url), 'utf8'),
  legacyCheckout: await readFile(
    new URL('../src/app/(public-routes)/checkout/creditos/[planoId]/page.tsx', import.meta.url),
    'utf8'
  ),
}

async function fileExists(url) {
  try {
    await access(url)
    return true
  } catch {
    return false
  }
}

assert.match(files.page, /fetchMinhaMonetizacao\(\)/)
assert.doesNotMatch(files.page, /totalAnuncios|anuncioId|anuncioSlug/)
assert.match(files.page, /\(monetizacao\?\.pacotesCredito \?\? \[\]\)\.filter\(\(plano\) => plano\.ativo\)/)
assert.match(files.page, /plano\.valor/)
assert.match(files.page, /plano\.quantidadeCreditos/)
assert.doesNotMatch(files.page, /R\$\s*\d/)

assert.match(files.page, /Confirmar compra de créditos/)
assert.match(files.page, /confirmarCobranca/)
assert.match(files.page, /createInFlightRef\.current/)
assert.match(files.page, /if \(!planoConfirmacao \|\| contaBloqueada \|\| createInFlightRef\.current\) return/)
assert.match(files.api, /body: JSON\.stringify\(\{ planoCreditoId \}\)/)
assert.doesNotMatch(files.api, /anuncioId|usuarioId|bonus:|txid/)
assert.match(files.api, /Idempotency-Key/)
assert.match(files.page, /readOrCreateIdempotencyKey\(planoConfirmacao\.id\)/)

assert.match(files.page, /const contaBloqueada = Boolean\(usuario && usuario\.status !== 'ATIVO'\)/)
assert.match(files.page, /disabled=\{busy \|\| contaBloqueada\}/)
assert.match(files.page, /Sua conta precisa estar ativa/)
assert.doesNotMatch(files.page, /Ir para meus anúncios|pelo menos um anúncio ativo/)

assert.match(files.page, /imagemQrCode/)
assert.match(files.page, /navigator\.clipboard\.writeText\(checkout\.pixCopiaECola\)/)
assert.match(files.page, /formatCountdown/)
assert.match(files.page, /Tempo restante:/)
assert.match(files.page, /max-h-\[calc\(100dvh-1rem\)\]/)
assert.match(files.page, /max-w-60/)
assert.match(files.page, /\[overflow-wrap:anywhere\]/)

assert.match(files.page, /window\.setInterval\(\(\) => void consultarPagamento\(\), 15_000\)/)
assert.match(files.page, /checkout\.status !== 'PENDENTE'/)
assert.match(files.page, /window\.clearInterval\(timer\)/)
assert.match(files.page, /document\.hidden/)
assert.match(files.page, /visibilitychange/)
assert.match(files.page, /void consultarPagamento\(\)[\s\S]*startTimer\(\)/)
assert.match(files.page, /consultarCobrancaPix\(checkout\.pagamentoId\)/)
assert.match(files.page, /conciliarCobrancaPix\(checkout\.pagamentoId\)/)
assert.doesNotMatch(files.page, /setInterval[\s\S]{0,180}conciliarCobrancaPix/)

assert.match(files.page, /pagamentosData\.find\(\(pagamento\) => pagamentoPendenteValido\(pagamento\)\)/)
assert.match(files.page, /setCheckout\(checkoutDoHistorico\(pendente\)\)/)
assert.match(files.page, /consultarCobrancaPix\(pendente\.pagamentoId\)/)
assert.match(files.page, /!expiracaoValida\(pagamento\.expiracaoEm, agora\)/)
assert.match(files.page, /Esta cobrança expirou/)

assert.match(files.page, /next\.status !== 'PENDENTE'/)
assert.match(files.page, /atualizarDadosDepoisDoPagamento/)
assert.match(files.page, /créditos adicionados ao saldo/)
assert.doesNotMatch(files.page, /comprarBeneficios|ativarPremium|criarStory|criarAnuncio/)

assert.doesNotMatch(files.page, /localStorage|indexedDB|console\./)
assert.doesNotMatch(
  files.page,
  /sessionStorage\.(?:getItem|setItem)\([^\n]*(?:pixCopiaECola|imagemQrCode|pagamentoId)/
)
assert.doesNotMatch(files.page, /URLSearchParams|router\.push\([^)]*(?:pagamento|pix|txid)/)
assert.doesNotMatch(files.api, /console\.|efi\.com|clientSecret|certificate|pixKey|usuarioId|txid/)
assert.match(files.api, /cache: 'no-store'/)
assert.match(files.api, /export class PixApiError/)
assert.match(files.api, /X-Request-Id/)
assert.match(files.page, /Código de atendimento:/)

assert.match(files.page, /role="status"/)
assert.match(files.page, /role="alert"/)
assert.match(files.page, /aria-live="polite"/)
assert.match(files.page, /onCloseAutoFocus/)
assert.match(files.page, /focusCheckoutAfterCloseRef/)

assert.match(files.legacyCheckout, /redirect\('\/creditos'\)/)
assert.doesNotMatch(files.legacyCheckout, /redirect\('\/painel'\)/)
assert.equal(
  await fileExists(new URL('../src/components/modals/credito-bloqueio-modal.tsx', import.meta.url)),
  false,
  'o modal legado que exigia anuncio deve ser removido'
)

console.log('COMPRA_CREDITOS_PIX_V3_FRONTEND_OK')
