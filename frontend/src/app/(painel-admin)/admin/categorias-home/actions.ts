'use server'

import { revalidateTag } from 'next/cache'

import { PUBLIC_HOME_CATEGORIES_CACHE_TAG } from '@/lib/public-catalog-api'

export async function revalidarCacheCategoriasHome() {
  revalidateTag(PUBLIC_HOME_CATEGORIES_CACHE_TAG)
}
