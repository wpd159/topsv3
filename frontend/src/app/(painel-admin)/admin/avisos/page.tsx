'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Megaphone, Plus, RefreshCw } from 'lucide-react'

import GerenciarAvisosTable from './avisos-table'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import {
  arquivarAdminAviso,
  atualizarAdminAviso,
  avisoError,
  buscarIndicadoresAvisos,
  criarAdminAviso,
  listarAdminAvisos,
  publicarAdminAviso,
  retirarAdminAviso,
  type AdminAviso,
  type AvisoEdicao,
  type AvisoIndicadores,
  type AvisoLocal,
  type AvisoPagina,
} from '@/lib/aviso-api'

const EMPTY_FORM: AvisoEdicao = {
  titulo: '',
  descricao: '',
  localExibicao: 'SITE',
  frequenciaExibicao: 'SEMPRE',
  permiteDispensar: true,
  ativoDe: null,
  ativoAte: null,
  versao: null,
}

function inputDate(value?: string | null) {
  if (!value) return ''
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(value))
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}T${part('hour')}:${part('minute')}`
}

function apiDate(value: string) {
  return value ? new Date(`${value}:00-03:00`).toISOString() : null
}

function emptyPage(): AvisoPagina<AdminAviso> {
  return { itens: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true }
}

export default function AdminAvisosPage() {
  const [termo, setTermo] = useState('')
  const [status, setStatus] = useState('TODOS')
  const [localExibicao, setLocalExibicao] = useState('TODOS')
  const [page, setPage] = useState(0)
  const [dados, setDados] = useState<AvisoPagina<AdminAviso>>(emptyPage())
  const [indicadores, setIndicadores] = useState<AvisoIndicadores | null>(null)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [formOpen, setFormOpen] = useState(false)
  const [editando, setEditando] = useState<AdminAviso | null>(null)
  const [form, setForm] = useState<AvisoEdicao>(EMPTY_FORM)
  const [inicioLocal, setInicioLocal] = useState('')
  const [fimLocal, setFimLocal] = useState('')
  const [previa, setPrevia] = useState<AdminAviso | null>(null)
  const [confirmacao, setConfirmacao] = useState<{ aviso: AdminAviso; acao: 'retirar' | 'arquivar' } | null>(null)
  const [operando, setOperando] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)
  const [createKey, setCreateKey] = useState('')
  const [sucesso, setSucesso] = useState<string | null>(null)

  const filters = useMemo(() => ({ termo, status, localExibicao, page, size: 10 }), [localExibicao, page, status, termo])

  const carregar = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    setErro(null)
    try {
      const [pagina, resumo] = await Promise.all([
        listarAdminAvisos(filters, signal),
        buscarIndicadoresAvisos(signal),
      ])
      setDados(pagina)
      setIndicadores(resumo)
    } catch (cause) {
      if (!signal?.aborted) setErro(avisoError(cause).message)
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [filters])

  useEffect(() => {
    const controller = new AbortController()
    void carregar(controller.signal)
    return () => controller.abort()
  }, [carregar, reload])

  const abrirNovo = () => {
    setEditando(null)
    setForm(EMPTY_FORM)
    setInicioLocal('')
    setFimLocal('')
    setCreateKey(`aviso-${crypto.randomUUID()}`)
    setFormOpen(true)
  }

  const abrirEdicao = (aviso: AdminAviso) => {
    setEditando(aviso)
    setForm({
      titulo: aviso.titulo,
      descricao: aviso.descricao,
      localExibicao: aviso.localExibicao,
      frequenciaExibicao: aviso.frequenciaExibicao,
      permiteDispensar: aviso.permiteDispensar,
      ativoDe: aviso.ativoDe,
      ativoAte: aviso.ativoAte,
      versao: aviso.versao,
    })
    setInicioLocal(inputDate(aviso.ativoDe))
    setFimLocal(inputDate(aviso.ativoAte))
    setFormOpen(true)
  }

  const salvar = async () => {
    setSalvando(true)
    setErro(null)
    try {
      const payload = { ...form, ativoDe: apiDate(inicioLocal), ativoAte: apiDate(fimLocal) }
      if (editando) {
        await atualizarAdminAviso(editando.id, payload)
        setSucesso('Aviso atualizado.')
      } else {
        await criarAdminAviso(payload, createKey)
        setSucesso('Aviso criado como rascunho.')
      }
      setFormOpen(false)
      setReload((value) => value + 1)
    } catch (cause) {
      setErro(avisoError(cause).message)
    } finally {
      setSalvando(false)
    }
  }

  const executar = async (aviso: AdminAviso, acao: 'publicar' | 'retirar' | 'arquivar') => {
    if (operando) return
    setOperando(aviso.id)
    setErro(null)
    try {
      if (acao === 'publicar') await publicarAdminAviso(aviso.id, aviso.versao)
      if (acao === 'retirar') await retirarAdminAviso(aviso.id, aviso.versao)
      if (acao === 'arquivar') await arquivarAdminAviso(aviso.id, aviso.versao)
      setSucesso(acao === 'publicar' ? 'Aviso publicado.' : acao === 'retirar' ? 'Aviso retirado imediatamente.' : 'Aviso arquivado.')
      setConfirmacao(null)
      setReload((value) => value + 1)
    } catch (cause) {
      setErro(avisoError(cause).message)
    } finally {
      setOperando(null)
    }
  }

  return (
    <section className="space-y-5">
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold text-zinc-950">Avisos</h1>
          <p className="text-sm text-zinc-600">Comunicados exibidos no site, apos login e no detalhe dos anuncios.</p>
        </div>
        <div className="flex gap-2">
          <Button type="button" size="icon" variant="outline" title="Atualizar" onClick={() => setReload((value) => value + 1)}>
            <RefreshCw className="h-4 w-4" />
          </Button>
          <Button type="button" onClick={abrirNovo}><Plus className="mr-2 h-4 w-4" />Novo aviso</Button>
        </div>
      </header>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6">
        {[
          ['Total', indicadores?.total],
          ['Rascunhos', indicadores?.rascunhos],
          ['Vigentes', indicadores?.vigentes],
          ['Agendados', indicadores?.agendados],
          ['Expirados', indicadores?.expirados],
          ['Arquivados', indicadores?.arquivados],
        ].map(([label, value]) => (
          <div key={String(label)} className="rounded-md border border-zinc-200 bg-white p-3">
            <p className="text-xs text-zinc-500">{label}</p>
            <p className="mt-1 text-xl font-semibold text-zinc-950">{value ?? '--'}</p>
          </div>
        ))}
      </div>

      <div className="grid gap-3 rounded-md border border-zinc-200 bg-white p-4 lg:grid-cols-[1fr_200px_220px]">
        <Input value={termo} onChange={(event) => { setTermo(event.target.value); setPage(0) }} placeholder="Buscar por titulo, texto ou criador" />
        <Select value={status} onValueChange={(value) => { setStatus(value); setPage(0) }}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="TODOS">Todos os estados</SelectItem>
            <SelectItem value="RASCUNHO">Rascunhos</SelectItem>
            <SelectItem value="PUBLICADO">Publicados</SelectItem>
            <SelectItem value="ARQUIVADO">Arquivados</SelectItem>
          </SelectContent>
        </Select>
        <Select value={localExibicao} onValueChange={(value) => { setLocalExibicao(value); setPage(0) }}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="TODOS">Todos os locais</SelectItem>
            <SelectItem value="SITE">Site</SelectItem>
            <SelectItem value="LOGIN_POPUP">Apos login</SelectItem>
            <SelectItem value="ANUNCIO_RODAPE">Detalhe do anuncio</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {sucesso ? <p className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{sucesso}</p> : null}
      {erro ? <div className="flex items-center justify-between gap-3 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800"><span>{erro}</span><Button size="sm" variant="outline" onClick={() => setReload((value) => value + 1)}>Tentar novamente</Button></div> : null}
      {loading ? <p className="py-12 text-center text-sm text-zinc-500">Carregando avisos...</p> : null}
      {!loading && !erro && dados.itens.length === 0 ? (
        <div className="py-14 text-center"><Megaphone className="mx-auto h-8 w-8 text-zinc-400" /><p className="mt-3 font-medium">Nenhum aviso encontrado</p></div>
      ) : null}
      {!loading && dados.itens.length ? (
        <>
          <GerenciarAvisosTable
            dados={dados}
            operando={operando}
            onEditar={abrirEdicao}
            onPrevia={setPrevia}
            onPublicar={(aviso) => void executar(aviso, 'publicar')}
            onRetirar={(aviso) => setConfirmacao({ aviso, acao: 'retirar' })}
            onArquivar={(aviso) => setConfirmacao({ aviso, acao: 'arquivar' })}
          />
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-zinc-500">{dados.totalElements} aviso(s)</p>
            <div className="flex gap-2">
              <Button variant="outline" disabled={dados.first} onClick={() => setPage((value) => Math.max(0, value - 1))}>Anterior</Button>
              <Button variant="outline" disabled={dados.last} onClick={() => setPage((value) => value + 1)}>Proximo</Button>
            </div>
          </div>
        </>
      ) : null}

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{editando ? 'Editar aviso' : 'Criar novo aviso'}</DialogTitle>
            <DialogDescription>O conteudo e texto simples e nao dispara comunicacoes externas.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2"><Label htmlFor="aviso-titulo">Titulo</Label><Input id="aviso-titulo" value={form.titulo} maxLength={160} onChange={(event) => setForm((current) => ({ ...current, titulo: event.target.value }))} /></div>
            <div className="space-y-2"><Label htmlFor="aviso-descricao">Mensagem</Label><Textarea id="aviso-descricao" rows={6} value={form.descricao} maxLength={4000} onChange={(event) => setForm((current) => ({ ...current, descricao: event.target.value }))} /></div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label>Local de exibicao</Label>
                <Select value={form.localExibicao} onValueChange={(value: AvisoLocal) => setForm((current) => ({ ...current, localExibicao: value }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent><SelectItem value="SITE">Site</SelectItem><SelectItem value="LOGIN_POPUP">Apos login</SelectItem><SelectItem value="ANUNCIO_RODAPE">Detalhe do anuncio</SelectItem></SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Frequencia</Label>
                <Select value={form.frequenciaExibicao} onValueChange={(value: AvisoEdicao['frequenciaExibicao']) => setForm((current) => ({ ...current, frequenciaExibicao: value }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent><SelectItem value="SEMPRE">Sempre</SelectItem><SelectItem value="UMA_VEZ">Uma vez</SelectItem><SelectItem value="DIARIO">Diario</SelectItem></SelectContent>
                </Select>
              </div>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2"><Label htmlFor="aviso-inicio">Inicio</Label><Input id="aviso-inicio" type="datetime-local" value={inicioLocal} onChange={(event) => setInicioLocal(event.target.value)} /></div>
              <div className="space-y-2"><Label htmlFor="aviso-fim">Fim</Label><Input id="aviso-fim" type="datetime-local" value={fimLocal} onChange={(event) => setFimLocal(event.target.value)} /></div>
            </div>
            <label className="flex items-center gap-3 text-sm"><input type="checkbox" checked={form.permiteDispensar} onChange={(event) => setForm((current) => ({ ...current, permiteDispensar: event.target.checked }))} />Permitir que o usuario dispense o aviso</label>
          </div>
          <DialogFooter><Button variant="outline" onClick={() => setFormOpen(false)} disabled={salvando}>Cancelar</Button><Button onClick={() => void salvar()} disabled={salvando}>{salvando ? 'Salvando...' : 'Salvar rascunho'}</Button></DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(previa)} onOpenChange={(open) => !open && setPrevia(null)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader><DialogTitle>Previa do aviso</DialogTitle><DialogDescription>{previa?.localExibicaoRotulo} - {previa?.frequenciaExibicaoRotulo}</DialogDescription></DialogHeader>
          <div className="rounded-md border border-pink-100 bg-pink-50 p-4"><p className="font-semibold text-zinc-950">{previa?.titulo}</p><p className="mt-2 whitespace-pre-line text-sm text-zinc-700">{previa?.descricao}</p></div>
          <DialogFooter><Button variant="outline" onClick={() => setPrevia(null)}>Fechar</Button></DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(confirmacao)} onOpenChange={(open) => !open && setConfirmacao(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader><DialogTitle>{confirmacao?.acao === 'retirar' ? 'Retirar aviso?' : 'Arquivar aviso?'}</DialogTitle><DialogDescription>{confirmacao?.acao === 'retirar' ? 'O aviso deixara de aparecer imediatamente.' : 'O aviso ficara preservado no historico administrativo e nao podera ser republicado.'}</DialogDescription></DialogHeader>
          <DialogFooter><Button variant="outline" onClick={() => setConfirmacao(null)}>Cancelar</Button><Button onClick={() => confirmacao && void executar(confirmacao.aviso, confirmacao.acao)} disabled={Boolean(operando)}>Confirmar</Button></DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
