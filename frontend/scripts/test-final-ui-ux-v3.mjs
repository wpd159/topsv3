import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const root = new URL('../src/', import.meta.url)
const read = (path) => readFile(new URL(path, root), 'utf8')

const [
  filters,
  users,
  ads,
  finance,
  stories,
  staff,
  publicCard,
] = await Promise.all([
  read('components/anuncios/barra-localizacao.tsx'),
  read('features/admin-usuarios/admin-usuarios-list.tsx'),
  read('features/admin-anuncios/admin-anuncios-list.tsx'),
  read('app/(painel-admin)/admin/components/financeiro/financeiro-table.tsx'),
  read('components/stories/admin-stories-management.tsx'),
  read('app/(painel-admin)/admin/components/staff-table.tsx'),
  read('components/anuncios/anuncio-card.tsx'),
])

assert.match(filters, /useState\(false\)/, 'Os refinadores mobile devem iniciar recolhidos.')
assert.match(filters, /aria-expanded=\{mobileRefinadoresOpen\}/)
assert.match(filters, /aria-controls="barra-localizacao-refinadores"/)
assert.match(filters, /refinadoresAtivosCount/)
assert.match(filters, /Resumo dos filtros ativos/)
assert.match(filters, /Limpar filtros/)
assert.match(filters, /mobileRefinadoresOpen \? "block" : "hidden"/)
assert.match(filters, /"md:block"/, 'O painel de filtros desktop deve permanecer visivel.')

for (const [name, source] of [
  ['usuarios', users],
  ['anuncios', ads],
  ['financeiro', finance],
  ['stories', stories],
  ['staff', staff],
]) {
  assert.match(source, /role="region"/, `A tabela de ${name} deve ser uma regiao acessivel.`)
  assert.match(source, /tabIndex=\{0\}/, `A tabela de ${name} deve aceitar foco para rolagem.`)
  assert.match(source, /sticky/, `A tabela de ${name} deve preservar o cabecalho durante a rolagem.`)
}

assert.match(users, /md:hidden/)
assert.match(ads, /md:hidden/)
assert.match(staff, /md:hidden/)
assert.match(finance, /overflow-x-auto/)
assert.match(stories, /overflow-x-auto/)

assert.match(publicCard, /flex h-full w-full/)
assert.match(publicCard, /min-h-\[224px\] flex-1/)
assert.match(publicCard, /line-clamp-1/)
assert.match(publicCard, /line-clamp-2 min-h-10/)
assert.match(publicCard, /className="flex-1 bg-\[#25D366\]/)
assert.match(publicCard, /className="flex-1 items-center/)

console.log('UI/UX final: filtros mobile, tabelas administrativas e cards consistentes validados.')
