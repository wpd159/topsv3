import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(
  new URL('../src/lib/meu-anuncio-beneficios.js', import.meta.url),
  'utf8',
)
const moduleUrl = `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const { apresentarBeneficioPremium, formatarDataBeneficio } = await import(moduleUrl)

assert.deepEqual(apresentarBeneficioPremium({ status: 'AGUARDANDO_MODERACAO' }), {
  situacao: 'Aguardando aprovação da moderação',
  complemento: 'O prazo ainda não começou.',
})

assert.deepEqual(apresentarBeneficioPremium({
  status: 'DISPONIVEL_PARA_PUBLICAR',
  motivoEspera: 'AGUARDANDO_PUBLICACAO_STORY',
}), {
  situacao: 'Disponível para publicar Story',
  complemento: 'O prazo começa quando o Story for publicado.',
})

assert.equal(
  formatarDataBeneficio('2026-08-09T18:30:00Z'),
  '09/08/2026 às 15:30',
)
assert.deepEqual(apresentarBeneficioPremium({
  status: 'ATIVO',
  fimEm: '2026-08-09T18:30:00Z',
  diasRestantes: 6,
}), {
  situacao: 'Ativo até 09/08/2026 às 15:30',
  complemento: 'Restam 6 dias.',
})
assert.deepEqual(apresentarBeneficioPremium({
  status: 'EXPIRADO',
  fimEm: '2026-07-27T15:00:00Z',
}), {
  situacao: 'Expirou em 27/07/2026 às 12:00',
  complemento: null,
})
assert.doesNotMatch(source, /Date\.now|setDate|86_?400|plusDays/)

const card = await readFile(
  new URL('../src/components/anuncios/meu-anuncio-card.tsx', import.meta.url),
  'utf8',
)
assert.match(card, /apresentarBeneficioPremium/)
assert.match(card, /beneficiosPremium/)
assert.match(card, /break-words/)

const api = await readFile(
  new URL('../src/lib/meus-anuncios-api.ts', import.meta.url),
  'utf8',
)
assert.match(api, /if \(payload == null\) return EMPTY_BENEFICIOS_PREMIUM\.slice\(\)/)
for (const field of ['inicioEm', 'fimEm', 'duracaoDias', 'diasRestantes', 'motivoEspera', 'origem']) {
  assert.match(api, new RegExp(`${field}: raw\\.${field} \\?\\? null`))
}

console.log('Meus anúncios: espera, vigência e término do benefício validados sem relógio paralelo.')
