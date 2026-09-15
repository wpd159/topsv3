'use server'

import { revalidateTag } from 'next/cache'
import { after } from 'next/server'

import { PUBLIC_CATALOG_CACHE_TAG } from '@/lib/public-catalog-api'
import { enviarUrlsParaIndexNow, type IndexNowPublicEvent } from '@/lib/seo/indexnow'

export async function revalidarCacheCatalogoPublico(event?: IndexNowPublicEvent) {
  revalidateTag(PUBLIC_CATALOG_CACHE_TAG)

  if (!event?.urls.length) return

  after(async () => {
    const startedAt = Date.now()
    try {
      const result = await enviarUrlsParaIndexNow(event)
      const logResult = result.ok ? console.info : console.warn
      logResult('[IndexNow] public change processed.', {
        eventType: result.eventType,
        status: result.status,
        ok: result.ok,
        attempts: result.attempts,
        urlCount: result.urlCount,
        acceptedUrlCount: result.acceptedUrlCount,
        failedUrlCount: result.failedUrlCount,
        unattemptedUrlCount: result.unattemptedUrlCount,
        deduplicatedCount: result.deduplicatedCount,
        reason: result.reason,
        durationMs: Date.now() - startedAt,
      })
    } catch (error) {
      console.warn('[IndexNow] public change notification failed.', {
        eventType: event.eventType,
        urlCount: event.urls.length,
        durationMs: Date.now() - startedAt,
        errorType: error instanceof Error ? error.name : 'unknown',
      })
    }
  })
}
