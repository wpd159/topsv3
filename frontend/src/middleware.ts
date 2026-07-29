import { NextResponse, type NextRequest } from 'next/server'

const SESSION_COOKIE_NAME = 'JSESSIONID'

export function middleware(req: NextRequest) {
  const pathname = req.nextUrl.pathname

  if (pathname === '/admin/login') {
    return NextResponse.next()
  }

  if (req.cookies.get(SESSION_COOKIE_NAME)?.value) {
    return NextResponse.next()
  }

  const nextPath = `${pathname}${req.nextUrl.search}`
  const url = req.nextUrl.clone()
  if (pathname === '/admin' || pathname.startsWith('/admin/')) {
    url.pathname = '/admin/login'
    url.searchParams.set('next', nextPath)
    return NextResponse.redirect(url)
  }

  url.pathname = '/'
  url.search = ''
  url.searchParams.set('login', '1')
  url.searchParams.set('next', nextPath)
  return NextResponse.redirect(url)
}

export const config = {
  matcher: [
    '/admin/:path*',
    '/anunciar/:path*',
    '/chat/:path*',
    '/favoritos/:path*',
    '/meus-anuncios/:path*',
    '/meus-tickets/:path*',
    '/minha-conta/:path*',
    '/painel',
    '/painel/:path*',
  ],
}
