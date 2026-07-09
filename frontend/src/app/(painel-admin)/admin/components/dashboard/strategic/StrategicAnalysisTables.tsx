'use client'

import { useRouter } from 'next/navigation'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  MOD_V2_QUERY_CLASSIFICACAO,
  QUERY_CLASSIFICACAO_SEM,
} from '@/features/moderation-v2/lib/url-dashboard-filters'
import type { CidadeAggRow, ClassificacaoAggRow } from './strategic-dashboard-utils'
import { labelClassificacaoDashboard } from './strategic-dashboard-utils'

type Props = {
  cidadeRows: CidadeAggRow[]
  classificacaoRows: ClassificacaoAggRow[]
  loading?: boolean
}

function fmt(n: number) {
  return Number(n).toLocaleString('pt-BR')
}

function fmtConv(n: number) {
  return `${Number(n).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`
}

function classificacaoToModeracaoQuery(classeRaw: string): string {
  const k = (classeRaw ?? '').trim()
  if (!k || k === '—') return QUERY_CLASSIFICACAO_SEM
  return k
}

export function StrategicAnalysisTables({ cidadeRows, classificacaoRows, loading }: Props) {
  const router = useRouter()

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <div className="overflow-hidden rounded-2xl border border-gray-200/80 bg-white shadow-sm">
        <div className="border-b border-gray-100 px-5 py-4">
          <h3 className="text-base font-bold text-gray-900">Desempenho por cidade</h3>
          <p className="mt-0.5 text-xs text-gray-500">Agregado de anúncios ativos na base (snapshot acumulado).</p>
        </div>
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="text-xs font-semibold uppercase text-gray-500">Cidade</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Ativos</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Views</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Cliques</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Conv.</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={5} className="py-8 text-center text-sm text-gray-500">
                  Carregando…
                </TableCell>
              </TableRow>
            ) : cidadeRows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="py-8 text-center text-sm text-gray-500">
                  Sem dados.
                </TableCell>
              </TableRow>
            ) : (
              cidadeRows.map((r) => (
                <TableRow key={r.cidade} className="text-sm">
                  <TableCell className="font-medium text-gray-900">{r.cidade}</TableCell>
                  <TableCell className="text-right tabular-nums">{r.ativos}</TableCell>
                  <TableCell className="text-right tabular-nums text-gray-800">{fmt(r.views)}</TableCell>
                  <TableCell className="text-right tabular-nums text-gray-800">{fmt(r.cliques)}</TableCell>
                  <TableCell className="text-right tabular-nums font-medium text-gray-800">{fmtConv(r.conversao)}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      <div className="overflow-hidden rounded-2xl border border-gray-200/80 bg-white shadow-sm">
        <div className="border-b border-gray-100 px-5 py-4">
          <h3 className="text-base font-bold text-gray-900">Desempenho por classificação</h3>
          <p className="mt-0.5 text-xs text-gray-500">Distribuição por classificação de conteúdo do anúncio.</p>
        </div>
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="text-xs font-semibold uppercase text-gray-500">Classificação</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Ativos</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Views</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Cliques</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Conv.</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={5} className="py-8 text-center text-sm text-gray-500">
                  Carregando…
                </TableCell>
              </TableRow>
            ) : classificacaoRows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="py-8 text-center text-sm text-gray-500">
                  Sem dados.
                </TableCell>
              </TableRow>
            ) : (
              classificacaoRows.map((r) => {
                const q = classificacaoToModeracaoQuery(r.classe)
                const href = `/admin/moderacao-v2?${MOD_V2_QUERY_CLASSIFICACAO}=${encodeURIComponent(q)}`
                return (
                  <TableRow
                    key={r.classe}
                    className="cursor-pointer text-sm hover:bg-pink-50/50"
                    role="link"
                    tabIndex={0}
                    title="Abrir na Moderação v2 com este filtro"
                    onClick={() => router.push(href)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        router.push(href)
                      }
                    }}
                  >
                    <TableCell className="max-w-[220px] font-medium text-gray-900" title={r.classe}>
                      {labelClassificacaoDashboard(r.classe)}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{r.ativos}</TableCell>
                    <TableCell className="text-right tabular-nums text-gray-800">{fmt(r.views)}</TableCell>
                    <TableCell className="text-right tabular-nums text-gray-800">{fmt(r.cliques)}</TableCell>
                    <TableCell className="text-right tabular-nums font-medium text-gray-800">{fmtConv(r.conversao)}</TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
