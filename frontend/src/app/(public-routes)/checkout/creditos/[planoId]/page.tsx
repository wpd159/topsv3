'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import Image from 'next/image'
import { useParams, useRouter, useSearchParams } from 'next/navigation'
import { QRCodeSVG } from 'qrcode.react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { toast } from 'sonner'
import { CreditoBloqueioModal } from '@/components/modals/credito-bloqueio-modal'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/context/AuthContext'
import { cpfFormatoBasicoValido, maskCPF } from '@/lib/checkout-identificacao'
import { messageFromApiBody } from '@/utils/read-api-error-response'

type CheckoutData = {
  pagamentoId: number
  planoId: number
  planoNome: string
  creditos: number
  valor: number
  provider: string
  metodoPagamento: string
  txid: string
  status: string
  statusEfetivo: string
  pixCopiaECola: string
  pixQrCode?: string | null
  expiracao?: string | null
  criadoEm?: string | null
}

const API = process.env.NEXT_PUBLIC_API_URL

function formatarValor(valor: number) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatarData(valor?: string | null) {
  if (!valor) return '-'
  const data = new Date(valor)
  if (Number.isNaN(data.getTime())) return '-'
  return data.toLocaleString('pt-BR')
}

function normalizarStatus(status?: string | null) {
  const upper = (status || '').toUpperCase()
  switch (upper) {
    case 'APPROVED':
      return 'Aprovado'
    case 'PENDING':
    case 'CREATED':
      return 'Aguardando pagamento'
    case 'CANCELLED':
      return 'Expirado'
    default:
      return status || '-'
  }
}

export default function CheckoutCreditosPage() {
  const router = useRouter()
  const params = useParams<{ planoId: string }>()
  const searchParams = useSearchParams()
  const { refresh } = useAuth()

  const planoId = params?.planoId || ''
  const [checkout, setCheckout] = useState<CheckoutData | null>(null)
  const pagamentoIdParam = searchParams.get('pagamentoId')
  const [loading, setLoading] = useState(!!pagamentoIdParam)
  const [nomeCompletoIdent, setNomeCompletoIdent] = useState('')
  const [cpfIdent, setCpfIdent] = useState('')
  const [checking, setChecking] = useState(false)
  const [bloqueioOpen, setBloqueioOpen] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const saldoRefrescado = useRef(false)

  const pagamentoId = pagamentoIdParam

  const carregar = async (forcarNovaVerificacao = false) => {
    if (!API || !planoId) return
    if (!pagamentoId) return

    try {
      setErro(null)
      if (!forcarNovaVerificacao) {
        setLoading(true)
      } else {
        setChecking(true)
      }

      const endpoint = forcarNovaVerificacao
        ? `${API}/checkout/creditos/pagamentos/${pagamentoId}/verificar`
        : `${API}/checkout/creditos/pagamentos/${pagamentoId}`

      const method = forcarNovaVerificacao ? 'POST' : 'GET'

      const res = await fetch(endpoint, {
        method,
        credentials: 'include',
      })

      const raw = await res.text()
      let data: Record<string, unknown> = {}
      if (raw.trim()) {
        try {
          data = JSON.parse(raw) as Record<string, unknown>
        } catch {
          if (res.ok) {
            setErro('Resposta inválida do servidor.')
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
          setErro('Faça login para continuar o checkout.')
          return
        }
        const msg = messageFromApiBody(raw, res.status, 'Erro ao carregar checkout.')
        console.error('[checkout]', method, endpoint, res.status, msg, raw.slice(0, 500))
        setErro(msg)
        return
      }

      setCheckout(data as CheckoutData)
    } catch (error: any) {
      setErro(error?.message || 'Erro ao carregar checkout Pix.')
    } finally {
      setLoading(false)
      setChecking(false)
    }
  }

  const submeterIdentificacaoCheckout = async () => {
    if (!API || !planoId) return
    const nome = nomeCompletoIdent.trim()
    if (nome.length < 3) {
      toast.error('Informe seu nome completo.')
      return
    }
    const cpfLimpo = cpfIdent.replace(/\D/g, '')
    if (cpfLimpo.length !== 11) {
      toast.error('O CPF deve ter 11 dígitos. Verifique o número e tente novamente.')
      return
    }
    if (!cpfFormatoBasicoValido(cpfIdent)) {
      toast.error('CPF inválido. Confira os dígitos informados.')
      return
    }
    try {
      setErro(null)
      setLoading(true)
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
            setErro('Resposta inválida do servidor.')
            setLoading(false)
            return
          }
        }
      }
      if (!res.ok) {
        if (data?.code === 'ANUNCIO_NAO_ATIVO') {
          setBloqueioOpen(true)
          setLoading(false)
          return
        }
        if (res.status === 401) {
          setErro('Faça login para continuar o checkout.')
          setLoading(false)
          return
        }
        const msg = messageFromApiBody(raw, res.status, 'Erro ao iniciar checkout.')
        setErro(msg)
        setLoading(false)
        return
      }
      const pid = (data as CheckoutData).pagamentoId
      if (pid == null) {
        setErro('Resposta inválida do servidor.')
        setLoading(false)
        return
      }
      setCheckout(data as CheckoutData)
      router.replace(`/checkout/creditos/${planoId}?pagamentoId=${pid}`)
    } catch (error: any) {
      setErro(error?.message || 'Erro ao iniciar checkout Pix.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!planoId) return
    if (!pagamentoId) {
      setLoading(false)
      return
    }
    carregar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [planoId, pagamentoId])

  useEffect(() => {
    if (!checkout || !pagamentoId) return
    if (!['PENDING', 'CREATED'].includes((checkout.status || '').toUpperCase())) return

    const timer = setInterval(async () => {
      try {
        const res = await fetch(`${API}/checkout/creditos/pagamentos/${pagamentoId}`, {
          credentials: 'include',
          cache: 'no-store',
        })
        if (!res.ok) return
        const data = await res.json()
        setCheckout(data)
      } catch {
      }
    }, 7000)

    return () => clearInterval(timer)
  }, [checkout, pagamentoId])

  useEffect(() => {
    if (checkout?.status === 'APPROVED' && !saldoRefrescado.current) {
      saldoRefrescado.current = true
      refresh().catch(() => null)
    }
  }, [checkout?.status, refresh])

  const expirado = useMemo(
    () => checkout?.status === 'CANCELLED',
    [checkout?.status]
  )

  const copiarCodigo = async () => {
    if (!checkout?.pixCopiaECola) return
    await navigator.clipboard.writeText(checkout.pixCopiaECola)
    toast.success('Código Pix copiado.')
  }

  return (
    <section className="mx-auto max-w-5xl px-4 py-10">
      <CreditoBloqueioModal open={bloqueioOpen} onOpenChange={setBloqueioOpen} />

      <div className="mb-8">
        <h1 className="text-3xl font-extrabold text-slate-900">Checkout interno de créditos</h1>
        <p className="mt-2 text-sm text-slate-600">
          Gere o Pix, copie o código ou escaneie o QR Code e acompanhe a confirmação sem sair do site.
        </p>
      </div>

      {loading ? (
        <div className="flex min-h-[40vh] items-center justify-center text-slate-500">
          Preparando checkout Pix...
        </div>
      ) : erro ? (
        <Card className="border border-red-200">
          <CardContent className="p-6">
            <p className="font-semibold text-red-700">{erro}</p>
            <Button
              className="mt-4 bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
              onClick={() => router.push('/creditos')}
            >
              Voltar para créditos
            </Button>
          </CardContent>
        </Card>
      ) : checkout ? (
        <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
          <Card className="border border-gray-200 shadow-sm">
            <CardContent className="p-6">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-sm text-slate-500">Plano</p>
                  <h2 className="text-2xl font-extrabold text-slate-900">{checkout.planoNome}</h2>
                </div>

                <Badge
                  variant="outline"
                  className={`${
                    checkout.status === 'APPROVED'
                      ? 'border-green-300 bg-green-50 text-green-700'
                      : expirado
                        ? 'border-red-300 bg-red-50 text-red-700'
                        : 'border-yellow-300 bg-yellow-50 text-yellow-700'
                  }`}
                >
                  {normalizarStatus(checkout.status)}
                </Badge>
              </div>

              <div className="mt-6 grid gap-4 sm:grid-cols-3">
                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Valor</p>
                  <p className="mt-2 text-xl font-bold text-slate-900">{formatarValor(checkout.valor)}</p>
                </div>
                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Créditos</p>
                  <p className="mt-2 text-xl font-bold text-slate-900">{checkout.creditos}</p>
                </div>
                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Expira em</p>
                  <p className="mt-2 text-sm font-semibold text-slate-900">{formatarData(checkout.expiracao)}</p>
                </div>
              </div>

              <div className="mt-6 rounded-2xl border border-dashed border-gray-200 p-4">
                <p className="text-sm font-semibold text-slate-900">Copia e cola Pix</p>
                <div className="mt-3 rounded-xl bg-slate-50 p-4 text-sm text-slate-700 break-all">
                  {checkout.pixCopiaECola || 'Código Pix indisponível.'}
                </div>

                <div className="mt-4 flex flex-wrap gap-3">
                  <Button
                    onClick={copiarCodigo}
                    className="bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
                    disabled={!checkout.pixCopiaECola}
                  >
                    Copiar código Pix
                  </Button>

                  <Button
                    variant="outline"
                    onClick={() => carregar(true)}
                    disabled={checking || checkout.status === 'APPROVED'}
                  >
                    {checking ? 'Verificando...' : 'Já paguei, verificar novamente'}
                  </Button>
                </div>
              </div>

              <div className="mt-6 text-xs text-slate-500">
                <p>Provedor: {checkout.provider}</p>
                <p>Método: {checkout.metodoPagamento?.toUpperCase()}</p>
                <p>TXID: {checkout.txid || '-'}</p>
                <p>Status Efetivo: {checkout.statusEfetivo || '-'}</p>
              </div>

              {checkout.status === 'APPROVED' && (
                <div className="mt-6 rounded-xl border border-green-200 bg-green-50 p-4 text-sm text-green-700">
                  Pagamento confirmado. Seus créditos já foram adicionados ao saldo.
                </div>
              )}
            </CardContent>
          </Card>

          <Card className="border border-gray-200 shadow-sm">
            <CardContent className="flex h-full flex-col items-center justify-center p-6 text-center">
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                {checkout.pixQrCode?.startsWith('data:image') ? (
                  <Image
                    src={checkout.pixQrCode}
                    alt="QR Code Pix"
                    width={260}
                    height={260}
                    className="h-[260px] w-[260px]"
                    unoptimized
                  />
                ) : checkout.pixCopiaECola ? (
                  <QRCodeSVG value={checkout.pixCopiaECola} size={260} />
                ) : (
                  <div className="flex h-[260px] w-[260px] items-center justify-center bg-slate-50 text-sm text-slate-500">
                    QR Code indisponível
                  </div>
                )}
              </div>

              <p className="mt-5 text-sm text-slate-600">
                Abra o app do seu banco, escaneie o QR Code e volte para verificar a confirmação.
              </p>

              <Button
                variant="outline"
                className="mt-5 w-full"
                onClick={() => router.push('/minha-conta')}
              >
                Voltar para minha conta
              </Button>
            </CardContent>
          </Card>
        </div>
      ) : !pagamentoId ? (
        <Card className="border border-gray-200 shadow-sm">
          <CardContent className="p-6">
            <h2 className="text-xl font-extrabold text-slate-900">Identificação para o Pix</h2>
            <p className="mt-1 text-sm text-slate-600">
              Finalidade: registrar esta compra de créditos e o pagamento Pix. Os dados não atualizam seu cadastro na
              plataforma. Tratamento conforme LGPD na medida aplicável.
            </p>
            <div className="mt-6 grid gap-4">
              <div className="grid gap-2">
                <Label htmlFor="ident-nome">Nome completo</Label>
                <Input
                  id="ident-nome"
                  autoComplete="name"
                  value={nomeCompletoIdent}
                  onChange={(e) => setNomeCompletoIdent(e.target.value)}
                  placeholder="Nome e sobrenome"
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="ident-cpf">CPF</Label>
                <Input
                  id="ident-cpf"
                  autoComplete="off"
                  inputMode="numeric"
                  value={cpfIdent}
                  onChange={(e) => setCpfIdent(maskCPF(e.target.value))}
                  placeholder="000.000.000-00 ou só números"
                />
              </div>
            </div>
            <div className="mt-6 flex flex-wrap gap-3">
              <Button
                type="button"
                className="bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
                onClick={() => submeterIdentificacaoCheckout()}
              >
                Gerar Pix
              </Button>
              <Button type="button" variant="outline" onClick={() => router.push('/creditos')}>
                Voltar
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : null}
    </section>
  )
}
