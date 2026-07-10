// src/app/layout.tsx

import type { Metadata } from "next"
import Script from "next/script"
import "./globals.css"

import { AuthProvider } from "@/context/AuthContext"
import { Toaster } from "sonner"
import { AgeGateModal } from "@/components/modals/age-gate-modal"
import { WhatsAppSafetyProvider } from "@/components/site/whatsapp-safety-provider"
import { SensitiveImageUnlockProvider } from "@/components/compliance/sensitive-image-unlock-provider"

export const metadata: Metadata = {
  metadataBase: new URL("https://topsdojob.com"),
  title: "Tops do Job - Encontre as melhores acompanhantes",
  description:
    "Encontre acompanhantes com fotos reais, anúncios verificados e contato direto em uma plataforma segura, discreta e atualizada diariamente.",
  verification: {
    other: {
      "msvalidate.01": "355836399E63074CDD49A338612E13C3",
    },
  },
  openGraph: {
    title: "Tops do Job - Encontre as melhores acompanhantes",
    description:
      "Encontre acompanhantes com fotos reais, anúncios verificados e contato direto em uma plataforma segura, discreta e atualizada diariamente.",
    url: "https://topsdojob.com",
    siteName: "Tops do Job",
    locale: "pt_BR",
    type: "website",
  },
}

const websiteSchema = {
  "@context": "https://schema.org",
  "@type": "WebSite",
  name: "Tops do Job",
  url: "https://topsdojob.com",
  potentialAction: {
    "@type": "SearchAction",
    target: "https://topsdojob.com/anuncios?busca={search_term_string}",
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
        <Script
          src="https://www.googletagmanager.com/gtag/js?id=G-E0CNBH6WPM"
          strategy="afterInteractive"
        />
        <Script id="ga4-init" strategy="afterInteractive">
          {`
            window.dataLayer = window.dataLayer || [];
            function gtag(){dataLayer.push(arguments);}
            window.gtag = gtag;
            gtag('js', new Date());
            gtag('config', 'G-E0CNBH6WPM');
          `}
        </Script>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(websiteSchema) }}
        />
      </head>

      <body className="antialiased bg-white text-[#111] w-[100vw] overflow-x-hidden">
        <AuthProvider>
          <SensitiveImageUnlockProvider>
            <WhatsAppSafetyProvider>
              <AgeGateModal
                termsHref="/termos-de-uso"
                denyRedirect="https://www.google.com"
              />

              {children}

              <Toaster position="top-right" richColors closeButton expand />
            </WhatsAppSafetyProvider>
          </SensitiveImageUnlockProvider>
        </AuthProvider>
      </body>
    </html>
  )
}
