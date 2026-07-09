import type { MetadataRoute } from "next"

function getBaseUrl() {
  const env = process.env.NEXT_PUBLIC_SITE_URL
  return (env ?? "https://topsdojob.com").replace(/\/$/, "")
}

// Reset do frontend V3 (copia fiel do clone): o clone e o site real de
// producao e por isso indexa por padrao. Fora do dominio real
// (local, HML v3.esle.cloud etc.) mantemos bloqueio total de indexacao,
// para nao duplicar conteudo/nao vazar homologacao pro Google. Nao muda
// nada do comportamento real de producao.
const isRealProductionDomain = getBaseUrl() === "https://topsdojob.com"

export default function robots(): MetadataRoute.Robots {
  const baseUrl = getBaseUrl()

  if (!isRealProductionDomain) {
    return {
      rules: {
        userAgent: "*",
        disallow: "/",
      },
      sitemap: `${baseUrl}/sitemap.xml`,
    }
  }

  return {
    rules: [
      {
        userAgent: "*",
        allow: ["/"],
        disallow: [
          "/admin",
          "/admin/*",
          "/anunciar",
          "/minha-conta",
          "/meus-anuncios",
          "/meus-anuncios/*",
          "/meus-tickets",
          "/favoritos",
          "/indicacoes",
          "/chat",
          "/painel",
          "/painel/*",
          "/*?*filter=",
          "/*&filter=",
          "/*?*sort=",
          "/*&sort=",
          "/*?*search=",
          "/*&search=",
          "/*?*utm_",
          "/*&utm_",
        ],
        crawlDelay: 1,
      },
    ],
    sitemap: `${baseUrl}/sitemap.xml`,
  }
}
