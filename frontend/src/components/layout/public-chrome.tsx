import HeaderWrapper from './header-wrapper'
import Footer from './footer'
import AbrirTicketButton from './ticket-button'

export function PublicChrome({ children }: { children: React.ReactNode }) {
  return (
    <>
      <div id="topo" aria-hidden="true" />
      <HeaderWrapper />

      <main className="public-shell relative mx-auto min-h-[calc(100svh-89px)] max-w-[1500px] px-4 sm:px-6 md:min-h-[calc(100svh-129px)] lg:px-8">
        {children}
      </main>

      <Footer />
      <AbrirTicketButton />
    </>
  )
}
