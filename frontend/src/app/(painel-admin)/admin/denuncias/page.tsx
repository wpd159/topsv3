'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Flag, RefreshCw, Search } from 'lucide-react'

import DenunciaDetailsModal from '../components/denuncia-details-modal'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  adminDenunciaError,
  buscarIndicadoresDenuncias,
  listarAdminDenuncias,
  type AdminDenunciaIndicadores,
  type AdminDenunciaPagina,
} from '@/lib/admin-denuncia-api'

const motivos = [
  ['TODOS', 'Todos os motivos'],
  ['CONTEUDO_INADEQUADO', 'Conteudo inadequado'],
  ['PERFIL_FALSO', 'Perfil falso'],
  ['GOLPE', 'Golpe / Scam'],
  ['SPAM', 'Spam'],
  ['OUTROS', 'Outros'],
] as const

const estados = [
  ['TODOS', 'Todas'],
  ['PENDENTE', 'Pendentes'],
  ['PUNIDA', 'Com providencia'],
  ['IGNORADA', 'Sem providencia'],
] as const

function dataHora(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default function AdminDenunciasPage() {
  const [termo, setTermo] = useState('')
  const [termoAplicado, setTermoAplicado] = useState('')
  const [motivo, setMotivo] = useState('TODOS')
  const [status, setStatus] = useState('TODOS')
  const [inicio, setInicio] = useState('')
  const [fim, setFim] = useState('')
  const [pagina, setPagina] = useState(0)
  const [tamanho] = useState(20)
  const [dados, setDados] = useState<AdminDenunciaPagina | null>(null)
  const [indicadores, setIndicadores] = useState<AdminDenunciaIndicadores | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [denunciaId, setDenunciaId] = useState<string | null>(null)
  const [detalheAberto, setDetalheAberto] = useState(false)

  const filtros = useMemo(() => ({
    termo: termoAplicado,
    motivo,
    status,
    inicio,
    fim,
    pagina,
    tamanho,
  }), [fim, inicio, motivo, pagina, status, tamanho, termoAplicado])

  const sincronizarUrl = useCallback(() => {
    const query = new URLSearchParams()
    if (termoAplicado) query.set('termo', termoAplicado)
    if (motivo !== 'TODOS') query.set('motivo', motivo)
    if (status !== 'TODOS') query.set('status', status)
    if (inicio) query.set('inicio', inicio)
    if (fim) query.set('fim', fim)
    if (pagina > 0) query.set('page', String(pagina))
    const suffix = query.size ? `?${query.toString()}` : ''
    window.history.replaceState(null, '', `/admin/denuncias${suffix}`)
  }, [fim, inicio, motivo, pagina, status, termoAplicado])

  const carregar = useCallback(async (signal?: AbortSignal) => {
    setCarregando(true)
    setErro(null)
    try {
      const [paginaCarregada, contadores] = await Promise.all([
        listarAdminDenuncias(filtros, signal),
        buscarIndicadoresDenuncias(signal),
      ])
      setDados(paginaCarregada)
      setIndicadores(contadores)
    } catch (cause) {
      if (!signal?.aborted) setErro(adminDenunciaError(cause).message)
    } finally {
      if (!signal?.aborted) setCarregando(false)
    }
  }, [filtros])

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const termoInicial = params.get('termo') || ''
    setTermo(termoInicial)
    setTermoAplicado(termoInicial)
    setMotivo(params.get('motivo') || 'TODOS')
    setStatus(params.get('status') || 'TODOS')
    setInicio(params.get('inicio') || '')
    setFim(params.get('fim') || '')
    setPagina(Math.max(0, Number(params.get('page') || 0)))
  }, [])

  useEffect(() => {
    sincronizarUrl()
    const controller = new AbortController()
    void carregar(controller.signal)
    return () => controller.abort()
  }, [carregar, sincronizarUrl])

  const contagem = (filtro: string) => {
    if (!indicadores) return '—'
    if (filtro === 'TODOS') return indicadores.total
    if (filtro === 'PENDENTE') return indicadores.pendentes
    if (filtro === 'PUNIDA') return indicadores.punidas
    return indicadores.ignoradas
  }

  const limpar = () => {
    setTermo('')
    setTermoAplicado('')
    setMotivo('TODOS')
    setStatus('TODOS')
    setInicio('')
    setFim('')
    setPagina(0)
  }

  const vazio = !carregando && !erro && (dados?.itens.length ?? 0) === 0

  return (
    <section className="space-y-5">
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold text-zinc-950">Denuncias</h1>
          <p className="text-sm text-zinc-600">Analise, providencias e historico de denuncias de anuncios.</p>
        </div>
        <Button type="button" variant="outline" onClick={() => void carregar()}>
          <RefreshCw className={`mr-2 h-4 w-4 ${carregando ? 'animate-spin' : ''}`} />
          Atualizar
        </Button>
      </header>

      <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
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
            <span className="mt-1 block text-xl font-semibold text-zinc-950">{contagem(value)}</span>
          </button>
        ))}
      </div>

      <div className="grid gap-3 border-y py-4 lg:grid-cols-[minmax(240px,1fr)_190px_160px_160px_auto]">
        <form
          className="relative"
          onSubmit={(event) => {
            event.preventDefault()
            setTermoAplicado(termo.trim())
            setPagina(0)
          }}
        >
          <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-zinc-400" />
          <Input
            value={termo}
            onChange={(event) => setTermo(event.target.value)}
            placeholder="Buscar denuncia ou anuncio"
            className="pl-9"
          />
        </form>
        <Select value={motivo} onValueChange={(value) => {
          setMotivo(value)
          setPagina(0)
        }}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            {motivos.map(([value, label]) => (
              <SelectItem key={value} value={value}>{label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Input
          type="date"
          aria-label="Data inicial"
          value={inicio}
          onChange={(event) => {
            setInicio(event.target.value)
            setPagina(0)
          }}
        />
        <Input
          type="date"
          aria-label="Data final"
          value={fim}
          onChange={(event) => {
            setFim(event.target.value)
            setPagina(0)
          }}
        />
        <Button type="button" variant="outline" onClick={limpar}>Limpar</Button>
      </div>

      {erro ? (
        <div className="flex flex-col items-start justify-between gap-3 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800 sm:flex-row sm:items-center">
          <p>{erro}</p>
          <Button type="button" size="sm" variant="outline" onClick={() => void carregar()}>
            Tentar novamente
          </Button>
        </div>
      ) : null}

      {carregando ? <p className="py-16 text-center text-sm text-zinc-500">Carregando denuncias...</p> : null}

      {vazio ? (
        <div className="flex min-h-64 flex-col items-center justify-center gap-3 border-y py-12 text-center">
          <Flag className="h-8 w-8 text-zinc-400" />
          <div>
            <p className="font-medium text-zinc-900">Nenhuma denuncia encontrada</p>
            <p className="text-sm text-zinc-500">A fila esta vazia para os filtros aplicados.</p>
          </div>
        </div>
      ) : null}

      <div className="divide-y border-y">
        {dados?.itens.map((denuncia) => (
          <article
            key={denuncia.id}
            className="grid gap-3 px-1 py-4 sm:px-3 lg:grid-cols-[minmax(0,1.2fr)_minmax(180px,.7fr)_150px_150px_auto] lg:items-center"
          >
            <div className="min-w-0">
              <p className="truncate font-semibold text-zinc-950">{denuncia.anuncioTitulo}</p>
              <p className="truncate text-sm text-zinc-500">{denuncia.anuncioSlug}</p>
              <p className="mt-1 text-xs text-zinc-400">{denuncia.protocolo}</p>
            </div>
            <div className="min-w-0">
              <p className="text-sm font-medium">{denuncia.motivoRotulo}</p>
              <p className="truncate text-xs text-zinc-500">
                {denuncia.denuncianteNome}
                {denuncia.denuncianteEmail ? ` · ${denuncia.denuncianteEmail}` : ''}
              </p>
            </div>
            <div>
              <span className={`inline-flex rounded-md border px-2 py-1 text-xs font-medium ${
                denuncia.status === 'PENDENTE'
                  ? 'border-amber-200 bg-amber-50 text-amber-800'
                  : 'border-zinc-200 bg-zinc-100 text-zinc-700'
              }`}>
                {denuncia.statusRotulo}
              </span>
            </div>
            <p className="text-sm text-zinc-600">{dataHora(denuncia.criadoEm)}</p>
            <div className="lg:text-right">
              <Button
                type="button"
                size="sm"
                onClick={() => {
                  setDenunciaId(denuncia.id)
                  setDetalheAberto(true)
                }}
              >
                Abrir detalhe
              </Button>
            </div>
          </article>
        ))}
      </div>

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

      <DenunciaDetailsModal
        open={detalheAberto}
        onOpenChange={setDetalheAberto}
        denunciaId={denunciaId}
        onChanged={() => {
          void carregar()
          window.dispatchEvent(new Event('admin-denuncias-updated'))
        }}
      />
    </section>
  )
}
