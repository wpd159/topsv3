import { ApiClientError, fetchLocalApi } from "./client";
import type { AdminAuthResponse } from "./adminAuthTypes";
import type {
  AdminAcaoModeracaoResponseDto,
  AdminAnuncioDetalheDto,
  AdminAnuncioListaItemDto,
  AdminDecidirMidiaRequestDto,
  AdminDecidirRevisaoRequestDto,
  AdminRemeterRevisaoRequestDto,
  AdminMidiaDetalheDto,
  AdminMidiaListaItemDto,
  AdminOutboxDetalheDto,
  AdminOutboxListaItemDto,
  AdminPaginaDto,
  AdminRevisaoDetalheDto,
  AdminRevisaoListaItemDto,
  AdminResumoAnunciosDto,
  AdminResumoMetricasDto,
  AdminResumoMidiasDto,
  AdminResumoModeracaoDto,
  AdminStatusSistemaDto,
  AdminVisaoGeralDto
} from "./adminReadonlyTypes";

const READONLY_ERROR_MESSAGE = "resumo administrativo local indisponivel";

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
