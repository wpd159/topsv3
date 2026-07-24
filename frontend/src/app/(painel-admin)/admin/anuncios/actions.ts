'use server'

import { revalidateTag } from 'next/cache'

import { PUBLIC_CATALOG_CACHE_TAG } from '@/lib/public-catalog-api'

export async function revalidarCacheCatalogoPublico() {
  revalidateTag(PUBLIC_CATALOG_CACHE_TAG)
}
