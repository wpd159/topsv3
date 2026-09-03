const AGE_GATE_EXEMPT_EXACT_PATHS = new Set([
  '/acesso-negado',
  '/aviso-seguranca-whatsapp',
  '/consentimento-promocional',
  '/contato',
  '/cookies',
  '/faq',
  '/politica-de-privacidade',
  '/registrar',
  '/sobre',
  '/termos-de-uso',
])

const AGE_GATE_EXEMPT_ROUTE_PREFIXES = ['/blog', '/politicas'] as const

export function isAgeGateExemptPath(pathname: string | null | undefined) {
  if (!pathname) return false
  if (AGE_GATE_EXEMPT_EXACT_PATHS.has(pathname)) return true

  return AGE_GATE_EXEMPT_ROUTE_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  )
}
