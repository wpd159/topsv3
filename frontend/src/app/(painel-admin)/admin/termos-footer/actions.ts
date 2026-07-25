'use server'

import { revalidateTag } from 'next/cache'
import { PUBLIC_SITE_CONTENT_CACHE_TAG } from '@/lib/site-content'

export async function revalidarCacheConteudoSite() {
  revalidateTag(PUBLIC_SITE_CONTENT_CACHE_TAG)
}
