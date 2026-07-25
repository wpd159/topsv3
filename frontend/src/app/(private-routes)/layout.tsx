import { PublicChrome } from '@/components/layout/public-chrome'
import { PrivateSessionGuard } from '@/components/auth/private-session-guard'
import { SiteContentProvider } from '@/components/site-content/site-content-provider'
import { resolveAllPublicSiteContent } from '@/lib/site-content'

export default async function PrivateRoutesLayout({ children }: { children: React.ReactNode }) {
  const siteContent = await resolveAllPublicSiteContent()

  return (
    <SiteContentProvider entries={siteContent}>
      <PublicChrome>
        <PrivateSessionGuard>{children}</PrivateSessionGuard>
      </PublicChrome>
    </SiteContentProvider>
  )
}
