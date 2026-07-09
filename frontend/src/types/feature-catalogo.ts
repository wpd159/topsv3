export type FeatureCatalogoItem = {
  codigo: string
  nome: string
  descricao?: string | null
  custoCreditos: number
  duracaoHoras?: number | null
  escopo?: "ANUNCIO" | "USUARIO"
  ativo?: boolean
}