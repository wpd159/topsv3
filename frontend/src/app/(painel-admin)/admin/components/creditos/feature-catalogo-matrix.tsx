'use client'

import { Button } from '@/components/ui/button'
import { useEffect, useMemo, useState } from 'react'

type FeatureCatalogoDuracao = {
  id?: number
  dias: number
  custoCreditos: number
  ativo: boolean
  sortOrder: number
}

type FeatureCatalogo = {
  id: number
  codigo: string
  nome: string
  descricao?: string
  custoCreditos: number
  duracaoHoras?: number | null
  escopo: 'ANUNCIO' | 'USUARIO' | string
  ativo: boolean
  duracoes?: FeatureCatalogoDuracao[]
}

type FeaturePatch = Partial<
  Pick<FeatureCatalogo, 'nome' | 'descricao' | 'custoCreditos' | 'duracaoHoras' | 'ativo'>
> & {
  duracoes?: FeatureCatalogoDuracao[]
}

type DraftCell = {
  id?: number
  value: string
  originalValue: string
  ativo: boolean
  sortOrder: number
}

type DraftByFeature = Record<number, Record<number, DraftCell>>

function featureName(feature: FeatureCatalogo) {
  if (feature.codigo === 'FOTOS_EXTRA_5') return 'Mais fotos'
  if (feature.codigo === 'ANUNCIO_TOPO') return 'Anuncio topo'
  if (feature.codigo === 'VIDEO_1') return 'Video'
  if (feature.codigo === 'WHATSAPP_CARD') return 'WhatsApp Card'
  if (feature.codigo === 'CARROSSEL_FOTOS') return 'Carrossel'
  if (feature.codigo === 'OCULTAR_IDADE') return 'Ocultar idade'
  if (feature.codigo === 'STORIES') return 'Stories'
  return feature.nome
}

function buildDraft(features: FeatureCatalogo[], columns: number[]): DraftByFeature {
  return Object.fromEntries(
    features.map((feature) => {
      const byDay = new Map((feature.duracoes ?? []).map((duracao) => [duracao.dias, duracao]))
      return [
        feature.id,
        Object.fromEntries(
          columns.map((dias, index) => {
            const duracao = byDay.get(dias)
            const value = duracao ? String(duracao.custoCreditos ?? '') : ''
            return [
              dias,
              {
                id: duracao?.id,
                value,
                originalValue: value,
                ativo: duracao?.ativo ?? true,
                sortOrder: duracao?.sortOrder ?? index + 1,
              },
            ]
          })
        ),
      ]
    })
  )
}

function isDirty(cell?: DraftCell) {
  return Boolean(cell && cell.value !== cell.originalValue)
}

export default function FeatureCatalogoMatrix({
  features,
  onSave,
}: {
  features: FeatureCatalogo[]
  onSave: (id: number, patch: FeaturePatch) => Promise<void> | void
}) {
  const catalogFeatures = useMemo(
    () => features.filter((feature) => feature.escopo === 'ANUNCIO'),
    [features]
  )

  const columns = useMemo(() => {
    const days = new Set<number>()
    catalogFeatures.forEach((feature) => {
      ;(feature.duracoes ?? []).forEach((duracao) => {
        if (duracao.dias > 0) days.add(duracao.dias)
      })
    })
    return Array.from(days).sort((a, b) => a - b)
  }, [catalogFeatures])

  const [draft, setDraft] = useState<DraftByFeature>(() => buildDraft(catalogFeatures, columns))
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    setDraft(buildDraft(catalogFeatures, columns))
  }, [catalogFeatures, columns])

  const dirtyFeatureIds = useMemo(
    () =>
      catalogFeatures
        .filter((feature) => columns.some((dias) => isDirty(draft[feature.id]?.[dias])))
        .map((feature) => feature.id),
    [catalogFeatures, columns, draft]
  )

  const hasChanges = dirtyFeatureIds.length > 0

  const updateCell = (featureId: number, dias: number, value: string) => {
    setDraft((current) => ({
      ...current,
      [featureId]: {
        ...current[featureId],
        [dias]: {
          ...current[featureId]?.[dias],
          value: value.replace(/[^\d]/g, ''),
        },
      },
    }))
  }

  const saveChanges = async () => {
    if (!hasChanges) return

    setSaving(true)
    try {
      for (const feature of catalogFeatures) {
        if (!dirtyFeatureIds.includes(feature.id)) continue

        const currentDraft = draft[feature.id] ?? {}
        const duracoes = columns
          .map((dias, index) => {
            const cell = currentDraft[dias]
            const value = cell?.value?.trim() ?? ''
            if (!value) return null
            return {
              id: cell?.id,
              dias,
              custoCreditos: Math.max(0, parseInt(value, 10) || 0),
              ativo: cell?.ativo ?? true,
              sortOrder: cell?.sortOrder ?? index + 1,
            }
          })
          .filter(Boolean) as FeatureCatalogoDuracao[]

        await onSave(feature.id, {
          nome: feature.nome,
          descricao: feature.descricao ?? '',
          custoCreditos: feature.custoCreditos,
          duracaoHoras: feature.duracaoHoras ?? null,
          ativo: feature.ativo,
          duracoes,
        })
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mt-5 rounded-2xl border border-gray-200 bg-white shadow-sm">
      <div className="flex flex-col gap-3 border-b border-gray-100 p-5 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h3 className="text-base font-semibold text-gray-900">Matriz de creditos por duracao</h3>
          <p className="mt-1 text-sm text-gray-500">
            Edite os creditos por beneficio sem abrir os cards individuais. As colunas vem das duracoes cadastradas.
          </p>
        </div>

        <Button
          type="button"
          onClick={() => void saveChanges()}
          disabled={!hasChanges || saving}
          className="h-10 px-4 text-sm text-white disabled:opacity-60"
        >
          {saving ? 'Salvando...' : 'Salvar alteracoes'}
        </Button>
      </div>

      {columns.length === 0 ? (
        <div className="p-5 text-sm text-gray-500">
          Nenhuma duracao cadastrada ainda. Use os cards abaixo para criar a primeira faixa.
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full border-separate border-spacing-0 text-sm">
            <thead>
              <tr className="bg-gray-50 text-left text-xs uppercase tracking-[0.12em] text-gray-500">
                <th className="sticky left-0 z-10 min-w-[220px] border-b border-gray-200 bg-gray-50 px-4 py-3">
                  Beneficio
                </th>
                {columns.map((dias) => (
                  <th key={dias} className="min-w-[112px] border-b border-gray-200 px-3 py-3 text-center">
                    {dias} {dias === 1 ? 'dia' : 'dias'}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {catalogFeatures.map((feature) => (
                <tr key={feature.id} className="border-b border-gray-100">
                  <th className="sticky left-0 z-10 border-b border-gray-100 bg-white px-4 py-3 text-left font-semibold text-gray-800">
                    <div>{featureName(feature)}</div>
                    <div className="mt-1 text-[11px] font-medium uppercase tracking-[0.12em] text-gray-400">
                      {feature.codigo}
                    </div>
                  </th>
                  {columns.map((dias) => {
                    const cell = draft[feature.id]?.[dias]
                    const changed = isDirty(cell)
                    const exists = Boolean(cell?.id)
                    return (
                      <td key={dias} className="border-b border-gray-100 px-3 py-2">
                        <div
                          className={[
                            'rounded-xl border bg-white px-2 py-1 transition',
                            changed
                              ? 'border-[#FC1EAD] bg-[#fff0f8] shadow-[0_0_0_1px_rgba(252,30,173,0.08)]'
                              : exists
                                ? 'border-gray-200'
                                : 'border-dashed border-gray-200 bg-gray-50',
                          ].join(' ')}
                        >
                          <input
                            value={cell?.value ?? ''}
                            onChange={(event) => updateCell(feature.id, dias, event.target.value)}
                            inputMode="numeric"
                            aria-label={`${featureName(feature)} ${dias} dias`}
                            placeholder="-"
                            className="h-8 w-full bg-transparent text-center font-semibold text-gray-900 outline-none placeholder:text-gray-300"
                          />
                        </div>
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
