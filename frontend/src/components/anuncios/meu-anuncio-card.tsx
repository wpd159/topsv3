'use client'

import Image from 'next/image'
import { useState, useEffect, useMemo } from 'react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import {
  MapPinIcon,
  PencilIcon,
  TrashIcon,
  PauseIcon,
  PlayIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ClockIcon,
  RocketLaunchIcon,
  BoltIcon,
  Squares2X2Icon,
  VideoCameraIcon,
  PhotoIcon,
  ChatBubbleLeftRightIcon,
  EyeSlashIcon,
  SparklesIcon,
} from '@heroicons/react/24/solid'
import { toast } from 'sonner'
import { ImpulsionarModal } from '@/components/modals/impulsionar-modal'
import { useRouter } from 'next/navigation'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'

type FeatureCatalogoItem = {
  codigo: string
  nome: string
  descricao?: string | null
  custoCreditos: number
  duracaoHoras?: number | null
  escopo?: 'ANUNCIO' | 'USUARIO'
  ativo?: boolean
}

type FeatureAtivaResumo = {
  codigo: string
  nome?: string | null
  expiraEm?: any // pode vir string ISO ou array
}

interface MeuAnuncioCardProps {
  id: number
  slug: string
  nome: string
  cidade: string
  valor: string
  imagens: string[]
  status: 'postado' | 'pendente' | 'pausado'
  descricao?: string
  onAtualizar?: () => void
  impulsionado?: boolean
  dataFimImpulsionamento?: string
  catalogoFeatures?: FeatureCatalogoItem[]
  featuresAtivas?: FeatureAtivaResumo[]
  pendingRevision?: boolean
  /** Story exige anúncio ATIVO + feature STORIES no catálogo (definido pelo pai). */
  podeAdicionarStory?: boolean
  onAdicionarStory?: () => void
}

const FEATURE_ICON: Record<string, any> = {
  FOTOS_EXTRA_5: PhotoIcon,
  CARROSSEL_FOTOS: Squares2X2Icon,
  VIDEO_1: VideoCameraIcon,
  WHATSAPP_CARD: ChatBubbleLeftRightIcon,
  OCULTAR_IDADE: EyeSlashIcon,
}

const FEATURE_LABEL: Record<string, string> = {
  FOTOS_EXTRA_5: 'Até 10 fotos',
  CARROSSEL_FOTOS: 'Carrossel',
  VIDEO_1: 'Vídeo',
  WHATSAPP_CARD: 'WhatsApp',
  OCULTAR_IDADE: 'Ocultar idade',
}

function parseBackendDate(v: any): Date | null {
  if (!v) return null

  // ISO string
  if (typeof v === 'string') {
    const d = new Date(v)
    return Number.isNaN(d.getTime()) ? null : d
  }

  // array [yyyy,mm,dd,hh,mm,ss] (às vezes Jackson manda assim)
  if (Array.isArray(v) && v.length >= 3) {
    const [Y, M, D, h = 0, m = 0, s = 0] = v.map((x) => Number(x))
    const d = new Date(Y, (M || 1) - 1, D || 1, h, m, s)
    return Number.isNaN(d.getTime()) ? null : d
  }

  return null
}

function timeLeftMs(expiraEm?: any): number | null {
  const end = parseBackendDate(expiraEm)
  if (!end) return null
  return end.getTime() - Date.now()
}

function timeLeftLabel(expiraEm?: any): string | null {
  const ms = timeLeftMs(expiraEm)
  if (ms == null) return null
  if (ms <= 0) return 'expirado'

  const minutes = Math.max(1, Math.ceil(ms / 60000))
  if (minutes < 60) return `${minutes}m`

  if (ms < 24 * 60 * 60 * 1000) {
    const hours = Math.max(1, Math.ceil(ms / (60 * 60 * 1000)))
    return `${hours}h`
  }

  const days = Math.max(1, Math.ceil(ms / (24 * 60 * 60 * 1000)))
  return `${days}d`
}

export function MeuAnuncioCard({
  id,
  slug,
  nome,
  cidade,
  valor,
  imagens,
  status,
  descricao,
  onAtualizar,
  impulsionado,
  dataFimImpulsionamento,
  catalogoFeatures = [],
  featuresAtivas = [],
  pendingRevision = false,
  podeAdicionarStory = false,
  onAdicionarStory,
}: MeuAnuncioCardProps) {
  const [index, setIndex] = useState(0)
  const [loading, setLoading] = useState(false)

  const [openImpulsionar, setOpenImpulsionar] = useState(false)
  const [openDeleteDialog, setOpenDeleteDialog] = useState(false)

  // tick pra atualizar labels de tempo das features
  const [tick, setTick] = useState(0)

  const API = process.env.NEXT_PUBLIC_API_URL
  const router = useRouter()

  const podeComprar = status !== 'pendente'

  const upgradesCatalogo = useMemo(() => {
    const ativos = (catalogoFeatures || [])
      .filter((f) => (f?.ativo ?? true) === true)
      .filter((f) => (f?.escopo ?? 'ANUNCIO') === 'ANUNCIO')

    const wanted = new Set(['FOTOS_EXTRA_5', 'CARROSSEL_FOTOS', 'VIDEO_1', 'WHATSAPP_CARD', 'OCULTAR_IDADE'])
    return ativos
      .filter((f) => wanted.has(f.codigo))
      .sort((a, b) => (a.custoCreditos ?? 0) - (b.custoCreditos ?? 0))
  }, [catalogoFeatures])

  // features visíveis no card
  const featuresVisiveis = useMemo(() => {
    const wanted = new Set(['FOTOS_EXTRA_5', 'CARROSSEL_FOTOS', 'VIDEO_1', 'WHATSAPP_CARD', 'OCULTAR_IDADE'])

    const normalized = (featuresAtivas || [])
      .filter((f) => wanted.has(f.codigo))
      .map((f) => {
        const ms = timeLeftMs(f.expiraEm)
        const label = timeLeftLabel(f.expiraEm)
        return {
          ...f,
          _ms: ms, // null => sem expiração
          _label: label, // null => sem expiração
        }
      })

    // ordena: primeiro os que expiram mais cedo, depois os sem expiração
    normalized.sort((a, b) => {
      const am = a._ms
      const bm = b._ms
      if (am == null && bm == null) return 0
      if (am == null) return 1
      if (bm == null) return -1
      return am - bm
    })

    // não mostra expirado (se backend mandar errado)
    return normalized.filter((f) => f._label !== 'expirado')
  }, [featuresAtivas, tick])

  const proximaExpiracao = useMemo(() => {
    const expiracoes: Array<{
      kind: 'impulsionamento' | 'beneficio'
      label: string
      ms: number
      countdown: string
    }> = []

    if (impulsionado) {
      const ms = timeLeftMs(dataFimImpulsionamento)
      const countdown = timeLeftLabel(dataFimImpulsionamento)
      if (ms != null && ms > 0 && countdown) {
        expiracoes.push({
          kind: 'impulsionamento',
          label: 'Impulsionamento',
          ms,
          countdown,
        })
      }
    }

    for (const feature of featuresAtivas || []) {
      const ms = timeLeftMs(feature.expiraEm)
      const countdown = timeLeftLabel(feature.expiraEm)
      if (ms != null && ms > 0 && countdown) {
        expiracoes.push({
          kind: 'beneficio',
          label: FEATURE_LABEL[feature.codigo] ?? feature.nome ?? 'Benefício',
          ms,
          countdown,
        })
      }
    }

    expiracoes.sort((a, b) => a.ms - b.ms)
    return expiracoes[0] ?? null
  }, [dataFimImpulsionamento, featuresAtivas, impulsionado, tick])

  const avisoExpiracao = useMemo(() => {
    if (!proximaExpiracao || proximaExpiracao.ms > 48 * 60 * 60 * 1000) return null

    const forte = proximaExpiracao.ms <= 24 * 60 * 60 * 1000
    const titulo =
      proximaExpiracao.kind === 'impulsionamento'
        ? 'Seu anúncio perderá visibilidade em breve'
        : 'Alguns benefícios expiram em breve'
    const descricao =
      proximaExpiracao.kind === 'impulsionamento'
        ? `Destaque expira em ${proximaExpiracao.countdown}. Renove para manter seu anúncio em evidência.`
        : `${proximaExpiracao.label} expira em ${proximaExpiracao.countdown}. Renove para manter os benefícios ativos.`
    const badge =
      proximaExpiracao.kind === 'impulsionamento'
        ? `Destaque expira em ${proximaExpiracao.countdown}`
        : `Expira em ${proximaExpiracao.countdown}`

    return {
      forte,
      titulo,
      descricao,
      countdown: proximaExpiracao.countdown,
      badge,
      cta: proximaExpiracao.kind === 'impulsionamento' ? 'Renovar anúncio' : 'Renovar benefícios',
    }
  }, [proximaExpiracao])

  useEffect(() => {
    const t = setInterval(() => setTick((x) => x + 1), 60_000)
    return () => clearInterval(t)
  }, [])

  const impulsionamentoCountdown = useMemo(() => {
    if (!impulsionado) return null
    return timeLeftLabel(dataFimImpulsionamento)
  }, [dataFimImpulsionamento, impulsionado, tick])

  const impulsionamentoAtivo = impulsionamentoCountdown !== null && impulsionamentoCountdown !== 'expirado'

  const next = () => setIndex((prev) => (prev + 1) % Math.max(imagens.length, 1))
  const prev = () =>
    setIndex((prev) => (prev - 1 + Math.max(imagens.length, 1)) % Math.max(imagens.length, 1))

  const statusLabel =
    status === 'postado' ? 'Postado' : status === 'pendente' ? 'Pendente' : 'Pausado'

  const statusColor =
    status === 'postado'
      ? 'bg-green-100 text-green-700 border-green-300'
      : status === 'pendente'
        ? 'bg-yellow-100 text-yellow-700 border-yellow-300'
        : 'bg-gray-100 text-gray-700 border-gray-300'

  const executarAcao = async (acao: 'pausar' | 'postar' | 'excluir') => {
    try {
      if (!API) throw new Error('API não definida')
      setLoading(true)

      const metodo = acao === 'excluir' ? 'DELETE' : 'PUT'
      const url = acao === 'excluir' ? `${API}/anuncios/${id}` : `${API}/anuncios/${id}/${acao}`

      const res = await fetch(url, { method: metodo, credentials: 'include' })
      if (!res.ok) throw new Error(`Erro ao tentar ${acao} o anúncio`)

      if (acao === 'excluir') {
        toast.success('Anúncio excluído com sucesso.')
        onAtualizar?.()
        setOpenDeleteDialog(false)
      } else {
        toast.success(acao === 'pausar' ? 'Anúncio pausado com sucesso.' : 'Anúncio reativado com sucesso.')
        onAtualizar?.()
      }
    } catch {
      toast.error('Falha ao executar ação no anúncio.')
    } finally {
      setLoading(false)
    }
  }

  const handleEditar = () => {
    if (!slug) {
      toast.error('Slug do anúncio ausente.')
      return
    }
    const path = `/meus-anuncios/${encodeURIComponent(slug)}/editar${impulsionado ? '?pro=1' : ''}`
    router.push(path)
  }

  const handleMonetizar = () => {
    if (!slug) {
      toast.error('Slug do anúncio ausente.')
      return
    }
    router.push(`/meus-anuncios/${encodeURIComponent(slug)}/monetizar`)
  }

  return (
    <>
      <div className="group block relative w-full max-w-[330px] mx-auto">
        <div className="flex flex-col rounded-xl border border-gray-200 bg-white transition-all duration-300 hover:shadow-md hover:border-gray-300">
          {/* imagem */}
          <div className="relative w-full aspect-[3/4] overflow-hidden rounded-t-xl bg-gray-50">
            <Image
              src={imagens[index] || '/icone-sem-foto.png'}
              alt={nome}
              fill
              className="object-cover object-center transition-transform duration-500 group-hover:scale-[1.02]"
              quality={85}
              sizes="(max-width: 768px) 100vw, 330px"
            />

            {imagens.length > 1 && (
              <>
                <button
                  onClick={(e) => {
                    e.preventDefault()
                    prev()
                  }}
                  className="absolute left-2 top-1/2 -translate-y-1/2 bg-white/70 hover:bg-white rounded-full p-1 transition"
                >
                  <ChevronLeftIcon className="w-5 h-5 text-gray-700" />
                </button>
                <button
                  onClick={(e) => {
                    e.preventDefault()
                    next()
                  }}
                  className="absolute right-2 top-1/2 -translate-y-1/2 bg-white/70 hover:bg-white rounded-full p-1 transition"
                >
                  <ChevronRightIcon className="w-5 h-5 text-gray-700" />
                </button>
              </>
            )}

            {/* status */}
            <div className="absolute top-2 left-2">
              <div className="flex flex-col gap-1">
                <span className={cn('text-[11px] font-medium px-2 py-[2px] rounded-md border', statusColor)}>
                  {statusLabel}
                </span>
                {pendingRevision && (
                  <span className="text-[11px] font-medium px-2 py-[2px] rounded-md border bg-amber-100 text-amber-700 border-amber-300">
                    Atualizacao em analise
                  </span>
                )}
              </div>
            </div>

            {/* ✅ BADGES features (top-right) */}
            {featuresVisiveis.length > 0 && (
              <div className="absolute right-2 top-2 flex max-w-[calc(100%-1rem)] flex-wrap justify-end gap-1">
                {featuresVisiveis.map((f) => {
                  const Icon = FEATURE_ICON[f.codigo] ?? BoltIcon
                  const label = FEATURE_LABEL[f.codigo] ?? (f.nome || f.codigo)
                  const tl = (f as any)._label as string | null
                  const end = parseBackendDate(f.expiraEm)

                  return (
                    <div
                      key={f.codigo}
                      className={cn(
                        'flex items-center gap-1.5 text-[11px] px-2 py-[3px] rounded-md shadow-md border',
                        'bg-white/90 text-gray-900 border-pink-200'
                      )}
                      title={
                        end
                          ? `${label} expira em ${end.toLocaleString('pt-BR')}`
                          : `${label} ativo`
                      }
                    >
                      <Icon className="w-3.5 h-3.5 text-[#FC1EAD]" />
                      <span className="font-semibold">{label}</span>

                      {tl ? (
                        <span className="ml-1 rounded bg-pink-50 px-1.5 py-0.5 text-[10px] font-bold text-[#FC1EAD]">
                          Restam {tl}
                        </span>
                      ) : (
                        <span className="ml-1 rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-bold text-slate-700">
                          ∞
                        </span>
                      )}
                    </div>
                  )
                })}
              </div>
            )}

            {/* badge impulso (bottom-left) */}
            {impulsionado && impulsionamentoCountdown !== null && (
              <div
                className={cn(
                  'absolute bottom-2 left-2 flex items-center gap-2 text-[11px] px-2 py-[3px] rounded-md shadow-md transition-all',
                  impulsionamentoAtivo
                    ? 'bg-gradient-to-r from-pink-600 to-pink-500 text-white'
                    : 'bg-gray-200 text-gray-600 border border-gray-300'
                )}
              >
                <BoltIcon className={cn('w-4 h-4', impulsionamentoAtivo ? 'text-yellow-300' : 'text-gray-500')} />
                {impulsionamentoAtivo
                  ? `Impulsionado — restam ${impulsionamentoCountdown}`
                  : 'Impulsionamento expirado'}
              </div>
            )}
          </div>

          {/* conteúdo */}
          <div className="flex flex-col justify-between flex-1 p-3 space-y-3">
            <div className="flex flex-col gap-1">
              <h3 className="text-base font-semibold text-gray-900 leading-tight line-clamp-1">{nome}</h3>

              <div className="flex items-center text-gray-500 text-xs">
                <MapPinIcon className="w-4 h-4 mr-1" />
                {cidade}
              </div>

              {descricao && <p className="text-xs text-gray-600 mt-1 line-clamp-2">{descricao}</p>}

              {avisoExpiracao && (
                <div
                  className={cn(
                    'mt-2 rounded-xl border px-3 py-3',
                    avisoExpiracao.forte
                      ? 'border-rose-200 bg-rose-50'
                      : 'border-amber-200 bg-amber-50'
                  )}
                >
                  <div className="flex items-start gap-2">
                    <ClockIcon
                      className={cn(
                        'mt-0.5 h-4 w-4 shrink-0',
                        avisoExpiracao.forte ? 'text-rose-600' : 'text-amber-600'
                      )}
                    />
                    <div className="min-w-0 flex-1">
                      <p
                        className={cn(
                          'text-xs font-semibold',
                          avisoExpiracao.forte ? 'text-rose-900' : 'text-amber-900'
                        )}
                      >
                        {avisoExpiracao.titulo}
                      </p>
                      <p
                        className={cn(
                          'mt-1 text-[11px] leading-5',
                          avisoExpiracao.forte ? 'text-rose-800' : 'text-amber-800'
                        )}
                      >
                        {avisoExpiracao.descricao}
                      </p>
                      <div className="mt-2 flex flex-wrap items-center gap-2">
                        <span
                          className={cn(
                            'rounded-full px-2 py-1 text-[10px] font-bold uppercase tracking-[0.12em]',
                            avisoExpiracao.forte
                              ? 'bg-white text-rose-700'
                              : 'bg-white text-amber-700'
                          )}
                        >
                          {avisoExpiracao.badge}
                        </span>
                        {podeComprar && (
                          <button
                            type="button"
                            onClick={handleMonetizar}
                            className={cn(
                              'text-[11px] font-semibold underline underline-offset-2',
                              avisoExpiracao.forte ? 'text-rose-700' : 'text-amber-700'
                            )}
                          >
                            {avisoExpiracao.cta}
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* footer */}
            <div className="border-t border-gray-100 pt-3 flex flex-col gap-2">
              <p className="text-pink-600 font-bold text-sm">{valor}</p>

              <div className="flex flex-wrap gap-2">
                <Button
                  disabled={loading}
                  onClick={handleEditar}
                  className="flex-1 bg-gray-100 hover:bg-gray-200 text-gray-800 font-medium text-xs px-3 py-1"
                >
                  <PencilIcon className="w-3 h-3 mr-1" />
                  Editar
                </Button>

                <Button
                  disabled={loading}
                  onClick={(e) => {
                    e.stopPropagation()
                    setOpenDeleteDialog(true)
                  }}
                  className="flex-1 bg-red-100 hover:bg-red-200 text-red-700 font-medium text-xs px-3 py-1"
                >
                  <TrashIcon className="w-3 h-3 mr-1" />
                  Excluir
                </Button>

                {/* Impulsionamento: fluxo público passa pelo wizard de monetização */}
                <Button
                  disabled={loading || !podeComprar}
                  title={!podeComprar ? 'Aguarde aprovação para comprar upgrades.' : ''}
                  onClick={(e) => {
                    e.stopPropagation()
                    handleMonetizar()
                  }}
                  className="flex-1 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-medium text-xs px-3 py-1"
                >
                  <RocketLaunchIcon className="w-3 h-3 mr-1" />
                  Impulsionamento
                </Button>

                {podeAdicionarStory && onAdicionarStory ? (
                  <Button
                    type="button"
                    disabled={loading}
                    onClick={(e) => {
                      e.stopPropagation()
                      onAdicionarStory()
                    }}
                    className="w-full bg-pink-50 hover:bg-pink-100 text-[#b8097a] border border-pink-200 font-medium text-xs px-3 py-1"
                  >
                    <SparklesIcon className="w-3 h-3 mr-1" />
                    Adicionar story
                  </Button>
                ) : null}

                {status === 'pendente' ? (
                  <Button
                    disabled
                    className="flex-1 bg-gray-100 text-gray-500 font-medium text-xs px-3 py-1 cursor-not-allowed opacity-80"
                  >
                    <ClockIcon className="w-3 h-3 mr-1" />
                    Aguardando aprovação
                  </Button>
                ) : status === 'postado' ? (
                  <Button
                    disabled={loading}
                    onClick={(e) => {
                      e.stopPropagation()
                      executarAcao('pausar')
                    }}
                    className="flex-1 bg-yellow-100 hover:bg-yellow-200 text-yellow-800 font-medium text-xs px-3 py-1"
                  >
                    <PauseIcon className="w-3 h-3 mr-1" />
                    Pausar
                  </Button>
                ) : (
                  <Button
                    disabled={loading}
                    onClick={(e) => {
                      e.stopPropagation()
                      executarAcao('postar')
                    }}
                    className="flex-1 bg-green-100 hover:bg-green-200 text-green-800 font-medium text-xs px-3 py-1"
                  >
                    <PlayIcon className="w-3 h-3 mr-1" />
                    Ativar
                  </Button>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Modal único: impulsionar + upgrades */}
      <ImpulsionarModal
        open={openImpulsionar}
        onOpenChange={setOpenImpulsionar}
        anuncioId={id}
        upgrades={upgradesCatalogo}
        featuresAtivas={featuresAtivas ?? []}
        onConfirm={() => onAtualizar?.()}
        redirectToOnSuccess={`/meus-anuncios/${encodeURIComponent(slug)}/editar?pro=1`}
        wizardHref={`/meus-anuncios/${encodeURIComponent(slug)}/monetizar`}
      />

      {/* delete */}
      <Dialog open={openDeleteDialog} onOpenChange={setOpenDeleteDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Excluir anúncio?</DialogTitle>
            <DialogDescription>
              Isso remove o anúncio da plataforma. Não dá pra desfazer depois.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="flex gap-2 justify-end">
            <Button variant="outline" onClick={() => setOpenDeleteDialog(false)} disabled={loading}>
              Cancelar
            </Button>
            <Button
              onClick={() => executarAcao('excluir')}
              className="bg-red-500 hover:bg-red-600 text-white"
              disabled={loading}
            >
              Excluir agora
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
