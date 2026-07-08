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
  "/checkout",
  "/chat",
  "/favoritos",
  "/indicacoes",
  "/meus-tickets"
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
    loginUrl.pathname = "/";
    loginUrl.search = "";
    loginUrl.searchParams.set("login", "1");
    loginUrl.searchParams.set("next", `${pathname}${search}`);
    return NextResponse.redirect(loginUrl);
  }

  if (pathname === "/entrar" || pathname === "/registrar") {
    const homeUrl = request.nextUrl.clone();
    homeUrl.pathname = "/";
    homeUrl.search = "";
    homeUrl.searchParams.set(pathname === "/registrar" ? "registro" : "login", "1");
    const nextPath = safeNextPath(request.nextUrl.searchParams.get("next"));
    if (nextPath) homeUrl.searchParams.set("next", nextPath);
    return NextResponse.redirect(homeUrl);
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
