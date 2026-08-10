import assert from 'node:assert/strict'
import { access, readFile, readdir } from 'node:fs/promises'

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
  frontendEnv: await readFile(new URL('../.env.local.example', import.meta.url), 'utf8'),
  workflow: await readFile(
    new URL('../../.github/workflows/deploy-preprod.yml', import.meta.url),
    'utf8'
  ),
  preprodCompose: await readFile(
    new URL('../../deploy/preprod/docker-compose.yml', import.meta.url),
    'utf8'
  ),
}

async function readRuntimeSources(directoryUrl) {
  const entries = await readdir(directoryUrl, { withFileTypes: true })
  const sources = []
  for (const entry of entries) {
    const entryUrl = new URL(entry.name, directoryUrl)
    if (entry.isDirectory()) {
      sources.push(...await readRuntimeSources(new URL(entry.name + '/', directoryUrl)))
    } else if (/\.(?:js|jsx|ts|tsx)$/.test(entry.name)) {
      sources.push(await readFile(entryUrl, 'utf8'))
    }
  }
  return sources
}

async function fileExists(url) {
  try {
    await access(url)
    return true
  } catch {
    return false
  }
}

const runtimeSources = (await readRuntimeSources(new URL('../src/', import.meta.url)))
  .join('\n')
  .replaceAll('NEXT_PUBLIC_EFI_PIX_ENABLED', 'NEXT_PUBLIC_PIX_CHECKOUT_ENABLED')

assert.doesNotMatch(
  runtimeSources,
  /\b(?:Efí|Efi|EFI|Gerencianet|PSP|OAuth|mTLS|certificado)\b|provedor externo|sejaefi|payloadlocation/i,
  'o runtime frontend não pode revelar a infraestrutura de pagamento'
)

assert.match(files.page, /fetchMinhaMonetizacao\(\)/)
assert.doesNotMatch(files.page, /totalAnuncios|anuncioId|anuncioSlug/)
assert.match(files.page, /\(monetizacao\?\.pacotesCredito \?\? \[\]\)\.filter\(\(plano\) => plano\.ativo\)/)
assert.match(files.page, /plano\.valor/)
assert.match(files.page, /plano\.quantidadeCreditos/)
assert.doesNotMatch(files.page, /R\$\s*\d/)

for (const label of [
  'Comprar créditos',
  'Pacotes disponíveis',
  'Histórico de compras via Pix',
  'Histórico de créditos',
  'Ativação de Story por 24 horas',
  'Código Pix disponível',
  'Copiar código Pix',
]) {
  assert.ok(files.page.includes(label), 'texto público ausente: ' + label)
}
assert.doesNotMatch(files.page, /Gerar cobrança Pix|Pacote configurável para futura cobrança/)
assert.doesNotMatch(files.api, /messageFromApiBody|body\.message|raw\.(?:message|error)/)
assert.match(files.api, /const body = JSON\.parse\(raw\) as \{ requestId\?: unknown; code\?: unknown \}/)

assert.equal(
  (files.page.match(/Comprar créditos/g) ?? []).length >= 2,
  true,
  'o botão geral e o CTA dos pacotes devem usar Comprar créditos'
)
assert.match(files.page, /disabled=\{!PIX_CHECKOUT_DISPONIVEL \|\| !planos\.length\}/)
assert.match(files.page, /onClick=\{focarPacotes\}/)
assert.match(files.page, /pacotesSectionRef/)
assert.match(files.page, /scrollIntoView\(\{ behavior: 'smooth', block: 'start' \}\)/)
assert.match(files.page, /section\.focus\(\{ preventScroll: true \}\)/)
assert.ok(files.page.includes('Nenhum pacote de créditos está disponível no momento'))
assert.match(files.page, /setPlanoConfirmacao\(plano\)/)
assert.match(files.page, /plano\.descricao\?\.trim\(\) \?/)

const unavailableMessage =
  'Serviço Pix temporariamente indisponível. Tente novamente mais tarde.'
assert.equal(
  (files.page.match(new RegExp(unavailableMessage, 'g')) ?? []).length,
  1,
  'a indisponibilidade Pix deve produzir uma única mensagem específica'
)
assert.match(
  files.page,
  /const PIX_CHECKOUT_DISPONIVEL = process\.env\.NEXT_PUBLIC_EFI_PIX_ENABLED === 'true'/
)
assert.match(files.page, /if \(!PIX_CHECKOUT_DISPONIVEL\) \{[\s\S]*?setCheckout\(null\)[\s\S]*?return/)
assert.match(files.page, /const selecionarPlano[\s\S]*?if \(!PIX_CHECKOUT_DISPONIVEL\) return/)

assert.ok(files.page.includes('Confirmar compra de créditos'))
assert.match(files.page, /confirmarCobranca/)
assert.match(files.page, /createInFlightRef\.current/)
assert.match(files.page, /const confirmarCobranca = async \(\) => \{[\s\S]*?!PIX_CHECKOUT_DISPONIVEL[\s\S]*?return[\s\S]*?criarCobrancaPix\(/)
assert.match(files.api, /body: JSON\.stringify\(\{ planoCreditoId \}\)/)
assert.doesNotMatch(files.api, /anuncioId|usuarioId|bonus:|txid/)
assert.match(files.api, /Idempotency-Key/)
assert.match(files.page, /readOrCreateIdempotencyKey\(planoConfirmacao\.id\)/)

assert.match(files.page, /const contaBloqueada = Boolean\(usuario && usuario\.status !== 'ATIVO'\)/)
assert.match(files.page, /disabled=\{!PIX_CHECKOUT_DISPONIVEL \|\| busy \|\| contaBloqueada\}/)
assert.ok(files.page.includes('Sua conta precisa estar ativa'))
assert.doesNotMatch(files.page, /Ir para meus anúncios|pelo menos um anúncio ativo/)

assert.match(files.page, /imagemQrCode/)
assert.match(files.page, /navigator\.clipboard\.writeText\(checkout\.pixCopiaECola\)/)
assert.ok(files.page.includes('Código Pix disponível'))
assert.ok(files.page.includes('Copiar código Pix'))
assert.ok(files.page.includes('Código Pix copiado'))
assert.doesNotMatch(files.page, /<textarea|<Textarea/)
assert.doesNotMatch(files.page, /\{checkout\.pixCopiaECola\}/)
assert.doesNotMatch(
  files.page,
  /(?:value|defaultValue|title|aria-label|data-[a-z-]+)=\{checkout\.pixCopiaECola\}/
)
assert.match(files.page, /formatCountdown/)
assert.match(files.page, /Tempo restante:/)
assert.match(files.page, /max-w-60/)
assert.match(files.page, /Fechar checkout Pix e retomar depois/)
assert.match(files.page, /setPagamentos\(\(current\) =>/)

assert.match(files.page, /window\.setInterval\(\(\) => void consultarPagamento\(\), 15_000\)/)
assert.match(files.page, /checkout\.status !== 'PENDENTE'/)
assert.match(files.page, /window\.clearInterval\(timer\)/)
assert.match(files.page, /document\.hidden/)
assert.match(files.page, /visibilitychange/)
assert.match(files.page, /void consultarPagamento\(\)[\s\S]*startTimer\(\)/)
assert.match(
  files.page,
  /const consultarPagamento[\s\S]*?conciliarCobrancaPix\(checkout\.pagamentoId\)/
)
assert.match(
  files.page,
  /const retomarPagamento[\s\S]*?conciliarCobrancaPix\(pagamento\.pagamentoId\)/
)
assert.match(
  files.page,
  /const verificarPagamento[\s\S]*?conciliarCobrancaPix\(checkout\.pagamentoId\)/
)

assert.match(files.page, /pagamentosData\.find\(\(pagamento\) => pagamentoPendenteValido\(pagamento\)\)/)
assert.match(files.page, /setCheckout\(checkoutDoHistorico\(pendente\)\)/)
assert.match(files.page, /consultarCobrancaPix\(pendente\.pagamentoId\)/)
assert.match(files.page, /!expiracaoValida\(pagamento\.expiracaoEm, agora\)/)
assert.ok(files.page.includes('Esta cobrança expirou'))

assert.match(files.page, /next\.status !== 'PENDENTE'/)
assert.match(files.page, /atualizarDadosDepoisDoPagamento/)
assert.ok(files.page.includes('créditos adicionados ao saldo'))
assert.doesNotMatch(files.page, /comprarBeneficios|ativarPremium|criarStory|criarAnuncio/)

assert.doesNotMatch(files.page, /localStorage|indexedDB|console\./)
assert.doesNotMatch(
  files.page,
  /sessionStorage\.(?:getItem|setItem)\([^\n]*(?:pixCopiaECola|imagemQrCode|pagamentoId)/
)
assert.doesNotMatch(files.page, /URLSearchParams|router\.push\([^)]*(?:pagamento|pix|txid)/)
assert.doesNotMatch(files.api, /console\.|clientSecret|certificate|pixKey|usuarioId|txid/)
assert.match(files.api, /cache: 'no-store'/)
assert.match(files.api, /export class PixApiError/)
assert.match(files.api, /X-Request-Id/)
assert.ok(files.page.includes('Código de atendimento:'))

for (const message of [
  'Não foi possível iniciar a cobrança Pix agora. Tente novamente.',
  'Não foi possível consultar o pagamento agora. A cobrança foi preservada e pode ser retomada com segurança.',
  'Não foi possível carregar o QR Code agora. Tente novamente.',
  'Não foi possível cancelar a cobrança agora. Tente novamente.',
  'Existe uma cobrança Pix pendente. Retome ou cancele essa cobrança antes de iniciar outra.',
  'Este pagamento já foi confirmado e não pode ser cancelado.',
]) {
  assert.ok(files.page.includes(message), 'mensagem neutra ausente: ' + message)
}

assert.match(files.page, /checkout\.ambiente === 'HOMOLOGACAO'/)
assert.ok(
  files.page.includes(
    'Ambiente de homologação. Esta cobrança é destinada somente a testes e não deve ser paga pelo aplicativo bancário.'
  )
)

assert.match(files.api, /export async function cancelarCobrancaPix/)
assert.match(files.api, /\/minha-conta\/pagamentos\/' \+ encodeURIComponent\(pagamentoId\) \+ '\/cancelar'/)
assert.match(files.api, /headers: await mutationHeaders\(idempotencyKey\)/)
assert.doesNotMatch(files.api, /body: JSON\.stringify\(\{[^}]*pagamentoId/)

assert.match(files.page, /checkout\.status === 'PENDENTE' && checkout\.cancelavel/)
assert.ok(files.page.includes('Cancelar cobrança Pix?'))
assert.ok(
  files.page.includes(
    'O QR Code e o código Pix deixarão de ser válidos. Nenhum crédito será adicionado.'
  )
)
assert.ok(files.page.includes('Manter cobrança'))
assert.match(files.page, /const confirmarCancelamento = useCallback\(async \(\) => \{/)
assert.match(files.page, /if \(!cobranca \|\| cancelamentoInFlightRef\.current\) return/)
assert.match(files.page, /cancelamentoInFlightRef\.current = true/)
assert.match(files.page, /await cancelarCobrancaPix\(cobranca\.pagamentoId, idempotencyKey\)/)
assert.match(files.page, /setCheckout\(null\)[\s\S]*?focarPacotes\(\)/)
assert.match(files.page, /setCheckoutError\(errorState\)/)
assert.match(files.page, /cancelamentoKeyRef\.current/)
assert.ok(files.page.includes('Esta cobrança foi cancelada e permanece somente no histórico.'))
assert.match(files.page, /className="w-\[calc\(100vw-1rem\)\] sm:max-w-md"/)

const fecharInicio = files.page.indexOf('const fecharCheckout')
const fecharFim = files.page.indexOf('const retomarPagamento', fecharInicio)
const fecharCheckout = files.page.slice(fecharInicio, fecharFim)
assert.match(fecharCheckout, /setCheckout\(null\)/)
assert.doesNotMatch(fecharCheckout, /cancelarCobrancaPix/)

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

assert.match(files.frontendEnv, /NEXT_PUBLIC_EFI_PIX_ENABLED=false/)
assert.match(files.workflow, /NEXT_PUBLIC_EFI_PIX_ENABLED: "false"/)
assert.match(files.preprodCompose, /NEXT_PUBLIC_EFI_PIX_ENABLED/)
assert.match(files.preprodCompose, /ARG NEXT_PUBLIC_EFI_PIX_ENABLED/)

console.log('COMPRA_CREDITOS_PIX_V3_FRONTEND_OK')
