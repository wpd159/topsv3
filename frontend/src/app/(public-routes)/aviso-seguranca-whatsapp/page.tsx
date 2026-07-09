import { SiteContentPage } from "@/components/site-content/site-content-page"
import { getFallbackSiteContent } from "@/lib/site-content"

export default function AvisoSegurancaWhatsappPage() {
  const fallback = getFallbackSiteContent("texto-whatsapp")

  return (
    <SiteContentPage
      contentKey="texto-whatsapp"
      fallbackTitle={fallback.titulo}
      fallbackBody={fallback.corpo}
      centered={false}
    />
  )
}
