import { SidebarLink } from './sidebar-links'

const MODERATOR_RESTRICTED_ROUTES = [
  '/admin/staff',
  '/admin/financeiro',
  '/admin/registros',
  '/admin/creditos',
  '/admin/beneficios-premium',
  '/admin/indicacoes',
  '/admin/termos-footer',
  '/admin/blog',
  '/admin/stories',
]

export const filterSidebarLinksByRole = (links: SidebarLink[], role: string): SidebarLink[] => {
  if (role === 'ADMIN') {
    return links
  }

  if (role === 'MODERADOR') {
    return links.filter((link) => !MODERATOR_RESTRICTED_ROUTES.includes(link.href))
  }

  return []
}

export const canAccessRoute = (route: string, role: string): boolean => {
  if (role === 'ADMIN') {
    return true
  }

  if (role === 'MODERADOR') {
    return !MODERATOR_RESTRICTED_ROUTES.some((restrictedRoute) => route.startsWith(restrictedRoute))
  }

  return false
}
