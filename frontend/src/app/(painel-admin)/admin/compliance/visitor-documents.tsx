"use client"

import { useCallback, useEffect, useState } from "react"
import { RefreshCw } from "lucide-react"
import { toast } from "sonner"
import { ContractState } from "@/components/feedback/contract-state"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Textarea } from "@/components/ui/textarea"
import {
  decideAdminVisitorDocument,
  fetchAdminVisitorDocuments,
  openAdminVisitorDocument,
  type AdminVisitorDocument,
} from "@/lib/admin-age-verification-api"

type Decision = "APPROVE" | "REJECT"

export function VisitorDocuments() {
  const [items, setItems] = useState<AdminVisitorDocument[]>([])
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<AdminVisitorDocument | null>(null)
  const [decision, setDecision] = useState<Decision | null>(null)
  const [reason, setReason] = useState("")
  const [submitting, setSubmitting] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setItems(await fetchAdminVisitorDocuments())
    } catch (nextError) {
      setError(nextError)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function preview(item: AdminVisitorDocument) {
    try {
      await openAdminVisitorDocument(item.id)
    } catch (nextError) {
      setError(nextError)
    }
  }

  async function confirmDecision() {
    if (!selected || !decision) return
    if (decision === "REJECT" && reason.trim().length < 3) {
      toast.error("Informe o motivo da rejeição.")
      return
    }
    setSubmitting(true)
    try {
      await decideAdminVisitorDocument(selected.id, decision, reason)
      toast.success(decision === "APPROVE" ? "Documento aprovado." : "Documento rejeitado.")
      setDecision(null)
      setSelected(null)
      setReason("")
      await load()
    } catch (nextError) {
      setError(nextError)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>Documentos de visitantes</CardTitle>
          <Button type="button" variant="outline" size="sm" onClick={() => void load()} disabled={loading}>
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
                  <TableHead>Documento</TableHead>
                  <TableHead>Challenge</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Arquivo</TableHead>
                  <TableHead>Data UTC</TableHead>
                  <TableHead>Motivo da decisão</TableHead>
                  <TableHead>Histórico da decisão</TableHead>
                  <TableHead>Ações</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {!loading && items.length === 0 ? (
                  <TableRow><TableCell colSpan={8}>Nenhum documento recebido.</TableCell></TableRow>
                ) : null}
                {items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-mono text-xs">{item.id.slice(0, 8)}</TableCell>
                    <TableCell className="font-mono text-xs">{item.challengeId.slice(0, 8)}</TableCell>
                    <TableCell>{item.status}</TableCell>
                    <TableCell>{item.mimeType} · {formatBytes(item.tamanhoBytes)}</TableCell>
                    <TableCell>{new Date(item.criadoEm).toLocaleString("pt-BR", { timeZone: "UTC" })}</TableCell>
                    <TableCell>{item.motivoPublico || "—"}</TableCell>
                    <TableCell>
                      {item.revisadoEm
                        ? `Decidido em ${new Date(item.revisadoEm).toLocaleString("pt-BR", { timeZone: "UTC" })}`
                        : "Aguardando decisão"}
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-2">
                        <Button type="button" size="sm" variant="outline" onClick={() => void preview(item)}>
                          Visualizar documento
                        </Button>
                        {item.status === "PENDING" ? (
                          <>
                            <Button type="button" size="sm" onClick={() => {
                              setSelected(item)
                              setDecision("APPROVE")
                            }}>
                              Aprovar
                            </Button>
                            <Button type="button" size="sm" variant="destructive" onClick={() => {
                              setSelected(item)
                              setDecision("REJECT")
                            }}>
                              Rejeitar
                            </Button>
                          </>
                        ) : null}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>

      <Dialog open={decision !== null} onOpenChange={(next) => {
        if (!next) {
          setDecision(null)
          setSelected(null)
          setReason("")
        }
      }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{decision === "APPROVE" ? "Aprovar documento" : "Rejeitar documento"}</DialogTitle>
            <DialogDescription>
              A decisão atualiza a análise, mas não emite token de acesso.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="visitor-document-reason">
              Motivo {decision === "REJECT" ? "obrigatório" : "opcional"}
            </Label>
            <Textarea
              id="visitor-document-reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={240}
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setDecision(null)}>
              Cancelar
            </Button>
            <Button
              type="button"
              variant={decision === "REJECT" ? "destructive" : "default"}
              disabled={submitting}
              onClick={() => void confirmDecision()}
            >
              {submitting ? "Registrando..." : "Confirmar decisão"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value < 1024) return `${Math.max(0, value)} B`
  return `${(value / 1024 / 1024).toFixed(2)} MB`
}
