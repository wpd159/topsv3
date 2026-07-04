import { ApiClientError, fetchLocalApi } from "./client";
import type {
  AnuncioDetalhePublicoDto,
  CliqueWhatsappPublicoResponseDto,
  ConfirmarIdadePublicaRequestDto,
  ListaAnunciosPublicaDto,
  ListaStoriesPublicosDto,
  MetricaPublicaRequestDto,
  PublicApiResponse,
  RegistrarVisualizacaoPublicaResponseDto,
  SeoRotaPublicaDto,
  SolicitarAnuncioPublicoRequestDto,
  SolicitarAnuncioPublicoResponseDto,
  SolicitarAnuncioValidationErrorResponseDto,
  StatusIdadePublicaDto
} from "./publicTypes";

const SAFE_UNAVAILABLE_MESSAGE = "conteudo indisponivel no momento";

export async function getAnuncioPublico(slug: string): Promise<PublicApiResponse<AnuncioDetalhePublicoDto>> {
  return safePublicFetch<AnuncioDetalhePublicoDto>(`/api/public/anuncios/${pathSegment(slug)}`);
}

export async function getListagemCidadePublica(
  uf: string,
  cidade: string
): Promise<PublicApiResponse<ListaAnunciosPublicaDto>> {
  return safePublicFetch<ListaAnunciosPublicaDto>(
    `/api/public/acompanhantes/${pathSegment(uf)}/${pathSegment(cidade)}`
  );
}

export async function getListagemBairroPublica(
  uf: string,
  cidade: string,
  bairro: string
): Promise<PublicApiResponse<ListaAnunciosPublicaDto>> {
  return safePublicFetch<ListaAnunciosPublicaDto>(
    `/api/public/acompanhantes/${pathSegment(uf)}/${pathSegment(cidade)}/${pathSegment(bairro)}`
  );
}

export async function getSeoRotaPublica(caminho: string): Promise<PublicApiResponse<SeoRotaPublicaDto>> {
  return safePublicFetch<SeoRotaPublicaDto>(`/api/public/seo/rota?caminho=${encodeURIComponent(caminho)}`);
}

export async function registrarVisualizacaoPublica(
  slug: string,
  body: MetricaPublicaRequestDto = {}
): Promise<PublicApiResponse<RegistrarVisualizacaoPublicaResponseDto>> {
  return safePublicFetch<RegistrarVisualizacaoPublicaResponseDto>(
    `/api/public/anuncios/${pathSegment(slug)}/visualizacao`,
    postOptions(body)
  );
}

export async function registrarCliqueWhatsappPublico(
  slug: string,
  body: MetricaPublicaRequestDto = {}
): Promise<PublicApiResponse<CliqueWhatsappPublicoResponseDto>> {
  return safePublicFetch<CliqueWhatsappPublicoResponseDto>(
    `/api/public/anuncios/${pathSegment(slug)}/clique-whatsapp`,
    postOptions(body)
  );
}

export async function getStatusIdadePublica(): Promise<PublicApiResponse<StatusIdadePublicaDto>> {
  return safePublicFetch<StatusIdadePublicaDto>("/api/public/idade/status");
}

export async function confirmarIdadePublica(
  body: ConfirmarIdadePublicaRequestDto
): Promise<PublicApiResponse<StatusIdadePublicaDto>> {
  return safePublicFetch<StatusIdadePublicaDto>("/api/public/idade/confirmar", postOptions(body));
}

export async function solicitarAnuncioPublico(
  body: SolicitarAnuncioPublicoRequestDto
): Promise<PublicApiResponse<SolicitarAnuncioPublicoResponseDto>> {
  try {
    const result = await fetchLocalApi<SolicitarAnuncioPublicoResponseDto>(
      "/api/public/anunciar",
      postOptions(body)
    );
    return {
      ok: true,
      data: result.data,
      status: result.status,
      requestId: result.requestId,
      source: "api-local"
    };
  } catch (error) {
    if (error instanceof ApiClientError) {
      const validation = isSolicitarAnuncioValidation(error.details) ? error.details : null;
      return {
        ok: false,
        data: null,
        status: error.status,
        requestId: error.requestId,
        message: validation?.mensagem ?? error.message,
        validationErrors: validation?.erros
      };
    }
    return unavailable(0, "sem-request-id");
  }
}

export async function getStoriesPublicos(slug: string): Promise<PublicApiResponse<ListaStoriesPublicosDto>> {
  return safePublicFetch<ListaStoriesPublicosDto>(`/api/public/anuncios/${pathSegment(slug)}/stories`);
}

async function safePublicFetch<T>(path: string, init: RequestInit = {}): Promise<PublicApiResponse<T>> {
  try {
    const result = await fetchLocalApi<T>(path, {
      cache: "no-store",
      ...init
    });
    return {
      ok: true,
      data: result.data,
      status: result.status,
      requestId: result.requestId,
      source: "api-local"
    };
  } catch (error) {
    if (error instanceof ApiClientError) {
      return unavailable(error.status, error.requestId);
    }
    return unavailable(0, "sem-request-id");
  }
}

function unavailable(status: number, requestId: string): PublicApiResponse<never> {
  return {
    ok: false,
    data: null,
    status,
    requestId,
    message: SAFE_UNAVAILABLE_MESSAGE
  };
}

function pathSegment(value: string): string {
  const normalized = value.trim() || "localidade";
  return encodeURIComponent(normalized);
}

function postOptions(
  body: MetricaPublicaRequestDto | ConfirmarIdadePublicaRequestDto | SolicitarAnuncioPublicoRequestDto
): RequestInit {
  return {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  };
}

function isSolicitarAnuncioValidation(value: unknown): value is SolicitarAnuncioValidationErrorResponseDto {
  return (
    typeof value === "object" &&
    value !== null &&
    "criado" in value &&
    "erros" in value &&
    Array.isArray((value as { erros?: unknown }).erros)
  );
}
