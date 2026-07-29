'use client'

import Image from 'next/image'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  Copy,
  CreditCard,
  LoaderCircle,
  QrCode,
  RefreshCw,
  WalletCards,
} from 'lucide-react'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  conciliarCobrancaPix,
  consultarCobrancaPix,
  criarCobrancaPix,
  listarPagamentosPix,
  novaIdempotencyKey,
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

function statusClass(status: PixStatus) {
  if (status === 'APROVADO') return 'border-emerald-200 bg-emerald-50 text-emerald-700'
  if (status === 'PENDENTE') return 'border-amber-200 bg-amber-50 text-amber-800'
  return 'border-red-200 bg-red-50 text-red-700'
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
  const [monetizacao, setMonetizacao] = useState<MinhaMonetizacaoBackend | null>(null)
  const [pagamentos, setPagamentos] = useState<PagamentoPixHistorico[]>([])
  const [checkout, setCheckout] = useState<CobrancaPix | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [statusBusy, setStatusBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [checkoutError, setCheckoutError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const carregar = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [monetizacaoData, pagamentosData] = await Promise.all([
        fetchMinhaMonetizacao(),
        listarPagamentosPix(),
      ])
      setMonetizacao(monetizacaoData)
      setPagamentos(pagamentosData)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Nao foi possivel carregar creditos e planos.')
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
      if (pagamento.status === 'PENDENTE' && !pendentes.has(pagamento.planoCreditoId)) {
        pendentes.set(pagamento.planoCreditoId, pagamento)
      }
    }
    return pendentes
  }, [pagamentos])

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
      setCheckout((current) => ({
        ...next,
        pixCopiaECola: next.pixCopiaECola ?? current?.pixCopiaECola ?? null,
        imagemQrCode: next.imagemQrCode ?? current?.imagemQrCode ?? null,
      }))
      if (next.status !== 'PENDENTE') {
        clearIdempotencyKey(next.planoCreditoId)
        await atualizarDadosDepoisDoPagamento()
      }
    },
    [atualizarDadosDepoisDoPagamento]
  )

  const criarOuRetomar = async (plano: PlanoCredito) => {
    if (busy) return
    setBusy(true)
    setCheckoutError(null)
    try {
      const pendente = pagamentosPendentes.get(plano.id)
      const next = pendente
        ? await consultarCobrancaPix(pendente.pagamentoId)
        : await criarCobrancaPix(plano.id, readOrCreateIdempotencyKey(plano.id))
      await aplicarCheckout(next)
    } catch (caught) {
      setCheckoutError(
        caught instanceof Error ? caught.message : 'Nao foi possivel iniciar a compra via Pix.'
      )
    } finally {
      setBusy(false)
    }
  }

  const atualizarPagamento = useCallback(
    async (silencioso = false) => {
      if (!checkout || statusBusy || checkout.status !== 'PENDENTE') return
      setStatusBusy(true)
      if (!silencioso) setCheckoutError(null)
      try {
        await aplicarCheckout(await conciliarCobrancaPix(checkout.pagamentoId))
      } catch (caught) {
        if (!silencioso) {
          setCheckoutError(
            caught instanceof Error ? caught.message : 'Nao foi possivel atualizar o pagamento.'
          )
        }
      } finally {
        setStatusBusy(false)
      }
    },
    [aplicarCheckout, checkout, statusBusy]
  )

  useEffect(() => {
    if (!checkout || checkout.status !== 'PENDENTE') return
    const timer = window.setInterval(() => {
      void atualizarPagamento(true)
    }, 15_000)
    return () => window.clearInterval(timer)
  }, [checkout, atualizarPagamento])

  const copiarPix = async () => {
    if (!checkout?.pixCopiaECola) return
    try {
      await navigator.clipboard.writeText(checkout.pixCopiaECola)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2_000)
    } catch {
      setCheckoutError('Nao foi possivel copiar automaticamente. Selecione o codigo Pix.')
    }
  }

  return (
    <PainelShell
      title="Créditos e planos"
      description="Consulte seu saldo, escolha um pacote ativo e acompanhe suas compras Pix."
    >
      {loading ? (
        <div className="flex min-h-64 items-center justify-center text-slate-600">
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

          <section aria-labelledby="pacotes-title">
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
                          <p className="mt-1 text-sm leading-5 text-slate-600">{plano.descricao}</p>
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
                        className="mt-auto w-full"
                        disabled={busy}
                        onClick={() => void criarOuRetomar(plano)}
                      >
                        {busy ? <LoaderCircle className="animate-spin" /> : <QrCode />}
                        {pendente ? 'Retomar Pix' : 'Gerar cobrança Pix'}
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
              aria-labelledby="checkout-title"
              className="rounded-md border border-slate-200 bg-white p-5 shadow-sm md:p-6"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 id="checkout-title" className="text-lg font-bold text-slate-950">
                    {checkout.planoNome}
                  </h2>
                  <p className="mt-1 text-sm text-slate-600">
                    {money.format(checkout.valor)} ·{' '}
                    {checkout.quantidadeCreditos.toLocaleString('pt-BR')} créditos
                  </p>
                </div>
                <Badge className={statusClass(checkout.status)} variant="outline">
                  {statusLabel[checkout.status]}
                </Badge>
              </div>

              {checkoutError ? (
                <div className="mt-4 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700" role="alert">
                  {checkoutError}
                </div>
              ) : null}

              <div className="mt-5 grid gap-6 lg:grid-cols-[240px_minmax(0,1fr)]">
                <div className="flex min-h-60 items-center justify-center rounded-md bg-slate-50 p-2">
                  {checkout.imagemQrCode ? (
                    <Image
                      src={checkout.imagemQrCode}
                      alt="QR Code da cobrança Pix"
                      width={224}
                      height={224}
                      unoptimized
                      className="h-56 w-56 object-contain"
                    />
                  ) : checkout.status === 'PENDENTE' ? (
                    <div className="text-center text-sm text-slate-500">
                      <QrCode className="mx-auto mb-2 h-8 w-8" />
                      QR Code indisponível nesta consulta.
                    </div>
                  ) : (
                    <CheckCircle2 className="h-12 w-12 text-emerald-600" />
                  )}
                </div>

                <div className="min-w-0 space-y-4">
                  <dl className="grid gap-3 sm:grid-cols-2">
                    <div>
                      <dt className="text-xs font-semibold uppercase text-slate-500">Identificação</dt>
                      <dd className="mt-1 text-sm font-medium text-slate-900">
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

                  {checkout.pixCopiaECola && checkout.status === 'PENDENTE' ? (
                    <div>
                      <label htmlFor="pix-copy-paste" className="text-sm font-semibold text-slate-900">
                        Pix copia e cola
                      </label>
                      <textarea
                        id="pix-copy-paste"
                        readOnly
                        value={checkout.pixCopiaECola}
                        className="mt-2 min-h-24 w-full resize-none break-all rounded-md border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700"
                      />
                      <Button className="mt-2" variant="outline" onClick={() => void copiarPix()}>
                        <Copy />
                        {copied ? 'Copiado' : 'Copiar código Pix'}
                      </Button>
                    </div>
                  ) : null}

                  {checkout.status === 'PENDENTE' ? (
                    <Button
                      variant="outline"
                      disabled={statusBusy}
                      onClick={() => void atualizarPagamento(false)}
                    >
                      <RefreshCw className={statusBusy ? 'animate-spin' : ''} />
                      Atualizar pagamento
                    </Button>
                  ) : checkout.status === 'APROVADO' ? (
                    <p className="flex items-center gap-2 text-sm font-semibold text-emerald-700">
                      <CheckCircle2 className="h-4 w-4" />
                      Saldo creditado uma única vez no ledger.
                    </p>
                  ) : null}
                </div>
              </div>
            </section>
          ) : checkoutError ? (
            <div className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
              {checkoutError}
            </div>
          ) : null}

          <section aria-labelledby="pagamentos-title">
            <h2 id="pagamentos-title" className="text-lg font-bold text-slate-950">
              Histórico de compras Pix
            </h2>
            <div className="mt-4 space-y-3">
              {pagamentos.length ? (
                pagamentos.map((pagamento) => (
                  <article
                    key={pagamento.pagamentoId}
                    className="grid gap-3 rounded-md border border-slate-200 bg-white p-4 shadow-sm sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
                  >
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="font-semibold text-slate-950">{pagamento.planoNome}</h3>
                        <Badge className={statusClass(pagamento.status)} variant="outline">
                          {statusLabel[pagamento.status]}
                        </Badge>
                      </div>
                      <p className="mt-1 text-sm text-slate-600">
                        {pagamento.quantidadeCreditos.toLocaleString('pt-BR')} créditos ·{' '}
                        {money.format(pagamento.valor)}
                      </p>
                      <p className="mt-1 text-xs text-slate-500">
                        {pagamento.identificacaoSanitizada} · criado em {formatDate(pagamento.criadoEm)}
                        {pagamento.confirmadoEm
                          ? ` · confirmado em ${formatDate(pagamento.confirmadoEm)}`
                          : ''}
                      </p>
                    </div>
                    {pagamento.status === 'PENDENTE' ? (
                      <Button
                        variant="outline"
                        disabled={busy}
                        onClick={() =>
                          void criarOuRetomar(
                            planos.find((plano) => plano.id === pagamento.planoCreditoId) ?? {
                              id: pagamento.planoCreditoId,
                              codigo: '',
                              nome: pagamento.planoNome,
                              descricao: '',
                              quantidadeCreditos: pagamento.quantidadeCreditos,
                              valor: pagamento.valor,
                              moeda: 'BRL',
                              ativo: true,
                              ordemExibicao: 0,
                            }
                          )
                        }
                      >
                        <Clock3 />
                        Consultar
                      </Button>
                    ) : null}
                  </article>
                ))
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
                        {movimento.motivo || movimento.natureza.replaceAll('_', ' ')}
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
    </PainelShell>
  )
}
