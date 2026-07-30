import { SiteContentPage } from '@/components/site-content/site-content-page'
import { buildPublicStaticMetadata } from '@/lib/seo/public-static-metadata'

export const metadata = buildPublicStaticMetadata({
  path: '/termos-de-uso',
  title: 'Termos de uso | Tops do Job',
  description: 'Consulte os termos e condições de uso da plataforma Tops do Job.',
})

export default function TermosDeUsoPage() {
  return <SiteContentPage contentKey="termos-de-uso" centered={false} />
}
