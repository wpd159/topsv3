import { SiteContentPage } from "@/components/site-content/site-content-page"
import { getFallbackSiteContent } from "@/lib/site-content"

export default function ConsentimentoPromocionalPage() {
  const fallback = getFallbackSiteContent("consentimento-promocional")

  return (
    <SiteContentPage
      contentKey="consentimento-promocional"
      fallbackTitle={fallback.titulo}
      fallbackBody={fallback.corpo}
      centered={false}
    />
  )
}
