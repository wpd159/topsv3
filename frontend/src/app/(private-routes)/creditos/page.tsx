'use client'

import Image from 'next/image'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  AlertTriangle,
  Ban,
  CheckCircle2,
  Clock3,
  Copy,
  CreditCard,
  LoaderCircle,
  QrCode,
  RefreshCw,
  WalletCards,
  X,
} from 'lucide-react'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useAuth } from '@/context/AuthContext'
import {
  cancelarCobrancaPix,
  conciliarCobrancaPix,
  consultarCobrancaPix,
  criarCobrancaPix,
  listarPagamentosPix,
  novaIdempotencyKey,
  PixApiError,
  type CobrancaPix,
  type PagamentoPixHistorico,
  type PixStatus,
} from '@/lib/creditos-pix-api'
import {
  fetchMinhaMonetizacao,
  type MinhaMonetizacaoBackend,
} from '@/features/monetizacao-wizard/api'
import type { PlanoCredito } from '@/features/monetizacao-wizard/types'

const money = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
})

const PIX_CHECKOUT_DISPONIVEL = process.env.NEXT_PUBLIC_EFI_PIX_ENABLED === 'true'
const PIX_INDISPONIVEL_MENSAGEM =
  'Serviço Pix temporariamente indisponível. Tente novamente mais tarde.'

const dateTime = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'short',
  timeStyle: 'short',
  timeZone: 'America/Sao_Paulo',
})

const statusLabel: Record<PixStatus, string> = {
  PENDENTE: 'Pendente',
  APROVADO: 'Aprovado',
  EXPIRADO: 'Expirado',
  CANCELADO: 'Cancelado',
  FALHO: 'Falhou',
}

function formatDate(value: string | null) {
  if (!value) return '-'
  return dateTime.format(new Date(value))
}

function movimentoRotulo(motivo: string | null, natureza: string) {
  const rotulo = motivo?.trim() || natureza.replaceAll('_', ' ')
  return rotulo === 'Ativacao de Story por 24 horas'
    ? 'Ativação de Story por 24 horas'
    : rotulo
}

function statusClass(status: PixStatus) {
  if (status === 'APROVADO') return 'border-emerald-200 bg-emerald-50 text-emerald-700'
  if (status === 'PENDENTE') return 'border-amber-200 bg-amber-50 text-amber-800'
  if (status === 'CANCELADO') return 'border-slate-200 bg-slate-50 text-slate-700'
  return 'border-red-200 bg-red-50 text-red-700'
}

type CheckoutErrorState = {
  message: string
  requestId: string | null
}

const pixErrorMessages: Record<string, string> = {
  PIX_CRIACAO_INDISPONIVEL:
    'Não foi possível iniciar a cobrança Pix agora. Tente novamente.',
  PIX_CONSULTA_INDISPONIVEL:
    'Não foi possível consultar o pagamento agora. A cobrança foi preservada e pode ser retomada com segurança.',
  PIX_QR_CODE_INDISPONIVEL:
    'Não foi possível carregar o QR Code agora. Tente novamente.',
  PIX_CANCELAMENTO_INDISPONIVEL:
    'Não foi possível cancelar a cobrança agora. Tente novamente.',
  PIX_COBRANCA_PENDENTE:
    'Existe uma cobrança Pix pendente. Retome ou cancele essa cobrança antes de iniciar outra.',
  PIX_PAGAMENTO_CONFIRMADO:
    'Este pagamento já foi confirmado e não pode ser cancelado.',
  PIX_CANCELAMENTO_NAO_PERMITIDO:
    'Esta cobrança não pode ser cancelada.',
  PIX_IDEMPOTENCIA_CONFLITANTE:
    'Não foi possível repetir esta operação com segurança.',
}

function checkoutErrorState(caught: unknown, fallback: string): CheckoutErrorState {
  if (caught instanceof PixApiError) {
    return {
      message: (caught.code && pixErrorMessages[caught.code]) || fallback,
      requestId: caught.requestId,
    }
  }
  return {
    message: fallback,
    requestId: null,
  }
}

function expiracaoValida(expiracaoEm: string | null, agora = Date.now()) {
  if (!expiracaoEm) return true
  const expiracao = new Date(expiracaoEm).getTime()
  return Number.isFinite(expiracao) && expiracao > agora
}

function pagamentoPendenteValido(
  pagamento: Pick<PagamentoPixHistorico, 'status' | 'expiracaoEm'>,
  agora = Date.now()
) {
  return pagamento.status === 'PENDENTE' && expiracaoValida(pagamento.expiracaoEm, agora)
}

function checkoutDoHistorico(pagamento: PagamentoPixHistorico): CobrancaPix {
  return {
    ...pagamento,
    pixCopiaECola: null,
    imagemQrCode: null,
    creditado: pagamento.status === 'APROVADO',
    idempotente: true,
  }
}

function pagamentoDoCheckout(checkout: CobrancaPix): PagamentoPixHistorico {
  return {
    pagamentoId: checkout.pagamentoId,
    planoCreditoId: checkout.planoCreditoId,
    planoNome: checkout.planoNome,
    quantidadeCreditos: checkout.quantidadeCreditos,
    valor: checkout.valor,
    criadoEm: checkout.criadoEm,
    expiracaoEm: checkout.expiracaoEm,
    status: checkout.status,
    ambiente: checkout.ambiente,
    cancelavel: checkout.cancelavel,
    identificacaoSanitizada: checkout.identificacaoSanitizada,
    confirmadoEm: checkout.confirmadoEm,
  }
}

function statusExibido(
  pagamento: Pick<CobrancaPix, 'status' | 'expiracaoEm'>,
  agora: number
): PixStatus {
  if (pagamento.status === 'PENDENTE' && !expiracaoValida(pagamento.expiracaoEm, agora)) {
    return 'EXPIRADO'
  }
  return pagamento.status
}

function formatCountdown(expiracaoEm: string | null, agora: number) {
  if (!expiracaoEm) return 'Vencimento não informado'
  const restante = Math.max(0, new Date(expiracaoEm).getTime() - agora)
  const totalSegundos = Math.floor(restante / 1_000)
  const horas = Math.floor(totalSegundos / 3_600)
  const minutos = Math.floor((totalSegundos % 3_600) / 60)
  const segundos = totalSegundos % 60
  return horas > 0
    ? `${horas}h ${String(minutos).padStart(2, '0')}min ${String(segundos).padStart(2, '0')}s`
    : `${String(minutos).padStart(2, '0')}min ${String(segundos).padStart(2, '0')}s`
}

function idempotencyStorageKey(planoId: string) {
  return `topsv3:pix:checkout:${planoId}`
}

function readOrCreateIdempotencyKey(planoId: string) {
  const storageKey = idempotencyStorageKey(planoId)
  const current = window.sessionStorage.getItem(storageKey)
  if (current) return current
  const created = novaIdempotencyKey()
  window.sessionStorage.setItem(storageKey, created)
  return created
}

function clearIdempotencyKey(planoId: string) {
  window.sessionStorage.removeItem(idempotencyStorageKey(planoId))
}

export default function CreditosPage() {
  const { usuario } = useAuth()
  const [monetizacao, setMonetizacao] = useState<MinhaMonetizacaoBackend | null>(null)
  const [pagamentos, setPagamentos] = useState<PagamentoPixHistorico[]>([])
  const [checkout, setCheckout] = useState<CobrancaPix | null>(null)
  const [planoConfirmacao, setPlanoConfirmacao] = useState<PlanoCredito | null>(null)
  const [cancelamentoConfirmacao, setCancelamentoConfirmacao] = useState<CobrancaPix | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [statusBusy, setStatusBusy] = useState(false)
  const [cancelamentoBusy, setCancelamentoBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [checkoutError, setCheckoutError] = useState<CheckoutErrorState | null>(null)
  const [checkoutBloqueado, setCheckoutBloqueado] = useState(false)
  const [copied, setCopied] = useState(false)
  const [agora, setAgora] = useState(() => Date.now())
  const createInFlightRef = useRef(false)
  const pollingInFlightRef = useRef(false)
  const manualInFlightRef = useRef(false)
  const cancelamentoInFlightRef = useRef(false)
  const cancelamentoKeyRef = useRef<{ pagamentoId: string; key: string } | null>(null)
  const copiedTimerRef = useRef<number | null>(null)
  const checkoutRef = useRef<HTMLElement | null>(null)
  const pacotesSectionRef = useRef<HTMLElement | null>(null)
  const returnFocusRef = useRef<HTMLButtonElement | null>(null)
  const focusCheckoutAfterCloseRef = useRef(false)

  const contaBloqueada = Boolean(usuario && usuario.status !== 'ATIVO') || checkoutBloqueado

  const carregar = useCallback(async () => {
    setLoading(true)
    setError(null)
    setCheckoutError(null)
    try {
      const [monetizacaoData, pagamentosData] = await Promise.all([
        fetchMinhaMonetizacao(),
        listarPagamentosPix(),
      ])
      setMonetizacao(monetizacaoData)
      setPagamentos(pagamentosData)

      if (!PIX_CHECKOUT_DISPONIVEL) {
        setCheckout(null)
        return
      }

      const pendente = pagamentosData.find((pagamento) => pagamentoPendenteValido(pagamento))
      if (!pendente) {
        setCheckout(null)
        return
      }

      setCheckout(checkoutDoHistorico(pendente))
      try {
        setCheckout(await consultarCobrancaPix(pendente.pagamentoId))
      } catch (caught) {
        setCheckoutError(
          checkoutErrorState(caught, 'Não foi possível retomar o pagamento pendente.')
        )
      }
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : 'Não foi possível carregar créditos e planos.'
      )
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void carregar()
  }, [carregar])

  const planos = useMemo(
    () => (monetizacao?.pacotesCredito ?? []).filter((plano) => plano.ativo),
    [monetizacao]
  )

  const pagamentosPendentes = useMemo(() => {
    const pendentes = new Map<string, PagamentoPixHistorico>()
    for (const pagamento of pagamentos) {
      if (pagamentoPendenteValido(pagamento, agora) && !pendentes.has(pagamento.planoCreditoId)) {
        pendentes.set(pagamento.planoCreditoId, pagamento)
      }
    }
    return pendentes
  }, [agora, pagamentos])

  const pagamentoPendenteAtivo = useMemo(
    () => pagamentos.find((pagamento) => pagamentoPendenteValido(pagamento, agora)) ?? null,
    [agora, pagamentos]
  )

  const atualizarDadosDepoisDoPagamento = useCallback(async () => {
    const [monetizacaoData, pagamentosData] = await Promise.all([
      fetchMinhaMonetizacao(),
      listarPagamentosPix(),
    ])
    setMonetizacao(monetizacaoData)
    setPagamentos(pagamentosData)
  }, [])

  const aplicarCheckout = useCallback(
    async (next: CobrancaPix) => {
      setCheckout((current) =>
        next.status === 'PENDENTE'
          ? {
              ...next,
              pixCopiaECola: next.pixCopiaECola ?? current?.pixCopiaECola ?? null,
              imagemQrCode: next.imagemQrCode ?? current?.imagemQrCode ?? null,
            }
          : { ...next, pixCopiaECola: null, imagemQrCode: null }
      )
      setPagamentos((current) => {
        const resumo = pagamentoDoCheckout(next)
        return current.some((pagamento) => pagamento.pagamentoId === next.pagamentoId)
          ? current.map((pagamento) =>
              pagamento.pagamentoId === next.pagamentoId ? resumo : pagamento
            )
          : [resumo, ...current]
      })
      setCheckoutError(null)
      if (next.status !== 'PENDENTE') {
        clearIdempotencyKey(next.planoCreditoId)
        try {
          await atualizarDadosDepoisDoPagamento()
        } catch (caught) {
          setCheckoutError(
            checkoutErrorState(
              caught,
              'O pagamento foi atualizado, mas não foi possível recarregar saldo e histórico.'
            )
          )
        }
      }
    },
    [atualizarDadosDepoisDoPagamento]
  )

  const focarCheckout = useCallback(() => {
    window.setTimeout(() => checkoutRef.current?.focus(), 0)
  }, [])

  const focarPacotes = useCallback(() => {
    const section = pacotesSectionRef.current
    if (!section) return
    section.scrollIntoView({ behavior: 'smooth', block: 'start' })
    window.setTimeout(() => section.focus({ preventScroll: true }), 0)
  }, [])

  const fecharCheckout = useCallback(() => {
    setCheckout(null)
    setCheckoutError(null)
    window.setTimeout(() => pacotesSectionRef.current?.focus({ preventScroll: true }), 0)
  }, [])

  const retomarPagamento = useCallback(
    async (pagamento: PagamentoPixHistorico) => {
      if (!PIX_CHECKOUT_DISPONIVEL) return
      if (createInFlightRef.current) return
      createInFlightRef.current = true
      setBusy(true)
      setCheckoutError(null)
      setCheckout(checkoutDoHistorico(pagamento))
      try {
        await aplicarCheckout(await conciliarCobrancaPix(pagamento.pagamentoId))
        focarCheckout()
      } catch (caught) {
        setCheckoutError(
          checkoutErrorState(caught, 'Não foi possível retomar o pagamento pendente.')
        )
      } finally {
        createInFlightRef.current = false
        setBusy(false)
      }
    },
    [aplicarCheckout, focarCheckout]
  )

  const selecionarPlano = (plano: PlanoCredito, trigger: HTMLButtonElement) => {
    if (!PIX_CHECKOUT_DISPONIVEL) return
    if (contaBloqueada) {
      setCheckoutError({
        message: 'Sua conta precisa estar ativa para iniciar uma compra Pix.',
        requestId: null,
      })
      return
    }
    const pendente = pagamentosPendentes.get(plano.id)
    if (pendente) {
      void retomarPagamento(pendente)
      return
    }
    if (pagamentoPendenteAtivo) return
    returnFocusRef.current = trigger
    setCheckoutError(null)
    setPlanoConfirmacao(plano)
  }

  const confirmarCobranca = async () => {
    if (
      !PIX_CHECKOUT_DISPONIVEL
      || !planoConfirmacao
      || contaBloqueada
      || createInFlightRef.current
    ) return
    createInFlightRef.current = true
    setBusy(true)
    setCheckoutError(null)
    try {
      const next = await criarCobrancaPix(
        planoConfirmacao.id,
        readOrCreateIdempotencyKey(planoConfirmacao.id)
      )
      await aplicarCheckout(next)
      focusCheckoutAfterCloseRef.current = true
      setPlanoConfirmacao(null)
    } catch (caught) {
      if (caught instanceof PixApiError && [401, 403].includes(caught.status)) {
        setCheckoutBloqueado(true)
      }
      setCheckoutError(
        checkoutErrorState(caught, 'Não foi possível iniciar a compra via Pix.')
      )
    } finally {
      createInFlightRef.current = false
      setBusy(false)
    }
  }

  const consultarPagamento = useCallback(async () => {
    if (
      !checkout
      || checkout.status !== 'PENDENTE'
      || pollingInFlightRef.current
      || manualInFlightRef.current
      || cancelamentoInFlightRef.current
    ) return
    pollingInFlightRef.current = true
    try {
      await aplicarCheckout(await conciliarCobrancaPix(checkout.pagamentoId))
    } catch {
      // O polling permanece silencioso; a verificacao manual apresenta o erro e o requestId.
    } finally {
      pollingInFlightRef.current = false
    }
  }, [aplicarCheckout, checkout])

  const verificarPagamento = useCallback(async () => {
    if (
      !checkout
      || checkout.status !== 'PENDENTE'
      || manualInFlightRef.current
      || pollingInFlightRef.current
      || cancelamentoInFlightRef.current
    ) return
    manualInFlightRef.current = true
    setStatusBusy(true)
    setCheckoutError(null)
    try {
      await aplicarCheckout(await conciliarCobrancaPix(checkout.pagamentoId))
    } catch (caught) {
      setCheckoutError(
        checkoutErrorState(caught, 'Não foi possível verificar o pagamento.')
      )
    } finally {
      manualInFlightRef.current = false
      setStatusBusy(false)
    }
  }, [aplicarCheckout, checkout])

  const abrirCancelamento = useCallback((cobranca: CobrancaPix) => {
    if (!cobranca.cancelavel || cobranca.status !== 'PENDENTE') return
    setCheckoutError(null)
    setCancelamentoConfirmacao(cobranca)
  }, [])

  const confirmarCancelamento = useCallback(async () => {
    const cobranca = cancelamentoConfirmacao
    if (!cobranca || cancelamentoInFlightRef.current) return

    cancelamentoInFlightRef.current = true
    setCancelamentoBusy(true)
    setCheckoutError(null)
    const currentKey = cancelamentoKeyRef.current
    const idempotencyKey = currentKey?.pagamentoId === cobranca.pagamentoId
      ? currentKey.key
      : novaIdempotencyKey()
    cancelamentoKeyRef.current = {
      pagamentoId: cobranca.pagamentoId,
      key: idempotencyKey,
    }

    try {
      const next = await cancelarCobrancaPix(cobranca.pagamentoId, idempotencyKey)
      await aplicarCheckout(next)
      cancelamentoKeyRef.current = null
      setCancelamentoConfirmacao(null)
      setCheckout(null)
      focarPacotes()
    } catch (caught) {
      const errorState = checkoutErrorState(
        caught,
        'Não foi possível cancelar a cobrança agora. Tente novamente.'
      )
      setCancelamentoConfirmacao(null)
      setCheckoutError(errorState)
      if (caught instanceof PixApiError && caught.code === 'PIX_PAGAMENTO_CONFIRMADO') {
        try {
          await atualizarDadosDepoisDoPagamento()
          setCheckout(null)
        } catch {
          // O erro original permanece visível com seu código de atendimento.
        }
      }
    } finally {
      cancelamentoInFlightRef.current = false
      setCancelamentoBusy(false)
    }
  }, [
    aplicarCheckout,
    atualizarDadosDepoisDoPagamento,
    cancelamentoConfirmacao,
    focarPacotes,
  ])

  const checkoutVencido = Boolean(
    checkout
      && checkout.status === 'PENDENTE'
      && !expiracaoValida(checkout.expiracaoEm, agora)
  )

  useEffect(() => {
    if (!checkout || checkout.status !== 'PENDENTE' || !checkout.expiracaoEm) return
    setAgora(Date.now())
    const timer = window.setInterval(() => setAgora(Date.now()), 1_000)
    return () => window.clearInterval(timer)
  }, [checkout])

  useEffect(() => {
    if (!checkout || checkout.status !== 'PENDENTE' || checkoutVencido) return
    let timer: number | null = null

    const stopTimer = () => {
      if (timer !== null) window.clearInterval(timer)
      timer = null
    }
    const startTimer = () => {
      stopTimer()
      timer = window.setInterval(() => void consultarPagamento(), 15_000)
    }
    const handleVisibility = () => {
      if (document.hidden) {
        stopTimer()
        return
      }
      void consultarPagamento()
      startTimer()
    }

    if (!document.hidden) startTimer()
    document.addEventListener('visibilitychange', handleVisibility)
    return () => {
      stopTimer()
      document.removeEventListener('visibilitychange', handleVisibility)
    }
  }, [checkout, checkoutVencido, consultarPagamento])

  useEffect(
    () => () => {
      if (copiedTimerRef.current !== null) window.clearTimeout(copiedTimerRef.current)
    },
    []
  )

  const copiarPix = async () => {
    if (!checkout?.pixCopiaECola) return
    try {
      await navigator.clipboard.writeText(checkout.pixCopiaECola)
      setCopied(true)
      if (copiedTimerRef.current !== null) window.clearTimeout(copiedTimerRef.current)
      copiedTimerRef.current = window.setTimeout(() => setCopied(false), 2_000)
    } catch {
      setCheckoutError({
        message: 'Não foi possível copiar o código Pix. Tente novamente.',
        requestId: null,
      })
    }
  }

  const checkoutStatus = checkout ? statusExibido(checkout, agora) : null
  const countdown = checkout ? formatCountdown(checkout.expiracaoEm, agora) : null

  return (
    <PainelShell
      title="Créditos e planos"
      description="Consulte seu saldo, escolha um pacote ativo e acompanhe suas compras Pix."
    >
      {loading ? (
        <div
          className="flex min-h-64 items-center justify-center text-slate-600"
          role="status"
          aria-live="polite"
        >
          <LoaderCircle className="mr-2 h-5 w-5 animate-spin" />
          Carregando créditos...
        </div>
      ) : error || !monetizacao ? (
        <section className="rounded-md border border-red-200 bg-red-50 p-5" role="alert">
          <div className="flex items-start gap-3">
            <AlertTriangle className="mt-0.5 h-5 w-5 text-red-600" />
            <div className="min-w-0 flex-1">
              <h2 className="font-semibold text-red-900">Não foi possível carregar</h2>
              <p className="mt-1 text-sm text-red-700">{error}</p>
              <Button className="mt-4" variant="outline" onClick={() => void carregar()}>
                <RefreshCw />
                Tentar novamente
              </Button>
            </div>
          </div>
        </section>
      ) : (
        <div className="space-y-8 pb-10">
          <section className="grid gap-4 md:grid-cols-2" aria-label="Resumo de créditos">
            <div className="rounded-md border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-center gap-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-md bg-pink-50 text-[#C51683]">
                  <WalletCards className="h-5 w-5" />
                </span>
                <div>
                  <p className="text-sm font-medium text-slate-500">Saldo disponível</p>
                  <p className="text-2xl font-bold text-slate-950">
                    {monetizacao.saldoCreditos.toLocaleString('pt-BR')} créditos
                  </p>
                </div>
              </div>
              <Button
                type="button"
                className="mt-4 w-full sm:w-auto"
                disabled={!PIX_CHECKOUT_DISPONIVEL || !planos.length}
                onClick={focarPacotes}
              >
                <CreditCard />
                Comprar créditos
              </Button>
              {!planos.length ? (
                <p className="mt-2 text-sm text-slate-500">
                  Nenhum pacote de créditos está disponível no momento
                </p>
              ) : null}
            </div>
            <div className="rounded-md border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-center gap-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-md bg-sky-50 text-sky-700">
                  <CreditCard className="h-5 w-5" />
                </span>
                <div>
                  <p className="text-sm font-medium text-slate-500">Compras Pix</p>
                  <p className="font-semibold text-slate-900">
                    {pagamentos.length
                      ? `${pagamentos.length} registro${pagamentos.length === 1 ? '' : 's'}`
                      : 'Nenhuma compra registrada'}
                  </p>
                </div>
              </div>
            </div>
          </section>

          {!PIX_CHECKOUT_DISPONIVEL ? (
            <section
              id="pix-indisponivel"
              className="rounded-md border border-amber-200 bg-amber-50 p-4"
              role="status"
            >
              <p className="text-sm font-medium text-amber-900">
                {PIX_INDISPONIVEL_MENSAGEM}
              </p>
            </section>
          ) : contaBloqueada ? (
            <section className="rounded-md border border-red-200 bg-red-50 p-4" role="alert">
              <div className="flex items-start gap-3">
                <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
                <div>
                  <h2 className="font-semibold text-red-900">Compra de créditos indisponível</h2>
                  <p className="mt-1 text-sm text-red-700">
                    Sua conta precisa estar ativa para iniciar uma cobrança Pix.
                  </p>
                </div>
              </div>
            </section>
          ) : pagamentoPendenteAtivo ? (
            <section className="rounded-md border border-amber-200 bg-amber-50 p-4" role="status">
              <p className="text-sm font-medium text-amber-900">
                Existe uma cobrança Pix pendente. Retome esse pagamento antes de iniciar outro.
              </p>
            </section>
          ) : null}

          <section
            ref={pacotesSectionRef}
            tabIndex={-1}
            aria-labelledby="pacotes-title"
            className="scroll-mt-4 rounded-md outline-none focus-visible:ring-2 focus-visible:ring-[#C51683]"
          >
            <div className="mb-4">
              <h2 id="pacotes-title" className="text-lg font-bold text-slate-950">
                Pacotes disponíveis
              </h2>
              <p className="mt-1 text-sm text-slate-600">
                Valores e quantidades são fornecidos pelo catálogo vigente.
              </p>
            </div>
            {planos.length ? (
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                {planos.map((plano) => {
                  const pendente = pagamentosPendentes.get(plano.id)
                  return (
                    <article
                      key={plano.id}
                      className="flex min-h-52 flex-col rounded-md border border-slate-200 bg-white p-5 shadow-sm"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <h3 className="font-bold text-slate-950">{plano.nome}</h3>
                          {plano.descricao?.trim() ? (
                            <p className="mt-1 text-sm leading-5 text-slate-600">
                              {plano.descricao}
                            </p>
                          ) : null}
                        </div>
                        {pendente ? (
                          <Badge className={statusClass('PENDENTE')} variant="outline">
                            Pendente
                          </Badge>
                        ) : null}
                      </div>
                      <div className="mt-5">
                        <p className="text-2xl font-bold text-slate-950">
                          {money.format(plano.valor)}
                        </p>
                        <p className="text-sm font-semibold text-[#C51683]">
                          {plano.quantidadeCreditos.toLocaleString('pt-BR')} créditos
                        </p>
                      </div>
                      <Button
                        type="button"
                        className="mt-auto w-full"
                        disabled={
                          !PIX_CHECKOUT_DISPONIVEL
                          || busy
                          || contaBloqueada
                          || Boolean(pagamentoPendenteAtivo && !pendente)
                        }
                        onClick={(event) => selecionarPlano(plano, event.currentTarget)}
                      >
                        {busy ? <LoaderCircle className="animate-spin" /> : <QrCode />}
                        {pendente
                          ? 'Retomar Pix'
                          : pagamentoPendenteAtivo
                            ? 'Finalize o Pix pendente'
                            : 'Comprar créditos'}
                      </Button>
                    </article>
                  )
                })}
              </div>
            ) : (
              <div className="rounded-md border border-slate-200 bg-slate-50 p-6 text-sm text-slate-600">
                Nenhum pacote de créditos está disponível no momento.
              </div>
            )}
          </section>

          {checkout ? (
            <section
              ref={checkoutRef}
              tabIndex={-1}
              aria-labelledby="checkout-title"
              className="scroll-mt-4 rounded-md border border-slate-200 bg-white p-4 shadow-sm outline-none focus-visible:ring-2 focus-visible:ring-[#C51683] sm:p-5 md:p-6"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <h2 id="checkout-title" className="text-lg font-bold text-slate-950">
                    {checkout.planoNome}
                  </h2>
                  <p className="mt-1 text-sm text-slate-600">
                    {money.format(checkout.valor)} ·{' '}
                    {checkout.quantidadeCreditos.toLocaleString('pt-BR')} créditos
                  </p>
                </div>
                <div className="flex flex-wrap items-center justify-end gap-2">
                  {checkoutStatus ? (
                    <Badge className={statusClass(checkoutStatus)} variant="outline">
                      {statusLabel[checkoutStatus]}
                    </Badge>
                  ) : null}
                  {checkout.status === 'PENDENTE' && checkout.cancelavel ? (
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={cancelamentoBusy}
                      onClick={() => abrirCancelamento(checkout)}
                    >
                      <Ban />
                      Cancelar cobrança
                    </Button>
                  ) : null}
                  {checkout.status === 'PENDENTE' ? (
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      disabled={cancelamentoBusy}
                      aria-label="Fechar checkout Pix e retomar depois"
                      onClick={fecharCheckout}
                    >
                      <X />
                      Fechar
                    </Button>
                  ) : null}
                </div>
              </div>

              {checkout.ambiente === 'HOMOLOGACAO' ? (
                <div className="mt-4 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900" role="status">
                  Ambiente de homologação. Esta cobrança é destinada somente a testes e não deve ser paga pelo aplicativo bancário.
                </div>
              ) : null}

              {checkoutError ? (
                <div className="mt-4 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700" role="alert">
                  <p>{checkoutError.message}</p>
                  {checkoutError.requestId ? (
                    <p className="mt-1 break-all text-xs">
                      Código de atendimento: <code>{checkoutError.requestId}</code>
                    </p>
                  ) : null}
                </div>
              ) : null}

              <div className="mt-5 grid min-w-0 gap-6 lg:grid-cols-[minmax(0,240px)_minmax(0,1fr)]">
                <div className="flex aspect-square w-full max-w-60 items-center justify-center justify-self-center rounded-md bg-slate-50 p-2 lg:justify-self-start">
                  {checkoutStatus === 'PENDENTE' && checkout.imagemQrCode ? (
                    <Image
                      src={checkout.imagemQrCode}
                      alt="QR Code da cobrança Pix"
                      width={224}
                      height={224}
                      unoptimized
                      className="h-auto max-h-56 w-full max-w-56 object-contain"
                    />
                  ) : checkoutStatus === 'PENDENTE' ? (
                    <div className="px-3 text-center text-sm text-slate-500">
                      <QrCode className="mx-auto mb-2 h-8 w-8" />
                      QR Code indisponível nesta consulta.
                    </div>
                  ) : checkoutStatus === 'APROVADO' ? (
                    <CheckCircle2 className="h-12 w-12 text-emerald-600" />
                  ) : (
                    <AlertTriangle className="h-12 w-12 text-slate-500" />
                  )}
                </div>

                <div className="min-w-0 space-y-4">
                  <dl className="grid gap-3 sm:grid-cols-2">
                    <div className="min-w-0">
                      <dt className="text-xs font-semibold uppercase text-slate-500">Identificação</dt>
                      <dd className="mt-1 break-words text-sm font-medium text-slate-900">
                        {checkout.identificacaoSanitizada}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-xs font-semibold uppercase text-slate-500">Vencimento</dt>
                      <dd className="mt-1 text-sm font-medium text-slate-900">
                        {formatDate(checkout.expiracaoEm)}
                      </dd>
                    </div>
                  </dl>

                  {checkoutStatus === 'PENDENTE' ? (
                    <p className="flex items-center gap-2 text-sm font-medium text-slate-700" role="status" aria-live="polite">
                      <Clock3 className="h-4 w-4 shrink-0" />
                      {checkoutVencido ? 'Prazo encerrado; verifique o status.' : `Tempo restante: ${countdown}`}
                    </p>
                  ) : null}

                  {checkout.pixCopiaECola && checkoutStatus === 'PENDENTE' ? (
                    <div className="min-w-0 rounded-md border border-slate-200 bg-slate-50 p-3">
                      <p className="text-sm font-semibold text-slate-900">
                        Código Pix disponível
                      </p>
                      <Button
                        type="button"
                        className="mt-2 w-full sm:w-auto"
                        variant="outline"
                        aria-describedby="pix-copy-status"
                        onClick={() => void copiarPix()}
                      >
                        <Copy />
                        Copiar código Pix
                      </Button>
                      <p
                        id="pix-copy-status"
                        className="mt-2 min-h-5 text-sm text-emerald-700"
                        role="status"
                        aria-live="polite"
                      >
                        {copied ? 'Código Pix copiado' : ''}
                      </p>
                    </div>
                  ) : null}

                  {checkoutStatus === 'PENDENTE' ? (
                    <Button
                      type="button"
                      className="w-full sm:w-auto"
                      variant="outline"
                      disabled={statusBusy}
                      onClick={() => void verificarPagamento()}
                    >
                      <RefreshCw className={statusBusy ? 'animate-spin' : ''} />
                      Verificar pagamento
                    </Button>
                  ) : checkoutStatus === 'APROVADO' ? (
                    <p className="flex items-center gap-2 text-sm font-semibold text-emerald-700" role="status">
                      <CheckCircle2 className="h-4 w-4 shrink-0" />
                      {checkout.quantidadeCreditos.toLocaleString('pt-BR')} créditos adicionados ao saldo.
                    </p>
                  ) : checkoutStatus === 'EXPIRADO' ? (
                    <p className="text-sm text-slate-700" role="status">
                      Esta cobrança expirou. Você pode escolher um pacote e gerar uma nova cobrança.
                    </p>
                  ) : checkoutStatus === 'CANCELADO' ? (
                    <p className="text-sm text-slate-700" role="status">
                      Esta cobrança foi cancelada e permanece somente no histórico.
                    </p>
                  ) : (
                    <p className="text-sm text-red-700" role="alert">
                      A cobrança não foi concluída. Tente novamente quando o serviço estiver disponível.
                    </p>
                  )}
                </div>
              </div>
            </section>
          ) : checkoutError ? (
            <div className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
              <p>{checkoutError.message}</p>
              {checkoutError.requestId ? (
                <p className="mt-1 break-all text-xs">
                  Código de atendimento: <code>{checkoutError.requestId}</code>
                </p>
              ) : null}
            </div>
          ) : null}
          <section aria-labelledby="pagamentos-title">
            <h2 id="pagamentos-title" className="text-lg font-bold text-slate-950">
              Histórico de compras via Pix
            </h2>
            <div className="mt-4 space-y-3">
              {pagamentos.length ? (
                pagamentos.map((pagamento) => {
                  const visualStatus = statusExibido(pagamento, agora)
                  const podeRetomar = pagamentoPendenteValido(pagamento, agora)
                  return (
                    <article
                      key={pagamento.pagamentoId}
                      className="grid gap-3 rounded-md border border-slate-200 bg-white p-4 shadow-sm sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
                    >
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <h3 className="font-semibold text-slate-950">{pagamento.planoNome}</h3>
                          <Badge className={statusClass(visualStatus)} variant="outline">
                            {statusLabel[visualStatus]}
                          </Badge>
                        </div>
                        <p className="mt-1 text-sm text-slate-600">
                          {pagamento.quantidadeCreditos.toLocaleString('pt-BR')} créditos ·{' '}
                          {money.format(pagamento.valor)}
                        </p>
                        <p className="mt-1 break-words text-xs text-slate-500">
                          {pagamento.identificacaoSanitizada} · criado em {formatDate(pagamento.criadoEm)}
                          {pagamento.confirmadoEm
                            ? ` · confirmado em ${formatDate(pagamento.confirmadoEm)}`
                            : ''}
                        </p>
                      </div>
                      {podeRetomar ? (
                        <Button
                          type="button"
                          className="w-full sm:w-auto"
                          variant="outline"
                          disabled={busy}
                          onClick={() => void retomarPagamento(pagamento)}
                        >
                          <Clock3 />
                          Retomar Pix
                        </Button>
                      ) : null}
                    </article>
                  )
                })
              ) : (
                <div className="rounded-md border border-slate-200 bg-slate-50 p-6 text-sm text-slate-600">
                  Nenhuma compra Pix registrada.
                </div>
              )}
            </div>
          </section>

          <section aria-labelledby="ledger-title">
            <h2 id="ledger-title" className="text-lg font-bold text-slate-950">
              Histórico de créditos
            </h2>
            <div className="mt-4 space-y-2">
              {monetizacao.historico.length ? (
                monetizacao.historico.slice(0, 10).map((movimento) => (
                  <div
                    key={movimento.id}
                    className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 py-3 text-sm"
                  >
                    <div>
                      <p className="font-medium text-slate-900">
                        {movimentoRotulo(movimento.motivo, movimento.natureza)}
                      </p>
                      <p className="text-xs text-slate-500">{formatDate(movimento.criadoEm)}</p>
                    </div>
                    <p className="font-semibold text-slate-900">
                      {movimento.saldoAnterior.toLocaleString('pt-BR')} →{' '}
                      {movimento.saldoPosterior.toLocaleString('pt-BR')}
                    </p>
                  </div>
                ))
              ) : (
                <div className="rounded-md border border-slate-200 bg-slate-50 p-6 text-sm text-slate-600">
                  Nenhum movimento de crédito registrado.
                </div>
              )}
            </div>
          </section>
        </div>
      )}
      <Dialog
        open={Boolean(planoConfirmacao)}
        onOpenChange={(open) => {
          if (!open && !busy) setPlanoConfirmacao(null)
        }}
      >
        <DialogContent
          className="max-h-[calc(100dvh-1rem)] w-[calc(100vw-1rem)] overflow-y-auto sm:max-w-md"
          onCloseAutoFocus={(event) => {
            event.preventDefault()
            if (focusCheckoutAfterCloseRef.current) {
              focusCheckoutAfterCloseRef.current = false
              focarCheckout()
              return
            }
            returnFocusRef.current?.focus()
          }}
        >
          <DialogHeader>
            <DialogTitle>Confirmar compra de créditos</DialogTitle>
            <DialogDescription>
              A cobrança pertence à sua carteira e não ativa anúncio, benefício ou Story.
            </DialogDescription>
          </DialogHeader>

          {planoConfirmacao ? (
            <dl className="grid gap-3 rounded-md border border-slate-200 bg-slate-50 p-4 sm:grid-cols-2">
              <div className="min-w-0">
                <dt className="text-xs font-semibold uppercase text-slate-500">Pacote</dt>
                <dd className="mt-1 break-words font-semibold text-slate-950">
                  {planoConfirmacao.nome}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-semibold uppercase text-slate-500">Valor</dt>
                <dd className="mt-1 font-semibold text-slate-950">
                  {money.format(planoConfirmacao.valor)}
                </dd>
              </div>
              <div className="sm:col-span-2">
                <dt className="text-xs font-semibold uppercase text-slate-500">Créditos</dt>
                <dd className="mt-1 font-semibold text-[#C51683]">
                  {planoConfirmacao.quantidadeCreditos.toLocaleString('pt-BR')} créditos
                </dd>
              </div>
            </dl>
          ) : null}

          {checkoutError ? (
            <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700" role="alert">
              <p>{checkoutError.message}</p>
              {checkoutError.requestId ? (
                <p className="mt-1 break-all text-xs">
                  Código de atendimento: <code>{checkoutError.requestId}</code>
                </p>
              ) : null}
            </div>
          ) : null}

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={busy}
              onClick={() => setPlanoConfirmacao(null)}
            >
              Cancelar
            </Button>
            <Button
              type="button"
              disabled={!PIX_CHECKOUT_DISPONIVEL || busy || contaBloqueada}
              onClick={() => void confirmarCobranca()}
            >
              {busy ? <LoaderCircle className="animate-spin" /> : <QrCode />}
              Confirmar e gerar Pix
            </Button>
          </DialogFooter>
          {busy ? <p className="sr-only" role="status">Criando cobrança Pix.</p> : null}
        </DialogContent>
      </Dialog>
      <Dialog
        open={Boolean(cancelamentoConfirmacao)}
        onOpenChange={(open) => {
          if (!open && !cancelamentoBusy) setCancelamentoConfirmacao(null)
        }}
      >
        <DialogContent className="w-[calc(100vw-1rem)] sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Cancelar cobrança Pix?</DialogTitle>
            <DialogDescription>
              O QR Code e o código Pix deixarão de ser válidos. Nenhum crédito será adicionado.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={cancelamentoBusy}
              onClick={() => setCancelamentoConfirmacao(null)}
            >
              Manter cobrança
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={cancelamentoBusy}
              onClick={() => void confirmarCancelamento()}
            >
              {cancelamentoBusy ? <LoaderCircle className="animate-spin" /> : <Ban />}
              Cancelar cobrança
            </Button>
          </DialogFooter>
          {cancelamentoBusy ? (
            <p className="sr-only" role="status">Cancelando cobrança Pix.</p>
          ) : null}
        </DialogContent>
      </Dialog>
    </PainelShell>
  )
}
