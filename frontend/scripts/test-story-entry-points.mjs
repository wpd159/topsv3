import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8')
}

const [page, card, dialog, selector, entryState, premiumApi, storyApi, genericWizard] = await Promise.all([
  source('../src/app/(private-routes)/meus-anuncios/page.tsx'),
  source('../src/components/anuncios/meu-anuncio-card.tsx'),
  source('../src/components/stories/story-create-dialog.tsx'),
  source('../src/components/stories/story-anuncio-selector-dialog.tsx'),
  source('../src/components/stories/story-entry-state.ts'),
  source('../src/features/monetizacao-wizard/api.ts'),
  source('../src/lib/meus-anuncios-api.ts'),
  source('../src/features/monetizacao-wizard/monetizacao-wizard.tsx'),
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

function matches(value, pattern) {
  assert.match(value, pattern)
}

function excludes(value, pattern) {
  assert.doesNotMatch(value, pattern)
}

check('1. botao global antecede Publicar novo anuncio no mesmo container', () => {
  matches(page, /flex flex-col justify-end[\s\S]*Publicar nos Stories[\s\S]*href="\/anunciar\/wizard"[\s\S]*Publicar novo anúncio/)
})

check('2. botao global fica no corpo de acoes e fora do cabecalho do painel', () => {
  matches(page, /<PainelShell[\s\S]*<div className="space-y-6">[\s\S]*Publicar nos Stories/)
  excludes(page, /title="Publicar nos Stories"/)
})

check('3. conta sem anuncio mantem botao com motivo acessivel', () => {
  matches(page, /anuncios\.length === 0/)
  matches(page, /disabled=\{storyGlobalDisabled\}/)
  excludes(page, /aria-disabled=\{storyGlobalDisabled\}/)
  matches(page, /if \(loading \|\| erro \|\| anuncios\.length === 0\) return/)
  matches(page, /Publique um anúncio antes de usar os Stories\./)
})

check('4. conta com um anuncio pula o seletor', () => {
  matches(page, /anuncios\.length === 1[\s\S]*abrirStory\(anuncios\[0\]\.id, trigger\)[\s\S]*return/)
})

check('5. conta com varios anuncios abre o seletor', () => {
  matches(page, /setStorySelectorOpen\(true\)/)
  matches(page, /<StoryAnuncioSelectorDialog/)
})

check('6. seletor recebe somente a lista da sessao', () => {
  matches(page, /anuncios=\{anuncios\}/)
  matches(page, /listarMeusAnuncios\(\)/)
  excludes(selector, /usuarioId|email/i)
})

check('7. selecao abre o dialogo canonico', () => {
  matches(page, /onSelect=\{\(anuncio\) => \{[\s\S]*setStoryTargetId\(anuncio\.id\)[\s\S]*setStoryDialogOpen\(true\)/)
  assert.equal((page.match(/<StoryCreateDialog/g) || []).length, 1)
})

check('8. botao do card fica abaixo de Monetizar', () => {
  matches(card, /href=\{`\/meus-anuncios\/\$\{encodeURIComponent\(anuncio\.slug\)\}\/monetizar`\}[\s\S]*Monetizar[\s\S]*storyEntry\.buttonLabel/)
})

check('9. botao do card fica acima das acoes de ciclo de vida', () => {
  matches(card, /storyEntry\.buttonLabel[\s\S]*<MeuAnuncioAcoesCicloVida/)
})

check('10. botao do card ocupa largura total', () => {
  matches(card, /col-span-2 min-w-0[\s\S]*min-h-10 w-full/)
})

check('11. botao do card fica sempre na area principal de acoes', () => {
  matches(card, /mt-auto grid grid-cols-2[\s\S]*storyEntry\.buttonLabel/)
  excludes(card, /<details[\s\S]*storyEntry\.buttonLabel/)
})

check('12. ausencia de beneficio nao remove o botao', () => {
  matches(entryState, /kind: 'NEEDS_ACTIVATION'[\s\S]*buttonLabel: 'Publicar nos Stories'/)
  matches(card, /const storyEntry = getStoryEntryState\(anuncio\)/)
  matches(card, /storyEntry\.buttonLabel/)
})

check('13. ausencia de beneficio carrega fluxo proprio de Stories', () => {
  matches(dialog, /setStep\('CONFIGURAR_CONTEUDO'\)/)
  matches(dialog, /setStep\('REVISAR'\)/)
  matches(dialog, /const slug = anuncio\.slug[\s\S]*consultarMeuAnuncioStoryOferta\(slug\)/)
  excludes(dialog, /fetchMinhaMonetizacao\(anuncio\.slug\)/)
})

check('14. fluxo de Stories nao redireciona ao wizard generico', () => {
  excludes(dialog, /\/monetizar|MonetizacaoWizard|fetchMonetizacaoWizardData/)
  matches(dialog, /comprarBeneficios\(/)
})

check('15. direito disponivel preserva ANUNCIO e MIDIA_UPLOAD', () => {
  matches(dialog, /value="ANUNCIO"/)
  matches(dialog, /value="MIDIA_UPLOAD"/)
})

check('16. Story ativo exibe acao de gerenciamento', () => {
  matches(entryState, /kind: 'ACTIVE'[\s\S]*buttonLabel: 'Gerenciar Story'/)
  matches(dialog, /activeStory[\s\S]*Gerenciar Story/)
})

check('17. Story ativo nao abre nova criacao', () => {
  matches(dialog, /\{activeStory \? \([\s\S]*\) : entry\.kind === 'UNAVAILABLE'/)
  matches(dialog, /result \? 'Seu Story foi publicado\.' : 'Story ativo'/)
})

check('18. anuncio inelegivel mantem botao desabilitado', () => {
  matches(entryState, /kind: 'UNAVAILABLE'[\s\S]*disabled: true/)
  matches(card, /disabled=\{storyEntry\.disabled\}/)
  excludes(card, /aria-disabled=\{storyEntry\.disabled\}/)
  matches(card, /if \(!storyEntry\.disabled\) onStoryOpen\(anuncio\.id, event\.currentTarget\)/)
})

check('19. motivo indisponivel e acessivel', () => {
  matches(card, /aria-describedby=\{storyEntry\.reason \? storyUnavailableId/)
  matches(card, /id=\{storyUnavailableId\}[\s\S]*storyEntry\.reason/)
})

check('20. gatilho antigo de baixa visibilidade foi removido', () => {
  excludes(card, /setStoryDialogOpen|<StoryCreateDialog|podePublicarStory/)
  excludes(card, /aria-label="Stories"[\s\S]*onClick=\{\(\) => setStoryDialogOpen/)
})

check('21. existem somente os dois pontos de entrada pedidos', () => {
  assert.equal((page.match(/Publicar nos Stories/g) || []).length, 1)
  assert.equal((card.match(/storyEntry\.buttonLabel/g) || []).length, 1)
  assert.equal((card.match(/<StoryCreateDialog/g) || []).length, 0)
})

check('22. topo e card usam o mesmo StoryCreateDialog', () => {
  matches(page, /<StoryCreateDialog[\s\S]*anuncio=\{storyTarget\}/)
  matches(card, /onStoryOpen\(anuncio\.id, event\.currentTarget\)/)
})

check('23. card passa diretamente o anuncio selecionado', () => {
  matches(card, /onStoryOpen: \(anuncioId: string, trigger: HTMLButtonElement\) => void/)
  matches(card, /onStoryOpen\(anuncio\.id, event\.currentTarget\)/)
})

check('24. topo seleciona o anuncio antes de abrir', () => {
  matches(page, /setStoryTargetId\(anuncioId\)[\s\S]*setStoryDialogOpen\(true\)/)
  matches(page, /const storyTarget = anuncios\.find/)
})

check('25. mobile empilha os botoes globais', () => {
  matches(page, /flex flex-col justify-end gap-2 sm:flex-row/)
  assert.ok((page.match(/sm:w-auto/g) || []).length >= 2)
})

check('26. componentes moveis limitam largura sem scroll lateral', () => {
  matches(selector, /w-\[calc\(100vw-1rem\)\]/)
  matches(dialog, /w-\[calc\(100vw-1rem\)\]/)
  excludes(page + card + selector + dialog, /w-\[100vw\]|min-w-\[[4-9][0-9]{2}px\]/)
})

check('27. card preserva ordem em 390 px', () => {
  matches(card, /Monetizar[\s\S]*storyEntry\.buttonLabel[\s\S]*MeuAnuncioAcoesCicloVida/)
  matches(card, /col-span-2/)
})

check('28. controles usam semantica de teclado nativa', () => {
  matches(page, /<button[\s\S]*Publicar nos Stories/)
  matches(selector, /type="radio"[\s\S]*name="story-anuncio"/)
  matches(dialog, /type="radio"[\s\S]*name="story-mode"/)
})

check('29. foco inicial e retorno ficam sob o Dialog canonico', () => {
  matches(selector, /onOpenAutoFocus=\{\(event\)/)
  matches(dialog, /onOpenAutoFocus=\{\(event\)/)
  matches(selector + dialog, /requestAnimationFrame/)
})

check('30. nao ha controle interativo aninhado', () => {
  excludes(page + card + selector + dialog, /<Link\b[^>]*>(?:(?!<\/Link>)[\s\S])*<button/)
  excludes(page + card + selector + dialog, /<button\b[^>]*>(?:(?!<\/button>)[\s\S])*<Link/)
})

check('31. upload e contrato dos modos permanecem canonicos', () => {
  matches(dialog, /publicarMeuAnuncioStory\([\s\S]*anuncio\.slug[\s\S]*mode/)
  matches(storyApi, /form\.append\('modoConteudo', modoConteudo\)/)
  matches(storyApi, /if \(arquivo\) form\.append\('arquivo', arquivo\)/)
})

check('32. status e expiracao do Story permanecem no card e dialogo', () => {
  matches(card, /Story ativo[\s\S]*Ativo até[\s\S]*fimEm/)
  matches(dialog, /Status:[\s\S]*Início:[\s\S]*Expira em:/)
})

check('33. frontend nao calcula a vigencia do Story', () => {
  excludes(entryState + page + card + dialog, /setHours|setDate|plusDays|24 \* 60|duracaoDias \* 24 \* 60/)
  matches(entryState, /formatStoryDate\(anuncio\.storyAtivo\.fimEm\)/)
})

check('34. wizard generico permanece sem Stories', () => {
  excludes(genericWizard, /beneficioCodigo:\s*'STORIES'|codigo:\s*'STORIES'/)
  matches(premiumApi, /\/minha-conta\/monetizacao\/compras/)
  excludes(dialog, /monetizacao-wizard\/monetizacao-wizard/)
})

check('35. titulo do dialogo preserva o id acessivel do Radix', () => {
  matches(dialog, /<DialogTitle>\{title\}<\/DialogTitle>/)
  excludes(dialog, /useId|<DialogTitle\s+id=/)
})

check('36. seletor grande limita o dialogo e rola o conteudo internamente', () => {
  matches(selector, /className="flex max-h-\[calc\(100dvh-1rem\)\][^"]*flex-col overflow-hidden/)
  matches(selector, /className="min-h-0 flex-1 overflow-y-auto px-4 py-5 sm:px-6"/)
})

check('37. dialogo devolve foco ao gatilho global ou do card', () => {
  matches(page, /storyReturnFocusRef = useRef<HTMLElement \| null>\(null\)/)
  matches(page, /returnFocusTo=\{storyReturnFocusRef\.current\}/)
  matches(dialog, /onCloseAutoFocus=\{\(event\)[\s\S]*returnFocusTo\?\.isConnected[\s\S]*returnFocusTo\.focus\(\)/)
})

check('38. seletor devolve foco ao cancelar sem roubar foco da continuacao', () => {
  matches(selector, /continuingRef = useRef\(false\)/)
  matches(selector, /onCloseAutoFocus=\{\(event\)[\s\S]*if \(continuingRef\.current\) return[\s\S]*returnFocusTo\.focus\(\)/)
  matches(selector, /continuingRef\.current = true[\s\S]*onSelect\(selected\)/)
})

assert.equal(checks, 38)
console.log(`STORY_ENTRY_POINTS_CHECKS=${checks}`)
console.log('STORY_ENTRY_POINTS_RESULT=OK')
