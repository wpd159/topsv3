import type { PublicCatalogDiscovery } from "@/lib/public-catalog-api"

export interface CidadeNavegacaoPublica {
  estadoUf: string
  cidadeNome: string
  cidadeSlug: string
  ultimaAtualizacao?: string
  totalAnunciosAtivos?: number
}

export interface EstadoComCidadesSeo {
  uf: string
  nome: string
  cidades: CidadeNavegacaoPublica[]
}

export const ESTADOS_UF_PARA_NOME: Record<string, string> = {
  AC: "Acre",
  AL: "Alagoas",
  AP: "Amapá",
  AM: "Amazonas",
  BA: "Bahia",
  CE: "Ceará",
  DF: "Distrito Federal",
  ES: "Espírito Santo",
  GO: "Goiás",
  MA: "Maranhão",
  MT: "Mato Grosso",
  MS: "Mato Grosso do Sul",
  MG: "Minas Gerais",
  PA: "Pará",
  PB: "Paraíba",
  PR: "Paraná",
  PE: "Pernambuco",
  PI: "Piauí",
  RJ: "Rio de Janeiro",
  RN: "Rio Grande do Norte",
  RS: "Rio Grande do Sul",
  RO: "Rondônia",
  RR: "Roraima",
  SC: "Santa Catarina",
  SP: "São Paulo",
  SE: "Sergipe",
  TO: "Tocantins",
}

export function getEstadoNomePorUf(uf: string) {
  return ESTADOS_UF_PARA_NOME[uf.toUpperCase()] || uf.toUpperCase()
}

export function cidadesDaDescobertaPublica(descoberta: PublicCatalogDiscovery) {
  return descoberta.estados.flatMap((estado) =>
    estado.cidades.map((cidade) => ({
      estadoUf: estado.uf,
      cidadeNome: cidade.nome,
      cidadeSlug: cidade.slug,
      ultimaAtualizacao: cidade.ultimaAtualizacao ?? undefined,
      totalAnunciosAtivos: cidade.totalAnunciosAtivos,
    }))
  ) satisfies CidadeNavegacaoPublica[]
}

export function agruparCidadesPorEstado(cidades: CidadeNavegacaoPublica[]) {
  const mapa = new Map<string, EstadoComCidadesSeo>()

  for (const cidade of cidades) {
    const uf = cidade.estadoUf.toUpperCase()

    if (!mapa.has(uf)) {
      mapa.set(uf, {
        uf,
        nome: getEstadoNomePorUf(uf),
        cidades: [],
      })
    }

    mapa.get(uf)?.cidades.push(cidade)
  }

  return [...mapa.values()]
    .map((estado) => ({
      ...estado,
      cidades: estado.cidades.sort((a, b) => a.cidadeNome.localeCompare(b.cidadeNome)),
    }))
    .sort((a, b) => a.nome.localeCompare(b.nome))
}
