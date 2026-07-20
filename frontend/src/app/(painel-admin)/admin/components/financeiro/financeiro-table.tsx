'use client'

import { useEffect, useState } from 'react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { listarAdminPagamentos, type AdminPagamentoItem } from '@/lib/admin-pagamentos-api'

const PAGE_SIZE = 20

function shortId(value: string) {
  return value ? `${value.slice(0, 8)}...` : '-'
}

export default function FinanceiroTabela() {
  const [transacoes, setTransacoes] = useState<AdminPagamentoItem[]>([])
  const [total, setTotal] = useState(0)
  const [pagina, setPagina] = useState(0)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<unknown>(null)
  const [reloadMarker, setReloadMarker] = useState(0)

  useEffect(() => {
    let active = true
    setLoading(true)
    setErro(null)

    listarAdminPagamentos(pagina, PAGE_SIZE)
      .then((response) => {
        if (!active) return
        setTransacoes(response.itens)
        setTotal(response.total)
      })
      .catch((error) => {
        if (active) setErro(error)
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [pagina, reloadMarker])

  const totalPaginas = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <section className="mt-10 overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm">
      <div className="border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent px-5 py-4">
        <h3 className="text-base font-semibold text-gray-800">Pagamentos</h3>
        <p className="mt-1 text-xs text-gray-500">Historico sanitizado do contrato administrativo V3.</p>
      </div>

      {erro ? (
        <div className="p-5">
          <ContractState error={erro} onRetry={() => setReloadMarker((value) => value + 1)} />
        </div>
      ) : (
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>ID</TableHead>
                <TableHead>Cliente / Usuario</TableHead>
                <TableHead>Provedor</TableHead>
                <TableHead>Metodo</TableHead>
                <TableHead>Creditos</TableHead>
                <TableHead>TXID</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Valor</TableHead>
                <TableHead>Data</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading && transacoes.length === 0 ? (
                <TableRow><TableCell colSpan={9} className="py-8 text-center text-gray-500">Carregando pagamentos...</TableCell></TableRow>
              ) : transacoes.length === 0 ? (
                <TableRow><TableCell colSpan={9} className="py-8 text-center text-gray-500">Nenhum pagamento registrado.</TableCell></TableRow>
              ) : transacoes.map((item) => (
                <TableRow key={item.id}>
                  <TableCell title={item.id}>{shortId(item.id)}</TableCell>
                  <TableCell title={item.usuarioId}>{shortId(item.usuarioId)}</TableCell>
                  <TableCell>{item.provedorClassificado || item.provedorDeclarado || '-'}</TableCell>
                  <TableCell>{item.metodo || '-'}</TableCell>
                  <TableCell>{item.quantidadeCreditos ?? '-'}</TableCell>
                  <TableCell>{item.evidenciaTransacaoMascarada || '-'}</TableCell>
                  <TableCell><Badge variant="outline">{item.statusOperacional || item.statusInterno}</Badge></TableCell>
                  <TableCell><span title="Campo ausente no contrato administrativo V3">Contrato pendente</span></TableCell>
                  <TableCell>{item.criadoEm ? new Date(item.criadoEm).toLocaleDateString('pt-BR') : '-'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <div className="flex items-center justify-between border-t bg-gray-50 px-5 py-3 text-sm text-gray-600">
        <span>{erro ? 'Contagem indisponivel' : `${total.toLocaleString('pt-BR')} registros`}</span>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" disabled={loading || pagina === 0 || Boolean(erro)} onClick={() => setPagina((value) => value - 1)}>Voltar</Button>
          <span className="self-center">{pagina + 1} / {totalPaginas}</span>
          <Button variant="outline" size="sm" disabled={loading || pagina + 1 >= totalPaginas || Boolean(erro)} onClick={() => setPagina((value) => value + 1)}>Proximo</Button>
        </div>
      </div>
    </section>
  )
}
