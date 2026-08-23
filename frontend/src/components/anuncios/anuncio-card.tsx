"use client"

import Image from "next/image"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useEffect, useMemo, useRef, useState, type MouseEvent } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { SensitiveImage } from "@/components/compliance/sensitive-image"
import { VisitorVerificationModal } from "@/components/compliance/visitor-verification-modal"
import { FavoritoButton } from "@/components/anuncios/favorito-button"
import { AnuncioCardVideo } from "@/components/anuncios/anuncio-card-video"
import {
  fontePublicaSegura,
  selecionarCapaVideoCard,
  selecionarGaleriaPublicaSegura,
  type MidiaPublica,
} from "@/lib/media/public-media"
import { useWhatsAppSafety } from "@/components/site/whatsapp-safety-provider"
import { useAuth } from "@/context/AuthContext"
import { corrigirTextoCorrompido } from "@/lib/text/encoding"
import { ApiContractError } from "@/lib/api-contract"
import {
  novaChaveMetricaPublica,
  registrarCliqueWhatsappPublico,
} from "@/lib/public-metrics-api"
import {
  formatarVisualizacoesCanonicas,
  type VisualizacoesCanonicas,
} from "@/lib/visualizacoes-canonicas"
import {
  MapPinIcon,
  ChatBubbleLeftIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  EyeIcon,
  PhotoIcon,
  PlayCircleIcon,
  CalendarDaysIcon,
} from "@heroicons/react/24/solid"

type AnuncioCardProps = {
  id: string | number
  slug: string
  nome: string
  estadoUf?: string | null
  cidadeNome?: string | null
  bairroNome?: string | null
  pontoReferenciaTexto?: string | null
  idade?: number | null
  valor: string
  midias?: MidiaPublica[]
  previewImagens?: string[]
  descricao?: string | null
  nomeAnunciante?: string
  usernameAnunciante?: string
  destaque?: boolean
  visualizacoes?: VisualizacoesCanonicas | null
  carrosselDisponivel?: boolean
  videoHabilitado?: boolean
  whatsappCardEnabled?: boolean
  comLocal?: boolean
  fazAnal?: boolean
  anunciaDesde?: string | null
  onAccessUpdated?: () => void
  previewMode?: boolean
  mediaPriority?: boolean
}

function clean(v?: string | null) {
  const s = corrigirTextoCorrompido(v ?? "").trim()
  if (!s) return ""
  const low = s.toLowerCase()
  if (low === "não informado" || low === "nao informado") return ""
  return s
}

function EmptyMediaState({ priority = false }: { priority?: boolean }) {
  return (
    <div className="absolute inset-0 overflow-hidden bg-gradient-to-br from-slate-50 to-slate-100">
      <Image
        src="/icone-sem-foto.png"
        alt="Perfil sem fotos"
        fill
        priority={priority}
        sizes="(max-width: 768px) 100vw, 360px"
        className="object-cover object-center opacity-90"
      />
      <div className="absolute inset-0 bg-white/15 backdrop-blur-[1px]" />
      <div className="absolute inset-x-4 bottom-4 rounded-2xl border border-white/60 bg-white/88 p-3 text-center shadow-sm">
        <div className="flex items-center justify-center gap-2 text-[12px] font-medium text-slate-700">
          <PhotoIcon className="h-4 w-4 text-slate-500" />
          Galeria em atualização
        </div>

        <div className="mt-2 flex items-center justify-center gap-2 text-[11px] text-slate-500">
          <PlayCircleIcon className="h-4 w-4 text-slate-400" />
          <span>Abra o perfil para ver os detalhes disponíveis.</span>
        </div>
      </div>
    </div>
  )
}

export function AnuncioCard({
  id,
  slug,
  nome,
  estadoUf,
  cidadeNome,
  bairroNome,
  pontoReferenciaTexto,
  idade,
  valor,
  midias = [],
  previewImagens = [],
  descricao,
  usernameAnunciante,
  destaque = false,
  visualizacoes,
  carrosselDisponivel = false,
  videoHabilitado = false,
  whatsappCardEnabled = false,
  comLocal = false,
  fazAnal = false,
  anunciaDesde,
  onAccessUpdated,
  previewMode = false,
  mediaPriority = false,
}: AnuncioCardProps) {
  const router = useRouter()
  const { usuario } = useAuth()
  const { openWhatsAppWarning } = useWhatsAppSafety()

  const localizacaoLabel = useMemo(() => {
    const uf = clean(estadoUf)
    const cidade = clean(cidadeNome)
    const bairro = clean(bairroNome)
    const ponto = clean(pontoReferenciaTexto)

    if (!uf && !bairro && cidade) return ponto ? `${cidade} | ${ponto}` : cidade
    const parts = [uf, cidade, bairro].filter(Boolean)
    const base = parts.length ? parts.join(" - ") : "Não informado"
    return ponto ? `${base} · ${ponto}` : base
  }, [estadoUf, cidadeNome, bairroNome, pontoReferenciaTexto])

  /**
   * Slug vazio (`""`) não é substituído por `??` na grid — gerava `/anuncios/` e quebrava a rota.
   * Vídeo com `stopPropagation` impedia o clique na mídia de acionar o `div` pai.
   */
  const slugRota = useMemo(() => {
    const s = (slug ?? "").trim()
    if (s.length > 0) return s
    return (nome ?? "anuncio")
      .toLowerCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/\s+/g, "-")
      .replace(/[^a-z0-9-]/g, "")
      .replace(/-+/g, "-")
      .replace(/^-|-$/g, "") || "anuncio"
  }, [slug, nome])

  const [badSrcs, setBadSrcs] = useState<Set<string>>(new Set())
  const [whatsappVerificationOpen, setWhatsappVerificationOpen] = useState(false)
  const [whatsappPending, setWhatsappPending] = useState(false)
  const [chatPending, setChatPending] = useState(false)
  const whatsappRequestKey = useRef<string | null>(null)
  const whatsappInFlight = useRef(false)

  const markBad = (src: string) => {
    if (!src) return
    setBadSrcs((prev) => {
      if (prev.has(src)) return prev
      const next = new Set(prev)
      next.add(src)
      return next
    })
  }

  const midiasSeguras = useMemo(() => {
    if (previewMode) {
      return previewImagens.filter(Boolean).map((urlPublica, ordem) => ({
        id: `preview-${ordem}`,
        tipo: "FOTO" as const,
        finalidade: ordem === 0 ? "CAPA" as const : "GALERIA" as const,
        ordem,
        visibilidadeMidia: "LIVRE" as const,
        autorizada: true,
        urlPublica,
      }))
    }
    return selecionarGaleriaPublicaSegura(midias).filter((midia) => {
      if (midia.tipo === "VIDEO" && !videoHabilitado) return false
      const fonte = fontePublicaSegura(midia)
      return fonte === null || !badSrcs.has(fonte)
    })
  }, [badSrcs, midias, previewImagens, previewMode, videoHabilitado])

  const [index, setIndex] = useState(0)
  const midiaAtual = midiasSeguras[index]
  const capaVideoAtual = useMemo(() => {
    if (midiaAtual?.tipo !== "VIDEO") return null
    return selecionarCapaVideoCard({
      video: midiaAtual,
      midiasDoAnuncio: midiasSeguras,
      autorizacaoValida: midiaAtual.autorizada,
      altText: `Capa do vídeo de ${corrigirTextoCorrompido(nome)}`,
    })
  }, [midiaAtual, midiasSeguras, nome])
  const visualizacoesLabel = formatarVisualizacoesCanonicas(visualizacoes)

  useEffect(() => {
    if (!midiasSeguras.length) {
      if (index !== 0) setIndex(0)
      return
    }
    if (index > midiasSeguras.length - 1) setIndex(0)
  }, [midiasSeguras.length, index])

  useEffect(() => {
    if ((!carrosselDisponivel && !videoHabilitado)
      || midiasSeguras.length <= 1
      || typeof window === "undefined") return

    const candidates = [
      midiasSeguras[(index + 1) % midiasSeguras.length],
      midiasSeguras[(index - 1 + midiasSeguras.length) % midiasSeguras.length],
    ]

    candidates.forEach((media) => {
      if (media?.tipo !== "FOTO") return
      const fonte = media ? fontePublicaSegura(media) : null
      if (!fonte || fonte === capaVideoAtual?.url) return
      const img = new window.Image()
      img.src = fonte
    })
  }, [capaVideoAtual?.url, carrosselDisponivel, index, midiasSeguras, videoHabilitado])

  const next = () => {
    if (!midiasSeguras.length) return
    setIndex((i) => (i + 1) % midiasSeguras.length)
  }

  const prev = () => {
    if (!midiasSeguras.length) return
    setIndex((i) => (i - 1 + midiasSeguras.length) % midiasSeguras.length)
  }

  const requestWhatsApp = async () => {
    if (previewMode) return
    if (typeof window !== "undefined" && (window as any).gtag) {
      ;(window as any).gtag("event", "click_whatsapp", {
        event_category: "engagement",
        event_label: slugRota,
      })
    }

    if (whatsappInFlight.current) return
    whatsappInFlight.current = true
    setWhatsappPending(true)
    const idempotencyKey = whatsappRequestKey.current ?? novaChaveMetricaPublica("clique-whatsapp")
    whatsappRequestKey.current = idempotencyKey
    try {
      const payload = await registrarCliqueWhatsappPublico(slugRota, idempotencyKey)
      if (!payload.disponivel || typeof payload.whatsappUrl !== "string") {
        whatsappRequestKey.current = null
        toast.error("Contato indisponível para este anúncio.")
        return
      }
      if (!payload.registrado) {
        toast.warning("WhatsApp liberado; a contagem ficará pendente para nova tentativa.")
      }
      whatsappRequestKey.current = null
      openWhatsAppWarning({ url: payload.whatsappUrl })
    } catch (error) {
      if (error instanceof ApiContractError && error.status === 403) {
        setWhatsappVerificationOpen(true)
        return
      }
      whatsappRequestKey.current = null
      toast.error(error instanceof Error ? error.message : "Não foi possível validar o acesso ao WhatsApp agora.")
    } finally {
      whatsappInFlight.current = false
      setWhatsappPending(false)
    }
  }

  const handleWhatsAppClick = (e: MouseEvent<HTMLButtonElement>) => {
    e.stopPropagation()
    void requestWhatsApp()
  }

  const handleChatClick = (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault()
    event.stopPropagation()
    if (previewMode) return
    if (chatPending) return
    setChatPending(true)
    router.push(`/chat?anuncio=${encodeURIComponent(String(id))}`)
  }

  const handleVerAnuncio = () => {
    if (previewMode) return
    /*
     * Contagem oficial: POST /anuncios/{id}/visualizar na página pública do anúncio (anuncio-detalhes).
     * Evita duplicar view (card + detalhe) e cobre abertura direta por URL.
     */
    router.push(`/anuncios/${encodeURIComponent(slugRota)}`)
  }

  const anuncioProprio = Boolean(
    usuario?.username
      && usernameAnunciante
      && usuario.username.replace(/^@+/, '').toLowerCase()
        === usernameAnunciante.replace(/^@+/, '').toLowerCase()
  )

  const anuncioHref = `/anuncios/${encodeURIComponent(slugRota)}`
  const nomeExibido = corrigirTextoCorrompido(nome)
  const nomeComIdade = idade != null ? `${nomeExibido}, ${idade} anos` : nomeExibido
  const altFoto = cidadeNome
    ? `Foto de perfil de ${nomeExibido} em ${clean(cidadeNome)}${clean(estadoUf) ? `, ${clean(estadoUf)}` : ""}`
    : `Foto de perfil de ${nomeExibido}`
  const descricaoExibida = corrigirTextoCorrompido(
    descricao ?? "Anúncio sem descrição ainda. Abra para ver mais detalhes."
  )
  const anunciaDesdeLabel = useMemo(() => {
    if (!anunciaDesde) return null
    const date = new Date(anunciaDesde)
    if (Number.isNaN(date.getTime())) return null
    return new Intl.DateTimeFormat("pt-BR", { month: "short", year: "numeric", timeZone: "UTC" })
      .format(date)
      .replace(" de ", "/")
      .replace(".", "")
  }, [anunciaDesde])

  return (
    <>
    <div
      className={`public-anuncio-card group relative mx-auto flex h-full w-full max-w-[360px] flex-col rounded-xl bg-white shadow-[0_0_18px_rgba(252,30,173,0.10)] transition-all duration-300 hover:-translate-y-0.5 hover:shadow-[0_0_26px_rgba(252,30,173,0.20)] ${
        destaque
          ? "border-2 border-pink-500"
          : "border border-pink-100 hover:border-pink-200"
      }`}
    >
      <div className="relative aspect-[3/4] w-full overflow-hidden rounded-t-xl bg-gray-50">
        {midiaAtual ? (
          midiaAtual.tipo === "VIDEO" ? (
            <AnuncioCardVideo
              key={String(midiaAtual.id)}
              midia={midiaAtual}
              anuncioId={id}
              anuncioSlug={slugRota}
              capa={capaVideoAtual ?? undefined}
              priority={mediaPriority}
              onVerificationSuccess={onAccessUpdated}
            />
          ) : (
            <div className="absolute inset-0">
              <SensitiveImage
                midia={midiaAtual}
                anuncioId={id}
                anuncioSlug={slugRota}
                alt={altFoto}
                fill
                sizes="(max-width: 768px) 100vw, 360px"
                priority={mediaPriority}
                className="transition-transform duration-500 group-hover:scale-[1.02]"
                onVerificationSuccess={onAccessUpdated}
                onAbrirPaginaDoAnuncio={handleVerAnuncio}
                onError={() => {
                  const fonte = fontePublicaSegura(midiaAtual)
                  if (fonte) markBad(fonte)
                }}
              />
            </div>
          )
        ) : (
          <EmptyMediaState priority={mediaPriority} />
        )}

        {!previewMode && (
          <Link
            href={anuncioHref}
            prefetch={false}
            aria-label={`Abrir anúncio de ${nomeExibido}`}
            className="absolute inset-0 z-[5] cursor-pointer"
          >
            <span className="sr-only">Abrir anúncio de {nomeExibido}</span>
          </Link>
        )}

        {!previewMode && (
          <FavoritoButton slug={slugRota} className="absolute right-3 top-3 z-20" />
        )}

        <div className="absolute left-3 top-3 z-20 flex min-h-7 max-w-[calc(100%-4.5rem)] flex-wrap items-start gap-1.5">
            {destaque ? (
              <span className="rounded-md border border-pink-300/70 bg-gradient-to-r from-pink-600 to-pink-500 px-3 py-1 text-[11px] font-semibold text-white shadow-[0_0_14px_rgba(252,30,173,0.40)]">
                Destaque
              </span>
            ) : null}
            {comLocal ? (
              <span className="rounded-md border border-pink-200 bg-white/95 px-2.5 py-1 text-[11px] font-semibold text-pink-700 shadow-[0_0_11px_rgba(252,30,173,0.24)]">
                Com local
              </span>
            ) : null}
            {fazAnal ? (
              <span className="rounded-md border border-pink-200 bg-white/95 px-2.5 py-1 text-[11px] font-semibold text-pink-700 shadow-[0_0_11px_rgba(252,30,173,0.24)]">
                Faz anal
              </span>
            ) : null}
        </div>

        {!previewMode
          && (carrosselDisponivel || videoHabilitado)
          && midiasSeguras.length > 1 && (
          <>
            <button
              type="button"
              onClick={(e) => {
                e.preventDefault()
                e.stopPropagation()
                prev()
              }}
              className="absolute left-2 top-1/2 z-20 -translate-y-1/2 cursor-pointer rounded-full bg-white/70 p-1 hover:bg-white"
              aria-label="Mídia anterior"
            >
              <ChevronLeftIcon className="h-5 w-5 text-gray-700" />
            </button>
            <button
              type="button"
              onClick={(e) => {
                e.preventDefault()
                e.stopPropagation()
                next()
              }}
              className="absolute right-2 top-1/2 z-20 -translate-y-1/2 cursor-pointer rounded-full bg-white/70 p-1 hover:bg-white"
              aria-label="Próxima mídia"
            >
              <ChevronRightIcon className="h-5 w-5 text-gray-700" />
            </button>
          </>
        )}
      </div>

      <div className="flex min-h-[224px] flex-1 flex-col justify-between space-y-3 p-3">
        <div className="flex flex-col gap-1">
          <h3 className="line-clamp-1 text-base font-semibold leading-tight text-gray-900">
            {previewMode ? (
              <span>{nomeComIdade}</span>
            ) : (
              <Link href={anuncioHref} prefetch={false} className="hover:text-pink-600">
                {nomeComIdade}
              </Link>
            )}
          </h3>

          <div className="flex flex-wrap items-center text-xs text-gray-500">
            <MapPinIcon className="mr-1 h-4 w-4" />
            <span>{localizacaoLabel}</span>
          </div>

          {anunciaDesdeLabel ? (
            <p className="flex items-center gap-1.5 text-xs text-gray-500">
              <CalendarDaysIcon className="h-4 w-4 text-pink-400" />
              Anuncia desde {anunciaDesdeLabel}
            </p>
          ) : null}

          {!previewMode && (
            <div className="mt-1 flex items-center gap-1 text-xs text-gray-500">
              <EyeIcon className="h-4 w-4 text-gray-400" />
              <span>{visualizacoesLabel} visualizações</span>
            </div>
          )}

          <p className="mt-1 line-clamp-2 min-h-10 text-xs leading-5 text-gray-600">{descricaoExibida}</p>
        </div>

        <div className="flex flex-col gap-2 border-t border-gray-100 pt-3">
          <p className="text-sm font-bold text-pink-600">{valor}</p>

          <div className="flex flex-wrap gap-2">
            {whatsappCardEnabled && !previewMode && (
              <Button
                disabled={whatsappPending}
                className="flex-1 bg-[#25D366] px-3 py-1 text-xs font-medium text-white hover:bg-[#20bd5a]"
                onClick={handleWhatsAppClick}
              >
                WhatsApp
              </Button>
            )}

            {!previewMode && !anuncioProprio && (
              <Button
                onClick={handleChatClick}
                disabled={chatPending}
                className="flex-1 items-center justify-center gap-1 bg-[#FC1EAD] px-3 py-1 text-xs font-medium text-white hover:bg-[#e01a9a]"
              >
                <ChatBubbleLeftIcon className="h-4 w-4" />
                {chatPending ? 'Abrindo conversa...' : 'Chat'}
              </Button>
            )}

            {previewMode ? (
              <div className="flex-1 rounded-md bg-gray-100 px-3 py-1 text-center text-xs font-medium text-gray-800">
                Ver anúncio
              </div>
            ) : (
              <Button
                asChild
                className="flex-1 bg-gray-100 px-3 py-1 text-xs font-medium text-gray-800 hover:bg-gray-200"
              >
                <Link href={anuncioHref} prefetch={false}>Ver anúncio</Link>
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
    <VisitorVerificationModal
      open={whatsappVerificationOpen}
      level="REINFORCED"
      scope="WHATSAPP"
      context={{
        anuncioId: id,
        route: `/anuncios/${slugRota}`,
      }}
      onOpenChange={(open) => {
        setWhatsappVerificationOpen(open)
        if (!open) whatsappRequestKey.current = null
      }}
      onVerified={() => {
        setWhatsappVerificationOpen(false)
        onAccessUpdated?.()
        void requestWhatsApp()
      }}
    />
    </>
  )
}

export default AnuncioCard
