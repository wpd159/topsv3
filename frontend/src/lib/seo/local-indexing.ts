export interface LocalIndexabilityInput {
  indexacao?: LocalIndexingDecision | null
}

export interface LocalIndexingDecision {
  indexavel: boolean
  motivo: string
  anunciosElegiveisUnicos: number
  minimoNecessario: number
  canonica: boolean
}

function isIndexavelPeloContrato(input: LocalIndexabilityInput | null | undefined) {
  return input?.indexacao?.indexavel === true && input.indexacao.canonica === true
}

export function isCidadeIndexavelLocal(input: LocalIndexabilityInput | null | undefined): boolean {
  return isIndexavelPeloContrato(input)
}

export function isBairroIndexavelLocal(input: LocalIndexabilityInput | null | undefined): boolean {
  return isIndexavelPeloContrato(input)
}
