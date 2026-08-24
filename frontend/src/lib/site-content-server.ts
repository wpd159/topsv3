import 'server-only'

import { ApiContractError } from '@/lib/api-contract'
import { publicServerApiJson } from '@/lib/public-server-api'
import {
  getUnavailableSiteContent,
  parseAllPublicSiteContent,
  PUBLIC_SITE_CONTENT_CACHE_TAG,
  PUBLIC_SITE_CONTENT_REVALIDATE_SECONDS,
  SITE_CONTENT_KEYS,
  type SiteContentEntry,
  type SiteContentKey,
} from '@/lib/site-content'

export async function fetchAllPublicSiteContent(): Promise<SiteContentEntry[]> {
  return publicServerApiJson('/conteudos-site', {
    endpointFamily: 'site-content.all',
    cache: {
      mode: 'revalidate',
      seconds: PUBLIC_SITE_CONTENT_REVALIDATE_SECONDS,
      tags: [PUBLIC_SITE_CONTENT_CACHE_TAG],
    },
    validate: parseAllPublicSiteContent,
  })
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
  } catch {
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
