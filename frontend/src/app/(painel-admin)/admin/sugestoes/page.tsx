'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Megaphone, RefreshCw, Search } from 'lucide-react'

import SugestaoDetailsModal from '../components/sugestao-details-modal'
import SugestoesTable from '../components/sugestoes-table'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  adminSugestaoError,
  buscarIndicadoresSugestoes,
  listarAdminSugestoes,
  type AdminSugestaoIndicadores,
  type AdminSugestaoPagina,
} from '@/lib/admin-sugestao-api'

const estados = [
  ['TODOS', 'Todas'],
  ['PENDENTE', 'Novas'],
  ['EM_ANALISE', 'Em analise'],
  ['RESOLVIDO', 'Aceitas'],
  ['RECUSADO', 'Recusadas'],
] as const

export default function AdminSugestoesPage() {
  const [termo, setTermo] = useState('')
  const [termoAplicado, setTermoAplicado] = useState('')
  const [status, setStatus] = useState('TODOS')
  const [pagina, setPagina] = useState(0)
  const [tamanho] = useState(20)
  const [dados, setDados] = useState<AdminSugestaoPagina | null>(null)
  const [indicadores, setIndicadores] = useState<AdminSugestaoIndicadores | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [sugestaoId, setSugestaoId] = useState<string | null>(null)
  const [detalheAberto, setDetalheAberto] = useState(false)

  const filtros = useMemo(() => ({
    termo: termoAplicado,
    status,
    pagina,
    tamanho,
  }), [pagina, status, tamanho, termoAplicado])

  const sincronizarUrl = useCallback(() => {
    const query = new URLSearchParams()
    if (termoAplicado) query.set('termo', termoAplicado)
    if (status !== 'TODOS') query.set('status', status)
    if (pagina > 0) query.set('page', String(pagina))
    const suffix = query.size ? `?${query.toString()}` : ''
    window.history.replaceState(null, '', `/admin/sugestoes${suffix}`)
  }, [pagina, status, termoAplicado])

  const carregar = useCallback(async (signal?: AbortSignal) => {
    setCarregando(true)
    setErro(null)
    try {
      const [paginaCarregada, contadores] = await Promise.all([
        listarAdminSugestoes(filtros, signal),
        buscarIndicadoresSugestoes(signal),
      ])
      setDados(paginaCarregada)
      setIndicadores(contadores)
    } catch (cause) {
      if (!signal?.aborted) setErro(adminSugestaoError(cause).message)
    } finally {
      if (!signal?.aborted) setCarregando(false)
    }
  }, [filtros])

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const termoInicial = params.get('termo') || ''
    setTermo(termoInicial)
    setTermoAplicado(termoInicial)
    setStatus(params.get('status') || 'TODOS')
    setPagina(Math.max(0, Number(params.get('page') || 0)))
  }, [])

  useEffect(() => {
    sincronizarUrl()
    const controller = new AbortController()
    void carregar(controller.signal)
    return () => controller.abort()
  }, [carregar, sincronizarUrl])

  const contagem = (filtro: string) => {
    if (!indicadores) return '-'
    if (filtro === 'TODOS') return indicadores.total
    if (filtro === 'PENDENTE') return indicadores.novas
    if (filtro === 'EM_ANALISE') return indicadores.emAnalise
    if (filtro === 'RESOLVIDO') return indicadores.resolvidas
    return indicadores.recusadas
  }

  const vazio = !carregando && !erro && (dados?.itens.length ?? 0) === 0

  return (
    <section className="space-y-5">
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold text-zinc-950">Sugestoes de melhoria e bugs</h1>
          <p className="text-sm text-zinc-600">
            Triagem, providencias e historico dos feedbacks recebidos.
          </p>
        </div>
        <Button type="button" variant="outline" onClick={() => void carregar()}>
          <RefreshCw className={`mr-2 h-4 w-4 ${carregando ? 'animate-spin' : ''}`} />
          Atualizar
        </Button>
      </header>

      <div className="grid grid-cols-2 gap-2 lg:grid-cols-5">
        {estados.map(([value, label]) => (
          <button
            key={value}
            type="button"
            onClick={() => {
              setStatus(value)
              setPagina(0)
            }}
            className={`border-l-4 px-4 py-3 text-left transition-colors ${
              status === value
                ? 'border-pink-500 bg-pink-50'
                : 'border-zinc-200 bg-white hover:bg-zinc-50'
            }`}
          >
            <span className="block text-xs font-medium text-zinc-500">{label}</span>
            <span className="mt-1 block text-xl font-semibold text-zinc-950">
              {contagem(value)}
            </span>
          </button>
        ))}
      </div>

      <form
        className="relative border-y py-4"
        onSubmit={(event) => {
          event.preventDefault()
          setTermoAplicado(termo.trim())
          setPagina(0)
        }}
      >
        <Search className="pointer-events-none absolute left-3 top-[26px] h-4 w-4 text-zinc-400" />
        <Input
          value={termo}
          onChange={(event) => setTermo(event.target.value)}
          placeholder="Buscar por titulo, descricao ou protocolo"
          className="pl-9"
        />
      </form>

      {erro ? (
        <div className="flex flex-col items-start justify-between gap-3 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800 sm:flex-row sm:items-center">
          <p>{erro}</p>
          <Button type="button" size="sm" variant="outline" onClick={() => void carregar()}>
            Tentar novamente
          </Button>
        </div>
      ) : null}

      {carregando ? (
        <p className="py-16 text-center text-sm text-zinc-500">Carregando sugestoes...</p>
      ) : null}

      {vazio ? (
        <div className="flex min-h-64 flex-col items-center justify-center gap-3 border-y py-12 text-center">
          <Megaphone className="h-8 w-8 text-zinc-400" />
          <div>
            <p className="font-medium text-zinc-900">Nenhuma sugestao encontrada</p>
            <p className="text-sm text-zinc-500">
              A fila esta vazia para os filtros aplicados.
            </p>
          </div>
        </div>
      ) : null}

      {!carregando && !erro ? (
        <SugestoesTable
          itens={dados?.itens ?? []}
          onOpen={(id) => {
            setSugestaoId(id)
            setDetalheAberto(true)
          }}
        />
      ) : null}

      {(dados?.totalPaginas ?? 0) > 1 ? (
        <div className="flex items-center justify-between">
          <Button
            type="button"
            variant="outline"
            disabled={pagina === 0}
            onClick={() => setPagina((value) => Math.max(0, value - 1))}
          >
            Anterior
          </Button>
          <span className="text-sm text-zinc-600">
            Pagina {pagina + 1} de {dados?.totalPaginas}
          </span>
          <Button
            type="button"
            variant="outline"
            disabled={pagina + 1 >= (dados?.totalPaginas ?? 0)}
            onClick={() => setPagina((value) => value + 1)}
          >
            Proxima
          </Button>
        </div>
      ) : null}

      <SugestaoDetailsModal
        open={detalheAberto}
        onOpenChange={setDetalheAberto}
        sugestaoId={sugestaoId}
        onChanged={() => {
          void carregar()
          window.dispatchEvent(new Event('admin-sugestoes-updated'))
        }}
      />
    </section>
  )
}
