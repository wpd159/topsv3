'use client'

import { useEffect, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import {
  RocketLaunchIcon,
  PlusIcon,
  MinusIcon,
  XMarkIcon,
  CheckIcon,
  BanknotesIcon,
  CalendarDaysIcon,
  CreditCardIcon,
  Squares2X2Icon,
  VideoCameraIcon,
  PhotoIcon,
  SparklesIcon,
  LockClosedIcon,
  ChatBubbleLeftRightIcon,
  EyeSlashIcon,
  StarIcon,
  BoltIcon,
} from '@heroicons/react/24/solid'
import { toast } from 'sonner'

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
  expiraEm?: any
}

type MonetizacaoCotacaoDuracao = {
  dias: number
  creditos: number
  saldoSuficiente: boolean
  creditosFaltantes: number
}

type MonetizacaoCotacaoOpcao = {
  codigo: string
  duracoes?: MonetizacaoCotacaoDuracao[]
}

type MonetizacaoCotacao = {
  opcoes?: MonetizacaoCotacaoOpcao[]
}

interface ImpulsionarModalProps {
  open: boolean
  onOpenChange: (v: boolean) => void
  anuncioId: number
  upgrades?: FeatureCatalogoItem[]
  featuresAtivas?: FeatureAtivaResumo[]
  onConfirm?: () => void
  redirectToOnSuccess?: string
  wizardHref?: string
}

type PacoteId = 'DESTAQUE' | 'PREMIUM' | 'TOP'

type PacoteConfig = {
  id: PacoteId
  titulo: string
  rotulo: string
  headline: string
  subtitulo: string
  descricao: string
  cta: string
  recomendado?: boolean
  recursos: string[]
  beneficios: string[]
}

const ICON_BY_CODE: Record<string, any> = {
  FOTOS_EXTRA_5: PhotoIcon,
  CARROSSEL_FOTOS: Squares2X2Icon,
  VIDEO_1: VideoCameraIcon,
  WHATSAPP_CARD: ChatBubbleLeftRightIcon,
  OCULTAR_IDADE: EyeSlashIcon,
}

const TITLE_BY_CODE: Record<string, string> = {
  FOTOS_EXTRA_5: 'Até 10 fotos no anúncio',
  CARROSSEL_FOTOS: 'Carrossel de fotos',
  VIDEO_1: 'Vídeo no anúncio',
  WHATSAPP_CARD: 'WhatsApp no card',
  OCULTAR_IDADE: 'Ocultar idade',
}

const DESCRIPTION_BY_CODE: Record<string, string> = {
  FOTOS_EXTRA_5: 'Amplie o limite do anúncio para até 10 fotos durante a vigência do benefício.',
}

const PACKAGE_CONFIGS: PacoteConfig[] = [
  {
    id: 'DESTAQUE',
    titulo: 'Destaque',
    rotulo: 'Entrada',
    headline: 'Ganhe tração logo nas primeiras buscas',
    subtitulo: 'Prioridade na listagem e WhatsApp visível.',
    descricao: 'Ideal para quem quer dar o primeiro salto de visibilidade e facilitar o contato direto no card.',
    cta: 'Escolher Destaque',
    recursos: ['WHATSAPP_CARD'],
    beneficios: ['Prioridade na listagem', 'WhatsApp visível no card'],
  },
  {
    id: 'PREMIUM',
    titulo: 'Premium',
    rotulo: 'Melhor escolha',
    headline: 'Melhor equilíbrio entre alcance, mídia e conversão',
    subtitulo: 'Tudo do destaque com mais mídia e apresentação mais forte.',
    descricao: 'Combina contato direto, mais fotos, carrossel e vídeo para transformar interesse em mais cliques.',
    cta: 'Quero Premium',
    recomendado: true,
    recursos: ['WHATSAPP_CARD', 'FOTOS_EXTRA_5', 'CARROSSEL_FOTOS', 'VIDEO_1'],
    beneficios: ['Tudo do Destaque', 'Até 10 fotos no anúncio', 'Carrossel de fotos', 'Vídeo no anúncio'],
  },
  {
    id: 'TOP',
    titulo: 'Top',
    rotulo: 'Máxima exposição',
    headline: 'Para disputar o topo com o pacote mais forte',
    subtitulo: 'Máxima exposição com a combinação mais forte do anúncio.',
    descricao: 'Pensado para quem quer dominar a vitrine com impulsionamento forte e pacote completo de mídia.',
    cta: 'Escolher Top',
    recursos: ['WHATSAPP_CARD', 'FOTOS_EXTRA_5', 'CARROSSEL_FOTOS', 'VIDEO_1'],
    beneficios: ['Máxima prioridade', 'Aparece no topo com maior exposição', 'Pacote completo de mídia'],
  },
]

const AVISO_FOTOS_TEMPORARIO =
  'Benefício temporário: ao vencer, o anúncio volta ao limite padrão de fotos e as fotos adicionais serão removidas.'

function resolveSuggestedDuration(pacoteId: PacoteId, duracoesDisponiveis: number[]) {
  if (duracoesDisponiveis.length === 0) return 1
  if (pacoteId === 'DESTAQUE') return duracoesDisponiveis[0]
  if (pacoteId === 'PREMIUM') return duracoesDisponiveis[Math.min(1, duracoesDisponiveis.length - 1)]
  return duracoesDisponiveis[Math.min(2, duracoesDisponiveis.length - 1)]
}

function parseBackendDate(v: any): Date | null {
  if (!v) return null
  if (typeof v === 'string') {
    const d = new Date(v)
    return Number.isNaN(d.getTime()) ? null : d
  }
  if (Array.isArray(v) && v.length >= 3) {
    const [Y, M, D, h = 0, m = 0, s = 0] = v.map((x) => Number(x))
    const d = new Date(Y, (M || 1) - 1, D || 1, h, m, s)
    return Number.isNaN(d.getTime()) ? null : d
  }
  return null
}

function formatPtBr(d: Date) {
  return d.toLocaleString('pt-BR')
}

export function ImpulsionarModal({
  open,
  onOpenChange,
  anuncioId,
  upgrades = [],
  featuresAtivas = [],
  onConfirm,
  redirectToOnSuccess,
  wizardHref,
}: ImpulsionarModalProps) {
  const [dias, setDias] = useState(1)
  const [loading, setLoading] = useState(false)
  const [pacoteSelecionado, setPacoteSelecionado] = useState<PacoteId>('PREMIUM')
  const [cotacao, setCotacao] = useState<MonetizacaoCotacao | null>(null)

  const API = process.env.NEXT_PUBLIC_API_URL!
  const router = useRouter()

  useEffect(() => {
    if (!open) return

    setPacoteSelecionado('PREMIUM')

    const fetchCotacao = async () => {
      try {
        const res = await fetch(`${API}/monetizacao/cotacao/anuncio/${anuncioId}`, {
          credentials: 'include',
        })
        if (!res.ok) throw new Error('Erro ao carregar cotacao')
        const data = await res.json()
        setCotacao(data)
      } catch {
        setCotacao(null)
      }
    }

    fetchCotacao()
  }, [API, anuncioId, open])

  const topoDuracoes = useMemo(
    () => cotacao?.opcoes?.find((opcao) => opcao.codigo === 'ANUNCIO_TOPO')?.duracoes ?? [],
    [cotacao]
  )

  const duracoesDisponiveis = useMemo(
    () => topoDuracoes.map((duracao) => duracao.dias).sort((a, b) => a - b),
    [topoDuracoes]
  )

  useEffect(() => {
    if (!open) return
    const duracaoInicial = resolveSuggestedDuration('PREMIUM', duracoesDisponiveis)
    setDias(duracaoInicial)
  }, [duracoesDisponiveis, open])

  const totalCreditosImpulsionamento = useMemo(
    () => topoDuracoes.find((duracao) => duracao.dias === dias)?.creditos ?? 0,
    [dias, topoDuracoes]
  )

  const upgradesDisponiveis = useMemo(() => {
    return (upgrades || [])
      .filter((f) => (f?.ativo ?? true) === true)
      .filter((f) => (f?.escopo ?? 'ANUNCIO') === 'ANUNCIO')
  }, [upgrades])

  const upgradesPorCodigo = useMemo(() => {
    return new Map(upgradesDisponiveis.map((item) => [item.codigo, item]))
  }, [upgradesDisponiveis])

  const activeMap = useMemo(() => {
    const now = Date.now()
    const map = new Map<string, { ativo: boolean; expira?: Date | null; reason?: string }>()

    for (const f of featuresAtivas || []) {
      if (!f?.codigo) continue
      const end = parseBackendDate(f.expiraEm)

      if (!end) {
        map.set(f.codigo, { ativo: true, expira: null, reason: 'Ativo (sem expiração)' })
        continue
      }

      const ms = end.getTime() - now
      if (ms > 0) {
        map.set(f.codigo, { ativo: true, expira: end, reason: `Ativo até ${formatPtBr(end)}` })
      } else {
        map.set(f.codigo, { ativo: false, expira: end, reason: 'Expirado' })
      }
    }

    return map
  }, [featuresAtivas])

  const escolherPacote = (pacote: PacoteConfig) => {
    setPacoteSelecionado(pacote.id)
    setDias(resolveSuggestedDuration(pacote.id, duracoesDisponiveis))
  }

  const metricasPacote = useMemo(() => {
    return PACKAGE_CONFIGS.reduce<Record<PacoteId, { custoExtras: number; custoSugerido: number }>>(
      (acc, pacote) => {
        const custoExtras = pacote.recursos.reduce(
          (total, codigo) => total + (upgradesPorCodigo.get(codigo)?.custoCreditos ?? 0),
          0
        )

        acc[pacote.id] = {
          custoExtras,
          custoSugerido:
            (topoDuracoes.find(
              (duracao) => duracao.dias === resolveSuggestedDuration(pacote.id, duracoesDisponiveis)
            )?.creditos ?? 0) + custoExtras,
        }

        return acc
      },
      {
        DESTAQUE: { custoExtras: 0, custoSugerido: 0 },
        PREMIUM: { custoExtras: 0, custoSugerido: 0 },
        TOP: { custoExtras: 0, custoSugerido: 0 },
      }
    )
  }, [duracoesDisponiveis, topoDuracoes, upgradesPorCodigo])

  const pacoteAtivo = useMemo(
    () => PACKAGE_CONFIGS.find((pacote) => pacote.id === pacoteSelecionado) ?? PACKAGE_CONFIGS[1],
    [pacoteSelecionado]
  )

  const recursosDoPacote = useMemo(() => {
    return pacoteAtivo.recursos
      .map((codigo) => upgradesPorCodigo.get(codigo))
      .filter(Boolean) as FeatureCatalogoItem[]
  }, [pacoteAtivo, upgradesPorCodigo])

  const recursosParaAtivar = useMemo(() => {
    return recursosDoPacote.filter((item) => !activeMap.get(item.codigo)?.ativo)
  }, [activeMap, recursosDoPacote])

  const totalCreditosPacote = useMemo(() => {
    const totalRecursos = recursosParaAtivar.reduce((acc, item) => acc + (item.custoCreditos ?? 0), 0)
    return totalCreditosImpulsionamento + totalRecursos
  }, [recursosParaAtivar, totalCreditosImpulsionamento])

  const ativarFeature = async (codigo: string) => {
    const st = activeMap.get(codigo)
    if (st?.ativo) {
      toast.info('Esse upgrade já está ativo neste anúncio.')
      return
    }

    try {
      setLoading(true)

      const res = await fetch(`${API}/features/ativar`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ anuncioId, codigo }),
      })

      const data = await res.json().catch(() => null)

      if (!res.ok) {
        const msg = data?.error || data?.message || 'Falha ao ativar feature.'
        throw new Error(msg)
      }

      toast.success('Upgrade ativado com sucesso.')
      onConfirm?.()
      onOpenChange(false)
    } catch (e: any) {
      toast.error(e?.message || 'Erro ao ativar upgrade.')
    } finally {
      setLoading(false)
    }
  }

  const confirmarImpulsionamentoManual = async () => {
    try {
      setLoading(true)
      const res = await fetch(`${API}/anuncios/${anuncioId}/impulsionar`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ dias }),
      })

      const data = await res.json().catch(() => ({}))

      if (!res.ok) {
        const erroMsg =
          data?.error ||
          (res.status === 400
            ? 'Créditos insuficientes para impulsionar o anúncio.'
            : 'Erro ao processar o impulsionamento.')
        toast.error(erroMsg)
        return
      }

      toast.success(`Anúncio impulsionado por ${dias} dia${dias > 1 ? 's' : ''}!`)
      onConfirm?.()
      onOpenChange(false)
      if (redirectToOnSuccess) router.push(redirectToOnSuccess)
    } catch {
      toast.error('Erro de conexão com o servidor.')
    } finally {
      setLoading(false)
    }
  }

  const ativarPacote = async () => {
    try {
      setLoading(true)

      const resImpulsionar = await fetch(`${API}/anuncios/${anuncioId}/impulsionar`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ dias }),
      })

      const dataImpulsionar = await resImpulsionar.json().catch(() => ({}))
      if (!resImpulsionar.ok) {
        const erroMsg =
          dataImpulsionar?.error ||
          (resImpulsionar.status === 400
            ? 'Créditos insuficientes para ativar esse pacote.'
            : 'Erro ao iniciar o impulsionamento do pacote.')
        throw new Error(erroMsg)
      }

      for (const recurso of recursosParaAtivar) {
        const resFeature = await fetch(`${API}/features/ativar`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ anuncioId, codigo: recurso.codigo }),
        })

        const dataFeature = await resFeature.json().catch(() => ({}))
        if (!resFeature.ok) {
          const erroMsg = dataFeature?.error || dataFeature?.message || `Falha ao ativar ${TITLE_BY_CODE[recurso.codigo] ?? recurso.nome}.`
          throw new Error(erroMsg)
        }
      }

      toast.success(`Pacote ${pacoteAtivo.titulo} ativado com sucesso.`)
      onConfirm?.()
      onOpenChange(false)
      if (redirectToOnSuccess) router.push(redirectToOnSuccess)
    } catch (error: any) {
      toast.error(error?.message || 'Não foi possível ativar o pacote agora.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-full max-w-[96vw] overflow-hidden border border-pink-100 bg-white p-0 shadow-[0_40px_120px_rgba(15,23,42,0.18)] sm:max-w-5xl xl:max-w-6xl">
        <div className="max-h-[92vh] overflow-y-auto">
          <DialogHeader className="border-b border-pink-100 bg-[radial-gradient(circle_at_top_left,_rgba(252,30,173,0.16),_transparent_38%),linear-gradient(180deg,_#fff7fc_0%,_#ffffff_72%)] px-4 py-5 sm:px-6 lg:px-8">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="inline-flex w-fit items-center rounded-full border border-pink-200 bg-white/80 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] text-pink-600 shadow-sm">
                Comparação de planos
              </div>

              {wizardHref ? (
                <Button
                  type="button"
                  variant="outline"
                  className="rounded-full border-pink-200 bg-white/80 text-[#b8097a] hover:bg-pink-50"
                  onClick={() => {
                    onOpenChange(false)
                    router.push(wizardHref)
                  }}
                >
                  Abrir wizard visual
                </Button>
              ) : null}
            </div>
            <DialogTitle className="mt-3 flex items-center gap-3 text-2xl font-bold tracking-tight text-gray-950 sm:text-3xl">
              <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[#FC1EAD]/10">
                <RocketLaunchIcon className="h-6 w-6 text-[#FC1EAD]" />
              </span>
              Impulsionar anúncio
            </DialogTitle>
            <DialogDescription className="max-w-3xl text-base leading-7 text-gray-600">
              Aumente sua visibilidade e receba mais contatos com uma vitrine mais forte, mais mídia e um pacote pensado para conversão.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-8 px-4 pb-6 pt-6 sm:px-6 lg:px-8">
            <section className="rounded-[28px] border border-pink-100 bg-gradient-to-br from-pink-50 via-white to-[#fff8fd] p-4 shadow-[0_18px_50px_rgba(252,30,173,0.08)] sm:p-6">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                <div className="space-y-2">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-pink-600">
                    Escolha o melhor plano
                  </p>
                  <h3 className="text-xl font-bold tracking-tight text-gray-950 sm:text-2xl">
                    Compare os três formatos e ative o que mais vende o seu anúncio
                  </h3>
                  <p className="max-w-3xl text-sm leading-6 text-gray-600 sm:text-[15px]">
                    Os planos mantêm a lógica atual do sistema, mas agora organizados para você comparar mais rápido e escolher com mais confiança.
                  </p>
                </div>

                <div className="rounded-2xl border border-pink-100 bg-white/90 px-4 py-3 text-sm text-gray-600 shadow-sm">
                  <p className="font-semibold text-gray-900">Plano mais vendedor</p>
                  <p className="mt-1">O Premium aparece com mais destaque visual para orientar a melhor decisão.</p>
                </div>
              </div>

              <div className="mt-6 grid auto-rows-fr gap-4 md:grid-cols-2 lg:grid-cols-3 lg:items-stretch">
                {PACKAGE_CONFIGS.map((pacote) => {
                  const selecionado = pacote.id === pacoteSelecionado
                  const recomendado = Boolean(pacote.recomendado)
                  const Icon = pacote.id === 'DESTAQUE' ? StarIcon : pacote.id === 'PREMIUM' ? SparklesIcon : BoltIcon
                  const metricas = metricasPacote[pacote.id]

                  return (
                    <div
                      key={pacote.id}
                      role="button"
                      tabIndex={0}
                      onClick={() => escolherPacote(pacote)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault()
                          escolherPacote(pacote)
                        }
                      }}
                      className={[
                        'relative flex h-full min-w-0 cursor-pointer flex-col overflow-hidden rounded-[28px] border p-5 text-left transition-all duration-200 sm:p-6',
                        recomendado
                          ? 'border-pink-300 bg-[linear-gradient(180deg,rgba(255,241,250,0.96)_0%,rgba(255,255,255,1)_46%,rgba(255,248,253,1)_100%)] shadow-[0_28px_80px_rgba(252,30,173,0.18)] lg:-translate-y-2 lg:scale-[1.02]'
                          : 'border-slate-200 bg-white shadow-[0_14px_40px_rgba(15,23,42,0.08)]',
                        selecionado
                          ? 'ring-2 ring-[#FC1EAD] ring-offset-2 ring-offset-white'
                          : 'hover:-translate-y-1 hover:shadow-[0_22px_60px_rgba(15,23,42,0.12)]',
                      ].join(' ')}
                    >
                      {recomendado && (
                        <span className="absolute left-5 top-5 rounded-full bg-[#FC1EAD] px-3 py-1 text-[10px] font-extrabold uppercase tracking-[0.18em] text-white shadow-[0_10px_22px_rgba(252,30,173,0.32)]">
                          Recomendado
                        </span>
                      )}

                      <div className={`flex items-start justify-between gap-4 ${recomendado ? 'pt-9' : ''}`}>
                        <div className="space-y-4">
                          <span
                            className={[
                              'inline-flex rounded-full px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.16em]',
                              recomendado ? 'bg-white/90 text-pink-600' : 'bg-slate-100 text-slate-600',
                            ].join(' ')}
                          >
                            {pacote.rotulo}
                          </span>

                          <div className="space-y-2">
                            <p className={`font-bold tracking-tight text-gray-950 ${recomendado ? 'text-3xl' : 'text-2xl'}`}>
                              {pacote.titulo}
                            </p>
                            <p className={`max-w-xs leading-6 text-gray-700 ${recomendado ? 'text-base font-medium' : 'text-sm'}`}>
                              {pacote.headline}
                            </p>
                          </div>
                        </div>

                        <span
                          className={[
                            'flex shrink-0 items-center justify-center rounded-3xl',
                            recomendado ? 'h-14 w-14 bg-[#FC1EAD] text-white shadow-[0_16px_32px_rgba(252,30,173,0.28)]' : 'h-12 w-12 bg-pink-50 text-[#FC1EAD]',
                          ].join(' ')}
                        >
                          <Icon className={recomendado ? 'h-7 w-7' : 'h-6 w-6'} />
                        </span>
                      </div>

                      <p className="mt-5 text-sm leading-6 text-gray-600">{pacote.subtitulo}</p>

                      <div
                        className={[
                          'mt-5 rounded-3xl border px-4 py-4',
                          recomendado ? 'border-pink-200 bg-white/90 shadow-sm' : 'border-slate-200 bg-slate-50/70',
                        ].join(' ')}
                      >
                        <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-gray-500">
                          Sugestão de ativação
                        </p>
                        <div className="mt-3 flex items-end justify-between gap-4">
                          <div>
                            <div className="flex items-baseline gap-2">
                              <span className={`font-black tracking-tight ${recomendado ? 'text-4xl text-[#FC1EAD]' : 'text-3xl text-gray-950'}`}>
                                {metricas.custoSugerido}
                              </span>
                              <span className="text-sm font-semibold text-gray-500">créditos</span>
                            </div>
                            <p className="mt-1 text-xs text-gray-500">
                              {resolveSuggestedDuration(pacote.id, duracoesDisponiveis)}{' '}
                              {resolveSuggestedDuration(pacote.id, duracoesDisponiveis) === 1 ? 'dia' : 'dias'} sugeridos
                            </p>
                          </div>

                          <div className="rounded-2xl bg-pink-50 px-3 py-2 text-right">
                            <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-pink-600">
                              Extras
                            </p>
                            <p className="mt-1 text-sm font-bold text-gray-900">{metricas.custoExtras} créditos</p>
                          </div>
                        </div>
                      </div>

                        <p className="mt-5 text-sm leading-6 text-gray-600">{pacote.descricao}</p>
                        {pacote.recursos.includes('FOTOS_EXTRA_5') ? (
                          <p className="mt-3 rounded-2xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-800">
                            {AVISO_FOTOS_TEMPORARIO}
                          </p>
                        ) : null}

                      <div className="mt-5 flex-1 space-y-3">
                        {pacote.beneficios.map((beneficio) => (
                          <div key={beneficio} className="flex items-start gap-3 text-sm text-gray-700">
                            <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-emerald-50">
                              <CheckIcon className="h-3.5 w-3.5 text-emerald-600" />
                            </span>
                            <span className="leading-6">{beneficio}</span>
                          </div>
                        ))}
                      </div>

                      <div className="mt-6 space-y-3">
                        <Button
                          type="button"
                          onClick={(event) => {
                            event.stopPropagation()
                            escolherPacote(pacote)
                          }}
                          className={[
                            'h-12 w-full rounded-2xl text-sm font-semibold transition',
                            recomendado
                              ? 'bg-[#FC1EAD] text-white shadow-[0_16px_28px_rgba(252,30,173,0.24)] hover:bg-[#e01a9a]'
                              : selecionado
                                ? 'bg-slate-900 text-white hover:bg-slate-800'
                                : 'border border-slate-200 bg-white text-slate-900 hover:bg-slate-50',
                          ].join(' ')}
                        >
                          {selecionado ? 'Plano selecionado' : pacote.cta}
                        </Button>

                        <p className="text-xs font-medium text-gray-500">
                          {recomendado
                            ? 'Melhor custo-benefício para a maioria dos anúncios.'
                            : pacote.id === 'DESTAQUE'
                              ? 'Entrada rápida para começar a ganhar visibilidade.'
                              : 'Escolha forte para quem quer máxima exposição.'}
                        </p>
                      </div>
                    </div>
                  )
                })}
              </div>

              <div className="mt-8 grid items-start gap-5 lg:grid-cols-[minmax(0,1.25fr)_360px]">
                <div className="rounded-[28px] border border-white/70 bg-white/85 p-5 shadow-[0_18px_44px_rgba(15,23,42,0.08)] sm:p-6">
                  <div className="space-y-2">
                    <p className="text-sm font-semibold uppercase tracking-[0.16em] text-pink-600">Duração</p>
                    <h4 className="text-xl font-bold tracking-tight text-gray-950">Escolha o período ideal da campanha</h4>
                    <p className="text-sm leading-6 text-gray-600">
                      Ajuste a duração sem alterar o funcionamento do pacote. O Premium já vem sugerido com o período mais vendedor.
                    </p>
                  </div>

                  <div className="mt-5 grid gap-3 sm:grid-cols-3">
                    {duracoesDisponiveis.map((duracao) => (
                      <button
                        key={duracao}
                        type="button"
                        onClick={() => setDias(duracao)}
                        className={[
                          'h-full rounded-3xl border px-4 py-4 text-left transition',
                          dias === duracao
                            ? 'border-[#FC1EAD] bg-white text-gray-900 shadow-[0_16px_32px_rgba(252,30,173,0.14)]'
                            : 'border-slate-200 bg-slate-50/80 text-gray-600 hover:border-pink-200 hover:bg-white',
                        ].join(' ')}
                      >
                        <div className="text-base font-bold tracking-tight">{duracao} {duracao === 1 ? 'dia' : 'dias'}</div>
                        <div className="mt-1 text-xs text-gray-500">
                          {topoDuracoes.find((item) => item.dias === duracao)?.creditos ?? 0} créditos de impulso
                        </div>
                      </button>
                    ))}
                  </div>

                  <div className="mt-5 rounded-3xl border border-emerald-100 bg-emerald-50 px-4 py-4">
                    <p className="text-sm font-semibold text-emerald-800">Anúncios destacados recebem mais cliques.</p>
                    <p className="mt-1 text-sm leading-6 text-emerald-700">
                      Quanto melhor a combinação entre visibilidade e mídia, maior a chance de gerar contato qualificado.
                    </p>
                  </div>
                </div>

                <div className="rounded-[28px] border border-pink-100 bg-[linear-gradient(180deg,rgba(255,255,255,1)_0%,rgba(255,247,252,1)_100%)] p-5 shadow-[0_20px_60px_rgba(252,30,173,0.12)] lg:sticky lg:top-0 sm:p-6">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-sm font-semibold uppercase tracking-[0.16em] text-pink-600">Resumo do pacote</p>
                      <h4 className="mt-2 text-2xl font-bold tracking-tight text-gray-950">{pacoteAtivo.titulo}</h4>
                    </div>
                    {pacoteAtivo.recomendado && (
                      <span className="rounded-full bg-[#FC1EAD] px-3 py-1 text-[10px] font-extrabold uppercase tracking-[0.16em] text-white">
                        Recomendado
                      </span>
                    )}
                  </div>

                  <div className="mt-5 space-y-3 rounded-3xl border border-white/70 bg-white/90 p-4 text-sm text-gray-600 shadow-sm">
                    <div className="flex items-center justify-between gap-3">
                      <span>Pacote escolhido</span>
                      <span className="font-semibold text-gray-900">{pacoteAtivo.titulo}</span>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <span>Duração</span>
                      <span className="font-semibold text-gray-900">{dias} {dias === 1 ? 'dia' : 'dias'}</span>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <span>Impulsionamento</span>
                      <span className="font-semibold text-gray-900">{totalCreditosImpulsionamento} créditos</span>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <span>Recursos extras</span>
                      <span className="font-semibold text-gray-900">
                        {recursosParaAtivar.reduce((acc, item) => acc + (item.custoCreditos ?? 0), 0)} créditos
                      </span>
                    </div>
                  </div>

                  <div className="mt-5 rounded-3xl bg-white/90 p-4 shadow-sm">
                    <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-gray-500">Inclui</p>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <span className="rounded-full bg-pink-50 px-3 py-1 text-xs font-semibold text-pink-700">
                        Impulsionamento por {dias} {dias === 1 ? 'dia' : 'dias'}
                      </span>
                      {pacoteAtivo.recursos.map((codigo) => (
                        <span key={codigo} className="rounded-full bg-pink-50 px-3 py-1 text-xs font-semibold text-pink-700">
                          {TITLE_BY_CODE[codigo] ?? codigo}
                        </span>
                      ))}
                    </div>
                  </div>

                  <div className="mt-6 flex items-end justify-between gap-3 border-t border-pink-100 pt-5">
                    <div>
                      <p className="text-sm font-semibold text-gray-900">Total do pacote</p>
                      <p className="mt-1 text-xs text-gray-500">Sem alterar preços, créditos ou regras atuais.</p>
                    </div>
                    <span className="text-3xl font-black tracking-tight text-[#FC1EAD]">{totalCreditosPacote}</span>
                  </div>

                  <Button
                    type="button"
                    onClick={ativarPacote}
                    disabled={loading}
                    className="mt-5 h-12 w-full rounded-2xl bg-[#FC1EAD] text-sm font-semibold text-white shadow-[0_18px_30px_rgba(252,30,173,0.22)] hover:bg-[#e01a9a]"
                  >
                    {loading ? 'Processando...' : 'Ativar impulsionamento'}
                  </Button>
                </div>
              </div>
            </section>

          <section className="rounded-2xl border border-gray-200 p-4 sm:p-5">
            <div className="space-y-1">
              <p className="text-sm font-semibold text-gray-900">Opção manual</p>
              <p className="text-sm text-gray-600">
                Continue usando o modo atual para comprar só o impulsionamento por dias ou ativar upgrades avulsos.
              </p>
            </div>

            <div className="mt-5 rounded-xl border border-gray-200 p-4">
              <div className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
                    <CalendarDaysIcon className="h-4 w-4 text-[#FC1EAD]" />
                    Impulsionar por dias
                  </div>
                  <p className="mt-1 text-xs text-gray-600">
                    Sobe seu anúncio nas listagens por um período. Você pode recomprar quando quiser.
                  </p>
                </div>

                <Button
                  disabled={loading}
                  onClick={confirmarImpulsionamentoManual}
                  className="bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
                >
                  <CheckIcon className="mr-1 h-4 w-4" />
                  {loading ? 'Processando...' : 'Confirmar'}
                </Button>
              </div>

              <div className="mt-3 flex flex-wrap items-center gap-2">
                <Button
                  variant="outline"
                  size="icon"
                  disabled={loading}
                  onClick={() => setDias((value) => Math.max(1, value - 1))}
                >
                  <MinusIcon className="h-4 w-4" />
                </Button>

                <span className="w-10 text-center text-base font-semibold">{dias}</span>

                <Button
                  variant="outline"
                  size="icon"
                  disabled={loading}
                  onClick={() => setDias((value) => value + 1)}
                >
                  <PlusIcon className="h-4 w-4" />
                </Button>

                <div className="ml-2 flex items-center gap-2 text-sm text-gray-700">
                  <BanknotesIcon className="h-5 w-5 text-green-600" />
                  <span>
                    Custo: <span className="font-semibold text-[#FC1EAD]">{totalCreditosImpulsionamento} créditos</span>
                  </span>
                </div>
              </div>
            </div>

            <div className="mt-4 rounded-xl border border-gray-200 p-4">
              <div className="min-w-0">
                <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
                  <SparklesIcon className="h-4 w-4 text-[#FC1EAD]" />
                  Upgrades do anúncio
                </div>
                <p className="mt-1 text-xs text-gray-600">
                  Recursos extras que liberam mais mídia e destaque. Se já estiver ativo, você não consegue comprar de novo.
                </p>
              </div>

              <div className="mt-3 space-y-2">
                {upgradesDisponiveis.length === 0 ? (
                  <p className="text-sm text-gray-600">Nenhum upgrade disponível no catálogo.</p>
                ) : (
                  upgradesDisponiveis.map((f) => {
                    const Icon = ICON_BY_CODE[f.codigo] ?? SparklesIcon
                    const titulo = TITLE_BY_CODE[f.codigo] ?? f.nome
                    const st = activeMap.get(f.codigo)
                    const jaAtivo = Boolean(st?.ativo)

                    return (
                      <div
                        key={f.codigo}
                        className="flex items-start justify-between gap-3 rounded-lg border border-gray-200 p-3"
                      >
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-pink-50">
                              <Icon className="h-4 w-4 text-[#FC1EAD]" />
                            </span>

                            <p className="font-semibold text-gray-900">{titulo}</p>

                            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-700">
                              {f.custoCreditos} créditos
                            </span>

                            {jaAtivo && (
                              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold text-emerald-700">
                                <LockClosedIcon className="h-3.5 w-3.5" />
                                Já ativo
                              </span>
                            )}
                          </div>

                            <p className="mt-1 text-xs text-gray-600">
                              {DESCRIPTION_BY_CODE[f.codigo] || f.descricao?.trim() || 'Ative e ganhe mais destaque.'}
                            </p>
                            {f.codigo === 'FOTOS_EXTRA_5' ? (
                              <p className="mt-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] leading-5 text-amber-800">
                                {AVISO_FOTOS_TEMPORARIO}
                              </p>
                            ) : null}

                          {jaAtivo ? (
                            <p className="mt-1 text-[11px] text-emerald-700">
                              {st?.reason || 'Esse upgrade já está ativo neste anúncio.'}
                            </p>
                          ) : f.duracaoHoras ? (
                            <p className="mt-1 text-[11px] text-gray-500">Duração: {f.duracaoHoras}h</p>
                          ) : (
                            <p className="mt-1 text-[11px] text-gray-500">Duração: sem expiração</p>
                          )}
                        </div>

                        <Button
                          disabled={loading || jaAtivo}
                          title={jaAtivo ? (st?.reason || 'Upgrade já ativo neste anúncio') : 'Ativar upgrade'}
                          onClick={() => ativarFeature(f.codigo)}
                          className={
                            jaAtivo
                              ? 'cursor-not-allowed bg-gray-200 text-gray-600 hover:bg-gray-200'
                              : 'bg-[#FC1EAD] text-white hover:bg-[#e01a9a]'
                          }
                        >
                          {jaAtivo ? 'Já ativo' : 'Ativar'}
                        </Button>
                      </div>
                    )
                  })
                )}
              </div>
            </div>
          </section>
        </div>
        </div>

        <DialogFooter className="mt-2 flex flex-col gap-3 sm:flex-row sm:justify-between">
          <Button
            type="button"
            onClick={() => router.push('/creditos')}
            variant="outline"
            className="flex w-full items-center justify-center gap-2 border-pink-400 text-[#FC1EAD] hover:bg-pink-50 sm:w-auto"
            disabled={loading}
          >
            <CreditCardIcon className="h-4 w-4" />
            Comprar créditos
          </Button>

          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
            <XMarkIcon className="mr-1 h-4 w-4" />
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
