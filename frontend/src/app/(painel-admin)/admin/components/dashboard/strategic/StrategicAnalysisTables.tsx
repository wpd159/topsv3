'use client'

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { CidadeAggRow } from './strategic-dashboard-utils'
import { ContractState } from '@/components/feedback/contract-state'

type Props = {
  cidadeRows: CidadeAggRow[]
  loading?: boolean
  error?: unknown
}

function fmt(value: number) {
  return Number(value).toLocaleString('pt-BR')
}

function fmtConv(value: number) {
  return `${Number(value).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`
}

export function StrategicAnalysisTables({ cidadeRows, loading, error }: Props) {
  return (
    <div className="overflow-hidden rounded-2xl border border-gray-200/80 bg-white shadow-sm">
      <div className="border-b border-gray-100 px-5 py-4">
        <h3 className="text-base font-bold text-gray-900">Desempenho por cidade</h3>
        <p className="mt-0.5 text-xs text-gray-500">Agregado de anúncios ativos na base.</p>
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
            <TableRow><TableCell colSpan={5} className="py-8 text-center text-sm text-gray-500">Carregando...</TableCell></TableRow>
          ) : error ? (
            <TableRow><TableCell colSpan={5}><ContractState error={error} compact /></TableCell></TableRow>
          ) : cidadeRows.length === 0 ? (
            <TableRow><TableCell colSpan={5} className="py-8 text-center text-sm text-gray-500">Sem dados.</TableCell></TableRow>
          ) : cidadeRows.map((row) => (
            <TableRow key={row.cidade} className="text-sm">
              <TableCell className="font-medium text-gray-900">{row.cidade}</TableCell>
              <TableCell className="text-right tabular-nums">{row.ativos}</TableCell>
              <TableCell className="text-right tabular-nums text-gray-800">{fmt(row.views)}</TableCell>
              <TableCell className="text-right tabular-nums text-gray-800">{fmt(row.cliques)}</TableCell>
              <TableCell className="text-right tabular-nums font-medium text-gray-800">{fmtConv(row.conversao)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
