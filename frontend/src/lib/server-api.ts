import { cookies, headers } from "next/headers"
import { corrigirEstruturaTexto } from "@/lib/text/encoding"

const FORWARDED_COOKIE_NAMES = [
  "token",
  "access_token",
  "authToken",
  "visitor_access_token",
  "visitor_explicit_access_token",
  "visitor_session_id",
]

export class ServerApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = "ServerApiError"
    this.status = status
  }
}

export async function buildServerApiHeaders(extraHeaders?: HeadersInit) {
  const cookieStore = await cookies()
  const headerStore = await headers()
  const forwarded = new Headers(extraHeaders)

  const cookieHeader = FORWARDED_COOKIE_NAMES.map((name) => {
    const value = cookieStore.get(name)?.value
    return value ? `${name}=${value}` : null
  })
    .filter(Boolean)
    .join("; ")

  if (cookieHeader) {
    forwarded.set("cookie", cookieHeader)
  }

  const userAgent = headerStore.get("user-agent")
  if (userAgent && !forwarded.has("user-agent")) {
    forwarded.set("user-agent", userAgent)
  }

  const forwardedFor = headerStore.get("x-forwarded-for")
  if (forwardedFor && !forwarded.has("x-forwarded-for")) {
    forwarded.set("x-forwarded-for", forwardedFor)
  }

  return forwarded
}

export async function serverApiFetch(input: string, init: RequestInit = {}) {
  const headers = await buildServerApiHeaders(init.headers)
  const hasNextCachePolicy = "next" in init

  return fetch(input, {
    ...init,
    headers,
    ...(!init.cache && !hasNextCachePolicy ? { cache: "no-store" as const } : {}),
  })
}

export async function serverApiFetchJson<T>(input: string, init: RequestInit = {}) {
  const response = await serverApiFetch(input, init)
  if (!response.ok) {
    throw new ServerApiError(`Falha ao buscar ${input}: ${response.status}`, response.status)
  }

  const data = (await response.json()) as T
  return corrigirEstruturaTexto(data)
}
