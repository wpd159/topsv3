import { publicEnv } from "../config/publicEnv";

export type ApiErrorResponse = {
  timestamp?: string;
  status?: number;
  error?: string;
  code?: string;
  message?: string;
  path?: string;
  requestId?: string;
};

export type ApiResult<T> = {
  data: T;
  status: number;
  requestId: string;
};

export type ApiRequestOptions = Omit<RequestInit, "headers"> & {
  headers?: HeadersInit;
  requestId?: string;
};

export class ApiClientError extends Error {
  readonly status: number;
  readonly requestId: string;
  readonly error?: ApiErrorResponse;
  readonly details?: unknown;

  constructor(message: string, status: number, requestId: string, error?: ApiErrorResponse, details?: unknown) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
    this.requestId = requestId;
    this.error = error;
    this.details = details;
  }
}

export function buildApiUrl(path: string): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return new URL(normalizedPath, `${publicEnv.apiBaseUrl}/`).toString();
}

export async function fetchLocalApi<T>(path: string, options: ApiRequestOptions = {}): Promise<ApiResult<T>> {
  const { headers: suppliedHeaders, requestId: suppliedRequestId, ...fetchOptions } = options;
  const requestId = suppliedRequestId ?? createRequestId();
  const headers = new Headers(suppliedHeaders);
  headers.set("Accept", "application/json");
  headers.set("X-Request-Id", requestId);

  let response: Response;
  try {
    response = await fetch(buildApiUrl(path), {
      ...fetchOptions,
      cache: fetchOptions.cache ?? "no-store",
      credentials: fetchOptions.credentials ?? "include",
      headers
    });
  } catch {
    throw new ApiClientError("API local indisponível.", 0, requestId);
  }

  const responseRequestId = response.headers.get("X-Request-Id") ?? requestId;
  const body = await readJson(response);

  if (!response.ok) {
    const apiError = isApiErrorResponse(body) ? body : undefined;
    throw new ApiClientError(
      apiError?.message ?? "Erro ao consultar API local.",
      response.status,
      responseRequestId,
      apiError,
      body
    );
  }

  return {
    data: body as T,
    status: response.status,
    requestId: responseRequestId
  };
}

function createRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `web-${crypto.randomUUID()}`;
  }
  return `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

async function readJson(response: Response): Promise<unknown> {
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    return null;
  }
  return response.json();
}

function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  return typeof value === "object" && value !== null && "code" in value && "message" in value;
}
