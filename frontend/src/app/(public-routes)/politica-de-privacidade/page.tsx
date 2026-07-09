import { SiteContentPage } from "@/components/site-content/site-content-page"
import { getFallbackSiteContent } from "@/lib/site-content"

export default function PoliticaPrivacidadePage() {
  const fallback = getFallbackSiteContent("politica-privacidade")

  return (
    <SiteContentPage
      contentKey="politica-privacidade"
      fallbackTitle={fallback.titulo}
      fallbackBody={fallback.corpo}
      centered={false}
    />
  )
}
