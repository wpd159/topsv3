'use client'

import { useEffect, useRef, useState } from 'react'
import { FileUp, Loader2 } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { AdminKycSubmission } from '@/features/admin-anuncios/types'

import { uploadAdminUserDocuments, type AdminKycUpload } from './api'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  usuarioId: string
  replacing?: AdminKycSubmission | null
  onSuccess: () => Promise<void> | void
}

function operationKey() {
  const suffix = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}`
  return `admin-kyc-${suffix}`
}

export function AdminKycUploadDialog({
  open,
  onOpenChange,
  usuarioId,
  replacing,
  onSuccess,
}: Props) {
  const [tipo, setTipo] = useState<AdminKycUpload['tipoDocumento']>('IDENTIDADE')
  const [modo, setModo] = useState<AdminKycUpload['modoDocumento']>('UNICO')
  const [unico, setUnico] = useState<File | null>(null)
  const [frente, setFrente] = useState<File | null>(null)
  const [verso, setVerso] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const keyRef = useRef(operationKey())

  useEffect(() => {
    if (!open) return
    setTipo((replacing?.documentos[0]?.tipo as AdminKycUpload['tipoDocumento']) || 'IDENTIDADE')
    setModo('UNICO')
    setUnico(null)
    setFrente(null)
    setVerso(null)
    setError(null)
    keyRef.current = operationKey()
  }, [open, replacing])

  async function submit() {
    if (busy) return
    if ((modo === 'UNICO' && !unico) || (modo === 'FRENTE_VERSO' && !frente)) {
      setError(new Error('Selecione os arquivos obrigatorios.'))
      return
    }
    setBusy(true)
    setError(null)
    try {
      await uploadAdminUserDocuments(usuarioId, {
        tipoDocumento: tipo,
        modoDocumento: modo,
        documentoUnico: unico,
        documentoFrente: frente,
        documentoVerso: verso,
        envioSubstituidoId: replacing?.envioId,
        idempotencyKey: keyRef.current,
      })
      await onSuccess()
      onOpenChange(false)
    } catch (reason) {
      setError(reason)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!busy) onOpenChange(next) }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{replacing ? 'Substituir documentos' : 'Adicionar documentos'}</DialogTitle>
          <DialogDescription>
            O novo envio permanece pendente ate uma decisao administrativa de KYC.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label>Tipo documental</Label>
              <Select value={tipo} onValueChange={(value) => setTipo(value as AdminKycUpload['tipoDocumento'])}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="IDENTIDADE">Identidade</SelectItem>
                  <SelectItem value="VERIFICACAO_IDADE">Verificacao de idade</SelectItem>
                  <SelectItem value="COMPROVANTE">Comprovante</SelectItem>
                  <SelectItem value="OUTRO">Outro</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>Formato do envio</Label>
              <Select
                value={modo}
                onValueChange={(value) => {
                  setModo(value as AdminKycUpload['modoDocumento'])
                  setUnico(null)
                  setFrente(null)
                  setVerso(null)
                }}
              >
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="UNICO">Arquivo unico</SelectItem>
                  <SelectItem value="FRENTE_VERSO">Frente e verso</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          {modo === 'UNICO' ? (
            <FileField
              label="Documento"
              accept="image/jpeg,image/png,application/pdf"
              onChange={setUnico}
            />
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              <FileField label="Frente" accept="image/jpeg,image/png" onChange={setFrente} />
              <FileField label="Verso (opcional)" accept="image/jpeg,image/png" onChange={setVerso} />
            </div>
          )}

          {error ? <ContractState error={error} compact /> : null}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" disabled={busy} onClick={() => onOpenChange(false)}>
            Cancelar
          </Button>
          <Button type="button" disabled={busy} onClick={() => void submit()}>
            {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <FileUp className="mr-2 h-4 w-4" />}
            {replacing ? 'Confirmar substituicao' : 'Enviar documentos'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function FileField({
  label,
  accept,
  onChange,
}: {
  label: string
  accept: string
  onChange: (file: File | null) => void
}) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <Input
        type="file"
        accept={accept}
        onChange={(event) => onChange(event.target.files?.[0] || null)}
      />
    </div>
  )
}
