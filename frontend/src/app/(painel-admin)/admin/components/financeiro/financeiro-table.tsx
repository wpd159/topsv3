'use client'

import Link from 'next/link'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { AdminRelatorioReceitaPagina } from '@/lib/admin-pagamentos-api'
import { buildWhatsAppUrl, formatarTelefoneExibicao } from '../admin-usuarios-utils'

function shortId(value: string) {
  return value ? `${value.slice(0, 8)}...` : '—'
}

type Props = {
  pagina: AdminRelatorioReceitaPagina | null
  loading: boolean
  error: unknown
  onRetry: () => void
  onPageChange: (page: number) => void
}

const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export default function FinanceiroTabela({ pagina, loading, error, onRetry, onPageChange }: Props) {
  const totalPaginas = Math.max(1, Math.ceil((pagina?.total ?? 0) / (pagina?.tamanho ?? 20)))
  return (
    <section className="overflow-hidden border border-zinc-200 bg-white">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-zinc-200 px-5 py-4">
        <div>
          <h2 className="text-base font-semibold text-zinc-950">Transações</h2>
          <p className="mt-1 text-xs text-zinc-500">Valores confirmados reconciliam com os cards para o mesmo filtro.</p>
        </div>
        {pagina ? (
          <div className="text-right text-xs text-zinc-600">
            <strong className="block text-sm text-zinc-950">{moeda.format(pagina.receitaConfirmadaFiltrada)}</strong>
            {pagina.pagamentosConfirmadosFiltrados.toLocaleString('pt-BR')} pagamentos confirmados
          </div>
        ) : null}
      </div>

      {error ? (
        <div className="p-5">
          <ContractState error={error} onRetry={onRetry} />
        </div>
      ) : (
        <div className="overflow-x-auto" role="region" aria-label="Tabela de transacoes financeiras" tabIndex={0}>
          <Table className="min-w-[78rem]">
            <TableHeader className="sticky top-0 z-10 bg-white">
              <TableRow>
                <TableHead>ID</TableHead>
                <TableHead>Usuário</TableHead>
                <TableHead>Tipo / produto</TableHead>
                <TableHead>Método</TableHead>
                <TableHead>Créditos</TableHead>
                <TableHead>Referência</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Valor</TableHead>
                <TableHead>Data</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading && !pagina ? (
                <TableRow><TableCell colSpan={9} className="py-8 text-center text-zinc-500">Carregando pagamentos...</TableCell></TableRow>
              ) : !pagina?.itens.length ? (
                <TableRow><TableCell colSpan={9} className="py-8 text-center text-zinc-500">Nenhuma transação encontrada para os filtros.</TableCell></TableRow>
              ) : pagina.itens.map((item) => {
                const whatsappUrl = buildWhatsAppUrl(item.usuarioWhatsapp)
                return <TableRow key={item.id}>
                  <TableCell title={item.id}>{shortId(item.id)}</TableCell>
                  <TableCell className="min-w-64">
                    <Link
                      href={`/admin/usuarios/${encodeURIComponent(item.usuarioId)}`}
                      className="block font-medium text-zinc-950 underline-offset-2 hover:text-[#C51683] hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C51683]"
                    >
                      {item.usuarioNome}
                    </Link>
                    <span className="block break-all text-xs text-zinc-500">
                      {item.usuarioEmail ?? 'E-mail não informado'}
                    </span>
                    {whatsappUrl ? (
                      <a
                        href={whatsappUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="mt-1 block w-fit text-xs font-medium text-emerald-700 underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-600"
                      >
                        WhatsApp: {formatarTelefoneExibicao(item.usuarioWhatsapp)}
                      </a>
                    ) : (
                      <span className="mt-1 block text-xs text-zinc-400">WhatsApp não informado</span>
                    )}
                  </TableCell>
                  <TableCell><span className="block">{item.tipo === 'REGISTRO_LEGADO' ? 'Registro legado' : 'Compra de créditos'}</span><span className="text-xs text-zinc-500">{item.produto}</span></TableCell>
                  <TableCell>{item.metodo}</TableCell>
                  <TableCell>{item.creditos?.toLocaleString('pt-BR') ?? '—'}</TableCell>
                  <TableCell>{item.identificadorExternoMascarado ?? '—'}</TableCell>
                  <TableCell><Badge variant="outline">{item.status}</Badge></TableCell>
                  <TableCell className={item.receitaConfirmada ? 'font-semibold text-emerald-700' : 'text-zinc-600'}>{moeda.format(item.valor)}</TableCell>
                  <TableCell>{item.data ? new Date(item.data).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' }) : '—'}</TableCell>
                </TableRow>
              })}
            </TableBody>
          </Table>
        </div>
      )}

      <div className="flex items-center justify-between border-t bg-zinc-50 px-5 py-3 text-sm text-zinc-600">
        <span>{error ? 'Contagem indisponível' : `${(pagina?.total ?? 0).toLocaleString('pt-BR')} registros`}</span>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" disabled={loading || !pagina || pagina.pagina === 0 || Boolean(error)} onClick={() => onPageChange((pagina?.pagina ?? 0) - 1)}>Anterior</Button>
          <span className="self-center">{(pagina?.pagina ?? 0) + 1} / {totalPaginas}</span>
          <Button variant="outline" size="sm" disabled={loading || !pagina || pagina.pagina + 1 >= totalPaginas || Boolean(error)} onClick={() => onPageChange((pagina?.pagina ?? 0) + 1)}>Próxima</Button>
        </div>
      </div>
    </section>
  )
}
