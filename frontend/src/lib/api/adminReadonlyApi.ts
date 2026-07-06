import { ApiClientError, fetchLocalApi } from "./client";
import type { AdminAuthResponse } from "./adminAuthTypes";
import type {
  AdminAcaoModeracaoResponseDto,
  AdminBeneficioAnuncioDto,
  AdminAnuncioDetalheDto,
  AdminAnuncioListaItemDto,
  AdminCreditoConsistenciaResumoDto,
  AdminCreditoMovimentoDto,
  AdminCreditoPaginaDto,
  AdminCreditoSaldoDto,
  AdminDesempenhoAnuncianteDto,
  AdminDesempenhoAnuncioDto,
  AdminDesempenhoDiarioDto,
  AdminDesempenhoOrigemDto,
  AdminDesempenhoResumoDto,
  AdminPagamentoConsistenciaResumoDto,
  AdminPagamentoDetalheDto,
  AdminPagamentoListaItemDto,
  AdminPagamentoPaginaDto,
  AdminDecidirMidiaRequestDto,
  AdminDecidirRevisaoRequestDto,
  AdminRemeterRevisaoRequestDto,
  AdminMidiaDetalheDto,
  AdminMidiaListaItemDto,
  AdminOutboxDetalheDto,
  AdminOutboxListaItemDto,
  AdminOutboxPreviewRenderizadaDto,
  AdminOutboxSimularProcessamentoRequestDto,
  AdminOutboxSimularProcessamentoResponseDto,
  AdminPaginaDto,
  AdminPremiumAnuncioStatusDto,
  AdminPremiumConsistenciaResumoDto,
  AdminPremiumVencendoResumoDto,
  AdminRevisaoDetalheDto,
  AdminRevisaoListaItemDto,
  AdminResumoAnunciosDto,
  AdminResumoMetricasDto,
  AdminResumoMidiasDto,
  AdminResumoModeracaoDto,
  AdminStatusSistemaDto,
  AdminVisaoGeralDto
} from "./adminReadonlyTypes";

const READONLY_ERROR_MESSAGE = "resumo administrativo indisponível";

export function getAdminVisaoGeral(): Promise<AdminAuthResponse<AdminVisaoGeralDto>> {
  return safeReadonlyFetch("/api/admin/visao-geral");
}

export function getAdminAnunciosResumo(): Promise<AdminAuthResponse<AdminResumoAnunciosDto>> {
  return safeReadonlyFetch("/api/admin/anuncios/resumo");
}

export function getAdminModeracaoResumo(): Promise<AdminAuthResponse<AdminResumoModeracaoDto>> {
  return safeReadonlyFetch("/api/admin/moderacao/resumo");
}

export function getAdminMidiasResumo(): Promise<AdminAuthResponse<AdminResumoMidiasDto>> {
  return safeReadonlyFetch("/api/admin/midias/resumo");
}

export function getAdminMetricasResumo(): Promise<AdminAuthResponse<AdminResumoMetricasDto>> {
  return safeReadonlyFetch("/api/admin/metricas/resumo");
}

export function getAdminSistemaStatus(): Promise<AdminAuthResponse<AdminStatusSistemaDto>> {
  return safeReadonlyFetch("/api/admin/sistema/status");
}

export function getAdminAnunciosDetalhados(): Promise<AdminAuthResponse<AdminPaginaDto<AdminAnuncioListaItemDto>>> {
  return safeReadonlyFetch("/api/admin/anuncios?page=0&size=5");
}

export function getAdminAnuncioDetalhe(id: string): Promise<AdminAuthResponse<AdminAnuncioDetalheDto>> {
  return safeReadonlyFetch(`/api/admin/anuncios/${encodeURIComponent(id)}`);
}

export function getAdminMidiasDetalhadas(): Promise<AdminAuthResponse<AdminPaginaDto<AdminMidiaListaItemDto>>> {
  return safeReadonlyFetch("/api/admin/midias?page=0&size=5");
}

export function getAdminMidiaDetalhe(id: string): Promise<AdminAuthResponse<AdminMidiaDetalheDto>> {
  return safeReadonlyFetch(`/api/admin/midias/${encodeURIComponent(id)}`);
}

export function getAdminRevisoesDetalhadas(): Promise<AdminAuthResponse<AdminPaginaDto<AdminRevisaoListaItemDto>>> {
  return safeReadonlyFetch("/api/admin/moderacao/revisoes?page=0&size=5");
}

export function getAdminRevisaoDetalhe(id: string): Promise<AdminAuthResponse<AdminRevisaoDetalheDto>> {
  return safeReadonlyFetch(`/api/admin/moderacao/revisoes/${encodeURIComponent(id)}`);
}

export function getAdminOutbox(): Promise<AdminAuthResponse<AdminPaginaDto<AdminOutboxListaItemDto>>> {
  return safeReadonlyFetch("/api/admin/outbox?page=0&size=5&status=PENDENTE");
}

export function getAdminOutboxDetalhe(id: string): Promise<AdminAuthResponse<AdminOutboxDetalheDto>> {
  return safeReadonlyFetch(`/api/admin/outbox/${encodeURIComponent(id)}`);
}

export function getAdminOutboxPreview(id: string): Promise<AdminAuthResponse<AdminOutboxPreviewRenderizadaDto>> {
  return safeReadonlyFetch(`/api/admin/outbox/${encodeURIComponent(id)}/preview`);
}

export function simularAdminOutboxProcessamentoLocal(
  id: string,
  request: AdminOutboxSimularProcessamentoRequestDto = {}
): Promise<AdminAuthResponse<AdminOutboxSimularProcessamentoResponseDto>> {
  return safeReadonlyFetch(`/api/admin/outbox/${encodeURIComponent(id)}/simular-processamento-local`, {
    method: "POST",
    body: JSON.stringify(request),
    headers: {
      "Content-Type": "application/json"
    }
  });
}

export function decidirAdminRevisao(
  id: string,
  request: AdminDecidirRevisaoRequestDto
): Promise<AdminAuthResponse<AdminAcaoModeracaoResponseDto>> {
  return safeReadonlyFetch(`/api/admin/moderacao/revisoes/${encodeURIComponent(id)}/decidir`, {
    method: "POST",
    body: JSON.stringify(request),
    headers: {
      "Content-Type": "application/json"
    }
  });
}

export function decidirAdminMidia(
  id: string,
  request: AdminDecidirMidiaRequestDto
): Promise<AdminAuthResponse<AdminAcaoModeracaoResponseDto>> {
  return safeReadonlyFetch(`/api/admin/midias/${encodeURIComponent(id)}/decidir`, {
    method: "POST",
    body: JSON.stringify(request),
    headers: {
      "Content-Type": "application/json"
    }
  });
}

export function remeterAdminAnuncioParaRevisao(
  id: string,
  request: AdminRemeterRevisaoRequestDto
): Promise<AdminAuthResponse<AdminAcaoModeracaoResponseDto>> {
  return safeReadonlyFetch(`/api/admin/anuncios/${encodeURIComponent(id)}/remeter-revisao`, {
    method: "POST",
    body: JSON.stringify(request),
    headers: {
      "Content-Type": "application/json"
    }
  });
}

export function getAdminPremiumAnuncioStatus(id: string): Promise<AdminAuthResponse<AdminPremiumAnuncioStatusDto>> {
  return safeReadonlyFetch(`/api/admin/premium/anuncios/${encodeURIComponent(id)}`);
}

export function getAdminPremiumBeneficios(id: string): Promise<AdminAuthResponse<readonly AdminBeneficioAnuncioDto[]>> {
  return safeReadonlyFetch(`/api/admin/premium/anuncios/${encodeURIComponent(id)}/beneficios`);
}

export function getAdminPremiumConsistencia(): Promise<AdminAuthResponse<AdminPremiumConsistenciaResumoDto>> {
  return safeReadonlyFetch("/api/admin/premium/consistencia");
}

export function getAdminPremiumVencendo(): Promise<AdminAuthResponse<AdminPremiumVencendoResumoDto>> {
  return safeReadonlyFetch("/api/admin/premium/vencendo");
}

export function getAdminCreditoSaldo(id: string): Promise<AdminAuthResponse<AdminCreditoSaldoDto>> {
  return safeReadonlyFetch(`/api/admin/creditos/usuarios/${encodeURIComponent(id)}/saldo`);
}

export function getAdminCreditoMovimentos(
  id: string
): Promise<AdminAuthResponse<AdminCreditoPaginaDto<AdminCreditoMovimentoDto>>> {
  return safeReadonlyFetch(`/api/admin/creditos/usuarios/${encodeURIComponent(id)}/movimentos?page=0&size=10`);
}

export function getAdminCreditoConsistencia(): Promise<AdminAuthResponse<AdminCreditoConsistenciaResumoDto>> {
  return safeReadonlyFetch("/api/admin/creditos/consistencia");
}

export function getAdminCreditoInconsistencias(): Promise<AdminAuthResponse<AdminCreditoConsistenciaResumoDto>> {
  return safeReadonlyFetch("/api/admin/creditos/inconsistencias");
}

export function getAdminPagamentos(): Promise<AdminAuthResponse<AdminPagamentoPaginaDto<AdminPagamentoListaItemDto>>> {
  return safeReadonlyFetch("/api/admin/pagamentos?page=0&size=10");
}

export function getAdminPagamentoDetalhe(id: string): Promise<AdminAuthResponse<AdminPagamentoDetalheDto>> {
  return safeReadonlyFetch(`/api/admin/pagamentos/${encodeURIComponent(id)}`);
}

export function getAdminPagamentoConsistencia(): Promise<AdminAuthResponse<AdminPagamentoConsistenciaResumoDto>> {
  return safeReadonlyFetch("/api/admin/pagamentos/consistencia");
}

export function getAdminPagamentoInconsistencias(): Promise<AdminAuthResponse<AdminPagamentoConsistenciaResumoDto>> {
  return safeReadonlyFetch("/api/admin/pagamentos/inconsistencias");
}

export function getAdminDesempenhoAnuncio(id: string): Promise<AdminAuthResponse<AdminDesempenhoAnuncioDto>> {
  return safeReadonlyFetch(`/api/admin/desempenho/anuncios/${encodeURIComponent(id)}`);
}

export function getAdminDesempenhoDiario(id: string): Promise<AdminAuthResponse<readonly AdminDesempenhoDiarioDto[]>> {
  return safeReadonlyFetch(`/api/admin/desempenho/anuncios/${encodeURIComponent(id)}/diario`);
}

export function getAdminDesempenhoOrigens(id: string): Promise<AdminAuthResponse<readonly AdminDesempenhoOrigemDto[]>> {
  return safeReadonlyFetch(`/api/admin/desempenho/anuncios/${encodeURIComponent(id)}/origens`);
}

export function getAdminDesempenhoAnunciante(id: string): Promise<AdminAuthResponse<AdminDesempenhoAnuncianteDto>> {
  return safeReadonlyFetch(`/api/admin/desempenho/anunciantes/${encodeURIComponent(id)}`);
}

export function getAdminDesempenhoResumo(): Promise<AdminAuthResponse<AdminDesempenhoResumoDto>> {
  return safeReadonlyFetch("/api/admin/desempenho/resumo");
}

async function safeReadonlyFetch<T>(path: string, init?: RequestInit): Promise<AdminAuthResponse<T>> {
  try {
    const result = await fetchLocalApi<T>(path, {
      ...init,
      cache: "no-store",
      credentials: "include"
    });
    return {
      ok: true,
      data: result.data,
      status: result.status,
      requestId: result.requestId
    };
  } catch (error) {
    if (error instanceof ApiClientError) {
      return {
        ok: false,
        data: null,
        status: error.status,
        requestId: error.requestId,
        message: error.message
      };
    }
    return {
      ok: false,
      data: null,
      status: 0,
      requestId: "sem-request-id",
      message: READONLY_ERROR_MESSAGE
    };
  }
}
