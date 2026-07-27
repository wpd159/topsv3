"use client"

import { useCallback, useEffect, useState } from "react"
import { RefreshCw } from "lucide-react"
import { ContractState } from "@/components/feedback/contract-state"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import {
  fetchAdminVisitorRisk,
  type AdminVisitorRisk,
} from "@/lib/admin-age-verification-api"

export function VisitorRisk() {
  const [items, setItems] = useState<AdminVisitorRisk[]>([])
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setItems(await fetchAdminVisitorRisk())
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
        <CardTitle>Risco por sessão</CardTitle>
        <Button type="button" size="sm" variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className="mr-2 h-4 w-4" />
          Atualizar
        </Button>
      </CardHeader>
      <CardContent>
        {error ? <ContractState error={error} /> : null}
        <div className="overflow-x-auto rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Sessão</TableHead>
                <TableHead>Score</TableHead>
                <TableHead>Decisão</TableHead>
                <TableHead>Falhas</TableHead>
                <TableHead>Restritos</TableHead>
                <TableHead>Explícitos</TableHead>
                <TableHead>Último motivo</TableHead>
                <TableHead>Atualização UTC</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {!loading && items.length === 0 ? (
                <TableRow><TableCell colSpan={8}>Nenhum perfil de risco registrado.</TableCell></TableRow>
              ) : null}
              {items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>{item.referenciaSessao}</TableCell>
                  <TableCell>{item.score}</TableCell>
                  <TableCell>{item.decisao}</TableCell>
                  <TableCell>{item.falhasConsecutivas}</TableCell>
                  <TableCell>{item.acessosRestritos}</TableCell>
                  <TableCell>{item.acessosExplicitos}</TableCell>
                  <TableCell>{item.motivoSanitizado || "-"}</TableCell>
                  <TableCell>{new Date(item.atualizadoEm).toLocaleString("pt-BR", { timeZone: "UTC" })}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  )
}
