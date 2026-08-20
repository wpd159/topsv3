// src/app/layout.tsx

import type { Metadata } from "next"
import "./globals.css"

import { ConsentAwareAnalytics } from "@/components/analytics/consent-aware-analytics"
import { AuthProvider } from "@/context/AuthContext"
import { FavoritosProvider } from "@/context/FavoritosContext"
import { Toaster } from "sonner"
import { WhatsAppSafetyProvider } from "@/components/site/whatsapp-safety-provider"
import { SiteContentProvider } from "@/components/site-content/site-content-provider"
import { getUnavailableSiteContent, SITE_CONTENT_KEYS } from "@/lib/site-content"
import { serializeJsonLd } from "@/lib/seo/json-ld"
import { buildPublicUrl, getPublicSiteBaseUrl } from "@/lib/seo/public-url"
import { resolveSearchIndexingPolicy } from "@/lib/seo/search-indexing-policy"

const publicSiteBaseUrl = getPublicSiteBaseUrl()
const searchIndexingPolicy = resolveSearchIndexingPolicy()
const unavailableSiteContent = SITE_CONTENT_KEYS.map(getUnavailableSiteContent)

export const metadata: Metadata = {
  metadataBase: new URL(publicSiteBaseUrl),
  robots: searchIndexingPolicy.publicIndexingEnabled
    ? undefined
    : { index: false, follow: false, noarchive: true },
  title: "Tops do Job | Acompanhantes por cidade e bairro",
  description:
    "Encontre anúncios de acompanhantes por cidade e bairro, explore perfis publicados e acesse as formas de contato disponíveis em cada anúncio.",
  verification: {
    other: {
      "msvalidate.01": "355836399E63074CDD49A338612E13C3",
    },
  },
  openGraph: {
    title: "Tops do Job | Acompanhantes por cidade e bairro",
    description:
      "Encontre anúncios de acompanhantes por cidade e bairro, explore perfis publicados e acesse as formas de contato disponíveis em cada anúncio.",
    url: publicSiteBaseUrl,
    siteName: "Tops do Job",
    locale: "pt_BR",
    type: "website",
  },
}

const websiteSchema = {
  "@context": "https://schema.org",
  "@type": "WebSite",
  name: "Tops do Job",
  url: publicSiteBaseUrl,
  potentialAction: {
    "@type": "SearchAction",
    target: `${buildPublicUrl("/anuncios")}?busca={search_term_string}`,
    "query-input": "required name=search_term_string",
  },
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="pt-BR">
      <head>
        <meta charSet="utf-8" />
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: serializeJsonLd(websiteSchema) }}
        />
      </head>

      <body className="min-w-0 w-full antialiased bg-white text-[#111]">
        <AuthProvider>
          <FavoritosProvider>
            <SiteContentProvider entries={unavailableSiteContent}>
              <WhatsAppSafetyProvider>
                {children}
                <Toaster position="top-right" richColors closeButton expand />
              </WhatsAppSafetyProvider>
            </SiteContentProvider>
          </FavoritosProvider>
        </AuthProvider>
        <ConsentAwareAnalytics />
      </body>
    </html>
  )
}
