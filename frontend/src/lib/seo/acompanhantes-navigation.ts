import { corrigirTextoCorrompido } from "@/lib/text/encoding"
import { serverApiFetchJson } from "@/lib/server-api"
import { isCidadeIndexavelLocal } from "@/lib/seo/local-indexing"

export interface CidadeAtivaSeoDTO {
  estadoUf: string
  cidadeNome: string
  cidadeSlug: string
  ultimaAtualizacao?: string
  shouldIndex?: boolean
  totalAnunciosAtivos?: number
  totalAnuncios?: number
  quantidadeAnuncios?: number
}

export interface EstadoComCidadesSeo {
  uf: string
  nome: string
  cidades: CidadeAtivaSeoDTO[]
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

export async function buscarCidadesAtivasSeo() {
  try {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL
    if (!apiUrl) return []

    const data = await serverApiFetchJson<CidadeAtivaSeoDTO[]>(`${apiUrl}/anuncios/cidades-ativas`, {
      next: { revalidate: 3600 },
    })
    if (!Array.isArray(data)) return []

    return data
      .filter((item) => item?.estadoUf && item?.cidadeSlug && item?.cidadeNome)
      .filter(isCidadeIndexavelLocal)
      .map((item) => ({
        estadoUf: String(item.estadoUf).toUpperCase(),
        cidadeNome: corrigirTextoCorrompido(String(item.cidadeNome)),
        cidadeSlug: String(item.cidadeSlug),
        ultimaAtualizacao: item.ultimaAtualizacao,
        shouldIndex: item.shouldIndex,
        totalAnunciosAtivos: item.totalAnunciosAtivos,
        totalAnuncios: item.totalAnuncios,
        quantidadeAnuncios: item.quantidadeAnuncios,
      })) as CidadeAtivaSeoDTO[]
  } catch {
    return []
  }
}

export function agruparCidadesPorEstado(cidades: CidadeAtivaSeoDTO[]) {
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
