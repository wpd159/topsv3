import { SiteContentPage } from '@/components/site-content/site-content-page'
import { buildPublicStaticMetadata } from '@/lib/seo/public-static-metadata'

export const metadata = buildPublicStaticMetadata({
  path: '/politicas/aviso-legal-conteudo-restrito',
  title: 'Aviso legal sobre conteúdo restrito | Tops do Job',
  description: 'Consulte o aviso legal aplicável ao acesso de conteúdo restrito no Tops do Job.',
})

export default function Page() {
  return <SiteContentPage contentKey="aviso-legal-conteudo-restrito" centered={false} />
}
