import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8')
}

const [
  dialog,
  entry,
  api,
  backendOffer,
  backendRight,
  activationService,
  storyService,
  controller,
  premiumService,
  premiumCatalog,
  configService,
  openapi,
  migration,
] = await Promise.all([
  source('../src/components/stories/story-create-dialog.tsx'),
  source('../src/components/stories/story-entry-state.ts'),
  source('../src/lib/meus-anuncios-api.ts'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/MeuAnuncioStoryOfertaService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/dto/MeuAnuncioStoryDireitoDto.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/MeuAnuncioStoryAtivacaoService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/MeuAnuncioStoryService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/web/publico/anunciante/MeusAnunciosController.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/premium/MinhaContaPremiumService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/premium/PremiumCatalogoService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/admin/stories/AdminStoryConfiguracaoService.java'),
  source('../../contracts/openapi/topsdojob-v3-local.yaml'),
  source('../../backend/src/main/resources/db/migration/V049__stories_configuracao_comercial.sql'),
])

let checks = 0
function check(name, callback) {
  try {
    callback()
    checks += 1
  } catch (error) {
    error.message = `${name}: ${error.message}`
    throw error
  }
}
function matches(value, pattern) { assert.match(value, pattern) }
function excludes(value, pattern) { assert.doesNotMatch(value, pattern) }

check('1. editor abre antes da oferta', () => matches(entry, /kind: 'NEEDS_ACTIVATION'[\s\S]*disabled: false/))
check('2. escolha de conteudo antecede a oferta', () => matches(dialog, /step === 'ESCOLHER_CONTEUDO'/))
check('3. modo ANUNCIO permanece disponivel', () => matches(dialog, /value="ANUNCIO"[\s\S]*setStep\('CONFIGURAR_CONTEUDO'\)/))
check('4. modo MIDIA_UPLOAD permanece disponivel', () => matches(dialog, /value="MIDIA_UPLOAD"[\s\S]*setStep\('CONFIGURAR_CONTEUDO'\)/))
check('5. revisao antecede consulta comercial', () => matches(dialog, /step === 'REVISAR'[\s\S]*setStep\('VERIFICAR_DIREITO_E_OFERTA'\); void loadOffer\(\)/))

check('6. Story ativo tem prioridade e abre gerenciamento', () => {
  matches(backendOffer, /if \(storyAtivo != null\)[\s\S]*"STORY_ATIVO"/)
  matches(dialog, /activeStory[\s\S]*Gerenciar Story/)
})

check('7. direito adquirido tem prioridade sobre configuracao', () => {
  matches(backendOffer, /AtivacaoBeneficioEntity direito = localizarDireitoDisponivel[\s\S]*if \(direito != null\)[\s\S]*configuracaoRepository/)
  matches(dialog, /offer\?\.estado === 'DIREITO_DISPONIVEL'/)
})

check('8. direito adquirido nao consulta saldo', () => {
  const beforeBalance = backendOffer.slice(0, backendOffer.indexOf('int saldo = ledgerService.consultarSaldo'))
  matches(beforeBalance, /"DIREITO_DISPONIVEL"/)
  excludes(beforeBalance.slice(beforeBalance.indexOf('AtivacaoBeneficioEntity direito')), /consultarSaldo/)
})

check('9. estado legado e traduzido sem moderacao humana', () => {
  matches(backendOffer, /StatusAtivacaoBeneficio\.AGUARDANDO_MODERACAO/)
  matches(backendOffer, /"DISPONIVEL_PARA_PUBLICAR"/)
  excludes(dialog, /Aguardando modera..o/)
})

check('10. configuracao ausente fecha apenas novas ativacoes', () => {
  matches(backendOffer, /if \(configuracao == null\)[\s\S]*indisponivel\(false, null\)/)
})

check('11. configuracao inativa fecha apenas novas ativacoes', () => {
  matches(backendOffer, /!Boolean\.TRUE\.equals\(configuracao\.getAtivo\(\)\)[\s\S]*indisponivel\(true/)
})

check('12. nova oferta usa custo e versao dedicados', () => {
  matches(backendOffer, /int custo = configuracao\.getCustoCreditos\(\)/)
  matches(backendOffer, /configuracao\.getVersao\(\)/)
  excludes(backendOffer, /opcaoId|catalogoService/)
})

check('13. saldo e deficit sao calculados no backend', () => {
  matches(backendOffer, /int saldo = ledgerService\.consultarSaldo\(usuarioId\)/)
  matches(backendOffer, /Math\.max\(0, saldo - custo\)/)
  matches(backendOffer, /Math\.max\(0, custo - saldo\)/)
})

check('14. oferta possui uma unica duracao fixa', () => {
  matches(backendOffer, /DURACAO_HORAS/)
  matches(configService, /DURACAO_HORAS = 24/)
  excludes(backendOffer + backendRight, /opcaoId|duracaoDias/)
})

check('15. frontend valida duracao fixa vinda do backend', () => {
  matches(api, /duracaoHoras: 24/)
  matches(api, /payload\.duracaoHoras !== 24/)
})

check('16. frontend mostra custo saldo projetado e deficit', () => {
  matches(dialog, /offer\.custoCreditos/)
  matches(dialog, /offer\.saldoAtual/)
  matches(dialog, /offer\.saldoProjetado/)
  matches(dialog, /offer\.deficit/)
})

check('17. frontend mostra duracao de 24 horas sem opcoes', () => {
  matches(dialog, /Story por 24 horas/)
  excludes(dialog, /selectedOption|opcoes\.map|duracaoDias/)
})

check('18. ativacao usa endpoint comercial dedicado', () => {
  matches(api, /ativarMeuAnuncioStory/)
  matches(api, /stories\/ativacoes/)
  matches(api, /custoCreditosEsperado, versaoConfiguracao/)
  excludes(dialog, /comprarBeneficios\(/)
})

check('19. compra ocorre somente por clique explicito', () => {
  matches(dialog, /onClick=\{\(\) => void activateAndPublish\(\)\}/)
})

check('20. compra e publicacao possuem chaves distintas', () => {
  matches(dialog, /purchaseIdempotencyKeyRef/)
  matches(dialog, /publishIdempotencyKeyRef/)
  excludes(dialog, /publicarMeuAnuncioStory\([\s\S]{0,220}purchaseIdempotencyKeyRef/)
})

check('21. duplo clique e bloqueado', () => {
  matches(dialog, /const busy = loadingOffer \|\| purchasing \|\| publishing/)
  matches(dialog, /disabled=\{busy\}/)
})

check('22. saldo insuficiente nao ativa nem envia midia', () => {
  matches(dialog, /offer\.deficit !== 0/)
  matches(dialog, /Comprar cr.*ditos/)
})

check('23. compra de creditos usa rota interna segura', () => {
  matches(dialog, /href="\/creditos"/)
  matches(dialog, /target="_blank" rel="noopener noreferrer"/)
})

check('24. retorno atualiza oferta sem polling', () => {
  matches(dialog, /window\.addEventListener\('focus', refreshOnReturn\)/)
  matches(dialog, /document\.addEventListener\('visibilitychange', refreshOnReturn\)/)
  excludes(dialog, /setInterval|setTimeout\([^,]+,\s*[0-9]{3,}/)
})

check('25. retorno de creditos nao publica automaticamente', () => {
  const refresh = dialog.slice(dialog.indexOf('const refreshOnReturn'), dialog.indexOf("window.addEventListener('focus'"))
  excludes(refresh, /publishStory|activateAndPublish/)
})

check('26. arquivo fica apenas em memoria', () => excludes(dialog, /localStorage|sessionStorage|indexedDB|document\.cookie/))
check('27. object URL e revogada', () => matches(dialog, /URL\.revokeObjectURL\(url\)/))

check('28. respostas obsoletas sao descartadas', () => {
  matches(dialog, /offerRequestSequenceRef/)
  matches(dialog, /offerRequestSequenceRef\.current !== sequence/)
})

check('29. indisponibilidade comercial tem mensagem propria', () => {
  matches(dialog, /Stories est. temporariamente indispon.vel para novas ativa..es/)
})

check('30. erro tecnico tem retry e requestId', () => {
  matches(dialog, /N.o foi poss.vel carregar a oferta de Stories/)
  matches(dialog, /Tentar novamente/)
  matches(dialog, /requestIdSuffix/)
})

check('31. conflito atualiza a oferta em vez de informar erro de carregamento', () => {
  matches(dialog, /cause\.status === 409/)
  matches(dialog, /A condi..o de Stories mudou/)
  matches(dialog, /await loadOffer\(\)/)
})

check('32. custo zero nao cria debito ficticio', () => {
  matches(activationService, /if \(custo > 0\)[\s\S]*ledgerService\.registrar/)
  matches(activationService, /if \(custo == 0\)[\s\S]*saldoAtual, saldoAtual/)
})

check('33. snapshot protege mudanca de custo e versao', () => {
  matches(activationService, /configuracao\.getVersao\(\), request\.versaoConfiguracao\(\)/)
  matches(activationService, /configuracao\.getCustoCreditos\(\), request\.custoCreditosEsperado\(\)/)
})

check('34. ativacao serializa saldo e configuracao', () => {
  matches(activationService, /bloquearEConsultarSaldo\(usuarioId\)/)
  matches(activationService, /configuracaoRepository\.findForUpdate\(\)/)
})

check('35. retry de compra e idempotente e valida intencao', () => {
  matches(activationService, /findByIdempotencyKey\(chave\)/)
  matches(activationService, /resultadoRepetido/)
  matches(activationService, /Idempotency-Key reutilizada com outra intencao/)
})

check('36. compra cria direito sem inicio de vigencia', () => {
  matches(activationService, /criarCompraAguardandoModeracao/)
  excludes(activationService, /iniciarVigenciaExclusiva/)
})

check('37. publicacao inicia exatamente 24 horas', () => {
  matches(storyService, /Duration\.ofHours\(24\)/)
  matches(storyService, /fimEm = publicadoEm\.plus\(selecionada\.duracao\(\)\)/)
  matches(storyService, /iniciarVigenciaExclusiva\(publicadoEm, fimEm\)/)
})

check('38. ativacao ja iniciada preserva fim original', () => {
  matches(storyService, /getStatus\(\) == StatusAtivacaoBeneficio\.ATIVA[\s\S]*return ativacao\.getFimEm\(\)/)
})

check('39. falha antes da publicacao nao inicia prazo', () => {
  const publication = storyService.slice(
    storyService.indexOf('public MeuAnuncioStoryDto publicar'),
    storyService.indexOf('private AtivacaoStories ativacaoStories'),
  )
  matches(publication, /validarArquivos[\s\S]*processarMidia[\s\S]*iniciarVigencia/)
})

check('40. publicacao cria Story diretamente publicado', () => {
  matches(storyService, /StoryAnuncioEntity\.criarAutogestao/)
  excludes(storyService, /AdminModeracao|remeterRevisao|fila.*moderacao/i)
})

check('41. oferta e autenticada por sessao e ownership', () => {
  matches(backendOffer, /anuncioDoUsuario\(slug, authentication\)/)
  matches(backendOffer, /usuarioAutenticado\(authentication\)/)
  excludes(api, /usuarioId|email/)
})

check('42. resposta da oferta e no-store', () => {
  matches(controller, /consultarOfertaStory[\s\S]*CacheControl\.noStore\(\)/)
  matches(openapi, /Cache-Control:[\s\S]*const: no-store/)
})

check('43. wizard Premium generico rejeita Stories', () => {
  matches(premiumCatalog, /!PremiumBeneficioCodigo\.STORIES\.equals/)
  matches(premiumService, /Stories utiliza o fluxo proprio de publicacao/)
})

check('44. OpenAPI documenta ativacao dedicada sem opcao generica', () => {
  const activationPath = openapi.slice(
    openapi.indexOf('/api/public/minha-conta/anuncios/{slug}/stories/ativacoes:'),
    openapi.indexOf('/api/public/minha-conta/anuncios/{slug}/midias/lote:'),
  )
  matches(activationPath, /MeuAnuncioStoryAtivacaoRequest/)
  excludes(activationPath, /opcaoId|duracaoDias/)
})

check('45. migration nao persiste duracao nem ativa oferta', () => {
  excludes(migration, /duracao_(?:horas|dias)|\bINSERT\b/i)
})

assert.equal(checks, 45)
console.log(`STORY_CHECKOUT_FLOW_CHECKS=${checks}`)
console.log('STORY_CHECKOUT_FLOW_RESULT=OK')
