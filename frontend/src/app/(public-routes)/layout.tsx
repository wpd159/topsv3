import { PublicChrome } from '@/components/layout/public-chrome'
import { AgeGateModal } from '@/components/modals/age-gate-modal'
import { SiteContentProvider } from '@/components/site-content/site-content-provider'
import { WhatsAppSafetyProvider } from '@/components/site/whatsapp-safety-provider'
import { resolveAllPublicSiteContent } from '@/lib/site-content'

export default async function PublicRoutesLayout({ children }: { children: React.ReactNode }) {
  const siteContent = await resolveAllPublicSiteContent()
  const whatsappContent = siteContent.find((entry) => entry.contentKey === 'texto-whatsapp')

  return (
    <SiteContentProvider entries={siteContent}>
      <WhatsAppSafetyProvider content={whatsappContent}>
        <AgeGateModal
          termsHref="/termos-de-uso"
          denyRedirect="https://www.google.com"
        />
        <PublicChrome>{children}</PublicChrome>
      </WhatsAppSafetyProvider>
    </SiteContentProvider>
  )
}
