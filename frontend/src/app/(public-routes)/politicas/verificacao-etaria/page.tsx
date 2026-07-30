import { SiteContentPage } from '@/components/site-content/site-content-page'
import { buildPublicStaticMetadata } from '@/lib/seo/public-static-metadata'

export const metadata = buildPublicStaticMetadata({
  path: '/politicas/verificacao-etaria',
  title: 'Verificação etária | Tops do Job',
  description: 'Entenda as regras de verificação etária para acesso a conteúdo restrito.',
})

export default function Page() {
  return <SiteContentPage contentKey="verificacao" centered={false} />
}
