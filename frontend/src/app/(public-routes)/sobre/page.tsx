import { SiteContentPage } from "@/components/site-content/site-content-page"
import { getFallbackSiteContent } from "@/lib/site-content"

export default function SobrePage() {
  const fallback = getFallbackSiteContent("quem-somos")

  return (
    <SiteContentPage
      contentKey="quem-somos"
      fallbackTitle={fallback.titulo}
      fallbackBody={fallback.corpo}
    />
  )
}
