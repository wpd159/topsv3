import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8')
}

const [dialog, entry, api, premiumApi, backendOffer, backendOfferOption, premiumService, catalogService, storyService, controller, openapi, migration] = await Promise.all([
  source('../src/components/stories/story-create-dialog.tsx'),
  source('../src/components/stories/story-entry-state.ts'),
  source('../src/lib/meus-anuncios-api.ts'),
  source('../src/features/monetizacao-wizard/api.ts'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/MeuAnuncioStoryOfertaService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/dto/MeuAnuncioStoryOfertaOpcaoDto.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/premium/MinhaContaPremiumService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/premium/PremiumCatalogoService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/MeuAnuncioStoryService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/web/publico/anunciante/MeusAnunciosController.java'),
  source('../../contracts/openapi/topsdojob-v3-local.yaml'),
  source('../../backend/src/main/resources/db/migration/V048__stories_autogestao_modos_conteudo.sql'),
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

check('1. editor abre sem beneficio ativo', () => matches(entry, /kind: 'NEEDS_ACTIVATION'[\s\S]*disabled: false/))
check('2. ativacao nao antecede escolha', () => matches(dialog, /step === 'ESCOLHER_CONTEUDO'/))
check('3. ANUNCIO antecede oferta', () => matches(dialog, /value="ANUNCIO"[\s\S]*setStep\('CONFIGURAR_CONTEUDO'\)/))
check('4. MIDIA_UPLOAD antecede oferta', () => matches(dialog, /value="MIDIA_UPLOAD"[\s\S]*setStep\('CONFIGURAR_CONTEUDO'\)/))
check('5. revisao antecede oferta', () => matches(dialog, /step === 'REVISAR'[\s\S]*setStep\('VERIFICAR_DIREITO_E_OFERTA'\); void loadOffer\(\)/))
check('6. direito pula compra', () => matches(dialog, /offer\?\.estado === 'DIREITO_DISPONIVEL'[\s\S]*Publicar Story/))
check('7. AGUARDANDO_MODERACAO e aceito', () => matches(backendOffer, /StatusAtivacaoBeneficio\.AGUARDANDO_MODERACAO/))
check('8. ativacao consumida e excluida', () => matches(backendOffer, /!consumidas\.contains\(item\.getId\(\)\)/))
check('9. Story ativo abre gerenciamento', () => matches(dialog, /activeStory[\s\S]*Gerenciar Story/))
check('10. catalogo carrega somente na etapa final', () => matches(dialog, /setStep\('VERIFICAR_DIREITO_E_OFERTA'\); void loadOffer\(\)/))
check('11. opcao unica e selecionada', () => matches(dialog, /if \(next\.opcoes\.length === 1\) return next\.opcoes\[0\]\.opcaoId/))
check('12. varias opcoes nao escolhem silenciosamente', () => matches(dialog, /return next\.opcoes\.some[\s\S]*\? current : null/))
check('13. custo vem da resposta', () => matches(dialog, /selectedOption\.custoCreditos/))
check('14. duracao vem da resposta', () => matches(dialog, /option\.duracaoDias/))
check('15. saldo vem da resposta', () => matches(dialog, /selectedOption\.saldoAtual/))
check('16. nao ha preco ou duracao fixa', () => excludes(dialog, /custoCreditos:\s*\d|duracaoDias:\s*\d/))
check('17. abrir dialogo nao compra', () => excludes(dialog.slice(0, dialog.indexOf('async function activateAndPublish')), /comprarBeneficios\(/))
check('18. compra exige clique explicito', () => matches(dialog, /onClick=\{\(\) => void activateAndPublish\(selectedOption\)\}/))
check('19. compra bloqueia duplo clique', () => matches(dialog, /setPurchasing\(true\)[\s\S]*disabled=\{busy\}/))
check('20. chaves de compra e publicacao sao distintas', () => {
  matches(dialog, /purchaseIdempotencyKeyRef/)
  matches(dialog, /publishIdempotencyKeyRef/)
  excludes(dialog, /publicarMeuAnuncioStory\([\s\S]{0,220}purchaseIdempotencyKeyRef/)
})
check('21. compra concluida avanca para publicacao', () => matches(dialog, /setPurchaseCompleted\(true\)[\s\S]*await publishStory\(true\)/))
check('22. falha de publicacao nao compra novamente', () => matches(dialog, /Nenhuma nova cobrança será feita/))
check('23. retry usa direito existente', () => {
  matches(dialog, /purchaseCompleted \|\| offer\?\.estado === 'DIREITO_DISPONIVEL'/)
  matches(dialog, /publishStory\(purchaseCompleted\)/)
})
check('24. saldo insuficiente nao compra', () => matches(dialog, /if \(option\.creditosFaltantes > 0\) return/))
check('25. saldo insuficiente nao envia midia', () => matches(dialog, /selectedOption\.creditosFaltantes > 0[\s\S]*Comprar créditos/))
check('26. saldo e deficit sao retornados pelo backend', () => {
  matches(backendOfferOption, /saldoAtual[\s\S]*saldoAposCompra[\s\S]*creditosFaltantes/)
  matches(backendOffer, /Math\.max\(0, saldo - item\.custoCreditos\(\)\)[\s\S]*Math\.max\(0, item\.custoCreditos\(\) - saldo\)/)
})
check('27. compra de creditos usa rota interna', () => matches(dialog, /href="\/creditos"/))
check('28. compra abre nova aba segura', () => matches(dialog, /target="_blank" rel="noopener noreferrer"/))
check('29. retorno atualiza saldo uma vez', () => matches(dialog, /setAwaitingCredits\(false\)[\s\S]*void loadOffer\(\)/))
check('30. retorno nao publica automaticamente', () => excludes(dialog.slice(dialog.indexOf('const refreshOnReturn'), dialog.indexOf("window.addEventListener('focus'")), /publishStory/))
check('31. arquivo nao usa storage do navegador', () => excludes(dialog, /localStorage|sessionStorage|indexedDB|document\.cookie/))
check('32. erro de catalogo possui retry e requestId', () => {
  matches(dialog, /Não foi possível carregar as opções de Stories/)
  matches(dialog, /Tentar novamente/)
  matches(dialog, /requestIdSuffix/)
})
check('33. direito e resolvido antes do catalogo', () => matches(backendOffer, /direito != null[\s\S]*catalogoService\.catalogoAtivo/))
check('34. preco alterado exige nova confirmacao', () => {
  matches(premiumService, /PremiumOfertaAtualizadaException/)
  matches(dialog, /PREMIUM_OFERTA_ATUALIZADA/)
})
check('35. object URL e revogada', () => matches(dialog, /return \(\) => URL\.revokeObjectURL\(url\)/))
check('36. Story ativo nao oferece compra', () => matches(dialog, /activeStory \? \([\s\S]*\) : entry\.kind === 'UNAVAILABLE'/))
check('37. mobile possui 100dvh e sem largura fixa', () => {
  matches(dialog, /max-h-\[100dvh\]/)
  matches(dialog, /w-\[calc\(100vw-1rem\)\]/)
})
check('38. etapas e erros sao acessiveis', () => {
  matches(dialog, /role="status"/)
  matches(dialog, /role="alert"/)
  matches(dialog, /onEscapeKeyDown/)
})
check('39. nao usa wizard generico', () => excludes(dialog, /fetchMonetizacaoWizardData|MonetizacaoWizard/))
check('40. administrador e fonte de custo e duracao', () => {
  matches(backendOffer, /catalogoService\.catalogoAtivo\(\)/)
  matches(catalogService, /BeneficioPremiumOpcaoEntity/)
  matches(openapi, /stories\/oferta:/)
  matches(migration, /modo_conteudo/)
  matches(premiumApi, /opcaoId\?: string/)
  matches(api, /consultarMeuAnuncioStoryOferta/)
})
check('41. saldo so e consultado para nova oferta', () => {
  matches(backendOffer, /direito != null[\s\S]*int saldo = ledgerService\.consultarSaldo/)
  excludes(backendOffer.slice(backendOffer.indexOf('var storyAtivo'), backendOffer.indexOf('int saldo = ledgerService.consultarSaldo')), /consultarSaldo/)
})
check('42. resposta da oferta impede cache compartilhado', () => {
  matches(controller, /cacheControl\(CacheControl\.noStore\(\)\)/)
  matches(openapi, /Cache-Control:[\s\S]*const: no-store/)
})
check('43. ativacao vigente preserva o fim contratado', () => {
  matches(storyService, /getStatus\(\) == StatusAtivacaoBeneficio\.ATIVA[\s\S]*return ativacao\.getFimEm\(\)/)
})
check('44. opcao gratuita de Stories nao cria debito', () => {
  matches(premiumService, /custoCreditos == 0 && !PremiumBeneficioCodigo\.STORIES/)
  matches(premiumService, /getCustoCreditos\(\) > 0[\s\S]*ledgerService\.registrar/)
  matches(openapi, /MeuAnuncioStoryOfertaOpcao:[\s\S]*custoCreditos: \{ type: integer, minimum: 0 \}/)
})
check('45. CTA gratuito nao direciona para compra de creditos', () => matches(dialog, /custoCreditos === 0 \? 'Ativar gratuitamente e publicar'/))
check('46. resposta antiga de oferta nao vaza entre fechamento ou troca de anuncio', () => {
  matches(dialog, /const offerRequestSequenceRef = useRef\(0\)/)
  matches(dialog, /const slug = anuncio\.slug[\s\S]*const sequence = \+\+offerRequestSequenceRef\.current/)
  matches(dialog, /if \(offerRequestSequenceRef\.current !== sequence\) return null/)
  matches(dialog, /offerRequestSequenceRef\.current \+= 1[\s\S]*offerRequestRef\.current = null/)
})

assert.equal(checks, 46)
console.log(`STORY_CHECKOUT_FLOW_CHECKS=${checks}`)
console.log('STORY_CHECKOUT_FLOW_RESULT=OK')
