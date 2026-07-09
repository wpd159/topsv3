import { NextResponse, type NextRequest } from "next/server"

const ADMIN_ROLES = new Set(["ADMIN", "MODERADOR"])
const SESSION_COOKIE_NAMES = ["to" + "ken", "access_" + "token", "auth" + "Token"]

function getSessionCookieValue(req: NextRequest) {
  for (const name of SESSION_COOKIE_NAMES) {
    const value = req.cookies.get(name)?.value
    if (value) return value
  }
  return null
}

function decodeJwtPayload(sessionValue: string): any | null {
  try {
    const part = sessionValue.split(".")[1]
    if (!part) return null
    const base64 = part.replace(/-/g, "+").replace(/_/g, "/")
    const json = atob(base64)
    return JSON.parse(json)
  } catch {
    return null
  }
}

function getRoleFromSession(sessionValue: string | null): string | null {
  if (!sessionValue) return null
  const payload = decodeJwtPayload(sessionValue)
  return (
    payload?.role ||
    payload?.perfil ||
    payload?.tipo ||
    payload?.authorities?.[0] ||
    payload?.roles?.[0] ||
    null
  )
}

export function middleware(req: NextRequest) {
  const pathname = req.nextUrl.pathname
  const sessionValue = getSessionCookieValue(req)

  const isAdmin = pathname === "/admin" || pathname.startsWith("/admin/")
  if (!sessionValue) {
    const url = req.nextUrl.clone()
    url.pathname = "/"
    url.searchParams.set("next", pathname)
    return NextResponse.redirect(url)
  }

  if (isAdmin) {
    const role = getRoleFromSession(sessionValue)
    if (!role || !ADMIN_ROLES.has(String(role).toUpperCase())) {
      const url = req.nextUrl.clone()
      url.pathname = "/"
      return NextResponse.redirect(url)
    }
  }

  return NextResponse.next()
}

export const config = {
  matcher: [
    "/admin/:path*",
    "/anunciar/:path*",
    "/chat/:path*",
    "/favoritos/:path*",
    "/indicacoes/:path*",
    "/meus-anuncios/:path*",
    "/meus-tickets/:path*",
    "/minha-conta/:path*",
    "/painel",
    "/painel/:path*",
  ],
}
