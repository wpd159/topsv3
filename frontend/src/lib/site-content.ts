import {
  ApiContractError,
  apiErrorFromResponse,
  publicApiUrl,
  requireArrayPayload,
} from '@/lib/api-contract'

export type SiteContentKey =
  | 'quem-somos'
  | 'footer-resumo-institucional'
  | 'termos-de-uso'
  | 'politica-privacidade'
  | 'politica-cookies'
  | 'consentimento-promocional'
  | 'verificacao'
  | 'popup-login'
  | 'texto-whatsapp'
  | 'termos-conteudo-restrito'
  | 'privacidade-conteudo-restrito'
  | 'aviso-legal-conteudo-restrito'

export type SiteContentEntry = {
  contentKey: SiteContentKey
  titulo: string
  corpo: string
  contentVersion: number | null
  contentHash: string | null
  updatedAt: string | null
  unavailable?: boolean
}

type SiteContentPayload = {
  contentKey?: unknown
  titulo?: unknown
  corpo?: unknown
  contentVersion?: unknown
  contentHash?: unknown
  updatedAt?: unknown
}

export const PUBLIC_SITE_CONTENT_CACHE_TAG = 'public-site-content'
export const PUBLIC_SITE_CONTENT_REVALIDATE_SECONDS = 3600

export const SITE_CONTENT_KEYS: SiteContentKey[] = [
  'quem-somos',
  'footer-resumo-institucional',
  'termos-de-uso',
  'politica-privacidade',
  'politica-cookies',
  'consentimento-promocional',
  'verificacao',
  'popup-login',
  'texto-whatsapp',
  'termos-conteudo-restrito',
  'privacidade-conteudo-restrito',
  'aviso-legal-conteudo-restrito',
]

export const getUnavailableSiteContent = (contentKey: SiteContentKey): SiteContentEntry => ({
  contentKey,
  titulo: 'Conteudo temporariamente indisponivel',
  corpo: 'Nao foi possivel carregar este conteudo agora. Tente novamente em instantes.',
  contentVersion: null,
  contentHash: null,
  updatedAt: null,
  unavailable: true,
})

function isSiteContentKey(value: unknown): value is SiteContentKey {
  return typeof value === 'string' && SITE_CONTENT_KEYS.includes(value as SiteContentKey)
}

function mapPayload(payload: SiteContentPayload): SiteContentEntry | null {
  if (
    !isSiteContentKey(payload.contentKey) ||
    typeof payload.titulo !== 'string' ||
    !payload.titulo.trim() ||
    typeof payload.corpo !== 'string' ||
    !payload.corpo.trim()
  ) {
    return null
  }
  return {
    contentKey: payload.contentKey,
    titulo: payload.titulo,
    corpo: payload.corpo,
    contentVersion:
      typeof payload.contentVersion === 'number' ? payload.contentVersion : null,
    contentHash: typeof payload.contentHash === 'string' ? payload.contentHash : null,
    updatedAt: typeof payload.updatedAt === 'string' ? payload.updatedAt : null,
  }
}

export function parseAllPublicSiteContent(payload: unknown): SiteContentEntry[] {
  return requireArrayPayload<SiteContentPayload>(payload)
    .map(mapPayload)
    .filter((entry): entry is SiteContentEntry => entry !== null)
}

function logSanitizedFailure(error: unknown) {
  const status =
    typeof error === 'object' && error && 'status' in error
      ? String((error as { status?: unknown }).status ?? 'unknown')
      : 'unknown'
  console.error('site-content-load-failed', {
    errorName: error instanceof Error ? error.name : 'UnknownError',
    status,
  })
}

export async function fetchAllPublicSiteContent(): Promise<SiteContentEntry[]> {
  const response = await fetch(publicApiUrl('/conteudos-site'), {
    signal: AbortSignal.timeout(5000),
    next: {
      revalidate: PUBLIC_SITE_CONTENT_REVALIDATE_SECONDS,
      tags: [PUBLIC_SITE_CONTENT_CACHE_TAG],
    },
  })
  if (!response.ok) {
    throw await apiErrorFromResponse(response)
  }
  return parseAllPublicSiteContent(await response.json())
}

export async function fetchPublicSiteContent(
  contentKey: SiteContentKey,
): Promise<SiteContentEntry> {
  const entries = await fetchAllPublicSiteContent()
  const entry = entries.find((candidate) => candidate.contentKey === contentKey)
  if (!entry) {
    throw new ApiContractError(
      'O documento solicitado esta temporariamente indisponivel.',
      'INTEGRATION_MISSING',
      404,
      true,
    )
  }
  return entry
}

export async function resolveAllPublicSiteContent(): Promise<SiteContentEntry[]> {
  try {
    const published = await fetchAllPublicSiteContent()
    const byKey = new Map(published.map((entry) => [entry.contentKey, entry]))
    return SITE_CONTENT_KEYS.map((key) => byKey.get(key) ?? getUnavailableSiteContent(key))
  } catch (error) {
    logSanitizedFailure(error)
    return SITE_CONTENT_KEYS.map(getUnavailableSiteContent)
  }
}

export async function resolvePublicSiteContent(
  contentKey: SiteContentKey,
): Promise<SiteContentEntry> {
  const entries = await resolveAllPublicSiteContent()
  return entries.find((entry) => entry.contentKey === contentKey)
    ?? getUnavailableSiteContent(contentKey)
}
