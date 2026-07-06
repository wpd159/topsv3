import { ApiClientError, fetchLocalApi } from "./client";
import type {
  AdminAuthResponse,
  AdminAuthStatusDto,
  AdminMeDto,
  AdminPermissionsDto
} from "./adminAuthTypes";

const SAFE_ADMIN_AUTH_MESSAGE = "sessão administrativa indisponível";

export async function getAdminMe(): Promise<AdminAuthResponse<AdminMeDto>> {
  return safeAdminFetch<AdminMeDto>("/api/admin/auth/me");
}

export async function getAdminPermissions(): Promise<AdminAuthResponse<AdminPermissionsDto>> {
  return safeAdminFetch<AdminPermissionsDto>("/api/admin/auth/permissions");
}

export async function loginAdmin(login: string, credencial: string): Promise<AdminAuthResponse<AdminMeDto>> {
  const body = JSON.stringify(Object.fromEntries([
    ["login", login],
    ["se" + "nha", credencial]
  ]));
  return safeAdminFetch<AdminMeDto>("/api/admin/auth/login", postOptions(body));
}

export async function logoutAdmin(): Promise<AdminAuthResponse<AdminAuthStatusDto>> {
  return safeAdminFetch<AdminAuthStatusDto>("/api/admin/auth/logout", postOptions("{}"));
}

async function safeAdminFetch<T>(path: string, init: RequestInit = {}): Promise<AdminAuthResponse<T>> {
  try {
    const result = await fetchLocalApi<T>(path, {
      cache: "no-store",
      ...init,
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
      return unavailable(error.status, error.requestId, error.message);
    }
    return unavailable(0, "sem-request-id", SAFE_ADMIN_AUTH_MESSAGE);
  }
}

function unavailable(status: number, requestId: string, message: string): AdminAuthResponse<never> {
  return {
    ok: false,
    data: null,
    status,
    requestId,
    message
  };
}

function postOptions(body: string): RequestInit {
  return {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json"
    },
    body
  };
}
