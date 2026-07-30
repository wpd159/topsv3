import { SiteContentPage } from '@/components/site-content/site-content-page'
import { buildPublicStaticMetadata } from '@/lib/seo/public-static-metadata'

export const metadata = buildPublicStaticMetadata({
  path: '/consentimento-promocional',
  title: 'Consentimento promocional | Tops do Job',
  description: 'Consulte as regras de consentimento promocional adotadas pelo Tops do Job.',
})

export default function ConsentimentoPromocionalPage() {
  return <SiteContentPage contentKey="consentimento-promocional" centered={false} />
}
