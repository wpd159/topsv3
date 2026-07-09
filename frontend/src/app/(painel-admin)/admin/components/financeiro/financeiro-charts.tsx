'use client'

import { useEffect, useState } from 'react'
import {
  ResponsiveContainer,
  ComposedChart,
  Bar,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
} from 'recharts'
import { toast } from 'sonner'
import { BanknotesIcon, ChartBarSquareIcon, ShoppingCartIcon } from '@heroicons/react/24/solid'

interface Pagamento {
  id: number
  status: string
  valor: number
  creditos: number
  dataPagamento: string
}

type Periodo = '7d' | '30d' | 'mes'

type FinanceiroBucket = {
  dia: string
  rotulo: string
  receita: number
  transacoes: number
  creditos: number
}

async function readApiError(response: Response, fallback: string) {
  const raw = await response.text().catch(() => '')
  if (!raw.trim()) return `${fallback} (HTTP ${response.status})`

  try {
    const parsed = JSON.parse(raw) as { error?: string; message?: string }
    return String(parsed?.error || parsed?.message || '').trim() || `${fallback} (HTTP ${response.status})`
  } catch {
    return raw.trim() || `${fallback} (HTTP ${response.status})`
  }
}

function formatCurrency(value: number) {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatCurrencyAxis(value: number) {
  if (value >= 1000) {
    return `R$ ${(value / 1000).toLocaleString('pt-BR', {
      minimumFractionDigits: value >= 10000 ? 0 : 1,
      maximumFractionDigits: 1,
    })}k`
  }

  return `R$ ${Number(value).toLocaleString('pt-BR')}`
}

function formatarRotuloDia(isoDate: string) {
  const data = new Date(`${isoDate}T12:00:00`)
  return data.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
}

function formatarDataTooltip(isoDate: string) {
  const data = new Date(`${isoDate}T12:00:00`)
  return data.toLocaleDateString('pt-BR', { day: '2-digit', month: 'long' })
}

function calcularTopoEixo(valorMaximo: number, minimo: number) {
  const base = Math.max(valorMaximo, minimo)

  if (base <= 10) {
    return base + 1
  }

  const magnitude = 10 ** Math.floor(Math.log10(base))
  return Math.ceil((base * 1.18) / magnitude) * magnitude
}

function buildChartData(pagamentos: Pagamento[], periodo: Periodo) {
  const hoje = new Date()
  hoje.setHours(0, 0, 0, 0)

  const inicio = new Date(hoje)
  if (periodo === '7d') {
    inicio.setDate(hoje.getDate() - 6)
  } else if (periodo === '30d') {
    inicio.setDate(hoje.getDate() - 29)
  } else {
    inicio.setDate(1)
  }

  const buckets = new Map<string, FinanceiroBucket>()
  const cursor = new Date(inicio)

  while (cursor <= hoje) {
    const iso = cursor.toISOString().slice(0, 10)
    buckets.set(iso, {
      dia: iso,
      rotulo: formatarRotuloDia(iso),
      receita: 0,
      transacoes: 0,
      creditos: 0,
    })
    cursor.setDate(cursor.getDate() + 1)
  }

  pagamentos.forEach((pagamento) => {
    if (pagamento.status?.toLowerCase() !== 'approved' || !pagamento.dataPagamento) return

    const data = new Date(pagamento.dataPagamento)
    data.setHours(0, 0, 0, 0)

    if (data < inicio || data > hoje) return

    const iso = data.toISOString().slice(0, 10)
    const bucket = buckets.get(iso)
    if (!bucket) return

    bucket.receita += Number(pagamento.valor || 0)
    bucket.transacoes += 1
    bucket.creditos += Number(pagamento.creditos || 0)
  })

  return Array.from(buckets.values())
}

function CustomTooltip({ active, payload, label }: any) {
  if (!active || !payload?.length) return null

  const receita = payload.find((item: any) => item.dataKey === 'receita')?.value ?? 0
  const transacoes = payload.find((item: any) => item.dataKey === 'transacoes')?.value ?? 0
  const creditos = payload.find((item: any) => item.dataKey === 'creditos')?.payload?.creditos ?? 0
  const dia = payload[0]?.payload?.dia ?? label

  return (
    <div className="min-w-[220px] rounded-3xl border border-slate-200/80 bg-white/95 p-4 text-xs shadow-2xl backdrop-blur">
      <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-400">Data</p>
      <p className="mt-1 font-semibold text-slate-900">{formatarDataTooltip(dia)}</p>

      <div className="mt-4 space-y-2.5">
        <p className="flex items-center justify-between gap-4 text-slate-600">
          <span className="flex items-center gap-2">
            <span className="h-2.5 w-2.5 rounded-full bg-[#FC1EAD]" />
            Receita
          </span>
          <span className="font-semibold text-[#c2185b]">{formatCurrency(receita)}</span>
        </p>
        <p className="flex items-center justify-between gap-4 text-slate-600">
          <span className="flex items-center gap-2">
            <span className="h-2.5 w-2.5 rounded-full bg-slate-900" />
            Vendas
          </span>
          <span className="font-semibold text-slate-900">{transacoes}</span>
        </p>
        <p className="flex items-center justify-between gap-4 text-slate-600">
          <span>Creditos vendidos</span>
          <span className="font-semibold text-slate-900">{creditos}</span>
        </p>
      </div>
    </div>
  )
}

export default function FinanceiroGrafico() {
  const [pagamentos, setPagamentos] = useState<Pagamento[]>([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [periodo, setPeriodo] = useState<Periodo>('30d')

  useEffect(() => {
    const fetchPagamentos = async () => {
      try {
        setLoading(true)
        setErro(null)
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/financeiro/pagamentos`, {
          credentials: 'include',
        })

        if (!res.ok) {
          const message = await readApiError(res, 'Erro ao carregar dados financeiros')
          console.error('[admin/financeiro] GET /financeiro/pagamentos', {
            status: res.status,
            message,
          })
          setErro(message)
          toast.error(message)
          return
        }

        setPagamentos(await res.json())
      } catch (err) {
        console.error('[admin/financeiro] erro ao carregar grafico financeiro', err)
        setErro('Falha ao carregar o grafico financeiro.')
        toast.error('Falha ao carregar o grafico financeiro.')
      } finally {
        setLoading(false)
      }
    }

    fetchPagamentos()
  }, [])

  const data = buildChartData(pagamentos, periodo)
  const receitaPeriodo = data.reduce((acc, item) => acc + item.receita, 0)
  const totalTransacoes = data.reduce((acc, item) => acc + item.transacoes, 0)
  const totalCreditos = data.reduce((acc, item) => acc + item.creditos, 0)
  const picoReceita = data.reduce<FinanceiroBucket | null>(
    (melhor, item) => (!melhor || item.receita > melhor.receita ? item : melhor),
    null
  )
  const picoVendas = data.reduce<FinanceiroBucket | null>(
    (melhor, item) => (!melhor || item.transacoes > melhor.transacoes ? item : melhor),
    null
  )
  const topoReceita = calcularTopoEixo(
    data.reduce((acc, item) => Math.max(acc, item.receita), 0),
    receitaPeriodo > 0 ? 100 : 10
  )
  const topoVendas = calcularTopoEixo(
    data.reduce((acc, item) => Math.max(acc, item.transacoes), 0),
    totalTransacoes > 0 ? 3 : 1
  )
  const insightReceita =
    picoReceita && picoReceita.receita > 0
      ? `Maior receita em ${picoReceita.rotulo}: ${formatCurrency(picoReceita.receita)}`
      : null
  const insightVendas =
    picoVendas && picoVendas.transacoes > 0
      ? `${picoVendas.transacoes} venda${picoVendas.transacoes > 1 ? 's' : ''} no melhor dia`
      : null
  const rotuloPeriodo =
    periodo === '7d' ? 'ultimos 7 dias' : periodo === '30d' ? 'ultimos 30 dias' : 'mes atual'

  if (loading) {
    return (
      <div className="rounded-3xl border border-slate-100 bg-white px-6 py-10 text-center text-gray-500 shadow-sm">
        Carregando grafico financeiro...
      </div>
    )
  }

  if (erro) {
    return (
      <div className="rounded-3xl border border-red-200 bg-red-50 px-6 py-10 text-center text-red-600 shadow-sm">
        {erro}
      </div>
    )
  }

  if (data.length === 0) {
    return (
      <div className="rounded-3xl border border-slate-100 bg-white px-6 py-10 text-center text-gray-400 shadow-sm">
        Nenhum dado financeiro disponivel.
      </div>
    )
  }

  return (
    <div className="overflow-hidden rounded-[28px] border border-slate-100 bg-white shadow-sm">
      <div className="border-b border-slate-100 bg-[radial-gradient(circle_at_top_left,_rgba(252,30,173,0.16),_transparent_42%),linear-gradient(135deg,#fff_0%,#fff6fb_52%,#fff_100%)] px-5 py-5 md:px-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-[#c2185b]">
              Desempenho financeiro
            </p>
            <h3 className="mt-2 text-lg font-semibold text-slate-900">Receita diaria e ritmo de vendas</h3>
            <p className="mt-1 text-sm text-slate-500">
              Barras usam o eixo esquerdo em reais e a linha acompanha as vendas no eixo direito.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            {([
              ['7d', '7 dias'],
              ['30d', '30 dias'],
              ['mes', 'Mes atual'],
            ] as Array<[Periodo, string]>).map(([value, label]) => (
              <button
                key={value}
                type="button"
                onClick={() => setPeriodo(value)}
                className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition ${
                  periodo === value
                    ? 'border-[#FC1EAD] bg-[#FC1EAD] text-white shadow-sm'
                    : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:text-slate-900'
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-5 grid gap-3 md:grid-cols-3">
          <div className="rounded-[24px] border border-white/80 bg-white/90 px-4 py-4 shadow-sm shadow-[#FC1EAD]/5">
            <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-500">
              <BanknotesIcon className="h-4 w-4 text-[#c2185b]" />
              Receita no periodo
            </div>
            <p className="mt-2 text-2xl font-semibold text-slate-900">{formatCurrency(receitaPeriodo)}</p>
            <p className="mt-1 text-xs text-slate-500">Aprovada no recorte de {rotuloPeriodo}.</p>
          </div>

          <div className="rounded-[24px] border border-white/80 bg-white/90 px-4 py-4 shadow-sm">
            <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-500">
              <ShoppingCartIcon className="h-4 w-4 text-slate-700" />
              Vendas aprovadas
            </div>
            <p className="mt-2 text-2xl font-semibold text-slate-900">{totalTransacoes}</p>
            <p className="mt-1 text-xs text-slate-500">Quantidade de transacoes aprovadas no periodo.</p>
          </div>

          <div className="rounded-[24px] border border-white/80 bg-white/90 px-4 py-4 shadow-sm">
            <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-500">
              <ChartBarSquareIcon className="h-4 w-4 text-slate-700" />
              Creditos vendidos
            </div>
            <p className="mt-2 text-2xl font-semibold text-slate-900">{totalCreditos}</p>
            <p className="mt-1 text-xs text-slate-500">Volume entregue para os pagamentos aprovados.</p>
          </div>
        </div>

        {(insightReceita || insightVendas) && (
          <div className="mt-4 rounded-[24px] border border-[#f7d4e8] bg-white/85 px-4 py-3 shadow-sm">
            <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-[#c2185b]">Insight rapido</p>
            <div className="mt-2 flex flex-col gap-1 text-sm text-slate-600 md:flex-row md:items-center md:gap-4">
              {insightReceita ? <span>{insightReceita}</span> : null}
              {insightVendas ? <span>{insightVendas}</span> : null}
            </div>
          </div>
        )}
      </div>

      <div className="h-[390px] w-full px-2 py-4 md:px-6">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={data} margin={{ top: 16, right: 10, left: 0, bottom: 4 }} barCategoryGap="28%">
            <defs>
              <linearGradient id="financeiroBar" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#FC1EAD" stopOpacity={0.96} />
                <stop offset="55%" stopColor="#FC1EAD" stopOpacity={0.78} />
                <stop offset="100%" stopColor="#FBCFE8" stopOpacity={0.42} />
              </linearGradient>
            </defs>

            <CartesianGrid vertical={false} strokeDasharray="4 4" stroke="#e5e7eb" />
            <XAxis
              dataKey="rotulo"
              stroke="#94a3b8"
              fontSize={12}
              tickLine={false}
              axisLine={false}
              tickMargin={10}
            />
            <YAxis
              yAxisId="receita"
              stroke="#94a3b8"
              fontSize={12}
              tickLine={false}
              axisLine={false}
              width={72}
              domain={[0, topoReceita]}
              tickCount={5}
              tickFormatter={formatCurrencyAxis}
            />
            <YAxis
              yAxisId="transacoes"
              orientation="right"
              stroke="#94a3b8"
              fontSize={12}
              tickLine={false}
              axisLine={false}
              width={42}
              allowDecimals={false}
              domain={[0, topoVendas]}
              tickCount={Math.min(topoVendas + 1, 5)}
            />
            <Tooltip
              content={<CustomTooltip />}
              cursor={{ fill: 'rgba(15, 23, 42, 0.05)', radius: 12 }}
            />
            <Legend
              verticalAlign="top"
              align="right"
              iconType="circle"
              wrapperStyle={{ paddingBottom: 12, fontSize: '12px' }}
            />
            <Bar
              yAxisId="receita"
              dataKey="receita"
              name="Receita"
              fill="url(#financeiroBar)"
              radius={[12, 12, 6, 6]}
              maxBarSize={32}
              minPointSize={3}
            />
            <Line
              yAxisId="transacoes"
              type="monotone"
              dataKey="transacoes"
              name="Vendas"
              stroke="#0f172a"
              strokeWidth={3.5}
              dot={{ r: 4, fill: '#0f172a', stroke: '#ffffff', strokeWidth: 2 }}
              activeDot={{ r: 6, fill: '#0f172a', stroke: '#ffffff', strokeWidth: 2 }}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
