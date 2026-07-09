'use client'

import { useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { toast } from 'sonner'
import { motion } from 'framer-motion'
import {
  BoltIcon,
  RocketLaunchIcon,
  SparklesIcon,
  CheckCircleIcon,
  EyeSlashIcon,
  ChatBubbleLeftRightIcon,
} from '@heroicons/react/24/solid'
import { CreditoBloqueioModal } from '@/components/modals/credito-bloqueio-modal'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/context/AuthContext'
import { LoginModal } from '@/components/modals/login-modal'
import { cpfFormatoBasicoValido, maskCPF } from '@/lib/checkout-identificacao'
import { messageFromApiBody } from '@/utils/read-api-error-response'

type PlanoCredito = {
  id: number
  nome: string
  creditos: number
  valor: number
  descricao: string
}

type FeatureCatalogoItem = {
  codigo: string
  nome: string
  descricao?: string | null
  custoCreditos: number
  duracaoHoras?: number | null
  escopo?: 'ANUNCIO' | 'USUARIO'
  ativo?: boolean
}

type CheckoutResponse = {
  pagamentoId: number
}

const API = process.env.NEXT_PUBLIC_API_URL
const AVISO_FOTOS_TEMPORARIO =
  'Benefício temporário: ao vencer, o anúncio volta ao limite padrão de fotos e as fotos adicionais serão removidas.'
const BENEFICIO_FOTOS_EXTRA_TITULO = 'Até 10 fotos no anúncio'
const BENEFICIO_FOTOS_EXTRA_DESCRICAO =
  'Amplie o limite do anúncio para até 10 fotos durante a vigência do benefício.'

function brl(v: number) {
  return v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function safeNumber(n: any, fallback = 0) {
  const x = Number(n)
  return Number.isFinite(x) ? x : fallback
}

export default function CreditosPage() {
  const router = useRouter()
  const { usuario, carregando: authCarregando } = useAuth()
  const [loginModalOpen, setLoginModalOpen] = useState(false)
  const [planos, setPlanos] = useState<PlanoCredito[]>([])
  const [features, setFeatures] = useState<FeatureCatalogoItem[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [carregandoPlano, setCarregandoPlano] = useState<number | null>(null)
  const [bloqueioOpen, setBloqueioOpen] = useState(false)
  const [checkoutDialogOpen, setCheckoutDialogOpen] = useState(false)
  const [planoParaCheckout, setPlanoParaCheckout] = useState<number | null>(null)
  const [nomeCompletoCheckout, setNomeCompletoCheckout] = useState('')
  const [cpfCheckout, setCpfCheckout] = useState('')

  async function fetchJson<T>(url: string): Promise<T> {
    const res = await fetch(url, {
      credentials: 'include',
      cache: 'no-store',
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json()
  }

  async function carregarCatalogo(): Promise<FeatureCatalogoItem[] | null> {
    if (!API) return null
    try {
      const data = await fetchJson<FeatureCatalogoItem[]>(`${API}/features/catalogo`)
      return Array.isArray(data) ? data : null
    } catch {
      return null
    }
  }

  useEffect(() => {
    const run = async () => {
      try {
        if (!API) throw new Error('NEXT_PUBLIC_API_URL não definido')

        const [planosResp, catalogoResp] = await Promise.all([
          fetchJson<PlanoCredito[]>(`${API}/creditos/planos`),
          carregarCatalogo(),
        ])

        setPlanos(Array.isArray(planosResp) ? planosResp : [])
        setFeatures(catalogoResp)
      } catch {
        toast.error('Erro ao carregar créditos.')
      } finally {
        setLoading(false)
      }
    }
    run()
  }, [])

  const planosOrdenados = useMemo(() => {
    const arr = [...planos]
    arr.sort((a, b) => safeNumber(a.valor) - safeNumber(b.valor))
    return arr
  }, [planos])

  const planoMelhorCusto = useMemo(() => {
    if (!planosOrdenados.length) return null
    const withRatio = planosOrdenados
      .map((p) => ({
        ...p,
        custoPorCredito: safeNumber(p.valor) / Math.max(1, safeNumber(p.creditos)),
      }))
      .sort((a, b) => a.custoPorCredito - b.custoPorCredito)
    return withRatio[0]
  }, [planosOrdenados])

  const planoDestaque = useMemo(() => {
    if (!planosOrdenados.length) return null
    return planoMelhorCusto ?? planosOrdenados[Math.floor(planosOrdenados.length / 2)]
  }, [planosOrdenados, planoMelhorCusto])

  const featuresFallback: FeatureCatalogoItem[] = [
    { codigo: 'CARROSSEL_FOTOS', nome: 'Carrossel de Fotos', custoCreditos: 0, duracaoHoras: null },
    { codigo: 'FOTOS_EXTRA_5', nome: BENEFICIO_FOTOS_EXTRA_TITULO, custoCreditos: 0, duracaoHoras: null },
    { codigo: 'VIDEO_1', nome: 'Vídeo no anúncio', custoCreditos: 0, duracaoHoras: null },
    { codigo: 'STORIES', nome: 'Story do anúncio', custoCreditos: 0, duracaoHoras: 24 },
    { codigo: 'WHATSAPP_CARD', nome: 'WhatsApp visível no card', custoCreditos: 0, duracaoHoras: null },
    { codigo: 'OCULTAR_IDADE', nome: 'Ocultar idade', custoCreditos: 0, duracaoHoras: null },
  ]

  const catalogo = (features && features.length ? features : featuresFallback).filter(Boolean)

  const getFeatureDisplayName = (feature: FeatureCatalogoItem) =>
    feature.codigo === 'FOTOS_EXTRA_5' ? BENEFICIO_FOTOS_EXTRA_TITULO : feature.nome

  const getFeatureDisplayDescription = (feature: FeatureCatalogoItem) =>
    feature.codigo === 'FOTOS_EXTRA_5'
      ? BENEFICIO_FOTOS_EXTRA_DESCRICAO
      : feature.descricao?.trim() || 'Ative no seu anúncio e ganhe destaque extra na vitrine.'

  const impactoPorCodigo: Record<string, string> = {
    CARROSSEL_FOTOS: 'Impacto alto na vitrine',
    FOTOS_EXTRA_5: 'Mais mídia para convencer',
    VIDEO_1: 'Impacto alto na conversão',
    STORIES: 'Exposição rápida e recorrente',
    WHATSAPP_CARD: 'Contato mais direto',
    OCULTAR_IDADE: 'Mais controle de exposição',
  }

  const abrirCheckout = async (planoId: number) => {
    if (authCarregando) return
    if (!usuario) {
      setLoginModalOpen(true)
      return
    }
    if (!API) {
      toast.error('API não configurada.')
      return
    }

    try {
      setCarregandoPlano(planoId)
      const res = await fetch(`${API}/anuncios/meus`, {
        credentials: 'include',
        cache: 'no-store',
      })
      if (!res.ok) {
        toast.error('Não foi possível verificar seus anúncios.')
        return
      }
      const data = await res.json()
      const lista = Array.isArray(data) ? data : []
      const temAnuncioAtivo = lista.some(
        (a: { status?: string }) => String(a?.status ?? '').toUpperCase() === 'ATIVO'
      )
      if (!temAnuncioAtivo) {
        setBloqueioOpen(true)
        return
      }

      setPlanoParaCheckout(planoId)
      setNomeCompletoCheckout('')
      setCpfCheckout('')
      setCheckoutDialogOpen(true)
    } catch {
      toast.error('Não foi possível verificar seus anúncios.')
    } finally {
      setCarregandoPlano(null)
    }
  }

  const handleCheckout = async () => {
    const planoId = planoParaCheckout
    if (planoId == null || !API) return

    const nome = nomeCompletoCheckout.trim()
    if (nome.length < 3) {
      toast.error('Informe seu nome completo.')
      return
    }
    const cpfLimpo = cpfCheckout.replace(/\D/g, '')
    if (cpfLimpo.length !== 11) {
      toast.error('O CPF deve ter 11 dígitos. Verifique o número e tente novamente.')
      return
    }
    if (!cpfFormatoBasicoValido(cpfCheckout)) {
      toast.error('CPF inválido. Confira os dígitos informados.')
      return
    }

    try {
      setCarregandoPlano(planoId)

      const res = await fetch(`${API}/checkout/creditos/${planoId}`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          nomeCompleto: nome,
          cpf: cpfLimpo,
        }),
      })

      const raw = await res.text()
      let data: Record<string, unknown> = {}
      if (raw.trim()) {
        try {
          data = JSON.parse(raw) as Record<string, unknown>
        } catch {
          if (res.ok) {
            toast.error('Resposta inválida do servidor.')
            return
          }
        }
      }

      if (!res.ok) {
        if (data?.code === 'ANUNCIO_NAO_ATIVO') {
          setBloqueioOpen(true)
          return
        }

        if (res.status === 401) {
          toast.error('Faça login para comprar créditos.')
          return
        }

        const msg = messageFromApiBody(raw, res.status, 'Erro ao iniciar checkout')
        console.error('[checkout POST /checkout/creditos]', res.status, msg, raw.slice(0, 500))
        toast.error(msg)
        return
      }

      const pid = (data as CheckoutResponse).pagamentoId
      if (pid == null) {
        toast.error('Resposta inválida do servidor.')
        return
      }

      setCheckoutDialogOpen(false)
      setPlanoParaCheckout(null)
      router.push(`/checkout/creditos/${planoId}?pagamentoId=${pid}`)
    } catch (error: any) {
      toast.error(error?.message || 'Erro ao abrir o checkout Pix.')
    } finally {
      setCarregandoPlano(null)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center text-slate-500">
        Carregando créditos...
      </div>
    )
  }

  return (
    <section className="mx-auto max-w-6xl px-4 py-12">
      <CreditoBloqueioModal open={bloqueioOpen} onOpenChange={setBloqueioOpen} />

      <Dialog open={checkoutDialogOpen} onOpenChange={setCheckoutDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Dados para o Pix</DialogTitle>
            <DialogDescription>
              Informe nome completo e CPF conforme constam no seu documento. Finalidade: registrar esta compra de
              créditos e o meio de pagamento Pix; não atualizam seu cadastro na plataforma nem são usados para
              finalidades não relacionadas a esta transação, na medida aplicável à LGPD.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-2">
              <Label htmlFor="checkout-nome">Nome completo</Label>
              <Input
                id="checkout-nome"
                autoComplete="name"
                value={nomeCompletoCheckout}
                onChange={(e) => setNomeCompletoCheckout(e.target.value)}
                placeholder="Nome e sobrenome"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="checkout-cpf">CPF</Label>
              <Input
                id="checkout-cpf"
                autoComplete="off"
                inputMode="numeric"
                value={cpfCheckout}
                onChange={(e) => setCpfCheckout(maskCPF(e.target.value))}
                placeholder="000.000.000-00 ou só números"
              />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setCheckoutDialogOpen(false)}>
              Cancelar
            </Button>
            <Button
              type="button"
              className="bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
              onClick={() => handleCheckout()}
              disabled={planoParaCheckout != null && carregandoPlano === planoParaCheckout}
            >
              {planoParaCheckout != null && carregandoPlano === planoParaCheckout ? 'Preparando...' : 'Continuar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <LoginModal
        open={loginModalOpen}
        onOpenChange={setLoginModalOpen}
        redirectAfterSuccess="/creditos"
      />

      <div className="flex flex-col items-center text-center">
        <p className="inline-flex items-center gap-2 rounded-full bg-pink-50 px-3 py-1 text-sm font-semibold text-[#FC1EAD]">
          <SparklesIcon className="h-4 w-4" />
          Créditos do TopsdoJob
        </p>

        <h1 className="mt-4 text-3xl font-extrabold tracking-tight text-slate-900 md:text-4xl">
          Compre créditos e destrave destaque de verdade
        </h1>

        <p className="mt-3 max-w-2xl text-base leading-relaxed text-slate-600">
          Créditos são sua moeda para impulsionar anúncio e ativar recursos como carrossel, até 10 fotos no anúncio, vídeo e stories.
          A compra agora acontece no checkout interno com Pix.
        </p>
      </div>

      <div className="mt-8 grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
        <div className="rounded-3xl border border-slate-200 bg-[linear-gradient(135deg,#fff5fb_0%,#ffffff_60%,#f8fbff_100%)] p-6 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[#FC1EAD]/10">
              <RocketLaunchIcon className="h-5 w-5 text-[#FC1EAD]" />
            </div>
            <div>
              <h2 className="text-xl font-extrabold text-slate-900">Créditos viram visibilidade</h2>
              <p className="text-sm text-slate-600">
                Use o saldo para destacar seus anúncios, ganhar mais mídia e aparecer antes nas decisões do cliente.
              </p>
            </div>
          </div>

          <div className="mt-5 grid gap-3 sm:grid-cols-3">
            {[
              {
                title: 'Destaque',
                text: 'Prioridade na listagem e presença comercial mais forte.',
              },
              {
                title: 'Premium',
                text: 'Combina vídeo, carrossel, mais fotos e maior percepção de valor.',
              },
              {
                title: 'Top',
                text: 'Maior exposição para os anúncios com melhor potencial de conversão.',
              },
            ].map((item) => (
              <div key={item.title} className="rounded-2xl border border-white/80 bg-white/90 p-4">
                <p className="text-sm font-bold text-slate-900">{item.title}</p>
                <p className="mt-2 text-sm leading-6 text-slate-600">{item.text}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-bold text-slate-900">Resumo comercial</h2>
          <p className="mt-1 text-sm text-slate-600">
            Quanto mais fácil for ativar recursos pagos, maior a chance de você aproveitar o melhor anúncio no momento certo.
          </p>

          <div className="mt-5 space-y-3">
            <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Saldo atual</p>
              <p className="mt-2 text-2xl font-extrabold text-slate-900">
                {usuario ? `${usuario.creditos ?? 0} créditos` : 'Entre para ver seu saldo'}
              </p>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Melhor uso</p>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                Combine impulso com vídeo, carrossel e WhatsApp card para elevar a percepção de valor antes do clique.
              </p>
            </div>
          </div>

          <div className="mt-5 flex flex-col gap-3">
            <Link
              href={usuario ? '/painel' : '/anunciar/wizard'}
              className="inline-flex items-center justify-center gap-2 rounded-2xl bg-slate-900 px-4 py-3 text-sm font-semibold text-white transition hover:bg-slate-800"
            >
              Ver painel comercial
              <RocketLaunchIcon className="h-4 w-4" />
            </Link>
            <Link
              href={usuario ? '/meus-anuncios' : '/anunciar/wizard'}
              className="inline-flex items-center justify-center gap-2 rounded-2xl border border-slate-200 px-4 py-3 text-sm font-semibold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50"
            >
              Gerenciar anúncios
            </Link>
          </div>
        </div>
      </div>

      <div className="mt-10">
        <div className="flex items-end justify-between gap-4">
          <div>
            <h2 className="text-2xl font-extrabold text-slate-900">Planos de crédito</h2>
            <p className="mt-1 text-slate-600">
              Escolha um plano, gere o Pix e os créditos entram automaticamente após a confirmação.
            </p>
          </div>
        </div>

        <div className="mt-6 grid grid-cols-1 gap-6 md:grid-cols-3">
          {planosOrdenados.map((plano, idx) => {
            const isFeatured = planoDestaque?.id === plano.id
            const custoPorCredito = safeNumber(plano.valor) / Math.max(1, safeNumber(plano.creditos))

            return (
              <motion.div
                key={plano.id}
                initial={{ opacity: 0, y: 12 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: idx * 0.06 }}
                className={[
                  'relative rounded-2xl border bg-white p-6 shadow-sm',
                  isFeatured ? 'border-pink-200 ring-2 ring-pink-100' : 'border-slate-200',
                ].join(' ')}
              >
                {isFeatured && (
                  <div className="absolute -top-3 left-5 rounded-full bg-[#FC1EAD] px-3 py-1 text-xs font-bold text-white">
                    Melhor custo/benefício
                  </div>
                )}

                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold text-slate-500">Plano</p>
                    <h3 className="text-xl font-extrabold text-slate-900">{plano.nome}</h3>
                  </div>

                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-pink-50">
                    <BoltIcon className="h-5 w-5 text-[#FC1EAD]" />
                  </div>
                </div>

                <div className="mt-5">
                  <p className="text-4xl font-extrabold text-slate-900">
                    {plano.creditos}
                    <span className="ml-2 text-base font-semibold text-slate-500">créditos</span>
                  </p>

                  <p className="mt-2 text-lg font-bold text-slate-900">{brl(plano.valor)}</p>

                  <p className="mt-1 text-sm text-slate-500">~ {brl(custoPorCredito)} por crédito</p>

                  <p className="mt-4 text-sm text-slate-600">{plano.descricao}</p>
                </div>

                <Button
                  onClick={() => abrirCheckout(plano.id)}
                  disabled={carregandoPlano === plano.id}
                  className="mt-6 w-full rounded-xl bg-[#FC1EAD] py-6 font-semibold text-white hover:bg-[#e01a9a]"
                >
                  {carregandoPlano === plano.id ? 'Preparando Pix...' : 'Comprar créditos'}
                </Button>
              </motion.div>
            )
          })}
        </div>
      </div>

      <div className="mt-12 grid grid-cols-1 gap-4 md:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm md:col-span-2">
          <h2 className="text-lg font-bold text-slate-900">O que você compra com créditos</h2>
          <p className="mt-1 text-sm text-slate-600">
            Aqui é o mapa do investimento: o usuário entende exatamente onde o dinheiro vira destaque.
          </p>

          <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2">
            {catalogo.map((f) => (
              <div key={f.codigo} className="flex items-start gap-3 rounded-xl border border-slate-200 p-4">
                <div className="mt-0.5 flex h-9 w-9 items-center justify-center rounded-xl bg-pink-50">
                  <BoltIcon className="h-5 w-5 text-[#FC1EAD]" />
                </div>

                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="font-semibold text-slate-900">{getFeatureDisplayName(f)}</p>

                    {impactoPorCodigo[f.codigo] ? (
                      <span className="rounded-full bg-pink-50 px-2 py-0.5 text-xs font-semibold text-[#C41E73]">
                        {impactoPorCodigo[f.codigo]}
                      </span>
                    ) : null}

                    {!!f.custoCreditos && (
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">
                        {f.custoCreditos} créditos
                      </span>
                    )}
                  </div>

                  <p className="mt-1 text-sm text-slate-600">
                    {getFeatureDisplayDescription(f)}
                  </p>
                  {f.codigo === 'FOTOS_EXTRA_5' ? (
                    <p className="mt-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-800">
                      {AVISO_FOTOS_TEMPORARIO}
                    </p>
                  ) : null}

                  {f.duracaoHoras ? (
                    <p className="mt-1 text-xs text-slate-500">Duração: {f.duracaoHoras}h</p>
                  ) : (
                    <p className="mt-1 text-xs text-slate-500">Duração: depende do plano/compra</p>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-bold text-slate-900">Impulsionamento</h2>
          <p className="mt-1 text-sm text-slate-600">
            Impulsionar joga seu anúncio para cima nos resultados.
          </p>

          <div className="mt-5 space-y-3">
            {[
              'Mais prioridade na listagem',
              'Mais chances de clique e WhatsApp',
              'Combina com carrossel e vídeo',
              'Pode ser reforçado com ocultar idade e WhatsApp card',
            ].map((t) => (
              <div key={t} className="flex items-start gap-2">
                <CheckCircleIcon className="mt-0.5 h-4 w-4 text-emerald-600" />
                <p className="text-sm text-slate-700">{t}</p>
              </div>
            ))}
          </div>

          <div className="mt-6 flex items-center gap-3 rounded-xl bg-pink-50 p-4">
            <RocketLaunchIcon className="h-6 w-6 text-[#FC1EAD]" />
            <p className="text-sm font-semibold text-slate-900">
              Melhor combinação: impulsionar + vídeo + até 10 fotos
            </p>
          </div>

          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <div className="flex items-center gap-2 rounded-xl border border-slate-200 p-3">
              <ChatBubbleLeftRightIcon className="h-5 w-5 text-emerald-600" />
              <div>
                <p className="text-sm font-semibold text-slate-900">WhatsApp card</p>
                <p className="text-xs text-slate-500">Contato mais direto no card do anúncio</p>
              </div>
            </div>
            <div className="flex items-center gap-2 rounded-xl border border-slate-200 p-3">
              <EyeSlashIcon className="h-5 w-5 text-slate-700" />
              <div>
                <p className="text-sm font-semibold text-slate-900">Ocultar idade</p>
                <p className="text-xs text-slate-500">Mesmo padrão de ativação por créditos</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
