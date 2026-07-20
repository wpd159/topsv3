'use client'

import {
  ContractState,
  PendingActionFeedback,
  usePendingContractActions,
} from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

type Props = {
  anuncioId: string
  open: boolean
  onCancel: () => void
  onSaved: () => void
}

const SERVICES = ['ANAL', 'ORAL', 'MASSAGEM', 'BEIJO', 'FANTASIAS']
const PLACES = ['MEU_LOCAL', 'HOTEL_MOTEL', 'A_COMBINAR']

export function AdminAnuncioDadosInlineEditor({ anuncioId, open, onCancel }: Props) {
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.moderationLegacyActions
  )

  if (!open) return null

  return (
    <section className="space-y-5 border-t border-gray-100 pt-5" aria-label="Edição administrativa do anúncio">
      <div><h3 className="font-semibold text-gray-900">Editar dados do anúncio</h3><p className="text-xs text-gray-500">ID técnico: {anuncioId}</p></div>
      <ContractState error={error} />
      <PendingActionFeedback attemptedAction={attemptedAction} />
      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-2 md:col-span-2"><Label htmlFor="staff-title">Título</Label><Input id="staff-title" placeholder="Carregamento depende do contrato V3" /></div>
        <div className="space-y-2 md:col-span-2"><Label htmlFor="staff-description">Descrição</Label><Textarea id="staff-description" rows={8} /></div>
        <div className="space-y-2"><Label>Categoria</Label><Select defaultValue="indisponivel"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="indisponivel">Categoria indisponível</SelectItem></SelectContent></Select></div>
        <div className="space-y-2"><Label htmlFor="staff-price">Preço</Label><Input id="staff-price" type="number" min={0} step="0.01" /></div>
        <div className="space-y-2"><Label>UF</Label><Select defaultValue="indisponivel"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="indisponivel">UF indisponível</SelectItem></SelectContent></Select></div>
        <div className="space-y-2"><Label>Cidade</Label><Select defaultValue="indisponivel"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="indisponivel">Cidade indisponível</SelectItem></SelectContent></Select></div>
        <div className="space-y-2"><Label>Bairro</Label><Select defaultValue="indisponivel"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="indisponivel">Bairro indisponível</SelectItem></SelectContent></Select></div>
        <div className="space-y-2"><Label>Horário</Label><Select defaultValue="indisponivel"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="indisponivel">Horário indisponível</SelectItem></SelectContent></Select></div>
        <div className="space-y-2"><Label htmlFor="staff-micro-region">Microrregião</Label><Input id="staff-micro-region" placeholder="Ponto de referência" /></div>
        <div className="space-y-2"><Label htmlFor="staff-whatsapp">WhatsApp público</Label><Input id="staff-whatsapp" /></div>
      </div>
      <fieldset className="space-y-2"><legend className="font-medium text-gray-800">Serviços</legend><div className="flex flex-wrap gap-3">{SERVICES.map((service) => <label key={service} className="flex items-center gap-2 text-sm"><input type="checkbox" /> {service}</label>)}</div></fieldset>
      <fieldset className="space-y-2"><legend className="font-medium text-gray-800">Locais de atendimento</legend><div className="flex flex-wrap gap-3">{PLACES.map((place) => <label key={place} className="flex items-center gap-2 text-sm"><input type="checkbox" /> {place}</label>)}</div></fieldset>
      <div className="flex justify-end gap-2"><Button type="button" variant="outline" onClick={onCancel}>Cancelar</Button><Button type="button" onClick={() => runPendingAction('Salvar alterações administrativas')}>Salvar alterações</Button></div>
    </section>
  )
}
