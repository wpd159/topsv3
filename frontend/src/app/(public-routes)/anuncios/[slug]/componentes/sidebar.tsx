'use client'

import { useMemo, useState, type MouseEvent } from 'react'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { CalendarDaysIcon, ChatBubbleLeftIcon, FlagIcon } from '@heroicons/react/24/solid'
import DenunciaModal from './denuncia-modal'
import { useWhatsAppSafety } from '@/components/site/whatsapp-safety-provider'
import { publicApiUrl } from '@/lib/api-contract'
import { VisitorVerificationModal } from '@/components/compliance/visitor-verification-modal'
import { publicCsrfHeaders } from '@/lib/compliance/age-gate-api'

const WhatsAppIcon = (props: React.SVGProps<SVGSVGElement>) => (
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" {...props}>
    <path fill="#25D366" d="M256 0C114.6 0 0 114.6 0 256c0 45.3 11.7 89.2 33.9 128.2L0 512l130.8-33.2C168.4 496.4 211.6 512 256 512c141.4 0 256-114.6 256-256S397.4 0 256 0z" />
    <path fill="#FFF" d="M391.1 358.3c-5.9 16.5-28.3 30.8-46.5 34.9-12.3 2.6-28.3 4.6-82.3-17.6-69.3-28.7-114.3-99.3-117.8-103.9-3.4-4.6-28.1-37.4-28.1-71.4 0-34 17.6-50.8 24-57.8 5.9-6.5 13-8.1 17.6-8.1 4.6 0 8.8.1 12.6.2 4 .1 9.4-.1 14.1 10.8 5.4 12.9 18.2 44.5 19.8 47.8 1.6 3.4 2.7 7.4.5 11.8-2.2 4.3-3.3 7-6.5 10.8-3.1 3.7-6.9 8.3-9.9 11.2-3.3 3.1-6.7 6.5-2.9 12.9 3.8 6.5 17 27.9 36.4 45.2 25.1 22.5 45.2 29.5 52.2 32.7 7 3.2 11.1 2.7 15.3-1.6 4.2-4.3 17.7-20.7 22.5-27.8 4.8-7 9.5-5.9 15.9-3.8 6.5 2.2 41.2 19.4 48.2 23 7 3.5 11.7 5.3 13.4 8.1 1.7 2.7 1.7 16.1-4.2 32.5z" />
  </svg>
)

type SidebarProps = {
  anuncio: {
    id: string | number
    slug: string
    nome: string
    username?: string | null
    idade?: number | null
    estadoUf?: string | null
    cidadeNome?: string | null
    bairroNome?: string | null
    pontoReferenciaTexto?: string | null
    cidade?: string | null
    localizacao?: string | null
    tipo?: string | null
    valor: string
    anunciaDesde?: string | null
  }
}

function clean(value?: string | null) {
  const normalized = (value ?? '').trim()
  if (!normalized || normalized.toLowerCase() === 'nao informado') return ''
  return normalized
}

export default function Sidebar({ anuncio }: SidebarProps) {
  const [modalAberto, setModalAberto] = useState(false)
  const [whatsappVerificationOpen, setWhatsappVerificationOpen] = useState(false)
  const router = useRouter()
  const { openWhatsAppWarning } = useWhatsAppSafety()

  const localizacaoLabel = useMemo(() => {
    const parts = [clean(anuncio.estadoUf), clean(anuncio.cidadeNome), clean(anuncio.bairroNome)].filter(Boolean)
    const ponto = clean(anuncio.pontoReferenciaTexto)
    if (parts.length) return ponto ? `${parts.join(' - ')} | ${ponto}` : parts.join(' - ')
    return ponto || clean(anuncio.localizacao) || clean(anuncio.cidade) || 'Cidade não informada'
  }, [anuncio.bairroNome, anuncio.cidade, anuncio.cidadeNome, anuncio.estadoUf, anuncio.localizacao, anuncio.pontoReferenciaTexto])

  const requestWhatsApp = async () => {
    try {
      const headers = await publicCsrfHeaders()
      const response = await fetch(publicApiUrl(`/anuncios/${encodeURIComponent(anuncio.slug)}/clique-whatsapp`), {
        method: 'POST',
        credentials: 'include',
        headers,
        body: '{}',
      })
      const payload = await response.json().catch(() => null)
      if (response.status === 403) {
        setWhatsappVerificationOpen(true)
        return
      }
      if (!response.ok || !payload?.disponivel || typeof payload.whatsappUrl !== 'string') {
        toast.error(payload?.message || payload?.error || 'Contato indisponível para este anúncio.')
        return
      }
      openWhatsAppWarning({ url: payload.whatsappUrl })
    } catch {
      toast.error('Não foi possível acessar o WhatsApp agora.')
    }
  }

  const handleWhatsAppClick = (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault()
    event.stopPropagation()
    void requestWhatsApp()
  }

  const usernameLabel = clean(anuncio.username)
    ? `@${clean(anuncio.username).replace(/^@+/, '')}`
    : '@usuario'
  const categoria = clean(anuncio.tipo).toLowerCase().replace(/_/g, ' ').replace(/^\w/, (char) => char.toUpperCase())
  const anunciaDesde = useMemo(() => {
    if (!anuncio.anunciaDesde) return null
    const date = new Date(anuncio.anunciaDesde)
    if (Number.isNaN(date.getTime())) return null
    return new Intl.DateTimeFormat('pt-BR', { month: 'short', year: 'numeric', timeZone: 'UTC' })
      .format(date)
      .replace(' de ', '/')
      .replace('.', '')
  }, [anuncio.anunciaDesde])

  return (
    <>
      <div className="public-contact-cta min-w-0 space-y-6 lg:sticky lg:top-24">
        <div className="rounded-xl border border-pink-100 bg-white p-5 shadow-[0_0_20px_rgba(252,30,173,0.10)]">
          <div className="mb-4 space-y-1">
            <h2 className="flex flex-wrap items-baseline gap-x-1 text-lg font-semibold text-gray-900">
              <span>{usernameLabel}</span>
            </h2>
            {anunciaDesde ? (
              <p className="flex items-center gap-1.5 text-xs text-gray-500">
                <CalendarDaysIcon className="h-4 w-4 text-pink-400" />
                Anuncia desde {anunciaDesde}
              </p>
            ) : null}
          </div>
          <div className="space-y-1 text-sm text-gray-600">
            <p>{categoria || 'Não informado'}</p>
            <p>{localizacaoLabel}</p>
            <p className="mt-2 font-semibold text-pink-600">{anuncio.valor}</p>
          </div>

          <div className="mt-5 flex flex-col gap-3">
            <Button variant="outline" className="flex h-12 w-full items-center justify-center border-emerald-200 font-semibold shadow-[0_0_14px_rgba(37,211,102,0.12)]" onClick={handleWhatsAppClick}>
              <span className="inline-flex items-center"><WhatsAppIcon className="mr-2 h-5 w-5" /> Conversar no WhatsApp</span>
            </Button>
            <Button
              type="button"
              disabled={!anuncio.username}
              onClick={() => anuncio.username && router.push(`/chat?usuario=${encodeURIComponent(anuncio.username)}`)}
              className="flex h-12 w-full items-center justify-center bg-[#FC1EAD] font-semibold text-white shadow-[0_0_18px_rgba(252,30,173,0.22)] hover:bg-[#e01a9a]"
            >
              <ChatBubbleLeftIcon className="mr-2 h-5 w-5" /> Conversar na plataforma
            </Button>
          </div>

          <button className="mx-auto mt-3 flex items-center gap-1 text-xs text-gray-400 transition hover:text-gray-600" onClick={() => setModalAberto(true)}>
            <FlagIcon className="h-3.5 w-3.5" /> Denunciar anúncio
          </button>
        </div>
      </div>

      <DenunciaModal open={modalAberto} onOpenChange={setModalAberto} anuncioId={anuncio.id} slug={anuncio.slug} />
      <VisitorVerificationModal
        open={whatsappVerificationOpen}
        level="REINFORCED"
        scope="WHATSAPP"
        context={{
          anuncioId: anuncio.id,
          route: `/anuncios/${anuncio.slug}`,
        }}
        onOpenChange={setWhatsappVerificationOpen}
        onVerified={() => {
          setWhatsappVerificationOpen(false)
          void requestWhatsApp()
        }}
      />
    </>
  )
}
