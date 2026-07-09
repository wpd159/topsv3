"use client"

import Image from "next/image"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useEffect, useMemo, useState, type MouseEvent } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { SensitiveImage } from "@/components/compliance/sensitive-image"
import { cn } from "@/lib/utils"
import { useWhatsAppSafety } from "@/components/site/whatsapp-safety-provider"
import { corrigirTextoCorrompido } from "@/lib/text/encoding"
import {
  MapPinIcon,
  ChatBubbleLeftIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  HeartIcon as HeartSolid,
  EyeIcon,
  PhotoIcon,
  PlayCircleIcon,
} from "@heroicons/react/24/solid"

type AnuncioCardProps = {
  id: number
  slug: string
  nome: string
  estadoUf?: string | null
  cidadeNome?: string | null
  bairroNome?: string | null
  pontoReferenciaTexto?: string | null
  idade?: number | null
  valor: string
  imagens: string[]
  videos?: string[]
  descricao?: string
  telefone?: string
  nomeAnunciante?: string
  usernameAnunciante?: string
  favoritoInicial?: boolean
  onDesfavoritar?: (nome: string) => void
  destaque?: boolean
  usuarioId?: number
  visualizacoes?: number
  carrosselDisponivel?: boolean
  videoHabilitado?: boolean
  contentClassification?: string | null
  requiresVisitorVerification?: boolean
  requiresStrongVerification?: boolean
  viewerAuthorized?: boolean
  restrictedPreview?: boolean
  whatsappCardEnabled?: boolean
  onAccessUpdated?: () => void
  previewMode?: boolean
}

function clean(v?: string | null) {
  const s = corrigirTextoCorrompido(v ?? "").trim()
  if (!s) return ""
  const low = s.toLowerCase()
  if (low === "não informado" || low === "nao informado") return ""
  return s
}

function EmptyMediaState() {
  return (
    <div className="absolute inset-0 overflow-hidden bg-gradient-to-br from-slate-50 to-slate-100">
      <Image
        src="/icone-sem-foto.png"
        alt="Perfil sem fotos"
        fill
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
  imagens,
  videos = [],
  descricao,
  telefone,
  usernameAnunciante,
  favoritoInicial = false,
  onDesfavoritar,
  destaque = false,
  usuarioId,
  visualizacoes = 0,
  carrosselDisponivel = false,
  videoHabilitado = false,
  contentClassification = null,
  requiresVisitorVerification = false,
  requiresStrongVerification = false,
  viewerAuthorized = false,
  restrictedPreview = false,
  whatsappCardEnabled = false,
  onAccessUpdated,
  previewMode = false,
}: AnuncioCardProps) {
  const router = useRouter()
  const API = process.env.NEXT_PUBLIC_API_URL
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

  const markBad = (src: string) => {
    if (!src) return
    setBadSrcs((prev) => {
      if (prev.has(src)) return prev
      const next = new Set(prev)
      next.add(src)
      return next
    })
  }

  const midias = useMemo(() => {
    const safeImgs = (Array.isArray(imagens) ? imagens : [])
      .filter(Boolean)
      .filter((s) => !badSrcs.has(s))

    const safeVids = videoHabilitado
      ? (Array.isArray(videos) ? videos : [])
          .filter(Boolean)
          .filter((s) => !badSrcs.has(s))
      : []

    const all = [
      ...safeVids.map((src) => ({ type: "video" as const, src })),
      ...safeImgs.map((src) => ({ type: "image" as const, src })),
    ]

    return all
  }, [imagens, videos, badSrcs, videoHabilitado])

  const whatsappUrl = useMemo(() => {
    const tel = (telefone ?? "").trim()
    if (!tel) return null

    let numero = tel.replace(/\D/g, "")
    if (!numero) return null
    if (!numero.startsWith("55")) numero = "55" + numero

    const mensagem = encodeURIComponent("Olá, vi seu anúncio no Tops do Job!")
    return `https://wa.me/${numero}?text=${mensagem}`
  }, [telefone])

  const [index, setIndex] = useState(0)
  const [favorito, setFavorito] = useState(favoritoInicial)
  const [loadingFavorito, setLoadingFavorito] = useState(false)
  const [views, setViews] = useState(visualizacoes)

  useEffect(() => {
    if (!midias.length) {
      if (index !== 0) setIndex(0)
      return
    }
    if (index > midias.length - 1) setIndex(0)
  }, [midias.length, index])

  useEffect(() => {
    if (!carrosselDisponivel || midias.length <= 1 || typeof window === "undefined") return

    const candidates = [
      midias[(index + 1) % midias.length],
      midias[(index - 1 + midias.length) % midias.length],
    ]

    candidates.forEach((media) => {
      if (media?.type !== "image" || !media.src) return
      const img = new window.Image()
      img.src = media.src
    })
  }, [carrosselDisponivel, index, midias])

  const next = () => {
    if (!midias.length) return
    setIndex((i) => (i + 1) % midias.length)
  }

  const prev = () => {
    if (!midias.length) return
    setIndex((i) => (i - 1 + midias.length) % midias.length)
  }

  const toggleFavorito = async (e: MouseEvent<HTMLButtonElement>) => {
    e.stopPropagation()
    if (previewMode) return
    if (loadingFavorito) return

    if (!usuarioId) {
      toast.info("Faça login para favoritar anúncios.")
      return
    }

    const wasFavorito = favorito
    const method = wasFavorito ? "DELETE" : "POST"

    setLoadingFavorito(true)

    try {
      const url = `${API}/anuncios/${id}/favoritar?usuarioId=${usuarioId}`
      const res = await fetch(url, { method, credentials: "include" })
      const text = await res.text()

      if (!res.ok) {
        const low = (text || "").toLowerCase()
        if (low.includes("próprio anúncio") || low.includes("proprio anuncio")) {
          toast.warning("Você não pode favoritar o seu próprio anúncio.")
        } else {
          toast.error(text || "Erro ao atualizar favorito.")
        }
        return
      }

      setFavorito(!wasFavorito)

      if (method === "DELETE") {
        toast.success(text || "Anúncio removido dos favoritos.")
        onDesfavoritar?.(nome)
      } else {
        toast.success(text || "Anúncio adicionado aos favoritos.")
      }
    } catch {
      toast.error("Erro de conexão com o servidor.")
    } finally {
      setLoadingFavorito(false)
    }
  }

  const handleWhatsAppClick = async (e: MouseEvent<HTMLButtonElement>) => {
    e.stopPropagation()
    if (previewMode) return
    if (!whatsappUrl) return

    if (typeof window !== "undefined" && (window as any).gtag) {
      ;(window as any).gtag("event", "click_whatsapp", {
        event_category: "engagement",
        event_label: slugRota,
      })
    }

    if (!API) {
      openWhatsAppWarning({ url: whatsappUrl })
      return
    }

    try {
      const res = await fetch(`${API}/cliques-whatsapp/${encodeURIComponent(slugRota)}`, {
        method: "POST",
        credentials: "include",
      })

      if (!res.ok) {
        const payload = await res.json().catch(async () => ({ message: (await res.text().catch(() => "")).trim() }))
        const message =
          payload?.message ||
          payload?.error ||
          "Verificação 18+ obrigatória para acessar o WhatsApp deste anúncio."
        toast.error(message)
        return
      }

      openWhatsAppWarning({ url: whatsappUrl })
    } catch {
      toast.error("Não foi possível validar o acesso ao WhatsApp agora.")
    }
  }

  const handleChatClick = () => {
    if (previewMode) return
    if (!usernameAnunciante) return
    router.push(`/chat?usuario=${encodeURIComponent(usernameAnunciante)}`)
  }

  const handleVerAnuncio = () => {
    if (previewMode) return
    /*
     * Contagem oficial: POST /anuncios/{id}/visualizar na página pública do anúncio (anuncio-detalhes).
     * Evita duplicar view (card + detalhe) e cobre abertura direta por URL.
     */
    router.push(`/anuncios/${encodeURIComponent(slugRota)}`)
  }

  const anuncioHref = `/anuncios/${encodeURIComponent(slugRota)}`
  const midiaAtual = midias[index]
  const nomeExibido = corrigirTextoCorrompido(nome)
  const descricaoExibida = corrigirTextoCorrompido(
    descricao ?? "Anúncio sem descrição ainda. Abra para ver mais detalhes."
  )

  return (
    <div
      className={`group relative mx-auto block w-full max-w-[360px] rounded-xl bg-white transition-all duration-300 hover:shadow-lg ${
        destaque
          ? "border-2 border-pink-500 shadow-pink-200 hover:shadow-pink-300"
          : "border border-gray-200 hover:border-gray-300"
      }`}
    >
      <div className="relative aspect-[3/4] w-full overflow-hidden rounded-t-xl bg-gray-50">
        {midiaAtual ? (
          midiaAtual.type === "video" ? (
            <video
              key={midiaAtual.src}
              src={midiaAtual.src}
              className="absolute inset-0 h-full w-full object-cover transition-transform duration-500 group-hover:scale-[1.02]"
              controls
              muted
              playsInline
              onError={() => markBad(midiaAtual.src)}
            />
          ) : (
            <div className="absolute inset-0">
              <SensitiveImage
                anuncioId={id}
                anuncioSlug={slugRota}
                anuncioNome={nomeExibido}
                cidade={cidadeNome ?? null}
                contentClassification={contentClassification}
                src={midiaAtual.src}
                alt={nomeExibido}
                fill
                sizes="(max-width: 768px) 100vw, 360px"
                className={cn("transition-transform duration-500 group-hover:scale-[1.02]")}
                requiresVisitorVerification={requiresVisitorVerification}
                requiresStrongVerification={requiresStrongVerification}
                viewerAuthorized={viewerAuthorized}
                deferCompliancePreview
                onVerificationSuccess={onAccessUpdated}
                onAbrirPaginaDoAnuncio={handleVerAnuncio}
                onError={() => markBad(midiaAtual.src)}
              />
            </div>
          )
        ) : (
          <EmptyMediaState />
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
          <button
            onClick={toggleFavorito}
            disabled={loadingFavorito}
            className="absolute right-3 top-3 z-20 rounded-full border border-gray-200 bg-white/90 p-2 shadow-sm hover:bg-white"
            aria-label={favorito ? "Remover dos favoritos" : "Adicionar aos favoritos"}
          >
            <HeartSolid className={`h-5 w-5 ${favorito ? "text-[#FC1EAD]" : "text-gray-600"}`} />
          </button>
        )}

        {destaque && (
          <div className="absolute left-3 top-3 z-20 flex items-center gap-1 rounded-md bg-gradient-to-r from-pink-600 to-pink-500 px-3 py-[3px] text-[11px] font-semibold text-white shadow-md">
            Destaque
          </div>
        )}

        {!previewMode && carrosselDisponivel && midias.length > 1 && (
          <>
            <button
              type="button"
              onClick={(e) => {
                e.preventDefault()
                e.stopPropagation()
                prev()
              }}
              className="absolute left-2 top-1/2 z-10 -translate-y-1/2 cursor-pointer rounded-full bg-white/70 p-1 hover:bg-white"
              aria-label="Foto anterior"
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
              className="absolute right-2 top-1/2 z-10 -translate-y-1/2 cursor-pointer rounded-full bg-white/70 p-1 hover:bg-white"
              aria-label="Próxima foto"
            >
              <ChevronRightIcon className="h-5 w-5 text-gray-700" />
            </button>
          </>
        )}
      </div>

      <div className="flex flex-1 flex-col justify-between space-y-3 p-3">
        <div className="flex flex-col gap-1">
          <h3 className="line-clamp-1 text-base font-semibold leading-tight text-gray-900">
            {previewMode ? (
              <span>{nomeExibido}</span>
            ) : (
              <Link href={anuncioHref} prefetch={false} className="hover:text-pink-600">
                {nomeExibido}
              </Link>
            )}
          </h3>

          {idade != null ? (
            <p className="text-sm font-medium text-gray-700">{idade} anos</p>
          ) : null}

          <div className="flex flex-wrap items-center text-xs text-gray-500">
            <MapPinIcon className="mr-1 h-4 w-4" />
            <span>{localizacaoLabel}</span>
          </div>

          {!previewMode && (
            <div className="mt-1 flex items-center gap-1 text-xs text-gray-500">
              <EyeIcon className="h-4 w-4 text-gray-400" />
              <span>{views} visualizações</span>
            </div>
          )}

          <p className="mt-1 line-clamp-2 text-xs text-gray-600">{descricaoExibida}</p>
        </div>

        <div className="flex flex-col gap-2 border-t border-gray-100 pt-3">
          <p className="text-sm font-bold text-pink-600">{valor}</p>

          <div className="flex flex-wrap gap-2">
            {whatsappUrl && whatsappCardEnabled && !previewMode && (
              <Button
                className="flex-1 bg-[#25D366] px-3 py-1 text-xs font-medium text-white hover:bg-[#20bd5a]"
                onClick={handleWhatsAppClick}
              >
                WhatsApp
              </Button>
            )}

            {whatsappCardEnabled && !previewMode && (
              <Button
                onClick={handleChatClick}
                disabled={!usernameAnunciante}
                className="flex-1 items-center justify-center gap-1 bg-[#FC1EAD] px-3 py-1 text-xs font-medium text-white hover:bg-[#e01a9a]"
              >
                <ChatBubbleLeftIcon className="h-4 w-4" />
                Chat
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
  )
}

export default AnuncioCard
