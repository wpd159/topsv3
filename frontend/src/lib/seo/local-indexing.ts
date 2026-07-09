export const MIN_ANUNCIOS_CIDADE_INDEX = 5
export const MIN_ANUNCIOS_BAIRRO_INDEX = 3

export interface LocalIndexabilityInput {
  shouldIndex?: boolean | null
  totalAnunciosAtivos?: number | string | null
  totalAnuncios?: number | string | null
  quantidadeAnuncios?: number | string | null
}

export function numeroLocal(value: number | string | null | undefined): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

export function totalAnunciosLocal(input: LocalIndexabilityInput | null | undefined): number {
  return numeroLocal(
    input?.totalAnunciosAtivos ?? input?.totalAnuncios ?? input?.quantidadeAnuncios
  )
}

export function isCidadeIndexavelLocal(input: LocalIndexabilityInput | null | undefined): boolean {
  return input?.shouldIndex === true || totalAnunciosLocal(input) >= MIN_ANUNCIOS_CIDADE_INDEX
}

export function isBairroIndexavelLocal(input: LocalIndexabilityInput | null | undefined): boolean {
  return totalAnunciosLocal(input) >= MIN_ANUNCIOS_BAIRRO_INDEX
}
