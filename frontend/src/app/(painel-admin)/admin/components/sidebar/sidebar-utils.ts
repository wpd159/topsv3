import type { SidebarLink } from './sidebar-links'

const MODERATOR_RESTRICTED_ROUTES = [
  '/admin/staff',
  '/admin/financeiro',
  '/admin/registros',
  '/admin/creditos',
  '/admin/beneficios-premium',
  '/admin/termos-footer',
  '/admin/blog',
  '/admin/stories',
]

function normalizedRoute(route: string) {
  return route.split(/[?#]/, 1)[0]
}

export const canAccessRoute = (route: string, role: string): boolean => {
  if (role === 'ADMIN') {
    return true
  }

  if (role === 'MODERADOR') {
    const pathname = normalizedRoute(route)
    return !MODERATOR_RESTRICTED_ROUTES.some(
      (restrictedRoute) =>
        pathname === restrictedRoute || pathname.startsWith(`${restrictedRoute}/`)
    )
  }

  return false
}

export const filterSidebarLinksByRole = (links: SidebarLink[], role: string): SidebarLink[] =>
  links.filter((link) => canAccessRoute(link.href, role))
