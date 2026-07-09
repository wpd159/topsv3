'use client'

import { Button } from '@/components/ui/button'
import { useMemo, useState } from 'react'

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
  escopo: string
  ativo: boolean
  cor?: string
  duracoes?: FeatureCatalogoDuracao[]
}

type FeaturePatch = Partial<
  Pick<FeatureCatalogo, 'nome' | 'descricao' | 'custoCreditos' | 'duracaoHoras' | 'ativo'>
> & {
  duracoes?: FeatureCatalogoDuracao[]
}

type FeatureDuracaoDraft = {
  id?: number
  dias: string
  custoCreditos: string
  ativo: boolean
  sortOrder: string
}

export default function FeatureCatalogoCard(p: {
  feature: FeatureCatalogo
  onSave: (patch: FeaturePatch) => Promise<void> | void
}) {
  const f = p.feature

  const displayNome =
    f.codigo === 'FOTOS_EXTRA_5'
      ? 'Ate 10 fotos no anuncio'
      : f.codigo === 'ANUNCIO_TOPO'
        ? 'Anuncio no topo'
        : f.nome

  const displayDescricao =
    f.codigo === 'FOTOS_EXTRA_5'
      ? 'Amplia o limite do anuncio para ate 10 fotos durante a vigencia do beneficio.'
      : f.codigo === 'ANUNCIO_TOPO'
        ? 'Usa a matriz de duracoes para definir dias e creditos do topo da lista.'
        : (f.descricao ?? '')

  const [editando, setEditando] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const [nome, setNome] = useState(f.nome ?? '')
  const [descricao, setDescricao] = useState(f.descricao ?? '')
  const [custo, setCusto] = useState(String(f.custoCreditos ?? 0))
  const [duracao, setDuracao] = useState(f.duracaoHoras == null ? '' : String(f.duracaoHoras))
  const [ativo, setAtivo] = useState(Boolean(f.ativo))
  const [duracoes, setDuracoes] = useState<FeatureDuracaoDraft[]>(
    (f.duracoes ?? []).map((item) => ({
      id: item.id,
      dias: String(item.dias ?? ''),
      custoCreditos: String(item.custoCreditos ?? ''),
      ativo: Boolean(item.ativo),
      sortOrder: String(item.sortOrder ?? 0),
    }))
  )

  const badgeCodigo = useMemo(() => {
    if (f.codigo === 'FOTOS_EXTRA_5') return 'FOTOS EXTRA'
    if (f.codigo === 'ANUNCIO_TOPO') return 'TOPO'
    const short = String(f.codigo || '').replaceAll('_', ' ')
    return short.length > 22 ? short.slice(0, 22) + '...' : short
  }, [f.codigo])

  const custoResumo = `${f.custoCreditos} credito${f.custoCreditos === 1 ? '' : 's'}`
  const duracaoResumo = f.duracaoHoras == null ? 'Livre' : `${f.duracaoHoras}h`
  const matrizResumo = `${f.duracoes?.length ?? 0} faixa${(f.duracoes?.length ?? 0) === 1 ? '' : 's'}`

  const reset = () => {
    setNome(f.nome ?? '')
    setDescricao(f.descricao ?? '')
    setCusto(String(f.custoCreditos ?? 0))
    setDuracao(f.duracaoHoras == null ? '' : String(f.duracaoHoras))
    setAtivo(Boolean(f.ativo))
    setDuracoes(
      (f.duracoes ?? []).map((item) => ({
        id: item.id,
        dias: String(item.dias ?? ''),
        custoCreditos: String(item.custoCreditos ?? ''),
        ativo: Boolean(item.ativo),
        sortOrder: String(item.sortOrder ?? 0),
      }))
    )
  }

  const updateDuracao = (index: number, patch: Partial<FeatureDuracaoDraft>) => {
    setDuracoes((prev) => prev.map((item, i) => (i === index ? { ...item, ...patch } : item)))
  }

  const addDuracao = () => {
    setDuracoes((prev) => [
      ...prev,
      { dias: '', custoCreditos: '', ativo: true, sortOrder: String(prev.length + 1) },
    ])
  }

  const removeDuracao = (index: number) => {
    setDuracoes((prev) => prev.filter((_, i) => i !== index))
  }

  const save = async () => {
    const custoInt = Math.max(0, parseInt(custo || '0', 10) || 0)
    const duracaoInt =
      duracao.trim() === '' ? null : Math.max(0, parseInt(duracao || '0', 10) || 0)

    const patch: FeaturePatch = {
      nome: nome.trim(),
      descricao: descricao.trim(),
      custoCreditos: custoInt,
      duracaoHoras: duracaoInt,
      ativo,
    }

    patch.duracoes = duracoes.map((item, index) => ({
      id: item.id,
      dias: Math.max(0, parseInt(item.dias || '0', 10) || 0),
      custoCreditos: Math.max(0, parseInt(item.custoCreditos || '0', 10) || 0),
      ativo: item.ativo,
      sortOrder: Math.max(0, parseInt(item.sortOrder || String(index), 10) || 0),
    }))

    setSalvando(true)
    try {
      await p.onSave(patch)
      setEditando(false)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
      <div className={`bg-gradient-to-br p-5 ${f.cor || 'from-gray-100 to-transparent'}`}>
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="rounded-full border border-white/60 bg-white/70 px-2 py-1 text-[11px]">
                {badgeCodigo}
              </span>
              <span className="rounded-full border border-white/60 bg-white/70 px-2 py-1 text-[11px]">
                {f.escopo}
              </span>
              {!ativo && (
                <span className="rounded-full border border-red-200 bg-red-50 px-2 py-1 text-[11px] text-red-600">
                  INATIVA
                </span>
              )}
            </div>

            <h3 className="mt-2 truncate text-base font-semibold text-gray-900">{displayNome}</h3>
            <p className="mt-1 line-clamp-2 text-sm text-gray-600">{displayDescricao || '-'}</p>
          </div>

          <Button
            type="button"
            variant="outline"
            onClick={() => {
              if (editando) {
                reset()
                setEditando(false)
              } else {
                setEditando(true)
              }
            }}
            className="px-3 py-2 text-xs"
          >
            {editando ? 'Cancelar' : 'Editar'}
          </Button>
        </div>

        <div className="mt-4 grid grid-cols-2 gap-3">
          <div className="rounded-xl border border-white/60 bg-white/70 p-3">
            <p className="text-[11px] text-gray-600">Matriz</p>
            <p className="text-lg font-bold text-gray-900">{matrizResumo}</p>
          </div>
          <div className="rounded-xl border border-white/60 bg-white/70 p-3">
            <p className="text-[11px] text-gray-600">Duracao padrao</p>
            <p className="text-lg font-bold text-gray-900">{duracaoResumo}</p>
          </div>
        </div>
      </div>

      {editando && (
        <div className="space-y-4 border-t border-gray-100 p-5">
          <div className="grid grid-cols-1 gap-3">
            <div>
              <label className="text-xs text-gray-600">Nome</label>
              <input
                value={nome}
                onChange={(e) => setNome(e.target.value)}
                className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"
                placeholder="Nome da feature"
              />
            </div>

            <div>
              <label className="text-xs text-gray-600">Descricao</label>
              <textarea
                value={descricao}
                onChange={(e) => setDescricao(e.target.value)}
                className="mt-1 min-h-[90px] w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"
                placeholder="Descricao curta"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-gray-600">Custo padrao</label>
                <div className="mt-1 flex items-center rounded-lg border border-gray-200 bg-white">
                  <input
                    value={custo}
                    onChange={(e) => setCusto(e.target.value.replace(/[^\d]/g, ''))}
                    className="w-full rounded-lg border-0 bg-transparent px-3 py-2 text-sm outline-none"
                    inputMode="numeric"
                    placeholder="Ex: 8"
                  />
                  <span className="pr-3 text-xs font-medium text-gray-500">creditos</span>
                </div>
              </div>

              <div>
                <label className="text-xs text-gray-600">Duracao padrao</label>
                <div className="mt-1 flex items-center rounded-lg border border-gray-200 bg-white">
                  <input
                    value={duracao}
                    onChange={(e) => setDuracao(e.target.value.replace(/[^\d]/g, ''))}
                    className="w-full rounded-lg border-0 bg-transparent px-3 py-2 text-sm outline-none"
                    inputMode="numeric"
                    placeholder="Ex: 168"
                  />
                  <span className="pr-3 text-xs font-medium text-gray-500">h</span>
                </div>
                <p className="mt-1 text-[11px] text-gray-500">Fallback quando a matriz estiver vazia.</p>
              </div>
            </div>

              <div className="rounded-2xl border border-gray-200 bg-gray-50/70 p-4">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold text-gray-900">Matriz de duracoes</p>
                    <p className="text-xs text-gray-500">
                      Dias, creditos, ordem e status usados pelo wizard de monetizacao.
                    </p>
                  </div>
                  <Button type="button" variant="outline" onClick={addDuracao} className="text-xs">
                    Adicionar linha
                  </Button>
                </div>

                <div className="mt-4 space-y-3">
                  {duracoes.map((item, index) => (
                    <div
                      key={item.id ?? `new-${index}`}
                      className="grid gap-3 rounded-xl border border-gray-200 bg-white p-3 sm:grid-cols-[1fr_1fr_1fr_auto_auto]"
                    >
                      <div>
                        <label className="text-[11px] text-gray-600">Dias</label>
                        <input
                          value={item.dias}
                          onChange={(e) =>
                            updateDuracao(index, { dias: e.target.value.replace(/[^\d]/g, '') })
                          }
                          className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"
                          inputMode="numeric"
                          placeholder="7"
                        />
                      </div>

                      <div>
                        <label className="text-[11px] text-gray-600">Creditos</label>
                        <input
                          value={item.custoCreditos}
                          onChange={(e) =>
                            updateDuracao(index, {
                              custoCreditos: e.target.value.replace(/[^\d]/g, ''),
                            })
                          }
                          className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"
                          inputMode="numeric"
                          placeholder="51"
                        />
                      </div>

                      <div>
                        <label className="text-[11px] text-gray-600">Ordem</label>
                        <input
                          value={item.sortOrder}
                          onChange={(e) =>
                            updateDuracao(index, {
                              sortOrder: e.target.value.replace(/[^\d]/g, ''),
                            })
                          }
                          className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"
                          inputMode="numeric"
                          placeholder="1"
                        />
                      </div>

                      <label className="flex items-end gap-2 text-sm text-gray-700">
                        <input
                          type="checkbox"
                          checked={item.ativo}
                          onChange={(e) => updateDuracao(index, { ativo: e.target.checked })}
                          className="mb-2 h-4 w-4"
                        />
                        Ativa
                      </label>

                      <div className="flex items-end">
                        <Button
                          type="button"
                          variant="outline"
                          onClick={() => removeDuracao(index)}
                          className="text-xs"
                        >
                          Remover
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

            <label className="flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={ativo}
                onChange={(e) => setAtivo(e.target.checked)}
                className="h-4 w-4"
              />
              Feature ativa
            </label>
          </div>

          <div className="flex gap-2">
            <Button
              type="button"
              onClick={save}
              disabled={salvando}
              className="flex-1 px-4 py-2 text-sm text-white disabled:opacity-60"
            >
              {salvando ? 'Salvando...' : 'Salvar'}
            </Button>
            <Button
              type="button"
              onClick={() => {
                reset()
                setEditando(false)
              }}
              className="px-4 py-2 text-sm text-white hover:bg-gray-50"
            >
              Fechar
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
