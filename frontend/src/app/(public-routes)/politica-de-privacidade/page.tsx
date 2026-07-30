import { SiteContentPage } from '@/components/site-content/site-content-page'
import { buildPublicStaticMetadata } from '@/lib/seo/public-static-metadata'

export const metadata = buildPublicStaticMetadata({
  path: '/politica-de-privacidade',
  title: 'Política de privacidade | Tops do Job',
  description: 'Consulte como o Tops do Job trata dados pessoais e protege a privacidade.',
})

export default function PoliticaPrivacidadePage() {
  return <SiteContentPage contentKey="politica-privacidade" centered={false} />
}
