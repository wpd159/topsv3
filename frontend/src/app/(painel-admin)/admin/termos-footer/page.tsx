'use client'

import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  listarConteudosSiteAdmin,
  salvarConteudoSiteAdmin,
} from '@/lib/admin-site-content-api'
import {
  normalizeApiError,
  type ApiContractError,
} from '@/lib/api-contract'
import { SITE_CONTENT_KEYS, type SiteContentKey } from '@/lib/site-content'
import { revalidarCacheConteudoSite } from './actions'

const LABELS: Record<SiteContentKey, string> = {
  'quem-somos': 'Quem somos',
  'footer-resumo-institucional': 'Resumo institucional do footer',
  'termos-de-uso': 'Termos de uso',
  'politica-privacidade': 'Privacidade',
  'politica-cookies': 'Cookies',
  'consentimento-promocional': 'Consentimento promocional',
  verificacao: 'Verificacao etaria',
  'popup-login': 'Aviso de acesso adulto',
  'texto-whatsapp': 'Aviso de seguranca no WhatsApp',
  'termos-conteudo-restrito': 'Termos de conteudo restrito',
  'privacidade-conteudo-restrito': 'Privacidade de conteudo restrito',
  'aviso-legal-conteudo-restrito': 'Aviso legal de conteudo restrito',
}

type EditorValue = {
  titulo: string
  corpo: string
  contentVersion: number | null
  contentHash: string | null
  updatedAt: string | null
}

const emptyValues = () =>
  Object.fromEntries(
    SITE_CONTENT_KEYS.map((key) => [
      key,
      {
        titulo: '',
        corpo: '',
        contentVersion: null,
        contentHash: null,
        updatedAt: null,
      },
    ]),
  ) as Record<SiteContentKey, EditorValue>

export default function AdminSiteContentPage() {
  const [openKey, setOpenKey] = useState<SiteContentKey | null>(null)
  const [values, setValues] = useState<Record<SiteContentKey, EditorValue>>(emptyValues)
  const [loading, setLoading] = useState(true)
  const [savingKey, setSavingKey] = useState<SiteContentKey | null>(null)
  const [error, setError] = useState<ApiContractError | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const entries = await listarConteudosSiteAdmin()
      const next = emptyValues()
      for (const entry of entries) {
        if (!SITE_CONTENT_KEYS.includes(entry.contentKey)) continue
        next[entry.contentKey] = {
          titulo: entry.titulo ?? '',
          corpo: entry.corpo ?? '',
          contentVersion: entry.contentVersion ?? null,
          contentHash: entry.contentHash ?? null,
          updatedAt: entry.updatedAt ?? null,
        }
      }
      setValues(next)
    } catch (loadError) {
      setError(normalizeApiError(loadError))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const save = async (key: SiteContentKey) => {
    const entry = values[key]
    setSavingKey(key)
    setError(null)
    try {
      const saved = await salvarConteudoSiteAdmin(key, {
        titulo: entry.titulo,
        corpo: entry.corpo,
      })
      await revalidarCacheConteudoSite()
      setValues((current) => ({
        ...current,
        [key]: {
          titulo: saved.titulo ?? '',
          corpo: saved.corpo ?? '',
          contentVersion: saved.contentVersion ?? null,
          contentHash: saved.contentHash ?? null,
          updatedAt: saved.updatedAt ?? null,
        },
      }))
      toast.success('Conteudo publicado e cache revalidado.')
    } catch (saveError) {
      setError(normalizeApiError(saveError))
    } finally {
      setSavingKey(null)
    }
  }

  return (
    <section className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-800">Conteudo do Site</h1>
        <p className="mt-1 text-sm text-gray-500">
          Fonte unica dos textos institucionais e juridicos exibidos ao publico.
        </p>
      </div>

      {error ? <ContractState error={error} onRetry={() => void load()} /> : null}

      <div className="grid grid-cols-1 gap-5">
        {SITE_CONTENT_KEYS.map((key) => {
          const entry = values[key]
          const isOpen = openKey === key
          const isSaving = savingKey === key
          return (
            <Card key={key} className="border border-gray-100 p-0 shadow-sm">
              <button
                type="button"
                className="flex w-full items-center justify-between gap-4 p-5 text-left"
                aria-expanded={isOpen}
                aria-controls={`${key}-editor`}
                onClick={() => setOpenKey((current) => (current === key ? null : key))}
                disabled={loading}
              >
                <div>
                  <h2 className="text-lg font-semibold text-gray-900">{LABELS[key]}</h2>
                  <p className="text-xs text-gray-500">
                    {entry.contentVersion === null
                      ? 'Ainda nao publicado'
                      : `Versao ${entry.contentVersion}`}
                  </p>
                </div>
                <span className="rounded-full border border-gray-200 px-3 py-1 text-xs font-medium text-gray-600">
                  {isOpen ? 'Recolher' : 'Expandir'}
                </span>
              </button>
              {isOpen ? (
                <div id={`${key}-editor`} className="space-y-4 border-t border-gray-100 p-5">
                  <div className="space-y-2">
                    <Label htmlFor={`${key}-title`}>Titulo</Label>
                    <Input
                      id={`${key}-title`}
                      value={entry.titulo}
                      onChange={(event) =>
                        setValues((current) => ({
                          ...current,
                          [key]: { ...current[key], titulo: event.target.value },
                        }))
                      }
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor={`${key}-body`}>Conteudo em Markdown seguro</Label>
                    <Textarea
                      id={`${key}-body`}
                      rows={key === 'termos-de-uso' ? 16 : 10}
                      value={entry.corpo}
                      onChange={(event) =>
                        setValues((current) => ({
                          ...current,
                          [key]: { ...current[key], corpo: event.target.value },
                        }))
                      }
                    />
                    <p className="text-xs text-gray-500">
                      Permitidos: paragrafos, titulos com #, listas e links HTTP(S) ou internos.
                    </p>
                  </div>
                  <div className="flex justify-end">
                    <Button
                      type="button"
                      disabled={
                        isSaving || !entry.titulo.trim() || !entry.corpo.trim()
                      }
                      onClick={() => void save(key)}
                    >
                      {isSaving ? 'Publicando...' : 'Publicar'}
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
