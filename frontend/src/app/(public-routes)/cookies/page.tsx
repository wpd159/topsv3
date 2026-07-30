import { CookiePreferences } from '@/app/(public-routes)/cookies/cookie-preferences'
import { SafeSiteContentBody } from '@/components/site-content/safe-site-content-body'
import { resolvePublicSiteContent } from '@/lib/site-content'
import { buildPublicStaticMetadata } from '@/lib/seo/public-static-metadata'

export const metadata = buildPublicStaticMetadata({
  path: '/cookies',
  title: 'Política de cookies | Tops do Job',
  description: 'Entenda como o Tops do Job utiliza cookies e gerencie suas preferências.',
})

export default async function CookiesPage() {
  const content = await resolvePublicSiteContent('politica-cookies')

  return (
    <section className="mx-auto max-w-5xl px-6 py-10">
      <header className="mb-8 space-y-4">
        <h1 className="text-3xl font-bold tracking-tight text-gray-900">{content.titulo}</h1>
        <SafeSiteContentBody
          content={content.corpo}
          unavailable={content.unavailable}
        />
      </header>
      <CookiePreferences />
    </section>
  )
}
