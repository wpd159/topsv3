import type { MetadataRoute } from "next"
import { getPublicSiteBaseUrl } from "@/lib/seo/public-url"
import {
  buildSearchRobotsRules,
  resolveSearchIndexingPolicy,
} from "@/lib/seo/search-indexing-policy"

export default function robots(): MetadataRoute.Robots {
  const baseUrl = getPublicSiteBaseUrl()
  const policy = resolveSearchIndexingPolicy()
  const rules = buildSearchRobotsRules(policy)

  return {
    rules,
    ...(policy.sitemapEnabled ? { sitemap: `${baseUrl}/sitemap.xml` } : {}),
  }
}
