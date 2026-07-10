export type PublishGuardState =
  | {
      open: false
      title: string
      description: string
      actionLabel: string
    }
  | {
      open: true
      title: string
      description: string
      actionLabel: string
    }

export const closedPublishGuard: PublishGuardState = {
  open: false,
  title: '',
  description: '',
  actionLabel: '',
}

export function buildPublishGuard(
  config: Omit<Extract<PublishGuardState, { open: true }>, 'open'>
): PublishGuardState {
  return {
    open: true,
    ...config,
  }
}

export function calculateAge(value?: string | null) {
  if (!value) return null
  const date = new Date(`${String(value).slice(0, 10)}T00:00:00`)
  if (Number.isNaN(date.getTime())) return null
  const today = new Date()
  let age = today.getFullYear() - date.getFullYear()
  const monthDiff = today.getMonth() - date.getMonth()
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < date.getDate())) age -= 1
  return age >= 18 && age < 100 ? age : null
}

export function digitsOnly(value: string) {
  return value.replace(/\D/g, '')
}

export function formatCpf(value: string) {
  const v = digitsOnly(value).slice(0, 11)
  return v
    .replace(/^(\d{3})(\d)/, '$1.$2')
    .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1-$2')
}

export function selectClassName() {
  return 'h-12 w-full rounded-xl border border-zinc-200 bg-white px-4 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-zinc-900 focus-visible:ring-offset-2'
}

export function normalizeSearchValue(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim()
}

export function hasPersistedKyc(usuario: unknown) {
  const user = usuario as {
    documentosUrls?: string[] | null
    totalDocumentos?: number | null
  } | null

  if (!user) return false

  const documentosPersistidos = Array.isArray(user.documentosUrls)
    ? user.documentosUrls.filter((value) => typeof value === 'string' && value.trim().length > 0)
    : []

  return documentosPersistidos.length >= 1 || (user.totalDocumentos ?? 0) >= 1
}
