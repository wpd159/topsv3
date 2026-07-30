import { SiteContentPage } from '@/components/site-content/site-content-page'
import { buildPublicStaticMetadata } from '@/lib/seo/public-static-metadata'

export const metadata = buildPublicStaticMetadata({
  path: '/politicas/privacidade-conteudo-restrito',
  title: 'Privacidade em conteúdo restrito | Tops do Job',
  description: 'Consulte as regras de privacidade para acesso a conteúdo restrito no Tops do Job.',
})

export default function Page() {
  return <SiteContentPage contentKey="privacidade-conteudo-restrito" centered={false} />
}
