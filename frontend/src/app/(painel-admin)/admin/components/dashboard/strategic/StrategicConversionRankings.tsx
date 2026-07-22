'use client'

import Link from 'next/link'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { AdminPerformanceItem } from '@/lib/admin-estatisticas-api'
import { ContractState } from '@/components/feedback/contract-state'

type Props = {
  topConversao: AdminPerformanceItem[]
  piorConversao: AdminPerformanceItem[]
  loading?: boolean
  error?: unknown
}

function fmt(n?: number) {
  return Number(n ?? 0).toLocaleString('pt-BR')
}

function fmtConv(n?: number) {
  return `${Number(n ?? 0).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`
}

export function StrategicConversionRankings({ topConversao, piorConversao, loading, error }: Props) {
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <div className="overflow-hidden rounded-2xl border border-gray-200/80 bg-white shadow-sm">
        <div className="border-b border-gray-100 px-5 py-4">
          <h3 className="text-base font-bold text-gray-900">Top conversão</h3>
          <p className="mt-0.5 text-xs text-gray-500">Melhor taxa cliques/WhatsApp sobre visualizações acumuladas.</p>
        </div>
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="w-10 text-xs font-semibold uppercase text-gray-500">#</TableHead>
              <TableHead className="text-xs font-semibold uppercase text-gray-500">Anúncio</TableHead>
              <TableHead className="text-xs font-semibold uppercase text-gray-500">Cidade</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Views</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Cliques</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Conv.</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={6} className="py-8 text-center text-sm text-gray-500">
                  Carregando…
                </TableCell>
              </TableRow>
            ) : error ? (
              <TableRow><TableCell colSpan={6}><ContractState error={error} compact /></TableCell></TableRow>
            ) : topConversao.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="py-8 text-center text-sm text-gray-500">
                  Sem dados.
                </TableCell>
              </TableRow>
            ) : (
              topConversao.map((item, i) => (
                <TableRow key={item.id} className="text-sm">
                  <TableCell className="font-semibold text-gray-700">{i + 1}</TableCell>
                  <TableCell className="max-w-[200px] truncate">
                    <Link href={`/admin/anuncios/${item.id}`} className="font-medium text-gray-900 hover:text-[#f0198f]">
                      {item.titulo || `Anúncio #${item.id}`}
                    </Link>
                  </TableCell>
                  <TableCell className="text-gray-600">{(item.cidadeNome ?? '—').toString()}</TableCell>
                  <TableCell className="text-right tabular-nums text-gray-800">{fmt(item.visualizacoes)}</TableCell>
                  <TableCell className="text-right tabular-nums text-gray-800">{fmt(item.cliquesWhatsapp)}</TableCell>
                  <TableCell className="text-right font-medium tabular-nums text-emerald-800">{fmtConv(item.taxaConversao)}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      <div className="overflow-hidden rounded-2xl border border-gray-200/80 bg-white shadow-sm">
        <div className="border-b border-gray-100 px-5 py-4">
          <h3 className="text-base font-bold text-gray-900">Pior conversão com tráfego</h3>
          <p className="mt-0.5 text-xs text-gray-500">Anúncios com volume mínimo de views e taxa mais baixa.</p>
        </div>
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="w-10 text-xs font-semibold uppercase text-gray-500">#</TableHead>
              <TableHead className="text-xs font-semibold uppercase text-gray-500">Anúncio</TableHead>
              <TableHead className="text-xs font-semibold uppercase text-gray-500">Cidade</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Views</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Cliques</TableHead>
              <TableHead className="text-right text-xs font-semibold uppercase text-gray-500">Conv.</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={6} className="py-8 text-center text-sm text-gray-500">
                  Carregando…
                </TableCell>
              </TableRow>
            ) : error ? (
              <TableRow><TableCell colSpan={6}><ContractState error={error} compact /></TableCell></TableRow>
            ) : piorConversao.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="py-8 text-center text-sm text-gray-500">
                  Sem anúncios com tráfego suficiente.
                </TableCell>
              </TableRow>
            ) : (
              piorConversao.map((item, i) => (
                <TableRow key={item.id} className="text-sm">
                  <TableCell className="font-semibold text-gray-700">{i + 1}</TableCell>
                  <TableCell className="max-w-[200px] truncate">
                    <Link href={`/admin/anuncios/${item.id}`} className="font-medium text-gray-900 hover:text-[#f0198f]">
                      {item.titulo || `Anúncio #${item.id}`}
                    </Link>
                  </TableCell>
                  <TableCell className="text-gray-600">{(item.cidadeNome ?? '—').toString()}</TableCell>
                  <TableCell className="text-right tabular-nums text-gray-800">{fmt(item.visualizacoes)}</TableCell>
                  <TableCell className="text-right tabular-nums text-gray-800">{fmt(item.cliquesWhatsapp)}</TableCell>
                  <TableCell className="text-right font-medium tabular-nums text-rose-800">{fmtConv(item.taxaConversao)}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
