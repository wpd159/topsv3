import { NextRequest, NextResponse } from "next/server";

const protectedPrefixes = [
  "/admin",
  "/anunciar",
  "/painel",
  "/meus-anuncios",
  "/minha-conta",
  "/moderacao",
  "/publicar",
  "/editar",
  "/checkout"
];

const sessionCookieNames = ["JSESSIONID", "SESSION"];

export function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  if (pathname.startsWith("/api/")) {
    return NextResponse.next();
  }

  const hasSessionCookie = sessionCookieNames.some((name) => Boolean(request.cookies.get(name)?.value));
  const isProtectedRoute = protectedPrefixes.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));

  if (isProtectedRoute && !hasSessionCookie) {
    const loginUrl = request.nextUrl.clone();
    loginUrl.pathname = "/entrar";
    loginUrl.search = "";
    loginUrl.searchParams.set("next", `${pathname}${search}`);
    return NextResponse.redirect(loginUrl);
  }

  if ((pathname === "/entrar" || pathname === "/registrar") && hasSessionCookie) {
    const nextPath = safeNextPath(request.nextUrl.searchParams.get("next"));
    if (nextPath) {
      const nextUrl = new URL(nextPath, request.nextUrl.origin);
      return NextResponse.redirect(nextUrl);
    }
  }

  return NextResponse.next();
}

function safeNextPath(value: string | null): string | null {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.startsWith("/api/")) {
    return null;
  }
  return value;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|robots.txt|sitemap.xml|.*\\..*).*)"]
};
