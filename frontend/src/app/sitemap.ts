import type { MetadataRoute } from "next";

import { localUrl } from "../lib/seo/localSeo";

export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();

  return [
    {
      url: localUrl("/"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.8
    },
    {
      url: localUrl("/acompanhantes/go/goiania"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.7
    },
    {
      url: localUrl("/acompanhantes/go/goiania/setor-bueno"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.6
    },
    {
      url: localUrl("/anuncios/anuncio-exemplo"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.5
    },
    {
      url: localUrl("/anunciar"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.5
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
      url: localUrl("/aviso-seguranca-whatsapp"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.1
    },
    {
      url: localUrl("/faq"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.1
    },
    {
      url: localUrl("/consentimento-promocional"),
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.1
    }
  ];
}
