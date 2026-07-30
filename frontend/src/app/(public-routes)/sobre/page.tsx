import { SiteContentPage } from '@/components/site-content/site-content-page'
import { buildPublicStaticMetadata } from '@/lib/seo/public-static-metadata'

export const metadata = buildPublicStaticMetadata({
  path: '/sobre',
  title: 'Sobre o Tops do Job',
  description: 'Conheça o Tops do Job, sua proposta e os compromissos da plataforma.',
})

export default function SobrePage() {
  return <SiteContentPage contentKey="quem-somos" />
}
