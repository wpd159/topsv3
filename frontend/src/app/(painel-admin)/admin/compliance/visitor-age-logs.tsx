'use client'

import { useCallback, useEffect, useState } from 'react'
import { RefreshCw } from 'lucide-react'
import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  fetchAdminAgeVerifications,
  type AdminAgeVerification,
} from '@/lib/admin-age-verification-api'

export function VisitorAgeLogs() {
  const [items, setItems] = useState<AdminAgeVerification[]>([])
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setItems(await fetchAdminAgeVerifications())
    } catch (nextError) {
      setError(nextError)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Verificações etárias de visitantes</CardTitle>
        <Button type="button" variant="outline" size="sm" onClick={() => void load()} disabled={loading}>
          <RefreshCw className="mr-2 h-4 w-4" />
          Atualizar
        </Button>
      </CardHeader>
      <CardContent>
        {error ? <ContractState error={error} /> : null}
        <div className="overflow-x-auto rounded-lg border border-gray-200">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Resultado</TableHead>
                <TableHead>Método</TableHead>
                <TableHead>Request ID</TableHead>
                <TableHead>Data UTC</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {!loading && items.length === 0 ? (
                <TableRow><TableCell colSpan={4}>Nenhuma verificação registrada.</TableCell></TableRow>
              ) : null}
              {items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>{item.resultado}</TableCell>
                  <TableCell>{item.metodo}</TableCell>
                  <TableCell className="font-mono text-xs">{item.requestId || '-'}</TableCell>
                  <TableCell>{new Date(item.criadoEm).toLocaleString('pt-BR', { timeZone: 'UTC' })}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  )
}
