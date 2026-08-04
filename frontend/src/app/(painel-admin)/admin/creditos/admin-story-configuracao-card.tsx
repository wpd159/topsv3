'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { RefreshCw, Save } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  fetchAdminStoryConfiguracao,
  saveAdminStoryConfiguracao,
  type AdminStoryConfiguracao,
} from '@/lib/admin-stories-api'

function custoValido(value: string) {
  if (!value.trim()) return null
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 0 && parsed <= 1_000_000 ? parsed : null
}

function custoSalvo(configuracao: AdminStoryConfiguracao | null) {
  return configuracao?.custoCreditos == null ? '' : String(configuracao.custoCreditos)
}

export function AdminStoryConfiguracaoCard() {
  const [configuracao, setConfiguracao] = useState<AdminStoryConfiguracao | null>(null)
  const [ativo, setAtivo] = useState(false)
  const [custo, setCusto] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const saveLock = useRef(false)
  const errorRef = useRef<HTMLDivElement | null>(null)

  const dirty = useMemo(
    () => ativo !== Boolean(configuracao?.ativo) || custo !== custoSalvo(configuracao),
    [ativo, configuracao, custo]
  )

  const carregar = useCallback(async () => {
    setLoading(true)
    setError(null)
    setSuccess(null)
    try {
      const atual = await fetchAdminStoryConfiguracao()
      setConfiguracao(atual)
      setAtivo(atual.ativo)
      setCusto(custoSalvo(atual))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível carregar a configuração de Stories.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void carregar()
  }, [carregar])

  useEffect(() => {
    if (error) errorRef.current?.focus()
  }, [error])

  async function salvar() {
    if (saveLock.current || !dirty) return
    const custoCreditos = custoValido(custo)
    if (custoCreditos == null) {
      setSuccess(null)
      setError('Informe um custo inteiro entre 0 e 1.000.000 créditos.')
      return
    }
    if (configuracao?.ativo && !ativo && !window.confirm(
      'Novas ativações de Stories ficarão indisponíveis. Direitos já adquiridos e Stories ativos serão preservados.'
    )) return
    saveLock.current = true
    setSaving(true)
    setError(null)
    setSuccess(null)
    try {
      const atual = await saveAdminStoryConfiguracao({
        ativo,
        custoCreditos,
        versao: configuracao?.versao ?? null,
      })
      setConfiguracao(atual)
      setAtivo(atual.ativo)
      setCusto(custoSalvo(atual))
      setSuccess('Configuração de Stories salva.')
      toast.success('Configuração de Stories atualizada.')
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : 'Não foi possível salvar a configuração de Stories.'
      setError(message)
      toast.error(message)
    } finally {
      saveLock.current = false
      setSaving(false)
    }
  }

  if (loading) {
    return <p className="text-sm text-gray-500" role="status">Carregando configuração de Stories...</p>
  }

  return (
    <section className="rounded-lg border border-gray-200 bg-white p-5" aria-labelledby="story-config-title">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="story-config-title" className="text-base font-semibold text-gray-900">Stories</h2>
          <p className="mt-1 text-sm text-gray-500">
            Permite promover um anúncio ou publicar uma mídia exclusiva por 24 horas.
          </p>
        </div>
        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${ativo ? 'bg-emerald-100 text-emerald-800' : 'bg-gray-100 text-gray-700'}`}>
          {ativo ? 'Novas ativações disponíveis' : 'Novas ativações indisponíveis'}
        </span>
      </div>

      {!configuracao?.configurada ? (
        <p className="mt-4 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800" role="status">
          Stories ainda não foi configurado.
        </p>
      ) : null}

      <div className="mt-5 grid gap-4 sm:grid-cols-[180px_220px_minmax(0,1fr)] sm:items-end">
        <label className="flex min-h-10 items-center gap-2 text-sm font-medium text-gray-800">
          <input
            type="checkbox"
            checked={ativo}
            onChange={(event) => {
              setAtivo(event.target.checked)
              setSuccess(null)
            }}
            className="size-4 accent-[#C51683]"
          />
          Status comercial ativo
        </label>
        <label className="text-xs font-medium text-gray-600">
          Custo por Story (créditos)
          <Input
            className="mt-1"
            inputMode="numeric"
            min={0}
            max={1_000_000}
            type="number"
            value={custo}
            onChange={(event) => {
              setCusto(event.target.value)
              setSuccess(null)
            }}
          />
        </label>
        <div className="min-w-0 rounded-md border border-gray-200 bg-gray-50 px-3 py-2">
          <p className="text-xs font-medium text-gray-500">Duração</p>
          <p className="mt-1 text-sm font-semibold text-gray-900">24 horas — fixa</p>
        </div>
      </div>

      <div className="mt-4 min-h-5 text-sm" aria-live="polite">
        {dirty ? <p className="font-medium text-amber-700" role="status">Alterações não salvas.</p> : null}
        {!dirty && success ? <p className="font-medium text-emerald-700" role="status">{success}</p> : null}
        {!dirty && !success && configuracao?.atualizadoEm ? (
          <p className="text-gray-500">Última atualização: {new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(configuracao.atualizadoEm))}</p>
        ) : null}
      </div>

      {error ? (
        <div ref={errorRef} tabIndex={-1} className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-md border border-red-200 bg-red-50 px-3 py-2 outline-none focus:ring-2 focus:ring-red-500" role="alert">
          <p className="text-sm text-red-700">{error}</p>
          <Button type="button" size="sm" variant="outline" onClick={() => void carregar()}>
            <RefreshCw className="mr-2 size-4" aria-hidden="true" />
            Tentar novamente
          </Button>
        </div>
      ) : null}

      <div className="mt-5 flex justify-end">
        <Button type="button" disabled={saving || !dirty} aria-busy={saving} onClick={() => void salvar()}>
          <Save className="mr-2 size-4" aria-hidden="true" />
          {saving ? 'Salvando...' : 'Salvar configuração'}
        </Button>
      </div>
    </section>
  )
}
