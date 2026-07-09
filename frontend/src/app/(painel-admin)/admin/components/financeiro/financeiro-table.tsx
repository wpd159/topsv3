'use client'

import { useEffect, useState } from 'react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from '@/components/ui/pagination'
import { toast } from 'sonner'
import { normalizarStatusPagamento } from '@/utils/normalizer'

interface Transacao {
  id: number
  nomeCliente: string
  provider: string
  txid?: string | null
  metodoPagamento: string
  valor: number
  creditos: number
  status: string
  dataPagamento: string
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

export default function FinanceiroTabela() {
  const [transacoes, setTransacoes] = useState<Transacao[]>([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [paginaAtual, setPaginaAtual] = useState(1)
  const itensPorPagina = 6

  useEffect(() => {
    const fetchTransacoes = async () => {
      try {
        setLoading(true)
        setErro(null)
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/financeiro/pagamentos`, {
          credentials: 'include',
        })

        if (!res.ok) {
          const message = await readApiError(res, 'Erro ao carregar pagamentos')
          console.error('[admin/financeiro] GET /financeiro/pagamentos', {
            status: res.status,
            message,
          })
          setErro(message)
          toast.error(message)
          return
        }

        const data = await res.json()
        const ordenado = Array.isArray(data)
          ? data.sort((a, b) => new Date(b.dataPagamento).getTime() - new Date(a.dataPagamento).getTime())
          : []
        setTransacoes(ordenado)
      } catch (error) {
        console.error('[admin/financeiro] erro ao carregar pagamentos', error)
        setErro('Falha ao conectar ao servidor financeiro.')
        toast.error('Falha ao conectar ao servidor financeiro.')
      } finally {
        setLoading(false)
      }
    }

    fetchTransacoes()
  }, [])

  const totalPaginas = Math.ceil(transacoes.length / itensPorPagina)
  const transacoesPagina = transacoes.slice(
    (paginaAtual - 1) * itensPorPagina,
    paginaAtual * itensPorPagina
  )

  const getBadgeColor = (status: string) => {
    const s = status.toLowerCase()
    if (s.includes('aprov')) return 'bg-green-100 text-green-700 border-green-300'
    if (s.includes('pend')) return 'bg-yellow-100 text-yellow-700 border-yellow-300'
    if (s.includes('rej') || s.includes('cancel') || s.includes('expir')) return 'bg-red-100 text-red-700 border-red-300'
    return 'bg-gray-100 text-gray-600 border-gray-300'
  }

  const formatarData = (dataIso: string) => {
    if (!dataIso) return '-'
    const data = new Date(dataIso)
    return data.toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    })
  }

  const formatarValor = (valor: number) =>
    valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

  if (loading) {
    return <div className="text-center py-10 text-gray-500">Carregando transações...</div>
  }

  if (erro) {
    return <div className="text-center py-10 text-red-600">{erro}</div>
  }

  if (transacoes.length === 0) {
    return <div className="text-center py-10 text-gray-400">Nenhum pagamento encontrado.</div>
  }

  return (
    <div className="bg-white border border-gray-100 rounded-xl shadow-sm mt-10 overflow-hidden">
      <div className="px-5 py-4 border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent">
        <h3 className="text-base font-semibold text-gray-800">Pagamentos</h3>
        <p className="text-xs text-gray-500 mt-1">
          Histórico de pagamentos registrados no sistema, incluindo PIX Efí e histórico legado.
        </p>
      </div>

      <div className="hidden md:block overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow className="bg-gray-50/60 border-b text-gray-500 uppercase text-[11px] tracking-wider">
              <TableHead className="py-3 px-6 font-semibold">ID</TableHead>
              <TableHead className="py-3 px-6 font-semibold">Cliente</TableHead>
              <TableHead className="py-3 px-6 font-semibold">Provedor</TableHead>
              <TableHead className="py-3 px-6 font-semibold">Método</TableHead>
              <TableHead className="py-3 px-6 font-semibold">Créditos</TableHead>
              <TableHead className="py-3 px-6 font-semibold">TXID</TableHead>
              <TableHead className="py-3 px-6 font-semibold text-center">Status</TableHead>
              <TableHead className="py-3 px-6 font-semibold text-right">Valor</TableHead>
              <TableHead className="py-3 px-6 font-semibold text-right">Data</TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {transacoesPagina.map((t, i) => {
              const statusNormalizado = normalizarStatusPagamento(t.status)
              return (
                <TableRow
                  key={t.id}
                  className={`${i % 2 === 0 ? 'bg-white' : 'bg-gray-50/40'} hover:bg-[#FC1EAD]/5`}
                >
                  <TableCell className="py-4 px-6 font-medium text-gray-800">#{t.id}</TableCell>
                  <TableCell className="px-6 text-gray-700">{t.nomeCliente || '-'}</TableCell>
                  <TableCell className="px-6 text-gray-600">{t.provider || '-'}</TableCell>
                  <TableCell className="px-6 text-gray-600 capitalize">{t.metodoPagamento || '-'}</TableCell>
                  <TableCell className="px-6 text-gray-700">{t.creditos ?? '-'}</TableCell>
                  <TableCell className="px-6 text-gray-500 max-w-[160px] truncate">{t.txid || '-'}</TableCell>
                  <TableCell className="px-6 text-center">
                    <Badge
                      variant="outline"
                      className={`inline-flex items-center justify-center text-[11px] font-medium border px-2 py-1 rounded-md ${getBadgeColor(statusNormalizado)}`}
                    >
                      {statusNormalizado}
                    </Badge>
                  </TableCell>
                  <TableCell className="px-6 text-right font-semibold text-gray-800">
                    {formatarValor(t.valor)}
                  </TableCell>
                  <TableCell className="px-6 text-right text-gray-500">
                    {formatarData(t.dataPagamento)}
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

      <div className="md:hidden divide-y divide-gray-100">
        {transacoesPagina.map((t) => {
          const statusNormalizado = normalizarStatusPagamento(t.status)
          return (
            <div key={t.id} className="p-4 flex flex-col gap-2 hover:bg-gray-50 transition">
              <div className="flex justify-between items-center">
                <p className="text-sm font-semibold text-gray-800">#{t.id}</p>
                <Badge
                  variant="outline"
                  className={`text-[11px] px-2 py-1 rounded-md border ${getBadgeColor(statusNormalizado)}`}
                >
                  {statusNormalizado}
                </Badge>
              </div>
              <p className="text-sm text-gray-700">{t.nomeCliente}</p>
              <p className="text-xs text-gray-500">{t.provider} · {t.metodoPagamento}</p>
              <p className="text-xs text-gray-500">Créditos: {t.creditos ?? '-'}</p>
              <div className="flex justify-between items-center mt-1">
                <p className="text-sm font-bold text-gray-800">{formatarValor(t.valor)}</p>
                <p className="text-xs text-gray-500">{formatarData(t.dataPagamento)}</p>
              </div>
            </div>
          )
        })}
      </div>

      {totalPaginas > 1 && (
        <div className="border-t bg-gray-50/70 px-4 py-3 flex justify-center">
          <Pagination>
            <PaginationContent>
              <PaginationItem>
                <PaginationPrevious
                  onClick={() => setPaginaAtual((p) => Math.max(p - 1, 1))}
                  className={paginaAtual === 1 ? 'opacity-40 pointer-events-none' : ''}
                />
              </PaginationItem>

              {Array.from({ length: totalPaginas }, (_, i) => (
                <PaginationItem key={i}>
                  <PaginationLink onClick={() => setPaginaAtual(i + 1)} isActive={paginaAtual === i + 1}>
                    {i + 1}
                  </PaginationLink>
                </PaginationItem>
              ))}

              <PaginationItem>
                <PaginationNext
                  onClick={() => setPaginaAtual((p) => Math.min(p + 1, totalPaginas))}
                  className={paginaAtual === totalPaginas ? 'opacity-40 pointer-events-none' : ''}
                />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
        </div>
      )}
    </div>
  )
}
