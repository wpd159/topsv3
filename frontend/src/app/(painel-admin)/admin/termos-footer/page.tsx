'use client'

import { useState } from 'react'

import {
  ContractState,
  PendingActionFeedback,
  usePendingContractActions,
} from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'
import { SITE_CONTENT_KEYS, type SiteContentKey } from '@/lib/site-content'

const LABELS: Record<SiteContentKey, string> = {
  'quem-somos': 'Quem somos',
  'footer-resumo-institucional': 'Resumo institucional do footer',
  'termos-de-uso': 'Termos de uso',
  'politica-privacidade': 'Privacidade',
  'politica-cookies': 'Cookies',
  'consentimento-promocional': 'Consentimento promocional',
  verificacao: 'Verificacao',
  'popup-login': 'Avisos de login',
  'texto-whatsapp': 'Texto WhatsApp',
}

export default function AdminSiteContentPage() {
  const [openKey, setOpenKey] = useState<SiteContentKey | null>(null)
  const [values, setValues] = useState<Record<SiteContentKey, { titulo: string; corpo: string }>>(
    () => Object.fromEntries(SITE_CONTENT_KEYS.map((key) => [key, { titulo: '', corpo: '' }])) as Record<SiteContentKey, { titulo: string; corpo: string }>
  )
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.siteContent
  )

  return (
    <section className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-800">Conteudo do Site</h1>
        <p className="mt-1 text-sm text-gray-500">Os editores foram preservados e aguardam o contrato backend V3.</p>
      </div>
      <ContractState error={error} />
      <PendingActionFeedback attemptedAction={attemptedAction} />
      <div className="grid grid-cols-1 gap-5">
        {SITE_CONTENT_KEYS.map((key) => {
          const entry = values[key]
          const isOpen = openKey === key
          return (
            <Card key={key} className="border border-gray-100 p-0 shadow-sm">
              <button
                type="button"
                className="flex w-full items-center justify-between gap-4 p-5 text-left"
                aria-expanded={isOpen}
                aria-controls={`${key}-editor`}
                onClick={() => setOpenKey((current) => (current === key ? null : key))}
              >
                <div>
                  <h2 className="text-lg font-semibold text-gray-900">{LABELS[key]}</h2>
                  <p className="text-xs text-gray-500">Chave interna: {key}</p>
                </div>
                <span className="rounded-full border border-gray-200 px-3 py-1 text-xs font-medium text-gray-600">
                  {isOpen ? 'Recolher' : 'Expandir'}
                </span>
              </button>
              {isOpen ? (
                <div id={`${key}-editor`} className="space-y-4 border-t border-gray-100 p-5">
                  <div className="space-y-2">
                    <Label htmlFor={`${key}-title`}>Titulo</Label>
                    <Input id={`${key}-title`} value={entry.titulo} placeholder="Conteúdo não carregado" onChange={(event) => setValues((current) => ({ ...current, [key]: { ...current[key], titulo: event.target.value } }))} />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor={`${key}-body`}>Conteudo</Label>
                    <Textarea id={`${key}-body`} rows={key === 'termos-de-uso' ? 12 : 8} value={entry.corpo} placeholder="Conteúdo não carregado" onChange={(event) => setValues((current) => ({ ...current, [key]: { ...current[key], corpo: event.target.value } }))} />
                  </div>
                  <div className="flex justify-end">
                    <Button type="button" onClick={() => runPendingAction(`Salvar ${LABELS[key]}`)}>
                      Salvar
                    </Button>
                  </div>
                </div>
              ) : null}
            </Card>
          )
        })}
      </div>
    </section>
  )
}
