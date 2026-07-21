export type SituacaoVisualizacoesCanonicas =
  | 'DISPONIVEL'
  | 'ZERO_LEGITIMO'
  | 'HISTORICO_PENDENTE'

export type VisualizacoesCanonicas = {
  total: number | null
  situacao: SituacaoVisualizacoesCanonicas
}

export class VisualizacoesCanonicasContractError extends Error {
  constructor() {
    super('O servico retornou visualizacoes em formato incompativel.')
    this.name = 'VisualizacoesCanonicasContractError'
  }
}

export function parseVisualizacoesCanonicas(value: unknown): VisualizacoesCanonicas {
  if (!value || typeof value !== 'object') {
    throw new VisualizacoesCanonicasContractError()
  }

  const raw = value as { total?: unknown; situacao?: unknown }
  if (raw.situacao === 'HISTORICO_PENDENTE') {
    if (raw.total !== null) throw new VisualizacoesCanonicasContractError()
    return { total: null, situacao: raw.situacao }
  }

  if (
    (raw.situacao !== 'DISPONIVEL' && raw.situacao !== 'ZERO_LEGITIMO') ||
    typeof raw.total !== 'number' ||
    !Number.isSafeInteger(raw.total) ||
    raw.total < 0 ||
    (raw.situacao === 'ZERO_LEGITIMO' && raw.total !== 0) ||
    (raw.situacao === 'DISPONIVEL' && raw.total === 0)
  ) {
    throw new VisualizacoesCanonicasContractError()
  }

  return { total: raw.total, situacao: raw.situacao }
}

export function formatarVisualizacoesCanonicas(value?: VisualizacoesCanonicas | null) {
  if (!value || value.situacao === 'HISTORICO_PENDENTE' || value.total === null) {
    return '\u2014'
  }
  return value.total.toLocaleString('pt-BR')
}
