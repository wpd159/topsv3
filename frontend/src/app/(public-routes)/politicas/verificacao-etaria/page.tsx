import { SiteContentPage } from "@/components/site-content/site-content-page"
import { getFallbackSiteContent } from "@/lib/site-content"

export default function Page() {
  const fallback = getFallbackSiteContent("verificacao")

  return (
    <SiteContentPage
      contentKey="verificacao"
      fallbackTitle={fallback.titulo}
      fallbackBody={fallback.corpo}
      centered={false}
    />
  )
}
