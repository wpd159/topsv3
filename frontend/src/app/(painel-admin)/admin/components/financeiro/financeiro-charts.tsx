'use client'

import { useMemo } from 'react'
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

import { Button } from '@/components/ui/button'
import type { AdminRelatorioReceitaResumo } from '@/lib/admin-pagamentos-api'

type Props = {
  resumo: AdminRelatorioReceitaResumo | null
  loading: boolean
  error: unknown
  onRetry: () => void
}

const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export default function FinanceiroGrafico({ resumo, loading, error, onRetry }: Props) {
  const serie = useMemo(() => (resumo?.evolucaoDiaria ?? []).map((item) => ({
    ...item,
    label: item.data.split('-').reverse().slice(0, 2).join('/'),
  })), [resumo])

  return (
    <section className="grid gap-6 border border-zinc-200 bg-white p-5 xl:grid-cols-[minmax(0,2fr)_minmax(18rem,1fr)]">
      <div className="min-w-0">
        <h2 className="font-semibold text-zinc-950">Evolução diária</h2>
        <p className="mt-1 text-xs text-zinc-500">
          Receita confirmada e quantidade de pagamentos em America/Sao_Paulo.
        </p>
        <div className="mt-4 h-72">
          {loading ? (
            <div className="flex h-full items-center justify-center border border-dashed border-zinc-200 text-sm text-zinc-500">Carregando série...</div>
          ) : error ? (
            <div className="flex h-full flex-col items-center justify-center gap-3 border border-red-200 bg-red-50 p-4">
              <p className="text-sm text-red-800">Não foi possível carregar a evolução diária.</p>
              <Button type="button" size="sm" variant="outline" onClick={onRetry}>Tentar novamente</Button>
            </div>
          ) : serie.length === 0 ? (
            <div className="flex h-full items-center justify-center border border-dashed border-zinc-200 text-sm text-zinc-500">Nenhum pagamento confirmado no período.</div>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={serie} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e4e4e7" vertical={false} />
                <XAxis dataKey="label" tick={{ fontSize: 11, fill: '#71717a' }} />
                <YAxis
                  yAxisId="receita"
                  tick={{ fontSize: 11, fill: '#be185d' }}
                  tickFormatter={(value) => `R$ ${Number(value).toLocaleString('pt-BR', { notation: 'compact' })}`}
                />
                <YAxis yAxisId="pagamentos" orientation="right" allowDecimals={false} tick={{ fontSize: 11, fill: '#0369a1' }} />
                <Tooltip formatter={(value, name) => name === 'Receita' ? moeda.format(Number(value)) : Number(value).toLocaleString('pt-BR')} />
                <Bar yAxisId="receita" dataKey="receitaConfirmada" name="Receita" fill="#f9a8d4" radius={[3, 3, 0, 0]} maxBarSize={30} />
                <Line yAxisId="pagamentos" dataKey="pagamentosConfirmados" name="Pagamentos" stroke="#0284c7" strokeWidth={2} dot={false} />
              </ComposedChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>
      <div>
        <h2 className="font-semibold text-zinc-950">Receita por pacote</h2>
        <p className="mt-1 text-xs text-zinc-500">Somente compras de créditos com pagamento confirmado.</p>
        <div className="mt-4 divide-y divide-zinc-200 border-y border-zinc-200">
          {resumo?.distribuicaoPorProduto.length ? resumo.distribuicaoPorProduto.map((item) => (
            <div key={item.codigo} className="grid grid-cols-[minmax(0,1fr)_auto] gap-3 py-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-zinc-950">{item.nome}</p>
                <p className="text-xs text-zinc-500">{item.pagamentosConfirmados.toLocaleString('pt-BR')} pagamentos · {item.creditosVendidos.toLocaleString('pt-BR')} créditos</p>
              </div>
              <strong className="text-sm text-zinc-950">{moeda.format(item.receitaConfirmada)}</strong>
            </div>
          )) : (
            <p className="py-8 text-center text-sm text-zinc-500">{loading ? 'Carregando pacotes...' : 'Nenhuma venda confirmada.'}</p>
          )}
        </div>
        {resumo ? (
          <p className="mt-3 text-xs text-zinc-500">Período: {formatarData(resumo.dataInicio)} a {formatarData(resumo.dataFim)}</p>
        ) : null}
      </div>
    </section>
  )
}

function formatarData(value: string) {
  return value.split('-').reverse().join('/')
}
