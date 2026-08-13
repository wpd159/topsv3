import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8')
const files = Object.fromEntries(await Promise.all(Object.entries({
  importer: '../../backend/src/main/java/br/com/topsdojob/v3/importacao/comercial/ImportadorConfiguracaoComercialFaseTres.java',
  matrix: '../../backend/src/main/java/br/com/topsdojob/v3/importacao/comercial/MatrizMapeamentoComercialImportacao.java',
  fixture: '../../backend/src/test/java/br/com/topsdojob/v3/importacao/comercial/ConfiguracaoComercialFaseTresFixture.java',
  premiumAdmin: '../src/app/(painel-admin)/admin/creditos/admin-premium-catalogo.tsx',
  packageAdmin: '../src/app/(painel-admin)/admin/components/plano-credito-manager.tsx',
  storyAdmin: '../src/app/(painel-admin)/admin/creditos/admin-story-configuracao-card.tsx',
  wizardApi: '../src/features/monetizacao-wizard/api.ts',
  wizard: '../src/features/monetizacao-wizard/monetizacao-wizard.tsx',
  premiumPublic: '../../backend/src/main/java/br/com/topsdojob/v3/application/premium/PremiumCatalogoService.java',
  premiumService: '../../backend/src/main/java/br/com/topsdojob/v3/application/admin/premium/AdminPremiumCatalogoService.java',
  storyService: '../../backend/src/main/java/br/com/topsdojob/v3/application/admin/stories/AdminStoryConfiguracaoService.java',
}).map(async ([key, path]) => [key, await read(path)])))

const canonicalBenefits = [
  'ANUNCIO_TOPO',
  'FOTOS_EXTRA_5',
  'OCULTAR_IDADE',
  'WHATSAPP_CARD',
  'CARROSSEL_FOTOS',
  'VIDEO_1',
  'STORIES',
]

for (const code of canonicalBenefits) {
  assert.match(files.matrix, new RegExp(`"${code}"`), `Missing explicit mapping for ${code}`)
}

assert.match(files.matrix, /DURACOES_PREMIUM_CANONICAS = Set\.of\(1, 7, 14, 30\)/)
assert.match(files.matrix, /MODOS_STORY_CANONICOS = Set\.of\("ANUNCIO", "MIDIA_UPLOAD"\)/)
assert.match(files.matrix, /"STORIES"[\s\S]*Decisao\.PRESERVAR_HISTORICO[\s\S]*PoliticaDuracao\.FIXA_24_HORAS/)
assert.match(files.matrix, /new LinhaPacote\("PACOTE_PRATA", "PACOTE_50"/)
assert.match(files.matrix, /new LinhaPacote\("PACOTE_OURO", "PACOTE_150"/)
assert.match(files.matrix, /new LinhaPacote\("PACOTE_DIAMANTE", "PACOTE_400"/)

for (const table of [
  'beneficio_premium',
  'beneficio_premium_opcao',
  'plano_credito',
  'story_configuracao_comercial',
]) {
  assert.match(files.importer, new RegExp(`INSERT INTO ${table}`))
}

for (const forbidden of [
  'ativacao_beneficio',
  'pagamento',
  'saldo_credito',
  'movimento_credito',
  'story_anuncio',
  'anuncio_midia',
]) {
  assert.doesNotMatch(
    files.importer,
    new RegExp(`(?:INSERT INTO|UPDATE|DELETE FROM)\\s+${forbidden}`, 'i'),
    `Importer must not write ${forbidden}`,
  )
}

assert.match(files.importer, /INSERT INTO importacao_execucao/)
assert.match(files.importer, /INSERT INTO importacao_mapeamento/)
assert.match(files.importer, /INSERT INTO importacao_pendencia/)
assert.match(files.importer, /Math\.addExact\(pacote\.quantidadeCreditosBase\(\), pacote\.bonusCreditos\(\)\)/)
assert.match(files.importer, /PACOTE_COMERCIAL_VALIDADE_NAO_SUPORTADA/)
assert.match(files.importer, /'BRL'/)
assert.match(files.importer, /Objects\.equals\(story\.duracaoHoras\(\), 24\)/)
assert.match(files.importer, /MODOS_STORY_CANONICOS/)

assert.match(files.fixture, /50,\s*5,\s*new BigDecimal\("9\.99"\)/)
assert.match(files.fixture, /MIDIA_UPLOAD/)
assert.match(files.fixture, /DESTAQUE_CONTA/)
assert.match(files.fixture, /BigDecimal\.ZERO/)

assert.match(files.premiumAdmin, /String\(opcao\.custoCreditos\)/)
assert.match(files.premiumAdmin, /custoCreditos: ''/)
assert.doesNotMatch(files.premiumAdmin, /custoCreditos:\s*[1-9][0-9]*/)
assert.match(files.packageAdmin, /String\(plano\.quantidadeCreditos\)/)
assert.match(files.packageAdmin, /plano\.valor\.toFixed\(2\)/)
assert.match(files.packageAdmin, /AdminCreditosApi\.pacotes/)
assert.match(files.storyAdmin, /String\(configuracao\.custoCreditos\)/)
assert.match(files.storyAdmin, /24 horas/)

assert.match(files.wizardApi, /data\.catalogo/)
assert.match(files.wizardApi, /opcao\.custoCreditos/)
assert.match(files.wizardApi, /planos: monetizacao\.pacotesCredito/)
assert.match(files.wizard, /fetchMonetizacaoWizardData\(slug\)/)
assert.doesNotMatch(files.wizardApi, /custoCreditos:\s*[1-9][0-9]*/)

assert.match(files.premiumPublic, /!PremiumBeneficioCodigo\.STORIES\.equals/)
assert.match(files.premiumService, /DURACOES_PERMITIDAS = Set\.of\(1, 7, 14, 30\)/)
assert.match(files.premiumService, /PremiumBeneficioCodigo\.STORIES/)
assert.match(files.storyService, /DURACAO_HORAS = 24/)
assert.match(files.storyService, /StoryConfiguracaoComercialEntity\.SINGLETON_ID/)

console.log('IMPORTACAO_CONFIGURACAO_COMERCIAL_CONTRATOS_OK')
