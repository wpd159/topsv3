import type { MetadataRoute } from "next";

import { localUrl } from "../lib/seo/localSeo";

export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();

  return [
    {
      url: localUrl("/"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.1
    },
    {
      url: localUrl("/health"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.1
    },
    {
      url: localUrl("/anuncios/skeleton-local"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.1
    },
    {
      url: localUrl("/acompanhantes/xx/local"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.1
    },
    {
      url: localUrl("/acompanhantes/xx/local/skeleton"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.1
    },
    {
      url: localUrl("/sobre"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.1
    },
    {
      url: localUrl("/como-funciona"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.1
    },
    {
      url: localUrl("/seguranca"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.1
    },
    {
      url: localUrl("/anunciar"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.1
    },
    {
      url: localUrl("/perguntas-frequentes"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.1
    }
  ];
}
