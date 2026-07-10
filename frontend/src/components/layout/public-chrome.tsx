import HeaderWrapper from './header-wrapper'
import Footer from './footer'
import AbrirTicketButton from './ticket-button'
import { SitePopupManager } from '@/components/site/site-popup-manager'

export function PublicChrome({ children }: { children: React.ReactNode }) {
  return (
    <>
      <HeaderWrapper />
      <SitePopupManager />

      <main className="public-shell relative max-w-[1500px] mx-auto px-4 sm:px-6 lg:px-8">
        {children}
      </main>

      <Footer />
      <AbrirTicketButton />
    </>
  )
}
