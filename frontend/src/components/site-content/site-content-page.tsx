import { SafeSiteContentBody } from '@/components/site-content/safe-site-content-body'
import {
  resolvePublicSiteContent,
  type SiteContentKey,
} from '@/lib/site-content'

export async function SiteContentPage({
  contentKey,
  centered = true,
}: {
  contentKey: SiteContentKey
  centered?: boolean
}) {
  const content = await resolvePublicSiteContent(contentKey)

  return (
    <section className="mx-auto max-w-5xl space-y-6 px-6 py-10">
      <h1
        className={`text-2xl font-extrabold leading-tight text-gray-900 md:text-4xl ${
          centered ? 'text-center' : ''
        }`}
      >
        {content.titulo}
      </h1>

      <SafeSiteContentBody
        content={content.corpo}
        centered={centered}
        unavailable={content.unavailable}
      />
    </section>
  )
}
