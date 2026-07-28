'use client'

import Link from 'next/link'

import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type {
  AdminDashboardCityPerformance,
  AdminDashboardClassificationPerformance,
} from '@/lib/admin-dashboard-api'

type Props = {
  cidadeRows: AdminDashboardCityPerformance[]
  classificacaoRows: AdminDashboardClassificationPerformance[]
  loading: boolean
  error: string | null
  onRetry: () => void
}

function formatPercent(value: number) {
  return `${value.toLocaleString('pt-BR', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 2,
  })}%`
}

function EmptyOrError({
  error,
  onRetry,
  colSpan,
}: {
  error: string | null
  onRetry: () => void
  colSpan: number
}) {
  return (
    <TableRow>
      <TableCell colSpan={colSpan} className="py-8 text-center">
        {error ? (
          <>
            <p className="text-sm text-red-700" role="alert">{error}</p>
            <Button type="button" size="sm" variant="outline" className="mt-3" onClick={onRetry}>
              Tentar novamente
            </Button>
          </>
        ) : (
          <span className="text-sm text-zinc-500">Sem dados elegíveis.</span>
        )}
      </TableCell>
    </TableRow>
  )
}

export function StrategicAnalysisTables({
  cidadeRows,
  classificacaoRows,
  loading,
  error,
  onRetry,
}: Props) {
  return (
    <div className="grid gap-6 xl:grid-cols-[2fr_1fr]">
      <section className="min-w-0 border border-zinc-200 bg-white">
        <header className="border-b border-zinc-200 px-5 py-4">
          <h3 className="text-base font-bold text-zinc-950">Desempenho por cidade</h3>
          <p className="mt-1 text-xs text-zinc-600">Até 12 cidades por volume de visualizações.</p>
        </header>
        <div className="overflow-x-auto">
          <Table className="min-w-[620px]">
            <TableHeader>
              <TableRow>
                <TableHead>Cidade</TableHead>
                <TableHead className="text-right">Ativos</TableHead>
                <TableHead className="text-right">Views</TableHead>
                <TableHead className="text-right">Cliques</TableHead>
                <TableHead className="text-right">Conversão</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={5} className="py-8 text-center">Carregando...</TableCell></TableRow>
              ) : error || cidadeRows.length === 0 ? (
                <EmptyOrError error={error} onRetry={onRetry} colSpan={5} />
              ) : (
                cidadeRows.map((row) => (
                  <TableRow key={`${row.uf}:${row.cidadeSlug ?? row.cidade}`}>
                    <TableCell>
                      <Link
                        href={`/admin/anuncios?uf=${encodeURIComponent(row.uf)}&cidade=${encodeURIComponent(row.cidadeSlug ?? row.cidade)}`}
                        className="font-medium hover:text-pink-700"
                      >
                        {row.cidade} - {row.uf}
                      </Link>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{row.anunciosPublicadosAtivos}</TableCell>
                    <TableCell className="text-right tabular-nums">{row.visualizacoes.toLocaleString('pt-BR')}</TableCell>
                    <TableCell className="text-right tabular-nums">{row.cliquesWhatsapp.toLocaleString('pt-BR')}</TableCell>
                    <TableCell className="text-right font-semibold tabular-nums">{formatPercent(row.conversaoPct)}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      </section>

      <section className="min-w-0 border border-zinc-200 bg-white">
        <header className="border-b border-zinc-200 px-5 py-4">
          <h3 className="text-base font-bold text-zinc-950">Por classificação</h3>
          <p className="mt-1 text-xs text-zinc-600">Cada anúncio contado uma única vez.</p>
        </header>
        <div className="overflow-x-auto">
          <Table className="min-w-[520px]">
            <TableHeader>
              <TableRow>
                <TableHead>Classificação</TableHead>
                <TableHead className="text-right">Ativos</TableHead>
                <TableHead className="text-right">Views</TableHead>
                <TableHead className="text-right">Cliques</TableHead>
                <TableHead className="text-right">Conversão</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={5} className="py-8 text-center">Carregando...</TableCell></TableRow>
              ) : error || classificacaoRows.length === 0 ? (
                <EmptyOrError error={error} onRetry={onRetry} colSpan={5} />
              ) : (
                classificacaoRows.map((row) => (
                  <TableRow key={row.classificacao}>
                    <TableCell className="font-medium">
                      {row.classificacao === 'RESTRITA_18' ? 'Restrita 18+' : 'Livre'}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{row.anunciosPublicadosAtivos}</TableCell>
                    <TableCell className="text-right tabular-nums">{row.visualizacoes.toLocaleString('pt-BR')}</TableCell>
                    <TableCell className="text-right tabular-nums">{row.cliquesWhatsapp.toLocaleString('pt-BR')}</TableCell>
                    <TableCell className="text-right font-semibold tabular-nums">{formatPercent(row.conversaoPct)}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      </section>
    </div>
  )
}
