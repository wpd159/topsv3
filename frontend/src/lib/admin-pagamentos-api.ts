import { adminApiUrl, apiErrorFromResponse, requireArrayPayload } from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

export type AdminPagamentoItem = {
  id: string
  usuarioId: string
  provedorDeclarado: string
  provedorClassificado: string
  metodo: string
  statusInterno: string
  statusOperacional: string
  quantidadeCreditos: number | null
  moeda: string
  evidenciaTransacaoPresente: boolean
  evidenciaTransacaoMascarada: string | null
  evidenciaProvedorPresente: boolean
  creditoVinculado: boolean
  criadoEm: string
  atualizadoEm: string
  somenteLeitura: true
}

export type AdminPagamentosPage = {
  itens: AdminPagamentoItem[]
  total: number
  pagina: number
  tamanho: number
  somenteLeitura: true
}

export type AdminRelatorioReceitaFiltros = {
  periodo: 'HOJE' | '7_DIAS' | '30_DIAS' | 'PERSONALIZADO'
  inicio?: string
  fim?: string
  status:
    | 'TODOS'
    | 'CONFIRMADO'
    | 'PENDENTE'
    | 'FALHO'
    | 'CANCELADO'
    | 'EXPIRADO'
    | 'ESTORNADO'
    | 'LEGADO'
  metodo: 'TODOS' | 'PIX' | 'LEGADO' | 'DESCONHECIDO'
  usuario?: string
  produto?: string
  ordenacao: 'MAIS_RECENTES' | 'MAIS_ANTIGOS' | 'MAIOR_VALOR' | 'MENOR_VALOR'
  page: number
  size: number
}

export type AdminReceitaPontoDiario = {
  data: string
  receitaConfirmada: number
  pagamentosConfirmados: number
}

export type AdminReceitaDistribuicaoProduto = {
  codigo: string
  nome: string
  receitaConfirmada: number
  pagamentosConfirmados: number
  creditosVendidos: number
}

export type AdminReceitaAlerta = {
  codigo: string
  mensagem: string
  quantidade: number
}

export type AdminRelatorioReceitaResumo = {
  receitaConfirmada: number
  pagamentosConfirmados: number
  ticketMedio: number
  creditosVendidos: number
  pagamentosPendentes: number
  pagamentosFalhos: number
  pagamentosCancelados: number
  pagamentosEstornados: number
  evolucaoDiaria: AdminReceitaPontoDiario[]
  distribuicaoPorProduto: AdminReceitaDistribuicaoProduto[]
  alertasConciliacao: AdminReceitaAlerta[]
  dataInicio: string
  dataFim: string
  timezone: 'America/Sao_Paulo'
  calculadoEm: string
  somenteLeitura: true
}

export type AdminReceitaTransacao = {
  id: string
  usuarioId: string
  data: string
  usuarioNome: string
  usuarioEmail: string | null
  usuarioWhatsapp: string | null
  tipo: 'COMPRA_DE_CREDITOS' | 'REGISTRO_LEGADO'
  produto: string
  valor: number
  moeda: string
  creditos: number | null
  status: 'CONFIRMADO' | 'PENDENTE' | 'FALHO' | 'CANCELADO' | 'EXPIRADO' | 'ESTORNADO' | 'LEGADO' | 'DESCONHECIDO'
  metodo: string
  identificadorExternoMascarado: string | null
  receitaConfirmada: boolean
  somenteLeitura: true
}

export type AdminRelatorioReceitaPagina = {
  itens: AdminReceitaTransacao[]
  total: number
  pagina: number
  tamanho: number
  receitaConfirmadaFiltrada: number
  pagamentosConfirmadosFiltrados: number
  dataInicio: string
  dataFim: string
  timezone: 'America/Sao_Paulo'
  somenteLeitura: true
}

function relatorioQuery(filtros: AdminRelatorioReceitaFiltros, paginado: boolean) {
  const query = new URLSearchParams({
    periodo: filtros.periodo,
    status: filtros.status,
    metodo: filtros.metodo,
  })
  if (filtros.periodo === 'PERSONALIZADO' && filtros.inicio) query.set('inicio', filtros.inicio)
  if (filtros.periodo === 'PERSONALIZADO' && filtros.fim) query.set('fim', filtros.fim)
  if (filtros.usuario) query.set('usuario', filtros.usuario)
  if (filtros.produto) query.set('produto', filtros.produto)
  if (paginado) {
    query.set('ordenacao', filtros.ordenacao)
    query.set('page', String(filtros.page))
    query.set('size', String(filtros.size))
  }
  return query
}

export async function obterRelatorioReceitaResumo(
  filtros: AdminRelatorioReceitaFiltros,
): Promise<AdminRelatorioReceitaResumo> {
  const response = await fetch(
    adminApiUrl(`/pagamentos/relatorio/resumo?${relatorioQuery(filtros, false).toString()}`),
    { credentials: 'include', cache: 'no-store' },
  )
  if (!response.ok) throw await apiErrorFromResponse(response)
  const payload = corrigirEstruturaTexto(await response.json()) as AdminRelatorioReceitaResumo
  return {
    ...payload,
    evolucaoDiaria: requireArrayPayload<AdminReceitaPontoDiario>(payload.evolucaoDiaria),
    distribuicaoPorProduto: requireArrayPayload<AdminReceitaDistribuicaoProduto>(
      payload.distribuicaoPorProduto,
    ),
    alertasConciliacao: requireArrayPayload<AdminReceitaAlerta>(payload.alertasConciliacao),
  }
}

export async function listarRelatorioReceitaTransacoes(
  filtros: AdminRelatorioReceitaFiltros,
): Promise<AdminRelatorioReceitaPagina> {
  const response = await fetch(
    adminApiUrl(`/pagamentos/relatorio/transacoes?${relatorioQuery(filtros, true).toString()}`),
    { credentials: 'include', cache: 'no-store' },
  )
  if (!response.ok) throw await apiErrorFromResponse(response)
  const payload = corrigirEstruturaTexto(await response.json()) as AdminRelatorioReceitaPagina
  return {
    ...payload,
    itens: requireArrayPayload<AdminReceitaTransacao>(payload.itens),
  }
}

export async function listarAdminPagamentos(pagina = 0, tamanho = 20): Promise<AdminPagamentosPage> {
  const query = new URLSearchParams({ page: String(pagina), size: String(tamanho) })
  const response = await fetch(adminApiUrl(`/pagamentos?${query.toString()}`), {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) throw await apiErrorFromResponse(response)

  const payload = corrigirEstruturaTexto(await response.json()) as Partial<AdminPagamentosPage>
  return {
    ...payload,
    itens: requireArrayPayload<AdminPagamentoItem>(payload.itens),
  } as AdminPagamentosPage
}
