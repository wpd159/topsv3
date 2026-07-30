import { SiteContentPage } from '@/components/site-content/site-content-page'
import { buildPublicStaticMetadata } from '@/lib/seo/public-static-metadata'

export const metadata = buildPublicStaticMetadata({
  path: '/politicas/termos-conteudo-restrito',
  title: 'Termos de conteúdo restrito | Tops do Job',
  description: 'Consulte os termos aplicáveis ao acesso de conteúdo restrito no Tops do Job.',
})

export default function Page() {
  return <SiteContentPage contentKey="termos-conteudo-restrito" centered={false} />
}
