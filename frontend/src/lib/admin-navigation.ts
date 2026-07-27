export const ADMIN_DASHBOARD_PATH = '/admin/dashboard'
export const ADMIN_LOGIN_PATH = '/admin/login'
export const ADMIN_ROOT_PATH = '/admin'

const ADMIN_NAVIGATION_ORIGIN = 'https://topsv3.invalid'

export function resolveAdminPostLoginPath(candidate: string | null | undefined) {
  const value = candidate?.trim()
  if (!value || !value.startsWith('/') || value.startsWith('//')) {
    return ADMIN_DASHBOARD_PATH
  }

  try {
    const url = new URL(value, ADMIN_NAVIGATION_ORIGIN)
    const isAdminPath =
      url.origin === ADMIN_NAVIGATION_ORIGIN &&
      (url.pathname === ADMIN_ROOT_PATH || url.pathname.startsWith(`${ADMIN_ROOT_PATH}/`))
    const isLoginPath =
      url.pathname === ADMIN_LOGIN_PATH || url.pathname.startsWith(`${ADMIN_LOGIN_PATH}/`)

    if (!isAdminPath || url.pathname === ADMIN_ROOT_PATH || isLoginPath) {
      return ADMIN_DASHBOARD_PATH
    }

    return `${url.pathname}${url.search}${url.hash}`
  } catch {
    return ADMIN_DASHBOARD_PATH
  }
}

export function resolveAdminPostLoginSearch(search: string) {
  return resolveAdminPostLoginPath(new URLSearchParams(search).get('next'))
}

export function activeAdminSidebarHref(
  hrefs: string[],
  pathname: string,
  hash: string
) {
  const normalizedHash = hash && !hash.startsWith('#') ? `#${hash}` : hash
  const exactHashHref = normalizedHash ? `${pathname}${normalizedHash}` : null

  if (exactHashHref && hrefs.includes(exactHashHref)) {
    return exactHashHref
  }

  return hrefs.find((href) => {
    const baseHref = href.split('#')[0]
    return (
      pathname === baseHref ||
      (baseHref !== ADMIN_ROOT_PATH && pathname.startsWith(`${baseHref}/`))
    )
  }) ?? null
}
