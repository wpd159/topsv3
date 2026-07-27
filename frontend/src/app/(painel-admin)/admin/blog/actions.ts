'use server'

import { revalidatePath, revalidateTag } from 'next/cache'
import { PUBLIC_BLOG_CACHE_TAG } from '@/lib/blog-api'

export async function revalidarBlogPublico(slug?: string) {
  revalidateTag(PUBLIC_BLOG_CACHE_TAG)
  revalidatePath('/blog')
  if (slug) revalidatePath(`/blog/${slug}`)
  revalidatePath('/sitemap.xml')
}
