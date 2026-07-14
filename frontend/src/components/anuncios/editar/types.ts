export type EstadoItem = { id: number; nome: string; uf?: string }
export type CidadeItem = { id: number; nome: string }
export type BairroItem = { id: number; nome: string }

export type AnuncioEditAPI = {
  id: number | string
  slug: string
  titulo?: string | null
  nome?: string | null
  categoria?: string | null
  preco?: number | string | null
  valor?: number | string | null
  horario?: string | null
  descricao?: string | null
  linkConteudo?: string | null
  locaisAtendimento?: string[] | null
  localAtendimento?: string | null
  servicos?: string[] | null
  estadoId?: number | null
  estadoNome?: string | null
  estadoUf?: string | null
  cidadeId?: number | null
  cidadeNome?: string | null
  bairroId?: number | null
  bairroNome?: string | null
  pontoReferenciaTexto?: string | null
  localizacaoLabel?: string | null
  fotos?: string[] | null
  fotosUrl?: string[] | null
  videosAnuncio?: string[] | null
}
