'use client'

import Link from 'next/link'

import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { AdminDashboardAdPerformance } from '@/lib/admin-dashboard-api'

type Props = {
  topConversao: AdminDashboardAdPerformance[]
  piorConversao: AdminDashboardAdPerformance[]
  minimumViews: number
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

function RankingTable({
  title,
  description,
  items,
  loading,
  error,
  onRetry,
  conversionClassName,
}: {
  title: string
  description: string
  items: AdminDashboardAdPerformance[]
  loading: boolean
  error: string | null
  onRetry: () => void
  conversionClassName: string
}) {
  return (
    <section className="min-w-0 border border-zinc-200 bg-white">
      <header className="border-b border-zinc-200 px-5 py-4">
        <h3 className="text-base font-bold text-zinc-950">{title}</h3>
        <p className="mt-1 text-xs text-zinc-600">{description}</p>
      </header>
      <div className="overflow-x-auto">
        <Table className="min-w-[650px]">
          <TableHeader>
            <TableRow>
              <TableHead className="w-10">#</TableHead>
              <TableHead>Anúncio</TableHead>
              <TableHead>Local</TableHead>
              <TableHead className="text-right">Views</TableHead>
              <TableHead className="text-right">Cliques</TableHead>
              <TableHead className="text-right">Conversão</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow><TableCell colSpan={6} className="py-8 text-center">Carregando...</TableCell></TableRow>
            ) : error ? (
              <TableRow>
                <TableCell colSpan={6} className="py-6 text-center">
                  <p className="text-sm text-red-700" role="alert">{error}</p>
                  <Button type="button" size="sm" variant="outline" className="mt-3" onClick={onRetry}>
                    Tentar novamente
                  </Button>
                </TableCell>
              </TableRow>
            ) : items.length === 0 ? (
              <TableRow><TableCell colSpan={6} className="py-8 text-center text-zinc-500">Sem dados elegíveis.</TableCell></TableRow>
            ) : (
              items.map((item, index) => (
                <TableRow key={item.anuncioId}>
                  <TableCell className="font-semibold">{index + 1}</TableCell>
                  <TableCell className="max-w-[220px]">
                    <Link href={`/admin/anuncios/${item.anuncioId}`} className="line-clamp-2 font-medium hover:text-pink-700">
                      {item.titulo}
                    </Link>
                  </TableCell>
                  <TableCell>{[item.cidade, item.uf].filter(Boolean).join(' - ') || '—'}</TableCell>
                  <TableCell className="text-right tabular-nums">{item.visualizacoes.toLocaleString('pt-BR')}</TableCell>
                  <TableCell className="text-right tabular-nums">{item.cliquesWhatsapp.toLocaleString('pt-BR')}</TableCell>
                  <TableCell className={`text-right font-semibold tabular-nums ${conversionClassName}`}>
                    {formatPercent(item.conversaoPct)}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
    </section>
  )
}

export function StrategicConversionRankings({
  topConversao,
  piorConversao,
  minimumViews,
  loading,
  error,
  onRetry,
}: Props) {
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <RankingTable
        title="Top conversão"
        description="Maior relação entre cliques permitidos e visualizações canônicas."
        items={topConversao}
        loading={loading}
        error={error}
        onRetry={onRetry}
        conversionClassName="text-emerald-700"
      />
      <RankingTable
        title="Pior conversão com tráfego"
        description={`Menores taxas entre anúncios com pelo menos ${minimumViews.toLocaleString('pt-BR')} views.`}
        items={piorConversao}
        loading={loading}
        error={error}
        onRetry={onRetry}
        conversionClassName="text-red-700"
      />
    </div>
  )
}
