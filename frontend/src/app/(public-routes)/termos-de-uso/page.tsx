import { SiteContentPage } from "@/components/site-content/site-content-page"
import { getFallbackSiteContent } from "@/lib/site-content"

export default function TermosDeUsoPage() {
  const fallback = getFallbackSiteContent("termos-de-uso")

  return (
    <SiteContentPage
      contentKey="termos-de-uso"
      fallbackTitle={fallback.titulo}
      fallbackBody={fallback.corpo}
      centered={false}
    />
  )
}
