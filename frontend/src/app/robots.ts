import type { MetadataRoute } from "next";

import { localUrl } from "../lib/seo/localSeo";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      disallow: "/"
    },
    sitemap: localUrl("/sitemap.xml")
  };
}
