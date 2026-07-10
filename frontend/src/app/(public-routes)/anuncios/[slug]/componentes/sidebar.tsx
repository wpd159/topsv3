'use client'

import { useMemo, useState, type MouseEvent } from 'react'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { ChatBubbleLeftIcon, FlagIcon } from '@heroicons/react/24/solid'
import DenunciaModal from './denuncia-modal'
import { useWhatsAppSafety } from '@/components/site/whatsapp-safety-provider'

const WhatsAppIcon = (props: React.SVGProps<SVGSVGElement>) => (
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" {...props}>
    <path fill="#25D366" d="M256 0C114.6 0 0 114.6 0 256c0 45.3 11.7 89.2 33.9 128.2L0 512l130.8-33.2C168.4 496.4 211.6 512 256 512c141.4 0 256-114.6 256-256S397.4 0 256 0z" />
    <path fill="#FFF" d="M391.1 358.3c-5.9 16.5-28.3 30.8-46.5 34.9-12.3 2.6-28.3 4.6-82.3-17.6-69.3-28.7-114.3-99.3-117.8-103.9-3.4-4.6-28.1-37.4-28.1-71.4 0-34 17.6-50.8 24-57.8 5.9-6.5 13-8.1 17.6-8.1 4.6 0 8.8.1 12.6.2 4 .1 9.4-.1 14.1 10.8 5.4 12.9 18.2 44.5 19.8 47.8 1.6 3.4 2.7 7.4.5 11.8-2.2 4.3-3.3 7-6.5 10.8-3.1 3.7-6.9 8.3-9.9 11.2-3.3 3.1-6.7 6.5-2.9 12.9 3.8 6.5 17 27.9 36.4 45.2 25.1 22.5 45.2 29.5 52.2 32.7 7 3.2 11.1 2.7 15.3-1.6 4.2-4.3 17.7-20.7 22.5-27.8 4.8-7 9.5-5.9 15.9-3.8 6.5 2.2 41.2 19.4 48.2 23 7 3.5 11.7 5.3 13.4 8.1 1.7 2.7 1.7 16.1-4.2 32.5z" />
  </svg>
)

type SidebarProps = {
  anuncio: {
    id: number
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
  }
}

function clean(value?: string | null) {
  const normalized = (value ?? '').trim()
  if (!normalized || normalized.toLowerCase() === 'nao informado') return ''
  return normalized
}

export default function Sidebar({ anuncio }: SidebarProps) {
  const [modalAberto, setModalAberto] = useState(false)
  const router = useRouter()
  const api = process.env.NEXT_PUBLIC_API_URL
  const { openWhatsAppWarning } = useWhatsAppSafety()

  const localizacaoLabel = useMemo(() => {
    const parts = [clean(anuncio.estadoUf), clean(anuncio.cidadeNome), clean(anuncio.bairroNome)].filter(Boolean)
    const ponto = clean(anuncio.pontoReferenciaTexto)
    if (parts.length) return ponto ? `${parts.join(' - ')} | ${ponto}` : parts.join(' - ')
    return ponto || clean(anuncio.localizacao) || clean(anuncio.cidade) || 'Cidade não informada'
  }, [anuncio.bairroNome, anuncio.cidade, anuncio.cidadeNome, anuncio.estadoUf, anuncio.localizacao, anuncio.pontoReferenciaTexto])

  const handleWhatsAppClick = async (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault()
    event.stopPropagation()
    if (!api) {
      toast.error('Contato indisponível no momento.')
      return
    }

    try {
      const response = await fetch(`${api}/api/public/anuncios/${encodeURIComponent(anuncio.slug)}/clique-whatsapp`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: '{}',
      })
      const payload = await response.json().catch(() => null)
      if (!response.ok || !payload?.disponivel || typeof payload.whatsappUrl !== 'string') {
        toast.error(payload?.message || payload?.error || 'Contato indisponível para este anúncio.')
        return
      }
      openWhatsAppWarning({ url: payload.whatsappUrl })
    } catch {
      toast.error('Não foi possível acessar o WhatsApp agora.')
    }
  }

  const usernameLabel = clean(anuncio.username)
    ? `@${clean(anuncio.username).replace(/^@+/, '')}`
    : '@usuario'
  const categoria = clean(anuncio.tipo).toLowerCase().replace(/_/g, ' ').replace(/^\w/, (char) => char.toUpperCase())

  return (
    <>
      <div className="public-contact-cta min-w-0 space-y-6">
        <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
          <div className="mb-4"><h2 className="text-lg font-semibold text-gray-900">{usernameLabel}</h2></div>
          <div className="space-y-1 text-sm text-gray-600">
            {anuncio.idade != null ? <p className="font-medium text-gray-700">{anuncio.idade} anos</p> : null}
            <p>{categoria || 'Não informado'}</p>
            <p>{localizacaoLabel}</p>
            <p className="mt-2 font-semibold text-pink-600">{anuncio.valor}</p>
          </div>

          <div className="mt-5 flex flex-col gap-3">
            <Button variant="outline" className="flex w-full items-center justify-center py-3 font-semibold" onClick={handleWhatsAppClick}>
              <span className="inline-flex items-center"><WhatsAppIcon className="mr-2 h-5 w-5" /> Conversar no WhatsApp</span>
            </Button>
            <Button
              type="button"
              disabled={!anuncio.username}
              onClick={() => anuncio.username && router.push(`/chat?usuario=${encodeURIComponent(anuncio.username)}`)}
              className="flex w-full items-center justify-center bg-[#FC1EAD] py-3 font-semibold text-white hover:bg-[#e01a9a]"
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
    </>
  )
}
