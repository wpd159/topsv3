import { NextResponse, type NextRequest } from 'next/server'

const SESSION_COOKIE_NAME = 'JSESSIONID'
const CANONICAL_ORIGIN = 'https://topsdojob.com'
const SECONDARY_HOSTS = new Set([
  'www.topsdojob.com',
  'topsdojob.com.br',
  'www.topsdojob.com.br',
])
const PROTECTED_PATH_PREFIXES = [
  '/admin',
  '/anunciar',
  '/chat',
  '/favoritos',
  '/meus-anuncios',
  '/meus-tickets',
  '/minha-conta',
  '/painel',
]

function requestHostname(req: NextRequest) {
  return (req.headers.get('host') ?? req.nextUrl.hostname)
    .split(':', 1)[0]
    .toLowerCase()
}

function isProtectedPath(pathname: string) {
  return PROTECTED_PATH_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  )
}

export function middleware(req: NextRequest) {
  const pathname = req.nextUrl.pathname

  if (SECONDARY_HOSTS.has(requestHostname(req))) {
    const canonical = new URL(`${pathname}${req.nextUrl.search}`, CANONICAL_ORIGIN)
    return NextResponse.redirect(canonical, 308)
  }

  if (!isProtectedPath(pathname)) {
    return NextResponse.next()
  }

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
    {
      source: '/:path*',
      has: [{ type: 'host', value: 'www.topsdojob.com' }],
    },
    {
      source: '/:path*',
      has: [{ type: 'host', value: 'topsdojob.com.br' }],
    },
    {
      source: '/:path*',
      has: [{ type: 'host', value: 'www.topsdojob.com.br' }],
    },
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
