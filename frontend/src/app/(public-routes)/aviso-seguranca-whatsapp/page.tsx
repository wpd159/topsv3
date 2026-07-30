import { SiteContentPage } from '@/components/site-content/site-content-page'
import { buildPublicStaticMetadata } from '@/lib/seo/public-static-metadata'

export const metadata = buildPublicStaticMetadata({
  path: '/aviso-seguranca-whatsapp',
  title: 'Segurança no WhatsApp | Tops do Job',
  description: 'Orientações de segurança para contatos realizados pelo WhatsApp no Tops do Job.',
})

export default function AvisoSegurancaWhatsappPage() {
  return <SiteContentPage contentKey="texto-whatsapp" centered={false} />
}
